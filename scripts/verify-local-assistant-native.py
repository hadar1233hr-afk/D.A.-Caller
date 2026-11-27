#!/usr/bin/env python3
"""Require real dedicated assistant evidence in addition to all prior native gates.
Synthetic contract fixtures are never accepted as native execution or visual review.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys

OWNER = "com.dacaller.app.LocalAssistantIntegrationTest"
METHODS = {
 "testAssistantProductionSearchDisambiguationAndCancel": {"assistant-entry-light-en.png", "assistant-contact-matches-light-en.png", "assistant-confirmation-cancel-light-en.png"},
 "testAssistantLocalHistoryBusinessAndUnsupportedCommands": {"assistant-history-dark-he.png", "assistant-business-dark-he.png", "assistant-unsupported-dark-he.png"},
 "testAssistantEffectivePermissionRevocationAndRuntimeDeniedPhase": {"assistant-permission-revoked-amoled-he.png"},
 "testAssistantExplicitConfirmedCallUsesActualTelecom": {"assistant-confirmation-amoled-he.png", "assistant-real-call-confirmed.png"},
}
RUNTIME_METHOD = "testAssistantEffectivePermissionRevocationAndRuntimeDeniedPhase"
RUNTIME_FRAME = "assistant-runtime-permissions-denied.png"
REQUIRED = set().union(*METHODS.values()) | {RUNTIME_FRAME}
LEGACY_REPORTS = ("matrix-verification.json", "personalization-verification.json", "call-integration-verification.json", "island-verification.json", "external-island-verification.json", "followup-verification.json", "callback-business-verification.json", "modern-features-verification.json")
PACKAGE = "com.dacaller.app"
READ_PERMISSIONS = {"android.permission.READ_CONTACTS": "READ_CONTACTS",
                    "android.permission.READ_CALL_LOG": "READ_CALL_LOG"}


def require(value, message):
    if not value:
        raise AssertionError(message)


def integer(value):
    return type(value) is int


def one(root, name):
    matches = [p for p in root.rglob(name) if p.is_file()]
    require(len(matches) == 1, f"Missing or duplicate authentic assistant file: {name}")
    return matches[0]


def strict_pairs(items):
    result = {}
    for key, value in items:
        require(key not in result, "Duplicate JSON evidence key")
        result[key] = value
    return result


def load(path):
    value = json.loads(path.read_text(), object_pairs_hook=strict_pairs, parse_constant=lambda x: require(False, "Nonfinite JSON evidence"))
    require(type(value) is dict, "Evidence root must be object")
    return value


def flags(value, names):
    for name in names:
        require(value.get(name) is True, "Missing actual assistant assertion: " + name)


def rectangle(value):
    match = re.fullmatch(r"\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]", str(value))
    require(match is not None, "Missing actual assistant native rectangle")
    left, top, right, bottom = map(int, match.groups())
    require(0 <= left < right <= 480 and 0 <= top < bottom <= 782, "Assistant action outside visible native application")
    return left, top, right, bottom


def permission_flags(package_dump):
    flags = {}
    for permission in READ_PERMISSIONS:
        matches = set(re.findall(re.escape(permission) + r":\s+granted=(true|false)\b", package_dump))
        require(len(matches) == 1, "Actual runtime permission state is missing or ambiguous: " + permission)
        flags[permission] = matches.pop() == "true"
    return flags


def appop_mode(raw, operation):
    modes = set(re.findall(re.escape(operation) + r":\s*(allow|ignore|deny|default|foreground)\b", raw))
    if not modes:
        require(raw.strip() in {"No operations.", "No operations", "No operations defined."},
                "Actual provider AppOps state is missing or ambiguous")
        return "default"
    require(len(modes) == 1, "Actual provider AppOps state has conflicting modes")
    return modes.pop()


def run_runtime_phase(destination):
    """Real second process; preserve its stdout and restore observed grant/AppOps state."""
    destination.mkdir(parents=True, exist_ok=True)

    def adb(*args):
        return subprocess.check_output(["adb", *args], text=True, timeout=20)

    require(adb("shell", "getprop", "ro.kernel.qemu").strip() == "1", "Refuse runtime permission changes on a real device")
    require(adb("shell", "getprop", "ro.build.version.sdk").strip() == "33", "Runtime phase requires the actual API33 emulator")
    before = adb("shell", "dumpsys", "package", PACKAGE)
    permissions = permission_flags(before)
    require(all(permissions.values()), "Main native run must leave actual granted flags before this revocation probe")
    operations = {operation: appop_mode(adb("shell", "cmd", "appops", "get", PACKAGE, operation), operation)
                  for operation in READ_PERMISSIONS.values()}
    (destination / "runtime-permission-before.txt").write_text(before)
    observed = {"package": PACKAGE, "permissions_before": permissions, "appops_before": operations,
                "method": RUNTIME_METHOD, "phase": "runtime-denied", "native_status_passed": False,
                "state_restored": False, "raw_instrumentation_not_synthesized": True, "emulator_only": True, "api": 33}
    failure, cleanup_errors = None, []
    try:
        adb("shell", "am", "force-stop", PACKAGE)
        for permission in READ_PERMISSIONS:
            adb("shell", "pm", "revoke", "--user", "0", PACKAGE, permission)
        denied = adb("shell", "dumpsys", "package", PACKAGE)
        (destination / "runtime-permission-denied.txt").write_text(denied)
        require(all(not granted for granted in permission_flags(denied).values()), "Host must actually revoke both runtime flags before restarting instrumentation")
        command = ["adb", "shell", "am", "instrument", "--user", "0", "-w", "-r", "-e", "class", OWNER + "#" + RUNTIME_METHOD,
                   "-e", "assistant_phase", "runtime-denied", PACKAGE + ".test/android.test.InstrumentationTestRunner"]
        try:
            completed = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=180, check=False)
            raw, stderr = completed.stdout, completed.stderr
        except subprocess.TimeoutExpired as error:
            (destination / "runtime-permission-phase.txt").write_bytes(error.output or b"")
            (destination / "runtime-permission-phase.stderr.txt").write_bytes(error.stderr or b"")
            raise
        (destination / "runtime-permission-phase.txt").write_bytes(raw)
        (destination / "runtime-permission-phase.stderr.txt").write_bytes(stderr)
        require(completed.returncode == 0, "Actual restarted instrumentation command failed")
        runtime_log(raw.decode("utf-8"))
        observed["native_status_passed"] = True
    except BaseException as error:
        failure = error
        observed["failure_type"] = type(error).__name__
    finally:
        try:
            adb("shell", "am", "force-stop", PACKAGE)
        except Exception as error:
            cleanup_errors.append(type(error).__name__ + ": force-stop")
        for permission, granted in permissions.items():
            try:
                adb("shell", "pm", "grant" if granted else "revoke", "--user", "0", PACKAGE, permission)
            except Exception as error:
                cleanup_errors.append(type(error).__name__ + ": " + permission)
        for operation, mode in operations.items():
            try:
                adb("shell", "cmd", "appops", "set", PACKAGE, operation, mode)
            except Exception as error:
                cleanup_errors.append(type(error).__name__ + ": " + operation)
        try:
            restored = adb("shell", "dumpsys", "package", PACKAGE)
            (destination / "runtime-permission-restored.txt").write_text(restored)
            require(permission_flags(restored) == permissions, "Runtime permission state was not restored")
            require(all(appop_mode(adb("shell", "cmd", "appops", "get", PACKAGE, operation), operation) == mode
                        for operation, mode in operations.items()), "Provider AppOps state was not restored")
        except Exception as error:
            cleanup_errors.append(type(error).__name__ + ": state verification")
        observed["state_restored"] = not cleanup_errors
        observed["cleanup_errors"] = cleanup_errors
        (destination / "runtime-permission-host-phase.json").write_text(json.dumps(observed, indent=2) + "\n")
    require(not cleanup_errors, "Runtime phase must restore actual observed permissions and AppOps")
    if failure is not None:
        raise failure


def runtime_log(raw):
    """Validate verbatim InstrumentationTestRunner status; never manufacture JUnit."""
    require(type(raw) is str and bool(raw), "Actual runtime-denied instrumentation output missing")
    require(not re.search(r"INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|FAILURES!!!|Process crashed|shortMsg=|INSTRUMENTATION_STATUS_CODE:\s*-", raw), "Runtime-denied instrumentation failed")
    codes = re.findall(r"(?m)^INSTRUMENTATION_STATUS_CODE:\s*(-?[0-9]+)\s*$", raw)
    require(codes == ["1", "0"], "Exactly one actual runtime-denied start and success required")
    blocks = re.split(r"(?m)^INSTRUMENTATION_STATUS_CODE:\s*-?[0-9]+\s*$", raw)
    for block in blocks[:2]:
        pairs = re.findall(r"(?m)^INSTRUMENTATION_STATUS: ([A-Za-z_]+)=(.*)$", block)
        require(len({key for key, _ in pairs}) == len(pairs), "Duplicate native instrumentation status field")
        values = dict(pairs)
        require(values.get("class", "").strip() == OWNER and values.get("test", "").strip() == RUNTIME_METHOD,
                "Runtime-denied instrumentation ran a different case")
        require(values.get("numtests", "").strip() == "1" and values.get("current", "").strip() == "1", "Runtime phase counters differ")
    summaries=[line.strip() for line in raw.splitlines() if line.strip().startswith("OK (")]
    final_codes=[line.strip() for line in raw.splitlines() if line.strip().startswith("INSTRUMENTATION_CODE:")]
    terminal=[line.strip() for line in raw.splitlines() if line.strip()]
    require(summaries==["OK (1 test)"] and len(final_codes)==1 and re.fullmatch(r"INSTRUMENTATION_CODE:[ \t]*-1",final_codes[0]) is not None
            and terminal and terminal[-1]==final_codes[0],
            "Runtime-denied instrumented case did not finish successfully")
    return {"class": OWNER, "method": RUNTIME_METHOD, "tests": 1, "raw_sha256": hashlib.sha256(raw.encode()).hexdigest(), "method_of_validation": "Verbatim native InstrumentationTestRunner statuses; no synthesized JUnit"}


def proof(value, method, phase, images, modern):
    require(value.get("method") == method and value.get("phase") == phase, "Assistant case/phase identity differs")
    require(integer(value.get("api")) and value["api"] == 33 and integer(value.get("density_dpi")) and value["density_dpi"] == 240
            and type(value.get("font_scale")) in (int, float) and abs(value["font_scale"] - 1.3) <= 0.000001, "Assistant fixed native profile differs")
    require(value.get("package") == "com.dacaller.app" and value.get("version_name") == "0.15.0" and integer(value.get("version_code")) and value["version_code"] == 24,
            "Assistant current version/package differs")
    flags(value, ("emulator_only", "no_network_permission", "preferences_restored", "navigation_preferences_restored", "appops_restored", "call_history_cache_restored", "cleanup_completed"))
    require(value.get("physical_carrier_audio_verified") is False and value.get("matching_key_upgrade_verified") is False,
            "Do not claim unperformed hardware/upgrade validation")
    require(value.get("fixture_scope") == "Isolated disposable-emulator provider rows; no owner data", "Provider fixtures must remain explicitly scoped")
    require(value.get("text_input_method") == "Production EditText.setText on UI thread; real native pointer for actions", "Native input scope must be honest")
    require(type(value.get("telecom_shell_identity_used")) is bool, "Actual Telecom observation scope missing")
    observation = "READ_PHONE_STATE-only UiAutomation shell identity for Telecom status observation; dropped before provider reads/actions" if value["telecom_shell_identity_used"] else "Target READ_PHONE_STATE permission"
    require(value.get("telecom_state_observation") == observation, "Telecom observation may not imply broader provider permissions")
    expected = {RUNTIME_FRAME} if phase == "runtime-denied" else METHODS[method]
    require(type(value.get("captures")) is dict and set(value["captures"]) == expected, "Assistant exact case captures missing/extra")
    for name, capture in value["captures"].items():
        require(name in images and capture.get("method") == "UiAutomation.takeScreenshot" and integer(capture.get("width")) and capture["width"] == 480
                and integer(capture.get("height")) and capture["height"] == 854 and capture.get("sha256") == hashlib.sha256(images[name].read_bytes()).hexdigest(),
                "Assistant actual saved native image/hash differs")
        modern.png(images[name].read_bytes(), name)
    pointers = value.get("pointers", [])
    require(type(pointers) is list and len(pointers) >= 3, "Actual native actions must be injected")
    for point in pointers:
        bounds = rectangle(point.get("bounds"));x,y=point.get("x"),point.get("y")
        flags(point, ("native_pointer", "full_native_ax_geometry"))
        require(bounds[2]-bounds[0] >= 72 and bounds[3]-bounds[1] >= 72 and integer(x) and integer(y)
                and bounds[0] < x < bounds[2] and bounds[1] < y < bounds[3], "Assistant native48dp action/point invalid")
    palette = value.get("palette", {})
    require(type(palette) is dict and type(palette.get("rtl")) is bool and type(palette.get("dark")) is bool and type(palette.get("amoled")) is bool, "Actual assistant palette missing")
    expected_palette = (False,False,False) if phase=="runtime-denied" or method=="testAssistantProductionSearchDisambiguationAndCancel" else (True,True,method!="testAssistantLocalHistoryBusinessAndUnsupportedCommands")
    require((palette["rtl"],palette["dark"],palette["amoled"]) == expected_palette, "Assistant theme/RTL matrix missing")
    flags(palette, ("input_state_saving_disabled", "ime_personalized_learning_request_disabled"))
    require(palette.get("external_keyboard_retention") == "NOT_TESTED", "Do not claim external keyboard behavior")
    expected_color=0xff000000 if palette["amoled"] else 0xff241a17 if palette["dark"] else 0xfff6f0e8
    require(integer(palette.get("background_argb")) and palette["background_argb"] & 0xffffffff == expected_color, "Assistant actual NEX/AMOLED background differs")
    require(integer(value.get("fixture_contacts_deleted")) and value["fixture_contacts_deleted"] >= 0
            and integer(value.get("fixture_call_rows_deleted")) and value["fixture_call_rows_deleted"] >= 0,
            "Actual cleanup counters missing")
    expected_contacts = 0 if phase == "runtime-denied" else 2 if method == "testAssistantProductionSearchDisambiguationAndCancel" else 1 if method == RUNTIME_METHOD else 0
    expected_seed_rows = 2 if method == "testAssistantLocalHistoryBusinessAndUnsupportedCommands" and phase == "main" else 0
    expected_confirmed_rows = 1 if method == "testAssistantExplicitConfirmedCallUsesActualTelecom" and phase == "main" else 0
    require(value["fixture_contacts_deleted"] == expected_contacts and value["fixture_call_rows_deleted"] == expected_seed_rows
            and integer(value.get("confirmed_call_rows_deleted")) and value["confirmed_call_rows_deleted"] == expected_confirmed_rows,
            "Exactly the owned provider fixtures and confirmed call row must be cleaned up")
    if phase == "runtime-denied":
        require(value.get("read_contacts_granted") is False and value.get("read_call_log_granted") is False, "Actual revoked runtime flags must be absent")
        flags(value, ("no_sensitive_results", "no_unconfirmed_call"))
        require(value.get("live_same_process_runtime_revoke") == "NOT_TESTED_PROCESS_MAY_BE_KILLED", "Do not confuse restarted runtime denial with live flag revocation")
    elif method == "testAssistantProductionSearchDisambiguationAndCancel":
        require(integer(value.get("fixture_contacts")) and integer(value.get("actual_contact_rows"))
                and value["fixture_contacts"] == value["actual_contact_rows"] == value["fixture_contacts_deleted"] == 2, "Real two-contact fixture/disambiguation missing")
        flags(value, ("no_call_before_confirmation", "cancel_did_not_dial", "edit_invalidated_results"))
    elif method == "testAssistantLocalHistoryBusinessAndUnsupportedCommands":
        require(integer(value.get("fixture_call_rows")) and value["fixture_call_rows"] == value["fixture_call_rows_deleted"] == 2, "Actual history fixtures/cleanup missing")
        flags(value, ("missed_filter_exact", "callback_latest_incoming_or_missed", "unsupported_honest", "unicode_numeric_dial_refused", "rtl", "dark", "no_unconfirmed_call"))
        require(value.get("business_license") == "ODbL-1.0" and str(value.get("business_source_url", "")).startswith("https://")
                and value.get("business_source_date") == "2026-11-03", "Actual licensed offline source/date required")
    elif method == RUNTIME_METHOD:
        flags(value, ("runtime_read_contacts_flag_remained_granted", "appops_denial_checked", "cached_selection_refused", "pending_confirmation_refused", "call_log_effective_denial_refused", "no_unconfirmed_call", "rtl", "amoled"))
        require(value.get("live_same_process_runtime_revoke") == "NOT_TESTED_PROCESS_MAY_BE_KILLED", "Live AppOps denial is not runtime flag revocation")
        require(value["fixture_contacts_deleted"] == 1, "Revocation fixture not cleaned up")
    else:
        flags(value, ("no_call_before_confirmation", "same_call_ended_by_native_button", "exactly_one_confirmed_call", "rtl", "amoled"))
        require(value.get("number") == value.get("telecom_handle") == "0500099951" and type(value.get("token")) is str and bool(value["token"]), "Actual confirmed Telecom identity missing")
        rows=value.get("actual_outgoing_rows", [])
        require(type(rows) is list and len(rows)==1 and rows[0].get("number")==value["number"] and integer(rows[0].get("id")) and rows[0]["id"]>0
                and integer(rows[0].get("type")) and rows[0]["type"]==2, "Exactly one real outgoing Telecom row required")


def verify(baseline, modern_root, assistant, junit_root):
    repo=Path.cwd();source=subprocess.check_output(["git","rev-parse","HEAD"],text=True).strip();tree=subprocess.check_output(["git","rev-parse","HEAD:app"],text=True).strip()
    spec=importlib.util.spec_from_file_location("assistant_legacy_modern",repo/"scripts/verify-modern-features-native.py");modern=importlib.util.module_from_spec(spec);spec.loader.exec_module(modern)
    prior=load(one(modern_root,"modern-features-verification.json"));require(modern.verify(baseline,modern_root,junit_root)==prior,"Original eight native gates must remain exact and source-bound")
    require(prior["native_junit"]["tests"]==44,"All40legacy+4assistant cases must pass")
    images={p.name:p for p in assistant.rglob("*.png")};require(set(images)==REQUIRED and len(list(assistant.rglob("*.png")))==len(REQUIRED),"Exactly10 actual assistant compositor captures required")
    proofs={}
    for method in METHODS:
        path=one(assistant,"assistant-native-"+method+"-main.json");value=load(path);proof(value,method,"main",images,modern);proofs[path.name]=hashlib.sha256(path.read_bytes()).hexdigest()
    path=one(assistant,"assistant-native-"+RUNTIME_METHOD+"-runtime-denied.json");value=load(path);proof(value,RUNTIME_METHOD,"runtime-denied",images,modern);proofs[path.name]=hashlib.sha256(path.read_bytes()).hexdigest()
    phase_path=one(assistant,"runtime-permission-phase.txt");runtime=runtime_log(phase_path.read_text());runtime["raw_sha256"]=hashlib.sha256(phase_path.read_bytes()).hexdigest()
    host_phase=load(one(assistant,"runtime-permission-host-phase.json"))
    flags(host_phase,("native_status_passed","state_restored","raw_instrumentation_not_synthesized","emulator_only"))
    require(host_phase.get("package")==PACKAGE and host_phase.get("method")==RUNTIME_METHOD and host_phase.get("phase")=="runtime-denied"
            and integer(host_phase.get("api")) and host_phase["api"]==33 and host_phase.get("cleanup_errors")==[], "Runtime host phase identity/cleanup differs")
    initial=host_phase.get("permissions_before",{})
    require(type(initial) is dict and set(initial)==set(READ_PERMISSIONS) and all(type(granted) is bool and granted for granted in initial.values()), "Actual initial runtime grants required")
    ops=host_phase.get("appops_before",{})
    require(type(ops) is dict and set(ops)==set(READ_PERMISSIONS.values()) and all(type(mode) is str and mode in {"allow","ignore","deny","default","foreground"} for mode in ops.values()), "Actual initial AppOps snapshot missing")
    require(permission_flags(one(assistant,"runtime-permission-before.txt").read_text())==host_phase["permissions_before"]
            and permission_flags(one(assistant,"runtime-permission-denied.txt").read_text())==dict.fromkeys(READ_PERMISSIONS,False)
            and permission_flags(one(assistant,"runtime-permission-restored.txt").read_text())==host_phase["permissions_before"],
            "Actual package dumps must prove granted, revoked and restored runtime flags")
    host_observations={name:hashlib.sha256(one(assistant,name).read_bytes()).hexdigest() for name in (
        "runtime-permission-host-phase.json","runtime-permission-before.txt","runtime-permission-denied.txt",
        "runtime-permission-restored.txt","runtime-permission-phase.stderr.txt")}
    return {"source_sha":source,"app_tree":tree,"workflow_run_id":os.environ.get("GITHUB_RUN_ID"),"api":33,"package":"com.dacaller.app","version_name":"0.15.0","version_code":24,
            "legacy_native_cases":40,"assistant_native_cases":4,"native_junit":prior["native_junit"],"runtime_denied_restarted_phase":runtime,
            "runtime_host_observations_sha256":host_observations,
            "prior_functional_pngs":272,"required_real_png_files":10,"total_functional_pngs":282,"sha256":{n:hashlib.sha256(p.read_bytes()).hexdigest() for n,p in sorted(images.items())},
            "proof_sha256":proofs,"original_modern_report_sha256":hashlib.sha256(one(modern_root,"modern-features-verification.json").read_bytes()).hexdigest(),
            "all_prior_gates_preserved":True,"full_visual_review":"NOT_ESTABLISHED_BY_THIS_AUTOMATIC_GATE","live_same_process_runtime_flag_revocation":"NOT_TESTED_PROCESS_MAY_BE_KILLED","physical_carrier_audio_verified":False,"matching_key_upgrade_verified":False}

if __name__=="__main__":
    if len(sys.argv)==3 and sys.argv[1]=="--run-runtime-phase":
        run_runtime_phase(Path(sys.argv[2]));sys.exit(0)
    parser=argparse.ArgumentParser();parser.add_argument("baseline",type=Path);parser.add_argument("modern",type=Path);parser.add_argument("assistant",type=Path);parser.add_argument("junit",type=Path);args=parser.parse_args()
    result=verify(args.baseline,args.modern,args.assistant,args.junit);destination=args.assistant/"assistant-verification.json";destination.write_text(json.dumps(result,ensure_ascii=False,indent=2)+"\n")
    print("Verified40original+4assistant native cases, real restarted denied-runtime phase and282 compositor frames; no full visual/hardware claim")
