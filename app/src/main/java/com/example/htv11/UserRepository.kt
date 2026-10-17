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
        usersRef.child(member.id).setValue(member)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    println("RTDB SAVE FAILED: ${task.exception?.message}")
                }
                onResult(task.isSuccessful)
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
                    val member = child.getValue(GroupMember::class.java)
                    if (member != null) {
                        list.add(member)
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
        val nextStatus = when (currentStatus.lowercase()) {
            "safe" -> "unsure"
            "unsure" -> "unsure"
            "danger" -> "danger"
            else -> "safe"
        }
        usersRef.child(targetUserId).child("status").setValue(nextStatus)
    }
}