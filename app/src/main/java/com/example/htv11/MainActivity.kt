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
import com.example.htv11.ui.AppTab
import com.example.htv11.ui.GlassBuildingMapScreen
import com.example.htv11.ui.KazeBottomBar
import com.example.htv11.ui.theme.Htv11Theme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        enableEdgeToEdge()

        setContent {
            Htv11Theme {
                // If there's a cached user, we need to verify them before displaying the main screen
                var isCheckingSession by remember { mutableStateOf(AuthManager.currentUser != null) }
                var currentUser by remember { mutableStateOf(AuthManager.currentUser) }

                // Only relevant once signed in — group view is the default landing tab.
                var selectedTab by remember { mutableStateOf(AppTab.GROUP) }

                LaunchedEffect(Unit) {
                    if (AuthManager.currentUser != null) {
                        AuthManager.verifySession { verifiedUser ->
                            currentUser = verifiedUser
                            isCheckingSession = false
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (!isCheckingSession && currentUser != null) {
                            KazeBottomBar(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
                        }
                    }
                ) { innerPadding ->
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

                            // Sync user profile and listen for updates from RTDB
                            DisposableEffect(activeUser.uid) {
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

                            var emergencyContacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }

                            DisposableEffect(activeUser.uid) {
                                // Listen to real emergency contacts
                                val contactListener = UserRepository.observeEmergencyContacts(activeUser.uid) { liveContacts ->
                                    emergencyContacts = liveContacts
                                }

                                onDispose {
                                    UserRepository.removeEmergencyContactsObserver(activeUser.uid, contactListener)
                                }
                            }

                            // Group/map toggle via the bottom bar — plain window swap, no
                            // transition. Firebase sync above keeps running either way.
                            when (selectedTab) {
                                AppTab.GROUP -> MainGroupScreen(
                                    currentUserName = displayName,
                                    members = members,
                                    emergencyContacts = emergencyContacts,
                                    onNudgeMember = { member ->
                                        UserRepository.nudgeUser(member.id, member.status)
                                        val nextStatus = when (member.status.lowercase()) {
                                            "safe" -> "unsure"
                                            "unsure" -> "unsure"
                                            "danger" -> "danger"
                                            else -> "safe"
                                        }
                                        Toast.makeText(
                                            this,
                                            "Nudged ${member.name}! Status -> $nextStatus",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )

                                AppTab.MAP -> GlassBuildingMapScreen(
                                    userId = activeUser.uid,
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}