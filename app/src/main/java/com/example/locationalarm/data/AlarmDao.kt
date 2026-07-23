package com.example.locationalarm.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarms ORDER BY updatedAt DESC")
    fun getAllAlarms(): LiveData<List<Alarm>>

    @Query("SELECT * FROM alarms WHERE enabled = 1 ORDER BY updatedAt DESC")
    suspend fun getEnabledAlarms(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Long): Alarm?

    @Insert
    suspend fun insert(alarm: Alarm): Long

    @Update
    suspend fun update(alarm: Alarm)

    @Delete
    suspend fun delete(alarm: Alarm)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE alarms SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE alarms SET triggered = :triggered, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTriggered(id: Long, triggered: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE alarms SET triggered = :triggered, lastTriggeredAt = :lastTriggeredAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTriggeredAndTime(id: Long, triggered: Boolean, lastTriggeredAt: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT DISTINCT tag FROM alarms WHERE tag != '' ORDER BY tag")
    fun getAllTags(): LiveData<List<String>>
}
