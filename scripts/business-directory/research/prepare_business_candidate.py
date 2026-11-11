#!/usr/bin/env python3
"""Prepare a fresh, outside-app source-spatial candidate, never app admission.

This deliberately does not call record_values or change the production country
guard. The real reader can load this schema if manually copied, so filenames and
the review envelope are preparation controls, not a runtime security boundary.
"""
from __future__ import annotations

import argparse
import collections
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import shutil
import sqlite3
import tempfile
import zipfile

import audit_expanded_research_sqlite as expanded_audit
import stage_expanded_research_sqlite as expanded
from phone_identity_review import production_packager, review_phone_identity
from research_node_geography import COUNTRY_FIELDS, country_decision
from source_text_review import TEXT_FIELDS

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
RESEARCH_SHA = "1a015426c33835a247198120bd05be026f4f0c25aa874d6c506cc7f07e1ad255"
MANIFEST_SHA = "e400db80e964d763f5a5df342781c671f1b4c7083b50afdc9ae83c049d56eee7"
AUDIT_SHA = "eb60bc3f97c039cf09ff4397127a70b484ab0af15f95f4d9d61a63b5ea8d1425"
PBF_SHA = "62c6ff161b738588454719f0fd1c4971c9b46535f4ab04313e7bbe1dd582afdf"
ARCHIVE_SHA = "578d67213db0cb617f18ccd12c60eab5c532f073fa5520b399d9f5d36973b11e"
STATUS = "CANDIDATE_FOR_REVIEW_NOT_ADMITTED"
PROJECTED = "PROJECTED_FOR_REVIEW_NOT_ADMITTED"


def digest(path: Path) -> str:
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def require_unchanged(snapshots: dict):
    require(all(digest(path) == sha for path, sha in snapshots.items()), "Source/code/audit changed during candidate build")


def stable_id(source_id: str, canonical: str) -> int:
    value = int.from_bytes(hashlib.sha256((source_id + ":" + canonical).encode()).digest()[:8], "big") & ((1 << 63) - 1)
    require(value > 0, "Invalid stable candidate identity")
    return value


def project_record(row: dict) -> dict:
    """A non-mutating reader projection; spatial evidence is bound by build()."""
    require(isinstance(row, dict), "Original source object required")
    source = row.get("source_id")
    match = re.fullmatch(r"osm:(node|way|relation):([1-9][0-9]*)", source if isinstance(source, str) else "")
    require(match is not None, "Exact source object required")
    require(row.get("source_url") == f"https://www.openstreetmap.org/{match[1]}/{match[2]}"
            and row.get("license_id") == "ODbL-1.0"
            and row.get("verification") == "source-listed-not-phone-confirmed"
            and row.get("status") == "RESEARCH_ONLY_NOT_APP_ACCEPTED", "Original source/license/status required")
    fields = row.get("source_country_fields")
    require(isinstance(fields, dict) and all(k in COUNTRY_FIELDS and isinstance(v, str) for k, v in fields.items()),
            "Original source country fields required")
    method, country = country_decision(fields)
    require(method != "reject-explicit-non-IL" and country == row.get("country_tag"),
            "Conflicting or unsourced country field; never infer or relabel")
    stamp = row.get("source_updated_at")
    require(isinstance(stamp, str), "Original source timestamp required")
    try:
        parsed = dt.datetime.fromisoformat(stamp.replace("Z", "+00:00"))
    except (ValueError, TypeError) as error:
        raise ValueError("Invalid source timestamp") from error
    require(parsed.tzinfo is not None and parsed.utcoffset() is not None, "Source timestamp needs timezone")
    if "source_version" in row:
        require(type(row["source_version"]) is int and row["source_version"] > 0, "Original source version required")
    require(isinstance(row.get("geographic_scope"), str) and bool(row["geographic_scope"]), "Original scope required")
    phone = review_phone_identity(row)
    withheld = lambda reason: dict(decision=reason, values=None, phone_review_status=phone["decision"])
    if row.get("extension", ""):
        return withheld("WITHHELD_EXTENSION")
    if phone["decision"] != "IDENTITY_UNCHANGED":
        return withheld("WITHHELD_PHONE_FORMAT_REVIEW")
    core = production_packager()
    for field, (maximum, empty, default) in TEXT_FIELDS.items():
        original = row.get(field, default)
        try:
            bounded = core.bounded_text(original, maximum, empty=empty)
        except ValueError:
            return withheld("WITHHELD_SOURCE_TEXT")
        if bounded != original:
            return withheld("WITHHELD_SOURCE_NORMALIZATION")
    try:
        name_units = len(row["name"].encode("utf-16-le")) // 2
    except UnicodeEncodeError:
        return withheld("WITHHELD_UTF16_NAME")
    if name_units > 300:
        return withheld("WITHHELD_UTF16_NAME")
    canonical = phone["proposal"]["base_dial_identity"]
    require(core.normalized_phone(row["phone"]) == canonical, "Reader/source dial identity differs")
    values = (stable_id(source, canonical), row["name"], row["phone"], row["phone"],
              row.get("city", ""), row.get("category", ""), row.get("address", ""),
              row["source_url"], source, "ODbL-1.0", stamp, canonical)
    return dict(decision=PROJECTED, values=values, phone_review_status=phone["decision"])


def create_candidate_schema(database: sqlite3.Connection):
    production_packager().create_schema(database)
    database.executescript("""
    PRAGMA foreign_keys=ON;
    CREATE TABLE candidate_raw_records(
      record_key TEXT PRIMARY KEY,corpus_kind TEXT NOT NULL,ordinal INTEGER NOT NULL,
      original_line BLOB NOT NULL,line_sha TEXT NOT NULL,UNIQUE(corpus_kind,ordinal));
    CREATE TABLE candidate_decisions(
      record_key TEXT PRIMARY KEY REFERENCES candidate_raw_records(record_key),
      decision TEXT NOT NULL,candidate_business_id INTEGER UNIQUE REFERENCES businesses(id),
      app_admitted INTEGER NOT NULL DEFAULT 0 CHECK(app_admitted=0),
      CHECK((decision='PROJECTED_FOR_REVIEW_NOT_ADMITTED')=(candidate_business_id IS NOT NULL)));
    """)


def validate_known_inputs(research_db: Path, manifest: Path, audit: Path, pbf: Path):
    """Bind this reviewed snapshot, not an arbitrary self-consistent input pair."""
    for path, expected in ((research_db, RESEARCH_SHA), (manifest, MANIFEST_SHA), (audit, AUDIT_SHA), (pbf, PBF_SHA)):
        require(digest(path) == expected, "Reviewed immutable input checksum mismatch: " + path.name)
    metadata, proof = json.loads(manifest.read_text()), json.loads(audit.read_text())
    require(metadata.get("database_sha256") == RESEARCH_SHA
            and type(metadata.get("database_bytes")) is int and metadata["database_bytes"] == research_db.stat().st_size
            and metadata.get("research_only") is True and metadata.get("app_accepted") is False
            and proof.get("status") == "PASS_EXPANDED_RESEARCH_SQLITE_FIDELITY_AND_QUERY_AUDIT_ONLY"
            and proof.get("database_sha256") == RESEARCH_SHA and proof.get("manifest_sha256") == MANIFEST_SHA
            and proof.get("production_admission") is False and proof.get("publication_approval") is False,
            "Reviewed research/audit state mismatch")
    expanded_audit.require_zero_aliases(proof)
    require(type(proof.get("raw_source_rows")) is int and proof["raw_source_rows"] == 2728,
            "Strict integer original row count required")
    return metadata


def validate_reader_manifest(manifest: dict):
    require(manifest.get("format") == "DA_CALLER_BUSINESSES" and type(manifest.get("schema_version")) is int
            and manifest["schema_version"] == 1, "Reader format/schema mismatch")
    require(type(manifest.get("record_count")) is int and 0 <= manifest["record_count"] <= 2_000_000,
            "Strict bounded record count required")
    require(isinstance(manifest.get("dataset_version"), str) and isinstance(manifest.get("database_sha256"), str)
            and re.fullmatch(r"[A-Za-z0-9._-]{1,80}", manifest["dataset_version"])
            and re.fullmatch(r"[a-f0-9]{64}", manifest["database_sha256"]), "Reader identity mismatch")
    core = production_packager()
    for key, maximum in (("source_name", 200), ("source_url", 2048), ("license", 200), ("license_url", 2048), ("updated_at", 80)):
        require(core.bounded_text(manifest.get(key), maximum) == manifest.get(key), "Reader manifest source field differs")
    from urllib.parse import urlsplit
    for key in ("source_url", "license_url"):
        url = urlsplit(manifest[key])
        require(url.scheme == "https" and bool(url.hostname) and not url.username and not url.password, "HTTPS provenance URL required")
    require(len(json.dumps(manifest).encode()) <= 65536, "Reader manifest exceeds bound")


def validate_owner_transfer(receipt: dict, pbf: Path, archive: Path) -> dict:
    """Check actual uploaded bytes, without inventing a country-scoped manifest."""
    require(receipt.get("source") == "owner-uploaded GitHub Release asset"
            and type(receipt.get("asset_id")) is int and receipt["asset_id"] == 712722687
            and receipt.get("url") == "https://github.com/hadar1233hr-afk/D.A.-Caller/releases/download/v0.11.0-beta.9-debug/israel-and-palestine-261103.osm.zip"
            and receipt.get("github_digest_matches") is True and receipt.get("zip_crc") == "PASS"
            and receipt.get("publisher_provenance_independently_verified") is False
            and not any(k in receipt for k in ("http_status", "status", "source_url", "source_license")),
            "Explicit truthful owner transfer receipt required; no publisher HTTP claim")
    for size_key, sha_key, path, expected in (("bytes", "sha256", archive, ARCHIVE_SHA),
                                            ("pbf_bytes", "pbf_sha256", pbf, PBF_SHA)):
        require(type(receipt.get(size_key)) is int and receipt[size_key] == path.stat().st_size
                and 0 < receipt[size_key] <= 2 * 1024 * 1024 * 1024
                and receipt.get(sha_key) == expected == digest(path), "Actual retained owner transfer bytes differ")
    with zipfile.ZipFile(archive) as zipped:
        members = [m for m in zipped.infolist() if not m.is_dir()]
        require(len(members) == 1 and members[0].filename == pbf.name and members[0].file_size == pbf.stat().st_size
                and zipped.testzip() is None, "Owner ZIP member/CRC differs")
        with zipped.open(members[0]) as stream:
            require(hashlib.file_digest(stream, "sha256").hexdigest() == PBF_SHA,
                    "Owner ZIP PBF bytes differ from retained source")
    return dict(method="owner-upload", asset_id=712722687, url=receipt["url"],
                source_sha256=PBF_SHA, source_bytes=pbf.stat().st_size, archive_sha256=ARCHIVE_SHA,
                archive_bytes=archive.stat().st_size, zip_crc_rechecked=True,
                zip_pbf_hash_rechecked=True, publisher_provenance_independently_verified=False,
                production_admission=False,
                origin_note="Retained uploaded ZIP/PBF consistency; not an authenticated publisher download or source-country admission")


def verify_candidate_database(database: sqlite3.Connection, expected: dict, count: int):
    require(database.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
            and not database.execute("PRAGMA foreign_key_check").fetchall(), "Candidate integrity/foreign keys failed")
    require(database.execute("PRAGMA user_version").fetchone()[0] == 1, "Reader schema version differs")
    actual = {row[0]: tuple(row) for row in database.execute("SELECT * FROM businesses")}
    require(actual == expected and len(actual) == count, "Candidate rows differ from original projection")
    indexed = {row[0]: tuple(row[1:]) for row in database.execute("SELECT docid,name,city,category,phone FROM business_search")}
    core = production_packager()
    require(indexed == {key: (core.search_text(row[1]), core.search_text(row[4]), core.search_text(row[5]), row[2])
                        for key, row in expected.items()}, "Missing/extra/swapped FTS identities or content")


def build(pbf: Path, research_db: Path, manifest: Path, evidence: Path, output: Path,
          version: str = "owner-261103-source-geometry-candidate-v1"):
    require(not output.exists(), "Candidate output exists; never overwrite retained data")
    require(re.fullmatch(r"[A-Za-z0-9._-]{1,80}", version) is not None, "Bounded candidate version required")
    require(manifest.resolve() == (research_db.parent / "manifest.json").resolve(), "Original adjacent manifest required")
    audit_path = evidence / "expanded-research-independent-audit.json"
    metadata = validate_known_inputs(research_db, manifest, audit_path, pbf)
    prior = pbf.parent / "research-search-stage-owner-261103-v2"
    receipt_path, archive = pbf.parent / "owner-upload-receipt.json", pbf.with_suffix(".zip")
    snapshot_paths = [pbf, research_db, manifest, audit_path, receipt_path, archive,
                      prior / "research-search.sqlite", prior / "manifest.json", Path(__file__),
                      HERE / "check_business_candidate.py", HERE / "audit_expanded_research_sqlite.py",
                      HERE / "audit_research_sqlite.py", REPO / "app/src/main/java/com/dacaller/app/BusinessDirectory.java"]
    snapshot_paths.extend(expanded_audit.code_paths(evidence).values())
    for cohort in metadata["cohorts"]:
        _, report_name, audit_name, _ = expanded_audit.FAMILIES[cohort["kind"]]
        report_path = evidence / report_name
        snapshot_paths.extend((Path(cohort["corpus_path"]), report_path, evidence / audit_name))
        report = json.loads(report_path.read_text())
        for name, sha in report["geometry_provenance"]["input_sha256"].items():
            path = pbf.parent / name
            require(digest(path) == sha, "Original geometry/reference checksum differs")
            snapshot_paths.append(path)
    snapshots = {path: digest(path) for path in snapshot_paths}
    require(snapshots[research_db] == RESEARCH_SHA and snapshots[manifest] == MANIFEST_SHA
            and snapshots[audit_path] == AUDIT_SHA and snapshots[pbf] == PBF_SHA,
            "Reviewed inputs changed before snapshot")
    receipt = json.loads(receipt_path.read_text())
    acquisition = validate_owner_transfer(receipt, pbf, archive)
    replay = expanded_audit.audit(research_db.parent, evidence, pbf, prior)
    require(replay["database_sha256"] == RESEARCH_SHA and replay["raw_source_rows"] == 2728,
            "Fresh read-only expanded research replay failed")
    for cohort in metadata["cohorts"]:
        validator = expanded.validate_extra if cohort["kind"] in expanded.EXTRA else expanded.base.validate_cohort
        _, binding = validator(cohort["kind"], Path(cohort["corpus_path"]), evidence, PBF_SHA, pbf.stat().st_size)
        require(binding == cohort, "Distinct source cohort proof differs")
    core = production_packager()
    output.parent.mkdir(parents=True, exist_ok=True)
    counts = collections.Counter()
    with tempfile.TemporaryDirectory(prefix="business-candidate-build-", dir=output.parent) as temporary:
        temp = Path(temporary)
        database_path = temp / "candidate-businesses.sqlite"
        connection = sqlite3.connect(database_path)
        original = sqlite3.connect(research_db.resolve().as_uri() + "?mode=ro", uri=True)
        original.row_factory = sqlite3.Row
        expected, ledgers, source_keys = {}, [], set()
        try:
            create_candidate_schema(connection)
            for retained in original.execute("SELECT r.*,e.default_searchable FROM raw_records r JOIN research_entries e USING(record_key) ORDER BY r.corpus_kind,r.ordinal"):
                row = json.loads(retained["original_line"])
                require(hashlib.sha256(retained["original_line"]).hexdigest() == retained["line_sha256"], "Original line checksum differs")
                projection = project_record(row)
                values = projection["values"]
                require(bool(values) == bool(retained["default_searchable"]), "New selection differs from audited default projection")
                binding = next(c for c in metadata["cohorts"] if c["kind"] == retained["corpus_kind"])
                require(row["geographic_scope"] == binding["scope"]
                        and row.get("margin_degrees") == 0.001
                        and row.get("geometry_input_sha256") == "04b9d0ed2ad8d28e2947bb9fde88cbe39dd7f37c666940ab48869fdb3939750f",
                        "Original row spatial evidence differs")
                counts["raw_source_rows"] += 1
                counts[projection["decision"]] += 1
                counts["raw_null_countries"] += row.get("country_tag") is None
                connection.execute("INSERT INTO candidate_raw_records VALUES (?,?,?,?,?)", (retained["record_key"], retained["corpus_kind"], retained["ordinal"], retained["original_line"], retained["line_sha256"]))
                if values:
                    require(values[0] not in expected, "Stable-ID collision; no merges or overwritten rows")
                    key = row["source_id"], values[11]
                    require(key not in source_keys, "Duplicate source/base identity; no implicit merge")
                    source_keys.add(key)
                    expected[values[0]] = values
                    connection.execute("INSERT INTO businesses VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", values)
                    connection.execute("INSERT INTO business_search(docid,name,city,category,phone) VALUES (?,?,?,?,?)",
                        (values[0], core.search_text(values[1]), core.search_text(values[4]), core.search_text(values[5]), values[2]))
                    counts["candidate_null_countries"] += row.get("country_tag") is None
                connection.execute("INSERT INTO candidate_decisions VALUES (?,?,?,0)", (retained["record_key"], projection["decision"], values[0] if values else None))
                ledgers.append(dict(record_key=retained["record_key"], corpus_kind=retained["corpus_kind"], ordinal=retained["ordinal"],
                    original_line_sha256=retained["line_sha256"], decision=projection["decision"], candidate_business_id=values[0] if values else None,
                    source_id=row["source_id"], source_phone=row["phone"], original_phone=row["original_phone"], extension=row["extension"],
                    original_country=row.get("country_tag"), original_country_fields=row["source_country_fields"], original_scope=row["geographic_scope"],
                    source_updated_at=row["source_updated_at"], source_version=row.get("source_version"),
                    source_version_absent_in_raw="source_version" not in row, geometry_evidence=binding,
                    app_admitted=False, production_admission=False))
            require(counts["raw_source_rows"] == 2728 and len(expected) == 2715, "Measured candidate cohort differs")
            connection.executemany("INSERT INTO metadata VALUES (?,?)", (("dataset_version", version), ("candidate_status", STATUS)))
            connection.commit()
            verify_candidate_database(connection, expected, len(expected))
            connection.execute("VACUUM")
        finally:
            original.close()
            connection.close()
        require(database_path.stat().st_size <= core.MAX_DATABASE_BYTES, "Candidate database exceeds reader cap")
        reader_manifest = dict(format="DA_CALLER_BUSINESSES", schema_version=1, dataset_version=version,
            database_sha256=digest(database_path), record_count=len(expected), source_name="OpenStreetMap owner-upload source-geometry candidate; NOT ADMITTED",
            source_url=core.SOURCE_PAGE, license="ODbL-1.0", license_url=core.LICENSE_URL, updated_at="2026-11-03")
        validate_reader_manifest(reader_manifest)
        write_json(temp / "candidate-reader-manifest.json", reader_manifest)
        with (temp / "candidate-decisions.ndjson").open("w", encoding="utf-8") as handle:
            for item in ledgers:
                handle.write(json.dumps(item, ensure_ascii=True, sort_keys=True) + "\n")
        evidence_output = temp / "bound-evidence"
        evidence_output.mkdir()
        for path in dict.fromkeys(snapshot_paths):
            if path in (pbf, archive, research_db, prior / "research-search.sqlite"):
                continue
            # Numbered names avoid collisions between original manifest/proofs.
            shutil.copyfile(path, evidence_output / (f"{len(list(evidence_output.iterdir())):03d}-" + path.name))
        write_json(temp / "input-binding.json", {str(path): sha for path, sha in snapshots.items()})
        write_json(temp / "expanded-research-replay.json", replay)
        counts.update(candidate_rows=len(expected), candidate_source_objects=len({r[8] for r in expected.values()}),
                      candidate_distinct_normalized_phones=len({r[11] for r in expected.values()}), app_records_added=0,
                      source_mutations=0, source_rows_merged=0)
        envelope = dict(status=STATUS, production_admission=False, native_integration="NOT_TESTED", publication_approval=False,
            app_records_added=0, current_apk_changed=False, candidate_only_source_spatial_contract=True,
            candidate_contract="Identity-unchanged, exact safe source display, full source geometry minus filled WB/Gaza; not explicit source-country admission",
            observed_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(), counts=dict(sorted(counts.items())),
            database_file=database_path.name, database_sha256=reader_manifest["database_sha256"], database_bytes=database_path.stat().st_size,
            reader_manifest_sha256=digest(temp / "candidate-reader-manifest.json"), decision_ledger_sha256=digest(temp / "candidate-decisions.ndjson"),
            builder_sha256=snapshots[Path(__file__)], acquisition=acquisition, publisher_provenance_independently_verified=False,
            source_pbf_sha256=PBF_SHA, reviewed_research_database_sha256=RESEARCH_SHA,
            new_source_pbf_objects_reread=False, original_source_audits_hash_bound=True, retained_zip_crc_rechecked=True,
            source_geometry_provenance=json.loads((evidence / "node-geography-report.json").read_text())["geometry_provenance"],
            limits=["Not copied to app assets; source-country/production/signing/CI guards unchanged.",
                    "Reader schema is loadable if manually copied; candidate fields are not a runtime security boundary.",
                    "Null source countries remain null. Source geometry is not an official/legal country determination.",
                    "Source/node version absent in original research stays absent, never invented.",
                    "Numbering metadata does not verify phone ownership, activity, reachability or present-day business status.",
                    "No national coverage percentage, million-record, Android/native/device or publication claim."])
        write_json(temp / "candidate-review-envelope.json", envelope)
        (temp / "LICENSE-NOTICE.txt").write_text(
            "© OpenStreetMap contributors\nODbL1.0 https://opendatacommons.org/licenses/odbl/1-0/\n"
            "Derived candidate database and exact original raw lines/decision ledger retained for redistribution.\n"
            "Owner-upload snapshot 2026-11-03; publisher authentication false; phone ownership/currentness not verified.\n"
            "Candidate only, NOT ADMITTED. Do not copy into app assets or publish as a verified APK dataset.\n", encoding="utf-8")
        require_unchanged(snapshots)
        temp.rename(output)
    return envelope


def write_json(path: Path, value):
    path.write_text(json.dumps(value, ensure_ascii=True, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for field in ("pbf", "research_db", "manifest", "evidence", "output"):
        parser.add_argument(field, type=Path)
    parser.add_argument("--version", default="owner-261103-source-geometry-candidate-v1")
    args = parser.parse_args()
    result = build(args.pbf, args.research_db, args.manifest, args.evidence, args.output, args.version)
    print(json.dumps({key: result[key] for key in ("status", "counts", "database_sha256", "app_records_added")}))


if __name__ == "__main__":
    main()
