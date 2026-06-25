package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.BreakLog
import com.example.data.database.WorkSchedule
import com.example.ui.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorkBreakReminderTheme {
                MainAppScreen()
            }
        }
    }
}

// Inline Material 3 light Theme with Clean Minimalism colors
@Composable
fun WorkBreakReminderTheme(content: @Composable () -> Unit) {
    val cleanMinimalistColorScheme = lightColorScheme(
        primary = Color(0xFF6750A4),            // Modern Purple
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFEADDFF),   // Pale Lavender Accent Card BG
        onPrimaryContainer = Color(0xFF21005D),
        secondary = Color(0xFF625B71),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE8DEF8), // Active status pill BG
        onSecondaryContainer = Color(0xFF1D192B),
        background = Color(0xFFF7F9FC),          // Light Refreshing Slate
        onBackground = Color(0xFF1C1B1F),
        surface = Color(0xFFFFFFFF),            // Clean Solid White Surfaces
        onSurface = Color(0xFF1C1B1F),
        outline = Color(0xFFE1E3E8)             // Subtle separation lines
    )

    MaterialTheme(
        colorScheme = cleanMinimalistColorScheme,
        typography = Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                letterSpacing = (-1).sp
            ),
            titleLarge = androidx.compose.ui.text.TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                letterSpacing = 0.sp
            ),
            bodyLarge = androidx.compose.ui.text.TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                letterSpacing = 0.5.sp
            )
        ),
        content = content
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainAppScreen(
    viewModel: BreakViewModel = viewModel()
) {
    val context = LocalContext.current
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val todayLogs by viewModel.todayLogs.collectAsStateWithLifecycle()
    val isTrackingActive by viewModel.isTrackingActive.collectAsStateWithLifecycle()
    val remainingSeconds by viewModel.remainingSeconds.collectAsStateWithLifecycle()
    val isTimerRunning by viewModel.isTimerRunning.collectAsStateWithLifecycle()
    val exerciseState by viewModel.exerciseState.collectAsStateWithLifecycle()
    val stepTimerSeconds by viewModel.stepTimerSeconds.collectAsStateWithLifecycle()
    val currentEyeStep by viewModel.currentEyeStep.collectAsStateWithLifecycle()
    val currentStretchIndex by viewModel.currentStretchIndex.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(1) } // 1 = Today, 2 = Team Schedule, 3 = History logs
    var editingScheduleDay by remember { mutableStateOf<WorkSchedule?>(null) }
    var showQuickSettingsDialog by remember { mutableStateOf(false) }

    // Live permission handler
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotificationPermission = granted
            if (granted) {
                Toast.makeText(context, "Notifications enabled successfully!", Toast.LENGTH_SHORT).show()
                viewModel.toggleTracking(true) // update database status
            } else {
                Toast.makeText(context, "Permission was denied. Alarm alerts will only be shown in-app.", Toast.LENGTH_LONG).show()
            }
        }
    )

    // Trigger initial permission prompt on first render
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Eye Logo",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = "Ocular Work Breaks",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            modifier = Modifier.testTag("settings_button"),
                            onClick = { showQuickSettingsDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }

                        // Profile badge representing John Doe ("JD") from Clean Minimalism UI draft
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "JD",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                // Friendly warning inside top bar if notification permission is not granted
                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= 33) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF1F1))
                            .clickable { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            .padding(vertical = 6.dp, horizontal = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Alert", tint = Color.Red, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Notifications disabled. Tap to enable desktop alerts during shift hours.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B1D1D),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Elegant M3 Bottom Navigation with active pill indicators matching guidelines
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = Color(0xFFF3F4F9),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(if (activeTab == 1) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = "Today") },
                    label = { Text("Today", fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(if (activeTab == 2) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth, contentDescription = "Schedule") },
                    label = { Text("Schedule", fontWeight = if (activeTab == 2) FontWeight.Bold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    icon = { Icon(if (activeTab == 3) Icons.Filled.BarChart else Icons.Outlined.BarChart, contentDescription = "History") },
                    label = { Text("History", fontWeight = if (activeTab == 3) FontWeight.Bold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Determine layout according to active tab select or overlay state
                if (exerciseState != ExerciseState.PRE_BREAK) {
                    // Fullscreen Intercept for running eye or stretch breaking exercise routines
                    ActiveBreakOverlay(
                        exerciseState = exerciseState,
                        stepTimer = stepTimerSeconds,
                        currentStep = currentEyeStep,
                        stretch = viewModel.deskStretches[currentStretchIndex],
                        onCancel = { viewModel.resetToCoolDownState() },
                        onComplete = { viewModel.completeCurrentBreak() }
                    )
                } else {
                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                        },
                        label = "tab_switch"
                    ) { targetTab ->
                        when (targetTab) {
                            1 -> TodayDashboard(
                                remainingSec = remainingSeconds,
                                isTimerRunning = isTimerRunning,
                                finishedTargetCount = todayLogs.size,
                                schedules = schedules,
                                onToggleTimer = { viewModel.toggleTimer() },
                                onFastForward = { secs -> viewModel.skipTime(secs) },
                                onTriggerBreak = { type -> viewModel.startExerciseMode(type) },
                                onEditSchedule = { activeTab = 2 } // redirects to schedule
                            )
                            2 -> ScheduleSetup(
                                schedules = schedules,
                                onEditDay = { day -> editingScheduleDay = day }
                            )
                            3 -> BreakLogHistory(
                                logs = todayLogs,
                                onClear = { viewModel.clearLogHistory() }
                            )
                        }
                    }
                }

                // Global dialogs
                editingScheduleDay?.let { schedule ->
                    EditScheduleDialog(
                        schedule = schedule,
                        onDismiss = { editingScheduleDay = null },
                        onSave = { updated ->
                            viewModel.saveSchedule(updated)
                            editingScheduleDay = null
                            Toast.makeText(context, "Schedule updated!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                if (showQuickSettingsDialog) {
                    QuickSettingsDialog(
                        isTracking = isTrackingActive,
                        onToggleTracking = { viewModel.toggleTracking(it) },
                        onDismiss = { showQuickSettingsDialog = false }
                    )
                }
            }
        }
    }
}

// TAB 1: Today Dashboard
@Composable
fun TodayDashboard(
    remainingSec: Int,
    isTimerRunning: Boolean,
    finishedTargetCount: Int,
    schedules: List<WorkSchedule>,
    onToggleTimer: () -> Unit,
    onFastForward: (Int) -> Unit,
    onTriggerBreak: (ExerciseState) -> Unit,
    onEditSchedule: () -> Unit
) {
    val scrollState = rememberScrollState()
    val totalProgressFraction = (remainingSec.toFloat() / 1800f).coerceIn(0f, 1f)

    // Calculate dynamic state representation
    val currentDay = remember {
        val cal = Calendar.getInstance()
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7; else -> 1
        }
    }

    val todaySchedule = schedules.find { it.dayOfWeek == currentDay }
    val isWorkingToday = todaySchedule?.isWorking ?: true
    val scheduleTimeSpan = when {
        todaySchedule == null -> "Not Set"
        !todaySchedule.isWorking -> "Day Off / Holiday"
        todaySchedule.isHalfDay && todaySchedule.halfDayType == "AM" -> "AM Half: ${todaySchedule.startMorning} - ${todaySchedule.endMorning}"
        todaySchedule.isHalfDay && todaySchedule.halfDayType == "PM" -> "PM Half: ${todaySchedule.startAfternoon} - ${todaySchedule.endAfternoon}"
        else -> "${todaySchedule.startMorning} - ${todaySchedule.endAfternoon}"
    }

    val minutesLeft = remainingSec / 60
    val formattedClock = String.format("%02d:%02d", remainingSec / 60, remainingSec % 60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card matching MD3 look in the specification
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("status_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E3E8))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "Current Session",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF49454F),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isTimerRunning) "Focusing" else "Paused",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Active banner indicator
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isTimerRunning) Color(0xFFE8DEF8) else Color(0xFFF3F4F9),
                                shape = RoundedCornerShape(100.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isTimerRunning) "ACTIVE" else "PAUSED",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTimerRunning) Color(0xFF1D192B) else Color(0xFF49454F)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Radial circle container
                Box(
                    modifier = Modifier.size(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Gray trace background
                    CircularProgressIndicator(
                        progress = { 1.0f },
                        modifier = Modifier.size(140.dp),
                        color = Color(0xFFE7E0EC),
                        strokeWidth = 10.dp,
                        strokeCap = StrokeCap.Round
                    )
                    // High-contrast primary indicator
                    CircularProgressIndicator(
                        progress = { totalProgressFraction },
                        modifier = Modifier.size(140.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 10.dp,
                        strokeCap = StrokeCap.Round
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${minutesLeft}m",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Next Break",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = Color(0xFF49454F)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Stats summaries
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFFF3F4F9), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("TODAY'S BREAKS", style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF49454F)))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$finishedTargetCount logged", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFFF3F4F9), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("STATUS", style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF49454F)))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(if (isWorkingToday) "On Shift" else "Off Shift", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (isWorkingToday) Color(0xFF2E7D32) else Color(0xFFC62828)))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Timer Controls & Fast Forward Simulation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onToggleTimer,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isTimerRunning) "Pause" else "Start"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isTimerRunning) "Pause Ticker" else "Start Ticker")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    ElevatedButton(
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("simulate_button"),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        onClick = { onFastForward(300) }, // Skips 5 minutes ahead
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = "Fast-Forward", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fast Forward 5m")
                    }
                }
            }
        }

        // Active Quick Launch/Exercise Selection Card matching clean purple look of upcoming recommendation box
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "Idea",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "MANUAL EXERCISE ACCELERATOR",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                Text(
                    text = "Launch Desk Exercises Instantly",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Text(
                    text = "No need to wait for the 30-minute schedule. Select a rest workout style below to practice discreet desk relaxation right now or stand up to refresh blood circulation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    lineHeight = 20.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onTriggerBreak(ExerciseState.EYE_EXERCISE) },
                        modifier = Modifier.weight(1f).testTag("eye_btn"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Visibility, contentDescription = "Eyes", modifier = Modifier.size(20.dp))
                            Text("Eyes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { onTriggerBreak(ExerciseState.DESK_STRETCH) },
                        modifier = Modifier.weight(1f).testTag("stretch_btn"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Accessibility, contentDescription = "Stretch", modifier = Modifier.size(20.dp))
                            Text("Stretch", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { onTriggerBreak(ExerciseState.STANDING_BREAK) },
                        modifier = Modifier.weight(1f).testTag("stand_btn"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.DirectionsWalk, contentDescription = "Stand", modifier = Modifier.size(20.dp))
                            Text("Stand", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Daily Work Schedule Mini Info View
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TODAY'S SCHEDULE SHIFT",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color(0xFF49454F)
                    )
                )

                Text(
                    text = "Edit Active Shift",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.clickable { onEditSchedule() }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFFE1E3E8), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (isWorkingToday) Color(0xFFE8DEF8) else Color(0xFFFFF1F1),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isWorkingToday) Icons.Default.Work else Icons.Default.EventBusy,
                            contentDescription = "Shift",
                            tint = if (isWorkingToday) MaterialTheme.colorScheme.primary else Color.Red,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        val currentDayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
                        Text(
                            text = "$currentDayName (Today)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = scheduleTimeSpan,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF49454F)
                        )
                    }
                }
            }
        }
    }
}

// TAB 2: Work Schedule Setup
@Composable
fun ScheduleSetup(
    schedules: List<WorkSchedule>,
    onEditDay: (WorkSchedule) -> Unit
) {
    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = "Your Working Schedule",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Set your standard office shift working hours. The app checks this schedule to only activate notifications and reminders when you are actually at work.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF49454F),
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(schedules) { schedule ->
                val dayName = dayNames.getOrElse(schedule.dayOfWeek - 1) { "Day Off" }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("schedule_item_${schedule.dayOfWeek}"),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE1E3E8)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = dayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                if (schedule.isHalfDay && schedule.isWorking) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFEADDFF), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Half-Day (${schedule.halfDayType})",
                                            style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF21005D))
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            if (schedule.isWorking) {
                                val workingText = when {
                                    schedule.isHalfDay && schedule.halfDayType == "AM" -> "AM only: ${schedule.startMorning} - ${schedule.endMorning}"
                                    schedule.isHalfDay && schedule.halfDayType == "PM" -> "PM only: ${schedule.startAfternoon} - ${schedule.endAfternoon}"
                                    else -> "Full Day: ${schedule.startMorning} - ${schedule.endMorning} & ${schedule.startAfternoon} - ${schedule.endAfternoon}"
                                }
                                Text(
                                    text = workingText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF49454F)
                                )
                            } else {
                                Text(
                                    text = "Off / Non-working",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Red,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        IconButton(
                            modifier = Modifier.testTag("edit_day_${schedule.dayOfWeek}"),
                            onClick = { onEditDay(schedule) }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Day Info", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

// TAB 3: Historical Break Logs
@Composable
fun BreakLogHistory(
    logs: List<BreakLog>,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Today's Break Activity",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Keep a clean history of eye rests, stand ups, and stretch sessions completed today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF49454F)
                )
            }

            if (logs.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = Color.Red, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "No items logged",
                        tint = Color(0xFFBBBCC4),
                        modifier = Modifier.size(60.dp)
                    )
                    Text(
                        text = "No breaks logged yet today.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF49454F)
                    )
                    Text(
                        text = "Complete your first eye exercise or stretching break to populate logs!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8B8D99),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(logs) { log ->
                    val colorAndIcon = when (log.type) {
                        "EYE" -> Pair(Color(0xFFEADDFF), Icons.Default.Visibility)
                        "STRETCH" -> Pair(Color(0xFFE8DEF8), Icons.Default.Accessibility)
                        "STAND" -> Pair(Color(0xFFD1E7DD), Icons.Default.DirectionsWalk)
                        else -> Pair(Color(0xFFF3F4F9), Icons.Default.Check)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE1E3E8)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(colorAndIcon.first, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = colorAndIcon.second,
                                    contentDescription = log.type,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = log.description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                val timeStr = remember(log.timestamp) {
                                    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                    sdf.format(Date(log.timestamp))
                                }
                                Text(
                                    text = "Logged at $timeStr",
                                    style = TextStyle(fontSize = 12.sp, color = Color(0xFF49454F))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// FULLSCREEN EXERCISE MODAL: Active routine screen
@Composable
fun ActiveBreakOverlay(
    exerciseState: ExerciseState,
    stepTimer: Int,
    currentStep: EyeStep,
    stretch: DeskStretch,
    onCancel: () -> Unit,
    onComplete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth().maxWidthIn(max = 500.dp)
        ) {
            // Exercise Category Pill
            val headerText = when (exerciseState) {
                ExerciseState.EYE_EXERCISE -> "EYE REST WORKOUT"
                ExerciseState.DESK_STRETCH -> "OFFICE DESK STRETCH"
                ExerciseState.STANDING_BREAK -> "STANDING CIRCULATION CYCLE"
                else -> "OFFICE WORK BREAK"
            }

            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(100.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = headerText,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, letterSpacing = 1.sp)
                )
            }

            // Big instructions header based on current status
            val exerciseTitle = when (exerciseState) {
                ExerciseState.EYE_EXERCISE -> currentStep.title
                ExerciseState.DESK_STRETCH -> stretch.title
                ExerciseState.STANDING_BREAK -> "Get Up & Stand"
                else -> ""
            }

            val instructions = when (exerciseState) {
                ExerciseState.EYE_EXERCISE -> currentStep.text
                ExerciseState.DESK_STRETCH -> stretch.instructions
                ExerciseState.STANDING_BREAK -> "Prolonged sitting fatigues key core support muscles. Step away from your setup, lift your gaze, and relax straight on your feet for just a minute."
                else -> ""
            }

            Text(
                text = exerciseTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            // Dynamic Step/Progress Sub counter
            Text(
                text = "Timer: $stepTimer seconds remaining",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            // ANIMATION WRAPPERS: Look-far / Eye-roll / Stretch diagrams Custom Canvas illustrations
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.3f)
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE1E3E8))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (exerciseState) {
                        ExerciseState.EYE_EXERCISE -> {
                            // High fidelity specific eye canvas animation
                            EyeExerciseAnimation(step = currentStep)
                        }
                        ExerciseState.DESK_STRETCH -> {
                            // Stretch visual representation (Static illustration representing chosen stretch)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val stretchIcon = when(stretch.iconName) {
                                    "airline_seat_recline_normal" -> Icons.Default.AirlineSeatReclineNormal
                                    "pan_tool" -> Icons.Default.PanTool
                                    "accessibility" -> Icons.Default.Accessibility
                                    else -> Icons.Default.SyncAlt
                                }
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .background(Color(0xFFEADDFF).copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = stretchIcon,
                                        contentDescription = stretch.title,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                                Text("Relax / Focus", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
                            }
                        }
                        ExerciseState.STANDING_BREAK -> {
                            // Circular pulse representation of standing
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .background(Color(0xFFD1E7DD).copy(alpha = 0.6f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsWalk,
                                        contentDescription = "Stand Up",
                                        tint = Color(0xFF0F5132),
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                                Text("Unpressurize Spine", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F5132))
                            }
                        }
                        else -> {}
                    }
                }
            }

            // Description of active instruction
            Text(
                text = instructions,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF49454F),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Done actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    onClick = onCancel
                ) {
                    Text("Skip Break")
                }

                Button(
                    modifier = Modifier.weight(1.2f).testTag("done_break_btn"),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    onClick = onComplete
                ) {
                    Icon(Icons.Default.Done, contentDescription = "Done")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Break Complete")
                }
            }
        }
    }
}

// DIALOG: Quick Settings (Tracking status, channel, background test, etc)
@Composable
fun QuickSettingsDialog(
    isTracking: Boolean,
    onToggleTracking: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E3E8))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "System Reminders",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Control whether the app periodically wakes up to deliver high-priority eye rest and office stretching notifications to your notification feed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF49454F)
                )

                Divider(color = Color(0xFFE1E3E8))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Work shift notifications",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Periodic alerts via system tray",
                            style = TextStyle(fontSize = 12.sp, color = Color(0xFF49454F))
                        )
                    }

                    Switch(
                        modifier = Modifier.testTag("tracking_switch"),
                        checked = isTracking,
                        onCheckedChange = { onToggleTracking(it) }
                    )
                }

                Divider(color = Color(0xFFE1E3E8))

                Button(
                    modifier = Modifier.fillMaxWidth().testTag("close_settings_btn"),
                    onClick = onDismiss,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("OK, Close")
                }
            }
        }
    }
}

// DIALOG: Daily schedule hours editor
@Composable
fun EditScheduleDialog(
    schedule: WorkSchedule,
    onDismiss: () -> Unit,
    onSave: (WorkSchedule) -> Unit
) {
    var isWorking by remember { mutableStateOf(schedule.isWorking) }
    var isHalfDay by remember { mutableStateOf(schedule.isHalfDay) }
    var halfDayType by remember { mutableStateOf(schedule.halfDayType) } // AM or PM
    
    var startMorning by remember { mutableStateOf(schedule.startMorning) }
    var endMorning by remember { mutableStateOf(schedule.endMorning) }
    var startAfternoon by remember { mutableStateOf(schedule.startAfternoon) }
    var endAfternoon by remember { mutableStateOf(schedule.endAfternoon) }

    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val titleLabel = dayNames.getOrElse(schedule.dayOfWeek - 1) { "Day Off" }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E3E8))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Edit: $titleLabel Shift",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Row: Working day status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Is Working Day", fontWeight = FontWeight.Bold)
                    Switch(
                        checked = isWorking,
                        onCheckedChange = { isWorking = it }
                    )
                }

                if (isWorking) {
                    // Row: Is Half Day Shift or Full Day
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Is Half-Day Shift", fontWeight = FontWeight.Medium)
                        Checkbox(
                            checked = isHalfDay,
                            onCheckedChange = { isHalfDay = it }
                        )
                    }

                    if (isHalfDay) {
                        // Radio option: morning only or afternoon only
                        Column {
                            Text("Select Active Shift Portion", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF49454F)))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = halfDayType == "AM",
                                        onClick = { halfDayType = "AM" }
                                    )
                                    Text("Morning (AM)", fontSize = 13.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = halfDayType == "PM",
                                        onClick = { halfDayType = "PM" }
                                    )
                                    Text("Afternoon (PM)", fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (!isHalfDay || halfDayType == "AM") {
                        Text("Morning Shift Hours", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = startMorning,
                                onValueChange = { startMorning = it },
                                label = { Text("Start (HH:MM)") },
                                modifier = Modifier.weight(1f).testTag("morning_start"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                            )
                            OutlinedTextField(
                                value = endMorning,
                                onValueChange = { endMorning = it },
                                label = { Text("End (HH:MM)") },
                                modifier = Modifier.weight(1f).testTag("morning_end"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                            )
                        }
                    }

                    if (!isHalfDay || halfDayType == "PM") {
                        Text("Afternoon Shift Hours", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = startAfternoon,
                                onValueChange = { startAfternoon = it },
                                label = { Text("Start (HH:MM)") },
                                modifier = Modifier.weight(1f).testTag("afternoon_start"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                            )
                            OutlinedTextField(
                                value = endAfternoon,
                                onValueChange = { endAfternoon = it },
                                label = { Text("End (HH:MM)") },
                                modifier = Modifier.weight(1f).testTag("afternoon_end"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        modifier = Modifier.weight(1.2f).testTag("save_schedule_day"),
                        shape = RoundedCornerShape(24.dp),
                        onClick = {
                            onSave(
                                schedule.copy(
                                    isWorking = isWorking,
                                    isHalfDay = isHalfDay,
                                    halfDayType = halfDayType,
                                    startMorning = startMorning,
                                    endMorning = endMorning,
                                    startAfternoon = startAfternoon,
                                    endAfternoon = endAfternoon
                                )
                            )
                        }
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

// Utility extension for max width bounds
fun Modifier.maxWidthIn(max: androidx.compose.ui.unit.Dp): Modifier = this.widthIn(max = max)
