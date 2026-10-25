#!/usr/bin/env python3
"""Validate all new personalization renders as genuine complete native PNG files."""
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
    "sha256": hashes,
    "limits": "Synthetic contacts/photos/recents only. Backup uses production SAF intents with a test-provider result; no human document-picker usability or physical carrier/launcher behavior claim.",
}, indent=2) + "\n")
print(f"Verified {len(required)} complete personalization PNG files (plus the existing call/widget/settings matrix)")
