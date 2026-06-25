package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.WorkSchedule
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Devices restarted, reschedule the main chain
            scheduleNextAlarm(context)
            return
        }

        val action = intent.getStringExtra("ACTION_TYPE") ?: "GENERIC"
        Log.d("AlarmReceiver", "Alarm received action = $action")

        // Run schedule verification on IO thread
        val database = AppDatabase.getDatabase(context)
        val dao = database.databaseDao()

        CoroutineScope(Dispatchers.IO).launch {
            val list = dao.getSchedules()
            if (isTimeInWorkSchedule(list)) {
                // Time matches work schedule! Trigger notification
                val notificationType = selectRandomBreakNotification()
                NotificationHelper.showReminderNotification(
                    context,
                    notificationType.first,
                    notificationType.second
                )
            } else {
                Log.d("AlarmReceiver", "Outside work schedule, skipping notification.")
            }

            // Always chain schedule the next alarm in 30 minutes
            scheduleNextAlarm(context)
        }
    }

    private fun selectRandomBreakNotification(): Pair<String, String> {
        val notifications = listOf(
            Pair("Eye Rest (20/20/20 Rule)", "It is time to look at an object 6 meters (20 feet) away for 20 seconds to rest your eye muscles!"),
            Pair("Discreet Desk Stretch", "Time for a quick desk break! Roll your shoulders back gently and twist your torso."),
            Pair("60-Second Standing Stretch", "Get up and stand for 60 seconds to improve circulation and relieve spine pressure."),
            Pair("Gentle Eye Tracking", "Rest your eyes! Track an object slowly to the left, then to the right to stretch focal muscles."),
            Pair("Sores and Spasms relief", "Do a quick wrist prayer stretch at your desk for immediate hands and fingers relaxation.")
        )
        return notifications.random()
    }

    companion object {
        private const val REQUEST_CODE = 99912

        // Decide if the current time matches the user's work schedule
        fun isTimeInWorkSchedule(schedules: List<WorkSchedule>): Boolean {
            if (schedules.isEmpty()) return true // default to active if no database loaded yet

            val calendar = Calendar.getInstance()
            // Convert Calendar's DAY_OF_WEEK (Sun=1, Mon=2, ..., Sat=7) to our DB format (Mon=1, ..., Sun=7)
            val dayOfWeekJava = calendar.get(Calendar.DAY_OF_WEEK)
            val currentDayOfWeek = when (dayOfWeekJava) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }

            val matchingDay = schedules.find { it.dayOfWeek == currentDayOfWeek } ?: return false

            if (!matchingDay.isWorking) return false

            val nowHour = calendar.get(Calendar.HOUR_OF_DAY)
            val nowMinute = calendar.get(Calendar.MINUTE)
            val nowMinutes = nowHour * 60 + nowMinute

            // Helper to convert "HH:MM" to minutes from midnight
            fun timeToMin(timeStr: String): Int {
                return try {
                    val parts = timeStr.split(":")
                    val hrs = parts[0].trim().toInt()
                    val mins = parts[1].trim().toInt()
                    hrs * 60 + mins
                } catch (e: Exception) {
                    0
                }
            }

            val startMorningMin = timeToMin(matchingDay.startMorning)
            val endMorningMin = timeToMin(matchingDay.endMorning)
            val startAfternoonMin = timeToMin(matchingDay.startAfternoon)
            val endAfternoonMin = timeToMin(matchingDay.endAfternoon)

            return if (matchingDay.isHalfDay) {
                when (matchingDay.halfDayType) {
                    "AM" -> nowMinutes in startMorningMin..endMorningMin
                    "PM" -> nowMinutes in startAfternoonMin..endAfternoonMin
                    else -> {
                        // Custom logic context fallback, default check morning
                        nowMinutes in startMorningMin..endMorningMin
                    }
                }
            } else {
                // Full working day checks morning OR afternoon
                (nowMinutes in startMorningMin..endMorningMin) || (nowMinutes in startAfternoonMin..endAfternoonMin)
            }
        }

        fun scheduleNextAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("ACTION_TYPE", "BREAK_CHECK")
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Interval is 30 minutes in milliseconds
            val intervalMs = 30 * 60 * 1000L

            // Use setAndAllowWhileIdle which doesn't require special SCHEDULE_EXACT_ALARM permission
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + intervalMs,
                        pendingIntent
                    )
                } catch (e: Exception) {
                    try {
                        alarmManager.set(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + intervalMs,
                            pendingIntent
                        )
                    } catch (ex: Exception) {
                        Log.e("AlarmReceiver", "Failed to schedule alarm", ex)
                    }
                }
            } else {
                try {
                    alarmManager.set(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + intervalMs,
                        pendingIntent
                    )
                } catch (ex: Exception) {
                    Log.e("AlarmReceiver", "Failed to schedule alarm on old Android", ex)
                }
            }
            Log.d("AlarmReceiver", "Scheduled next alarm break check in 30 minutes")
        }

        fun cancelTracking(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d("AlarmReceiver", "Cancelled break checks and alarms")
        }
    }
}
