package com.example.htv11

import com.example.htv11.location.FloorClassification
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

private const val DATABASE_URL = "https://squad-mesh-default-rtdb.firebaseio.com"

data class LocationRecord(
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var clampedAltitudeMeters: Double = 0.0,
    var classification: String = FloorClassification.UNCERTAIN.name
)

data class GroupLocation(
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val clampedAltitudeMeters: Double,
    val classification: FloorClassification
)

/** Straight-line 3D distance (horizontal + vertical combined) between two synced locations. */
fun distance3dMeters(from: GroupLocation, to: GroupLocation): Double {
    val horizontalResult = FloatArray(1)
    android.location.Location.distanceBetween(
        from.latitude, from.longitude,
        to.latitude, to.longitude,
        horizontalResult
    )
    val horizontal = horizontalResult[0].toDouble()
    val vertical = from.clampedAltitudeMeters - to.clampedAltitudeMeters
    return kotlin.math.sqrt(horizontal * horizontal + vertical * vertical)
}

object LocationRepository {
    // Flat and global, same as users/{uid} — a location isn't scoped to any particular group.
    private val locationsRef: DatabaseReference =
        FirebaseDatabase.getInstance(DATABASE_URL).getReference("locations")

    fun pushLocation(
        userId: String,
        latitude: Double,
        longitude: Double,
        clampedAltitudeMeters: Double,
        classification: FloorClassification
    ) {
        val record = LocationRecord(
            latitude = latitude,
            longitude = longitude,
            clampedAltitudeMeters = clampedAltitudeMeters,
            classification = classification.name
        )
        locationsRef.child(userId).setValue(record)
    }

    fun observeAllLocations(onUpdate: (List<GroupLocation>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val result = mutableListOf<GroupLocation>()
                for (child in snapshot.children) {
                    val userId = child.key ?: continue
                    val record = child.getValue(LocationRecord::class.java) ?: continue
                    val classification = runCatching {
                        FloorClassification.valueOf(record.classification)
                    }.getOrDefault(FloorClassification.UNCERTAIN)

                    result.add(
                        GroupLocation(
                            userId = userId,
                            latitude = record.latitude,
                            longitude = record.longitude,
                            clampedAltitudeMeters = record.clampedAltitudeMeters,
                            classification = classification
                        )
                    )
                }
                onUpdate(result)
            }

            override fun onCancelled(error: DatabaseError) {
                // Empty handler
            }
        }

        locationsRef.addValueEventListener(listener)
        return listener
    }

    fun removeObserver(listener: ValueEventListener) {
        locationsRef.removeEventListener(listener)
    }
}
