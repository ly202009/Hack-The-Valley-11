package com.example.htv11

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.example.htv11.location.AltitudeFusion
import com.example.htv11.location.BarometerReader
import com.example.htv11.location.rawLocationUpdates
import com.example.htv11.map.ElevationLookupClient
import com.example.htv11.ui.AppTab
import com.example.htv11.ui.GlassBuildingMapScreen
import com.example.htv11.ui.KazeBottomBar
import androidx.lifecycle.lifecycleScope
import com.example.htv11.ui.theme.Htv11Theme
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        enableEdgeToEdge()

        setContent {
            Htv11Theme {
                val prefs = remember { getSharedPreferences("htv11_prefs", MODE_PRIVATE) }
                var isCheckingSession by remember { mutableStateOf(AuthManager.currentUser != null) }
                var currentUser by remember { mutableStateOf(AuthManager.currentUser) }
                var currentGroupId by remember {
                    mutableStateOf(prefs.getString("current_group_id", "") ?: "")
                }

                var selectedTab by remember { mutableStateOf(AppTab.GROUP) }

                LaunchedEffect(Unit) {
                    if (AuthManager.currentUser != null) {
                        AuthManager.verifySession { verifiedUser ->
                            if (verifiedUser == null) {
                                // If cached UUID cannot be found or verified on Firebase, reset to first login page
                                prefs.edit().clear().apply()
                                currentGroupId = ""
                                currentUser = null
                            } else {
                                currentUser = verifiedUser
                            }
                            isCheckingSession = false
                        }
                    } else {
                        isCheckingSession = false
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (!isCheckingSession && currentUser != null && currentGroupId.isNotBlank()) {
                            KazeBottomBar(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
                        }
                    }
                ) { innerPadding ->
                    when {
                        // 1. Brief verification check in progress
                        isCheckingSession -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                DynamicBlueGridBackground()
                            }
                        }

                        // 2. No session, display name missing, or group ID missing: show registration / group selection
                        currentUser == null || currentUser?.displayName.isNullOrBlank() || currentGroupId.isBlank() -> {
                            val activeDisplayName = currentUser?.displayName
                            RegistrationScreen(
                                modifier = Modifier
                                    .padding(innerPadding)
                                    .imePadding(),
                                currentDisplayName = activeDisplayName,
                                onSaveName = { enteredName ->
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
                                },
                                onJoinGroup = { groupId ->
                                    UserRepository.createGroup(groupId) {
                                        prefs.edit().putString("current_group_id", groupId).apply()
                                        currentGroupId = groupId
                                    }
                                },
                                onCreateGroup = { groupId ->
                                    UserRepository.createGroup(groupId) {
                                        prefs.edit().putString("current_group_id", groupId).apply()
                                        currentGroupId = groupId
                                    }
                                }
                            )
                        }

                        // 3. Authenticated & verified user in an active group: proceed to squad screen or map
                        else -> {
                            val activeUser = currentUser!!
                            val displayName = activeUser.displayName?.ifBlank { "Operative" } ?: "Operative"
                            var members by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
                            var isProfileLoaded by remember { mutableStateOf(false) }
                            var showLeaveGroupDialog by remember { mutableStateOf(false) }

                            // Sync user profile and listen for updates from RTDB under currentGroupId
                            DisposableEffect(activeUser.uid, currentGroupId) {
                                prefs.edit().putString("current_user_id", activeUser.uid).apply()
                                prefs.edit().putString("current_group_id", currentGroupId).apply()

                                val self = GroupMember(
                                    id = activeUser.uid,
                                    name = displayName,
                                    isOnline = true,
                                    status = "safe"
                                )
                                UserRepository.saveUser(currentGroupId, self) { success ->
                                    if (success) {
                                        UserRepository.setOnlineStatus(currentGroupId, activeUser.uid, true)
                                    }
                                }

                                var listener: ValueEventListener? = null
                                listener = UserRepository.observeAllUsers(currentGroupId) { liveMembers ->
                                    val validMembers = liveMembers.filter { it.id.isNotBlank() && it.name.isNotBlank() }
                                    members = validMembers

                                    // If user profile/UUID was deleted from RTDB after initial load, push back to first login page
                                    if (isProfileLoaded && validMembers.none { it.id == activeUser.uid }) {
                                        listener?.let { UserRepository.removeObserver(currentGroupId, it) }
                                        UserRepository.setOnlineStatus(currentGroupId, activeUser.uid, false)
                                        prefs.edit().clear().apply()
                                        AuthManager.signOut()
                                        currentGroupId = ""
                                        currentUser = null
                                    } else if (validMembers.any { it.id == activeUser.uid }) {
                                        isProfileLoaded = true
                                    }
                                }

                                onDispose {
                                    listener?.let { UserRepository.removeObserver(currentGroupId, it) }
                                    if (currentUser != null && currentGroupId.isNotBlank()) {
                                        UserRepository.setOnlineStatus(currentGroupId, activeUser.uid, false)
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
                                            UserRepository.setStatus(currentGroupId, activeUser.uid, status)
                                        }
                                        if (status == SafetyStatus.DANGER) {
                                            lifecycleScope.launch {
                                                DistressAlert.sendIfAlone(this@MainActivity, currentGroupId, activeUser.uid, displayName)
                                            }
                                        }
                                        status != null
                                    }
                                )
                            }

                            var emergencyContacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }

                            DisposableEffect(activeUser.uid, currentGroupId) {
                                val contactListener = UserRepository.observeEmergencyContacts(currentGroupId, activeUser.uid) { liveContacts ->
                                    emergencyContacts = liveContacts
                                    EmergencyContactStore.save(this@MainActivity, liveContacts)
                                }

                                onDispose {
                                    UserRepository.removeEmergencyContactsObserver(currentGroupId, activeUser.uid, contactListener)
                                }
                            }

                            // Sensor pipeline: GPS + barometer + MapTiler ground-truth elevation
                            val context = LocalContext.current

                            var hasLocationPermission by remember {
                                mutableStateOf(
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                )
                            }
                            val locationPermissionLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.RequestPermission()
                            ) { granted -> hasLocationPermission = granted }

                            LaunchedEffect(Unit) {
                                if (!hasLocationPermission) {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            }

                            val barometerReader = remember { BarometerReader(context) }
                            val elevationClient = remember { ElevationLookupClient() }
                            val altitudeFusion = remember { AltitudeFusion() }

                            LaunchedEffect(activeUser.uid, currentGroupId, hasLocationPermission) {
                                if (!hasLocationPermission) return@LaunchedEffect

                                launch {
                                    barometerReader.pressureReadings().collect { pressure ->
                                        altitudeFusion.onPressureReading(pressure)
                                    }
                                }

                                var lastLoggedLocation: Location? = null

                                rawLocationUpdates(context).collect { location ->
                                    val accuracy = if (location.hasAccuracy()) location.accuracy else null
                                    val movedMeters = lastLoggedLocation?.let { prev ->
                                        val results = FloatArray(1)
                                        Location.distanceBetween(
                                            prev.latitude, prev.longitude,
                                            location.latitude, location.longitude,
                                            results
                                        )
                                        results[0]
                                    }
                                    Log.d(
                                        "PositionJitter",
                                        "lat=${location.latitude} lng=${location.longitude} " +
                                            "accuracy=$accuracy movedMeters=$movedMeters"
                                    )
                                    lastLoggedLocation = location

                                    val groundTruth = elevationClient.groundElevationMeters(
                                        latitude = location.latitude,
                                        longitude = location.longitude,
                                        accuracyMeters = if (location.hasAccuracy()) location.accuracy else null
                                    )
                                    val fused = altitudeFusion.onLocationUpdate(
                                        location = location,
                                        groundTruthMeters = groundTruth,
                                        hasBarometerHardware = barometerReader.hasBarometerHardware
                                    )

                                    LocationRepository.pushLocation(
                                        groupId = currentGroupId,
                                        userId = activeUser.uid,
                                        latitude = location.latitude,
                                        longitude = location.longitude,
                                        clampedAltitudeMeters = fused.clampedAltitudeMeters,
                                        classification = fused.classification
                                    )
                                }
                            }

                            var showAddContact by remember { mutableStateOf(false) }
                            var pendingContact by remember { mutableStateOf<EmergencyContact?>(null) }
                            var contactToDelete by remember { mutableStateOf<EmergencyContact?>(null) }

                            LaunchedEffect(activeUser.uid) {
                                LocationHelper.currentLocation(this@MainActivity)
                            }

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
                                        openSmsComposer(this@MainActivity, contact.phone, message)
                                    }
                                }
                            }

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
                                        UserRepository.addEmergencyContact(currentGroupId, activeUser.uid, name, phone) { saved ->
                                            if (!saved) {
                                                Toast.makeText(this, "Couldn't save contact", Toast.LENGTH_SHORT).show()
                                            }
                                        }
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
                                            UserRepository.deleteEmergencyContact(currentGroupId, activeUser.uid, contact.name)
                                            contactToDelete = null
                                        }) { Text("Remove") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { contactToDelete = null }) { Text("Cancel") }
                                    }
                                )
                            }

                            // Leave Group Confirmation Dialog
                            if (showLeaveGroupDialog) {
                                AlertDialog(
                                    onDismissRequest = { showLeaveGroupDialog = false },
                                    title = { Text("Leave Group?") },
                                    text = { Text("Are you sure you want to leave group '$currentGroupId'? Your profile and location data will be removed from this squad.") },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                showLeaveGroupDialog = false
                                                val leavingGroupId = currentGroupId
                                                UserRepository.leaveGroup(leavingGroupId, activeUser.uid) {
                                                    prefs.edit().remove("current_group_id").apply()
                                                    currentGroupId = ""
                                                }
                                            }
                                        ) {
                                            Text("Leave", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showLeaveGroupDialog = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }

                            // Group/map toggle via bottom bar
                            when (selectedTab) {
                                AppTab.GROUP -> MainGroupScreen(
                                    currentUserName = displayName,
                                    groupId = currentGroupId,
                                    members = members,
                                    emergencyContacts = emergencyContacts,
                                    onLeaveGroupClick = { showLeaveGroupDialog = true },
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
                                        if (myMember != null) showAddContact = true
                                    },
                                    onNudgeMember = { member ->
                                        UserRepository.nudgeUser(currentGroupId, member.id)
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

                                AppTab.MAP -> GlassBuildingMapScreen(
                                    groupId = currentGroupId,
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
