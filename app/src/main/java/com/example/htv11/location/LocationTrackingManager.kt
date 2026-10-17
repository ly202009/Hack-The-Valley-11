package com.example.htv11.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val UPDATE_INTERVAL_MS = 5000L

class LocationTrackingManager(context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Emits this device's location roughly every 5 seconds, with altitude already
     * snapped to a 4m floor bucket (or null if the device has no altitude fix).
     *
     * Caller must already hold ACCESS_FINE_LOCATION — this is what actually
     * produces altitude; ACCESS_COARSE_LOCATION alone will almost never set it.
     * Permission handling (the runtime request UI) is out of scope for Step 1.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    @SuppressLint("MissingPermission") // precondition documented above; caller's responsibility
    fun trackLocation(): Flow<TrackedLocation> = callbackFlow {
        // HIGH_ACCURACY so this actually drives the GPS chip — altitude fixes are
        // unreliable (often entirely absent) on network/Wi-Fi-only positioning.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val tracked = location.toTrackedLocation()
                trySend(tracked)

                // TODO (Step 2): Push snapped location to Firebase groups/{group_id}/members/{user_id}
                // TODO (Step 2): Listen to Firebase for group updates (separate stream —
                //                everyone else's locations, merged with this one on the map)
            }
        }

        fusedLocationClient.requestLocationUpdates(request, callback, null)
        awaitClose { fusedLocationClient.removeLocationUpdates(callback) }
    }

    private fun Location.toTrackedLocation(): TrackedLocation {
        // The hardware constraint: barometer presence/quality varies by device and
        // raw GPS altitude drifts, so two friends standing on the same floor would
        // otherwise render at different heights. Snapping to 4m buckets fixes that.
        val snappedAltitude: Double? = if (hasAltitude()) {
            snapToFloorHeight(altitude)
        } else {
            // No barometer / no altitude fix on this device: don't guess a height
            // and don't crash — null means "render on the ground floor." Step 3's
            // UI uses hasAltitude=false here to show the yellow caution badge.
            null
        }

        return TrackedLocation(
            latitude = latitude,
            longitude = longitude,
            snappedAltitudeMeters = snappedAltitude,
            hasAltitude = hasAltitude(),
            timestampMillis = time
        )
    }
}
