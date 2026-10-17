package com.example.htv11

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.htv11.ui.theme.Htv11Theme
import com.google.firebase.database.PropertyName

// 1. Database-ready model: matches Firebase Realtime DB / Firestore documents
data class GroupMember(
    var id: String = "",
    var name: String = "",
    @get:PropertyName("isOnline") @set:PropertyName("isOnline")
    var isOnline: Boolean = true,
    var lastNudged: Long = 0L,
    var status: String = "safe"
)

data class EmergencyContact(
    var name: String = "",
    var phone: String = ""
)

@Composable
fun MainGroupScreen(
    currentUserName: String = "Daniel",
    members: List<GroupMember>,
    emergencyContacts: List<EmergencyContact> = emptyList(),
    onNudgeMember: (GroupMember) -> Unit = {},
    onEmergencyContactClick: (EmergencyContact) -> Unit = {},
    onAddEmergencyContactClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Reuse the background from RegistrationScreen
        DynamicBlueGridBackground()

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 48.dp, bottom = 32.dp)
        ) {
            // --- SECTION 1: KAZE SQUAD ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "KAZE SQUAD",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Group Count: ${members.size}",
                            fontSize = 13.sp,
                            color = Color(0xFF8BB5E2)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0x2200FFCC), RoundedCornerShape(20.dp))
                            .border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = currentUserName,
                            color = Color(0xFF00FFCC),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(
                items = members,
                key = { "squad_${it.id}" }
            ) { member ->
                MemberCard(
                    member = member,
                    isSelf = member.name.equals(currentUserName, ignoreCase = true),
                    onNudgeClick = { onNudgeMember(member) }
                )
            }

            // --- SECTION 2: EMERGENCY CONTACTS ---
            item {
                Spacer(modifier = Modifier.height(20.dp))

                // Title and "+" button on the exact same line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EMERGENCY CONTACTS",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Button(
                        onClick = onAddEmergencyContactClick,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x2200FFCC),
                            contentColor = Color(0xFF00FFCC)
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .size(36.dp)
                            .border(1.dp, Color(0xFF00FFCC), CircleShape)
                    ) {
                        Text(
                            text = "+",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00FFCC)
                        )
                    }
                }

                Text(
                    text = "Contact Count: ${emergencyContacts.size}",
                    fontSize = 13.sp,
                    color = Color(0xFF8BB5E2)
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            items(
                items = emergencyContacts,
                key = { "emergency_${it.name}" }
            ) { contact ->
                EmergencyContactCard (
                    contact = contact,
                    onContactClick = { onEmergencyContactClick(contact) }
                )
            }
        }
    }
}

@Composable
fun MemberCard(
    member: GroupMember,
    isSelf: Boolean,
    onNudgeClick: () -> Unit
) {
    // Your own card never shows "in danger": whoever is looking at your screen must see nothing wrong.
    val actualStatus = SafetyStatus.fromKey(member.status)
    val status = if (isSelf && actualStatus == SafetyStatus.DANGER) SafetyStatus.SAFE else actualStatus
    val inDanger = status == SafetyStatus.DANGER

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (inDanger) SafetyStatus.DANGER.color.copy(alpha = 0.18f) else Color(0x11FFFFFF),
                RoundedCornerShape(14.dp)
            )
            .border(
                width = if (inDanger) 2.dp else 1.dp,
                color = when {
                    inDanger -> SafetyStatus.DANGER.color
                    isSelf -> Color(0xFF00FFCC)
                    else -> Color(0x3300FFCC)
                },
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Status Indicator & Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val statusStr = member.status.ifBlank { "safe" }.lowercase()
                val (statusText, statusColor) = when (statusStr) {
                    "danger" -> "Danger" to Color(0xFFFF5252)
                    "unsure" -> "Unsure" to Color(0xFFFFC107)
                    else -> "Safe" to Color(0xFF00FFCC)
                }

                // Glow dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = statusColor,
                            shape = CircleShape
                        )
                )

                Column {
                    Text(
                        text = member.name + if (isSelf) " (You)" else "",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Row {
                        Text(
                            text = status.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = status.color
                        )
                        Text(
                            text = if (member.isOnline) " · Active Now" else " · Offline",
                            fontSize = 12.sp,
                            color = if (member.isOnline) Color(0xFF8BB5E2) else Color(0xFF425A77)
                        )
                    }
                }
            }

            // Nudge Action Button
            if (!isSelf) {
                Button(
                    onClick = onNudgeClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x3300FFCC),
                        contentColor = Color(0xFF00FFCC)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "Nudge",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun EmergencyContactCard(
    contact: EmergencyContact,
    onContactClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x11FFFFFF), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x3300FFCC), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = contact.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = contact.phone.ifBlank { "No phone listed" },
                    fontSize = 12.sp,
                    color = Color(0xFF8BB5E2)
                )
            }

            Button(
                onClick = onContactClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x3300FFCC),
                    contentColor = Color(0xFF00FFCC)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = "Contact",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainGroupScreenPreview() {
    Htv11Theme {
        MainGroupScreen(
            currentUserName = "Daniel",
            members = listOf(
                GroupMember(id = "1", name = "Daniel", isOnline = true, status = "safe"),
                GroupMember(id = "2", name = "Sean", isOnline = true, status = "safe"),
                GroupMember(id = "3", name = "Liwen", isOnline = false, status = "safe")
            )
        )
    }
}