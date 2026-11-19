#!/usr/bin/env python3
"""Prepare and verify one private DA CALLER signing identity.

No command exports a private key or prints passwords. `init` is a deliberate
one-time operation; verification and upload never regenerate a missing key.
"""
from __future__ import annotations

import argparse
import base64
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import datetime


ALIAS = "da-caller"
STORE_NAME = "da-caller.jks"
PASSWORD_NAME = "store-password.txt"
CERTIFICATE_NAME = "certificate.json"
SCHEMA = 1
BETA9_CERTIFICATE = "3d41dabd6e32a28ba6af2c57bb872fb3dfafa1f85a5c8523b72b54e4d8a32f56"
REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
SECRET_NAMES = (
    "DA_CALLER_KEYSTORE_BASE64", "DA_CALLER_KEYSTORE_PASSWORD", "DA_CALLER_CERT_SHA256",
)


class SigningError(Exception):
    """An intentionally sanitized error: never include tool output or secrets."""


def fingerprint(value: str) -> str:
    normalized = value.replace(":", "").strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", normalized):
        raise SigningError("Expected a complete SHA256 certificate fingerprint.")
    return normalized


def public_command(arguments: list[str], operation: str, *, input_bytes: bytes | None = None) -> bytes:
    """Capture tool output so failure cannot accidentally echo sensitive stdin."""
    try:
        result = subprocess.run(arguments, input=input_bytes, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, check=False, timeout=120)
    except (OSError, subprocess.SubprocessError) as error:
        raise SigningError(f"{operation} could not complete ({type(error).__name__}).") from None
    if result.returncode:
        raise SigningError(f"{operation} failed (exit {result.returncode}); tool output withheld.")
    return result.stdout


def private_path(path: Path) -> Path:
    """Reject repository destinations and symlinked paths before handling a key."""
    path = path.expanduser().absolute()
    for part in (path, *path.parents):
        if part.is_symlink():
            raise SigningError("Signing paths must not contain symbolic links.")
    resolved = path.resolve()
    if resolved == REPOSITORY_ROOT or REPOSITORY_ROOT in resolved.parents:
        raise SigningError("Private signing files must be outside this repository.")
    # A worktree has a .git FILE, while a normal checkout has a directory.
    # Both are repository boundaries. Also reject placement inside Git's own
    # metadata directory or a recognizable bare repository.
    for ancestor in (resolved, *resolved.parents):
        marker = ancestor / ".git"
        # Managed execution sandboxes may expose an EMPTY read-only .git guard
        # at /workspace or /tmp. It is not a checkout. Every actual .git
        # directory has metadata; a .git file marks a worktree/submodule.
        checkout = (marker.is_symlink() or marker.is_file()
                    or marker.is_dir() and any(marker.iterdir()))
        bare = ((ancestor / "HEAD").is_file() and (ancestor / "objects").is_dir()
                and (ancestor / "refs").is_dir())
        if ancestor.name == ".git" or checkout or bare:
            raise SigningError("Private signing files must be outside every Git repository/worktree.")
    return resolved


def require_private_file(path: Path) -> None:
    if path.is_symlink() or not path.is_file():
        raise SigningError(f"Required private file is missing or unsafe: {path}")
    metadata = path.stat()
    mode = stat.S_IMODE(metadata.st_mode)
    if os.name != "posix" or mode & 0o077 or metadata.st_uid != os.getuid():
        raise SigningError(f"Owner-only file permissions are required: {path}")


def write_exclusive(path: Path, data: bytes, mode: int = 0o600) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, mode)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)
    except BaseException:
        # Preserve any material already created. Never silently regenerate it.
        raise


def identity(directory: Path, expected: str, *, previous: str | None = None,
             keytool: str = "keytool") -> dict:
    """Verify a provided identity; no file mutation and no generation fallback."""
    expected = fingerprint(expected)
    if previous is not None and fingerprint(previous) != expected:
        raise SigningError("Previous-release certificate differs: this identity cannot update that installation. The original private key is required; do not uninstall it.")
    directory = private_path(directory)
    store, password = directory / STORE_NAME, directory / PASSWORD_NAME
    require_private_file(store)
    require_private_file(password)
    if not password.read_bytes().strip():
        raise SigningError("Signing password file is empty.")
    output = public_command([
        keytool, "-J-Duser.language=en", "-J-Duser.country=US", "-list", "-v",
        "-keystore", str(store), "-storepass:file", str(password), "-alias", ALIAS,
    ], "Certificate inspection").decode("utf-8", errors="replace")
    match = re.search(r"SHA256:\s*([A-Fa-f0-9:]+)", output)
    if not match or "Entry type: PrivateKeyEntry" not in output:
        raise SigningError("Expected alias with a private-key entry and SHA256 certificate.")
    actual = fingerprint(match.group(1))
    if actual != expected:
        raise SigningError("Signing certificate differs from the pinned expected certificate; publication must stop.")
    return {
        "schema_version": SCHEMA, "alias": ALIAS, "store_type": "JKS",
        "certificate_sha256": actual,
        "previous_release_certificate_checked": previous is not None,
        "previous_release_certificate_matches": True if previous is not None else None,
        "matches_published_beta9": actual == BETA9_CERTIFICATE,
    }


def initialize(directory: Path, *, keytool: str = "keytool") -> dict:
    if os.name != "posix":
        raise SigningError("Initialization requires POSIX owner-only permissions; configure equivalent secure storage on other platforms.")
    directory = private_path(directory)
    backup = private_path(directory.with_name(directory.name + "-owner-backup.tar.gz"))
    if directory.exists() or backup.exists():
        raise SigningError("Signing directory or owner backup already exists; refusing to overwrite or regenerate an identity.")
    directory.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    directory.mkdir(mode=0o700)
    password = directory / PASSWORD_NAME
    store = directory / STORE_NAME
    write_exclusive(password, (secrets.token_urlsafe(48) + "\n").encode("ascii"))
    # keytool receives file paths, never password values through argv or stdin.
    previous_umask = os.umask(0o077)
    try:
        public_command([
            keytool, "-J-Duser.language=en", "-J-Duser.country=US", "-genkeypair",
            "-alias", ALIAS, "-keyalg", "RSA", "-keysize", "3072",
            "-sigalg", "SHA256withRSA", "-validity", "36500", "-storetype", "JKS",
            "-dname", "CN=DA CALLER, OU=Application Signing, O=DA CALLER",
            "-keystore", str(store), "-storepass:file", str(password),
            "-keypass:file", str(password), "-noprompt",
        ], "One-time signing identity generation")
    finally:
        os.umask(previous_umask)
    if not store.is_file() or store.is_symlink():
        raise SigningError("Signing tool did not create the expected private keystore; preserve this directory and investigate.")
    store.chmod(0o600)
    public = public_command([
        keytool, "-J-Duser.language=en", "-J-Duser.country=US", "-list", "-v",
        "-keystore", str(store), "-storepass:file", str(password), "-alias", ALIAS,
    ], "New certificate inspection").decode("utf-8", errors="replace")
    match = re.search(r"SHA256:\s*([A-Fa-f0-9:]+)", public)
    if not match:
        raise SigningError("Generated identity lacks its public certificate fingerprint; preserve this directory and investigate.")
    certificate = identity(directory, match.group(1), keytool=keytool)
    certificate["created_at_utc"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    certificate["purpose"] = "One stable DA CALLER identity for future releases; no compatibility claim for an older different signer."
    write_exclusive(directory / CERTIFICATE_NAME,
                    (json.dumps(certificate, indent=2) + "\n").encode("utf-8"))
    instructions = (
        "PRIVATE DA CALLER SIGNING RECOVERY BACKUP\n"
        "This archive contains BOTH the private keystore and its password.\n"
        "Keep it in owner-controlled secure backup storage, never a public repository, release, issue or CI artifact.\n"
        "Restore all files into a private directory outside Git; directory mode0700, files0600.\n"
        "Keep using this exact identity. Never regenerate a missing identity during a build.\n"
        "A new identity DOES NOT update the published beta.9 installation signed by a different key.\n"
        "Never uninstall an existing installation to work around signing: its private archive/settings would be lost.\n"
    )
    write_exclusive(directory / "RECOVERY.txt", instructions.encode("utf-8"))
    descriptor, temporary_name = tempfile.mkstemp(prefix=".da-caller-signing-backup-", dir=directory.parent)
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        with tarfile.open(temporary, "w:gz") as archive:
            for name in (STORE_NAME, PASSWORD_NAME, CERTIFICATE_NAME, "RECOVERY.txt"):
                archive.add(directory / name, arcname=name, recursive=False)
        temporary.chmod(0o600)
        # A hard-link creation is exclusive; an unexpected existing backup is
        # never replaced. The temporary file and final backup share filesystem.
        os.link(temporary, backup)
    finally:
        temporary.unlink(missing_ok=True)
    return {**certificate, "private_directory": str(directory), "owner_backup": str(backup)}


def upload_secrets(directory: Path, expected: str, repository: str,
                   *, previous: str | None = None, environment: str | None = None,
                   keytool: str = "keytool", gh: str = "gh") -> dict:
    """Explicit owner command, not an automatic build step. No auth fallback."""
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise SigningError("Repository must be an explicit owner/name.")
    if environment is not None and not re.fullmatch(r"[A-Za-z0-9_.-]+", environment):
        raise SigningError("Environment name contains unsupported characters.")
    certificate = identity(directory, expected, previous=previous, keytool=keytool)
    directory = private_path(directory)
    values = (
        base64.b64encode((directory / STORE_NAME).read_bytes()),
        (directory / PASSWORD_NAME).read_bytes().strip(),
        certificate["certificate_sha256"].encode("ascii"),
    )
    for name, value in zip(SECRET_NAMES, values):
        command = [gh, "secret", "set", name, "--repo", repository]
        if environment:
            command.extend(["--env", environment])
        public_command(command, f"GitHub secret configuration for {name}", input_bytes=value)
    return {**certificate, "repository": repository, "environment": environment,
            "configured_secret_names": list(SECRET_NAMES)}


def main(arguments: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("init", "verify", "upload-secrets"):
        command = commands.add_parser(name)
        command.add_argument("--directory", type=Path, required=True,
                             help="Private directory OUTSIDE the repository")
        command.add_argument("--keytool", default=shutil.which("keytool") or "keytool")
        if name != "init":
            command.add_argument("--expected-certificate", required=True)
            command.add_argument("--previous-certificate")
        if name == "upload-secrets":
            command.add_argument("--repository", required=True)
            command.add_argument("--environment")
            command.add_argument("--gh", default=shutil.which("gh") or "gh")
    args = parser.parse_args(arguments)
    try:
        if args.command == "init":
            result = initialize(args.directory, keytool=args.keytool)
        elif args.command == "verify":
            result = identity(args.directory, args.expected_certificate,
                              previous=args.previous_certificate, keytool=args.keytool)
        else:
            result = upload_secrets(args.directory, args.expected_certificate, args.repository,
                                    previous=args.previous_certificate, environment=args.environment,
                                    keytool=args.keytool, gh=args.gh)
    except (SigningError, OSError) as error:
        # OSError arguments are paths/error codes, never the captured keytool/gh
        # output or supplied secret content. Do not enable debug tracebacks.
        message = str(error) if isinstance(error, SigningError) else f"Private storage operation failed ({type(error).__name__})."
        print("DA CALLER signing: " + message, file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
