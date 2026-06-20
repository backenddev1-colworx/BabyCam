# BabyCam Completion Tracker

_Updated: 2026-06-20_

This file is the source of truth for autonomous work. Check items only after the
corresponding automated verification passes. Real-device acceptance testing remains
the owner's final step.

## Work Done Summary

- [x] Camera, baby microphone, cry detection, torch, lullaby, parent camera, and Talk default OFF
- [x] Parent given authoritative remote control with baby-side state acknowledgements
- [x] Camera and microphone tracks provisioned without activating hardware on startup
- [x] Reconnect flow de-duplicated; duplicate audio/video tracks and overlapping offers prevented
- [x] MQTT retained SDP removed; presence checks separated from WebRTC negotiation
- [x] Parent disconnect and control-lease timeout force all active monitoring features OFF
- [x] Battery signaling de-duplicated and reconnect battery synchronization added
- [x] App-lock now blocks saved-session restoration until the user unlocks
- [x] Service coroutine, AudioRecord, receiver, wake-lock, boot, and restart lifecycle hardened
- [x] Baby and parent UI updated to display confirmed ON/OFF states
- [x] Unit tests, lint, Kotlin compilation, and debug APK build completed successfully
- [x] Debug hardening applied in the `main` working tree; physical devices kept disconnected

## Current

- [x] Disconnect parent and baby Wi-Fi ADB devices
- [x] Review current media, signaling, service, and UI state
- [x] Lock implementation design and default-state policy
- [x] Write resumable implementation plan
- [x] Implement framework-free parent-authoritative, default-OFF state model
- [x] Integrate default-OFF state with WebRTC media and signaling

## Debug Runtime Hardening

- [x] Retry parent session sync until the baby acknowledges the active sync ID
- [x] Reject stale control commands and stale state acknowledgements
- [x] Deliver explicit parent disconnect before closing MQTT
- [x] Correct parent-camera OFF state after reconnect replay
- [x] Prevent parallel WebRTC and cry-detection microphone capture
- [x] Relock the app after it leaves the foreground
- [x] Start presence response timeout only after MQTT is connected
- [x] Enable communication audio routing only while an audio feature is active
- [x] Remove indefinite standby wake-lock ownership
- [x] Run 50 unit tests, lint, and one final debug APK build

## Deferred Public Release Work

- [ ] Replace four-digit pairing security before any public release
- [ ] Replace temporary Metered TURN credentials with a sustainable deployment
- [ ] Configure release signing and public-store packaging

## P0: Privacy And Parent Control

- [x] Baby camera is provisioned for WebRTC but never captures on startup
- [x] Baby microphone m-line is negotiated without opening hardware; track is created only on ON
- [x] Cry detection starts disabled and stays disabled until explicitly enabled by parent
- [x] Torch, lullaby, and parent camera sharing start disabled
- [x] Parent UI state starts with camera and microphone controls OFF
- [x] Baby sends actual camera, microphone, torch, cry, and parent-camera states
- [x] Parent UI updates from baby acknowledgements instead of optimistic state alone
- [x] Reconnect preserves the current parent-selected states without auto-enabling media
- [x] App lock gates session restoration; no media/control session starts behind the lock screen
- [x] Explicit parent disconnect or expired control lease forces camera, mic, torch, and playback OFF
- [x] Baby status screen accurately labels every active/inactive capability

## P0: Signaling And Reconnect

- [x] Initial MQTT connection runs setup exactly once
- [x] MQTT reconnect runs recovery exactly once
- [x] Audio/video tracks cannot be added twice
- [x] Concurrent ping/reconnect/ICE recovery cannot create overlapping offers
- [x] Stale retained offer cannot silently activate media
- [x] SDP offers are not retained; offline babies cannot appear online from stale MQTT data
- [x] Presence checks use correlated ping/pong and never trigger WebRTC renegotiation
- [x] SDP set/create operations are ordered and serialized
- [x] Connection stop prevents late callbacks and reconnect work
- [x] Replaced-session callbacks cannot mutate or command the current room

## P0: Service And Resource Safety

- [x] MonitorService owns a cancellable SupervisorJob
- [x] MonitorService cancels collectors in onDestroy
- [x] AudioRecord always stops and releases on disable/service teardown
- [x] Wake locks and receivers are released on teardown
- [x] Foreground notification says standby when no monitoring capability is active
- [x] Boot restore offers user-mediated resume; it does not activate camera or mic
- [x] Services use START_NOT_STICKY to avoid notification-only zombie restarts
- [x] Parent standby does not hold an indefinite wake lock
- [x] Auto-start defaults OFF and reboot requires user-mediated resume
- [x] Foreground-service declarations cover the media work each service may perform

## P1: Battery And State Sync

- [x] Battery registration does not publish the sticky value twice
- [x] Battery publishes only when percentage changes
- [x] Reconnecting parent receives one forced current battery value
- [x] Low-battery notification remains edge-triggered at 20%
- [x] Notifications-enabled startup at low battery produces exactly one alert
- [x] Invalid battery values are ignored

## P1: Automated Verification

- [x] Add JVM tests for default state and command transitions
- [x] Implement serialized reconnect de-duplication; owner will run end-to-end reconnect testing
- [x] Add JVM tests for battery de-duplication and forced sync
- [x] Add JVM tests for lifecycle/state reducers
- [x] Run focused tests for policy changes
- [x] Run full unit-test suite (50 tests)
- [x] Run lint/static checks
- [x] Build final debug APK

## Final Manual Acceptance: Owner

- [ ] Install final APK on both physical phones
- [ ] Confirm baby launch shows camera/mic/cry/torch OFF
- [ ] Confirm no camera or microphone privacy indicator appears before parent action
- [ ] Toggle every parent control ON and OFF and verify baby response
- [ ] Lock/background both phones and repeat controls
- [ ] Drop/rejoin Wi-Fi and verify state-preserving reconnect
- [ ] Test separate networks and TURN relay
- [ ] Verify talk audio, camera flip, torch result, cry alert, battery update, and snapshots
- [ ] Capture final dual-device logcat and confirm no crash, ANR, duplicate tracks, or signaling spam

## Known External Constraint

- Reliable torch control while WebRTC owns the camera is OEM/library dependent. The app must
  report the real result and never display a false ON state. A universal torch guarantee requires
  replacing or forking the current WebRTC Camera2 capturer.
