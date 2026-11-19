"""Security contract tests; no real key generation, GitHub writes or networking."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import stat
import subprocess
import tarfile
import tempfile
import unittest
from unittest import mock

SPEC = importlib.util.spec_from_file_location("da_caller_signing", Path(__file__).parents[1] / "da_caller_signing.py")
signing = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(signing)
CERT = "a1" * 32
OTHER = "b2" * 32
PUBLIC_OUTPUT = ("Entry type: PrivateKeyEntry\nSHA256: " + ":".join(["A1"] * 32) + "\n").encode()


class SigningContractTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="da-caller-signing-test-")
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)

    def existing(self):
        directory = self.base / "existing"
        directory.mkdir(mode=0o700)
        for name, data in ((signing.STORE_NAME, b"fake-test-keystore"), (signing.PASSWORD_NAME, b"test-password-never-in-argv\n")):
            signing.write_exclusive(directory / name, data)
        return directory

    def test_fingerprint_accepts_public_colons_but_rejects_partial_value(self):
        self.assertEqual(CERT, signing.fingerprint(":".join(["A1"] * 32)))
        with self.assertRaises(signing.SigningError):signing.fingerprint("a1")

    def test_existing_identity_is_never_regenerated(self):
        directory = self.existing()
        with mock.patch.object(signing, "public_command") as command:
            with self.assertRaises(signing.SigningError):signing.initialize(directory)
            command.assert_not_called()
        self.assertEqual(b"fake-test-keystore", (directory / signing.STORE_NAME).read_bytes())

    def test_existing_backup_prevents_new_generation(self):
        directory = self.base / "new"
        directory.with_name(directory.name + "-owner-backup.tar.gz").write_bytes(b"prior-private-backup")
        with mock.patch.object(signing, "public_command") as command:
            with self.assertRaises(signing.SigningError):signing.initialize(directory)
            command.assert_not_called()
        self.assertFalse(directory.exists())

    def test_private_files_inside_repository_are_rejected_before_tool_use(self):
        directory = signing.REPOSITORY_ROOT / "accidental-private-key"
        with mock.patch.object(signing, "public_command") as command:
            with self.assertRaises(signing.SigningError):signing.initialize(directory)
            command.assert_not_called()
        self.assertFalse(directory.exists())

    def test_missing_key_is_a_hard_failure_not_generation_fallback(self):
        with mock.patch.object(signing, "public_command") as command:
            with self.assertRaises(signing.SigningError):signing.identity(self.base / "missing", CERT)
            command.assert_not_called()

    def test_another_git_checkout_cannot_receive_private_material(self):
        repository = self.base / "other-repository"
        (repository / ".git").mkdir(parents=True)
        (repository / ".git" / "HEAD").write_text("ref: refs/heads/main\n")
        directory = repository / "nested" / "private-signing"
        with mock.patch.object(signing, "public_command") as command:
            with self.assertRaisesRegex(signing.SigningError, "every Git"):
                signing.initialize(directory)
            command.assert_not_called()
        self.assertFalse(directory.exists())

    def test_another_git_worktree_cannot_receive_private_material(self):
        repository = self.base / "other-worktree"
        repository.mkdir()
        (repository / ".git").write_text("gitdir: /not-read-by-test/worktrees/example\n")
        directory = repository / "nested" / "private-signing"
        with mock.patch.object(signing, "public_command") as command:
            with self.assertRaisesRegex(signing.SigningError, "every Git"):
                signing.initialize(directory)
            command.assert_not_called()
        self.assertFalse(directory.exists())

    def test_git_metadata_and_bare_repositories_are_rejected(self):
        metadata = self.base / ".git"
        metadata.mkdir()
        with self.assertRaises(signing.SigningError):signing.private_path(metadata / "private")
        bare = self.base / "bare-repository"
        (bare / "objects").mkdir(parents=True)
        (bare / "refs").mkdir()
        (bare / "HEAD").write_text("ref: refs/heads/main\n")
        with self.assertRaises(signing.SigningError):signing.private_path(bare / "private")

    def test_empty_sandbox_guard_directory_is_not_a_git_checkout(self):
        guard = self.base / ".git"
        guard.mkdir()
        directory = self.base / "private"
        self.assertEqual(directory, signing.private_path(directory))

    def test_different_previous_release_certificate_stops_before_reading_key(self):
        with mock.patch.object(signing, "public_command") as command:
            with self.assertRaisesRegex(signing.SigningError, "cannot update"):
                signing.identity(self.base / "missing", CERT, previous=OTHER)
            command.assert_not_called()

    def test_matching_key_verification_passes_only_password_file_path_in_argv(self):
        directory = self.existing()
        with mock.patch.object(signing, "public_command", return_value=PUBLIC_OUTPUT) as command:
            result = signing.identity(directory, CERT, previous=CERT)
        self.assertTrue(result["previous_release_certificate_matches"])
        self.assertFalse(result["matches_published_beta9"])
        arguments = command.call_args.args[0]
        self.assertIn("-storepass:file", arguments)
        self.assertNotIn("test-password-never-in-argv", " ".join(arguments))
        self.assertNotIn("-genkeypair", arguments)

    def test_wrong_key_certificate_fails_closed(self):
        directory = self.existing()
        with mock.patch.object(signing, "public_command", return_value=PUBLIC_OUTPUT):
            with self.assertRaisesRegex(signing.SigningError, "pinned expected"):
                signing.identity(directory, OTHER)

    @unittest.skipUnless(os.name == "posix", "POSIX permissions contract")
    def test_world_readable_password_is_rejected(self):
        directory = self.existing()
        (directory / signing.PASSWORD_NAME).chmod(0o644)
        with mock.patch.object(signing, "public_command") as command:
            with self.assertRaisesRegex(signing.SigningError, "Owner-only"):
                signing.identity(directory, CERT)
            command.assert_not_called()

    @unittest.skipUnless(os.name == "posix", "POSIX symbolic link contract")
    def test_symlinked_private_directory_is_rejected(self):
        original = self.existing()
        link = self.base / "link"
        link.symlink_to(original, target_is_directory=True)
        with self.assertRaisesRegex(signing.SigningError, "symbolic"):
            signing.identity(link, CERT)

    @unittest.skipUnless(os.name == "posix", "POSIX creation contract")
    def test_mocked_one_time_init_has_private_backup_and_no_secret_argv(self):
        directory = self.base / "new"
        calls = []
        def command(arguments, operation, **kwargs):
            calls.append(arguments)
            if "-genkeypair" in arguments:
                Path(arguments[arguments.index("-keystore") + 1]).write_bytes(b"mock-keystore-not-a-real-private-key")
                return b""
            return PUBLIC_OUTPUT
        with mock.patch.object(signing, "public_command", side_effect=command):
            result = signing.initialize(directory)
        password = (directory / signing.PASSWORD_NAME).read_text().strip()
        self.assertGreaterEqual(len(password), 48)
        self.assertFalse(any(password in " ".join(arguments) for arguments in calls))
        self.assertEqual(0o700, stat.S_IMODE(directory.stat().st_mode))
        for name in (signing.STORE_NAME, signing.PASSWORD_NAME, signing.CERTIFICATE_NAME, "RECOVERY.txt"):
            self.assertEqual(0o600, stat.S_IMODE((directory / name).stat().st_mode))
        backup = Path(result["owner_backup"])
        self.assertEqual(0o600, stat.S_IMODE(backup.stat().st_mode))
        with tarfile.open(backup, "r:gz") as archive:
            self.assertEqual({signing.STORE_NAME, signing.PASSWORD_NAME, signing.CERTIFICATE_NAME, "RECOVERY.txt"}, set(archive.getnames()))
        self.assertFalse(result["matches_published_beta9"])
        self.assertEqual(CERT, json.loads((directory / signing.CERTIFICATE_NAME).read_text())["certificate_sha256"])

    def test_upload_uses_only_stdin_for_all_three_secret_values(self):
        directory = self.existing()
        with mock.patch.object(signing, "public_command", return_value=b"") as command, \
             mock.patch.object(signing, "identity", return_value={"certificate_sha256": CERT}):
            result = signing.upload_secrets(directory, CERT, "owner/repo", environment="protected-release")
        self.assertEqual(list(signing.SECRET_NAMES), result["configured_secret_names"])
        self.assertEqual(3, command.call_count)
        for call in command.call_args_list:
            self.assertNotIn("test-password-never-in-argv", " ".join(call.args[0]))
            self.assertIn("--env", call.args[0])
            self.assertTrue(call.kwargs["input_bytes"])

    def test_failed_tool_does_not_echo_sensitive_stdin_or_stderr(self):
        failure = subprocess.CompletedProcess(["gh"], 1, stdout=b"private-value", stderr=b"private-value")
        with mock.patch.object(signing.subprocess, "run", return_value=failure):
            with self.assertRaises(signing.SigningError) as caught:
                signing.public_command(["gh", "secret", "set", "NAME"], "Secret upload", input_bytes=b"private-value")
        self.assertNotIn("private-value", str(caught.exception))

    def test_cli_error_is_sanitized_without_traceback(self):
        output, errors = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            result = signing.main(["verify", "--directory", str(self.base / "missing"), "--expected-certificate", CERT])
        self.assertEqual(1, result)
        self.assertEqual("", output.getvalue())
        self.assertNotIn("Traceback", errors.getvalue())


if __name__ == "__main__":
    unittest.main()
