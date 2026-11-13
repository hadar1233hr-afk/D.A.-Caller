#!/usr/bin/env python3
"""Explicit five-cohort research staging; base tool/data and app stay unchanged."""
from __future__ import annotations

import argparse
import collections
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import tempfile

import stage_research_sqlite as base

HERE = Path(__file__).resolve().parent
PROFILE = "EXPLICIT_FIVE_SOURCE_COHORTS_RESEARCH_ONLY"
EXTRA = {
    "extra_node": ("node", "extra-category-node-report.json", "extra-category-node-independent-audit.json"),
    "extra_way": ("way", "extra-category-way-report.json", "extra-category-way-independent-audit.json"),
}
NODE_CHECKS = (
    "exact_original_coordinates_rows", "exact_original_id_url_timestamp_version_rows",
    "exact_original_raw_tags_rows", "exact_source_country_fields_without_inference_rows",
    "exact_source_name_city_address_rows", "exact_source_original_canonical_phone_and_extension_rows",
    "independent_source_point_inclusion_margin_exclusion_rows",
    "independent_whitelist_access_residential_lifecycle_and_shared_policy_rows",
    "research_status_license_provenance_rows",
)
WAY_CHECKS = (
    "exact_full_raw_source_tag_rows", "exact_ordered_source_ref_and_coordinate_rows",
    "exact_source_country_fields_without_inference_rows",
    "source_raw_and_reparsed_canonical_phone_extension_rows",
    "independent_complete_polygon_valid_area_containment_margin_exclusion_rows",
)


def count_is(value, expected):
    return type(value) is int and value == expected


def validate_extra(kind: str, corpus: Path, evidence: Path, source_sha: str, source_bytes: int):
    if kind not in EXTRA:
        raise ValueError("Explicit extra-source cohort family required")
    physical, report_name, audit_name = EXTRA[kind]
    report_path, audit_path = evidence / report_name, evidence / audit_name
    report, audit = json.loads(report_path.read_text()), json.loads(audit_path.read_text())
    blob = corpus.read_bytes()
    corpus_sha = hashlib.sha256(blob).hexdigest()
    lines = blob.splitlines(keepends=True)
    rows = [json.loads(line) for line in lines]
    if not rows or any(not isinstance(row, dict) for row in rows):
        raise ValueError("Nonempty original extra-source rows required")
    sources = len({row.get("source_id") for row in rows})
    policy_sha = base.digest(HERE / "extended_business_category.py")
    if (report.get("source_pbf_sha256") != source_sha
            or audit.get("source_pbf_sha256") != source_sha
            or not count_is(report.get("source_pbf_bytes"), source_bytes)
            or not count_is(audit.get("source_pbf_bytes"), source_bytes)
            or report.get("ndjson_sha256") != corpus_sha
            or audit.get("research_ndjson_sha256") != corpus_sha
            or audit.get("research_report_sha256") != base.digest(report_path)
            or not count_is(report.get("raw_phone_rows"), len(rows))):
        raise ValueError("Actual extra-source/PBF/report/audit bytes do not agree")
    if kind == "extra_node":
        valid = (audit.get("status") == "PASS_RESEARCH_EXTRA_CATEGORY_NODE_SOURCE_AND_POINT_AUDIT_ONLY"
                 and count_is(report.get("source_nodes"), sources)
                 and count_is(audit.get("source_node_rows"), len(rows))
                 and count_is(audit.get("unique_original_source_nodes_found"), sources)
                 and report.get("policy_module_sha256") == audit.get("reviewed_policy_sha256") == policy_sha
                 and report.get("research_script_sha256") == audit.get("reviewed_producer_sha256")
                     == base.digest(HERE / "research_extra_category_nodes.py")
                 and report.get("unchanged_core_extractor_sha256") == audit.get("reference_extractor_sha256")
                     == base.digest(HERE.parent / "extract_osm_businesses.py")
                 and audit.get("audit_script_sha256") == base.digest(HERE / "audit_extra_category_nodes.py")
                 and all(count_is(audit.get("checks_passed", {}).get(key), len(rows)) for key in NODE_CHECKS))
    else:
        valid = (audit.get("status") == "PASS_RESEARCH_SOURCE_AND_COMPLETE_POLYGON_AUDIT_ONLY"
                 and count_is(report.get("source_ways"), sources)
                 and count_is(audit.get("source_way_rows"), len(rows))
                 and count_is(audit.get("unique_original_source_ways_found"), sources)
                 and report.get("extractor_sha256") == audit.get("reference_extractor_sha256") == policy_sha
                 and report.get("research_script_sha256") == audit.get("reviewed_way_helper_sha256")
                     == base.digest(HERE / "research_way_geography.py")
                 and audit.get("audit_script_sha256") == base.digest(HERE / "audit_way_geography.py")
                 and all(count_is(audit.get("checks_passed", {}).get(key), len(rows)) for key in WAY_CHECKS))
    app_counters = [audit[key] for key in ("app_records_added", "app_rows_added",
        "new_app_bundled_rows", "new_app_bundled_records") if key in audit]
    if (not valid or not app_counters or not all(count_is(value, 0) for value in app_counters)
            or not count_is(audit.get("source_mutations"), 0)
            or not count_is(audit.get("automatic_merges"), 0)):
        raise ValueError("Successful distinct extra-category source/spatial/policy audit required")
    for row in rows:
        if (not re.fullmatch(r"osm:" + physical + r":[1-9][0-9]*", row.get("source_id", ""))
                or row.get("license_id") != "ODbL-1.0"
                or row.get("verification") != "source-listed-not-phone-confirmed"
                or row.get("status") != "RESEARCH_ONLY_NOT_APP_ACCEPTED"
                or row.get("geographic_scope") != report.get("scope")):
            raise ValueError("Original extra identity/license/research scope required")
    return list(zip(rows, lines)), dict(kind=kind, physical_source_type=physical,
        corpus_path=str(corpus), rows=len(rows), source_objects=sources,
        corpus_sha256=corpus_sha, report_sha256=base.digest(report_path),
        audit_sha256=base.digest(audit_path), audit_status=audit["status"], scope=report["scope"])


def unique_source_families(cohorts):
    seen = {}
    for kind, rows in cohorts.items():
        for row, _ in rows:
            source = row["source_id"]
            if source in seen and seen[source] != kind:
                raise ValueError("Source object appears across distinct cohort families; no merge")
            seen[source] = kind


def build(pbf: Path, corpus_paths: dict, evidence: Path, output: Path):
    if output.exists():
        raise ValueError("Expanded output exists; retain original research/app data")
    kinds = ("node", "way", "relation", "extra_node", "extra_way")
    if set(corpus_paths) != set(kinds):
        raise ValueError("Exactly five explicitly audited source cohorts required")
    code_names = (Path(__file__).name, "stage_research_sqlite.py", "phone_identity_review.py",
        "source_text_review.py", "extended_business_category.py", "research_extra_category_nodes.py",
        "research_node_geography.py", "research_way_geography.py", "research_relation_geography.py",
        "audit_extra_category_nodes.py", "audit_way_geography.py", "audit_relation_geography.py")
    code_paths = [HERE / name for name in code_names] + [
        HERE.parent / "package_app_dataset.py", HERE.parent / "extract_osm_businesses.py",
        HERE.parent / "requirements-lock.txt", evidence / "node-geography-executed-source-audit.py"]
    code_sha = {p: base.digest(p) for p in code_paths}
    evidence_names = ("node-geography-report.json", "node-geography-source-audit.json",
        "way-geography-report.json", "way-geography-independent-audit.json",
        "relation-geography-report.json", "relation-geography-independent-audit.json",
        *(name for spec in EXTRA.values() for name in spec[1:]))
    snapshots = {p: base.digest(p) for p in [pbf, *corpus_paths.values(),
                                          *(evidence / name for name in evidence_names)]}
    snapshots.update(code_sha)
    source_sha, source_bytes = snapshots[pbf], pbf.stat().st_size
    cohorts, bindings = {}, []
    for kind in kinds:
        validator = validate_extra if kind in EXTRA else base.validate_cohort
        cohorts[kind], binding = validator(kind, corpus_paths[kind], evidence, source_sha, source_bytes)
        bindings.append(binding)
    unique_source_families(cohorts)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="expanded-research-search-", dir=output.parent) as temporary:
        temp = Path(temporary)
        db_path = temp / "research-search.sqlite"
        db = sqlite3.connect(db_path)
        try:
            base.create_schema(db)
            counts = collections.Counter()
            for kind, rows in cohorts.items():
                for ordinal, (row, line) in enumerate(rows):
                    decision = base.insert_original(db, kind, ordinal, row, line)
                    counts["raw_rows"] += 1
                    counts[decision["text_status"]] += 1
                    counts[decision["phone_status"]] += 1
                    counts["default_searchable_rows"] += decision["default_searchable"]
            metadata = dict(schema=base.SCHEMA, builder_profile=PROFILE, research_only=True,
                app_accepted=False, source_pbf_sha256=source_sha, source_pbf_bytes=source_bytes,
                cohorts=bindings, counts=dict(sorted(counts.items())),
                observed_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(),
                staging_script_sha256=code_sha[Path(__file__)],
                code_sha256={p.name: sha for p, sha in code_sha.items()},
                phonenumbers_version=base.phonenumbers.__version__, sqlite_version=sqlite3.sqlite_version,
                limits=["Explicit additional research cohorts retain their own audit families and source scopes.",
                        "All raw lines/country fields remain original; no production admission or phone/name repair.",
                        "Research schema/searchability is not app integration, ownership verification or national coverage.",
                        "Base tool, prior2696-row DB, app, signing/CI and production guards remain unchanged."])
            db.executemany("INSERT INTO research_metadata VALUES (?,?)", [
                (key, json.dumps(value, ensure_ascii=True, sort_keys=True)) for key, value in metadata.items()])
            db.commit()
            if db.execute("PRAGMA integrity_check").fetchone()[0] != "ok" or db.execute("PRAGMA foreign_key_check").fetchall():
                raise ValueError("Expanded research database integrity failed")
            db.execute("VACUUM")
        finally:
            db.close()
        if any(base.digest(path) != sha for path, sha in snapshots.items()):
            raise ValueError("Source/code/audit input changed during expanded build")
        metadata.update(database_sha256=base.digest(db_path), database_bytes=db_path.stat().st_size)
        (temp / "manifest.json").write_text(json.dumps(metadata, ensure_ascii=True, indent=2) + "\n")
        (temp / "LICENSE-NOTICE.txt").write_text(
            "© OpenStreetMap contributors\nODbL1.0 https://opendatacommons.org/licenses/odbl/1-0/\n"
            "Research source data only; no current ownership, country-tag inference or production admission.\n")
        temp.rename(output)
    return metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pbf", type=Path)
    parser.add_argument("evidence", type=Path)
    parser.add_argument("output", type=Path)
    for kind in ("node", "way", "relation", "extra_node", "extra_way"):
        parser.add_argument("--" + kind.replace("_", "-"), dest=kind, type=Path, required=True)
    args = parser.parse_args()
    paths = {kind: getattr(args, kind) for kind in ("node", "way", "relation", "extra_node", "extra_way")}
    result = build(args.pbf, paths, args.evidence, args.output)
    print(json.dumps({key: result[key] for key in ("builder_profile", "counts", "database_sha256")}))


if __name__ == "__main__":
    main()
