# Parent-Authoritative Monitoring Design

## Goal

BabyCam must start in a privacy-safe standby state. Camera capture, baby microphone,
cry detection, torch, lullaby playback, parent camera sharing, and parent talk are OFF
until the parent explicitly enables the relevant capability.

## Product Decisions

- "All default OFF" includes cry detection.
- The signaling connection and foreground service may remain active in standby so the
  parent can reach the baby device.
- A reconnect must preserve the active session state. It must never turn a capability ON
  merely because MQTT or ICE reconnected.
- The baby device is authoritative for hardware state. Parent controls may update
  optimistically for responsiveness, but baby acknowledgements correct the displayed state.
- Existing saved microphone preferences do not auto-enable the microphone. New sessions begin
  OFF. State may be restored only inside the same uninterrupted live session after a transport
  reconnect.

## Considered Approaches

### 1. Pre-provision disabled WebRTC tracks

Create the baby video and audio tracks before the initial offer, but keep the camera capturer
stopped and both tracks disabled. Parent commands start/stop capture or enable/disable the track.

This is the selected approach. It provides privacy-safe startup and keeps stable SDP media
sections, so normal toggles do not require renegotiation.

### 2. Add tracks only after the parent enables media

This gives the smallest startup footprint, but every first activation requires renegotiation.
It adds race conditions across MQTT and ICE recovery and makes control latency less predictable.

### 3. Start media and immediately disable it

This preserves the current SDP flow but can briefly activate Android privacy indicators and
capture frames. It violates the requirement and is rejected.

## Architecture

### Media provisioning

`WebRtcSession` exposes idempotent provisioning operations:

- Baby video track: initialized and attached, capturer initially stopped, track disabled.
- Baby audio track: initialized and attached, track initially disabled.
- Parent talk audio: initialized and attached, track initially disabled.
- Parent camera track: attached to the reserved transceiver, capturer initially stopped.

Repeated provisioning is a no-op. This is defense-in-depth against reconnect callbacks.

### State model

A small framework-free state model represents camera, microphone, torch, cry detection,
parent camera sharing, talk, and playback. Defaults are all false. Incoming commands produce
explicit state transitions and acknowledgements.

`LiveSession` defaults and reset values match this model. Parent UI does not infer ON from the
presence of a track.

### Signaling lifecycle

`SignalingClient` separates transport state from connection-ready events:

- `onState(true/false)` only reports broker reachability.
- A connection-ready callback reports whether the event is initial or reconnect.
- `BabyCamConnection` owns one idempotent setup/recovery path.

Initial baby connection creates one retained offer and registers battery monitoring once.
Reconnect creates one recovery offer. Initial parent connection provisions muted talk audio and
sends one ping. Reconnect sends one recovery ping. Track creation never occurs in these callbacks.

Offer generation is serialized so MQTT reconnect, parent ping, and ICE restart cannot create
overlapping SDP operations.

### State synchronization

Baby sends acknowledgements for camera, microphone, torch, cry detection, and parent-camera
visibility. On parent ping/reconnect, baby sends a full state snapshot plus current battery.
The snapshot reports actual state and does not mutate it.

### Battery behavior

Battery percentage parsing and publication policy are framework-free:

- Ignore invalid level/scale values.
- Publish on first valid observation.
- Publish again only when percentage changes.
- Allow one forced publication for a newly connected/reconnected parent.

The sticky registration result is processed through the same de-duplication path as receiver
callbacks.

### Service lifecycle

`MonitorService` uses `SupervisorJob + Dispatchers.IO`, cancels the scope in `onDestroy`, and
releases `AudioRecord` in a deterministic stop path. The foreground service can remain alive in
standby, but notification copy must not claim active audio/video monitoring while all capabilities
are OFF.

Boot recovery restores standby signaling/service state only. Stored cry detection is reset OFF
for the new service session under the all-default-OFF policy.

## Error Handling

- Failed camera provisioning leaves camera OFF and reports OFF.
- Failed microphone initialization leaves microphone OFF and reports OFF.
- Failed torch operations report OFF.
- Commands received before provisioning are queued only where safe; otherwise they fail closed
  and report OFF.
- Late signaling callbacks after `stop()` are ignored.
- Resource cleanup is idempotent.

## Testing

Framework-free reducers/policies receive JVM unit tests first. Android/WebRTC wrappers remain thin
and are verified by compilation plus the owner's final two-phone acceptance pass.

Required automated cases:

- Every default state is false.
- Camera/mic commands transition only the requested capability.
- Reconnect snapshots do not mutate state.
- Initial and reconnect callbacks each schedule one action.
- Duplicate readiness events do not add tracks or create duplicate recovery work.
- Battery duplicate values are suppressed; forced sync emits once.
- Invalid battery values are ignored.
- Service stop policy is idempotent.

## Explicit Non-Goals

- Replacing the WebRTC Camera2 capturer to guarantee torch on every OEM.
- Adding paid infrastructure or accounts.
- Changing pairing, visual design, or navigation beyond accurate state labels.
- Claiming physical-device acceptance before the owner runs the final checklist.
