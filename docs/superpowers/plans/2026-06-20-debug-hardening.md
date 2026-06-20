# Debug Runtime Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a debug APK with deterministic reconnect/control behavior, single-owner microphone capture, lifecycle relocking, and bounded resource use.

**Architecture:** Pure policy classes own ordering and lifecycle decisions so JVM tests can prove
the edge cases. `BabyCamConnection` applies those policies to MQTT/WebRTC, while a small audio
coordinator switches cry detection between WebRTC PCM and the service fallback recorder.

**Tech Stack:** Kotlin, Android services, Jetpack Compose lifecycle, coroutines, Paho MQTT, WebRTC
JavaAudioDeviceModule, JUnit 4.

---

### Task 1: Protocol Ordering And Session Handshake

**Files:**
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/SessionControlPolicy.kt`
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/BabyCamConnection.kt`
- Modify: `app/src/main/java/com/colworx/babycam/signaling/SignalingClient.kt`
- Test: `app/src/test/java/com/colworx/babycam/webrtc/SessionControlPolicyTest.kt`

- [x] Write failing tests proving stale command revisions and acknowledgements are rejected.
- [x] Run `./gradlew testDebugUnitTest --tests '*SessionControlPolicyTest'` and confirm RED.
- [x] Add revision trackers, repeated `session_sync`, `session_ready`, and disconnect acknowledgement.
- [x] Fix parent-camera replay to use the actual `setCameraStandby` result.
- [x] Run the focused test and confirm GREEN.

### Task 2: Single-Owner Cry Audio

**Files:**
- Create: `app/src/main/java/com/colworx/babycam/audio/CryAudioCoordinator.kt`
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/WebRtcSession.kt`
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/BabyCamConnection.kt`
- Modify: `app/src/main/java/com/colworx/babycam/service/MonitorService.kt`
- Test: `app/src/test/java/com/colworx/babycam/audio/CryAudioCoordinatorTest.kt`

- [x] Write failing tests for fallback/WebRTC ownership transitions.
- [x] Run the focused test and confirm RED.
- [x] Add WebRTC PCM callback conversion and coordinator ownership state.
- [x] Stop fallback AudioRecord before WebRTC microphone activation and resume it after deactivation.
- [x] Run the focused test and confirm GREEN.

### Task 3: Lifecycle And Resource Policies

**Files:**
- Create: `app/src/main/java/com/colworx/babycam/ui/AppLockPolicy.kt`
- Modify: `app/src/main/java/com/colworx/babycam/ui/AppNavigation.kt`
- Modify: `app/src/main/java/com/colworx/babycam/signaling/PresenceChecker.kt`
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/WebRtcSession.kt`
- Modify: `app/src/main/java/com/colworx/babycam/service/MonitorService.kt`
- Test: `app/src/test/java/com/colworx/babycam/ui/AppLockPolicyTest.kt`

- [x] Write a failing lifecycle test proving a foreground-unlocked app relocks after backgrounding.
- [x] Run the focused test and confirm RED.
- [x] Observe lifecycle in Compose and reset unlock state on background.
- [x] Start presence response timeout only after MQTT readiness.
- [x] Make audio routing demand-driven and remove forced maximum volume.
- [x] Replace the indefinite service wake lock with bounded active-use acquisition.
- [x] Run focused tests and confirm GREEN.

### Task 4: Debug Verification And Tracking

**Files:**
- Modify: `TASKS.md`

- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew lintDebug`.
- [x] Run `./gradlew assembleDebug` once at the end.
- [x] Mark only verified debug-hardening items complete in `TASKS.md`.
- [x] Record remaining public-release blockers separately from debug runtime work.
