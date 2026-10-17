package com.example.htv11

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BluetoothDebugScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter = bluetoothManager?.adapter

    var isBroadcasting by remember { mutableStateOf(false) }
    var currentRssi by remember { mutableStateOf<Int?>(null) }
    var medianRssi by remember { mutableStateOf<Double?>(null) }
    val rssiWindow = remember { mutableStateListOf<Int>() }

    // App specific Service UUID to identify app-related signals
    val appServiceUuid = ParcelUuid.fromString("12345678-1234-5678-1234-567812345678")

    val advertiser = remember { bluetoothAdapter?.bluetoothLeAdvertiser }
    val scanner = remember { bluetoothAdapter?.bluetoothLeScanner }

    val advertiseCallback = remember {
        object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                isBroadcasting = true
            }
            override fun onStartFailure(errorCode: Int) {
                isBroadcasting = false
            }
        }
    }

    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val rssi = result.rssi
                currentRssi = rssi

                rssiWindow.add(rssi)
                if (rssiWindow.size > 20) {
                    rssiWindow.removeAt(0)
                }

                if (rssiWindow.isNotEmpty()) {
                    val sorted = rssiWindow.sorted()
                    medianRssi = if (sorted.size % 2 == 1) {
                        sorted[sorted.size / 2].toDouble()
                    } else {
                        (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    // Automatically start scanning upon entering debug screen
    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        permissionLauncher.launch(permissions.toTypedArray())

        if (bluetoothAdapter?.isEnabled == true) {
            val filter = ScanFilter.Builder()
                .setServiceUuid(appServiceUuid)
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            try {
                scanner?.startScan(listOf(filter), settings, scanCallback)
            } catch (_: SecurityException) {}
        }
    }

    // Stop scan when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            try {
                scanner?.stopScan(scanCallback)
                advertiser?.stopAdvertising(advertiseCallback)
            } catch (_: SecurityException) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        DynamicBlueGridBackground()

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "📶 BLE Proximity Debugger",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x11FFFFFF), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x3300FFCC), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Toggle to start/stop broadcasting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Broadcast App Signal",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Switch(
                            checked = isBroadcasting,
                            onCheckedChange = { checked ->
                                if (bluetoothAdapter?.isEnabled == true) {
                                    try {
                                        if (checked) {
                                            val settings = AdvertiseSettings.Builder()
                                                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                                                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                                                .setConnectable(false)
                                                .build()
                                            val data = AdvertiseData.Builder()
                                                .setIncludeDeviceName(false)
                                                .addServiceUuid(appServiceUuid)
                                                .build()
                                            advertiser?.startAdvertising(settings, data, advertiseCallback)
                                        } else {
                                            advertiser?.stopAdvertising(advertiseCallback)
                                            isBroadcasting = false
                                        }
                                    } catch (_: SecurityException) {}
                                }
                            }
                        )
                    }

                    Text(
                        text = if (isBroadcasting) "🟢 Broadcasting app signal..." else "🔴 Broadcast inactive",
                        fontSize = 12.sp,
                        color = if (isBroadcasting) Color(0xFF00FFCC) else Color(0xFF8BB5E2)
                    )

                    HorizontalDivider(color = Color(0x33FFFFFF))

                    // Number display 1: Current signal strength being received
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Current Signal Strength (RSSI)", fontSize = 13.sp, color = Color(0xFF8BB5E2))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (currentRssi != null) "$currentRssi dBm" else "Scanning...",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                    }

                    // Number display 2: Median signal being received over time
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Median Signal (Over Time)", fontSize = 13.sp, color = Color(0xFF8BB5E2))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (medianRssi != null) "%.1f dBm".format(medianRssi) else "Collecting...",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00FFCC)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8BB5E2))
            ) {
                Text(
                    text = "Back to Sign In",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
