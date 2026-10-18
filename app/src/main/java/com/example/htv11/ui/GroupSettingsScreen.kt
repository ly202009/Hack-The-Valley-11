package com.example.htv11.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.htv11.DynamicBlueGridBackground
import com.example.htv11.R
import com.example.htv11.ui.theme.Htv11Theme

private val ACCENT = Color(0xFF00FFCC)
private val SECONDARY_TEXT = Color(0xFF8BB5E2)
private val CARD_BORDER = Color(0x3300FFCC)
private val CARD_BACKGROUND = Color(0x11FFFFFF)

private const val SAFE_DISTANCE_MIN_METERS = 5f
private const val SAFE_DISTANCE_MAX_METERS = 50f
private const val SAFE_DISTANCE_STEP_METERS = 5f

// (max - min) / step - 1 discrete stops between the endpoints, so the slider
// snaps to every 5m from 5 to 50.
private const val SAFE_DISTANCE_SLIDER_STEPS =
    ((SAFE_DISTANCE_MAX_METERS - SAFE_DISTANCE_MIN_METERS) / SAFE_DISTANCE_STEP_METERS).toInt() - 1

@Composable
fun GroupSettingsScreen(
    groupId: String,
    groupName: String?,
    safeDistanceMeters: Float,
    onSafeDistanceChange: (Float) -> Unit,
    isSafeDistanceEnabled: Boolean,
    onToggleSafeDistanceEnabled: (Boolean) -> Unit,
    onClose: () -> Unit,
    onLeaveGroupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        DynamicBlueGridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 48.dp, bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Group Settings",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, ACCENT, CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Close",
                        tint = ACCENT,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            GroupIdCard(groupId = groupId, groupName = groupName)

            Spacer(modifier = Modifier.height(20.dp))

            SafeDistanceCard(
                safeDistanceMeters = safeDistanceMeters,
                onSafeDistanceChange = onSafeDistanceChange,
                isEnabled = isSafeDistanceEnabled,
                onToggleEnabled = onToggleSafeDistanceEnabled
            )

            Spacer(modifier = Modifier.height(20.dp)) // Adjust to 24.dp or more if you want wider spacing

            ServerNudgeLengthCard()
            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onLeaveGroupClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x33FF5252),
                    contentColor = Color(0xFFFF5252)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(14.dp))
            ) {
                Text(
                    text = "Leave Group",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GroupIdCard(groupId: String, groupName: String?) {
    val clipboardManager = LocalClipboardManager.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CARD_BACKGROUND, RoundedCornerShape(14.dp))
            .border(1.dp, CARD_BORDER, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = groupName?.ifBlank { null } ?: "Unnamed Squad",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = groupId,
                    fontSize = 13.sp,
                    color = SECONDARY_TEXT
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .border(1.dp, ACCENT, CircleShape)
                    .clickable { clipboardManager.setText(AnnotatedString(groupId)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = "Copy group ID",
                    tint = ACCENT,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SafeDistanceCard(
    safeDistanceMeters: Float,
    onSafeDistanceChange: (Float) -> Unit,
    isEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CARD_BACKGROUND, RoundedCornerShape(14.dp))
            .border(1.dp, CARD_BORDER, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Group Bubble",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.alpha(if (isEnabled) 1f else 0.35f)
                )

                // Visual on/off only for now — no behavior wired to this yet,
                // it just grays out the slider below.
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ACCENT,
                        checkedTrackColor = Color(0x3300FFCC),
                        checkedBorderColor = ACCENT,
                        uncheckedThumbColor = SECONDARY_TEXT,
                        uncheckedTrackColor = Color(0xFF0A1B33),
                        uncheckedBorderColor = SECONDARY_TEXT
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isEnabled) 1f else 0.35f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Slider(
                    value = safeDistanceMeters,
                    onValueChange = onSafeDistanceChange,
                    valueRange = SAFE_DISTANCE_MIN_METERS..SAFE_DISTANCE_MAX_METERS,
                    steps = SAFE_DISTANCE_SLIDER_STEPS,
                    enabled = isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = ACCENT,
                        activeTrackColor = ACCENT,
                        inactiveTrackColor = Color(0x3300FFCC),
                        disabledThumbColor = SECONDARY_TEXT,
                        disabledActiveTrackColor = SECONDARY_TEXT,
                        disabledInactiveTrackColor = Color(0x330A1B33)
                    ),
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "${safeDistanceMeters.toInt()}m",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ACCENT
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupSettingsScreenPreview() {
    Htv11Theme {
        GroupSettingsScreen(
            groupId = "KZ-8391",
            groupName = "Night Patrol",
            safeDistanceMeters = 25f,
            onSafeDistanceChange = {},
            isSafeDistanceEnabled = true,
            onToggleSafeDistanceEnabled = {},
            onClose = {},
            onLeaveGroupClick = {}
        )
    }
}

@Composable
private fun ServerNudgeLengthCard() {
    var hours by remember { mutableStateOf("0") }
    var minutes by remember { mutableStateOf("30") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CARD_BACKGROUND, RoundedCornerShape(14.dp))
            .border(1.dp, CARD_BORDER, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Server Nudge Length",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hour Box
                OutlinedTextField(
                    value = hours,
                    onValueChange = { input ->
                        hours = input.filter { it.isDigit() }.take(2)
                    },
                    label = { Text("Hours", color = SECONDARY_TEXT) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ACCENT,
                        unfocusedBorderColor = CARD_BORDER,
                        focusedLabelColor = ACCENT,
                        unfocusedLabelColor = SECONDARY_TEXT,
                        cursorColor = ACCENT
                    )
                )

                // Minutes Box
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { input ->
                        minutes = input.filter { it.isDigit() }.take(2)
                    },
                    label = { Text("Minutes", color = SECONDARY_TEXT) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ACCENT,
                        unfocusedBorderColor = CARD_BORDER,
                        focusedLabelColor = ACCENT,
                        unfocusedLabelColor = SECONDARY_TEXT,
                        cursorColor = ACCENT
                    )
                )
            }
        }
    }
}