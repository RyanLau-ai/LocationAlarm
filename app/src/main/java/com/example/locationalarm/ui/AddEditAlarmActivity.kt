package com.example.locationalarm.ui

import android.location.Geocoder
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.locationalarm.LocationAlarmApp
import com.example.locationalarm.data.Alarm
import com.example.locationalarm.databinding.ActivityAddEditAlarmBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddEditAlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditAlarmBinding
    private var editingAlarmId: Long = -1L

    // 已解析的位置信息
    private var selectedLatitude: Double = 0.0
    private var selectedLongitude: Double = 0.0
    private var selectedAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        editingAlarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)

        setupClickListeners()

        if (editingAlarmId != -1L) {
            title = "编辑闹钟"
            loadAlarmForEditing()
        } else {
            title = "添加闹钟"
        }
    }

    private fun setupClickListeners() {
        binding.btnSearchAddress.setOnClickListener {
            val query = binding.etAddress.text.toString().trim()
            if (query.isNotEmpty()) {
                searchAddress(query)
            }
        }

        binding.btnSave.setOnClickListener {
            saveAlarm()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    /**
     * 使用 Geocoder 将地址文本解析为经纬度坐标
     */
    private fun searchAddress(query: String) {
        binding.btnSearchAddress.isEnabled = false
        binding.tvSearchResult.text = "正在搜索..."

        lifecycleScope.launch {
            try {
                val geocoder = Geocoder(this@AddEditAlarmActivity)
                val results = withContext(Dispatchers.IO) {
                    // Geocoder.getFromLocationName 在 Android 13+ 使用新 API
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, 5)
                }

                if (results.isNullOrEmpty()) {
                    binding.tvSearchResult.text = "未找到该地址，请尝试更详细的地址"
                    binding.btnSearchAddress.isEnabled = true
                    return@launch
                }

                val firstResult = results[0]
                selectedLatitude = firstResult.latitude
                selectedLongitude = firstResult.longitude
                selectedAddress = firstResult.getAddressLine(0) ?: query

                binding.tvSearchResult.text = buildString {
                    append("✓ 已找到位置\n")
                    append(selectedAddress)
                    append("\n经度: ${"%.6f".format(selectedLatitude)}")
                    append("  纬度: ${"%.6f".format(selectedLongitude)}")
                }
            } catch (e: Exception) {
                binding.tvSearchResult.text = "搜索失败: ${e.message}"
            } finally {
                binding.btnSearchAddress.isEnabled = true
            }
        }
    }

    private fun loadAlarmForEditing() {
        lifecycleScope.launch {
            val alarm = (application as LocationAlarmApp).repository.getAlarmById(editingAlarmId)
            alarm?.let {
                binding.etName.setText(it.name)
                binding.etReminder.setText(it.reminder)
                binding.etAddress.setText(it.address)
                binding.etRadius.setText(it.radius.toString())
                binding.etTag.setText(it.tag)
                selectedLatitude = it.latitude
                selectedLongitude = it.longitude
                selectedAddress = it.address
                binding.tvSearchResult.text = "当前位置: ${it.address}\n经度: ${"%.6f".format(it.latitude)}  纬度: ${"%.6f".format(it.longitude)}"
            }
        }
    }

    private fun saveAlarm() {
        val name = binding.etName.text.toString().trim()
        val reminder = binding.etReminder.text.toString().trim()
        val radiusStr = binding.etRadius.text.toString().trim()
        val tag = binding.etTag.text.toString().trim()

        // 输入校验
        if (name.isEmpty()) {
            binding.etName.error = "请输入闹钟名称"
            return
        }
        if (reminder.isEmpty()) {
            binding.etReminder.error = "请输入提醒内容"
            return
        }
        if (selectedLatitude == 0.0 && selectedLongitude == 0.0) {
            binding.tvSearchResult.text = "请先搜索并选择一个位置"
            return
        }
        val radius = radiusStr.toIntOrNull()
        if (radius == null || radius <= 0) {
            binding.etRadius.error = "请输入有效的范围（米）"
            return
        }

        val alarm = Alarm(
            id = if (editingAlarmId != -1L) editingAlarmId else 0,
            name = name,
            reminder = reminder,
            latitude = selectedLatitude,
            longitude = selectedLongitude,
            address = selectedAddress,
            radius = radius,
            tag = tag
        )

        lifecycleScope.launch {
            val app = application as LocationAlarmApp
            if (editingAlarmId != -1L) {
                app.repository.update(alarm)
            } else {
                app.repository.insert(alarm)
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
    }
}
