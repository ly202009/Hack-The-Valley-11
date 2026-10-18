package com.example.htv11

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
import com.google.firebase.database.ValueEventListener

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                            var isProfileLoaded by remember { mutableStateOf(false) }

                            // Sync user profile and listen for updates from RTDB
                            DisposableEffect(activeUser.uid) {
                                val prefs = getSharedPreferences("htv11_prefs", MODE_PRIVATE)
                                prefs.edit().putString("current_user_id", activeUser.uid).apply()

                                val self = GroupMember(
                                    id = activeUser.uid,
                                    name = displayName,
                                    isOnline = true,
                                    status = "safe"
                                )
                                UserRepository.saveUser(self) { success ->
                                    if (success) {
                                        UserRepository.setOnlineStatus(activeUser.uid, true)
                                    }
                                }

                                var listener: ValueEventListener? = null
                                listener = UserRepository.observeAllUsers { liveMembers ->
                                    // Filter ghost users (profiles with blank ID or name)
                                    val validMembers = liveMembers.filter { it.id.isNotBlank() && it.name.isNotBlank() }
                                    members = validMembers

                                    // If user profile is not found in RTDB after initial load, pause updates & return to entry screen
                                    if (isProfileLoaded && validMembers.none { it.id == activeUser.uid }) {
                                        listener?.let { UserRepository.removeObserver(it) }
                                        UserRepository.setOnlineStatus(activeUser.uid, false)
                                        AuthManager.signOut()
                                        currentUser = null
                                    } else if (validMembers.any { it.id == activeUser.uid }) {
                                        isProfileLoaded = true
                                    }
                                }

                                onDispose {
                                    listener?.let { UserRepository.removeObserver(it) }
                                    if (currentUser != null) {
                                        UserRepository.setOnlineStatus(activeUser.uid, false)
                                    }
                                }
                            }

                            val myMember = members.firstOrNull { it.id == activeUser.uid }
                            val myStatus = SafetyStatus.fromKey(myMember?.status)

                            val shouldShowPinPrompt = (myStatus == SafetyStatus.UNSURE)

                            if (shouldShowPinPrompt) {
                                NudgePinDialog(
                                    onSubmit = { pin ->
                                        val status = when (PinStore.check(pin)) {
                                            PinResult.NORMAL -> SafetyStatus.SAFE
                                            PinResult.DURESS -> SafetyStatus.DANGER
                                            PinResult.WRONG -> null
                                        }
                                        if (status != null) {
                                            UserRepository.setStatus(activeUser.uid, status)
                                        }
                                        status != null
                                    }
                                )
                            }

                            var emergencyContacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }

                            DisposableEffect(activeUser.uid) {
                                val contactListener = UserRepository.observeEmergencyContacts(activeUser.uid) { liveContacts ->
                                    emergencyContacts = liveContacts
                                }

                                onDispose {
                                    UserRepository.removeEmergencyContactsObserver(activeUser.uid, contactListener)
                                }
                            }

                            MainGroupScreen(
                                currentUserName = displayName,
                                members = members,
                                emergencyContacts = emergencyContacts,
                                onNudgeMember = { member ->
                                    UserRepository.nudgeUser(member.id)
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
}
