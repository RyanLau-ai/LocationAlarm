package com.example.locationalarm.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.locationalarm.LocationAlarmApp
import java.util.concurrent.TimeUnit

/**
 * 服务健康检查 Worker
 *
 * 每 15 分钟检查一次：
 * 1. 是否有启用的闹钟
 * 2. 如果有，但服务未运行，则重新启动服务
 *
 * 这是在服务被系统杀死后的"最后一道防线"恢复机制
 */
class ServiceHealthWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ServiceHealthWorker"
        private const val WORK_NAME = "location_alarm_health_check"

        /**
         * 注册周期性健康检查（每 15 分钟一次）
         * 15 分钟是 WorkManager 的最短周期
         */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceHealthWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(TAG, "健康检查已注册")
        }

        /**
         * 取消健康检查
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as LocationAlarmApp
            val enabledAlarms = app.repository.getEnabledAlarms()

            if (enabledAlarms.isNotEmpty() && !LocationAlarmService.isRunning.value) {
                Log.w(TAG, "检测到 ${enabledAlarms.size} 个启用闹钟但服务未运行，正在重启服务...")
                val serviceIntent = Intent(applicationContext, LocationAlarmService::class.java).apply {
                    action = LocationAlarmService.ACTION_START
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        applicationContext.startForegroundService(serviceIntent)
                    } else {
                        applicationContext.startService(serviceIntent)
                    }
                    Log.i(TAG, "服务重启请求已发送")
                } catch (e: Exception) {
                    Log.e(TAG, "WorkManager 重启服务失败（可能因后台限制）", e)
                }
            } else {
                Log.d(TAG, "健康检查通过: ${enabledAlarms.size} 个闹钟, 服务运行=${LocationAlarmService.isRunning.value}")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "健康检查异常", e)
            Result.retry()
        }
    }
}
