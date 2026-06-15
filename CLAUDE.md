# BabyCam — Claude Code Instructions

## Project
Native Android baby monitor app. Package: `com.colworx.babycam`
Repo: `/Users/applem2air/Documents/GitHub/BabyCam`
Git user: Colworx / backend.dev1@colworx.com — **NEVER add Co-Authored-By lines**
**Always ask before git commit or push.**

## Rules
- English-only UI
- All services 100% free, no accounts, no credit card
- Android-only (iOS excluded)
- Do NOT build APK repeatedly — build once at the very end
- Commits in user's name only (Colworx)

## Tech Stack
- Kotlin + Jetpack Compose + Material3
- compileSdk 35, minSdk 26, JDK 17
- Gradle 8.9, AGP 8.7.2, Kotlin 2.0.21, Compose BOM 2024.10.01
- WebRTC: `io.getstream:stream-webrtc-android:1.3.8`
- Signaling: Paho MQTT `mqttv3:1.2.5` → `broker.hivemq.com:1883` (free, no account)
- AES-GCM encryption on all MQTT payloads (SignalCrypto)
- ICE: Google STUN + Open Relay TURN (free, no account)
- QR: `zxing-android-embedded:4.3.0`
- Biometric: `androidx.biometric:biometric:1.1.0`
- DataStore Preferences for persistence

## Build Command
```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home
cd /Users/applem2air/Documents/GitHub/BabyCam
JAVA_HOME=$JAVA_HOME PATH=$JAVA_HOME/bin:$PATH ./gradlew assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Emulator
- AVD: `BabyCam_Test` (Pixel 6, Android 35 API, arm64-v8a)
- ADB: `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`
- Device: `emulator-5554`
- **UIAutomator crashes on QR Pairing screen** — do NOT run `uiautomator dump` on that screen
- Emulator is unstable on API 35 — system dies occasionally (Google services crash), unrelated to app

## Tap Coordinates (device pixels, confirmed via UIAutomator)
All coords are in actual device pixels (1080x2400). Do NOT scale from screenshots.
- Welcome "Get started": (540, 2275)
- Choose Device "Baby unit": (540, 301)
- Permissions "Allow & continue": (540, 955)
- Battery "Done": (540, 874)
- QR Pairing "Go to monitor": (540, 1632)

## Screenshot Method
```bash
ADB=/opt/homebrew/share/android-commandlinetools/platform-tools/adb
$ADB shell screencap -p /sdcard/s.png && $ADB pull /sdcard/s.png /tmp/s.png
```
Then Read `/tmp/s.png`. Do NOT use `exec-out screencap -p >` — it corrupts the PNG.

---

## ✅ COMPLETED TASKS

### Phase 0 — Toolchain & Project Scaffold
- Gradle 8.9 + AGP 8.7.2 + Kotlin 2.0.21 setup
- Package `com.colworx.babycam`, compileSdk 35, minSdk 26
- All dependencies in `libs.versions.toml`

### Phase 1 — Design System & Navigation Shell
- Material3 theme with custom purple palette
- NavHost with all routes: WELCOME, CHOOSE, PERMISSIONS, BATTERY, BABY_PAIRING, BABY_ACTIVE, PARENT_SCAN, PARENT_LIVE, SETTINGS
- `SecondaryButton` and `PrimaryButton` components in `ui/components/`

### Phase 2 — Permissions & OEM Battery Setup
- PermissionsScreen: Camera, Microphone, Notifications — all with green ✓ indicators
- BatterySetupScreen: OEM steps (Autostart, Unrestricted battery, disable optimization)
- All permissions granted via `pm grant` for testing

### Phase 3 — Foreground Service & Boot Auto-Start
- `MonitorService.kt`: PARTIAL_WAKE_LOCK, dual notification channels (`babycam_monitor`, `babycam_alerts`)
- `MonitorController.kt`: single entry point for start/stop + SharedPreferences persistence
- **BUG FIXED**: `BootReceiver` was starting camera/microphone FGS from BOOT_COMPLETED → Android 14+ crash
  - Fix: API 34+ shows "tap to resume" notification instead of direct service start
- Foreground service type: `camera|microphone` in manifest + runtime flags

### Phase 4 — MQTT Signaling & QR Pairing
- `SignalingClient.kt`: Paho MQTT on background Thread, AES-GCM encrypted payloads
- `SignalCrypto.kt`: per-room AES-256 key derived from room token
- `RoomToken.kt`: UUID-based room ID generator
- `BabyPairingScreen`: shows live QR code + "Connecting…" / "Waiting for parent…" status pill
- `ParentScanScreen`: ZXing QR scanner

### Phase 5 — WebRTC Live Video
- `WebRtcSession.kt`: Camera2 + CameraX capturer, PeerConnectionFactory, ICE servers
- `BabyCamConnection.kt`: ties WebRtcSession to SignalingClient, offer/answer/ICE flow
- `LiveSession.kt`: singleton observable state (remoteVideo, connState, signalingUp)
- **BUG FIXED**: `session.initialize()` + `session.startCamera()` were called on main thread → ANR
  - Fix: `BabyCamConnection.start()` now launches on `Dispatchers.IO` coroutine scope

### Phase 8 — Cry Detection
- `CryDetector.kt`: amplitude-based detection with configurable Sensitivity
- `MonitorService.kt`: AudioRecord loop feeding CryDetector, fires HIGH priority alert notification
- `AppPreferences.kt`: `crySensitivity: Flow<Float>` (default 0.55), `dataSaver: Flow<Boolean>`

### Phase 10 — Lullaby / White-Noise Playback
- `LullabyPlayer.kt`: AudioTrack procedural PCM generation — WHITE_NOISE, HEARTBEAT, RAIN
- Baby unit plays sound when parent sends "lullaby" MQTT signal
- `BabyCamConnection.kt`: handles "lullaby" and "video_enabled" signal types

### Phase 11 — Night Mode + Snapshot
- `nightModeFilter` Modifier extension for orange-tinted night vision overlay
- `ParentLiveScreen`: night mode toggle button, changes color when active

### Phase 12 — Low Battery Alert
- `MonitorService.kt`: BroadcastReceiver for `ACTION_BATTERY_CHANGED`, alerts at ≤20%

### Phase 14 — App Lock (PIN / Biometric)
- `PinManager.kt`: DataStore-backed PIN storage + biometric flag
- `AppLockScreen.kt`: biometric prompt + PIN entry UI
- `AppNavigation.kt`: shows AppLockScreen before NavHost if PIN enabled

---

## ❌ PENDING TASKS

### CRITICAL — Must fix before calling done:
1. **BabyActiveScreen not tested** — "Go to monitor" tap navigates there but emulator instability prevented screenshot verification. Need to confirm BabyActiveScreen renders (camera preview + foreground service notification in status bar).
2. **MonitorService foreground notification** — verify the persistent "BabyCam is monitoring" notification appears after tapping "Go to monitor"
3. **Parent flow** — ParentScanScreen → scan QR → ParentLiveScreen: not tested end-to-end on emulator

### Phase 6 — Two-Way Audio Talk (pending)
- Parent mic → baby speaker not implemented in UI (backend ready via `setTalking()`)
- Need push-to-talk button in ParentLiveScreen

### Phase 7 — Reconnect Handling (pending)
- ICE FAILED/DISCONNECTED overlay shown but auto-reconnect not implemented

### Phase 9 — Alert delivery polish (pending)
- Cry alert notification tapping should deep-link to ParentLiveScreen

### Phase 13 — Data Saver (pending)
- `setVideoEnabled()` wired but UI switch in SettingsScreen needs testing

### Phase 15 — Settings, Remembered Pairing, Polish (pending)
- Remembered room token so parent doesn't re-scan after reconnect
- SettingsScreen: cry sensitivity slider + data saver switch (UI built, needs testing)

### Phase 16 — Release APK (pending)
- Signed release APK for Play Store
- `keystore.jks` needed + `signingConfigs` in `build.gradle`
- Play Store listing assets (screenshots, description, icon 512x512)

---

## Key Files
```
app/src/main/java/com/colworx/babycam/
├── MainActivity.kt
├── ui/
│   ├── AppNavigation.kt          ← NavHost + app lock gate
│   ├── screens/
│   │   ├── WelcomeScreen.kt
│   │   ├── ChooseDeviceScreen.kt
│   │   ├── PermissionsScreen.kt
│   │   ├── BatterySetupScreen.kt
│   │   ├── BabyScreens.kt        ← BabyPairingScreen + BabyActiveScreen
│   │   ├── ParentScreens.kt      ← ParentLiveScreen + SettingsScreen
│   │   ├── AppLockScreen.kt
│   │   └── ParentScanScreen.kt
│   └── components/               ← PrimaryButton, SecondaryButton, etc.
├── webrtc/
│   ├── LiveSession.kt            ← singleton state
│   ├── BabyCamConnection.kt      ← WebRTC + MQTT orchestration
│   └── WebRtcSession.kt          ← PeerConnection + camera/mic
├── signaling/
│   ├── SignalingClient.kt        ← MQTT client (background thread)
│   ├── SignalCrypto.kt           ← AES-GCM encryption
│   └── RoomToken.kt
├── service/
│   ├── MonitorService.kt         ← FGS + cry detection + battery alert
│   ├── MonitorController.kt      ← start/stop helper
│   └── BootReceiver.kt           ← boot auto-start (API 34+: notification only)
├── audio/
│   ├── CryDetector.kt
│   └── LullabyPlayer.kt
├── data/
│   └── AppPreferences.kt         ← DataStore prefs
└── security/
    └── PinManager.kt
```

## Known Issues / Quirks
- Emulator API 35 crashes frequently (Google services instability) — not app-related
- UIAutomator dump causes null root node + system crash on QR Pairing screen — avoid it
- `sleep` > 2s in Bash commands causes the tool to background the command — use separate calls
- ADB tap coordinates must come from UIAutomator XML, NOT estimated from displayed screenshots (visual estimates are ~22% too low in Y)
- `exec-out screencap -p > file.png` corrupts PNG — always use `screencap -p /sdcard/f.png && pull`
