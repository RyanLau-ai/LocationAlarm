package com.example.locationalarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.locationalarm.R
import com.example.locationalarm.ui.MainActivity

/**
 * 通知管理器 — 创建通知渠道、构建前台服务通知和闹钟触发通知
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID_SERVICE = "location_alarm_service"
        const val CHANNEL_ID_ALARM = "location_alarm_trigger"
        const val CHANNEL_ID_PERMISSION = "location_alarm_permission"

        const val NOTIFICATION_ID_SERVICE = 1
        const val NOTIFICATION_ID_ALARM_BASE = 1000 // 闹钟通知 ID 基数，+alarmId 确保唯一
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)

        // 前台服务通知渠道
        val serviceChannel = NotificationChannel(
            CHANNEL_ID_SERVICE,
            context.getString(R.string.notification_channel_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_service_desc)
            setShowBadge(false)
        }

        // 闹钟触发通知渠道
        val alarmChannel = NotificationChannel(
            CHANNEL_ID_ALARM,
            context.getString(R.string.notification_channel_alarm),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_alarm_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            enableLights(true)
        }

        // 权限提醒通知渠道
        val permissionChannel = NotificationChannel(
            CHANNEL_ID_PERMISSION,
            context.getString(R.string.notification_channel_permission),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_permission_desc)
        }

        manager.createNotificationChannels(listOf(serviceChannel, alarmChannel, permissionChannel))
    }

    /**
     * 构建前台服务持续运行通知
     */
    fun buildServiceNotification(activeAlarmCount: Int = 0): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (activeAlarmCount > 0) {
            context.getString(R.string.service_notification_active, activeAlarmCount)
        } else {
            context.getString(R.string.service_notification_idle)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /**
     * 发送闹钟触发通知
     */
    fun showAlarmNotification(alarmName: String, reminder: String, address: String, alarmId: Long) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, alarmId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
            .setContentTitle("📍 $alarmName")
            .setContentText(reminder)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$reminder\n\n📍 $address"))
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify((NOTIFICATION_ID_ALARM_BASE + alarmId).toInt(), notification)
    }

    /**
     * 取消指定闹钟的通知
     */
    fun cancelAlarmNotification(alarmId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel((NOTIFICATION_ID_ALARM_BASE + alarmId).toInt())
    }

    /**
     * 发送权限缺失提醒通知
     */
    fun showPermissionNotification(message: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_PERMISSION)
            .setContentTitle(context.getString(R.string.permission_required_title))
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(9999, notification)
    }
}
