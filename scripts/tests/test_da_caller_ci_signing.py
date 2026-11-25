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


class ApprovedPreview012ContractTest(unittest.TestCase):
    """Synthetic gate fixtures; a separate real retained APK check is required."""

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="da-caller-approved-preview-test-")
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.apk = self.base / "synthetic.apk"
        self.apk.write_bytes(b"SYNTHETIC APPROVED PREVIEW CONTRACT: not an Android APK")
        self.sha = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        # Only these synthetic tests replace byte/certificate pins. Committed
        # real pins are asserted separately and verified with Android tools.
        self.pins = mock.patch.dict(ci.APPROVED_PREVIEW_012_PINS,
                                   {"apk_sha256": self.sha, "certificate_sha256": CERT})
        self.pins.start()
        self.addCleanup(self.pins.stop)
        self.policy = copy.deepcopy(POLICY)
        self.policy["required_update_certificate_sha256"] = OTHER
        self.policy["approved_preview_012"] = {"approved": True, **ci.APPROVED_PREVIEW_012_PINS}
        self.context = {"schema_version": 1, "mode": "ephemeral-test-only",
                        "apk_sha256": self.sha, "certificate_sha256": CERT}
        self.metadata = b"package: name='com.dacaller.app' versionCode='21' versionName='0.12.0' platformBuildVersionName='15'\n"

    def command(self, arguments, operation):
        if arguments[0] == "apksigner":
            return SIGNATURE
        self.assertEqual(["aapt", "dump", "badging", str(self.apk)], arguments)
        return self.metadata

    def verify(self, **changes):
        arguments = dict(apksigner="apksigner", aapt="aapt", publication=True,
                         approved_preview_012=True)
        arguments.update(changes)
        with mock.patch.object(ci, "public_command", side_effect=self.command):
            return ci.verify_apk(self.apk, self.context, self.policy, **arguments)

    def test_exact_opted_in_scope_opens_signing_gate_without_claiming_upgrade_or_durable_key(self):
        result = self.verify()
        self.assertTrue(result["publication_allowed"])
        self.assertTrue(result["signing_gate_only"])
        self.assertFalse(result["required_update_certificate_matches"])
        self.assertFalse(result["durable_future_signing_verified"])
        self.assertEqual("ephemeral-test-only", result["mode"])
        self.assertEqual(("com.dacaller.app", "0.12.0", 21),
                         (result["application_id"], result["version_name"], result["version_code"]))
        self.assertEqual(self.sha, result["apk_sha256"])

    def test_policy_exception_does_not_enable_default_publication_route(self):
        with mock.patch.object(ci, "public_command") as command:
            with self.assertRaisesRegex(ci.SigningError, "disposable test key"):
                ci.verify_apk(self.apk, self.context, self.policy, apksigner="tool", publication=True)
            command.assert_not_called()

    def test_explicit_publication_mode_boolean_and_metadata_tool_are_required(self):
        for changes in ({"publication": False}, {"publication": "true"}, {"aapt": None},
                        {"approved_preview_012": "true"}, {"approved_preview_012": 1}):
            with self.subTest(changes=changes), self.assertRaises(ci.SigningError):
                self.verify(**changes)

    def test_missing_owner_approved_policy_cannot_be_enabled_by_cli_flag(self):
        for value in (None, {}, {"approved": False}, {**self.policy["approved_preview_012"], "approved": 1}):
            self.policy["approved_preview_012"] = value
            with self.assertRaisesRegex(ci.SigningError, "policy"):
                self.verify()

    def test_scope_and_real_binary_pins_cannot_be_widened_in_runtime_policy(self):
        original = copy.deepcopy(self.policy["approved_preview_012"])
        for key, changed in (("application_id", "com.other.app"), ("version_name", "0.12.1"),
                             ("version_code", 22), ("version_code", True),
                             ("apk_sha256", "f0"*32), ("certificate_sha256", OTHER),
                             ("approval_record", "another-record.md"), ("unexpected_future_scope", True)):
            self.policy["approved_preview_012"] = {**original, key: changed}
            with self.subTest(key=key), self.assertRaisesRegex(ci.SigningError, "policy"):
                self.verify()

    def test_unmeasured_or_wrong_native_context_fails_before_android_tools(self):
        original = copy.deepcopy(self.context)
        for key, changed in (("apk_sha256", None), ("apk_sha256", "f0"*32),
                             ("certificate_sha256", OTHER), ("mode", "permanent"),
                             ("schema_version", True)):
            self.context = {**original, key: changed}
            with self.subTest(key=key), self.assertRaises(ci.SigningError):
                self.verify()

    def test_changed_apk_bytes_fail_even_with_original_native_context(self):
        self.apk.write_bytes(b"rebuilt or replaced candidate")
        with self.assertRaisesRegex(ci.SigningError, "bytes differ"):
            self.verify()

    def test_actual_embedded_other_product_version_code_or_beta_suffix_is_rejected(self):
        original = self.metadata
        for before, after in ((b"com.dacaller.app", b"com.dacaller.other"),
                              (b"versionName='0.12.0'", b"versionName='0.12.1'"),
                              (b"versionName='0.12.0'", b"versionName='0.12.0-beta.1'"),
                              (b"versionCode='21'", b"versionCode='22'")):
            self.metadata = original.replace(before, after)
            with self.subTest(after=after), self.assertRaisesRegex(ci.SigningError, "embedded"):
                self.verify()

    def test_missing_duplicate_or_filename_only_package_metadata_is_rejected(self):
        original = self.metadata
        for value in (b"DA-CALLER-v0.12.0.apk", b"", original+original,
                      b"package: name='com.dacaller.app' versionName='0.12.0'\n",
                      original.replace(b"versionCode='21'", b"versionCode='21' versionCode='21'")):
            self.metadata = value
            with self.subTest(value=value), self.assertRaises(ci.SigningError):
                self.verify()

    def test_actual_cryptographic_signer_must_match_retained_binary_pin(self):
        for output in (SIGNATURE.replace(CERT.encode(), OTHER.encode()), b"",
                       SIGNATURE+b"Signer #2 certificate SHA-256 digest: "+CERT.encode()):
            def command(arguments, operation):
                return output if arguments[0] == "apksigner" else self.metadata
            with mock.patch.object(ci, "public_command", side_effect=command):
                with self.subTest(output=output), self.assertRaises(ci.SigningError):
                    ci.verify_apk(self.apk, self.context, self.policy, apksigner="apksigner", aapt="aapt",
                                  publication=True, approved_preview_012=True)

    def test_tool_failure_cannot_be_treated_as_a_successful_signature_or_manifest(self):
        with mock.patch.object(ci, "public_command", side_effect=ci.SigningError("verification failed")):
            with self.assertRaises(ci.SigningError):
                ci.verify_apk(self.apk, self.context, self.policy, apksigner="apksigner", aapt="aapt",
                              publication=True, approved_preview_012=True)

    def test_candidate_mutation_during_metadata_inspection_is_rejected(self):
        def command(arguments, operation):
            if arguments[0] == "apksigner":
                return SIGNATURE
            self.apk.write_bytes(b"changed during aapt inspection")
            return self.metadata
        with mock.patch.object(ci, "public_command", side_effect=command):
            with self.assertRaisesRegex(ci.SigningError, "changed during"):
                ci.verify_apk(self.apk, self.context, self.policy, apksigner="apksigner", aapt="aapt",
                              publication=True, approved_preview_012=True)

    def test_policy_reader_rejects_broadened_or_truthy_only_preview_exception(self):
        path = self.base / "policy.json"
        for changed in ({"approved": "true"}, {"version_name": "0.13.0"}, {"version_code": True}):
            path.write_text(json.dumps({**self.policy, "approved_preview_012":
                                        {**self.policy["approved_preview_012"], **changed}}))
            with self.assertRaisesRegex(ci.SigningError, "policy"):
                ci.read_policy(path)


class CommittedPreview012PinsTest(unittest.TestCase):
    def test_actual_committed_exception_pins_one_reviewed_native_binary_and_keeps_default_block(self):
        policy = ci.read_policy(Path(__file__).parents[2] / "docs/DA_CALLER_SIGNING_POLICY.json")
        expected = policy["approved_preview_012"]
        self.assertEqual("f025fad667edafe2c76ab3f574c7ff1bfd2aeb301fdd1d035ac1acb6b311e16a", expected["apk_sha256"])
        self.assertEqual("fbf3b0bc4d5cda06e2bbe0e07e2e2aa1828f30f09e86e82e9afc1f302a38b723", expected["certificate_sha256"])
        self.assertIsNone(policy["permanent_certificate_sha256"])
        self.assertEqual("0.11.0-beta.9", policy["required_update_version"])
        self.assertEqual("3d41dabd6e32a28ba6af2c57bb872fb3dfafa1f85a5c8523b72b54e4d8a32f56", policy["required_update_certificate_sha256"])


if __name__ == "__main__":
    unittest.main()
