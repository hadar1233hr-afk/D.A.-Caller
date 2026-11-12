#!/usr/bin/env python3
"""Prepare an existing CI signing key and block incompatible APK publication.

An absent identity permits disposable build/test artifacts only. This script
never creates a key, uploads a secret, or repairs a certificate mismatch.
"""
from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import json
import os
from pathlib import Path
import re
import sys

from da_caller_signing import (SigningError, SECRET_NAMES, STORE_NAME, PASSWORD_NAME,
                               fingerprint, identity, private_path, public_command,
                               write_exclusive)


# Deliberately binds one already native-tested binary, not every APK bearing the
# same version string. Changing a future release still requires its normal key.
APPROVED_PREVIEW_012_PINS = {
    "application_id": "com.dacaller.app",
    "version_name": "0.12.0",
    "version_code": 21,
    "apk_sha256": "f025fad667edafe2c76ab3f574c7ff1bfd2aeb301fdd1d035ac1acb6b311e16a",
    "certificate_sha256": "fbf3b0bc4d5cda06e2bbe0e07e2e2aa1828f30f09e86e82e9afc1f302a38b723",
    "app_tree": "9f15350b457405bf88a35c0ac5badc8f3a85cd94",
    "approval_record": "docs/releases/0.12.0-publication-approval.md",
}


def approved_preview_012_policy(policy: dict) -> dict:
    exception = policy.get("approved_preview_012")
    if (not isinstance(exception, dict) or exception.get("approved") is not True
            or set(exception) != {"approved", *APPROVED_PREVIEW_012_PINS}
            or any(type(exception.get(key)) is not type(value) or exception[key] != value
                   for key, value in APPROVED_PREVIEW_012_PINS.items())):
        raise SigningError("Approved preview policy must pin only the exact reviewed 0.12.0 APK.")
    return exception


def read_policy(path: Path) -> dict:
    try:
        value = json.loads(path.read_text())
    except (OSError, ValueError):
        raise SigningError("Public signing policy is missing or invalid.") from None
    if (not isinstance(value, dict) or value.get("format") != "DA_CALLER_SIGNING_POLICY"
            or value.get("schema_version") != 1
            or value.get("application_id") != "com.dacaller.app"):
        raise SigningError("Unexpected signing policy schema/product.")
    for key in ("permanent_certificate_sha256", "previous_permanent_certificate_sha256"):
        if value.get(key) is not None:
            if not isinstance(value[key], str):
                raise SigningError("Public certificate pin must be a complete SHA256 string.")
            value[key] = fingerprint(value[key])
    if not isinstance(value.get("required_update_certificate_sha256"), str):
        raise SigningError("Required existing-installation certificate is missing.")
    value["required_update_certificate_sha256"] = fingerprint(value.get("required_update_certificate_sha256", ""))
    previous = value.get("previous_permanent_certificate_sha256")
    if previous and previous != value.get("permanent_certificate_sha256"):
        raise SigningError("Previous permanent release has another certificate; restore its original key.")
    if "approved_preview_012" in value:
        approved_preview_012_policy(value)
    return value


def prepare(directory: Path, policy: dict, environ: dict, *, keytool: str = "keytool") -> dict:
    values = [environ.get(name, "") for name in SECRET_NAMES]
    available = [bool(value) for value in values]
    report = {"schema_version": 1, "mode": "ephemeral-test-only",
              "certificate_sha256": None, "publication_allowed": False,
              "required_update_version": policy.get("required_update_version"),
              "required_update_certificate_matches": False}
    if not any(available):
        if environ.get("DA_CALLER_KEYSTORE_PATH"):
            raise SigningError("Unverified signing path supplied without the complete identity.")
        return report
    if not all(available):
        raise SigningError("Signing identity is incomplete; configure all three secrets together. No fallback is allowed.")
    expected = policy.get("permanent_certificate_sha256")
    if not expected:
        raise SigningError("Permanent public certificate is not pinned; publication remains blocked.")
    encoded, password, supplied_certificate = values
    if fingerprint(supplied_certificate) != expected:
        raise SigningError("Configured certificate differs from the committed public pin.")
    if (len(password) < 6 or len(password) > 4096
            or any(character in password for character in "\r\n\0")):
        raise SigningError("Signing password must be a bounded single-line secret.")
    if len(encoded) > 2_000_000:
        raise SigningError("Provided signing key exceeds the supported private-key size.")
    try:
        key_bytes = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error):
        raise SigningError("Provided signing key is not valid base64.") from None
    if not key_bytes:
        raise SigningError("Provided signing key is empty.")
    directory = private_path(directory)
    if directory.exists():
        raise SigningError("CI private signing directory already exists; refusing to overwrite it.")
    directory.mkdir(mode=0o700, parents=True)
    write_exclusive(directory / STORE_NAME, key_bytes)
    write_exclusive(directory / PASSWORD_NAME, (password + "\n").encode())
    checked = identity(directory, expected,
                       previous=policy.get("previous_permanent_certificate_sha256"),
                       keytool=keytool)
    matches = checked["certificate_sha256"] == policy["required_update_certificate_sha256"]
    report.update(mode="permanent", certificate_sha256=expected,
                  required_update_certificate_matches=matches,
                  publication_allowed=matches)
    return report


def write_github_environment(directory: Path, environ: dict, report: dict) -> None:
    """Only a verified identity enters later build steps; never log its password."""
    output = environ.get("GITHUB_OUTPUT")
    if output:
        with Path(output).open("a") as stream:
            stream.write("mode=" + report["mode"] + "\n")
    if report["mode"] != "permanent":
        return
    environment_file = environ.get("GITHUB_ENV")
    if not environment_file:
        raise SigningError("GITHUB_ENV is required to pass the verified signing identity to the build.")
    key_path = str(private_path(directory) / STORE_NAME)
    if any(character in key_path for character in "\r\n\0"):
        raise SigningError("Private signing path contains unsupported characters.")
    with Path(environment_file).open("a") as stream:
        stream.write("DA_CALLER_KEYSTORE_PATH=" + key_path + "\n")
        stream.write("DA_CALLER_KEYSTORE_PASSWORD=" + environ["DA_CALLER_KEYSTORE_PASSWORD"] + "\n")


def verify_approved_preview_012(apk: Path, context: dict, policy: dict, *,
                              apksigner: str, aapt: str | None, publication: bool) -> dict:
    """Check an explicit, owner-approved signing exception without re-signing.

    This result opens only the signing gate. Authentic native/release-scope,
    complete visual review and public-download equality remain separate gates.
    """
    if publication is not True:
        raise SigningError("Approved 0.12.0 preview opt-in requires explicit publication mode.")
    exception = approved_preview_012_policy(policy)
    if type(context.get("schema_version")) is not int or context["mode"] != "ephemeral-test-only":
        raise SigningError("Approved 0.12.0 preview requires its original native-test signing context.")
    if (context.get("apk_sha256") != exception["apk_sha256"]
            or context.get("certificate_sha256") != exception["certificate_sha256"]):
        raise SigningError("Approved preview context differs from the reviewed native APK hash/signer.")
    if not aapt:
        raise SigningError("Approved preview requires Android build-tools aapt for embedded APK metadata.")
    if hashlib.sha256(apk.read_bytes()).hexdigest() != exception["apk_sha256"]:
        raise SigningError("Approved preview APK bytes differ from the exact native-tested binary.")
    output = public_command([apksigner, "verify", "--verbose", "--print-certs", str(apk)],
                            "Approved preview APK cryptographic signature verification").decode(errors="replace")
    found = re.findall(r"Signer #([0-9]+) certificate SHA-256 digest:\s*([a-fA-F0-9:]+)", output)
    if len(found) != 1 or found[0][0] != "1":
        raise SigningError("Expected exactly one cryptographically verified APK signer.")
    actual = fingerprint(found[0][1])
    if actual != exception["certificate_sha256"]:
        raise SigningError("Approved preview signer differs from the exact native-tested APK.")
    metadata = public_command([aapt, "dump", "badging", str(apk)],
                              "Approved preview embedded APK identity inspection").decode(errors="replace")
    packages = re.findall(r"^package:\s+(.+)$", metadata, re.MULTILINE)
    if len(packages) != 1:
        raise SigningError("Expected one embedded APK package metadata record.")
    fields = {}
    for key in ("name", "versionName", "versionCode"):
        values = re.findall(r"(?:^|\s)" + key + r"='([^']*)'", packages[0])
        if len(values) != 1:
            raise SigningError("Embedded approved-preview package/version metadata is missing or ambiguous.")
        fields[key] = values[0]
    if (fields["name"] != exception["application_id"]
            or fields["versionName"] != exception["version_name"]
            or fields["versionCode"] != str(exception["version_code"])):
        raise SigningError("Approved preview requires embedded com.dacaller.app version 0.12.0/code21 only.")
    if hashlib.sha256(apk.read_bytes()).hexdigest() != exception["apk_sha256"]:
        raise SigningError("Approved preview APK changed during cryptographic/metadata verification.")
    required_matches = actual == policy["required_update_certificate_sha256"]
    return {"schema_version": 1, "mode": context["mode"], "certificate_sha256": actual,
            "apk_sha256": exception["apk_sha256"],
            "application_id": fields["name"], "version_name": fields["versionName"],
            "version_code": exception["version_code"], "approved_app_tree": exception["app_tree"],
            "required_update_version": policy.get("required_update_version"),
            "required_update_certificate_matches": required_matches,
            "publication_allowed": True, "publication_exception": "owner-approved-exact-0.12.0-preview",
            "approval_record": exception["approval_record"],
            "signing_gate_only": True, "durable_future_signing_verified": False,
            "limits": "Exact retained native-tested binary only; other release gates remain required. Different beta.9 signer cannot update in place; never uninstall an existing app."}


def verify_apk(apk: Path, context: dict, policy: dict, *, apksigner: str,
               publication: bool = False, approved_preview_012: bool = False,
               aapt: str | None = None) -> dict:
    if (not isinstance(context, dict) or context.get("schema_version") != 1
            or context.get("mode") not in ("permanent", "ephemeral-test-only")):
        raise SigningError("APK signing context is missing/invalid.")
    if type(approved_preview_012) is not bool:
        raise SigningError("Approved preview opt-in must be an explicit boolean.")
    if approved_preview_012:
        return verify_approved_preview_012(apk, context, policy, apksigner=apksigner,
                                           aapt=aapt, publication=publication)
    mode = context["mode"]
    if publication and mode != "permanent":
        raise SigningError("Publication blocked: emulator APK used a disposable test key. Provision the original compatible permanent signing identity before testing again; do not uninstall an existing app.")
    if publication and not re.fullmatch(r"[a-f0-9]{64}", str(context.get("apk_sha256", ""))):
        raise SigningError("Publication requires the exact APK digest from native verification.")
    expected = policy.get("permanent_certificate_sha256")
    if mode == "permanent":
        if not expected or context.get("certificate_sha256") != expected:
            raise SigningError("Verified signing context differs from the current permanent public pin.")
        if publication and expected != policy["required_update_certificate_sha256"]:
            raise SigningError("Publication blocked: certificate cannot update the required existing installation. The original private key is unavailable; do not uninstall it.")
    output = public_command([apksigner, "verify", "--verbose", "--print-certs", str(apk)],
                            "APK cryptographic signature verification").decode(errors="replace")
    found = re.findall(r"Signer #([0-9]+) certificate SHA-256 digest:\s*([a-fA-F0-9:]+)", output)
    if len(found) != 1 or found[0][0] != "1":
        raise SigningError("Expected exactly one cryptographically verified APK signer.")
    actual = fingerprint(found[0][1])
    if mode == "permanent" and actual != expected:
        raise SigningError("APK signer differs from the pinned key used before emulator testing.")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    prior_digest = context.get("apk_sha256")
    if prior_digest and prior_digest != digest:
        raise SigningError("APK bytes differ from the verified native-test artifact.")
    required_matches = actual == policy["required_update_certificate_sha256"]
    return {"schema_version": 1, "mode": mode, "certificate_sha256": actual,
            "apk_sha256": digest, "required_update_version": policy.get("required_update_version"),
            "required_update_certificate_matches": required_matches,
            "publication_allowed": mode == "permanent" and required_matches}


def main(arguments: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--policy", type=Path, default=Path("docs/DA_CALLER_SIGNING_POLICY.json"))
    commands = parser.add_subparsers(dest="command", required=True)
    setup = commands.add_parser("prepare")
    setup.add_argument("--directory", type=Path, required=True)
    setup.add_argument("--report", type=Path, required=True)
    setup.add_argument("--keytool", default="keytool")
    verify = commands.add_parser("verify-apk")
    verify.add_argument("--apk", type=Path, required=True)
    verify.add_argument("--context", type=Path, required=True)
    verify.add_argument("--report", type=Path, required=True)
    verify.add_argument("--apksigner", required=True)
    verify.add_argument("--publication", action="store_true")
    verify.add_argument("--approved-preview-012", action="store_true",
                        help="Explicit owner-approved exception for one pinned native-tested 0.12.0 binary only")
    verify.add_argument("--aapt", help="Android build-tools aapt required for the approved 0.12.0 exception")
    args = parser.parse_args(arguments)
    try:
        policy = read_policy(args.policy)
        if args.command == "prepare":
            result = prepare(args.directory, policy, os.environ, keytool=args.keytool)
            write_github_environment(args.directory, os.environ, result)
        else:
            result = verify_apk(args.apk, json.loads(args.context.read_text()), policy,
                                apksigner=args.apksigner, publication=args.publication,
                                approved_preview_012=args.approved_preview_012, aapt=args.aapt)
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    except (SigningError, OSError, ValueError) as error:
        message = str(error) if isinstance(error, SigningError) else "Signing report/storage operation failed; details withheld."
        print("DA CALLER signing: " + message, file=sys.stderr)
        return 1
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
