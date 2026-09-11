# vorht-capture

Android app that captures WhatsApp verification messages from system notifications
(no screenshots/OCR in the primary flow) and uploads them to the Vorht API.

## Stack

- **Kotlin + Jetpack Compose (Material 3)** — dark-first UI matching the repo style reference
- **Room/SQLite** — every captured event is persisted *before* any upload is attempted
- **WorkManager** — background upload with exponential backoff, network-constrained
- **Retrofit + OkHttp + Gson** — PRD API contract
- **NotificationListenerService** — monitors `com.whatsapp` notifications only
- `compileSdk = targetSdk = 36` (Android 16), `minSdk = 26`

## Reliability model

| Requirement | Implementation |
|---|---|
| Never lose an event when internet drops | Event written to Room first; WorkManager drains the queue only with a network |
| Idempotent uploads | `Idempotency-Key: <event_id>` (UUID) on every request; server duplicate = success |
| Never process the same notification twice | Unique index on `notificationKey` + `INSERT IGNORE` dedup |
| Statuses | `PENDING`, `SENT`, `FAILED`, `REVIEW` |
| Offline queueing | Continues capturing; `Sync now` + automatic retries |
| Manual review | Review tab: unrecognized messages, editable code, one-tap retry |

## API

All delivery goes to the Vorht ops relay — **no localhost, no user-configured endpoints**:

`POST https://carla.codedynasty.dev`

```json
{
  "project": "<whatsapp sender name>",
  "message": "VORHT CAPTURE\nFrom: John Doe\nCode: BIRTH123\nAt: 2026-09-10T20:30:00Z\nEvent: <uuid>\nText: ..."
}
```

- Crash reports and non-fatal errors use the same relay (`project: "vorht-capture"`).
- Any HTTP 2xx counts as delivered; failures stay queued in Room and are retried
  by WorkManager with exponential backoff (network-constrained), so an internet
  drop never loses an event.

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
