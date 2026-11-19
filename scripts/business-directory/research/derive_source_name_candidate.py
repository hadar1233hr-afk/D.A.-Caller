#!/usr/bin/env python3
"""Select two exact source names in a new outside-app, non-admitted candidate.

The original raw records, original decisions and parent candidate remain intact.
No record_values/project_record, source repair, asset installation or publication
step is used. Native schema/text/phone/FTS helpers are shared reference contracts.
"""
from __future__ import annotations

import argparse
from contextlib import closing
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import shutil
import sqlite3
import tempfile
import unicodedata

from phone_identity_review import production_packager, review_phone_identity
import prepare_business_candidate as reference
from source_text_review import TEXT_FIELDS

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
PROFILE = "EXACT_SOURCE_NAME_SELECTION_DERIVATIVE_NOT_ADMITTED"
REPORT_SHA = "e04848419c58d79e9d3e5eeab9c3e778cfa3f223554966f374625d4bd3a299fc"
PBF_SHA = "62c6ff161b738588454719f0fd1c4971c9b46535f4ab04313e7bbe1dd582afdf"
PARENT_SHA = {
    "candidate-businesses.sqlite": "4eee8e84a3ccab3f291cb58be1e384a0dc16351612511cfe884e14519af3fd0e",
    "candidate-reader-manifest.json": "be9ce29231392c50920fdaaf53a9375a24a810a9b5bf65a3d6bf200f1179ee88",
    "candidate-review-envelope.json": "764add4727a2598ec28277bac0dab10fb4b06039b225111098334d497d8d4439",
    "candidate-decisions.ndjson": "18303b1c9e46f49adddcb6ba3323f64e86ce0c8ec5f691f38c61d34ce72421de",
    "input-binding.json": "b0533ce259fc71ffd0959c05314f479b84cc2b7cf8bb7844d41c0e51dca1450a",
}
SELECTED = {
    "osm:node:5801536067": "ee14d01f178fbf01f8c69ecf2260d819f4f74eecc2f3dc89914e4b58677b1cf3",
    "osm:node:12543641901": "47ab06491d476194656605194af2ad9b0582d829cd069eaeee632592a9b907d7",
}
HISTORICAL_TABLES = ("candidate_raw_records", "candidate_decisions")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def require_unchanged(snapshots):
    for path, expected_sha in snapshots.items():
        require(digest(path) == expected_sha, "Parent/source/code/policy changed during derivative build")


def read_only(path):
    db = sqlite3.connect(Path(path).resolve().as_uri() + "?mode=ro", uri=True)
    db.row_factory = sqlite3.Row
    return db


def exact_text(value, maximum, empty=False):
    observed = production_packager().bounded_text(value, maximum, empty=empty)
    require(observed == value, "Exact source text would require normalization/trim")
    try:
        units = len(value.encode("utf-16-le")) // 2
    except UnicodeEncodeError as error:
        raise ValueError("Invalid source UTF-16") from error
    require(units <= maximum, "Source exceeds native UTF-16 limit")
    return value


def record_key(row):
    return hashlib.sha256(json.dumps([row["source_id"], row["phone"], row["extension"]],
        ensure_ascii=True, separators=(",", ":")).encode()).hexdigest()


def select_original_name(original_line, proof, historical_decision, selected_tag="name"):
    """Return a new projection/event, never an altered source record.

    This deliberately narrow snapshot rule admits only the two reviewed raw
    source-line hashes. Other alternatives and future snapshots require review.
    """
    require(isinstance(original_line, bytes), "Exact original BLOB required")
    row = json.loads(original_line)
    source = row.get("source_id")
    require(source in SELECTED and hashlib.sha256(original_line).hexdigest() == SELECTED[source],
            "Reviewed source identity/original-line checksum differs")
    require(isinstance(proof, dict) and proof.get("original_record") == row
            and proof.get("failed_fields") == ["name"] and proof.get("app_accepted") is False,
            "Exact independently reread source row/name-only proof required")
    require(historical_decision == "WITHHELD_SOURCE_TEXT", "Original withholding decision required")
    require(selected_tag == "name", "Only exact primary source name selection is reviewed")
    source_node = proof.get("independent_source_node", {})
    require(all(source_node.get(key) == row.get(key) for key in
                ("source_id", "source_url", "source_updated_at")), "Source proof ID/URL/time mismatch")
    raw = source_node.get("source_fields", {})
    names = row.get("source_names", {})
    require(isinstance(raw, dict) and isinstance(names, dict)
            and raw.get("name") == names.get("name")
            and raw.get("name:he") == names.get("name:he") == row.get("name"),
            "Exact original primary/preferred source-name tags disagree")
    selected = exact_text(raw.get("name"), 300)
    preferred = row["name"]
    require(selected != preferred and any(unicodedata.category(c) == "Cf" for c in preferred),
            "Selection requires the reviewed unsafe preferred name")
    require(row["source_url"] == "https://www.openstreetmap.org/node/" + source.split(":")[-1]
            and row["license_id"] == "ODbL-1.0"
            and row["status"] == "RESEARCH_ONLY_NOT_APP_ACCEPTED"
            and row["verification"] == "source-listed-not-phone-confirmed"
            and row["geographic_scope"] == "research-node-source-IL-interior-minus-filled-WB-and-Gaza"
            and row["country_tag"] is None and row["source_country_fields"] == {},
            "Original source/country/scope/license provenance differs")
    stamp = dt.datetime.fromisoformat(row["source_updated_at"].replace("Z", "+00:00"))
    require(stamp.tzinfo is not None and stamp.utcoffset() is not None, "Source timezone missing")
    core = production_packager()
    for field, (maximum, empty, default) in TEXT_FIELDS.items():
        if field != "name":
            exact_text(row.get(field, default), maximum, empty)
    phone = review_phone_identity(row)
    require(phone["decision"] == "IDENTITY_UNCHANGED" and row["extension"] == "",
            "Name selection cannot change a phone identity/extension")
    canonical = phone["proposal"]["base_dial_identity"]
    require(core.normalized_phone(row["phone"]) == canonical, "Current reader dial identity differs")
    stable = reference.stable_id(source, canonical)
    values = (stable, selected, row["phone"], row["phone"], row["city"], row["category"],
              row["address"], row["source_url"], source, "ODbL-1.0", row["source_updated_at"], canonical)
    event = dict(record_key=record_key(row), source_id=source,
        old_decision=historical_decision, old_candidate_business_id=None,
        derivative_decision="SELECTED_EXACT_SOURCE_NAME_FOR_REVIEW_NOT_ADMITTED",
        new_candidate_business_id=stable, selected_source_tag=selected_tag,
        selected_source_value=selected, original_preferred_tag="name:he",
        original_preferred_value=preferred, original_line_sha256=SELECTED[source],
        source_url=row["source_url"], source_updated_at=row["source_updated_at"],
        source_version=row.get("source_version"), source_version_absent_in_raw="source_version" not in row,
        source_phone=row["phone"], original_phone=row["original_phone"], extension=row["extension"],
        original_country=row["country_tag"], original_country_fields=row["source_country_fields"],
        original_scope=row["geographic_scope"], source_text_report_sha256=REPORT_SHA,
        source_pbf_sha256=PBF_SHA, source_mutations=0, app_admitted=False,
        production_admission=False, publication_approval=False)
    return values, event


def validate_inputs(parent, report_path):
    snapshots = {parent / name: expected for name, expected in PARENT_SHA.items()}
    snapshots[report_path] = REPORT_SHA
    buffers = {}
    for path, expected in snapshots.items():
        blob = path.read_bytes()
        require(hashlib.sha256(blob).hexdigest() == expected,
                "Immutable reviewed parent checksum or source report checksum differs: " + path.name)
        buffers[path] = blob
    report = json.loads(buffers[report_path])
    require(report.get("source_pbf_sha256") == PBF_SHA
            and type(report.get("app_accepted_rows")) is int and report["app_accepted_rows"] == 0
            and type(report.get("automatically_replaced_names")) is int and report["automatically_replaced_names"] == 0,
            "Retained source proof must not claim admission or replacement")
    require(len(report["rows"]) == 3
            and {p["original_record"]["source_id"] for p in report["rows"]}
                == set(SELECTED) | {"osm:node:5066707334"}, "Exact three source rereads required")
    bindings = json.loads(buffers[parent / "input-binding.json"])
    require(len(bindings) == 49 and all(isinstance(k, str) and isinstance(v, str)
            and re.fullmatch(r"[a-f0-9]{64}", v) for k, v in bindings.items()),
            "All49 original source/code/audit bindings required")
    for key, expected in bindings.items():
        path = Path(key)
        require(snapshots.setdefault(path, expected) == expected, "Contradictory immutable input binding")
    for path in parent.rglob("*"):
        if path.is_file():
            observed = digest(path)
            require(snapshots.setdefault(path, observed) == observed,
                    "Immutable parent changed during input validation")
    require_unchanged(snapshots)
    return report, snapshots


def create_selection_schema(db):
    db.executescript("""
    PRAGMA foreign_keys=ON;
    CREATE TABLE candidate_source_name_selections(
      record_key TEXT PRIMARY KEY REFERENCES candidate_raw_records(record_key),
      candidate_business_id INTEGER NOT NULL UNIQUE REFERENCES businesses(id),
      original_decision TEXT NOT NULL CHECK(original_decision='WITHHELD_SOURCE_TEXT'),
      selected_tag TEXT NOT NULL CHECK(selected_tag='name'),
      original_preferred_name TEXT NOT NULL,selected_value TEXT NOT NULL,
      source_id TEXT NOT NULL,source_url TEXT NOT NULL,source_updated_at TEXT NOT NULL,
      original_line_sha256 TEXT NOT NULL,text_source_report_sha256 TEXT NOT NULL,
      app_admitted INTEGER NOT NULL DEFAULT 0 CHECK(app_admitted=0));
    """)


def table_rows(db, table):
    require(table in (*HISTORICAL_TABLES, "businesses", "business_search"), "Unknown comparison table")
    order = "record_key" if table in HISTORICAL_TABLES else "id" if table == "businesses" else "docid"
    columns = "docid,name,city,category,phone" if table == "business_search" else "*"
    return [tuple(r) for r in db.execute(f"SELECT {columns} FROM {table} ORDER BY {order}")]


def verify_derivative(db, parent_db, new_values):
    require(db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
            and not db.execute("PRAGMA foreign_key_check").fetchall(), "Derivative integrity/foreign keys failed")
    for table in HISTORICAL_TABLES:
        require(table_rows(db, table) == table_rows(parent_db, table), "Historical raw/decisions changed")
    expected = {r[0]: r for r in table_rows(parent_db, "businesses")}
    for row in new_values:
        require(row[0] not in expected and not any((r[8], r[11]) == (row[8], row[11]) for r in expected.values()),
                "Stable/source identity collision; never merge or overwrite")
        expected[row[0]] = tuple(row)
    require({r[0]: r for r in table_rows(db, "businesses")} == expected, "Existing/new business fields changed")
    core = production_packager()
    expected_fts = {k: (core.search_text(r[1]), core.search_text(r[4]), core.search_text(r[5]), r[2])
                    for k, r in expected.items()}
    require({r[0]: r[1:] for r in table_rows(db, "business_search")} == expected_fts,
            "Derivative missing/extra/swapped FTS identity/content")
    projected = {r[0] for r in db.execute("SELECT candidate_business_id FROM candidate_decisions "
        "WHERE candidate_business_id IS NOT NULL UNION ALL SELECT candidate_business_id FROM candidate_source_name_selections")}
    require(projected == set(expected) and len(expected) == 2717, "Effective projection union differs")
    require(db.execute("SELECT count(*) FROM candidate_source_name_selections").fetchone()[0] == 2,
            "Exactly two selected source names required")
    return expected


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=True, sort_keys=True, indent=2) + "\n", encoding="utf-8")


def build(parent: Path, report_path: Path, output: Path,
          version="owner-261103-exact-source-name-candidate-v1"):
    require(not output.exists(), "Output exists; never overwrite parent or retained candidate")
    require(isinstance(version, str) and re.fullmatch(r"[A-Za-z0-9._-]{1,80}", version), "Bounded candidate version required")
    policy = REPO / "docs/releases/business-source-name-selection-review.md"
    execution_snapshots = {path: digest(path) for path in
        (Path(__file__), HERE / "check_source_name_candidate.py", policy)}
    report, snapshots = validate_inputs(parent, report_path)
    for path, expected in execution_snapshots.items():
        require(snapshots.setdefault(path, expected) == expected, "Contradictory code/policy binding")
    require_unchanged(snapshots)
    parent_envelope = json.loads((parent / "candidate-review-envelope.json").read_text())
    require(parent_envelope["production_admission"] is False
            and parent_envelope["publication_approval"] is False
            and parent_envelope["native_integration"] == "NOT_TESTED"
            and type(parent_envelope["app_records_added"]) is int and parent_envelope["app_records_added"] == 0,
            "Parent candidate must remain non-admitted")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="source-name-candidate-", dir=output.parent) as temporary:
        temp = Path(temporary)
        shutil.copytree(parent, temp, dirs_exist_ok=True)
        for name in ("candidate-reader-manifest.json", "candidate-review-envelope.json"):
            shutil.copyfile(parent / name, temp / ("parent-" + name))
        shutil.copyfile(report_path, temp / "bound-source-text-report.json")
        shutil.copyfile(policy, temp / "source-name-selection-policy-review.md")
        db_path = temp / "candidate-businesses.sqlite"
        events, projected = [], []
        with closing(read_only(parent / "candidate-businesses.sqlite")) as parent_db, closing(sqlite3.connect(db_path)) as db:
            create_selection_schema(db)
            for proof in report["rows"]:
                source = proof["original_record"]["source_id"]
                if source not in SELECTED:
                    continue
                key = record_key(proof["original_record"])
                original = parent_db.execute("SELECT original_line,line_sha FROM candidate_raw_records WHERE record_key=?", (key,)).fetchone()
                old = parent_db.execute("SELECT decision,candidate_business_id,app_admitted FROM candidate_decisions WHERE record_key=?", (key,)).fetchone()
                require(original is not None and old is not None and old[1] is None and old[2] == 0,
                        "Exact parent withheld record required")
                require(original["line_sha"] == SELECTED[source], "Parent original-line proof differs")
                values, event = select_original_name(original["original_line"], proof, old[0])
                require(db.execute("SELECT 1 FROM businesses WHERE id=? OR (source_id=? AND normalized_phone=?)",
                    (values[0], values[8], values[11])).fetchone() is None, "New selection identity collides")
                db.execute("INSERT INTO businesses VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", values)
                core = production_packager()
                db.execute("INSERT INTO business_search(docid,name,city,category,phone) VALUES (?,?,?,?,?)",
                    (values[0], core.search_text(values[1]), core.search_text(values[4]), core.search_text(values[5]), values[2]))
                db.execute("INSERT INTO candidate_source_name_selections VALUES (?,?,?,?,?,?,?,?,?,?,?,0)",
                    (key, values[0], old[0], "name", event["original_preferred_value"], values[1],
                     source, values[7], values[10], SELECTED[source], REPORT_SHA))
                events.append(event)
                projected.append(values)
            db.execute("UPDATE metadata SET value=? WHERE key='dataset_version'", (version,))
            db.commit()
            expected = verify_derivative(db, parent_db, projected)
            db.execute("VACUUM")
            counts = dict(candidate_rows=len(expected), candidate_source_objects=len({r[8] for r in expected.values()}),
                candidate_distinct_normalized_phones=len({r[11] for r in expected.values()}), raw_source_rows=2728,
                historical_decision_rows=2728, historical_source_text_withheld_rows=3,
                exact_source_name_selections=len(events), effective_source_text_withheld=1,
                effective_phone_format_withheld=10, app_records_added=0, source_mutations=0, source_rows_merged=0)
        ledger = temp / "candidate-source-name-selections.ndjson"
        ledger.write_text("".join(json.dumps(e, ensure_ascii=True, sort_keys=True) + "\n" for e in events), encoding="utf-8")
        require(digest(temp / "candidate-decisions.ndjson") == PARENT_SHA["candidate-decisions.ndjson"], "Original decision ledger changed")
        manifest = json.loads((parent / "candidate-reader-manifest.json").read_text())
        manifest.update(dataset_version=version, database_sha256=digest(db_path), record_count=len(expected),
            source_name="OpenStreetMap exact source-name derivative; NOT ADMITTED")
        reference.validate_reader_manifest(manifest)
        require(db_path.stat().st_size <= production_packager().MAX_DATABASE_BYTES, "Candidate exceeds native DB cap")
        write_json(temp / "candidate-reader-manifest.json", manifest)
        write_json(temp / "source-name-input-binding.json", {str(p): h for p, h in snapshots.items()})
        envelope = dict(parent_envelope)
        acquisition = dict(parent_envelope["acquisition"])
        acquisition.update(zip_crc_rechecked=False, zip_pbf_hash_rechecked=False,
            parent_transfer_envelope_sha256=PARENT_SHA["candidate-review-envelope.json"],
            origin_note="Derivative rehashes retained parent/all49 reference files; ZIP CRC/member equality proof is inherited from exact parent, not newly executed")
        envelope.update(profile=PROFILE, counts=counts, parent_counts=parent_envelope["counts"],
            parent_database_sha256=PARENT_SHA["candidate-businesses.sqlite"], parent_envelope_sha256=PARENT_SHA["candidate-review-envelope.json"],
            database_sha256=digest(db_path), database_bytes=db_path.stat().st_size,
            reader_manifest_sha256=digest(temp / "candidate-reader-manifest.json"),
            decision_ledger_sha256=PARENT_SHA["candidate-decisions.ndjson"],
            source_name_selection_ledger_sha256=digest(ledger), source_text_report_sha256=REPORT_SHA,
            source_name_selection_builder_sha256=snapshots[Path(__file__)],
            source_name_selection_policy_sha256=snapshots[policy],
            source_name_selection_check_sha256=snapshots[HERE / "check_source_name_candidate.py"],
            parent_builder_sha256=parent_envelope["builder_sha256"],
            builder_sha256=snapshots[Path(__file__)],
            acquisition=acquisition, retained_zip_crc_rechecked=False,
            retained_zip_crc_proof_inherited_from_parent=True, retained_parent_source_files_rehashed=True,
            historical_parent_decisions_unchanged=True, existing_2715_businesses_unchanged=True,
            observed_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(),
            candidate_contract="Explicit exact original primary-name selection for two reviewed rows; parent raw/decisions unchanged; not production admission",
            limits=parent_envelope["limits"] + ["Two unsafe preferred Hebrew names remain original in raw/history; only exact source tag name is selected for display.",
                "One city/address row and ten UAN phone proposals remain effectively withheld.",
                "Original decision ledger is historical; separate source-name selections define the derivative's two additional projections."])
        write_json(temp / "candidate-review-envelope.json", envelope)
        (temp / "LICENSE-NOTICE.txt").write_text((parent / "LICENSE-NOTICE.txt").read_text()
            + "Two exact source primary-name selections and their original raw/history/proof remain downloadable.\n", encoding="utf-8")
        require_unchanged(snapshots)
        require(not output.exists(), "Output appeared during build; do not overwrite")
        temp.rename(output)
    return envelope


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("parent", "report", "output"):
        parser.add_argument(name, type=Path)
    parser.add_argument("--version", default="owner-261103-exact-source-name-candidate-v1")
    args = parser.parse_args()
    result = build(args.parent, args.report, args.output, args.version)
    print(json.dumps({key: result[key] for key in ("profile", "counts", "database_sha256", "app_records_added")}))


if __name__ == "__main__":
    main()
