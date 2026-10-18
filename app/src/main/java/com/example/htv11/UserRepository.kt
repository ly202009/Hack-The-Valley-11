package com.example.htv11

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

object UserRepository {
    private const val db = "https://squad-mesh-default-rtdb.firebaseio.com"

    private val usersRef: DatabaseReference = FirebaseDatabase.getInstance(db).getReference("UUID")

    fun saveUser(member: GroupMember, onResult: (Boolean) -> Unit = {}) {
        if (member.id.isBlank()) {
            onResult(false)
            return
        }
        val userNodeRef = usersRef.child(member.id)
        userNodeRef.child("status").get().addOnCompleteListener { task ->
            val existingStatus = if (task.isSuccessful) task.result.getValue(String::class.java) else null
            val profile = mutableMapOf<String, Any>(
                "id" to member.id,
                "name" to member.name,
                "isOnline" to member.isOnline
            )
            // Ensure status field is created if profile/status does not exist yet
            if (existingStatus.isNullOrBlank()) {
                profile["status"] = member.status.ifBlank { "safe" }
            }
            userNodeRef.updateChildren(profile).addOnCompleteListener { saveTask ->
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
        val target = usersRef.child(targetUserId)

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
                    // Older entries have no "name" field and used the key as the name.
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
}
