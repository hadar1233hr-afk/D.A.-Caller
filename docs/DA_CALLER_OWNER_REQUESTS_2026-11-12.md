# New direct owner requirements — 2026-11-12

These eleven requirements were added directly by the owner after 0.12.0 had
already been published, verified and delivered. Later scheduled repeats of the
older 0.12.0 prompt do **not** cancel them. They extend the unfinished business
and durable-signing work. None of these eleven new changes has been implemented
or tested at this checkpoint; do not present the existing 0.13.0 business test
run as proof of them. Work only in DA CALLER `:app`.

1. **Recent calls expand inline:** tapping a recent-call row expands that row to
   reveal attractive modern actions inspired by Google Phone, replacing the
   current old-style primary action presentation. Preserve every existing call,
   contact/create-contact, data and archive action, list/query/Back state and
   explicit-only dialing; expansion itself must never place a call.
2. **Drag and temporarily hide the dynamic island:** allow finger dragging to a
   user-chosen screen position, and let the user hide it completely when it is
   inconvenient. Provide a clear way to show it again. Preserve the real call,
   native notification, controls and data; distinguish dragging from tapping.
3. **Optional status-bar-area position:** retain the current below-status-bar
   placement by default, and add a user-selectable position in the status-bar
   area, where compatible and comfortable on that device. Android protects its
   SystemUI layers: investigate supported positioning, respect icons/cutouts and
   provide truthful limitations/fallback. Do not promise an exact Apple-style
   system-status-bar replacement or use privileged/permission workarounds.
4. **Optional archive in the same backup file:** the user chooses whether the
   settings backup also includes call-archive records. Preserve settings-only
   compatibility and existing archive/settings; validate restore before changes
   and never implicitly delete/replace data. This does not authorize copying
   private archives into Git, public Releases or the public business dataset.
5. **Modern wine-colored controls:** replace the dated green controls with
   modern capsule/oval switches whose thumb moves inside the track. Use wine
   colors, retain real accessible checked state, RTL, disabled state and touch
   targets, and preserve preference identities/backup behavior.
6. **Modern summary/favorites/arrows:** improve the recent-call summary and
   favorites strip. Replace the current plain arrows/chevrons throughout the
   app, including Settings, with polished consistent icons. Preserve expand/
   collapse, saved state, scrolling and call behavior.
7. **Navigation:** move the dialpad entry to the bottom navigation bar. Move
   Settings to a quiet, unobtrusive top-left control with a proper accessible
   touch target and no overlap with content. Preserve dialing and Back/return
   state and remove the old obstructing floating dialpad presentation.
8. **Rounded global typeface:** use a more modern, rounded, slightly heavier
   typeface for all application text. Include Hebrew/English glyph support and
   font license/provenance; retain legibility on Android13/JellyStar-sized
   screens and larger text. Keep the already requested rounded Google digits.
9. **Call-screen branding/Nothing:** remove the redundant DA CALLER branding
   from all five call-screen designs. Center and organize the Nothing layout
   like the Google layout, retaining caller identity, phase/timer and controls.
10. **Transparent island style:** add a see-through island style and a
    user-adjustable transparency amount. Preserve usable controls, legibility,
    real underlying content, existing style preferences and offline operation.
11. **Additional launcher icons/brighter gold:** add more app icon choices;
    make icon gold brighter/more vivid and improve gold accents throughout the
    app while preserving contrast, existing selected aliases and custom settings.

## Verification and release boundaries

- The published/delivered 0.12.0 release stays immutable; do not repeat its APK
  link, rebuild/re-sign it or invent another release of the same version.
- Current **unreleased development version is 0.13.0/code22**. Fold the new
  work into a coherent next release; do not manufacture version increments for
  intermediate attempts. Continue semantic MAJOR.MINOR.PATCH without BETA.
- Existing real baseline is source3f6702b / CI43202338930:361 main units and36
  emulator cases passed,245 real PNGs retained. This is prior business work,
  **not** implementation/testing of the eleven requirements above. Its business
  UI has a visual NO-GO: retained empty-header gap, truncated source summary and
  raw taxonomy labels. Fix these during the requested modest interface polish.
- New application changes require new native Telecom/interaction/gesture/backup
  checks and fresh real screenshots/full visual review. Preserve every earlier
  required baseline frame and meaningful regression, and explicitly account for
  any new captures; never weaken gates or label unexecuted checks PASS.
- Preserve applicationId, offline dialing, personal contacts, settings, call log
  and archive. Never recommend uninstalling. Hardware/matching-key upgrade
  remain NOT TESTED; physical JellyStar testing is waived as a prerequisite.
- The owner's explicit signing/publication exception applies only to the one
  retained 0.12.0 binary. Future permanent signing/private backup/authorized
  CI-secret provisioning and applicable publication approval remain unresolved.
  Do all independent implementation/verification work before asking for any
  necessary final approval. Do not reuse the 0.12.0 exception for 0.13.0.
- Existing two-hour automation remains active while these original/new owner
  requests are unfinished. Do not create a duplicate timer or disable it merely
  because 0.12.0 was delivered. Stop on an actual usage-limit error, save state
  and wait for the next run; no account/model/route bypass or quota API assumption.
