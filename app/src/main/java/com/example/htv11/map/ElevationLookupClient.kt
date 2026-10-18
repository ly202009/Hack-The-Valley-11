package com.example.htv11.map

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val MIN_LOOKUP_INTERVAL_MS = 15_000L
private const val MIN_MOVEMENT_METERS = 10.0

// Live ground-truth elevation for wherever the user currently is, instead of a
// hardcoded constant or a single device's own first-fix snapshot. Throttled
// hard: at most one network call per 15s, and only then if the user has moved
// further than max(10m, their current GPS accuracy) since the last successful
// lookup — the accuracy-aware part keeps indoor GPS jitter from spamming calls
// chasing noise. Between calls (and if a call fails), the last cached value is
// served as the live answer.
class ElevationLookupClient {
    private data class CachedElevation(
        val latitude: Double,
        val longitude: Double,
        val elevationMeters: Double,
        val timestampMillis: Long
    )

    @Volatile
    private var cached: CachedElevation? = null

    suspend fun groundElevationMeters(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?
    ): Double? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val last = cached

        if (last != null) {
            val sinceLastLookup = now - last.timestampMillis
            val movedMeters = distanceMeters(last.latitude, last.longitude, latitude, longitude)
            val requiredMovement = maxOf(MIN_MOVEMENT_METERS, (accuracyMeters ?: 0f).toDouble())

            if (sinceLastLookup < MIN_LOOKUP_INTERVAL_MS || movedMeters < requiredMovement) {
                return@withContext last.elevationMeters
            }
        }

        val fetched = runCatching { fetchElevation(latitude, longitude) }.getOrNull()
        if (fetched != null) {
            cached = CachedElevation(latitude, longitude, fetched, now)
            fetched
        } else {
            // Call failed (network hiccup, timeout, etc.) — keep serving the
            // last-known value rather than leaving the caller with nothing.
            last?.elevationMeters
        }
    }

    private fun fetchElevation(latitude: Double, longitude: Double): Double? {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.maptiler.com/elevation/%f,%f.json?key=%s",
                longitude,
                latitude,
                MapTilerConfig.API_KEY
            )
        )
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            // Response shape: [[lng, lat, elevationMeters], ...]
            val points = JSONArray(body)
            if (points.length() == 0) return null
            points.getJSONArray(0).getDouble(2)
        } finally {
            connection.disconnect()
        }
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }
}
