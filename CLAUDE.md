# BabyCam — Claude Code Instructions

## Project
Native Android baby monitor app. Package: `com.colworx.babycam`
Repo: `/Users/applem2air/Documents/GitHub/BabyCam`
GitHub: `https://github.com/mzashah/BabyCam`
Git user: mzashah / mz.ashah@outlook.com — **NEVER add Co-Authored-By lines**
**Always ask before git commit or push** (unless the user has explicitly authorized autonomous
work for a session — that authorization does not carry over to future sessions).

## Rules
- English-only UI
- All services 100% free, no accounts, no credit card
- Android-only (iOS excluded)
- Do NOT build APK repeatedly — build once at the very end
- Commits in user's name only (mzashah / mz.ashah@outlook.com)
- Both WapiBot and BabyCam are personal projects — always use mzashah GitHub account, never backenddev1-colworx

## Tech Stack
- Kotlin + Jetpack Compose + Material3
- compileSdk 35, minSdk 26, JDK 17
- Gradle 8.9, AGP 8.7.2, Kotlin 2.0.21, Compose BOM 2024.10.01
- WebRTC: `io.getstream:stream-webrtc-android:1.3.8`
- Signaling: Paho MQTT `mqttv3:1.2.5` → `broker.hivemq.com:1883` (free, no account)
- AES-256-GCM encryption on all MQTT payloads (`SignalCrypto.kt`)
- ICE: Google STUN + Open Relay TURN (free, no account)
- Pairing: 4-digit code (`RoomToken.kt`) — **no QR/ZXing**, that was removed; baby shows the code,
  parent types it in
- Biometric: `androidx.biometric:biometric:1.1.0`
- DataStore Preferences for persistence

## Build Command
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
cd /Users/applem2air/Documents/GitHub/BabyCam
JAVA_HOME=$JAVA_HOME PATH=$JAVA_HOME/bin:$PATH ./gradlew assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`

Plain `which java`/`adb`/`emulator` is NOT on PATH in this environment — always pass the full
`JAVA_HOME` above, and use the full tool paths below for adb/emulator.

## Emulator
- AVD: `BabyCam_Test` (Pixel 6, Android 35 API, arm64-v8a)
- ADB: `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`
- Emulator binary: `/opt/homebrew/share/android-commandlinetools/emulator/emulator`
- Device: `emulator-5554`
- **Not actually unstable — it was being killed by us.** Earlier sessions saw it "crash" ~30-60s
  after boot ("Failed to restore previous context" in the log), but the logs show clean boots
  followed by a *graceful shutdown request* — that only fires when something signals the emulator
  to quit. Root cause: launching it as a blocking foreground `Bash` command means the emulator's
  process tree dies when that tool call ends/times out. **Fix: always launch it detached** so it
  survives independent of any single tool call:
  ```bash
  nohup env ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools \
    ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
    /opt/homebrew/share/android-commandlinetools/emulator/emulator \
    -avd BabyCam_Test -gpu swiftshader_indirect -no-snapshot-save -no-boot-anim -no-audio \
    > /tmp/emu.log 2>&1 < /dev/null &
  disown
  ```
  Then poll `adb shell getprop sys.boot_completed` (or use the Monitor tool) instead of blocking
  on the launch command. If `~/.android/avd/BabyCam_Test.avd/multiinstance.lock` or
  `tmpAdbCmds/` are left over from a previous kill, remove them before relaunching.
- **UIAutomator crashes on the pairing screen** — do NOT run `uiautomator dump` there
- The emulator's virtual camera and (if no LED model attached) flash unit are not representative
  of real hardware — camera/torch behavior must ultimately be confirmed on real phones

## Screenshot Method (when the emulator is actually up)
```bash
ADB=/opt/homebrew/share/android-commandlinetools/platform-tools/adb
$ADB shell screencap -p /sdcard/s.png && $ADB pull /sdcard/s.png /tmp/s.png
```
Then Read `/tmp/s.png`. Do NOT use `exec-out screencap -p >` — it corrupts the PNG.

For the README, design mockups (SVG, built from `ui/theme/Color.kt` + actual screen copy) live in
`docs/screenshots/` instead of real captures, since the emulator can't be relied on. Swap in real
photos from the user's phone if/when available.

---

## ✅ COMPLETED — all 15 functional phases implemented, build passes

- **Phase 0-2**: Toolchain, design system, navigation shell, role-aware permissions (parent skips
  camera permission), OEM battery setup guidance
- **Phase 3**: Foreground service (camera+mic type), boot auto-start (API 34+: notification-based
  resume, not direct FGS start, to avoid the Android 14+ crash)
- **Phase 4**: MQTT signaling (`broker.hivemq.com`) + AES-GCM encrypted payloads + 4-digit pairing
  code (replaced the original QR flow entirely)
- **Phase 5**: WebRTC live video, baby → parent, via `stream-webrtc-android`
- **Phase 6**: Two-way talk — parent pre-adds a muted audio track during `start()` (before any SDP
  exchange) so toggling Talk needs no renegotiation
- **Phase 7**: Reconnect — MQTT `MqttCallbackExtended.connectComplete` auto-resubscribes; baby does
  ICE restart on FAILED; parent sends a "ping" on ICE disconnect to trigger a baby re-offer.
  Pairing persists until the parent disconnects explicitly (`BackHandler` + confirm dialog)
- **Phase 8-9**: Cry detection (amplitude threshold, sensitivity read from DataStore, was previously
  hardcoded), local + push-style alert notification, deep-links to `ParentLiveScreen`
- **Phase 10**: Lullaby/white-noise playback (procedural PCM: white noise, heartbeat, rain)
- **Phase 11**: Night mode color filter, snapshot-to-gallery
- **Phase 12**: Low battery alert (≤20%)
- **Phase 13**: Data saver (audio-only toggle)
- **Phase 14**: App lock (PIN / biometric)
- **Phase 15**: Settings (About dialog, Forget pairing flow), remembered pairing, full parent
  remote-control panel (baby cam/mic/torch/flip + parent talk/night/lullaby/snapshot)

### Phase 16 — Release APK & public GitHub (the one real gap)
- Repo is public with a README + design mockups, but **no signed release build yet** — only debug
  builds exist. Needs a keystore + `signingConfigs` before a Play Store-style release APK.

---

## Connectivity fixes (2026-06-16, verified on real Samsung A06 via USB logcat)

These were the real reasons "parent showed black / no video" in field testing — found by pulling
live logcat from a real phone, not the emulator:

1. **Baby couldn't reach the MQTT broker on NAT64/DNS64 networks (the big one).** On networks
   that hand out IPv6 + DNS64 (the test Wi-Fi "AST" did), `broker.hivemq.com` resolved to a
   *synthesized* IPv6 address (`2a01:578:13::12c6:9968` = embedded IPv4 18.198.153.104) and the
   NAT64 gateway refused it (`ECONNREFUSED`). Paho only tries the first resolved address, so it
   never fell back to the working IPv4. Result: baby never connected to signaling → never sent an
   offer → parent stayed black. **Fix:** `SignalingClient.resolveBrokerUrls()` now resolves the
   broker to all addresses, **prefers IPv4**, and tries each `tcp://ip:port` until one connects.
2. **No retry on the initial MQTT connect.** Paho's `automaticReconnect` only fires *after* the
   first successful connect; a single transient failure (the free public broker often refuses)
   left the device permanently `Signaling DOWN`. **Fix:** initial-connect retry loop with capped
   backoff (2s→15s) until connected or `close()`.
3. **TURN relay was dead → ICE FAILED on any non-direct path.** ICE logging showed `typ=host` and
   `typ=srflx` candidates but **never `typ=relay`** — the old free `openrelay.metered.ca` no longer
   allocates relays. So cross-network / client-isolated-Wi-Fi / emulator-double-NAT calls had no
   fallback and failed. **Fix:** switched `WebRtcSession.iceServers()` to **Metered free tier**
   TURN (`global.relay.metered.ca`, account `babycam.metered.live`, static creds embedded in the
   client — normal practice). Google STUN kept for redundancy. STUN was already working.
   - Metered API key (server-side, to regenerate/rotate creds): in the project owner's Metered
     dashboard. Static client creds are hardcoded in `iceServers()`.
   - Diagnostic logging added this session: ICE connection/gathering state, `onAddTrack` kind,
     local ICE candidate `typ=`, and full MQTT cause chain — keep for future field debugging.

**Network gotcha for testing:** the test router "AST" has **client isolation** (Mac could reach
the gateway but not the phone) — so wireless `adb` to the phone fails and same-Wi-Fi P2P needs
TURN. The dev Mac is also on a VPN (`utun18` default route). Use **USB** for on-device logcat.

## Known bugs fixed this session (2026-06-16/17) — read before assuming these still work

1. **Baby's own camera preview was permanently black.** `BabyActiveScreen` read
   `connection.localVideoTrack` as a plain Kotlin property, not a Compose `State` — the camera
   track is created asynchronously after the screen is already composed, so it never recomposed.
   Fixed via `LiveSession.localVideo` (observable), set through a new `onLocalVideo` callback on
   `BabyCamConnection`.
2. **"Flip Cam" button on the parent's live screen did nothing.** It called `switchCamera()` on
   the *parent's own* `WebRtcSession` — but the parent never opens a camera at all (only the baby
   does). Fixed by sending a `"switch_camera"` MQTT signal to the baby, which then calls its own
   `session.switchCamera()`.
3. **Call audio was very quiet on both sides.** Classic WebRTC-on-Android gotcha: without forcing
   `AudioManager.MODE_IN_COMMUNICATION` + `isSpeakerphoneOn = true`, Android routes call audio
   through the earpiece at a much lower max volume. Fixed in `WebRtcSession.configureAudioRouting()`,
   called from `initialize()` on both baby and parent; reverted in `close()`. Needed adding
   `MODIFY_AUDIO_SETTINGS` to the manifest.
4. **Torch (flashlight) toggle is unreliable on some devices — partially fixed, partially a hard
   library limitation.** `CameraManager.setTorchMode()` is called while the baby's camera is
   already open and actively streaming for WebRTC; many OEM camera HALs reject torch changes on a
   camera ID that's mid-capture (`CameraAccessException: CAMERA_IN_USE`), and the
   `stream-webrtc-android` `Camera2Capturer` doesn't expose its internal `CaptureRequest` to set
   `FLASH_MODE` directly on the active session (verified via `javap` against the library — no
   such method exists). What WAS fixed: the baby now sends a `"torch_state"` ack back to the
   parent with the real outcome, so the UI doesn't show "Torch ON" when the flash never actually
   turned on. **Whether the flash itself fires is still device-dependent** — confirmed reportedly
   broken on the user's test phones; a full fix would require forking the camera capture session
   to inject `FLASH_MODE_TORCH` into the repeating capture request, which is a larger undertaking
   than a quick patch. If this needs to actually work reliably, that's the next real task.

**Still NOT independently re-verified on real devices** (logic looks correct, build is clean, but
no live two-phone test from this side since the audio/torch/camera-switch fixes): talk audio
volume after the routing fix, torch ack behavior, camera switch after the signaling fix, full
reconnect-after-network-drop flow.

---

## Key Files
```
app/src/main/java/com/colworx/babycam/
├── MainActivity.kt
├── ui/
│   ├── AppNavigation.kt          ← NavHost + app lock gate
│   ├── screens/
│   │   ├── OnboardingScreens.kt  ← WelcomeScreen, ChooseDeviceScreen
│   │   ├── SetupScreens.kt       ← PermissionsScreen (role-aware), BatterySetupScreen
│   │   ├── BabyScreens.kt        ← BabyPairingScreen + BabyActiveScreen
│   │   ├── ParentScreens.kt      ← ParentScanScreen, ParentLiveScreen, SettingsScreen, LullabyPickerSheet
│   │   └── AppLockScreen.kt
│   └── components/               ← PrimaryButton, SecondaryButton, AppCard, etc.
├── webrtc/
│   ├── LiveSession.kt            ← app-wide observable state (singleton)
│   ├── BabyCamConnection.kt      ← WebRTC + MQTT orchestration, all signal types
│   └── WebRtcSession.kt          ← PeerConnection, camera/mic tracks, audio routing
├── signaling/
│   ├── SignalingClient.kt        ← MQTT client (background thread, MqttCallbackExtended)
│   ├── SignalCrypto.kt           ← AES-GCM encryption
│   └── RoomToken.kt              ← 4-digit pairing code
├── service/
│   ├── MonitorService.kt         ← FGS + cry detection + battery alert
│   ├── MonitorController.kt      ← start/stop helper
│   └── BootReceiver.kt           ← boot auto-start (API 34+: notification only)
├── audio/
│   ├── CryDetector.kt
│   └── LullabyPlayer.kt
├── data/
│   └── AppPreferences.kt         ← DataStore prefs (cry sensitivity, parent room, data saver)
└── security/
    └── PinManager.kt
```

## Known Issues / Quirks
- Emulator API 35 crashes frequently and on its own — see Emulator section above
- UIAutomator dump causes null root node + system crash on the pairing screen — avoid it
- `sleep` > 2s in Bash commands causes the tool to background the command — use separate calls
- ADB tap coordinates must come from UIAutomator XML, NOT estimated from displayed screenshots
- `exec-out screencap -p > file.png` corrupts PNG — always use `screencap -p /sdcard/f.png && pull`
- `AudioManager.isSpeakerphoneOn` is deprecated since API 31 (in favor of
  `setCommunicationDevice`) but still functions correctly through current Android versions; kept
  for minSdk 26 compatibility — the deprecation warning at build time is expected and harmless
