package com.example.locationalarm

import android.app.Application
import com.amap.api.location.AMapLocationClient
import com.example.locationalarm.data.AlarmRepository

/**
 * Application 类 — 全局初始化
 *
 * 高德隐私合规要求：
 * 在调用定位 SDK 任何接口之前，必须调用 AMapLocationClient.updatePrivacyShow/Agree，
 * 否则 SDK 不会正常工作。
 */
class LocationAlarmApp : Application() {

    val repository: AlarmRepository by lazy {
        AlarmRepository(this)
    }

    override fun onCreate() {
        super.onCreate()

        // 高德隐私合规初始化
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)
    }
}
