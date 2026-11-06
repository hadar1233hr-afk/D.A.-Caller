#!/usr/bin/env python3
"""Direct-PBF extra-category source audit with independent point predicates.

No source inventory substitutes for the PBF. The reviewed category and phone
parsers remain shared, hash-bound reference contracts. Spatial checks use the
separate existing audit GEOS bindings, never the producer's GeometryScope.classify.
"""
from __future__ import annotations

import argparse
import collections
import datetime as dt
import hashlib
import json
import math
from pathlib import Path
import re
import time

import osmium

import extended_business_category as policy
from audit_way_geography import IndependentPolygons
from research_node_geography import validate_geometry_provenance

COUNTRY_KEYS = ("addr:country", "country", "is_in:country_code", "is_in:country")
IL_VALUES = {"IL", "ISR", "ISRAEL", "ישראל"}
SCOPE = "research-extra-category-node-source-IL-minus-filled-WB-and-Gaza"
EXTRA = {
    "leisure": {"fitness_centre", "bowling_alley", "escape_game", "trampoline_park", "amusement_arcade"},
    "amenity": {"driving_school", "internet_cafe", "nightclub"},
}


def digest(path: Path) -> str:
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def original_nodes(pbf: Path, wanted: set[int]) -> dict:
    found = {}

    class ReadNodes(osmium.SimpleHandler):
        def node(self, obj):
            require(obj.id not in found, "Repeated selected source node")
            require(obj.location.valid(), "Invalid original source coordinate")
            lon, lat = obj.location.lon, obj.location.lat
            require(math.isfinite(lon) and math.isfinite(lat) and -180 <= lon <= 180 and -90 <= lat <= 90,
                    "Original source coordinate outside finite world bounds")
            found[obj.id] = {"tags": dict(obj.tags), "version": obj.version,
                             "timestamp": obj.timestamp.isoformat(), "lon": lon, "lat": lat}

    osmium.apply(str(pbf), osmium.filter.EntityFilter(osmium.osm.NODE),
                 osmium.filter.IdFilter(wanted), ReadNodes())
    require(set(found) == wanted, "Some emitted node IDs not found in actual PBF")
    return found


def extra_category_review(tags: dict) -> str:
    """Independently observe narrow scope/privacy rules; reference parity too."""
    require(policy.core.category(tags) is None, "Source is already covered by unchanged core category")
    category = next((f"{key}:{tags[key]}" for key, values in EXTRA.items() if tags.get(key) in values), None)
    require(category is not None, "Source category outside reviewed extra whitelist")
    require(str(tags.get("access", "")).strip().lower() in {"", "yes", "public", "customers", "permissive"},
            "Source restricted/ambiguous access admitted")
    require(not tags.get("access:conditional", "").strip(), "Conditional source access admitted")
    require(str(tags.get("building", "")).strip().lower() not in policy.RESIDENTIAL_BUILDINGS,
            "Residential source building admitted")
    require(str(tags.get("residential", "")).strip().lower() not in {"yes", "private"},
            "Residential-only source object admitted")
    require(str(tags.get("building", "")).strip().lower() not in policy.NON_OPERATING_BUILDINGS,
            "Non-operating source building admitted")
    for prefix in ("disused", "abandoned", "demolished", "razed", "construction", "proposed"):
        require(str(tags.get(prefix, "")).strip().lower() in {"", "no", "false", "0"},
                "Source lifecycle conflict admitted")
        require(not any(tags.get(prefix + ":" + key, "").strip() for key in EXTRA),
                "Prefixed source lifecycle conflict admitted")
    require(policy.category_decision(tags) == ("research-extra-category-no-restricted-source-access", category),
            "Reviewed policy no longer agrees with independent whitelist/access observations")
    return category


def point_replay(spatial: IndependentPolygons, lon: float, lat: float, margin: float) -> dict:
    require(type(margin) in (int, float) and margin == 0.001, "Unexpected angular policy margin")
    pointer = spatial.g.GEOSWKTReader_read(spatial.reader, f"POINT ({lon} {lat})".encode())
    require(bool(pointer), "Independent point construction failed")
    try:
        require(spatial.predicate("GEOSContains", spatial.polygons["source_IL"], pointer),
                "Original point is outside/on source IL")
        distances = {"source_IL_boundary": spatial.distance(spatial.boundary, pointer)}
        require(distances["source_IL_boundary"] > margin, "Source IL angular margin failed")
        for region in ("Gaza_admin4", "JudeaSamaria_admin4_outer_filled", "WestBank_historic_outer_filled"):
            require(not spatial.predicate("GEOSIntersects", spatial.polygons[region], pointer),
                    "Original point intersects conservative exclusion")
            distances[region] = spatial.distance(spatial.polygons[region], pointer)
            require(distances[region] > margin, "Source exclusion angular margin failed")
        return distances
    finally:
        spatial.g.GEOSGeom_destroy(pointer)


def audit(pbf: Path, corpus: Path, geometry_base: Path, prior_corpora: list[Path]) -> dict:
    started = time.monotonic()
    report_path = corpus.with_name("report.json")
    here = Path(__file__).resolve().parent
    producer = here / "research_extra_category_nodes.py"
    paths = [pbf, corpus, report_path, producer, Path(policy.__file__), policy.CORE_PATH,
             here / "audit_way_geography.py", here / "research_node_geography.py", *prior_corpora]
    before = {path: digest(path) for path in paths}
    source_sha, corpus_sha = before[pbf], before[corpus]
    report = json.loads(report_path.read_text())
    require(report.get("status") == "RESEARCH_ONLY_NOT_APP_ACCEPTED" and report.get("scope") == SCOPE,
            "Wrong producer status/scope")
    require(report.get("source_pbf_sha256") == source_sha and report.get("source_pbf_bytes") == pbf.stat().st_size,
            "Producer source PBF binding differs")
    require(report.get("ndjson_sha256") == corpus_sha and report.get("research_script_sha256") == before[producer],
            "Corpus or frozen producer hash differs")
    require(report.get("policy_module_sha256") == before[Path(policy.__file__)]
            and report.get("unchanged_core_extractor_sha256") == before[policy.CORE_PATH]
            and report.get("node_geometry_reference_sha256") == before[here / "research_node_geography.py"],
            "Producer category/parser/geometry reference changed")
    rows = [json.loads(line) for line in corpus.read_text().splitlines() if line.strip()]
    require(len(rows) == report.get("raw_phone_rows") and len({r["id"] for r in rows}) == len(rows),
            "Research row count or unique identity mismatch")
    identifiers = set()
    for row in rows:
        match = re.fullmatch(r"osm:node:([1-9][0-9]*)", row["source_id"])
        require(match is not None, "Only exact original source node IDs allowed")
        identifiers.add(int(match[1]))
    require(len(identifiers) == report.get("source_nodes"), "Producer unique node count differs")
    originals = original_nodes(pbf, identifiers)
    prior_ids, prior_phones, prior_inputs = set(), set(), []
    for path in prior_corpora:
        prior_rows = [json.loads(line) for line in path.read_text().splitlines() if line.strip()]
        prior_ids.update(row["source_id"] for row in prior_rows)
        prior_phones.update(row["phone"] for row in prior_rows)
        prior_inputs.append({"path": str(path), "sha256": before[path], "rows": len(prior_rows)})
    current_ids = {row["source_id"] for row in rows}
    require(not current_ids & prior_ids, "Extra source IDs overlap a previous cohort")
    rings, geometry = validate_geometry_provenance(geometry_base, source_sha)
    spatial = IndependentPolygons(rings)
    checks, minima = collections.Counter(), {}
    expected_phones, observed_phones = {}, collections.defaultdict(set)
    try:
        for row in rows:
            identifier = int(row["source_id"].rsplit(":", 1)[1])
            node = originals[identifier]
            tags = node["tags"]
            require(row["source_url"] == f"https://www.openstreetmap.org/node/{identifier}"
                    and row["source_updated_at"] == node["timestamp"] and row["source_version"] == node["version"],
                    "Source node identity/URL/timestamp/version differs")
            checks["exact_original_id_url_timestamp_version_rows"] += 1
            require(row["source_tags"] == tags, "Full original source tags differ")
            checks["exact_original_raw_tags_rows"] += 1
            category = extra_category_review(tags)
            require(row["category"] == category and row["category_admission"] == "research-extra-category-no-restricted-source-access",
                    "Source category/privacy decision differs")
            checks["independent_whitelist_access_residential_lifecycle_and_shared_policy_rows"] += 1
            name = next((policy.clean(tags[key]) for key in ("name:he", "name", "name:en")
                         if tags.get(key, "").strip()), "")
            city = policy.clean(tags.get("addr:city", ""))
            address = policy.clean(tags.get("addr:full", "")) or " ".join(
                value for value in (policy.clean(tags.get("addr:street", "")),
                                    policy.clean(tags.get("addr:housenumber", "")), city) if value)
            require(row["name"] == name and row["city"] == city and row["address"] == address,
                    "Source-derived display/address text differs")
            checks["exact_source_name_city_address_rows"] += 1
            fields = {key: tags[key] for key in COUNTRY_KEYS if tags.get(key)}
            values = [tags[key].strip().upper() for key in COUNTRY_KEYS if tags.get(key, "").strip()]
            require(all(value in IL_VALUES for value in values), "Conflicting explicit source country admitted")
            country = values[0] if values else None
            method = "explicit-IL" if country else "missing-source-country-tag"
            require(row["source_country_fields"] == fields and row["country_tag"] == country
                    and row["country_admission"] == method and row["country_inferred"] is False,
                    "Original/missing country fields differ")
            checks["exact_source_country_fields_without_inference_rows"] += 1
            raw = {key: tags[key] for key in ("contact:phone", "phone") if tags.get(key)}
            require(row["source_phones"] == raw, "Original source phone fields differ")
            candidates = {}
            for value in raw.values():
                for candidate in policy.phones(value)[0]:
                    key = candidate["phone"], candidate["extension"]
                    if key not in candidates and (candidate["e164"] or country in IL_VALUES):
                        candidates[key] = candidate
            key = row["phone"], row["extension"]
            require(key in candidates and all(row[field] == value for field, value in candidates[key].items()),
                    "Source original/canonical phone/type/extension differs")
            expected_phones[identifier] = set(candidates)
            observed_phones[identifier].add(key)
            expected_id = row["source_id"] + ":" + row["phone"] + (";ext=" + row["extension"] if row["extension"] else "")
            require(row["id"] == expected_id, "Research row source-phone identity differs")
            checks["exact_source_original_canonical_phone_and_extension_rows"] += 1
            require(row["longitude"] == node["lon"] and row["latitude"] == node["lat"],
                    "Original point coordinates differ")
            checks["exact_original_coordinates_rows"] += 1
            require(row["geometry_input_sha256"] == geometry["input_sha256"]["research-region-outer-rings.json"]
                    and row["geometry_admission"] == "research-qualified-source-interior",
                    "Source geometry/policy binding differs")
            distances = point_replay(spatial, node["lon"], node["lat"], row["margin_degrees"])
            for region, value in distances.items():
                minima[region] = min(minima.get(region, math.inf), value)
            checks["independent_source_point_inclusion_margin_exclusion_rows"] += 1
            require(row["geographic_scope"] == SCOPE and row["status"] == "RESEARCH_ONLY_NOT_APP_ACCEPTED"
                    and row["license_id"] == "ODbL-1.0" and row["verification"] == "source-listed-not-phone-confirmed",
                    "Source research/license/provenance differs")
            checks["research_status_license_provenance_rows"] += 1
        require(expected_phones == dict(observed_phones), "A qualifying phone was dropped or duplicated")
        geos_version = spatial.version
    finally:
        spatial.close()
    require(all(digest(path) == value for path, value in before.items()), "Source/reference input changed during audit")
    return {
        "status": "PASS_RESEARCH_EXTRA_CATEGORY_NODE_SOURCE_AND_POINT_AUDIT_ONLY",
        "observed_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "elapsed_seconds": round(time.monotonic() - started, 3),
        "source_pbf_sha256": source_sha, "source_pbf_bytes": pbf.stat().st_size,
        "research_ndjson_sha256": corpus_sha, "research_report_sha256": before[report_path],
        "audit_script_sha256": digest(Path(__file__)), "reviewed_producer_sha256": before[producer],
        "reviewed_policy_sha256": before[Path(policy.__file__)],
        "reference_extractor_sha256": before[policy.CORE_PATH],
        "reference_spatial_binding_sha256": before[here / "audit_way_geography.py"],
        "geometry_input_sha256": geometry["input_sha256"],
        "source_node_rows": len(rows), "unique_original_source_nodes_found": len(originals),
        "checks_passed": dict(sorted(checks.items())),
        "minimum_actual_point_distances_degrees": minima, "geos_version": geos_version,
        "prior_cohorts": prior_inputs, "source_id_overlap_with_prior_cohorts": 0,
        "source_phone_overlap_with_prior_cohorts": len({r["phone"] for r in rows} & prior_phones),
        "app_records_added": 0, "source_mutations": 0, "automatic_merges": 0,
        "method": ["Direct actual-PBF C++ NODE/IdFilter reread of every emitted source ID, full tags/version/time/coordinate",
                   "Exact source names/addresses/country and original/canonical phone/extension compared for every emitted row",
                   "Independent narrow whitelist/privacy observations with hash-bound shared reviewed policy parity",
                   "Separate GEOS point bindings test actual points against retained original source polygons; never producer GeometryScope.classify",
                   "All prior source cohorts compared for duplicate source IDs without merging shared phone numbers"],
        "limits": ["The original phone/category parser and reviewed policy remain shared hash-bound reference contracts.",
                   "Spatial replay uses the separate existing audit GEOS bindings and source geometry provenance validator; official/legal geography is not established.",
                   "Only emitted source nodes are audited; this is not proof all eligible source categories/objects were captured.",
                   "Source listing, privacy-tag checks and numbering metadata do not establish public access, current operation/ownership or reachability.",
                   "Angular0.001degree margins are not meters; no country field is inferred or production/app/release admission approved."]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for field in ("pbf", "corpus", "geometry_base", "output"):
        parser.add_argument(field, type=Path)
    parser.add_argument("--prior-corpus", type=Path, action="append", required=True)
    args = parser.parse_args()
    require(not args.output.exists(), "Audit output exists; retain it and use a new path")
    result = audit(args.pbf, args.corpus, args.geometry_base, args.prior_corpus)
    with args.output.open("x", encoding="utf-8") as handle:
        json.dump(result, handle, ensure_ascii=True, indent=2)
        handle.write("\n")
    print(json.dumps({key: result[key] for key in (
        "status", "source_node_rows", "unique_original_source_nodes_found", "source_id_overlap_with_prior_cohorts", "app_records_added")}))


if __name__ == "__main__":
    main()
