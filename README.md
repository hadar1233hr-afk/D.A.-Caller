# DA CALLER v0.7.0 · Contacts & Performance
All call history entries and device contacts are shown by virtualized Android ListView adapters, without "show more" buttons. Favorites use a distinct GridView. Swipe direction is configurable. The dialpad floats on all non-settings tabs, with circular glass keys and a prominent green phone icon. The app has its own contact editor with multiple phone/email fields, address, dates, work and notes. Device contacts are modified through Android ContactsContract with user permissions.
## Permanent signing
To enable upgrades without reinstall, configure two GitHub Actions repository secrets: DA_CALLER_KEYSTORE_BASE64 and DA_CALLER_KEYSTORE_PASSWORD, from the separately supplied private signing kit. The alias is da-caller. Protect and back up the JKS! If these secrets are missing, CI builds a temporary debug-signed APK and updates may require reinstall. APKs from v0.6.0 and before were debug signed using runner-specific keys, so at least one migration reinstall may be necessary. Never commit a keystore/password.
## Safety and scope
An APK compiling and signing does not prove calls work correctly across OEMs. Keep your stock phone app available. The native editor implements many common fields but does not yet duplicate *every* Google Contacts feature, like custom groups and contact photos. Full-duplex recording remains unsupported without privileged permissions.
## Download
[DA CALLER Releases](../../releases) contains DA-CALLER-v0.7.0.apk.
