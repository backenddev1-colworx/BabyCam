# Live Session Fixes And Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the current parent/baby live-session regressions and add a compact parent diagnostics chip with an expandable details panel.

**Architecture:** Keep the existing parent-authoritative WebRTC/session model, but harden the media-state path in `WebRtcSession` and `BabyCamConnection`, then expose a lightweight diagnostics snapshot to the parent UI. Reuse existing `LiveSession` state and `ParentLiveScreen` instead of introducing new navigation or services.

**Tech Stack:** Kotlin, Jetpack Compose, Android WebRTC, JUnit4, Gradle debug unit tests

---

### Task 1: Control-State Hardening

**Files:**
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/BabyCamConnection.kt`
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/SessionControlPolicy.kt`
- Test: `app/src/test/java/com/colworx/babycam/webrtc/SessionControlPolicyTest.kt`

- [ ] Add a failing regression test proving `parent_talk` survives state snapshot serialization.
- [ ] Implement shared session-control JSON helpers and route snapshot/ack logic through them.
- [ ] Re-run the focused unit test and confirm it passes.

### Task 2: Audio Path Hardening

**Files:**
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/WebRtcSession.kt`
- Test: `app/src/test/java/com/colworx/babycam/webrtc/SessionControlPolicyTest.kt`

- [ ] Add a failing test for remote-audio gating helper behavior.
- [ ] Restore robust audio routing and receiver-side remote track enable/disable handling.
- [ ] Re-run focused tests and confirm they pass.

### Task 3: Parent Local Preview

**Files:**
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/LiveSession.kt`
- Modify: `app/src/main/java/com/colworx/babycam/ui/screens/ParentScreens.kt`

- [ ] Wire the parent's local video track into `LiveSession`.
- [ ] Render it as a mirrored PiP overlay only while parent camera sharing is active.
- [ ] Verify the screen still builds cleanly.

### Task 4: Parent Diagnostics

**Files:**
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/WebRtcSession.kt`
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/BabyCamConnection.kt`
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/LiveSession.kt`
- Modify: `app/src/main/java/com/colworx/babycam/ui/screens/ParentScreens.kt`

- [ ] Add a parent diagnostics snapshot model covering video and network details.
- [ ] Implement WebRTC stats parsing for bitrate/FPS/loss/ICE path.
- [ ] Add a compact status chip plus expandable details panel on the parent live screen.

### Task 5: Verification

**Files:**
- Modify: `TASKS.md`

- [ ] Update the task tracker to reflect the new work.
- [ ] Run `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew testDebugUnitTest assembleDebug`.
- [ ] Report actual verification status with evidence only.
