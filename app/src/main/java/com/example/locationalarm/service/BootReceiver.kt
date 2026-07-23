package com.example.locationalarm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 开机自启接收器 — 设备重启后自动恢复位置闹钟服务
 *
 * 注意：Android 10+ 应用安装后需至少手动启动一次，接收器才会生效。
 * 用户需在系统设置中允许应用自启动。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "收到开机广播: ${intent.action}")

        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            try {
                val serviceIntent = Intent(context, LocationAlarmService::class.java).apply {
                    action = LocationAlarmService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.i(TAG, "开机后服务启动请求已发送")
            } catch (e: Exception) {
                Log.e(TAG, "开机自启失败", e)
            }
        }
    }
}
