"""Business-only grammar regressions and ten retained, real source UAN rows.

The immutable source-name research archive is a source fixture, not an app asset
or an assertion of present ownership, currentness or country-wide coverage.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
import zipfile

from business_phone_v2 import CONTRACT_ID, is_country_code_only, normalized_phone, phone_prefix


class BusinessPhoneV2Test(unittest.TestCase):
    def test_contract_has_an_explicit_new_identity(self):
        self.assertEqual("DA_CALLER_BUSINESS_PHONE_V2", CONTRACT_ID)

    def test_service_aliases_preserve_the_national_service_identity(self):
        for value in ("1599555535", "+9721599555535", "009721599555535", "9721599555535", "+972 1599-555-535"):
            with self.subTest(value=value):
                self.assertEqual("1599555535", normalized_phone(value))
                self.assertEqual("1599555535", phone_prefix(value))
        for value in ("1599", "+9721599", "009721599", "9721599"):
            self.assertEqual("1599", phone_prefix(value))
        self.assertEqual("", normalized_phone("01599555535"))
        self.assertEqual("", normalized_phone("+97201599555535"))

    def test_fixed_mobile_and_stars_keep_their_existing_identity(self):
        examples = {
            "031234567": "031234567", "+97231234567": "031234567",
            "00972 4-123-4567": "041234567", "97291234567": "091234567",
            "+972501234567": "0501234567", "00972581234567": "0581234567",
            "972771234567": "0771234567", "*3456": "*3456",
        }
        for raw, expected in examples.items():
            with self.subTest(raw=raw):
                self.assertEqual(expected, normalized_phone(raw))
                self.assertEqual(expected, phone_prefix(raw))
        self.assertEqual("050123", phone_prefix("+972 50-123"))
        self.assertEqual("*34", phone_prefix("*34"))
        self.assertEqual("1234567", phone_prefix("1234567"))  # literal prefix, never a suffix expansion

    def test_country_only_is_explicit_and_cannot_be_created_by_invalid_input(self):
        for raw in ("+972", "00972", "972", " (+972) ", "00 972", "\u00a0+972\u00a0"):
            with self.subTest(raw=raw):
                self.assertTrue(is_country_code_only(raw))
                self.assertEqual("", normalized_phone(raw))
                self.assertEqual("", phone_prefix(raw))
        for raw in ("+1", "+44", "+972+", "+972;12", "+972,", "+972/", "972 OR *", "+972\u2009", "%", "", None):
            self.assertFalse(is_country_code_only(raw), raw)

    def test_foreign_postdial_ranges_and_hidden_digits_do_not_become_identities(self):
        invalid = (
            "+1 599555535", "+44 1599555535", "+972+1599555535", "+9721599555535;12",
            "1599555535,1599500901", "1599555535 ext 12", "1599555535/1599500901",
            "1599555535-1599500901", "1599\u202e555535", "١٥٩٩٥٥٥٥٣٥", "1599\t555535",
            "phone 0501234567", "טלפון0501234567", None, 1599555535,
        )
        for raw in invalid:
            with self.subTest(raw=raw):
                self.assertEqual("", normalized_phone(raw))
                self.assertFalse(is_country_code_only(raw))
        for raw in invalid:
            self.assertEqual("", phone_prefix(raw))

    def test_ascii_edge_trim_and_nbsp_match_native_grammar(self):
        self.assertEqual("1599555535", normalized_phone("\t+972 1599\u00a0555-535\n"))
        for raw in ("\u2009+9721599555535", "+9721599\n555535", "+9721599\x00555535"):
            self.assertEqual("", normalized_phone(raw))
            self.assertEqual("", phone_prefix(raw))

    def test_all_ten_retained_source_service_rows_have_exact_known_identities(self):
        archive = Path(__file__).resolve().parents[2] / "docs/evidence/business-acquisition/owner-261103/source-name-reviewed.zip"
        expected = {
            "osm:node:12625537932": ("+9721599510510", "1599510510"),
            "osm:node:11881331125": ("+9721599554949", "1599554949"),
            "osm:way:137316205": ("+9721599520520", "1599520520"),
            "osm:way:94669391": ("+9721599550560", "1599550560"),
            "osm:node:9970152588": ("+9721599500907", "1599500907"),
            "osm:node:14046803650": ("+9721599510510", "1599510510"),
            "osm:way:531392365": ("+9721599500171", "1599500171"),
            "osm:node:5291178729": ("+9721599500901", "1599500901"),
            "osm:node:10001924959": ("+9721599510510", "1599510510"),
            "osm:node:1753285823": ("+9721599555535", "1599555535"),
        }
        with zipfile.ZipFile(archive) as retained:
            source = retained.read("candidate/candidate-businesses.sqlite")
        source_sha = "36a7c0a07fad679249c44c975cba7c8d4fb00171d593d19f4ff0c6413a0f115a"
        self.assertEqual(source_sha, hashlib.sha256(source).hexdigest())
        with tempfile.TemporaryDirectory() as temporary:
            database = Path(temporary) / "retained-source.sqlite"
            database.write_bytes(source)
            with sqlite3.connect(f"{database.as_uri()}?mode=ro", uri=True) as connection:
                rows = connection.execute("SELECT original_line,line_sha FROM candidate_raw_records JOIN candidate_decisions USING(record_key) WHERE decision='WITHHELD_PHONE_FORMAT_REVIEW'").fetchall()
            self.assertEqual(10, len(rows))
            observed = set()
            phones = set()
            for raw, raw_sha in rows:
                self.assertEqual(raw_sha, hashlib.sha256(raw).hexdigest())
                row = json.loads(raw)
                observed.add(row["source_id"])
                e164, national = expected[row["source_id"]]
                self.assertEqual(e164, row["phone"])
                self.assertEqual(national, normalized_phone(row["phone"]))
                self.assertEqual(national, normalized_phone(row["original_phone"]))
                phones.add(e164)
                for prefix in ("1599", "+9721599", "009721599", "9721599"):
                    self.assertTrue(national.startswith(phone_prefix(prefix)))
            self.assertEqual(set(expected), observed)
            self.assertEqual(8, len(phones))
            self.assertEqual(source_sha, hashlib.sha256(database.read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main()
