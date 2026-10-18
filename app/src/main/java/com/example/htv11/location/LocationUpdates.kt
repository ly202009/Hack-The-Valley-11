package com.example.htv11.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val LOCATION_UPDATE_INTERVAL_MS = 1500L

// Permission (foreground fine location) is assumed already granted — caller
// gates this behind a granted runtime permission check.
@SuppressLint("MissingPermission")
fun rawLocationUpdates(context: Context): Flow<Location> = callbackFlow {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL_MS)
        .setWaitForAccurateLocation(false) // don't delay the first fix waiting for a "better" one
        .build()

    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { trySend(it) }
        }
    }

    client.requestLocationUpdates(request, callback, null)
    awaitClose { client.removeLocationUpdates(callback) }
}
