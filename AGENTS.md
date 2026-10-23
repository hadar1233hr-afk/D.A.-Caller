# DA CALLER repository instructions for coding agents

- **Current owner-authorized priority:** Read `docs/CODEX_DA_CALLER_PRODUCTION_AUDIT_2026-10-23.md` in full and follow its requirements REQ-01..REQ-17 and traceability/reporting rules. Related issue: #1.
- **Blocker:** Clicking the dial/call icon in both `Contacts` and `Recent Calls` jumps list rows rather than placing a call. Reproduce, find root cause, fix click/touch/scroll interaction, and add regression coverage. Never claim verified without test evidence.
- **Quality gate:** Test against Android 13 (target Unihertz Jelly Star/Helio G99, 8GB RAM, small screen) as far as possible. Test every requirement and report `PASS / FAIL / BLOCKED / NOT TESTED`. Device-only checks must remain `NOT TESTED` without device access.
- **Module isolation:** `:app` is DA CALLER. `:businessbook` and `:offline` are separate products: no accidental code/data/release mixing.
- **Release discipline:** Do not republish, overwrite, or rename the v0.9.0 APK for untested changes. Produce changes on a reviewable branch/PR, maintain stable `applicationId`, protect user data/archive, do not commit signing keys. Fix current workflow's auto-overwrite behavior before merging.
- **Maintain real behavior:** Dialing via TelecomManager and relevant Android permissions must work; avoid touching UI thread for I/O and decoding; preserve swipe configuration, current recents, optional archive, contact photos, Hebrew RTL / English, light/dark/AMOLED and accessibility.
- Report results to owner in Hebrew with code links, bugs fixed, test evidence, honestly stated limitations and a **new** version only when genuinely ready. Never guarantee zero bugs.
