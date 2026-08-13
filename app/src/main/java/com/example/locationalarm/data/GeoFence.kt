package com.example.locationalarm.data

/**
 * 提醒方式枚举，对应独立通知渠道
 */
enum class AlertWay {
    VIBRATE,   // 仅震动
    RING,      // 仅响铃
    BOTH,      // 震动+响铃
    SILENT     // 静默
}

/**
 * 地理围栏数据模型（持久化存储）
 */
data class GeoFence(
    val id: String,              // 唯一标识（UUID）
    val name: String,            // 围栏名称
    val latitude: Double,         // 中心纬度
    val longitude: Double,        // 中心经度
    val address: String,          // 地址文字
    val radius: Int,              // 围栏半径（米）
    val enableIn: Boolean,        // 开启进入提醒
    val enableOut: Boolean,       // 开启离开提醒
    val alertContent: String,     // 提醒内容
    val alertWay: AlertWay,      // 提醒方式
    val trackRecord: Boolean = false  // 活动轨迹记录开关
) {
    companion object {
        const val DEFAULT_RADIUS = 200  // 默认半径 200 米

        fun create(
            name: String,
            latitude: Double,
            longitude: Double,
            address: String,
            radius: Int = DEFAULT_RADIUS,
            enableIn: Boolean = true,
            enableOut: Boolean = false,
            alertContent: String = "",
            alertWay: AlertWay = AlertWay.VIBRATE,
            trackRecord: Boolean = false
        ): GeoFence {
            return GeoFence(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                latitude = latitude,
                longitude = longitude,
                address = address,
                radius = radius,
                enableIn = enableIn,
                enableOut = enableOut,
                alertContent = alertContent,
                alertWay = alertWay,
                trackRecord = trackRecord
            )
        }
    }
}
