package com.example.htv11

import android.widget.Toast
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

@Composable
fun NudgeTestScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val nudgeService = remember { NudgeService() }

    var senderUuid by remember { mutableStateOf("Device_A") }
    var targetUuid by remember { mutableStateOf("Device_B") }
    var myListeningUuid by remember { mutableStateOf("Device_B") }

    var isListening by remember { mutableStateOf(false) }
    var receivedNudgesLog by remember { mutableStateOf(listOf<String>()) }
    var sendStatus by remember { mutableStateOf<String?>(null) }

    // Start listening effect
    LaunchedEffect(isListening, myListeningUuid) {
        if (isListening && myListeningUuid.isNotBlank()) {
            nudgeService.listenForNudges(myListeningUuid) { senderId ->
                val logEntry = "🔔 Nudge received from $senderId!"
                receivedNudgesLog = receivedNudgesLog + logEntry
                Toast.makeText(context, logEntry, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "⚡ Firebase Nudge Tester",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Sender Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "📤 Send a Nudge",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                OutlinedTextField(
                    value = senderUuid,
                    onValueChange = { senderUuid = it },
                    label = { Text("Your Sender UUID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = targetUuid,
                    onValueChange = { targetUuid = it },
                    label = { Text("Target User UUID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (senderUuid.isBlank() || targetUuid.isBlank()) {
                            sendStatus = "Please enter both sender and target UUIDs."
                            return@Button
                        }
                        nudgeService.sendNudge(
                            senderUuid = senderUuid,
                            targetUuid = targetUuid,
                            onSuccess = {
                                sendStatus = "✅ Nudge successfully sent to $targetUuid!"
                                Toast.makeText(context, "Nudge sent!", Toast.LENGTH_SHORT).show()
                            },
                            onError = { e ->
                                sendStatus = "❌ Failed to send: ${e.localizedMessage}"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Text("Send Nudge 🚀")
                }

                if (sendStatus != null) {
                    Text(text = sendStatus!!, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Receiver Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "📥 Receive Nudges (Listener)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                OutlinedTextField(
                    value = myListeningUuid,
                    onValueChange = { myListeningUuid = it },
                    label = { Text("My UUID (To Listen On)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isListening
                )

                Button(
                    onClick = { isListening = !isListening },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) Color(0xFFD32F2F) else Color(0xFF388E3C)
                    )
                ) {
                    Text(if (isListening) "Stop Listening ⏹️" else "Start Listening for Nudges 🎧")
                }

                Text(
                    text = if (isListening) "🟢 Status: Listening for incoming nudges..." else "🔴 Status: Not listening",
                    fontSize = 13.sp,
                    color = if (isListening) Color(0xFF388E3C) else Color.Gray
                )
            }
        }

        // Live Log Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📜 Real-Time Nudge Log:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                if (receivedNudgesLog.isEmpty()) {
                    Text(
                        text = "No nudges received yet. Start listening and send a nudge from another device/emulator or yourself!",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                } else {
                    receivedNudgesLog.forEach { log ->
                        Text(text = log, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}
