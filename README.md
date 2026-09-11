# vorht-capture

Android app that captures WhatsApp verification messages from system notifications
(no screenshots/OCR in the primary flow) and uploads them to the Vorht API.

## Stack

- **Kotlin + Jetpack Compose (Material 3)** — dark-first UI matching the repo style reference
- **NotificationListenerService** — system-bound service monitoring `com.whatsapp` notifications only
- **OkHttp + Gson** — non-blocking coroutine HTTP transport with strict per-attempt timeout bounding
- **compileSdk = targetSdk = 36** (Android 16), `minSdk = 26`

## Delivery & Reliability Model

| Property | Implementation |
|---|---|
| Delivery Semantics | Ephemeral, immediate HTTP attempt; rapid retries (1s, 2s, 3s, 5s) within a 30s freshness window |
| Strict 30s TTL | Events not delivered within 30 seconds are intentionally discarded (no stale OTP delivery) |
| Idempotency | `Idempotency-Key: <event_id>` (UUID) on every request; HTTP 2xx or 409 = success |
| In-Memory Dedup | Dedup heuristic on `(packageName + notificationKey + code/message)` to ignore redundant updates |
| No Deferrable Queue | No Room delivery queue and no WorkManager; state is held in-memory during active attempt |

## API

All delivery goes to the Vorht ops relay — **no localhost, no user-configured endpoints**:

`POST https://carla.codedynasty.dev`
`Headers: Idempotency-Key: <event_id>`

```json
{
  "project": "<whatsapp sender name>",
  "message": "VORHT CAPTURE\nFrom: John Doe\nCode: BIRTH123\nAt: 2026-09-10T20:30:00Z\nEvent: <uuid>\nText: ..."
}
```

- Crash reports and non-fatal errors use the same relay (`project: "vorht-capture"`).
- HTTP 2xx or 409 counts as delivered. Failed attempts retry with backoff strictly within the 30s window.

## Building the APK locally

Prerequisites: JDK 17 (`brew install --cask temurin@17`) and `ANDROID_HOME`
pointing at the Android SDK (`brew install --cask android-commandlinetools`, then
`sdkmanager "platforms;android-36" "build-tools;36.0.0"`). Create
`local.properties` with `sdk.dir=/Users/<you>/Library/Android/sdk`.

```bash
./gradlew assembleDebug    # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease  # signed build needs a signingConfig; R8 minify enabled
```

## First run on a device

1. Install the APK and open Vorht Capture — the setup screen launches
   automatically and asks for both grants.
2. Flip **Notification access → Vorht Capture** in the system screen it opens.
3. Tap **Allow** on the battery-optimization dialog.
4. On Honor/Huawei also: Settings → Battery → App launch → Vorht Capture →
   Manage manually → enable all three switches.
5. Any WhatsApp verification notification is captured, stored, and delivered to
   the team chat via the relay. Its status appears in the Events tab; anything
   unparseable lands in the Review tab.

Note: `usesCleartextTraffic` is enabled so plain-HTTP dev servers work; behind a
production HTTPS API you can remove it from `AndroidManifest.xml`.
