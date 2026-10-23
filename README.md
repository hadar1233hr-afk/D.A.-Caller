# DA CALLER v0.8.0 · Privacy archive & call screen themes

Android offline dialer beta with a responsive Hebrew/English interface.

## New features
- Private SQLite archive for call metadata (not recordings), configurable as Off, extra 1/3/6/12 months after first observed disappearance from Android's log, or Keep Forever. Local offline periodic JobScheduler reconciliation (12 hours), and reconciliation whenever the app is opened or a call ends. Archive records missing from the system are viewed separately via a footer at the END of Recents, not inside the main scroll list.
- **Important:** Since Android does not expose the actual deletion timestamp, the extra retention period starts when the app FIRST NOTICES a record missing. There is no retroactive recovery of already deleted history. OEM power saving/permissions can delay sync.
- Five original in-call design palettes: Graphite Luxe, Pixel Clean, Emerald AMOLED, Frosted Slate, Ivory Studio. Every palette follows the global Light/Dark/AMOLED choice.
- Four incoming answer gestures: right answer/left decline, left answer/right decline, up answer/down decline, and iOS-style right answer/red-button decline.
- Separate, low-importance silent ongoing-call notification channel; high-priority call presentation only for incoming calls. No red phone image.
- Corrected recents swipe-to-call with ListView gesture interception, three distinct touch zones in each recent call entry, larger left-side call icon, optional green call button, and avatar/contact details.
- Google-inspired contact card with multiple fields and stored contact photos, plus gallery-based photo editing.
- Organized categories for new and existing settings, bilingual Hebrew/English core UI and contextual labels, improved nav card spacing.

## Build and installation
CI builds debug APK if permanent-signing secrets are absent. For stable future in-place updates, configure `DA_CALLER_KEYSTORE_BASE64` and `DA_CALLER_KEYSTORE_PASSWORD` securely with a private JKS alias `da-caller`; never commit the key to the repository. Previous debug-signed versions might require a one-time reinstall.
The APK is compiled and verified by GitHub Actions, but **real call handling cannot be guaranteed until tested on the physical Android 13 phone**. Keep another functioning phone app installed.
