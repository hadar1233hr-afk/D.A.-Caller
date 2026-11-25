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
