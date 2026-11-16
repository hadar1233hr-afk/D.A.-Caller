# DA CALLER – Shizuku call recording: Phase 1 (2026-11-16)

**Status: source-only lifecycle scaffold; NOT wired to Telecom or Shizuku; NO working call recorder or APK.**

## Baseline and containment
- Derived from `codex/da-caller-dynamic-island-20261027` at `a6eaf11affb08bb54bf2e150fbf24e9900edd3f2` (0.13.0 / versionCode 22). Independent branch; PR #5 and `main` untouched.
- Existing `PhoneSessionService` extends `InCallService`; its `onCallAdded`, call callback `onStateChanged`, and `onCallRemoved` can supply events later, keyed by its existing opaque per-call UUID. No PhoneSessionService changes in this phase.
- Never edit APK signatures, release gates, business directory, archives or existing UI to integrate capture until separately reviewed.

## Implemented and locally validated
- Added independent Java `CallRecordingCoordinator`: default-off opt-in gate plus separate Shizuku-authorized-and-running gate.
- Starts only on ACTIVE; deduplicates repeated callbacks and prevents a second call taking over one recording stream.
- Stops on HOLDING / DISCONNECTING / DISCONNECTED / REMOVED, explicit opt-out, Shizuku loss, or shutdown; HOLDING→ACTIVE requires a *new* segment, never overwrite.
- On any backend start or stop exception, fail closed (latch fault and block restart). Failed start requests backend cleanup.
- Call tokens are opaque; no names or phone numbers are recorded by this class.
- Twelve source-level test cases passed under a standalone local JDK 21 test harness with temporary minimal JUnit-API stubs. **This is not Gradle, Android CI, emulator, Shizuku, audio, or device proof.** Run real repository JUnit/Gradle CI next.

## Evidence and source constraints
- ShizuCallRecorder (`kitsumed/ShizuCallRecorder`) implements capture through Shizuku-powered shell UserService, AudioRecord and privileged AudioPolicy reflection; includes GPL-3.0 + Section 7 additional terms. No source copied.
- RivoPhone-2.2 ZIP shows a scrcpy-over-Shizuku capture approach, but current `user-grinch/RivoPhone` GitHub LICENSE declares all rights reserved, different from the archive. Do not copy source or binaries without verifying author permission/license. No source copied.
- Shell Shizuku permissions cannot be assumed to unlock two-party capture across vendors. The Android 11, 12, 15 matrix must be independently tested with consent.

## Next development slice (not done)
1. Add a runtime Shizuku capability probe using an approved library/dependency and explicit user-grant checks, checking binder death/permission revocation and actual remote permission availability.
2. Implement an independent recording backend on a worker thread (no main-thread blocking), with fail-closed lifecycle, local app-private files, storage limits, and explicit per-recording consent/visibility.
3. Wire existing Telecom call UUID/state callbacks only after tests; prevent accidental duplicate capture from multiple calls, and never introduce a microphone fallback silently.
4. Test hardware on Bluefox NX1 Android 15: call parties' explicit consent, Shizuku running, start/stop, two separate voices, pause/resume, BT/speaker, lock-screen, recovery and privacy. Repeat on QIN F21 Pro Android 11 and Android 12.
5. Only after reliable real capture proof, work on opt-in STT Hebrew and later DA CHAT. No Release/APK until signing/CI/native/visual guards are satisfied separately.

## Decision pending from device tests
Device test feedback: Shizuku authorized? actual recording file? near/far side audible independently? any system restrictions or app crash? Never upload private recordings unless deliberately redacted and authorized.
