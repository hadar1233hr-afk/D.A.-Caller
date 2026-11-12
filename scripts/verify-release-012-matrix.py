#!/usr/bin/env python3
"""Revalidate retained 0.12.0 evidence under its explicitly approved 243 scope.

This is not a fresh emulator run or a change to the original 245-frame gates.
Publication/signature/owner approval are separate gates. The retained APK must
never be rebuilt or re-signed after these checks.
"""
from __future__ import annotations

import argparse
import collections
import datetime as dt
import hashlib
import io
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

APP_TREE = "9f15350b457405bf88a35c0ac5badc8f3a85cd94"
NATIVE_SOURCE = "0d266d63d446b46d3e2a98048b112572d22768fa"
RUN = 42001641651
RAW_EXPORT = "612188913a0e9ff2040d58892d1f9e83d528c4da"
APK_SHA = "f025fad667edafe2c76ab3f574c7ff1bfd2aeb301fdd1d035ac1acb6b311e16a"
VISUAL_SHA = "689954f804f3e2ccf31395ad217fc559196b95375e19f0a513f919aa207dc23d"
BASELINE_SHA = "6ba47695bc601281e1eb4058798d4add84e67a122a87ed363725fc5baeab6954"
ARTIFACTS = {
    "ui": ("ui.zip", 14225526, "55ae41e1c1c710985d7bf5b722148554e144ce5e19c84225a9dafd3c5b97f0fa"),
    "checks": ("checks.zip", 130531, "f36b4f7def34c55384e531e32242836d8e50c27ff8c2ce6dd0583bac229d2d37"),
    "apk": ("native-failure-candidate.zip", 454470, "a09cfb1e0a6c69a69ab050394e7f3589bf976358c4e27ac3474555d01f3cf850"),
}
VERIFIERS = {
    "verify-ui-matrix.py": "e3fd18f544b028ffeddaddee272b2f4e7aefa2cc47f5d2d90fac5d9580c9dab2",
    "verify-personalization-matrix.py": "0fa3106ef1832cd5fb7fd8df167c56e19c70b5a034ae1ffc20636659220d2825",
    "verify-call-integration-matrix.py": "0dbe8072173a2417ce9bb46fb686c9ab8fc194255c38dabdb1e396d5e0b22e6f",
    "verify-island-matrix.py": "a2f0b025aeb20536acadd574ec36a500141516a9cefe3f99e4b4478fa051be36",
    "verify-external-island-matrix.py": "db313c16aea96a65b1d194de80a3562e9a73204efb845473c89cff4664844c65",
    "verify-followup-matrix.py": "6f7c540bc7703f25957be11566789e021eb438208942fc9c0d73c94579c14fa8",
}
PROPOSED_SHA = "9846bb4146523e0b5f65d01d8c491b3daef711c07aa7f5de397ecfd490e14cb5"
EXTRA_NAMES = {
    "callback-settings-light.png", "callback-settings-dark.png", "callback-list-light.png", "callback-list-dark.png",
    "business-deferred-light.png", "business-deferred-dark.png", "incoming-swipe-resting.png",
    "incoming-swipe-dragging.png", "recents-unknown-number-actions.png", "recents-new-contact-editor.png",
}
SCOPED_COUNTS = {"verify-external-island-matrix.py", "verify-followup-matrix.py"}


def require(value, message):
    if not value:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def digest(path):
    return sha(Path(path).read_bytes())


def checked_json(path, expected):
    data = Path(path).read_bytes()
    require(sha(data) == expected, "Retained review bytes changed: " + str(path))
    return json.loads(data)


def unpack_artifact(path, expected_size, expected_sha, target):
    data = Path(path).read_bytes()
    require(len(data) == expected_size and sha(data) == expected_sha,
            "Official retained artifact size/hash differs: " + Path(path).name)
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "Duplicate artifact archive members")
        for item in archive.infolist():
            member = Path(item.filename)
            require(not member.is_absolute() and ".." not in member.parts
                    and not ((item.external_attr >> 16) & 0o170000 == 0o120000),
                    "Unsafe artifact member")
        require(archive.testzip() is None, "Artifact ZIP CRC differs")
        archive.extractall(target)
    return dict(bytes=len(data), sha256=expected_sha, members=len(names), crc="PASS")


def audit_junit(root, expected):
    paths = sorted(Path(root).rglob("*.xml"))
    require(bool(paths), "Authentic JUnit is missing")
    identities, files = set(), {}
    count = 0
    for path in paths:
        data = path.read_bytes()
        document = ET.fromstring(data)
        require(document.tag in {"testsuite", "testsuites"}, "Unexpected JUnit root")
        for tag in ("failure", "error", "skipped"):
            require(not list(document.iter(tag)), "JUnit contains " + tag)
        for suite in document.iter("testsuite"):
            for key in ("failures", "errors", "skipped"):
                if key in suite.attrib:
                    require(suite.attrib[key] == "0", "Nonzero JUnit suite counter: " + key)
            direct = suite.findall("testcase")
            if "tests" in suite.attrib and direct:
                require(suite.attrib["tests"].isdigit() and int(suite.attrib["tests"]) == len(direct),
                        "JUnit case/counter mismatch")
        for case in document.iter("testcase"):
            identity = (case.get("classname"), case.get("name"))
            require(all(isinstance(value, str) and value for value in identity), "JUnit identity missing")
            require(identity not in identities, "Repeated JUnit identity")
            identities.add(identity)
            count += 1
        files[path.relative_to(root).as_posix()] = sha(data)
    require(count == len(identities) == expected, "Actual unique JUnit count differs")
    return dict(tests=count, failures=0, errors=0, skipped=0, files_sha256=files)


def validate_visual(review, baseline, root):
    require(review.get("source_sha") == NATIVE_SOURCE and review.get("app_tree") == APP_TREE
            and type(review.get("workflow_run_id")) is int and review["workflow_run_id"] == RUN
            and review.get("raw_export_commit") == RAW_EXPORT,
            "Visual source/run/app-tree/export differs")
    require(review.get("whole_matrix_visual_review_complete") is True
            and type(review.get("actual_viewed_png_count")) is int and review["actual_viewed_png_count"] == 243
            and review.get("available_functional_pngs") == 243,
            "Complete actual visual observation missing")
    expected = review.get("reviewed_png_sha256", {})
    require(isinstance(expected, dict) and len(expected) == 243, "Full 243-image hash ledger required")
    previous = {entry["name"] for entry in baseline["files"]}
    require(len(previous) == 233 and previous | EXTRA_NAMES == set(expected)
            and not previous & EXTRA_NAMES, "All 233 baseline names and exactly ten new names required")
    paths = [path for path in Path(root).rglob("*.png") if path.is_file()]
    counts = collections.Counter(path.name for path in paths)
    require(len(paths) == len(counts) == 243 and set(counts) == set(expected),
            "Missing, duplicate or unexpected release PNG")
    require(not any(any(marker in name for marker in ("failed", "unreachable", "not-ready")) for name in counts),
            "Failure diagnostics are not approved screenshots")
    actual = {}
    for path in paths:
        data = path.read_bytes()
        require(len(data) > 1000 and data[:8] == b"\x89PNG\r\n\x1a\n"
                and struct.unpack(">II", data[16:24]) == (480, 854)
                and data[-12:] == b"\x00\x00\x00\x00IEND\xaeB`\x82", "Invalid native PNG: " + path.name)
        require(sha(data) == expected[path.name], "Actual PNG differs from manually reviewed bytes: " + path.name)
        actual[path.name] = expected[path.name]
    return actual


def profile_source(name, data):
    require(name in VERIFIERS and sha(data) == VERIFIERS[name], "Original verifier changed: " + name)
    text = data.decode()
    if name in SCOPED_COUNTS:
        # Pinned originals contain exactly three occurrences: the total-count
        # assertion, generated total-count metadata and its explanatory output.
        # Every functional/native assertion is otherwise byte-for-byte retained.
        require(text.count("245") == 3, "Unexpected legacy matrix-count structure")
        return text.replace("245", "243"), dict(original_sha256=sha(data), approved_total_count=243,
            replaced_total_count_literals=3, all_other_native_assertions_unchanged=True)
    return text, dict(original_sha256=sha(data), replaced_total_count_literals=0)


def replay_functional_gates(repository, root):
    results = {}
    for name in VERIFIERS:
        source, note = profile_source(name, (repository / "scripts" / name).read_bytes())
        process = subprocess.run([sys.executable, "-c", source, str(root)], cwd=repository,
                                 capture_output=True, text=True, timeout=180)
        require(process.returncode == 0, "0.12 scoped native-proof replay failed: " + name + "\n" + process.stderr[-2500:])
        note.update(scoped_source_sha256=sha(source.encode()), stdout=process.stdout.strip())
        results[name] = note
    path = repository / "docs/releases/0.12.0-proposed/verify-release-matrix.py"
    source = path.read_bytes()
    require(sha(source) == PROPOSED_SHA, "Reviewed independent reminder/deferred profile changed")
    process = subprocess.run([sys.executable, "-c", source.decode(), str(root)], cwd=repository,
                             capture_output=True, text=True, timeout=180)
    require(process.returncode == 0, "0.12 reminder/swipe/contact/deferred proof failed:\n" + process.stderr[-2500:])
    results["independent-reminder-swipe-contact-deferred-profile"] = dict(source_sha256=PROPOSED_SHA,
        stdout=process.stdout.strip(), original_245_populated_business_verifier_executed=False)
    return results


def verify(repository, artifact_root, apk, visual_review=None):
    repository, artifact_root, apk = map(lambda value: Path(value).resolve(), (repository, artifact_root, apk))
    current = subprocess.check_output(["git", "rev-parse", "HEAD:app"], cwd=repository, text=True).strip()
    original = subprocess.check_output(["git", "rev-parse", NATIVE_SOURCE + ":app"], cwd=repository, text=True).strip()
    require(current == original == APP_TREE, "Application changed since actual native/visual verification")
    require(digest(apk) == APK_SHA, "Retained native-tested APK bytes changed")
    visual_review = visual_review or repository / "docs/evidence/release-0.12.0/polish-ci/full-visual-observation.json"
    review = checked_json(visual_review, VISUAL_SHA)
    baseline = checked_json(repository / "docs/evidence/call-followup-2026-11-01/visual-review.json", BASELINE_SHA)
    snapshots = {Path(visual_review): VISUAL_SHA, apk: APK_SHA,
                 repository / "docs/evidence/call-followup-2026-11-01/visual-review.json": BASELINE_SHA,
                 Path(__file__).resolve(): digest(Path(__file__).resolve())}
    snapshots.update({repository / "scripts" / name: value for name, value in VERIFIERS.items()})
    proposed = repository / "docs/releases/0.12.0-proposed/verify-release-matrix.py"
    snapshots[proposed] = PROPOSED_SHA
    with tempfile.TemporaryDirectory(prefix="da-caller-012-scope-") as temporary:
        temp = Path(temporary)
        artifacts = {}
        for key, (name, size, expected) in ARTIFACTS.items():
            path = artifact_root / name
            snapshots[path] = expected
            artifacts[key] = unpack_artifact(path, size, expected, temp / key)
        candidates = list((temp / "apk").rglob("app-debug.apk"))
        require(len(candidates) == 1 and digest(candidates[0]) == APK_SHA,
                "Official native artifact does not contain the exact candidate")
        unit = audit_junit(temp / "checks/test-results", 356)
        native = audit_junit(temp / "ui/outputs/androidTest-results", 35)
        roots = list((temp / "ui").rglob("ui-snapshots"))
        require(len(roots) == 1, "Actual native snapshot root missing/ambiguous")
        images = validate_visual(review, baseline, roots[0])
        gates = replay_functional_gates(repository, roots[0])
        # Generated replay manifests are scoped revalidation output only. The
        # original official archive, native proofs and historical review remain
        # untouched; no new emulator run or overall old-workflow PASS is claimed.
    require(all(digest(path) == expected for path, expected in snapshots.items()),
            "Evidence/code/APK changed during scoped verification")
    require(subprocess.check_output(["git", "rev-parse", "HEAD:app"], cwd=repository, text=True).strip() == APP_TREE,
            "Application changed during scoped verification")
    return dict(status="PASS_APPROVED_012_RELEASE_SCOPE_REVALIDATION", observed_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(),
        application_id="com.dacaller.app", version="0.12.0", app_tree=APP_TREE, native_source_sha=NATIVE_SOURCE,
        native_workflow_run_id=RUN, raw_export_commit=RAW_EXPORT, native_apk_sha256=APK_SHA,
        total_required_png_files=243, baseline_png_files=233, additional_png_files=10,
        authentic_unit_junit=unit, authentic_native_junit=native, reviewed_png_sha256=images,
        original_artifacts=artifacts, functional_revalidation=gates,
        original_workflow_conclusion="failure", fresh_emulator_execution=False,
        publication_signature_and_owner_approval_checked=False,
        limits=["Revalidation of retained authentic native data, not a new emulator execution or historical workflow PASS.",
                "Original245 populated-business guards unchanged; independent approved0.12 scope excludes business data.",
                "Exact manually reviewed243 image bytes and all233 baseline names retained.",
                "Signing/embedded identity/owner approval/public download equality are separate publication gates.",
                "Physical JellyStar/SIM/audio/BT/matching-key upgrade remain NOT TESTED; hardware prerequisite waived only."])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--artifacts", type=Path, required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--visual-review", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    require(not args.output.exists(), "Output already exists; select a new path")
    result = verify(args.repository, args.artifacts, args.apk, args.visual_review)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x") as stream:
        json.dump(result, stream, ensure_ascii=True, indent=2)
        stream.write("\n")
    print(json.dumps({key: result[key] for key in ("status", "app_tree", "native_apk_sha256", "total_required_png_files")}))


if __name__ == "__main__":
    main()
