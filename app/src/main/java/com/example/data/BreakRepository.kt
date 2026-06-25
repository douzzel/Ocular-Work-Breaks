package com.example.data

import com.example.data.database.BreakLog
import com.example.data.database.DatabaseDao
import com.example.data.database.WorkSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class BreakRepository(private val dao: DatabaseDao) {

    val schedulesFlow: Flow<List<WorkSchedule>> = dao.getSchedulesFlow()
    val logsFlow: Flow<List<BreakLog>> = dao.getLogsFlow()

    suspend fun getSchedulesDirect(): List<WorkSchedule> {
        var list = dao.getSchedules()
        if (list.isEmpty()) {
            populateDefaultSchedules()
            list = dao.getSchedules()
        }
        return list
    }

    suspend fun checkAndPrepopulateSchedules() {
        val list = dao.getSchedules()
        if (list.isEmpty()) {
            populateDefaultSchedules()
        }
    }

    private suspend fun populateDefaultSchedules() {
        val defaults = listOf(
            WorkSchedule(dayOfWeek = 1, isWorking = true, isHalfDay = false, startMorning = "09:00", endMorning = "12:30", startAfternoon = "13:30", endAfternoon = "18:00"),
            WorkSchedule(dayOfWeek = 2, isWorking = true, isHalfDay = false, startMorning = "09:00", endMorning = "12:30", startAfternoon = "13:30", endAfternoon = "18:00"),
            WorkSchedule(dayOfWeek = 3, isWorking = true, isHalfDay = false, startMorning = "09:00", endMorning = "12:30", startAfternoon = "13:30", endAfternoon = "18:00"),
            WorkSchedule(dayOfWeek = 4, isWorking = true, isHalfDay = false, startMorning = "09:00", endMorning = "12:30", startAfternoon = "13:30", endAfternoon = "18:00"),
            WorkSchedule(dayOfWeek = 5, isWorking = true, isHalfDay = false, startMorning = "09:00", endMorning = "12:30", startAfternoon = "13:30", endAfternoon = "18:00"),
            WorkSchedule(dayOfWeek = 6, isWorking = false, isHalfDay = false, startMorning = "09:00", endMorning = "12:30", startAfternoon = "13:30", endAfternoon = "18:00"),
            WorkSchedule(dayOfWeek = 7, isWorking = false, isHalfDay = false, startMorning = "09:00", endMorning = "12:30", startAfternoon = "13:30", endAfternoon = "18:00")
        )
        dao.insertSchedules(defaults)
    }

    suspend fun saveSchedule(schedule: WorkSchedule) {
        dao.insertSchedule(schedule)
    }

    suspend fun insertLog(log: BreakLog) {
        dao.insertLog(log)
    }

    fun getTodayLogs(startOfDay: Long, endOfDay: Long): Flow<List<BreakLog>> {
        return dao.getTodayLogsFlow(startOfDay, endOfDay)
    }

    suspend fun clearLogs() {
        dao.clearLogs()
    }
}
