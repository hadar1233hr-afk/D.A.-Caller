"""Synthetic corruption contracts for the additive native-evidence verifier.

Generated images/proofs below are unit fixtures, never emulator/native evidence.
The CI still has to execute the actual40 native methods and all269 captures.
"""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import struct
import tempfile
import unittest
from unittest import mock
import xml.etree.ElementTree as ET
import zlib

REPO = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("da_caller_modern_native", REPO / "scripts/verify-modern-features-native.py")
gate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gate)
SOURCE, TREE = "a" * 40, "b" * 40


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)


def image(rgb=(180, 120, 60), method=0):
    """Independent PNG encoder; test every reversible row-filter family."""
    width, height = 480, 854
    actual = bytes(rgb) * width
    previous = bytes(len(actual))
    filtered = bytearray()
    for _ in range(height):
        filtered.append(method)
        for i, byte in enumerate(actual):
            left = actual[i - 3] if i >= 3 else 0
            above = previous[i]
            corner = previous[i - 3] if i >= 3 else 0
            if method == 1:
                predictor = left
            elif method == 2:
                predictor = above
            elif method == 3:
                predictor = (left + above) // 2
            elif method == 4:
                pa, pb, pc = abs(above - corner), abs(left - corner), abs(left + above - 2 * corner)
                predictor = left if pa <= pb and pa <= pc else above if pb <= pc else corner
            else:
                predictor = 0
            filtered.append((byte - predictor) & 255)
        previous = actual
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(filtered))) + chunk(b"IEND", b""))


def signed(rgb):
    value = 0xff000000 | rgb[0] << 16 | rgb[1] << 8 | rgb[2]
    return value - (1 << 32)


def native_identities():
    result = []
    for source in (REPO / "app/src/androidTest/java/com/dacaller/app").glob("*.java"):
        for method in re.findall(r"public\s+void\s+(test\w+)\s*\(\s*\)", source.read_text()):
            result.append(("com.dacaller.app." + source.stem, method))
    return result


class ModernNativeContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base_png = image()
        cls.opaque_png = image((20, 20, 20))
        cls.blend_png = image(tuple(int(fg * 89 / 255 + bg * 166 / 255 + 0.5) for bg, fg in zip((180, 120, 60), (20, 20, 20))))

    def setUp(self):
        # Hosted CI supplies its real run ID; synthetic fixtures must be isolated.
        environment = mock.patch.dict(os.environ, {"GITHUB_RUN_ID": "123456"})
        environment.start();self.addCleanup(environment.stop)
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.baseline, self.modern, self.xml = (self.root / name for name in ("baseline", "modern", "xml"))
        for path in (self.baseline, self.modern, self.xml):
            path.mkdir()

    def legacy_fixture(self):
        names = ["synthetic-baseline-%03d.png" % n for n in range(245)]
        digest = hashlib.sha256(self.base_png).hexdigest()
        for name in names:
            (self.baseline / name).write_bytes(self.base_png)
        offset = 122
        for name, count in gate.LEGACY.items():
            report = dict(source_sha=SOURCE, app_tree=TREE, workflow_run_id="123456", required_real_png_files=count)
            if name == "call-integration-verification.json":
                report["native_assertions"] = dict(api=33, real_systemui=True, notification_actions_acknowledged=True,
                    native_back_returns_to_app=True, real_phone_call_foreground_service=True)
            else:
                report["api"] = 33
            if name == "matrix-verification.json":
                report["all_files"] = names
            else:
                report["sha256"] = {frame: digest for frame in names[offset:offset + count]}
                offset += count
            (self.baseline / name).write_text(json.dumps(report))
        return names

    def write_junit(self, extra=""):
        suite = ET.Element("testsuite", tests="40", failures="0", errors="0", skipped="0")
        for owner, method in native_identities():
            ET.SubElement(suite, "testcase", classname=owner, name=method)
        if extra:
            document = ET.Element("testsuites")
            document.append(suite)
            document.append(ET.fromstring(extra))
        else:
            document = suite
        (self.xml / "native.xml").write_bytes(ET.tostring(document))
        return document

    def proofs_fixture(self):
        for name in gate.REQUIRED:
            data = self.opaque_png if name == "modern-island-opacity-100.png" else self.blend_png if name == "modern-island-opacity-35.png" else self.base_png
            (self.modern / name).write_bytes(data)
        presentation = dict(no_floating_dialpad=True, all_inline_actions_retained=True, back_collapses_without_dial=True, observations=[])
        for theme, language in (("light", "en"), ("dark", "he")):
            presentation["observations"].append(dict(theme=theme, language=language, font_scale=1.3,
                inline_expansion_dial_requests=0, icon_choices=9, selected_last_icon="signal",
                settings_bounds="[9,36][81,108]", dialpad_nav_bounds="[320,690][465,785]",
                switch_native_pointer_drag=True, switch_accessibility_checkable=True,
                switch_before=False, switch_after=True, switch_bounds="[300,400][450,472]"))
        backup = dict(independent_export_and_restore_opt_ins=True, insert_only_conflicts_preserved=True, settings_only_legacy_compatible=True,
            document_flow="synthetic test ContentProvider contract fixture", observations=[dict(legacy_version=1,
            legacy_contains_archive=False, bundle_version=2, export_archive_default=False, restore_archive_default=False,
            archive_policy_at_export="off", opt_out_added=0, opt_in_added=1, same_identity_conflict_retained_seconds=999,
            pending_recovery_after_success=False, raw_archive_uploaded=False, preexisting_rows_preserved=0,
            stored_rows_before=2, stored_rows_exported=2)])
        island = dict(role_held=True, hide_preserves_call_and_notification=True, settings_restore_on_home=True,
            new_call_ignores_previous_hidden_token=True, single_owned_surface=True, protected_status_icons_remain_visible=True,
            modem_end_removes_island=True, default_dialer="com.dacaller.app", data_origin="Android Telecom -> production PhoneSessionService",
            first_token="first-owned-token", second_token="second-owned-token", launcher_package="com.android.launcher3",
            foreground_evidence=[dict(stage=stage, foreground_package="com.android.launcher3", top_resumed=True, native_application_window_visible=True)
                for stage in ("active-home", "hide-home", "restore-home", "status-external", "new-call-home")],
            answers=[dict(token=token, number="0500099971", window_id=7, bounds="[100,200][250,275]", stable_millis=350,
                configured_touch_acceptance_delay_millis=0, input_method="native-pointer", native_caller_row=True)
                for token in ("first-owned-token", "second-owned-token")],
            modem_commands=[dict(operation=operation, id=str(index), serial="emulator-5554", number="0500099971", returncode=0, stdout="OK")
                for index, operation in enumerate(("call", "cancel", "call", "cancel"))],
            gestures=[dict(kind="completed_external_drag", before="[10,40][270,125]", after="[100,200][360,285]", x=5000, y=4000, call_state=4),
                dict(kind="cancelled_external_drag", normalized_position_preserved=True, geometry_restored=True, call_state=4),
                dict(kind="drag_started_on_end_control", end_confirmation_opened=False, call_state=4, before="[100,200][360,285]", after="[100,260][360,345]",
                    position_before=4000, position_after=5000, body_scrolling_required=False)],
            surfaces=[dict(stage=stage, bounds="[100,36][380,125]", token="first-owned-token", call_state=4, external_overlay_count=count)
                for stage, count in (("external-protected-status-fallback", 1), ("inapp-supported-central-status-area", 0), ("inapp-default-below-restored", 0))],
            transparency=dict(background_is_real_app_compositor=True, view_alpha=1, caller_alpha=1, background_alpha_35=89, rgb_tolerance=3,
                sample_region="[100,200][360,285]", samples=[dict(x=110 + n, y=250, background=signed((180, 120, 60)), opacity0=signed((180, 120, 60)),
                opacity35=signed(tuple(int(fg * 89 / 255 + bg * 166 / 255 + 0.5) for bg, fg in zip((180, 120, 60), (20, 20, 20)))), opacity100=signed((20, 20, 20))) for n in range(5)]))
        proofs = dict(zip(gate.PROOFS, (presentation, backup, island)))
        for name, value in proofs.items():
            value.update(api=33, density_dpi=240, font_scale=1.3, package="com.dacaller.app", version_name="0.13.0", version_code=22,
                physical_carrier_audio_verified=False, matching_key_upgrade_verified=False,
                captures={frame: dict(sha256=hashlib.sha256((self.modern / frame).read_bytes()).hexdigest(), width=480, height=854,
                    method="UiAutomation.takeScreenshot") for frame in gate.PROOFS[name]})
            (self.modern / name).write_text(json.dumps(value))
        return proofs

    def legacy(self):
        return gate.legacy(self.baseline, list(self.baseline.glob("*.png")), SOURCE, TREE)

    def test_authentic_legacy_schema_uses_nested_api_without_changing_old_gate(self):
        self.legacy_fixture()
        self.assertEqual(7, len(self.legacy()))
        nested = json.loads((self.baseline / "call-integration-verification.json").read_text())
        self.assertNotIn("api", nested)
        self.assertEqual(33, nested["native_assertions"]["api"])

    def test_modified_baseline_bytes_are_not_admitted_by_stale_legacy_hashes(self):
        names = self.legacy_fixture()
        (self.baseline / names[-1]).write_bytes(self.opaque_png)
        with self.assertRaisesRegex(AssertionError, "saved PNG bytes differ"):
            self.legacy()

    def test_old_source_or_workflow_cannot_be_relabelled_current(self):
        self.legacy_fixture()
        with self.assertRaisesRegex(AssertionError, "current source"):
            gate.legacy(self.baseline, list(self.baseline.glob("*.png")), "old-source", TREE)
        with mock.patch.dict(os.environ, {"GITHUB_RUN_ID": "different-run"}):
            with self.assertRaisesRegex(AssertionError, "another workflow"):
                self.legacy()

    def test_legacy_api_and_counter_types_are_strict(self):
        self.legacy_fixture()
        path = self.baseline / "personalization-verification.json"
        value = json.loads(path.read_text());value["required_real_png_files"] = "53";path.write_text(json.dumps(value))
        with self.assertRaisesRegex(AssertionError, "current source"):
            self.legacy()

    def test_source_enumerated40_named_native_cases_are_required(self):
        self.write_junit()
        self.assertEqual(40, gate.junit(self.xml, REPO / "app/src/androidTest/java/com/dacaller/app")["tests"])
        path = self.xml / "native.xml";value=ET.parse(path).getroot();value.remove(value[0]);value.set("tests", "39");path.write_bytes(ET.tostring(value))
        with self.assertRaisesRegex(AssertionError, "methods differ"):
            gate.junit(self.xml, REPO / "app/src/androidTest/java/com/dacaller/app")

    def test_junit_empty_suite_and_aggregate_counter_mismatch_are_rejected(self):
        self.write_junit('<testsuite tests="999" failures="0" errors="0" skipped="0"/>')
        with self.assertRaisesRegex(AssertionError, "counter mismatch"):
            gate.junit(self.xml, REPO / "app/src/androidTest/java/com/dacaller/app")
        value = self.write_junit();parent=ET.Element("testsuites", tests="41");parent.append(value);(self.xml / "native.xml").write_bytes(ET.tostring(parent))
        with self.assertRaisesRegex(AssertionError, "aggregate counter"):
            gate.junit(self.xml, REPO / "app/src/androidTest/java/com/dacaller/app")

    def test_native_failures_skips_and_duplicate_methods_are_rejected(self):
        for tag in ("failure", "error", "skipped", "duplicate"):
            with self.subTest(tag=tag):
                value = self.write_junit()
                if tag == "duplicate":
                    value.append(copy.deepcopy(value[0]));value.set("tests", "41")
                else:
                    ET.SubElement(value[0], tag)
                (self.xml / "native.xml").write_bytes(ET.tostring(value))
                with self.assertRaises(AssertionError):
                    gate.junit(self.xml, REPO / "app/src/androidTest/java/com/dacaller/app")

    def test_all_supported_png_row_filters_decode_actual_rgb(self):
        for method in range(5):
            with self.subTest(method=method):
                data = self.base_png if method == 0 else image(method=method)
                decoded = gate.png(data, "synthetic-filter-contract.png", True)
                self.assertEqual(signed((180, 120, 60)) & 0xffffffff, gate.argb(decoded, 234, 500))

    def test_png_crc_truncation_and_compressed_trailing_bytes_are_rejected(self):
        corrupt = bytearray(self.base_png);corrupt[44] ^= 1
        for data in (bytes(corrupt), self.base_png[:-1], self.base_png + b"unexpected"):
            with self.subTest(length=len(data)), self.assertRaises(AssertionError):
                gate.png(data, "synthetic-corrupt-contract.png")

    def test_no_dial_count_cannot_be_a_boolean_and_backup_count_is_typed(self):
        proofs = self.proofs_fixture()
        value = proofs["modern-presentation-native.json"];value["observations"][0]["inline_expansion_dial_requests"] = False
        with self.assertRaisesRegex(AssertionError, "no-dial"):
            gate.presentation(value)
        value = proofs["modern-backup-native.json"];value["observations"][0]["opt_out_added"] = False
        with self.assertRaisesRegex(AssertionError, "Wrong backup result"):
            gate.backup(value)

    def test_modem_identity_and_returncode_are_real_typed_acknowledgements(self):
        value = self.proofs_fixture()["modern-island-native.json"]
        value["modem_commands"][0]["id"] = None
        with self.assertRaisesRegex(AssertionError, "acknowledgements"):
            gate.island(value, {p.name:p for p in self.modern.glob("*.png")})
        value["modem_commands"][0]["id"] = "0";value["modem_commands"][0]["returncode"] = False
        with self.assertRaisesRegex(AssertionError, "acknowledgement"):
            gate.island(value, {p.name:p for p in self.modern.glob("*.png")})

    def test_saved_compositor_samples_are_independently_decoded(self):
        value = self.proofs_fixture()["modern-island-native.json"]
        images = {p.name:p for p in self.modern.glob("*.png")}
        gate.island(value, images)
        value["transparency"]["samples"][0]["opacity35"] += 1
        with self.assertRaisesRegex(AssertionError, "actual saved PNG"):
            gate.island(value, images)

    def test_home_evidence_and_answer_delay_cannot_be_asserted_without_actual_context(self):
        value = self.proofs_fixture()["modern-island-native.json"]
        images = {p.name:p for p in self.modern.glob("*.png")}
        value["foreground_evidence"][1]["foreground_package"] = "com.dacaller.app"
        with self.assertRaisesRegex(AssertionError, "another app"):
            gate.island(value, images)
        value["foreground_evidence"][1]["foreground_package"] = value["launcher_package"]
        value["answers"][0]["configured_touch_acceptance_delay_millis"] = 1000
        with self.assertRaisesRegex(AssertionError, "touch guard"):
            gate.island(value, images)
        value["answers"][0]["stable_millis"] = 1100
        value["answers"][1]["token"] = value["first_token"]
        with self.assertRaisesRegex(AssertionError, "distinct owned call"):
            gate.island(value, images)

    def test_end_control_drag_requires_real_motion_and_persisted_position(self):
        value = self.proofs_fixture()["modern-island-native.json"]
        images = {p.name:p for p in self.modern.glob("*.png")}
        child = value["gestures"][2];child["after"] = child["before"]
        with self.assertRaisesRegex(AssertionError, "actually move"):
            gate.island(value, images)

    def test_duplicate_json_keys_cannot_hide_conflicting_native_assertions(self):
        (self.modern / "conflict.json").write_text('{"api":32,"api":33}')
        with self.assertRaisesRegex(AssertionError, "Duplicate JSON key"):
            gate.one(self.modern, "conflict.json")

    def test_complete_synthetic_contract_and_unknown_or_duplicate_frame_rejection(self):
        self.legacy_fixture();self.proofs_fixture();self.write_junit()
        with mock.patch.object(gate.subprocess, "check_output", side_effect=[SOURCE, TREE]):
            report = gate.verify(self.baseline, self.modern, self.xml)
        self.assertEqual(269, report["total_required_png_files"])
        self.assertEqual(40, report["native_junit"]["tests"])
        (self.modern / "unexpected-diagnostic.png").write_bytes(self.base_png)
        with mock.patch.object(gate.subprocess, "check_output", side_effect=[SOURCE, TREE]), self.assertRaisesRegex(AssertionError, "exactly24"):
            gate.verify(self.baseline, self.modern, self.xml)
        (self.modern / "unexpected-diagnostic.png").unlink()
        extra=self.modern / "duplicate";extra.mkdir();(extra / next(iter(gate.REQUIRED))).write_bytes(self.base_png)
        with mock.patch.object(gate.subprocess, "check_output", side_effect=[SOURCE, TREE]), self.assertRaisesRegex(AssertionError, "exactly24"):
            gate.verify(self.baseline, self.modern, self.xml)


if __name__ == "__main__":
    unittest.main()
