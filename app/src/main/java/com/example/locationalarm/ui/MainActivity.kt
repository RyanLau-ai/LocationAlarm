package com.example.locationalarm.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.locationalarm.R
import com.example.locationalarm.data.AlertWay
import com.example.locationalarm.data.GeoFence
import com.example.locationalarm.data.GeoFenceManager
import com.example.locationalarm.databinding.ActivityMainBinding
import com.example.locationalarm.databinding.ItemFenceBinding
import com.example.locationalarm.service.GeofenceForegroundService
import com.example.locationalarm.util.PermissionHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * 主界面：围栏列表 + 轨迹记录开关
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fenceManager: GeoFenceManager
    private lateinit var adapter: FenceAdapter
    private var trackSwitch: MaterialSwitch? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fenceManager = GeoFenceManager(this)
        adapter = FenceAdapter { fence ->
            // 点击条目进入编辑
            val intent = Intent(this, EditFenceActivity::class.java)
            intent.putExtra(EditFenceActivity.EXTRA_FENCE_ID, fence.id)
            startActivity(intent)
        }

        binding.rvFences.layoutManager = LinearLayoutManager(this)
        binding.rvFences.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, EditFenceActivity::class.java))
        }

        setupToolbar()
        checkPermissions()
    }

    @SuppressLint("SetTextI18n")
    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.menu_main)
        val menu = binding.toolbar.menu
        val trackItem = menu.findItem(R.id.action_track)
        val actionView = trackItem.actionView

        if (actionView is MaterialSwitch) {
            trackSwitch = actionView
            // 从 SharedPreferences 读取轨迹开关状态
            val sp = getSharedPreferences("settings", MODE_PRIVATE)
            val enabled = sp.getBoolean("track_record", false)
            actionView.isChecked = enabled
            actionView.text = getString(R.string.switch_track)
            actionView.setOnCheckedChangeListener { _, checked ->
                sp.edit().putBoolean("track_record", checked).apply()
                // 通知所有围栏更新轨迹记录状态
                updateAllTrackFlags(checked)
                if (checked) {
                    // 开启轨迹记录时确保服务运行
                    GeofenceForegroundService.start(this)
                }
            }
        }
    }

    /** 全局轨迹开关：更新所有围栏的 trackRecord 字段 */
    private fun updateAllTrackFlags(enabled: Boolean) {
        val fences = fenceManager.getAll()
        if (fences.isEmpty()) return
        val updated = fences.map { it.copy(trackRecord = enabled) }
        fenceManager.saveAll(updated)
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val fences = fenceManager.getAll()
        adapter.submitList(fences)
        binding.emptyView.visibility = if (fences.isEmpty()) View.VISIBLE else View.GONE
        // 更新服务通知中的围栏数
        GeofenceForegroundService.start(this)
    }

    /**
     * 权限引导流程：
     * 1. 前台定位 + 通知（一次请求）
     * 2. 后台定位（Android 10+ 单独引导）
     * 3. 电池优化白名单引导
     */
    private fun checkPermissions() {
        val needed = PermissionHelper.neededPermissions(this)
        if (needed.isNotEmpty()) {
            // 首次请求：前台定位 + 通知
            val firstBatch = needed.filter {
                it == android.Manifest.permission.ACCESS_FINE_LOCATION ||
                    it == android.Manifest.permission.ACCESS_COARSE_LOCATION ||
                    it == android.Manifest.permission.POST_NOTIFICATIONS
            }
            if (firstBatch.isNotEmpty()) {
                showRationaleDialog(getString(R.string.permission_location_title),
                    getString(R.string.permission_location_rationale)) {
                    PermissionHelper.requestPermissions(this, firstBatch)
                }
            }
        } else {
            // 所有权限已就绪，检查电池优化
            checkBatteryOptimization()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.REQ_PERMISSIONS) {
            // 前台权限已请求，检查是否还需要后台定位
            if (!PermissionHelper.hasBackgroundLocation(this)) {
                showRationaleDialog(
                    getString(R.string.permission_background_title),
                    getString(R.string.permission_background_rationale)
                ) {
                    PermissionHelper.requestPermissions(
                        this,
                        listOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    )
                }
            } else {
                checkBatteryOptimization()
            }
        }
    }

    /**
     * 电池优化白名单引导
     */
    private fun checkBatteryOptimization() {
        if (PermissionHelper.isIgnoringBatteryOptimizations(this)) return
        if (getSharedPreferences("settings", MODE_PRIVATE)
                .getBoolean("battery_opt_skipped", false)
        ) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_battery_title)
            .setMessage(R.string.permission_battery_rationale)
            .setPositiveButton(R.string.btn_grant) { _, _ ->
                PermissionHelper.requestIgnoreBatteryOptimizations(this)
            }
            .setNegativeButton(R.string.btn_skip) { _, _ ->
                getSharedPreferences("settings", MODE_PRIVATE)
                    .edit().putBoolean("battery_opt_skipped", true).apply()
            }
            .setCancelable(false)
            .show()
    }

    private fun showRationaleDialog(title: String, message: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.btn_grant) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.btn_skip, null)
            .show()
    }
}
