package com.example.htv11

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.htv11.ui.theme.Htv11Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleEmergencyIntent(intent)

        setContent {
            Htv11Theme {
                // If there's a cached user, we need to verify them before displaying the main screen
                var isCheckingSession by remember { mutableStateOf(AuthManager.currentUser != null) }
                var currentUser by remember { mutableStateOf(AuthManager.currentUser) }

                LaunchedEffect(Unit) {
                    if (AuthManager.currentUser != null) {
                        AuthManager.verifySession { verifiedUser ->
                            currentUser = verifiedUser
                            isCheckingSession = false
                        }
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when {
                        // 1. Brief verification check in progress: display the themed background to prevent screen flash
                        isCheckingSession -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                DynamicBlueGridBackground()
                            }
                        }

                        // 2. No session or user deleted on Firebase: show registration
                        currentUser == null -> {
                            RegistrationScreen(
                                modifier = Modifier
                                    .padding(innerPadding)
                                    .imePadding(),
                                onJoinGroup = { enteredName ->
                                    AuthManager.signInAnonymously(
                                        displayName = enteredName,
                                        onSuccess = {
                                            currentUser = AuthManager.currentUser
                                            currentUser?.let { user ->
                                                val prefs = getSharedPreferences("htv11_prefs", Context.MODE_PRIVATE)
                                                prefs.edit().putString("current_user_id", user.uid).apply()
                                            }
                                        },
                                        onError = { error ->
                                            Toast.makeText(
                                                this,
                                                error.localizedMessage ?: "Sign-in failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                }
                            )
                        }

                        // 3. Authenticated & verified user: proceed to main group screen
                        else -> {
                            val activeUser = currentUser!!
                            val displayName = activeUser.displayName?.ifBlank { "Operative" } ?: "Operative"
                            var members by remember { mutableStateOf<List<GroupMember>>(emptyList()) }

                            // Sync user profile and listen for updates from RTDB
                            DisposableEffect(activeUser.uid) {
                                val prefs = getSharedPreferences("htv11_prefs", Context.MODE_PRIVATE)
                                prefs.edit().putString("current_user_id", activeUser.uid).apply()

                                val self = GroupMember(
                                    id = activeUser.uid,
                                    name = displayName,
                                    isOnline = true,
                                    status = "safe"
                                )
                                UserRepository.saveUser(self)
                                UserRepository.setOnlineStatus(activeUser.uid, true)

                                val listener = UserRepository.observeAllUsers { liveMembers ->
                                    members = liveMembers
                                }

                                onDispose {
                                    UserRepository.removeObserver(listener)
                                    UserRepository.setOnlineStatus(activeUser.uid, false)
                                }
                            }
                            MainGroupScreen(
                                currentUserName = displayName,
                                members = members,
                                onNudgeMember = { member ->
                                    UserRepository.nudgeUser(member.id, member.status)
                                    val isSafe = member.status.lowercase() == "safe"
                                    val message = if (isSafe) {
                                        "Nudged ${member.name}! Status set to Unsure."
                                    } else {
                                        "Nudged ${member.name}!"
                                    }
                                    Toast.makeText(
                                        this,
                                        message,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleEmergencyIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("EMERGENCY_TRIGGERED", false) == true) {
            val user = AuthManager.currentUser
            if (user != null) {
                UserRepository.updateStatus(user.uid, "danger")
            }
        }
    }
}