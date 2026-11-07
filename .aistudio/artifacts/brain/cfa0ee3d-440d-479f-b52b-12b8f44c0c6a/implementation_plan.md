# ALVR Android Client with Gear VR 3DoF Controller Integration

Integrating Samsung Gear VR 3DoF Bluetooth Low Energy controller support directly into the ALVR Android client codebase, featuring a pre-streaming Controller Settings & Pairing dashboard, a real-time 3D rotating cube orientation visualizer, hardware button diagnostics, and streaming 3DoF controller tracking to SteamVR.

### User Review & Critical Decisions

> [!IMPORTANT]
> **Building on ALVR Source Architecture**:
> Instead of building an isolated new app, this implementation directly incorporates and extends the ALVR Android client architecture:
> 1. **Core ALVR Base**: Integrates ALVR's Android client pipeline (connection handshake, stream receiver, and tracking sender).
> 2. **Pre-Streaming Settings & Pairing Screen**: Embedded into the ALVR client launch sequence before streaming starts, allowing users to enable Bluetooth, scan for nearby devices, and pair the Gear VR controller.
> 3. **Real-Time 3D Cube & Button Visualizer**: A 3D hardware-accelerated cube rotated live by the controller's orientation quaternion, accompanied by live indicators for Trigger, Touchpad click & coordinates ($X, Y$), Back, Home, and Volume buttons.
> 4. **Zero Vendor Lock-in**: Directly implements the Oculus Threemote BLE protocol (`4f63756c-7573-2054-6872-65656d6f7465`) so the controller works seamlessly on any Android phone (Pixel, OnePlus, Xiaomi, Samsung, etc.).
> 5. **Controller Packet Forwarding**: Bridges the 3DoF controller pose and button events into the ALVR tracking stream sent to SteamVR.

- **Confirmed Decision 1**: Build directly on top of the ALVR client codebase rather than creating a separate standalone app.
- **Confirmed Decision 2**: Introduce a pre-stream Settings & Pairing Screen with Bluetooth management, device selection, and live 3D cube diagnostics.
- **Confirmed Decision 3**: Feed real-time 3DoF controller rotation and button states directly into the ALVR tracking stream to SteamVR.

---

### 1. Overview & Core Concept

- **What It Does**: Extends the ALVR Android client to natively support Samsung Gear VR controllers over Bluetooth Low Energy. Before streaming begins, users can open the Settings screen, enable Bluetooth, select their Gear VR controller, calibrate its orientation while watching a 3D cube rotate to their hand movements, test all buttons/touchpad inputs, and then launch VR streaming with active 3DoF controller tracking in SteamVR.
- **Target Audience / Persona**: SteamVR gamers and VR enthusiasts using mobile VR headsets with ALVR who want a dedicated physical 3DoF controller without needing a Samsung Gear VR phone or legacy Oculus runtime.
- **Key Value**: Delivers seamless physical controller tracking in SteamVR through ALVR with zero proprietary system bloat and real-time visual feedback.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **ALVR Launcher / Home Screen**:
     - Displays ALVR server discovery status (searching for PC / connected).
     - Prominent **"Gear VR Controller Settings"** button displaying current controller status (e.g. *Not Connected*, *Scanning...*, or *Connected: 85% Battery*).
     - **"Start VR Streaming"** button to enter stereoscopic / immersive streaming mode.
  2. **Controller Settings & Pairing Screen**:
     - **Bluetooth & Device Bar**: Quick toggle to enable Bluetooth, "Scan for Controllers" button, and list of discovered BLE devices with Gear VR identification badges.
     - **3D Interactive Cube Visualizer**:
       - 3D cube rendered in real-time on Canvas with perspective projection.
       - Rotates smoothly matching the controller's live pitch, yaw, and roll derived from sensor fusion (gyroscope + accelerometer).
       - Colored axis faces (Front: Cyan, Top: Amber, Right: Green) for intuitive orientation verification.
     - **Hardware Button & Touchpad Diagnostics**:
       - Visual glow indicators for Trigger, Touchpad Click, Back, Home, and Volume +/- buttons.
       - Interactive 2D touchpad radar showing finger touch position ($0..315$ coordinates) in real-time.
     - **Re-center / Zero Orientation Button**: Instantly calibrates and aligns the controller forward vector.
  3. **Streaming View**:
     - Streams low-latency VR video from PC.
     - Streams 3DoF controller rotation and input states to SteamVR alongside head orientation.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Dark cyberpunk / technical VR theme (Deep Charcoal `#0D1117`, Midnight Surface `#161B22`, Neon Cyan `#00E5FF`, Vivid Amber `#FF9100`, Emerald `#00E676`).
  - *Typography*: Clean monospace readouts for sensor data (deg/s, g-force, touch coordinates) and modern sans-serif headers.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Direct BLE Protocol Integration into ALVR**
  - *Chosen Approach*: Implement `GearVRBluetoothManager` directly inside the ALVR client using standard Android `BluetoothGatt` APIs, communicating with service `4f63756c-7573-2054-6872-65656d6f7465`, notify characteristic `c8c51726-81bc-483b-a052-f7a14ea3d281`, and command characteristic `c8c51726-81bc-483b-a052-f7a14ea3d282`.
  - *Why*: Eliminates dependencies on proprietary Samsung VR services and external helper apps; works out of the box on all Android devices.
- **Decision 2: 3D Visualization Implementation**
  - *Chosen Approach*: Lightweight Jetpack Compose Canvas 3D projection matrix.
  - *Why*: Zero external game engine dependencies, ultra-fast 60-120fps rendering, and seamless lifecycle handling.
- **Decision 3: Orientation Filter**
  - *Chosen Approach*: Sensor fusion algorithm combining gyroscope angular rates and gravity acceleration vector with automatic bias calibration.
  - *Why*: Delivers drift-free, responsive 3DoF orientation matching the controller's physical motions.

---

### 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                    ALVR Android Application                     │
│  ┌──────────────────────────────┐  ┌──────────────────────────┐ │
│  │     ALVR Main Launcher       │  │   Controller Settings    │ │
│  │   - Server Discovery & IP    │  │   - BLE Device Scanner   │ │
│  │   - Stream Resolution/Codec  │  │   - 3D Cube Visualizer   │ │
│  │   - Controller Status Pill   │  │   - Button & Touch Radar │ │
│  └──────────────┬───────────────┘  └────────────┬─────────────┘ │
└─────────────────┼───────────────────────────────┼───────────────┘
                  │                               │
┌─────────────────▼───────────────────────────────▼───────────────┐
│                   GearVR BLE Controller Module                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ GearVRManager (GATT Client)                                │  │
│  │  - Scan & Pair "Gear VR Controller(xxxx)"                  │  │
│  │  - Low Power Mode Disable (0x07, 0x00)                     │  │
│  │  - Sensor Mode Enable (0x01, 0x00)                         │  │
│  │  - 60-Byte Packet Decoder (IMU, Touch X/Y, Buttons)        │  │
│  │  - 3DoF Orientation Filter (Quaternion w, x, y, z)        │  │
│  │  - StateFlow<GearVRState> (Observed by UI & Streamer)      │  │
│  └──────────────────────────────┬────────────────────────────┘  │
└─────────────────────────────────┼───────────────────────────────┘
                                  │
┌─────────────────────────────────▼───────────────────────────────┐
│                    ALVR Streaming Engine                        │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  - HMD Tracking Loop (Head Pose)                          │  │
│  │  - Controller Tracking Loop (GearVR Pose + Buttons)       │  │
│  │  - Video Decoder (H.264/HEVC Surface)                     │  │
│  │  - UDP / TCP Packet Transport to PC Server                │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

- **Data Models**:
  - `GearVRState`: Live quaternion (`w, x, y, z`), touch coordinates (`touchX, touchY`, `isTouching`), buttons (`trigger`, `padClick`, `back`, `home`, `volUp`, `volDown`), temperature, connection state (`DISCONNECTED`, `SCANNING`, `CONNECTING`, `STREAMING`).
  - `BluetoothDeviceInfo`: Device name, MAC address, RSSI, bonded state.

- **Execution Steps**:
  1. Set up permissions in `AndroidManifest.xml` (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`).
  2. Implement `GearVRBluetoothManager` with the Oculus Threemote GATT protocol and 3DoF sensor fusion.
  3. Implement the 3D rotating cube visualizer and button diagnostic UI in Compose.
  4. Integrate the Settings & Pairing screen into the ALVR client navigation flow.
  5. Feed controller orientation and button inputs into the ALVR tracking stream.
  6. Compile and verify the build.
