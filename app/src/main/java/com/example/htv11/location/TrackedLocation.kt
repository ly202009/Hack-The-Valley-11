package com.example.htv11.location

// Device-local, cloud-ready reading: altitude has already been snapped to a
// 4m floor bucket (or is null if this device couldn't produce one). This is
// the shape Step 2 will push to Firebase as-is.
data class TrackedLocation(
    val latitude: Double,
    val longitude: Double,
    val snappedAltitudeMeters: Double?,
    val hasAltitude: Boolean,
    val timestampMillis: Long
)
