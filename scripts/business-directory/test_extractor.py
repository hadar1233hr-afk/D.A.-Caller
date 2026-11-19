"""Synthetic fixture tests only. These names/phones never enter a real dataset."""
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

import osmium

from extract_osm_businesses import Extractor, category, phones, schema


class PhoneValidation(unittest.TestCase):
    def test_israeli_national_and_e164_deduplicate(self):
        valid, bad = phones("03-7486860;+972 3 7486860")
        self.assertEqual([p["phone"] for p in valid], ["+97237486860"])
        self.assertEqual(bad, [])

    def test_non_israeli_numbers_are_not_relabelled(self):
        valid, bad = phones("+970599123456;+12025550123")
        self.assertFalse(valid)
        self.assertEqual(len(bad), 2)

    def test_ambiguous_abbreviated_ranges_are_rejected(self):
        self.assertEqual(phones("8375777/8")[0], [])
        self.assertEqual(phones("03-7486860 hello")[0], [])

    def test_rfc_extension_is_separate(self):
        valid, bad = phones("tel:+972-3-7486860;ext=42")
        self.assertEqual(valid[0]["extension"], "42")
        self.assertEqual(valid[0]["phone"], "+97237486860")
        self.assertFalse(bad)

    def test_public_star_codes_do_not_become_fabricated_e164(self):
        valid, bad = phones("*3456")
        self.assertEqual(valid[0]["phone"], "*3456")
        self.assertIsNone(valid[0]["e164"])
        self.assertFalse(bad)

    def test_bidi_and_unicode_digits_are_normalized(self):
        self.assertEqual(phones("\u200e+972-3-7486860")[0][0]["phone"], "+97237486860")

    def test_national_service_extension_never_becomes_dialled_digits(self):
        valid, bad = phones("1800-200-800;ext=42")
        self.assertFalse(bad)
        self.assertEqual(valid[0]["phone"], "1800200800")
        self.assertEqual(valid[0]["extension"], "42")


class PublicBusinessFilter(unittest.TestCase):
    def test_residential_and_private_objects_do_not_qualify(self):
        self.assertIsNone(category({"building": "house", "name": "Synthetic home", "phone": "03-7486860"}))
        self.assertIsNone(category({"office": "company", "access": "private"}))

    def test_closed_places_are_not_current_businesses(self):
        self.assertIsNone(category({"shop": "vacant"}))
        self.assertIsNone(category({"shop": "supermarket", "disused": "yes"}))

    def test_public_service_and_named_shop_categories_qualify(self):
        self.assertEqual(category({"shop": "supermarket"}), "shop:supermarket")
        self.assertEqual(category({"office": "insurance"}), "office:insurance")

    def test_real_osmium_fixture_preserves_country_source_and_duplicates(self):
        xml = '''<?xml version="1.0"?><osm version="0.6" generator="synthetic-test-only">
          <node id="1" lat="32" lon="34.8" version="1" timestamp="2026-01-01T00:00:00Z">
            <tag k="shop" v="supermarket"/><tag k="name:he" v="עסק בדיקה סינתטי"/>
            <tag k="addr:country" v="IL"/><tag k="phone" v="03-7486860"/>
            <tag k="contact:phone" v="+97237486860"/><tag k="addr:street" v="רחוב בדיקה"/>
          </node>
          <node id="2" lat="32" lon="34.8" version="1"><tag k="office" v="company"/>
            <tag k="name" v="Synthetic non-IL"/><tag k="addr:country" v="PS"/>
            <tag k="phone" v="+97237486860"/></node>
          <node id="3" lat="32" lon="34.8" version="1"><tag k="building" v="house"/>
            <tag k="name" v="Synthetic home"/><tag k="phone" v="+97237486860"/></node>
          <node id="4" lat="32" lon="34.8" version="1"><tag k="office" v="company"/>
            <tag k="name" v="Synthetic untagged country"/><tag k="phone" v="+97237486860"/></node>
        </osm>'''
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "fixture.osm"
            path.write_text(xml)
            db = sqlite3.connect(":memory:")
            schema(db)
            handler = Extractor(db, "2026-11-02T00:00:00+00:00", "explicit-il")
            handler.apply_file(str(path), locations=False)
            rows = db.execute("SELECT name,phone,record_json FROM business_contacts").fetchall()
            self.assertEqual(len(rows), 1)
            self.assertEqual(rows[0][:2], ("עסק בדיקה סינתטי", "+97237486860"))
            record = json.loads(rows[0][2])
            self.assertEqual(record["source_url"], "https://www.openstreetmap.org/node/1")
            self.assertEqual(record["verification"], "source-listed-not-phone-confirmed")
            self.assertEqual(handler.counts["rejected_explicit_non_il_country"], 1)
            self.assertEqual(handler.counts["rejected_missing_explicit_il_country"], 1)


if __name__ == "__main__":
    unittest.main()
