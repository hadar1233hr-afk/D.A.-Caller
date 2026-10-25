#!/usr/bin/env python3
"""Require native SystemUI notification and Back-navigation screenshot evidence."""
import collections
import hashlib
import json
import os
import struct
import subprocess
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/ui-snapshots")
expected = {
    "call-integration-notification-incoming-he.png",
    "call-integration-notification-outgoing-he.png",
    "call-integration-notification-active-private-he.png",
    "call-integration-notification-active-speaker-he.png",
    "call-integration-notification-active-private-en.png",
    "call-integration-notification-active-speaker-en.png",
    "call-integration-return-contacts.png",
    "call-integration-return-settings.png",
}
files = [file for file in root.rglob("*.png") if file.is_file()]
counts = collections.Counter(file.name for file in files)
assert not any(counts[name] != 1 for name in expected), "Missing or duplicate call integration screenshots"
images = {file.name: file for file in files}
hashes = {}
for name in sorted(expected):
    data = images[name].read_bytes()
    assert len(data) > 1000 and data[:8] == b"\x89PNG\r\n\x1a\n", f"Invalid PNG: {name}"
    assert struct.unpack(">II", data[16:24]) == (480, 854), f"Wrong native size: {name}"
    assert data[-12:] == b"\x00\x00\x00\x00IEND\xaeB`\x82", f"Incomplete PNG: {name}"
    hashes[name] = hashlib.sha256(data).hexdigest()
evidence_files = [file for file in root.rglob("call-integration-native.json") if file.is_file()]
assert len(evidence_files) == 1, "Missing native call notification/navigation assertions"
evidence = json.loads(evidence_files[0].read_text())
assert evidence["api"] == 33 and evidence["real_systemui"] is True
assert evidence["notification_actions_acknowledged"] is True and evidence["native_back_returns_to_app"] is True
(root / "call-integration-verification.json").write_text(json.dumps({
    "source_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
    "app_tree": subprocess.check_output(["git", "rev-parse", "HEAD:app"], text=True).strip(),
    "workflow_run_id": os.environ.get("GITHUB_RUN_ID"),
    "required_real_png_files": len(expected),
    "sha256": hashes,
    "native_assertions": evidence,
    "limits": "SystemUI renders the production notification builder with a debug-only synthetic call session. Native button dispatch is acknowledged by the fixture; Telecom/carrier operations are separately unit-tested and require physical-device verification.",
}, indent=2) + "\n")
print(f"Verified {len(expected)} native notification/Back PNG files")
