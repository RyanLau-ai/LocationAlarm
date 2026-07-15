package com.example.locationalarm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.geocoder.GeocodeAddress
import com.amap.api.services.geocoder.GeocodeQuery
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeAddress
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.example.locationalarm.LocationAlarmApp
import com.example.locationalarm.data.Alarm
import com.example.locationalarm.databinding.ActivityAddEditAlarmBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddEditAlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditAlarmBinding
    private var editingAlarmId: Long = -1L

    // 高德地图
    private lateinit var aMap: AMap
    private lateinit var geocodeSearch: GeocodeSearch
    private var currentMarker: Marker? = null

    // 已选位置
    private var selectedLatitude: Double = 0.0
    private var selectedLongitude: Double = 0.0
    private var selectedAddress: String = ""

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            aMap.myLocation.isMyLocationEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        editingAlarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        title = if (editingAlarmId != -1L) "编辑闹钟" else "添加闹钟"

        initMap(savedInstanceState)
        initGeocodeSearch()
        setupClickListeners()

        if (editingAlarmId != -1L) {
            loadAlarmForEditing()
        }
    }

    // ---- 地图初始化 ----

    private fun initMap(savedInstanceState: Bundle?) {
        binding.mapView.onCreate(savedInstanceState)
        aMap = binding.mapView.map

        // 地图设置
        aMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isMyLocationButtonEnabled = false // 用自定义 FAB
            isScrollGesturesEnabled = true
            isZoomGesturesEnabled = true
        }

        // 默认视角：北京天安门
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(39.9087, 116.3975), 12f))

        // 请求定位权限
        if (hasLocationPermission()) {
            aMap.myLocation.isMyLocationEnabled = true
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // 地图点击选点
        aMap.setOnMapClickListener { latLng ->
            selectLocation(latLng)
        }
    }

    /**
     * 选中某个坐标：添加/移动标记 + 反向地理编码获取地址
     */
    private fun selectLocation(latLng: LatLng) {
        selectedLatitude = latLng.latitude
        selectedLongitude = latLng.longitude

        // 移动标记
        currentMarker?.remove()
        currentMarker = aMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("选中位置")
        )

        binding.tvSearchResult.text = "正在获取地址..."

        // 反向地理编码
        val query = RegeocodeQuery(
            LatLonPoint(latLng.latitude, latLng.longitude),
            200f, // 搜索半径（米）
            GeocodeSearch.AMAP
        )
        geocodeSearch.getFromLocationAsyn(query)
    }

    // ---- 地理编码初始化 ----

    private fun initGeocodeSearch() {
        geocodeSearch = GeocodeSearch(this)

        // 正向地理编码回调（地址 → 坐标）
        geocodeSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
            override fun onGeocodeResult(result: GeocodeResult?, rCode: Int) {
                val addressList = result?.geocodeAddressList
                if (addressList.isNullOrEmpty()) {
                    binding.tvSearchResult.text = "未找到该地址，请尝试更详细的关键词"
                    return
                }

                val first = addressList[0]
                val latLng = LatLng(first.latLonPoint.latitude, first.latLonPoint.longitude)
                selectedLatitude = latLng.latitude
                selectedLongitude = latLng.longitude
                selectedAddress = first.formatAddress ?: first.adcode

                // 移动地图到搜索结果
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))

                // 添加标记
                currentMarker?.remove()
                currentMarker = aMap.addMarker(
                    MarkerOptions().position(latLng).title(selectedAddress)
                )

                binding.tvSearchResult.text = buildString {
                    append("✓ ${selectedAddress}\n")
                    append("纬度: ${"%.6f".format(selectedLatitude)}")
                    append("  经度: ${"%.6f".format(selectedLongitude)}")
                }
            }

            override fun onRegeocodeResult(result: RegeocodeResult?, rCode: Int) {
                val addr: RegeocodeAddress? = result?.regeocodeAddress
                if (addr == null) {
                    selectedAddress = "纬度 ${"%.6f".format(selectedLatitude)}, 经度 ${"%.6f".format(selectedLongitude)}"
                } else {
                    selectedAddress = addr.formatAddress ?: addr.description ?: "未知地址"
                }

                binding.tvSearchResult.text = buildString {
                    append("✓ ${selectedAddress}\n")
                    append("纬度: ${"%.6f".format(selectedLatitude)}")
                    append("  经度: ${"%.6f".format(selectedLongitude)}")
                }
            }
        })
    }

    // ---- 交互 ----

    private fun setupClickListeners() {
        binding.btnSearchAddress.setOnClickListener {
            val query = binding.etAddress.text.toString().trim()
            if (query.isNotEmpty()) {
                searchAddress(query)
            }
        }

        binding.fabMyLocation.setOnClickListener {
            // 回到我的位置 — 使用高德定位
            aMap.myLocation.isMyLocationEnabled = true
            // 移动到定位点
            val myLoc = aMap.myLocation.location
            if (myLoc != null) {
                aMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(myLoc.latitude, myLoc.longitude), 16f
                    )
                )
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
     * 正向地理编码搜索：地址文本 → 坐标
     */
    private fun searchAddress(query: String) {
        binding.btnSearchAddress.isEnabled = false
        binding.tvSearchResult.text = "正在搜索..."

        val geocodeQuery = GeocodeQuery(query, "")
        geocodeSearch.getFromLocationNameAsyn(geocodeQuery)

        // 恢复按钮（回调后会更新文本）
        binding.btnSearchAddress.postDelayed({
            binding.btnSearchAddress.isEnabled = true
        }, 1000)
    }

    // ---- 编辑模式加载 ----

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

                // 地图移动到已存位置
                val latLng = LatLng(it.latitude, it.longitude)
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                currentMarker = aMap.addMarker(
                    MarkerOptions().position(latLng).title(it.address)
                )

                binding.tvSearchResult.text = buildString {
                    append("✓ ${it.address}\n")
                    append("纬度: ${"%.6f".format(it.latitude)}")
                    append("  经度: ${"%.6f".format(it.longitude)}")
                }
            }
        }
    }

    // ---- 保存 ----

    private fun saveAlarm() {
        val name = binding.etName.text.toString().trim()
        val reminder = binding.etReminder.text.toString().trim()
        val radiusStr = binding.etRadius.text.toString().trim()
        val tag = binding.etTag.text.toString().trim()

        if (name.isEmpty()) {
            binding.etName.error = "请输入闹钟名称"
            return
        }
        if (reminder.isEmpty()) {
            binding.etReminder.error = "请输入提醒内容"
            return
        }
        if (selectedLatitude == 0.0 && selectedLongitude == 0.0) {
            binding.tvSearchResult.text = "请在地图上选择一个位置"
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

    // ---- 权限 ----

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ---- MapView 生命周期 ----

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDestroy()
    }

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
    }
}
