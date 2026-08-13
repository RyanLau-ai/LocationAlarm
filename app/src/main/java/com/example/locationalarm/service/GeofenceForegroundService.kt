package com.example.locationalarm.service

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.amap.api.fence.GeoFence
import com.amap.api.fence.GeoFenceClient
import com.amap.api.fence.GeoFenceListener
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.location.DPoint
import com.example.locationalarm.R
import com.example.locationalarm.data.AlertWay
import com.example.locationalarm.data.GeoFenceManager
import com.example.locationalarm.util.NotificationHelper

/**
 * 地理围栏前台服务：常驻内存，监控进入/离开事件
 *
 * - START_STICKY：被系统杀死后自动重启
 * - foregroundServiceType=location：Android 14+ 要求
 * - 围栏触发事件通过广播接收（GeoFenceClient.createPendingIntent + BroadcastReceiver）
 * - 无围栏时仅保持常驻通知，有围栏时注册高德 GeoFenceClient
 */
class GeofenceForegroundService : Service(), GeoFenceListener, AMapLocationListener {

    companion object {
        private const val TAG = "GeofenceService"

        /** 自定义围栏 action（区分进入/离开：后缀 _in / _out） */
        private const val GEOFENCE_BROADCAST_ACTION =
            "com.example.locationalarm.action.GEOFENCE_BROADCAST"

        fun start(context: Context) {
            val intent = Intent(context, GeofenceForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GeofenceForegroundService::class.java))
        }
    }

    private var geoFenceClient: GeoFenceClient? = null
    private var locationClient: AMapLocationClient? = null
    private val fenceManager by lazy { GeoFenceManager(this) }

    /** 围栏触发广播接收器 */
    private val geoFenceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == GEOFENCE_BROADCAST_ACTION) {
                handleGeoFenceBroadcast(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        startForegroundWithNotification()
        registerGeoFenceReceiver()
        initLocation()
        setupFences()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        // 每次启动都同步一次围栏（支持添加/删除后热更新）
        setupFences()
        return START_STICKY
    }

    /**
     * 启动前台服务并显示常驻通知
     */
    private fun startForegroundWithNotification() {
        val count = fenceManager.getAll().size
        val notification = NotificationHelper.buildServiceNotification(this, count)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationHelper.NOTIFY_ID_GEO,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NotificationHelper.NOTIFY_ID_GEO, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
            try {
                startForeground(NotificationHelper.NOTIFY_ID_GEO, notification)
            } catch (e2: Exception) {
                Log.e(TAG, "fallback startForeground failed: ${e2.message}")
            }
        }
    }

    /**
     * 注册围栏触发广播接收器（动态注册，服务内有效）
     */
    private fun registerGeoFenceReceiver() {
        try {
            val filter = IntentFilter(GEOFENCE_BROADCAST_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(geoFenceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(geoFenceReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerGeoFenceReceiver error: ${e.message}")
        }
    }

    /**
     * 初始化定位客户端（备用定位，用于轨迹记录）
     */
    private fun initLocation() {
        if (!hasLocationPermission()) return
        try {
            locationClient = AMapLocationClient(this).apply {
                val option = AMapLocationClientOption().apply {
                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                    isOnceLocation = false
                    interval = 5000
                    isNeedAddress = true
                }
                setLocationOption(option)
                setLocationListener(this@GeofenceForegroundService)
                startLocation()
            }
        } catch (e: Exception) {
            Log.e(TAG, "initLocation failed: ${e.message}")
        }
    }

    /**
     * 注册/更新全部围栏到高德 GeoFenceClient
     */
    private fun setupFences() {
        val fences = fenceManager.getAll()
        Log.d(TAG, "setupFences: ${fences.size} fences")

        // 更新常驻通知计数
        val notification = NotificationHelper.buildServiceNotification(this, fences.size)
        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .notify(NotificationHelper.NOTIFY_ID_GEO, notification)

        if (fences.isEmpty()) {
            geoFenceClient?.removeGeoFence()
            return
        }
        if (!hasLocationPermission()) {
            Log.w(TAG, "no location permission, skip fence setup")
            return
        }

        try {
            if (geoFenceClient == null) {
                geoFenceClient = GeoFenceClient(this).apply {
                    setGeoFenceListener(this@GeofenceForegroundService)
                    // 进入 + 离开 两种触发行为
                    setActivateAction(GeoFenceClient.GEOFENCE_IN or GeoFenceClient.GEOFENCE_OUT)
                    // 广播触发
                    createPendingIntent(GEOFENCE_BROADCAST_ACTION)
                }
            }

            // 先清空旧围栏再注册（保证与本地数据一致）
            geoFenceClient?.removeGeoFence()

            fences.forEach { fence ->
                val center = DPoint(fence.latitude, fence.longitude)
                val radius = fence.radius.toFloat()

                // 进入提醒（customId 后缀 _in）
                if (fence.enableIn) {
                    geoFenceClient?.addGeoFence(center, radius, "${fence.id}_in")
                    Log.d(TAG, "addGeoFence IN: ${fence.id}")
                }
                // 离开提醒（customId 后缀 _out）
                if (fence.enableOut) {
                    geoFenceClient?.addGeoFence(center, radius, "${fence.id}_out")
                    Log.d(TAG, "addGeoFence OUT: ${fence.id}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "setupFences error: ${e.message}")
        }
    }

    /**
     * 高德围栏创建回调
     */
    override fun onGeoFenceCreateFinished(geoFenceList: List<GeoFence>?, errorCode: Int, customId: String?) {
        Log.d(TAG, "onGeoFenceCreateFinished code=$errorCode customId=$customId count=${geoFenceList?.size}")
    }

    /**
     * 解析围栏触发广播
     */
    private fun handleGeoFenceBroadcast(intent: Intent) {
        val extras: Bundle? = intent.extras
        if (extras == null) {
            Log.w(TAG, "geo fence broadcast with empty extras")
            return
        }

        // 围栏状态：进入 / 离开
        val status = extras.getInt(GeoFence.BUNDLE_KEY_FENCESTATUS, -1)
        // 业务 ID（我们用它承载 fenceId + in/out 标记）
        val customId = extras.getString(GeoFence.BUNDLE_KEY_CUSTOMID) ?: ""
        // 触发时的位置（高德广播中位置信息通过 "location" key 携带 AMapLocation）
        @Suppress("DEPRECATION")
        val location = extras.getParcelable<AMapLocation>("location")

        val fenceId = customId.removeSuffix("_in").removeSuffix("_out")
        val isIn = customId.endsWith("_in")

        val fence = fenceManager.getById(fenceId)
        if (fence == null) {
            Log.w(TAG, "fence not found: $fenceId")
            return
        }

        when (status) {
            GeoFence.STATUS_IN -> {
                if (isIn && fence.enableIn) {
                    Log.i(TAG, "TRIGGERED IN: ${fence.name}")
                    NotificationHelper.showFenceAlert(
                        this, fence.name, fence.alertContent, isIn = true, fence.alertWay
                    )
                    recordTrackIfNeeded(fenceId, location)
                }
            }
            GeoFence.STATUS_OUT -> {
                if (!isIn && fence.enableOut) {
                    Log.i(TAG, "TRIGGERED OUT: ${fence.name}")
                    NotificationHelper.showFenceAlert(
                        this, fence.name, fence.alertContent, isIn = false, fence.alertWay
                    )
                    recordTrackIfNeeded(fenceId, location)
                }
            }
            GeoFence.STATUS_LOCFAIL -> {
                Log.w(TAG, "geo fence location failed")
            }
            else -> {
                Log.d(TAG, "geo fence status=$status customId=$customId")
            }
        }
    }

    /**
     * 轨迹记录（围栏开关开启时记录经过点）
     */
    private fun recordTrackIfNeeded(fenceId: String, location: AMapLocation?) {
        val fence = fenceManager.getById(fenceId) ?: return
        if (!fence.trackRecord) return
        if (location == null) return
        fenceManager.appendTrackPoint(
            fenceId, location.latitude, location.longitude, System.currentTimeMillis()
        )
        Log.d(TAG, "track point recorded for $fenceId")
    }

    /**
     * 定位监听（备用定位）
     */
    override fun onLocationChanged(location: AMapLocation?) {
        // 空闲定位回调，轨迹由围栏触发时记录
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        try {
            unregisterReceiver(geoFenceReceiver)
        } catch (_: Exception) {
        }
        try {
            geoFenceClient?.removeGeoFence()
            locationClient?.stopLocation()
            locationClient?.onDestroy()
        } catch (e: Exception) {
            Log.e(TAG, "cleanup error: ${e.message}")
        }
    }
}
