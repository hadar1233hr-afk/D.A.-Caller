"""Signing-gate contract tests; tools/keys/APKs are synthetic and never published."""
import base64
import copy
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).parents[1]))
import da_caller_ci_signing as ci

CERT = "a1" * 32
OTHER = "b2" * 32
POLICY = {"format": "DA_CALLER_SIGNING_POLICY", "schema_version": 1,
          "application_id": "com.dacaller.app", "permanent_certificate_sha256": CERT,
          "previous_permanent_certificate_sha256": CERT,
          "required_update_version": "test-previous", "required_update_certificate_sha256": CERT}
SECRETS = {"DA_CALLER_KEYSTORE_BASE64": base64.b64encode(b"test-key-not-real").decode(),
           "DA_CALLER_KEYSTORE_PASSWORD": "test-password-never-log",
           "DA_CALLER_CERT_SHA256": CERT}
KEYTOOL = ("Entry type: PrivateKeyEntry\nSHA256: " + ":".join(["A1"] * 32) + "\n").encode()
SIGNATURE = ("Verified using v2 scheme (APK Signature Scheme v2): true\n"
             "Signer #1 certificate SHA-256 digest: " + CERT + "\n").encode()


class CiSigningContractTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="da-caller-ci-signing-test-")
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.directory = self.base / "private"
        self.apk = self.base / "synthetic.apk"
        self.apk.write_bytes(b"SYNTHETIC: not an Android APK")
        self.context = {"schema_version": 1, "mode": "permanent", "certificate_sha256": CERT}

    def test_absent_secrets_permit_only_disposable_tests_without_creating_key_files(self):
        with mock.patch.object(ci, "identity") as identity:
            result = ci.prepare(self.directory, POLICY, {})
        self.assertEqual("ephemeral-test-only", result["mode"])
        self.assertFalse(result["publication_allowed"])
        self.assertFalse(self.directory.exists())
        identity.assert_not_called()

    def test_partial_identity_never_falls_back_to_debug_key(self):
        for missing in SECRETS:
            values = {name: value for name, value in SECRETS.items() if name != missing}
            with self.subTest(missing=missing), self.assertRaisesRegex(ci.SigningError, "incomplete"):
                ci.prepare(self.directory, POLICY, values)
        self.assertFalse(self.directory.exists())

    def test_unpinned_complete_identity_is_rejected_before_handling_private_bytes(self):
        policy = {**POLICY, "permanent_certificate_sha256": None}
        with self.assertRaisesRegex(ci.SigningError, "not pinned"):
            ci.prepare(self.directory, policy, SECRETS)
        self.assertFalse(self.directory.exists())

    def test_wrong_secret_certificate_is_rejected_before_private_files(self):
        with self.assertRaisesRegex(ci.SigningError, "public pin"):
            ci.prepare(self.directory, POLICY, {**SECRETS, "DA_CALLER_CERT_SHA256": OTHER})
        self.assertFalse(self.directory.exists())

    def test_invalid_base64_and_multiline_password_cannot_inject_github_environment(self):
        for values in ({**SECRETS, "DA_CALLER_KEYSTORE_BASE64": "not valid base64!"},
                       {**SECRETS, "DA_CALLER_KEYSTORE_PASSWORD": "password\nINJECTED=value"}):
            with self.subTest(values=list(values)), self.assertRaises(ci.SigningError):
                ci.prepare(self.directory, POLICY, values)
        self.assertFalse(self.directory.exists())

    def test_unverified_inherited_keystore_path_cannot_sneak_into_test_build(self):
        with self.assertRaisesRegex(ci.SigningError, "Unverified"):
            ci.prepare(self.directory, POLICY, {"DA_CALLER_KEYSTORE_PATH": "/some/path"})

    def test_complete_identity_is_verified_before_export_with_owner_only_files(self):
        with mock.patch("da_caller_signing.public_command", return_value=KEYTOOL) as command:
            result = ci.prepare(self.directory, POLICY, SECRETS)
        self.assertEqual("permanent", result["mode"])
        self.assertTrue(result["publication_allowed"])
        self.assertEqual(0o700, stat.S_IMODE(self.directory.stat().st_mode))
        self.assertEqual(0o600, stat.S_IMODE((self.directory / ci.STORE_NAME).stat().st_mode))
        arguments = command.call_args.args[0]
        self.assertNotIn(SECRETS["DA_CALLER_KEYSTORE_PASSWORD"], arguments)
        self.assertNotIn("-genkeypair", arguments)
        environment = self.base / "github-env"
        output = self.base / "github-output"
        ci.write_github_environment(self.directory, {**SECRETS, "GITHUB_ENV": str(environment),
                                                     "GITHUB_OUTPUT": str(output)}, result)
        self.assertIn("DA_CALLER_KEYSTORE_PATH=", environment.read_text())
        self.assertEqual("mode=permanent\n", output.read_text())

    def test_permanent_identity_that_cannot_update_installed_version_remains_blocked(self):
        policy = {**POLICY, "required_update_certificate_sha256": OTHER}
        with mock.patch("da_caller_signing.public_command", return_value=KEYTOOL):
            result = ci.prepare(self.directory, policy, SECRETS)
        self.assertEqual("permanent", result["mode"])
        self.assertFalse(result["publication_allowed"])
        with mock.patch.object(ci, "public_command") as command:
            with self.assertRaisesRegex(ci.SigningError, "cannot update"):
                ci.verify_apk(self.apk, {**result, "apk_sha256": "f0" * 32}, policy,
                              apksigner="apksigner", publication=True)
            command.assert_not_called()

    def test_existing_private_directory_is_preserved_not_overwritten(self):
        self.directory.mkdir()
        marker = self.directory / "keep"
        marker.write_bytes(b"preserve")
        with self.assertRaisesRegex(ci.SigningError, "already exists"):
            ci.prepare(self.directory, POLICY, SECRETS)
        self.assertEqual(b"preserve", marker.read_bytes())

    def test_disposable_test_report_never_exports_a_password(self):
        environment = self.base / "github-env"
        output = self.base / "github-output"
        ci.write_github_environment(self.directory, {"GITHUB_ENV": str(environment),
                                  "GITHUB_OUTPUT": str(output)},
                                  {"mode": "ephemeral-test-only"})
        self.assertFalse(environment.exists())
        self.assertEqual("mode=ephemeral-test-only\n", output.read_text())

    def test_ephemeral_apk_can_be_measured_but_cannot_be_published(self):
        context = {"schema_version": 1, "mode": "ephemeral-test-only"}
        with mock.patch.object(ci, "public_command", return_value=SIGNATURE):
            result = ci.verify_apk(self.apk, context, POLICY, apksigner="apksigner")
        self.assertFalse(result["publication_allowed"])
        self.assertEqual(hashlib.sha256(self.apk.read_bytes()).hexdigest(), result["apk_sha256"])
        with mock.patch.object(ci, "public_command") as command:
            with self.assertRaisesRegex(ci.SigningError, "disposable test key"):
                ci.verify_apk(self.apk, result, POLICY, apksigner="apksigner", publication=True)
            command.assert_not_called()

    def test_actual_apk_certificate_must_match_pin_after_cryptographic_verification(self):
        with mock.patch.object(ci, "public_command", return_value=SIGNATURE.replace(CERT.encode(), OTHER.encode())):
            with self.assertRaisesRegex(ci.SigningError, "APK signer differs"):
                ci.verify_apk(self.apk, self.context, POLICY, apksigner="apksigner")

    def test_multiple_or_missing_signers_are_rejected(self):
        for output in (b"", SIGNATURE + b"Signer #2 certificate SHA-256 digest: " + OTHER.encode()):
            with self.subTest(output=output), mock.patch.object(ci, "public_command", return_value=output):
                with self.assertRaisesRegex(ci.SigningError, "exactly one"):
                    ci.verify_apk(self.apk, self.context, POLICY, apksigner="apksigner")

    def test_publication_binds_exact_native_tested_apk_bytes_and_certificate(self):
        with mock.patch.object(ci, "public_command", return_value=SIGNATURE) as command:
            measured = ci.verify_apk(self.apk, self.context, POLICY, apksigner="tool")
            published = ci.verify_apk(self.apk, measured, POLICY, apksigner="tool", publication=True)
        self.assertTrue(published["publication_allowed"])
        self.assertEqual(measured["apk_sha256"], published["apk_sha256"])
        self.assertIn("verify", command.call_args.args[0])
        self.apk.write_bytes(b"replaced APK bytes")
        with mock.patch.object(ci, "public_command", return_value=SIGNATURE):
            with self.assertRaisesRegex(ci.SigningError, "bytes differ"):
                ci.verify_apk(self.apk, measured, POLICY, apksigner="tool", publication=True)

    def test_changed_public_pin_rejects_stale_native_context(self):
        with mock.patch.object(ci, "public_command") as command:
            with self.assertRaisesRegex(ci.SigningError, "current permanent public pin"):
                ci.verify_apk(self.apk, {**self.context, "apk_sha256": "f0" * 32},
                              {**POLICY, "permanent_certificate_sha256": OTHER},
                              apksigner="tool", publication=True)
            command.assert_not_called()

    def test_publication_requires_native_apk_digest_even_if_a_certificate_is_pinned(self):
        with mock.patch.object(ci, "public_command") as command:
            with self.assertRaisesRegex(ci.SigningError, "exact APK digest"):
                ci.verify_apk(self.apk, self.context, POLICY, apksigner="tool", publication=True)
            command.assert_not_called()

    def test_policy_cannot_silently_replace_a_previous_permanent_certificate(self):
        path = self.base / "policy.json"
        path.write_text(json.dumps({**POLICY, "previous_permanent_certificate_sha256": OTHER}))
        with self.assertRaisesRegex(ci.SigningError, "Previous permanent release"):
            ci.read_policy(path)

    def test_committed_policy_honestly_blocks_new_compatible_release_without_original_key(self):
        path = Path(__file__).parents[2] / "docs" / "DA_CALLER_SIGNING_POLICY.json"
        policy = ci.read_policy(path)
        result = ci.prepare(self.directory, policy, {})
        self.assertFalse(result["publication_allowed"])
        self.assertEqual("0.11.0-beta.9", policy["required_update_version"])
        self.assertEqual("3d41dabd6e32a28ba6af2c57bb872fb3dfafa1f85a5c8523b72b54e4d8a32f56",
                         policy["required_update_certificate_sha256"])


if __name__ == "__main__":
    unittest.main()
