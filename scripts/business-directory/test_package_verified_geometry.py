"""Negative admission contracts; synthetic fixtures never become app records."""
from __future__ import annotations

import copy
import hashlib
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

import business_phone_v2 as phone
import package_app_dataset as schema
import package_verified_geometry_dataset as package


class VerifiedGeometryPackageTest(unittest.TestCase):
    def setUp(self):
        self.policy = package.load_policy(package.REPO / "docs/business/source-geometry-admission-261103.md")
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)

    def fixture(self):
        row = dict(source_id="osm:node:123", source_url="https://www.openstreetmap.org/node/123",
            license_id="ODbL-1.0", verification="source-listed-not-phone-confirmed",
            source_country_fields={}, country_tag=None, source_updated_at="2026-11-03T12:00:00+00:00",
            name="Synthetic source fixture", phone="+972502345678", e164="+972502345678",
            extension="", city="", category="shop:books", address="")
        actual = dict(tags={}, timestamp=row["source_updated_at"])
        return row, actual

    def project(self, row=None, actual=None):
        base, source = self.fixture()
        return package.projected_values(row or base, b"synthetic fixture\n", actual or source,
            phone.normalized_phone, self.policy)[0]

    def test_policy_is_exact_and_does_not_authorize_publication(self):
        self.assertFalse(self.policy["authorizes_publication_or_signing_exception"])
        self.assertFalse(self.policy["force_expected_row_count"])
        self.assertTrue(self.policy["require_fresh_pbf_tags"])
        self.assertTrue(self.policy["require_complete_geometry"])
        self.assertEqual(2728, self.policy["bounded_raw_rows"])

    def test_policy_drift_is_rejected_before_use(self):
        modified = self.directory / "policy.md"
        modified.write_text((package.REPO / "docs/business/source-geometry-admission-261103.md").read_text() + "\n")
        with self.assertRaisesRegex(ValueError, "checksum"):
            package.load_policy(modified)

    def test_restricted_and_ambiguous_access_values_are_held(self):
        for value in ("private", "permit", "no", "members", "destination", "unknown", " PRIVATE ", "yes;private"):
            with self.subTest(value=value):
                self.assertIn("restricted-or-ambiguous-access", package.source_publicness({"access": value}, self.policy, "office:government"))

    def test_allowed_access_and_private_ownership_are_distinct(self):
        for value in self.policy["access_allowed"]:
            self.assertEqual([], package.source_publicness({"access": value.upper(), "ownership": "private", "operator:type": "private"}, self.policy, "shop:books"))

    def test_conditional_access_is_held_even_with_public_base(self):
        self.assertIn("conditional-access", package.source_publicness({"access": "yes", "access:conditional": "yes @ (Mo-Fr)"}, self.policy, "amenity:library"))

    def test_residential_flags_are_held_for_otherwise_public_businesses(self):
        for value in self.policy["residential_buildings"]:
            self.assertIn("residential-source-object", package.source_publicness({"building": " " + value.upper() + " ", "access": "public"}, self.policy, "shop:books"))
        for value in ("yes", "private"):
            self.assertIn("residential-source-object", package.source_publicness({"residential": value}, self.policy, "amenity:clinic"))

    def test_nonoperating_buildings_are_held(self):
        for value in self.policy["nonoperating_buildings"]:
            self.assertIn("nonoperating-building", package.source_publicness({"building": value.upper()}, self.policy, "shop:books"))

    def test_lifecycle_values_and_prefixed_keys_are_held(self):
        for prefix in self.policy["lifecycle_keys"]:
            for value in ("yes", "closed", "1999", " YES "):
                self.assertIn("lifecycle-flag:" + prefix, package.source_publicness({prefix: value}, self.policy, "shop:books"))
            for key in (prefix + ":shop", " " + prefix.upper() + ":shop "):
                self.assertIn("lifecycle-prefixed-tag:" + prefix, package.source_publicness({key: "books"}, self.policy, "shop:books"))

    def test_negative_lifecycle_values_and_empty_prefix_do_not_invent_holds(self):
        for prefix in self.policy["lifecycle_keys"]:
            for value in self.policy["lifecycle_allowed_values"]:
                self.assertEqual([], package.source_publicness({prefix: value, prefix + ":shop": ""}, self.policy, "shop:books"))

    def test_inactive_category_conflicts_are_held(self):
        for value in self.policy["inactive_category_values"]:
            self.assertIn("inactive-category-value", package.source_publicness({}, self.policy, "shop:" + value.upper()))

    def test_null_country_remains_null_and_source_rows_are_unchanged(self):
        row, actual = self.fixture()
        before = copy.deepcopy((row, actual))
        values = self.project(row, actual)
        self.assertEqual(before, (row, actual))
        self.assertIsNone(row["country_tag"])
        self.assertEqual("+972502345678", values[2])
        self.assertEqual("0502345678", values[11])

    def test_fabricated_or_conflicting_country_is_not_admitted(self):
        for change in (dict(country_tag="IL"), dict(source_country_fields={"addr:country": "IL"})):
            row, actual = self.fixture(); row.update(change)
            with self.assertRaisesRegex(ValueError, "country"):
                self.project(row, actual)
        row, actual = self.fixture(); actual["tags"]["addr:country"] = "PS"
        row.update(country_tag="PS", source_country_fields={"addr:country": "PS"})
        with self.assertRaisesRegex(ValueError, "country"):
            self.project(row, actual)

    def test_source_url_license_and_timestamp_are_bound(self):
        for change in (dict(source_url="https://example.com/node/123"), dict(license_id="unknown"),
                       dict(verification="phone-confirmed"), dict(source_updated_at="2026-11-04T12:00:00+00:00")):
            row, actual = self.fixture(); row.update(change)
            with self.assertRaises(ValueError):
                self.project(row, actual)

    def test_unreviewed_name_fallback_cannot_replace_source_text(self):
        row, actual = self.fixture(); row["source_id"] = "osm:node:5801536067"
        row["source_url"] = "https://www.openstreetmap.org/node/5801536067"
        actual["tags"]["name"] = "Unreviewed replacement"
        with self.assertRaisesRegex(ValueError, "Unauthorized source-name"):
            self.project(row, actual)

    def test_unsafe_city_address_and_controls_are_held(self):
        for field in ("name", "city", "address"):
            row, actual = self.fixture(); row[field] = "Source\u200e"
            with self.assertRaisesRegex(ValueError, "Unsafe source text"):
                self.project(row, actual)
        row, actual = self.fixture(); row["source_id"] = "osm:node:5066707334"
        row["source_url"] = "https://www.openstreetmap.org/node/5066707334"
        with self.assertRaisesRegex(ValueError, "Unsafe source city"):
            self.project(row, actual)

    def test_utf16_bounds_are_native_bounds_and_never_truncate(self):
        package.exact_text("\U0001f600" * 150, 300)
        with self.assertRaises(ValueError):
            package.exact_text("\U0001f600" * 151, 300)
        for text in (" value", "value ", "e\u0301", "line\n", "\ud800"):
            with self.assertRaises((ValueError, UnicodeError)):
                package.exact_text(text, 300)

    def test_source_service_phone_preserves_e164_and_roundtrip(self):
        row, _ = self.fixture(); row.update(phone="+9721599555535", e164="+9721599555535")
        self.assertEqual("1599555535", package.phone_identity(row, phone.normalized_phone))
        self.assertEqual("+9721599555535", row["phone"])

    def test_foreign_postdial_ranges_and_extensions_are_rejected(self):
        for raw in ("+14155552671", "+972501234567;12", "+972501234567/0521234567", "01599555535"):
            row, _ = self.fixture(); row.update(phone=raw, e164=raw)
            with self.assertRaises(ValueError):
                package.phone_identity(row, phone.normalized_phone)
        for extension in ("12", "0", "12;3", None):
            row, _ = self.fixture(); row["extension"] = extension
            with self.assertRaises(ValueError):
                package.phone_identity(row, phone.normalized_phone)

    def test_mismatched_canonical_identity_is_rejected(self):
        row, _ = self.fixture(); row["e164"] = "+972521234567"
        with self.assertRaises(ValueError):
            package.phone_identity(row, phone.normalized_phone)
        row, _ = self.fixture()
        with self.assertRaises(ValueError):
            package.phone_identity(row, lambda value: "0521234567")

    def database(self):
        path = self.directory / "fixture.sqlite"
        value = self.project()
        with sqlite3.connect(path) as db:
            schema.create_schema(db)
            db.execute("INSERT INTO metadata VALUES('dataset_version','synthetic-fixture')")
            db.execute("INSERT INTO businesses VALUES(" + ",".join("?" for _ in value) + ")", value)
            db.execute("INSERT INTO business_search(docid,name,city,category,phone) VALUES(?,?,?,?,?)",
                (value[0], package.search_text(value[1]), value[4], value[5], value[11]))
        manifest = dict(format="DA_CALLER_BUSINESSES", schema_version=1,
            dataset_version="synthetic-fixture", database_sha256=package.digest(path), record_count=1,
            source_url=package.SOURCE_PAGE, source_name=package.SOURCE_NAME,
            license="ODbL-1.0", license_url=package.LICENSE_URL,
            updated_at="2026-11-03")
        return path, value, manifest

    def test_clean_native_database_and_all_fts_fields_are_verified(self):
        path, value, manifest = self.database()
        proof = package.validate_database(path, manifest, [value])
        self.assertTrue(proof["actual_read_only_sqlite"])
        self.assertEqual(1, proof["all_projected_fields_and_fts_compared"])

    def test_same_count_cannot_hide_fts_field_or_identity_corruption(self):
        path, value, manifest = self.database()
        with sqlite3.connect(path) as db:
            db.execute("UPDATE business_search SET phone='0521234567'")
        manifest["database_sha256"] = package.digest(path)
        with self.assertRaises(ValueError):
            package.validate_database(path, manifest, [value])

    def test_research_tables_cannot_be_carried_into_clean_native_schema(self):
        path, value, manifest = self.database()
        with sqlite3.connect(path) as db:
            db.execute("CREATE TABLE candidate_decisions(fake TEXT)")
        manifest["database_sha256"] = package.digest(path)
        with self.assertRaises(ValueError):
            package.validate_database(path, manifest, [value])

    def test_readonly_hash_and_declared_manifest_count_are_checked(self):
        path, value, manifest = self.database()
        for change in (dict(database_sha256="0" * 64), dict(record_count=2), dict(dataset_version="other"), dict(source_name="OpenStreetMap")):
            bad = dict(manifest, **change)
            with self.assertRaises(ValueError):
                package.validate_database(path, bad, [value])

    def test_input_drift_fails_closed_and_atomic_output_never_overwrites(self):
        source = self.directory / "source.txt"; source.write_text("before")
        snapshots = {source: package.digest(source)}; source.write_text("after")
        with self.assertRaisesRegex(ValueError, "drift"):
            package.unchanged(snapshots)
        source_dir = self.directory / "temporary"; source_dir.mkdir()
        target = self.directory / "retained"; target.mkdir(); (target / "sentinel").write_text("keep")
        with self.assertRaises(FileExistsError):
            package.retain_atomic(source_dir, target)
        self.assertEqual("keep", (target / "sentinel").read_text())
        self.assertTrue(source_dir.is_dir())


if __name__ == "__main__":
    unittest.main()
