"""Adversarial exact013 publication contracts; synthetic fixtures are NOT APKs.

These tests never build, sign, publish, or relabel synthetic proof as native CI.
Official binary hashes are patched only inside isolated in-process fixtures.
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
spec = importlib.util.spec_from_file_location("approved_013_stage_contract", REAL_REPO / "scripts/prepare-approved-013-release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


def blob_zip(files):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_STORED) as archive:
        for name, data in files.items():
            archive.writestr(name, data)
    return stream.getvalue()


def junit(count, classes=1):
    cases = "".join('<testcase classname="SyntheticClass%d" name="case%d"/>' % (i % classes, i) for i in range(count))
    return ('<testsuite tests="%d" failures="0" errors="0" skipped="0">%s</testsuite>' % (count, cases)).encode()


def provenance():
    run = dict(id=release.RUN, head_sha=release.NATIVE_SOURCE, head_branch=release.BRANCH,
        path=".github/workflows/android.yml", status="completed", conclusion="success", event="pull_request",
        repository=dict(full_name=release.REPOSITORY, id=release.REPOSITORY_ID),
        head_repository=dict(full_name=release.REPOSITORY, id=release.REPOSITORY_ID))
    entries = []
    for name, (identity, size, digest) in release.ARTIFACTS.items():
        url = "https://api.github.com/repos/" + release.REPOSITORY + "/actions/artifacts/" + str(identity)
        entries.append(dict(id=identity, size_in_bytes=size, digest="sha256:" + digest, expired=False,
            url=url, archive_download_url=url + "/zip", workflow_run=dict(id=release.RUN,
            repository_id=release.REPOSITORY_ID, head_repository_id=release.REPOSITORY_ID,
            head_sha=release.NATIVE_SOURCE, head_branch=release.BRANCH)))
    return run, dict(total_count=len(entries), artifacts=entries)


def fake_tool(command):
    if "verify" in command:
        return "Signer #1 certificate SHA-256 digest: " + release.CERT + "\n"
    return "package: name='com.dacaller.app' versionName='0.13.0' versionCode='22'\n"


@contextmanager
def fixture():
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        repo, raw = root / "repo", root / "raw"
        raw.mkdir()
        (repo / Path(release.AUTH_PATH).parent).mkdir(parents=True)
        evidence = repo / release.EVIDENCE_PATH
        evidence.mkdir(parents=True)
        helper = root / "executing-helper.py"
        helper.write_bytes((REAL_REPO / "scripts/prepare-approved-013-release.py").read_bytes())
        asset_data = {"assets/business-directory/israel-businesses.sqlite": b"SYNTHETIC DB, not data",
                      "assets/business-directory/manifest.json": b'{"SYNTHETIC":true}'}
        asset_pins = {name:release.sha(data) for name,data in asset_data.items()}
        apk = blob_zip(dict(asset_data, **{"AndroidManifest.xml": b"SYNTHETIC NOT AN ANDROID APK"}))
        source_data = {"app-" + name:data for name,data in asset_data.items()}
        for name in ("app-assets/business-directory/NOTICE.txt", "redistribution/LICENSE-NOTICE.txt"):
            source_data[name] = b"SYNTHETIC UNIT NOTICE OpenStreetMap ODbL"
        source = blob_zip(source_data)
        source_path = repo / release.SOURCE_PATH
        source_path.parent.mkdir(parents=True)
        source_path.write_bytes(source)
        context = dict(schema_version=1, mode="ephemeral-test-only", apk_sha256=release.sha(apk),
            certificate_sha256=release.CERT, publication_allowed=False, required_update_certificate_matches=False)
        native = {"outputs/apk/debug/app-debug.apk": apk,
            "native-tested/debug-signing-policy.json": json.dumps(context).encode(),
            "native-tested/debug-signature.txt": b"SYNTHETIC UNIT SIGNATURE, not apksigner evidence",
            "native-tested/apk-metadata.txt": b"SYNTHETIC UNIT METADATA, not aapt evidence"}
        ui = {"outputs/androidTest-results/connected/debug/native.xml":junit(40)}
        frames = []
        for i in range(269):
            name = "synthetic-%03d.png" % i
            data = ("SYNTHETIC NOT PNG %d" % i).encode()
            ui["ui-snapshots/" + name] = data
            frames.append(dict(filename=name, sha256=release.sha(data), report="review%d.json" % (i % 6), status="PASS"))
        reports = []
        for i in range(6):
            name = "review%d.json" % i
            data = b"SYNTHETIC ACTUAL-REVIEW TEST FIXTURE"
            (evidence / ("visual-" + name)).write_bytes(data)
            reports.append(dict(file=name, sha256=release.sha(data), frames=sum(f["report"] == name for f in frames)))
        visual = dict(source_sha=release.NATIVE_SOURCE, app_tree=release.APP_TREE, run_id=release.RUN,
            actually_reviewed_unique_frames=269, **{"pass":269}, fail=0, unreviewed=0,
            all_current_raw_hashes_verified=True, software_visual_scope_approved=True, reports=reports, frames=frames)
        release.write_json(evidence / "visual-summary.json", visual)
        # input_paths uses the actual fixed six reviewer names; provide those as
        # separate immutable input fixtures in addition to the six synthetic references.
        for name in ("visual-call-a.json", "visual-call-b.json", "visual-island.json", "visual-other-a.json",
                     "visual-other-b.json", "visual-modern-root.json"):
            (evidence / name).write_bytes(b"SYNTHETIC INPUT REVIEW FIXTURE")
        for index, name in enumerate(release.REPORTS):
            prefix = "ui-modern-snapshots" if index == 7 else "ui-snapshots"
            ui[prefix + "/" + name] = json.dumps(dict(source_sha=release.NATIVE_SOURCE, app_tree=release.APP_TREE,
                workflow_run_id=str(release.RUN), synthetic_fixture_only=True)).encode()
        blobs = {"tested-apk.zip":blob_zip(native), "ui.zip":blob_zip(ui),
                 "checks.zip":blob_zip({"test-results/unit.xml":junit(460,44)})}
        archives = {name:(release.ARTIFACTS[name][0],len(data),release.sha(data)) for name,data in blobs.items()}
        pins = dict(release.AUTH_PINS, apk_bytes=len(apk), apk_sha256=release.sha(apk),
                    corresponding_source_bytes=len(source), corresponding_source_sha256=release.sha(source))
        auth = dict(pins, owner_approved=True, owner_reply="אני מאשר את הAPK")
        release.write_json(repo / release.AUTH_PATH, auth)
        (repo / release.APPROVAL_EVIDENCE).write_bytes(b"SYNTHETIC RECEIPT FIXTURE")
        for name,data in blobs.items():
            (raw / name).write_bytes(data)
        with mock.patch.multiple(release, REPO=repo, __file__=str(helper), APK_BYTES=len(apk), APK_SHA=release.sha(apk),
            ASSETS=asset_pins, AUTH_PINS=pins, ARTIFACTS=archives, SOURCE_BYTES=len(source), SOURCE_SHA=release.sha(source),
            VISUAL_SHA=release.digest(evidence / "visual-summary.json")):
            run, artifacts = provenance()
            release.write_json(raw / "source-run.json", run)
            release.write_json(raw / "artifacts.json", artifacts)
            def gates(ui, temporary):
                result = {}
                for index,(name,digest) in enumerate(release.VERIFIERS.items()):
                    report = ui / ("ui-modern-snapshots" if index == 7 else "ui-snapshots") / release.REPORTS[index]
                    result[name] = dict(source_sha256=digest, report=release.REPORTS[index], retained_report_sha256=release.digest(report),
                        canonical_report_sha256=release.sha(json.dumps(release.read_json(report), sort_keys=True, ensure_ascii=False).encode()),
                        stdout="Verified SYNTHETIC CONTRACT FIXTURE ONLY", all_native_assertions_unchanged=True)
                return result
            with mock.patch.object(release,"check_source",return_value="1"*40), mock.patch.object(release,"git",return_value=b""), \
                 mock.patch.object(release,"validate_approval_evidence"), mock.patch.object(release,"replay_gates",side_effect=gates), \
                 mock.patch.object(release,"tool",side_effect=fake_tool):
                yield dict(root=root,repo=repo,raw=raw,output=root/"staged",helper=helper,auth=auth,apk=apk,context=context)


class ExactAuthorizationContracts(unittest.TestCase):
    def test_all_authorization_pins_are_strict_and_typed(self):
        valid = dict(release.AUTH_PINS,owner_approved=True,owner_reply="אני מאשר את הAPK")
        self.assertEqual(valid, release.validate_authorization(valid))
        for key,value in valid.items():
            invalid=dict(valid)
            invalid[key] = not value if type(value) is bool else True if type(value) is int else "changed"
            with self.subTest(key=key),self.assertRaises(ValueError): release.validate_authorization(invalid)

    def test_actual_receipt_and_original_linked_document_are_bound(self):
        authorization=release.read_json(REAL_REPO/release.AUTH_PATH)
        release.validate_approval_evidence(release.validate_authorization(authorization))
        with mock.patch.object(release,"digest",return_value="0"*64),self.assertRaises(ValueError):
            release.validate_approval_evidence(authorization)
        with mock.patch.object(release,"git",return_value=b"changed immutable document"),self.assertRaises(ValueError):
            release.validate_approval_evidence(authorization)

    def test_duplicate_json_keys_cannot_hide_approval_mutations(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/"duplicate.json";path.write_text('{"owner_approved":false,"owner_approved":true}')
            with self.assertRaises(ValueError):release.read_json(path)

    def test_provenance_rejects_run_repository_artifact_and_truthy_pin_mutations(self):
        run,artifacts=provenance();release.validate_provenance(run,artifacts)
        for key,value in (("id",True),("head_sha","0"*40),("path",".github/workflows/android.yml/other"),("conclusion","failure")):
            bad=copy.deepcopy(run);bad[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):release.validate_provenance(bad,artifacts)
        for key,value in (("expired","false"),("expired",True),("size_in_bytes",True),("digest","sha256:"+"0"*64)):
            bad=copy.deepcopy(artifacts);bad['artifacts'][0][key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):release.validate_provenance(run,bad)
        bad=copy.deepcopy(artifacts);bad['artifacts'].append(bad['artifacts'][0])
        with self.assertRaises(ValueError):release.validate_provenance(run,bad)
        bad=copy.deepcopy(artifacts);bad['artifacts'][0]['workflow_run']['head_repository_id']=1
        with self.assertRaises(ValueError):release.validate_provenance(run,bad)

    def test_protected_source_rejects_app_root_producer_and_untracked_drift(self):
        for changed_call in (2,3):
            calls=[0]
            def git(*args):
                calls[0]+=1
                if args[0]=='rev-parse':return (release.APP_TREE+'\n').encode()
                return b'changed\n' if calls[0]==changed_call+1 else b''
            with self.subTest(changed_call=changed_call),mock.patch.object(release,'git',side_effect=git),self.assertRaises(ValueError):
                release.check_source()


class EvidenceRefusalContracts(unittest.TestCase):
    def test_zip_path_alias_symlink_traversal_and_collision_rejection(self):
        for name in ('../escape','/absolute','a\\b','C:drive','a//b','a/./b','symlink'):
            with self.subTest(name=name),tempfile.TemporaryDirectory() as directory:
                path=Path(directory)/'input.zip'
                with zipfile.ZipFile(path,'w') as archive:
                    item=zipfile.ZipInfo(name)
                    if name=='symlink':item.external_attr=(stat.S_IFLNK|0o777)<<16
                    archive.writestr(item,b'fixture')
                with self.assertRaises(ValueError):release.archive_manifest(path)
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'input.zip';path.write_bytes(blob_zip({'a':b'file','a/b':b'child'}))
            with self.assertRaises(ValueError):release.archive_manifest(path)

    def test_zip_duplicate_crc_size_and_digest_rejection(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'input.zip'
            with warnings.catch_warnings(),zipfile.ZipFile(path,'w') as archive:
                warnings.simplefilter('ignore',UserWarning);archive.writestr('same',b'one');archive.writestr('same',b'two')
            with self.assertRaises(ValueError):release.archive_manifest(path)
            path.write_bytes(blob_zip({'name':b'UNIQUE CONTENT'}))
            with self.assertRaises(ValueError):release.archive_manifest(path,path.stat().st_size+1,release.digest(path))
            with self.assertRaises(ValueError):release.archive_manifest(path,path.stat().st_size,'0'*64)
            data=path.read_bytes().replace(b'UNIQUE CONTENT',b'CORRUPTCONTENT');path.write_bytes(data)
            with self.assertRaises(zipfile.BadZipFile):release.archive_manifest(path,path.stat().st_size,release.digest(path))

    def test_junit_rejects_duplicate_failed_skipped_empty_and_counter_forgery(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'results.xml';path.write_bytes(junit(2,2));release.audit_junit(directory,2,2)
            for content in (junit(2).replace(b'case1',b'case0'),junit(2).replace(b'</testcase>',b'</testcase>'),
                junit(2).replace(b'/>',b'><failure/></testcase>',1),junit(2).replace(b'/>',b'><skipped/></testcase>',1),
                junit(2).replace(b'tests="2"',b'tests="3"'),b'<testsuite tests="0"/>'):
                if content==junit(2):continue
                path.write_bytes(content)
                with self.subTest(content=content[:80]),self.assertRaises(ValueError):release.audit_junit(directory,2)

    def test_signing_context_embedded_identity_signer_and_tool_failures(self):
        with fixture() as f:
            apk=f['root']/'synthetic.apk';apk.write_bytes(f['apk'])
            release.verify_apk(apk,f['context'],'apksigner','aapt')
            for changed in ({'publication_allowed':True},{'schema_version':True},{'apk_sha256':'0'*64}):
                with self.subTest(changed=changed),self.assertRaises(ValueError):release.verify_apk(apk,dict(f['context'],**changed),'sign','aapt')
            for text in ('Signer #1 certificate SHA-256 digest: '+'0'*64,
                         fake_tool(['verify'])+fake_tool(['verify'])):
                with mock.patch.object(release,'tool',return_value=text),self.assertRaises(ValueError):release.verify_apk(apk,f['context'],'sign','aapt')
            for value in ("0.14.0","0.13.0-beta","wrong.product"):
                def output(command):return fake_tool(command).replace('0.13.0',value).replace('com.dacaller.app',value)
                with mock.patch.object(release,'tool',side_effect=output),self.assertRaises(ValueError):release.verify_apk(apk,f['context'],'sign','aapt')
            with mock.patch.object(release,'tool',side_effect=OSError('tool unavailable')),self.assertRaises(OSError):release.verify_apk(apk,f['context'],'sign','aapt')
            def mutate(command):
                result=fake_tool(command)
                if 'badging' in command:apk.write_bytes(apk.read_bytes()+b'mutation')
                return result
            with mock.patch.object(release,'tool',side_effect=mutate),self.assertRaises(ValueError):release.verify_apk(apk,f['context'],'sign','aapt')

    def test_visual_union_duplicates_raw_mutation_and_review_failures_rejected(self):
        for mutation in ('duplicate','fail','raw'):
            with self.subTest(mutation=mutation),fixture() as f:
                out=f['output'];release.stage(f['raw'],out)
                if mutation=='raw':(out/'ui/ui-snapshots/synthetic-000.png').write_bytes(b'changed')
                else:
                    path=f['repo']/release.EVIDENCE_PATH/'visual-summary.json';value=release.read_json(path)
                    if mutation=='duplicate':value['frames'][1]=value['frames'][0]
                    else:value['frames'][0]['status']='FAIL'
                    release.write_json(path,value)
                    with mock.patch.object(release,'VISUAL_SHA',release.digest(path)),self.assertRaises(ValueError):
                        release.validate_visual(out/'ui',f['repo']/release.EVIDENCE_PATH)
                    continue
                with self.assertRaises(ValueError):release.validate_visual(out/'ui',f['repo']/release.EVIDENCE_PATH)


class AtomicStagePackageAndPublicContracts(unittest.TestCase):
    def test_stage_package_exact_eight_assets_without_claiming_public_checks(self):
        with fixture() as f:
            result=release.stage(f['raw'],f['output']);self.assertFalse(result['fresh_emulator_execution'])
            manifest=release.package(f['output']);self.assertEqual(set(manifest['files']),set(release.ASSET_NAMES.values()))
            self.assertEqual((f['output']/'assets'/release.ASSET_NAMES['apk']).read_bytes(),f['apk'])
            self.assertEqual(manifest['public_download_verification'],'PENDING_POST_PUBLICATION')
            self.assertFalse((f['output']/'public-verification.json').exists())
            with self.assertRaises(ValueError):release.stage(f['raw'],f['output'])
            with self.assertRaises(ValueError):release.package(f['output'])

    def test_stage_midflight_helper_or_raw_mutation_emits_no_output(self):
        for target in ('helper','raw'):
            with self.subTest(target=target),fixture() as f:
                original=release.replay_gates.side_effect
                def change(ui,temp):
                    result=original(ui,temp)
                    path=f['helper'] if target=='helper' else f['raw']/'ui.zip'
                    path.write_bytes(path.read_bytes()+b'mutated')
                    return result
                with mock.patch.object(release,'replay_gates',side_effect=change),self.assertRaises(ValueError):release.stage(f['raw'],f['output'])
                self.assertFalse(f['output'].exists())

    def test_package_rejects_modified_native_sidecars_scope_signing_and_gates(self):
        for target in ('native','signing','gates'):
            with self.subTest(target=target),fixture() as f:
                release.stage(f['raw'],f['output'])
                if target=='native':(f['output']/'tested-apk/native-tested/apk-metadata.txt').write_bytes(b'changed')
                else:
                    path=f['output']/'stage.json';value=release.read_json(path)
                    if target=='signing':value['measured_signing']['durable_future_signing_verified']=True
                    else:value['functional_revalidation'].pop(next(iter(release.VERIFIERS)))
                    release.write_json(path,value)
                with self.assertRaises(ValueError):release.package(f['output'])
                self.assertFalse((f['output']/'assets').exists())

    def public_fixture(self,f):
        release.stage(f['raw'],f['output']);manifest=release.package(f['output'])
        public=f['root']/'independent-public';shutil.copytree(f['output']/'assets',public)
        release_json=f['root']/'public-release.json'
        entries=[dict(name=name,size=value['bytes'],url='https://github.com/'+release.REPOSITORY+'/releases/download/v0.13.0/'+name)
                 for name,value in manifest['files'].items()]
        release.write_json(release_json,dict(tagName='v0.13.0',targetCommitish='1'*40,isDraft=False,isPrerelease=True,assets=entries))
        release.write_json(f['output']/'public-tag-ref.json',dict(ref='refs/tags/v0.13.0',object=dict(type='commit',sha='1'*40)))
        return public,release_json

    def test_independent_public_bytes_signature_archives_and_exact_asset_set(self):
        with fixture() as f:
            public,release_json=self.public_fixture(f)
            result=release.verify_public(f['output'],release_json,public,tag_ref_json=f['output']/'public-tag-ref.json')
            self.assertEqual(result['status'],'PASS_ALL_INDEPENDENT_DIRECTORY_BYTES_ANONYMOUS_PROVENANCE_REQUIRES_ATTESTATION')
            self.assertFalse(result['anonymous_download_verified'])
            self.assertTrue(result['apk_signature_reverified'])
            self.assertTrue(all(item['exact_bytes_equal'] for item in result['assets'].values()))
            with self.assertRaises(ValueError):release.verify_public(f['output'],release_json,public,tag_ref_json=f['output']/'public-tag-ref.json')

    def test_public_missing_extra_duplicate_renamed_url_and_wrong_bytes_fail_closed(self):
        for mutation in ('missing','extra','duplicate','url','bytes','symlink','draft'):
            with self.subTest(mutation=mutation),fixture() as f:
                public,release_json=self.public_fixture(f);value=release.read_json(release_json)
                name=release.ASSET_NAMES['apk']
                if mutation=='missing':value['assets'].pop()
                elif mutation=='extra':(public/'extra').write_bytes(b'private data must not appear')
                elif mutation=='duplicate':value['assets'][1]=value['assets'][0]
                elif mutation=='url':value['assets'][0]['url']='https://example.invalid/asset'
                elif mutation=='bytes':(public/name).write_bytes(b'wrong binary')
                elif mutation=='symlink':
                    target=f['root']/'outside';target.write_bytes((public/name).read_bytes());(public/name).unlink();(public/name).symlink_to(target)
                else:value['isDraft']=True
                release.write_json(release_json,value)
                with self.assertRaises(ValueError):release.verify_public(f['output'],release_json,public,tag_ref_json=f['output']/'public-tag-ref.json')
                self.assertFalse((f['output']/'public-verification.json').exists())

    def test_tag_ref_actual_sha_and_type_cannot_be_hidden_by_target_metadata(self):
        valid=dict(ref='refs/tags/v0.13.0',object=dict(type='commit',sha='1'*40))
        release.validate_tag_ref(valid,'1'*40)
        for changed in (dict(ref='refs/tags/v0.12.0',object=valid['object']),
                        dict(valid,object=dict(type='commit',sha='0'*40)),dict(valid,object=dict(type='tag',sha='1'*40))):
            with self.subTest(changed=changed),self.assertRaises(ValueError):release.validate_tag_ref(changed,'1'*40)


if __name__ == '__main__':
    unittest.main()
