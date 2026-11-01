# DA CALLER continuation state

Updated 2026-11-01. Active branch: `codex/da-caller-dynamic-island-20261027`; draft [PR5](https://github.com/hadar1233hr-afk/D.A.-Caller/pull/5). Candidate version remains **0.11.0-beta.9**, code **19**, and has not been published.

The owner added two requirements after the external-island review: DA CALLER notifications for missed calls, replacing Telecom's fallback system-dialer notification, and an offline changelog in Settings showing the progress of each version. Finish these together with the external island, then deliver a verified APK from GitHub Releases. Do not publish the earlier baseline as if it contained the two new features.

## Verified external-island baseline

- Source `5d984dc5f9d336a71755b33951ab09d50f11193e`; app tree `077ea26b62ac30ea3856fd78e3d7386a35b5cb0e`.
- [CI41287047774](https://github.com/hadar1233hr-afk/D.A.-Caller/actions/runs/41287047774):257 unit and30 Android13 tests, zero failures/errors/skips;227 unique480x854 native PNGs.
- All227 were visually inspected:101 call-design frames,44 island/external frames and82 other frames. [Review and hashes](evidence/external-island-2026-10-28/baseline-227/visual-review.json); [unmodified images and native proof](evidence/external-island-2026-10-28/baseline-227/reviewed-Android13-227.zip). Bounded ellipsization, small-screen list/FAB behavior and static-fixture limits are recorded in the reviews.
- The optional external island works for outgoing/active/held calls. Incoming calls retain the native HIGH CallStyle; actual SystemUI pointer answer activates the call. Protected system screens may hide overlays. Native controls, return/Back/query, permission loss/regrant, confirmed hang-up and cleanup passed with the real emulator modem and production Telecom service.
- SystemUI's actual700ms touch guard is read before a single native Answer tap. Native AXroot bounds fully contain the island; original WindowInfo bounds may differ by at most one physical pixel per edge. Both raw rectangles remain in the proof.
- Speaker switching is **UNAVAILABLE** on this emulator's only supported route8/mask8; unsupported controls are hidden. This is not a claim of physical audio routing.

## New code implemented; full native verification pending

The missed-call receiver/channel, snapshot-scoped read acknowledgement, direct Recents action, and bilingual19-version Settings changelog are now implemented. Local verification passed278 unit tests before the final role-guard refactor; the21 affected missed-call tests passed again afterwards. Debug, unsigned Release, Android-test Java compilation and lint also passed after that refactor (0 errors,6 existing warnings). None of this is a claim that the new32-test/233-image emulator run has passed.

The new native test and verifier preserve all227 earlier captures and add six captures. They exercise three actual unanswered emulator calls, the protected Telecom broadcast, our notification versus both stock notification owners, explicit Clear/Open, retained call-log rows and the actual Settings changelog. Next: commit/push this application source, run the complete new CI suite, inspect every fresh image and finish the publication checks below.

The SDK annotation on `TelecomManager.cancelMissedCallsNotification()` models only `MODIFY_PHONE_STATE`. [Android13's server enforcement](https://github.com/aosp-mirror-neo/platform_packages_services_Telecomm/blob/3f4456d1b27cc019b4bc4f79fda8273d4506ea95/src/com/android/server/telecom/TelecomServiceImpl.java#L1116) also authorizes the verified default/system dialer. A narrow private helper checks the actual default package again, handles runtime failure and suppresses only this annotation mismatch; no signature-only permission is requested.

## Completion checklist

1. Verify the implemented protected platform `android.telecom.action.SHOW_MISSED_CALLS_NOTIFICATION` receiver and separate missed-call channel. Contact resolution runs off the UI thread. Do not infer misses from arbitrary disconnects, auto-dial from broadcasts, or cancel another application's notifications. Only explicit opening/dismissal may mark the captured missed-row IDs as read; never delete call-log or archive rows.
2. Verify the implemented Settings changelog and local bilingual19-version catalog. Show the installed test version and actual source history without invented publication dates. Preserve RTL, dark/light/AMOLED, scroll and Back behavior.
3. Run the extended native suite while keeping all227 baseline captures. Added captures are two missed notifications (single/multiple) and four changelog captures (light/dark top/bottom), giving233 PNGs and32 native test methods; confirm these counts from actual CI output.
4. Build/unit/lint, run the complete Android13 emulator suite, inspect every fresh screenshot for the changed application tree, and retain independent per-image hashes. Only then add the visual-review publication label and commit the final evidence/report. The later publication run must pass all checks again and publish its exact emulator-tested APK.
5. Download public release assets and verify APK bytes, checksum, signer, package/version, no INTERNET, module isolation, screenshot bytes/manifests and release-tag target against that final run. Update PR5 with the exact results and download link; keep the PR draft while device/signing limitations remain.

Local orchestration checkpoints are also in `/workspace/work/DA_CALLER_ISLAND_STATE.txt` when that workspace is available. Do not start a competing change if another active worker or current CI run is already handling this continuation; inspect its state first. Save meaningful progress in the repository before an interruption.

## Installation and hardware limits

No matching private signing key for the published beta.8 debug APK is available. A new ephemeral debug build cannot update a differently signed install. **Do not uninstall an existing application: that deletes its private archive and settings. Settings backup does not back up the archive.** Use a spare device until a compatible signing/update path is established.

Physical Unihertz Jelly Star, SIM/carrier calls, audible microphone/speaker/Bluetooth routing, lockscreen/proximity/OEM behavior, full TalkBack and a matching-key data-preserving upgrade remain **NOT TESTED**. The APK candidate is a debug test build, not a production/device-verified release.
