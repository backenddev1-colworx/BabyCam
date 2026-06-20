# Parent-Authoritative Monitoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every monitoring capability default OFF, give the parent authoritative controls,
and eliminate duplicate reconnect, battery, and service-lifecycle behavior.

**Architecture:** Keep WebRTC media sections stable by provisioning disabled tracks, separate
transport state from one-shot connection-ready actions, synchronize actual baby state through
acknowledgements, and isolate Android-independent policies for JVM testing.

**Tech Stack:** Kotlin, Android services, Jetpack Compose, WebRTC, Paho MQTT, coroutines, JUnit 4.

---

### Task 1: Framework-Free Monitoring State

**Files:**
- Create: `app/src/main/java/com/colworx/babycam/webrtc/MonitoringState.kt`
- Create: `app/src/test/java/com/colworx/babycam/webrtc/MonitoringStateTest.kt`
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/LiveSession.kt`

- [ ] Write tests proving every capability defaults OFF and individual commands do not mutate
  unrelated state.
- [ ] Run `./gradlew testDebugUnitTest --tests '*MonitoringStateTest'` and confirm RED.
- [ ] Implement immutable state and command transitions.
- [ ] Run the focused test and confirm GREEN.
- [ ] Align `LiveSession` initial/reset values with the tested defaults.

### Task 2: Battery Publication Policy

**Files:**
- Create: `app/src/main/java/com/colworx/babycam/webrtc/BatteryPublisher.kt`
- Create: `app/src/test/java/com/colworx/babycam/webrtc/BatteryPublisherTest.kt`
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/BabyCamConnection.kt`

- [ ] Write tests for invalid input, first value, unchanged value, changed value, and forced sync.
- [ ] Run the focused test and confirm RED.
- [ ] Implement the minimal percentage/de-duplication policy.
- [ ] Run the focused test and confirm GREEN.
- [ ] Route sticky and receiver battery events through one policy instance.

### Task 3: Idempotent Disabled Media Provisioning

**Files:**
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/WebRtcSession.kt`
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/BabyCamConnection.kt`

- [ ] Add a testable provisioning policy/state test that rejects duplicate audio/video setup.
- [ ] Run the focused test and confirm RED.
- [ ] Make baby video provisioning attach a disabled track without starting capture.
- [ ] Make audio provisioning idempotent and disabled by default.
- [ ] Keep parent camera provisioning in stopped standby.
- [ ] Run focused and existing unit tests.

### Task 4: Single Reconnect Path

**Files:**
- Create: `app/src/main/java/com/colworx/babycam/signaling/ConnectionReadyPolicy.kt`
- Create: `app/src/test/java/com/colworx/babycam/signaling/ConnectionReadyPolicyTest.kt`
- Modify: `app/src/main/java/com/colworx/babycam/signaling/SignalingClient.kt`
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/BabyCamConnection.kt`

- [ ] Write tests proving initial and reconnect readiness each dispatch once and duplicates are
  ignored.
- [ ] Run the focused test and confirm RED.
- [ ] Separate signaling reachability callbacks from initial/reconnect ready callbacks.
- [ ] Move offer/ping recovery to one role-aware path.
- [ ] Serialize offer generation across ping, MQTT reconnect, and ICE restart.
- [ ] Run focused and existing unit tests.

### Task 5: Actual-State Acknowledgements

**Files:**
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/BabyCamConnection.kt`
- Modify: `app/src/main/java/com/colworx/babycam/webrtc/LiveSession.kt`
- Modify: `app/src/main/java/com/colworx/babycam/ui/screens/ParentScreens.kt`
- Modify: `app/src/main/java/com/colworx/babycam/ui/screens/BabyScreens.kt`

- [ ] Write reducer tests for camera, mic, torch, cry, and parent-camera acknowledgements.
- [ ] Run the focused test and confirm RED.
- [ ] Add baby state acknowledgement messages and a full state snapshot on ping.
- [ ] Update parent state only from confirmed acknowledgements, with bounded optimistic feedback.
- [ ] Disable flip/snapshot/quality actions when camera is OFF where applicable.
- [ ] Update baby status labels to show actual ON/OFF state.
- [ ] Run focused and existing unit tests.

### Task 6: Service Lifecycle Safety

**Files:**
- Create: `app/src/main/java/com/colworx/babycam/service/MonitorLifecycle.kt`
- Create: `app/src/test/java/com/colworx/babycam/service/MonitorLifecycleTest.kt`
- Modify: `app/src/main/java/com/colworx/babycam/service/MonitorService.kt`
- Modify: `app/src/main/java/com/colworx/babycam/service/BootReceiver.kt`

- [ ] Write tests for default standby, cry enable/disable, and idempotent stop decisions.
- [ ] Run the focused test and confirm RED.
- [ ] Give the service a cancellable SupervisorJob and cancel it on destroy.
- [ ] Stop and release AudioRecord from a deterministic cleanup path.
- [ ] Ensure boot/service restart cannot activate cry detection automatically.
- [ ] Change notification text to accurate standby/active wording.
- [ ] Run focused and existing unit tests.

### Task 7: Documentation And Final Automated Verification

**Files:**
- Modify: `README.md`
- Modify: `CLAUDE.md`
- Modify: `TASKS.md`

- [ ] Update product copy from “camera & mic on/always-on” to parent-authoritative standby.
- [ ] Mark every completed tracker item only after its verification passes.
- [ ] Run `./gradlew testDebugUnitTest`.
- [ ] Run `./gradlew lintDebug`.
- [ ] Run `./gradlew assembleDebug` once at the end.
- [ ] Record residual physical-device-only risks and provide the final manual test checklist.
