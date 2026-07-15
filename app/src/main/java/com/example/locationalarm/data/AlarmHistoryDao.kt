package com.example.locationalarm.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface AlarmHistoryDao {

    @Query("SELECT * FROM alarm_history ORDER BY triggeredAt DESC")
    fun getAllHistory(): LiveData<List<AlarmHistory>>

    @Query("SELECT * FROM alarm_history WHERE alarmId = :alarmId ORDER BY triggeredAt DESC")
    fun getHistoryByAlarm(alarmId: Long): LiveData<List<AlarmHistory>>

    @Query("DELETE FROM alarm_history WHERE alarmId = :alarmId")
    suspend fun deleteByAlarmId(alarmId: Long)

    @Query("DELETE FROM alarm_history")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(history: AlarmHistory): Long

    @Query("DELETE FROM alarm_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
