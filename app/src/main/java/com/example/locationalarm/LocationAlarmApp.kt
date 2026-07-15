package com.example.locationalarm

import android.app.Application
import com.example.locationalarm.data.AlarmRepository

/**
 * Application 类 — 全局初始化
 */
class LocationAlarmApp : Application() {

    val repository: AlarmRepository by lazy {
        AlarmRepository(this)
    }
}
