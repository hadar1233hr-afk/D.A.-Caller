# DA CALLER version numbering

Starting with the next published version, use MAJOR.MINOR.PATCH without a BETA suffix. The pending release is0.12.0 (Android versionCode21).

- MINOR increases for a meaningful set of new features:0.12.0 →0.13.0.
- PATCH increases for fixes and small visual refinements:0.12.0 →0.12.1.
- MAJOR increases for a major product generation or incompatible behavior; it does not indicate a change of Android package or permission to delete user data.
- Android versionCode increases for every published APK; it never decreases or repeats. Semantic versions are three integer components, not decimal fractions.
- Never rename, replace or overwrite a released APK/tag to change its version. Include truthful per-version notes in Settings.

0.12.0 is not yet published. Version numbering does not establish signature compatibility: updating an installed application still requires a compatible signing identity. Physical checks remain NOT TESTED where no evidence exists; the owner waived Jelly Star hardware checks as a publication prerequisite.
