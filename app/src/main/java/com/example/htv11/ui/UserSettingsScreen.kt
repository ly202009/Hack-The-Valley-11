package com.example.htv11.ui

import android.widget.Toast
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.htv11.DynamicBlueGridBackground
import com.example.htv11.R
import com.example.htv11.ui.theme.Htv11Theme

private val ACCENT = Color(0xFF00FFCC)
private val SECONDARY_TEXT = Color(0xFF8BB5E2)
private val CARD_BORDER = Color(0x3300FFCC)
private val CARD_BACKGROUND = Color(0x11FFFFFF)
private val CAUTION_COLOR = Color(0xFFFFC107)

@Composable
fun UserSettingsScreen(
    displayName: String,
    photoUrl: String? = null,
    onSaveDisplayName: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onClose: () -> Unit,
    onDeleteAccountClick: () -> Unit = {},
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
                    text = "User Settings",
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

            ProfileCard(
                displayName = displayName,
                photoUrl = photoUrl,
                onSaveDisplayName = onSaveDisplayName
            )

            Spacer(modifier = Modifier.height(20.dp))

            PermissionsCard(
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenLocationSettings = onOpenLocationSettings
            )

            Spacer(modifier = Modifier.weight(1f))

            // No functionality yet — placeholder button only, per explicit instruction.
            Button(
                onClick = onDeleteAccountClick,
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
                    text = "Delete Account",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    displayName: String,
    photoUrl: String?,
    onSaveDisplayName: (String) -> Unit
) {
    val context = LocalContext.current
    var nameInput by remember(displayName) { mutableStateOf(displayName) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CARD_BACKGROUND, RoundedCornerShape(14.dp))
            .border(1.dp, CARD_BORDER, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Profile picture — tap-to-change is UI only for now (photos are set via the RTDB
            // console directly, not an in-app picker), but a real photoUrl already renders here.
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0x2200FFCC), CircleShape)
                    .border(1.dp, ACCENT, CircleShape)
                    .clickable {
                        Toast.makeText(context, "Photo picker coming soon", Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Your profile picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_camera),
                        contentDescription = "Change profile picture",
                        tint = ACCENT,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Display Name", color = SECONDARY_TEXT) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ACCENT,
                        unfocusedBorderColor = Color(0xFF0A51A4),
                        focusedLabelColor = ACCENT,
                        unfocusedLabelColor = SECONDARY_TEXT,
                        cursorColor = ACCENT
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) onSaveDisplayName(nameInput.trim())
                    },
                    enabled = nameInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ACCENT,
                        contentColor = Color(0xFF020B1E),
                        disabledContainerColor = Color(0x22FFFFFF),
                        disabledContentColor = Color(0x44FFFFFF)
                    )
                ) {
                    Text(text = "Save", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PermissionsCard(
    onOpenAccessibilitySettings: () -> Unit,
    onOpenLocationSettings: () -> Unit
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Recommended Permissions",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Icon(
                    painter = painterResource(R.drawable.ic_ghost_caution),
                    contentDescription = "Recommended",
                    tint = CAUTION_COLOR,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x2200FFCC),
                    contentColor = ACCENT
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, ACCENT)
            ) {
                Text(
                    text = "⚙️ Enable Silent Alarm Accessibility Service",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onOpenLocationSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x2200FFCC),
                    contentColor = ACCENT
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, ACCENT)
            ) {
                Text(
                    text = "📍 Enable Location Tracking Permissions",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UserSettingsScreenPreview() {
    Htv11Theme {
        UserSettingsScreen(
            displayName = "Daniel",
            onSaveDisplayName = {},
            onOpenAccessibilitySettings = {},
            onOpenLocationSettings = {},
            onClose = {}
        )
    }
}
