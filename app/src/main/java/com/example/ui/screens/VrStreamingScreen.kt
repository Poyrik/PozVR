package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alvr.AlvrClientManager
import com.example.gearvr.GearVRBluetoothManager
import com.example.ui.components.CubeVisualizer3D
import com.example.ui.theme.VrAmber
import com.example.ui.theme.VrCyan
import com.example.ui.theme.VrGreen
import com.example.ui.theme.VrOutline
import com.example.ui.theme.VrRed

@Composable
fun VrStreamingScreen(
    alvrManager: AlvrClientManager,
    gearVRManager: GearVRBluetoothManager,
    onOpenSettings: () -> Unit,
    onStopStreaming: () -> Unit
) {
    BackHandler { onStopStreaming() }

    val connectionInfo by alvrManager.connectionInfo.collectAsState()
    val controllerState by gearVRManager.controllerState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("vr_streaming_screen")
    ) {
        // Dual-Eye VR Viewport Simulation / Split Display
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // Left Eye
            EyeViewport(
                eyeLabel = "LEFT EYE",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            // Center VR separator line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1E232E))
            )

            // Right Eye
            EyeViewport(
                eyeLabel = "RIGHT EYE",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        // Overlay HUD for Controller Diagnostics & Stream Metrics
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp, start = 16.dp, end = 16.dp)
                .testTag("vr_stream_hud"),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0B0E14).copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(1.dp, VrOutline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Controller 3DoF Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (controllerState.packetsPerSec > 0 || controllerState.isSimulated) VrGreen else VrAmber,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (controllerState.packetsPerSec > 0 || controllerState.isSimulated)
                            "GearVR 3DoF: ${controllerState.packetsPerSec} Hz"
                        else "GearVR: Unpaired",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Streaming FPS
                Text(
                    text = "Stream: ${connectionInfo.fps} FPS",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = VrCyan
                )

                // Latency
                Text(
                    text = "Ping: ${connectionInfo.latencyMs} ms",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF8B949E)
                )

                // Quick Settings Button
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("hud_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = VrCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Exit VR Button
                IconButton(
                    onClick = onStopStreaming,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("exit_stream_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit Streaming",
                        tint = VrRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Live Bottom Controller Button Indicator Overlay
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .testTag("vr_button_hud"),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0B0E14).copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(1.dp, VrOutline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudKeyIndicator("TRIG", controllerState.triggerPressed, VrCyan)
                HudKeyIndicator("PAD", controllerState.touchpadClicked, VrAmber)
                HudKeyIndicator("TOUCH", controllerState.isTouching, VrCyan)
                HudKeyIndicator("BACK", controllerState.backPressed, VrRed)
                HudKeyIndicator("HOME", controllerState.homePressed, Color(0xFFB388FF))
            }
        }
    }
}

@Composable
private fun EyeViewport(
    eyeLabel: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF07090D))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Subtle VR Reticle / Horizon Grid
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .border(1.dp, VrOutline.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(VrCyan.copy(alpha = 0.8f), CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = eyeLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF484F58),
                letterSpacing = 2.sp
            )
            Text(
                text = "SteamVR Stream Receiver Active",
                fontSize = 10.sp,
                color = Color(0xFF2E3846)
            )
        }
    }
}

@Composable
private fun HudKeyIndicator(label: String, active: Boolean, activeColor: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (active) activeColor else Color(0xFF1E232E)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color(0xFF0B0E14) else Color(0xFF8B949E),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
