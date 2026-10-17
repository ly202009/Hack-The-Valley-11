package com.example.htv11

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.htv11.ui.theme.Htv11Theme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private var showFormState = mutableStateOf(false)
    private var friendNameState = mutableStateOf("Friend")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleIntent(intent)

        setContent {
            Htv11Theme {
                MainHubScreen(
                    initialShowForm = showFormState.value,
                    initialFriendName = friendNameState.value
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val showForm = intent?.getBooleanExtra("SHOW_CHECKUP_FORM", false) ?: false
        val friendName = intent?.getStringExtra("FRIEND_NAME") ?: "Friend"
        if (showForm) {
            showFormState.value = true
            friendNameState.value = friendName
        }
    }
}

@Composable
fun MainHubScreen(
    initialShowForm: Boolean = false,
    initialFriendName: String = "Friend"
) {
    var selectedTab by remember { mutableIntStateOf(1) } // Default to Check-Up tab if opened via notification, else 0

    LaunchedEffect(initialShowForm) {
        if (initialShowForm) {
            selectedTab = 1
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Text("🛡️", fontSize = 20.sp) },
                    label = { Text("Duress") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Text("🤝", fontSize = 20.sp) },
                    label = { Text("Check-Up") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Text("⚡", fontSize = 20.sp) },
                    label = { Text("Nudges") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            val context = LocalContext.current
            when (selectedTab) {
                0 -> VolumeDuressDashboardTab()
                1 -> CheckUpFormScreen(
                    initialFriendName = initialFriendName,
                    initialShowForm = initialShowForm,
                    onSendNotification = { name ->
                        CheckUpNotificationHelper.sendCheckUpNotification(context, name)
                    },
                    onBack = {}
                )
                2 -> NudgeTestScreen()
            }
        }
    }
}

@Composable
fun VolumeDuressDashboardTab() {
    val state by VolumeDuressManager.detectorState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🚨 Volume Duress Detector",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (state.isTriggered) Color(0xFFD32F2F) else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (state.isTriggered) "🚨 DURESS TRIGGERED! 🚨" else "🟢 Monitoring (Listening)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.isTriggered) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pattern: ${state.patternType ?: "None (Waiting)"}",
                    fontSize = 13.sp,
                    color = if (state.isTriggered) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Volume UP", fontSize = 13.sp, color = Color.Gray)
                    Text(text = "${state.upCountInSequence}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
                }
                HorizontalDivider(
                    modifier = Modifier
                        .height(40.dp)
                        .width(1.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Volume DOWN", fontSize = 13.sp, color = Color.Gray)
                    Text(text = "${state.downCountInSequence}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                }
            }
        }

        Text(text = "Simulate Volume Presses:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { VolumeDuressManager.onVolumePress(VolumeDuressDetector.VolumePress.UP) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
            ) {
                Text("Press UP")
            }
            Button(
                onClick = { VolumeDuressManager.onVolumePress(VolumeDuressDetector.VolumePress.DOWN) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                Text("Press DOWN")
            }
        }

        OutlinedButton(
            onClick = { VolumeDuressManager.reset() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset Detector State")
        }

        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("⚙️ Enable Background Accessibility Service")
        }
    }
}
