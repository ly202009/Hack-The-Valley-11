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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.htv11.ui.theme.Htv11Theme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            Htv11Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DuressDashboardScreen(
                        modifier = Modifier.padding(innerPadding),
                        onOpenAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DuressDashboardScreen(
    modifier: Modifier = Modifier,
    onOpenAccessibilitySettings: () -> Unit
) {
    val state by VolumeDuressManager.detectorState.collectAsState()
    var resetTimeout by remember { mutableStateOf(2000f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🚨 Volume Duress Detector",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Status Card
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
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.isTriggered) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Pattern: ${state.patternType ?: "None (Waiting)"}",
                    fontSize = 14.sp,
                    color = if (state.isTriggered) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Progress: Block ${state.currentBlock + 1}/3 (Half ${state.currentHalf + 1})",
                    fontSize = 14.sp,
                    color = if (state.isTriggered) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Live Press Counters Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Volume UP", fontSize = 14.sp, color = Color.Gray)
                    Text(text = "${state.upCountInSequence}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
                }
                HorizontalDivider(
                    modifier = Modifier
                        .height(50.dp)
                        .width(1.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Volume DOWN", fontSize = 14.sp, color = Color.Gray)
                    Text(text = "${state.downCountInSequence}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                }
            }
        }

        // Simulate Buttons for Testing
        Text(text = "Simulate Volume Presses (Live Test):", fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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

        // Reset Button
        OutlinedButton(
            onClick = { VolumeDuressManager.reset() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset Detector State")
        }

        // Adjustable Reset Timer Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Reset Timer: ${resetTimeout.toInt()} ms", fontSize = 14.sp)
            Slider(
                value = resetTimeout,
                onValueChange = {
                    resetTimeout = it
                    VolumeDuressManager.resetTimeoutMillis = it.toLong()
                },
                valueRange = 500f..5000f,
                steps = 9
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Enable Background Service Button
        Button(
            onClick = onOpenAccessibilitySettings,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Text("⚙️ Enable Background Accessibility Service")
        }
    }
}
