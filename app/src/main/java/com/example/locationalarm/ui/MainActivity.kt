package com.example.locationalarm.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.locationalarm.LocationAlarmApp
import com.example.locationalarm.databinding.ActivityMainBinding
import com.example.locationalarm.service.LocationAlarmService
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: AlarmViewModel
    private lateinit var adapter: AlarmAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineLocationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val notificationGranted = results[Manifest.permission.POST_NOTIFICATIONS] ?: true

        if (fineLocationGranted) {
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
    ) { _ ->
        startLocationService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViewModel()
        setupRecyclerView()
        setupClickListeners()
        checkAndRequestPermissions()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    true
                }
                else -> false
            }
        }
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
                Snackbar.make(binding.root, "已删除：${alarm.name}", Snackbar.LENGTH_SHORT)
                    .setAction("撤销") { viewModel.insertAlarm(alarm) }
                    .show()
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.fabAddAlarm.setOnClickListener {
            startActivity(Intent(this, AddEditAlarmActivity::class.java))
        }
    }

    // ---- Permissions ----

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Start service failed", e)
        }

        requestBatteryOptimizationExemption()
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName)

            if (!isIgnoring) {
                AlertDialog.Builder(this)
                    .setTitle("需要关闭电池优化")
                    .setMessage(
                        "为了确保位置闹钟在后台持续运行，需要将此应用加入" +
                        "电池优化白名单。\n\n" +
                        "如果跳过此步骤，系统可能在后台杀死定位服务，" +
                        "导致无法准时提醒。\n\n" +
                        "点击「确定」前往设置页面，选择「不优化」。"
                    )
                    .setPositiveButton("确定") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                startActivity(intent)
                            } catch (e2: Exception) {
                                Toast.makeText(this, "请手动在系统设置中关闭电池优化", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .setNegativeButton("稍后") { _, _ ->
                        Toast.makeText(this, "未关闭电池优化可能导致后台服务被杀", Toast.LENGTH_LONG).show()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun showPermissionDeniedMessage() {
        binding.tvEmpty.text = "缺少定位权限，请在设置中开启后才能使用位置闹钟功能"
        binding.tvEmpty.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        try {
            if (!LocationAlarmService.isRunning.value) {
                viewModel.allAlarms.value?.let { alarms ->
                    if (alarms.any { it.enabled }) {
                        startLocationService()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Restart service failed", e)
        }
    }
}
