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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.htv11.location.AltitudeFusion
import com.example.htv11.location.BarometerReader
import com.example.htv11.location.rawLocationUpdates
import com.example.htv11.map.ElevationLookupClient
import com.example.htv11.ui.AppTab
import com.example.htv11.ui.GlassBuildingMapScreen
import com.example.htv11.ui.KazeBottomBar
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

                            // Sensor pipeline: GPS + barometer + MapTiler ground-truth elevation,
                            // fused into a floor classification, pushed to Firebase. Lives here
                            // (not inside GlassBuildingMapScreen) specifically so it runs the whole
                            // time you're signed in, regardless of which tab is selected — opening
                            // the Map tab no longer restarts the altitude-fusion startup baseline.
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
                                    // TEMPORARY diagnostic logging for the position-jitter investigation.
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
                                }
                            }

                            // Group/map toggle via the bottom bar — plain window swap, no
                            // transition. Firebase sync and the sensor pipeline above keep
                            // running either way.
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
