package com.example.locationalarm.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.ServiceCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.example.locationalarm.LocationAlarmApp
import com.example.locationalarm.data.Alarm
import com.example.locationalarm.data.AlarmHistory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 位置闹钟前台服务（高德定位版）
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

        // 定位间隔（毫秒）：30 秒
        private const val LOCATION_UPDATE_INTERVAL = 30_000L

        /**
         * 服务是否正在运行的全局标志，供 UI 层检查
         */
        val isRunning = MutableStateFlow(false)
    }

    private var locationClient: AMapLocationClient? = null
    private lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
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
     * 启动前台服务并开始高德定位监听
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

        try {
            // 初始化高德定位客户端
            locationClient = AMapLocationClient(applicationContext)

            // 配置定位参数
            val option = AMapLocationClientOption().apply {
                // 定位模式：高精度（GPS + 网络）
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                // 定位间隔：30 秒
                interval = LOCATION_UPDATE_INTERVAL
                // 返回地址描述
                isNeedAddress = true
                // 缓存定位
                isLocationCacheEnable = true
                // 单次定位设为 false（持续定位）
                isOnceLocation = false
            }

            locationClient?.setLocationOption(option)
            locationClient?.setLocationListener { aMapLocation ->
                if (aMapLocation != null && aMapLocation.errorCode == 0) {
                    // 定位成功，转换为标准 Location 对象
                    val location = Location("amap").apply {
                        latitude = aMapLocation.latitude
                        longitude = aMapLocation.longitude
                        accuracy = aMapLocation.accuracy
                        time = aMapLocation.time
                    }
                    checkAlarms(location)
                } else {
                    Log.w(TAG, "高德定位失败: errorCode=${aMapLocation?.errorCode}, errorInfo=${aMapLocation?.errorInfo}")
                }
            }

            // 启动定位
            locationClient?.startLocation()
            Log.i(TAG, "高德位置监听已启动")

        } catch (e: Exception) {
            Log.e(TAG, "高德定位初始化失败", e)
            stopSelfSafely()
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
                        // 之前已触发，现在离开了范围 → 重置触发状态
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
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun stopSelfSafely() {
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        locationClient = null
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
