# DA CALLER · 0.10.0-beta.1 validation candidate

Offline Android dialer, package `com.dacaller.app`, with Hebrew RTL and English UI. `:app` is DA CALLER; `:businessbook` and `:offline` remain separate products and APKs.

This candidate includes substantive fixes to list dialing, gesture routing, live refresh and permission handling; archive retention/migration; multi-field contact editing/photos; call notifications, multiple calls, audio routing, proximity and accessible controls. See [the verification matrix](docs/DA_CALLER_VERIFICATION_MATRIX.md) for individually bounded results and remaining gaps.

## Build and validation

Java 17, Android SDK 35 / build-tools 35.0.0, pinned Gradle 8.9 wrapper:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

The second command requires an Android device/emulator. Automated call tests use recorded requests/mocked calls; they never place carrier calls. The debug-only instrumentation host is non-exported, isolates its preferences and denies real telephony/provider access. It is excluded from release builds.

CI validates only DA CALLER, checks APK identity/signature and module isolation, stores APK/test reports, and runs Android 13 UI regressions. A push cannot publish a release. New immutable prereleases require an explicit manual main-branch request, device verification, the permanent signing key and a pinned certificate fingerprint.

## Installation and remaining checks

This is a **test candidate**, not a field-verified public release. Real ringing, answer/decline, SIM/Telecom/audio, lock-screen behavior, OEM background scheduling, proximity and Jelly Star hardware performance require the [physical-device checklist](docs/DA_CALLER_DEVICE_CHECKLIST.md). The original previously uploaded specification was unavailable; a literal comparison with it remains blocked.

The existing public v0.9.0 uses a different Android debug certificate from this environment. The local candidate cannot update it in place. **Do not uninstall an existing installation before preserving its private archive/settings**; uninstall removes private app data. No permanent signing material is committed. Configure `DA_CALLER_KEYSTORE_BASE64`, `DA_CALLER_KEYSTORE_PASSWORD` and `DA_CALLER_CERT_SHA256` securely for future permanent builds; changing keys does not repair an old certificate mismatch.

## Private metadata archive

SQLite stores call metadata, never audio. Choose Off, one/three/six/twelve additional calendar months after first observed disappearance from Android's log, or forever. Records removed before observation cannot be recovered. Off stops new archiving and hides the archive; existing local metadata remains until explicitly deleted. Deletion never changes Android's call log. Current history and the older archive remain separate, with archive access at the end of Recents. Reconciliation runs on opening/after calls and via a persisted 12-hour job, subject to Android permissions and OEM power restrictions. Cloud backup and device transfer are excluded.
