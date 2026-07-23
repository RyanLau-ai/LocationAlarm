package com.example.locationalarm.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Search result from Nominatim geocoding API
 */
data class SearchResult(
    val displayName: String,
    val shortName: String,
    val lat: Double,
    val lon: Double
)

/**
 * Nominatim geocoding helper - address search and reverse geocoding
 * using OpenStreetMap's free Nominatim API.
 *
 * No API key required. Rate limited to 1 request per 1.1 seconds
 * per Nominatim Usage Policy.
 *
 * Usage Policy: https://operations.osmfoundation.org/policies/nominatim/
 */
object NominatimHelper {

    private const val TAG = "NominatimHelper"
    private const val BASE_URL = "https://nominatim.openstreetmap.org"
    private const val MIN_INTERVAL_MS = 1100L

    @Volatile
    private var lastRequestTime = 0L

    /**
     * Search for addresses by keyword
     * Returns up to 10 results
     */
    suspend fun searchAddress(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "$BASE_URL/search?q=$encodedQuery&format=json&limit=10&accept-language=zh-CN,en"

        val response = rateLimitedRequest(url) ?: return@withContext emptyList()

        try {
            val jsonArray = JSONArray(response)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                val displayName = obj.optString("display_name", "")
                val shortName = displayName.split(",").firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: displayName.take(50)
                SearchResult(
                    displayName = displayName,
                    shortName = shortName,
                    lat = obj.optString("lat", "0").toDoubleOrNull() ?: 0.0,
                    lon = obj.optString("lon", "0").toDoubleOrNull() ?: 0.0
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse search results failed", e)
            emptyList()
        }
    }

    /**
     * Reverse geocode: coordinates -> address text
     */
    suspend fun reverseGeocode(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/reverse?lat=$lat&lon=$lon&format=json&accept-language=zh-CN,en"

        val response = rateLimitedRequest(url)
            ?: return@withContext "%.6f, %.6f".format(lat, lon)

        try {
            val obj = JSONObject(response)
            val address = obj.optJSONObject("address")
            if (address != null) {
                // Build a readable address from address components
                val parts = mutableListOf<String>()
                address.optString("road")?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
                address.optString("house_number")?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
                address.optString("neighbourhood")?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
                address.optString("suburb")?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
                address.optString("city")?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
                address.optString("town")?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
                address.optString("county")?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
                address.optString("state")?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }

                if (parts.isNotEmpty()) {
                    return@withContext parts.joinToString(", ")
                }
            }
            obj.optString("display_name", "%.6f, %.6f".format(lat, lon))
        } catch (e: Exception) {
            Log.e(TAG, "Parse reverse geocode failed", e)
            "%.6f, %.6f".format(lat, lon)
        }
    }

    /**
     * Rate-limited HTTP GET request
     * Ensures minimum 1.1 second gap between requests per Nominatim policy
     */
    private suspend fun rateLimitedRequest(urlString: String): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRequestTime
        if (elapsed < MIN_INTERVAL_MS) {
            try {
                Thread.sleep(MIN_INTERVAL_MS - elapsed)
            } catch (e: InterruptedException) {
                return@withContext null
            }
        }
        lastRequestTime = System.currentTimeMillis()

        try {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "LocationAlarm/2.0 (Android)")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.e(TAG, "Nominatim API error: HTTP ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Nominatim request failed: ${e.message}")
            null
        }
    }
}
