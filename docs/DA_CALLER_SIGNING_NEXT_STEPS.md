# Remaining signing work after 0.12.0 — 2026-11-12

The verified 0.12.0 APK has already been delivered. Do not publish it again or
repeat its download link. The owner's approval covered that one retained binary;
it does not authorize a future incompatible release or a general signing bypass.

## What this read-only audit actually found

| Identity | Public SHA256 | Current evidence |
| --- | --- | --- |
| Installed/published beta.9 | `3d41dabd6e32a28ba6af2c57bb872fb3dfafa1f85a5c8523b72b54e4d8a32f56` | Retained actual APK verification; ephemeral test identity, matching private key unavailable in audited paths |
| Published 0.12.0 | `fbf3b0bc4d5cda06e2bbe0e07e2e2aa1828f30f09e86e82e9afc1f302a38b723` | Published native-tested APK/public-byte proof; durable identity was not provisioned |
| Known local disposable debug key | `fb4a138563caff0c59a35327a8e2865c91fea72c6d28c667c56623b1ba9110fc` | Current public-certificate inspection of `/workspace/toolchains/android-user/debug.keystore`; mode0600, PrivateKeyEntry metadata present; matches neither published identity |

The local certificate was inspected using `keytool -list`; no private-key payload
was exported and no password file was read or printed. The configured
`ANDROID_USER_HOME` identifies this known debug-key path. No permanent
`certificate.json`/`RECOVERY.txt` or signing directory was found in the known
DA CALLER private/work locations. `/root/.android/debug.keystore` was inaccessible,
so this is a bounded audit, not a claim that every possible location was searched.

The current public policy still has `permanent_certificate_sha256:null` and keeps
the original beta.9 required-update pin. Its 0.12.0 exception pins only the exact
published APK. No policy, key, secret, workflow or release was changed here.

## The concrete missing pieces

1. A stable private signing identity and an independent private backup controlled
   by the owner. A new workspace-only key plus a backup on the same temporary
   machine would not establish durability. Prefer recovering an existing matching
   identity if the owner has a private backup; only verified private-key material
   can provide that update path.
2. A supported, authorized way to configure the three repository-level signing
   secrets before both CI build/test jobs: `DA_CALLER_KEYSTORE_BASE64`,
   `DA_CALLER_KEYSTORE_PASSWORD` and `DA_CALLER_CERT_SHA256`. A verified public pin
   must match the same identity. Environment-only secrets in the release job do
   not reach the preceding validation jobs.
3. Real signed-APK verification and a matching-certificate update test for any
   proposed compatible baseline. The existing tool contract tests and the
   successful incompatible 0.12.0 exception do not prove those results.

Current runtime metadata reports no configured cloud-secret bindings. This does
**not** prove that GitHub repository secrets are empty. No supported repository
secret setter or secure-vault tool is exposed in this session, and the local
GitHub API route previously returned Forbidden under the restricted runtime.
No credential/account change or alternate route was used. Network readiness and
repository administrative permission would both need verification through a
supported configuration path; neither may be assumed from public-repository
read/write access.

## What can proceed, and what needs owner-controlled setup

Business-data implementation and ordinary nonpublication tests can continue with
the existing fail-closed signing guards. The stable-key tools are already present
in [da_caller_signing.py](../scripts/da_caller_signing.py) and documented in
[DA_CALLER_SIGNING.md](DA_CALLER_SIGNING.md); another key generator is not needed.

Before using their one-time `init`/`upload-secrets` path, arrange a private backup
destination under owner control and supported repository-secret administration
(the owner's GitHub settings or an authorized setup session). Never ask the owner
to paste a private key, password or token into chat. Never put a keystore, its
password or its recovery archive in Git, a public Release, an Issue or a CI
artifact. No key was generated merely to fill this missing setup step.

A newly generated stable key can keep **later APKs signed with that same key**
compatible with each other. It cannot update beta.9 or 0.12.0 unless its private
identity actually matches that installed certificate, or a valid rotation lineage
was created using the old private key. Version increments, a public certificate
fingerprint and a new key do not supply the old private key. Do not silently
change the required-update pin to hide this mismatch or claim an untested data
migration. Do not recommend uninstalling: that deletes private archive/settings.

No physical-device, matching-key upgrade, permanent-key provisioning or durable
backup check is marked PASS by this audit. The remaining blocker is
owner-controlled identity, private backup and CI-secret setup.
