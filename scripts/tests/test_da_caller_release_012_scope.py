"""Meaningful corruption checks for the separate approved0.12 retained-data gate."""
from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock
from types import SimpleNamespace
import struct
import zipfile

HERE = Path(__file__).resolve()
REPO = HERE.parents[2]
SPEC = importlib.util.spec_from_file_location("da_caller_release_012_scope", REPO / "scripts/verify-release-012-matrix.py")
scope = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(scope)


class ScopeContracts(unittest.TestCase):
    def test_scoped_adaptation_preserves_all_functional_source(self):
        for name in scope.SCOPED_COUNTS:
            blob = (REPO / "scripts" / name).read_bytes()
            changed, proof = scope.profile_source(name, blob)
            self.assertEqual(blob.decode().replace("245", "243"), changed)
            self.assertEqual(3, proof["replaced_total_count_literals"])
            self.assertTrue(proof["all_other_native_assertions_unchanged"])
            self.assertEqual(blob, (REPO / "scripts" / name).read_bytes())

    def test_changed_original_guard_is_never_adapted(self):
        name = "verify-external-island-matrix.py"
        blob = (REPO / "scripts" / name).read_bytes()
        with self.assertRaisesRegex(ValueError, "Original verifier changed"):
            scope.profile_source(name, blob.replace(b"is True", b"is False", 1))
        with self.assertRaises(ValueError):
            scope.profile_source("unreviewed.py", blob)

    def test_noncount_original_verifiers_are_byte_identical(self):
        for name in set(scope.VERIFIERS) - scope.SCOPED_COUNTS:
            blob = (REPO / "scripts" / name).read_bytes()
            source, proof = scope.profile_source(name, blob)
            self.assertEqual(blob.decode(), source)
            self.assertEqual(0, proof["replaced_total_count_literals"])

    def junit(self, temporary, body):
        root = Path(temporary)
        (root / "actual.xml").write_text(body)
        return root

    def test_real_junit_case_identity_and_counters_are_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self.junit(temporary, '<testsuite tests="2" failures="0" errors="0" skipped="0">'
                '<testcase classname="C" name="one"/><testcase classname="C" name="two"/></testsuite>')
            result = scope.audit_junit(root, 2)
            self.assertEqual(2, result["tests"])
            self.assertEqual(1, len(result["files_sha256"]))
            with self.assertRaisesRegex(ValueError, "unique JUnit count"):
                scope.audit_junit(root, 3)

    def test_junit_rejects_skipped_failed_errored_duplicate_or_empty_cases(self):
        variants = (
            '<testsuite tests="1"><testcase classname="C" name="a"><failure/></testcase></testsuite>',
            '<testsuite tests="1"><testcase classname="C" name="a"><error/></testcase></testsuite>',
            '<testsuite tests="1"><testcase classname="C" name="a"><skipped/></testcase></testsuite>',
            '<testsuite tests="2"><testcase classname="C" name="a"/><testcase classname="C" name="a"/></testsuite>',
            '<testsuite tests="1" failures="1"><testcase classname="C" name="a"/></testsuite>',
            '<testsuite tests="2"><testcase classname="C" name="a"/></testsuite>',
            '<testsuite tests="1"><testcase name="a"/></testsuite>',
            '<testsuite tests="0"/>',
        )
        for xml in variants:
            with self.subTest(xml=xml), tempfile.TemporaryDirectory() as temporary:
                with self.assertRaises(ValueError):
                    scope.audit_junit(self.junit(temporary, xml), 1)

    def test_retained_review_cannot_be_silently_replaced(self):
        with tempfile.TemporaryDirectory() as temporary:
            p = Path(temporary) / "review.json"; p.write_text('{"verified":true}')
            expected = scope.digest(p)
            self.assertTrue(scope.checked_json(p, expected)["verified"])
            p.write_text('{"verified":false}')
            with self.assertRaisesRegex(ValueError, "review bytes changed"):
                scope.checked_json(p, expected)

    def test_archive_hash_size_path_and_duplicate_members_are_checked(self):
        with tempfile.TemporaryDirectory() as temporary:
            p = Path(temporary) / "actual.zip"
            with zipfile.ZipFile(p, "w") as archive:
                archive.writestr("actual.txt", b"actual")
            result = scope.unpack_artifact(p, p.stat().st_size, scope.digest(p), Path(temporary) / "safe")
            self.assertEqual("PASS", result["crc"])
            with self.assertRaisesRegex(ValueError, "size/hash"):
                scope.unpack_artifact(p, p.stat().st_size + 1, scope.digest(p), Path(temporary) / "wrong")
            with self.assertRaisesRegex(ValueError, "size/hash"):
                scope.unpack_artifact(p, p.stat().st_size, "0" * 64, Path(temporary) / "wrong")
            with zipfile.ZipFile(p, "w") as archive:
                archive.writestr("../escape.txt", b"unsafe")
            with self.assertRaisesRegex(ValueError, "Unsafe artifact"):
                scope.unpack_artifact(p, p.stat().st_size, scope.digest(p), Path(temporary) / "unsafe")

    def test_wrong_source_incomplete_review_or_baseline_omission_rejected(self):
        review = json.loads((REPO / "docs/evidence/release-0.12.0/polish-ci/full-visual-observation.json").read_text())
        baseline = json.loads((REPO / "docs/evidence/call-followup-2026-11-01/visual-review.json").read_text())
        for key, value in (("source_sha", "0" * 40), ("app_tree", "1" * 40),
                           ("workflow_run_id", True), ("raw_export_commit", "0" * 40),
                           ("whole_matrix_visual_review_complete", False), ("actual_viewed_png_count", 242)):
            changed = copy.deepcopy(review); changed[key] = value
            with self.subTest(key=key), tempfile.TemporaryDirectory() as temporary:
                with self.assertRaises(ValueError):
                    scope.validate_visual(changed, baseline, Path(temporary))
        changed = copy.deepcopy(baseline); changed["files"].pop()
        with tempfile.TemporaryDirectory() as temporary, self.assertRaisesRegex(ValueError, "233 baseline"):
            scope.validate_visual(review, changed, Path(temporary))

    def visual_inputs(self):
        review = json.loads((REPO / "docs/evidence/release-0.12.0/polish-ci/full-visual-observation.json").read_text())
        baseline = json.loads((REPO / "docs/evidence/call-followup-2026-11-01/visual-review.json").read_text())
        return review, baseline

    def fake_image(self, name, data=b""):
        return SimpleNamespace(name=name, is_file=lambda: True, read_bytes=lambda: data)

    def test_missing_duplicate_or_diagnostic_images_cannot_replace_reviewed_frames(self):
        review, baseline = self.visual_inputs()
        names = list(review["reviewed_png_sha256"])
        variants = (names[:-1], names + names[:1], names[:-1] + ["failed-diagnostic.png"])
        for candidate_names in variants:
            root = mock.Mock(); root.rglob.return_value = [self.fake_image(name) for name in candidate_names]
            with self.subTest(count=len(candidate_names)), mock.patch.object(scope, "Path", return_value=root):
                with self.assertRaisesRegex(ValueError, "Missing, duplicate or unexpected"):
                    scope.validate_visual(review, baseline, "synthetic-negative-only")

    def test_structurally_plausible_unreviewed_png_fails_exact_manual_hash(self):
        review, baseline = self.visual_inputs()
        # Deliberately synthetic negative data. It never becomes a passing PNG
        # or an emulator proof; the fixed manual-review hash must reject it.
        data = b"\x89PNG\r\n\x1a\n" + b"\0" * 8 + struct.pack(">II", 480, 854) + b"\0" * 1100 + b"\0\0\0\0IEND\xaeB`\x82"
        root = mock.Mock()
        root.rglob.return_value = [self.fake_image(name, data) for name in review["reviewed_png_sha256"]]
        with mock.patch.object(scope, "Path", return_value=root):
            with self.assertRaisesRegex(ValueError, "differs from manually reviewed bytes"):
                scope.validate_visual(review, baseline, "synthetic-negative-only")


if __name__ == "__main__":
    unittest.main(verbosity=2)
