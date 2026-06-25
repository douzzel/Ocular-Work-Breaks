package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BreakRepository
import com.example.data.database.AppDatabase
import com.example.data.database.BreakLog
import com.example.data.database.WorkSchedule
import com.example.receiver.AlarmReceiver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class BreakViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BreakRepository
    
    // Core Database Flows
    private val _schedules = MutableStateFlow<List<WorkSchedule>>(emptyList())
    val schedules: StateFlow<List<WorkSchedule>> = _schedules.asStateFlow()

    private val _todayLogs = MutableStateFlow<List<BreakLog>>(emptyList())
    val todayLogs: StateFlow<List<BreakLog>> = _todayLogs.asStateFlow()

    // Tracking states
    private val _isTrackingActive = MutableStateFlow(true)
    val isTrackingActive: StateFlow<Boolean> = _isTrackingActive.asStateFlow()

    // Timer configuration (30 minutes default = 1800 seconds)
    private val defaultIntervalSeconds = 30 * 60
    private val _remainingSeconds = MutableStateFlow(1800)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(true)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    // Current exercise states
    private val _exerciseState = MutableStateFlow(ExerciseState.PRE_BREAK)
    val exerciseState: StateFlow<ExerciseState> = _exerciseState.asStateFlow()

    // Sub-timer for the 20-sec or 60-sec stretches
    private val _stepTimerSeconds = MutableStateFlow(20)
    val stepTimerSeconds: StateFlow<Int> = _stepTimerSeconds.asStateFlow()

    private val _currentEyeStep = MutableStateFlow(EyeStep.LOOK_FAR)
    val currentEyeStep: StateFlow<EyeStep> = _currentEyeStep.asStateFlow()

    private val _currentStretchIndex = MutableStateFlow(0)
    val currentStretchIndex: StateFlow<Int> = _currentStretchIndex.asStateFlow()

    private var timerJob: Job? = null
    private var exerciseTimerJob: Job? = null

    // Preloaded desk stretches
    val deskStretches = listOf(
        DeskStretch(
            title = "Shoulder Roll",
            instructions = "Sit straight up. Inhale and lift your shoulders toward your ears, then roll them backward and down as you exhale. Repeat 5 times.",
            iconName = "airline_seat_recline_normal"
        ),
        DeskStretch(
            title = "Wrist Prayer stretch",
            instructions = "Join your palms in front of your chest in a prayer position. Slowly press your wrists downward while keeping palms together. Hold for 15s to relieve computer typing strain.",
            iconName = "pan_tool"
        ),
        DeskStretch(
            title = "Gentle Neck Tilt",
            instructions = "Drop your left ear toward your left shoulder without lifting your shoulder. Hold safely for 10s, then return to center and gently switch sides to target desk-neck stiffness.",
            iconName = "accessibility"
        ),
        DeskStretch(
            title = "Seated Torso Twist",
            instructions = "Place your right hand on your left knee and look over your left shoulder, rotating your torso. Keep thighs facing forward. Hold for 10s then switch sides.",
            iconName = "sync_alt"
        )
    )

    init {
        val databaseDao = AppDatabase.getDatabase(application).databaseDao()
        repository = BreakRepository(databaseDao)
        
        // Initial setup
        viewModelScope.launch {
            repository.checkAndPrepopulateSchedules()
            
            // Collect flow updates from database
            launch {
                repository.schedulesFlow.collect { list ->
                    _schedules.value = list
                }
            }
            
            launch {
                val startOfDay = getStartOfDayTimestamp()
                val endOfDay = getEndOfDayTimestamp()
                repository.getTodayLogs(startOfDay, endOfDay).collect { logs ->
                    _todayLogs.value = logs
                }
            }
        }

        startTimer()
        toggleTracking(true) // schedule initial background Alarm service
    }

    // Toggle background alarms
    fun toggleTracking(active: Boolean) {
        _isTrackingActive.value = active
        if (active) {
            AlarmReceiver.scheduleNextAlarm(getApplication())
        } else {
            AlarmReceiver.cancelTracking(getApplication())
        }
    }

    // Controls for simulation/testing
    fun toggleTimer() {
        _isTimerRunning.value = !_isTimerRunning.value
    }

    fun skipTime(seconds: Int) {
        val next = _remainingSeconds.value - seconds
        if (next <= 0) {
            _remainingSeconds.value = 5 // Go to last 5 seconds so users can watch transition!
        } else {
            _remainingSeconds.value = next
        }
    }

    // Starts the standard 30-minute countdown loop
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_isTimerRunning.value && _exerciseState.value == ExerciseState.PRE_BREAK) {
                    val next = _remainingSeconds.value - 1
                    if (next <= 0) {
                        triggerBreakAlert()
                    } else {
                        _remainingSeconds.value = next
                    }
                }
            }
        }
    }

    private fun triggerBreakAlert() {
        // Randomly choose next recommended break type
        val choice = when ((1..3).random()) {
            1 -> ExerciseState.EYE_EXERCISE
            2 -> ExerciseState.DESK_STRETCH
            else -> ExerciseState.STANDING_BREAK
        }
        startExerciseMode(choice)
    }

    fun startExerciseMode(state: ExerciseState) {
        _exerciseState.value = state
        _isTimerRunning.value = false
        
        when (state) {
            ExerciseState.EYE_EXERCISE -> {
                startEyeExerciseSteps()
            }
            ExerciseState.DESK_STRETCH -> {
                // Select random stretch or cycle
                _currentStretchIndex.value = (deskStretches.indices).random()
                _stepTimerSeconds.value = 20
                startExerciseCountdown()
            }
            ExerciseState.STANDING_BREAK -> {
                _stepTimerSeconds.value = 60
                startExerciseCountdown()
            }
            else -> {}
        }
    }

    // Countdown loop for the active breaks
    private fun startExerciseCountdown() {
        exerciseTimerJob?.cancel()
        exerciseTimerJob = viewModelScope.launch {
            while (_stepTimerSeconds.value > 0) {
                delay(1000)
                 _stepTimerSeconds.value -= 1
            }
        }
    }

    // Specific sequence of eye movements matching 20/20/20 custom design guidelines
    fun startEyeExerciseSteps() {
        viewModelScope.launch {
            // Step 1: Look Far (20 seconds)
            _currentEyeStep.value = EyeStep.LOOK_FAR
            _stepTimerSeconds.value = 20
            runEyeStepCountdown()

            // Step 2: Roll Eyes Circularly (10 seconds)
            _currentEyeStep.value = EyeStep.ROLL_EYES
            _stepTimerSeconds.value = 10
            runEyeStepCountdown()

            // Step 3: Horizontal Trace Left/Right (10 seconds)
            _currentEyeStep.value = EyeStep.TRACE_HORIZONTAL
            _stepTimerSeconds.value = 10
            runEyeStepCountdown()

            // Step 4: Rapid blinking (10 seconds)
            _currentEyeStep.value = EyeStep.BLINK_RELAX
            _stepTimerSeconds.value = 10
            runEyeStepCountdown()

            // Complete automatically
            logExerciseCompletion("Eye Exercises (20/20/20 Complete)")
            resetToCoolDownState()
        }
    }

    private suspend fun runEyeStepCountdown() {
        while (_stepTimerSeconds.value > 0) {
            delay(1000)
            _stepTimerSeconds.value -= 1
        }
    }

    fun completeCurrentBreak() {
        val label = when (_exerciseState.value) {
            ExerciseState.EYE_EXERCISE -> "Completed 20/20/20 eye exercises"
            ExerciseState.DESK_STRETCH -> "Completed stretch: ${deskStretches[_currentStretchIndex.value].title}"
            ExerciseState.STANDING_BREAK -> "Completed 60s standing break"
            else -> "Quick desk break"
        }
        
        logExerciseCompletion(label)
        resetToCoolDownState()
    }

    fun logExerciseCompletion(text: String) {
        val type = when (_exerciseState.value) {
            ExerciseState.EYE_EXERCISE -> "EYE"
            ExerciseState.DESK_STRETCH -> "STRETCH"
            ExerciseState.STANDING_BREAK -> "STAND"
            else -> "GENERIC"
        }

        viewModelScope.launch {
            repository.insertLog(
                BreakLog(
                    type = type,
                    description = text,
                    completed = true
                )
            )
        }
    }

    fun resetToCoolDownState() {
        exerciseTimerJob?.cancel()
        _exerciseState.value = ExerciseState.PRE_BREAK
        _remainingSeconds.value = defaultIntervalSeconds
        _isTimerRunning.value = true
        startTimer()
    }

    // Save individual updated schedule
    fun saveSchedule(schedule: WorkSchedule) {
        viewModelScope.launch {
            repository.saveSchedule(schedule)
            // Recheck/reschedule to make sure it respects changes
            if (_isTrackingActive.value) {
                AlarmReceiver.scheduleNextAlarm(getApplication())
            }
        }
    }

    fun clearLogHistory() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    // Time calculations
    private fun getStartOfDayTimestamp(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun getEndOfDayTimestamp(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return calendar.timeInMillis
    }

    // Utility formatting
    fun formatRemainingTime(sec: Int): String {
        val min = sec / 60
        val s = sec % 60
        return String.format("%02d:%02d", min, s)
    }
}

enum class ExerciseState {
    PRE_BREAK,
    EYE_EXERCISE,
    DESK_STRETCH,
    STANDING_BREAK
}

enum class EyeStep(val title: String, val text: String) {
    LOOK_FAR("20/20/20 Focal Shift", "Look at any object at least 6 meters (20 feet) away. Rest your focus on it to completely relax your eyeball muscles."),
    ROLL_EYES("Circumarcular Roll", "Gently roll your eyes clockwise in a wide circular loop. Then counter-clockwise. Relieves muscle strain."),
    TRACE_HORIZONTAL("Left-to-Right Tracking", "Gently shift your gaze completely to the left, hold for 1 second, then sweep slowly to the far right."),
    BLINK_RELAX("Blink & Rest", "Blink rapidly 8-10 times, then comfortably close your eyes. Allow your retinas to soothe under lids.")
}

data class DeskStretch(
    val title: String,
    val instructions: String,
    val iconName: String
)
