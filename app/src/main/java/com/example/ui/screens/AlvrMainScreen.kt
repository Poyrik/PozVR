package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alvr.AlvrClientManager
import com.example.alvr.AlvrStreamState
import com.example.gearvr.GearVRBluetoothManager
import com.example.gearvr.GearVRConnectionState
import com.example.ui.theme.VrAmber
import com.example.ui.theme.VrBackground
import com.example.ui.theme.VrCyan
import com.example.ui.theme.VrGreen
import com.example.ui.theme.VrOutline
import com.example.ui.theme.VrRed
import com.example.ui.theme.VrSurface
import com.example.ui.theme.VrSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlvrMainScreen(
    alvrManager: AlvrClientManager,
    gearVRManager: GearVRBluetoothManager,
    onOpenControllerSettings: () -> Unit,
    onStartVrStream: () -> Unit
) {
    val connectionInfo by alvrManager.connectionInfo.collectAsState()
    val controllerState by gearVRManager.controllerState.collectAsState()

    var serverIpInput by remember { mutableStateOf(connectionInfo.serverIp) }
    var serverPortInput by remember { mutableStateOf(connectionInfo.serverPort.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(VrCyan.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = "ALVR Logo",
                                tint = VrCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ALVR Client",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "PhoneVR + Gear VR 3DoF Input",
                                style = MaterialTheme.typography.bodySmall,
                                color = VrCyan
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenControllerSettings,
                        modifier = Modifier.testTag("top_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Controller Settings",
                            tint = VrCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VrBackground)
            )
        },
        containerColor = VrBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Controller Integration Banner & Settings Launcher
            item {
                ControllerStatusBanner(
                    controllerState = controllerState,
                    onOpenSettings = onOpenControllerSettings
                )
            }

            // 2. PC Server Streaming Connection Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pc_server_card"),
                    shape = RoundedCornerShape(16.dp),
                    color = VrSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VrOutline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lan,
                                    contentDescription = "Server Network",
                                    tint = VrCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "STEAMVR PC STREAMER",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Connection status chip
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (connectionInfo.streamState == AlvrStreamState.STREAMING) Color(0xFF003915)
                                else Color(0xFF1E232E)
                            ) {
                                Text(
                                    text = when (connectionInfo.streamState) {
                                        AlvrStreamState.STREAMING -> "STREAMING"
                                        AlvrStreamState.CONNECTED -> "CONNECTED"
                                        AlvrStreamState.SEARCHING -> "DISCOVERING..."
                                        AlvrStreamState.ERROR -> "ERROR"
                                        else -> "READY"
                                    },
                                    color = if (connectionInfo.streamState == AlvrStreamState.STREAMING) VrGreen else VrCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Target Server IP Input
                        OutlinedTextField(
                            value = serverIpInput,
                            onValueChange = {
                                serverIpInput = it
                                alvrManager.updateServerIp(it)
                            },
                            label = { Text("PC Server IP Address") },
                            placeholder = { Text("e.g. 192.168.1.100") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("server_ip_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VrCyan,
                                unfocusedBorderColor = VrOutline,
                                focusedLabelColor = VrCyan
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Target Port Input
                        OutlinedTextField(
                            value = serverPortInput,
                            onValueChange = {
                                serverPortInput = it
                                it.toIntOrNull()?.let { port -> alvrManager.updateServerPort(port) }
                            },
                            label = { Text("ALVR / PhoneVR UDP Port") },
                            placeholder = { Text("9944") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("server_port_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VrCyan,
                                unfocusedBorderColor = VrOutline,
                                focusedLabelColor = VrCyan
                            )
                        )
                    }
                }
            }

            // 3. VR Streaming Telemetry Overview (When streaming or testing)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = VrSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VrOutline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "TRACKING TELEMETRY OVERVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Head Tracking", fontSize = 11.sp, color = Color(0xFF8B949E))
                                Text("Active (120 Hz)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VrCyan)
                            }

                            Column {
                                Text("Gear VR Controller", fontSize = 11.sp, color = Color(0xFF8B949E))
                                Text(
                                    text = if (controllerState.packetsPerSec > 0 || controllerState.isSimulated)
                                        "3DoF Linked (${controllerState.packetsPerSec} Hz)"
                                    else "Disconnected",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (controllerState.packetsPerSec > 0 || controllerState.isSimulated) VrGreen else VrAmber
                                )
                            }

                            Column {
                                Text("Network Latency", fontSize = 11.sp, color = Color(0xFF8B949E))
                                Text(
                                    text = if (connectionInfo.streamState == AlvrStreamState.STREAMING) "${connectionInfo.latencyMs} ms" else "--",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }

            // 4. Primary Launch VR Streaming Button
            item {
                Button(
                    onClick = onStartVrStream,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_vr_stream_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VrCyan,
                        contentColor = Color(0xFF0B0E14)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start Streaming",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Start VR Streaming",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Controller Status Banner before streaming starts, providing a clear
 * entry point to the requested Bluetooth Settings & 3D rotating cube screen.
 */
@Composable
private fun ControllerStatusBanner(
    controllerState: com.example.gearvr.GearVRState,
    onOpenSettings: () -> Unit
) {
    val isLinked = controllerState.connectionState == GearVRConnectionState.STREAMING ||
            controllerState.connectionState == GearVRConnectionState.CONNECTED ||
            controllerState.isSimulated

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("controller_status_banner"),
        shape = RoundedCornerShape(16.dp),
        color = if (isLinked) VrSurfaceVariant else VrSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isLinked) VrCyan else VrAmber.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                if (isLinked) VrCyan.copy(alpha = 0.2f) else VrAmber.copy(alpha = 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = "Gear VR Controller",
                            tint = if (isLinked) VrCyan else VrAmber,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Samsung Gear VR Controller",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (controllerState.isSimulated) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = VrGreen.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "DEMO",
                                        color = VrGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (isLinked)
                                "Status: Connected • 3DoF Active (${controllerState.packetsPerSec} Hz)"
                            else "Status: Not Connected • Setup required",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLinked) VrGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Settings button explicitly highlighted before streaming starts
            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_controller_settings_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLinked) Color(0xFF2E3846) else VrAmber,
                    contentColor = if (isLinked) Color.White else Color(0xFF0B0E14)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isLinked) "Controller Diagnostics & 3D Cube View" else "Open Controller Settings & Pair",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
