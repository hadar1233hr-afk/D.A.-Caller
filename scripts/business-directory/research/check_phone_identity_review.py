#!/usr/bin/env python3
"""Focused offline phone-identity contracts, separate from Android verification."""
from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

import phonenumbers

SCRIPT = Path(__file__).with_name("phone_identity_review.py")
spec = importlib.util.spec_from_file_location("phone_identity_under_test", SCRIPT)
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)

# Actual source forms for the seven audited app-dial-identity rejections. These
# are retained source observations, never currentness, owner or call-test claims.
SOURCE_FORMS = (
    ("1753285823", "1599555535", "+9721599555535", "1599555535"),
    ("5291178729", "1599-500-901", "+9721599500901", "1599500901"),
    ("9970152588", "1599 500 907", "+9721599500907", "1599500907"),
    ("10001924959", "1599510510", "+9721599510510", "1599510510"),
    ("11881331125", "+9721599554949", "+9721599554949", "1599554949"),
    ("12625537932", "+972 159 951 0510", "+9721599510510", "1599510510"),
    ("14046803650", "+972 159 951 0510", "+9721599510510", "1599510510"),
)


def record(phone="+9721599555535", e164="+9721599555535", **extra):
    result = {
        "name": "Source-listed fixture; not independently phone-confirmed",
        "phone": phone,
        "e164": e164,
        "original_phone": "1599555535",
        "extension": "",
        "country_tag": None,
        "source_country_fields": {},
        "geographic_scope": "research-node-source-IL-interior-minus-filled-WB-and-Gaza",
        "source_id": "osm:node:1753285823",
        "source_url": "https://www.openstreetmap.org/node/1753285823",
        "license_id": "ODbL-1.0",
        "verification": "source-listed-not-phone-confirmed",
        "source_updated_at": "2023-10-20T14:12:50+00:00",
    }
    result.update(extra)
    return result


class PhoneIdentityContract(unittest.TestCase):
    def test_all_seven_actual_source_forms_propose_same_e164_without_trunk_zero(self):
        identities = set()
        for node, original, canonical, expected in SOURCE_FORMS:
            with self.subTest(node=node):
                source = record(phone=canonical, e164=canonical, original_phone=original,
                                source_id="osm:node:" + node)
                decision = review.review_phone_identity(source)
                proposal = decision["proposal"]
                self.assertEqual("RESEARCH_REFORMAT_PROPOSAL", decision["decision"])
                self.assertEqual("national-service-identity-without-trunk-zero", decision["reason"])
                self.assertEqual(expected, proposal["base_dial_identity"])
                self.assertFalse(proposal["base_dial_identity"].startswith("0"))
                self.assertEqual("UAN", proposal["phone_type"])
                self.assertEqual(canonical, phonenumbers.format_number(
                    phonenumbers.parse(original, "IL"), phonenumbers.PhoneNumberFormat.E164))
                self.assertEqual(canonical, phonenumbers.format_number(
                    phonenumbers.parse(expected, "IL"), phonenumbers.PhoneNumberFormat.E164))
                identities.add(proposal["e164"])
        self.assertEqual(5, len(identities))

    def test_existing_standard_fixed_and_mobile_identities_stay_unchanged(self):
        for raw, canonical, national in (
            ("+97235551234", "+97235551234", "035551234"),
            ("03-555-1234", "+97235551234", "035551234"),
            ("+972502345678", "+972502345678", "0502345678"),
            ("00972 50 234 5678", "+972502345678", "0502345678"),
            ("050-2345678", "+972502345678", "0502345678"),
        ):
            with self.subTest(raw=raw):
                decision = review.review_phone_identity(record(raw, canonical))
                self.assertEqual("IDENTITY_UNCHANGED", decision["decision"])
                self.assertEqual(national, decision["proposal"]["base_dial_identity"])
                self.assertEqual(national, decision["current_app_base_identity"])

    def test_national_uan_identity_does_not_propose_another_phone(self):
        decision = review.review_phone_identity(record("1599-555535"))
        self.assertEqual("IDENTITY_UNCHANGED", decision["decision"])
        self.assertEqual("1599555535", decision["proposal"]["base_dial_identity"])

    def test_foreign_number_is_never_inferred_to_be_israeli(self):
        for raw in ("+970599123456", "+12025550123", "+442079460123"):
            decision = review.review_phone_identity(record(raw, raw, country_tag="IL"))
            self.assertEqual("REJECTED_FOR_PHONE_PROPOSAL", decision["decision"])
            self.assertEqual("not-israeli-numbering-plan", decision["reason"])
            self.assertIsNone(decision["proposal"])

    def test_malformed_ambiguous_or_hidden_base_forms_are_not_stripped_into_numbers(self):
        for raw in ("", " ", "++9721599555535", "1599+555535", "1599555535/6",
                    "1599555535;1599500901", "tel:+9721599555535", "1599555535 ext 12",
                    "1599555535#12", "1599555535\u200e", "１５９９５５５５３５", "1" * 81, None):
            with self.subTest(raw=raw):
                decision = review.review_phone_identity(record(raw))
                self.assertEqual("REJECTED_FOR_PHONE_PROPOSAL", decision["decision"])
                self.assertIsNone(decision["proposal"])

    def test_inconsistent_source_e164_is_not_silently_replaced(self):
        source = record(e164="+9721599500901")
        decision = review.review_phone_identity(source)
        self.assertEqual("source-canonical-identities-disagree", decision["reason"])
        self.assertEqual(source, decision["source_record"])
        self.assertIsNone(decision["proposal"])

    def test_invalid_numbering_plan_candidate_has_no_proposal(self):
        for raw in ("+9721599", "+972999999999999", "159955553500000000"):
            decision = review.review_phone_identity(record(raw, raw))
            self.assertEqual("REJECTED_FOR_PHONE_PROPOSAL", decision["decision"])
            self.assertIsNone(decision["proposal"])

    def test_app_shape_alone_does_not_establish_numbering_plan_validity(self):
        # The current app accepts the shape; the pinned library rejects this
        # prefix. Research must not claim metadata validity from shape alone.
        source = record("+972501234567", "+972501234567")
        self.assertEqual("0501234567", review.production_packager().normalized_phone(source["phone"]))
        decision = review.review_phone_identity(source)
        self.assertEqual("REJECTED_FOR_PHONE_PROPOSAL", decision["decision"])
        self.assertIsNone(decision["proposal"])

    def test_separate_extension_is_preserved_but_never_appended(self):
        source = record(extension="007", original_phone="1599555535 ext. 007")
        decision = review.review_phone_identity(source)
        self.assertEqual("1599555535", decision["proposal"]["base_dial_identity"])
        self.assertEqual("007", decision["proposal"]["extension_retained_separately"])
        self.assertEqual(source, decision["source_record"])

    def test_malformed_extension_is_never_coerced_or_called(self):
        for extension in (7, "7#8", "7;8", "1" * 21):
            decision = review.review_phone_identity(record(extension=extension))
            self.assertEqual("invalid-separate-extension", decision["reason"])
            self.assertIsNone(decision["proposal"])

    def test_complete_original_is_deep_copied_and_never_mutated(self):
        source = record(source_country_fields={"country": "DE"}, country_tag=None)
        before = copy.deepcopy(source)
        decision = review.review_phone_identity(source)
        self.assertEqual(before, source)
        self.assertEqual(before, decision["source_record"])
        decision["source_record"]["source_country_fields"]["country"] = "ZZ"
        self.assertEqual("DE", source["source_country_fields"]["country"])
        self.assertIsNone(decision["source_record"]["country_tag"])

    def test_missing_country_and_scope_are_not_invented(self):
        source = record()
        del source["country_tag"]
        del source["geographic_scope"]
        decision = review.review_phone_identity(source)
        self.assertNotIn("country_tag", decision["source_record"])
        self.assertNotIn("geographic_scope", decision["source_record"])
        self.assertEqual("RESEARCH_REFORMAT_PROPOSAL", decision["decision"])
        self.assertFalse(decision["app_accepted"])

    def test_production_packager_still_rejects_original_and_country_guard_after_proposal(self):
        source = record()
        decision = review.review_phone_identity(source)
        app = review.production_packager()
        with self.assertRaisesRegex(ValueError, "Invalid app dial identity"):
            app.record_values(source)
        # A disposable test copy demonstrates that national formatting cannot
        # bypass country/scope admission. The helper returns no rewritten row.
        hypothetical = copy.deepcopy(source)
        hypothetical["phone"] = decision["proposal"]["base_dial_identity"]
        with self.assertRaisesRegex(ValueError, "Country membership not explicitly sourced"):
            app.record_values(hypothetical)
        self.assertEqual("+9721599555535", source["phone"])

    def test_every_result_is_marked_not_app_accepted(self):
        for source in (record(), record("+972502345678", "+972502345678"), record("bad")):
            decision = review.review_phone_identity(source)
            self.assertEqual("RESEARCH_ONLY_NOT_APP_ACCEPTED", decision["status"])
            self.assertIs(False, decision["app_accepted"])
            self.assertNotIn("rewritten_record", decision)
            if decision["proposal"]:
                self.assertTrue(decision["proposal"]["requires_review_before_production_use"])

    def test_corpus_review_reads_only_and_preserves_unrelated_failures(self):
        corpus = [record(), record("+972502345678", "+972502345678"), record(name="bad\u200ename")]
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "source.ndjson"
            data = "".join(json.dumps(row) + "\n" for row in corpus).encode()
            source.write_bytes(data)
            before = source.stat().st_mtime_ns
            report = review.review_corpus(source)
            self.assertEqual(data, source.read_bytes())
            self.assertEqual(before, source.stat().st_mtime_ns)
            self.assertEqual(hashlib.sha256(data).hexdigest(), report["source_sha256"])
            self.assertEqual(3, report["source_rows"])
            self.assertEqual(1, report["phone_rejected_rows"])
            self.assertEqual(1, report["production_results"]["Country membership not explicitly sourced"])
            self.assertEqual(1, report["production_results"]["Invalid source text length/control character"])
            self.assertEqual(0, report["rows_added_to_app"])
            self.assertFalse(report["app_accepted"])

    def test_exact_retained_2448_row_snapshot_has_seven_uan_rejections(self):
        repository = SCRIPT.resolve().parents[3]
        retained = repository / "docs/evidence/business-acquisition/owner-261103/node-geography-research-candidates.zip"
        with zipfile.ZipFile(retained) as archive:
            source_bytes = archive.read("research-node-businesses.ndjson")
        self.assertEqual("37ce40b735e144cc38eee1e72ce75c785424f0213d05c9c35feb766c1b2704df",
                         hashlib.sha256(source_bytes).hexdigest())
        with tempfile.TemporaryDirectory() as temporary:
            copy_path = Path(temporary) / "retained-source-copy.ndjson"
            copy_path.write_bytes(source_bytes)
            report = review.review_corpus(copy_path)
        self.assertEqual(2448, report["source_rows"])
        self.assertEqual({"Country membership not explicitly sourced": 2439,
                          "Invalid app dial identity": 7,
                          "Invalid source text length/control character": 2}, report["production_results"])
        self.assertEqual(7, report["phone_rejected_rows"])
        self.assertEqual(5, report["phone_rejected_unique_e164"])
        self.assertEqual({"RESEARCH_REFORMAT_PROPOSAL": 7}, report["phone_review_decisions"])
        actual = {item["source_record"]["source_id"].split(":")[-1]: item
                  for item in report["phone_rejections"]}
        for node, original, canonical, expected in SOURCE_FORMS:
            with self.subTest(node=node):
                decision = actual[node]
                self.assertEqual(original, decision["source_record"]["original_phone"])
                self.assertEqual(canonical, decision["source_record"]["phone"])
                self.assertEqual(canonical, decision["source_record"]["e164"])
                self.assertIsNone(decision["source_record"]["country_tag"])
                self.assertEqual({}, decision["source_record"]["source_country_fields"])
                self.assertEqual("", decision["source_record"]["extension"])
                self.assertEqual(expected, decision["proposal"]["base_dial_identity"])
                self.assertEqual("UAN", decision["proposal"]["phone_type"])
                self.assertFalse(decision["app_accepted"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
