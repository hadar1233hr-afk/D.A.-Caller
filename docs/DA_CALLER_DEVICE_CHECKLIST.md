# DA CALLER — Android 13 / Unihertz Jelly Star field checklist

Status: **NOT PHYSICALLY DEVICE-TESTED**. No physical phone was attached; the initial environment also lacked an emulator/system image and `/dev/kvm`. The implementation has 79 passing JVM tests and 5 passing native UI tests on an accelerated Android 13 CI emulator at 480×854px / 240dpi for the final candidate source `35b4c21` (details in `DA_CALLER_VERIFICATION_MATRIX.md`); emulator results cannot substitute for this hardware checklist. The local nonaccelerated emulator failed before app installation, with no app tests executed. Automated Robolectric tests simulate Android API 33 on the JVM and capture outgoing requests; they never place real calls. Complete this checklist against the exact candidate APK/commit before calling it ready for public distribution.

## Record the candidate

Record device model, Android build/security patch, display density/font scale, SIM/carrier, app versionCode/versionName, commit SHA, APK SHA-256, certificate SHA-256 and default-dialer role. Keep a functioning alternative phone app installed. Compare the candidate signer with the currently installed DA CALLER signer before installing an update; do not assume debug and permanent certificates match. Archive upgrade/preservation must be tested before uninstalling an older build.

Suggested read-only identification commands:

```sh
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.fingerprint
adb shell wm size
adb shell wm density
adb shell settings get system font_scale
adb shell dumpsys package com.dacaller.app
```

Store logs/screenshots only after removing personal phone numbers/contact details. For actual call checks use a reachable, non-emergency test destination you control and verify the displayed and network destination agree.

## Priority P0: exact call, no list jump

For every case record initial first visible row and offset, tapped displayed person/number, outgoing request count, actual destination and final list position after returning. Expected: exactly one correct call request, no unexpected list jump; no call for scrolling.

| Check | Steps and success criterion | Result |
|---|---|---|
| P0-01 | With 1000+ test contacts, tap the explicit phone control at top/middle/end; test both LTR/RTL and native fast-scroll thumb visible. Exact shown number calls once, same scroll anchor on return. | NOT TESTED |
| P0-02 | Filter contacts by name and formatted/local/international number; tap first/middle/end match after rapid typing and scrolling. No stale/recycled destination. | NOT TESTED |
| P0-03 | Tap Recents phone controls in short and long histories, including identical dates and repeat callers. Exact entry number once, no reorder/jump. | NOT TESTED |
| P0-04 | Tap Recents avatar and center separately. Avatar opens corresponding contact; center shows that call's type/date/duration; neither places a call. | NOT TESTED |
| P0-05 | Tap Contacts row/avatar/star separately. Details and favorite changes affect the displayed contact only; phone-less/private entries cannot place calls. | NOT TESTED |
| P0-06 | Start vertical flicks on center, phone, star, avatar and near fast-scroller edge; do short drag, long fling, diagonal drag, cancel and two-finger gesture. No accidental call. | NOT TESTED |
| P0-07 | Test swipe mode Right, Left, Off. Only sufficiently long/dominantly horizontal configured swipe on row center calls; wrong direction/short drag/control region does not. | NOT TESTED |
| P0-08 | Begin touch while async refresh/filter runs; repeat after tab return, contact edit, foreground return and screen rotation. No wrong number or queued restore jump. | NOT TESTED |
| P0-09 | Deny CALL_PHONE; refresh/navigate/rotate while prompt is open; grant or deny. Grant uses the original requested number once; denial opens an alternative dialer or usable local keypad, without a self-launch loop. | NOT TESTED |
| P0-10 | Rapidly double tap the same/different phone buttons; cancel a gesture midway. At most one intended outgoing request, no lingering pressed state. | NOT TESTED |

## Telephony and notifications

| Check | Steps and success criterion | Result |
|---|---|---|
| CALL-01 | Fresh install: accept/refuse default dialer role and each runtime permission separately. Clear feedback and recoverable navigation; existing alternate dialer remains usable. | NOT TESTED |
| CALL-02 | Incoming call with app foreground/background, screen on/locked/off, notification permission granted/denied. Ring/vibration follow device settings; answer/decline reachable; appropriate lock-screen presentation. | NOT TESTED |
| CALL-03 | Test all four incoming gesture modes in Hebrew and English: right/left, left/right, up/down, iOS-style right with tap-decline. Wrong direction/short diagonal/tap does not answer accidentally; explicit accessible buttons work. | NOT TESTED |
| CALL-04 | Place outgoing call while call screen visible; active/held updates do not repeatedly show heads-up/pull-down/full-screen UI. Background ongoing notification is silent with correct actions. | NOT TESTED |
| CALL-05 | Answer second incoming call/hold first; end one and verify remaining active/held call stays available. Tap an old notification action after call replacement; it cannot answer/end the new call. | NOT TESTED |
| CALL-06 | Verify mute, speaker, earpiece, Bluetooth available/unavailable, hold/resume supported/unsupported, DTMF digits and add-call. No tone remains playing when keypad closes or screen pauses. | NOT TESTED |
| CALL-07 | End/decline while unlocked/locked and via notification; final notification disappears only after final call; return to list preserves position. | NOT TESTED |
| CALL-08 | Test SIM unavailable, no service, airplane mode and optional dual-SIM chooser. Clear fallback/error, no crash; contacts/archive/settings remain usable offline. | NOT TESTED |
| CALL-09 | Verify normal/silent/vibrate/DND ringtone behavior and proximity/earpiece use on Jelly Star. API simulations cannot establish these behaviors. | NOT TESTED |

## Archive and privacy

Use synthetic or disposable call history; never delete needed real history to test archiving. Compare current Android log before/after all archive operations.

| Check | Steps and success criterion | Result |
|---|---|---|
| ARC-01 | Off: no periodic collection/new archive writes; retained private snapshots are hidden. Re-enable policy deliberately; explanation must not imply recovery of history never observed by the app. | NOT TESTED |
| ARC-02 | For each 1/3/6/12-month/forever policy observe test calls, remove only controlled provider records, reconcile. Only formerly observed missing records appear; current records remain in Recents. | NOT TESTED |
| ARC-03 | Reconcile repeatedly, including identical timestamps/duplicates. No duplicate archive row, no lost first-disappearance timestamp, stable count. | NOT TESTED |
| ARC-04 | Reach end of long Recents list. Archive appears only via footer and opens separately; archive search/clear search/type/date/duration/scroll work. | NOT TESTED |
| ARC-05 | Kill/relaunch process, reboot, then inspect archive. Test upgrade from v1 database candidate with real prior archive. All unexpired metadata survives; app never records audio. | NOT TESTED |
| ARC-06 | Revoke READ_CALL_LOG during refresh; simulate provider failure. Sensitive current cache disappears on revoke; failed/null/partial read cannot mark every call missing. Restore permission and refresh successfully. | NOT TESTED |
| ARC-07 | Delete archive: cancel leaves data; confirm deletes app snapshots only, without Android call log/contact loss. Simultaneous refresh cannot resurrect previously archived missing calls. | NOT TESTED |
| ARC-08 | Verify JobScheduler after reboot/Doze/OEM battery restrictions and permission change. Record actual delays; do not promise exact 12-hour execution. | NOT TESTED |
| ARC-09 | Verify application has no INTERNET/audio-recording permission and excluded data is not cloud/device transferred. Distinguish private archive from Android's own call-log backup behavior. | NOT TESTED |

## Contacts, themes, widgets and accessibility

| Check | Steps and success criterion | Result |
|---|---|---|
| UX-01 | Create/edit a contact with multiple phones/emails, organization/title/department, full postal fields, dates, website and note; reopen in DA CALLER and native Contacts. Values/types/account aggregation preserved. | NOT TESTED |
| UX-02 | Clear selected fields and save. Only intended data rows are deleted; no blank phone/email/event rows or other-contact changes. Reject save during loading/read-only account with clear feedback. | NOT TESTED |
| UX-03 | Add large gallery image, rotate, save and revisit after scrolling. Correct sampled image/initial/name on details, lists, favorites and widgets; no stale image on recycled row. | NOT TESTED |
| UX-04 | Change name/photo in native Contacts while app open/background. Debounced update arrives and cached old photo is invalidated; revoked contacts access clears private cached presentation. | NOT TESTED |
| UX-05 | Test five named call designs in Light/Dark/AMOLED. Check contacts/detail/editor/archive/settings/widget configuration/dialogs, contrast and status/navigation bars. | NOT TESTED |
| UX-06 | Change Hebrew/English offline. Correct RTL/LTR, translated primary and secondary labels, long text, phone digit direction, date/duration formatting and persistent startup tab. | NOT TESTED |
| UX-07 | At Jelly Star actual screen size, portrait/landscape and largest supported font/display scale, all call/end/answer controls/keypad actions remain reachable by scroll; touch targets do not overlap fast-scroller. | NOT TESTED |
| UX-08 | TalkBack/switch access: meaningful action labels, phone/contact/star regions distinct, answer/decline available without gesture, natural traversal and focus after navigation. | NOT TESTED |
| UX-09 | Empty contacts/log/archive, filtered zero results, loading/provider error, rapid tabs/search, refresh while typing, background/foreground and process recreation. Feedback is accurate and state preserved. | NOT TESTED |
| UX-10 | Configure all widget types at small/large launcher sizes and both themes/languages; add/remove/change favorite, tap/contact/call actions and repeated async refresh. Correct current person/photo and no stale actions. | NOT TESTED |
| UX-11 | Confirm installed package/app branding is DA CALLER only; DA Business Book and DA ID Offline remain separately installed and unchanged. | NOT TESTED |

## Repeatable performance protocol

Measure baseline v0.9.0 and candidate on the **same phone**, Android build, density/font settings, battery/power mode and synthetic dataset. Record dataset count, warm-up protocol, number of repetitions and raw results. Do not compare JVM timings with device timings. Do not claim a speedup without paired measurements.

1. Prepare 1000+ synthetic contacts with photos and a large controlled current call log; note total counts. Keep the same data between builds.
2. For each build, perform at least 10 cold process launches. Force-stop before each launch; capture `TotalTime`, `WaitTime` and `ThisTime` from `am start -W`. Report median and p95 from raw samples; do not include initial install/permission prompt launches in the steady-state result.
3. Capture at least 10 warm foreground returns without force-stop. Measure and report separately from cold launches.
4. Reset gfxinfo; perform the same 30-second contact/Recents fling, fast-scroll, filter and tab sequence. Capture frame/jank stats. Repeat three times per build; note any incorrect calls/jumps (functional failure supersedes timing).
5. Capture process memory/GC before and after fixed photo-scroll loops. Inspect allocation pressure and bounded image cache, not only one RSS snapshot. Check background CPU/job activity after several idle minutes.
6. Enable developer StrictMode/profiling on a test build or use Android Studio/Perfetto to identify actual main-thread disk/provider I/O and frame stalls. Record traces and exact method fixes; source architecture is not runtime proof.

Example commands (keep raw outputs associated with candidate identity):

```sh
adb shell am force-stop com.dacaller.app
adb shell am start -W -n com.dacaller.app/.MainActivity
adb shell dumpsys gfxinfo com.dacaller.app reset
adb shell dumpsys gfxinfo com.dacaller.app framestats
adb shell dumpsys meminfo com.dacaller.app
adb shell dumpsys jobscheduler
```

## Release decision record

Record PASS/FAIL/NOT TESTED for every table row, the tester/date, exact candidate commit/APK/certificate and linked evidence. Block public distribution for wrong/accidental calls, unavailable incoming ringing/answer/end, data loss, an unknown update certificate or an unreviewed failing check. Do not overwrite v0.8.0/v0.9.0 assets/tags. A signed debug APK or successful CI artifact proves build/installability checks only; it does not establish permanent signing continuity or physical telephony reliability.
