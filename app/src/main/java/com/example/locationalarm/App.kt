package com.example.locationalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.example.locationalarm.util.NotificationHelper

/**
 * 应用入口：初始化通知渠道
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }

    companion object {
        fun appContext(context: Context): Context = context.applicationContext
    }
}
