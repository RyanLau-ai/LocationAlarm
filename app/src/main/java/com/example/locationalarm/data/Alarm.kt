package com.example.locationalarm.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * 位置闹钟实体
 *
 * @param id 自增主键
 * @param name 闹钟名称（如"到公司提醒打卡"）
 * @param reminder 提醒内容（如"记得打卡！"）
 * @param latitude 目标位置纬度
 * @param longitude 目标位置经度
 * @param address 目标地址文本
 * @param radius 触发范围半径（米）
 * @param enabled 是否启用
 * @param triggered 是否已触发（到达范围内后设为 true，离开后可重新触发）
 * @param tag 分类标签
 * @param repeatInterval 重复提醒间隔（毫秒），0 表示仅提醒一次
 * @param lastTriggeredAt 上次触发时间戳，用于重复提醒间隔判断
 * @param createdAt 创建时间戳
 * @param updatedAt 更新时间戳
 */
@Parcelize
@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val reminder: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val radius: Int,
    val enabled: Boolean = true,
    val triggered: Boolean = false,
    val tag: String = "",
    val repeatInterval: Long = 0L,
    val lastTriggeredAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Parcelable
