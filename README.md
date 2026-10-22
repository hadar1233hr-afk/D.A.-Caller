# DA CALLER 0.4.0 · Premium Glass

Modern offline Android dialer for Android 8.0+, optimized for compact 4-inch phones.

## Changes
- Gold/graphite native Android adaptive launcher icon and small monochrome notification icon.
- Larger rounded-square call controls, two large columns in the center. Incoming accept and decline buttons are large.
- Conditional VoLTE badge on an active call when Android voice network reports LTE and READ_PHONE_STATE permission is granted. This is a best-effort voice radio indicator, not a privileged IMS-registration proof. Hidden otherwise.
- Contacts are loaded from the full Android Contacts provider, with phone number mapping, OEM fallback, manual refresh, search, and contacts without numbers.
- Independent background executor for contact loading, separate from call-log statistics.
- Compact single-line call statistics. Tap period name to cycle day/week/month/year and horizontally scroll for counts.
- Minimal header, more modern font, glass-inspired subtle gradients, and configurable haptics.
- No Internet permission.

## Testing
APK builds and signature are validated in GitHub Actions. Real phone calls and OEM contact providers must be tested on an actual device. Keep the stock dialer installed.
The launcher may tint the app icon when Android themed icons are enabled.
Two-way call audio recording is not implemented because it requires restricted capabilities.

## APK
Download DA-CALLER-v0.4.0.apk from GitHub Releases. Debug certificates may vary between test builds; uninstalling the previous APK may be required, which can delete local favorites and app settings.
