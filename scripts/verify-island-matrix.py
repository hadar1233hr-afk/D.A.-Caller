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
             "native_preference_persistence", "app_main_integration"):
    assert proof.get(flag) is True, f"Native island check did not pass: {flag}"
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
