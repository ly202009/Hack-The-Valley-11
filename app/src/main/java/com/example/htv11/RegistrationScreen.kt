package com.example.htv11

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.htv11.ui.theme.Htv11Theme
import kotlin.random.Random

fun generateRandomGroupId(): String {
    val charPool = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val code = (1..6).map { charPool[Random.nextInt(charPool.length)] }.joinToString("")
    return "KZ-$code"
}

@Composable
fun RegistrationScreen(
    modifier: Modifier = Modifier,
    currentDisplayName: String? = null,
    onSaveName: (name: String) -> Unit = {},
    onJoinGroup: (groupId: String) -> Unit = {},
    onCreateGroup: (groupId: String, groupName: String) -> Unit = { _, _ -> },
    emergencyContacts: List<EmergencyContact> = emptyList(),
    onEmergencyContactClick: (EmergencyContact) -> Unit = {},
    onDeleteEmergencyContactClick: (EmergencyContact) -> Unit = {},
    onAddEmergencyContactClick: () -> Unit = {},
    onOpenUserSettings: () -> Unit = {}
) {
    var nameInput by remember(currentDisplayName) { mutableStateOf(currentDisplayName ?: "") }
    var groupIdInput by remember { mutableStateOf("") }
    var newGroupNameInput by remember { mutableStateOf("") }
    var isCheckingGroup by remember { mutableStateOf(false) }
    var lastJoinAttemptTime by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val attemptJoinGroup: () -> Unit = {
        focusManager.clearFocus()
        val cleanGroup = groupIdInput.trim()

        if (cleanGroup.isNotBlank()) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastJoinAttemptTime
            if (elapsed < 5000L) {
                val remainingSecs = ((5000L - elapsed) / 1000L) + 1
                errorMessage = "Rate limit: Wait ${remainingSecs}s before testing another Group ID."
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            } else {
                lastJoinAttemptTime = now
                isCheckingGroup = true
                errorMessage = null

                AuthManager.ensureAuth { authenticated ->
                    if (!authenticated) {
                        isCheckingGroup = false
                        errorMessage = "Authentication failed. Check your network connection."
                        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                        return@ensureAuth
                    }
                    UserRepository.checkGroupExists(cleanGroup) { exists ->
                        isCheckingGroup = false
                        if (exists) {
                            onJoinGroup(cleanGroup)
                        } else {
                            errorMessage = "Group '$cleanGroup' not found in squad network."
                            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        DynamicBlueGridBackground()

        // Same scrolling mechanism as the group view (LazyColumn), not a plain
        // Column + verticalScroll — kept identical on purpose.
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 48.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (currentDisplayName.isNullOrBlank()) "KAZE MESH" else "SOLO MODE",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (currentDisplayName.isNullOrBlank()) {
                Text(
                    text = "Identify yourself to register your permanent user UUID.",
                    fontSize = 14.sp,
                    color = Color(0xFF8BB5E2),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x11FFFFFF), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x3300FFCC), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (currentDisplayName.isNullOrBlank()) {
                        // Stage 1: Enter User Name
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("User Name", color = Color(0xFF8BB5E2)) },
                            placeholder = { Text("e.g., Daniel", color = Color(0xFF425A77)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00FFCC),
                                unfocusedBorderColor = Color(0xFF0A51A4),
                                focusedLabelColor = Color(0xFF00FFCC),
                                unfocusedLabelColor = Color(0xFF8BB5E2),
                                cursorColor = Color(0xFF00FFCC)
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    if (nameInput.isNotBlank()) onSaveName(nameInput.trim())
                                }
                            )
                        )

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                if (nameInput.isNotBlank()) onSaveName(nameInput.trim())
                            },
                            enabled = nameInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color(0xFF020B1E),
                                disabledContainerColor = Color(0x22FFFFFF),
                                disabledContentColor = Color(0x44FFFFFF)
                            )
                        ) {
                            Text(
                                text = "Continue ->",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Stage 2: Group Join or Creation
                        OutlinedTextField(
                            value = groupIdInput,
                            onValueChange = {
                                groupIdInput = it.uppercase()
                                errorMessage = null
                            },
                            label = { Text("Group ID", color = Color(0xFF8BB5E2)) },
                            placeholder = { Text("e.g., KZ-8391", color = Color(0xFF425A77)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00FFCC),
                                unfocusedBorderColor = Color(0xFF0A51A4),
                                focusedLabelColor = Color(0xFF00FFCC),
                                unfocusedLabelColor = Color(0xFF8BB5E2),
                                cursorColor = Color(0xFF00FFCC)
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { attemptJoinGroup() }
                            )
                        )

                        errorMessage?.let { error ->
                            Text(
                                text = error,
                                color = Color(0xFFFF5252),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Join Existing Group Button
                        Button(
                            onClick = attemptJoinGroup,
                            enabled = groupIdInput.isNotBlank() && !isCheckingGroup,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color(0xFF020B1E),
                                disabledContainerColor = Color(0x22FFFFFF),
                                disabledContentColor = Color(0x44FFFFFF)
                            )
                        ) {
                            Text(
                                text = if (isCheckingGroup) "Connecting..." else "Join Squad Group",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color(0x3300FFCC)
                            )
                            Text(
                                text = "  OR  ",
                                color = Color(0xFF8BB5E2),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color(0x3300FFCC)
                            )
                        }

                        // Create New Group Option
                        OutlinedTextField(
                            value = newGroupNameInput,
                            onValueChange = { newGroupNameInput = it },
                            label = { Text("Group Name (optional)", color = Color(0xFF8BB5E2)) },
                            placeholder = { Text("e.g., Night Patrol", color = Color(0xFF425A77)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00FFCC),
                                unfocusedBorderColor = Color(0xFF0A51A4),
                                focusedLabelColor = Color(0xFF00FFCC),
                                unfocusedLabelColor = Color(0xFF8BB5E2),
                                cursorColor = Color(0xFF00FFCC)
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )

                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus()
                                val newGroupId = generateRandomGroupId()
                                onCreateGroup(newGroupId, newGroupNameInput.trim())
                            },
                            enabled = !isCheckingGroup,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00FFCC)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF00FFCC),
                                disabledContentColor = Color(0x44FFFFFF)
                            )
                        ) {
                            Text(
                                text = "+ Create New Squad Group",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Contacts are personal data (flat, tied to your profile, not a group), so they're
            // visible and editable here too, even before you've joined or created a squad.
            if (!currentDisplayName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                EmergencyContactsSection(
                    emergencyContacts = emergencyContacts,
                    onContactClick = onEmergencyContactClick,
                    onDeleteContactClick = onDeleteEmergencyContactClick,
                    onAddContactClick = onAddEmergencyContactClick
                )
            }
            } // end Column
            } // end item
        }

        // User Settings entry point — same badge MainGroupScreen uses, same destination.
        // Only once there's an identity to manage (i.e. past Stage 1).
        if (!currentDisplayName.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 20.dp)
                    .background(Color(0x2200FFCC), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenUserSettings)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentDisplayName,
                    color = Color(0xFF00FFCC),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun DynamicBlueGridBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "gridTransition")

    val offsetProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gridOffset"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gridPulse"
    )

    val particles = remember {
        List(40) {
            Offset(Random.nextFloat(), Random.nextFloat()) to Random.nextFloat()
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF020B1E),
                    Color(0xFF051C3B),
                    Color(0xFF030D26)
                )
            )
        )

        val gridSpacing = 80f
        val animatedOffset = offsetProgress % gridSpacing

        if (size.width.isFinite()) {
            val verticalLineCount = (size.width / gridSpacing).toInt() + 1
            for (i in 0..verticalLineCount) {
                val x = i * gridSpacing
                drawLine(
                    color = Color(0xFF0A51A4).copy(alpha = 0.15f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.5f
                )
            }
        }

        if (size.height.isFinite()) {
            val horizontalLineCount = (size.height / gridSpacing).toInt() + 1
            for (i in 0..horizontalLineCount) {
                val y = animatedOffset + i * gridSpacing
                if (y >= size.height) break
                drawLine(
                    color = Color(0xFF0E73E1).copy(alpha = 0.12f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2f
                )
            }
        }

        particles.forEach { (positionRatio, sizeMultiplier) ->
            val particleX = positionRatio.x * size.width
            val particleY = positionRatio.y * size.height
            val finalAlpha = (sizeMultiplier * pulseAlpha).coerceIn(0.1f, 0.8f)

            drawCircle(
                color = Color(0xFF00FFCC),
                center = Offset(particleX, particleY),
                radius = 3f * sizeMultiplier,
                alpha = finalAlpha
            )
            drawCircle(
                color = Color(0xFF0288D1),
                center = Offset(particleX, particleY),
                radius = 8f * sizeMultiplier,
                alpha = finalAlpha * 0.4f
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RegistrationScreenPreview() {
    Htv11Theme {
        RegistrationScreen()
    }
}
