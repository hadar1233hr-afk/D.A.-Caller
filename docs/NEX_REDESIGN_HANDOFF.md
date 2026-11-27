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

## Actual second native CI: foreground-proof format correction — 2026-11-26 UTC

CI45728361423 at c7b4e62e5aac324a7ea46e84ca14d4170a6241fb completed:
103 tooling contracts PASS,467 unique host methods/45classes PASS, lint0errors/
16warnings; native40 completed with38PASS/2FAIL/0errors/0skips. Both failures
occurred in the newly added foreground proof before the three new captures.
The API33 ActivityRecord format places a closing brace immediately after the
component; the new helper incorrectly required a space. The original native
focused-window checks had passed. Correct exact-component parsing and focused
Activity/native-window identity checks are being prepared without dropping gates.

The artifact has267actual PNGs:245original functional,19modern functional and
3diagnostic. Cleanup precedes the failed-test diagnostic captures, so these are
not the exact failing NEX/widget surface. Eight verification reports were not
produced after the JUnit failure. No272-frame visual GO or tested-debug APK exists;
the retained failure candidate remains explicitly unapproved. Authentic evidence
is retained under second-native-ci. Fresh all40native/272captures/full actual
visual review are required after compilation/lint of the correction. No production
UI/data changes, no signing/publication exception, no old-link resend or AI.

## Foreground correction locally verified; fresh native proof next — 2026-11-26 UTC

The exact-component ActivityRecord parser now accepts actualAPI33 closing-brace
and historical task formats, compact/full class spellings, while rejecting stale/
prefix/paused/wrong-component rows. The real Activity Decor AXwindowID must match
the focused active visible native application window with valid onscreen bounds.
Bounded diagnostic-only pre-cleanup observations retain future rejection reasons.
Actual full106Python contracts PASS, Androidtest APK compilation PASS, lint0errors/
16warnings (48seconds), all226tracked inputs unchanged. Production/host/debug/
signing/workflows remain unchanged from actual467host45classCI PASS, so no local
host rerun. Independent16accept/40reject/20window-corruption probes CODEGO; all40
nativeIDs/seven original gates/272requiredframes preserved. Evidence is retained
in foreground-correction. Fresh emulator40/272/fullactualvisual still PENDING;
previous38/40failure remains truthful history. No0.14publication approval or
hardware/update/durablekey claim; oldreleases/AI/otherproducts unchanged.

## Third CI: exact foreground succeeded; measured AX bounds offset — 2026-11-26 UTC

CI45766109135 at24d90804b84ff473cbe749eace0f27f2655f2ea3/app68bebeded
passed106tooling contracts,467uniquehost/45classes and lint0errors/16warnings.
Native40completed with38PASS/2FAIL/0errors/0skips,267PNGs and all8downstream
reports absent. No tested-debug artifact or272visualapproval was produced.
The two new pre-cleanup probes now identify exact resumed MainActivity/Widget
records, matching Decor/nativewindow IDs, active/focused visible application
roots and correct package. Both reject only native-window containment: Android
reports [0,0][479,853] while actual root and Decor are [0,0][480,854]. The same
one-pixel representation difference appears in independent SystemUI windows.

A narrow coordinate correction is being prepared: actual native root must equal
Decor exactly; all three bounds stay valid/nonempty/on-screen; native window
edges may differ by at most one measured pixel, while two pixels must reject.
Passing proof will record all raw bounds and computed maximum delta, independently
recomputed by Python. Original target AXgeometry/focus/windowID/40nativeIDs/old7
gates remain mandatory. Authentic logs/JUnit/pre-cleanup probes retained under
third-native-ci. Compile/lint and a fresh40/272/fullvisualrun still required.
Production/UI/host/debug/data/signing/workflows remain unchanged; no publication
approval, oldAPKlink, AI or otherproduct work.

## Measured window-edge correction passes local checks — 2026-11-26 UTC

The two authentic pre-cleanup probes now have a narrowly bounded correction.
Native accessible root must equal Activity Decor exactly. All three rectangles
must be nonempty and onscreen; each native-window edge may differ by at most one
measured pixel. Passing proof retains all raw bounds and the computed maximum,
which Python independently recomputes. Two pixels, wrong window identity/focus,
false summaries and content outside Decor still fail. Original exact target AX
geometry, all 40 native IDs, seven prior gates and 272 required images remain.

Actual local results: 109 tooling tests PASS; 48 focused modern contracts PASS;
Androidtest APK build PASS; lint zero errors/16 warnings in 56 seconds. All 226
tracked inputs remained unchanged. Independent review passed 16 valid cases and
114 rejection probes. Production, host/debug sources, data, signing policy and
workflows are unchanged from the authentic 467-test/45-class CI pass; that host
suite was not rerun locally. Evidence: foreground-bounds-correction and authentic
third-native-ci failure bundle. Fresh full native/272-image visual proof remains
pending. No 0.14 publication exception or durable signing setup is approved;
immutable older releases, deferred AI and separate products remain unchanged.

## Completed272-image review and required polish — 2026-11-27

The authenticated1a492678/CI45773402159 source genuinely passed40native methods,
467host methods and all272automatic image checks. All272 images have now also
been actually reviewed:266PASS/6FAIL/0unreviewed in69lossless sheets. Two visual
causes affect six frames: native Hebrew speaker activation label truncation and
large-font English Contacts hint clipping. Evidence and all six raw before
images are retained in visual-review-1a49267. No full visual GO is claimed.

The current isolated polish changes only visible copy/accessibility and two
corresponding native label expectations: short Hebrew קול preserves full TTS
הפעל רמקול and unchanged SPEAKER_ON/OFF intents; short English Name or number
keeps15sp and the full search accessible description. Widget hints, actual
search, dialing, data, all40native method IDs and release/signing guards stay
unchanged. Two new host regressions check fit and full spoken/action semantics.
Build/unit/lint are running; fresh40native/272screens and actual review remain
required. Do not send the already delivered0.14test APK again. PR8AI work stays
in feature/da-intelligence-0.15; never mix it into this0.14branch.

## Visual polish locally verified; fresh emulator review next — 2026-11-27

The isolated two-cause polish passes469 unique host methods in45 classes with
zero failures/errors/skips, debug and Androidtest APK builds, and lint with zero
errors. All tracked app/script source hashes remained unchanged during checks.
All40native method IDs, signing/workflows and frozen business assets are preserved.
Authentic JUnit/log/lint proof is retained in visual-polish-preflight.

The historical1a492678 full actual review remains266PASS/6FAIL; it is not relabeled
as passing. This new source needs fresh40native/272screens and actual visual
review before approval. No official publication, matching-key update or hardware
verification is claimed. Previously delivered APKs are not resent; AI stays inPR8.
