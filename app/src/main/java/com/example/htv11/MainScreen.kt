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

// 1. Database-ready model: matches Firebase Realtime DB / Firestore documents
data class GroupMember(
    val id: String = "",
    val name: String = "",
    val isOnline: Boolean = true,
    val lastNudged: Long = 0L
)

@Composable
fun MainGroupScreen(
    currentUserName: String = "Daniel",
    members: List<GroupMember>,
    onNudgeMember: (GroupMember) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Reuse the background from RegistrationScreen
        DynamicBlueGridBackground()

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Header Section
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
                        text = "Active Operatives: ${members.size}",
                        fontSize = 13.sp,
                        color = Color(0xFF8BB5E2)
                    )
                }

                // Profile badge for the active user
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

            Spacer(modifier = Modifier.height(20.dp))

            // Operatives List (Column)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(
                    items = members,
                    key = { it.id } // Ensures performant real-time additions/updates
                ) { member ->
                    MemberCard(
                        member = member,
                        isSelf = member.name.equals(currentUserName, ignoreCase = true),
                        onNudgeClick = { onNudgeMember(member) }
                    )
                }
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x11FFFFFF), RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = if (isSelf) Color(0xFF00FFCC) else Color(0x3300FFCC),
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
                // Glow dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (member.isOnline) Color(0xFF00FFCC) else Color(0xFF425A77),
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
                    Text(
                        text = if (member.isOnline) "Active Now" else "Offline",
                        fontSize = 12.sp,
                        color = if (member.isOnline) Color(0xFF8BB5E2) else Color(0xFF425A77)
                    )
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

@Preview(showBackground = true)
@Composable
fun MainGroupScreenPreview() {
    Htv11Theme {
        MainGroupScreen(
            currentUserName = "Daniel",
            members = listOf(
                GroupMember(id = "1", name = "Daniel", isOnline = true),
                GroupMember(id = "2", name = "Sean", isOnline = true),
                GroupMember(id = "3", name = "Liwen", isOnline = false)
            )
        )
    }
}