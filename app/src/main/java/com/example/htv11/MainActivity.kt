package com.example.htv11

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.htv11.ui.theme.Htv11Theme
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch

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
                                        if (status == SafetyStatus.DANGER) {
                                            // Silent: also text the emergency contacts, whoever else is in the group.
                                            lifecycleScope.launch {
                                                DistressAlert.send(this@MainActivity, displayName)
                                            }
                                        }
                                        status != null
                                    }
                                )
                            }

                            var emergencyContacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }

                            DisposableEffect(activeUser.uid) {
                                val contactListener = UserRepository.observeEmergencyContacts(activeUser.uid) { liveContacts ->
                                    emergencyContacts = liveContacts
                                    EmergencyContactStore.save(this@MainActivity, liveContacts)
                                }

                                onDispose {
                                    UserRepository.removeEmergencyContactsObserver(activeUser.uid, contactListener)
                                }
                            }

                            var showAddContact by remember { mutableStateOf(false) }
                            var pendingContact by remember { mutableStateOf<EmergencyContact?>(null) }
                            var contactToDelete by remember { mutableStateOf<EmergencyContact?>(null) }

                            // Keep a recent location on hand for the silent alarm, which can't read it from the background.
                            LaunchedEffect(activeUser.uid) {
                                LocationHelper.currentLocation(this@MainActivity)
                            }

                            // Finds the user's location and address, then texts the contact straight away.
                            val textContact: (EmergencyContact) -> Unit = { contact ->
                                Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show()
                                lifecycleScope.launch {
                                    val location = LocationHelper.currentLocation(this@MainActivity)
                                    val address = location?.let { LocationHelper.addressFor(this@MainActivity, it) }
                                    val message = buildDangerMessage(displayName, location, address)

                                    if (sendSms(this@MainActivity, contact.phone, message)) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Alert sent to ${contact.name}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        // No SMS permission or sending failed: let the user send it from their messaging app.
                                        openSmsComposer(this@MainActivity, contact.phone, message)
                                    }
                                }
                            }

                            // If a permission is refused, the alert still goes out without that part.
                            val alertPermissionLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.RequestMultiplePermissions()
                            ) {
                                pendingContact?.let(textContact)
                                pendingContact = null
                            }

                            if (showAddContact) {
                                AddEmergencyContactDialog(
                                    onDismiss = { showAddContact = false },
                                    onSave = { name, phone ->
                                        showAddContact = false
                                        UserRepository.addEmergencyContact(activeUser.uid, name, phone) { saved ->
                                            if (!saved) {
                                                Toast.makeText(this, "Couldn't save contact", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        // Ask now: the silent alarm can't show a permission prompt when it fires.
                                        if (!LocationHelper.hasPermission(this) || !hasSmsPermission(this)) {
                                            alertPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                                    Manifest.permission.SEND_SMS
                                                )
                                            )
                                        }
                                    }
                                )
                            }

                            contactToDelete?.let { contact ->
                                AlertDialog(
                                    onDismissRequest = { contactToDelete = null },
                                    title = { Text("Remove contact?") },
                                    text = { Text("${contact.name} will no longer be alerted.") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            UserRepository.deleteEmergencyContact(activeUser.uid, contact.name)
                                            contactToDelete = null
                                        }) { Text("Remove") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { contactToDelete = null }) { Text("Cancel") }
                                    }
                                )
                            }

                            MainGroupScreen(
                                currentUserName = displayName,
                                members = members,
                                emergencyContacts = emergencyContacts,
                                onEmergencyContactClick = { contact ->
                                    if (LocationHelper.hasPermission(this) && hasSmsPermission(this)) {
                                        textContact(contact)
                                    } else {
                                        pendingContact = contact
                                        alertPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                                Manifest.permission.SEND_SMS
                                            )
                                        )
                                    }
                                },
                                onDeleteEmergencyContactClick = { contact -> contactToDelete = contact },
                                onAddEmergencyContactClick = {
                                    // Writing before the profile exists would create a nameless ghost user.
                                    if (myMember != null) showAddContact = true
                                },
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
