package com.example.locationalarm.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.locationalarm.R
import com.example.locationalarm.data.AlertWay
import com.example.locationalarm.ui.MainActivity

/**
 * 通知工具：按提醒方式建立独立通知渠道
 * - 仅震动 / 仅响铃 / 震动+响铃 / 静默
 */
object NotificationHelper {

    // 渠道 ID
    const val CHANNEL_GEO = "geo_service"        // 前台服务常驻
    const val CHANNEL_IN = "alert_in"            // 进入提醒（震动）
    const val CHANNEL_IN_RING = "alert_in_ring"  // 进入提醒（响铃）
    const val CHANNEL_IN_BOTH = "alert_in_both"  // 进入提醒（震动+响铃）
    const val CHANNEL_IN_SILENT = "alert_in_silent" // 进入提醒（静默）
    const val CHANNEL_OUT = "alert_out"          // 离开提醒（震动）
    const val CHANNEL_OUT_RING = "alert_out_ring"
    const val CHANNEL_OUT_BOTH = "alert_out_both"
    const val CHANNEL_OUT_SILENT = "alert_out_silent"

    const val NOTIFY_ID_GEO = 1001

    /**
     * 创建所有通知渠道（应用启动时调用一次）
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 前台服务渠道（低优先级，无声音）
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GEO,
                context.getString(R.string.notification_channel_name_geo),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_desc_geo)
                setSound(null, null)
                enableVibration(false)
            }
        )

        // 进入提醒渠道（按方式区分）
        nm.createNotificationChannel(channelFor(
            context, CHANNEL_IN, R.string.notification_channel_name_in,
            R.string.notification_channel_desc_in, vibrate = true, ring = false
        ))
        nm.createNotificationChannel(channelFor(
            context, CHANNEL_IN_RING, R.string.notification_channel_name_in,
            R.string.notification_channel_desc_in, vibrate = false, ring = true
        ))
        nm.createNotificationChannel(channelFor(
            context, CHANNEL_IN_BOTH, R.string.notification_channel_name_in,
            R.string.notification_channel_desc_in, vibrate = true, ring = true
        ))
        nm.createNotificationChannel(channelFor(
            context, CHANNEL_IN_SILENT, R.string.notification_channel_name_in,
            R.string.notification_channel_desc_in, vibrate = false, ring = false, silent = true
        ))

        // 离开提醒渠道
        nm.createNotificationChannel(channelFor(
            context, CHANNEL_OUT, R.string.notification_channel_name_out,
            R.string.notification_channel_desc_out, vibrate = true, ring = false
        ))
        nm.createNotificationChannel(channelFor(
            context, CHANNEL_OUT_RING, R.string.notification_channel_name_out,
            R.string.notification_channel_desc_out, vibrate = false, ring = true
        ))
        nm.createNotificationChannel(channelFor(
            context, CHANNEL_OUT_BOTH, R.string.notification_channel_name_out,
            R.string.notification_channel_desc_out, vibrate = true, ring = true
        ))
        nm.createNotificationChannel(channelFor(
            context, CHANNEL_OUT_SILENT, R.string.notification_channel_name_out,
            R.string.notification_channel_desc_out, vibrate = false, ring = false, silent = true
        ))
    }

    /**
     * 构建单个通知渠道
     */
    private fun channelFor(
        context: Context,
        id: String,
        nameRes: Int,
        descRes: Int,
        vibrate: Boolean,
        ring: Boolean,
        silent: Boolean = false
    ): NotificationChannel {
        val importance = when {
            ring || vibrate -> NotificationManager.IMPORTANCE_HIGH
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }
        val channel = NotificationChannel(
            id,
            context.getString(nameRes),
            importance
        ).apply {
            description = context.getString(descRes)
            if (!ring) {
                setSound(null, null)  // 无声
            } else {
                // 默认通知铃声
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(uri, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            enableVibration(vibrate)
            if (vibrate) {
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            setShowBadge(true)
        }
        return channel
    }

    /**
     * 根据提醒方式选择通知渠道 ID
     */
    fun channelIdFor(way: AlertWay, isIn: Boolean): String = when (way) {
        AlertWay.VIBRATE -> if (isIn) CHANNEL_IN else CHANNEL_OUT
        AlertWay.RING -> if (isIn) CHANNEL_IN_RING else CHANNEL_OUT_RING
        AlertWay.BOTH -> if (isIn) CHANNEL_IN_BOTH else CHANNEL_OUT_BOTH
        AlertWay.SILENT -> if (isIn) CHANNEL_IN_SILENT else CHANNEL_OUT_SILENT
    }

    /**
     * 发送围栏触发通知
     */
    fun showFenceAlert(
        context: Context,
        fenceName: String,
        content: String,
        isIn: Boolean,
        way: AlertWay
    ) {
        val channelId = channelIdFor(way, isIn)
        val title = context.getString(if (isIn) R.string.notification_in_title else R.string.notification_out_title)

        // 点击通知打开主界面
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_pin)
            .setContentTitle("$title · $fenceName")
            .setContentText(content.ifBlank { fenceName })
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.ifBlank { fenceName }))
            .setAutoCancel(true)
            .setContentIntent(pi)

        // 震动（BOTH / VIBRATE）
        if (way == AlertWay.VIBRATE || way == AlertWay.BOTH) {
            builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifyId = (if (isIn) 2000 else 3000) + fenceName.hashCode() % 900
        nm.notify(notifyId, builder.build())

        // 兜底震动（若渠道设置未生效）
        if (way == AlertWay.VIBRATE || way == AlertWay.BOTH) {
            vibrate(context)
        }
    }

    /**
     * 手动震动（兼容 API 26-30）
     */
    private fun vibrate(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * 前台服务常驻通知
     */
    fun buildServiceNotification(context: Context, fenceCount: Int): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_GEO)
            .setSmallIcon(R.drawable.ic_pin)
            .setContentTitle(context.getString(R.string.notification_geo_title))
            .setContentText(context.getString(R.string.notification_geo_text, fenceCount))
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
