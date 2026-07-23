package com.example.locationalarm

import android.app.Application
import android.util.Log
import com.amap.api.location.AMapLocationClient
import com.example.locationalarm.data.AlarmRepository
import com.example.locationalarm.service.ServiceHealthWorker

/**
 * Application 类 — 全局初始化
 *
 * 高德隐私合规要求：
 * 在调用定位 SDK 任何接口之前，必须调用 AMapLocationClient.updatePrivacyShow/Agree，
 * 否则 SDK 不会正常工作。
 */
class LocationAlarmApp : Application() {

    companion object {
        private const val TAG = "LocationAlarmApp"
    }

    val repository: AlarmRepository by lazy {
        AlarmRepository(this)
    }

    override fun onCreate() {
        super.onCreate()

        // 高德隐私合规初始化
        try {
            AMapLocationClient.updatePrivacyShow(this, true, true)
            AMapLocationClient.updatePrivacyAgree(this, true)
        } catch (e: Exception) {
            Log.e(TAG, "高德隐私合规初始化失败", e)
        }

        // 注册 WorkManager 周期性健康检查
        // 每 15 分钟检查一次服务是否存活，如果被杀则自动恢复
        ServiceHealthWorker.enqueue(this)
        Log.i(TAG, "应用初始化完成")
    }
}
