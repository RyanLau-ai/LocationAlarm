package com.example.locationalarm.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 围栏数据管理器：SharedPreferences 持久化存储
 */
class GeoFenceManager(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 读取全部围栏 */
    fun getAll(): List<GeoFence> {
        val json = sp.getString(KEY_FENCES, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                parseGeoFence(array.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 保存全部围栏（覆盖写入） */
    fun saveAll(fences: List<GeoFence>) {
        val array = JSONArray()
        fences.forEach { fence ->
            array.put(toJson(fence))
        }
        sp.edit().putString(KEY_FENCES, array.toString()).apply()
    }

    /** 新增或更新（id 相同则覆盖） */
    fun upsert(fence: GeoFence) {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.id == fence.id }
        if (idx >= 0) list[idx] = fence else list.add(fence)
        saveAll(list)
    }

    /** 按 ID 删除 */
    fun delete(id: String) {
        saveAll(getAll().filter { it.id != id })
    }

    /** 按 ID 查询 */
    fun getById(id: String): GeoFence? {
        return getAll().find { it.id == id }
    }

    /** 记录轨迹点（追加到 SharedPreferences） */
    fun appendTrackPoint(fenceId: String, latitude: Double, longitude: Double, timestamp: Long) {
        val key = "$KEY_TRACK_PREFIX$fenceId"
        val raw = sp.getString(key, "[]") ?: "[]"
        val array = JSONArray(raw)
        val point = JSONObject().apply {
            put("lat", latitude)
            put("lng", longitude)
            put("ts", timestamp)
        }
        array.put(point)
        sp.edit().putString(key, array.toString()).apply()
    }

    fun getTrackPoints(fenceId: String): List<Triple<Double, Double, Long>> {
        val key = "$KEY_TRACK_PREFIX$fenceId"
        val raw = sp.getString(key, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map {
                Triple(
                    array.getJSONObject(it).getDouble("lat"),
                    array.getJSONObject(it).getDouble("lng"),
                    array.getJSONObject(it).getLong("ts")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ──────────────── JSON 序列化 ────────────────

    private fun toJson(f: GeoFence): JSONObject = JSONObject().apply {
        put("id", f.id)
        put("name", f.name)
        put("lat", f.latitude)
        put("lng", f.longitude)
        put("address", f.address)
        put("radius", f.radius)
        put("enableIn", f.enableIn)
        put("enableOut", f.enableOut)
        put("alertContent", f.alertContent)
        put("alertWay", f.alertWay.name)
        put("trackRecord", f.trackRecord)
    }

    private fun parseGeoFence(obj: JSONObject): GeoFence = GeoFence(
        id = obj.getString("id"),
        name = obj.getString("name"),
        latitude = obj.getDouble("lat"),
        longitude = obj.getDouble("lng"),
        address = obj.optString("address", ""),
        radius = obj.optInt("radius", GeoFence.DEFAULT_RADIUS),
        enableIn = obj.optBoolean("enableIn", true),
        enableOut = obj.optBoolean("enableOut", false),
        alertContent = obj.optString("alertContent", ""),
        alertWay = try { AlertWay.valueOf(obj.getString("alertWay")) } catch (e: Exception) { AlertWay.VIBRATE },
        trackRecord = obj.optBoolean("trackRecord", false)
    )

    companion object {
        private const val PREF_NAME = "geo_fence_prefs"
        private const val KEY_FENCES = "fences"
        private const val KEY_TRACK_PREFIX = "track_"
    }
}
