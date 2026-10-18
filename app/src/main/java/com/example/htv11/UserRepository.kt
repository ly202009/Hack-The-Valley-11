package com.example.htv11

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object UserRepository {
    private const val db = "https://squad-mesh-default-rtdb.firebaseio.com"

    private val groupsRef: DatabaseReference = FirebaseDatabase.getInstance(db).getReference("groups")

    private fun getUsersRef(groupId: String): DatabaseReference {
        val safeGroupId = groupId.trim().ifBlank { "DEFAULT_SQUAD" }
        return groupsRef.child(safeGroupId).child("UUID")
    }

    /** Checks whether a group ID exists in RTDB under groups/{groupId}. */
    fun checkGroupExists(groupId: String, onResult: (Boolean) -> Unit) {
        val cleanGroupId = groupId.trim()
        if (cleanGroupId.isBlank()) {
            onResult(false)
            return
        }
        groupsRef.child(cleanGroupId).get().addOnCompleteListener { task ->
            if (task.isSuccessful && task.result.exists()) {
                onResult(true)
            } else {
                onResult(false)
            }
        }.addOnFailureListener {
            onResult(false)
        }
    }

    /** Creates or ensures a group node exists in RTDB at groups/{groupId}. */
    fun createGroup(groupId: String, onResult: (Boolean) -> Unit = {}) {
        val cleanGroupId = groupId.trim()
        if (cleanGroupId.isBlank()) {
            onResult(false)
            return
        }
        val groupData = mapOf<String, Any>(
            "createdAt" to System.currentTimeMillis(),
            "groupId" to cleanGroupId
        )
        groupsRef.child(cleanGroupId).updateChildren(groupData).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                println("RTDB CREATE GROUP FAILED: ${task.exception?.message}")
            }
            onResult(task.isSuccessful)
        }
    }

    /**
     * Leaves the group by removing the user's UUID profile node and location node from groups/{groupId}.
     * If this was the last remaining member in the group, deletes the entire groups/{groupId} node.
     */
    fun leaveGroup(groupId: String, userId: String, onResult: (Boolean) -> Unit = {}) {
        if (userId.isBlank()) {
            onResult(false)
            return
        }
        val safeGroupId = groupId.trim().ifBlank { "DEFAULT_SQUAD" }
        val groupNodeRef = groupsRef.child(safeGroupId)
        val userRef = groupNodeRef.child("UUID").child(userId)
        val locationRef = groupNodeRef.child("LOCATIONS").child(userId)

        userRef.removeValue().addOnCompleteListener {
            locationRef.removeValue().addOnCompleteListener {
                // Check if any other users remain in this group
                groupNodeRef.child("UUID").get().addOnCompleteListener { snapshotTask ->
                    val remainingCount = if (snapshotTask.isSuccessful && snapshotTask.result.exists()) {
                        snapshotTask.result.childrenCount
                    } else {
                        0L
                    }
                    if (remainingCount == 0L) {
                        // Deletes the entire group node from RTDB if no members remain
                        groupNodeRef.removeValue().addOnCompleteListener { task ->
                            onResult(task.isSuccessful)
                        }
                    } else {
                        onResult(true)
                    }
                }
            }
        }
    }

    fun saveUser(groupId: String, member: GroupMember, onResult: (Boolean) -> Unit = {}) {
        if (member.id.isBlank()) {
            onResult(false)
            return
        }
        val safeGroupId = groupId.trim().ifBlank { "DEFAULT_SQUAD" }

        // Guarantee group exists in RTDB before writing user
        createGroup(safeGroupId)

        val userNodeRef = getUsersRef(safeGroupId).child(member.id)
        val profileData = mapOf<String, Any>(
            "id" to member.id,
            "name" to member.name,
            "isOnline" to member.isOnline,
            "status" to member.status.ifBlank { "safe" }
        )

        userNodeRef.updateChildren(profileData).addOnCompleteListener { saveTask ->
            if (!saveTask.isSuccessful) {
                println("RTDB SAVE FAILED: ${saveTask.exception?.message}")
            }
            onResult(saveTask.isSuccessful)
        }
    }

    fun setOnlineStatus(groupId: String, userId: String, isOnline: Boolean) {
        if (userId.isBlank()) return
        val statusRef = getUsersRef(groupId).child(userId).child("isOnline")
        statusRef.setValue(isOnline)
        statusRef.onDisconnect().setValue(false)
    }

    fun observeAllUsers(groupId: String, onUpdate: (List<GroupMember>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<GroupMember>()
                for (child in snapshot.children) {
                    try {
                        val member = child.getValue(GroupMember::class.java)
                        if (member != null) {
                            if (member.id.isBlank()) {
                                member.id = child.key ?: ""
                            }
                            // Filter ghost users (profiles with blank ID or blank name)
                            if (member.id.isNotBlank() && member.name.isNotBlank()) {
                                list.add(member)
                            }
                        }
                    } catch (e: Exception) {
                        println("Error parsing GroupMember snapshot: ${e.message}")
                    }
                }
                onUpdate(list)
            }

            override fun onCancelled(error: DatabaseError) {
                // Empty handler
            }
        }

        val ref = getUsersRef(groupId)
        ref.addValueEventListener(listener)
        return listener
    }

    /**
     * How many real members besides [selfId] are in the group, or null if that can't be read
     * (offline). Nameless entries are ghost profiles and don't count.
     */
    suspend fun countOtherMembers(groupId: String, selfId: String): Int? = withTimeoutOrNull(5_000L) {
        val ref = getUsersRef(groupId)
        suspendCancellableCoroutine<Int?> { continuation ->
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val others = snapshot.children.count { child ->
                        child.key != selfId && !child.child("name").getValue(String::class.java).isNullOrBlank()
                    }
                    if (continuation.isActive) continuation.resume(others)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            ref.addListenerForSingleValueEvent(listener)
            continuation.invokeOnCancellation { ref.removeEventListener(listener) }
        }
    }

    fun removeObserver(groupId: String, listener: ValueEventListener) {
        getUsersRef(groupId).removeEventListener(listener)
    }

    fun nudgeUser(groupId: String, targetUserId: String) {
        if (targetUserId.isBlank()) return
        val target = getUsersRef(groupId).child(targetUserId)

        // A nudge puts someone to "unsure" until they answer, but must never hide an active danger status.
        target.child("status").runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                if (currentData.getValue(String::class.java) != SafetyStatus.DANGER.key) {
                    currentData.value = SafetyStatus.UNSURE.key
                }
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {}
        })
    }

    fun setStatus(groupId: String, userId: String, status: SafetyStatus) {
        if (userId.isBlank()) return
        getUsersRef(groupId).child(userId).child("status").setValue(status.key)
    }

    // Save or update an emergency contact
    fun addEmergencyContact(
        groupId: String,
        userId: String,
        name: String,
        phoneNumber: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (userId.isBlank() || name.isBlank()) {
            onComplete(false)
            return
        }
        // The key can't hold . $ # [ ] / so the real name is also stored as a field.
        getUsersRef(groupId).child(userId)
            .child("emergency-contacts")
            .child(contactKey(name))
            .updateChildren(mapOf("name" to name.trim(), "phone" to phoneNumber))
            .addOnCompleteListener { task -> onComplete(task.isSuccessful) }
    }

    // Remove an emergency contact
    fun deleteEmergencyContact(groupId: String, userId: String, name: String) {
        if (userId.isBlank() || name.isBlank()) return
        getUsersRef(groupId).child(userId)
            .child("emergency-contacts")
            .child(contactKey(name))
            .removeValue()
    }

    private fun contactKey(name: String): String = name.trim().replace(Regex("[.$#\\[\\]/]"), "_")

    // Live listener for the current user's emergency contacts
    fun observeEmergencyContacts(
        groupId: String,
        userId: String,
        onUpdate: (List<EmergencyContact>) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val contacts = mutableListOf<EmergencyContact>()
                for (child in snapshot.children) {
                    val name = child.child("name").getValue(String::class.java) ?: child.key ?: ""
                    val phone = child.child("phone").getValue(String::class.java) ?: ""
                    if (name.isNotBlank()) {
                        contacts.add(EmergencyContact(name = name, phone = phone))
                    }
                }
                onUpdate(contacts)
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        if (userId.isNotBlank()) {
            getUsersRef(groupId).child(userId).child("emergency-contacts").addValueEventListener(listener)
        }
        return listener
    }

    // Cleanup helper
    fun removeEmergencyContactsObserver(groupId: String, userId: String, listener: ValueEventListener) {
        if (userId.isNotBlank()) {
            getUsersRef(groupId).child(userId).child("emergency-contacts").removeEventListener(listener)
        }
    }
}
