package com.example.htv11

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest

object AuthManager {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /** Ensures the user is authenticated anonymously before making database calls. */
    fun ensureAuth(onComplete: (Boolean) -> Unit) {
        if (auth.currentUser != null) {
            onComplete(true)
        } else {
            auth.signInAnonymously().addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
        }
    }

    /**
     * Checks Firebase servers to confirm the locally cached user actually exists.
     * If deleted on the console/backend, it wipes the local session.
     */
    fun verifySession(onResult: (FirebaseUser?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult(null)
            return
        }

        user.reload().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onResult(auth.currentUser)
            } else {
                // If deleted from Firebase console, clear the local cache
                if (task.exception is FirebaseAuthInvalidUserException) {
                    auth.signOut()
                    onResult(null)
                } else {
                    // Retain local session if failure was just a temporary network loss
                    onResult(auth.currentUser)
                }
            }
        }
    }

    fun signInAnonymously(
        displayName: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        // Clear any orphaned or dead local session before creating a fresh anonymous user
        if (auth.currentUser != null) {
            auth.signOut()
        }

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

    /** Updates the Firebase Auth profile's display name — the canonical "who am I" most of the app reads. */
    fun updateDisplayName(
        name: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        val user = auth.currentUser
        if (user == null) {
            onError(Exception("Not signed in"))
            return
        }
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(name.trim())
            .build()
        user.updateProfile(profileUpdates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onSuccess()
            } else {
                onError(task.exception ?: Exception("Failed to update display name"))
            }
        }
    }
}
