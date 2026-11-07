package com.example.ui.screens

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gearvr.BleDeviceItem
import com.example.gearvr.GearVRBluetoothManager
import com.example.gearvr.GearVRConnectionState
import com.example.ui.components.CubeVisualizer3D
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
fun ControllerSettingsScreen(
    gearVRManager: GearVRBluetoothManager,
    onNavigateBack: () -> Unit,
    onStartStreaming: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val controllerState by gearVRManager.controllerState.collectAsState()
    val scannedDevices by gearVRManager.scannedDevices.collectAsState()
    val isBtEnabled by gearVRManager.isBluetoothEnabled.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Gear VR Controller",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Settings, Pairing & 3D Live Diagnostics",
                            style = MaterialTheme.typography.bodySmall,
                            color = VrCyan
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // Re-center orientation action
                    IconButton(
                        onClick = { gearVRManager.reCenter() },
                        modifier = Modifier.testTag("recenter_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Re-center Orientation",
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
            // 1. Bluetooth Status & Scan Controls Card
            item {
                BluetoothControlCard(
                    isBtEnabled = isBtEnabled,
                    connectionState = controllerState.connectionState,
                    isSimulated = controllerState.isSimulated,
                    onEnableBtClick = {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        (context as? Activity)?.startActivityForResult(enableBtIntent, 1)
                        gearVRManager.refreshBluetoothState()
                    },
                    onScanClick = {
                        if (controllerState.connectionState == GearVRConnectionState.SCANNING) {
                            gearVRManager.stopScan()
                        } else {
                            gearVRManager.startScan()
                        }
                    },
                    onToggleSimulation = {
                        gearVRManager.toggleSimulation()
                    }
                )
            }

            // 2. Interactive 3D Cube Visualizer (The requested 3D model)
            item {
                CubeVisualizer3D(
                    gearVRState = controllerState,
                    modifier = Modifier.testTag("cube_visualizer_container")
                )
            }

            // 3. Controller Calibration & Orientation Detail Card
            item {
                OrientationDetailCard(
                    controllerState = controllerState,
                    onReCenter = { gearVRManager.reCenter() }
                )
            }

            // 4. Bluetooth Devices Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BLUETOOTH CONTROLLER DEVICES",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "${scannedDevices.size} found",
                        style = MaterialTheme.typography.bodySmall,
                        color = VrCyan
                    )
                }
            }

            // 5. Scanned / Bonded Devices List
            if (scannedDevices.isEmpty()) {
                item {
                    EmptyDevicesCard(
                        isScanning = controllerState.connectionState == GearVRConnectionState.SCANNING,
                        onScanClick = { gearVRManager.startScan() }
                    )
                }
            } else {
                items(scannedDevices, key = { it.address }) { device ->
                    DeviceListItem(
                        device = device,
                        connectedAddress = controllerState.deviceAddress,
                        connectionState = controllerState.connectionState,
                        onConnect = { gearVRManager.connect(device.address) },
                        onDisconnect = { gearVRManager.disconnect() }
                    )
                }
            }

            // 6. Action Button: Start VR Streaming
            item {
                Button(
                    onClick = onStartStreaming,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_streaming_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VrCyan,
                        contentColor = Color(0xFF0B0E14)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start Streaming",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start VR Streaming with Controller",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Bluetooth Power & Scan Action Card
 */
@Composable
private fun BluetoothControlCard(
    isBtEnabled: Boolean,
    connectionState: GearVRConnectionState,
    isSimulated: Boolean,
    onEnableBtClick: () -> Unit,
    onScanClick: () -> Unit,
    onToggleSimulation: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bluetooth_control_card"),
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
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (isBtEnabled) VrCyan.copy(alpha = 0.15f) else Color(0xFF2E3846),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "Bluetooth Status",
                            tint = if (isBtEnabled) VrCyan else Color(0xFF8B949E),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isBtEnabled) "Bluetooth Active" else "Bluetooth Disabled",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (isBtEnabled) "Ready to discover Gear VR controller" else "Tap enable to pair",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isBtEnabled) {
                    FilledTonalButton(
                        onClick = onEnableBtClick,
                        modifier = Modifier.testTag("enable_bluetooth_button"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = VrCyan,
                            contentColor = Color(0xFF0B0E14)
                        )
                    ) {
                        Text("Enable", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Scan & Demo Simulation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Scan Button
                Button(
                    onClick = onScanClick,
                    enabled = isBtEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scan_controllers_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (connectionState == GearVRConnectionState.SCANNING) VrAmber else VrCyan,
                        contentColor = Color(0xFF0B0E14)
                    )
                ) {
                    if (connectionState == GearVRConnectionState.SCANNING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF0B0E14),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop Scan", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.BluetoothSearching,
                            contentDescription = "Scan",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Controllers", fontWeight = FontWeight.Bold)
                    }
                }

                // Simulation / Demo Mode Toggle
                OutlinedButton(
                    onClick = onToggleSimulation,
                    modifier = Modifier.testTag("toggle_simulation_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSimulated) VrGreen else VrOutline
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isSimulated) VrGreen else MaterialTheme.colorScheme.onBackground
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = "Demo Mode",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSimulated) "Demo: ON" else "Demo Mode",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Orientation telemetry and re-center calibration card
 */
@Composable
private fun OrientationDetailCard(
    controllerState: com.example.gearvr.GearVRState,
    onReCenter: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("orientation_detail_card"),
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
                Text(
                    text = "SENSOR TELEMETRY & CALIBRATION",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = onReCenter,
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("recenter_action_button"),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VrCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VrCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = "Re-center",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Re-center (Zero)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Telemetry grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelemetryStatItem(
                    label = "Quaternion",
                    value = "w:%.2f x:%.2f y:%.2f z:%.2f".format(
                        controllerState.quatW,
                        controllerState.quatX,
                        controllerState.quatY,
                        controllerState.quatZ
                    )
                )
                TelemetryStatItem(
                    label = "Touchpad Pos",
                    value = if (controllerState.isTouching) "X:%.0f  Y:%.0f".format(
                        controllerState.touchX,
                        controllerState.touchY
                    ) else "Released"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelemetryStatItem(
                    label = "Accelerometer",
                    value = "%.1f, %.1f, %.1f m/s²".format(
                        controllerState.accelX,
                        controllerState.accelY,
                        controllerState.accelZ
                    )
                )
                TelemetryStatItem(
                    label = "Gyroscope",
                    value = "%.2f, %.2f, %.2f rad/s".format(
                        controllerState.gyroX,
                        controllerState.gyroY,
                        controllerState.gyroZ
                    )
                )
            }
        }
    }
}

@Composable
private fun TelemetryStatItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * Item in the Bluetooth scanned device list.
 */
@Composable
private fun DeviceListItem(
    device: BleDeviceItem,
    connectedAddress: String,
    connectionState: GearVRConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isConnected = (connectedAddress == device.address &&
            (connectionState == GearVRConnectionState.CONNECTED || connectionState == GearVRConnectionState.STREAMING))
    val isConnecting = (connectedAddress == device.address && connectionState == GearVRConnectionState.CONNECTING)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_item_${device.address}"),
        shape = RoundedCornerShape(12.dp),
        color = if (isConnected) VrSurfaceVariant else VrSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isConnected) VrCyan else VrOutline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isConnected) VrCyan.copy(alpha = 0.2f)
                            else if (device.isGearVRController) VrAmber.copy(alpha = 0.15f)
                            else Color(0xFF2E3846),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.SportsEsports,
                        contentDescription = "Device Icon",
                        tint = if (isConnected) VrCyan else if (device.isGearVRController) VrAmber else Color(0xFF8B949E),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (device.isGearVRController) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = VrAmber.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Gear VR",
                                    color = VrAmber,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "${device.address}  •  ${if (device.rssi != 0) "${device.rssi} dBm" else "Saved"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Connection action button
            if (isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.testTag("disconnect_device_button"),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VrRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VrRed)
                ) {
                    Text("Disconnect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = !isConnecting,
                    modifier = Modifier.testTag("connect_device_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (device.isGearVRController) VrCyan else Color(0xFF2E3846),
                        contentColor = if (device.isGearVRController) Color(0xFF0B0E14) else Color.White
                    )
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Placeholder when no devices are discovered yet.
 */
@Composable
private fun EmptyDevicesCard(
    isScanning: Boolean,
    onScanClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = VrSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, VrOutline)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = "No Devices",
                tint = Color(0xFF6E7681),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isScanning) "Searching for Gear VR controllers..." else "No controllers detected yet",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Hold the Home button on your controller until the LED flashes to enter pairing mode.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8B949E),
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (!isScanning) {
                FilledTonalButton(
                    onClick = onScanClick,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Start Scan")
                }
            }
        }
    }
}
