# DA CALLER v0.6.0 · Performance & Precision Preview

An Android 8+ offline native dialer for compact phones, with Android default-dialer and InCallService integration.

## Performance work
- **Persistent activity shell**: changing tabs no longer tears down the entire Activity view hierarchy, toolbar, ScrollView, and navigation bar. Only the page content is refreshed.
- **No pointless recent-call repaint on resume**: cached call history returns without triggering UI reconstruction.
- **Incremental list rendering**: 16 recents / 24 contacts loaded initially, with "show more" options.
- **Background device queries**: contacts and call logs use dedicated workers; phone number/name lookup is cached.
- **Cached date formatter** in recent calls avoids repeated formatter instantiation.
- No large third-party UI/runtime dependencies.

## Appearance and feedback
- New vector icons for the dial action and bottom navigation; selected-tab pill treatment and high-contrast labels.
- Distinct recent-call categories: blue incoming, green outgoing, red missed, amber rejected/zero-duration outgoing attempts, and violet blocked calls. Every category includes a **text label**, not color alone.
- **Zero-duration outgoing calls are not proof of network failure**. The app labels them as "יוצאת · ללא זמן שיחה".
- Refined dark graphite in-call buttons with real vector icons (mic, speaker, keypad, hold, Bluetooth, add call, contacts, note).
- Touch animations and a short customizable physical vibration. Settings offer off/on and subtle / standard / emphasized.
- AMOLED black mode and compact call statistics remain available.
- Explicit configurable list of in-call buttons, with accept/reject/hangup always visible.

## Still to validate
Success in GitHub Actions verifies that the app compiles and the APK signature is valid. It does not establish real-world latency or reliability on the Unihertz Jelly Star. Before adopting DA CALLER as the sole phone app, test incoming calls, dialing, lock-screen call notifications, in-call controls, and Bluetooth. Keep the stock dialer installed.

This version is **not** feature-complete with Secure Dialer; it is an original lightweight prototype using some comparable UX features and public Android APIs. Two-way call recording is not supported due to Android system restrictions.

## Install
Download DA-CALLER-v0.6.0.apk from [Releases](../../releases). APK signing is debug and may differ between builds, meaning an Android reinstall may be needed; uninstalling an old build can erase app-local favorites/settings. Device contacts and Android call history are not owned by this app.

## Build
GitHub Actions uses Gradle, Android SDK 35, a signature check, and publishes DA CALLER and the separate DA ID Offline APK. This release only updates the DA CALLER version; the independent offline ID module is unchanged.
