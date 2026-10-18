package com.example.htv11

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.htv11.ui.theme.Htv11Theme
import com.google.firebase.database.PropertyName

// 1. Database-ready model: matches Firebase Realtime DB / Firestore documents
data class GroupMember(
    var id: String = "",
    var name: String = "",
    @get:PropertyName("isOnline") @set:PropertyName("isOnline")
    var isOnline: Boolean = true,
    var status: String = "safe",
    var photoUrl: String = ""
)

data class EmergencyContact(
    var name: String = "",
    var phone: String = ""
)

@Composable
fun MainGroupScreen(
    currentUserName: String = "Daniel",
    groupName: String? = null,
    members: List<GroupMember>,
    netDistances: Map<String, Double> = emptyMap(),
    safeDistanceMeters: Float = 25f,
    emergencyContacts: List<EmergencyContact> = emptyList(),
    onNudgeMember: (GroupMember) -> Unit = {},
    onEmergencyContactClick: (EmergencyContact) -> Unit = {},
    onDeleteEmergencyContactClick: (EmergencyContact) -> Unit = {},
    onAddEmergencyContactClick: () -> Unit = {},
    onOpenGroupSettings: () -> Unit = {},
    onOpenUserSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val validMembers = members.filter { it.id.isNotBlank() && it.name.isNotBlank() }.distinctBy { it.id }

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
            // --- SECTION 1: KAZE MESH ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "KAZE MESH",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Icon(
                                painter = painterResource(R.drawable.ic_settings_cog),
                                contentDescription = "Group settings",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clickable(onClick = onOpenGroupSettings)
                            )
                        }
                        Text(
                            text = if (!groupName.isNullOrBlank()) {
                                "$groupName  ·  Count: ${validMembers.size}"
                            } else {
                                "Group Count: ${validMembers.size}"
                            },
                            fontSize = 13.sp,
                            color = Color(0xFF8BB5E2)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0x2200FFCC), RoundedCornerShape(20.dp))
                            .border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(20.dp))
                            .clickable(onClick = onOpenUserSettings)
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
                items = validMembers,
                key = { "squad_${it.id}" }
            ) { member ->
                MemberCard(
                    member = member,
                    isSelf = member.name.equals(currentUserName, ignoreCase = true) || (member.id.isNotBlank() && member.id == AuthManager.currentUser?.uid),
                    netDistanceMeters = netDistances[member.id],
                    safeDistanceMeters = safeDistanceMeters,
                    onNudgeClick = { onNudgeMember(member) }
                )
            }

            // --- SECTION 2: EMERGENCY CONTACTS ---
            item {
                Spacer(modifier = Modifier.height(20.dp))
                EmergencyContactsSection(
                    emergencyContacts = emergencyContacts,
                    onContactClick = onEmergencyContactClick,
                    onDeleteContactClick = onDeleteEmergencyContactClick,
                    onAddContactClick = onAddEmergencyContactClick
                )
            }
        }
    }
}

/**
 * The "EMERGENCY CONTACTS" header + add button + contact list, shared between the main squad
 * screen and the pre-group registration/chooser screen — contacts are personal data (stored flat
 * under the user, not the group), so they're visible either way.
 */
@Composable
fun EmergencyContactsSection(
    emergencyContacts: List<EmergencyContact>,
    onContactClick: (EmergencyContact) -> Unit,
    onDeleteContactClick: (EmergencyContact) -> Unit,
    onAddContactClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
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
                onClick = onAddContactClick,
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

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            emergencyContacts.forEach { contact ->
                EmergencyContactCard(
                    contact = contact,
                    onContactClick = { onContactClick(contact) },
                    onDeleteClick = { onDeleteContactClick(contact) }
                )
            }
        }
    }
}

@Composable
fun MemberCard(
    member: GroupMember,
    isSelf: Boolean,
    netDistanceMeters: Double? = null,
    safeDistanceMeters: Float = 25f,
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
                // Face photo, only when one's actually set — no fallback placeholder, layout
                // stays exactly as it was before for anyone without a photoUrl.
                if (member.photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = member.photoUrl,
                        contentDescription = "${member.name}'s photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color(0xFF00FFCC), CircleShape)
                    )
                }

                // Glow dot uses status.color so it stays green (0xFF00FFCC) when status is SAFE or facade cover
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = status.color,
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

                    // Bubble-distance indicator — separate concept from the status above (that's
                    // danger/duress; this is "how far away are they right now").
                    if (!isSelf && netDistanceMeters != null) {
                        val withinBubble = netDistanceMeters <= safeDistanceMeters
                        val bubbleColor = if (withinBubble) Color(0xFF00FFCC) else SafetyStatus.DANGER.color
                        Text(
                            text = "${if (withinBubble) "Safe" else "Unsafe"} ${netDistanceMeters.toInt()}m",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = bubbleColor
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
    onContactClick: () -> Unit,
    onDeleteClick: () -> Unit = {}
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDeleteClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x22FF5252),
                        contentColor = Color(0xFFFF5252)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.border(1.dp, Color(0xFFFF5252), RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "Delete",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
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
}

@Preview(showBackground = true)
@Composable
fun MainGroupScreenPreview() {
    Htv11Theme {
        MainGroupScreen(
            currentUserName = "Daniel",
            groupName = "KZ-1234",
            members = listOf(
                GroupMember(id = "1", name = "Daniel", isOnline = true, status = "safe"),
                GroupMember(id = "2", name = "Sean", isOnline = true, status = "safe"),
                GroupMember(id = "3", name = "Liwen", isOnline = false, status = "safe")
            )
        )
    }
}
