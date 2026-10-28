#!/usr/bin/env python3
"""Require actual modem/Telecom external-window proof in addition to all219 renders."""
import collections
import hashlib
import json
import os
import re
from pathlib import Path
import struct
import subprocess
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/ui-snapshots")
required = {f"external-{name}.png" for name in (
    "launcher-incoming-compact", "launcher-incoming-expanded", "launcher-active-expanded",
    "settings-active-expanded", "end-confirmation", "inapp-return",
    "permission-denied-launcher", "ended-launcher",
)}
files = [path for path in root.rglob("*.png") if path.is_file()]
counts = collections.Counter(path.name for path in files)
assert all(counts[name] == 1 for name in required), "Missing or duplicate real Telecom external PNG"
images = {path.name: path for path in files}
hashes = {}
for name in sorted(required):
    data = images[name].read_bytes()
    assert len(data) > 1000 and data[:8] == b"\x89PNG\r\n\x1a\n", f"Invalid PNG: {name}"
    assert struct.unpack(">II", data[16:24]) == (480, 854), f"Wrong native dimensions: {name}"
    assert data[-12:] == b"\x00\x00\x00\x00IEND\xaeB`\x82", f"Truncated PNG: {name}"
    hashes[name] = hashlib.sha256(data).hexdigest()
proof_files = [path for path in root.rglob("external-island-native.json") if path.is_file()]
assert len(proof_files) == 1, "Missing actual modem/Telecom external integration evidence"
proof = json.loads(proof_files[0].read_text())
assert proof.get("api") == 33 and proof.get("density_dpi") == 240 and proof.get("font_scale") == 1.3
checks = proof.get("checks", {})
for flag in ("real_modem_telecom_incallservice", "native_answer_acknowledged_by_telecom",
             "native_mute_acknowledged_by_telecom", "native_speaker_capability_guard",
             "return_back_preserves_call_query_without_duplicate", "launcher_and_other_app_real_windows",
             "native_end_cancel_confirm_and_cleanup", "disabled_or_permission_denied_preserves_real_call",
             "modem_end_removes_window", "incoming_notification_handoff_and_fallback"):
    assert checks.get(flag) is True, f"Actual external integration check failed: {flag}"
assert proof.get("physical_cellular_audio_verified") is False, "Emulator must not claim physical carrier/audio proof"
observations = proof.get("observations", [])
role = proof.get("role", {})
assert role.get("role_held") is True and role.get("default_dialer") == "com.dacaller.app", "Production app must actually hold Telecom's default dialer role"
speaker = proof.get("speaker_result", {})
mask = speaker.get("supported_route_mask")
transitions = speaker.get("transitions")
assert isinstance(mask, int) and isinstance(transitions, list) and speaker.get("reason"), "Actual speaker capability evidence missing"
if speaker.get("status") == "PASS":
    assert mask & 8 and mask & 7, "Both speaker and a private route must really be available"
    assert any(item.get("action") == "SPEAKER_ON" for item in transitions)
    assert any(item.get("action") == "SPEAKER_OFF" for item in transitions)
    route = speaker.get("initial_route")
    for item in transitions:
        assert item.get("from") == route and item.get("acknowledged") is True
        route = item.get("to")
        assert item.get("supported_route_mask", 0) & route
        if item.get("action") == "SPEAKER_ON":
            assert route == 8
        else:
            assert item.get("action") == "SPEAKER_OFF" and route in (1, 2, 4) and mask & route
    assert speaker.get("final_route") == route and route != 8
else:
    assert speaker.get("status") == "UNAVAILABLE", "Unknown speaker verification status"
    assert mask == speaker.get("initial_route") == speaker.get("final_route") == 8
    assert not transitions and speaker.get("unavailable_controls_absent") is True
handoff = {item.get("stage"): item for item in observations if item.get("event") == "incoming-notification"}
for stage, high in {"permission-denied": True, "overlay-attached": False, "second-overlay-attached": False,
                    "preference-disabled": True, "preference-reenabled": False,
                    "permission-revoked": True, "permission-regranted": False}.items():
    item = handoff.get(stage, {})
    assert item.get("channel") == ("dacaller_incoming_v5" if high else "dacaller_ongoing_v5"), f"Wrong native call channel: {stage}"
    assert item.get("importance") == (4 if high else 2) and item.get("full_screen_intent") is high
    assert item.get("heads_up_visible") is high, f"Actual native fallback/handoff visibility failed: {stage}"
    if high:
        popup = re.fullmatch(r"\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]", item.get("heads_up_bounds", ""))
        assert popup
        left, top, right, bottom = map(int, popup.groups())
        assert 0 <= left < right <= 480 and 0 <= top < bottom <= 854
    else:
        assert item.get("heads_up_bounds") is None
    assert item.get("overlay_windows") == (0 if high else 1) and item.get("call_state") == 2
    assert item.get("answer_intent") is True and item.get("decline_intent") is True
    assert str(item.get("template", "")).endswith("$CallStyle") and item.get("caller") == "נועה שיחת אמולטור"
    if stage in ("permission-denied", "permission-revoked"):
        assert item.get("overlay_permission") is False
clear = {item.get("stage"): item for item in observations if item.get("event") == "unobscured-native-input"}
for stage in ("first-incoming", "second-incoming"):
    item = clear.get(stage, {})
    assert 350 <= item.get("wait_millis", 0) <= 8000 and item.get("blocked_samples", -1) >= 0
    rectangle = re.fullmatch(r"\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]", item.get("island_bounds", ""))
    assert rectangle, f"No native unobscured incoming input bounds: {stage}"
    left, top, right, bottom = map(int, rectangle.groups())
    assert 0 <= left < right <= 480 and 0 <= top < bottom <= 854
commands = [item["response"] for item in observations if item.get("event", "").startswith("modem-")]
assert sum(item.get("operation") == "call" for item in commands) == 2, "Two actual modem calls required"
assert any(item.get("operation") == "cancel" for item in commands), "Modem cancellation proof missing"
for command in commands:
    assert command.get("number") == "0500099971" and command.get("returncode") == 0
    assert str(command.get("serial", "")).startswith("emulator-"), "Not a simulated emulator modem"
    if command.get("operation") == "call":
        assert "OK" in command.get("stdout", ""), "Modem did not acknowledge incoming call"
tokens = {item["token"] for item in observations if item.get("token")}
assert len(tokens) == 2, "Production service must issue distinct UUIDs for the two real calls"
for item in observations:
    assert item.get("overlay_windows", 0) <= 1, "Duplicate external window"
# Preserve each old mandatory matrix. They are generated by their existing strict
# verifiers earlier in run-android-ui.sh, not replaced by a smaller new suite.
previous = {"matrix-verification.json": 122, "personalization-verification.json": 53,
            "call-integration-verification.json": 8, "island-verification.json": 36}
for name, count in previous.items():
    path = root / name
    assert path.is_file(), f"Existing verification missing: {name}"
    assert json.loads(path.read_text())["required_real_png_files"] == count, f"Reduced previous coverage: {name}"
assert len(images) == 227, f"Expected219 previous and8 actual Telecom PNGs, found{len(images)}"
(root / "external-island-verification.json").write_text(json.dumps({
    "source_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
    "app_tree": subprocess.check_output(["git", "rev-parse", "HEAD:app"], text=True).strip(),
    "workflow_run_id": os.environ.get("GITHUB_RUN_ID"),
    "api": 33, "size": "480x854", "density_dpi": 240, "font_scale": 1.3,
    "required_real_png_files": len(required), "total_required_png_files": 227,
    "sha256": hashes, "native_assertions": proof,
    "limits": "Actual simulated modem calls, Android Telecom, production service and application-overlay windows. Physical SIM/carrier, audible routing, Bluetooth, lockscreen and Jelly Star not verified.",
}, indent=2) + "\n")
print("Verified8 actual Telecom external-island PNGs and call/audio/lifecycle assertions; all227 required renders retained")
