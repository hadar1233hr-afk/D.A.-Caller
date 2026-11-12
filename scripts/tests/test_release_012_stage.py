"""Bounded release tooling contracts; synthetic ZIP/APK bytes are never publishable.

The real retained-artifact execution is recorded separately. These unit fixtures
patch byte pins only in process to exercise refusal and atomic-retention paths.
"""
from contextlib import contextmanager
import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import shutil
import stat
import tempfile
import unittest
from unittest import mock
import warnings
import zipfile


REAL_REPO = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("approved_012_stage_contract", REAL_REPO / "scripts/prepare-approved-012-release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


def blob_zip(files):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_STORED) as archive:
        for name, data in files.items():
            archive.writestr(name, data)
    return stream.getvalue()


def scope_fixture(visual):
    """A declared synthetic report for unit tests, not genuine JUnit evidence."""
    functional = {}
    for name, (original, count, adapted) in release.FUNCTIONAL_PINS.items():
        item = dict(original_sha256=original, replaced_total_count_literals=count,
                    scoped_source_sha256=adapted, stdout="Verified synthetic unit fixture only")
        if count:
            item.update(approved_total_count=243, all_other_native_assertions_unchanged=True)
        functional[name] = item
    functional["independent-reminder-swipe-contact-deferred-profile"] = dict(
        source_sha256=release.PROPOSED_SHA, stdout="Verified10 synthetic unit fixture only",
        original_245_populated_business_verifier_executed=False)
    artifacts = {}
    for key, name in (("ui", "ui.zip"), ("checks", "checks.zip"), ("apk", "native-failure-candidate.zip")):
        size, sha = release.ARTIFACTS[name]
        artifacts[key] = dict(bytes=size, sha256=sha, members=1, crc="PASS")
    return dict(status="PASS_APPROVED_012_RELEASE_SCOPE_REVALIDATION", application_id="com.dacaller.app",
        version="0.12.0", app_tree=release.APP_TREE, native_source_sha=release.NATIVE_SOURCE,
        native_workflow_run_id=release.RUN, raw_export_commit=release.EXPORT, native_apk_sha256=release.APK_SHA,
        total_required_png_files=243, baseline_png_files=233, additional_png_files=10,
        reviewed_png_sha256=visual["reviewed_png_sha256"],
        authentic_unit_junit=dict(tests=356, failures=0, errors=0, skipped=0, files_sha256={"synthetic.xml":"a"*64}),
        authentic_native_junit=dict(tests=35, failures=0, errors=0, skipped=0, files_sha256={"synthetic.xml":"b"*64}),
        original_artifacts=artifacts, functional_revalidation=functional,
        original_workflow_conclusion="failure", fresh_emulator_execution=False)


def signing_fixture():
    return dict(publication_allowed=True, publication_exception="owner-approved-exact-0.12.0-preview",
        signing_gate_only=True, required_update_certificate_matches=False, durable_future_signing_verified=False,
        apk_sha256=release.APK_SHA, certificate_sha256=release.CERT, application_id="com.dacaller.app",
        version_name="0.12.0", version_code=21, approved_app_tree=release.APP_TREE)


@contextmanager
def fixture():
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        repo = root / "repository"
        (repo / Path(release.AUTH_PATH).parent).mkdir(parents=True)
        (repo / Path(release.VISUAL_PATH).parent).mkdir(parents=True, exist_ok=True)
        shutil.copyfile(REAL_REPO / release.VISUAL_PATH, repo / release.VISUAL_PATH)
        for name in release.FUNCTIONAL_PINS:
            (repo / "scripts").mkdir(exist_ok=True)
            shutil.copyfile(REAL_REPO / "scripts" / name, repo / "scripts" / name)
        proposed = "docs/releases/0.12.0-proposed/verify-release-matrix.py"
        (repo / Path(proposed).parent).mkdir(parents=True, exist_ok=True)
        shutil.copyfile(REAL_REPO / proposed, repo / proposed)
        helper = root / "executing-helper.py"
        helper.write_bytes((REAL_REPO / "scripts/prepare-approved-012-release.py").read_bytes())
        apk = b"Synthetic non-Android unit fixture, not an APK or publishable data"
        apk_sha = hashlib.sha256(apk).hexdigest()
        auth_pins = dict(release.AUTH_PINS, apk_sha256=apk_sha)
        authorization = dict(auth_pins, owner_approved=True, owner_reply="כמובן")
        (repo / release.AUTH_PATH).write_text(json.dumps(authorization))
        native = {
            "app-debug.apk": apk,
            "debug-signature.txt": b"SYNTHETIC UNIT FIXTURE signature, not apksigner evidence",
            "apk-metadata.txt": b"SYNTHETIC UNIT FIXTURE metadata, not aapt evidence",
            "debug-signing-policy.json": json.dumps(dict(apk_sha256=apk_sha, certificate_sha256=release.CERT)).encode(),
        }
        blobs = {"native-failure-candidate.zip": blob_zip(native),
                 "ui.zip": blob_zip({"synthetic-ui.txt":b"not a screenshot"}),
                 "checks.zip": blob_zip({"synthetic-checks.txt":b"not JUnit"}),
                 "validate-apk.zip": blob_zip({"synthetic-validation.txt":b"unit fixture"})}
        archives = {name:(len(data), hashlib.sha256(data).hexdigest()) for name,data in blobs.items()}
        blobs["provenance.json"] = json.dumps(dict(repository="hadar1233hr-afk/D.A.-Caller",
            source_sha=release.NATIVE_SOURCE, source_run_id=str(release.RUN), conclusion="failure")).encode()
        blobs["source-run.json"] = b"{}"
        def git(*args):
            if args[0] == "cat-file": return b""
            if args[0] == "show": return blobs[args[1].rsplit("/",1)[1]]
            raise AssertionError("Unexpected synthetic git operation: " + repr(args))
        with mock.patch.multiple(release, REPO=repo, APK_SHA=apk_sha, AUTH_PINS=auth_pins,
                                 ARTIFACTS=archives, __file__=str(helper)):
            with mock.patch.object(release, "check_source", return_value="1"*40), mock.patch.object(release,"git",side_effect=git):
                output = root / "staged"
                yield dict(root=root, repo=repo, output=output, helper=helper, blobs=blobs,
                           authorization=authorization, visual=release.read_json(repo/release.VISUAL_PATH))


def complete_fixture_stage(f):
    release.stage(f["output"])
    release.write_json(f["output"] / "scope.json", scope_fixture(f["visual"]))
    release.write_json(f["output"] / "signing.json", signing_fixture())


class ApprovedReleaseStageContracts(unittest.TestCase):
    def test_authorization_requires_direct_reply_and_true_boolean(self):
        valid = dict(release.AUTH_PINS, owner_approved=True, owner_reply="כמובן")
        self.assertEqual(valid, release.validate_authorization(valid))
        for key, value in (("owner_approved",False),("owner_approved",1),("owner_reply","continue")):
            invalid=dict(valid);invalid[key]=value
            with self.subTest(key=key,value=value), self.assertRaises(ValueError): release.validate_authorization(invalid)

    def test_every_identity_count_and_waiver_constraint_is_pinned_and_typed(self):
        valid = dict(release.AUTH_PINS, owner_approved=True, owner_reply="כמובן")
        for key, value in release.AUTH_PINS.items():
            invalid=dict(valid)
            invalid[key] = not value if type(value) is bool else True if type(value) is int else "changed"
            with self.subTest(key=key), self.assertRaises(ValueError): release.validate_authorization(invalid)

    def test_changed_application_or_root_build_inputs_are_rejected(self):
        for bad in (0,1,2):
            counter=[0]
            def git(*args):
                if args[0]=="rev-parse": return (release.APP_TREE+"\n").encode()
                counter[0]+=1
                return b"build.gradle\n" if counter[0]==bad+1 else b""
            with self.subTest(change=bad), mock.patch.object(release,"git",side_effect=git), self.assertRaises(ValueError):
                release.check_source()

    def test_wrong_application_tree_rejected(self):
        with mock.patch.object(release,"git",return_value=b"0"*40), self.assertRaises(ValueError): release.check_source()

    def test_archive_size_or_digest_corruption_fails_before_extraction(self):
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory)/"archive.zip";p.write_bytes(blob_zip({"safe":b"data"}))
            for size,sha in ((p.stat().st_size+1,release.digest(p)),(p.stat().st_size,"0"*64)):
                with self.assertRaises(ValueError): release.validate_archive(p,size,sha,Path(directory)/"out")
            self.assertFalse((Path(directory)/"out").exists())

    def test_archive_traversal_absolute_backslash_and_symlink_members_rejected(self):
        for name in ("../outside","/absolute","dir\\file","symlink"):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                p=Path(directory)/"archive.zip"
                with zipfile.ZipFile(p,"w") as archive:
                    item=zipfile.ZipInfo(name)
                    if name=="symlink":item.external_attr=(stat.S_IFLNK|0o777)<<16
                    archive.writestr(item,b"unsafe")
                with self.assertRaises(ValueError): release.validate_archive(p,p.stat().st_size,release.digest(p))

    def test_duplicate_zip_members_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory)/"archive.zip"
            with warnings.catch_warnings(),zipfile.ZipFile(p,"w") as archive:
                warnings.simplefilter("ignore",UserWarning);archive.writestr("same",b"one");archive.writestr("same",b"two")
            with self.assertRaises(ValueError):release.validate_archive(p,p.stat().st_size,release.digest(p))

    def test_crc_corruption_is_not_hidden_by_a_current_file_digest(self):
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory)/"archive.zip";data=blob_zip({"safe":b"unique-payload"})
            p.write_bytes(data.replace(b"unique-payload",b"changed-value!",1))
            with self.assertRaises((ValueError,zipfile.BadZipFile)):
                release.validate_archive(p,p.stat().st_size,release.digest(p))

    def test_scope_rejects_truthy_counts_missing_or_forged_junit_hashes(self):
        visual=release.read_json(REAL_REPO/release.VISUAL_PATH);valid=scope_fixture(visual)
        release.validate_scope(valid,visual)
        for key,value in (("tests",True),("tests",355),("skipped",1),("files_sha256",{}),
                          ("files_sha256",{"bad.xml":True}),("files_sha256",{"bad.xml":"not-a-hash"})):
            invalid=copy.deepcopy(valid);invalid["authentic_unit_junit"][key]=value
            with self.subTest(key=key,value=value),self.assertRaises(ValueError):release.validate_scope(invalid,visual)

    def test_scope_rejects_empty_or_failed_functional_evidence_and_guard_changes(self):
        visual=release.read_json(REAL_REPO/release.VISUAL_PATH);valid=scope_fixture(visual)
        for key,value in (("stdout","FAIL"),("original_sha256","0"*64),
                          ("replaced_total_count_literals",4),("scoped_source_sha256",None)):
            invalid=copy.deepcopy(valid);invalid["functional_revalidation"]["verify-external-island-matrix.py"][key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):release.validate_scope(invalid,visual)
        for value in ({},None):
            invalid=copy.deepcopy(valid);invalid["functional_revalidation"]["verify-ui-matrix.py"]=value
            with self.assertRaises(ValueError):release.validate_scope(invalid,visual)

    def test_scope_rejects_changed_visual_artifact_and_historical_workflow_claims(self):
        visual=release.read_json(REAL_REPO/release.VISUAL_PATH);valid=scope_fixture(visual)
        for key,value in (("reviewed_png_sha256",{}),("total_required_png_files",245),
                          ("original_workflow_conclusion","success"),("fresh_emulator_execution",True)):
            invalid=copy.deepcopy(valid);invalid[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):release.validate_scope(invalid,visual)
        invalid=copy.deepcopy(valid);invalid["original_artifacts"]["ui"]["sha256"]="0"*64
        with self.assertRaises(ValueError):release.validate_scope(invalid,visual)

    def test_signing_gate_rejects_other_binary_version_and_unsupported_compatibility(self):
        valid=signing_fixture();release.validate_signing(valid)
        for key,value in (("publication_allowed",1),("apk_sha256","0"*64),("certificate_sha256","0"*64),
                          ("version_name","0.13.0"),("version_code",True),
                          ("required_update_certificate_matches",True),("durable_future_signing_verified",True)):
            invalid=dict(valid);invalid[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):release.validate_signing(invalid)

    def test_stage_and_package_preserve_all_bytes_and_pending_public_verification(self):
        with fixture() as f:
            complete_fixture_stage(f);manifest=release.package(f["output"])
            for name,item in manifest["files"].items():
                p=f["output"]/"assets"/name;self.assertEqual(item["sha256"],release.digest(p));self.assertEqual(item["bytes"],p.stat().st_size)
            self.assertEqual(release.APK_SHA,release.digest(f["output"]/"assets/DA-CALLER-v0.12.0.apk"))
            proof=release.read_json(f["output"]/"assets/DA-CALLER-v0.12.0-verification.json")
            self.assertEqual("PENDING_POST_PUBLICATION",proof["public_download_verification"])
            self.assertTrue(proof["no_build_or_resigning_performed"])
            self.assertNotIn("BETA",manifest["version_name"])

    def test_stage_existing_destination_never_overwritten(self):
        with fixture() as f:
            f["output"].mkdir();marker=f["output"]/"keep";marker.write_bytes(b"keep")
            with self.assertRaises(ValueError):release.stage(f["output"])
            self.assertEqual(b"keep",marker.read_bytes())

    def test_stage_helper_drift_during_artifact_validation_retains_no_partial_output(self):
        with fixture() as f:
            original=release.validate_archive
            def changed(*args,**kwargs):
                result=original(*args,**kwargs);f["helper"].write_bytes(b"changed executing code");return result
            with mock.patch.object(release,"validate_archive",side_effect=changed),self.assertRaises(ValueError):release.stage(f["output"])
            self.assertFalse(f["output"].exists())

    def test_native_sidecar_missing_extra_or_changed_is_rejected(self):
        for change in ("changed","extra","missing"):
            with self.subTest(change=change),fixture() as f:
                release.stage(f["output"]);native=f["output"]/"native"
                if change=="changed":(native/"debug-signature.txt").write_bytes(b"forged sidecar")
                elif change=="extra":(native/"other").write_bytes(b"extra")
                else:(native/"apk-metadata.txt").unlink()
                with self.assertRaises(ValueError):release.validate_native_members(f["output"]/"artifacts/native-failure-candidate.zip",native)

    def test_package_missing_scope_or_failed_signing_cannot_emit_assets(self):
        for change in ("scope","signing"):
            with self.subTest(change=change),fixture() as f:
                complete_fixture_stage(f)
                p=f["output"]/(change+".json");value=release.read_json(p)
                value["status" if change=="scope" else "publication_allowed"]="FAIL";release.write_json(p,value)
                with self.assertRaises(ValueError):release.package(f["output"])
                self.assertFalse((f["output"]/"assets").exists())

    def test_package_existing_asset_output_never_overwritten(self):
        with fixture() as f:
            complete_fixture_stage(f);(f["output"]/"assets").mkdir();marker=f["output"]/"assets/keep";marker.write_bytes(b"keep")
            with self.assertRaises(ValueError):release.package(f["output"])
            self.assertEqual(b"keep",marker.read_bytes())

    def test_midpackage_native_metadata_ui_proof_and_helper_drift_fail_atomically(self):
        for change in ("native/apk-metadata.txt","artifacts/ui.zip","scope.json","helper"):
            with self.subTest(change=change),fixture() as f:
                complete_fixture_stage(f);original=release.shutil.copyfile;done=[False]
                def changed(source,target):
                    result=original(source,target)
                    if not done[0]:
                        done[0]=True;p=f["helper"] if change=="helper" else f["output"]/change
                        p.write_bytes(b"changed after validation")
                    return result
                with mock.patch.object(release.shutil,"copyfile",side_effect=changed),self.assertRaises(ValueError):release.package(f["output"])
                self.assertFalse((f["output"]/"assets").exists());self.assertFalse((f["output"]/"assets-manifest.json").exists())


if __name__=="__main__":unittest.main(verbosity=2)
