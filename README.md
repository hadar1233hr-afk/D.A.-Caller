# DA CALLER 0.2.0 (Android)
Modern compact native Android dialer with graphite / champagne-gold UI, AMOLED mode, one-tap phone keypad, recents with today/week/month/year minutes and outgoing/incoming/missed counts, favorite contacts, and Android default-dialer role.

## Important
The in-call experience uses InCallService and may be selected as the Android default Phone app from inside Settings. Incoming/ongoing UI is an initial test implementation; test carefully and keep a backup dialer installed. Emergency calls always use the OEM dialer per Android rules.

Call recording both sides: not supported in this APK. Android protects telephony capture via privileged CAPTURE_AUDIO_OUTPUT permission (not granted to normal apps). No feature is advertised as recording when it is not possible.

Data remains on device, app does not request Internet permission. Statistics use system CallLog durations; unreadable until permissions granted.

## Build
GitHub Actions builds debug APK on pushes to main, publishes version 0.2.0 release.
