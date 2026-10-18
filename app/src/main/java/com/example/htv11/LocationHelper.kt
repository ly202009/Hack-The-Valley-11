package com.example.htv11

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

private const val FRESH_FIX_MS = 2 * 60_000L
private const val FRESH_REQUEST_TIMEOUT_MS = 10_000L

/** Finds where the user is right now and turns it into something a person can read. */
object LocationHelper {
    fun hasPermission(context: Context): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** A recent location, or the newest known one if a fresh fix doesn't arrive in time. Null if none. */
    suspend fun currentLocation(context: Context): Location? =
        findLocation(context)?.also { cacheLocation(context, it) }

    /**
     * The last location found while the app could see it. Android hides location from an app in the
     * background, so the silent alarm falls back to this.
     */
    fun cachedLocation(context: Context): Location? {
        val prefs = context.getSharedPreferences("last_location", Context.MODE_PRIVATE)
        if (!prefs.contains("lat")) return null
        return Location("cache").apply {
            latitude = prefs.getFloat("lat", 0f).toDouble()
            longitude = prefs.getFloat("lng", 0f).toDouble()
            time = prefs.getLong("time", 0L)
        }
    }

    private fun cacheLocation(context: Context, location: Location) {
        context.getSharedPreferences("last_location", Context.MODE_PRIVATE).edit()
            .putFloat("lat", location.latitude.toFloat())
            .putFloat("lng", location.longitude.toFloat())
            .putLong("time", location.time)
            .apply()
    }

    @SuppressLint("MissingPermission")
    private suspend fun findLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        val lastKnown = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < FRESH_FIX_MS) return lastKnown

        val provider = providers.firstOrNull() ?: return lastKnown
        val fresh = withTimeoutOrNull(FRESH_REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { continuation ->
                // Older Android versions still call all of these, so all must be overridden.
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) continuation.resume(location)
                    }
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }
                try {
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                } catch (_: SecurityException) {
                    continuation.resume(null)
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            }
        }
        return fresh ?: lastKnown
    }

    /** A street address for [location], or null if none can be found (offline, no geocoder). */
    suspend fun addressFor(context: Context, location: Location): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.getAddressLine(0)
        } catch (_: Exception) {
            null
        }
    }
}

/** The text sent to an emergency contact: who is in danger and where, with a map link. */
fun buildDangerMessage(name: String, location: Location?, address: String?): String {
    val who = "$name is in danger and needs help."
    if (location == null) return "$who Their location is unavailable."

    val mapLink = "https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, location.latitude, location.longitude)
    return if (address != null) "$who They are at $address ($mapLink)" else "$who They are at $mapLink"
}
