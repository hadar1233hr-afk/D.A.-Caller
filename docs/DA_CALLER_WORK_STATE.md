# DA CALLER continuation state

## Additional owner tasks — 2026-11-04 (pending)

1. IMPLEMENTED, pending native verification: the unknown-number dialog reached from Recents now offers explicit Create new contact, preserving the selected number and issuing no call. Hidden/unknown system sentinels have no create action.10 MainActivity regression tests passed (0 failures/errors/skips), including2 new cases. [Results](evidence/recents-create-contact/verification.json). Fresh native screenshots/full CI remain required.
2. Repair Nothing and Xiaomi call-screen arrangement. Increase digits across all five call designs; Google additionally needs a rounder, more attractive, bolder typeface. Verify large-font Hebrew/English and the Jelly Star-sized viewport without overlaps.
3. IMPLEMENTED, pending native Telecom verification: incoming prompt follows the finger and resets partial/cancelled/multi-pointer gestures; the ordinary tap-to-answer tile was removed. The emergency fallback uses the same gesture binding. Completion targets only the currently owned ringing Call; explicit accessibility action remains available.16 focused unit cases passed; after the final shared-binding change2 UI regressions passed again, along with Android-test compilation/lint. [Proof](evidence/incoming-swipe-answer/verification.json). Actual emulator Telecom swipes/new screenshots remain NOT TESTED.

These additions were recorded at the owner's request; implementation and verification remain pending. Existing backup-capture, licensed-data and compatible-signing work remains active. No new APK is approved.

## Native incoming-swipe verification in progress

Backup-capture source4c7922b / CI41956399559 stopped before Android execution:356 unit cases ran,354 passed and2 old CallScreenActivity tests still required the removed ordinary Answer tile. They are being updated to assert the explicit accessibility Answer action and stale-call guard; this failure is not native evidence.

The real reminder Telecom scenario now injects touchscreen DOWN/MOVE/UP/CANCEL events into the production incoming prompt, preserving its four actual modem calls. It checks tap/partial/cancelled drags remain RINGING, translation resets, and a complete drag activates the same Call. Two new raw captures (`incoming-swipe-resting`, `incoming-swipe-dragging`) raise the full required matrix from241 to243 while retaining all earlier captures. New test Java compilation/lint passed; the corrected full unit run produced356 unique cases with0 failures/errors/skips. A final null-safe accessibility-label assertion still needs its focused rerun. Actual native run and visual review remain PENDING. No swipe PASS is inferred from the previous CallStyle PendingIntent answer.

## Latest verified checkpoint

Source46c500a / CI41851873318 completed:352 unique unit PASS;34 native cases,33 PASS and1 explicit business-dataset MISSING failure;0 errors/skips. All237 functional images were freshly reviewed:101 call and44 island frames PASS,91 other PASS and1 visual FAIL (personalization-backup-saved-light.png: white system icons on near-white bars). All36 island compositor proofs independently passed. Reminder real Telecom/OS-alarm/read-only checks passed again. No release approval. [Retained second-run evidence](evidence/callback-business-2026-11-04/second-ci/pipeline-summary.json).

Backup compositor producer and independent PNG verifier are now implemented for the three existing backup captures. They require three consecutive focused, visible native-bar samples within five seconds and save/hash exactly the checked Bitmap. Android-test Java compilation and lint passed; fresh emulator execution and visual review remain PENDING. Backup SAF is intercepted with a test provider, not real DocumentsUI. Compatible beta9 private key and licensed business dataset remain unavailable.


## Active new owner request after the completed beta.9 release

On2026-11-02 the owner added: (1) opt-in end-of-day reminders for today's missed callers without a later connected call longer than one second; group identities and distinguish missed counts from callback attempts; (2) a sourced, licensed, offline Israeli business directory in a separate small Contacts tab, with measured coverage rather than invented hundreds of thousands/millions; (3) durable update signing. The user confirms beta.9 is installed and does not know what a keystore is. Android requires that installed certificate's exact private key: audit its availability before claiming an update path. Never suggest uninstalling. The prior immutable release is baseline evidence, not approval of new edits. Scheduled repeats of the old prompt must preserve this newer objective and avoid competing development. New implementation/test counts/version remain pending; no new APK is verified yet.

Reminder cutoff defaults to21:00 local and is user-selectable. A connection is actual duration>=2s after the latest missed call for the normalized number; a later miss makes an earlier connection insufficient. All runtime data work remains offline and off the UI thread. Personal contacts must never enter the public business dataset. Licensed source access, measured row counts and freshness are pending research. A business-source agent hit transient selected-model capacity and was retried with the same model, with no account/model changes. Actual usage-quota errors must stop work rather than trigger a model/account switch.

### Resumed implementation and first CI review (2026-11-04)

The reminder reducer/scheduler/list, separate business UI/backend, importer and Settings/navigation integration are implemented and undergoing verification on this branch. A first complete local unit run executed344 tests (343 PASS,1 FTS4 search failure); that portability defect was fixed, and two day/time-zone regressions were added. The final local run then passed349 unique unit tests, debug/unsigned-release/Android-test compilation and lint (0errors,6warnings). CI41803113785 then confirmed349 unique unit PASS and34 actual native cases:33 PASS,1 explicit missing-business-data failure,0 errors/skips. The237 functional PNGs plus2 diagnostics were all actually reviewed; one DEBUG fixture frame has unreadable light native bars and four English contact chips break Businesses inside the word. These require small UI/capture fixes and a fresh source run; the beta.9 results below do not apply to these edits. Candidate version is0.11.0-beta.10/code20, unreleased. Source402da24a939113fa08655e704633d86a0375d959/app1ce289c4ba733312821173549d2efd99f19f9bfc; official raw artifacts retained at evidence commit3477355fca1c5b646a3c0701a1d979c208f9a44c. [First failed visual review](evidence/callback-business-2026-11-04/first-ci/review-summary.json) records the exact frames; it is not publication approval. Fresh unit/native/visual evidence is required. At resume no competing worker or newer PR head was present.

The signing audit found beta.9's installed public certificate `3d41dabd6e32a28ba6af2c57bb872fb3dfafa1f85a5c8523b72b54e4d8a32f56`, but no matching private key in the retained CI artifacts, cache or local keystore. An in-place beta.9 update remains **BLOCKED**; see [the signing audit and recovery procedure](DA_CALLER_SIGNING.md). The signing tools passed36 mocked security-contract tests and the data tooling passed23 fixture tests; this is not proof of real APK signing or an update. Persistent-key provisioning and an owner-controlled private backup remain pending. Publication must fail closed rather than silently use another ephemeral debug key.

Business source acquisition is **BLOCKED**: the configured proxy returnedHTTP403 for the Geofabrik public extract; the executor policy does not permit that host. Actual source bytes downloaded:0; actual production business records imported:0. Do not bypass this policy, ship synthetic records or claim complete/current national coverage. Reproducible importer, provenance/ODbL requirements and source-access documentation are implemented under scripts/business-directory/. The business UI must state dataset unavailability honestly until licensed source data are imported and measured.

### Second source: bounded native-bar capture and the small business entry

The English chip now stays single-line at12sp/96dp×48dp and passes3 native-font/touch unit tests at320dp/font1.3. All36 island PNGs must now pass3 consecutive compositor samples (clock left-half and navigation contrast≥40, actual visible insets/focus, bounded5s); the exact checked Bitmap is saved and hash-bound to raw records. A narrow1e-6 check accepts Android's actual1.2999999523 font scale. Debug/unsigned-release/native-test Java/lint passed locally (0errors,6warnings). A local352-test run had350PASS and2 new touch-test coordinate failures; corrected coordinate conversion then passed all3 focused tests. Fresh full CI/native/visual evidence is required; no352-test PASS is claimed yet. [Preflight](evidence/callback-business-2026-11-04/second-source-preflight.json).

Updated 2026-11-04. Active branch: `codex/da-caller-dynamic-island-20261027`; draft [PR5](https://github.com/hadar1233hr-afk/D.A.-Caller/pull/5). Published verified debug test version: **0.11.0-beta.9**, code **19**.

The owner added two requirements after the external-island review: DA CALLER notifications for missed calls, replacing Telecom's fallback system-dialer notification, and an offline changelog in Settings showing the progress of each version. These software features are now implemented and delivered together in the verified GitHub debug prerelease. Do not publish the earlier baseline as if it contained the two new features.

## Completed debug release and verified download

The requested external island, DA CALLER missed-call notifications and local nineteen-version changelog are complete in [v0.11.0-beta.9-debug](https://github.com/hadar1233hr-afk/D.A.-Caller/releases/tag/v0.11.0-beta.9-debug). [Verified APK](https://github.com/hadar1233hr-afk/D.A.-Caller/releases/download/v0.11.0-beta.9-debug/DA-CALLER-v0.11.0-beta.9-debug.apk).

- Visually reviewed source `04e58247399aaa579b7de51c8439fa41653ab98c`, CI41341757982:278 unit/32 native, zero failures/errors/skips, all233 original PNGs actually inspected. [Review](evidence/call-followup-2026-11-01/visual-review.json).
- Publication source/tag `c9d1eca7385c1b1711f5004185f292ca923c36bb`, [CI41363043464](https://github.com/hadar1233hr-afk/D.A.-Caller/actions/runs/41363043464): validate/android-ui/preview-debug all PASS, another278 unique unit/32 unique native tests, zero failures/errors/skips,233 PNGs. Only documents/evidence changed from the reviewed source; app tree remains `7d00a6eca4d9f5005329d4790d6f8383b48e42a7`.
- All five public assets were freshly downloaded by their public URLs with HTTP200; APK bytes, checksum, certificate, metadata and every one of311 public UI archive files match the final native artifacts. Actual release and tag target are the publication source above. [Public proof](evidence/call-followup-2026-11-01/public-verification.json), [complete release/archive proof](evidence/call-followup-2026-11-01/public-release-envelope-verification.json), [summary](evidence/call-followup-2026-11-01/publication-summary.json).
- APK SHA256 `10b78c9b422b9cb678d8ab97ced02818d40103de972bd3be6719df7d39c4396d`; signer `3d41dabd6e32a28ba6af2c57bb872fb3dfafa1f85a5c8523b72b54e4d8a32f56`, different from beta.8. No matching beta.8 key is available; do not uninstall an existing installation. Hardware and compatible-key upgrade limits below remain NOT TESTED. PR5 remains draft.

No software development remains for this request. The verified APK link is the Hebrew completion response for this session; after it has been sent, scheduled checks must not repeat the link or invent new tasks. This post-publication checkpoint is documents only; the immutable release tag stays at the tested publication source. Future application changes require a new version and a fresh review, never an overwrite of this release.

## Verified external-island baseline

- Source `5d984dc5f9d336a71755b33951ab09d50f11193e`; app tree `077ea26b62ac30ea3856fd78e3d7386a35b5cb0e`.
- [CI41287047774](https://github.com/hadar1233hr-afk/D.A.-Caller/actions/runs/41287047774):257 unit and30 Android13 tests, zero failures/errors/skips;227 unique480x854 native PNGs.
- All227 were visually inspected:101 call-design frames,44 island/external frames and82 other frames. [Review and hashes](evidence/external-island-2026-10-28/baseline-227/visual-review.json); [unmodified images and native proof](evidence/external-island-2026-10-28/baseline-227/reviewed-Android13-227.zip). Bounded ellipsization, small-screen list/FAB behavior and static-fixture limits are recorded in the reviews.
- The optional external island works for outgoing/active/held calls. Incoming calls retain the native HIGH CallStyle; actual SystemUI pointer answer activates the call. Protected system screens may hide overlays. Native controls, return/Back/query, permission loss/regrant, confirmed hang-up and cleanup passed with the real emulator modem and production Telecom service.
- SystemUI's actual700ms touch guard is read before a single native Answer tap. Native AXroot bounds fully contain the island; original WindowInfo bounds may differ by at most one physical pixel per edge. Both raw rectangles remain in the proof.
- Speaker switching is **UNAVAILABLE** on this emulator's only supported route8/mask8; unsupported controls are hidden. This is not a claim of physical audio routing.

## Implemented follow-up and verification history

The missed-call receiver/channel, snapshot-scoped read acknowledgement, direct Recents action, and bilingual19-version Settings changelog are now implemented. Local verification passed278 unit tests before the final role-guard refactor; the21 affected missed-call tests passed again afterwards. Debug, unsigned Release, Android-test Java compilation and lint also passed after that refactor (0 errors,6 existing warnings). These local results preceded the successful fresh CI described above; only its actual artifacts establish the final278/32/233 counts.

The new native test and verifier preserve all227 earlier captures and add six captures. They exercise three actual unanswered emulator calls, the protected Telecom broadcast, our notification versus both stock notification owners, explicit Clear/Open, retained call-log rows and the actual Settings changelog. The first source was committed as `def76d1261d18e31ddf1e08dbb943d6d431c5944`.

### First follow-up CI: not approved

[CI41336631898](https://github.com/hadar1233hr-afk/D.A.-Caller/actions/runs/41336631898) passed278 actual unit tests, builds and lint, but the native job was cancelled at its30-minute limit after progress stopped at15/32. It produced no complete native JUnit result or pulled PNG matrix. [Failure review](evidence/call-followup-2026-11-01/first-ci-review.json) records the actual artifact digests and logcat observations; immutable raw evidence is retained at evidence-branch commit `6e59291`.

The16th test opened the light changelog and returned to Main, then hung when instrumentation waited for creation of a `singleTop` Main that Android reused through `onNewIntent`. The test now creates a fresh disposable-emulator task for each palette while preserving its actual Back/UI assertions. The host test command has a15-minute bound and captures failure state before the job's30-minute limit. Local Android-test Java compilation and shell syntax checks passed after this correction. A complete fresh32-test/233-image run and visual review remain required; this failure is not publication approval.

### Second follow-up CI: functional PASS, display correction required

[CI41339945300](https://github.com/hadar1233hr-afk/D.A.-Caller/actions/runs/41339945300), source `40925aacc1280f88303c745f50820ce4e6fd4873`, app tree `9ea43d2e5e45001f2d874056e58606e0f56ff8e9`, passed278 actual unit tests and32 actual Android13 tests with zero failures/errors/skips and233 PNGs. Official artifact digests were independently verified; exact raw artifacts remain at evidence commit `bec4c24279d03ca8d5365da12d25cc2a1215af85`.

Fresh visual review covered101 call,88 other and24 island images before finding that the inline Hebrew version labels displayed `beta.9-0.11.0` instead of `0.11.0-beta.9`. The dedicated version card was correct. [Second-run review](evidence/call-followup-2026-11-01/second-ci-review.json) and source2 group reports retain this evidence without publication approval.

Both the Settings subtitle and changelog installed-version label now wrap the version explicitly LTR with Android's BidiFormatter. The native test checks actual glyph-selection rectangles for increasing horizontal order in both places; the CI verifier and final evidence helpers require the resulting native proof. Java compilation and lint passed after this edit (0 errors,6 existing warnings). A local full-unit retry encountered Robolectric initialization failures because the host's download-lock file is read-only; it is not counted as a PASS. The corrected source subsequently passed the fresh CI and complete visual review described above.

The SDK annotation on `TelecomManager.cancelMissedCallsNotification()` models only `MODIFY_PHONE_STATE`. [Android13's server enforcement](https://github.com/aosp-mirror-neo/platform_packages_services_Telecomm/blob/3f4456d1b27cc019b4bc4f79fda8273d4506ea95/src/com/android/server/telecom/TelecomServiceImpl.java#L1116) also authorizes the verified default/system dialer. A narrow private helper checks the actual default package again, handles runtime failure and suppresses only this annotation mismatch; no signature-only permission is requested.

## Completed verification

1. Actual emulator modem calls reached Android Telecom, the production InCallService/external island and protected missed-call receiver. Native return/end/confirmation, optional overlay permission and cleanup were verified; protected Android screens retain native notification handling.
2. Three actual unanswered calls produced DA CALLER notifications with contact identity, grouped counts and native Clear/Open. No new stock-dialer missed notification competed, and all three call-log rows were retained with scoped read acknowledgement.
3. The production Settings entry opened the local changelog; nineteen native version cards, light/dark/AMOLED, Hebrew/English, correct drawn version order, scrolling/expand and Back were checked.
4. All227 previous captures plus six new captures were retained; all233 were visually inspected and hash-bound to the reviewed application tree. Final publication ran278/32 tests again and retained its exact emulator-tested APK.
5. Public five-asset/tag/APK/UI/JUnit byte identity passed. The final proof and Hebrew report are in the repository; signing/hardware limitations remain below.

Local orchestration checkpoints are also in `/workspace/work/DA_CALLER_ISLAND_STATE.txt` when that workspace is available. Do not start a competing change if another active worker or current CI run is already handling this continuation; inspect its state first. Save meaningful progress in the repository before an interruption.

## Installation and hardware limits

No matching private signing key for the published beta.8 debug APK is available. A new ephemeral debug build cannot update a differently signed install. **Do not uninstall an existing application: that deletes its private archive and settings. Settings backup does not back up the archive.** Use a spare device until a compatible signing/update path is established.

Physical Unihertz Jelly Star, SIM/carrier calls, audible microphone/speaker/Bluetooth routing, lockscreen/proximity/OEM behavior, full TalkBack and a matching-key data-preserving upgrade remain **NOT TESTED**. The APK candidate is a debug test build, not a production/device-verified release.
