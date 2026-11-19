"""Synthetic source-text/review-only contracts; no phone/currentness claims."""
import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


HERE = Path(__file__).resolve().parent
review = module("source_text_review_under_test", HERE / "source_text_review.py")
production = module("production_text_comparison", HERE.parent / "package_app_dataset.py")


def sample(name="Synthetic test business"):
    return dict(name=name, phone="+972000000000", source_id="osm:node:1",
                source_url="https://www.openstreetmap.org/node/1",
                source_updated_at="2026-01-01T00:00:00+00:00",
                city="", category="shop:synthetic", address="",
                source_names={"name:he": name}, country_tag=None,
                geographic_scope="research-only")


def node(row, **tags):
    return {**{key: row[key] for key in ("source_id", "source_url", "source_updated_at")},
            "source_fields": {**row["source_names"], **tags}}


class TextBoundaryContract(unittest.TestCase):
    def test_name_300_passes_301_fails_without_truncation(self):
        self.assertTrue(review.review_text("א" * 300, 300)["within_text_contract"])
        actual = review.review_text("א" * 301, 300)
        self.assertEqual(["exceeds-code-point-bound"], actual["reasons"])
        self.assertEqual("א" * 301, actual["original_value"])

    def test_all_production_field_bounds_exact_and_one_over(self):
        for field, (maximum, empty, _) in review.TEXT_FIELDS.items():
            with self.subTest(field=field):
                self.assertTrue(review.review_text("x" * maximum, maximum, empty=empty)["within_text_contract"])
                self.assertFalse(review.review_text("x" * (maximum + 1), maximum, empty=empty)["within_text_contract"])

    def test_nfc_length_is_observed_but_original_is_preserved(self):
        original = "e\u0301" * 300
        actual = review.review_text(original, 300)
        self.assertEqual(600, actual["original_length"])
        self.assertEqual(300, actual["contract_length"])
        self.assertTrue(actual["within_text_contract"])
        self.assertEqual(original, actual["original_value"])

    def test_required_whitespace_is_empty(self):
        self.assertEqual(["empty-required-text"], review.review_text(" \t\n", 300)["reasons"])

    def test_optional_empty_and_missing_defaults_match(self):
        row = sample()
        for field in ("city", "category", "address"):
            del row[field]
        self.assertEqual([], review.review_record(row)["failed_fields"])
        self.assertTrue(review.review_text("\t\n", 300, empty=True)["within_text_contract"])

    def test_outer_whitespace_controls_are_observed_as_production_strips_them(self):
        actual = review.review_text("\tname\n", 300)
        self.assertTrue(actual["within_text_contract"])
        self.assertEqual(2, len(actual["original_controls"]))
        self.assertEqual([], actual["contract_controls"])

    def test_inner_cc_controls_fail(self):
        for char in ("\t", "\n", "\x00", "\x7f"):
            with self.subTest(code=ord(char)):
                actual = review.review_text("a" + char + "b", 300)
                self.assertFalse(actual["within_text_contract"])
                self.assertEqual("Cc", actual["contract_controls"][0]["category"])

    def test_cf_bidi_marks_fail_even_at_edges(self):
        for char in ("\u200e", "\u202b", "\u202c", "\u2067", "\u200d"):
            with self.subTest(code=ord(char)):
                actual = review.review_text(char + "שם", 300)
                self.assertFalse(actual["within_text_contract"])
                self.assertEqual(f"U+{ord(char):04X}", actual["contract_controls"][0]["code_point"])

    def test_both_oversize_and_controls_are_reported(self):
        self.assertEqual(["exceeds-code-point-bound", "disallowed-control-or-format-character"],
                         review.review_text("x" * 300 + "\u200e", 300)["reasons"])

    def test_hebrew_arabic_marks_and_emoji_without_controls_are_allowed(self):
        for value in ("בית עסק", "دار كنا الضيافة", "בַּיִת", "Café", "☎️"):
            self.assertTrue(review.review_text(value, 300)["within_text_contract"])

    def test_non_string_is_not_coerced(self):
        for value in (None, 123, True, [], {}):
            self.assertEqual(["not-source-text"], review.review_text(value, 300)["reasons"])

    def test_invalid_bounds_are_rejected(self):
        for maximum in (0, -1, True, 1.5):
            with self.assertRaises(ValueError):
                review.review_text("x", maximum)

    def test_independent_comparison_to_unchanged_production_text_contract(self):
        for maximum in (80, 100, 300, 1200):
            for empty in (True, False):
                for value in (None, "", " \t", "\tname\n", "e\u0301" * maximum,
                              "x" * maximum, "x" * (maximum + 1), "a\nname", "\u200eשם"):
                    with self.subTest(maximum=maximum, empty=empty, value=repr(value)[:35]):
                        actual = review.review_text(value, maximum, empty=empty)
                        try:
                            expected = production.bounded_text(value, maximum, empty=empty)
                        except ValueError:
                            self.assertFalse(actual["within_text_contract"])
                        else:
                            self.assertTrue(actual["within_text_contract"])
                            self.assertEqual(expected, actual["contract_observed_text"])


class AlternativeContract(unittest.TestCase):
    def test_source_alternatives_are_exact_and_never_selected(self):
        row = sample("\u200eמקורי")
        source = node(row, name="Original source name", short_name="Exact Short")
        actual = review.review_record(row, source)
        self.assertEqual({"Original source name", "Exact Short"},
                         {candidate["exact_source_value"] for candidate in actual["source_name_alternatives"]})
        for candidate in actual["source_name_alternatives"]:
            self.assertEqual(review.ALTERNATIVE_STATUS, candidate["status"])
            self.assertFalse(candidate["automatically_selected"])
            self.assertFalse(candidate["app_accepted"])
        self.assertEqual(row["name"], actual["original_record"]["name"])

    def test_bidi_annotated_name_is_not_stripped_or_reversed(self):
        raw = "\u202bבכנא\u202c \u202bהארחה\u202c \u202bבית\u202c"
        row = sample(raw)
        actual = review.review_record(row, node(row))
        self.assertEqual([], actual["source_name_alternatives"])
        self.assertEqual(raw, actual["original_record"]["name"])
        self.assertEqual(6, len(actual["field_reviews"]["name"]["contract_controls"]))

    def test_oversize_name_not_truncated_and_bad_alternatives_not_promoted(self):
        row = sample("x" * 301)
        actual = review.review_record(row, node(row, name="y" * 301, short_name="\u200ebad"))
        self.assertEqual([], actual["source_name_alternatives"])
        self.assertEqual("x" * 301, actual["original_record"]["name"])

    def test_same_value_alternatives_group_provenance(self):
        row = sample("\u200eמקורי")
        source = node(row, name="Exact", **{"name:en": "Exact"})
        actual = review.review_record(row, source)["source_name_alternatives"]
        self.assertEqual(1, len(actual))
        self.assertEqual(["name", "name:en"], actual[0]["source_fields"])

    def test_brand_operator_and_old_name_are_not_name_substitutes(self):
        row = sample("\u200eמקורי")
        source = node(row, brand="Brand", operator="Operator", old_name="Obsolete")
        self.assertEqual([], review.review_record(row, source)["source_name_alternatives"])

    def test_name_metadata_keys_are_not_replacement_names(self):
        row = sample("\u200eמקורי")
        for key in ("name:pronunciation", "name:etymology", "name:source",
                    "name:description", "name:he:pronunciation", "short_name:etymology"):
            with self.subTest(key=key):
                source = node(row, **{key: "Clean metadata text"})
                actual = review.review_record(row, source)
                self.assertEqual([], actual["source_name_alternatives"])
                self.assertEqual("Clean metadata text", actual["independent_source_node"]["source_fields"][key])

    def test_only_explicit_supported_language_keys_are_proposed_without_guessing(self):
        row = sample("\u200eמקורי")
        for base in ("name", "short_name"):
            for language in ("he", "ar", "en"):
                key = f"{base}:{language}"
                self.assertIn(key, review.NAME_KEYS)
                # Do not change the source-bound invalid preferred name:he.
                if key == "name:he":
                    continue
                actual = review.review_record(row, node(row, **{key: "Exact source text"}))
                self.assertEqual([key], actual["source_name_alternatives"][0]["source_fields"])
        for key in ("name:fr", "name:en_US", "name:he-Latn", "name:unknown", "name:EN"):
            self.assertNotIn(key, review.NAME_KEYS)
            self.assertEqual([], review.review_record(row, node(row, **{key: "Unselected raw text"}))["source_name_alternatives"])

    def test_no_mutation_of_record_or_reread_node(self):
        row = sample("\u200eמקורי")
        source = node(row, name="Alternate")
        row_before, source_before = copy.deepcopy(row), copy.deepcopy(source)
        actual = review.review_record(row, source)
        actual["original_record"]["source_names"]["name:he"] = "changed report copy"
        actual["independent_source_node"]["source_fields"]["name"] = "changed copy"
        self.assertEqual(row_before, row)
        self.assertEqual(source_before, source)

    def test_source_id_timestamp_and_url_mismatch_rejected(self):
        row = sample("\u200eמקורי")
        for field in ("source_id", "source_url", "source_updated_at"):
            source = node(row, name="Alternate")
            source[field] = "different"
            with self.assertRaises(ValueError):
                review.review_record(row, source)

    def test_source_preferred_tag_mismatch_rejected(self):
        row = sample("\u200eמקורי")
        source = node(row, **{"name:he": "Different"})
        with self.assertRaises(ValueError):
            review.review_record(row, source)

    def test_non_name_failure_still_binds_source_id_timestamp_and_url(self):
        row = sample()
        row["city"] = "City\u200e"
        for field in ("source_id", "source_url", "source_updated_at"):
            with self.subTest(field=field):
                source = node(row)
                source[field] = "unrelated"
                with self.assertRaises(ValueError):
                    review.review_record(row, source)

    def test_clean_row_supplied_source_must_also_bind_and_retain_raw_fields(self):
        row = sample()
        source = node(row)
        self.assertEqual([], review.review_record(row, source)["failed_fields"])
        for field in ("source_id", "source_url", "source_updated_at"):
            with self.subTest(field=field):
                unrelated = copy.deepcopy(source)
                del unrelated[field]
                with self.assertRaises(ValueError):
                    review.review_record(row, unrelated)
        source.pop("source_fields")
        with self.assertRaises(ValueError):
            review.review_record(row, source)

    def test_exact_alternative_whitespace_or_nfc_are_not_rewritten(self):
        row = sample("\u200eמקורי")
        raw = "  Cafe\u0301  "
        actual = review.review_record(row, node(row, name=raw))
        self.assertEqual(raw, actual["source_name_alternatives"][0]["exact_source_value"])

    def test_text_only_pass_never_fills_country_or_accepts_app_row(self):
        row = sample()
        actual = review.review_record(row)
        self.assertEqual("WITHIN_TEXT_BOUNDS_ONLY", actual["classification"])
        self.assertEqual(review.RESEARCH_STATUS, actual["status"])
        self.assertFalse(actual["app_accepted"])
        self.assertIsNone(actual["original_record"]["country_tag"])

    def test_later_city_and_address_controls_detected_without_forging_country(self):
        row = sample()
        row.update(city="Tel Aviv\u200e", address="Synthetic Street 3 Tel Aviv\u200e")
        original = copy.deepcopy(row)
        actual = review.review_record(row)
        self.assertEqual(["city", "address"], actual["failed_fields"])
        self.assertTrue(actual["field_reviews"]["name"]["within_text_contract"])
        self.assertEqual(original, row)
        self.assertIsNone(actual["original_record"]["country_tag"])
        self.assertFalse(actual["app_accepted"])

    def test_evidence_is_exclusively_created_and_original_bidi_is_json_escaped(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "report.json"
            review.write_report(path, {"original": "\u200eשם"})
            self.assertIn("\\u200e", path.read_text())
            with self.assertRaises(FileExistsError):
                review.write_report(path, {})


if __name__ == "__main__":
    unittest.main(verbosity=2)
