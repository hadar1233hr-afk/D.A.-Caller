#!/usr/bin/env python3
"""Describe source-text blockers without rewriting or admitting research rows.

This is a research report, not an alternative production packager. Bounds mirror
package_app_dataset.bounded_text: NFC, strip, then code-point length and Cc/Cf.
Original values and complete independently reread node tags remain in the report.
Clean source name/short_name alternatives are proposals for human review only.
"""
from __future__ import annotations

import argparse
import collections
import copy
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import unicodedata


# (code-point maximum after NFC/strip, permits empty, missing optional default)
TEXT_FIELDS = {
    "name": (300, False, None),
    "phone": (80, False, None),
    "source_id": (100, False, None),
    "source_updated_at": (80, False, None),
    "city": (300, True, ""),
    "category": (300, True, ""),
    "address": (1200, True, ""),
}
RESEARCH_STATUS = "RESEARCH_ONLY_NOT_APP_ACCEPTED"
ALTERNATIVE_STATUS = "REVIEW_ONLY_NOT_SELECTED_OR_APP_ACCEPTED"
# A suffix is not necessarily a language: name:pronunciation/name:etymology
# describe metadata. Preserve every raw tag, but never guess its language.
SUPPORTED_NAME_LANGUAGES = ("he", "ar", "en")
NAME_KEYS = frozenset(
    key for base in ("name", "short_name")
    for key in (base, *(f"{base}:{language}" for language in SUPPORTED_NAME_LANGUAGES))
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def controls(text: str) -> list[dict]:
    return [
        {"index": index, "code_point": f"U+{ord(char):04X}",
         "category": unicodedata.category(char),
         "unicode_name": unicodedata.name(char, "UNNAMED")}
        for index, char in enumerate(text)
        if unicodedata.category(char) in {"Cc", "Cf"}
    ]


def review_text(value: object, maximum: int, *, empty: bool = False) -> dict:
    """Observe the existing text contract; never return a repaired value."""
    if type(maximum) is not int or maximum < 1:
        raise ValueError("A positive integer text bound is required")
    result = {"original_value": copy.deepcopy(value), "maximum": maximum,
              "empty_allowed": empty, "reasons": []}
    if not isinstance(value, str):
        result["reasons"].append("not-source-text")
    else:
        bounded = unicodedata.normalize("NFC", value).strip()
        result.update(
            original_length=len(value), contract_observed_text=bounded,
            contract_length=len(bounded), original_controls=controls(value),
            contract_controls=controls(bounded),
            normalization_is_observation_only=True,
        )
        if not bounded and not empty:
            result["reasons"].append("empty-required-text")
        if len(bounded) > maximum:
            result["reasons"].append("exceeds-code-point-bound")
        if result["contract_controls"]:
            result["reasons"].append("disallowed-control-or-format-character")
    result["within_text_contract"] = not result["reasons"]
    return result


def bind_source_node(record: dict, node: dict) -> dict:
    """Require exact source-object/version binding for every supplied reread."""
    for identity in ("source_id", "source_url", "source_updated_at"):
        if (not isinstance(node.get(identity), str) or not node[identity]
                or node[identity] != record.get(identity)):
            raise ValueError(f"Independent source {identity} does not match row")
    fields = node.get("source_fields")
    if not isinstance(fields, dict):
        raise ValueError("Independent source fields must be retained")
    # The corpus already retains raw preferred name tags. A different reread is
    # not a license to quietly switch the underlying source version or name.
    for key, value in record.get("source_names", {}).items():
        if fields.get(key) != value:
            raise ValueError(f"Independent source tag {key} does not match row")
    return fields


def source_alternatives(record: dict, node: dict) -> list[dict]:
    """Retain exact clean name tags from this source object, never select one."""
    fields = bind_source_node(record, node)
    grouped = {}
    for key, value in fields.items():
        if key not in NAME_KEYS:
            continue
        observed = review_text(value, 300)
        if not observed["within_text_contract"] or value == record.get("name"):
            continue
        # Keep the source's exact string, including whitespace/decomposition;
        # neither contract normalization nor Unicode control deletion is a fix.
        grouped.setdefault(value, []).append(key)
    return [
        {"source_fields": sorted(keys), "exact_source_value": value,
         "source_id": node["source_id"], "source_url": node["source_url"],
         "source_updated_at": node["source_updated_at"],
         "status": ALTERNATIVE_STATUS, "automatically_selected": False,
         "app_accepted": False}
        for value, keys in sorted(grouped.items())
    ]


def review_record(record: dict, source_node: dict | None = None) -> dict:
    fields = {
        field: review_text(record.get(field, default), maximum, empty=empty)
        for field, (maximum, empty, default) in TEXT_FIELDS.items()
    }
    failed = [field for field, result in fields.items()
              if not result["within_text_contract"]]
    result = {
        "status": RESEARCH_STATUS, "app_accepted": False,
        "original_record": copy.deepcopy(record), "field_reviews": fields,
        "failed_fields": failed,
        "classification": "TEXT_REVIEW_REQUIRED" if failed
                          else "WITHIN_TEXT_BOUNDS_ONLY",
        "source_name_alternatives": [],
    }
    if source_node is not None:
        bind_source_node(record, source_node)
        result["independent_source_node"] = copy.deepcopy(source_node)
        if "name" in failed:
            result["source_name_alternatives"] = source_alternatives(record, source_node)
    if "name" in failed:
        result["name_disposition"] = (
            "EXACT_SOURCE_ALTERNATIVES_REQUIRE_REVIEW"
            if result["source_name_alternatives"] else
            "RETAIN_ORIGINAL_EXCLUDE_UNTIL_REVIEW_NO_CLEAN_SOURCE_ALTERNATIVE"
        )
    return result


def reread_source_nodes(pbf: Path, wanted_ids: set[str]) -> dict:
    """Independent PBF read with C++ filters; callbacks visit only wanted nodes."""
    import osmium  # Optional research dependency; pure review/tests need stdlib.

    numeric = set()
    for source_id in wanted_ids:
        match = re.fullmatch(r"osm:node:([1-9][0-9]*)", source_id)
        if not match:
            raise ValueError("This independent rereader supports source nodes only")
        numeric.add(int(match[1]))
    found = {}

    class SourceRead(osmium.SimpleHandler):
        def node(self, node):
            source_id = f"osm:node:{node.id}"
            if source_id in found:
                raise ValueError("Duplicate selected source node")
            found[source_id] = {
                "source_id": source_id,
                "source_url": f"https://www.openstreetmap.org/node/{node.id}",
                "source_updated_at": node.timestamp.isoformat(),
                "source_fields": dict(node.tags),
            }

    if numeric:
        osmium.apply(str(pbf), osmium.filter.EntityFilter(osmium.osm.NODE),
                     osmium.filter.IdFilter(numeric), SourceRead())
    if set(found) != wanted_ids:
        raise ValueError("Independent source reread did not find every requested ID")
    return found


def write_report(output: Path, report: dict) -> None:
    # Exclusive create: retained evidence must not silently overwrite a result.
    with output.open("x", encoding="utf-8") as handle:
        json.dump(report, handle, ensure_ascii=True, indent=2, sort_keys=True)
        handle.write("\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus", type=Path, required=True)
    parser.add_argument("--source-pbf", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Output already exists; retain it and choose another path")
    rows = [json.loads(line) for line in args.corpus.read_text().splitlines() if line.strip()]
    blockers = [row for row in rows if review_record(row)["failed_fields"]]
    source_nodes = reread_source_nodes(args.source_pbf, {row["source_id"] for row in blockers})
    reports = [review_record(row, source_nodes[row["source_id"]]) for row in blockers]
    counts = collections.Counter(field for result in reports for field in result["failed_fields"])
    production = Path(__file__).resolve().parents[1] / "package_app_dataset.py"
    report = {
        "status": RESEARCH_STATUS, "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "purpose": "Source-text triage only; no rows rewritten, selected, packaged or app accepted",
        "script_sha256": sha256(Path(__file__)),
        "production_packager_sha256": sha256(production),
        "corpus_sha256": sha256(args.corpus), "source_pbf_sha256": sha256(args.source_pbf),
        "corpus_rows_examined": len(rows), "text_blocker_rows": len(reports),
        "field_failure_counts": dict(sorted(counts.items())),
        "independent_pbf_method": "pyosmium C++ EntityFilter(NODE) + IdFilter; exact source tags/timestamp reread",
        "independent_pbf_nodes_found": len(source_nodes),
        "app_accepted_rows": 0, "automatically_replaced_names": 0,
        "production_scope_or_country_guards_changed": False,
        "limits": ["This report does not evaluate production geographic admission or phone dial identities",
                   "Within text bounds is not app admission or name/phone ownership verification",
                   "Exact source alternatives remain review-only, with no language preference chosen",
                   "Historical source timestamps do not establish current business names or phones"],
        "rows": reports,
    }
    write_report(args.output, report)
    print(json.dumps({key: report[key] for key in
                     ("corpus_rows_examined", "text_blocker_rows", "field_failure_counts",
                      "independent_pbf_nodes_found", "app_accepted_rows")}, sort_keys=True))


if __name__ == "__main__":
    main()
