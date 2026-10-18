package com.example.htv11

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object UserRepository {
    private const val db = "https://squad-mesh-default-rtdb.firebaseio.com"

    // Flat and global: a user's profile, status and emergency contacts live here regardless of
    // which group (if any) they're currently in. Joining or leaving a group never touches this.
    private val usersRef: DatabaseReference = FirebaseDatabase.getInstance(db).getReference("users")

    // Groups hold only a membership roster (and, later, inter-tether data) — no profile data.
    private val groupsRef: DatabaseReference = FirebaseDatabase.getInstance(db).getReference("groups")

    private fun safeGroupId(groupId: String) = groupId.trim().ifBlank { "DEFAULT_SQUAD" }
    private fun membersRef(groupId: String) = groupsRef.child(safeGroupId(groupId)).child("members")

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

    /** Creates or ensures a group node exists in RTDB at groups/{groupId}, optionally naming it. */
    fun createGroup(groupId: String, name: String? = null, onResult: (Boolean) -> Unit = {}) {
        val cleanGroupId = groupId.trim()
        if (cleanGroupId.isBlank()) {
            onResult(false)
            return
        }
        val groupData = mutableMapOf<String, Any>(
            "createdAt" to System.currentTimeMillis(),
            "groupId" to cleanGroupId
        )
        if (!name.isNullOrBlank()) {
            groupData["name"] = name.trim()
        }
        groupsRef.child(cleanGroupId).updateChildren(groupData).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                println("RTDB CREATE GROUP FAILED: ${task.exception?.message}")
            }
            onResult(task.isSuccessful)
        }
    }

    /** One-shot fetch of a group's display name (null if never set). */
    fun getGroupName(groupId: String, onResult: (String?) -> Unit) {
        val safeId = safeGroupId(groupId)
        groupsRef.child(safeId).child("name").get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onResult(task.result?.getValue(String::class.java))
            } else {
                onResult(null)
            }
        }
    }

    /** Registers [userId] in groups/{groupId}/members — the only group-side effect of joining. */
    fun joinGroup(groupId: String, userId: String, onResult: (Boolean) -> Unit = {}) {
        if (userId.isBlank()) {
            onResult(false)
            return
        }
        val safeId = safeGroupId(groupId)
        createGroup(safeId)
        membersRef(safeId).child(userId).setValue(true).addOnCompleteListener { task ->
            onResult(task.isSuccessful)
        }
    }

    /**
     * Leaves the group by removing the user from groups/{groupId}/members only. Never touches the
     * user's profile or location data — those are global and outlive group membership. If this
     * was the last remaining member, deletes the entire groups/{groupId} node (roster + tethers).
     */
    fun leaveGroup(groupId: String, userId: String, onResult: (Boolean) -> Unit = {}) {
        if (userId.isBlank()) {
            onResult(false)
            return
        }
        val safeId = safeGroupId(groupId)
        val groupNodeRef = groupsRef.child(safeId)

        groupNodeRef.child("members").child(userId).removeValue().addOnCompleteListener {
            groupNodeRef.child("members").get().addOnCompleteListener { snapshotTask ->
                val remainingCount = if (snapshotTask.isSuccessful && snapshotTask.result.exists()) {
                    snapshotTask.result.childrenCount
                } else {
                    0L
                }
                if (remainingCount == 0L) {
                    // Deletes the entire group node (roster + any inter-tether data) once empty.
                    groupNodeRef.removeValue().addOnCompleteListener { task ->
                        onResult(task.isSuccessful)
                    }
                } else {
                    onResult(true)
                }
            }
        }
    }

    // ---- Flat user profile: global, independent of group membership ----

    fun saveUser(member: GroupMember, onResult: (Boolean) -> Unit = {}) {
        if (member.id.isBlank()) {
            onResult(false)
            return
        }
        val userNodeRef = usersRef.child(member.id)
        userNodeRef.child("status").get().addOnCompleteListener { task ->
            val existingStatus = if (task.isSuccessful && task.result.exists()) {
                task.result?.getValue(String::class.java)
            } else null

            val profileData = mutableMapOf<String, Any>(
                "id" to member.id,
                "name" to member.name,
                "isOnline" to member.isOnline
            )
            if (!existingStatus.isNullOrBlank()) {
                profileData["status"] = existingStatus
            } else if (member.status.isNotBlank()) {
                profileData["status"] = member.status
            } else {
                profileData["status"] = SafetyStatus.SAFE.key
            }

            userNodeRef.updateChildren(profileData).addOnCompleteListener { saveTask ->
                if (!saveTask.isSuccessful) {
                    println("RTDB SAVE FAILED: ${saveTask.exception?.message}")
                }
                onResult(saveTask.isSuccessful)
            }
        }
    }

    fun setOnlineStatus(userId: String, isOnline: Boolean) {
        if (userId.isBlank()) return
        val statusRef = usersRef.child(userId).child("isOnline")
        statusRef.setValue(isOnline)
        statusRef.onDisconnect().setValue(false)
    }

    /** Every registered user, globally, with no group scoping — not currently wired to any screen. */
    fun observeAllUsers(onUpdate: (List<GroupMember>) -> Unit): ValueEventListener {
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

        usersRef.addValueEventListener(listener)
        return listener
    }

    fun removeObserver(listener: ValueEventListener) {
        usersRef.removeEventListener(listener)
    }

    fun nudgeUser(targetUserId: String) {
        if (targetUserId.isBlank()) return
        val statusRef = usersRef.child(targetUserId).child("status")

        statusRef.get().addOnCompleteListener { task ->
            val currentStatus = if (task.isSuccessful && task.result.exists()) {
                task.result?.getValue(String::class.java)
            } else null

            val newStatus = when (currentStatus?.lowercase()) {
                SafetyStatus.SAFE.key -> SafetyStatus.UNSURE.key
                SafetyStatus.UNSURE.key -> SafetyStatus.UNSURE.key
                SafetyStatus.DANGER.key -> SafetyStatus.DANGER.key
                else -> SafetyStatus.SAFE.key
            }

            statusRef.setValue(newStatus).addOnCompleteListener { writeTask ->
                if (!writeTask.isSuccessful) {
                    println("RTDB NUDGE FAILED: ${writeTask.exception?.message}")
                }
            }
        }
    }

    fun setStatus(userId: String, status: SafetyStatus) {
        if (userId.isBlank()) return
        usersRef.child(userId).child("status").setValue(status.key)
    }

    // Save or update an emergency contact
    fun addEmergencyContact(
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
        usersRef.child(userId)
            .child("emergency-contacts")
            .child(contactKey(name))
            .updateChildren(mapOf("name" to name.trim(), "phone" to phoneNumber))
            .addOnCompleteListener { task -> onComplete(task.isSuccessful) }
    }

    // Remove an emergency contact
    fun deleteEmergencyContact(userId: String, name: String) {
        if (userId.isBlank() || name.isBlank()) return
        usersRef.child(userId)
            .child("emergency-contacts")
            .child(contactKey(name))
            .removeValue()
    }

    private fun contactKey(name: String): String = name.trim().replace(Regex("[.$#\\[\\]/]"), "_")

    // Live listener for the current user's emergency contacts
    fun observeEmergencyContacts(
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
            usersRef.child(userId).child("emergency-contacts").addValueEventListener(listener)
        }
        return listener
    }

    // Cleanup helper
    fun removeEmergencyContactsObserver(userId: String, listener: ValueEventListener) {
        if (userId.isNotBlank()) {
            usersRef.child(userId).child("emergency-contacts").removeEventListener(listener)
        }
    }

    // ---- Group roster, joined client-side with live flat profile data ----

    /** Handle returned by [observeGroupMembers]; pass to [removeGroupMembersObserver] to clean up. */
    class GroupMembersSubscription internal constructor(
        internal val groupId: String,
        internal val membersListener: ValueEventListener,
        internal val profileListeners: MutableMap<String, ValueEventListener>
    )

    /**
     * Live list of [GroupMember] for everyone currently in groups/{groupId}/members. RTDB has no
     * server-side joins, so this watches the membership roster and separately watches each
     * member's flat users/{uid} profile, merging the two client-side whenever either changes.
     */
    fun observeGroupMembers(groupId: String, onUpdate: (List<GroupMember>) -> Unit): GroupMembersSubscription {
        val safeId = safeGroupId(groupId)
        val profileListeners = mutableMapOf<String, ValueEventListener>()
        val latestProfiles = mutableMapOf<String, GroupMember>()

        fun emit() {
            onUpdate(latestProfiles.values.filter { it.id.isNotBlank() && it.name.isNotBlank() })
        }

        val membersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentIds = snapshot.children.mapNotNull { it.key }.toSet()

                // Member removed from the roster (left, or kicked elsewhere): drop their profile.
                val staleIds = profileListeners.keys - currentIds
                staleIds.forEach { uid ->
                    profileListeners.remove(uid)?.let { usersRef.child(uid).removeEventListener(it) }
                    latestProfiles.remove(uid)
                }

                // New member: start watching their flat profile.
                val newIds = currentIds - profileListeners.keys
                newIds.forEach { uid ->
                    val profileListener = object : ValueEventListener {
                        override fun onDataChange(userSnapshot: DataSnapshot) {
                            val member = try {
                                userSnapshot.getValue(GroupMember::class.java)
                            } catch (e: Exception) {
                                println("Error parsing GroupMember snapshot: ${e.message}")
                                null
                            }
                            if (member != null) {
                                if (member.id.isBlank()) member.id = uid
                                latestProfiles[uid] = member
                            } else {
                                latestProfiles.remove(uid)
                            }
                            emit()
                        }

                        override fun onCancelled(error: DatabaseError) {}
                    }
                    usersRef.child(uid).addValueEventListener(profileListener)
                    profileListeners[uid] = profileListener
                }

                emit()
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        membersRef(safeId).addValueEventListener(membersListener)
        return GroupMembersSubscription(safeId, membersListener, profileListeners)
    }

    fun removeGroupMembersObserver(subscription: GroupMembersSubscription) {
        membersRef(subscription.groupId).removeEventListener(subscription.membersListener)
        subscription.profileListeners.forEach { (uid, listener) ->
            usersRef.child(uid).removeEventListener(listener)
        }
    }

}
