#!/usr/bin/env python3
"""Independently compare a research SQLite stage with exact source cohorts.

This audits staging fidelity/search behavior, not original-source authenticity,
phone ownership, country admission, Android performance or APK publication.
"""
from __future__ import annotations

import argparse
import collections
import datetime as dt
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sqlite3
import tempfile
import time
import unicodedata

import phonenumbers

HERE = Path(__file__).resolve().parent
APPLICATION_ID = 0x44525331
SCHEMA = "DA_CALLER_RESEARCH_SEARCH_V1_NOT_APP_ACCEPTED"
TEXT_BOUNDS = {"name": (300, False), "phone": (80, False),
               "source_id": (100, False), "source_updated_at": (80, False),
               "city": (300, True), "category": (300, True), "address": (1200, True)}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path: Path) -> str:
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def load(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def indexed_text(text: str) -> str:
    return "".join(char for char in unicodedata.normalize("NFKD", text).casefold()
                   if not unicodedata.category(char).startswith("M"))


def text_withheld(row: dict) -> bool:
    for name, (bound, empty) in TEXT_BOUNDS.items():
        value = row.get(name, "" if empty else None)
        if not isinstance(value, str):
            return True
        observed = unicodedata.normalize("NFC", value).strip()
        if ((not observed and not empty) or len(observed) > bound
                or any(unicodedata.category(char) in {"Cc", "Cf"} for char in observed)):
            return True
    return False


def phone_projection(row: dict, normalizer) -> tuple[str, str | None]:
    """Independent projection; same library/normalizer are reference contracts."""
    phone, extension = row.get("phone"), row.get("extension", "")
    if (not isinstance(phone, str) or not 0 < len(phone) <= 80
            or re.fullmatch(r"[+0-9() .\-]+", phone, re.ASCII) is None
            or ("+" in phone and (not phone.strip().startswith("+") or phone.count("+") != 1))
            or not isinstance(extension, str) or len(extension) > 20
            or (extension and re.fullmatch(r"[0-9]+", extension, re.ASCII) is None)):
        return "REJECTED_FOR_PHONE_PROPOSAL", None
    try:
        parsed = phonenumbers.parse(phone, "IL")
        if (parsed.extension or parsed.country_code != 972
                or phonenumbers.region_code_for_number(parsed) != "IL"
                or not phonenumbers.is_valid_number(parsed)):
            return "REJECTED_FOR_PHONE_PROPOSAL", None
        canonical = phonenumbers.format_number(parsed, phonenumbers.PhoneNumberFormat.E164)
        if "e164" in row and row["e164"] != canonical:
            return "REJECTED_FOR_PHONE_PROPOSAL", None
        national = phonenumbers.format_number(parsed, phonenumbers.PhoneNumberFormat.NATIONAL)
        if re.fullmatch(r"[+0-9() .\-]+", national, re.ASCII) is None:
            return "REJECTED_FOR_PHONE_PROPOSAL", None
        dial = re.sub(r"[() .\-]", "", national)
        if re.fullmatch(r"[0-9]+", dial, re.ASCII) is None or normalizer(dial) != dial:
            return "REJECTED_FOR_PHONE_PROPOSAL", None
        reparsed = phonenumbers.parse(dial, "IL")
        if (not phonenumbers.is_valid_number(reparsed)
                or phonenumbers.format_number(reparsed, phonenumbers.PhoneNumberFormat.E164) != canonical):
            return "REJECTED_FOR_PHONE_PROPOSAL", None
    except phonenumbers.NumberParseException:
        return "REJECTED_FOR_PHONE_PROPOSAL", None
    status = "IDENTITY_UNCHANGED" if normalizer(phone) == dial else "RESEARCH_REFORMAT_PROPOSAL"
    return status, dial


def audit(directory: Path, evidence: Path) -> dict:
    started = time.monotonic()
    manifest_path, database_path = directory / "manifest.json", directory / "research-search.sqlite"
    manifest = json.loads(manifest_path.read_text())
    before_sha = digest(database_path)
    require(before_sha == manifest["database_sha256"]
            and database_path.stat().st_size == manifest["database_bytes"], "Retained SQLite size/hash mismatch")
    require(manifest["schema"] == SCHEMA and manifest["research_only"] is True
            and manifest["app_accepted"] is False, "Research-only metadata required")
    stage_path = HERE / "stage_research_sqlite.py"
    require(manifest["staging_script_sha256"] == digest(stage_path), "Current staging helper differs from executed source")
    require(manifest["phone_review_sha256"] == digest(HERE / "phone_identity_review.py")
            and manifest["text_review_sha256"] == digest(HERE / "source_text_review.py"), "Referenced review helpers changed")
    # The production normalizer is a read-only reference contract, not modified.
    contract_path = HERE.parent / "package_app_dataset.py"
    require(manifest["production_phone_contract_sha256"] == digest(contract_path)
            and manifest["requirements_lock_sha256"] == digest(HERE.parent / "requirements-lock.txt")
            and manifest["phonenumbers_version"] == phonenumbers.__version__
            and manifest["sqlite_version"] == sqlite3.sqlite_version,
            "Bound phone contract, locked requirements or actual runtime versions differ")
    normalizer = load("audit_existing_phone_contract", contract_path).normalized_phone
    stage = load("audit_research_query_api", stage_path)
    expected, corpus_files = {}, {}
    for cohort in manifest["cohorts"]:
        kind, corpus = cohort["kind"], Path(cohort["corpus_path"])
        require(kind in {"node", "way", "relation"} and kind not in corpus_files, "Unexpected or repeated source cohort")
        corpus_files[kind] = corpus
        data = corpus.read_bytes()
        require(hashlib.sha256(data).hexdigest() == cohort["corpus_sha256"], "Original source cohort changed")
        report_name = {"node": "node-geography-report.json", "way": "way-geography-report.json",
                       "relation": "relation-geography-report.json"}[kind]
        audit_name = {"node": "node-geography-source-audit.json", "way": "way-geography-independent-audit.json",
                      "relation": "relation-geography-independent-audit.json"}[kind]
        report_path, source_audit_path = evidence / report_name, evidence / audit_name
        require(digest(report_path) == cohort["report_sha256"]
                and digest(source_audit_path) == cohort["audit_sha256"], "Referenced cohort report/audit changed")
        report = json.loads(report_path.read_text())
        source_audit = json.loads(source_audit_path.read_text())
        expected_status = {"node": "INDEPENDENT_ACTUAL_SOURCE_NODE_AUDIT_PASS_RESEARCH_ONLY",
                           "way": "PASS_RESEARCH_SOURCE_AND_COMPLETE_POLYGON_AUDIT_ONLY",
                           "relation": "PASS_RESEARCH_SOURCE_AND_HOLE_AWARE_RELATION_AUDIT_ONLY"}[kind]
        require(source_audit["status"] == cohort["audit_status"] == expected_status
                and report["source_pbf_sha256"] == manifest["source_pbf_sha256"]
                and report["source_pbf_bytes"] == manifest["source_pbf_bytes"]
                and report["ndjson_sha256"] == cohort["corpus_sha256"]
                and report["scope"] == cohort["scope"], "Source-cohort reference binding failed")
        audit_corpus_hash = source_audit.get("ndjson_sha256") if kind == "node" else source_audit.get("research_ndjson_sha256")
        require(audit_corpus_hash == cohort["corpus_sha256"], "Source audit references another corpus")
        lines = data.splitlines(keepends=True)
        require(len(lines) == cohort["rows"], "Source cohort count mismatch")
        source_ids = set()
        for ordinal, original in enumerate(lines):
            row = json.loads(original)
            require(re.fullmatch("osm:" + kind + r":[1-9][0-9]*", row["source_id"])
                    and row["license_id"] == "ODbL-1.0"
                    and row["geographic_scope"] == cohort["scope"]
                    and row["status"] == "RESEARCH_ONLY_NOT_APP_ACCEPTED"
                    and row["verification"] == "source-listed-not-phone-confirmed",
                    "Original cohort source type/license/scope/verification changed")
            source_ids.add(row["source_id"])
            key = hashlib.sha256(json.dumps([row["source_id"], row["phone"], row["extension"]],
                                ensure_ascii=True, separators=(",", ":")).encode()).hexdigest()
            require(key not in expected, "Unexpected duplicate source identity")
            expected[key] = (kind, ordinal, original, row)
        require(len(source_ids) == cohort["source_objects"], "Source cohort object count changed")

    connection = sqlite3.connect(database_path.resolve().as_uri() + "?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    counts = collections.Counter()
    queries = {}
    try:
        require(connection.execute("PRAGMA application_id").fetchone()[0] == APPLICATION_ID,
                "Research SQLite application identity changed")
        require(connection.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
                and not connection.execute("PRAGMA foreign_key_check").fetchall(), "SQLite integrity failed")
        names = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        require("businesses" not in names and "metadata" not in names, "Research stage must not masquerade as app schema")
        metadata = {key: json.loads(value) for key, value in connection.execute("SELECT key,value_json FROM research_metadata")}
        require(metadata == {key: value for key, value in manifest.items()
                             if key not in {"database_sha256", "database_bytes"}}, "Manifest/SQLite metadata diverged")
        raw = {row["record_key"]: dict(row) for row in connection.execute("SELECT * FROM raw_records")}
        entries = {row["record_key"]: dict(row) for row in connection.execute("SELECT * FROM research_entries")}
        require(set(raw) == set(entries) == set(expected), "Source rows omitted, merged or invented")
        indexed = {row["docid"]: dict(row) for row in connection.execute("SELECT docid,* FROM research_search")}
        expected_indexed = set()
        for key, (kind, ordinal, original, source) in expected.items():
            retained, entry = raw[key], entries[key]
            require(retained["corpus_kind"] == kind and retained["ordinal"] == ordinal
                    and retained["original_line"] == original
                    and retained["line_sha256"] == hashlib.sha256(original).hexdigest(), "Raw original line/identity changed")
            counts["exact_raw_lines_and_sha"] += 1
            for field, column in (("source_id", "source_id"), ("source_url", "source_url"),
                                  ("source_updated_at", "source_updated_at"), ("name", "name"),
                                  ("phone", "source_phone"), ("e164", "e164"), ("extension", "extension"),
                                  ("city", "city"), ("address", "address"), ("category", "category"),
                                  ("country_tag", "country_tag"), ("geographic_scope", "original_scope")):
                require(entry[column] == source.get(field), "Original projected source field changed: " + field)
            require(json.loads(entry["country_fields_json"]) == source["source_country_fields"], "Country fields inferred or changed")
            require(entry["app_accepted"] == 0, "Research entry unexpectedly app accepted")
            counts["exact_original_display_phone_country_scope_fields"] += 1
            counts["null_country_fields_retained"] += source["country_tag"] is None
            withheld = text_withheld(source)
            status, national = phone_projection(source, normalizer)
            require(entry["text_review_status"] == ("SOURCE_TEXT_WITHHELD" if withheld else "SOURCE_TEXT_WITHIN_BOUNDS")
                    and entry["phone_review_status"] == status
                    and entry["national_identity_proposal"] == national
                    and entry["default_searchable"] == int(not withheld and status == "IDENTITY_UNCHANGED")
                    and entry["sort_name"] == indexed_text(source["name"]), "Review status/projection differed from original source")
            counts["independent_text_phone_review_projections"] += 1
            counts["source_text_withheld"] += withheld
            counts[status] += 1
            counts["default_searchable"] += entry["default_searchable"]
            rowid = entry["search_rowid"]
            if not withheld and national is not None:
                expected_indexed.add(rowid)
                require(rowid in indexed, "Searchable source missing from FTS")
                item = indexed[rowid]
                require(all(item[field] == indexed_text(source[field]) for field in ("name", "city", "address", "category")),
                        "FTS display-search projection differs from source")
                variants = " ".join(value for value in (source["phone"], source.get("e164"), national) if value)
                require(item["phone_variants"] == variants, "FTS phone projection changed identity")
                counts["exact_fts_source_bindings"] += 1
            else:
                require(rowid not in indexed, "Withheld/invalid source unexpectedly indexed")
        require(set(indexed) == expected_indexed, "Phantom/unbound FTS records")
        for kind, corpus in corpus_files.items():
            restored = b"".join(raw[key]["original_line"] for key, values in sorted(
                expected.items(), key=lambda pair: pair[1][1]) if values[0] == kind)
            require(restored == corpus.read_bytes(), "Ordered raw records do not reconstruct original corpus")

        # Exercise the actual query API on the retained SQLite, not a fake fixture.
        candidates = [entry for entry in entries.values() if entry["default_searchable"]]
        for label, entry in (("source_e164_phone", candidates[0]),
                             ("hebrew_name", next(item for item in candidates if re.search(r"[א-ת]", item["name"])))):
            value = entry["source_phone"] if label.endswith("phone") else entry["name"]
            found = stage.query(database_path, value, limit=100)
            require(any(item["record_key"] == entry["record_key"] for item in found), "Actual query omitted known source: " + label)
            queries[label] = {"query": value, "returned": len(found), "known_source_found": True}
        proposals = [entry for entry in entries.values() if entry["phone_review_status"] == "RESEARCH_REFORMAT_PROPOSAL"
                     and entry["text_review_status"] == "SOURCE_TEXT_WITHIN_BOUNDS"]
        if proposals:
            target = proposals[0]
            value = target["national_identity_proposal"]
            require(all(row["phone_review_status"] != "RESEARCH_REFORMAT_PROPOSAL" for row in stage.query(database_path, value, limit=100)),
                    "Default actual query exposed a review proposal")
            expanded = stage.query(database_path, value, limit=100, include_phone_proposals=True)
            require(any(row["record_key"] == target["record_key"] for row in expanded), "Explicit research proposal query omitted source")
            queries["uan_opt_in"] = {"source_phone": target["source_phone"], "national_query": value,
                                     "default_proposals_hidden": True, "explicit_proposal_found": True}
        expected_shop = {row[0] for row in connection.execute(
            "SELECT e.record_key FROM research_search f JOIN research_entries e ON f.docid=e.search_rowid "
            "WHERE research_search MATCH ? AND e.default_searchable=1", ('"shop*"',))}
        pages, gathered, cursor = 0, [], None
        while True:
            page = stage.query(database_path, "shop", limit=37, after=cursor)
            if not page:
                break
            require(not set(gathered) & {row["record_key"] for row in page}, "Actual query pagination repeated a source")
            gathered.extend(row["record_key"] for row in page)
            cursor = page[-1]["sort_name"], page[-1]["record_key"]
            pages += 1
            require(pages <= len(entries), "Actual pagination did not terminate")
        require(set(gathered) == expected_shop, "Actual pagination skipped matching source rows")
        queries["shop_keyset_pagination"] = {"pages": pages, "rows": len(gathered), "repeated_or_omitted": 0}
        for query in ('"()---', "unknown_source_zzzzzzzzzz", "' OR 1=1 audit_nonexistent_zqx98765 --"):
            require(stage.query(database_path, query) == [], "Literal unknown/operator query unexpectedly matched")
        for bad in ("false", 1, None):
            try:
                stage.query(database_path, "1599", include_phone_proposals=bad)
            except ValueError:
                pass
            else:
                raise ValueError("Non-boolean opt-in accepted")
        queries["literal_unknown_and_optin_bounds"] = "PASS"
        plan = [tuple(row) for row in connection.execute(
            "EXPLAIN QUERY PLAN SELECT e.record_key FROM research_search f JOIN research_entries e ON f.docid=e.search_rowid "
            "WHERE research_search MATCH ? AND e.default_searchable=1 ORDER BY e.sort_name,e.record_key LIMIT 30", ('"shop*"',))]
        # Verify CHECK using an in-memory copy, never mutate the retained file.
        with sqlite3.connect(":memory:") as clone:
            connection.backup(clone)
            try:
                clone.execute("UPDATE research_entries SET app_accepted=1")
            except sqlite3.IntegrityError:
                pass
            else:
                raise ValueError("Research-only CHECK constraint absent")
    finally:
        connection.close()
    require(digest(database_path) == before_sha, "Queries changed retained SQLite bytes")
    return {
        "status": "PASS_RESEARCH_SQLITE_FIDELITY_AND_QUERY_AUDIT_ONLY",
        "observed_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "elapsed_seconds": round(time.monotonic() - started, 3),
        "database_sha256": before_sha, "database_bytes": database_path.stat().st_size,
        "bound_source_pbf_sha256": manifest["source_pbf_sha256"],
        "manifest_sha256": digest(manifest_path), "audit_script_sha256": digest(Path(__file__)),
        "reviewed_staging_script_sha256": digest(stage_path),
        "reference_production_normalizer_sha256": digest(contract_path),
        "phonenumbers_version": phonenumbers.__version__, "sqlite_version": sqlite3.sqlite_version,
        "raw_source_rows": len(expected), "cohorts": manifest["cohorts"],
        "checks_passed": dict(sorted(counts.items())), "actual_query_checks": queries,
        "actual_query_plan": plan, "database_unchanged_after_queries": True,
        "source_rows_merged": 0, "app_records_added": 0,
        "limits": ["Source-cohort/audit artifacts are hash-bound reference evidence; PBF source authenticity was not reread by this SQLite audit.",
                   "Numbering-plan metadata and the unchanged production normalizer are shared reference contracts, not phone ownership verification.",
                   "Actual desktop SQLite queries do not verify Android UI, device performance or million-record scale.",
                   "Research searchability/proposals are not production admission, country-tag inference or APK publication approval."],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("evidence", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    require(not args.output.exists(), "Audit output exists; retain it and choose a new path")
    result = audit(args.directory, args.evidence)
    with args.output.open("x", encoding="utf-8") as handle:
        json.dump(result, handle, ensure_ascii=True, indent=2)
        handle.write("\n")
    print(json.dumps({key: result[key] for key in ("status", "raw_source_rows", "database_sha256", "app_records_added")}))


if __name__ == "__main__":
    main()
