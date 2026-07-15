package com.example.locationalarm.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.locationalarm.LocationAlarmApp
import com.example.locationalarm.databinding.ActivityMainBinding
import com.example.locationalarm.service.LocationAlarmService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: AlarmViewModel
    private lateinit var adapter: AlarmAdapter

    /**
     * 权限请求 launcher
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineLocationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val notificationGranted = results[Manifest.permission.POST_NOTIFICATIONS] ?: true

        if (fineLocationGranted) {
            // 定位权限已获取，请求后台定位权限 (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundLocationPermission()
            } else {
                startLocationService()
            }
        } else {
            showPermissionDeniedMessage()
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // 无论是否授予后台定位权限，都尝试启动服务
        startLocationService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupRecyclerView()
        setupClickListeners()
        checkAndRequestPermissions()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[AlarmViewModel::class.java]

        viewModel.allAlarms.observe(this) { alarms ->
            adapter.submitList(alarms)
            binding.tvEmpty.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setupRecyclerView() {
        adapter = AlarmAdapter(
            onToggle = { alarm ->
                viewModel.setAlarmEnabled(alarm.id, alarm.enabled)
                // 如果有启用的闹钟，确保服务在运行
                if (alarm.enabled) startLocationService()
            },
            onEdit = { alarm ->
                val intent = Intent(this, AddEditAlarmActivity::class.java).apply {
                    putExtra(AddEditAlarmActivity.EXTRA_ALARM_ID, alarm.id)
                }
                startActivity(intent)
            },
            onDelete = { alarm ->
                viewModel.deleteAlarm(alarm)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.fabAddAlarm.setOnClickListener {
            startActivity(Intent(this, AddEditAlarmActivity::class.java))
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    // ---- 权限处理 ----

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        // 定位权限
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // 通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            // 已有定位权限，检查后台定位
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestBackgroundLocationPermission()
            } else {
                startLocationService()
            }
        }
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun startLocationService() {
        val serviceIntent = Intent(this, LocationAlarmService::class.java).apply {
            action = LocationAlarmService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun showPermissionDeniedMessage() {
        binding.tvEmpty.text = "缺少定位权限，请在设置中开启后才能使用位置闹钟功能"
        binding.tvEmpty.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        // 恢复时如果服务未运行且有启用的闹钟，重新启动服务
        if (!LocationAlarmService.isRunning.value) {
            viewModel.allAlarms.value?.let { alarms ->
                if (alarms.any { it.enabled }) {
                    startLocationService()
                }
            }
        }
    }
}
