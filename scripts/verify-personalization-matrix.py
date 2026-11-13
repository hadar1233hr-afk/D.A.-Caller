#!/usr/bin/env python3
"""Validate all new personalization renders as genuine complete native PNG files."""
import collections
import hashlib
import json
import os
import struct
import subprocess
import sys
import zlib
from pathlib import Path


def native_png_rows(content, name):
    """Decode the saved compositor PNG independently of the device's counters."""
    offset, payload, header, ended = 8, bytearray(), None, False
    while offset < len(content):
        assert offset + 12 <= len(content), f"Truncated PNG chunk: {name}"
        length = struct.unpack(">I", content[offset:offset + 4])[0]
        kind = content[offset + 4:offset + 8]
        end = offset + 12 + length
        assert end <= len(content), f"Truncated PNG chunk payload: {name}"
        chunk = content[offset + 8:offset + 8 + length]
        crc = struct.unpack(">I", content[offset + 8 + length:end])[0]
        assert zlib.crc32(kind + chunk) & 0xffffffff == crc, f"PNG chunk checksum mismatch: {name}"
        if kind == b"IHDR":
            assert header is None and offset == 8 and length == 13, f"Invalid PNG header: {name}"
            header = struct.unpack(">IIBBBBB", chunk)
        elif kind == b"IDAT":
            assert header is not None and not ended, f"Invalid PNG image data order: {name}"
            payload.extend(chunk)
        elif kind == b"IEND":
            assert length == 0 and end == len(content), f"Invalid PNG end: {name}"
            ended = True
        offset = end
    assert header is not None and ended and payload, f"Incomplete native PNG: {name}"
    width, height, depth, color, compression, filtering, interlace = header
    assert (width, height) == (480, 854), f"Wrong decoded native PNG size: {name}"
    assert depth == 8 and color in (2, 6) and (compression, filtering, interlace) == (0, 0, 0), f"Unsupported native PNG encoding: {name}"
    channels = 3 if color == 2 else 4
    stride = width * channels
    expected = height * (stride + 1)
    decoder = zlib.decompressobj()
    decoded = decoder.decompress(bytes(payload), expected + 1)
    assert len(decoded) == expected and decoder.eof and not decoder.unused_data and not decoder.unconsumed_tail, f"Invalid native PNG compressed data: {name}"
    rows, previous, position = [], bytearray(stride), 0
    for _ in range(height):
        method = decoded[position]
        assert method <= 4, f"Unknown native PNG row filter: {name}"
        row = bytearray(decoded[position + 1:position + 1 + stride])
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
                distances = (abs(estimate - left), abs(estimate - above), abs(estimate - upper_left))
                predictor = left if distances[0] <= min(distances[1:]) else above if distances[1] <= distances[2] else upper_left
            else:
                predictor = 0
            row[index] = (row[index] + predictor) & 255
        rows.append(row)
        previous = row
        position += stride + 1
    return rows, channels


def saved_bar_pixels(rows, channels, left, right, top, bottom, dark, name):
    count = 0
    for row in rows[top:bottom]:
        for x in range(left, right):
            offset = x * channels
            if channels == 4:
                assert row[offset + 3] == 255, f"Native compositor bar has transparent pixels: {name}"
            brightness = sum(row[offset:offset + 3])
            count += brightness > 600 if dark else brightness < 384
    return count


def validate_backup_bars(root, backup, files, hashes):
    proofs = [file for file in root.rglob("personalization-backup-native.json") if file.is_file()]
    assert len(proofs) == 1, "Missing or duplicate native backup bar proof"
    proof = json.loads(proofs[0].read_text())
    assert type(proof.get("api")) is int and proof["api"] == 33, "Wrong native backup proof API"
    captures = proof.get("nativeBarCaptures")
    assert isinstance(captures, list) and len(captures) == len(backup), "All three backup PNGs require checked native compositor captures"
    assert all(isinstance(capture, dict) for capture in captures), "Invalid native backup capture records"
    assert collections.Counter(capture.get("filename") for capture in captures) == collections.Counter(backup), "Native backup bar filenames do not match the required PNGs"
    for capture in captures:
        name = capture["filename"]
        confirmation = name == "personalization-backup-restore-confirmation.png"
        kind = "restore-confirmation" if confirmation else "activity"
        # Android's confirmation dialog dims the light owner's system bars and
        # uses light icons. Its effective bar palette differs from its owner.
        dark = name == "personalization-backup-saved-dark.png" or confirmation
        assert capture.get("validated") is True, f"Native backup bar capture did not pass: {name}"
        assert capture.get("dark") is dark, f"Wrong effective native backup bar palette: {name}"
        assert capture.get("focused_window") == kind, f"Wrong focused backup window: {name}"
        assert capture.get("capture_method") == "UiAutomation.takeScreenshot", f"Not a native backup compositor capture: {name}"
        assert capture.get("sha256") == hashes[name], f"Checked backup Bitmap does not match the saved PNG: {name}"
        requirements = {"status_sample_left_px": 0, "status_sample_right_px": 240,
                        "required_navigation_px": 72, "required_contrasting_pixels_per_bar": 40,
                        "required_consecutive_valid_samples": 3, "deadline_ms": 5000,
                        "sample_interval_ms": 100, "max_samples": 50}
        for field, value in requirements.items():
            assert type(capture.get(field)) is int and capture[field] == value, f"Wrong native backup requirement {field}: {name}"
        samples = capture.get("samples")
        assert isinstance(samples, list) and 3 <= len(samples) <= 50, f"Missing or unbounded native backup samples: {name}"
        assert type(capture.get("sample_count")) is int and capture["sample_count"] == len(samples), f"Wrong native backup sample count: {name}"
        stable, previous_elapsed, previous_uptime = 0, -1, -1
        state_fields = ("focused", "status_top_px", "navigation_bottom_px", "navigation_visible", "status_visible",
                        "owner_class", "owner_active", "window_kind")
        for sample in samples:
            assert isinstance(sample, dict), f"Invalid native backup sample: {name}"
            elapsed, uptime = sample.get("elapsed_ms"), sample.get("uptime_ms")
            assert type(elapsed) is int and 0 <= elapsed <= 5000 and previous_elapsed <= elapsed, f"Native backup sample exceeded 5s or reordered: {name}"
            assert type(uptime) is int and 0 <= uptime and previous_uptime <= uptime, f"Native backup samples are not chronological: {name}"
            if previous_uptime >= 0:
                assert uptime - previous_uptime == elapsed - previous_elapsed, f"Native backup sample clocks disagree: {name}"
            previous_elapsed, previous_uptime = elapsed, uptime
            before, after = sample.get("window_before"), sample.get("window_after")
            assert isinstance(before, dict) and isinstance(after, dict), f"Missing actual native backup window state: {name}"
            for state in (before, after):
                for field in ("focused", "navigation_visible", "status_visible", "owner_active"):
                    assert type(state.get(field)) is bool, f"Invalid native backup flag {field}: {name}"
                for field in ("status_top_px", "navigation_bottom_px", "requested_system_bars_appearance"):
                    assert type(state.get(field)) is int, f"Invalid native backup value {field}: {name}"
                assert 0 <= state["status_top_px"] <= 854 and 0 <= state["navigation_bottom_px"] <= 854, f"Invalid native backup bar insets: {name}"
                assert state.get("owner_class") == "com.dacaller.app.SettingsBackupActivity", f"Wrong native backup owner: {name}"
                assert state.get("window_kind") == kind, f"Wrong sampled native backup window: {name}"
            status_pixels, navigation_pixels = sample.get("contrasting_status_pixels"), sample.get("contrasting_navigation_pixels")
            width, height = sample.get("width_px"), sample.get("height_px")
            assert type(width) is int and type(height) is int and (width, height) in ((0, 0), (480, 854)), f"Wrong native backup sample size: {name}"
            assert type(status_pixels) is int and 0 <= status_pixels <= width // 2 * before["status_top_px"], f"Invalid backup left-half clock contrast count: {name}"
            assert type(navigation_pixels) is int and 0 <= navigation_pixels <= width * before["navigation_bottom_px"], f"Invalid backup navigation contrast count: {name}"
            good = (all(before[field] == after[field] for field in state_fields)
                    and before["focused"] and before["owner_active"] and before["status_visible"] and before["navigation_visible"]
                    and before["status_top_px"] > 0 and before["navigation_bottom_px"] >= 72
                    and (width, height) == (480, 854) and status_pixels >= 40 and navigation_pixels >= 40)
            stable = stable + 1 if good else 0
            assert type(sample.get("consecutive_valid_samples")) is int and sample["consecutive_valid_samples"] == stable, f"Native backup stability count disagrees with raw samples: {name}"
        assert stable == 3, f"Native backup bars were not valid for three consecutive compositor samples: {name}"
        final = samples[-1]
        state = final["window_before"]
        rows, channels = native_png_rows(files[name].read_bytes(), name)
        actual_status = saved_bar_pixels(rows, channels, 0, 240, 0, state["status_top_px"], dark, name)
        actual_navigation = saved_bar_pixels(rows, channels, 0, 480, 854 - state["navigation_bottom_px"], 854, dark, name)
        assert actual_status == final["contrasting_status_pixels"] and actual_navigation == final["contrasting_navigation_pixels"], f"Saved backup PNG pixels disagree with the final checked sample: {name}"
        assert actual_status >= 40 and actual_navigation >= 40, f"Saved backup PNG has unreadable native bar icons: {name}"
    return proof


root = Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/ui-snapshots")
presentation = {
    f"personalization-{page}-{theme}-{language}-{font}-{state}.png"
    for theme in ("light", "dark")
    for language in ("he", "en")
    for font in ("standard", "largefont")
    for page, states in (("contacts", ("expanded", "collapsed")),
                         ("recents", ("compact", "expanded")))
    for state in states
}
choosers = {f"personalization-chooser-{theme}-{kind}{suffix}.png"
            for theme in ("light", "dark") for kind in ("icons", "avatar_shapes")
            for suffix in ("", "-bottom")}
shapes = {f"personalization-{page}-shape-{shape}.png"
          for page in ("contacts", "widgets")
          for shape in ("circle", "rounded_square", "square", "hexagon")}
archive = {f"personalization-archive-{theme}.png" for theme in ("light", "dark")}
backup = {"personalization-backup-saved-light.png", "personalization-backup-saved-dark.png",
          "personalization-backup-restore-confirmation.png"}
required = presentation | choosers | shapes | archive | backup
assert len(required) == 53
all_files = [file for file in root.rglob("*.png") if file.is_file()]
counts = collections.Counter(file.name for file in all_files)
duplicates = sorted(name for name in required if counts[name] > 1)
assert not duplicates, f"Duplicate evidence files: {duplicates}"
files = {file.name: file for file in all_files}
missing = sorted(required - files.keys())
assert not missing, f"Missing personalization evidence (directories do not count): {missing}"
hashes = {}
for name in sorted(required):
    content = files[name].read_bytes()
    assert len(content) > 1000 and content[:8] == b"\x89PNG\r\n\x1a\n", f"Invalid PNG: {name}"
    assert struct.unpack(">II", content[16:24]) == (480, 854), f"Wrong native size: {name}"
    assert content[-12:] == b"\x00\x00\x00\x00IEND\xaeB`\x82", f"Truncated PNG: {name}"
    hashes[name] = hashlib.sha256(content).hexdigest()
backup_bar_proof = validate_backup_bars(root, backup, files, hashes)
insets_files = [file for file in root.rglob("window-insets.json") if file.is_file()]
assert len(insets_files) == 1, "Missing native insets evidence from call-layout suite"
insets = json.loads(insets_files[0].read_text())
assert insets.get("api") == 33 and insets.get("density_dpi") == 240
assert insets.get("navigation_bar_visible") is True and insets.get("navigation_bar_bottom_px", 0) >= 60
(root / "personalization-verification.json").write_text(json.dumps({
    "source_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
    "app_tree": subprocess.check_output(["git", "rev-parse", "HEAD:app"], text=True).strip(),
    "workflow_run_id": os.environ.get("GITHUB_RUN_ID"),
    "api": 33, "size": "480x854", "density_dpi": 240,
    "window_insets": insets,
    "required_real_png_files": len(required),
    "presentation_screenshots": len(presentation), "chooser_screenshots": len(choosers),
    "contact_and_widget_shape_screenshots": len(shapes),
    "archive_screenshots": len(archive), "backup_screenshots": len(backup),
    "sha256": hashes, "native_backup_bar_assertions": backup_bar_proof,
    "limits": "Synthetic contacts/photos/recents only. Backup uses production SAF intents with a test-provider result; no human document-picker usability or physical carrier/launcher behavior claim.",
}, indent=2) + "\n")
print(f"Verified {len(required)} complete personalization PNG files (plus the existing call/widget/settings matrix)")
