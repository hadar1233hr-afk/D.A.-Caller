#!/usr/bin/env python3
"""Publish-proof preparation for ONE owner-approved retained 0.13.0 APK.

No build, key generation, signing, secret handling, or release mutation exists in
this helper. The normal signing policy and all 0.12.0 machinery remain unchanged.
API JSON inputs must be downloaded from the authenticated, fixed GitHub endpoints
by the workflow; their contents never substitute for the hardcoded binary hashes.
"""
from __future__ import annotations

import argparse
import collections
import datetime as dt
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

REPO = Path(__file__).resolve().parents[1]
REPOSITORY = "hadar1233hr-afk/D.A.-Caller"
REPOSITORY_ID = 1429912387
BRANCH = "codex/da-caller-dynamic-island-20261027"
NATIVE_SOURCE = "7e039daaf2b43716ed2a2b3db1df0b04a8647fcd"
APP_TREE = "96aa47e9152edc931e4830dd3dbe65e3a09418df"
RUN = 44521985299
APK_BYTES = 1357445
APK_SHA = "e29b4c6beeb97ccb4ab1b306c83438bbd30eead7d169fda95e722ab84d5ae5f9"
CERT = "c42aa0e8e318e965444ec99adc4cc6645e677d13b2b8ce2d4e67b54b1e933376"
AUTH_PATH = "docs/releases/0.13.0-publication-authorization.json"
APPROVAL_EVIDENCE = "docs/releases/0.13.0-owner-approval-receipt.json"
APPROVAL_EVIDENCE_SHA = "a0119e54d8932fe371baa3ac311eb2a02e2864bc783518dbbfb231c172be8a40"
APPROVAL_COMMIT = "b38fb48a80ac47a3e7405f2ba96b40f3ae29979d"
APPROVAL_DOCUMENT_SHA = "b846e18874c3f82c01a7c3a86fae9dcf5fb2a8fa02d23f4b2758f8527c3f20e5"
EVIDENCE_PATH = "docs/evidence/interface-island-archive-0.13.0/final-native-ci"
VISUAL_SHA = "c31fb98ba97c69ce88d51e41e0a98d6e2d5a6706ff3d931660db396365e404e9"
SOURCE_PATH = "docs/evidence/business-acquisition/owner-261103/verified-geometry-source-and-dataset.zip"
SOURCE_BYTES = 3169035
SOURCE_SHA = "5d6358c7d8ebc7b3be51717c3829a1b3c56e0f1aacf90bbf54fa1a04f71a5224"
ASSETS = {
    "assets/business-directory/israel-businesses.sqlite": "cbc7dc7b41c3fa9335cbf37907243681820fe23107e40541efb306dc1b566af3",
    "assets/business-directory/manifest.json": "fec348522615f62d36fa62ee65ff317340ea4806a85f15c457daa30378805cfa",
}
ARTIFACTS = {
    "tested-apk.zip": (14528288719, 1332503, "e44ee3214c43272bf65b6e07517b721d4d1047cbf6331ca5c2675ef22b11b58e"),
    "ui.zip": (14528238780, 17134697, "4ee0610b0b8fd57af1f72f7434fa7be3380546f86fabf8743ba63d8e41458eda"),
    "checks.zip": (14528790986, 163499, "afc4d05f503f7ef2d4193636a64074ec306521a5006e8659e833383b483e858c"),
}
VERIFIERS = {
    "verify-ui-matrix.py": "e3fd18f544b028ffeddaddee272b2f4e7aefa2cc47f5d2d90fac5d9580c9dab2",
    "verify-personalization-matrix.py": "0fa3106ef1832cd5fb7fd8df167c56e19c70b5a034ae1ffc20636659220d2825",
    "verify-call-integration-matrix.py": "0dbe8072173a2417ce9bb46fb686c9ab8fc194255c38dabdb1e396d5e0b22e6f",
    "verify-island-matrix.py": "a2f0b025aeb20536acadd574ec36a500141516a9cefe3f99e4b4478fa051be36",
    "verify-external-island-matrix.py": "db313c16aea96a65b1d194de80a3562e9a73204efb845473c89cff4664844c65",
    "verify-followup-matrix.py": "6f7c540bc7703f25957be11566789e021eb438208942fc9c0d73c94579c14fa8",
    "verify-callback-business-matrix.py": "18c3f965bd85cb4ed68ace00f83e1384b8b9416dc07a4323c5f3bfec4c0c70f2",
    "verify-modern-features-native.py": "a615b533729f61484cca04fefc07d43f26efd87c29a33fdc5e186343c3482b57",
}
REPORTS = ("matrix-verification.json", "personalization-verification.json", "call-integration-verification.json",
           "island-verification.json", "external-island-verification.json", "followup-verification.json",
           "callback-business-verification.json", "modern-features-verification.json")
PROTECTED_PATHS = ("app", "build.gradle", "settings.gradle", "gradle.properties", "gradle", "gradlew", "gradlew.bat",
    ".github/workflows/android.yml", "scripts/run-android-ui.sh", "scripts/emulator-call-driver.py",
    *("scripts/" + name for name in VERIFIERS), "scripts/da_caller_ci_signing.py", "scripts/da_caller_signing.py",
    "docs/DA_CALLER_SIGNING_POLICY.json", ".github/workflows/release-012-approved.yml",
    "scripts/prepare-approved-012-release.py", "scripts/verify-release-012-matrix.py",
    "docs/releases/0.12.0-publication-authorization.json", "docs/releases/0.12.0-publication-approval.md")
AUTH_PINS = dict(format="DA_CALLER_BOUNDED_PUBLICATION_AUTHORIZATION", schema_version=1,
    repository=REPOSITORY, pull_request=5, approval_document="docs/releases/0.13.0-publication-approval.md",
    approval_question="מאשר את חריג החתימה והפרסום לקובץ המדויק של 0.13.0 בלבד?",
    approval_evidence=APPROVAL_EVIDENCE, approval_evidence_sha256=APPROVAL_EVIDENCE_SHA,
    approval_document_at_request_commit=APPROVAL_COMMIT,
    approval_document_at_request_sha256=APPROVAL_DOCUMENT_SHA, durable_signing_setup_waived=False,
    application_id="com.dacaller.app", version_name="0.13.0", version_code=22, apk_bytes=APK_BYTES,
    apk_sha256=APK_SHA, certificate_sha256=CERT, app_tree=APP_TREE, native_source_sha=NATIVE_SOURCE,
    source_run_id=RUN, native_apk_artifact_id=14528288719, native_ui_artifact_id=14528238780,
    native_checks_artifact_id=14528790986, release_scope_pngs=269, authentic_unit_cases=460,
    authentic_unit_classes=44, authentic_native_cases=40, rebuild_allowed=False, resigning_allowed=False,
    business_data_in_apk=True, corresponding_source_bytes=SOURCE_BYTES, corresponding_source_sha256=SOURCE_SHA,
    future_default_signing_guards_unchanged=True, legacy_245_business_gate_unchanged=True,
    original_seven_and_additive_gates_unchanged=True, physical_jelly_star_prerequisite_waived=True,
    physical_device_tested=False, matching_key_upgrade_tested=False, wide_native_status_lane_tested=False,
    public_download_byte_equality_required=True)
ASSET_NAMES = {
    "apk": "DA-CALLER-v0.13.0.apk", "sha": "DA-CALLER-v0.13.0.apk.sha256",
    "ui": "DA-CALLER-v0.13.0-Android13-evidence.zip", "source": "DA-CALLER-v0.13.0-business-source.zip",
    "signature": "DA-CALLER-v0.13.0-signature.txt", "metadata": "DA-CALLER-v0.13.0-metadata.txt",
    "verification": "DA-CALLER-v0.13.0-verification.json", "checks": "DA-CALLER-v0.13.0-CI-checks.zip",
}
LIMITS = ("Ephemeral DEBUG test signer differs from beta.9 and 0.12.0; cannot update those installations in place. "
    "Keep existing installation/data; never uninstall to install this APK. Durable owner-controlled signing/private backup/CI secrets remain unresolved. "
    "No physical Jelly Star/carrier/audio/Bluetooth/proximity/overnight Doze/full TalkBack/matching-key upgrade verification. "
    "Physical testing was waived as a publication prerequisite only. Fixed320dp/font1.3 uses readable protected below-bar fallback; "
    "compatible wide native/hardware status placement remains NOT TESTED.")
NOTES = """# DA CALLER 0.13.0

The exact retained Android 13 emulator-tested DEBUG APK; no publication-time build or re-signing.
Inline Recents actions, optional archive backup, adjustable draggable/hideable call island,
wine controls, updated summary/favorites, bottom dialpad, rounded Hebrew font and nine icons.

460 unique host tests in44 classes,40 native cases and269 actual screenshots reviewed269PASS.
All seven original gates and the additive24-frame gate were replayed unchanged on retained evidence.
This replay is not a fresh emulator run.

Offline business snapshot2026-11-03:2,720records/2,660source objects/2,616phone identities.
Partial coverage; current ownership/reachability unverified. © OpenStreetMap contributors,
ODbL1.0: https://opendatacommons.org/licenses/odbl/1-0/ . Corresponding raw/source,
history and reproducible derivative materials are included in the business-source ZIP.
Private contacts/archive backups and private keys are not release assets.

""" + LIMITS + "\n"


def require(value, message):
    if not value:
        raise ValueError(message)


def typed(value, expected):
    return type(value) is type(expected) and value == expected


def sha(data):
    return hashlib.sha256(data).hexdigest()


def digest(path):
    require(Path(path).is_file() and not Path(path).is_symlink(), "Missing or symlink input: " + str(path))
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "Duplicate JSON key: " + key)
        result[key] = value
    return result


def read_json(path):
    return json.loads(Path(path).read_bytes(), object_pairs_hook=unique_object)


def write_json(path, value):
    Path(path).write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n")


def snapshot(paths):
    return {str(Path(path).resolve()): digest(path) for path in paths}


def unchanged(bindings):
    require(all(digest(path) == expected for path, expected in bindings.items()), "Input changed during publication preparation")


def git(*args):
    return subprocess.check_output(["git", *args], cwd=REPO)


def validate_authorization(value):
    require(isinstance(value, dict) and value.get("owner_approved") is True
            and value.get("owner_reply") == "אני מאשר את הAPK", "Explicit exact-candidate owner approval required")
    for key, expected in AUTH_PINS.items():
        require(typed(value.get(key), expected), "Owner authorization pin differs: " + key)
    return value


def validate_approval_evidence(authorization):
    require(digest(REPO / APPROVAL_EVIDENCE) == APPROVAL_EVIDENCE_SHA, "Direct owner receipt changed/missing")
    receipt = read_json(REPO / APPROVAL_EVIDENCE)
    require(receipt.get("format") == "DA_CALLER_DIRECT_OWNER_APPROVAL_RECEIPT"
            and typed(receipt.get("schema_version"), 1)
            and receipt.get("approval_kind") == "explicit_direct_reply_to_previously_delivered_exact_candidate_question"
            and receipt.get("owner_reply") == authorization["owner_reply"], "Direct owner receipt is not exact approval")
    for key in ("approval_question", "approval_document_at_request_commit", "approval_document_at_request_sha256",
                "repository", "pull_request", "application_id", "version_name", "version_code", "apk_bytes",
                "apk_sha256", "certificate_sha256", "native_source_sha", "app_tree", "rebuild_allowed",
                "resigning_allowed", "future_default_signing_guards_unchanged", "durable_signing_setup_waived"):
        require(typed(receipt.get(key), authorization[key]), "Receipt/authorization boundary differs: " + key)
    require(typed(receipt.get("native_workflow_run_id"), RUN)
            and receipt.get("immutable_delivered_012_unchanged") is True
            and receipt.get("hardware_or_matching_key_upgrade_verified") is False
            and receipt.get("publication_already_executed") is False, "Receipt validation limits differ")
    document = authorization["approval_document"]
    require(receipt.get("approval_link") == "https://github.com/" + REPOSITORY + "/blob/" + APPROVAL_COMMIT + "/" + document
            and sha(git("show", APPROVAL_COMMIT + ":" + document)) == APPROVAL_DOCUMENT_SHA,
            "Immutable document linked to actual approval differs")


def check_source():
    require(git("rev-parse", "HEAD:app").decode().strip() == APP_TREE
            and git("rev-parse", NATIVE_SOURCE + ":app").decode().strip() == APP_TREE, "Reviewed app tree differs")
    require(not git("diff", "--name-only", NATIVE_SOURCE, "HEAD", "--", *PROTECTED_PATHS).strip(),
            "Build/native producer/default signing/0.12.0 inputs changed after native review")
    require(not git("status", "--porcelain=v1", "--untracked-files=all", "--", *PROTECTED_PATHS).strip(),
            "Dirty or untracked build/native producer/default signing/0.12.0 input")
    for name, expected in VERIFIERS.items():
        require(digest(REPO / "scripts" / name) == expected, "Original verifier changed: " + name)
    return git("rev-parse", "HEAD").decode().strip()


def validate_provenance(run, artifacts):
    require(isinstance(run, dict) and isinstance(artifacts, dict), "API provenance objects required")
    for key, expected in dict(id=RUN, head_sha=NATIVE_SOURCE, head_branch=BRANCH, path=".github/workflows/android.yml",
                               status="completed", conclusion="success", event="pull_request").items():
        require(typed(run.get(key), expected), "Native run provenance differs: " + key)
    for key in ("repository", "head_repository"):
        item = run.get(key, {})
        require(isinstance(item, dict) and item.get("full_name") == REPOSITORY
                and typed(item.get("id"), REPOSITORY_ID), "Run repository differs")
    entries = artifacts.get("artifacts")
    require(isinstance(entries, list) and bool(entries), "Artifact API list missing")
    ids = [item.get("id") for item in entries if isinstance(item, dict)]
    require(len(ids) == len(entries) == len(set(ids)) and all(type(i) is int for i in ids), "Duplicate/invalid artifact IDs")
    by_id = {item["id"]: item for item in entries}
    for name, (identity, size, expected_sha) in ARTIFACTS.items():
        item = by_id.get(identity, {})
        prefix = "https://api.github.com/repos/" + REPOSITORY + "/actions/artifacts/" + str(identity)
        require(typed(item.get("size_in_bytes"), size) and item.get("digest") == "sha256:" + expected_sha
                and item.get("expired") is False and item.get("url") == prefix
                and item.get("archive_download_url") == prefix + "/zip", "Artifact API pin differs: " + name)
        workflow = item.get("workflow_run", {})
        for key, expected in dict(id=RUN, repository_id=REPOSITORY_ID, head_repository_id=REPOSITORY_ID,
                                  head_sha=NATIVE_SOURCE, head_branch=BRANCH).items():
            require(typed(workflow.get(key), expected), "Artifact native-source provenance differs: " + name + ":" + key)
    return {name: dict(id=i, bytes=n, sha256=h) for name, (i, n, h) in ARTIFACTS.items()}


def archive_manifest(path, size=None, expected_sha=None, extract_to=None):
    require(size is None or typed(Path(path).stat().st_size, size), "Archive size differs")
    require(expected_sha is None or digest(path) == expected_sha, "Archive digest differs")
    with zipfile.ZipFile(path) as archive:
        items = archive.infolist()
        require(items and len(items) == len({i.filename for i in items}), "Empty/duplicate ZIP members")
        require(sum(i.file_size for i in items) <= 256 * 1024 * 1024, "Unbounded ZIP contents")
        files, all_names = {}, set()
        for item in items:
            name = item.filename.rstrip("/")
            part = PurePosixPath(name)
            require(name and name == part.as_posix() and not part.is_absolute() and ".." not in part.parts
                    and "\\" not in name and ":" not in name and "\x00" not in name
                    and stat.S_IFMT(item.external_attr >> 16) in (0, stat.S_IFREG, stat.S_IFDIR)
                    and not (item.flag_bits & 1), "Unsafe ZIP member")
            require(name not in all_names, "ZIP file/directory alias")
            all_names.add(name)
            if not item.is_dir():
                files[name] = dict(bytes=item.file_size, sha256=sha(archive.read(item)))
        for name in all_names:
            require(not any(p.as_posix() in files for p in PurePosixPath(name).parents if p.as_posix() != "."),
                    "ZIP file/directory collision")
        require(archive.testzip() is None, "ZIP CRC failure")
        if extract_to is not None:
            archive.extractall(extract_to)
    return files


def tree_manifest(root):
    root = Path(root)
    require(root.is_dir() and not root.is_symlink(), "Extracted input directory missing")
    result = {}
    for path in root.rglob("*"):
        require(not path.is_symlink(), "Extracted input contains symlink")
        if path.is_file():
            result[path.relative_to(root).as_posix()] = dict(bytes=path.stat().st_size, sha256=digest(path))
    return result


def audit_junit(root, count, classes=None):
    paths = sorted(Path(root).rglob("*.xml"))
    require(paths, "Authentic JUnit files missing")
    identities, classnames, files = set(), set(), {}
    for path in paths:
        document = ET.fromstring(path.read_bytes())
        require(document.tag in ("testsuite", "testsuites"), "Unexpected JUnit root")
        require(not any(list(document.iter(tag)) for tag in ("failure", "error", "skipped")), "Failed/skipped JUnit case")
        for suite in document.iter():
            if suite.tag not in ("testsuite", "testsuites"):
                continue
            actual = len(list(suite.iter("testcase")))
            require(actual > 0, "Empty JUnit suite")
            for key in ("failures", "errors", "skipped"):
                if key in suite.attrib:
                    require(suite.get(key) == "0", "Nonzero JUnit counter")
            if "tests" in suite.attrib:
                require(re.fullmatch(r"[0-9]+", suite.get("tests", "")) and int(suite.get("tests")) == actual,
                        "JUnit test counter differs")
        for case in document.iter("testcase"):
            identity = (case.get("classname"), case.get("name"))
            require(all(isinstance(v, str) and v.strip() for v in identity) and identity not in identities,
                    "Missing/duplicate JUnit identity")
            identities.add(identity)
            classnames.add(identity[0])
        files[path.relative_to(root).as_posix()] = digest(path)
    require(len(identities) == count and (classes is None or len(classnames) == classes), "Authentic JUnit count/classes differ")
    return dict(tests=len(identities), classes=len(classnames), failures=0, errors=0, skipped=0, files_sha256=files)


def validate_visual(ui, evidence):
    visual_path = evidence / "visual-summary.json"
    require(digest(visual_path) == VISUAL_SHA, "Actual visual review summary changed")
    visual = read_json(visual_path)
    for key, expected in dict(source_sha=NATIVE_SOURCE, app_tree=APP_TREE, run_id=RUN,
            actually_reviewed_unique_frames=269, pass_=269, fail=0, unreviewed=0,
            all_current_raw_hashes_verified=True, software_visual_scope_approved=True).items():
        key = "pass" if key == "pass_" else key
        require(typed(visual.get(key), expected), "Actual visual review pin differs: " + key)
    frames, reports = visual.get("frames"), visual.get("reports")
    require(isinstance(frames, list) and len(frames) == 269 and isinstance(reports, list) and len(reports) == 6,
            "Full visual frame/report union missing")
    names, hashes, report_names, assigned = set(), {}, set(), collections.Counter()
    for item in reports:
        name = item.get("file", "")
        require(isinstance(name, str) and name == Path(name).name and name not in report_names, "Duplicate/unsafe visual report")
        require(digest(evidence / ("visual-" + name)) == item.get("sha256"), "Visual reviewer evidence changed")
        require(type(item.get("frames")) is int and item["frames"] > 0, "Invalid visual reviewer count")
        report_names.add(name)
    for item in frames:
        name = item.get("filename")
        require(isinstance(name, str) and name.endswith(".png") and name == Path(name).name and name not in names
                and item.get("status") == "PASS" and item.get("report") in report_names, "Duplicate/incomplete visual frame")
        require(re.fullmatch(r"[0-9a-f]{64}", str(item.get("sha256", ""))), "Invalid reviewed image hash")
        names.add(name)
        hashes[name] = item["sha256"]
        assigned[item["report"]] += 1
    require(all(assigned[item["file"]] == item["frames"] for item in reports), "Visual group union/count differs")
    images = [p for p in ui.rglob("*.png") if p.is_file()]
    require(len(images) == len({p.name for p in images}) == 269 and {p.name for p in images} == names,
            "Actual native PNG union differs")
    require(all(digest(path) == hashes[path.name] for path in images), "Actual PNG differs from viewed bytes")
    return dict(summary_sha256=VISUAL_SHA, reviewed_png_sha256=hashes, reports=reports,
                actually_reviewed=269, passed=269, failed=0, unreviewed=0)


def tool(command):
    result = subprocess.run(command, check=True, capture_output=True, text=True, timeout=90)
    return result.stdout


def verify_apk(apk, context, apksigner, aapt):
    require(apk.stat().st_size == APK_BYTES and digest(apk) == APK_SHA, "APK differs from exact retained native binary")
    require(isinstance(context, dict) and typed(context.get("schema_version"), 1)
            and context.get("mode") == "ephemeral-test-only" and context.get("apk_sha256") == APK_SHA
            and context.get("certificate_sha256") == CERT and context.get("publication_allowed") is False
            and context.get("required_update_certificate_matches") is False, "Original native signing context differs")
    signature = tool([apksigner, "verify", "--verbose", "--print-certs", str(apk)])
    signers = re.findall(r"Signer #([0-9]+) certificate SHA-256 digest:\s*([a-fA-F0-9:]+)", signature)
    require(len(signers) == 1 and signers[0][0] == "1" and signers[0][1].replace(":", "").lower() == CERT,
            "APK cryptographic signer differs")
    metadata = tool([aapt, "dump", "badging", str(apk)])
    packages = re.findall(r"^package:\s+(.+)$", metadata, re.MULTILINE)
    require(len(packages) == 1, "Embedded package metadata missing/ambiguous")
    for key, expected in (("name", "com.dacaller.app"), ("versionName", "0.13.0"), ("versionCode", "22")):
        require(re.findall(r"(?:^|\s)" + key + r"='([^']*)'", packages[0]) == [expected], "Embedded APK identity differs")
    require("android.permission.INTERNET" not in metadata, "APK unexpectedly requests INTERNET")
    archive_manifest(apk)
    with zipfile.ZipFile(apk) as archive:
        for name, expected in ASSETS.items():
            require(sha(archive.read(name)) == expected, "Frozen APK business asset differs")
    require(digest(apk) == APK_SHA, "APK changed during signature/metadata verification")
    return dict(application_id="com.dacaller.app", version_name="0.13.0", version_code=22,
                apk_bytes=APK_BYTES, apk_sha256=APK_SHA, certificate_sha256=CERT,
                publication_allowed=True, publication_exception="owner-approved-exact-0.13.0-preview",
                signing_gate_only=True, required_update_certificate_matches=False,
                durable_future_signing_verified=False, no_internet_permission=True, frozen_assets_match=ASSETS)


def validate_source_zip(path):
    members = archive_manifest(path, SOURCE_BYTES, SOURCE_SHA)
    with zipfile.ZipFile(path) as archive:
        for name, expected in ASSETS.items():
            member = "app-" + name
            require(sha(archive.read(member)) == expected, "Corresponding-source DB/manifest differs from APK")
        for name in ("app-assets/business-directory/NOTICE.txt", "redistribution/LICENSE-NOTICE.txt"):
            text = archive.read(name).decode()
            require("OpenStreetMap" in text and ("ODbL" in text or "opendatacommons.org/licenses/odbl" in text),
                    "Corresponding-source attribution/license missing")
    return dict(bytes=SOURCE_BYTES, sha256=SOURCE_SHA, members=members, crc="PASS", frozen_assets_match=ASSETS)


def replay_gates(ui, temporary):
    original = temporary / "original-source"
    subprocess.run(["git", "clone", "--quiet", "--shared", "--no-checkout", "--no-hardlinks", str(REPO), str(original)], check=True)
    subprocess.run(["git", "checkout", "--quiet", "--detach", NATIVE_SOURCE], cwd=original, check=True)
    scratch = temporary / "replayed-ui"
    shutil.copytree(ui, scratch)
    environment = dict(os.environ, GITHUB_RUN_ID=str(RUN))
    results = {}
    baseline, modern = scratch / "ui-snapshots", scratch / "ui-modern-snapshots"
    for index, (name, expected) in enumerate(VERIFIERS.items()):
        path = original / "scripts" / name
        require(digest(path) == expected, "Detached native verifier bytes differ")
        args = [sys.executable, str(path), str(baseline)]
        if index == 7:
            args += [str(modern), str(scratch / "outputs/androidTest-results")]
        process = subprocess.run(args, cwd=original, env=environment, capture_output=True, text=True, timeout=180)
        require(process.returncode == 0 and process.stdout.startswith("Verified"),
                "Untouched functional verifier failed: " + name + "\n" + process.stderr[-2000:])
        report_name = REPORTS[index]
        retained = read_json((ui / ("ui-modern-snapshots" if index == 7 else "ui-snapshots")) / report_name)
        measured = read_json((modern if index == 7 else baseline) / report_name)
        require(measured == retained, "Replayed functional proof differs from retained source/run/native hashes: " + name)
        require(measured.get("source_sha") == NATIVE_SOURCE and measured.get("app_tree") == APP_TREE
                and measured.get("workflow_run_id") == str(RUN), "Functional report source binding differs")
        results[name] = dict(source_sha256=expected, report=report_name, retained_report_sha256=digest(
            (ui / ("ui-modern-snapshots" if index == 7 else "ui-snapshots")) / report_name),
            canonical_report_sha256=sha(json.dumps(measured, sort_keys=True, ensure_ascii=False).encode()),
            stdout=process.stdout.strip(), all_native_assertions_unchanged=True)
    return results


def input_paths(artifacts):
    evidence = REPO / EVIDENCE_PATH
    return [Path(__file__), REPO / AUTH_PATH, REPO / APPROVAL_EVIDENCE, REPO / SOURCE_PATH,
            *(evidence / name for name in ("visual-summary.json", "visual-call-a.json", "visual-call-b.json",
              "visual-island.json", "visual-other-a.json", "visual-other-b.json", "visual-modern-root.json")),
            *(artifacts / name for name in (*ARTIFACTS, "source-run.json", "artifacts.json")),
            *(REPO / path for path in git("ls-files", "-z", "--", *PROTECTED_PATHS).decode().split("\0") if path)]


def stage(artifacts, output, apksigner="apksigner", aapt="aapt"):
    artifacts, output = Path(artifacts).resolve(), Path(output).resolve()
    require(not output.exists(), "Stage exists; never overwrite")
    commit = check_source()
    authorization = validate_authorization(read_json(REPO / AUTH_PATH))
    validate_approval_evidence(authorization)
    bindings = snapshot(input_paths(artifacts))
    provenance = validate_provenance(read_json(artifacts / "source-run.json"), read_json(artifacts / "artifacts.json"))
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="approved-013-stage-", dir=output.parent) as directory:
        temp = Path(directory)
        raw = temp / "artifacts"
        raw.mkdir()
        members = {}
        for name, (_, size, expected) in ARTIFACTS.items():
            shutil.copyfile(artifacts / name, raw / name)
            members[name] = archive_manifest(raw / name, size, expected, temp / name.removesuffix(".zip"))
        for name in ("source-run.json", "artifacts.json"):
            shutil.copyfile(artifacts / name, raw / name)
        source = temp / "corresponding-source.zip"
        shutil.copyfile(REPO / SOURCE_PATH, source)
        source_proof = validate_source_zip(source)
        native = temp / "tested-apk"
        apk = native / "outputs/apk/debug/app-debug.apk"
        signing = verify_apk(apk, read_json(native / "native-tested/debug-signing-policy.json"), apksigner, aapt)
        host = audit_junit(temp / "checks/test-results", 460, 44)
        native_cases = audit_junit(temp / "ui/outputs/androidTest-results", 40)
        visual = validate_visual(temp / "ui", REPO / EVIDENCE_PATH)
        replay = temp / "scratch"
        replay.mkdir()
        gates = replay_gates(temp / "ui", replay)
        shutil.rmtree(replay)
        for name, expected in members.items():
            require(tree_manifest(temp / name.removesuffix(".zip")) == expected, "Retained extracted evidence mutated")
        unchanged(bindings)
        require(check_source() == commit, "Source changed during staging")
        result = dict(status="ALL_OWNER_APPROVED_013_PREPUBLICATION_GATES_PASS", publication_commit=commit,
            native_source_sha=NATIVE_SOURCE, app_tree=APP_TREE, source_run_id=RUN,
            owner_authorization=authorization, authorization_sha256=digest(REPO / AUTH_PATH),
            helper_sha256=digest(Path(__file__)), authenticated_artifacts=provenance, original_members=members,
            authentic_unit_junit=host, authentic_native_junit=native_cases, visual_review=visual,
            functional_revalidation=gates, measured_signing=signing, corresponding_source=source_proof,
            no_build_or_resigning_performed=True, fresh_emulator_execution=False,
            public_download_verification="PENDING_POST_PUBLICATION", limits=LIMITS,
            observed_at_utc=dt.datetime.now(dt.timezone.utc).isoformat())
        write_json(temp / "stage.json", result)
        require(not output.exists(), "Stage destination appeared during staging")
        temp.rename(output)
    return result


def validate_stage(root):
    result = read_json(root / "stage.json")
    validate_approval_evidence(validate_authorization(read_json(REPO / AUTH_PATH)))
    require(result.get("status") == "ALL_OWNER_APPROVED_013_PREPUBLICATION_GATES_PASS"
            and result.get("publication_commit") == check_source()
            and result.get("helper_sha256") == digest(Path(__file__))
            and result.get("authorization_sha256") == digest(REPO / AUTH_PATH)
            and result.get("owner_authorization") == validate_authorization(read_json(REPO / AUTH_PATH)),
            "Staged helper/source/authorization differs")
    for name, (_, size, expected) in ARTIFACTS.items():
        manifest = archive_manifest(root / "artifacts" / name, size, expected)
        require(manifest == tree_manifest(root / name.removesuffix(".zip")) == result.get("original_members", {}).get(name),
                "Staged retained native/UI/checks evidence changed")
    validate_provenance(read_json(root / "artifacts/source-run.json"), read_json(root / "artifacts/artifacts.json"))
    require(validate_source_zip(root / "corresponding-source.zip") == result.get("corresponding_source"), "Staged source proof changed")
    require(validate_visual(root / "ui", REPO / EVIDENCE_PATH) == result.get("visual_review"), "Staged visual proof changed")
    require(audit_junit(root / "checks/test-results", 460, 44) == result.get("authentic_unit_junit")
            and audit_junit(root / "ui/outputs/androidTest-results", 40) == result.get("authentic_native_junit"), "Staged JUnit proof changed")
    gates = result.get("functional_revalidation", {})
    require(set(gates) == set(VERIFIERS), "Required functional gate missing")
    for index, (name, expected) in enumerate(VERIFIERS.items()):
        item = gates[name]
        report_path = root / "ui" / ("ui-modern-snapshots" if index == 7 else "ui-snapshots") / REPORTS[index]
        require(item.get("source_sha256") == expected and item.get("retained_report_sha256") == digest(report_path)
                and item.get("canonical_report_sha256") == sha(json.dumps(read_json(report_path), sort_keys=True, ensure_ascii=False).encode())
                and item.get("all_native_assertions_unchanged") is True and str(item.get("stdout", "")).startswith("Verified"),
                "Staged functional evidence changed")
    measured = result.get("measured_signing", {})
    require(measured.get("publication_allowed") is True and measured.get("signing_gate_only") is True
            and measured.get("publication_exception") == "owner-approved-exact-0.13.0-preview"
            and measured.get("required_update_certificate_matches") is False
            and measured.get("durable_future_signing_verified") is False,
            "Staged exact signing exception missing/overstated")
    for key in ("application_id", "version_name", "version_code", "apk_bytes", "apk_sha256", "certificate_sha256"):
        require(typed(measured.get(key), AUTH_PINS[key]), "Staged signing identity changed")
    require(result.get("no_build_or_resigning_performed") is True and result.get("fresh_emulator_execution") is False
            and result.get("public_download_verification") == "PENDING_POST_PUBLICATION", "Staged validation limits overstated")
    return result


def package(root):
    root = Path(root).resolve()
    require(not any((root / name).exists() for name in ("assets", "assets-manifest.json", "release-notes.md")), "Package exists; never overwrite")
    stage_proof = validate_stage(root)
    bindings = snapshot([*input_paths(root / "artifacts"), root / "stage.json", root / "corresponding-source.zip",
                         *(p for directory in ("ui", "checks", "tested-apk") for p in (root / directory).rglob("*") if p.is_file())])
    with tempfile.TemporaryDirectory(prefix="approved-013-package-", dir=root) as temporary:
        temp = Path(temporary)
        assets = temp / "assets"
        assets.mkdir()
        native = root / "tested-apk"
        for source, key in ((native / "outputs/apk/debug/app-debug.apk", "apk"),
             (native / "native-tested/debug-signature.txt", "signature"), (native / "native-tested/apk-metadata.txt", "metadata"),
             (root / "artifacts/ui.zip", "ui"), (root / "artifacts/checks.zip", "checks"), (root / "corresponding-source.zip", "source")):
            shutil.copyfile(source, assets / ASSET_NAMES[key])
        (assets / ASSET_NAMES["sha"]).write_text(APK_SHA + "  " + ASSET_NAMES["apk"] + "\n")
        write_json(assets / ASSET_NAMES["verification"], stage_proof)
        files = {path.name: dict(bytes=path.stat().st_size, sha256=digest(path)) for path in sorted(assets.iterdir())}
        require(set(files) == set(ASSET_NAMES.values()) and files[ASSET_NAMES["apk"]]["sha256"] == APK_SHA,
                "Packaged asset set/native bytes differ")
        manifest = dict(files=files, publication_commit=stage_proof["publication_commit"], version_name="0.13.0",
            tag_name="v0.13.0", public_download_verification="PENDING_POST_PUBLICATION")
        write_json(temp / "assets-manifest.json", manifest)
        (temp / "release-notes.md").write_text(NOTES)
        unchanged(bindings)
        validate_stage(root)
        for name in ("assets", "assets-manifest.json", "release-notes.md"):
            require(not (root / name).exists(), "Package destination appeared during preparation")
        # All checks precede these exclusive same-filesystem renames. A crash may
        # leave a visibly incomplete package, which the next invocation refuses.
        for name in ("assets", "assets-manifest.json", "release-notes.md"):
            (temp / name).rename(root / name)
    return manifest


def validate_release(release, manifest):
    require(isinstance(release, dict), "Public release metadata missing")
    raw = "tag_name" in release
    fields = (("tag_name", "v0.13.0"), ("target_commitish", manifest["publication_commit"]), ("draft", False), ("prerelease", True)) if raw else (
        ("tagName", "v0.13.0"), ("targetCommitish", manifest["publication_commit"]), ("isDraft", False), ("isPrerelease", True))
    for key, expected in fields:
        require(typed(release.get(key), expected), "Public release identity/state differs: " + key)
    entries = release.get("assets")
    require(isinstance(entries, list) and len(entries) == len(ASSET_NAMES), "Public asset count differs")
    names = [item.get("name") for item in entries if isinstance(item, dict)]
    require(len(names) == len(entries) == len(set(names)) and set(names) == set(ASSET_NAMES.values()) == set(manifest["files"]),
            "Public assets missing/extra/duplicate")
    urls = {}
    for item in entries:
        name = item["name"]
        expected = "https://github.com/" + REPOSITORY + "/releases/download/v0.13.0/" + name
        url = item.get("browser_download_url") if raw else item.get("url")
        require(url == expected, "Public download URL differs")
        require(typed(item.get("size"), manifest["files"][name]["bytes"]), "Public asset metadata size differs")
        urls[name] = url
    return urls


def download(url):
    # Deliberately no Authorization header, gh invocation, token, or account fallback.
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "DA-CALLER-public-byte-verifier"}), timeout=45) as response:
        require(response.status == 200, "Anonymous public download failed")
        data = response.read(32 * 1024 * 1024 + 1)
    require(len(data) <= 32 * 1024 * 1024, "Public asset too large")
    return data


def validate_tag_ref(value, commit):
    require(isinstance(value, dict) and value.get("ref") == "refs/tags/v0.13.0"
            and value.get("object", {}).get("type") == "commit"
            and value.get("object", {}).get("sha") == commit,
            "Actual public tag does not resolve to exact publication commit")


def verify_public(root, release_json=None, public_directory=None, apksigner="apksigner", aapt="aapt", tag_ref_json=None):
    root = Path(root).resolve()
    require(not (root / "public-verification.json").exists(), "Public verification exists; never overwrite")
    staged = validate_stage(root)
    manifest = read_json(root / "assets-manifest.json")
    require(manifest.get("publication_commit") == staged["publication_commit"] and manifest.get("tag_name") == "v0.13.0"
            and set(manifest.get("files", {})) == set(ASSET_NAMES.values()), "Asset manifest differs")
    bindings = snapshot([*input_paths(root / "artifacts"), root / "stage.json", root / "assets-manifest.json",
                         *(root / "assets" / name for name in ASSET_NAMES.values())])
    if release_json is None:
        release = json.loads(download("https://api.github.com/repos/" + REPOSITORY + "/releases/tags/v0.13.0"), object_pairs_hook=unique_object)
    else:
        release = read_json(release_json)
        bindings.update(snapshot([release_json]))
    if tag_ref_json is None:
        tag_ref = json.loads(download("https://api.github.com/repos/" + REPOSITORY + "/git/ref/tags/v0.13.0"), object_pairs_hook=unique_object)
    else:
        tag_ref = read_json(tag_ref_json)
        bindings.update(snapshot([tag_ref_json]))
    validate_tag_ref(tag_ref, staged["publication_commit"])
    urls = validate_release(release, manifest)
    if public_directory is not None:
        public_directory = Path(public_directory)
        require(public_directory.is_dir() and not public_directory.is_symlink()
                and {p.name for p in public_directory.iterdir()} == set(ASSET_NAMES.values()), "Independent public directory member set differs")
        bindings.update(snapshot(public_directory / name for name in ASSET_NAMES.values()))
    with tempfile.TemporaryDirectory(prefix="approved-013-public-", dir=root) as directory:
        public = Path(directory)
        verified = {}
        for name, url in urls.items():
            data = (public_directory / name).read_bytes() if public_directory is not None else download(url)
            expected = manifest["files"][name]
            require(len(data) == expected["bytes"] and sha(data) == expected["sha256"]
                    and data == (root / "assets" / name).read_bytes(), "Public bytes differ: " + name)
            (public / name).write_bytes(data)
            verified[name] = dict(url=url, bytes=len(data), sha256=sha(data), exact_bytes_equal=True)
        verify_apk(public / ASSET_NAMES["apk"], read_json(root / "tested-apk/native-tested/debug-signing-policy.json"), apksigner, aapt)
        archive_manifest(public / ASSET_NAMES["ui"], ARTIFACTS["ui.zip"][1], ARTIFACTS["ui.zip"][2])
        archive_manifest(public / ASSET_NAMES["checks"], ARTIFACTS["checks.zip"][1], ARTIFACTS["checks.zip"][2])
        validate_source_zip(public / ASSET_NAMES["source"])
        require(read_json(public / ASSET_NAMES["verification"]) == staged, "Public source/gate proof differs")
        unchanged(bindings)
        validate_stage(root)
        result = dict(status=("PASS_ALL_ANONYMOUS_PUBLIC_RELEASE_BYTES" if public_directory is None else
            "PASS_ALL_INDEPENDENT_DIRECTORY_BYTES_ANONYMOUS_PROVENANCE_REQUIRES_ATTESTATION"), publication_commit=staged["publication_commit"],
            assets=verified, independent_download_directory_supplied=public_directory is not None,
            anonymous_download_verified=public_directory is None,
            actual_tag_ref=tag_ref,
            anonymous_download_provenance="Root/workflow must attest independent downloads when directory mode is used",
            apk_signature_reverified=True, archives_crc_members_source_gates_verified=True, limits=LIMITS)
        with (root / "public-verification.json").open("x") as stream:
            stream.write(json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    first = commands.add_parser("stage")
    first.add_argument("--artifacts", type=Path, required=True)
    first.add_argument("--output", type=Path, required=True)
    second = commands.add_parser("package")
    second.add_argument("--root", type=Path, required=True)
    third = commands.add_parser("verify-public")
    third.add_argument("--root", type=Path, required=True)
    third.add_argument("--release-json", type=Path)
    third.add_argument("--public-directory", type=Path)
    third.add_argument("--tag-ref-json", type=Path, help="Independent downloaded exact public git ref metadata")
    for command in (first, third):
        command.add_argument("--apksigner", default="apksigner")
        command.add_argument("--aapt", default="aapt")
    args = parser.parse_args()
    try:
        if args.command == "stage":
            result = stage(args.artifacts, args.output, args.apksigner, args.aapt)
        elif args.command == "package":
            result = package(args.root)
        else:
            result = verify_public(args.root, args.release_json, args.public_directory, args.apksigner, args.aapt, args.tag_ref_json)
    except (ValueError, OSError, KeyError, TypeError, zipfile.BadZipFile, ET.ParseError, subprocess.SubprocessError) as error:
        print("Exact0.13 publication proof refused: " + str(error), file=sys.stderr)
        return 1
    print(json.dumps({key: result[key] for key in ("status", "publication_commit") if key in result}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
