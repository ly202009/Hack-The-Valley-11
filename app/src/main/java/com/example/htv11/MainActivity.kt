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
import com.example.htv11.ui.GroupSettingsScreen
import com.example.htv11.ui.KazeBottomBar
import androidx.lifecycle.lifecycleScope
import com.example.htv11.ui.theme.Htv11Theme
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
                var currentGroupName by remember { mutableStateOf<String?>(null) }
                var justCreatedGroupId by remember { mutableStateOf<String?>(null) }

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
                                prefs.edit()
                                    .putString("user_uuid", verifiedUser.uid)
                                    .putString("current_user_id", verifiedUser.uid)
                                    .apply()
                            }
                            isCheckingSession = false
                        }
                    } else {
                        isCheckingSession = false
                    }
                }

                // Emergency contacts are flat, personal data (users/{uid}/emergency-contacts) —
                // not tied to group membership — so this whole cluster lives above the group/
                // no-group branching below and is shared by both the pre-group chooser screen
                // and the in-group squad screen.
                val activeUserForContacts = currentUser
                var emergencyContacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }

                DisposableEffect(activeUserForContacts?.uid) {
                    val uid = activeUserForContacts?.uid
                    if (uid == null) {
                        onDispose {}
                    } else {
                        val contactListener = UserRepository.observeEmergencyContacts(uid) { liveContacts ->
                            emergencyContacts = liveContacts
                            EmergencyContactStore.save(this@MainActivity, liveContacts)
                        }
                        onDispose {
                            UserRepository.removeEmergencyContactsObserver(uid, contactListener)
                        }
                    }
                }

                var showAddContact by remember { mutableStateOf(false) }
                var pendingContact by remember { mutableStateOf<EmergencyContact?>(null) }
                var contactToDelete by remember { mutableStateOf<EmergencyContact?>(null) }

                // Keep a recent location on hand for the silent alarm, which can't read it from the background.
                LaunchedEffect(activeUserForContacts?.uid) {
                    if (activeUserForContacts != null) {
                        LocationHelper.currentLocation(this@MainActivity)
                    }
                }

                val textContact: (EmergencyContact) -> Unit = { contact ->
                    val uid = activeUserForContacts?.uid
                    if (uid != null) {
                        val alertDisplayName = activeUserForContacts.displayName?.ifBlank { "User" } ?: "User"
                        Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show()
                        lifecycleScope.launch {
                            val location = LocationHelper.currentLocation(this@MainActivity)
                            val address = location?.let { LocationHelper.addressFor(this@MainActivity, it) }
                            val message = buildDangerMessage(alertDisplayName, location, address)

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
                }

                val alertPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    pendingContact?.let(textContact)
                    pendingContact = null
                }

                val onEmergencyContactClick: (EmergencyContact) -> Unit = { contact ->
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
                }

                if (showAddContact) {
                    AddEmergencyContactDialog(
                        onDismiss = { showAddContact = false },
                        onSave = { name, phone ->
                            showAddContact = false
                            val uid = activeUserForContacts?.uid
                            if (uid != null) {
                                UserRepository.addEmergencyContact(uid, name, phone) { saved ->
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
                                activeUserForContacts?.uid?.let { uid ->
                                    UserRepository.deleteEmergencyContact(uid, contact.name)
                                }
                                contactToDelete = null
                            }) { Text("Remove") }
                        },
                        dismissButton = {
                            TextButton(onClick = { contactToDelete = null }) { Text("Cancel") }
                        }
                    )
                }

                justCreatedGroupId?.let { newGroupId ->
                    GroupCreatedDialog(
                        groupId = newGroupId,
                        onDismiss = { justCreatedGroupId = null }
                    )
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
                            val activeUuid = currentUser?.uid ?: prefs.getString("user_uuid", "") ?: ""
                            RegistrationScreen(
                                modifier = Modifier
                                    .padding(innerPadding)
                                    .imePadding(),
                                currentDisplayName = activeDisplayName,
                                userUuid = activeUuid,
                                onSaveName = { enteredName ->
                                    AuthManager.signInAnonymously(
                                        displayName = enteredName,
                                        onSuccess = {
                                            val user = AuthManager.currentUser
                                            currentUser = user
                                            if (user != null) {
                                                prefs.edit()
                                                    .putString("user_uuid", user.uid)
                                                    .putString("current_user_id", user.uid)
                                                    .apply()
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
                                },
                                onJoinGroup = { groupId ->
                                    UserRepository.createGroup(groupId) {
                                        prefs.edit().putString("current_group_id", groupId).apply()
                                        currentGroupId = groupId
                                    }
                                },
                                onCreateGroup = { groupId, groupName ->
                                    UserRepository.createGroup(groupId, name = groupName) { success ->
                                        if (success) {
                                            prefs.edit().putString("current_group_id", groupId).apply()
                                            currentGroupId = groupId
                                            currentGroupName = groupName.ifBlank { null }
                                            justCreatedGroupId = groupId
                                        }
                                    }
                                },
                                emergencyContacts = emergencyContacts,
                                onEmergencyContactClick = onEmergencyContactClick,
                                onDeleteEmergencyContactClick = { contact -> contactToDelete = contact },
                                onAddEmergencyContactClick = { showAddContact = true }
                            )
                        }

                        // 3. Authenticated & verified user in an active group: proceed to squad screen or map
                        else -> {
                            val activeUser = currentUser!!
                            val displayName = activeUser.displayName?.ifBlank { "User" } ?: "User"
                            var members by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
                            var isProfileLoaded by remember { mutableStateOf(false) }
                            var showLeaveGroupDialog by remember { mutableStateOf(false) }

                            // Group Settings page state. Safe-distance toggle is visual-only for
                            // now (grays out the slider) — no alerting/behavior wired to it yet.
                            var showGroupSettings by remember { mutableStateOf(false) }
                            var safeDistanceMeters by remember { mutableStateOf(25f) }
                            var isSafeDistanceEnabled by remember { mutableStateOf(true) }

                            // Everyone's synced (not live) location, used both to compute net
                            // distance below and — same values — by the map when it's open.
                            var groupLocations by remember { mutableStateOf<List<GroupLocation>>(emptyList()) }
                            DisposableEffect(Unit) {
                                val listener = LocationRepository.observeAllLocations { locations ->
                                    groupLocations = locations
                                }
                                onDispose { LocationRepository.removeObserver(listener) }
                            }

                            // My own computed distance (meters) to each other current member.
                            var netDistances by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
                            DisposableEffect(activeUser.uid, currentGroupId) {
                                val listener = NetDistanceRepository.observeDistances(currentGroupId, activeUser.uid) { distances ->
                                    netDistances = distances
                                }
                                onDispose { NetDistanceRepository.removeObserver(currentGroupId, activeUser.uid, listener) }
                            }

                            // Sync user profile (flat, global — not scoped to currentGroupId) and
                            // register membership in that group's roster.
                            DisposableEffect(activeUser.uid, currentGroupId) {
                                prefs.edit().putString("current_user_id", activeUser.uid).apply()
                                prefs.edit().putString("user_uuid", activeUser.uid).apply()
                                prefs.edit().putString("current_group_id", currentGroupId).apply()

                                UserRepository.joinGroup(currentGroupId, activeUser.uid)
                                UserRepository.getGroupName(currentGroupId) { name -> currentGroupName = name }

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

                                var subscription: UserRepository.GroupMembersSubscription? = null
                                subscription = UserRepository.observeGroupMembers(currentGroupId) { liveMembers ->
                                    members = liveMembers

                                    // If removed from the group's roster after initial load, push back to group join screen
                                    if (isProfileLoaded && liveMembers.none { it.id == activeUser.uid }) {
                                        subscription?.let { UserRepository.removeGroupMembersObserver(it) }
                                        UserRepository.setOnlineStatus(activeUser.uid, false)
                                        prefs.edit().remove("current_group_id").apply()
                                        currentGroupId = ""
                                    } else if (liveMembers.any { it.id == activeUser.uid }) {
                                        isProfileLoaded = true
                                    }
                                }

                                onDispose {
                                    subscription?.let { UserRepository.removeGroupMembersObserver(it) }
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
                                            // This dialog only ever appears after a group nudge, so we're
                                            // always in a group here — status is already flipped above.
                                            lifecycleScope.launch {
                                                DistressAlert.send(this@MainActivity, displayName)
                                            }
                                        }
                                        status != null
                                    }
                                )
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

                            LaunchedEffect(activeUser.uid, hasLocationPermission) {
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
                                        userId = activeUser.uid,
                                        latitude = location.latitude,
                                        longitude = location.longitude,
                                        clampedAltitudeMeters = fused.clampedAltitudeMeters,
                                        classification = fused.classification
                                    )

                                    // Net distance to each other current member — computed from
                                    // already-synced locations for BOTH sides (this tick's fresh
                                    // fix is intentionally not used for self either), so this stays
                                    // consistent with how every other member computes their own
                                    // reverse-direction entry from the same synced stream.
                                    val mySyncedLocation = groupLocations.firstOrNull { it.userId == activeUser.uid }
                                    if (mySyncedLocation != null) {
                                        val distances = members
                                            .filter { it.id != activeUser.uid }
                                            .mapNotNull { member ->
                                                groupLocations.firstOrNull { it.userId == member.id }
                                                    ?.let { member.id to distance3dMeters(mySyncedLocation, it) }
                                            }
                                            .toMap()
                                        NetDistanceRepository.pushDistances(currentGroupId, activeUser.uid, distances)
                                    }
                                }
                            }

                            // Leave Group Confirmation Dialog
                            if (showLeaveGroupDialog) {
                                AlertDialog(
                                    onDismissRequest = { showLeaveGroupDialog = false },
                                    title = { Text("Leave Group?") },
                                    text = { Text("Are you sure you want to leave group '$currentGroupId'? You'll be removed from this squad's roster — your profile and emergency contacts stay put.") },
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

                            if (showGroupSettings) {
                                GroupSettingsScreen(
                                    groupId = currentGroupId,
                                    groupName = currentGroupName,
                                    safeDistanceMeters = safeDistanceMeters,
                                    onSafeDistanceChange = { safeDistanceMeters = it },
                                    isSafeDistanceEnabled = isSafeDistanceEnabled,
                                    onToggleSafeDistanceEnabled = { isSafeDistanceEnabled = it },
                                    onClose = { showGroupSettings = false },
                                    onLeaveGroupClick = { showLeaveGroupDialog = true },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            } else {
                                // Group/map toggle via bottom bar
                                when (selectedTab) {
                                    AppTab.GROUP -> MainGroupScreen(
                                        currentUserName = displayName,
                                        groupName = currentGroupName,
                                        members = members,
                                        netDistances = netDistances,
                                        safeDistanceMeters = safeDistanceMeters,
                                        emergencyContacts = emergencyContacts,
                                        onEmergencyContactClick = onEmergencyContactClick,
                                        onDeleteEmergencyContactClick = { contact -> contactToDelete = contact },
                                        onAddEmergencyContactClick = { showAddContact = true },
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
                                        onOpenGroupSettings = { showGroupSettings = true },
                                        modifier = Modifier.padding(innerPadding)
                                    )

                                    AppTab.MAP -> GlassBuildingMapScreen(
                                        groupId = currentGroupId,
                                        groupLocations = groupLocations,
                                        hasLocationPermission = hasLocationPermission,
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
}
