package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.alvr.AlvrClientManager
import com.example.gearvr.GearVRBluetoothManager
import com.example.ui.screens.AlvrMainScreen
import com.example.ui.screens.ControllerSettingsScreen
import com.example.ui.screens.VrStreamingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.VrBackground

enum class AppScreen {
    MAIN_HUB,
    CONTROLLER_SETTINGS,
    VR_STREAMING
}

class MainActivity : ComponentActivity() {

    private lateinit var gearVRManager: GearVRBluetoothManager
    private lateinit var alvrManager: AlvrClientManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        gearVRManager = GearVRBluetoothManager(applicationContext)
        alvrManager = AlvrClientManager(applicationContext)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = VrBackground
                ) {
                    AppContent(
                        gearVRManager = gearVRManager,
                        alvrManager = alvrManager
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gearVRManager.disconnect()
        alvrManager.stopStreaming()
    }
}

@Composable
fun AppContent(
    gearVRManager: GearVRBluetoothManager,
    alvrManager: AlvrClientManager
) {
    var currentScreen by remember { mutableStateOf(AppScreen.MAIN_HUB) }

    // Request BLE and Location runtime permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            gearVRManager.refreshBluetoothState()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    Crossfade(targetState = currentScreen, label = "screenTransition") { screen ->
        when (screen) {
            AppScreen.MAIN_HUB -> {
                AlvrMainScreen(
                    alvrManager = alvrManager,
                    gearVRManager = gearVRManager,
                    onOpenControllerSettings = {
                        currentScreen = AppScreen.CONTROLLER_SETTINGS
                    },
                    onStartVrStream = {
                        alvrManager.startStreaming { gearVRManager.controllerState.value }
                        currentScreen = AppScreen.VR_STREAMING
                    }
                )
            }

            AppScreen.CONTROLLER_SETTINGS -> {
                ControllerSettingsScreen(
                    gearVRManager = gearVRManager,
                    onNavigateBack = {
                        currentScreen = AppScreen.MAIN_HUB
                    },
                    onStartStreaming = {
                        alvrManager.startStreaming { gearVRManager.controllerState.value }
                        currentScreen = AppScreen.VR_STREAMING
                    }
                )
            }

            AppScreen.VR_STREAMING -> {
                VrStreamingScreen(
                    alvrManager = alvrManager,
                    gearVRManager = gearVRManager,
                    onOpenSettings = {
                        currentScreen = AppScreen.CONTROLLER_SETTINGS
                    },
                    onStopStreaming = {
                        alvrManager.stopStreaming()
                        currentScreen = AppScreen.MAIN_HUB
                    }
                )
            }
        }
    }
}
