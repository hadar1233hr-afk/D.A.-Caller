#!/usr/bin/env python3
"""Stage/package the one owner-approved 0.12.0 native artifact; never build/sign.

Default/future release policy is not handled here. Scope, signature and explicit
owner approval are independent gates. Public-byte verification happens only
after the GitHub release exists and is deliberately marked pending here.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import stat
import subprocess
import tempfile
import zipfile

REPO = Path(__file__).resolve().parents[1]
NATIVE_SOURCE = "0d266d63d446b46d3e2a98048b112572d22768fa"
EXPORT = "612188913a0e9ff2040d58892d1f9e83d528c4da"
APP_TREE = "9f15350b457405bf88a35c0ac5badc8f3a85cd94"
APK_SHA = "f025fad667edafe2c76ab3f574c7ff1bfd2aeb301fdd1d035ac1acb6b311e16a"
CERT = "fbf3b0bc4d5cda06e2bbe0e07e2e2aa1828f30f09e86e82e9afc1f302a38b723"
RUN = 42001641651
VISUAL_SHA = "689954f804f3e2ccf31395ad217fc559196b95375e19f0a513f919aa207dc23d"
AUTH_PATH = "docs/releases/0.12.0-publication-authorization.json"
VISUAL_PATH = "docs/evidence/release-0.12.0/polish-ci/full-visual-observation.json"
BUILD_INPUTS = ("app", "build.gradle", "settings.gradle", "gradle.properties", "gradle", "gradlew", "gradlew.bat")
ARTIFACTS = {
    "native-failure-candidate.zip": (454470, "a09cfb1e0a6c69a69ab050394e7f3589bf976358c4e27ac3474555d01f3cf850"),
    "ui.zip": (14225526, "55ae41e1c1c710985d7bf5b722148554e144ce5e19c84225a9dafd3c5b97f0fa"),
    "checks.zip": (130531, "f36b4f7def34c55384e531e32242836d8e50c27ff8c2ce6dd0583bac229d2d37"),
    "validate-apk.zip": (454281, "918115f9717e247404648c9235c63caed745bff3011a81bcbd786837f855ae47"),
}
AUTH_PINS = dict(application_id="com.dacaller.app", version_name="0.12.0", version_code=21,
                 apk_sha256=APK_SHA, certificate_sha256=CERT, app_tree=APP_TREE,
                 native_source_sha=NATIVE_SOURCE, source_run_id=RUN,
                 format="DA_CALLER_BOUNDED_PUBLICATION_AUTHORIZATION", schema_version=1,
                 repository="hadar1233hr-afk/D.A.-Caller", pull_request=5,
                 raw_export_commit=EXPORT, release_scope_pngs=243, baseline_pngs=233,
                 authentic_unit_cases=356, authentic_native_cases=35,
                 rebuild_allowed=False, resigning_allowed=False, business_data_in_apk=False,
                 future_default_signing_guards_unchanged=True, legacy_245_business_gate_unchanged=True,
                 physical_jelly_star_prerequisite_waived=True, physical_device_tested=False,
                 beta9_compatible_upgrade_tested=False, public_download_byte_equality_required=True)
FUNCTIONAL = {"verify-ui-matrix.py", "verify-personalization-matrix.py", "verify-call-integration-matrix.py",
              "verify-island-matrix.py", "verify-external-island-matrix.py", "verify-followup-matrix.py",
              "independent-reminder-swipe-contact-deferred-profile"}
FUNCTIONAL_PINS = {
    "verify-ui-matrix.py": ("e3fd18f544b028ffeddaddee272b2f4e7aefa2cc47f5d2d90fac5d9580c9dab2", 0,
        "e3fd18f544b028ffeddaddee272b2f4e7aefa2cc47f5d2d90fac5d9580c9dab2"),
    "verify-personalization-matrix.py": ("0fa3106ef1832cd5fb7fd8df167c56e19c70b5a034ae1ffc20636659220d2825", 0,
        "0fa3106ef1832cd5fb7fd8df167c56e19c70b5a034ae1ffc20636659220d2825"),
    "verify-call-integration-matrix.py": ("0dbe8072173a2417ce9bb46fb686c9ab8fc194255c38dabdb1e396d5e0b22e6f", 0,
        "0dbe8072173a2417ce9bb46fb686c9ab8fc194255c38dabdb1e396d5e0b22e6f"),
    "verify-island-matrix.py": ("a2f0b025aeb20536acadd574ec36a500141516a9cefe3f99e4b4478fa051be36", 0,
        "a2f0b025aeb20536acadd574ec36a500141516a9cefe3f99e4b4478fa051be36"),
    "verify-external-island-matrix.py": ("db313c16aea96a65b1d194de80a3562e9a73204efb845473c89cff4664844c65", 3,
        "83989b5ebf70ab983d8d908fc2c36bcf6072fddfa76a2e496b18e1fe4910f18f"),
    "verify-followup-matrix.py": ("6f7c540bc7703f25957be11566789e021eb438208942fc9c0d73c94579c14fa8", 3,
        "8ab256b87e9e2b59df9b40bbb5a7ee1e51d0c466e755612a8dc91dd8db50ad1b"),
}
PROPOSED_SHA = "9846bb4146523e0b5f65d01d8c491b3daef711c07aa7f5de397ecfd490e14cb5"


def require(value, message):
    if not value:
        raise ValueError(message)


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def git(*args):
    return subprocess.check_output(["git", *args], cwd=REPO)


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=True, sort_keys=True, indent=2) + "\n").encode()


def read_json(path):
    return json.loads(Path(path).read_bytes())


def write_json(path, value):
    Path(path).write_bytes(json_bytes(value))


def snapshot(paths):
    return {Path(path): digest(path) for path in paths}


def require_unchanged(bindings):
    require(all(path.is_file() and digest(path) == expected for path, expected in bindings.items()),
            "Release source/proof/native input changed during preparation")


def validate_authorization(value):
    require(isinstance(value, dict) and value.get("owner_approved") is True
            and value.get("owner_reply") == "כמובן", "Explicit direct owner approval is required")
    for key, expected in AUTH_PINS.items():
        require(type(value.get(key)) is type(expected) and value[key] == expected,
                "Owner approval is not pinned to this exact retained artifact: " + key)
    return value


def check_source():
    require(git("rev-parse", "HEAD:app").decode().strip() == APP_TREE
            and git("rev-parse", NATIVE_SOURCE + ":app").decode().strip() == APP_TREE,
            "Application differs from native/visually reviewed source")
    for args in (("diff", "--name-only", NATIVE_SOURCE, "HEAD", "--", *BUILD_INPUTS),
                 ("diff", "--name-only", "HEAD", "--", *BUILD_INPUTS),
                 ("diff", "--cached", "--name-only", "HEAD", "--", *BUILD_INPUTS)):
        require(not git(*args).strip(), "Application or root build inputs changed after review")
    return git("rev-parse", "HEAD").decode().strip()


def validate_archive(path, expected_size, expected_sha, extract_to=None):
    require(Path(path).stat().st_size == expected_size and digest(path) == expected_sha,
            "Official retained artifact size/hash mismatch: " + Path(path).name)
    with zipfile.ZipFile(path) as archive:
        items = archive.infolist()
        require(len(items) == len({item.filename for item in items}), "Duplicate ZIP members")
        for item in items:
            p = PurePosixPath(item.filename)
            require(not p.is_absolute() and ".." not in p.parts and "\\" not in item.filename
                    and stat.S_IFMT(item.external_attr >> 16) != stat.S_IFLNK,
                    "Unsafe retained ZIP member")
        require(archive.testzip() is None, "Retained ZIP CRC failed")
        if extract_to is not None:
            archive.extractall(extract_to)


def validate_native_members(archive_path, native_directory):
    native_directory = Path(native_directory)
    with zipfile.ZipFile(archive_path) as archive:
        expected = {item.filename: dict(sha256=hashlib.sha256(archive.read(item)).hexdigest(), bytes=item.file_size)
                    for item in archive.infolist() if not item.is_dir()}
    actual = {path.relative_to(native_directory).as_posix(): path
              for path in native_directory.rglob("*") if path.is_file()}
    require(set(actual) == set(expected), "Native extracted member set differs from pinned archive")
    for name, path in actual.items():
        require(not path.is_symlink() and path.stat().st_size == expected[name]["bytes"]
                and digest(path) == expected[name]["sha256"], "Native extracted member changed: " + name)
    return expected


def stage(output):
    output = Path(output).resolve()
    require(not output.exists(), "Stage output exists; never overwrite retained material")
    auth_path = REPO / AUTH_PATH
    bindings = snapshot((Path(__file__), auth_path))
    commit = check_source()
    authorization = validate_authorization(read_json(auth_path))
    auth_sha = digest(auth_path)
    # The export commit is an immutable copy of the official GitHub artifacts.
    try:
        git("cat-file", "-e", EXPORT + "^{commit}")
    except subprocess.CalledProcessError:
        subprocess.run(["git", "fetch", "--no-tags", "--depth=1", "origin", EXPORT], cwd=REPO, check=True)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="approved-012-stage-", dir=output.parent) as temporary:
        temp = Path(temporary)
        raw = temp / "artifacts"
        raw.mkdir()
        prefix = "native-review-export/" + str(RUN) + "/"
        for name, (size, sha) in ARTIFACTS.items():
            path = raw / name
            path.write_bytes(git("show", EXPORT + ":" + prefix + name))
            validate_archive(path, size, sha, temp / "native" if name == "native-failure-candidate.zip" else None)
        for name in ("provenance.json", "source-run.json"):
            (raw / name).write_bytes(git("show", EXPORT + ":" + prefix + name))
        provenance = read_json(raw / "provenance.json")
        require(provenance.get("repository") == "hadar1233hr-afk/D.A.-Caller"
                and provenance.get("source_sha") == NATIVE_SOURCE
                and provenance.get("source_run_id") == str(RUN)
                and provenance.get("conclusion") == "failure", "Historical artifact provenance changed")
        apk = temp / "native/app-debug.apk"
        require(digest(apk) == APK_SHA, "Native ZIP APK differs from reviewed binary")
        context = read_json(temp / "native/debug-signing-policy.json")
        require(context.get("apk_sha256") == APK_SHA and context.get("certificate_sha256") == CERT,
                "Native retained signing context differs")
        native_members = validate_native_members(raw / "native-failure-candidate.zip", temp / "native")
        result = dict(status="STAGED_EXACT_OWNER_APPROVED_012_NATIVE_ARTIFACT", publication_commit=commit,
                      authorization_sha256=auth_sha, owner_authorization=authorization,
                      native_source_sha=NATIVE_SOURCE, app_tree=APP_TREE, source_run_id=RUN,
                      raw_export_commit=EXPORT, apk_sha256=APK_SHA,
                      historical_workflow_conclusion="failure", helper_sha256=bindings[Path(__file__)],
                      native_members=native_members,
                      public_download_verification="PENDING_POST_PUBLICATION")
        write_json(temp / "stage.json", result)
        require_unchanged(bindings)
        require(check_source() == commit and digest(auth_path) == auth_sha, "Source or approval changed during staging")
        require(not output.exists(), "Stage destination appeared during preparation")
        temp.rename(output)
    return result


def validate_scope(scope, visual):
    require(isinstance(scope, dict) and isinstance(visual, dict), "Scope/visual report must be objects")
    require(scope.get("status") == "PASS_APPROVED_012_RELEASE_SCOPE_REVALIDATION"
            and scope.get("application_id") == "com.dacaller.app" and scope.get("version") == "0.12.0"
            and scope.get("app_tree") == APP_TREE and scope.get("native_source_sha") == NATIVE_SOURCE
            and scope.get("native_workflow_run_id") == RUN and scope.get("raw_export_commit") == EXPORT
            and scope.get("native_apk_sha256") == APK_SHA, "Complete approved release scope has not passed")
    for key, expected in (("total_required_png_files", 243), ("baseline_png_files", 233), ("additional_png_files", 10)):
        require(type(scope.get(key)) is int and scope[key] == expected, "Scope coverage count changed")
    require(scope.get("reviewed_png_sha256") == visual.get("reviewed_png_sha256")
            and len(scope["reviewed_png_sha256"]) == 243, "Scope does not bind all manually reviewed PNG bytes")
    for key, expected in (("authentic_unit_junit", 356), ("authentic_native_junit", 35)):
        item = scope.get(key, {})
        require(isinstance(item, dict), "JUnit report must be an object")
        hashes = item.get("files_sha256")
        require(type(item.get("tests")) is int and item["tests"] == expected
                and all(type(item.get(k)) is int and item[k] == 0 for k in ("failures", "errors", "skipped"))
                and isinstance(hashes, dict) and bool(hashes)
                and all(isinstance(name, str) and name.endswith(".xml") and isinstance(value, str)
                        and len(value) == 64 and all(c in "0123456789abcdef" for c in value)
                        for name, value in hashes.items()), "Authentic complete JUnit gate missing: " + key)
    require(isinstance(scope.get("functional_revalidation"), dict)
            and set(scope["functional_revalidation"]) == FUNCTIONAL,
            "Required scoped functional revalidation is missing")
    functional = scope["functional_revalidation"]
    for name, (original, replaced, adapted) in FUNCTIONAL_PINS.items():
        proof = functional[name]
        require(isinstance(proof, dict) and proof.get("original_sha256") == original
                and digest(REPO / "scripts" / name) == original
                and type(proof.get("replaced_total_count_literals")) is int
                and proof["replaced_total_count_literals"] == replaced
                and proof.get("scoped_source_sha256") == adapted
                and isinstance(proof.get("stdout"), str) and len(proof["stdout"]) > 8
                and proof["stdout"].startswith("Verified"),
                "Functional proof/source/count adaptation missing: " + name)
        if replaced:
            require(type(proof.get("approved_total_count")) is int and proof["approved_total_count"] == 243
                    and proof.get("all_other_native_assertions_unchanged") is True,
                    "Only the reviewed 243 total-count adaptation is allowed")
    deferred = functional["independent-reminder-swipe-contact-deferred-profile"]
    require(isinstance(deferred, dict) and deferred.get("source_sha256") == PROPOSED_SHA
            and digest(REPO / "docs/releases/0.12.0-proposed/verify-release-matrix.py") == PROPOSED_SHA
            and deferred.get("original_245_populated_business_verifier_executed") is False
            and isinstance(deferred.get("stdout"), str) and deferred["stdout"].startswith("Verified10 "),
            "Independent reminder/swipe/contact/deferred proof missing")
    for key, archive_name in (("ui", "ui.zip"), ("checks", "checks.zip"), ("apk", "native-failure-candidate.zip")):
        proof = scope.get("original_artifacts", {}).get(key, {})
        size, expected_sha = ARTIFACTS[archive_name]
        require(type(proof.get("bytes")) is int and proof["bytes"] == size
                and proof.get("sha256") == expected_sha and proof.get("crc") == "PASS"
                and type(proof.get("members")) is int and proof["members"] > 0,
                "Authenticated scoped archive proof missing: " + key)
    require(scope.get("original_workflow_conclusion") == "failure" and scope.get("fresh_emulator_execution") is False,
            "Historical native evidence must not be relabelled as a new successful run")


def validate_signing(signing):
    require(isinstance(signing, dict) and signing.get("publication_allowed") is True
            and signing.get("publication_exception") == "owner-approved-exact-0.12.0-preview"
            and signing.get("signing_gate_only") is True
            and signing.get("required_update_certificate_matches") is False
            and signing.get("durable_future_signing_verified") is False,
            "Exact approved signing gate is missing or claims unsupported compatibility")
    for key, expected in (("apk_sha256", APK_SHA), ("certificate_sha256", CERT),
                          ("application_id", "com.dacaller.app"), ("version_name", "0.12.0"),
                          ("version_code", 21), ("approved_app_tree", APP_TREE)):
        require(type(signing.get(key)) is type(expected) and signing[key] == expected,
                "Measured signing/embedded APK identity changed: " + key)


NOTES = """# DA CALLER 0.12.0

This is the exact Android 13 emulator-tested DEBUG APK, with semantic versioning
and no business directory. No publication-time build or re-signing occurred.

- Create a new contact from an unknown Recents number, with its exact number prefilled.
- Organized Nothing/Xiaomi call designs; larger digits across all designs and rounded, heavier Google digits.
- Genuine swipe to answer; incomplete/cancelled gestures do not answer.
- Optional local daily missed-callback reminder, plus modest Settings polish.
- Existing call island, DA CALLER missed-call notifications, changelog, offline dialing, contacts and archive remain.

Verification: 356 unique unit tests, 35 Android 13 emulator tests and all 243
actual screenshots reviewed. A separate owner-approved 0.12.0 scope revalidated
those exact retained files. The earlier workflow still has its historical
failure at the old 245-frame populated-business gate; it is not relabelled PASS.

The signer differs from installed 0.11.0-beta.9, so this APK cannot update that
installation. Its private signing key is unavailable; durable future signing
and a matching-key upgrade are not verified. Keep your existing installation
and data. Do not uninstall it: uninstalling deletes private archive/settings.
Physical Jelly Star/SIM/carrier/audible audio/Bluetooth/proximity/overnight Doze
and matching-key upgrade remain NOT TESTED. Hardware checks were waived as a
publication prerequisite, not claimed performed. This is a debug test release,
not a hardware-verified production build.
"""


def package(root):
    root = Path(root).resolve()
    require(not (root / "assets").exists() and not (root / "assets-manifest.json").exists(),
            "Release assets already exist; never overwrite")
    input_paths = [Path(__file__), REPO / AUTH_PATH, REPO / VISUAL_PATH,
                   root / "stage.json", root / "scope.json", root / "signing.json",
                   *(root / "artifacts" / name for name in ARTIFACTS),
                   *(path for path in (root / "native").rglob("*") if path.is_file()),
                   *(REPO / "scripts" / name for name in FUNCTIONAL_PINS),
                   REPO / "docs/releases/0.12.0-proposed/verify-release-matrix.py"]
    bindings = snapshot(input_paths)
    commit = check_source()
    authorization = validate_authorization(read_json(REPO / AUTH_PATH))
    staged = read_json(root / "stage.json")
    require(staged.get("publication_commit") == commit
            and staged.get("authorization_sha256") == digest(REPO / AUTH_PATH)
            and staged.get("helper_sha256") == digest(Path(__file__)), "Staged source/approval/helper changed")
    for name, (size, sha) in ARTIFACTS.items():
        validate_archive(root / "artifacts" / name, size, sha)
    native_members = validate_native_members(root / "artifacts/native-failure-candidate.zip", root / "native")
    require(staged.get("native_members") == native_members, "Staged native-member proof changed")
    require(digest(root / "native/app-debug.apk") == APK_SHA, "Retained native APK changed before packaging")
    require(digest(REPO / VISUAL_PATH) == VISUAL_SHA, "Manually reviewed visual observation changed")
    visual = read_json(REPO / VISUAL_PATH)
    scope, signing = read_json(root / "scope.json"), read_json(root / "signing.json")
    validate_scope(scope, visual)
    validate_signing(signing)
    result = dict(status="ALL_OWNER_APPROVED_012_PREPUBLICATION_GATES_PASS", publication_commit=commit,
        application_id="com.dacaller.app", version_name="0.12.0", version_code=21,
        apk_sha256=APK_SHA, certificate_sha256=CERT, native_source_sha=NATIVE_SOURCE,
        source_run_id=RUN, app_tree=APP_TREE, raw_export_commit=EXPORT,
        owner_authorization=authorization, release_scope=scope, measured_signing=signing,
        historical_full_visual_observation=visual, no_build_or_resigning_performed=True,
        public_download_verification="PENDING_POST_PUBLICATION",
        observed_at_utc=dt.datetime.now(dt.timezone.utc).isoformat())
    with tempfile.TemporaryDirectory(prefix="approved-012-assets-", dir=root) as temporary:
        assets = Path(temporary)
        apk_name = "DA-CALLER-v0.12.0.apk"
        shutil.copyfile(root / "native/app-debug.apk", assets / apk_name)
        (assets / (apk_name + ".sha256")).write_text(APK_SHA + "  " + apk_name + "\n")
        for source, target in ((root / "artifacts/ui.zip", "DA-CALLER-v0.12.0-Android13-evidence.zip"),
                               (root / "native/debug-signature.txt", "DA-CALLER-v0.12.0-signature.txt"),
                               (root / "native/apk-metadata.txt", "DA-CALLER-v0.12.0-metadata.txt")):
            shutil.copyfile(source, assets / target)
        write_json(assets / "DA-CALLER-v0.12.0-verification.json", result)
        require(digest(assets / apk_name) == APK_SHA, "Packaged APK is not the exact retained native binary")
        require(digest(assets / "DA-CALLER-v0.12.0-Android13-evidence.zip") == ARTIFACTS["ui.zip"][1],
                "Packaged UI evidence differs from authenticated native ZIP")
        files = {path.name: dict(sha256=digest(path), bytes=path.stat().st_size) for path in sorted(assets.iterdir())}
        require_unchanged(bindings)
        require(check_source() == commit and digest(REPO / AUTH_PATH) == staged["authorization_sha256"],
                "Source/approval changed during release packaging")
        assets.rename(root / "assets")
    (root / "release-notes.md").write_text(NOTES)
    manifest = dict(files=files, publication_commit=commit, version_name="0.12.0", tag_name="v0.12.0",
                    public_download_verification="PENDING_POST_PUBLICATION")
    write_json(root / "assets-manifest.json", manifest)
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    first = commands.add_parser("stage")
    first.add_argument("--output", type=Path, required=True)
    second = commands.add_parser("package")
    second.add_argument("--root", type=Path, required=True)
    args = parser.parse_args()
    result = stage(args.output) if args.command == "stage" else package(args.root)
    print(json.dumps({key: result[key] for key in ("status", "publication_commit") if key in result}))


if __name__ == "__main__":
    main()
