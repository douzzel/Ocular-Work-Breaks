package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DatabaseDao {
    @Query("SELECT * FROM work_schedule ORDER BY dayOfWeek ASC")
    fun getSchedulesFlow(): Flow<List<WorkSchedule>>

    @Query("SELECT * FROM work_schedule ORDER BY dayOfWeek ASC")
    suspend fun getSchedules(): List<WorkSchedule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<WorkSchedule>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: WorkSchedule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: BreakLog)

    @Query("SELECT * FROM break_log ORDER BY timestamp DESC")
    fun getLogsFlow(): Flow<List<BreakLog>>

    @Query("SELECT * FROM break_log WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay ORDER BY timestamp DESC")
    fun getTodayLogsFlow(startOfDay: Long, endOfDay: Long): Flow<List<BreakLog>>

    @Query("DELETE FROM break_log")
    suspend fun clearLogs()
}
