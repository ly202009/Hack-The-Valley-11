package com.example.htv11

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Shown when someone nudges you. [onSubmit] returns false for an unknown PIN; for either valid
 * PIN it returns true and the dialog closes the same way, so nothing on screen reveals which was used.
 */
@Composable
fun NudgePinDialog(onSubmit: (pin: String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Check-in") },
        text = {
            Column {
                Text("You've been nudged. Enter your PIN.")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(PinStore.PIN_LENGTH)
                        pin = digits
                        showError = false
                        if (digits.length == PinStore.PIN_LENGTH && !onSubmit(digits)) {
                            pin = ""
                            showError = true
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
            }
        },
        confirmButton = {}
    )
}
