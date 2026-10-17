package com.example.htv11

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CheckUpFormScreen(
    initialFriendName: String,
    modifier: Modifier = Modifier,
    initialShowForm: Boolean = false,
    onSendNotification: (String) -> Unit,
    onBack: () -> Unit
) {
    var friendNameInput by remember { mutableStateOf(initialFriendName) }
    var isFormVisible by remember { mutableStateOf(initialShowForm) }
    var targetFriend by remember { mutableStateOf(initialFriendName) }

    // MCQ Form state
    var foundFriend by remember { mutableStateOf<Boolean?>(null) }
    var friendCondition by remember { mutableStateOf<String?>(null) }
    var serverStatusMessage by remember { mutableStateOf<String?>(null) }

    // Dialog states
    var show911Dialog by remember { mutableStateOf(false) }
    var countdown911 by remember { mutableStateOf(10) }

    var showNotSureDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Countdown effect for 911 dialog (must wait 10 seconds to close)
    LaunchedEffect(show911Dialog) {
        if (show911Dialog) {
            countdown911 = 10
            while (countdown911 > 0) {
                delay(1000L)
                countdown911--
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
            text = "🤝 Friend Check-Up & Safety",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        if (isFormVisible) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "📋 Check-In Form for $targetFriend",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Question 1: Have you physically found [Name]?
                    Text(
                        text = "1. Have you physically found $targetFriend?",
                        fontWeight = FontWeight.SemiBold
                    )
                    val q1Options = listOf(true to "Yes", false to "No")
                    q1Options.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = (foundFriend == value),
                                onClick = {
                                    foundFriend = value
                                    if (value == false) {
                                        friendCondition = null
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label)
                        }
                    }

                    // Question 2: Appears only if user's input to Q1 is Yes
                    if (foundFriend == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "2. How are they doing?",
                            fontWeight = FontWeight.SemiBold
                        )
                        val q2Options = listOf(
                            "Awake and responding normally",
                            "Unwell, confused, or unusually impaired",
                            "Unresponsive or difficult to wake",
                            "I'm not sure they're okay"
                        )
                        q2Options.forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = (friendCondition == option),
                                    onClick = { friendCondition = option }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = option)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Submit Button
                    Button(
                        onClick = {
                            if (foundFriend == false) {
                                serverStatusMessage = "Signal sent: $targetFriend not found."
                                coroutineScope.launch {
                                    delay(1500L)
                                    isFormVisible = false
                                    foundFriend = null
                                    friendCondition = null
                                    serverStatusMessage = null
                                }
                            } else if (foundFriend == true) {
                                when (friendCondition) {
                                    "Awake and responding normally" -> {
                                        serverStatusMessage = "Safe alert sent for $targetFriend."
                                        coroutineScope.launch {
                                            delay(1500L)
                                            isFormVisible = false
                                            foundFriend = null
                                            friendCondition = null
                                            serverStatusMessage = null
                                        }
                                    }
                                    "Unwell, confused, or unusually impaired",
                                    "Unresponsive or difficult to wake" -> {
                                        show911Dialog = true
                                    }
                                    "I'm not sure they're okay" -> {
                                        showNotSureDialog = true
                                    }
                                }
                            }
                        },
                        enabled = foundFriend != null && (foundFriend == false || friendCondition != null),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Submit Check-In")
                    }

                    if (serverStatusMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF388E3C))
                        ) {
                            Text(
                                text = serverStatusMessage!!,
                                color = Color.White,
                                modifier = Modifier.padding(12.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            isFormVisible = false
                            foundFriend = null
                            friendCondition = null
                            serverStatusMessage = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
        } else {
            // Home / Notification Sender Screen
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Send Check-Up Reminder Notification",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = friendNameInput,
                        onValueChange = { friendNameInput = it },
                        label = { Text("Friend's Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            val name = if (friendNameInput.isBlank()) "Friend" else friendNameInput
                            onSendNotification(name)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Send Check-Up Notification 🔔")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    targetFriend = if (friendNameInput.isBlank()) "Friend" else friendNameInput
                    isFormVisible = true
                    foundFriend = null
                    friendCondition = null
                    serverStatusMessage = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Check-In Form Manually")
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }

    // Dialog for 911 Instructions (Cannot be closed until 10 seconds elapse)
    if (show911Dialog) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissing early */ },
            title = {
                Text("🚨 EMERGENCY INSTRUCTIONS", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your friend $targetFriend may require immediate medical assistance.")
                    Text("Please call 911 immediately and follow all dispatcher instructions.", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (countdown911 > 0) "Dismissal locked for ${countdown911}s..." else "You may now close this notice.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        show911Dialog = false
                        isFormVisible = false
                        foundFriend = null
                        friendCondition = null
                        serverStatusMessage = null
                    },
                    enabled = countdown911 <= 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text(if (countdown911 > 0) "Wait (${countdown911}s)" else "I Understand & Called 911 (Close)")
                }
            }
        )
    }

    // Dialog for "Not Sure" State
    if (showNotSureDialog) {
        AlertDialog(
            onDismissRequest = { showNotSureDialog = false },
            title = {
                Text("⚠️ STAY WITH YOUR FRIEND", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Please stay with $targetFriend and monitor their condition.")
                    Text("We have notified the server to keep this incident open and are dispatching another friend to come assist you.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotSureDialog = false
                        isFormVisible = false
                        foundFriend = null
                        friendCondition = null
                        serverStatusMessage = null
                    }
                ) {
                    Text("Okay, I am staying with them (Close)")
                }
            }
        )
    }
}
