package com.example.locationalarm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 开机自启接收器 — 设备重启后自动恢复位置闹钟服务
 *
 * 注意：Android 10+ 应用安装后需至少手动启动一次，接收器才会生效。
 * 用户需在系统设置中允许应用自启动。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, LocationAlarmService::class.java).apply {
                action = LocationAlarmService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
