package com.example.htv11

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private const val MIN_PHONE_DIGITS = 3
private val PHONE_CHARS = Regex("[0-9+()\\-. ]*")

/** Asks for a contact's name and phone number. [onSave] gets the trimmed values. */
@Composable
fun AddEmergencyContactDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    val phoneValid = phone.matches(PHONE_CHARS) && phone.count { it.isDigit() } >= MIN_PHONE_DIGITS
    val canSave = name.isNotBlank() && phoneValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add emergency contact") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = phone.isNotEmpty() && !phoneValid,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(name.trim(), phone.trim()) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun hasSmsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

/**
 * Sends [message] to [phone] straight away through the phone's own SMS, with no messaging app
 * opened. Returns false if it couldn't be sent, so the caller can fall back to [openSmsComposer].
 */
fun sendSms(context: Context, phone: String, message: String): Boolean {
    val number = phone.filter { it.isDigit() || it == '+' }
    if (number.isEmpty() || !hasSmsPermission(context)) return false
    return try {
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        // Addresses can push the message past one SMS, so it is split into parts when needed.
        manager.sendMultipartTextMessage(number, null, manager.divideMessage(message), null, null)
        true
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: RuntimeException) {
        false
    }
}

/**
 * Opens the phone's messaging app with a text to [phone] already written. The user still has
 * to press send, and no SMS permission is needed.
 */
fun openSmsComposer(context: Context, phone: String, message: String) {
    val number = phone.filter { it.isDigit() || it == '+' }
    if (number.isEmpty()) {
        Toast.makeText(context, "No phone number saved", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
        .putExtra("sms_body", message)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No messaging app found", Toast.LENGTH_SHORT).show()
    }
}
