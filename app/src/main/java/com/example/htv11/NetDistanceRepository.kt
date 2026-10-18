package com.example.htv11

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

private const val DATABASE_URL = "https://squad-mesh-default-rtdb.firebaseio.com"

/**
 * groups/{groupId}/netDistance/{myUid}/{otherUid}: meters (Double). Each member writes only their
 * own {myUid} subtree — same ownership pattern as locations/{uid} and users/{uid}. Both directions
 * of a pair (a-b and b-a) are computed and stored independently by each side; see the brainstorm
 * for why that duplication is intentional and harmless here, not a bug to dedupe.
 */
object NetDistanceRepository {
    private val groupsRef: DatabaseReference =
        FirebaseDatabase.getInstance(DATABASE_URL).getReference("groups")

    private fun safeGroupId(groupId: String) = groupId.trim().ifBlank { "DEFAULT_SQUAD" }

    private fun netDistanceRef(groupId: String, userId: String): DatabaseReference =
        groupsRef.child(safeGroupId(groupId)).child("netDistance").child(userId)

    /**
     * Overwrites [userId]'s whole netDistance subtree with freshly computed distances (meters) to
     * every other member this currently has a reading for. A full overwrite (not a per-key patch)
     * so a member who's since left the group is naturally dropped on the next tick.
     */
    fun pushDistances(groupId: String, userId: String, distancesToOthers: Map<String, Double>) {
        if (userId.isBlank()) return
        netDistanceRef(groupId, userId).setValue(distancesToOthers)
    }

    /** Deletes [userId]'s own netDistance subtree — call this when they leave the group's roster. */
    fun clearDistances(groupId: String, userId: String) {
        if (userId.isBlank()) return
        netDistanceRef(groupId, userId).removeValue()
    }

    /** Live map of otherUid -> meters, for [userId]'s own computed distances. */
    fun observeDistances(
        groupId: String,
        userId: String,
        onUpdate: (Map<String, Double>) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val result = mutableMapOf<String, Double>()
                for (child in snapshot.children) {
                    val otherUid = child.key ?: continue
                    val meters = child.getValue(Double::class.java) ?: continue
                    result[otherUid] = meters
                }
                onUpdate(result)
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        if (userId.isNotBlank()) {
            netDistanceRef(groupId, userId).addValueEventListener(listener)
        }
        return listener
    }

    fun removeObserver(groupId: String, userId: String, listener: ValueEventListener) {
        if (userId.isNotBlank()) {
            netDistanceRef(groupId, userId).removeEventListener(listener)
        }
    }
}
