#!/usr/bin/env python3
"""Require actual missed-call Telecom delivery, local version history and six native PNGs."""
import collections
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/ui-snapshots")
required = {"missed-single-he.png", "missed-multiple-he.png",
            "changelog-light.png", "changelog-light-bottom.png",
            "changelog-dark.png", "changelog-dark-bottom.png"}
files = [path for path in root.rglob("*.png") if path.is_file()]
counts = collections.Counter(path.name for path in files)
assert all(counts[name] == 1 for name in required), "Missing or duplicate follow-up PNG"
assert len(files) == len(counts) == 245, "Keep all233 earlier PNGs and add exactly12 callback/business/swipe/contact renders"
assert not any("failed" in name or "unreachable" in name or "not-ready" in name for name in counts), "Failure diagnostics are not passing evidence"
images = {path.name: path for path in files}
hashes = {}
for name in sorted(required):
    data = images[name].read_bytes()
    assert len(data) > 1000 and data[:8] == b"\x89PNG\r\n\x1a\n", name
    assert struct.unpack(">II", data[16:24]) == (480, 854), name
    assert data[-12:] == b"\x00\x00\x00\x00IEND\xaeB`\x82", name
    hashes[name] = hashlib.sha256(data).hexdigest()


def one(name):
    paths = [path for path in root.rglob(name) if path.is_file()]
    assert len(paths) == 1, (name, len(paths))
    return json.loads(paths[0].read_text())


def rect(value):
    match = re.fullmatch(r"\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]", value)
    assert match, f"Missing actual native bounds: {value}"
    left, top, right, bottom = map(int, match.groups())
    assert 0 <= left < right <= 480 and 0 <= top < bottom <= 854
    return left, top, right, bottom


def flags(section, expected):
    for flag in expected:
        assert section.get("checks", {}).get(flag) is True, f"Native follow-up check failed: {flag}"


def row(value, new, read):
    assert type(value.get("id")) is int and value["id"] > 0
    assert value.get("type") == 3 and value.get("duration") == 0 and value.get("date", 0) > 0
    assert re.sub(r"[^0-9]", "", value.get("number", "")).endswith("500099971")
    assert value.get("new") == new and value.get("is_read") == read
    return value["id"]


proof = one("followup-native.json")
assert proof.get("api") == 33 and proof.get("density_dpi") == 240 and proof.get("font_scale") == 1.3
assert proof.get("size") == "480x854" and proof.get("physical_cellular_audio_verified") is False
assert "Telecom protected missed-call broadcast" in proof.get("data_origin", "")
missed = proof.get("missed", {})
flags(missed, ("real_modem_telecom_to_protected_receiver", "default_dialer_owns_missed_notification",
               "actual_contact_identity", "single_and_multiple_counts", "notification_survives_call_service",
               "native_open_returns_to_recents", "native_clear_acknowledged", "call_log_rows_retained",
               "no_new_stock_dialer_missed_notification"))
role = missed.get("role", {})
assert role.get("role_held") is True and role.get("default_dialer") == "com.dacaller.app"
stock = role.get("system_dialer", "")
assert re.fullmatch(r"[A-Za-z0-9_.]+", stock) and stock != "com.dacaller.app"
assert set(missed.get("stock_notification_packages", [])) == {stock, "com.android.server.telecom"}
commands = missed.get("modem_commands", [])
assert [item.get("operation") for item in commands] == ["call", "cancel"] * 3
assert len({item.get("id") for item in commands}) == 6
assert len({item.get("serial") for item in commands}) == 1
for command in commands:
    assert command.get("number") == "0500099971" and command.get("returncode") == 0
    assert re.fullmatch(r"emulator-[0-9]+", str(command.get("serial", "")))
    if command["operation"] == "call":
        assert "OK" in command.get("stdout", "")
calls = missed.get("calls", [])
assert [item.get("index") for item in calls] == [1, 2, 3]
tokens = {item.get("token") for item in calls}
assert len(tokens) == 3
for token in tokens:
    assert re.fullmatch(r"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}", str(token))
ids = []
for call in calls:
    assert call.get("ringing_confirmed") is True and call.get("disconnect_cause") == 5
    ids.append(row(call.get("call_log_row", {}), 1, 0))
assert len(set(ids)) == 3
notifications = missed.get("notifications", [])
assert [item.get("stage") for item in notifications] == ["single", "multiple", "third"]
for item, count, selected in zip(notifications, (1, 2, 1), ([ids[0]], ids[:2], [ids[2]])):
    assert item.get("package") == "com.dacaller.app" and item.get("id") == 1503
    assert item.get("channel") == "dacaller_missed_v1" and item.get("category") == "missed_call"
    assert item.get("count") == count and item.get("platform_count", 0) > 0
    assert item.get("name") == item.get("text") == "מיכל שיחה שלא נענתה"
    assert set(item.get("snapshot_ids", [])) == set(selected)
    assert item.get("native_caller_visible") is True and item.get("active_call_absent") is True
    assert item.get("incall_service_absent") is True and item.get("platform_events", 0) > 0
    assert item.get("title") and item.get("own_key") in item.get("all_native_notification_keys", [])
    assert "|com.dacaller.app|1503|" in item["own_key"]
    assert item.get("stock_notification_keys") == []
    for key in item["all_native_notification_keys"]:
        assert f"|{stock}|" not in key and "|com.android.server.telecom|" not in key
assert missed.get("stock_notification_keys_before") == missed.get("stock_notification_keys_after") == []
actions = missed.get("actions", [])
assert [item.get("action") for item in actions] == ["clear", "open"]
for item, expected_ids, method in zip(actions, (set(ids[:2]), set(ids)), ("native-pointer", "native-accessibility-click")):
    rect(item.get("bounds", ""))
    assert item.get("input_method") == method and item.get("acknowledged") is True
    assert item.get("notification_absent") is True and item.get("state_count") == 0
    assert {row(value, 0, 1) for value in item.get("call_log_rows", [])} == expected_ids
assert actions[1].get("main_page") == "recents" and actions[1].get("main_focused") is True

changelog = proof.get("changelog", {})
flags(changelog, ("actual_settings_entry_opens_activity", "catalog_newest_first_and_current_version",
                  "hebrew_english_native_ui", "native_light_dark_bar_contrast", "large_font_rtl_and_text_layout",
                  "native_expand_collapse_and_bottom_reachable", "back_returns_to_settings"))
versions = changelog.get("versions", [])
current = changelog.get("current_version")
assert versions and len(versions) >= 19 and len(versions) == len(set(versions)) and versions[0] == current
assert versions[-1] == "0.1.0"


def version_key(value):
    match = re.fullmatch(r"([0-9]+)\.([0-9]+)\.([0-9]+)(?:-beta\.([0-9]+))?", value)
    assert match, value
    major, minor, patch, beta = match.groups()
    return int(major), int(minor), int(patch), beta is None, int(beta or 0)


assert versions == sorted(versions, key=version_key, reverse=True)
events = changelog.get("observations", [])
palettes = [item for item in events if item.get("event") == "changelog-palette"]
assert [item.get("palette") for item in palettes] == ["light", "dark"]
for item in palettes:
    assert item.get("activity") == "com.dacaller.app.ChangelogActivity"
    assert item.get("root_rtl") is True and abs(item.get("font_scale", 0) - 1.3) < 0.0001 and item.get("density_dpi") == 240
    assert item.get("title") == "יומן שינויים" and current in item.get("installed_label", "")
    assert item.get("installed_version_drawn_ltr") is True
    assert item.get("versions_in_native_tree") == versions and item.get("current_expanded") is True
    assert item.get("oldest_version") == "0.1.0" and item.get("oldest_expanded") is True and item.get("bottom_reachable") is True
    assert item.get("nav_bottom_px") == 72 and item.get("status_top_px") == 36
    assert rect(item.get("footer_bounds", ""))[3] <= 782
    entry = item.get("settings_entry", {})
    assert entry.get("tag") == "settings_changelog" and entry.get("input_method") == "native-pointer"
    assert entry.get("version_drawn_ltr") is True
    rect(entry.get("bounds", ""))
    for place in ("top", "bottom"):
        contrast = item.get("bar_contrast", {}).get(place, {})
        assert contrast.get("status", 0) >= 40 and contrast.get("navigation", 0) >= 40
english = [item for item in events if item.get("event") == "changelog-english"]
assert len(english) == 1 and english[0].get("title") == "What's new" and english[0].get("root_rtl") is False
assert english[0].get("current_version") == current and english[0].get("catalog_bilingual") is True
amoled = [item for item in events if item.get("event") == "changelog-amoled"]
assert len(amoled) == 1 and amoled[0].get("background_argb") == -16777216
back = [item for item in events if item.get("event") == "changelog-native-back"]
assert len(back) == 4
for item in back:
    assert item.get("main_page") == "settings" and item.get("main_focused") is True and item.get("footer_entry_present") is True

# Existing mandatory matrices remain independent gates and must all be present.
for filename, count in {"matrix-verification.json": 122, "personalization-verification.json": 53,
                        "call-integration-verification.json": 8, "island-verification.json": 36,
                        "external-island-verification.json": 8}.items():
    assert one(filename).get("required_real_png_files") == count
(root / "followup-verification.json").write_text(json.dumps({
    "source_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
    "app_tree": subprocess.check_output(["git", "rev-parse", "HEAD:app"], text=True).strip(),
    "workflow_run_id": os.environ.get("GITHUB_RUN_ID"),
    "api": 33, "size": "480x854", "density_dpi": 240, "font_scale": 1.3,
    "required_real_png_files": 6, "total_required_png_files": 245,
    "sha256": hashes, "native_assertions": proof,
    "limits": "The six new screenshots come from actual Android UI. Missed calls use an emulator modem, Android Telecom's protected default-dialer broadcast and the production receiver. Native notification clear/open actions preserve platform call-log rows. Hardware SIM/audio/JellyStar and matching-key upgrades remain unverified. Permission-denial fallbacks are separately unit-tested; the native test does not revoke dangerous permissions or fake a platform broadcast.",
}, ensure_ascii=False, indent=2) + "\n")
print("Verified6 actual missed-call/changelog renders and native assertions; all245 required PNGs retained")
