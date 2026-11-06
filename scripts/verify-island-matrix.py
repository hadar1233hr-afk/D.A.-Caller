#!/usr/bin/env python3
"""Require real native island renders, action acknowledgement and list-state proof."""
import collections
import hashlib
import json
import os
import struct
import subprocess
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/ui-snapshots")
presentation = {
    f"island-{style}-{theme}-{language}-{state}.png"
    for style in ("apple", "glass", "emerald")
    for theme in ("light", "dark")
    for language in ("he", "en")
    for state in ("compact", "expanded")
}
states = {f"island-state-{state}.png" for state in ("incoming", "outgoing", "held", "noaudio")}
settings = {f"island-settings-{theme}{suffix}.png" for theme in ("light", "dark") for suffix in ("", "-bottom")}
app_layouts = {f"island-app-{page}-{mode}.png" for page in ("contacts", "recents") for mode in ("compact", "expanded")}
required = presentation | states | settings | app_layouts
assert len(required) == 36
files = [p for p in root.rglob("*.png") if p.is_file()]
counts = collections.Counter(p.name for p in files)
assert not any(counts[name] != 1 for name in required), "Missing or duplicate island PNG files"
images = {p.name: p for p in files}
hashes = {}
for name in sorted(required):
    data = images[name].read_bytes()
    assert len(data) > 1000 and data[:8] == b"\x89PNG\r\n\x1a\n", f"Invalid PNG: {name}"
    assert struct.unpack(">II", data[16:24]) == (480, 854), f"Wrong native dimensions: {name}"
    assert data[-12:] == b"\x00\x00\x00\x00IEND\xaeB`\x82", f"Truncated PNG: {name}"
    hashes[name] = hashlib.sha256(data).hexdigest()
proof_files = [p for p in root.rglob("island-native.json") if p.is_file()]
assert len(proof_files) == 1, "Missing native island interaction proof"
proof = json.loads(proof_files[0].read_text())
assert proof.get("api") == 33
for flag in ("real_renderer_and_controller", "matrix_native_layout_assertions",
             "native_action_dispatch_and_acknowledgement", "confirmed_end_cancel_and_accept",
             "disabled_ui_preserves_session", "content_margin_and_list_anchor_restored",
             "pause_stops_surface_and_resume_restores",
             "activity_recreation_preserves_fixture_session_and_query",
             "customization_preview_and_bottom_controls", "animations_lifecycle",
             "native_preference_persistence", "app_main_integration",
             "native_settings_bar_contrast"):
    assert proof.get(flag) is True, f"Native island check did not pass: {flag}"
bar_captures = proof.get("nativeBarCaptures")
assert isinstance(bar_captures, list) and len(bar_captures) == len(required), "All 36 island PNGs require native bar captures"
assert collections.Counter(capture.get("filename") for capture in bar_captures) == collections.Counter(required), "Native bar capture filenames do not match the 36 required PNGs"
for capture in bar_captures:
    name = capture["filename"]
    assert capture.get("validated") is True, f"Native bar capture did not pass: {name}"
    assert capture.get("dark") is ("-dark" in name or name in states or name in app_layouts), f"Wrong bar contrast palette: {name}"
    assert capture.get("capture_method") == "UiAutomation.takeScreenshot", f"Not a native compositor capture: {name}"
    assert capture.get("sha256") == hashes[name], f"Checked native Bitmap does not match the saved PNG: {name}"
    expected = {"status_sample_left_px": 0, "status_sample_right_px": 240,
                "required_navigation_px": 72, "required_contrasting_pixels_per_bar": 40,
                "required_consecutive_valid_samples": 3, "deadline_ms": 5000,
                "sample_interval_ms": 100, "max_samples": 50}
    for field, value in expected.items():
        assert type(capture.get(field)) is int and capture[field] == value, f"Wrong native bar capture requirement {field}: {name}"
    samples = capture.get("samples")
    assert isinstance(samples, list) and 3 <= len(samples) <= 50, f"Missing or unbounded native bar samples: {name}"
    assert type(capture.get("sample_count")) is int and capture["sample_count"] == len(samples), f"Wrong native bar sample count: {name}"
    stable = 0
    previous_elapsed = -1
    previous_uptime = -1
    state_fields = ("focused", "status_top_px", "navigation_bottom_px", "navigation_visible", "status_visible")
    for sample in samples:
        elapsed, uptime = sample.get("elapsed_ms"), sample.get("uptime_ms")
        assert type(elapsed) is int and 0 <= elapsed <= 5000 and previous_elapsed <= elapsed, f"Native bar capture exceeded 5s or reordered samples: {name}"
        assert type(uptime) is int and previous_uptime <= uptime, f"Native bar samples are not chronological: {name}"
        if previous_uptime >= 0:
            assert uptime - previous_uptime == elapsed - previous_elapsed, f"Native bar sample clocks disagree: {name}"
        previous_elapsed, previous_uptime = elapsed, uptime
        before, after = sample.get("window_before"), sample.get("window_after")
        assert isinstance(before, dict) and isinstance(after, dict), f"Missing actual native window state: {name}"
        for state in (before, after):
            for field in ("focused", "navigation_visible", "status_visible"):
                assert type(state.get(field)) is bool, f"Invalid native window flag {field}: {name}"
            for field in ("status_top_px", "navigation_bottom_px", "requested_system_bars_appearance"):
                assert type(state.get(field)) is int, f"Invalid native window value {field}: {name}"
        status_pixels, navigation_pixels = sample.get("contrasting_status_pixels"), sample.get("contrasting_navigation_pixels")
        width, height = sample.get("width_px"), sample.get("height_px")
        assert type(width) is int and type(height) is int and (width, height) in ((0, 0), (480, 854)), f"Wrong native bar sample dimensions: {name}"
        assert type(status_pixels) is int and 0 <= status_pixels <= width // 2 * max(0, before["status_top_px"]), f"Invalid left-half clock contrast count: {name}"
        assert type(navigation_pixels) is int and 0 <= navigation_pixels <= width * max(0, before["navigation_bottom_px"]), f"Invalid navigation contrast count: {name}"
        good = (all(before[field] == after[field] for field in state_fields)
                and before["focused"] and before["status_visible"] and before["navigation_visible"]
                and before["status_top_px"] > 0 and before["navigation_bottom_px"] >= 72
                and status_pixels >= 40 and navigation_pixels >= 40)
        stable = stable + 1 if good else 0
        assert type(sample.get("consecutive_valid_samples")) is int and sample["consecutive_valid_samples"] == stable, f"Native bar stability count disagrees with raw samples: {name}"
    assert stable == 3, f"Native bars were not valid for 3 consecutive compositor samples: {name}"
insets_files = [p for p in root.rglob("window-insets.json") if p.is_file()]
assert len(insets_files) == 1
insets = json.loads(insets_files[0].read_text())
assert insets.get("api") == 33 and insets.get("density_dpi") == 240
assert insets.get("navigation_bar_visible") is True and insets.get("navigation_bar_bottom_px", 0) >= 60
(root / "island-verification.json").write_text(json.dumps({
    "source_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
    "app_tree": subprocess.check_output(["git", "rev-parse", "HEAD:app"], text=True).strip(),
    "workflow_run_id": os.environ.get("GITHUB_RUN_ID"),
    "api": 33, "size": "480x854", "density_dpi": 240, "font_scale": 1.3,
    "window_insets": insets,
    "required_real_png_files": len(required),
    "style_theme_language_presentation_screenshots": len(presentation),
    "additional_state_screenshots": len(states), "settings_screenshots": len(settings),
    "actual_app_layout_screenshots": len(app_layouts),
    "sha256": hashes, "native_assertions": proof,
    "limits": "Real production in-app island renderer/controller and native windows. A non-exported DEBUG fixture supplies fictional call state and acknowledgements; real Telecom actions are separately unit-tested. No physical SIM, audio, Bluetooth or Jelly Star verification.",
}, indent=2) + "\n")
print(f"Verified {len(required)} native dynamic-island PNG files and interaction assertions")
