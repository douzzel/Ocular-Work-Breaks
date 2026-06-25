package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_schedule")
data class WorkSchedule(
    @PrimaryKey val dayOfWeek: Int, // 1 = Monday, 2 = Tuesday, ..., 7 = Sunday
    val isWorking: Boolean = true,
    val isHalfDay: Boolean = false,
    val halfDayType: String = "AM", // "AM" (Morning only), "PM" (Afternoon only), "CUSTOM"
    val startMorning: String = "09:00",
    val endMorning: String = "12:30",
    val startAfternoon: String = "13:30",
    val endAfternoon: String = "18:00"
)
