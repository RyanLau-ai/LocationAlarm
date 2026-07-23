package com.example.locationalarm

import android.app.Application
import android.util.Log
import com.example.locationalarm.data.AlarmRepository
import com.example.locationalarm.service.ServiceHealthWorker
import org.osmdroid.config.Configuration

/**
 * Application class - global initialization
 *
 * Initializes OSMDroid configuration and registers WorkManager health check.
 * No third-party API key required.
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

        // Initialize OSMDroid - set user agent (required by OpenStreetMap tile usage policy)
        try {
            Configuration.getInstance().userAgentValue = packageName
            Configuration.getInstance().osmdroidTileCache = getExternalFilesDir(null)
            Log.i(TAG, "OSMDroid initialized, userAgent=$packageName")
        } catch (e: Exception) {
            Log.e(TAG, "OSMDroid initialization failed", e)
        }

        // Register WorkManager periodic health check
        ServiceHealthWorker.enqueue(this)
        Log.i(TAG, "Application initialized")
    }
}
