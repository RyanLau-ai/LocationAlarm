package com.example.locationalarm.data

import android.content.Context
import androidx.lifecycle.LiveData

/**
 * 数据仓库 — 统一管理闹钟和历史记录的数据访问
 */
class AlarmRepository(context: Context) {

    private val alarmDao = AlarmDatabase.getDatabase(context).alarmDao()
    private val historyDao = AlarmDatabase.getDatabase(context).alarmHistoryDao()

    // ---- 闹钟 CRUD ----

    fun getAllAlarms(): LiveData<List<Alarm>> = alarmDao.getAllAlarms()

    suspend fun getEnabledAlarms(): List<Alarm> = alarmDao.getEnabledAlarms()

    suspend fun getAlarmById(id: Long): Alarm? = alarmDao.getAlarmById(id)

    suspend fun insert(alarm: Alarm): Long = alarmDao.insert(alarm)

    suspend fun update(alarm: Alarm) = alarmDao.update(alarm)

    suspend fun delete(alarm: Alarm) = alarmDao.delete(alarm)

    suspend fun deleteById(id: Long) = alarmDao.deleteById(id)

    suspend fun setEnabled(id: Long, enabled: Boolean) = alarmDao.setEnabled(id, enabled)

    suspend fun setTriggered(id: Long, triggered: Boolean) = alarmDao.setTriggered(id, triggered)

    suspend fun setTriggeredAndTime(id: Long, triggered: Boolean, lastTriggeredAt: Long) =
        alarmDao.setTriggeredAndTime(id, triggered, lastTriggeredAt)

    fun getAllTags(): LiveData<List<String>> = alarmDao.getAllTags()

    // ---- 历史记录 ----

    fun getAllHistory(): LiveData<List<AlarmHistory>> = historyDao.getAllHistory()

    fun getHistoryByAlarm(alarmId: Long): LiveData<List<AlarmHistory>> = historyDao.getHistoryByAlarm(alarmId)

    suspend fun insertHistory(history: AlarmHistory) = historyDao.insert(history)

    suspend fun deleteHistoryById(id: Long) = historyDao.deleteById(id)

    suspend fun deleteAllHistory() = historyDao.deleteAll()

    suspend fun deleteHistoryByAlarmId(alarmId: Long) = historyDao.deleteByAlarmId(alarmId)
}
