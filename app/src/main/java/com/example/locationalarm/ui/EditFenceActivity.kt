package com.example.locationalarm.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.UiSettings
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Circle
import com.amap.api.maps.model.CircleOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.PoiItem
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.RegeocodeAddress
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.example.locationalarm.BuildConfig
import com.example.locationalarm.R
import com.example.locationalarm.data.AlertWay
import com.example.locationalarm.data.GeoFence
import com.example.locationalarm.data.GeoFenceManager
import com.example.locationalarm.databinding.ActivityEditFenceBinding
import com.example.locationalarm.databinding.ItemSuggestionBinding
import com.example.locationalarm.service.GeofenceForegroundService
import com.example.locationalarm.util.PermissionHelper
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * 编辑围栏界面：
 * - MapView 地图选点（点击地图移动中心图钉）
 * - 高德 Inputtips 搜索联想（300ms 防抖）
 * - 逆地理编码获取地址
 * - 进入/离开提醒开关 + 提醒方式 + 提醒内容
 */
class EditFenceActivity : AppCompatActivity(), AMap.OnMapClickListener, AMap.OnCameraChangeListener,
    Inputtips.InputtipsListener, GeocodeSearch.OnGeocodeSearchListener {

    companion object {
        private const val TAG = "EditFenceActivity"
        const val EXTRA_FENCE_ID = "fence_id"
        private const val DEBOUNCE_MS = 300L
    }

    private lateinit var binding: ActivityEditFenceBinding

    private lateinit var mapView: MapView
    private var aMap: AMap? = null
    private var marker: Marker? = null
    private var circle: Circle? = null

    private lateinit var fenceManager: GeoFenceManager

    // 当前选中位置（地图中心）
    private var selectedLatLng: LatLng? = null
    private var selectedAddress: String = ""

    // 编辑模式
    private var editingId: String? = null

    // 搜索
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var lastQuery: String = ""
    private val suggestions = mutableListOf<Tip>()
    private lateinit var suggestionAdapter: SuggestionAdapter

    // 逆地理编码
    private var regeocodeSearch: GeocodeSearch? = null

    // 定位
    private var locationClient: AMapLocationClient? = null

    // 当前提醒方式
    private var selectedWay: AlertWay = AlertWay.VIBRATE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditFenceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fenceManager = GeoFenceManager(this)

        // 编辑模式：读取传入 ID
        editingId = intent.getStringExtra(EXTRA_FENCE_ID)

        setupToolbar()
        setupMap()
        setupSearch()
        setupForm()
        setupAlertWay()

        if (editingId != null) {
            loadFenceForEdit()
        } else {
            // 新建：尝试定位到当前位置
            binding.toolbar.title = getString(R.string.title_new_fence)
            locateMe()
        }
    }

    // ──────────────── Toolbar ────────────────

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    // ──────────────── 地图 ────────────────

    private fun setupMap() {
        mapView = binding.mapView
        mapView.onCreate(null)
        aMap = mapView.map.apply {
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isCompassEnabled = true
            setOnMapClickListener(this@EditFenceActivity)
            setOnCameraChangeListener(this@EditFenceActivity)

            // 高德 Key 未配置时提示
            if (BuildConfig.AMAP_KEY == "YOUR_AMAP_KEY_HERE") {
                Snackbar.make(binding.root, R.string.err_no_amap_key, Snackbar.LENGTH_LONG).show()
            }
        }

        binding.fabMyLocation.setOnClickListener { locateMe() }

        // 点击地图：把中心点移动到点击位置（图钉始终在屏幕中心）
        // 通过 onMapClick 实现
    }

    override fun onMapClick(latLng: LatLng) {
        // 移动相机使点击位置成为中心（图钉视觉上落在点击处）
        aMap?.animateCamera(CameraUpdateFactory.newLatLng(latLng))
        updateSelectedPosition(latLng)
    }

    override fun onCameraChange(position: CameraPosition) {
        // 相机移动过程中更新图钉位置
        selectedLatLng = position.target
    }

    override fun onCameraChangeFinish(position: CameraPosition) {
        // 相机停止后：确认选点 + 逆地理编码
        val target = position.target
        updateSelectedPosition(target)
    }

    /**
     * 更新选中位置：移动图钉、画半径圈、逆地理编码
     */
    private fun updateSelectedPosition(latLng: LatLng) {
        selectedLatLng = latLng

        // 图钉
        if (marker == null) {
            marker = aMap?.addMarker(
                MarkerOptions().position(latLng).draggable(false)
            )
        } else {
            marker?.position = latLng
        }

        // 半径圈（使用当前半径输入值）
        val radius = binding.etRadius.text.toString().toIntOrNull()
            ?: GeoFence.DEFAULT_RADIUS
        if (circle == null) {
            circle = aMap?.addCircle(
                CircleOptions()
                    .center(latLng)
                    .radius(radius.toDouble())
                    .strokeColor(0x553F51B5)
                    .fillColor(0x223F51B5)
                    .strokeWidth(2f)
            )
        } else {
            circle?.center = latLng
            circle?.radius = radius.toDouble()
        }

        // 逆地理编码获取地址
        reverseGeocode(latLng)
    }

    /**
     * 逆地理编码：坐标 → 地址文字
     */
    private fun reverseGeocode(latLng: LatLng) {
        try {
            if (regeocodeSearch == null) {
                regeocodeSearch = GeocodeSearch(this).apply {
                    setOnGeocodeSearchListener(this@EditFenceActivity)
                }
            }
            val query = RegeocodeQuery(
                LatLonPoint(latLng.latitude, latLng.longitude),
                200f,  // 半径 200 米
                GeocodeSearch.AMAP
            )
            regeocodeSearch?.getFromLocationAsyn(query)
        } catch (e: Exception) {
            Log.e(TAG, "reverseGeocode error: ${e.message}")
        }
    }

    override fun onGeocodeSearched(result: GeocodeResult?, code: Int) {
        if (code == 1000 && result != null) {
            val address = result.regeocodeAddress
            selectedAddress = formatAddress(address)
            binding.tvAddress.text = selectedAddress
        }
    }

    private fun formatAddress(addr: RegeocodeAddress?): String {
        if (addr == null) return ""
        val sb = StringBuilder()
        sb.append(addr.province ?: "")
        sb.append(addr.city ?: "")
        sb.append(addr.district ?: "")
        sb.append(addr.township ?: "")
        // 合并包中街道信息封装在 StreetNumber 对象中
        addr.streetNumber?.let { sn ->
            sb.append(sn.street ?: "")
            sb.append(sn.number ?: "")
        }
        return sb.toString().ifBlank { "未知地址" }
    }

    /**
     * 定位到当前位置
     */
    private fun locateMe() {
        if (!PermissionHelper.hasFineLocation(this)) {
            Toast.makeText(this, R.string.err_no_location, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            if (locationClient == null) {
                locationClient = AMapLocationClient(this).apply {
                    val option = AMapLocationClientOption().apply {
                        locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                        isOnceLocation = true
                        isNeedAddress = true
                    }
                    setLocationOption(option)
                    setLocationListener(object : AMapLocationListener {
                        override fun onLocationChanged(location: AMapLocation?) {
                            if (location != null && location.errorCode == 0) {
                                val ll = LatLng(location.latitude, location.longitude)
                                aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 16f))
                                updateSelectedPosition(ll)
                            } else {
                                Toast.makeText(
                                    this@EditFenceActivity,
                                    R.string.err_no_location,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            locationClient?.stopLocation()
                        }
                    })
                }
            }
            locationClient?.startLocation()
        } catch (e: Exception) {
            Log.e(TAG, "locateMe error: ${e.message}")
            Toast.makeText(this, R.string.err_no_location, Toast.LENGTH_SHORT).show()
        }
    }

    // ──────────────── 搜索联想 ────────────────

    private fun setupSearch() {
        suggestionAdapter = SuggestionAdapter { tip ->
            // 点击联想结果：移动到该地点
            if (tip.point != null) {
                val ll = LatLng(tip.point.latitude, tip.point.longitude)
                aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 16f))
                selectedAddress = tip.name + " " + tip.address
                binding.tvAddress.text = selectedAddress
                hideSuggestions()
                binding.etSearch.clearFocus()
            }
        }
        binding.rvSuggestions.layoutManager = LinearLayoutManager(this)
        binding.rvSuggestions.adapter = suggestionAdapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                // 300ms 防抖
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    if (query.isNotEmpty()) {
                        searchInputTips(query)
                    } else {
                        suggestions.clear()
                        suggestionAdapter.notifyDataSetChanged()
                        binding.rvSuggestions.visibility = View.GONE
                    }
                }
                searchHandler.postDelayed(searchRunnable!!, DEBOUNCE_MS)
            }
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // 直接搜索第一个结果
                val query = binding.etSearch.text.toString().trim()
                if (query.isNotEmpty()) searchInputTips(query)
                true
            } else false
        }
    }

    /**
     * 高德 Inputtips 搜索联想
     */
    private fun searchInputTips(keyword: String) {
        if (keyword == lastQuery && suggestions.isNotEmpty()) return
        lastQuery = keyword
        try {
            val query = InputtipsQuery(keyword, "")
            query.cityLimit = false
            val inputtips = Inputtips(this, query)
            inputtips.setInputtipsListener(this)
            inputtips.requestInputtipsAsyn()
        } catch (e: AMapException) {
            Log.e(TAG, "Inputtips error: ${e.errorMessage}")
        }
    }

    override fun onGetInputtips(list: MutableList<Tip>?, code: Int) {
        if (code == 1000) {
            suggestions.clear()
            list?.let { suggestions.addAll(it) }
            suggestionAdapter.notifyDataSetChanged()
            binding.rvSuggestions.visibility =
                if (suggestions.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun hideSuggestions() {
        binding.rvSuggestions.visibility = View.GONE
    }

    // ──────────────── 表单 ────────────────

    private fun setupForm() {
        // 半径变化时更新圆圈
        binding.etRadius.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val radius = s?.toString()?.toIntOrNull()
                if (radius != null && selectedLatLng != null) {
                    circle?.radius = radius.toDouble()
                }
            }
        })

        binding.btnSave.setOnClickListener { saveFence() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnDelete.setOnClickListener { confirmDelete() }

        // 进入/离开开关：至少一个开启
        binding.switchInAlert.isChecked = true
    }

    private fun setupAlertWay() {
        binding.chipGroupAlertWay.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedWay = when {
                checkedIds.contains(binding.chipVibrate.id) -> AlertWay.VIBRATE
                checkedIds.contains(binding.chipRing.id) -> AlertWay.RING
                checkedIds.contains(binding.chipBoth.id) -> AlertWay.BOTH
                checkedIds.contains(binding.chipSilent.id) -> AlertWay.SILENT
                else -> AlertWay.VIBRATE
            }
        }
        binding.chipVibrate.isChecked = true
    }

    // ──────────────── 加载编辑数据 ────────────────

    private fun loadFenceForEdit() {
        val fence = fenceManager.getById(editingId!!) ?: run {
            Toast.makeText(this, R.string.err_save_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.toolbar.title = getString(R.string.title_edit_fence)

        binding.etName.setText(fence.name)
        binding.etRadius.setText(fence.radius.toString())
        binding.tvAddress.text = fence.address
        selectedAddress = fence.address
        binding.switchInAlert.isChecked = fence.enableIn
        binding.switchOutAlert.isChecked = fence.enableOut
        binding.etContent.setText(fence.alertContent)

        // 提醒方式
        binding.chipVibrate.isChecked = fence.alertWay == AlertWay.VIBRATE
        binding.chipRing.isChecked = fence.alertWay == AlertWay.RING
        binding.chipBoth.isChecked = fence.alertWay == AlertWay.BOTH
        binding.chipSilent.isChecked = fence.alertWay == AlertWay.SILENT
        selectedWay = fence.alertWay

        binding.btnDelete.visibility = View.VISIBLE

        // 地图定位到围栏中心
        val ll = LatLng(fence.latitude, fence.longitude)
        aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(ll, 16f))
        updateSelectedPosition(ll)
    }

    // ──────────────── 保存 / 删除 ────────────────

    private fun saveFence() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.err_name_required, Toast.LENGTH_SHORT).show()
            return
        }
        val radius = binding.etRadius.text.toString().toIntOrNull()
        if (radius == null || radius < 50 || radius > 5000) {
            Toast.makeText(this, R.string.err_radius_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        val latLng = selectedLatLng
        if (latLng == null) {
            Toast.makeText(this, R.string.err_location_not_set, Toast.LENGTH_SHORT).show()
            return
        }
        if (!binding.switchInAlert.isChecked && !binding.switchOutAlert.isChecked) {
            Toast.makeText(this, R.string.err_no_alert_selected, Toast.LENGTH_SHORT).show()
            return
        }

        val fence = if (editingId != null) {
            val old = fenceManager.getById(editingId!!) ?: return
            old.copy(
                name = name,
                latitude = latLng.latitude,
                longitude = latLng.longitude,
                address = selectedAddress,
                radius = radius,
                enableIn = binding.switchInAlert.isChecked,
                enableOut = binding.switchOutAlert.isChecked,
                alertContent = binding.etContent.text.toString().trim(),
                alertWay = selectedWay
            )
        } else {
            GeoFence.create(
                name = name,
                latitude = latLng.latitude,
                longitude = latLng.longitude,
                address = selectedAddress,
                radius = radius,
                enableIn = binding.switchInAlert.isChecked,
                enableOut = binding.switchOutAlert.isChecked,
                alertContent = binding.etContent.text.toString().trim(),
                alertWay = selectedWay,
                trackRecord = false
            )
        }

        fenceManager.upsert(fence)
        GeofenceForegroundService.start(this)  // 重新注册围栏
        Toast.makeText(this, R.string.btn_save, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDelete() {
        val fence = fenceManager.getById(editingId!!) ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(getString(R.string.confirm_delete_message, fence.name))
            .setPositiveButton(R.string.confirm_yes) { _, _ ->
                fenceManager.delete(fence.id)
                GeofenceForegroundService.start(this)
                finish()
            }
            .setNegativeButton(R.string.confirm_no, null)
            .show()
    }

    // ──────────────── 生命周期 ────────────────

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        searchHandler.removeCallbacksAndMessages(null)
        mapView.onDestroy()
        try {
            locationClient?.stopLocation()
            locationClient?.onDestroy()
        } catch (_: Exception) {
        }
    }

    // ──────────────── 搜索联想适配器 ────────────────

    private inner class SuggestionAdapter(
        private val onClick: (Tip) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<SuggestionAdapter.VH>() {

        override fun onCreateViewHolder(
            parent: ViewGroup, viewType: Int
        ): VH {
            val binding = ItemSuggestionBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun getItemCount() = suggestions.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(suggestions[position])
        }

        inner class VH(
            private val binding: ItemSuggestionBinding
        ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

            fun bind(tip: Tip) {
                binding.tvSuggestion.text = tip.name
                binding.tvSuggestionAddr.text = tip.address
                binding.root.setOnClickListener { onClick(tip) }
            }
        }
    }
}
