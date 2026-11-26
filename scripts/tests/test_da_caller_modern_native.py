"""Synthetic corruption contracts for the additive native-evidence verifier.

Generated images/proofs below are unit fixtures, never emulator/native evidence.
The CI still has to execute the actual40 native methods and all272 captures.
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


def widget_configuration_fixture():
    """Synthetic actual-Activity schema only; never a native launch/visual result."""
    observations = []
    def geometry(bounds):
        return dict(bounds=bounds, native_ax_bounds=bounds, window_id=17, stable_samples=2,
                    view_window_focus=True, native_window_focus=True)
    for amoled in (False, True):
        heading = "Choose a contact" if amoled else "בחר איש קשר"
        bg, surface, ink, muted = (((0, 0, 0), (10, 10, 10), (249, 241, 232), (242, 229, 221)) if amoled
                                   else ((246, 240, 232), (255, 253, 252), (55, 41, 31), (76, 53, 42)))
        observations.append(dict(frame="modern-nex-widget-" + ("amoled-en" if amoled else "light-he") + ".png",
            activity="com.dacaller.app.WidgetConfigure", theme="amoled" if amoled else "light", language="en" if amoled else "he",
            amoled=amoled, layout_direction="LTR" if amoled else "RTL", font_scale=1.3,
            widget_id=2147483000, intent_widget_id=2147483000, widget_provider_info="null",
            foreground=dict(component="com.dacaller.app/.WidgetConfigure",
                resumed_record="topResumedActivity=ActivityRecord{123 u0 com.dacaller.app/.WidgetConfigure} t1}",
                top_resumed=True, native_application_window_visible=True, window_id=17),
            root_geometry=geometry("[0,36][480,818]"), heading_geometry=geometry("[96,48][456,120]"),
            search_geometry=geometry("[24,149][456,230]"), back_geometry=geometry("[24,48][96,120]"),
            background_argb=signed(bg), surface_argb=signed(surface), heading_argb=signed(ink), search_argb=signed(ink), hint_argb=signed(muted),
            status_bar_argb=signed(bg), navigation_bar_argb=signed(bg),
            heading=dict(text=heading, text_size_sp=22, font_weight=750, font_variation="'wght' 750", layout_width_px=348,
                content_width_px=348, content_height_px=54,
                lines=[dict(start_utf16=0, end_utf16=len(heading), ellipsis_count=0, left_px=0, right_px=315, bottom_px=51)]),
            search_hint=dict(text="Search name or number" if amoled else "חיפוש שם או מספר", text_size_sp=16, font_weight=600,
                font_variation="'wght' 600", measured_width_px=330, available_width_px=384, font_top_px=-36, font_bottom_px=10,
                baseline_px=51, padding_top_px=15, padding_bottom_px=15, height_px=81),
            background_samples=[dict(x=3+n, y=39, argb=signed(bg)) for n in range(5)],
            selection_actions=0, native_pointer_inputs=0, provider_writes_requested=0, provider_change_notifications=0,
            preferences_unchanged=True, telecom_call_unchanged=True, finished=True))
    return dict(scope="Actual WidgetConfigure Activity; synthetic unbound ID configuration preview", launcher_host_binding="NOT_TESTED",
                preferences_restored=True, observations=observations)


def nex_island_fixture():
    """Synthetic owned-Telecom schema; no real modem call is placed here."""
    return dict(frame="modern-nex-island-active-he.png", style_id="nex", language="he", layout_direction="RTL",
        data_origin="Android Telecom -> production PhoneSessionService -> live CallIslandView", token="first-owned-token",
        bound_view_token="first-owned-token", call_state=4, bound_view_state=4, number="0500099971", same_owned_call=True,
        single_owned_surface=True, preferences_restored=True, previous_layout_restored=True, extra_modem_commands=0,
        previous_bounds="[45,48][435,180]", restored_bounds="[45,48][435,180]", view_alpha=1, scale_x=1, scale_y=1,
        body_top_argb=signed((52, 39, 32)), body_bottom_argb=signed((36, 26, 23)), edge_argb=signed((99, 77, 67)),
        accent_argb=signed((246, 167, 115)), timer_argb=signed((246, 167, 115)),
        foreground=dict(component="com.dacaller.app/.MainActivity", resumed_record="topResumedActivity=ActivityRecord{123 u0 com.dacaller.app/.MainActivity} t1}",
            top_resumed=True, native_application_window_visible=True, window_id=19),
        geometry=dict(bounds="[45,48][435,180]", native_ax_bounds="[45,48][435,180]", window_id=19, stable_samples=2,
            view_window_focus=True, native_window_focus=True),
        background_samples=[dict(x=238+n, y=53, argb=signed((44, 32, 27))) for n in range(5)])


def status_protection_fixture():
    """Synthetic API33-shaped records, never an actual emulator observation."""
    raw = ("1: name='435d73f StatusBar', id=85, displayId=0, inputConfig=NOT_FOCUSABLE | TRUSTED_OVERLAY, "
           "alpha=1.00, frame=[0,35][320,59], globalScale=1.000000, applicationInfo.name=, "
           "applicationInfo.token=<null>, touchableRegion=[0,35][320,59], ownerPid=758, ownerUid=10157, "
           "dispatchingTimeout=5000ms, hasToken=true, touchOcclusionMode=BLOCK_UNTRUSTED")
    return dict(system_ui_package="com.android.systemui", system_ui_uid=10157,
        system_ui_package_record="package:com.android.systemui uid:10157",
        native_window=dict(index=1, id=85, name="435d73f StatusBar", uid=10157,
            input_config="NOT_FOCUSABLE | TRUSTED_OVERLAY", frame="[0,35][320,59]", raw=raw),
        window_has_input_token=True, window_visible=True, mapped_status_bounds="[0,0][480,37]",
        display_transform=dict(scale_x=1.5, scale_y=1.5009, translate_x=0, translate_y=-52.5308,
            raw="\n  Display: 0\n    logicalSize=480x854\n        transform (ROT_0) (SCALE TRANSLATE)\n"
                "            1.5000  -0.0000  0.0000\n            -0.0000  1.5009  -52.5308\n"
                "            0.0000  0.0000  1.0000"),
        root_status_inset=36, root_status_bars_visible=True, clock_lane_bounds="[0,0][90,37]",
        right_icon_lane_bounds="[390,0][480,37]", stable_samples=2, clock_lane_clear=True, right_icon_lane_clear=True,
        proof_scope="Native SystemUI StatusBar window, root WindowInsets and protected geometric lanes; icon visual presence is reviewed in the real capture")


def readable_status_fixture():
    """A readable fallback is the only positive branch of the fixed profile."""
    def geometry(bounds):
        return dict(bounds=bounds, native_ax_bounds=bounds, window_id=11, stable_samples=2,
                    view_window_focus=True, native_window_focus=True)
    return dict(stage="inapp-protected-readable-status-fallback", bounds="[45,48][435,180]",
        token="first-owned-token", call_state=4, external_overlay_count=0,
        display_width=480, display_height=854, density_dpi=240, font_scale=1.3,
        status_inset=36, safe_lane_width=264, minimum_readable_width=285, capsule_minimum_width=276,
        caller_full_text="0500099971", caller_code_points=10, caller_visible_prefix="0500099971", caller_visible_code_points=10,
        caller_ellipsis_start_utf16=0, caller_ellipsis_count_utf16=0, caller_line_count=1, caller_ellipsize="END",
        caller_minimum_text="050009\u2026", caller_minimum_text_width=74.2, caller_padding_horizontal=0,
        position_mode="status_area", custom_position_set=False,
        capsule_geometry=geometry("[45,48][435,180]"), caller_geometry=geometry("[69,65][249,93]"),
        status_bar_protection=status_protection_fixture())


class ReadableStatusContracts(unittest.TestCase):
    """Only offline corruption checks, never Android/native PASS claims."""
    def test_fixed_profile_positive_fixture_is_readable_fallback(self):
        value = readable_status_fixture()
        self.assertEqual(36, gate.readable_status_surface(value))
        self.assertGreater(value["capsule_minimum_width"], value["safe_lane_width"])

    def test_actual_visible_prefix_cannot_be_less_than_six_code_points(self):
        value = readable_status_fixture()
        value.update(caller_visible_prefix="05000", caller_visible_code_points=5,
                     caller_ellipsis_start_utf16=5, caller_ellipsis_count_utf16=5)
        with self.assertRaisesRegex(AssertionError, "retain six"):
            gate.readable_status_surface(value)

    def test_whole_shorter_identity_and_supplementary_unicode_prefix_are_valid(self):
        for full, visible, start, omitted in (("א😀ב", "א😀ב", 0, 0), ("😀אב12ג789", "😀אב12ג", 7, 3)):
            with self.subTest(full=full):
                value = readable_status_fixture()
                value.update(caller_full_text=full, caller_code_points=len(full), caller_visible_prefix=visible,
                    caller_visible_code_points=len(visible), caller_ellipsis_start_utf16=start,
                    caller_ellipsis_count_utf16=omitted, caller_minimum_text=full[:6] + ("\u2026" if len(full) > 6 else ""))
                self.assertEqual(36, gate.readable_status_surface(value))

    def test_prefix_and_utf16_ellipsis_metrics_must_match_actual_full_text(self):
        changes = (dict(caller_minimum_text="different\u2026"), dict(caller_code_points=9),
                   dict(caller_visible_prefix="different"), dict(caller_visible_code_points=9),
                   dict(caller_ellipsis_start_utf16=6, caller_ellipsis_count_utf16=3),
                   dict(caller_ellipsis_start_utf16=1), dict(caller_ellipsis_start_utf16=-1),
                   dict(caller_ellipsis_count_utf16=11), dict(caller_ellipsis_start_utf16=True))
        for change in changes:
            with self.subTest(change=change), self.assertRaises(AssertionError):
                value = readable_status_fixture();value.update(change);gate.readable_status_surface(value)
        value = readable_status_fixture()
        value.update(caller_full_text="😀abcdefghi", caller_code_points=10, caller_minimum_text="😀abcde\u2026",
                     caller_ellipsis_start_utf16=1, caller_ellipsis_count_utf16=10)
        with self.assertRaisesRegex(AssertionError, "split a code point"):
            gate.readable_status_surface(value)

    def test_paint_and_padding_metrics_must_be_finite_typed_and_fit_actual_viewport(self):
        for key, invalid in (("caller_minimum_text_width", (float("nan"), float("inf"), -1, 0, True, "74.2", 181)),
                             ("caller_padding_horizontal", (-1, 180, True, "0"))):
            for observed in invalid:
                with self.subTest(key=key, observed=observed), self.assertRaises(AssertionError):
                    value = readable_status_fixture();value[key] = observed;gate.readable_status_surface(value)

    def test_reported_minimum_and_capsule_minimum_cannot_fabricate_a_supported_lane(self):
        for change in (dict(minimum_readable_width=276), dict(capsule_minimum_width=264, minimum_readable_width=264),
                       dict(safe_lane_width=390), dict(stage="inapp-supported-central-status-area")):
            with self.subTest(change=change), self.assertRaises(AssertionError):
                value = readable_status_fixture();value.update(change);gate.readable_status_surface(value)

    def test_fallback_must_be_below_bar_and_wide_enough_for_independently_derived_minimum(self):
        for bounds, caller in (("[45,47][435,179]", "[69,64][249,92]"), ("[45,48][319,180]", "[69,65][249,93]")):
            with self.subTest(bounds=bounds), self.assertRaises(AssertionError):
                value = readable_status_fixture();value["bounds"] = bounds
                value["capsule_geometry"].update(bounds=bounds, native_ax_bounds=bounds)
                value["caller_geometry"].update(bounds=caller, native_ax_bounds=caller)
                value["minimum_readable_width"] = 285 if bounds.endswith("179]") else 276
                gate.readable_status_surface(value)

    def test_wrong_mode_or_custom_position_cannot_count_as_status_placement(self):
        for change in (dict(position_mode="below"), dict(position_mode=None), dict(custom_position_set=True)):
            with self.subTest(change=change), self.assertRaisesRegex(AssertionError, "actual status_area mode"):
                value = readable_status_fixture();value.update(change);gate.readable_status_surface(value)

    def test_actual_layout_remains_single_line_end_ellipsized(self):
        for change in (dict(caller_line_count=2), dict(caller_line_count=True), dict(caller_ellipsize="START")):
            with self.subTest(change=change), self.assertRaisesRegex(AssertionError, "one line"):
                value = readable_status_fixture();value.update(change);gate.readable_status_surface(value)

    def test_caller_geometry_must_be_inside_capsule_focused_stable_and_same_native_window(self):
        changes = (dict(native_ax_bounds="[70,65][250,93]"), dict(stable_samples=1), dict(stable_samples=True),
                   dict(window_id=12), dict(window_id=False), dict(view_window_focus=False), dict(native_window_focus=False),
                   dict(bounds="[40,65][249,93]", native_ax_bounds="[40,65][249,93]"))
        for change in changes:
            with self.subTest(change=change), self.assertRaises(AssertionError):
                value = readable_status_fixture();value["caller_geometry"].update(change);gate.readable_status_surface(value)

    def test_fixed_native_display_density_font_and_status_inset_are_not_relaxed(self):
        for key, invalid in (("display_width", 540), ("display_height", 960), ("density_dpi", 200),
                             ("font_scale", 1.0), ("status_inset", 0), ("status_inset", 855)):
            with self.subTest(key=key), self.assertRaises(AssertionError):
                value = readable_status_fixture();value[key] = invalid;gate.readable_status_surface(value)

    def test_native_bar_requires_actual_package_uid_visible_token_and_two_samples(self):
        for change in (dict(system_ui_package="com.dacaller.app"), dict(system_ui_uid=0), dict(system_ui_uid=True),
                       dict(system_ui_package_record="package:com.android.systemui uid:1000"),
                       dict(system_ui_package_record="package:com.android.systemui.other uid:10157"),
                       dict(window_visible=False), dict(window_has_input_token=False), dict(stable_samples=1),
                       dict(root_status_bars_visible=False), dict(root_status_inset=35)):
            with self.subTest(change=change), self.assertRaises(AssertionError):
                value = readable_status_fixture();value["status_bar_protection"].update(change);gate.readable_status_surface(value)

    def test_native_bar_raw_window_records_cannot_be_relabelled(self):
        for change in (dict(name="NotificationShade"), dict(uid=1000), dict(id=True), dict(frame="[0,36][320,60]"),
                       dict(raw="unrecorded"), dict(input_config="NOT_VISIBLE | TRUSTED_OVERLAY"),
                       dict(input_config="NO_INPUT_CHANNEL | TRUSTED_OVERLAY"), dict(input_config="NOT_FOCUSABLE"),
                       dict(input_config="TRUSTED_OVERLAY | UNKNOWN_FLAG")):
            with self.subTest(change=change), self.assertRaises(AssertionError):
                value = readable_status_fixture();value["status_bar_protection"]["native_window"].update(change)
                gate.readable_status_surface(value)
        for before, after in (("hasToken=true", "hasToken=false"), ("displayId=0", "displayId=1"), ("alpha=1.00", "alpha=0.00")):
            with self.subTest(after=after), self.assertRaises(AssertionError):
                value = readable_status_fixture();window = value["status_bar_protection"]["native_window"]
                window["raw"] = window["raw"].replace(before, after);gate.readable_status_surface(value)

    def test_native_display_transform_and_mapped_strip_are_independently_checked(self):
        for key, invalid in (("scale_x", 1), ("scale_y", float("nan")), ("translate_y", -50), ("raw", "unrecorded")):
            with self.subTest(key=key), self.assertRaises(AssertionError):
                value = readable_status_fixture();value["status_bar_protection"]["display_transform"][key] = invalid
                gate.readable_status_surface(value)
        for key, invalid in (("mapped_status_bounds", "[0,0][480,36]"), ("clock_lane_bounds", "[0,0][89,37]"),
                             ("right_icon_lane_bounds", "[391,0][480,37]"), ("clock_lane_clear", False), ("right_icon_lane_clear", False)):
            with self.subTest(key=key), self.assertRaises(AssertionError):
                value = readable_status_fixture();value["status_bar_protection"][key] = invalid
                gate.readable_status_surface(value)

    def test_capsule_overlap_cannot_be_hidden_by_native_icon_clear_flags(self):
        for bounds in ((45, 0, 435, 120), (0, 0, 89, 36), (391, 0, 480, 36)):
            with self.subTest(bounds=bounds), self.assertRaisesRegex(AssertionError, "overlaps protected"):
                gate.status_bar_protection(status_protection_fixture(), bounds, 36)

    def test_exact_three_surfaces_owned_overlay_counts_and_wide_native_limitation_are_preserved(self):
        proof = dict(compatible_wide_native_lane="NOT_TESTED_FIXED_480PX_VIEWPORT", surfaces=[
            dict(stage="external-protected-status-fallback", bounds="[45,48][435,180]", token="first-owned-token", call_state=4, external_overlay_count=1),
            readable_status_fixture(),
            dict(stage="inapp-default-below-restored", bounds="[45,48][435,180]", token="first-owned-token", call_state=4, external_overlay_count=0)])
        gate.status_surfaces(proof, "first-owned-token")
        for change in ("extra", "missing", "supported", "overlay", "external-top", "default-top", "wide"):
            with self.subTest(change=change), self.assertRaises(AssertionError):
                value = copy.deepcopy(proof)
                if change == "extra": value["surfaces"].append(copy.deepcopy(value["surfaces"][0]))
                if change == "missing": value["surfaces"].pop()
                if change == "supported": value["surfaces"][1]["stage"] = "inapp-supported-central-status-area"
                if change == "overlay": value["surfaces"][1]["external_overlay_count"] = 1
                if change == "external-top": value["surfaces"][0]["bounds"] = "[45,47][435,179]"
                if change == "default-top": value["surfaces"][2]["bounds"] = "[45,35][435,167]"
                if change == "wide": value["compatible_wide_native_lane"] = "PASS"
                gate.status_surfaces(value, "first-owned-token")


class ModernNativeContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base_png = image()
        cls.opaque_png = image((20, 20, 20))
        cls.blend_png = image(tuple(int(fg * 89 / 255 + bg * 166 / 255 + 0.5) for bg, fg in zip((180, 120, 60), (20, 20, 20))))
        cls.widget_light_png, cls.widget_amoled_png = image((246, 240, 232)), image((0, 0, 0))
        cls.nex_island_png = image((44, 32, 27))

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
            if name in gate.WIDGET:
                data = self.widget_amoled_png if "amoled" in name else self.widget_light_png
            if name == gate.NEX_ISLAND:
                data = self.nex_island_png
            (self.modern / name).write_bytes(data)
        presentation = dict(no_floating_dialpad=True, all_inline_actions_retained=True, back_collapses_without_dial=True, observations=[],
                            widget_configuration=widget_configuration_fixture())
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
        island = dict(nex_active=nex_island_fixture(), role_held=True, hide_preserves_call_and_notification=True, settings_restore_on_home=True,
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
            compatible_wide_native_lane="NOT_TESTED_FIXED_480PX_VIEWPORT",
            surfaces=[dict(stage="external-protected-status-fallback", bounds="[45,48][435,180]", token="first-owned-token", call_state=4, external_overlay_count=1),
                readable_status_fixture(), dict(stage="inapp-default-below-restored", bounds="[45,48][435,180]", token="first-owned-token", call_state=4, external_overlay_count=0)],
            transparency=dict(background_is_real_app_compositor=True, view_alpha=1, caller_alpha=1, background_alpha_35=89, rgb_tolerance=3,
                sample_region="[100,200][360,285]", samples=[dict(x=110 + n, y=250, background=signed((180, 120, 60)), opacity0=signed((180, 120, 60)),
                opacity35=signed(tuple(int(fg * 89 / 255 + bg * 166 / 255 + 0.5) for bg, fg in zip((180, 120, 60), (20, 20, 20)))), opacity100=signed((20, 20, 20))) for n in range(5)]))
        proofs = dict(zip(gate.PROOFS, (presentation, backup, island)))
        for name, value in proofs.items():
            value.update(api=33, density_dpi=240, font_scale=1.3, package="com.dacaller.app", version_name="0.14.0", version_code=23,
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
            gate.presentation(value, {p.name:p for p in self.modern.glob("*.png")})
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

    def test_previous_release_wrong_version_and_untyped_code_are_rejected(self):
        self.legacy_fixture()
        self.proofs_fixture()
        self.write_junit()
        name = next(iter(gate.PROOFS))
        path = self.modern / name
        valid = path.read_text()
        for version, code in (("0.13.0", 22), ("0.14.0-beta.1", 23),
                              ("0.14.1", 23), ("0.14.0", 22),
                              ("0.14.0", "23"), ("0.14.0", True)):
            value = json.loads(valid)
            value.update(version_name=version, version_code=code)
            path.write_text(json.dumps(value))
            with self.subTest(version=version, code=code), \
                 mock.patch.object(gate.subprocess, "check_output", side_effect=[SOURCE, TREE]), \
                 mock.patch.object(gate, "png"), \
                 self.assertRaisesRegex(AssertionError, "Wrong native package/version/configuration"):
                gate.verify(self.baseline, self.modern, self.xml)
        path.write_text(valid)

    def test_duplicate_json_keys_cannot_hide_conflicting_native_assertions(self):
        (self.modern / "conflict.json").write_text('{"api":32,"api":33}')
        with self.assertRaisesRegex(AssertionError, "Duplicate JSON key"):
            gate.one(self.modern, "conflict.json")

    def test_complete_synthetic_contract_and_unknown_or_duplicate_frame_rejection(self):
        self.legacy_fixture();self.proofs_fixture();self.write_junit()
        with mock.patch.object(gate.subprocess, "check_output", side_effect=[SOURCE, TREE]):
            report = gate.verify(self.baseline, self.modern, self.xml)
        self.assertEqual("0.14.0", report["version_name"])
        self.assertEqual(23, report["version_code"])
        self.assertEqual(27, report["required_real_png_files"])
        self.assertEqual(272, report["total_required_png_files"])
        self.assertEqual(40, report["native_junit"]["tests"])
        (self.modern / "unexpected-diagnostic.png").write_bytes(self.base_png)
        with mock.patch.object(gate.subprocess, "check_output", side_effect=[SOURCE, TREE]), self.assertRaisesRegex(AssertionError, "exactly27"):
            gate.verify(self.baseline, self.modern, self.xml)
        (self.modern / "unexpected-diagnostic.png").unlink()
        extra=self.modern / "duplicate";extra.mkdir();(extra / next(iter(gate.REQUIRED))).write_bytes(self.base_png)
        with mock.patch.object(gate.subprocess, "check_output", side_effect=[SOURCE, TREE]), self.assertRaisesRegex(AssertionError, "exactly27"):
            gate.verify(self.baseline, self.modern, self.xml)

    def test_both_widget_images_and_widget_proof_are_required(self):
        self.legacy_fixture();proofs=self.proofs_fixture();self.write_junit()
        for frame in sorted(gate.WIDGET):
            path=self.modern/frame;data=path.read_bytes();path.unlink()
            with self.subTest(frame=frame), mock.patch.object(gate.subprocess, "check_output", side_effect=[SOURCE, TREE]), \
                 self.assertRaisesRegex(AssertionError, "exactly27"):
                gate.verify(self.baseline, self.modern, self.xml)
            path.write_bytes(data)
        proof=proofs["modern-presentation-native.json"];proof.pop("widget_configuration")
        with self.assertRaisesRegex(AssertionError, "preview proof missing"):
            gate.presentation(proof, {p.name:p for p in self.modern.glob("*.png")})

    def test_widget_capture_hash_is_bound_to_its_presentation_proof(self):
        self.legacy_fixture();self.proofs_fixture();self.write_junit()
        path=self.modern/"modern-presentation-native.json";proof=json.loads(path.read_text())
        proof["captures"]["modern-nex-widget-light-he.png"]["sha256"]="0"*64;path.write_text(json.dumps(proof))
        with mock.patch.object(gate.subprocess, "check_output", side_effect=[SOURCE, TREE]), self.assertRaisesRegex(AssertionError, "saved bytes differ"):
            gate.verify(self.baseline, self.modern, self.xml)

    def test_widget_preview_requires_real_foreground_and_honest_unbound_scope(self):
        self.proofs_fixture();images={p.name:p for p in self.modern.glob("*.png")}
        changes = (("activity", "com.dacaller.app.ListDispatchHostActivity"), ("widget_id", 0), ("intent_widget_id", False),
                   ("widget_provider_info", "bound"), ("layout_direction", "LTR"), ("language", "en"), ("amoled", True),
                   ("frame", "modern-recents-inline-light-en.png"))
        for key, invalid in changes:
            with self.subTest(key=key), self.assertRaises(AssertionError):
                proof=widget_configuration_fixture();proof["observations"][0][key]=invalid;gate.widget_configuration(proof, images)
        for key, invalid in (("scope", "RemoteViews fixture"), ("launcher_host_binding", "PASS"), ("preferences_restored", False), ("observations", [])):
            with self.subTest(key=key), self.assertRaises(AssertionError):
                proof=widget_configuration_fixture();proof[key]=invalid;gate.widget_configuration(proof, images)
        for key, invalid in (("component", "com.dacaller.app/.MainActivity"), ("resumed_record", "unrecorded"),
                             ("top_resumed", False), ("native_application_window_visible", False), ("window_id", 18)):
            with self.subTest(key=key), self.assertRaises(AssertionError):
                proof=widget_configuration_fixture();proof["observations"][0]["foreground"][key]=invalid;gate.widget_configuration(proof, images)

    def test_resumed_record_accepts_actual_api33_brace_legacy_space_and_qualified_components(self):
        # The authentic API33 failure dump used the brace immediately after the
        # component. These are schema fixtures; they are not native PASS proof.
        source=(REPO / "app/src/androidTest/java/com/dacaller/app/ModernFeaturesIntegrationTest.java").read_text()
        pattern=re.search(r'RESUMED_ACTIVITY=Pattern\.compile\(("(?:\\.|[^"\\])*")\);',source)
        self.assertIsNotNone(pattern)
        native_pattern=re.compile(json.loads(pattern.group(1)))
        self.proofs_fixture();images={p.name:p for p in self.modern.glob("*.png")}
        for activity in ("MainActivity", "WidgetConfigure"):
            component="com.dacaller.app/."+activity
            qualified="com.dacaller.app/com.dacaller.app."+activity
            for label, separator, spelling, suffix in (
                ("topResumedActivity", "=", component, "} t8}"),
                ("topResumedActivity", "=", qualified, "} t8}"),
                ("mResumedActivity", ": ", component, " t8}"),
                ("mResumedActivity", ": ", qualified, " t8}"),
            ):
                record=f"  {label}{separator}ActivityRecord{{901df3e u0 {spelling}{suffix}"
                with self.subTest(activity=activity,record=record):
                    self.assertTrue(gate.resumed_activity_record(record,component))
                    self.assertIsNotNone(native_pattern.fullmatch(record))
                    if activity=="MainActivity":
                        proof=nex_island_fixture();proof["foreground"]["resumed_record"]=record
                        gate.nex_island(proof,"first-owned-token",images)
                    else:
                        proof=widget_configuration_fixture()
                        for observation in proof["observations"]: observation["foreground"]["resumed_record"]=record
                        gate.widget_configuration(proof,images)

    def test_resumed_record_rejects_prefix_paused_stale_wrong_component_and_nonrecords(self):
        source=(REPO / "app/src/androidTest/java/com/dacaller/app/ModernFeaturesIntegrationTest.java").read_text()
        pattern=re.search(r'RESUMED_ACTIVITY=Pattern\.compile\(("(?:\\.|[^"\\])*")\);',source)
        native_pattern=re.compile(json.loads(pattern.group(1)))
        self.proofs_fixture();images={p.name:p for p in self.modern.glob("*.png")}
        for activity in ("MainActivity", "WidgetConfigure"):
            component="com.dacaller.app/."+activity
            valid=f"topResumedActivity=ActivityRecord{{901df3e u0 {component}}} t8}}"
            records=(
                valid.replace(component,component+"Stale"),
                valid.replace(component,"com.other.app/."+activity),
                valid.replace(component,"com.dacaller.app/.ListDispatchHostActivity"),
                valid.replace("topResumedActivity","mLastPausedActivity"),
                valid.replace("topResumedActivity","oldtopResumedActivity"),
                "mLastPausedActivity="+valid,
                valid+" stale=true",
                valid.replace(" u0 ","\n u0 "),
                valid.replace("} t8}","}\n t8}"),
                valid+"\n"+valid.replace(component,"com.dacaller.app/.ListDispatchHostActivity"),
                valid.replace("ActivityRecord{","FakeRecord{"),
                valid.replace("} t8}",""),
                "topResumedActivity=null "+component,
                None,
            )
            for record in records:
                with self.subTest(activity=activity,record=record):
                    self.assertFalse(gate.resumed_activity_record(record,component))
                    match=native_pattern.fullmatch(record) if type(record) is str else None
                    if match is not None:
                        self.assertNotIn(match.group(1),(component,"com.dacaller.app/com.dacaller.app."+activity))
                    with self.assertRaisesRegex(AssertionError,"foreground Activity missing|resumed component is missing"):
                        if activity=="MainActivity":
                            proof=nex_island_fixture();proof["foreground"]["resumed_record"]=record
                            gate.nex_island(proof,"first-owned-token",images)
                        else:
                            proof=widget_configuration_fixture();proof["observations"][0]["foreground"]["resumed_record"]=record
                            gate.widget_configuration(proof,images)

    def test_every_widget_and_nex_geometry_requires_its_actual_focused_native_window(self):
        self.proofs_fixture();images={p.name:p for p in self.modern.glob("*.png")}
        for part in ("root", "heading", "search", "back"):
            for key, invalid in (("window_id",18),("window_id",False),("view_window_focus",False),
                                 ("native_window_focus",False),("native_ax_bounds","[0,0][0,0]")):
                with self.subTest(part=part,key=key,invalid=invalid), self.assertRaises(AssertionError):
                    proof=widget_configuration_fixture();proof["observations"][0][part+"_geometry"][key]=invalid
                    gate.widget_configuration(proof,images)
        for key, invalid in (("window_id",20),("window_id",False),("view_window_focus",False),
                             ("native_window_focus",False),("native_ax_bounds","[0,0][0,0]")):
            with self.subTest(nex_geometry=key,invalid=invalid), self.assertRaises(AssertionError):
                proof=nex_island_fixture();proof["geometry"][key]=invalid
                gate.nex_island(proof,"first-owned-token",images)

    def test_widget_targets_fonts_and_actual_text_layout_cannot_be_weakened(self):
        self.proofs_fixture();images={p.name:p for p in self.modern.glob("*.png")}
        for part, key, invalid in (("back_geometry", "bounds", "[24,48][95,120]"),
                                   ("search_geometry", "bounds", "[24,149][456,229]"),
                                   ("heading_geometry", "native_ax_bounds", "[96,48][455,120]"),
                                   ("root_geometry", "native_window_focus", False),
                                   ("heading", "font_weight", 600), ("heading", "font_variation", "'wght' 600"),
                                   ("heading", "text_size_sp", 21), ("heading", "text", "בחר"),
                                   ("heading", "content_height_px", 50), ("heading", "lines", []),
                                   ("search_hint", "text", "Search"), ("search_hint", "font_weight", 750),
                                   ("search_hint", "measured_width_px", 385), ("search_hint", "baseline_px", 49),
                                   ("search_hint", "font_top_px", float("nan"))):
            with self.subTest(part=part, key=key), self.assertRaises(AssertionError):
                proof=widget_configuration_fixture();proof["observations"][0][part][key]=invalid;gate.widget_configuration(proof, images)
        for key, invalid in (("end_utf16", 3), ("start_utf16", 1), ("ellipsis_count", 1), ("right_px", 350), ("bottom_px", 55)):
            with self.subTest(line_key=key), self.assertRaises(AssertionError):
                proof=widget_configuration_fixture();proof["observations"][0]["heading"]["lines"][0][key]=invalid;gate.widget_configuration(proof, images)

    def test_widget_live_and_saved_colors_require_nex_day_and_true_amoled_black(self):
        self.proofs_fixture();images={p.name:p for p in self.modern.glob("*.png")}
        for index in range(2):
            for key in ("background_argb", "surface_argb", "heading_argb", "search_argb", "hint_argb", "status_bar_argb", "navigation_bar_argb"):
                with self.subTest(index=index, key=key), self.assertRaisesRegex(AssertionError, "live color differs"):
                    proof=widget_configuration_fixture();proof["observations"][index][key]+=1;gate.widget_configuration(proof, images)
        for frame in sorted(gate.WIDGET):
            path=images[frame];original=path.read_bytes();path.write_bytes(self.base_png)
            with self.subTest(frame=frame), self.assertRaisesRegex(AssertionError, "actual saved PNG"):
                gate.widget_configuration(widget_configuration_fixture(), images)
            path.write_bytes(original)

    def test_widget_preview_rejects_selection_input_provider_changes_and_mutation(self):
        self.proofs_fixture();images={p.name:p for p in self.modern.glob("*.png")}
        for key, invalid in (("selection_actions", 1), ("native_pointer_inputs", 1), ("provider_writes_requested", 1),
                             ("provider_change_notifications", 1), ("selection_actions", False), ("preferences_unchanged", False),
                             ("telecom_call_unchanged", False), ("finished", False)):
            with self.subTest(key=key, invalid=invalid), self.assertRaises(AssertionError):
                proof=widget_configuration_fixture();proof["observations"][0][key]=invalid;gate.widget_configuration(proof, images)

    def test_nex_island_real_image_and_owned_call_proof_are_required(self):
        self.legacy_fixture();proofs=self.proofs_fixture();self.write_junit()
        images={p.name:p for p in self.modern.glob("*.png")};gate.nex_island(nex_island_fixture(), "first-owned-token", images)
        path=images[gate.NEX_ISLAND];data=path.read_bytes();path.unlink()
        with mock.patch.object(gate.subprocess, "check_output", side_effect=[SOURCE, TREE]), self.assertRaisesRegex(AssertionError, "exactly27"):
            gate.verify(self.baseline, self.modern, self.xml)
        path.write_bytes(data);proof=proofs["modern-island-native.json"];proof.pop("nex_active")
        with self.assertRaisesRegex(AssertionError, "active Telecom island proof missing"):
            gate.island(proof, images)

    def test_nex_island_cannot_substitute_preview_token_state_style_or_foreground(self):
        self.proofs_fixture();images={p.name:p for p in self.modern.glob("*.png")}
        for key, invalid in (("frame", "modern-island-dragged.png"), ("style_id", "apple"), ("language", "en"), ("layout_direction", "LTR"),
                             ("data_origin", "synthetic preview"), ("token", "second-owned-token"), ("bound_view_token", "unbound"),
                             ("call_state", 2), ("bound_view_state", False), ("number", "0500000000"), ("extra_modem_commands", 1),
                             ("same_owned_call", False), ("single_owned_surface", False), ("preferences_restored", False),
                             ("previous_layout_restored", False), ("restored_bounds", "[45,49][435,181]"), ("view_alpha", 0.35),
                             ("body_top_argb", signed((20, 20, 20))), ("accent_argb", signed((0, 255, 0)))):
            with self.subTest(key=key), self.assertRaises(AssertionError):
                proof=nex_island_fixture();proof[key]=invalid;gate.nex_island(proof, "first-owned-token", images)
        for key, invalid in (("component", "com.dacaller.app/.ListDispatchHostActivity"), ("resumed_record", "unrecorded"),
                             ("top_resumed", False), ("native_application_window_visible", False), ("window_id", 20)):
            with self.subTest(foreground=key), self.assertRaises(AssertionError):
                proof=nex_island_fixture();proof["foreground"][key]=invalid;gate.nex_island(proof, "first-owned-token", images)
        proof=nex_island_fixture();proof["geometry"]["native_ax_bounds"]="[45,48][434,180]"
        with self.assertRaisesRegex(AssertionError, "AX bounds"):
            gate.nex_island(proof, "first-owned-token", images)

    def test_nex_island_palette_samples_are_bound_to_saved_compositor_bytes(self):
        self.proofs_fixture();images={p.name:p for p in self.modern.glob("*.png")}
        proof=nex_island_fixture();proof["background_samples"][0]["argb"]+=1
        with self.assertRaisesRegex(AssertionError, "actual saved PNG"):
            gate.nex_island(proof, "first-owned-token", images)
        proof=nex_island_fixture();proof["background_samples"][0]["y"]=60
        with self.assertRaisesRegex(AssertionError, "top padding"):
            gate.nex_island(proof, "first-owned-token", images)
        images[gate.NEX_ISLAND].write_bytes(self.base_png)
        with self.assertRaisesRegex(AssertionError, "actual saved PNG"):
            gate.nex_island(nex_island_fixture(), "first-owned-token", images)


if __name__ == "__main__":
    unittest.main()
