"""Owner-transfer contract fixtures are synthetic; no real businesses are shipped."""
import copy
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
import zipfile

from package_app_dataset import SOURCE_URL, package, sha256
from test_package import synthetic_record


class OwnerUploadContract(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="synthetic-owner-upload-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / "extracted-synthetic"
        self.source.mkdir()
        self.output = self.root / "not-production-output"
        self.pbf = self.root / "synthetic-source.osm.pbf"
        self.pbf.write_bytes(b"SYNTHETIC PACKAGING INPUT: not a parsed OSM PBF")
        self.archive = self.root / "synthetic-source.osm.zip"
        with zipfile.ZipFile(self.archive, "w") as archive:
            archive.write(self.pbf, self.pbf.name)
        with sqlite3.connect(self.source / "businesses.sqlite") as database:
            database.execute("CREATE TABLE fixture(value TEXT)")
        (self.source / "businesses.ndjson").write_text(json.dumps(synthetic_record(), ensure_ascii=False) + "\n")
        self.manifest = dict(source_url=SOURCE_URL, source_sha256=sha256(self.pbf),
            source_bytes=self.pbf.stat().st_size, license_id="ODbL-1.0", scope="explicit-il",
            record_count=1, imported_at="2026-11-05T09:00:00+00:00",
            sqlite_sha256=sha256(self.source / "businesses.sqlite"),
            ndjson_sha256=sha256(self.source / "businesses.ndjson"))
        self.receipt = dict(source="owner-uploaded GitHub Release asset", asset_id=123,
            url="https://github.com/hadar1233hr-afk/D.A.-Caller/releases/download/synthetic/synthetic-source.osm.zip",
            bytes=self.archive.stat().st_size, sha256=sha256(self.archive),
            github_digest_matches=True, zip_crc="PASS", publisher_provenance_independently_verified=False,
            pbf_bytes=self.pbf.stat().st_size, pbf_sha256=sha256(self.pbf),
            members=[dict(name=self.pbf.name, bytes=self.pbf.stat().st_size)])
        self.receipt_path = self.root / "owner-transfer.json"
        self.write_inputs()

    def write_inputs(self):
        self.receipt_path.write_text(json.dumps(self.receipt))
        (self.source / "source-manifest.json").write_text(json.dumps(self.manifest))

    def build(self, **kwargs):
        options = dict(acquisition_mode="owner-upload", source_pbf=self.pbf, source_archive=self.archive)
        options.update(kwargs)
        return package(self.source, self.receipt_path, self.output, "synthetic-owner-only", **options)

    def test_owner_provenance_is_retained_without_authenticating_publisher(self):
        report = self.build()
        assets = self.output / "app-assets/business-directory"
        manifest = json.loads((assets / "manifest.json").read_text())
        self.assertEqual(manifest["acquisition"], report["acquisition"])
        self.assertEqual(manifest["acquisition"]["method"], "owner-upload")
        self.assertIs(manifest["acquisition"]["publisher_download_authenticated"], False)
        self.assertEqual(manifest["acquisition"]["source_sha256"], sha256(self.pbf))
        self.assertIn("not independently authenticated", (assets / "LICENSE-NOTICE.txt").read_text())
        self.assertEqual(report["counts"]["packaged_records"], 1)
        with sqlite3.connect(assets / "israel-businesses.sqlite") as database:
            stored = json.loads(database.execute("SELECT value FROM metadata WHERE key='acquisition'").fetchone()[0])
        self.assertEqual(manifest["acquisition"], stored)
        self.assertEqual((self.output / "redistribution/acquisition-receipt.json").read_bytes(), self.receipt_path.read_bytes())

    def test_upload_cannot_silently_enter_default_publisher_mode(self):
        with self.assertRaisesRegex(ValueError, "receipt"):
            package(self.source, self.receipt_path, self.output, "synthetic-owner-only")
        self.assertFalse(self.output.exists())

    def test_both_retained_source_paths_are_mandatory(self):
        for options in (dict(source_pbf=None), dict(source_archive=None)):
            with self.subTest(options=options), self.assertRaisesRegex(ValueError, "retained"):
                self.build(**options)
        self.assertFalse(self.output.exists())

    def test_no_http_success_or_publisher_authentication_claim_can_be_added(self):
        original = copy.deepcopy(self.receipt)
        for key, value in (("http_status", 200), ("status", "DOWNLOADED"),
                           ("source_url", SOURCE_URL), ("source_license", "ODbL-1.0"),
                           ("publisher_provenance_independently_verified", True),
                           ("github_digest_matches", False), ("zip_crc", "UNKNOWN")):
            self.receipt = {**original, key: value}
            self.write_inputs()
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, "provenance"):
                self.build()
        self.assertFalse(self.output.exists())

    def test_transfer_url_cannot_claim_other_repo_host_or_credentials(self):
        original = self.receipt["url"]
        for url in (original.replace("github.com", "github.com.evil.test"),
                    original.replace("github.com", "user@github.com"),
                    original.replace("D.A.-Caller", "AnotherProduct"),
                    original + "?download=1", original.replace("https:", "http:")):
            self.receipt["url"] = url
            self.write_inputs()
            with self.subTest(url=url), self.assertRaisesRegex(ValueError, "provenance"):
                self.build()

    def test_local_pbf_and_zip_byte_changes_are_rejected(self):
        for path in (self.pbf, self.archive):
            original = path.read_bytes()
            path.write_bytes(original + b"changed")
            with self.subTest(path=path.name), self.assertRaisesRegex(ValueError, "byte count"):
                self.build()
            path.write_bytes(original)
        self.assertFalse(self.output.exists())

    def test_same_size_pbf_and_archive_tampering_are_rejected(self):
        for path in (self.pbf, self.archive):
            original = path.read_bytes()
            path.write_bytes(bytes([original[0] ^ 1]) + original[1:])
            with self.subTest(path=path.name), self.assertRaisesRegex(ValueError, "checksum"):
                self.build()
            path.write_bytes(original)

    def test_boolean_byte_count_and_false_member_size_are_rejected(self):
        original = copy.deepcopy(self.receipt)
        for key, value in (("bytes", True), ("pbf_bytes", True), ("asset_id", True),
                           ("members", [dict(name=self.pbf.name, bytes=self.pbf.stat().st_size + 1)])):
            self.receipt = {**original, key: value}
            self.write_inputs()
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.build()

    def test_matching_archive_digest_does_not_hide_wrong_zip_member_bytes(self):
        with zipfile.ZipFile(self.archive, "w") as archive:
            archive.writestr(self.pbf.name, b"X" * self.pbf.stat().st_size)
        self.receipt.update(bytes=self.archive.stat().st_size, sha256=sha256(self.archive))
        self.write_inputs()
        with self.assertRaisesRegex(ValueError, "member checksum"):
            self.build()

    def test_extra_and_traversal_zip_members_cannot_enter_retained_source(self):
        for members in (((self.pbf.name, self.pbf.read_bytes()), ("extra.txt", b"extra")),
                        (("../" + self.pbf.name, self.pbf.read_bytes()),)):
            with zipfile.ZipFile(self.archive, "w") as archive:
                for name, data in members:
                    archive.writestr(name, data)
            self.receipt.update(bytes=self.archive.stat().st_size, sha256=sha256(self.archive))
            self.write_inputs()
            with self.subTest(members=members[0][0]), self.assertRaisesRegex(ValueError, "declared root PBF"):
                self.build()

    def test_source_license_scope_hash_and_count_are_still_required(self):
        original = copy.deepcopy(self.manifest)
        for key, value in (("license_id", "unknown"), ("scope", "il-phone-in-combined-extract"),
                           ("source_sha256", "b" * 64), ("source_bytes", self.pbf.stat().st_size + 1),
                           ("record_count", 1000000)):
            self.manifest = {**original, key: value}
            self.write_inputs()
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.build()
        self.assertFalse(self.output.exists())

    def test_country_per_record_and_nonempty_dataset_are_still_required(self):
        record = synthetic_record()
        record["country_tag"] = "PS"
        (self.source / "businesses.ndjson").write_text(json.dumps(record) + "\n")
        self.manifest["ndjson_sha256"] = sha256(self.source / "businesses.ndjson")
        self.write_inputs()
        with self.assertRaisesRegex(ValueError, "sufficiently populated"):
            self.build()
        self.assertFalse(self.output.exists())

    def test_extracted_ndjson_changes_are_still_rejected(self):
        with (self.source / "businesses.ndjson").open("a") as stream:
            stream.write("{}\n")
        with self.assertRaisesRegex(ValueError, "checksum"):
            self.build()

    def test_existing_output_is_never_overwritten(self):
        self.build()
        sentinel = self.output / "keep.txt"
        sentinel.write_text("keep")
        with self.assertRaisesRegex(ValueError, "already exists"):
            self.build()
        self.assertEqual(sentinel.read_text(), "keep")


if __name__ == "__main__":
    unittest.main()
