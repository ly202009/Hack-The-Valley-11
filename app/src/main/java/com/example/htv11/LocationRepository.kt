package com.example.htv11

import com.example.htv11.location.FloorClassification
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

private const val DATABASE_URL = "https://squad-mesh-default-rtdb.firebaseio.com"

// Firebase POJO for the locations/{userId} node. Kept separate from
// GroupMember/UserRepository on purpose: GPS ticks every ~1.5s and would
// otherwise also fire MainGroupScreen's member-list listener on every update.
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

object LocationRepository {
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
