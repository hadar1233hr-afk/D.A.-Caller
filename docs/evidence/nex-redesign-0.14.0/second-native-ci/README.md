# Second native CI: two new foreground-proof failures

CI [45728361423](https://github.com/hadar1233hr-afk/D.A.-Caller/actions/runs/45728361423), source `c7b4e62e5aac324a7ea46e84ca14d4170a6241fb`, app tree `7aa469df7e34eda9568b5adc92a56795f3fdf2fd`, failed with **38 of 40 native methods passing** (two failures, no errors/skips). All 467 unique host tests in 45 classes and 103 Python tooling contracts passed; lint reported zero errors and 16 warnings.

Both failures enter the new `activityForeground` helper after the existing native-focus check passes. Its exact-component lookup assumes a trailing space. The actual API33 activity dump serializes the component with a trailing `}`:

```text
topResumedActivity=ActivityRecord{901df3e u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity} t8}
```

This identifies a real test-harness format defect. The retained dump is after cleanup and does not establish the contemporaneous failing Activities. Correct exact-token parsing and fresh full native execution are still required. No functional foreground requirement is waived.

The archive retains verbatim job logs, native JUnit, final activity/window/logcat diagnostics, three actual diagnostic images, source-at-run excerpts/full test source, original checks ZIP, API metadata and verified artifact hashes. Its internal manifest gives every retained file's bytes and SHA256. The original full UI ZIP (17,611,292 bytes) is represented by its verified artifact hash and workspace copy, not duplicated in this repository bundle.

There are 267 actual PNGs: 245 original functional, 19 modern functional and three diagnostic captures. Eight required modern images are missing, and all eight downstream functional reports are absent because the native failure stopped that step. No 272-frame or full visual PASS is claimed. The widget diagnostic shows launcher after the helper's finally cleanup; the island diagnostic follows restoration of the previous style. Neither is the missing new presentation capture.

Only an explicitly unapproved **native-failure candidate** APK exists; it is not a tested-debug artifact or publication candidate. No new release, hardware, matching-key update or durable signing success is claimed. Existing publication/signing protections remain in place.
