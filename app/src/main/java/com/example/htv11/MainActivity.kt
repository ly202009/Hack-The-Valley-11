package com.example.htv11

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.htv11.ui.theme.Htv11Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Htv11Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var showPinDialog by remember { mutableStateOf(false) }
    var isUnlocked by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isUnlocked) {
            Text(
                text = "Welcome to Squad Mesh!",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Everything looks perfectly normal here.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { isUnlocked = false }) {
                Text("Lock App")
            }
        } else {
            Text(
                text = "App is Locked",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { showPinDialog = true }) {
                Text("Enter PIN to Unlock")
            }
        }
    }

    if (showPinDialog) {
        SecretPinDialog(
            onDismiss = { showPinDialog = false },
            onUnlockSuccess = {
                isUnlocked = true
                showPinDialog = false
            }
        )
    }
}

@Composable
fun SecretPinDialog(
    onDismiss: () -> Unit,
    onUnlockSuccess: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    // Hardcoded for demonstration: 
    // 1234 is the "normal" PIN.
    // 6767 is the "duress" PIN that triggers the silent alarm.
    val normalPin = "1234"
    val duressPin = "6767"
    val pinLength = 4

    // Helper function to handle PIN submission logic
    fun submitPin(enteredPin: String) {
        when (enteredPin) {
            normalPin -> {
                // Normal unlock
                onUnlockSuccess()
            }
            duressPin -> {
                // Duress unlock! Send silent signal.
                sendSilentAlertToServer()
                // Let the user in anyway
                onUnlockSuccess()
            }
            else -> {
                // Wrong PIN
                showError = true
                pin = "" // Optionally clear the PIN field on error so they can type again quickly
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Security PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { newValue ->
                        // Only allow numbers and max length of pinLength
                        if (newValue.length <= pinLength && newValue.all { it.isDigit() }) {
                            pin = newValue
                            showError = false // Hide error when user types

                            // Auto-submit when the required length is reached
                            if (newValue.length == pinLength) {
                                submitPin(newValue)
                            }
                        }
                    },
                    label = { Text("PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = showError,
                    singleLine = true
                )
                if (showError) {
                    Text(
                        text = "Incorrect PIN. Try again.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Hint: Normal PIN is 1234. Duress PIN is 6767.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            // No manual submit button needed since it auto-submits
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Simulates sending a silent SOS signal to a backend server.
 * In a real application, this would use Retrofit/Ktor to hit an API.
 */
fun sendSilentAlertToServer() {
    // We launch this in a background thread so it doesn't block the UI
    CoroutineScope(Dispatchers.IO).launch {
        Log.e("SECURITY_ALERT", "DURESS PIN ENTERED! Silently alerting group members...")
        // TODO: Make network request to server here
        // e.g., apiService.sendSosAlert(userId, location)
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    Htv11Theme {
        MainScreen()
    }
}
