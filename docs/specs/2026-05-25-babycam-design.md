# BabyCam — Design Spec

**Date:** 2026-05-25
**Status:** Approved design, pending implementation plan
**Owner:** mzashah

## 1. Goal

A native Android baby-monitor app. One phone (the **Baby unit**) sits near the baby with
camera + mic and streams live video/audio. Another phone (the **Parent unit**) watches and
listens from anywhere, and gets alerts when the baby cries, the connection drops, or the baby
unit's battery is low. The whole system runs on **only free, lifetime-free services** — no
recurring cost, no credit card.

## 2. Platform & hard constraints

- **Android only.** iOS is intentionally excluded: Apple does not permit continuous background
  camera or auto-start on boot for any app, and free iOS distribution is not possible. These are
  OS-level limits, not tooling choices.
- **Must run in the background with the screen off**, survive being swiped away, and **auto-start
  on boot**. This requires a native foreground service that owns the camera + WebRTC — a WebView
  cannot keep the camera alive in the background. Hence native Kotlin, not a web wrapper.
- **Zero cost, forever.** Every dependency and service must have a genuine lifetime-free tier.

## 3. Tech stack (all free)

| Layer | Choice |
|---|---|
| Language / UI | Kotlin + Jetpack Compose |
| Camera | CameraX |
| Real-time media | WebRTC (`io.github.webrtc-sdk` / Google `webrtc-android`) |
| Background | Foreground Service (`camera` + `microphone` type) + `BOOT_COMPLETED` receiver |
| Signaling / pairing | Supabase Realtime (free tier) |
| NAT traversal | Google STUN (free) + Open Relay TURN (free public) |
| Push alerts (backup) | Firebase Cloud Messaging (free) + Supabase Edge Function to send |
| QR | ZXing (generate + scan) |
| Local notifications | Android NotificationManager |
| Source | Public GitHub repo |

## 4. Architecture

```
 Baby unit (Android)                         Parent unit (Android)
 ┌──────────────────────┐                    ┌──────────────────────┐
 │ Foreground service    │                    │ App (foreground svc   │
 │  - CameraX capture     │  WebRTC P2P media  │  for alert listening) │
 │  - WebRTC peer         │◄──────────────────►│  - WebRTC peer        │
 │  - cry detector (mic)  │   (DTLS-SRTP enc)  │  - live view + talk   │
 │  - lullaby player      │                    │  - lullaby control    │
 └─────────┬─────────────┘                    └─────────┬────────────┘
           │  offer/answer/ICE + events                 │
           └──────────────┬─────────────────────────────┘
                          ▼
              ┌───────────────────────────┐
              │ Supabase                   │  signaling channel (room = secret token)
              │  - Realtime (signaling)    │  pairing record + push tokens
              │  - Edge Function -> FCM    │  sends cry/offline/low-batt push
              └───────────────────────────┘
              Google STUN + Open Relay TURN  (media relay when needed)
```

**Connection model:** The baby unit's foreground service keeps a Supabase Realtime subscription
open continuously, so it is always reachable. The parent connects on demand (opening the app or
tapping an alert) — media does not stream 24/7 (saves battery/data). Same-Wi-Fi connections use
direct/host+STUN candidates (no TURN). Remote connections fall back to free TURN.

## 5. Roles & screens

Single app, role chosen on first run (and changeable later).

1. **Welcome / splash** — logo, intro, Get started.
2. **Choose device** — Baby unit vs Parent unit.
3. **Permissions** — camera, microphone, notifications (Android 13+ runtime).
4. **Battery/autostart setup** — OEM-specific guidance to disable battery optimization and allow
   autostart (Infinix, Tecno, Xiaomi, Oppo/Vivo, Samsung, stock). Critical for reliability.
5. **Baby — pairing** — shows QR containing a high-entropy room token.
6. **Baby — active** — status (monitoring active), camera/mic/cry indicators, "runs in background /
   screen-off / auto-start on boot", stop button.
7. **Parent — scan** — QR scanner, auto-detect.
8. **Parent — live view** — full video, hold-to-talk, night mode, snapshot, lullaby, sound on/off,
   baby battery + connection status.
9. **Lullaby picker** — soft lullaby, white noise, heartbeat, rain (plays on baby unit).
10. **Settings** — cry sensitivity, auto-start toggle, auto night mode, video quality, data-saver
    (audio-only), app lock, notification sound, about.
11. **States** — reconnecting overlay; lock-screen alerts (cry / connection lost / low battery);
    app-lock unlock screen.

## 6. Feature specs

1. **Live video + audio** — WebRTC, baby → parent. Auto aspect, front/back camera switch on baby.
2. **Two-way talk** — hold-to-talk: parent mic → baby unit speaker.
3. **Cry / sound detection** — baby unit analyzes mic amplitude against a sensitivity threshold;
   sustained level → fire alert (local notification + FCM). Runs even with no parent connected.
4. **Night mode + snapshot** — low-light view treatment on parent; snapshot saves a frame to the
   parent phone gallery.
5. **QR pairing** — baby shows QR (room token); parent scans → join that room.
6. **Background always-on + boot auto-start** — foreground service + `BOOT_COMPLETED` receiver
   restarts the baby unit's monitoring after reboot.
7. **Connection-lost alert + auto-reconnect** — detect ICE/connection drop; parent alerted; both
   sides retry (exponential backoff, up to N attempts) and restore automatically.
8. **Baby-unit low-battery alert** — baby unit reports battery level; threshold crossing → parent
   alert.
9. **Lullaby / white-noise playback** — bundled audio files; parent triggers, baby unit plays.
10. **Pairing security** — room token is long + random; only one parent admitted per room; extra
    join attempts rejected. Media encrypted by WebRTC default (DTLS-SRTP).
11. **Remembered pairing** — pairing persisted locally; parent auto-reconnects to the known baby
    unit without rescanning.
12. **OEM battery-setup guidance** — see screen 4.
13. **FCM push backup** — alerts delivered via FCM so they arrive even if the local service was
    killed; baby unit writes event to Supabase, an Edge Function sends FCM to the parent token.
14. **Data-saver** — audio-only mode (no video) + video quality selector (auto/low/high).
15. **App lock** — optional PIN / biometric to open the app / view the feed.

## 7. Free-service limits & risks (explicit)

- **Open Relay (free TURN):** bandwidth-limited and not guaranteed; remote (non-Wi-Fi) viewing is
  best-effort. Same-Wi-Fi viewing needs no TURN and is reliable. A self-hosted/own TURN can be
  added later without app changes (config only).
- **Supabase free project** pauses after ~1 week of total inactivity; resumes on next use. Fine for
  a regularly-used personal monitor.
- **OEM battery killers** are the top real-world failure mode; mitigated by screen 4 + FCM backup,
  but cannot be 100% guaranteed on every device.

## 8. Permissions / manifest

`CAMERA`, `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_CAMERA`, `FOREGROUND_SERVICE_MICROPHONE`, `RECEIVE_BOOT_COMPLETED`,
`INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `USE_BIOMETRIC` (app lock),
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

## 9. Out of scope (v1)

iOS; cloud recording / clip history; temperature/humidity; motion detection; multiple cameras;
multiple simultaneous viewers; Play Store publishing (sideload APK for now).

## 10. Deliverable

- Full source in a public GitHub repo.
- A buildable, installable **APK** for the user's Android phone(s).
- Android SDK + JDK set up locally to produce the APK.
