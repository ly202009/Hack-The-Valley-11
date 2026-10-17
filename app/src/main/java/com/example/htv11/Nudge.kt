package com.example.htv11

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class NudgeService {
    private val database = FirebaseDatabase.getInstance("https://squad-mesh-default-rtdb.firebaseio.com")
    private val auth = FirebaseAuth.getInstance()

    init {
        if (auth.currentUser == null) {
            auth.signInAnonymously().addOnFailureListener { e ->
                // Handle or log auth failure
            }
        }
    }

    /**
     * Sends a nudge to a target user via Firebase Realtime Database after ensuring anonymous authentication.
     */
    fun sendNudge(senderUuid: String, targetUuid: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val action = {
            try {
                val nudgeRef = database.getReference("nudges").child(targetUuid).push()
                val nudgeData = mapOf(
                    "senderId" to senderUuid,
                    "timestamp" to ServerValue.TIMESTAMP
                )

                nudgeRef.setValue(nudgeData)
                    .addOnSuccessListener {
                        onSuccess()
                    }
                    .addOnFailureListener { exception ->
                        onError(exception)
                    }
            } catch (e: Exception) {
                onError(e)
            }
        }

        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener { action() }
                .addOnFailureListener { e -> onError(e) }
        } else {
            action()
        }
    }

    /**
     * Listens for incoming nudges for the current user in real time after ensuring anonymous authentication.
     */
    fun listenForNudges(myUuid: String, onNudgeReceived: (senderId: String) -> Unit) {
        val action = {
            try {
                val nudgesRef = database.getReference("nudges").child(myUuid)
                nudgesRef.addChildEventListener(object : ChildEventListener {
                    override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                        val senderId = snapshot.child("senderId").getValue(String::class.java)
                        if (senderId != null) {
                            onNudgeReceived(senderId)
                            snapshot.ref.removeValue()
                        }
                    }

                    override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                    override fun onChildRemoved(snapshot: DataSnapshot) {}
                    override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                    override fun onCancelled(error: DatabaseError) {}
                })
            } catch (_: Exception) {
                // Ignore listener exceptions
            }
        }

        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener { action() }
                .addOnFailureListener { /* Handle auth error */ }
        } else {
            action()
        }
    }
}
