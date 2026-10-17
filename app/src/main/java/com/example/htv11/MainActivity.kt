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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.htv11.ui.theme.Htv11Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Htv11Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // --- Registration Screen (Commented out for testing) ---
                    /*
                    RegistrationScreen(
                        modifier = Modifier
                            .padding(innerPadding)
                            .imePadding(),
                        onJoinGroup = { enteredName ->
                            // TODO: Save to Firebase and proceed to Map
                        }
                    )
                    */

                    // --- Main Screen (Active for testing) ---
                    val mockMembers = remember {
                        listOf(
                            GroupMember(id = "1", name = "Daniel", isOnline = true),
                            GroupMember(id = "2", name = "Sean", isOnline = true),
                            GroupMember(id = "3", name = "Liwen", isOnline = false),
                            GroupMember(id = "4", name = "Alex", isOnline = true)
                        )
                    }

                    MainGroupScreen(
                        currentUserName = "Daniel",
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