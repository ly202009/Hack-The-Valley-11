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
        // Only the profile fields: overwriting the whole node would reset status and lastNudged on every login.
        val profile = mapOf("id" to member.id, "name" to member.name, "isOnline" to member.isOnline)
        usersRef.child(member.id).updateChildren(profile)
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

    fun nudgeUser(targetUserId: String) {
        val target = usersRef.child(targetUserId)
        target.child("lastNudged").setValue(System.currentTimeMillis())

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
        usersRef.child(userId).child("status").setValue(status.key)
    }
}