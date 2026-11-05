#!/usr/bin/env python3
"""Fail closed on actual Telecom/reminder proof and a nonempty licensed business asset.

A missing production dataset is BLOCKED, never a passing search/details result.
No test-only business seed or synthetic CallLog data qualifies for publication.
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

root = Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/ui-snapshots")
required = {"callback-settings-light.png", "callback-settings-dark.png",
            "callback-list-light.png", "callback-list-dark.png",
            "business-tab-light.png", "business-tab-dark.png",
            "business-search.png", "business-detail.png",
            "incoming-swipe-resting.png", "incoming-swipe-dragging.png",
            "recents-unknown-number-actions.png", "recents-new-contact-editor.png"}
files = [path for path in root.rglob("*.png") if path.is_file()]
counts = collections.Counter(path.name for path in files)
assert all(counts[name] == 1 for name in required), "Missing/duplicate real callback/business PNG; an unavailable dataset is BLOCKED"
assert len(files) == len(counts) == 245, "Retain all233 previous PNGs and add exactly12 callback/business/swipe/contact captures"
assert not any(any(marker in name for marker in ("failed", "unreachable", "not-ready")) for name in counts), "Failure diagnostics do not qualify as passing evidence"
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


def flags(section, names):
    for name in names:
        assert section.get("checks", {}).get(name) is True, f"Missing actual native assertion: {name}"


def number(value):
    return re.sub(r"[^0-9]", "", str(value)).endswith("500099971")


def call_row(value):
    assert type(value.get("id")) is int and value["id"] > 0
    assert number(value.get("number")) and value.get("date", 0) > 0
    assert type(value.get("duration")) is int and value["duration"] >= 0
    assert value.get("type") in (1, 3)
    assert value.get("new") in (0, 1) and value.get("is_read") in (0, 1)
    return value["id"]


def bars(value):
    assert value.get("status", 0) >= 40 and value.get("navigation", 0) >= 40


def large_font(value):
    # Android reports Configuration.fontScale as float32, e.g.1.2999999523.
    return type(value) in (int, float) and abs(value - 1.3) <= 0.000001


def https(value):
    from urllib.parse import urlsplit
    result = urlsplit(value)
    assert result.scheme == "https" and result.hostname and not result.username and not result.password


contact = one("recents-create-contact-native.json")
assert contact.get("api") == 33 and contact.get("origin") == "actual emulator Telecom missed call"
assert contact.get("number") == "0500099971"
for flag in ("exact_number_prefilled", "new_contact_without_existing_id", "no_automatic_dial",
             "cancel_keeps_contacts_and_call_rows", "back_to_recents"):
    assert contact.get(flag) is True, f"Missing native create-contact assertion: {flag}"
for frame in ("actions_bars", "editor_bars"):
    bars(contact.get(frame, {}))
assert contact.get("call_log_before_editor") == contact.get("call_log_after_cancel")
assert len(contact.get("call_log_after_cancel", [])) == 1
assert contact["call_log_after_cancel"][0]["type"] == 3
assert [item.get("operation") for item in contact.get("modem_commands", [])] == ["call", "cancel"]
for command in contact["modem_commands"]:
    assert command.get("returncode") == 0 and command.get("number") == "0500099971"
    assert re.fullmatch(r"emulator-[0-9]+", str(command.get("serial", "")))

proof = one("callback-business-native.json")
assert proof.get("api") == 33 and proof.get("size") == "480x854"
assert proof.get("density_dpi") == 240 and large_font(proof.get("font_scale"))
assert proof.get("physical_cellular_audio_verified") is False
assert "production private OS alarm" in proof.get("data_origin", "")
reminder = proof.get("reminder", {})
flags(reminder, ("default_off_native_opt_in", "actual_modem_calls_and_protected_missed_receiver",
                 "actual_contact_identity_and_grouped_counts", "private_os_alarm_delivered",
                 "reminder_load_and_delivery_preserve_call_rows_and_read_flags",
                 "actual_active_connection_over_one_second_resolves", "later_missed_call_restores_pending",
                 "native_settings_and_list_light_dark", "direct_notification_details_never_autodial",
                 "native_back_to_previous_settings"))
commands = reminder.get("modem_commands", [])
# Extra cancel in teardown is separate cleanup, outside the scenario proof.
assert [command.get("operation") for command in commands] == ["call", "cancel"] * 4
assert len({command.get("id") for command in commands}) == 8
assert len({command.get("serial") for command in commands}) == 1
for command in commands:
    assert command.get("returncode") == 0 and command.get("number") == "0500099971"
    assert re.fullmatch(r"emulator-[0-9]+", str(command.get("serial", "")))
    if command["operation"] == "call":
        assert "OK" in command.get("stdout", "")
calls = reminder.get("calls", [])
assert len(calls) == 4
rows = [item.get("row", {}) for item in calls]
ids = [call_row(row) for row in rows]
assert len(set(ids)) == 4 and [row["type"] for row in rows] == [3, 3, 1, 3]
assert [item.get("disconnect_cause") for item in calls][0:2] == [5, 5]
assert calls[-1].get("disconnect_cause") == 5
for row in (rows[0], rows[1], rows[3]):
    assert row["duration"] == 0 and row["new"] == 1 and row["is_read"] == 0
assert rows[2]["duration"] >= 2 and reminder.get("answered_call") == rows[2]
assert reminder.get("answer_method") == "production-real-touchscreen-swipe"
swipe = reminder.get("incoming_swipe", {})
assert swipe.get("input_method") == "UiAutomation.injectInputEvent SOURCE_TOUCHSCREEN" and swipe.get("answer_mode") == "ios"
assert swipe.get("threshold_px") == 96 and swipe.get("drag_translation_px", 0) > 0
for flag in ("tap_stayed_ringing", "short_drag_stayed_ringing", "cancelled_drag_stayed_ringing",
             "cancel_reset_translation", "completed_swipe_activated_owned_call", "incoming_header_outside_swipe", "drag_keeps_prompt_fully_visible"):
    assert swipe.get(flag) is True, f"Missing real native incoming swipe assertion: {flag}"
for capture in ("resting_bars", "dragging_bars"):
    bars(swipe.get(capture, {}))
before = reminder.get("pending_before_connection", {})
after = reminder.get("pending_after_later_miss", {})
for entry in (before, after):
    assert entry.get("key") == "0500099971" and number(entry.get("number"))
    assert entry.get("name") == "נועה תזכורת לשיחה" and entry.get("callback_attempts") == 0
assert before.get("missed_count") == 2 and before.get("latest_missed_id") == ids[1]
assert after.get("missed_count") == 1 and after.get("missed_today", 0) >= 3 and after.get("latest_missed_id") == ids[3]
assert after.get("latest_missed_at", 0) > rows[2]["date"]
assert reminder.get("rows_before_delivery") == rows[:2]
retained = reminder.get("rows_after_all_reminder_reads", [])
assert len(retained) == len(rows)
for observed, original in zip(retained, rows):
    for field in ("id", "number", "date", "duration", "type"):
        assert observed[field] == original[field], "Reminder must retain actual call identities"
reads = reminder.get("read_observations", [])
assert len(reads) >= 4
for observation in reads:
    assert observation.get("observed_at", 0) > 0
    assert observation.get("before") == observation.get("after"), "Reminder query changed real rows or read flags"
    for value in observation["before"]:
        call_row(value)
alarm = reminder.get("alarm", {})
assert alarm.get("method") == "production-private-immutable-PendingIntent-via-AlarmManager.setAndAllowWhileIdle"
assert alarm.get("accelerated_delivery") is True and alarm.get("waited_until_user_cutoff") is False
assert alarm.get("app_background_before_alarm") is True
assert alarm.get("scheduled_at", 0) > 0
assert alarm["scheduled_at"] < alarm.get("due", 0) <= alarm.get("post_time", 0) <= alarm.get("observed_at", 0)
assert alarm.get("id") == 1504 and alarm.get("channel") == "dacaller_callbacks_v1" and alarm.get("category") == "reminder"
assert re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", str(alarm.get("day")))
assert alarm.get("people", 0) >= 1 and alarm.get("missed", 0) >= 2
observations = reminder.get("observations", [])
settings = [item for item in observations if item.get("event") == "settings-opt-in"]
lists = [item for item in observations if item.get("event") == "reminder-list-palette"]
assert [item.get("palette") for item in settings] == ["light", "dark"]
assert [item.get("palette") for item in lists] == ["light", "dark"]
for item in settings:
    assert item.get("enabled") is True and item.get("minute") == 1439
    bars(item.get("bar_contrast", {}))
assert settings[0].get("input_method") == "native-pointer"
for item in lists:
    assert item.get("root_rtl") is True and item.get("number_ltr") is True and large_font(item.get("font_scale"))
    assert item.get("name") == "נועה תזכורת לשיחה" and "2" in item.get("counts", "")
    bars(item.get("bar_contrast", {}))
cleanup = reminder.get("cleanup", {})
assert cleanup.get("method") == "production-own-missed-notification-delete-PendingIntent-after-read-only-proof"
assert cleanup.get("no_call_log_delete") is True and cleanup.get("all_missed_rows_read") is True
assert ids[3] in cleanup.get("acknowledged_ids", [])
assert set(cleanup.get("acknowledged_ids", [])) <= {ids[0], ids[1], ids[3]}
cleaned = cleanup.get("retained_rows", [])
assert [call_row(row) for row in cleaned] == ids
for row, original in zip(cleaned, rows):
    for key in ("id", "number", "date", "duration", "type"):
        assert row[key] == original[key]
    if row["type"] == 3:
        assert row["new"] == 0 and row["is_read"] == 1

business = proof.get("business", {})
assert business.get("readiness") == "AVAILABLE", "Production directory unavailable: feature/data verification BLOCKED"
flags(business, ("actual_nonempty_licensed_bundled_dataset", "native_small_contacts_tab", "native_light_dark_rtl_ltr_phone",
                 "native_phone_query_keeps_source_identity", "details_show_source_license_and_source_date",
                 "private_contacts_unchanged", "no_automatic_business_call",
                 "native_detail_back_and_private_public_switch_preserve_query", "bounded_production_pages"))
dataset = business.get("dataset", {})
assert type(dataset.get("record_count")) is int and 0 < dataset["record_count"] <= 2_000_000
assert re.fullmatch(r"[a-f0-9]{64}", str(dataset.get("sha256", "")))
assert dataset.get("version") and dataset.get("source_name") and dataset.get("license") and dataset.get("updated_at")
https(dataset.get("source_url", "")); https(dataset.get("license_url", ""))
record = business.get("selected_record", {})
assert type(record.get("id")) is int and record["id"] > 0
assert record.get("name") and record.get("phone") and record.get("source_id") and record.get("source_license") and record.get("source_updated_at")
https(record.get("source_url", ""))
assert business.get("query") == record["phone"] and business.get("business_numbers_dialed") == 0
assert business.get("private_contacts_before") == business.get("private_contacts_after")
assert re.fullmatch(r"[a-f0-9]{64}", str(business.get("private_contacts_digest_before", "")))
assert business.get("private_contacts_digest_before") == business.get("private_contacts_digest_after")
assert 0 < business.get("first_page_rows", 0) <= 50 and 0 < business.get("search_rows", 0) <= 50
for field in ("source_url", "source_license", "source_updated_at"):
    assert record[field] in business.get("detail_text", "")
palettes = business.get("palettes", [])
assert [item.get("palette") for item in palettes] == ["light", "dark"]
for item in palettes:
    assert item.get("native_entry_tag") == "contacts_directory_tab"
    assert re.fullmatch(r"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]", str(item.get("entry_bounds", "")))
    assert item.get("root_rtl") is True and item.get("phone_ltr") is True and large_font(item.get("font_scale"))
    assert item.get("first_record_id") == record["id"]
    bars(item.get("bar_contrast", {}))
for name, expected in {"matrix-verification.json": 122, "personalization-verification.json": 53,
                       "call-integration-verification.json": 8, "island-verification.json": 36,
                       "external-island-verification.json": 8, "followup-verification.json": 6}.items():
    assert one(name).get("required_real_png_files") == expected
(root / "callback-business-verification.json").write_text(json.dumps({
    "source_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
    "app_tree": subprocess.check_output(["git", "rev-parse", "HEAD:app"], text=True).strip(),
    "workflow_run_id": os.environ.get("GITHUB_RUN_ID"),
    "api": 33, "size": "480x854", "density_dpi": 240, "font_scale": 1.3,
    "required_real_png_files": 12, "total_required_png_files": 245,
    "sha256": hashes, "native_assertions": proof,
    "limits": "Actual emulator Telecom and production call-log rows, real OS accelerated private-alarm delivery and the actual licensed packaged business dataset. Accelerated delivery does not mean the test waited until21:00. No real business number was dialed. Physical SIM/carrier/audio/JellyStar, actual overnight/Doze/OEM delivery and matching-key installed-beta9 upgrade remain NOT TESTED.",
}, ensure_ascii=False, indent=2) + "\n")
print("Verified12 real callback/business/swipe/contact captures and native proof; all245 required PNGs retained")
