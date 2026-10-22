# DA CALLER v0.3.0 · Premium Dialer beta

Native Android app, Hebrew RTL interface, no Internet permission. Tested at compile/build level using Android SDK 35. Real-life incoming and outgoing calls have **not** yet been tested on the target phone.

## What's new in 0.3
- More reliable in-call screen with answer/decline from the call notification, end, microphone mute, speaker route, hold/resume, and DTMF keypad.
- Audio controls now use Android telecom InCallService, not AudioManager system toggles.
- Compact in-call interface for small phones, including 4-inch displays.
- Optimized rendering: contact number lookup cached; contacts and call history read in background; recents and contacts shown in batches.
- Gold/graphite design, AMOLED black mode, optional silver highlight, startup tab, summary visibility, per-tab text sizing.
- Fixed denied CALL_PHONE permission loop. Native dialer opens instead if direct calling is denied.
- Modern monochrome gold widgets; widget selection is searchable and loaded in a background thread.
- "Open contact" widget opens the actual system contact details.
- Automatic APK verification during GitHub Actions builds.

## Limitations and safety
**Early beta! Keep another working phone app installed.** Request DA CALLER as default Phone app in settings. Incoming call notifications and in-call UI might behave differently on different Android vendors; confirm they work before relying on this app.

The history dashboard uses local Android call logs (permission required) for total call minutes in the current day/week/month/year, plus incoming/outgoing/missed call counts.

No two-way call recording is implemented: third-party apps cannot guarantee privileged telecom uplink/downlink capture on Android 13. Do not infer recording from the UI.

No Internet permission. Contacts and favorites are local; backup is disabled to avoid exporting personal data.

## Install
Open [Releases](../../releases) and download **DA-CALLER-v0.3.0.apk**.
This is a debug-signed development APK, built on a hosted runner. Because the signing certificate can change between GitHub Actions runs, you may need to **uninstall the old beta** before installing it. Uninstalling can erase the app's favorites and preferences. Device contacts and call history belong to Android, not DA CALLER.

## Build
The [GitHub Actions workflow](.github/workflows/android.yml) builds, verifies and publishes an APK for version 0.3.0. Local builds require JDK 17, Android SDK 35 and Gradle 8.9.
