package com.example.locationalarm.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.locationalarm.LocationAlarmApp
import com.example.locationalarm.data.Alarm
import com.example.locationalarm.databinding.ActivityAddEditAlarmBinding
import com.example.locationalarm.util.AMapTileSource
import com.example.locationalarm.util.NominatimHelper
import com.example.locationalarm.util.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class AddEditAlarmActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AddEditAlarmActivity"
        const val EXTRA_ALARM_ID = "extra_alarm_id"

        private val REPEAT_OPTIONS = arrayOf(
            Pair("仅提醒一次", 0L),
            Pair("每 1 分钟", 60_000L),
            Pair("每 3 分钟", 180_000L),
            Pair("每 5 分钟", 300_000L),
            Pair("每 10 分钟", 600_000L),
            Pair("每 15 分钟", 900_000L),
            Pair("每 30 分钟", 1_800_000L),
            Pair("每 1 小时", 3_600_000L),
            Pair("每 2 小时", 7_200_000L)
        )
    }

    private lateinit var binding: ActivityAddEditAlarmBinding
    private var editingAlarmId: Long = -1L

    // OSMDroid
    private lateinit var mapView: MapView
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var currentMarker: Marker? = null

    // Search suggestions
    private var searchJob: Job? = null
    private val searchResults = mutableListOf<SearchResult>()
    private lateinit var suggestionAdapter: ArrayAdapter<String>

    // Selected location
    private var selectedLatitude: Double = 0.0
    private var selectedLongitude: Double = 0.0
    private var selectedAddress: String = ""

    // Repeat interval
    private var selectedRepeatInterval: Long = 0L

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && !isFinishing && !isDestroyed) {
            enableMyLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        editingAlarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        title = if (editingAlarmId != -1L) "编辑闹钟" else "添加闹钟"

        initMap()
        initSearchBox()
        initRepeatSpinner()
        setupClickListeners()

        if (editingAlarmId != -1L) {
            loadAlarmForEditing()
        }
    }

    private fun runOnUiIfAlive(action: () -> Unit) {
        if (!isFinishing && !isDestroyed) {
            runOnUiThread(action)
        }
    }

    // ---- Map ----

    @SuppressLint("ClickableViewAccessibility")
    private fun initMap() {
        try {
            Configuration.getInstance().load(this, getSharedPreferences("osmdroid", 0))

            mapView = binding.mapView
            mapView.setTileSource(AMapTileSource.ROAD_MAP)
            mapView.controller.setZoom(13.0)

            // Default center: Beijing
            mapView.controller.setCenter(GeoPoint(39.9087, 116.3975))

            // Built-in zoom controls
            mapView.setBuiltInZoomControls(true)
            mapView.setMultiTouchControls(true)

            // Map click to select location
            val mapEventsReceiver = object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    selectLocation(p.latitude, p.longitude)
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean {
                    selectLocation(p.latitude, p.longitude)
                    return true
                }
            }
            mapView.overlays.add(0, MapEventsOverlay(mapEventsReceiver))

            // Request location permission and enable my location
            if (hasLocationPermission()) {
                enableMyLocation()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Map init failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        try {
            val provider = GpsMyLocationProvider(this)
            myLocationOverlay = MyLocationNewOverlay(provider, mapView)
            myLocationOverlay?.enableMyLocation()
            myLocationOverlay?.enableFollowLocation()
            mapView.overlays.add(myLocationOverlay)
        } catch (e: Exception) {
            Log.e(TAG, "Enable my location failed", e)
        }
    }

    private fun selectLocation(lat: Double, lon: Double) {
        selectedLatitude = lat
        selectedLongitude = lon

        try {
            val geoPoint = GeoPoint(lat, lon)

            // Move marker
            currentMarker?.let { mapView.overlays.remove(it) }
            currentMarker = Marker(mapView).apply {
                position = geoPoint
                title = "选中位置"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(currentMarker)
            mapView.invalidate()

            // Move camera
            mapView.controller.animateTo(geoPoint)
        } catch (e: Exception) {
            Log.e(TAG, "Set marker failed", e)
        }

        runOnUiIfAlive {
            binding.tvSearchResult.text = "正在获取地址..."
        }

        // Reverse geocode
        lifecycleScope.launch {
            try {
                val address = NominatimHelper.reverseGeocode(lat, lon)
                selectedAddress = address
                runOnUiIfAlive {
                    binding.tvSearchResult.text = buildString {
                        append("* $address\n")
                        append("纬度: ${"%.6f".format(lat)}")
                        append("  经度: ${"%.6f".format(lon)}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reverse geocode failed", e)
                selectedAddress = "%.6f, %.6f".format(lat, lon)
                runOnUiIfAlive {
                    binding.tvSearchResult.text = "获取地址失败，但位置已选中\n纬度: ${"%.6f".format(lat)}  经度: ${"%.6f".format(lon)}"
                }
            }
        }
    }

    // ---- Search ----

    private fun initSearchBox() {
        try {
            val autoComplete = binding.etAddress as AutoCompleteTextView
            suggestionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf())
            autoComplete.setAdapter(suggestionAdapter)
            autoComplete.threshold = 2

            autoComplete.setOnItemClickListener { _, _, position, _ ->
                try {
                    if (position < searchResults.size) {
                        val result = searchResults[position]
                        selectSearchResult(result)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Suggestion click failed", e)
                }
            }

            autoComplete.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val keyword = s?.toString()?.trim() ?: ""
                    if (keyword.length >= 2) {
                        searchSuggestions(keyword)
                    } else {
                        searchResults.clear()
                        suggestionAdapter.clear()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Search box init failed", e)
        }
    }

    private fun searchSuggestions(keyword: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(500) // 500ms debounce
            if (!isActive) return@launch
            try {
                val results = NominatimHelper.searchAddress(keyword)
                if (results.isEmpty()) return@launch

                searchResults.clear()
                searchResults.addAll(results)

                val displayNames = results.map { it.shortName }
                suggestionAdapter.clear()
                suggestionAdapter.addAll(displayNames)
                suggestionAdapter.notifyDataSetChanged()

                if (binding.etAddress is AutoCompleteTextView) {
                    (binding.etAddress as AutoCompleteTextView).showDropDown()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search suggestions failed: ${e.message}")
            }
        }
    }

    private fun selectSearchResult(result: SearchResult) {
        selectedLatitude = result.lat
        selectedLongitude = result.lon
        selectedAddress = result.displayName

        try {
            val geoPoint = GeoPoint(result.lat, result.lon)

            currentMarker?.let { mapView.overlays.remove(it) }
            currentMarker = Marker(mapView).apply {
                position = geoPoint
                title = result.shortName
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(currentMarker)

            mapView.controller.animateTo(geoPoint)
            mapView.controller.setZoom(17.0)
            mapView.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Move to search result failed", e)
        }

        binding.tvSearchResult.text = buildString {
            append("* ${result.shortName}\n")
            append("纬度: ${"%.6f".format(result.lat)}")
            append("  经度: ${"%.6f".format(result.lon)}")
        }
    }

    // ---- Repeat spinner ----

    private fun initRepeatSpinner() {
        val spinner = binding.spinnerRepeat
        val labels = REPEAT_OPTIONS.map { it.first }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        spinner.setAdapter(adapter)
        spinner.threshold = 0
        spinner.setOnClickListener {
            spinner.showDropDown()
        }

        spinner.setOnItemClickListener { _, _, position, _ ->
            if (position < REPEAT_OPTIONS.size) {
                selectedRepeatInterval = REPEAT_OPTIONS[position].second
            }
        }

        spinner.setText(labels[0], false)
        selectedRepeatInterval = REPEAT_OPTIONS[0].second
    }

    // ---- Click listeners ----

    @SuppressLint("MissingPermission")
    private fun setupClickListeners() {
        binding.btnSearchAddress.setOnClickListener {
            val query = binding.etAddress.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            }
        }

        binding.fabMyLocation.setOnClickListener {
            try {
                myLocationOverlay?.let { overlay ->
                    overlay.enableFollowLocation()
                    val location = overlay.lastFix
                    if (location != null) {
                        val geoPoint = GeoPoint(location.latitude, location.longitude)
                        mapView.controller.animateTo(geoPoint)
                        mapView.controller.setZoom(17.0)
                    } else {
                        // Try system location
                        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
                        val lastKnown = if (hasLocationPermission()) {
                            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        } else null
                        if (lastKnown != null) {
                            val geoPoint = GeoPoint(lastKnown.latitude, lastKnown.longitude)
                            mapView.controller.animateTo(geoPoint)
                            mapView.controller.setZoom(17.0)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Locate current position failed", e)
            }
        }

        binding.btnSave.setOnClickListener {
            saveAlarm()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun performSearch(query: String) {
        binding.btnSearchAddress.isEnabled = false
        binding.tvSearchResult.text = "正在搜索..."

        lifecycleScope.launch {
            try {
                val results = NominatimHelper.searchAddress(query)
                runOnUiIfAlive {
                    binding.btnSearchAddress.isEnabled = true
                    if (results.isEmpty()) {
                        binding.tvSearchResult.text = "未找到该地址，请尝试更详细的关键词"
                    } else {
                        selectSearchResult(results[0])
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Address search failed", e)
                runOnUiIfAlive {
                    binding.btnSearchAddress.isEnabled = true
                    binding.tvSearchResult.text = "搜索失败，请重试"
                }
            }
        }
    }

    // ---- Edit mode ----

    private fun loadAlarmForEditing() {
        lifecycleScope.launch {
            try {
                val alarm = (application as LocationAlarmApp).repository.getAlarmById(editingAlarmId)
                alarm?.let {
                    if (isFinishing || isDestroyed) return@let

                    binding.etName.setText(it.name)
                    binding.etReminder.setText(it.reminder)
                    binding.etAddress.setText(it.address)
                    binding.etRadius.setText(it.radius.toString())
                    binding.etTag.setText(it.tag)
                    selectedLatitude = it.latitude
                    selectedLongitude = it.longitude
                    selectedAddress = it.address
                    selectedRepeatInterval = it.repeatInterval

                    // Set repeat spinner
                    val matchIndex = REPEAT_OPTIONS.indexOfFirst { option -> option.second == it.repeatInterval }
                    if (matchIndex >= 0) {
                        binding.spinnerRepeat.setText(REPEAT_OPTIONS[matchIndex].first, false)
                    }

                    // Move map to saved location
                    val geoPoint = GeoPoint(it.latitude, it.longitude)
                    mapView.controller.setCenter(geoPoint)
                    mapView.controller.setZoom(17.0)

                    currentMarker = Marker(mapView).apply {
                        position = geoPoint
                        title = it.address
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    mapView.overlays.add(currentMarker)
                    mapView.invalidate()

                    binding.tvSearchResult.text = buildString {
                        append("* ${it.address}\n")
                        append("纬度: ${"%.6f".format(it.latitude)}")
                        append("  经度: ${"%.6f".format(it.longitude)}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load alarm data failed", e)
            }
        }
    }

    // ---- Save ----

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

        binding.btnSave.isEnabled = false

        val alarm = Alarm(
            id = if (editingAlarmId != -1L) editingAlarmId else 0,
            name = name,
            reminder = reminder,
            latitude = selectedLatitude,
            longitude = selectedLongitude,
            address = selectedAddress,
            radius = radius,
            tag = tag,
            repeatInterval = selectedRepeatInterval
        )

        val app = application as LocationAlarmApp
        lifecycleScope.launch {
            try {
                if (editingAlarmId != -1L) {
                    val existing = app.repository.getAlarmById(editingAlarmId)
                    val updated = alarm.copy(
                        triggered = existing?.triggered ?: false,
                        lastTriggeredAt = existing?.lastTriggeredAt ?: 0L
                    )
                    app.repository.update(updated)
                } else {
                    app.repository.insert(alarm)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Save alarm failed", e)
            }
            if (!isFinishing && !isDestroyed) {
                finish()
            }
        }
    }

    // ---- Permissions ----

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ---- Lifecycle ----

    override fun onResume() {
        super.onResume()
        try {
            mapView.onResume()
        } catch (e: Exception) {
            Log.e(TAG, "MapView onResume failed", e)
        }
    }

    override fun onPause() {
        try {
            mapView.onPause()
        } catch (e: Exception) {
            Log.e(TAG, "MapView onPause failed", e)
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        try {
            mapView.onSaveInstanceState(outState)
        } catch (e: Exception) {
            Log.e(TAG, "MapView onSaveInstanceState failed", e)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            mapView.onLowMemory()
        } catch (e: Exception) {
            Log.e(TAG, "MapView onLowMemory failed", e)
        }
    }

    override fun onDestroy() {
        try {
            myLocationOverlay?.disableMyLocation()
            myLocationOverlay?.disableFollowLocation()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup myLocationOverlay failed", e)
        }
        try {
            mapView.onDetach()
        } catch (e: Exception) {
            Log.e(TAG, "MapView onDetach failed", e)
        }
        searchJob?.cancel()
        super.onDestroy()
    }
}
