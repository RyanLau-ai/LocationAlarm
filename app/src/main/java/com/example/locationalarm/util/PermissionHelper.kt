package com.example.locationalarm.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 权限辅助类：定位（始终允许）、通知、后台定位、电池优化白名单
 */
object PermissionHelper {

    /** 运行时权限请求码 */
    const val REQ_PERMISSIONS = 1001

    /**
     * 需要请求的权限列表（按系统版本递进）
     */
    fun neededPermissions(context: Context): List<String> {
        val list = mutableListOf<String>()

        // 前台定位
        if (!hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION)
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // 通知（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 后台定位（Android 10+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            list.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        return list.distinct()
    }

    fun hasPermission(context: Context, perm: String): Boolean {
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun hasFineLocation(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBackgroundLocation(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)

    /** 是否已忽略电池优化 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 请求忽略电池优化（跳系统设置） */
    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
        } catch (e: Exception) {
            // 部分系统不支持该 action，直接跳应用详情页
            try {
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}")))
            } catch (e2: Exception) {
                // 忽略
            }
        }
    }

    /** 打开应用详情页（引导用户手动开启自启动/白名单） */
    fun openAppDetails(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"))
            )
        } catch (_: Exception) {
        }
    }

    /** 主界面权限是否完备（决定是否引导弹窗） */
    fun isAllGranted(context: Context): Boolean {
        return hasFineLocation(context) &&
            hasNotificationPermission(context) &&
            hasBackgroundLocation(context)
    }

    /** 请求权限（兼容各版本） */
    fun requestPermissions(activity: Activity, perms: List<String>) {
        ActivityCompat.requestPermissions(
            activity,
            perms.toTypedArray(),
            REQ_PERMISSIONS
        )
    }
}
