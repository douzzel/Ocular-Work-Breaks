package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "break_log")
data class BreakLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "EYE", "STRETCH", "STAND"
    val description: String,
    val completed: Boolean = true
)
