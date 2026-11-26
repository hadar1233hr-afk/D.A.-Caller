#!/usr/bin/env python3
"""Require complete, real PNG evidence before CI can publish a debug preview."""
import collections
import json
import struct
import subprocess
import os
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/ui-snapshots")
styles = ("graphite", "pixel", "emerald", "frost", "ivory")
matrix = {
    f"call-{style}-{color}-{panel}-{scale}.png"
    for style in styles
    for color in ("light", "dark")
    for panel in ("normal", "audio", "more", "keypad")
    for scale in ("standard", "largefont")
}
long_names = {
    f"call-{style}-dark-{panel}-longname-{language}.png"
    for style in styles
    for panel in ("normal", "audio")
    for language in ("he", "en")
}
widgets = {
    f"widget-{color}-{scenario}.png"
    for color in ("light", "dark")
    for scenario in ("quick-1x1", "contact-2x1", "favorites-2x2", "favorites-4x2",
                     "permission", "empty", "two-favorites")
} | {"widget-reconfigured.png", "widget-light-favorites-2x2-largefont.png",
     "widget-dark-favorites-2x2-largefont.png"}
settings = {f"settings-{color}{suffix}.png"
            for color in ("light", "dark") for suffix in ("", "-bottom")}
english = {"call-pixel-light-audio-english-no-bluetooth.png"}
expected = matrix | long_names | widgets | settings | english
# UiAutomation.executeShellCommand is not a shell: accidental '&&' previously
# produced directories named *.png. Count and validate regular files only.
files = [p for p in root.rglob("*.png") if p.is_file()]
counts = collections.Counter(p.name for p in files)
duplicates = sorted(name for name in expected if counts[name] > 1)
if duplicates:
    raise SystemExit(f"Ambiguous duplicate screenshots: {duplicates}")
images = {p.name: p for p in files}
missing = expected - images.keys()
if missing:
    raise SystemExit(f"Missing screenshot files (directories do not count): {sorted(missing)}")
for name in expected:
    content = images[name].read_bytes()
    if len(content) < 1000 or content[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(f"Invalid screenshot PNG: {name}")
    if struct.unpack(">II", content[16:24]) != (480, 854):
        raise SystemExit(f"Unexpected screenshot size: {name}")
    if content[-12:] != b"\x00\x00\x00\x00IEND\xaeB`\x82":
        raise SystemExit(f"Incomplete screenshot PNG: {name}")
inset_files = [p for p in root.rglob("window-insets.json") if p.is_file()]
if len(inset_files) != 1:
    raise SystemExit("Missing or ambiguous native window-insets diagnostics")
insets = json.loads(inset_files[0].read_text())
if not (insets.get("api") == 33 and insets.get("density_dpi") == 240
        and insets.get("navigation_bar_visible") is True
        and insets.get("navigation_bar_bottom_px", 0) >= 60):
    raise SystemExit(f"Expected native three-button navigation not verified: {insets}")
(root / "matrix-verification.json").write_text(json.dumps({
    "window_insets": insets,
    "source_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
    "app_tree": subprocess.check_output(["git", "rev-parse", "HEAD:app"], text=True).strip(),
    "workflow_run_id": os.environ.get("GITHUB_RUN_ID"),
    "api": 33,
    "size": "480x854",
    "density_dpi": 240,
    "styles": 5,
    "themes": 2,
    "panels": 4,
    "font_scales": [1.0, 1.3],
    "matrix_screenshots": len(matrix),
    "long_name_screenshots": len(long_names),
    "widget_screenshots": len(widgets),
    "settings_screenshots": len(settings),
    "english_without_bluetooth_screenshots": len(english),
    "required_real_png_files": len(expected),
    "all_files": sorted(images),
    "data": "Synthetic preview fixtures only; no real cellular call validation",
}, indent=2))
print(f"Verified {len(expected)} complete API33 PNG files: "
      f"{len(matrix)} call matrix + {len(long_names)} long names + "
      f"{len(english)} English/no Bluetooth + {len(widgets)} widgets + {len(settings)} settings")
