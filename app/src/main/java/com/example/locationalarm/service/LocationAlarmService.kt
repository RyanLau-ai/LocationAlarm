package com.example.locationalarm.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.ServiceCompat
import com.example.locationalarm.LocationAlarmApp
import com.example.locationalarm.data.Alarm
import com.example.locationalarm.data.AlarmHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Location alarm foreground service (Android LocationManager version)
 *
 * Uses Android's built-in LocationManager instead of any third-party SDK.
 * No API key required, works on all devices without registration.
 *
 * Responsibilities:
 * 1. Run as foreground service with persistent notification
 * 2. Periodically get current location, compare with all enabled alarms
 * 3. Enter range -> trigger notification + record history
 * 4. Leave range -> reset alarm, allow re-trigger
 * 5. Support repeat reminders
 */
class LocationAlarmService : Service() {

    companion object {
        private const val TAG = "LocationAlarmService"

        const val ACTION_START = "com.example.locationalarm.START_SERVICE"
        const val ACTION_STOP = "com.example.locationalarm.STOP_SERVICE"
        const val ACTION_RESTART_SERVICE = "com.example.locationalarm.RESTART_SERVICE"

        // Location update interval: 30 seconds
        private const val LOCATION_UPDATE_INTERVAL = 30_000L
        private const val LOCATION_UPDATE_MIN_DISTANCE = 0f

        val isRunning = MutableStateFlow(false)
    }

    private var locationManager: LocationManager? = null
    private lateinit var notificationHelper: NotificationHelper
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isLocationMonitoring = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            checkAlarms(location)
        }

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {
            Log.w(TAG, "Location provider disabled: $provider")
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfSafely()
                return START_NOT_STICKY
            }
            else -> {
                startLocationMonitoring()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "onTaskRemoved - service may be killed, attempting restart")
        val restartIntent = Intent(applicationContext, LocationAlarmService::class.java).apply {
            action = ACTION_START
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        val pendingIntent = android.app.PendingIntent.getService(
            this, 1, restartIntent,
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        android.os.SystemClock.elapsedRealtime() + 1000,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        android.os.SystemClock.elapsedRealtime() + 1000,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + 1000,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    android.app.AlarmManager.ELAPSED_REALTIME,
                    android.os.SystemClock.elapsedRealtime() + 1000,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm permission denied, fallback to normal timer", e)
            try {
                alarmManager.set(
                    android.app.AlarmManager.ELAPSED_REALTIME,
                    android.os.SystemClock.elapsedRealtime() + 1000,
                    pendingIntent
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Restart timer completely failed", e2)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Restart timer failed", e)
        }

        val restartBroadcast = Intent(applicationContext, RestartReceiver::class.java).apply {
            action = ACTION_RESTART_SERVICE
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        sendBroadcast(restartBroadcast)

        super.onTaskRemoved(rootIntent)
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = powerManager.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "LocationAlarm::ServiceWakeLock"
            )
            wakeLock?.acquire(12 * 60 * 60 * 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "Acquire WakeLock failed", e)
        }
    }

    /**
     * Start foreground service and begin location monitoring using Android LocationManager.
     */
    @Synchronized
    private fun startLocationMonitoring() {
        val notification = notificationHelper.buildServiceNotification(0)
        try {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID_SERVICE,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (e: Exception) {
            Log.e(TAG, "Start foreground service failed", e)
        }
        isRunning.value = true

        if (isLocationMonitoring && locationManager != null) {
            Log.i(TAG, "Location monitoring already running, skip duplicate init")
            return
        }

        if (!hasLocationPermission()) {
            Log.w(TAG, "Missing location permission, cannot start monitoring")
            notificationHelper.showPermissionNotification("缺少定位权限，请在设置中开启")
            stopSelfSafely()
            return
        }

        try {
            locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

            // Request location updates from both GPS and Network providers
            val providers = mutableListOf<String>()
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                providers.add(LocationManager.GPS_PROVIDER)
            }
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                providers.add(LocationManager.NETWORK_PROVIDER)
            }

            if (providers.isEmpty()) {
                Log.w(TAG, "No location providers available")
                notificationHelper.showPermissionNotification("请开启 GPS 或网络定位")
            }

            for (provider in providers) {
                try {
                    locationManager?.requestLocationUpdates(
                        provider,
                        LOCATION_UPDATE_INTERVAL,
                        LOCATION_UPDATE_MIN_DISTANCE,
                        locationListener
                    )
                    Log.i(TAG, "Location updates requested from $provider")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Permission denied for $provider", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to request updates from $provider", e)
                }
            }

            // Try to get last known location for immediate check
            try {
                val lastKnown = providers.mapNotNull { p ->
                    locationManager?.getLastKnownLocation(p)
                }.maxByOrNull { it.time }

                if (lastKnown != null) {
                    Log.i(TAG, "Got last known location: ${lastKnown.latitude}, ${lastKnown.longitude}")
                    checkAlarms(lastKnown)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Get last known location failed", e)
            }

            isLocationMonitoring = true
            Log.i(TAG, "Location monitoring started with ${providers.size} providers")

        } catch (e: Exception) {
            Log.e(TAG, "Location monitoring init failed", e)
            stopSelfSafely()
        }
    }

    /**
     * Check if current position is within any alarm's trigger range.
     * Supports multiple alarms and repeat reminders.
     */
    private fun checkAlarms(currentLocation: Location) {
        serviceScope.launch {
            try {
                val app = application as LocationAlarmApp
                val repository = app.repository

                val enabledAlarms = repository.getEnabledAlarms()
                var activeCount = 0

                for (alarm in enabledAlarms) {
                    val results = FloatArray(1)
                    Location.distanceBetween(
                        currentLocation.latitude, currentLocation.longitude,
                        alarm.latitude, alarm.longitude,
                        results
                    )
                    val distance = results[0]

                    if (distance <= alarm.radius) {
                        activeCount++

                        if (alarm.repeatInterval > 0) {
                            val now = System.currentTimeMillis()
                            if (!alarm.triggered) {
                                triggerAlarm(alarm, distance, currentLocation)
                            } else if (now - alarm.lastTriggeredAt >= alarm.repeatInterval) {
                                triggerAlarm(alarm, distance, currentLocation)
                            }
                        } else {
                            if (!alarm.triggered) {
                                triggerAlarm(alarm, distance, currentLocation)
                            }
                        }
                    } else {
                        if (alarm.triggered) {
                            repository.setTriggered(alarm.id, false)
                            notificationHelper.cancelAlarmNotification(alarm.id)
                        }
                    }
                }

                updateServiceNotification(activeCount)
            } catch (e: Exception) {
                Log.e(TAG, "Check alarms failed", e)
            }
        }
    }

    private suspend fun triggerAlarm(alarm: Alarm, distance: Float, location: Location) {
        val app = application as LocationAlarmApp
        val repository = app.repository

        val now = System.currentTimeMillis()

        if (alarm.repeatInterval > 0) {
            repository.setTriggeredAndTime(alarm.id, true, now)
        } else {
            repository.setTriggered(alarm.id, true)
        }

        notificationHelper.showAlarmNotification(
            alarmName = alarm.name,
            reminder = alarm.reminder,
            address = alarm.address,
            alarmId = alarm.id
        )

        repository.insertHistory(
            AlarmHistory(
                alarmId = alarm.id,
                alarmName = alarm.name,
                reminder = alarm.reminder,
                address = alarm.address,
                latitude = location.latitude,
                longitude = location.longitude,
                distance = distance
            )
        )

        Log.i(TAG, "Alarm triggered: ${alarm.name}, distance ${distance.toInt()}m, repeat=${alarm.repeatInterval}ms")
    }

    private fun updateServiceNotification(activeCount: Int) {
        try {
            val notification = notificationHelper.buildServiceNotification(activeCount)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Update service notification failed", e)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun stopSelfSafely() {
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Remove location updates failed", e)
        }
        locationManager = null
        isLocationMonitoring = false
        isRunning.value = false
        serviceScope.cancel()
        releaseWakeLock()
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "Stop foreground failed", e)
        }
        stopSelf()
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Release WakeLock failed", e)
        }
    }

    override fun onDestroy() {
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy remove updates failed", e)
        }
        locationManager = null
        isLocationMonitoring = false
        isRunning.value = false
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
