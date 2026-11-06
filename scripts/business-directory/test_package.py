"""Temporary synthetic contract fixtures; never a production phonebook."""
import copy
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
import unicodedata

from package_app_dataset import SOURCE_URL, normalized_phone, package, record_values, sha256


def synthetic_record():
    return dict(id="osm:node:1:+97237486860", name="עסק בדיקה סינתטי", phone="+97237486860",
        city="עיר בדיקה", category="shop:supermarket", address="רחוב בדיקה 1",
        source_id="osm:node:1", source_url="https://www.openstreetmap.org/node/1",
        license_id="ODbL-1.0", geographic_scope="explicit-il", country_tag="IL",
        verification="source-listed-not-phone-confirmed", source_updated_at="2026-01-01T00:00:00+00:00")


class PackageContract(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / "synthetic-test-input"
        self.source.mkdir()
        self.output = self.root / "synthetic-test-output"
        self.receipt = self.root / "synthetic-test-receipt.json"

    def inputs(self, records=None):
        records = records if records is not None else [synthetic_record()]
        with sqlite3.connect(self.source / "businesses.sqlite") as database:
            database.execute("CREATE TABLE IF NOT EXISTS synthetic_fixture(value TEXT)")
        (self.source / "businesses.ndjson").write_text("".join(json.dumps(r, ensure_ascii=False)+"\n" for r in records))
        manifest = dict(source_url=SOURCE_URL, source_sha256="a"*64, source_bytes=100,
            license_id="ODbL-1.0", scope="explicit-il", record_count=len(records),
            imported_at="2026-11-02T10:00:00+00:00",
            sqlite_sha256=sha256(self.source / "businesses.sqlite"),
            ndjson_sha256=sha256(self.source / "businesses.ndjson"))
        (self.source / "source-manifest.json").write_text(json.dumps(manifest))
        receipt = dict(status="DOWNLOADED", http_status=200, source_url=SOURCE_URL,
            source_license="ODbL-1.0", actual_source_bytes=100, source_sha256="a"*64)
        self.receipt.write_text(json.dumps(receipt))
        return manifest, receipt

    def build(self, **kwargs):
        return package(self.source, self.receipt, self.output, "synthetic-test-only", **kwargs)

    def test_exact_android_schema_hash_indexes_fts_and_source_offer(self):
        self.inputs()
        report = self.build()
        assets = self.output / "app-assets/business-directory"
        database_path = assets / "israel-businesses.sqlite"
        manifest = json.loads((assets / "manifest.json").read_text())
        self.assertEqual(manifest["database_sha256"], sha256(database_path))
        self.assertEqual(manifest["record_count"], 1)
        self.assertEqual(report["status"], "PACKAGED_NOT_PUBLISHED")
        with sqlite3.connect(database_path) as database:
            self.assertEqual(database.execute("PRAGMA user_version").fetchone()[0], 1)
            self.assertEqual(database.execute("SELECT value FROM metadata WHERE key='dataset_version'").fetchone()[0], "synthetic-test-only")
            business = database.execute("SELECT id,name,phone,phone_display,city,category,address,source_url,source_id,source_license,source_updated_at,normalized_phone FROM businesses").fetchone()
            self.assertGreater(business[0], 0)
            self.assertEqual(business[2:4], ("+97237486860", "+97237486860"))
            self.assertEqual(business[11], "037486860")
            self.assertEqual(database.execute("SELECT docid FROM business_search WHERE business_search MATCH ?", ('"סינ*"',)).fetchone()[0], business[0])
            self.assertEqual(database.execute("SELECT count(*) FROM businesses WHERE normalized_phone LIKE '037%'").fetchone()[0], 1)
            self.assertEqual({r[1] for r in database.execute("PRAGMA index_list('businesses')")}, {"business_phone", "business_name"})
        for filename in ("israel-businesses.sqlite", "manifest.json", "LICENSE-NOTICE.txt"):
            self.assertEqual((assets / filename).read_bytes(), (self.output / "redistribution" / filename).read_bytes())
        self.assertTrue((self.output / "redistribution/businesses.ndjson").is_file())
        self.assertIn("source-listed", (assets / "LICENSE-NOTICE.txt").read_text())

    def test_blocked_receipt_never_creates_a_dataset(self):
        _, receipt = self.inputs()
        receipt.update(status="BLOCKED_OR_HTTP_FAILURE", http_status=403)
        self.receipt.write_text(json.dumps(receipt))
        with self.assertRaisesRegex(ValueError, "receipt"):
            self.build()
        self.assertFalse(self.output.exists())

    def test_mismatched_source_hash_is_rejected(self):
        _, receipt = self.inputs()
        receipt["source_sha256"] = "b"*64
        self.receipt.write_text(json.dumps(receipt))
        with self.assertRaises(ValueError):
            self.build()

    def test_changed_ndjson_after_extraction_is_rejected(self):
        self.inputs()
        with (self.source / "businesses.ndjson").open("a") as stream:
            stream.write("{}\n")
        with self.assertRaisesRegex(ValueError, "checksum"):
            self.build()

    def test_combined_extract_country_mode_cannot_be_shipped_as_israel(self):
        manifest, _ = self.inputs()
        manifest["scope"] = "il-phone-in-combined-extract"
        (self.source / "source-manifest.json").write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, "explicit IL"):
            self.build()

    def test_unknown_dates_and_hidden_control_names_not_packaged(self):
        bad = synthetic_record()
        bad["source_updated_at"] = None
        bad2 = synthetic_record()
        bad2["name"] = "Name\u202ehidden"
        self.inputs([bad, bad2])
        with self.assertRaisesRegex(ValueError, "sufficiently populated"):
            self.build()
        self.assertFalse(self.output.exists())

    def test_extensions_do_not_become_a_second_base_number_or_dial_suffix(self):
        first = synthetic_record()
        first["extension"] = "42"
        second = copy.deepcopy(first)
        second["extension"] = "91"
        self.inputs([first, second])
        report = self.build()
        self.assertEqual(report["counts"]["packaged_records"], 1)
        self.assertEqual(report["counts"]["duplicate_base_number_extensions"], 1)
        self.assertEqual(report["distinct_phone_count"], 1)
        raw = (self.output / "redistribution/businesses.ndjson").read_text()
        self.assertIn('"42"', raw)
        self.assertIn('"91"', raw)

    def test_no_overwrite_of_existing_public_dataset(self):
        self.inputs()
        self.build()
        sentinel = self.output / "keep.txt"
        sentinel.write_text("do not replace")
        with self.assertRaisesRegex(ValueError, "already exists"):
            self.build()
        self.assertEqual(sentinel.read_text(), "do not replace")

    def test_declared_count_must_match_actual_ndjson(self):
        manifest, _ = self.inputs()
        manifest["record_count"] = 1000000
        (self.source / "source-manifest.json").write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, "row count"):
            self.build()
        self.assertFalse(self.output.exists())

    def test_explicit_source_object_url_and_license_match_required(self):
        for key, value in (("source_url", "https://example.com/another-business"),
                           ("license_id", "unknown"), ("country_tag", "PS")):
            record = synthetic_record()
            record[key] = value
            with self.assertRaises(ValueError):
                record_values(record)

    def test_android_canonical_identity_rejects_ranges_and_extensions(self):
        self.assertEqual(normalized_phone("+97237486860"), "037486860")
        self.assertEqual(normalized_phone("0097237486860"), "037486860")
        self.assertEqual(normalized_phone("*3456"), "*3456")
        for value in ("03-7486860;42", "03-7486860/1", "+970599123456", "unknown"):
            self.assertEqual(normalized_phone(value), "")

    def test_hebrew_points_preserved_for_display_but_unpointed_search_matches(self):
        record = synthetic_record()
        record["name"] = "בֵּית-קָפֶה בדיקה סינתטי"
        record["city"] = "חֵיפָה"
        self.inputs([record])
        self.build()
        with sqlite3.connect(self.output / "app-assets/business-directory/israel-businesses.sqlite") as database:
            source = database.execute("SELECT name,city FROM businesses").fetchone()
            self.assertEqual(source, tuple(unicodedata.normalize("NFC", record[key]) for key in ("name", "city")))
            for term in ('"בי*"', '"קפ*"', '"חיפ*"'):
                self.assertEqual(database.execute("SELECT count(*) FROM business_search WHERE business_search MATCH ?", (term,)).fetchone()[0], 1)


if __name__ == "__main__":
    unittest.main()
