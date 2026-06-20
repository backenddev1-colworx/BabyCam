# Debug Runtime Hardening Design

## Scope

This pass targets the debug APK used for two-phone functional testing. It does not add release
signing, replace the temporary four-digit pairing code, or migrate the development TURN service.

## Reliability

- Parent session sync is retried until the baby acknowledges the exact sync ID.
- Control commands carry a monotonic revision. The baby ignores stale revisions and the parent
  ignores stale acknowledgements, so rapid ON/OFF actions cannot restore an older state.
- Explicit disconnect waits briefly for a baby acknowledgement before MQTT closes.
- Parent camera replay reports the actual camera state, including OFF.
- Presence timeout begins after MQTT is ready, not while MQTT is still connecting.

## Audio And Privacy

- Camera, baby microphone, cry detection, parent camera, and parent talk remain OFF by default.
- The WebRTC audio device exposes captured PCM through its supported samples callback.
- Cry detection uses WebRTC PCM while the baby microphone is active.
- When cry detection is active but WebRTC microphone is inactive, the foreground service owns the
  fallback AudioRecord.
- Ownership changes stop one source before starting the other, preventing two microphone captures.
- Communication-mode speaker routing is enabled only while an audio feature is active and never
  forces the user's call volume to maximum.

## Lifecycle And Resources

- App lock relocks after the app leaves the foreground.
- The monitor service does not hold an indefinite partial wake lock while all monitoring is off.
- Enabling notifications while the battery is already low can produce one low-battery alert.

## Verification

Pure ordering, sync, lifecycle, audio ownership, and battery policies receive JVM tests. The final
verification is one `testDebugUnitTest`, one `lintDebug`, and one `assembleDebug`; physical
two-device testing remains the owner's final acceptance step.
