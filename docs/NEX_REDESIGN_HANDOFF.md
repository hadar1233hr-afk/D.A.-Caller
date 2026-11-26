# Owner-authorized NEX-inspired redesign — 2026-11-25
The owner requested a completely new dialer design inspired by their NEX app before any AI development, and an APK when the design is ready. This supersedes prior no-new-UI-work instructions for this new scope only. The separate durable signing setup remains unresolved and all publication safeguards remain intact.

Branch design/nex-caller-0.14 starts at PR #5 head 413cab78e8a1d85652e82b26705e416d350a778a, preserving the delivered 0.13.0 release. Reference only: DA-SECRETARY main app/src/main/java/com/dasecretary/app/ui/Theme.kt and MainActivity.kt. No edits to NEX or other apps.

Initial source changes already committed: shared NEX warm parchment/terracotta/espresso/mint palette; actual MainActivity navigation/keypad styling, FastPages recent/contact cards and settings palette, stronger native text weights. Existing gold/silver personalization and call action colors retained where explicitly user-controlled. No builds or native tests have been run for these changes. The first pass is NOT a complete redesign.
Next:
1. Inspect all DA CALLER screens and extend the visual system to contact details/editor, archive, business directory, dialogs and in-call screens (preserve five distinct selectable call styles, island functionality, user customization and real AMOLED black).
2. Refine hierarchy, rounded surfaces, bottom navigation, readable heavier Hebrew/English typography with small-screen and large-font checks. Validate contrast and touch targets; retain all features, data, offline behavior, settings and module isolation.
3. Target next version 0.14.0/code23 only after coordinating changelog and existing version-specific checks. Preserve old immutable public APKs, all prior CI/signing safeguards; adapt visual assertions honestly to approved new visual expectations, not weaken functional gates.
4. Run actual unit/lint/build, native Telecom and screenshot validation on new source; record authentic evidence. Do not claim physical hardware or update compatibility. No publication unless applicable signing requirements/owner approval are satisfied; report concrete blocker rather than bypass. Deliver verified new APK link once available; never resend 0.13/0.12 as new work.
5. AI roadmap is saved in docs/DA_INTELLIGENCE_ROADMAP.md; do not implement it now.

## Continued implementation — 2026-11-25
Additional actual commits extended NexPalette to ContactDetailActivity, ContactEditorActivity, ArchiveActivity, BusinessDirectoryView, ContactChooseActivity, ChangelogActivity, CallbackReminderActivity, CallIslandSettingsActivity and SettingsBackupActivity, preserving data and actions. The Google/Pixel call style now uses NEX day/night colors while keeping the other four visual kits and all IDs. Added a darker light-mode primaryText token for active nav contrast (original NEX rust on soft parchment failed 4.5:1). No changes to business database, other repos, signing gates, or released APKs. Native call island's own visual options still need refinement/validation. Need actual build, tests, and screenshot review; this chat runtime has no Android SDK, Gradle or adb. Do not report an APK or passing tests yet. Next: review remaining hardcoded UI colors, contrast, heavier-font fit, preserve customization, finalize version/changelog and run native CI from the managed environment.

## Island, typography and token verification — continuation
Added optional fifth island appearance `nex` (espresso/copper) to view palette, bilingual choice UI and backup allowlist. The original four styles and classic default remain unchanged; transparent opacity and gesture/telephony logic are unchanged. Added new style to existing compact-size regression, settings selection/opacity test and a new backup round-trip test. Heavier Rubik font family XML now matches AppAppearance regular 600/bold 750; adjusted explicit old visual-weight assertions only, preserving glyph/source/fit/touch validations and separate Google Nunito 650 keypad contract. Dialog bold detection uses >=650 to distinguish regular 600. Added NexPaletteTest for contrast and AMOLED. Actual standalone JDK test compiled byte-identical NexPalette.java (Git blob 427c99258d0287f8761c0a9df51fd1213f27aece) and ran 34 assertions: 33 palette contrast checks (minimum 4.803:1) and black AMOLED background, all PASS. This does not establish Android compilation, actual text fit, native gesture functionality or APK readiness. JUnit/Robolectric and native tests are NOT EXECUTED for this revision. Need full Android CI on new source; review remaining hardcoded colors, version/changelog, and layout at 320dp/large fonts. Existing permanent signing blocker and publication safeguards remain. No AI or other product changes.

## First Android CI regression fixes
Authentic CI run 45712687526 on 3c130f4: 463 host tests, 455 passed and 8 failed; native job skipped. Log identifies two dialog font-weight failures, two clipped Businesses chip failures, one business attribution font-weight assertion, one old dark-root palette assertion and two backup checkbox font-weight assertions. Production fixes: regular 600 for native dialog buttons/checked choice rows; measure full Businesses label using real current text paint and padding instead of fixed 96dp width, preserving 48dp refresh target and weighted count. Updated only intentionally changed visual expected values for source attribution (600), dark-root background (NexPalette) and backup checkbox (600); original functional assertions remain. All edits committed through 380c689. Fresh CI required; no claim of passing these fixes, native validation or new APK. Previous standalone palette claim is not independently evidenced in this chat; rely on authentic CI evidence instead. Keep AI deferred and permanent signing safeguards intact.

## Managed continuation — current work
The managed continuation uses isolated worktree `/workspace/D.A.-Caller-nex-014`
on `design/nex-caller-0.14`, PR7 only, starting from latest handoffdf7ccf4.
No competing local worker was active; later source work was scoped among root
and bounded workers, now frozen for one serial local build/test/lint command.
Actual NEX Theme/MainActivity source was read only through the connected provider;
reference findings are retained privately in work/nex-014/reference-audit.md.
Warm cards/selected-icon pills, stronger600/750 typography and secondary/call/
island coherence are implemented. Five call styles and four island style IDs,
user accents, functional handlers and opacity/token mechanisms are preserved.
Day gold was minimally darkened to pass4.5:1 on the new soft surface; dialog
font classification distinguishes600body from750headings. AMOLED stays black.
Version0.14.0/code23 and bilingual changelog are coordinated; old21entries remain.
Native40cases and245+24required frames remain; version/catalog checks are strict.
Only focused33Pythonversion contracts and static checks have passed so far.
Full current-source unit/build/lint is IN_PROGRESS; no native/visual PASS yet.
Fresh emulator Telecom269-image proof and full actual visual review are required.
Signing/publication safeguards, immutable0.12/0.13 and frozen business data remain.
There is NO0.14publication exception approval; durable owner key/backup/secrets
setup still awaits the already requested owner information. Complete design and
proof before asking about a concrete publication candidate. AI remains deferred.

## Integrated upstream continuation6191392 — managed checkpoint
RemotePR7 advanced during the environment pause. Root preserved all36localWIP
paths and patch privately, stashed the WIP, fast-forwarded to6191392, then merged
without force or dropping owner work. Five conflicts were resolved preserving
the upstream optional fifth `nex` island style/backup allowlist, matching600/750
font XML, full-label measured Businesses chip width and updated visual checks,
plus local NEX surfaces, gestures/opacity invariants and AMOLED/cache fixes.
The original four island IDs/order/default remain supported; NEX adds a fifth.
Local first runnable host suite before this merge genuinely ran467cases:
465PASS/2FAIL (full Businesses label clipped in both permission paths), no errors
or skips and no source drift. Upstream measured-width fix directly addresses
those two failures without relaxing the label/48dp assertions. Full merged host
and debug build is IN_PROGRESS with writable JVM test cache; no PASS yet.
Earlier sandbox socket/home-lock failures are retained as infrastructure failures.
Two actual WidgetConfigure previews plus one actual owned NEX-island call frame
are being added to the existing native methods: preserve all40case IDs and old
245+24images, require27additive/272total, no synthetic launcher-host binding claim.
No fresh native execution or visual approval exists yet. Existing signing,
publication and fixed business-data guards remain; no0.14exception approval.

## Current local preflight PASS; fresh native and visual proof next — 2026-11-26 UTC

Merged source now passes467uniquehost methods in45classes,0failures/errors/skips,
local debug and Androidtest APK builds, lint0errors/16warnings and42focused
Python modern-profile contracts. All frozen source hashes remained unchanged.
Independent merged-source and additive-native reviews are CODEGO; authentic
JUnit/logs/metadata/source manifests retained in
`docs/evidence/nex-redesign-0.14.0/local-preflight`.

The original40native IDs and all245+24original images remain. The strict profile
adds2actual WidgetConfigure previews with synthetic IDs/no launcherhostbinding
claim and1actualowned NEX-island call image:27modern/272total. These native
captures are NOT YET EXECUTED; fresh CI Telecom and actual full272visualreview
are required on the newly committed app tree. No publication, hardware, upgrade
or durable-signing success is claimed. No0.14signing/publication exception was
approved; finish concrete software proof before requesting that boundary.

## First CI historical-contract fixture corrected — 2026-11-26 UTC

FreshCI45727531391 at962fab4 stopped before Java/native:101of102Python tooling
contracts passed, one historical0.12scope test read the current stronger0.14
followup verifier and the immutable0.12hash guard correctly rejected it.
The test now uses six exact historical0d266d63source fixtures verified against
unchanged legacy pins; workspace-unchanged checks remain, and an addednegative
rejects all six corrupted sources plus changed current0.14bytes. ActualfullCI
Python command now103PASS/0fail/error. No helper/hash pin/signing/defaultCI or
publication guard changed. Authentic failed artifact and fixed-test logs are
retained in`docs/evidence/nex-redesign-0.14.0/first-ci-failure`.

This is test-fixture-only; appTree7aa469df7e34eda9568b5adc92a56795f3fdf2fd and
local467host/build/lint results remain unchanged. Fresh emulator40cases/272real
captures and all-image visual review remain pending; no native PASS is claimed.
