package com.example.locationalarm.service

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.ServiceCompat
import com.example.locationalarm.LocationAlarmApp
import com.example.locationalarm.data.Alarm
import com.example.locationalarm.data.AlarmHistory
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 位置闹钟前台服务
 *
 * 职责：
 * 1. 作为前台服务持续运行，保持定位活跃
 * 2. 定期获取当前位置，与所有启用的闹钟比较距离
 * 3. 进入范围 → 触发通知 + 记录历史
 * 4. 离开范围 → 重置闹钟，允许再次触发
 *
 * 厂商适配注意：
 * - 小米/红米：需在「自启动管理」中允许应用自启动
 * - 华为/荣耀：需在「电池优化」中设为不优化，并在「应用启动管理」中允许后台活动
 * - OPPO/vivo：需在「自启动管理」中允许，并开启「后台冻结/电池优化」白名单
 * - 三星：需在「电池」设置中关闭「自适应电池」对该应用的限制
 */
class LocationAlarmService : Service() {

    companion object {
        private const val TAG = "LocationAlarmService"

        const val ACTION_START = "com.example.locationalarm.START_SERVICE"
        const val ACTION_STOP = "com.example.locationalarm.STOP_SERVICE"

        // 定位更新间隔（毫秒）
        // 使用 30 秒平衡电量与响应速度；可通过 Geofence 优化，但此处用持续定位更可靠
        private const val LOCATION_UPDATE_INTERVAL = 30_000L
        private const val LOCATION_UPDATE_FASTEST_INTERVAL = 15_000L

        /**
         * 服务是否正在运行的全局标志，供 UI 层检查
         */
        val isRunning = MutableStateFlow(false)
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationHelper = NotificationHelper(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    checkAlarms(location)
                }
            }
        }
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

    /**
     * 启动前台服务并开始定位监听
     */
    private fun startLocationMonitoring() {
        // 启动前台服务通知
        val notification = notificationHelper.buildServiceNotification(0)
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        isRunning.value = true

        // 检查权限
        if (!hasLocationPermission()) {
            Log.w(TAG, "缺少定位权限，无法启动位置监听")
            notificationHelper.showPermissionNotification("缺少定位权限，请在设置中开启")
            stopSelfSafely()
            return
        }

        // 构建定位请求
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(LOCATION_UPDATE_FASTEST_INTERVAL)
            setWaitForAccurateLocation(false)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.i(TAG, "位置监听已启动")
        } catch (e: SecurityException) {
            Log.e(TAG, "定位权限被拒绝", e)
            stopSelfSafely()
        }

        // 同时立即请求一次位置
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    location?.let { checkAlarms(it) }
                }
        } catch (e: SecurityException) {
            // ignore
        }
    }

    /**
     * 检查当前位置是否在任一闹钟的触发范围内
     */
    private fun checkAlarms(currentLocation: Location) {
        serviceScope.launch {
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
                    if (!alarm.triggered) {
                        // 进入范围且尚未触发 → 触发闹钟
                        triggerAlarm(alarm, distance, currentLocation)
                    }
                } else {
                    // 不在范围内
                    if (alarm.triggered) {
                        // 之前已触发，现在离开了范围 → 重置触发状态，允许再次触发
                        repository.setTriggered(alarm.id, false)
                        notificationHelper.cancelAlarmNotification(alarm.id)
                    }
                }
            }

            // 更新前台通知显示的活跃闹钟数
            updateServiceNotification(activeCount)
        }
    }

    /**
     * 触发闹钟：发送通知 + 记录历史
     */
    private suspend fun triggerAlarm(alarm: Alarm, distance: Float, location: Location) {
        val app = application as LocationAlarmApp
        val repository = app.repository

        // 标记为已触发
        repository.setTriggered(alarm.id, true)

        // 发送通知
        notificationHelper.showAlarmNotification(
            alarmName = alarm.name,
            reminder = alarm.reminder,
            address = alarm.address,
            alarmId = alarm.id
        )

        // 记录历史
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

        Log.i(TAG, "闹钟触发: ${alarm.name}, 距离目标 ${distance.toInt()} 米")
    }

    /**
     * 更新前台服务通知
     */
    private fun updateServiceNotification(activeCount: Int) {
        val notification = notificationHelper.buildServiceNotification(activeCount)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun stopSelfSafely() {
        if (this::fusedLocationClient.isInitialized && this::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        isRunning.value = false
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSelfSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
