package com.example.locationalarm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 服务重启接收器
 *
 * 接收来自 LocationAlarmService.onTaskRemoved() 发出的重启广播，
 * 在服务被系统杀死后尝试重新启动。
 *
 * 与 BootReceiver 的区别：
 * - BootReceiver: 设备开机后触发
 * - RestartReceiver: 服务运行期间被系统杀死后触发
 *
 * 注意：Android 8.0+ 后台启动前台服务有限制，但以下情况例外：
 * - 收到广播时应用处于前台
 * - 前台服务已经被启动过
 * - 用户最近与 app 有交互
 *
 * 对于第三方 ROM (小米/华为/OPPO/vivo) 需要用户在设置中允许自启动
 */
class RestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RestartReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "收到重启广播: ${intent.action}")

        when (intent.action) {
            LocationAlarmService.ACTION_RESTART_SERVICE,
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                try {
                    val serviceIntent = Intent(context, LocationAlarmService::class.java).apply {
                        action = LocationAlarmService.ACTION_START
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.i(TAG, "服务重启请求已发送")
                } catch (e: Exception) {
                    Log.e(TAG, "服务重启失败", e)
                    // 在 Android 10+ 上，后台启动前台服务可能被拒绝
                    // 此时依赖 WorkManager 的周期性健康检查来恢复
                }
            }
        }
    }
}
