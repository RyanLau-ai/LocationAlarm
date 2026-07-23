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
 * 5. 支持重复提醒：设置 repeatInterval > 0 时，在范围内按间隔重复触发
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
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 防止重复初始化定位客户端 */
    private var isLocationMonitoring = false

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
        // START_STICKY: 服务被杀后系统会尝试重建
        return START_STICKY
    }

    /**
     * 用户从最近任务列表划掉 app 时调用
     * 重新启动服务，保持后台运行
     * 使用 setExactAndAllowWhileIdle 确保在 Doze 模式下也能唤醒
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "onTaskRemoved - 服务可能被系统回收，尝试重启")
        val restartIntent = Intent(applicationContext, LocationAlarmService::class.java).apply {
            action = ACTION_START
        }
        val pendingIntent = android.app.PendingIntent.getService(
            this, 1, restartIntent,
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        // 1 秒后重启服务，使用 setExactAndAllowWhileIdle 以在 Doze 下也能触发
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
        } catch (e: Exception) {
            Log.e(TAG, "onTaskRemoved 重启定时器失败", e)
        }
        super.onTaskRemoved(rootIntent)
    }

    /**
     * 获取 WakeLock 防止 CPU 休眠
     * 延长到 12 小时，并在到期前续期
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = powerManager.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "LocationAlarm::ServiceWakeLock"
            )
            // 12 小时后自动释放（服务运行期间会持续持有）
            wakeLock?.acquire(12 * 60 * 60 * 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "获取 WakeLock 失败", e)
        }
    }

    /**
     * 启动前台服务并开始高德定位监听
     * 防重复初始化：如果已经在监听则直接返回
     */
    @Synchronized
    private fun startLocationMonitoring() {
        // 启动前台服务通知
        val notification = notificationHelper.buildServiceNotification(0)
        try {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID_SERVICE,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (e: Exception) {
            Log.e(TAG, "启动前台服务失败", e)
        }
        isRunning.value = true

        // 防止重复初始化
        if (isLocationMonitoring && locationClient != null) {
            Log.i(TAG, "定位监听已在运行，跳过重复初始化")
            return
        }

        // 检查权限
        if (!hasLocationPermission()) {
            Log.w(TAG, "缺少定位权限，无法启动位置监听")
            notificationHelper.showPermissionNotification("缺少定位权限，请在设置中开启")
            stopSelfSafely()
            return
        }

        try {
            // 先清理旧的客户端（如果存在）
            locationClient?.let {
                try {
                    it.stopLocation()
                    it.onDestroy()
                } catch (e: Exception) {
                    Log.w(TAG, "清理旧定位客户端失败", e)
                }
            }

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
            isLocationMonitoring = true
            Log.i(TAG, "高德位置监听已启动")

        } catch (e: Exception) {
            Log.e(TAG, "高德定位初始化失败", e)
            stopSelfSafely()
        }
    }

    /**
     * 检查当前位置是否在任一闹钟的触发范围内
     * 支持多闹钟同时监测，支持重复提醒
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

                    if (alarm.repeatInterval > 0) {
                        // 重复提醒模式
                        val now = System.currentTimeMillis()
                        if (!alarm.triggered) {
                            // 首次进入范围 → 触发
                            triggerAlarm(alarm, distance, currentLocation)
                        } else if (now - alarm.lastTriggeredAt >= alarm.repeatInterval) {
                            // 已在范围内且达到重复间隔 → 再次触发
                            triggerAlarm(alarm, distance, currentLocation)
                        }
                        // 未到间隔时间，不触发
                    } else {
                        // 单次提醒模式（原有逻辑）
                        if (!alarm.triggered) {
                            triggerAlarm(alarm, distance, currentLocation)
                        }
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

        val now = System.currentTimeMillis()

        // 标记为已触发，并记录触发时间
        if (alarm.repeatInterval > 0) {
            repository.setTriggeredAndTime(alarm.id, true, now)
        } else {
            repository.setTriggered(alarm.id, true)
        }

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

        Log.i(TAG, "闹钟触发: ${alarm.name}, 距离目标 ${distance.toInt()} 米, 重复间隔=${alarm.repeatInterval}ms")
    }

    /**
     * 更新前台服务通知
     */
    private fun updateServiceNotification(activeCount: Int) {
        try {
            val notification = notificationHelper.buildServiceNotification(activeCount)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
        } catch (e: Exception) {
            Log.w(TAG, "更新服务通知失败", e)
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
            locationClient?.stopLocation()
            locationClient?.onDestroy()
        } catch (e: Exception) {
            Log.e(TAG, "停止定位客户端失败", e)
        }
        locationClient = null
        isLocationMonitoring = false
        isRunning.value = false
        serviceScope.cancel()
        releaseWakeLock()
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "停止前台服务失败", e)
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
            Log.e(TAG, "释放 WakeLock 失败", e)
        }
    }

    override fun onDestroy() {
        try {
            locationClient?.stopLocation()
            locationClient?.onDestroy()
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy 清理定位客户端失败", e)
        }
        locationClient = null
        isLocationMonitoring = false
        isRunning.value = false
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
