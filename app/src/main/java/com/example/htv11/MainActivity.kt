package com.example.htv11

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

        setContent {
            Htv11Theme {
                var currentUser by remember { mutableStateOf(AuthManager.currentUser) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (currentUser == null) {
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
                    } else {
                        val activeUser = currentUser!!
                        val displayName = activeUser.displayName?.ifBlank { "Daniel" } ?: "Daniel"

                        val mockMembers = remember(activeUser.uid) {
                            listOf(
                                GroupMember(id = activeUser.uid, name = displayName, isOnline = true),
                                GroupMember(id = "2", name = "Sean", isOnline = true),
                                GroupMember(id = "3", name = "Liwen", isOnline = false),
                                GroupMember(id = "4", name = "Alex", isOnline = true)
                            )
                        }

                        MainGroupScreen(
                            currentUserName = displayName,
                            members = mockMembers,
                            onNudgeMember = { member ->
                                Toast.makeText(
                                    this,
                                    "Nudged ${member.name}!",
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