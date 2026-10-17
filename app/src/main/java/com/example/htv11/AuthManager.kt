package com.example.htv11

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest

object AuthManager {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun signInAnonymously(
        displayName: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        auth.signInAnonymously().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build()

                user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                    onSuccess()
                } ?: onSuccess()
            } else {
                onError(task.exception ?: Exception("Authentication failed"))
            }
        }
    }

    fun signOut() {
        auth.signOut()
    }
}