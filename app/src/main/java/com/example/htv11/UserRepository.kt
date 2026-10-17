package com.example.htv11

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object UserRepository {
    private const val db = "https://squad-mesh-default-rtdb.firebaseio.com"

    private val usersRef: DatabaseReference = FirebaseDatabase.getInstance(db).getReference("UUID")

    fun saveUser(member: GroupMember, onResult: (Boolean) -> Unit = {}) {
        val userMap = mutableMapOf<String, Any>(
            "id" to member.id,
            "name" to member.name,
            "isOnline" to member.isOnline
        )
        usersRef.child(member.id).child("status").get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists() || snapshot.value == null || (snapshot.value as? String).isNullOrBlank()) {
                userMap["status"] = member.status.ifBlank { "safe" }
            }
            usersRef.child(member.id).updateChildren(userMap).addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    println("RTDB SAVE FAILED: ${task.exception?.message}")
                }
                onResult(task.isSuccessful)
            }
        }.addOnFailureListener {
            userMap["status"] = member.status.ifBlank { "safe" }
            usersRef.child(member.id).updateChildren(userMap).addOnCompleteListener { task ->
                onResult(task.isSuccessful)
            }
        }
    }

    fun setOnlineStatus(userId: String, isOnline: Boolean) {
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
                            list.add(member)
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

    fun nudgeUser(targetUserId: String, currentStatus: String) {
        val target = usersRef.child(targetUserId)
        target.child("lastNudged").setValue(System.currentTimeMillis())

        if (currentStatus.lowercase() == "safe") {
            target.child("status").setValue("unsure")
        }
    }

    fun updateStatus(userId: String, status: String) {
        usersRef.child(userId).child("status").setValue(status)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    println("RTDB UPDATE STATUS SUCCESS: $userId -> $status")
                } else {
                    println("RTDB UPDATE STATUS FAILED: ${task.exception?.message}")
                }
            }
    }
}