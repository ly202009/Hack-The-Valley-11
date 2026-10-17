package com.example.htv11

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.htv11.ui.theme.Htv11Theme
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Htv11Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Dynamic background placed underneath
                        DynamicBlueGridBackground()

                        RegistrationScreen(
                            modifier = Modifier.padding(innerPadding),
                            onJoinGroup = { enteredName ->
                                // Next steps
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DynamicBlueGridBackground() {
    // 1. Set up infinite animations for the moving grid and blinking dots
    val infiniteTransition = rememberInfiniteTransition(label = "gridTransition")

    // Moving grid offset
    val offsetProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gridOffset"
    )

    // Pulse/Blink opacity
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gridPulse"
    )

    // Random star/data particles generated once
    val particles = remember {
        List(40) {
            Offset(Random.nextFloat(), Random.nextFloat()) to Random.nextFloat()
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // A. Draw rich deep navy/indigo back-gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF020B1E), // Deepest dark blue
                    Color(0xFF051C3B),
                    Color(0xFF030D26)
                )
            )
        )

        // B. Draw perspectived digital grids (horizontal and vertical lines)
        val gridSpacing = 80f
        val animatedOffset = offsetProgress % gridSpacing

        // Vertical Grid Lines
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = Color(0xFF0A51A4).copy(alpha = 0.15f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.5f
            )
            x += gridSpacing
        }

        // Horizontal Grid Lines (Moves downwards)
        var y = animatedOffset
        while (y < size.height) {
            drawLine(
                color = Color(0xFF0E73E1).copy(alpha = 0.12f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2f
            )
            y += gridSpacing
        }

        // C. Draw glowing digital dot intersections and data particles
        particles.forEach { (positionRatio, sizeMultiplier) ->
            val particleX = positionRatio.x * size.width
            val particleY = positionRatio.y * size.height
            val finalAlpha = (sizeMultiplier * pulseAlpha).coerceIn(0.1f, 0.8f)

            // Dynamic Data Nodes / Stars
            drawCircle(
                color = Color(0xFF00FFCC), // Sci-fi cyan
                center = Offset(particleX, particleY),
                radius = 3f * sizeMultiplier,
                alpha = finalAlpha
            )
            // Ambient Glow around particles
            drawCircle(
                color = Color(0xFF0288D1),
                center = Offset(particleX, particleY),
                radius = 8f * sizeMultiplier,
                alpha = finalAlpha * 0.4f
            )
        }
    }
}

@Composable
fun RegistrationScreen(
    modifier: Modifier = Modifier,
    onJoinGroup: (String) -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Screen Header
        Text(
            text = "Welcome to KAZE",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Device not recognized. Set up your profile to continue.",
            fontSize = 14.sp,
            color = Color(0xFF8BB5E2) // Softer digital blue
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Futuristic Translucent Card Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x11FFFFFF), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0x3300FFCC), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name Input Field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = Color(0xFF8BB5E2)) },
                    placeholder = { Text("e.g., Daniel", color = Color(0xFF425A77)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FFCC), // Glowing cyan when selected
                        unfocusedBorderColor = Color(0xFF0A51A4),
                        focusedLabelColor = Color(0xFF00FFCC),
                        unfocusedLabelColor = Color(0xFF8BB5E2),
                        cursorColor = Color(0xFF00FFCC)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (name.isNotBlank()) onJoinGroup(name.trim())
                        }
                    )
                )

                // Join Group Button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onJoinGroup(name.trim())
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF), // Digital neon cyan
                        contentColor = Color(0xFF020B1E),   // Contrasting dark text
                        disabledContainerColor = Color(0x22FFFFFF),
                        disabledContentColor = Color(0x44FFFFFF)
                    )
                ) {
                    Text(
                        text = "Join Group",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RegistrationScreenPreview() {
    Htv11Theme {
        Box(modifier = Modifier.fillMaxSize()) {
            DynamicBlueGridBackground()
            RegistrationScreen()
        }
    }
}