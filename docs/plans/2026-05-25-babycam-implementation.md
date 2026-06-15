# BabyCam Implementation Plan

> **For agentic workers:** Implement phase-by-phase. Each phase produces a buildable,
> installable APK that can be verified on a device. Steps use checkbox (`- [ ]`) syntax.
> Mark each step done as it completes.

**Goal:** Build a native Android baby-monitor app (Baby unit + Parent unit) with live WebRTC
video/audio, two-way talk, cry/connection/battery alerts, lullaby playback, and full background
operation — using only free, lifetime-free services.

**Architecture:** Single Kotlin/Compose app with two roles. A foreground service on the baby unit
owns CameraX + WebRTC + cry detection and stays alive in the background. Supabase Realtime is the
signaling/pairing channel; Google STUN + Open Relay TURN handle NAT traversal; FCM (via a Supabase
Edge Function) delivers alerts even if the app is killed.

**Tech Stack:** Kotlin, Jetpack Compose, CameraX, webrtc-android (webrtc-sdk), Supabase Realtime,
Firebase Cloud Messaging, ZXing, Android Foreground Service + BootReceiver.

**Reference spec:** `docs/specs/2026-05-25-babycam-design.md`

---

## Phase 0 — Toolchain & project scaffold

**Outcome:** An empty Compose app builds to a debug APK and installs/runs on the phone.

- [x] **0.1** Install JDK 17 (`brew install openjdk@17`) and set `JAVA_HOME`.
- [x] **0.2** Install Android command-line tools (`brew install --cask android-commandlinetools`),
      then `sdkmanager` for `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`.
- [x] **0.3** Accept SDK licenses (`sdkmanager --licenses`). Create `local.properties` with
      `sdk.dir`.
- [x] **0.4** Scaffold a Gradle Android project: `app/` module, Kotlin DSL, Compose, min SDK 26,
      target SDK 35. Package `com.colworx.babycam`.
- [x] **0.5** Add base dependencies (Compose BOM, Material3, Navigation-Compose, lifecycle).
- [ ] **0.6** Build: `./gradlew assembleDebug` → APK at `app/build/outputs/apk/debug/` (building…).
- [ ] **0.7** Verify on device: `adb install -r` the APK, launch, see a "Hello BabyCam" screen.
- [ ] **0.8** Commit scaffold.

## Phase 1 — Design system & navigation shell

**Outcome:** App theme + navigation between empty screens matching the approved UI.

- [ ] **1.1** Theme: lavender/indigo palette (light) + dark live-view colors; typography; shapes.
- [ ] **1.2** Reusable components: `PrimaryButton`, `Card`, `StatusPill`, `BottomSheet`,
      `RoundControl` (icon button), phone-screen scaffolds.
- [ ] **1.3** Nav graph + routes for all screens: Welcome, ChooseDevice, Permissions,
      BatterySetup, BabyPairing, BabyActive, ParentScan, ParentLive, LullabyPicker, Settings.
- [ ] **1.4** Build Welcome + ChooseDevice screens (real UI), wire navigation.
- [ ] **1.5** Persist chosen role + first-run flag in DataStore.
- [ ] **1.6** Verify on device: navigate Welcome → ChooseDevice → role stored. Commit.

## Phase 2 — Permissions & OEM battery setup

**Outcome:** Runtime permissions flow + battery-optimization/autostart guidance.

- [ ] **2.1** Permissions screen: request CAMERA, RECORD_AUDIO, POST_NOTIFICATIONS (API 33+).
- [ ] **2.2** Battery setup screen: detect manufacturer; show per-OEM steps (Infinix/Tecno,
      Xiaomi, Oppo/Vivo, Samsung, stock); button to open battery-optimization settings +
      `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent.
- [ ] **2.3** Verify on device: permissions granted, settings deep-links open. Commit.

## Phase 3 — Foreground service & boot auto-start

**Outcome:** A persistent foreground service starts on demand and restarts after reboot.

- [ ] **3.1** `MonitorService` (foreground, types camera+microphone) with a persistent
      notification; start/stop API.
- [ ] **3.2** Manifest: service + `FOREGROUND_SERVICE*` + `RECEIVE_BOOT_COMPLETED` perms.
- [ ] **3.3** `BootReceiver` → restart MonitorService if role==Baby and monitoring was enabled.
- [ ] **3.4** Verify on device: start service (notification shows), swipe app away (service
      survives), reboot (service auto-restarts). Commit.

## Phase 4 — Supabase signaling & QR pairing

**Outcome:** Baby generates a secure room token + QR; parent scans and both join the same room.

- [ ] **4.1** Create Supabase project (free); enable Realtime; tables for pairings + push tokens.
- [ ] **4.2** `SignalingClient` over Supabase Realtime: join room (by secret token), send/receive
      `offer`/`answer`/`ice`/`event` messages.
- [ ] **4.3** Baby: generate high-entropy room token; show QR (ZXing). Reject >1 viewer.
- [ ] **4.4** Parent: ZXing scanner → parse token → join room.
- [ ] **4.5** Persist pairing locally (DataStore) for auto-reconnect (remembered pairing).
- [ ] **4.6** Verify on two devices: scan pairs them; signaling messages round-trip (log).
      Commit.

## Phase 5 — WebRTC live video (baby → parent)

**Outcome:** Parent sees baby's live camera video.

- [ ] **5.1** `WebRtcSession`: PeerConnectionFactory, ICE servers (Google STUN + Open Relay TURN).
- [ ] **5.2** Baby: CameraX capture → WebRTC video track; create offer via SignalingClient.
- [ ] **5.3** Parent: receive offer → answer; render remote track in `SurfaceViewRenderer`.
- [ ] **5.4** Baby camera capture runs inside MonitorService (works screen-off/background).
- [ ] **5.5** Front/back camera switch on baby.
- [ ] **5.6** Verify on two devices: live video shows, incl. baby screen off. Commit.

## Phase 6 — Audio: live listen + two-way talk

**Outcome:** Parent hears baby; hold-to-talk sends parent audio to baby.

- [ ] **6.1** Add baby audio track (mic) to the stream; parent plays it.
- [ ] **6.2** Parent hold-to-talk: enable parent mic track → baby plays via speaker; audio focus.
- [ ] **6.3** Sound on/off toggle on parent.
- [ ] **6.4** Verify on two devices: two-way audio works. Commit.

## Phase 7 — Reconnect & connection-lost handling

**Outcome:** Drops are detected, auto-reconnected, and surfaced to the parent.

- [ ] **7.1** Observe ICE/peer connection state; classify disconnect.
- [ ] **7.2** Exponential-backoff reconnect (up to 5 attempts) on both sides via signaling.
- [ ] **7.3** Parent UI: "Reconnecting… attempt n/5" overlay; local notification on lost.
- [ ] **7.4** Verify: toggle airplane mode on baby → parent shows reconnecting → restores. Commit.

## Phase 8 — Cry / sound detection + local notification

**Outcome:** Baby unit detects crying and alerts the parent (local notification).

- [ ] **8.1** Audio analyzer in MonitorService: RMS amplitude vs sensitivity threshold, sustained.
- [ ] **8.2** On cry: send `event:cry` via signaling + post local notification on parent.
- [ ] **8.3** Sensitivity setting (low/med/high) in DataStore.
- [ ] **8.4** Verify: loud sound near baby → parent gets "Baby is crying". Commit.

## Phase 9 — FCM push backup (alerts when app killed)

**Outcome:** Cry/offline/low-battery alerts arrive via FCM even if the local service died.

- [ ] **9.1** Add Firebase project (free) + `google-services.json`; FCM dependency.
- [ ] **9.2** Register parent FCM token in Supabase on pairing.
- [ ] **9.3** Supabase Edge Function `send-alert`: on event row insert, send FCM to parent token.
- [ ] **9.4** Baby writes events to Supabase (in addition to signaling) to trigger the function.
- [ ] **9.5** `FirebaseMessagingService` on parent → notification + deep-link to live view.
- [ ] **9.6** Verify: kill parent app → trigger cry on baby → push still arrives. Commit.

## Phase 10 — Lullaby / white-noise playback

**Outcome:** Parent triggers a sound that plays on the baby unit.

- [ ] **10.1** Bundle 4 looping audio assets: lullaby, white noise, heartbeat, rain (CC0/free).
- [ ] **10.2** LullabyPicker bottom sheet (parent) → send `event:play`/`stop` via signaling.
- [ ] **10.3** Baby MonitorService plays/loops/stops the chosen track (MediaPlayer).
- [ ] **10.4** Verify: parent picks lullaby → baby plays it. Commit.

## Phase 11 — Night mode + snapshot

- [ ] **11.1** Parent night-mode toggle: low-light render treatment.
- [ ] **11.2** Snapshot: capture current remote frame → save to gallery (MediaStore).
- [ ] **11.3** Verify: snapshot saved; night mode toggles. Commit.

## Phase 12 — Low-battery alert

- [ ] **12.1** Baby reports battery level (BatteryManager) periodically via signaling/events.
- [ ] **12.2** Threshold crossing (e.g. ≤15%) → parent alert (local + FCM) + battery shown on live.
- [ ] **12.3** Verify: simulate low battery → parent alerted. Commit.

## Phase 13 — Data-saver (audio-only + quality)

- [ ] **13.1** Audio-only mode: parent disables remote video track (saves data).
- [ ] **13.2** Video quality selector (auto/low/high) → adjust capture resolution/bitrate.
- [ ] **13.3** Verify: audio-only reduces data; quality changes resolution. Commit.

## Phase 14 — App lock (PIN / biometric)

- [ ] **14.1** Set/verify PIN (hashed in DataStore) + BiometricPrompt option.
- [ ] **14.2** Gate app open / live view behind lock when enabled.
- [ ] **14.3** Verify: lock prompts on open; biometric works. Commit.

## Phase 15 — Settings, remembered pairing, polish

- [ ] **15.1** Settings screen wiring all toggles (sensitivity, auto-start, auto night, quality,
      data-saver, app lock, notification sound, about).
- [ ] **15.2** Parent auto-reconnect to remembered baby unit on launch (no rescan).
- [ ] **15.3** Empty/error states, icons, app name + launcher icon.
- [ ] **15.4** Verify full end-to-end on two devices. Commit.

## Phase 16 — Release APK & public GitHub

- [ ] **16.1** Build signed release APK (debug-key acceptable for sideload v1).
- [ ] **16.2** README (setup, free-service config, build, install) + screenshots.
- [ ] **16.3** Create public GitHub repo `BabyCam`, push, attach APK to a release.
- [ ] **16.4** Hand APK to user; verify install on their phone(s).

---

## Self-review notes

- **Spec coverage:** all 15 features mapped (live A/V → P5/6, talk → P6, cry → P8, night+snapshot
  → P11, QR pair → P4, background+boot → P3, reconnect → P7, low-battery → P12, lullaby → P10,
  pairing security → P4.3, remembered pairing → P4.5/15.2, OEM guide → P2, FCM → P9, data-saver →
  P13, app-lock → P14).
- **Risk:** free TURN reliability for remote (spec §7) — design works on same-Wi-Fi regardless.
- **Note:** TDD-style failing-test-first is applied where unit-testable (signaling parsing, token
  gen, cry threshold, PIN hashing). Camera/WebRTC/UI are verified by on-device manual checks
  described per phase.
