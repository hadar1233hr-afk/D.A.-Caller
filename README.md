# DA CALLER 0.5.0 — configurable in-call buttons

This is a small native Android beta, not feature-equivalent to Google Phone.

## New in 0.5.0
- A modest reduction in the in-call tile height, plus an optional slider from 54 to 84dp.
- Compact three-column in-call grid to fit additional controls on small screens.
- Settings → "כפתורים בזמן שיחה": individual switches for mute, speaker, keypad, hold, Bluetooth, add call, contacts, and call notes.
- Incoming answer/reject and the red end-call button remain visible and cannot be hidden.
- Bluetooth action uses the actual supported Android telecom audio route; shows an error if unavailable.
- Add-call action opens the in-app dialpad. Whether another call can be placed depends on the operator/device and is not guaranteed.
- Contacts action opens the app's contacts screen. Notes are stored locally per number; they are not cloud-synced or encrypted.
- Reopens the in-call grid after returning from Settings to show new choices.

## Why the APK is so small
The app uses Android framework views and platform telephony APIs rather than bundling Material Compose, Google libraries, spam databases, photos, languages, or large assets. A small APK is not equivalent to comprehensive functionality. The app is not yet tested for reliable carrier and OEM behavior, and does not provide protected two-way call recording.

## Testing
GitHub Actions compiles the Android SDK 35 project, verifies its APK signature and publishes DA-CALLER-v0.5.0.apk to Releases. A successful build is not an end-to-end phone-call test. Keep a functioning stock dialer installed.
