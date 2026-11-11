#!/usr/bin/env python3
"""Independent retained-source audit of an outside-app business candidate.

Never imports the candidate builder or uses its projection/selection methods.
Earlier source-cohort audit and phone-number metadata/production normalizer are
explicit shared references. No PBF object reread, Android/emulator execution,
production admission or publication approval is established by this audit.
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
import unicodedata

import phonenumbers
import audit_research_sqlite as reference
import audit_expanded_research_sqlite as expanded_reference

HERE = Path(__file__).resolve().parent
EXPECTED_EXPANDED_SHA = "1a015426c33835a247198120bd05be026f4f0c25aa874d6c506cc7f07e1ad255"
EXPECTED_PBF_SHA = "62c6ff161b738588454719f0fd1c4971c9b46535f4ab04313e7bbe1dd582afdf"
EXPECTED_EXPANDED_AUDIT_SHA = "eb60bc3f97c039cf09ff4397127a70b484ab0af15f95f4d9d61a63b5ea8d1425"
BUSINESS_COLUMNS = ("id", "name", "phone", "phone_display", "city", "category", "address",
                    "source_url", "source_id", "source_license", "source_updated_at", "normalized_phone")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read_only(path):
    connection = sqlite3.connect(Path(path).resolve().as_uri() + "?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    return connection


def independent_projection(source, normalizer):
    """Recompute candidate values directly from an unmodified source record.

    Phone parsing and current Android-compatible normalizer are disclosed
    reference contracts; no candidate or staging insertion/projection is called.
    """
    if source.get("extension"):
        return "WITHHELD_EXTENSION", None
    if reference.text_withheld(source):
        return "WITHHELD_SOURCE_TEXT", None
    status, national = reference.phone_projection(source, normalizer)
    if status != "IDENTITY_UNCHANGED":
        return "WITHHELD_PHONE_FORMAT_REVIEW", None
    name = source["name"]
    if len(name.encode("utf-16-le")) // 2 > 300:
        return "WITHHELD_UTF16_NAME", None
    if any(unicodedata.normalize("NFC", source.get(field, "")).strip() != source.get(field, "")
           for field in reference.TEXT_BOUNDS):
        return "WITHHELD_SOURCE_NORMALIZATION", None
    source_id = source["source_id"]
    match = re.fullmatch(r"osm:(node|way|relation):([1-9][0-9]*)", source_id)
    require(bool(match), "Original source identity invalid")
    require(source["source_url"] == f"https://www.openstreetmap.org/{match[1]}/{match[2]}",
            "Original source URL differs from identity")
    stamp = dt.datetime.fromisoformat(source["source_updated_at"].replace("Z", "+00:00"))
    require(stamp.tzinfo is not None, "Source date lacks a timezone")
    require(source["license_id"] == "ODbL-1.0"
            and source["status"] == "RESEARCH_ONLY_NOT_APP_ACCEPTED"
            and source["verification"] == "source-listed-not-phone-confirmed", "Source provenance changed")
    require(normalizer(source["phone"]) == national, "Current native dial identity differs")
    identity = source_id + ":" + national
    stable = int.from_bytes(hashlib.sha256(identity.encode()).digest()[:8], "big") & ((1 << 63) - 1)
    require(stable > 0, "Stable identity is zero")
    return "PROJECTED_FOR_REVIEW_NOT_ADMITTED", (stable, name, source["phone"], source["phone"],
        source["city"], source["category"], source["address"], source["source_url"], source_id,
        "ODbL-1.0", source["source_updated_at"], national)


def validate_candidate_schema(connection, manifest):
    require(connection.execute("PRAGMA user_version").fetchone()[0] == 1,
            "Candidate schema version is not native-compatible1")
    names = tuple(row[1] for row in connection.execute("PRAGMA table_info(businesses)"))
    require(names == BUSINESS_COLUMNS, "Candidate native columns differ")
    require(connection.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
            and not connection.execute("PRAGMA foreign_key_check").fetchall(), "Candidate integrity/FK failed")
    sql = connection.execute("SELECT sql FROM sqlite_master WHERE name='business_search'").fetchone()
    require(sql is not None and "using fts4" in re.sub(r"\s+", " ", sql[0].lower()), "Candidate index is not FTS4")
    count = connection.execute("SELECT count(*) FROM businesses").fetchone()[0]
    require(type(manifest["record_count"]) is int and count == manifest["record_count"], "Candidate count differs")
    require(connection.execute("SELECT value FROM metadata WHERE key='dataset_version'").fetchone()[0]
            == manifest["dataset_version"], "Candidate plain-string dataset version differs")
    require(connection.execute("SELECT count(*) FROM business_search").fetchone()[0] == count,
            "Candidate FTS row count differs")
    require(not connection.execute("SELECT b.id FROM businesses b LEFT JOIN business_search f ON f.docid=b.id "
                                  "WHERE f.docid IS NULL LIMIT 1").fetchall(), "Candidate FTS identity missing")
    require(not connection.execute("SELECT f.docid FROM business_search f LEFT JOIN businesses b ON f.docid=b.id "
                                  "WHERE b.id IS NULL LIMIT 1").fetchall(), "Candidate phantom FTS identity")
    return count


def validate_manifest(manifest, database_path):
    require(manifest.get("format") == "DA_CALLER_BUSINESSES"
            and type(manifest.get("schema_version")) is int and manifest["schema_version"] == 1,
            "Candidate inner manifest format/version differs from reader contract")
    require(type(manifest.get("record_count")) is int and 0 <= manifest["record_count"] <= 2_000_000,
            "Candidate manifest record count must be an actual bounded integer")
    require(isinstance(manifest.get("dataset_version"), str)
            and re.fullmatch(r"[A-Za-z0-9._-]{1,80}", manifest["dataset_version"]), "Candidate version invalid")
    require(manifest.get("database_sha256") == digest(database_path)
            and 0 < database_path.stat().st_size <= 512 * 1024 * 1024, "Candidate byte hash/size differs")
    for field, maximum in (("source_name", 200), ("source_url", 2048), ("license", 200),
                           ("license_url", 2048), ("updated_at", 80)):
        value = manifest.get(field)
        require(isinstance(value, str) and value.strip() and "\0" not in value
                and len(value.encode("utf-16-le")) // 2 <= maximum,
                "Candidate reader manifest field invalid: " + field)
    from urllib.parse import urlsplit
    for field in ("source_url", "license_url"):
        parsed = urlsplit(manifest[field])
        require(parsed.scheme.lower() == "https" and parsed.hostname and parsed.username is None,
                "Candidate reader URL is not HTTPS/no-userinfo")
    require(manifest["license"] == "ODbL-1.0"
            and manifest["license_url"] == "https://opendatacommons.org/licenses/odbl/1-0/",
            "Candidate declared source license changed")


def reconstruct_sources(expanded_directory, evidence, pbf):
    db_path = expanded_directory / "research-search.sqlite"
    manifest_path = expanded_directory / "manifest.json"
    audit_path = evidence / "expanded-research-independent-audit.json"
    require(digest(db_path) == EXPECTED_EXPANDED_SHA, "Retained independently audited expanded DB changed")
    require(digest(audit_path) == EXPECTED_EXPANDED_AUDIT_SHA,
            "Retained expanded independent audit changed")
    manifest, audit = json.loads(manifest_path.read_text()), json.loads(audit_path.read_text())
    require(audit["database_sha256"] == manifest["database_sha256"] == EXPECTED_EXPANDED_SHA
            and audit["manifest_sha256"] == digest(manifest_path)
            and manifest["source_pbf_sha256"] == EXPECTED_PBF_SHA == digest(pbf)
            and manifest["source_pbf_bytes"] == pbf.stat().st_size,
            "Expanded input/audit/PBF binding failed")
    require(audit["production_admission"] is False and audit["publication_approval"] is False
            and type(audit["app_records_added"]) is int and audit["app_records_added"] == 0,
            "Retained expanded audit did not remain research-only")
    code_paths = expanded_reference.code_paths(evidence)
    require({name: digest(path) for name, path in code_paths.items()} == manifest["code_sha256"],
            "Retained cohort/reference/normalizer code changed")
    snapshots = {db_path: EXPECTED_EXPANDED_SHA, manifest_path: digest(manifest_path),
                 audit_path: EXPECTED_EXPANDED_AUDIT_SHA, pbf: EXPECTED_PBF_SHA,
                 **{path: digest(path) for path in code_paths.values()}}
    require(len(manifest["cohorts"]) == 5
            and {cohort["kind"] for cohort in manifest["cohorts"]} == set(expanded_reference.FAMILIES),
            "Exactly five distinct audited source families required")
    expected = {}
    for cohort in manifest["cohorts"]:
        kind = cohort["kind"]
        physical, report_name, audit_name, audit_status = expanded_reference.FAMILIES[kind]
        corpus_path = Path(cohort["corpus_path"])
        report_path, source_audit_path = evidence / report_name, evidence / audit_name
        require(digest(corpus_path) == cohort["corpus_sha256"]
                and digest(report_path) == cohort["report_sha256"]
                and digest(source_audit_path) == cohort["audit_sha256"], "Cohort source/audit bytes differ")
        source_audit, report = json.loads(source_audit_path.read_text()), json.loads(report_path.read_text())
        expanded_reference.require_zero_aliases(source_audit)
        require(source_audit["status"] == cohort["audit_status"] == audit_status
                and report["scope"] == cohort["scope"]
                and report["source_pbf_sha256"] == EXPECTED_PBF_SHA,
                "Cohort original scope/spatial audit status differs")
        for name, sha in report["geometry_provenance"]["input_sha256"].items():
            geometry_path = pbf.parent / name
            require(digest(geometry_path) == sha, "Original spatial inclusion/exclusion input differs")
            snapshots[geometry_path] = sha
        lines = corpus_path.read_bytes().splitlines(keepends=True)
        require(len(lines) == cohort["rows"], "Original cohort line count changed")
        for ordinal, line in enumerate(lines):
            source = json.loads(line)
            require(re.fullmatch(r"osm:" + physical + r":[1-9][0-9]*", source["source_id"])
                    and source["geographic_scope"] == cohort["scope"], "Original source type/scope changed")
            key = hashlib.sha256(json.dumps([source["source_id"], source["phone"], source["extension"]],
                ensure_ascii=True, separators=(",", ":")).encode()).hexdigest()
            require(key not in expected, "Original source/base/extension key duplicated")
            expected[key] = (kind, ordinal, line, source)
        snapshots.update({corpus_path: cohort["corpus_sha256"], report_path: cohort["report_sha256"],
                          source_audit_path: cohort["audit_sha256"]})
    with read_only(db_path) as connection:
        retained = {row["record_key"]: dict(row) for row in connection.execute("SELECT * FROM raw_records")}
        require(set(expected) == set(retained), "Audited expanded DB has lost/invented source rows")
        for key, (kind, ordinal, line, _) in expected.items():
            row = retained[key]
            require(row["corpus_kind"] == kind and row["ordinal"] == ordinal
                    and row["original_line"] == line
                    and row["line_sha256"] == hashlib.sha256(line).hexdigest(),
                    "Expanded raw BLOB does not match exact retained source line")
    return expected, manifest, snapshots


def native_limit(query):
    # Java substring uses UTF-16 code units. These are query forms from the
    # existing reader, not a claim that this is an Android execution.
    return query.encode("utf-16-le", errors="surrogatepass")[:320].decode("utf-16-le", errors="surrogatepass")


def native_trim(query):
    return query.strip("".join(chr(code) for code in range(33)))


def native_expression(query):
    normalized = "".join(char for char in unicodedata.normalize("NFKD", native_limit(query))
                         if not unicodedata.category(char).startswith("M"))
    tokens = "".join(char if unicodedata.category(char)[0] in {"L", "N"} else " "
                     for char in normalized).split()
    return " ".join('"' + token + '*"' for token in tokens[:10])


def native_phone_prefix(query):
    value = native_trim(query).replace("\u00a0", " ")
    if re.fullmatch(r"[+*0-9() .\-]+", value) is None:
        return ""
    value = re.sub(r"[^0-9+*]", "", value)
    for prefix in ("+972", "00972", "972"):
        if value.startswith(prefix):
            value = "0" + value[len(prefix):]
            break
    return value if re.fullmatch(r"[0-9*]{1,16}", value) else ""


def query_page(connection, query, offset=0):
    text = native_limit(native_trim(query))
    prefix, expression = native_phone_prefix(text), native_expression(text)
    if text and prefix:
        where, args = " WHERE normalized_phone LIKE ?", [prefix + "%"]
    elif text and expression:
        where, args = " WHERE id IN (SELECT docid FROM business_search WHERE business_search MATCH ?)", [expression]
    elif text:
        return [], False
    else:
        where, args = "", []
    rows = [row[0] for row in connection.execute("SELECT id FROM businesses" + where
            + " ORDER BY name COLLATE NOCASE,id LIMIT ? OFFSET ?", [*args, 51, max(0, offset)])]
    return rows[:50], len(rows) > 50


def pagination_check(connection, query):
    gathered, pages, offset = [], 0, 0
    while True:
        page, more = query_page(connection, query, offset)
        require(not set(gathered).intersection(page), "Native-form offset query repeated candidate IDs")
        gathered.extend(page)
        pages += 1
        if not more:
            break
        require(len(page) == 50, "Native-form lookahead has a short nonterminal page")
        offset += len(page)
        require(pages < 100, "Native-form offset query failed to terminate")
    return gathered, dict(pages=pages, rows=len(gathered), repeated_or_omitted=0,
                          page_size=50, actual_sql_lookahead=51)


def audit(directory, expanded_directory, evidence, pbf):
    started = time.monotonic()
    expected, input_manifest, snapshots = reconstruct_sources(expanded_directory, evidence, pbf)
    database_path = directory / "candidate-businesses.sqlite"
    manifest_path = directory / "candidate-reader-manifest.json"
    envelope_path = directory / "candidate-review-envelope.json"
    ledger_path = directory / "candidate-decisions.ndjson"
    binding_path = directory / "input-binding.json"
    envelope, manifest = json.loads(envelope_path.read_text()), json.loads(manifest_path.read_text())
    require(envelope["status"] == "CANDIDATE_FOR_REVIEW_NOT_ADMITTED"
            and envelope["production_admission"] is False and envelope["publication_approval"] is False
            and envelope["native_integration"] == "NOT_TESTED"
            and type(envelope["app_records_added"]) is int and envelope["app_records_added"] == 0
            and envelope["current_apk_changed"] is False,
            "Candidate envelope is not explicitly outside-app/non-admitted/non-native")
    require(envelope["database_file"] == database_path.name
            and envelope["database_sha256"] == digest(database_path)
            and type(envelope["database_bytes"]) is int and envelope["database_bytes"] == database_path.stat().st_size
            and envelope["reader_manifest_sha256"] == digest(manifest_path)
            and envelope["decision_ledger_sha256"] == digest(ledger_path)
            and envelope["builder_sha256"] == digest(HERE / "prepare_business_candidate.py")
            and envelope["source_pbf_sha256"] == EXPECTED_PBF_SHA
            and envelope["reviewed_research_database_sha256"] == EXPECTED_EXPANDED_SHA,
            "Candidate byte/code/input envelope binding differs")
    require(not (directory / "app-assets").exists() and not (directory / "business-directory").exists()
            and not (directory / "manifest.json").exists(), "Candidate must not emit an app asset layout")
    validate_manifest(manifest, database_path)
    notice = (directory / "LICENSE-NOTICE.txt").read_text()
    require("OpenStreetMap contributors" in notice
            and "https://opendatacommons.org/licenses/odbl/1-0/" in notice,
            "Candidate source attribution/license notice missing")
    acquisition = envelope["acquisition"]
    receipt_path = pbf.parent / "owner-upload-receipt.json"
    receipt = json.loads(receipt_path.read_text())
    require(envelope["publisher_provenance_independently_verified"] is False
            and acquisition["publisher_provenance_independently_verified"] is False
            and acquisition["production_admission"] is False
            and acquisition["method"] == "owner-upload"
            and type(acquisition["asset_id"]) is int and acquisition["asset_id"] == receipt["asset_id"] == 712722687
            and acquisition["source_sha256"] == receipt["pbf_sha256"] == EXPECTED_PBF_SHA
            and acquisition["archive_sha256"] == receipt["sha256"]
            and acquisition["url"] == receipt["url"]
            and not any(key in acquisition for key in ("http_status", "geographic_scope", "country_tag")),
            "Candidate owner transfer provenance fabricated publisher/country admission")
    snapshots[receipt_path] = digest(receipt_path)
    original_geometry = json.loads((evidence / "node-geography-report.json").read_text())["geometry_provenance"]
    require(envelope["source_geometry_provenance"] == original_geometry,
            "Candidate full source geometry provenance differs")
    bindings = json.loads(binding_path.read_text())
    require(isinstance(bindings, dict) and bool(bindings), "Candidate input bindings missing")
    for path, sha in bindings.items():
        require(digest(Path(path)) == sha, "Candidate bound input/code/proof changed: " + path)
    resolved_bindings = {Path(path).resolve(): sha for path, sha in bindings.items()}
    for path, sha in snapshots.items():
        require(resolved_bindings.get(path.resolve()) == sha, "Expected original input is absent from candidate bindings")
    all_retained_files = [path for path in directory.rglob("*") if path.is_file()]
    snapshots.update({path: digest(path) for path in all_retained_files})
    snapshots[HERE / "prepare_business_candidate.py"] = envelope["builder_sha256"]
    snapshots[Path(__file__).resolve()] = digest(Path(__file__))
    core_path = HERE.parent / "package_app_dataset.py"
    core = reference.load("candidate_independent_existing_core_reference", core_path)
    ledger_rows = [json.loads(line) for line in ledger_path.read_text().splitlines()]
    ledger = {row["record_key"]: row for row in ledger_rows}
    require(len(ledger_rows) == len(ledger) == len(expected) and set(ledger) == set(expected),
            "Candidate decision ledger has repeated/lost/invented source rows")
    checks, dispositions, strict_rejections = collections.Counter(), collections.Counter(), collections.Counter()
    expected_businesses, expected_fts = {}, {}
    candidate_nulls = 0
    source_countries = collections.Counter()
    with read_only(database_path) as connection:
        count = validate_candidate_schema(connection, manifest)
        raw = {row["record_key"]: dict(row) for row in connection.execute("SELECT * FROM candidate_raw_records")}
        decisions = {row["record_key"]: dict(row) for row in connection.execute("SELECT * FROM candidate_decisions")}
        require(set(raw) == set(decisions) == set(expected), "Candidate raw/decision keys do not retain all originals")
        for key, (kind, ordinal, line, source) in expected.items():
            retained, decision, log = raw[key], decisions[key], ledger[key]
            require(retained["corpus_kind"] == kind and retained["ordinal"] == ordinal
                    and retained["original_line"] == line
                    and retained["line_sha"] == hashlib.sha256(line).hexdigest(), "Candidate raw BLOB/source family changed")
            checks["exact_original_raw_blob_line_hash_family_ordinal"] += 1
            state, values = independent_projection(source, core.normalized_phone)
            require(decision["decision"] == state
                    and decision["candidate_business_id"] == (values[0] if values else None)
                    and type(decision["app_admitted"]) is int and decision["app_admitted"] == 0,
                    "Independent candidate disposition differs")
            fields = dict(record_key=key, corpus_kind=kind, ordinal=ordinal,
                original_line_sha256=hashlib.sha256(line).hexdigest(), decision=state,
                candidate_business_id=values[0] if values else None, source_id=source["source_id"],
                source_phone=source["phone"], original_phone=source["original_phone"], extension=source["extension"],
                original_country=source.get("country_tag"), original_country_fields=source["source_country_fields"],
                original_scope=source["geographic_scope"], source_updated_at=source["source_updated_at"],
                source_version=source.get("source_version"), source_version_absent_in_raw="source_version" not in source)
            require(all(log.get(field) == value for field, value in fields.items())
                    and log["app_admitted"] is False and log["production_admission"] is False,
                    "Candidate ledger altered source country/phone/scope/version/decision")
            binding = next(item for item in input_manifest["cohorts"] if item["kind"] == kind)
            require(log["geometry_evidence"] == binding, "Candidate row spatial provenance changed family/audit")
            checks["exact_source_identity_display_dial_country_scope_provenance_ledger"] += 1
            checks["independent_candidate_disposition"] += 1
            dispositions[state] += 1
            source_countries["null" if source.get("country_tag") is None else source["country_tag"]] += 1
            try:
                core.record_values(source)
            except ValueError as error:
                strict_rejections[str(error)] += 1
            else:
                raise ValueError("Original research unexpectedly passes unchanged production admission")
            checks["unchanged_strict_production_rejection"] += 1
            if values:
                require(values[0] not in expected_businesses, "Stable candidate identity collision")
                expected_businesses[values[0]] = values
                def index_text(value):
                    return "".join(char for char in unicodedata.normalize("NFKD", value)
                                   if not unicodedata.category(char).startswith("M"))
                expected_fts[values[0]] = (index_text(values[1]), index_text(values[4]), index_text(values[5]), values[2])
                candidate_nulls += source.get("country_tag") is None
        actual_businesses = {row[0]: tuple(row) for row in connection.execute("SELECT * FROM businesses")}
        actual_fts = {row[0]: tuple(row[1:]) for row in connection.execute("SELECT docid,name,city,category,phone FROM business_search")}
        require(actual_businesses == expected_businesses and len(actual_businesses) == count,
                "Candidate row values/count differ from independent original-source projection")
        require(actual_fts == expected_fts, "Candidate FTS docid/value mapping differs or contains phantom/missing entries")
        checks["independent_exact_twelve_column_projection_and_stable_identity"] = len(expected_businesses)
        checks["independent_exact_fts4_docid_and_search_values"] = len(expected_fts)
        for cohort in input_manifest["cohorts"]:
            kind = cohort["kind"]
            reconstructed = b"".join(raw[key]["original_line"] for key in sorted(raw, key=lambda key: raw[key]["ordinal"])
                                      if raw[key]["corpus_kind"] == kind)
            require(reconstructed == Path(cohort["corpus_path"]).read_bytes(), "Candidate raw lines do not reconstruct original cohort")
        checks["byte_exact_reconstructed_source_cohorts"] = 5
        require(len({(row[8], row[11]) for row in expected_businesses.values()}) == count,
                "Same original source/base identity appears twice")
        gathered, all_pages = pagination_check(connection, "")
        expected_order = [row[0] for row in connection.execute("SELECT id FROM businesses ORDER BY name COLLATE NOCASE,id")]
        require(gathered == expected_order, "Native-form empty-list pagination omitted/reordered source rows")
        queries = {"all_rows_offset_pagination": all_pages}
        for value in ("shop", "fitness"):
            gathered, query_result = pagination_check(connection, value)
            expected_ids = [row[0] for row in connection.execute("SELECT id FROM businesses WHERE id IN "
                "(SELECT docid FROM business_search WHERE business_search MATCH ?) ORDER BY name COLLATE NOCASE,id",
                (native_expression(value),))]
            require(gathered == expected_ids, "Native-form actual FTS pagination omitted/reordered source rows")
            queries[value + "_offset_pagination"] = query_result
        example = next(iter(expected_businesses.values()))
        for label, value in (("national_prefix", example[11]), ("e164_prefix", example[2])):
            rows, more = query_page(connection, value)
            require(example[0] in rows, "Known original phone absent from native-form query")
            queries[label] = dict(query=value, returned=len(rows), has_more=more, known_source_found=True)
        for label, pattern in (("hebrew", r"[א-ת]{2,}"), ("english", r"[A-Za-z]{3,}")):
            entry = next(value for value in expected_businesses.values() if re.search(pattern, value[1]))
            word = re.search(pattern, entry[1]).group(0)
            gathered, _ = pagination_check(connection, word)
            require(entry[0] in gathered, "Known original name absent from native-form token query")
            queries[label + "_name"] = dict(query=word, returned=len(gathered), known_source_found=True)
            if label == "hebrew":
                pointed = word[0] + "\u05b6" + word[1:]
                require(pagination_check(connection, pointed)[0] == gathered, "Hebrew niqqud query changes native-form matches")
                queries["hebrew_with_niqqud"] = dict(query=pointed, same_matches_as_unpointed=True)
        require(query_page(connection, '"()---')[0] == [], "Punctuation-only query unexpectedly matched")
        queries["literal_punctuation_only"] = "PASS"
        shared = connection.execute("SELECT count(*) FROM (SELECT normalized_phone FROM businesses "
                                    "GROUP BY normalized_phone HAVING count(*)>1)").fetchone()[0]
        with sqlite3.connect(":memory:") as clone:
            connection.backup(clone)
            clone.execute("UPDATE candidate_decisions SET app_admitted=0")
            try:
                clone.execute("UPDATE candidate_decisions SET app_admitted=1")
            except sqlite3.IntegrityError:
                pass
            else:
                raise ValueError("Candidate app-admission CHECK guard missing")
        checks["in_memory_candidate_nonadmission_check_rejection"] = 1
        for label, sql in (
            ("missing_fts", "DELETE FROM business_search WHERE docid=(SELECT id FROM businesses LIMIT 1)"),
            ("extra_fts", "INSERT INTO business_search(docid,name,city,category,phone) VALUES(-1,'phantom','','','')"),
            ("same_count_mapped_identity", "UPDATE business_search SET docid=-1 WHERE docid=(SELECT id FROM businesses LIMIT 1)"),
            ("schema_version", "PRAGMA user_version=2"),
            ("dataset_version", "UPDATE metadata SET value='invalid-reviewed-version' WHERE key='dataset_version'")):
            with sqlite3.connect(":memory:") as clone:
                connection.backup(clone)
                clone.execute(sql)
                try:
                    validate_candidate_schema(clone, manifest)
                except ValueError:
                    pass
                else:
                    raise ValueError("Corrupted candidate unexpectedly accepted: " + label)
            checks["in_memory_rejected_" + label] = 1
    measured = dict(raw_source_rows=len(expected), candidate_rows=len(expected_businesses),
        candidate_source_objects=len({row[8] for row in expected_businesses.values()}),
        candidate_distinct_normalized_phones=len({row[11] for row in expected_businesses.values()}),
        candidate_null_countries=candidate_nulls, raw_null_countries=source_countries["null"],
        app_records_added=0, source_mutations=0, source_rows_merged=0, **dict(dispositions))
    require(envelope["counts"] == dict(sorted(measured.items()))
            and all(type(value) is int for value in envelope["counts"].values()), "Candidate measured counters differ/are non-integer")
    require(len(expected) == 2728 and len(expected_businesses) == 2715
            and dispositions["WITHHELD_PHONE_FORMAT_REVIEW"] == 10
            and dispositions["WITHHELD_SOURCE_TEXT"] == 3, "Candidate differs from reviewed source cohort")
    require(all(digest(path) == sha for path, sha in snapshots.items()), "Read-only audit/query changed source/code/candidate files")
    return dict(status="PASS_OUTSIDE_APP_CANDIDATE_SOURCE_PROJECTION_AND_HOST_SQL_ONLY",
        observed_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(), elapsed_seconds=round(time.monotonic()-started, 3),
        candidate_database_sha256=digest(database_path), candidate_database_bytes=database_path.stat().st_size,
        candidate_manifest_sha256=digest(manifest_path), candidate_envelope_sha256=digest(envelope_path),
        candidate_ledger_sha256=digest(ledger_path), reviewed_builder_sha256=envelope["builder_sha256"],
        audit_script_sha256=digest(Path(__file__)), reference_production_normalizer_sha256=digest(core_path),
        expanded_input_database_sha256=EXPECTED_EXPANDED_SHA, retained_expanded_audit_sha256=EXPECTED_EXPANDED_AUDIT_SHA,
        retained_pbf_sha256=EXPECTED_PBF_SHA, original_pbf_objects_reread=False,
        checks_passed=dict(sorted(checks.items())), measured_counts=measured,
        original_strict_production_rejection_counts=dict(sorted(strict_rejections.items())),
        actual_queries=queries, shared_phone_groups_retained=shared, readonly_hashes_unchanged=True,
        app_records_added=0, production_admission=False, native_integration="NOT_TESTED", publication_approval=False,
        limits=["Independent source projection never calls candidate producer/selection methods.",
                "The earlier independent text/phone audit, numbering metadata and unchanged production normalizer are shared reference contracts.",
                "All original raw lines/cohort bytes and retained source-spatial audit references were checked; original PBF objects were not reread.",
                "Actual desktop SQLite/FTS4 queries mimic native implicit-MATCH and50+1 offset forms; this is not Android/emulator/UI/dial verification.",
                "Null countries/scopes and separate shared-phone source objects remain unchanged; no source ownership/currentness/national coverage guarantee.",
                "This candidate remains outside-app, not production-admitted and not an approved APK/release dataset."])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("directory", "expanded_directory", "evidence", "pbf", "output"):
        parser.add_argument(name, type=Path)
    args = parser.parse_args()
    require(not args.output.exists(), "Independent audit output exists; select a new path")
    result = audit(args.directory, args.expanded_directory, args.evidence, args.pbf)
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, ensure_ascii=True, indent=2)
        stream.write("\n")
    print(json.dumps({key: result[key] for key in ("status", "candidate_database_sha256", "measured_counts", "app_records_added")}))


if __name__ == "__main__":
    main()
