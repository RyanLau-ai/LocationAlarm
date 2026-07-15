package com.example.locationalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 闹钟触发历史记录
 *
 * @param id 自增主键
 * @param alarmId 关联的闹钟 ID
 * @param alarmName 闹钟名称快照
 * @param reminder 提醒内容快照
 * @param address 地址快照
 * @param latitude 触发时位置纬度
 * @param longitude 触发时位置经度
 * @param distance 触发时与目标的距离（米）
 * @param triggeredAt 触发时间戳
 */
@Entity(tableName = "alarm_history")
data class AlarmHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val alarmId: Long,
    val alarmName: String,
    val reminder: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distance: Float,
    val triggeredAt: Long = System.currentTimeMillis()
)
