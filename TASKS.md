# BabyCam Completion Tracker

_Updated: 2026-06-20_

This file is the source of truth for autonomous work. Check items only after the
corresponding automated verification passes. Real-device acceptance testing remains
the owner's final step.

## Current

- [x] Disconnect parent and baby Wi-Fi ADB devices
- [x] Review current media, signaling, service, and UI state
- [x] Lock implementation design and default-state policy
- [x] Write resumable implementation plan
- [x] Implement framework-free parent-authoritative, default-OFF state model
- [ ] Integrate default-OFF state with WebRTC media and signaling

## P0: Privacy And Parent Control

- [ ] Baby camera is provisioned for WebRTC but never captures on startup
- [ ] Baby microphone track exists for WebRTC but starts disabled
- [ ] Cry detection starts disabled and stays disabled until explicitly enabled
- [ ] Torch, lullaby, and parent camera sharing start disabled
- [x] Parent UI state starts with camera and microphone controls OFF
- [ ] Baby sends actual camera, microphone, torch, cry, and parent-camera states
- [ ] Parent UI updates from baby acknowledgements instead of optimistic state alone
- [ ] Reconnect preserves the current parent-selected states without auto-enabling media
- [ ] App lock gates session restoration; no media/control session starts behind the lock screen
- [ ] Explicit parent disconnect or expired control lease forces camera, mic, torch, and playback OFF
- [ ] Baby status screen accurately labels every active/inactive capability

## P0: Signaling And Reconnect

- [ ] Initial MQTT connection runs setup exactly once
- [ ] MQTT reconnect runs recovery exactly once
- [ ] Audio/video tracks cannot be added twice
- [ ] Concurrent ping/reconnect/ICE recovery cannot create overlapping offers
- [ ] Stale retained offer cannot silently activate media
- [ ] SDP offers are not retained; offline babies cannot appear online from stale MQTT data
- [ ] Presence checks use correlated ping/pong and never trigger WebRTC renegotiation
- [ ] SDP set/create operations are ordered and serialized
- [ ] Connection stop prevents late callbacks and reconnect work
- [ ] Replaced-session callbacks cannot mutate or command the current room

## P0: Service And Resource Safety

- [ ] MonitorService owns a cancellable SupervisorJob
- [ ] MonitorService cancels collectors in onDestroy
- [ ] AudioRecord always stops and releases on disable/service teardown
- [ ] Wake locks and receivers are released exactly once
- [ ] Foreground notification says standby when no monitoring capability is active
- [ ] Boot restore starts signaling/standby only; it does not activate camera or mic
- [ ] Services do not use START_STICKY unless they reconstruct the owned session
- [ ] Parent standby does not hold an indefinite wake lock
- [ ] Auto-start defaults OFF and reboot requires user-mediated resume
- [ ] Foreground-service types match work that is actually active

## P1: Battery And State Sync

- [ ] Battery registration does not publish the sticky value twice
- [ ] Battery publishes only when percentage changes
- [ ] Reconnecting parent receives one forced current battery value
- [ ] Low-battery notification remains edge-triggered at 20%
- [ ] Notifications-enabled startup at low battery produces exactly one alert
- [ ] Invalid battery values are ignored

## P1: Automated Verification

- [x] Add JVM tests for default state and command transitions
- [ ] Add JVM tests for reconnect action de-duplication
- [ ] Add JVM tests for battery de-duplication and forced sync
- [ ] Add JVM tests for lifecycle/state reducers
- [ ] Run focused tests after every task
- [ ] Run full unit-test suite
- [ ] Run lint/static checks
- [ ] Build debug APK once, at the end

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
