#!/usr/bin/env python3
"""Additive0.13 native gate: original245 frames AND24 new real compositor frames.

This gate does not authorize publication/signing or relabel preview fixtures as
Telecom proof. It requires all original gate reports, actual current-source
instrumentation JUnit, source-bound screenshot hashes and independent saved
pixel verification of the transparency proof. No diagnostics are filtered out.
"""
import collections
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zlib

PRESENTATION = {f"modern-{part}-{suffix}.png" for part in (
    "recents-inline", "bottom-dialpad", "icons-all-nine", "wine-controls")
    for suffix in ("light-en", "dark-he")}
BACKUP = {f"modern-{part}.png" for part in (
    "backup-options-off", "backup-options-on", "archive-restore-off",
    "archive-restore-on", "archive-merged")}
ISLAND = {f"modern-island-{part}.png" for part in (
    "dragged", "hide-home", "restore-home", "status-external", "status-inapp",
    "below-inapp", "composition-background", "opacity-0", "opacity-35",
    "opacity-100", "new-call-shown")}
REQUIRED = PRESENTATION | BACKUP | ISLAND
PROOFS = {"modern-presentation-native.json": PRESENTATION,
          "modern-backup-native.json": BACKUP,
          "modern-island-native.json": ISLAND}
LEGACY = {"matrix-verification.json": 122,
          "personalization-verification.json": 53,
          "call-integration-verification.json": 8,
          "island-verification.json": 36,
          "external-island-verification.json": 8,
          "followup-verification.json": 6,
          "callback-business-verification.json": 12}


def require(value, message):
    if not value:
        raise AssertionError(message)


def integer(value, expected=None):
    return type(value) is int and (expected is None or value == expected)


def strict_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "Duplicate JSON key: " + key)
        result[key] = value
    return result


def one(root, name):
    files = [p for p in root.rglob(name) if p.is_file()]
    require(len(files) == 1, f"Missing/duplicate {name}: {len(files)}")
    value = json.loads(files[0].read_text(), object_pairs_hook=strict_object,
                       parse_constant=lambda value: require(False, "Invalid JSON number: " + value))
    require(type(value) is dict, "Evidence must be a JSON object: " + name)
    return value


def flags(value, names):
    for name in names:
        require(value.get(name) is True, f"Missing native assertion: {name}")


def large_font(value):
    return type(value) in (int, float) and abs(value - 1.3) <= 0.000001


def rectangle(value):
    match = re.fullmatch(r"\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]", str(value))
    require(match is not None, f"Missing native rectangle: {value}")
    left, top, right, bottom = map(int, match.groups())
    require(0 <= left < right <= 480 and 0 <= top < bottom <= 854,
            f"Native rectangle outside480x854: {value}")
    return left, top, right, bottom


def png(content, name, decode=False):
    """Validate complete saved PNG, optionally unfilter actual RGB sample rows."""
    require(len(content) > 1000 and content[:8] == b"\x89PNG\r\n\x1a\n", f"Invalid native PNG: {name}")
    offset, payload, header, ended = 8, bytearray(), None, False
    while offset < len(content):
        require(offset + 12 <= len(content), f"Truncated PNG chunk: {name}")
        length = struct.unpack(">I", content[offset:offset + 4])[0]
        kind = content[offset + 4:offset + 8]
        end = offset + 12 + length
        require(end <= len(content), f"Truncated PNG payload: {name}")
        chunk = content[offset + 8:offset + 8 + length]
        crc = struct.unpack(">I", content[offset + 8 + length:end])[0]
        require(zlib.crc32(kind + chunk) & 0xffffffff == crc, f"PNG CRC mismatch: {name}")
        if kind == b"IHDR":
            require(header is None and offset == 8 and length == 13, f"Invalid PNG header: {name}")
            header = struct.unpack(">IIBBBBB", chunk)
        elif kind == b"IDAT":
            require(header is not None and not ended, f"Invalid PNG data order: {name}")
            payload.extend(chunk)
        elif kind == b"tRNS":
            require(False, f"Native compositor PNG cannot use color-key transparency: {name}")
        elif kind == b"IEND":
            require(length == 0 and end == len(content), f"Invalid PNG ending: {name}")
            ended = True
        offset = end
    require(header is not None and ended and payload, f"Incomplete native PNG: {name}")
    width, height, depth, color, compression, filtering, interlace = header
    require((width, height) == (480, 854) and depth == 8 and color in (2, 6)
            and (compression, filtering, interlace) == (0, 0, 0), f"Wrong native PNG encoding: {name}")
    channels = 3 if color == 2 else 4
    stride = width * channels
    expected = height * (stride + 1)
    decoder = zlib.decompressobj()
    data = decoder.decompress(bytes(payload), expected + 1)
    require(len(data) == expected and decoder.eof and not decoder.unused_data
            and not decoder.unconsumed_tail, f"Invalid native PNG compressed stream: {name}")
    if not decode:
        require(all(data[y * (stride + 1)] <= 4 for y in range(height)), f"Invalid PNG filter: {name}")
        return None
    rows, previous, position = [], bytearray(stride), 0
    for _ in range(height):
        method = data[position]
        require(method <= 4, f"Invalid PNG row filter: {name}")
        row = bytearray(data[position + 1:position + 1 + stride])
        for index in range(stride):
            left = row[index - channels] if index >= channels else 0
            above = previous[index]
            upper_left = previous[index - channels] if index >= channels else 0
            if method == 1:
                predictor = left
            elif method == 2:
                predictor = above
            elif method == 3:
                predictor = (left + above) // 2
            elif method == 4:
                estimate = left + above - upper_left
                distance = (abs(estimate - left), abs(estimate - above), abs(estimate - upper_left))
                predictor = left if distance[0] <= min(distance[1:]) else above if distance[1] <= distance[2] else upper_left
            else:
                predictor = 0
            row[index] = (row[index] + predictor) & 255
        rows.append(row)
        previous, position = row, position + stride + 1
    return rows, channels


def junit(root, sources):
    expected = set()
    for source in sources.glob("*.java"):
        text = source.read_text()
        methods = re.findall(r"public\s+void\s+(test\w+)\s*\(\s*\)", text)
        for method in methods:
            expected.add(("com.dacaller.app." + source.stem, method))
    require(len(expected) == 40, "Current native source must retain36original+1inline+3modern cases")
    require(sum(owner.endswith(".ModernFeaturesIntegrationTest") for owner, _ in expected) == 3,
            "Missing additive native methods")
    files = sorted(root.rglob("*.xml"))
    require(bool(files), "Authentic native JUnit is missing")
    actual, hashes = set(), {}
    for path in files:
        data = path.read_bytes()
        document = ET.fromstring(data)
        require(document.tag in {"testsuite", "testsuites"}, "Unexpected native JUnit root")
        for tag in ("failure", "error", "skipped"):
            require(not list(document.iter(tag)), "Native JUnit contains " + tag)
        for suite in document.iter("testsuite"):
            for key in ("failures", "errors", "skipped"):
                if key in suite.attrib:
                    require(suite.attrib[key] == "0", "Nonzero native JUnit " + key)
            cases = list(suite.iter("testcase"))
            if "tests" in suite.attrib:
                require(suite.attrib["tests"].isdigit() and int(suite.attrib["tests"]) == len(cases), "Native JUnit counter mismatch")
        if document.tag == "testsuites" and "tests" in document.attrib:
            count = document.attrib["tests"]
            require(count.isdigit() and int(count) == len(list(document.iter("testcase"))), "Native JUnit aggregate counter mismatch")
        for key in ("failures", "errors", "skipped"):
            if key in document.attrib:
                require(document.attrib[key] == "0", "Nonzero native JUnit aggregate " + key)
        for case in document.iter("testcase"):
            identity = (case.get("classname"), case.get("name"))
            require(identity not in actual, "Duplicate native JUnit method")
            actual.add(identity)
        hashes[path.relative_to(root).as_posix()] = hashlib.sha256(data).hexdigest()
    require(actual == expected, f"Native current-source methods differ: missing={sorted(expected - actual)}, extra={sorted(actual - expected)}")
    return {"tests": len(actual), "failures": 0, "errors": 0, "skipped": 0, "files_sha256": hashes}


def presentation(proof):
    flags(proof, ("no_floating_dialpad", "all_inline_actions_retained", "back_collapses_without_dial"))
    observations = proof.get("observations", [])
    require([(o.get("theme"), o.get("language")) for o in observations] == [("light", "en"), ("dark", "he")], "Both native theme/language cases required")
    for value in observations:
        require(large_font(value.get("font_scale")), "Actual large-font configuration missing")
        require(integer(value.get("inline_expansion_dial_requests"), 0) and integer(value.get("icon_choices"), 9)
                and value.get("selected_last_icon") == "signal", "Inline no-dial/ninth picker entry failed")
        left, top, right, bottom = rectangle(value.get("settings_bounds"))
        require(left < 30 and right - left >= 72 and bottom - top >= 72, "Settings must be top-left and48dp")
        left, top, right, bottom = rectangle(value.get("dialpad_nav_bounds"))
        require(top > 427 and bottom - top >= 72, "Bottom dialpad target must be accessible48dp")
        flags(value, ("switch_native_pointer_drag", "switch_accessibility_checkable"))
        require(type(value.get("switch_before")) is bool and type(value.get("switch_after")) is bool
                and value["switch_before"] != value["switch_after"], "Native wine Switch did not toggle")
        left, top, right, bottom = rectangle(value.get("switch_bounds"))
        require(right - left >= 72 and bottom - top >= 72, "Native Switch is smaller than48dp")


def backup(proof):
    flags(proof, ("independent_export_and_restore_opt_ins", "insert_only_conflicts_preserved", "settings_only_legacy_compatible"))
    require("test ContentProvider" in proof.get("document_flow", ""), "Do not claim human DocumentsUI interaction")
    observations = proof.get("observations", [])
    require(len(observations) == 1, "One complete same-file backup scenario required")
    value = observations[0]
    for key, expected in {"legacy_version": 1, "legacy_contains_archive": False, "bundle_version": 2,
                          "export_archive_default": False, "restore_archive_default": False,
                          "archive_policy_at_export": "off", "opt_out_added": 0, "opt_in_added": 1,
                          "same_identity_conflict_retained_seconds": 999,
                          "pending_recovery_after_success": False, "raw_archive_uploaded": False}.items():
        require(type(value.get(key)) is type(expected) and value[key] == expected, "Wrong backup result: " + key)
    before = value.get("preexisting_rows_preserved")
    require(type(before) is int and before >= 0 and integer(value.get("stored_rows_before"), before + 2) and integer(value.get("stored_rows_exported"), before + 2),
            "All stored rows including OFF-policy records must be exported and retained")


def argb(decoded, x, y):
    rows, channels = decoded
    pixel = rows[y][x * channels:(x + 1) * channels]
    require(channels == 3 or pixel[3] == 255, "Real compositor samples must be opaque")
    return (0xff << 24) | (pixel[0] << 16) | (pixel[1] << 8) | pixel[2]


def island(proof, images):
    flags(proof, ("role_held", "hide_preserves_call_and_notification", "settings_restore_on_home",
                  "new_call_ignores_previous_hidden_token", "single_owned_surface",
                  "protected_status_icons_remain_visible", "modem_end_removes_island"))
    require(proof.get("default_dialer") == "com.dacaller.app" and "Android Telecom -> production PhoneSessionService" in proof.get("data_origin", ""), "Actual production Telecom binding missing")
    first, second = proof.get("first_token"), proof.get("second_token")
    require(isinstance(first, str) and first and isinstance(second, str) and second and first != second, "Two distinct real call tokens required")
    launcher = proof.get("launcher_package")
    require(isinstance(launcher, str) and re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", launcher)
            and launcher not in {"com.dacaller.app", "com.android.systemui"}, "Resolved native Home package missing")
    foreground = proof.get("foreground_evidence", [])
    require([value.get("stage") for value in foreground] == ["active-home", "hide-home", "restore-home", "status-external", "new-call-home"], "Every named Home frame needs actual foreground evidence")
    for value in foreground:
        require(value.get("foreground_package") == launcher, "Home capture belongs to another app")
        flags(value, ("top_resumed", "native_application_window_visible"))
    answers = proof.get("answers", [])
    require([value.get("token") for value in answers] == [first, second], "Native answer must bind each distinct owned call")
    for value in answers:
        rectangle(value.get("bounds"))
        delay, stable, window = (value.get(key) for key in ("configured_touch_acceptance_delay_millis", "stable_millis", "window_id"))
        require(integer(delay) and 0 <= delay <= 3000 and integer(stable) and stable >= max(350, delay + 100)
                and integer(window) and window >= 0 and value.get("number") == "0500099971"
                and value.get("input_method") == "native-pointer" and value.get("native_caller_row") is True,
                "Native answer must use stable caller-owned SystemUI bounds beyond its actual touch guard")
    commands = proof.get("modem_commands", [])
    require([c.get("operation") for c in commands] == ["call", "cancel", "call", "cancel"], "Actual modem lifecycle required")
    require(all(isinstance(c.get("id"), str) and re.fullmatch(r"[0-9]+", c["id"]) for c in commands)
            and len({c["id"] for c in commands}) == 4 and len({c.get("serial") for c in commands}) == 1, "Unique modem acknowledgements required")
    for command in commands:
        require(command.get("number") == "0500099971" and integer(command.get("returncode"), 0)
                and re.fullmatch(r"emulator-[0-9]+", str(command.get("serial", ""))), "Invalid emulator modem acknowledgement")
        if command.get("operation") == "call":
            require("OK" in command.get("stdout", ""), "Modem call not acknowledged")
    gestures = proof.get("gestures", [])
    require([g.get("kind") for g in gestures] == ["completed_external_drag", "cancelled_external_drag", "drag_started_on_end_control"], "All real drag outcomes required")
    require(all(integer(g.get("call_state"), 4) for g in gestures), "Dragging must preserve active Telecom call")
    completed, cancelled, child = gestures
    initial, moved = rectangle(completed.get("before")), rectangle(completed.get("after"))
    require(moved[1] > initial[1] + 75 and type(completed.get("x")) is int and type(completed.get("y")) is int
            and 0 <= completed["x"] <= 10000 and 0 < completed["y"] <= 10000, "Real drag must move window and save normalized position")
    flags(cancelled, ("normalized_position_preserved", "geometry_restored"))
    require(child.get("end_confirmation_opened") is False, "Dragging End must not dispatch click")
    child_before, child_after = rectangle(child.get("before")), rectangle(child.get("after"))
    before_position, after_position = child.get("position_before"), child.get("position_after")
    require(child_after[1] > child_before[1] + 38 and integer(before_position) and integer(after_position)
            and 0 <= before_position < after_position <= 10000 and child.get("body_scrolling_required") is False,
            "Drag beginning on End must actually move and save its non-overflowing owned window")
    surfaces = proof.get("surfaces", [])
    require([s.get("stage") for s in surfaces] == ["external-protected-status-fallback", "inapp-supported-central-status-area", "inapp-default-below-restored"], "Protected fallback/default restoration proof missing")
    for value, overlays in zip(surfaces, (1, 0, 0)):
        rectangle(value.get("bounds"))
        require(value.get("token") == first and integer(value.get("call_state"), 4) and integer(value.get("external_overlay_count"), overlays), "Wrong owned island surface")
    transparent = proof.get("transparency", {})
    flags(transparent, ("background_is_real_app_compositor",))
    require(type(transparent.get("view_alpha")) in (int, float) and transparent["view_alpha"] == 1
            and type(transparent.get("caller_alpha")) in (int, float) and transparent["caller_alpha"] == 1
            and integer(transparent.get("background_alpha_35"), 89) and integer(transparent.get("rgb_tolerance"), 3), "Opacity must affect background only")
    left, top, right, bottom = rectangle(transparent.get("sample_region"))
    samples = transparent.get("samples", [])
    require(len(samples) == 5 and len({(s.get("x"), s.get("y")) for s in samples}) == 5, "Five distinct real compositor samples required")
    decoded = {key: png(images[f"modern-island-{name}.png"].read_bytes(), name, True)
               for key, name in (("background", "composition-background"), ("opacity0", "opacity-0"), ("opacity35", "opacity-35"), ("opacity100", "opacity-100"))}
    for sample in samples:
        x, y = sample.get("x"), sample.get("y")
        require(type(x) is int and type(y) is int and left <= x < right and top <= y < bottom, "Sample outside actual capsule")
        for key, data in decoded.items():
            require(integer(sample.get(key)) and -(1 << 31) <= sample[key] < (1 << 31) and sample[key] & 0xffffffff == argb(data, x, y), "Reported sample differs from actual saved PNG: " + key)
        base, zero, partial, opaque = (sample[k] & 0xffffffff for k in ("background", "opacity0", "opacity35", "opacity100"))
        require(base == zero, "Zero background opacity must expose exact real underlying pixels")
        difference = 0
        for shift in (16, 8, 0):
            bg, fg, actual = (value >> shift & 255 for value in (base, opaque, partial))
            expected = int(fg * 89 / 255 + bg * 166 / 255 + 0.5)
            require(abs(expected - actual) <= 3, "35% real compositor blend does not match saved PNGs")
            difference += abs(bg - fg)
        require(difference > 80, "Samples cannot distinguish actual underlying content and opaque capsule")


def legacy(baseline, all_baseline, source, app_tree):
    images = {p.name: p for p in all_baseline}
    hashes = {}
    for name, path in images.items():
        content = path.read_bytes()
        png(content, name)
        hashes[name] = hashlib.sha256(content).hexdigest()
    reports, covered = {}, set()
    for name, count in LEGACY.items():
        report = one(baseline, name)
        api = report.get("native_assertions", {}).get("api") if name == "call-integration-verification.json" else report.get("api")
        require(report.get("source_sha") == source and report.get("app_tree") == app_tree
                and integer(api, 33) and integer(report.get("required_real_png_files"), count),
                "Original gate was not completed on current source: " + name)
        if os.environ.get("GITHUB_RUN_ID") is not None:
            require(report.get("workflow_run_id") == os.environ["GITHUB_RUN_ID"], "Legacy report belongs to another workflow run: " + name)
        if name == "call-integration-verification.json":
            flags(report.get("native_assertions", {}), ("real_systemui", "notification_actions_acknowledged", "native_back_returns_to_app", "real_phone_call_foreground_service"))
        if name != "matrix-verification.json":
            saved = report.get("sha256", {})
            require(type(saved) is dict and len(saved) == count, "Missing original PNG hashes: " + name)
            for frame, digest in saved.items():
                require(frame in hashes and re.fullmatch(r"[0-9a-f]{64}", str(digest)) and digest == hashes[frame], "Original saved PNG bytes differ: " + frame)
            require(not covered.intersection(saved), "Ambiguous original report coverage: " + name)
            covered.update(saved)
        reports[name] = hashlib.sha256(json.dumps(report, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
    require(len(covered) == 123, "Original complementary123-frame reports missing")
    require(one(baseline, "matrix-verification.json").get("all_files") == sorted(images), "Original matrix names differ from required native gate")
    return reports


def verify(baseline, modern, junit_root):
    require(baseline.resolve() != modern.resolve(), "Baseline and additive PNG roots must be separate")
    source = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    app_tree = subprocess.check_output(["git", "rev-parse", "HEAD:app"], text=True).strip()
    all_baseline = [p for p in baseline.rglob("*.png") if p.is_file()]
    require(len(all_baseline) == len({p.name for p in all_baseline}) == 245, "All original245 real PNGs must remain present exactly once")
    require(not any("modern-" in p.name or any(bad in p.name for bad in ("failed", "unreachable", "not-ready")) for p in all_baseline), "No additive/diagnostic filtering in original matrix")
    legacy_reports = legacy(baseline, all_baseline, source, app_tree)
    files = [p for p in modern.rglob("*.png") if p.is_file()]
    counts = collections.Counter(p.name for p in files)
    require(set(counts) == REQUIRED and all(v == 1 for v in counts.values()) and len(files) == 24, "Require exactly24 new real PNGs; no missing/duplicate/unknown diagnostic frame")
    images = {p.name: p for p in files}
    hashes = {}
    for name, path in sorted(images.items()):
        content = path.read_bytes();png(content, name);hashes[name] = hashlib.sha256(content).hexdigest()
    proofs = {}
    for name, expected in PROOFS.items():
        proof = one(modern, name)
        require(integer(proof.get("api"), 33) and integer(proof.get("density_dpi"), 240) and large_font(proof.get("font_scale"))
                and proof.get("package") == "com.dacaller.app" and proof.get("version_name") == "0.13.0" and integer(proof.get("version_code"), 22), "Wrong native package/version/configuration")
        require(proof.get("physical_carrier_audio_verified") is False and proof.get("matching_key_upgrade_verified") is False, "Emulator cannot establish hardware/audio/upgrade proof")
        captures = proof.get("captures", {})
        require(set(captures) == expected, "Exact per-scenario saved captures missing: " + name)
        for frame, value in captures.items():
            require(value.get("sha256") == hashes[frame] and integer(value.get("width"), 480) and integer(value.get("height"), 854)
                    and value.get("method") == "UiAutomation.takeScreenshot", "Real compositor saved bytes differ: " + frame)
        proofs[name] = proof
    presentation(proofs["modern-presentation-native.json"])
    backup(proofs["modern-backup-native.json"])
    island(proofs["modern-island-native.json"], images)
    native = junit(junit_root, Path("app/src/androidTest/java/com/dacaller/app"))
    return {"source_sha": source, "app_tree": app_tree, "workflow_run_id": os.environ.get("GITHUB_RUN_ID"),
            "api": 33, "size": "480x854", "density_dpi": 240, "font_scale": 1.3,
            "version_name": "0.13.0", "version_code": 22, "required_real_png_files": 24,
            "original_required_png_files": 245, "total_required_png_files": 269,
            "native_junit": native, "legacy_reports_canonical_sha256": legacy_reports,
            "sha256": hashes, "baseline_sha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in all_baseline},
            "native_assertions": proofs,
            "limits": "Fresh emulator interaction and actual production Telecom island proof. List presentation uses the explicit no-dial debug host; archive uses synthetic stored rows and isolated test-provider SAF results. No human DocumentsUI, physical JellyStar/carrier/audio/overnightDoze/matching-key upgrade proof or publication authorization."}


def main():
    baseline = Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/ui-snapshots")
    modern = Path(sys.argv[2] if len(sys.argv) > 2 else "app/build/ui-modern-snapshots")
    results = Path(sys.argv[3] if len(sys.argv) > 3 else "app/build/outputs/androidTest-results")
    result = verify(baseline, modern, results)
    (modern / "modern-features-verification.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print("Verified40 authentic API33 native cases, original245frames and24additive real compositor frames (269total)")


if __name__ == "__main__":
    main()
