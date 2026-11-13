#!/usr/bin/env python3
"""Build a NEW, bounded source-spatial directory outside the application.

This deliberately does not call the old explicit-country record projector. The
original raw/candidate data, missing countries and old decisions remain intact.
Fresh PBF geometry/tags, the reviewed policy and a separately versioned business
phone contract determine new decisions. A result is native-evaluation input,
never an APK, publication permission or live phone-ownership assertion.
"""
from __future__ import annotations

import argparse
import collections
import ctypes
import datetime as dt
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import sys
import tempfile
import unicodedata
import zipfile

import osmium
import phonenumbers

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
RESEARCH = HERE / "research"
POLICY_SHA = "c421cf24a6442f31b991037a45883234b8570e2ad82dae6cb8f673d2523e4d2c"
PBF_SHA = "62c6ff161b738588454719f0fd1c4971c9b46535f4ab04313e7bbe1dd582afdf"
PBF_BYTES = 121050024
ZIP_SHA = "578d67213db0cb617f18ccd12c60eab5c532f073fa5520b399d9f5d36973b11e"
REPORT_SHA = "e04848419c58d79e9d3e5eeab9c3e778cfa3f223554966f374625d4bd3a299fc"
SOURCE_PAGE = "https://download.geofabrik.de/asia/israel-and-palestine.html"
LICENSE_URL = "https://opendatacommons.org/licenses/odbl/1-0/"
ATTRIBUTION = "© OpenStreetMap contributors"
SOURCE_NAME = "OpenStreetMap — " + ATTRIBUTION
REDISTRIBUTION_PAGE = "https://github.com/hadar1233hr-afk/D.A.-Caller/blob/codex/da-caller-dynamic-island-20261027/docs/evidence/business-acquisition/owner-261103/verified-geometry-README.md"
STATUS = "VERIFIED_FOR_NATIVE_EVALUATION_NOT_PUBLISHED"
COUNTRY_KEYS = ("addr:country", "country", "is_in:country_code", "is_in:country")
IL = {"IL", "ISR", "ISRAEL", "ישראל"}
NAMES = {
    "osm:node:5801536067": ("ee14d01f178fbf01f8c69ecf2260d819f4f74eecc2f3dc89914e4b58677b1cf3", "دار كنا الضيافة"),
    "osm:node:12543641901": ("47ab06491d476194656605194af2ad9b0582d829cd069eaeee632592a9b907d7", "R. Gallery Ltd"),
}
ANCHORS = {
    "candidate-businesses.sqlite": "36a7c0a07fad679249c44c975cba7c8d4fb00171d593d19f4ff0c6413a0f115a",
    "candidate-reader-manifest.json": "de06f2770c48e4da65966c1dda7a8fd4e1af8596698f3537652259827bcdebe5",
    "candidate-review-envelope.json": "ecea7fe5eab66f9ab4007784092c08a8cd7cbe50772d7737d95e5b493ed72d72",
    "candidate-decisions.ndjson": "18303b1c9e46f49adddcb6ba3323f64e86ce0c8ec5f691f38c61d34ce72421de",
    "candidate-source-name-selections.ndjson": "9e731705145540e0ff03d9fba508a7542002d8ed065347d676df5ed0b53868cb",
    "input-binding.json": "b0533ce259fc71ffd0959c05314f479b84cc2b7cf8bb7844d41c0e51dca1450a",
    "source-name-input-binding.json": "529180df6f52d69b587a6b5329cd1b3eee822d40c8d74b6699e5d6e52ae78f4a",
}
COLUMNS = ("id", "name", "phone", "phone_display", "city", "category", "address",
           "source_url", "source_id", "source_license", "source_updated_at", "normalized_phone")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n")


def write_lines(path, rows):
    with path.open("w") as stream:
        for row in rows:
            stream.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def read_only(path):
    db = sqlite3.connect(Path(path).resolve().as_uri() + "?mode=ro", uri=True)
    db.row_factory = sqlite3.Row
    return db


def unchanged(snapshots):
    for path, expected in snapshots.items():
        require(digest(path) == expected, "Immutable input drift: " + str(path))


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    require(spec is not None and spec.loader is not None, "Missing dependency module")
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def load_policy(path):
    require(digest(path) == POLICY_SHA, "Reviewed admission policy checksum differs")
    text = path.read_text()
    match = re.search(r"BEGIN_ADMISSION_POLICY_JSON -->\s*```json\s*(.*?)\s*```\s*<!-- END_ADMISSION_POLICY_JSON", text, re.S)
    require(match is not None, "Missing machine-readable admission policy")
    policy = json.loads(match[1])
    required = {
        "policy_id": "da-caller-owner-261103-source-spatial-v1",
        "review_status": "CONDITIONAL_GO_FOR_OUTSIDE_APP_NATIVE_EVALUATION",
        "source_pbf_sha256": PBF_SHA, "source_pbf_bytes": PBF_BYTES,
        "bounded_raw_rows": 2728, "bounded_source_objects": 2668,
        "admission_scope": "source-il-interior-minus-filled-wb-gaza-angular-margin",
        "inside_relation": 1473946, "filled_excluded_relations": [1803010, 12331025, 1473938],
        "margin_lonlat_degrees": 0.001, "require_complete_geometry": True,
        "require_fresh_pbf_tags": True, "preserve_raw_country_nulls": True,
        "preserve_prior_country_exclusions": True, "allow_new_source_objects": False,
        "category_policy": "unchanged-reviewed-core-plus-reviewed-eight-extras",
        "flag_normalization": "strip-casefold-for-decisions-only",
        "hold_any_nonempty_access_conditional": True,
        "hold_any_nonempty_lifecycle_prefixed_tag": True,
        "private_ownership_alone_is_private_access": False,
        "require_native_python_phone_v2_roundtrip": True,
        "maximum_possible_projected_rows": 2727, "force_expected_row_count": False,
        "retain_every_raw_row_and_old_decision": True,
        "license_id": "ODbL-1.0", "attribution": ATTRIBUTION,
        "ready_state_before_native_verification": "READY_FOR_NATIVE_VALIDATION_ONLY",
        "authorizes_publication_or_signing_exception": False,
    }
    for key, value in required.items():
        require(type(policy.get(key)) is type(value) and policy[key] == value,
                "Admission policy contract differs: " + key)
    require(set(policy["allowed_exact_name_fallback_ids"]) == set(NAMES)
            and policy["always_hold_unsafe_city_address_ids"] == ["osm:node:5066707334"],
            "Reviewed text selection set differs")
    return policy


def bind_inputs(candidate, evidence, pbf, policy_path, java):
    """Snapshot before reading or executing dependencies, with ONE old-code alias."""
    snapshots, historical = {}, []
    def bind(path, expected=None):
        path = Path(path).resolve()
        observed = digest(path)
        require(expected is None or observed == expected, "Bound input checksum differs: " + str(path))
        require(snapshots.setdefault(path, observed) == observed, "Contradictory input binding")
    for name, expected in ANCHORS.items():
        bind(candidate / name, expected)
    bind(evidence / "text-readiness-report.json", REPORT_SHA)
    bind(pbf, PBF_SHA)
    require(pbf.stat().st_size == PBF_BYTES, "Original source PBF byte count differs")
    bind(policy_path, POLICY_SHA)
    for binding, count in (("input-binding.json", 49), ("source-name-input-binding.json", 105)):
        values = json.loads((candidate / binding).read_text())
        require(len(values) == count, "Missing historical input bindings")
        for original, expected in values.items():
            require(isinstance(original, str) and re.fullmatch(r"[a-f0-9]{64}", expected) is not None,
                    "Invalid retained input binding")
            source = Path(original)
            if not source.is_absolute():
                source = REPO / source
            if source.resolve() == java.resolve():
                retained = candidate / "bound-evidence/008-BusinessDirectory.java"
                bind(retained, expected)
                event = dict(original_path=original, expected_sha256=expected,
                    verified_historical_path=str(retained),
                    historical_git_source="0d266d63d446b46d3e2a98048b112572d22768fa",
                    reason="Historical reference only; current reader bound independently")
                if event not in historical:
                    historical.append(event)
            else:
                bind(source, expected)
    for path in candidate.rglob("*"):
        if path.is_file():
            bind(path)
    # Independent source proof scripts are unchanged. New code is bound before
    # importing it; it cannot be substituted by a historical native reference.
    for path in (Path(__file__), HERE / "test_package_verified_geometry.py",
                 HERE / "business_phone_v2.py", java):
        bind(path)
    require(snapshots[HERE / "business_phone_v2.py"] == "95d36a035cce09416857925351d40298c9fde9cd3572dd553c6e4b6d93a96f15"
            and snapshots[java] == "5d8c3d01e4aab6f5dd198b4c2fe588e301c7298003192c21ec97332e279efa52",
            "Reviewed current Python/native phone V2 contract changed")
    for name in ("owner-upload-receipt.json", "geography-research-review.json",
                 "research-region-outer-rings.json", "research-region-geometry-inventory.json",
                 "country-relation-inventory.json", "disputed-region-relation-inventory.json"):
        bind(pbf.parent / name)
    bind(pbf.with_suffix(".zip"), ZIP_SHA)
    unchanged(snapshots)
    return snapshots, historical


def source_publicness(tags, policy, category):
    """Unmodified tags in evidence; only flags are trimmed/casefolded here."""
    flag = lambda key: tags.get(key, "").strip().casefold()
    reasons = []
    if flag("access") not in policy["access_allowed"]:
        reasons.append("restricted-or-ambiguous-access")
    if flag("access:conditional"):
        reasons.append("conditional-access")
    if (flag("building") in policy["residential_buildings"]
            or flag("residential") in policy["residential_values_held"]):
        reasons.append("residential-source-object")
    if flag("building") in policy["nonoperating_buildings"]:
        reasons.append("nonoperating-building")
    for prefix in policy["lifecycle_keys"]:
        if flag(prefix) not in policy["lifecycle_allowed_values"]:
            reasons.append("lifecycle-flag:" + prefix)
        if any(key.strip().casefold().startswith(prefix + ":") and value.strip() for key, value in tags.items()):
            reasons.append("lifecycle-prefixed-tag:" + prefix)
    if category.split(":", 1)[-1].strip().casefold() in policy["inactive_category_values"]:
        reasons.append("inactive-category-value")
    return sorted(reasons)


def exact_text(value, maximum, empty=False):
    require(isinstance(value, str), "Non-string source text")
    require(unicodedata.normalize("NFC", value).strip() == value,
            "Source text would require normalization")
    require((empty or bool(value)) and len(value.encode("utf-16-le")) // 2 <= maximum,
            "Source text exceeds native UTF-16 bound or is empty")
    require(not any(unicodedata.category(char) in {"Cc", "Cf", "Cs"} for char in value),
            "Unsafe source text control/surrogate")
    return value


def phone_identity(row, normalizer):
    """Native grammar AND actual IL metadata AND unchanged source round trip."""
    require(phonenumbers.__version__ == "9.0.42", "Unexpected phone metadata version")
    phone = exact_text(row.get("phone"), 80)
    canonical = normalizer(phone)
    require(bool(canonical), "Unsupported native source phone")
    extension = row.get("extension")
    require(isinstance(extension, str) and re.fullmatch(r"[0-9]{0,20}", extension) is not None,
            "Invalid separate source extension")
    require(extension == "", "Native dataset cannot represent a separate phone extension")
    parsed = phonenumbers.parse(phone, "IL")
    require(not parsed.extension and parsed.country_code == 972
            and phonenumbers.region_code_for_number(parsed) == "IL"
            and phonenumbers.is_valid_number_for_region(parsed, "IL"),
            "Source phone is not a valid IL base identity")
    e164 = phonenumbers.format_number(parsed, phonenumbers.PhoneNumberFormat.E164)
    require(row.get("e164") == e164, "Source E.164 identity differs")
    national = phonenumbers.format_number(parsed, phonenumbers.PhoneNumberFormat.NATIONAL)
    roundtrip = phonenumbers.parse(canonical, "IL")
    require(normalizer(national) == canonical
            and phonenumbers.is_valid_number_for_region(roundtrip, "IL")
            and phonenumbers.format_number(roundtrip, phonenumbers.PhoneNumberFormat.E164) == e164,
            "Native national identity changed the source E.164 number")
    require(all(normalizer(alias) == canonical for alias in (e164, "00972" + e164[4:], "972" + e164[4:])),
            "Native international phone aliases disagree")
    return canonical


def record_key(row):
    return hashlib.sha256(json.dumps([row["source_id"], row["phone"], row["extension"]],
        ensure_ascii=True, separators=(",", ":")).encode()).hexdigest()


def projected_values(row, original_line, actual, normalizer, policy):
    """Fresh admission fields; never mutate a raw row or call old record_values."""
    source = exact_text(row.get("source_id"), 100)
    match = re.fullmatch(r"osm:(node|way|relation):([1-9][0-9]*)", source)
    require(match is not None, "Invalid exact source object identity")
    url = f"https://www.openstreetmap.org/{match[1]}/{match[2]}"
    require(row.get("source_url") == url and row.get("license_id") == "ODbL-1.0"
            and row.get("verification") == "source-listed-not-phone-confirmed",
            "Source URL/license/verification mismatch")
    countries = {k: actual["tags"][k] for k in COUNTRY_KEYS if actual["tags"].get(k)}
    values = [actual["tags"][k].strip().upper() for k in COUNTRY_KEYS if actual["tags"].get(k, "").strip()]
    require(row.get("source_country_fields") == countries
            and row.get("country_tag") == (values[0] if values else None)
            and all(value in IL for value in values), "Source country conflict or manufacture")
    require(row.get("source_updated_at") == actual["timestamp"], "Source timestamp differs")
    stamp = exact_text(row["source_updated_at"], 80)
    require(dt.datetime.fromisoformat(stamp.replace("Z", "+00:00")).tzinfo is not None,
            "Source date lacks timezone")
    name, choice = row.get("name"), None
    if source in NAMES:
        sha, selected = NAMES[source]
        require(source in policy["allowed_exact_name_fallback_ids"]
                and hashlib.sha256(original_line).hexdigest() == sha
                and actual["tags"].get("name") == selected
                and row.get("source_names", {}).get("name") == selected,
                "Unauthorized source-name replacement")
        name, choice = selected, dict(selected_tag="name", selected_value=selected,
            original_preferred_name=row["name"], original_line_sha256=sha,
            source_text_report_sha256=REPORT_SHA)
    require(source not in policy["always_hold_unsafe_city_address_ids"], "Unsafe source city/address remains held")
    canonical = phone_identity(row, normalizer)
    stable = int.from_bytes(hashlib.sha256((source + ":" + canonical).encode()).digest()[:8], "big") & ((1 << 63) - 1)
    require(stable > 0, "Zero stable source identity")
    return (stable, exact_text(name, 300), exact_text(row["phone"], 80), row["phone"],
        exact_text(row.get("city", ""), 300, True), exact_text(row.get("category", ""), 300, True),
        exact_text(row.get("address", ""), 1200, True), url, source, "ODbL-1.0", stamp, canonical), choice


def reread_source(pbf, wanted):
    found = {}
    wanted_numbers = {int(source.rsplit(":", 1)[1]) for source in wanted}
    class Source(osmium.SimpleHandler):
        def retain(self, obj, kind):
            source = f"osm:{kind}:{obj.id}"
            if source not in wanted:
                return
            require(source not in found, "Repeated actual PBF source object")
            entry = dict(source_id=source, tags=dict(obj.tags), timestamp=obj.timestamp.isoformat(), version=obj.version)
            if kind == "node":
                require(obj.location.valid(), "Missing original point geometry")
                entry.update(longitude=obj.location.lon, latitude=obj.location.lat)
            elif kind == "way":
                entry["refs"] = [n.ref for n in obj.nodes]
            else:
                entry["members"] = [dict(type=m.type, ref=m.ref, role=m.role) for m in obj.members]
            found[source] = entry
        def node(self, obj): self.retain(obj, "node")
        def way(self, obj): self.retain(obj, "way")
        def relation(self, obj): self.retain(obj, "relation")
    osmium.apply(str(pbf), osmium.filter.IdFilter(wanted_numbers), Source())
    require(set(found) == wanted, "Not every bounded source object found in actual PBF")
    return found


def audit_fresh_sources(rows, source, geometry_base, pbf, policy, dependencies):
    """Every point/full-way is replayed, plus actual source true-hole relation audit."""
    core, extra, node_geo, way_audit, point_audit, relation_audit = dependencies
    regions, provenance = node_geo.validate_geometry_provenance(geometry_base, PBF_SHA)
    spatial = way_audit.IndependentPolygons(regions)
    objects, geometry = {}, {}
    way_ids = {int(s.rsplit(":", 1)[1]) for s in source if s.startswith("osm:way:")}
    ways, coordinates = way_audit.reread_objects(pbf, way_ids)
    try:
        for source_id, actual in sorted(source.items()):
            tags, kind = actual["tags"], source_id.split(":")[1]
            category = core.category(tags) or extra.category(tags)
            require(category is not None, "Source category no longer in reviewed original cohorts")
            holds = source_publicness(tags, policy, category)
            objects[source_id] = dict(actual, source_pbf_sha256=PBF_SHA,
                reviewed_category=category, publicness_holds=holds)
            if kind == "node":
                require(node_geo.valid_coordinates(actual["longitude"], actual["latitude"]), "Invalid source point")
                distances = point_audit.point_replay(spatial, actual["longitude"], actual["latitude"], policy["margin_lonlat_degrees"])
                geometry[source_id] = dict(kind="point", source_distances_lonlat_degrees=distances)
            elif kind == "way":
                number = int(source_id.rsplit(":", 1)[1])
                require(actual["refs"] == ways[number]["refs"] and actual["tags"] == ways[number]["tags"]
                        and actual["timestamp"] == ways[number]["timestamp"], "Source way rereads disagree")
                coords = [coordinates[n] for n in actual["refs"]]
                distances = spatial.replay(coords, policy["margin_lonlat_degrees"])
                geometry[source_id] = dict(kind="complete-closed-way", source_node_refs=actual["refs"],
                    source_coordinates=coords, source_distances_lonlat_degrees=distances)
    finally:
        spatial.close()
    # The original independent relation auditor rereads actual PBF roles, all
    # ways and nodes, builds complete multipolygons and verifies true holes.
    relation_rows = [row for _, _, _, row in rows if row["source_id"].startswith("osm:relation:")]
    require(len(relation_rows) == 1, "Bounded relation cohort differs")
    relation_corpus = geometry_base / "relation-geography-owner-261103-v2/research-relation-businesses.ndjson"
    relation_proof = relation_audit.audit(pbf, relation_corpus, geometry_base, HERE / "extract_osm_businesses.py")
    for row in relation_rows:
        geometry[row["source_id"]] = dict(kind="complete-source-role-aware-true-hole-multipolygon",
            retained_geometry=row["relation_geometry"], fresh_independent_audit=relation_proof)
    for _, _, original, row in rows:
        actual, proof = source[row["source_id"]], objects[row["source_id"]]
        tags = actual["tags"]
        name = next((core.clean(tags[k]) for k in ("name:he", "name", "name:en") if tags.get(k, "").strip()), "")
        city = core.clean(tags.get("addr:city", ""))
        address = core.clean(tags.get("addr:full", "")) or " ".join(v for v in
            (core.clean(tags.get("addr:street", "")), core.clean(tags.get("addr:housenumber", "")), city) if v)
        require((row["name"], row["category"], row["city"], row["address"], row["source_updated_at"])
                == (name, proof["reviewed_category"], city, address, actual["timestamp"]),
                "Raw displayed fields differ from fresh source PBF")
        if "source_version" in row:
            require(row["source_version"] == actual["version"], "Original source version differs")
        for key in ("source_tags", "source_names"):
            require(all(tags.get(k) == v for k, v in row.get(key, {}).items()), "Retained original source tag differs")
        if row["source_id"].startswith("osm:node:"):
            require((row["longitude"], row["latitude"]) == (actual["longitude"], actual["latitude"]), "Original point differs")
        elif row["source_id"].startswith("osm:way:"):
            require(row["source_node_refs"] == geometry[row["source_id"]]["source_node_refs"]
                    and row["complete_source_ring_coordinates"] == geometry[row["source_id"]]["source_coordinates"],
                    "Original complete way differs")
        require(row["geometry_input_sha256"] == provenance["input_sha256"]["research-region-outer-rings.json"]
                and row["margin_degrees"] == policy["margin_lonlat_degrees"], "Original geometry admission contract differs")
        parsed = [phone for key in ("contact:phone", "phone") if tags.get(key)
                  for phone in core.phones(tags[key])[0]]
        require(any(all(row[k] == phone[k] for k in ("phone", "e164", "extension", "original_phone", "phone_type", "dialing_scope"))
                    for phone in parsed), "Original source phone no longer matches actual PBF")
    return objects, geometry, provenance


def validate_database(path, manifest, expected):
    require(type(manifest.get("schema_version")) is int and manifest["schema_version"] == 1,
            "Native schema version must be integer1")
    require(type(manifest.get("record_count")) is int and manifest["record_count"] == len(expected)
            and 0 < len(expected) <= 2_000_000, "Invalid measured native record count")
    require(manifest["database_sha256"] == digest(path) and path.stat().st_size <= 512 * 1024 * 1024,
            "Native database hash/size differs")
    require(re.fullmatch(r"[A-Za-z0-9._-]{1,80}", manifest["dataset_version"]) is not None,
            "Invalid native dataset version")
    require(manifest["format"] == "DA_CALLER_BUSINESSES" and manifest["source_url"] == SOURCE_PAGE
            and manifest["source_name"] == SOURCE_NAME
            and manifest["license"] == "ODbL-1.0" and manifest["license_url"] == LICENSE_URL
            and manifest["updated_at"] == "2026-11-03", "Native manifest provenance differs")
    before = digest(path)
    with read_only(path) as db:
        require(db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
                and not db.execute("PRAGMA foreign_key_check").fetchall()
                and db.execute("PRAGMA user_version").fetchone()[0] == 1, "Native SQLite integrity/version differs")
        tables = {r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        require(tables == {"metadata", "businesses", "business_search", "business_search_content",
            "business_search_segments", "business_search_segdir", "business_search_docsize", "business_search_stat"},
            "Unexpected research/history or incompatible native tables")
        require(tuple(r[1] for r in db.execute("PRAGMA table_info(businesses)")) == COLUMNS,
                "Native12-column schema differs")
        actual = [tuple(row) for row in db.execute("SELECT " + ",".join(COLUMNS) + " FROM businesses ORDER BY id")]
        require(actual == sorted(expected), "Native business fields differ from new source decisions")
        require(db.execute("SELECT value FROM metadata WHERE key='dataset_version'").fetchone()[0]
                == manifest["dataset_version"], "Native dataset metadata differs")
        expected_fts = [(r[0], search_text(r[1]), search_text(r[4]), search_text(r[5]), r[11]) for r in sorted(expected)]
        require([tuple(r) for r in db.execute("SELECT docid,name,city,category,phone FROM business_search ORDER BY docid")]
                == expected_fts, "Native FTS field/docid differs")
        for row in expected:
            require(db.execute("SELECT id FROM businesses WHERE id=? AND normalized_phone=?", (row[0], row[11])).fetchone() is not None,
                    "Native exact source/phone query omitted identity")
            # The standard FTS4 parser treats column:"token" differently from
            # enhanced syntax. Admitted base identities contain only digits.
            require(db.execute("SELECT docid FROM business_search WHERE docid=? AND business_search MATCH ?",
                               (row[0], 'phone:' + row[11])).fetchone() is not None,
                    "Native FTS phone query omitted identity")
    require(digest(path) == before, "Read-only native checks mutated database")
    return dict(actual_read_only_sqlite=True, integrity_check="ok", clean_native_tables=True,
                all_projected_fields_and_fts_compared=len(expected), all_exact_phone_and_fts_queries=2 * len(expected))


def search_text(text):
    return "".join(c for c in unicodedata.normalize("NFKD", text) if not unicodedata.category(c).startswith("M"))


def retain_atomic(temp, output):
    """Linux atomic no-replace rename; an old package is never overwritten."""
    rename = ctypes.CDLL(None, use_errno=True).renameat2
    rename.argtypes = [ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint]
    rename.restype = ctypes.c_int
    if rename(-100, os.fsencode(temp), -100, os.fsencode(output), 1) != 0:
        error = ctypes.get_errno()
        raise OSError(error, os.strerror(error), str(output))


def build(candidate, evidence, pbf, output, policy_path, dataset_version="owner-261103-source-spatial-v1"):
    candidate, evidence, pbf, output, policy_path = [Path(p).resolve() for p in (candidate, evidence, pbf, output, policy_path)]
    require(not output.exists(), "Output exists; never overwrite a retained package")
    require(output.parent.is_dir() and output.name.startswith("verified-geometry-package-"), "Explicit outside-app output directory required")
    require(not output.is_relative_to(REPO / "app"), "Exporter must stay outside application assets")
    java = REPO / "app/src/main/java/com/dacaller/app/BusinessDirectory.java"
    snapshots, historical = bind_inputs(candidate, evidence, pbf, policy_path, java)
    policy = load_policy(policy_path)
    require(phonenumbers.__version__ == "9.0.42", "Pinned phone metadata required")
    sys.path.insert(0, str(RESEARCH))
    normalizer = load_module("new_business_phone_contract", HERE / "business_phone_v2.py")
    require(normalizer.CONTRACT_ID == "DA_CALLER_BUSINESS_PHONE_V2", "Missing current native phone contract")
    pack = load_module("strict_schema_reference", HERE / "package_app_dataset.py")
    core = load_module("source_core_reference", HERE / "extract_osm_businesses.py")
    import audit_expanded_research_sqlite as expanded_audit
    import extended_business_category as extra
    import research_node_geography as node_geo
    import audit_way_geography as way_audit
    import audit_extra_category_nodes as point_audit
    import audit_relation_geography as relation_audit
    replay = expanded_audit.audit(pbf.parent / "expanded-research-search-owner-261103-v1", evidence,
        pbf, pbf.parent / "research-search-stage-owner-261103-v2")
    rows = []
    with read_only(candidate / "candidate-businesses.sqlite") as db:
        require(db.execute("PRAGMA integrity_check").fetchone()[0] == "ok", "Historical candidate corrupt")
        for item in db.execute("SELECT record_key,corpus_kind,ordinal,original_line,line_sha FROM candidate_raw_records ORDER BY corpus_kind,ordinal"):
            original, row = bytes(item[3]), json.loads(item[3])
            require(digest_bytes(original) == item[4] and record_key(row) == item[0], "Original raw line/identity differs")
            rows.append((item[1], item[2], original, row))
        old_decisions = {r["record_key"]: dict(r) for r in db.execute("SELECT * FROM candidate_decisions")}
        old_projected = {r["id"]: tuple(r) for r in db.execute("SELECT " + ",".join(COLUMNS) + " FROM businesses")}
    require(len(rows) == policy["bounded_raw_rows"] and len(old_decisions) == len(rows), "Raw/history bounded count differs")
    source = reread_source(pbf, {row["source_id"] for _, _, _, row in rows})
    require(len(source) == policy["bounded_source_objects"], "Raw source-object bound differs")
    objects, geometry, geometry_provenance = audit_fresh_sources(rows, source, pbf.parent, pbf, policy,
        (core, extra, node_geo, way_audit, point_audit, relation_audit))
    projected, decisions, ids, identities = [], [], set(), set()
    for kind, ordinal, original, row in rows:
        key, actual = record_key(row), objects[row["source_id"]]
        event = dict(record_key=key, corpus_kind=kind, ordinal=ordinal, source_id=row["source_id"],
            original_line_sha256=digest_bytes(original), original_country=row["country_tag"],
            original_country_fields=row["source_country_fields"], original_scope=row["geographic_scope"],
            original_phone=row["phone"], original_extension=row["extension"],
            prior_decision=old_decisions[key], source_pbf_sha256=PBF_SHA, policy_id=policy["policy_id"],
            admission_method=policy["admission_scope"], source_mutations=0, production_publication=False,
            source_geometry_replayed=True, fresh_source_tags_replayed=True)
        holds = list(actual["publicness_holds"])
        values, choice = None, None
        try:
            values, choice = projected_values(row, original, actual, normalizer.normalized_phone, policy)
        except (ValueError, phonenumbers.NumberParseException) as error:
            holds.append("source-field-or-phone:" + str(error))
        if holds:
            event.update(decision="WITHHELD", hold_reasons=sorted(holds), projected_business_id=None)
        else:
            identity = (values[8], values[11])
            require(values[0] not in ids and identity not in identities, "Stable identity collision or source/base duplicate; no merging")
            ids.add(values[0]); identities.add(identity); projected.append(values)
            if values[0] in old_projected:
                require(values == old_projected[values[0]], "Previously projected source fields changed")
            event.update(decision="ADMITTED_FOR_NATIVE_EVALUATION", hold_reasons=[],
                projected_business_id=values[0], normalized_phone=values[11], source_name_choice=choice,
                prior_phone_hold_resolved=old_decisions[key]["decision"] == "WITHHELD_PHONE_FORMAT_REVIEW")
        decisions.append(event)
    require(0 < len(projected) <= policy["maximum_possible_projected_rows"], "Measured projection outside reviewed bound")
    unchanged(snapshots)
    temp = Path(tempfile.mkdtemp(prefix=".verified-geometry-package-", dir=output.parent))
    try:
        assets, redistribution = temp / "app-assets/business-directory", temp / "redistribution"
        assets.mkdir(parents=True); redistribution.mkdir()
        raw = redistribution / "original-cohorts"; raw.mkdir()
        for kind in sorted({kind for kind, _, _, _ in rows}):
            (raw / (kind + ".ndjson")).write_bytes(b"".join(line for family, _, line, _ in rows if family == kind))
        for name in ("candidate-decisions.ndjson", "candidate-source-name-selections.ndjson", "input-binding.json", "source-name-input-binding.json", "candidate-reader-manifest.json", "candidate-review-envelope.json", "LICENSE-NOTICE.txt"):
            shutil.copyfile(candidate / name, redistribution / name)
        shutil.copyfile(evidence / "text-readiness-report.json", redistribution / "bound-source-text-report.json")
        shutil.copyfile(policy_path, redistribution / "source-geometry-admission-policy.md")
        write_json(redistribution / "effective-admission-policy.json", policy)
        write_lines(redistribution / "fresh-source-objects.ndjson", (objects[key] for key in sorted(objects)))
        write_json(redistribution / "fresh-geometry-replay.json", dict(provenance=geometry_provenance, objects=geometry))
        write_lines(redistribution / "new-admission-decisions.ndjson", decisions)
        write_json(redistribution / "expanded-research-replay.json", replay)
        write_json(redistribution / "historical-reference-resolutions.json", historical)
        receipt = json.loads((pbf.parent / "owner-upload-receipt.json").read_text())
        require(receipt["publisher_provenance_independently_verified"] is False, "Upload must not become publisher authentication")
        shutil.copyfile(pbf.parent / "owner-upload-receipt.json", redistribution / "owner-upload-receipt.json")
        with zipfile.ZipFile(pbf.with_suffix(".zip")) as source_zip:
            require(source_zip.testzip() is None, "Source ZIP CRC failed")
            members = [name for name in source_zip.namelist() if name.endswith(".osm.pbf")]
            require(len(members) == 1, "Ambiguous source ZIP PBF member")
            with source_zip.open(members[0]) as stream:
                require(hashlib.file_digest(stream, "sha256").hexdigest() == PBF_SHA, "Source ZIP member bytes differ from PBF")
        copy_proofs = redistribution / "bound-evidence"; copy_proofs.mkdir()
        for index, (path, sha) in enumerate(sorted(snapshots.items(), key=lambda item: str(item[0]))):
            if path.stat().st_size <= 2_000_000:
                target = copy_proofs / (f"{index:03d}-" + path.name)
                shutil.copyfile(path, target)
                require(digest(target) == sha, "Retained dependency copy differs")
        database = assets / "israel-businesses.sqlite"
        with sqlite3.connect(database) as db:
            pack.create_schema(db)
            db.execute("INSERT INTO metadata VALUES('dataset_version',?)", (dataset_version,))
            db.executemany("INSERT INTO businesses VALUES(" + ",".join("?" for _ in COLUMNS) + ")", sorted(projected))
            db.executemany("INSERT INTO business_search(docid,name,city,category,phone) VALUES(?,?,?,?,?)",
                [(v[0], search_text(v[1]), search_text(v[4]), search_text(v[5]), v[11]) for v in sorted(projected)])
            db.commit(); db.execute("VACUUM")
        manifest = dict(format="DA_CALLER_BUSINESSES", schema_version=1, dataset_version=dataset_version,
            database_sha256=digest(database), record_count=len(projected),
            source_name=SOURCE_NAME, source_url=SOURCE_PAGE,
            license="ODbL-1.0", license_url=LICENSE_URL, updated_at="2026-11-03")
        write_json(assets / "manifest.json", manifest)
        notice = (ATTRIBUTION + "\nODbL-1.0: " + LICENSE_URL + "\nSource: " + SOURCE_PAGE
            + "\nSnapshot: 2026-11-03; user-uploaded source, publisher origin not independently authenticated.\n"
            + "Bounded source geometry, not an official/legal border decision. Partial listed categories only; "
            + "no complete or current business/phone ownership guarantee.\n"
            + "Corresponding exact raw records, decisions and derivative materials are in the redistribution/ directory "
            + "within the source-and-dataset archive linked here:\n" + REDISTRIBUTION_PAGE + "\n")
        (assets / "NOTICE.txt").write_text(notice)
        checks = validate_database(database, manifest, projected)
        phone_counts = collections.Counter(v[11] for v in projected)
        admitted_keys = {e["record_key"] for e in decisions if e["decision"] == "ADMITTED_FOR_NATIVE_EVALUATION"}
        counts = dict(raw_rows=len(rows), raw_source_objects=len(source),
            raw_null_countries=sum(row["country_tag"] is None for _, _, _, row in rows),
            projected_rows=len(projected), projected_source_objects=len({v[8] for v in projected}),
            projected_dial_identities=len(phone_counts), shared_phone_groups=sum(n > 1 for n in phone_counts.values()),
            projected_raw_null_countries=sum(row["country_tag"] is None and record_key(row) in admitted_keys for _, _, _, row in rows),
            holds=dict(sorted(collections.Counter(reason for event in decisions for reason in event["hold_reasons"]).items())),
            withheld_rows=sum(e["decision"] == "WITHHELD" for e in decisions),
            categories=dict(sorted(collections.Counter(v[5] for v in projected).items())),
            geometry_objects=dict(sorted(collections.Counter(g["kind"] for g in geometry.values()).items())),
            admitted_rows_by_cohort=dict(sorted(collections.Counter(e["corpus_kind"] for e in decisions if e["decision"] == "ADMITTED_FOR_NATIVE_EVALUATION").items())),
            exact_source_name_choices=sum(e.get("source_name_choice") is not None for e in decisions),
            admitted_previously_phone_held_rows=sum(e.get("prior_phone_hold_resolved", False) for e in decisions))
        coverage = dict(measurement_scope="Only the bounded five original owner-261103 source cohorts",
            national_coverage_denominator=None, national_coverage_percentage=None, coverage="partial",
            current_phone_ownership_verified=False, publisher_origin_independently_authenticated=False,
            source_snapshot="2026-11-03", source_edit_date_min=min(v[10] for v in projected),
            source_edit_date_max=max(v[10] for v in projected), counts=counts)
        write_json(redistribution / "coverage.json", coverage)
        write_json(temp / "input-binding.json", {str(path): sha for path, sha in sorted(snapshots.items(), key=lambda item: str(item[0]))})
        report = dict(status=STATUS, ready_state=policy["ready_state_before_native_verification"],
            production_publication=False, app_assets_installed=False, apk_changed=False,
            policy_sha256=POLICY_SHA, effective_policy_id=policy["policy_id"],
            source_pbf_sha256=PBF_SHA, source_pbf_bytes=PBF_BYTES, source_zip_sha256=ZIP_SHA,
            source_zip_crc_and_member_pbf_hash_rechecked=True,
            publisher_provenance_independently_verified=False, source_snapshot="2026-11-03",
            original_candidate_sha256=ANCHORS["candidate-businesses.sqlite"],
            phone_contract=normalizer.CONTRACT_ID, native_reader_sha256=snapshots[java],
            phonenumbers_version=phonenumbers.__version__, sqlite_version=sqlite3.sqlite_version,
            observed_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(), counts=counts,
            database_sha256=manifest["database_sha256"], database_bytes=database.stat().st_size,
            measured_checks=checks, historical_native_reference_resolutions=historical,
            independent_data_review="PENDING", current_actual_reader_host_checks="PENDING",
            emulator_telecom_screenshots_visual_review="NOT_TESTED", hardware_and_matching_key_upgrade="NOT_TESTED",
            limits=["Outside-app native-evaluation package only; independent data and actual-reader checks still required.",
                "No official-border, full-national-coverage, live operation, reachability or current phone-owner claim.",
                "No raw phone/name/country/scope rewrite; no source/business merging; source extensions remain separate provenance.",
                "No APK/publication/signing approval; 0.12.0 remains unchanged."])
        write_json(temp / "verification-report.json", report)
        inventory = {str(path.relative_to(temp)): dict(sha256=digest(path), bytes=path.stat().st_size)
            for path in sorted(temp.rglob("*")) if path.is_file()}
        write_json(temp / "artifact-inventory.json", inventory)
        with zipfile.ZipFile(temp / "redistributable-source-and-dataset.zip", "w", zipfile.ZIP_DEFLATED) as archive:
            for relative in sorted(inventory):
                archive.write(temp / relative, relative)
            archive.write(temp / "artifact-inventory.json", "artifact-inventory.json")
        unchanged(snapshots)
        retain_atomic(temp, output)
        return report
    except BaseException:
        shutil.rmtree(temp, ignore_errors=True)
        raise


def digest_bytes(value):
    return hashlib.sha256(value).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--pbf", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--policy", type=Path, required=True)
    parser.add_argument("--dataset-version", default="owner-261103-source-spatial-v1")
    args = parser.parse_args()
    report = build(args.candidate, args.evidence, args.pbf, args.output, args.policy, args.dataset_version)
    print(json.dumps(report, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
