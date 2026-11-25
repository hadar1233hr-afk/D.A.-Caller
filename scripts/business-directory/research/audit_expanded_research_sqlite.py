#!/usr/bin/env python3
"""Independently audit five-cohort research SQLite fidelity and actual queries.

No staging insert/validator is called. Earlier independent text/phone projection
algorithms are shared, hash-bound audit references. Actual queries use the frozen
base query API. This is not source-PBF object authentication, Android validation,
phone ownership verification, production admission or APK approval.
"""
from __future__ import annotations

import argparse
import collections
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import time

import phonenumbers

import audit_research_sqlite as reference
import stage_research_sqlite as query_api

HERE = Path(__file__).resolve().parent
SCHEMA = "DA_CALLER_RESEARCH_SEARCH_V1_NOT_APP_ACCEPTED"
PROFILE = "EXPLICIT_FIVE_SOURCE_COHORTS_RESEARCH_ONLY"
APPLICATION_ID = 0x44525331
PRIOR_SHA = "b8d5f5209ea87969a1b42d3907e73f99466f869f7df2afcd8d7bb4eeb4dda274"
BASE_SHA = "5d4d264c8f88dccd9ed5dc1ae432bac767446105b8be9779f3dcf83714f82743"
FAMILIES = {
    "node": ("node", "node-geography-report.json", "node-geography-source-audit.json",
             "INDEPENDENT_ACTUAL_SOURCE_NODE_AUDIT_PASS_RESEARCH_ONLY"),
    "way": ("way", "way-geography-report.json", "way-geography-independent-audit.json",
            "PASS_RESEARCH_SOURCE_AND_COMPLETE_POLYGON_AUDIT_ONLY"),
    "relation": ("relation", "relation-geography-report.json", "relation-geography-independent-audit.json",
                 "PASS_RESEARCH_SOURCE_AND_HOLE_AWARE_RELATION_AUDIT_ONLY"),
    "extra_node": ("node", "extra-category-node-report.json", "extra-category-node-independent-audit.json",
                   "PASS_RESEARCH_EXTRA_CATEGORY_NODE_SOURCE_AND_POINT_AUDIT_ONLY"),
    "extra_way": ("way", "extra-category-way-report.json", "extra-category-way-independent-audit.json",
                  "PASS_RESEARCH_SOURCE_AND_COMPLETE_POLYGON_AUDIT_ONLY"),
}


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def code_paths(evidence: Path) -> dict[str, Path]:
    names = ("stage_expanded_research_sqlite.py", "stage_research_sqlite.py",
        "phone_identity_review.py", "source_text_review.py", "extended_business_category.py",
        "research_extra_category_nodes.py", "research_node_geography.py", "research_way_geography.py",
        "research_relation_geography.py", "audit_extra_category_nodes.py", "audit_way_geography.py",
        "audit_relation_geography.py")
    result = {name: HERE / name for name in names}
    result.update({name: HERE.parent / name for name in (
        "package_app_dataset.py", "extract_osm_businesses.py", "requirements-lock.txt")})
    result["node-geography-executed-source-audit.py"] = evidence / "node-geography-executed-source-audit.py"
    return result


def require_zero_aliases(audit: dict):
    present = [audit[key] for key in ("app_records_added", "app_rows_added", "new_app_bundled_rows",
                                     "new_app_bundled_records") if key in audit]
    require(bool(present) and all(type(value) is int and value == 0 for value in present),
            "Referenced source audit has missing/contradictory app counters")


def audit(directory: Path, evidence: Path, pbf: Path, prior_stage: Path) -> dict:
    started = time.monotonic()
    database_path, manifest_path = directory / "research-search.sqlite", directory / "manifest.json"
    manifest = json.loads(manifest_path.read_text())
    before_sha = digest(database_path)
    require(manifest["database_sha256"] == before_sha
            and manifest["database_bytes"] == database_path.stat().st_size,
            "Expanded retained database size/hash mismatch")
    require(manifest["schema"] == SCHEMA and manifest["builder_profile"] == PROFILE
            and manifest["research_only"] is True and manifest["app_accepted"] is False,
            "Explicit research-only schema/profile required")
    bound_code = code_paths(evidence)
    actual_code = {name: digest(path) for name, path in bound_code.items()}
    require(manifest["code_sha256"] == actual_code
            and manifest["staging_script_sha256"] == actual_code["stage_expanded_research_sqlite.py"],
            "Executed code/reference/lock snapshot differs")
    require(manifest["phonenumbers_version"] == phonenumbers.__version__
            and manifest["sqlite_version"] == sqlite3.sqlite_version,
            "Observed runtime differs from retained manifest")
    require(digest(pbf) == manifest["source_pbf_sha256"]
            and pbf.stat().st_size == manifest["source_pbf_bytes"],
            "Retained original PBF checksum/size differs")
    prior_path = prior_stage / "research-search.sqlite"
    prior_manifest_path = prior_stage / "manifest.json"
    prior_manifest = json.loads(prior_manifest_path.read_text())
    prior_before = digest(prior_path)
    require(prior_before == PRIOR_SHA == prior_manifest["database_sha256"],
            "Original2696-row research database changed")
    require(prior_manifest["staging_script_sha256"] == actual_code["stage_research_sqlite.py"] == BASE_SHA,
            "Original base staging helper changed")
    normalizer = reference.load("expanded_audit_phone_contract", bound_code["package_app_dataset.py"]).normalized_phone
    expected, corpora, source_families = {}, {}, {}
    require(len(manifest["cohorts"]) == len(FAMILIES)
            and {item["kind"] for item in manifest["cohorts"]} == set(FAMILIES),
            "Exactly five distinct explicit source families required")
    source_snapshots = {pbf: digest(pbf), manifest_path: digest(manifest_path), prior_path: prior_before,
                        prior_manifest_path: digest(prior_manifest_path)}
    for cohort in manifest["cohorts"]:
        kind = cohort["kind"]
        require(kind not in corpora, "Repeated source family")
        physical, report_name, audit_name, status = FAMILIES[kind]
        corpus = Path(cohort["corpus_path"])
        report_path, audit_path = evidence / report_name, evidence / audit_name
        blob = corpus.read_bytes()
        corpus_sha = hashlib.sha256(blob).hexdigest()
        require(corpus_sha == cohort["corpus_sha256"]
                and digest(report_path) == cohort["report_sha256"]
                and digest(audit_path) == cohort["audit_sha256"], "Cohort/reference bytes changed")
        report, source_audit = json.loads(report_path.read_text()), json.loads(audit_path.read_text())
        require(source_audit["status"] == cohort["audit_status"] == status
                and report["source_pbf_sha256"] == manifest["source_pbf_sha256"]
                and report["source_pbf_bytes"] == manifest["source_pbf_bytes"]
                and report["ndjson_sha256"] == corpus_sha
                and report["scope"] == cohort["scope"], "Cohort source/audit/scope binding mismatch")
        audit_corpus_sha = source_audit.get("ndjson_sha256") if kind == "node" else source_audit.get("research_ndjson_sha256")
        require(audit_corpus_sha == corpus_sha, "Source audit refers to another corpus")
        require_zero_aliases(source_audit)
        if kind != "node":
            require(source_audit["research_report_sha256"] == digest(report_path)
                    and source_audit["source_pbf_sha256"] == manifest["source_pbf_sha256"]
                    and source_audit["source_pbf_bytes"] == manifest["source_pbf_bytes"],
                    "Independent source audit/report/PBF binding mismatch")
        if kind.startswith("extra_"):
            require(cohort["physical_source_type"] == physical, "Extra family changed physical source type")
            policy_sha = actual_code["extended_business_category.py"]
            if kind == "extra_node":
                require(report["policy_module_sha256"] == source_audit["reviewed_policy_sha256"] == policy_sha
                        and report["research_script_sha256"] == source_audit["reviewed_producer_sha256"]
                            == actual_code["research_extra_category_nodes.py"], "Extra NODE policy/producer proof mismatch")
            else:
                require(report["extractor_sha256"] == source_audit["reference_extractor_sha256"] == policy_sha,
                        "Extra WAY must retain its reviewed additional-category policy")
        lines = blob.splitlines(keepends=True)
        require(len(lines) == cohort["rows"], "Source cohort line count mismatch")
        source_ids = set()
        for ordinal, original in enumerate(lines):
            row = json.loads(original)
            require(re.fullmatch(r"osm:" + physical + r":[1-9][0-9]*", row["source_id"])
                    and row["license_id"] == "ODbL-1.0" and row["geographic_scope"] == cohort["scope"]
                    and row["status"] == "RESEARCH_ONLY_NOT_APP_ACCEPTED"
                    and row["verification"] == "source-listed-not-phone-confirmed", "Original source identity/scope changed")
            source_id = row["source_id"]
            require(source_id not in source_families or source_families[source_id] == kind,
                    "Source object duplicated across families")
            source_families[source_id] = kind
            source_ids.add(source_id)
            key = hashlib.sha256(json.dumps([source_id, row["phone"], row["extension"]],
                ensure_ascii=True, separators=(",", ":")).encode()).hexdigest()
            require(key not in expected, "Source/base/extension identity merged or repeated")
            expected[key] = kind, ordinal, original, row
        require(len(source_ids) == cohort["source_objects"], "Source object count mismatch")
        corpora[kind] = corpus
        source_snapshots.update({corpus: corpus_sha, report_path: digest(report_path), audit_path: digest(audit_path)})

    connection = sqlite3.connect(database_path.resolve().as_uri() + "?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    counts, actual_counts, queries = collections.Counter(), collections.Counter(), {}
    try:
        require(connection.execute("PRAGMA application_id").fetchone()[0] == APPLICATION_ID,
                "Research application identity changed")
        require(connection.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
                and not connection.execute("PRAGMA foreign_key_check").fetchall(), "SQLite integrity or foreign keys failed")
        names = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        require("businesses" not in names and "metadata" not in names, "Research must not masquerade as app schema")
        metadata = {key: json.loads(value) for key, value in connection.execute("SELECT key,value_json FROM research_metadata")}
        require(metadata == {key: value for key, value in manifest.items()
                             if key not in {"database_sha256", "database_bytes"}}, "Manifest/database metadata differ")
        raw = {row["record_key"]: dict(row) for row in connection.execute("SELECT * FROM raw_records")}
        entries = {row["record_key"]: dict(row) for row in connection.execute("SELECT * FROM research_entries")}
        require(set(raw) == set(entries) == set(expected), "Source records lost, merged or invented")
        indexed = {row["docid"]: dict(row) for row in connection.execute("SELECT docid,* FROM research_search")}
        expected_fts = set()
        for key, (kind, ordinal, original, source) in expected.items():
            retained, entry = raw[key], entries[key]
            require(retained["corpus_kind"] == kind and retained["ordinal"] == ordinal
                    and retained["original_line"] == original
                    and retained["line_sha256"] == hashlib.sha256(original).hexdigest(), "Exact original raw line changed")
            counts["exact_raw_line_and_sha"] += 1
            for field, column in (("source_id", "source_id"), ("source_url", "source_url"),
                ("source_updated_at", "source_updated_at"), ("name", "name"), ("phone", "source_phone"),
                ("e164", "e164"), ("extension", "extension"), ("city", "city"), ("address", "address"),
                ("category", "category"), ("country_tag", "country_tag"), ("geographic_scope", "original_scope")):
                require(entry[column] == source.get(field), "Projected original source field changed: " + field)
            require(json.loads(entry["country_fields_json"]) == source["source_country_fields"]
                    and entry["app_accepted"] == 0, "Source country or research-only state changed")
            counts["exact_source_projection_and_scope"] += 1
            counts["null_country_fields_preserved"] += source["country_tag"] is None
            withheld = reference.text_withheld(source)
            phone_status, national = reference.phone_projection(source, normalizer)
            default = int(not withheld and phone_status == "IDENTITY_UNCHANGED")
            require(entry["text_review_status"] == ("SOURCE_TEXT_WITHHELD" if withheld else "SOURCE_TEXT_WITHIN_BOUNDS")
                    and entry["phone_review_status"] == phone_status and entry["national_identity_proposal"] == national
                    and entry["default_searchable"] == default
                    and entry["sort_name"] == reference.indexed_text(source["name"]), "Source review/proposal projection changed")
            counts["independent_text_phone_projection"] += 1
            actual_counts["raw_rows"] += 1
            actual_counts[phone_status] += 1
            actual_counts["SOURCE_TEXT_WITHHELD" if withheld else "SOURCE_TEXT_WITHIN_BOUNDS"] += 1
            actual_counts["default_searchable_rows"] += default
            rowid = entry["search_rowid"]
            if not withheld and national is not None:
                expected_fts.add(rowid)
                require(rowid in indexed, "Searchable row missing from FTS")
                item = indexed[rowid]
                require(all(item[field] == reference.indexed_text(source[field]) for field in ("name", "city", "address", "category")),
                        "Exact FTS source-text projection changed")
                variants = " ".join(value for value in (source["phone"], source.get("e164"), national) if value)
                require(item["phone_variants"] == variants, "FTS phone identity changed")
                counts["exact_fts_binding"] += 1
            else:
                require(rowid not in indexed, "Withheld source unexpectedly indexed")
        require(set(indexed) == expected_fts and dict(actual_counts) == manifest["counts"],
                "Phantom FTS records or measured review counts differ")
        for kind, corpus in corpora.items():
            ordered = sorted((value[1], key) for key, value in expected.items() if value[0] == kind)
            require(b"".join(raw[key]["original_line"] for _, key in ordered) == corpus.read_bytes(),
                    "Retained raw BLOBs do not reconstruct cohort bytes")
        counts["exact_reconstructed_source_cohorts"] = len(corpora)

        extras = [(key, entry) for key, entry in entries.items() if expected[key][0].startswith("extra_")]
        extra_phones = []
        for key, entry in extras:
            require(entry["default_searchable"] == 1, "Actual additional source unexpectedly needs withheld review")
            found = query_api.query(database_path, entry["source_phone"], limit=100)
            require(any(row["record_key"] == key for row in found), "Actual extra-source phone query omitted known source")
            extra_phones.append({"source_id": entry["source_id"], "query": entry["source_phone"],
                                 "returned": len(found), "known_source_found": True})
        queries["all_extra_source_phone_queries"] = extra_phones
        for label, entry in (("hebrew_name", next(row for row in entries.values() if row["default_searchable"]
                         and re.search(r"[א-ת]", row["name"]))),):
            found = query_api.query(database_path, entry["name"], limit=100)
            require(any(row["record_key"] == entry["record_key"] for row in found), "Known Hebrew query omitted source")
            queries[label] = {"query": entry["name"], "returned": len(found), "known_source_found": True}
        for value, limit in (("shop", 37), ("fitness", 5)):
            sql_expected = {row[0] for row in connection.execute(
                "SELECT e.record_key FROM research_search f JOIN research_entries e ON e.search_rowid=f.docid "
                "WHERE research_search MATCH ? AND e.default_searchable=1", ('"' + value + '*"',))}
            pages, gathered, cursor = 0, [], None
            while True:
                page = query_api.query(database_path, value, limit=limit, after=cursor)
                if not page:
                    break
                require(not set(gathered) & {row["record_key"] for row in page}, "Query pagination repeats a source")
                gathered.extend(row["record_key"] for row in page)
                cursor = page[-1]["sort_name"], page[-1]["record_key"]
                pages += 1
                require(pages <= len(entries), "Pagination failed to terminate")
            require(set(gathered) == sql_expected, "Query pagination omits matching source")
            extra_found = sum(expected[key][0].startswith("extra_") for key in gathered)
            if value == "fitness":
                require({key for key, entry in extras if entry["category"] == "leisure:fitness_centre"}
                        <= set(gathered), "Fitness pagination omitted additional fitness source")
            queries[value + "_keyset_pagination"] = {"pages": pages, "rows": len(gathered),
                "extra_rows_found": extra_found, "repeated_or_omitted": 0}
        proposals = [row for row in entries.values() if row["phone_review_status"] == "RESEARCH_REFORMAT_PROPOSAL"
                     and row["text_review_status"] == "SOURCE_TEXT_WITHIN_BOUNDS"]
        require(bool(proposals), "Expected actual UAN research proposal cohort absent")
        target = proposals[0]
        value = target["national_identity_proposal"]
        require(all(row["phone_review_status"] != "RESEARCH_REFORMAT_PROPOSAL" for row in query_api.query(database_path, value, limit=100)),
                "Default query exposed a phone-review proposal")
        require(any(row["record_key"] == target["record_key"] for row in query_api.query(database_path, value, limit=100,
                    include_phone_proposals=True)), "Explicit research opt-in omitted proposal")
        queries["uan_optin_default"] = "PASS"
        for value in ('"()---', "unknown_source_zzzzzzzzzz", "' OR 1=1 audit_nonexistent_zqx98765 --"):
            require(query_api.query(database_path, value) == [], "Literal query/operators unexpectedly matched")
        for bad in ("false", 1, None):
            try:
                query_api.query(database_path, "1599", include_phone_proposals=bad)
            except ValueError:
                pass
            else:
                raise ValueError("Non-boolean opt-in accepted")
        queries["literal_unknown_and_boolean_optin"] = "PASS"
        plan = [tuple(row) for row in connection.execute(
            "EXPLAIN QUERY PLAN SELECT e.record_key FROM research_search f JOIN research_entries e ON f.docid=e.search_rowid "
            "WHERE research_search MATCH ? AND e.default_searchable=1 ORDER BY e.sort_name,e.record_key LIMIT 30", ('"fitness*"',))]
        with sqlite3.connect(":memory:") as clone:
            connection.backup(clone)
            try:
                clone.execute("UPDATE research_entries SET app_accepted=1")
            except sqlite3.IntegrityError:
                pass
            else:
                raise ValueError("Research-only CHECK missing")
    finally:
        connection.close()
    require(digest(database_path) == before_sha and digest(prior_path) == prior_before == PRIOR_SHA,
            "Queries changed expanded or prior retained database")
    require(all(digest(path) == sha for path, sha in source_snapshots.items())
            and {name: digest(path) for name, path in bound_code.items()} == actual_code,
            "Code/source/audit inputs changed during independent query audit")
    return dict(status="PASS_EXPANDED_RESEARCH_SQLITE_FIDELITY_AND_QUERY_AUDIT_ONLY",
        observed_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(), elapsed_seconds=round(time.monotonic()-started, 3),
        database_sha256=before_sha, database_bytes=database_path.stat().st_size,
        manifest_sha256=digest(manifest_path), audit_script_sha256=digest(Path(__file__)),
        reviewed_expanded_builder_sha256=actual_code["stage_expanded_research_sqlite.py"],
        unchanged_base_builder_sha256=actual_code["stage_research_sqlite.py"],
        unchanged_prior_database_sha256=prior_before, shared_projection_audit_sha256=digest(HERE / "audit_research_sqlite.py"),
        reference_production_normalizer_sha256=actual_code["package_app_dataset.py"],
        retained_pbf_sha256=manifest["source_pbf_sha256"], original_pbf_objects_reread=False,
        phonenumbers_version=phonenumbers.__version__, sqlite_version=sqlite3.sqlite_version,
        raw_source_rows=len(expected), source_objects=len(source_families),
        distinct_source_phones=len({value[3]["phone"] for value in expected.values()}),
        measured_review_counts=dict(sorted(actual_counts.items())), checks_passed=dict(sorted(counts.items())),
        cohorts=manifest["cohorts"], actual_query_checks=queries, actual_query_plan=plan,
        expanded_database_unchanged=True, prior_database_unchanged=True,
        source_rows_merged=0, app_records_added=0, production_admission=False, publication_approval=False,
        limits=["Original source audits are hash-bound reference evidence; this audit checks retained PBF bytes but does not reread its objects.",
                "Text/phone projection algorithms reuse the earlier independent audit, and numbering metadata/normalizer are shared reference contracts.",
                "Actual queries exercise the unchanged base query API; no staging insert/validator or expanded producer is called.",
                "Desktop SQLite/FTS uses temporary B-tree sorting; no Android UI/device, million-record performance or phone ownership claim.",
                "Explicit research cohorts retain original null countries/scopes and are not app/production/release admission."])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for field in ("directory", "evidence", "pbf", "prior_stage", "output"):
        parser.add_argument(field, type=Path)
    args = parser.parse_args()
    require(not args.output.exists(), "Audit output exists; retain it and choose a new path")
    result = audit(args.directory, args.evidence, args.pbf, args.prior_stage)
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, ensure_ascii=True, indent=2)
        stream.write("\n")
    print(json.dumps({key: result[key] for key in ("status", "raw_source_rows", "source_objects",
        "distinct_source_phones", "database_sha256", "app_records_added")}))


if __name__ == "__main__":
    main()
