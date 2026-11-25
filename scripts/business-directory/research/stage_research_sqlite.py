#!/usr/bin/env python3
"""Offline research search staging. Never a production app dataset or importer."""
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
import unicodedata

import phonenumbers

from phone_identity_review import review_phone_identity
from source_text_review import review_record

SCHEMA = "DA_CALLER_RESEARCH_SEARCH_V1_NOT_APP_ACCEPTED"
DATABASE_APPLICATION_ID = 0x44525331
HERE = Path(__file__).resolve().parent


def digest(path: Path) -> str:
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def record_key(record: dict) -> str:
    values = [record.get(key) for key in ("source_id", "phone", "extension")]
    if any(not isinstance(value, str) for value in values):
        raise ValueError("Original source/phone/extension identity required")
    return hashlib.sha256(json.dumps(values, ensure_ascii=True, separators=(",", ":")).encode()).hexdigest()


def search_text(value: str) -> str:
    return "".join(char for char in unicodedata.normalize("NFKD", value).casefold()
                   if not unicodedata.category(char).startswith("M"))


def search_expression(value: str) -> str:
    if not isinstance(value, str) or len(value) > 300:
        raise ValueError("Bounded research query required")
    tokens = re.findall(r"[^\W_]+", search_text(value), flags=re.UNICODE)
    if re.fullmatch(r"[+0-9() .\-]+", value):
        digits = re.sub(r"[^0-9]", "", value)
        tokens = [digits] if digits else []
    if len(tokens) > 20:
        raise ValueError("At most20 search tokens")
    return " AND ".join('"' + token + '*"' for token in tokens)


def validate_cohort(kind: str, corpus: Path, evidence: Path, source_sha: str, source_bytes: int) -> tuple[list, dict]:
    if kind == "node":
        report_path = evidence / "node-geography-report.json"
        audit_path = evidence / "node-geography-source-audit.json"
    elif kind == "way":
        report_path = evidence / "way-geography-report.json"
        audit_path = evidence / "way-geography-independent-audit.json"
    elif kind == "relation":
        report_path = evidence / "relation-geography-report.json"
        audit_path = evidence / "relation-geography-independent-audit.json"
    else:
        raise ValueError("Only explicitly audited source geometry cohorts supported")
    report, audit = json.loads(report_path.read_text()), json.loads(audit_path.read_text())
    data = corpus.read_bytes()
    corpus_sha = hashlib.sha256(data).hexdigest()
    if (report.get("source_pbf_sha256") != source_sha
            or report.get("source_pbf_bytes") != source_bytes
            or report.get("ndjson_sha256") != corpus_sha):
        raise ValueError("Actual corpus/source bytes differ from retained report")
    lines = data.splitlines(keepends=True)
    rows = [json.loads(line) for line in lines]
    if not rows or any(not isinstance(row, dict) for row in rows):
        raise ValueError("Nonempty original research object rows required")
    unique_sources = len({row.get("source_id") for row in rows})
    if kind == "node":
        valid = (audit.get("status") == "INDEPENDENT_ACTUAL_SOURCE_NODE_AUDIT_PASS_RESEARCH_ONLY"
                 and audit.get("ndjson_sha256") == corpus_sha
                 and audit.get("v2_rows") == report.get("research_raw_phone_records") == len(rows)
                 and audit.get("v2_unique_nodes") == report.get("research_unique_source_nodes") == unique_sources
                 and audit.get("all_v2_node_ids_names_categories_phone_values_country_fields_coordinates_timestamps_matched_actual_PBF") is True
                 and audit.get("input_rows_and_hashes_matched") is True)
    elif kind == "way":
        valid = (audit.get("status") == "PASS_RESEARCH_SOURCE_AND_COMPLETE_POLYGON_AUDIT_ONLY"
                 and audit.get("source_pbf_sha256") == source_sha
                 and audit.get("research_ndjson_sha256") == corpus_sha
                 and audit.get("research_report_sha256") == digest(report_path)
                 and audit.get("source_way_rows") == report.get("raw_phone_rows") == len(rows)
                 and audit.get("unique_original_source_ways_found") == report.get("source_ways") == unique_sources
                 and audit.get("checks_passed", {}).get("independent_complete_polygon_valid_area_containment_margin_exclusion_rows") == len(rows))
    else:
        valid = (audit.get("status") == "PASS_RESEARCH_SOURCE_AND_HOLE_AWARE_RELATION_AUDIT_ONLY"
                 and audit.get("source_pbf_sha256") == source_sha
                 and audit.get("research_ndjson_sha256") == corpus_sha
                 and audit.get("research_report_sha256") == digest(report_path)
                 and audit.get("emitted_phone_rows") == report.get("raw_phone_rows") == len(rows)
                 and audit.get("emitted_source_relations") == report.get("source_relations") == unique_sources
                 and audit.get("checks_passed", {}).get("exact_role_cycles_traversal_source_edges_true_holes_rows") == len(rows)
                 and audit.get("checks_passed", {}).get("independent_complete_polygon_valid_area_containment_margin_exclusion_rows") == len(rows))
    if not valid:
        raise ValueError("Exact successful retained source audit required")
    for row in rows:
        source_id = row.get("source_id", "")
        if (not re.fullmatch(r"osm:" + kind + r":[1-9][0-9]*", source_id)
                or row.get("license_id") != "ODbL-1.0"
                or row.get("verification") != "source-listed-not-phone-confirmed"
                or row.get("status") != "RESEARCH_ONLY_NOT_APP_ACCEPTED"
                or row.get("geographic_scope") != report.get("scope")):
            raise ValueError("Original research identity/license/status required")
    return list(zip(rows, lines)), {
        "kind": kind, "corpus_path": str(corpus), "rows": len(rows),
        "source_objects": unique_sources, "corpus_sha256": corpus_sha,
        "report_sha256": digest(report_path), "audit_sha256": digest(audit_path),
        "audit_status": audit["status"], "scope": report["scope"],
    }


def create_schema(database: sqlite3.Connection) -> None:
    database.executescript(f"""
    PRAGMA application_id={DATABASE_APPLICATION_ID};
    PRAGMA user_version=1;
    PRAGMA foreign_keys=ON;
    CREATE TABLE research_metadata(key TEXT PRIMARY KEY,value_json TEXT NOT NULL);
    CREATE TABLE raw_records(
      record_key TEXT PRIMARY KEY,corpus_kind TEXT NOT NULL,ordinal INTEGER NOT NULL,
      original_line BLOB NOT NULL,line_sha256 TEXT NOT NULL,
      UNIQUE(corpus_kind,ordinal));
    CREATE TABLE research_entries(
      search_rowid INTEGER PRIMARY KEY,
      record_key TEXT UNIQUE NOT NULL REFERENCES raw_records(record_key),
      source_id TEXT NOT NULL,source_url TEXT NOT NULL,source_updated_at TEXT NOT NULL,
      name TEXT NOT NULL,source_phone TEXT NOT NULL,e164 TEXT,extension TEXT NOT NULL,
      national_identity_proposal TEXT,city TEXT NOT NULL,address TEXT NOT NULL,
      category TEXT NOT NULL,country_tag TEXT,country_fields_json TEXT NOT NULL,
      original_scope TEXT NOT NULL,text_review_status TEXT NOT NULL,
      phone_review_status TEXT NOT NULL,default_searchable INTEGER NOT NULL,
      sort_name TEXT NOT NULL,app_accepted INTEGER NOT NULL DEFAULT 0 CHECK(app_accepted=0));
    CREATE INDEX research_source_id ON research_entries(source_id);
    CREATE INDEX research_page ON research_entries(sort_name,record_key);
    CREATE VIRTUAL TABLE research_search USING fts4(
      name,city,address,category,phone_variants,tokenize=unicode61);
    """)


def insert_original(database: sqlite3.Connection, kind: str, ordinal: int, row: dict, original_line: bytes) -> dict:
    """Retain originals first. Search eligibility never means app admission."""
    key = record_key(row)
    if json.loads(original_line) != row:
        raise ValueError("Exact source line does not describe supplied record")
    text = review_record(row)
    phone = review_phone_identity(row)
    text_status = "SOURCE_TEXT_WITHHELD" if text["failed_fields"] else "SOURCE_TEXT_WITHIN_BOUNDS"
    phone_status = phone["decision"]
    proposal = phone["proposal"]["base_dial_identity"] if phone["proposal"] else None
    default_searchable = not text["failed_fields"] and phone_status == "IDENTITY_UNCHANGED"
    # Duplicate source/base/extension keys fail the unique constraint; no merge.
    database.execute("INSERT INTO raw_records VALUES (?,?,?,?,?)", (
        key, kind, ordinal, original_line, hashlib.sha256(original_line).hexdigest()))
    values = (key, row["source_id"], row["source_url"], row["source_updated_at"], row["name"],
              row["phone"], row.get("e164"), row["extension"], proposal, row["city"],
              row["address"], row["category"], row["country_tag"],
              json.dumps(row["source_country_fields"], ensure_ascii=True, sort_keys=True),
              row["geographic_scope"], text_status, phone_status, int(default_searchable),
              search_text(row["name"]), 0)
    cursor = database.execute("INSERT INTO research_entries VALUES (NULL," + ",".join("?" for _ in values) + ")", values)
    if not text["failed_fields"] and phone["proposal"]:
        phone_variants = " ".join(value for value in (row["phone"], row.get("e164"), proposal) if value)
        database.execute("INSERT INTO research_search(docid,name,city,address,category,phone_variants) VALUES (?,?,?,?,?,?)", (
            cursor.lastrowid, *(search_text(row[field]) for field in ("name", "city", "address", "category")), phone_variants))
    return {"text_status": text_status, "phone_status": phone_status,
            "default_searchable": bool(default_searchable), "original_fields_unchanged": True}


def query(database: Path, value: str, *, limit: int = 30, after: tuple | None = None,
          include_phone_proposals: bool = False) -> list[dict]:
    if type(limit) is not int or not 1 <= limit <= 100:
        raise ValueError("Research query page size must be1..100")
    if type(include_phone_proposals) is not bool:
        raise ValueError("Phone proposals require an explicit boolean research opt-in")
    expression = search_expression(value)
    if not expression:
        return []
    connection = sqlite3.connect(database.resolve().as_uri() + "?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    try:
        if connection.execute("PRAGMA application_id").fetchone()[0] != DATABASE_APPLICATION_ID:
            raise ValueError("This is not a research staging database")
        conditions = ["research_search MATCH ?"]
        params = [expression]
        if not include_phone_proposals:
            conditions.append("e.default_searchable=1")
        if after is not None:
            if (not isinstance(after, (tuple, list)) or len(after) != 2
                    or any(not isinstance(item, str) for item in after)):
                raise ValueError("Exact previous research sort/key cursor required")
            conditions.append("(e.sort_name,e.record_key)>(?,?)")
            params.extend(after)
        params.append(limit)
        result = connection.execute(
            "SELECT e.* FROM research_search f JOIN research_entries e ON e.search_rowid=f.docid WHERE "
            + " AND ".join(conditions) + " ORDER BY e.sort_name,e.record_key LIMIT ?", params)
        return [dict(row) for row in result]
    finally:
        connection.close()


def build(pbf: Path, node_corpus: Path, way_corpus: Path, evidence: Path, output: Path,
          *, relation_corpus: Path | None = None) -> dict:
    if output.exists():
        raise ValueError("Staging output exists; never overwrite source/app data")
    code_paths = (Path(__file__), HERE / "phone_identity_review.py", HERE / "source_text_review.py",
                  HERE.parent / "package_app_dataset.py", HERE.parent / "requirements-lock.txt")
    code_hashes = {path: digest(path) for path in code_paths}
    input_paths = [pbf, node_corpus, way_corpus,
        evidence / "node-geography-report.json", evidence / "node-geography-source-audit.json",
        evidence / "way-geography-report.json", evidence / "way-geography-independent-audit.json"]
    cohort_inputs = [("node", node_corpus), ("way", way_corpus)]
    if relation_corpus is not None:
        input_paths.extend((relation_corpus, evidence / "relation-geography-report.json",
                            evidence / "relation-geography-independent-audit.json"))
        cohort_inputs.append(("relation", relation_corpus))
    bound_paths = {p: digest(p) for p in input_paths}
    bound_paths.update(code_hashes)
    source_sha, source_bytes = bound_paths[pbf], pbf.stat().st_size
    cohorts, bindings = {}, []
    for kind, corpus in cohort_inputs:
        cohorts[kind], binding = validate_cohort(kind, corpus, evidence, source_sha, source_bytes)
        bindings.append(binding)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="research-search-", dir=output.parent) as temporary:
        temp = Path(temporary)
        database_path = temp / "research-search.sqlite"
        database = sqlite3.connect(database_path)
        try:
            create_schema(database)
            counts = collections.Counter()
            for kind, rows in cohorts.items():
                for ordinal, (record, original_line) in enumerate(rows):
                    decision = insert_original(database, kind, ordinal, record, original_line)
                    counts["raw_rows"] += 1
                    counts[decision["text_status"]] += 1
                    counts[decision["phone_status"]] += 1
                    counts["default_searchable_rows"] += decision["default_searchable"]
            metadata = dict(schema=SCHEMA, research_only=True, app_accepted=False,
                source_pbf_sha256=source_sha, source_pbf_bytes=source_bytes, cohorts=bindings,
                counts=dict(sorted(counts.items())), observed_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(),
                staging_script_sha256=code_hashes[Path(__file__)],
                phone_review_sha256=code_hashes[HERE / "phone_identity_review.py"],
                text_review_sha256=code_hashes[HERE / "source_text_review.py"],
                production_phone_contract_sha256=code_hashes[HERE.parent / "package_app_dataset.py"],
                requirements_lock_sha256=code_hashes[HERE.parent / "requirements-lock.txt"],
                phonenumbers_version=phonenumbers.__version__, sqlite_version=sqlite3.sqlite_version,
                limits=["Research searchability is not production admission or verified ownership/currentness.",
                        "Raw source lines and null country tags remain unchanged; no country is inferred.",
                        "Three source-text rows are retained outside search; phone proposals require explicit research opt-in.",
                        "No app/businesses schema, dial action, personal data, network or release safeguard changes."])
            database.executemany("INSERT INTO research_metadata VALUES (?,?)", [
                (key, json.dumps(value, ensure_ascii=True, sort_keys=True)) for key, value in metadata.items()])
            database.commit()
            if (database.execute("PRAGMA integrity_check").fetchone()[0] != "ok"
                    or database.execute("PRAGMA foreign_key_check").fetchall()):
                raise ValueError("Research database integrity failed")
            database.execute("VACUUM")
        finally:
            database.close()
        if any(digest(path) != value for path, value in bound_paths.items()):
            raise ValueError("Bound source/evidence changed during staging")
        metadata["database_sha256"] = digest(database_path)
        metadata["database_bytes"] = database_path.stat().st_size
        (temp / "manifest.json").write_text(json.dumps(metadata, ensure_ascii=True, indent=2) + "\n")
        (temp / "LICENSE-NOTICE.txt").write_text(
            "© OpenStreetMap contributors\nODbL1.0 https://opendatacommons.org/licenses/odbl/1-0/\n"
            "Research derived source database; not app accepted or verified live business/phone ownership.\n")
        Path(temporary).rename(output)
    return metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    prepare = sub.add_parser("build")
    for field in ("pbf", "node_corpus", "way_corpus", "evidence", "output"):
        prepare.add_argument(field, type=Path)
    prepare.add_argument("--relation-corpus", type=Path)
    search = sub.add_parser("query")
    search.add_argument("database", type=Path)
    search.add_argument("value")
    search.add_argument("--include-phone-proposals", action="store_true")
    args = parser.parse_args()
    if args.command == "build":
        result = build(args.pbf, args.node_corpus, args.way_corpus, args.evidence, args.output,
                       relation_corpus=args.relation_corpus)
        print(json.dumps({key: result[key] for key in ("schema", "counts", "database_sha256")}, sort_keys=True))
    else:
        print(json.dumps(query(args.database, args.value, include_phone_proposals=args.include_phone_proposals), ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
