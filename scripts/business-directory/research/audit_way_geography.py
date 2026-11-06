#!/usr/bin/env python3
"""Independently reread emitted WAYs/nodes from PBF and replay full polygons.

No source inventory is used as a substitute for PBF objects. The existing
extractor's category/phone policy is deliberately reused and hash-bound; this
audit does not independently establish numbering-plan truth or current ownership.
Spatial replay uses its own GEOS bindings and the original retained source rings,
without calling ClosedWayScope or its classify_ring implementation.
"""
from __future__ import annotations

import argparse
import collections
import ctypes
import ctypes.util
import datetime as dt
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import re
import time
import unicodedata

import osmium

from research_node_geography import validate_geometry_provenance


COUNTRY_KEYS = ("addr:country", "country", "is_in:country_code", "is_in:country")
IL_VALUES = {"IL", "ISR", "ISRAEL", "ישראל"}
SCOPE = "research-complete-closed-way-source-IL-interior-minus-filled-WB-and-Gaza"
REGIONS = ("source_IL", "Gaza_admin4", "JudeaSamaria_admin4_outer_filled",
           "WestBank_historic_outer_filled")


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def clean(value: str) -> str:
    return unicodedata.normalize("NFC", value).strip()


def reread_objects(pbf: Path, identifiers: set[int]) -> tuple[dict, dict]:
    """Two C++-filtered PBF passes; source refs determine requested node IDs."""
    ways = {}

    class WayRead(osmium.SimpleHandler):
        def way(self, obj):
            require(obj.id not in ways, "Duplicate selected PBF way")
            ways[obj.id] = {"tags": dict(obj.tags), "refs": [ref.ref for ref in obj.nodes],
                            "timestamp": obj.timestamp.isoformat(), "version": obj.version}

    osmium.apply(str(pbf), osmium.filter.EntityFilter(osmium.osm.WAY),
                 osmium.filter.IdFilter(identifiers), WayRead())
    require(set(ways) == identifiers, "Not every emitted way was found in PBF")
    needed = {ref for way in ways.values() for ref in way["refs"]}
    nodes = {}

    class NodeRead(osmium.SimpleHandler):
        def node(self, obj):
            require(obj.id not in nodes, "Duplicate referenced PBF node")
            require(obj.location.valid(), "Invalid referenced PBF node location")
            lon, lat = obj.location.lon, obj.location.lat
            require(math.isfinite(lon) and math.isfinite(lat)
                    and -180 <= lon <= 180 and -90 <= lat <= 90,
                    "Referenced PBF node location outside finite world bounds")
            nodes[obj.id] = [lon, lat]

    osmium.apply(str(pbf), osmium.filter.EntityFilter(osmium.osm.NODE),
                 osmium.filter.IdFilter(needed), NodeRead())
    require(set(nodes) == needed, "Some original PBF way references are unresolved")
    return ways, nodes


class IndependentPolygons:
    """Independent full-geometry predicate replay; lon/lat degree distances."""
    def __init__(self, rings: dict):
        library = ctypes.util.find_library("geos_c")
        require(bool(library), "Existing GEOS C library required")
        self.g = g = ctypes.CDLL(library)
        pointer = ctypes.c_void_p
        g.initGEOS.argtypes = [pointer, pointer]
        g.initGEOS(None, None)
        g.GEOSversion.restype = ctypes.c_char_p
        self.version = g.GEOSversion().decode()
        g.GEOSWKTReader_create.restype = pointer
        g.GEOSWKTReader_read.argtypes = [pointer, ctypes.c_char_p]
        g.GEOSWKTReader_read.restype = pointer
        g.GEOSWKTReader_destroy.argtypes = [pointer]
        g.GEOSGeom_destroy.argtypes = [pointer]
        g.GEOSBoundary.argtypes = [pointer]
        g.GEOSBoundary.restype = pointer
        for name in ("GEOSContains", "GEOSIntersects"):
            getattr(g, name).argtypes = [pointer, pointer]
            getattr(g, name).restype = ctypes.c_char
        for name in ("GEOSisValid", "GEOSisEmpty"):
            getattr(g, name).argtypes = [pointer]
            getattr(g, name).restype = ctypes.c_char
        g.GEOSDistance.argtypes = [pointer, pointer, ctypes.POINTER(ctypes.c_double)]
        g.GEOSDistance.restype = ctypes.c_int
        g.GEOSArea.argtypes = [pointer, ctypes.POINTER(ctypes.c_double)]
        g.GEOSArea.restype = ctypes.c_int
        self.reader = g.GEOSWKTReader_create()
        require(bool(self.reader), "GEOS reader creation failed")
        self.polygons = {}
        self.boundary = None
        try:
            for region in REGIONS:
                require(len(rings[region]) == 1, "Exactly one source outer ring required")
                self.polygons[region] = self.polygon(rings[region][0]["coordinates"])
            self.boundary = g.GEOSBoundary(self.polygons["source_IL"])
            require(bool(self.boundary), "GEOS source inclusion boundary failed")
        except BaseException:
            self.close()
            raise

    def polygon(self, coordinates: list) -> int:
        require(len(coordinates) >= 4 and coordinates[0] == coordinates[-1],
                "Original coordinates do not form a closed ring")
        require(len({tuple(pair) for pair in coordinates[:-1]}) >= 3,
                "Source ring collapsed to fewer than3 positions")
        text = "POLYGON ((" + ",".join(f"{lon} {lat}" for lon, lat in coordinates) + "))"
        polygon = self.g.GEOSWKTReader_read(self.reader, text.encode())
        require(bool(polygon), "GEOS source polygon creation failed")
        try:
            require(self.g.GEOSisValid(polygon) == b"\x01"
                    and self.g.GEOSisEmpty(polygon) == b"\x00", "Invalid or empty source polygon")
            area = ctypes.c_double()
            require(self.g.GEOSArea(polygon, ctypes.byref(area)) == 1
                    and math.isfinite(area.value) and area.value > 0,
                    "Nonpositive or invalid source polygon area")
        except BaseException:
            self.g.GEOSGeom_destroy(polygon)
            raise
        return polygon

    def predicate(self, name: str, first: int, second: int) -> bool:
        answer = getattr(self.g, name)(first, second)
        require(answer in (b"\x00", b"\x01"), "GEOS predicate failed")
        return answer == b"\x01"

    def distance(self, first: int, second: int) -> float:
        answer = ctypes.c_double()
        require(self.g.GEOSDistance(first, second, ctypes.byref(answer)) == 1
                and math.isfinite(answer.value), "GEOS distance failed")
        return answer.value

    def replay(self, coordinates: list, margin: float) -> dict:
        require(type(margin) in (int, float) and margin == 0.001,
                "Only the retained0.001degree policy may be audited")
        polygon = self.polygon(coordinates)
        try:
            require(self.predicate("GEOSContains", self.polygons["source_IL"], polygon),
                    "Full source polygon not contained in source IL")
            distances = {"source_IL_boundary": self.distance(self.boundary, polygon)}
            require(distances["source_IL_boundary"] > margin, "Source IL margin failed")
            for region in REGIONS[1:]:
                require(not self.predicate("GEOSIntersects", self.polygons[region], polygon),
                        "Full source polygon intersects exclusion")
                distances[region] = self.distance(self.polygons[region], polygon)
                require(distances[region] > margin, "Full source polygon exclusion margin failed")
            return distances
        finally:
            self.g.GEOSGeom_destroy(polygon)

    def close(self):
        if self.boundary:
            self.g.GEOSGeom_destroy(self.boundary)
            self.boundary = None
        for value in self.polygons.values():
            self.g.GEOSGeom_destroy(value)
        self.polygons = {}
        if self.reader:
            self.g.GEOSWKTReader_destroy(self.reader)
            self.reader = None


def audit(pbf: Path, corpus: Path, geometry_base: Path, extractor: Path) -> dict:
    started = time.monotonic()
    source_hash, corpus_hash = digest(pbf), digest(corpus)
    report_path = corpus.with_name("report.json")
    report = json.loads(report_path.read_text())
    require(report["source_pbf_sha256"] == source_hash
            and report["source_pbf_bytes"] == pbf.stat().st_size
            and report["ndjson_sha256"] == corpus_hash,
            "PBF/corpus bytes do not match retained research report")
    require(report["extractor_sha256"] == digest(extractor), "Existing extractor changed")
    root_helper = Path(__file__).with_name("research_way_geography.py")
    require(report["research_script_sha256"] == digest(root_helper), "Reviewed research helper changed")
    rows = [json.loads(line) for line in corpus.read_text().splitlines() if line.strip()]
    require(len(rows) == report["raw_phone_rows"] and len({row["id"] for row in rows}) == len(rows),
            "Retained source row count/unique identity failed")
    identifiers = set()
    for row in rows:
        match = re.fullmatch(r"osm:way:([1-9][0-9]*)", row["source_id"])
        require(match is not None, "Only exact source WAY identities may be audited")
        identifiers.add(int(match[1]))
    require(len(identifiers) == report["source_ways"], "Source way count mismatch")
    ways, nodes = reread_objects(pbf, identifiers)
    rings, geometry = validate_geometry_provenance(geometry_base, source_hash)
    spec = importlib.util.spec_from_file_location("source_audit_existing_extractor", extractor)
    reference = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(reference)
    checks = collections.Counter()
    observed_phone_keys = collections.defaultdict(set)
    expected_phone_keys = {}
    minima = {}
    spatial = IndependentPolygons(rings)
    try:
        for row in rows:
            way_id = int(row["source_id"].rsplit(":", 1)[1])
            actual, tags = ways[way_id], ways[way_id]["tags"]
            require(row["source_url"] == f"https://www.openstreetmap.org/way/{way_id}"
                    and row["source_updated_at"] == actual["timestamp"]
                    and row["source_version"] == actual["version"], "Source way ID/version/time/URL changed")
            checks["exact_source_way_identity_version_timestamp_url_rows"] += 1
            require(row["source_tags"] == tags, "Source raw way tags changed")
            checks["exact_full_raw_source_tag_rows"] += 1
            name = next((clean(tags[key]) for key in ("name:he", "name", "name:en")
                         if tags.get(key, "").strip()), "")
            city = clean(tags.get("addr:city", ""))
            address = clean(tags.get("addr:full", "")) or " ".join(
                value for value in (clean(tags.get("addr:street", "")),
                                    clean(tags.get("addr:housenumber", "")), city) if value)
            require(row["name"] == name and row["city"] == city and row["address"] == address
                    and row["category"] == reference.category(tags), "Source text/category differs")
            checks["source_derived_name_city_address_category_rows"] += 1
            fields = {key: tags[key] for key in COUNTRY_KEYS if tags.get(key)}
            country_values = [tags[key].strip().upper() for key in COUNTRY_KEYS
                              if tags.get(key, "").strip()]
            require(all(value in IL_VALUES for value in country_values), "Non-IL source country field admitted")
            country = country_values[0] if country_values else None
            method = "explicit-IL" if country else "missing-source-country-tag"
            require(row["source_country_fields"] == fields and row["country_tag"] == country
                    and row["country_admission"] == method and row["country_inferred"] is False,
                    "Source country fields or missing-country provenance changed")
            checks["exact_source_country_fields_without_inference_rows"] += 1
            raw_phones = {key: tags[key] for key in ("contact:phone", "phone") if tags.get(key)}
            require(row["source_phones"] == raw_phones, "Source raw phone tags changed")
            candidates = {}
            for raw in raw_phones.values():
                for candidate in reference.phones(raw)[0]:
                    key = candidate["phone"], candidate["extension"]
                    if key not in candidates and (candidate["e164"] or country in IL_VALUES):
                        candidates[key] = candidate
            key = row["phone"], row["extension"]
            require(key in candidates and all(row[field] == value
                    for field, value in candidates[key].items()), "Source original/canonical phone differs")
            expected_phone_keys[way_id] = set(candidates)
            observed_phone_keys[way_id].add(key)
            expected_id = row["source_id"] + ":" + row["phone"]
            if row["extension"]:
                expected_id += ";ext=" + row["extension"]
            require(row["id"] == expected_id, "Source row identity changed")
            checks["source_raw_and_reparsed_canonical_phone_extension_rows"] += 1
            refs = actual["refs"]
            require(len(refs) >= 4 and refs[0] == refs[-1]
                    and len(set(refs[:-1])) >= 3, "Source way is not a complete closed ring")
            require(tags.get("area", "").strip().lower() != "no", "Source area=no way admitted")
            coordinates = [nodes[ref] for ref in refs]
            require(row["source_node_refs"] == refs
                    and row["complete_source_ring_coordinates"] == coordinates,
                    "Ordered original source refs or every coordinate differs")
            checks["exact_ordered_source_ref_and_coordinate_rows"] += 1
            checks["coordinate_slots_rechecked_against_pbf"] += len(coordinates)
            require(row["geometry_input_sha256"] == geometry["input_sha256"]["research-region-outer-rings.json"]
                    and row["geometry_admission"] == "research-qualified-complete-source-polygon",
                    "Original geometry policy/hash binding differs")
            distances = spatial.replay(coordinates, row["margin_degrees"])
            for region, value in distances.items():
                minima[region] = min(minima.get(region, math.inf), value)
            checks["independent_complete_polygon_valid_area_containment_margin_exclusion_rows"] += 1
            require(row["geographic_scope"] == SCOPE
                    and row["status"] == "RESEARCH_ONLY_NOT_APP_ACCEPTED"
                    and row["license_id"] == "ODbL-1.0"
                    and row["verification"] == "source-listed-not-phone-confirmed",
                    "Research status/license/provenance changed")
            checks["research_status_license_provenance_rows"] += 1
        require(expected_phone_keys == dict(observed_phone_keys),
                "A qualifying phone for an emitted source way was dropped or duplicated")
        geos_version = spatial.version
    finally:
        spatial.close()
    require(digest(pbf) == source_hash and digest(corpus) == corpus_hash,
            "Source PBF or research corpus changed during audit")
    return {
        "status": "PASS_RESEARCH_SOURCE_AND_COMPLETE_POLYGON_AUDIT_ONLY",
        "observed_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "elapsed_seconds": round(time.monotonic() - started, 3),
        "source_pbf_sha256": source_hash, "source_pbf_bytes": pbf.stat().st_size,
        "research_ndjson_sha256": corpus_hash, "research_report_sha256": digest(report_path),
        "audit_script_sha256": digest(Path(__file__)),
        "reviewed_way_helper_sha256": digest(root_helper),
        "reference_extractor_sha256": digest(extractor),
        "geometry_input_sha256": geometry["input_sha256"],
        "source_way_rows": len(rows), "unique_original_source_ways_found": len(ways),
        "unique_original_referenced_nodes_found": len(nodes),
        "checks_passed": dict(sorted(checks.items())),
        "minimum_actual_polygon_distances_degrees": minima,
        "geos_version": geos_version,
        "method": ["PBF pass1 C++ EntityFilter(WAY)+IdFilter on emitted way IDs, retaining exact raw tags/refs/version/time",
                   "PBF pass2 C++ EntityFilter(NODE)+IdFilter using refs obtained from original PBF ways, not cached inventory",
                   "Exact original ordered refs and every retained coordinate compared for every research phone row",
                   "Own GEOS bindings replay complete original polygons against retained original source rings; no ClosedWayScope calls",
                   "Original source phones re-parsed through the unchanged hash-bound existing extractor contract"],
        "app_rows_added": 0, "source_mutations": 0, "automatic_merges": 0,
        "limits": ["Source fidelity and numbering-plan policy replay do not establish current business operation, phone ownership or reachability.",
                   "The unchanged source category/phone parser and geometry-provenance validator are shared reference contracts, not independent implementations.",
                   "Angular0.001degree rules use source lon/lat coordinates, not surveyed meters or official/legal borders.",
                   "Only emitted standalone closed ways audited; omitted ways/relations and national completeness are not verified here.",
                   "Missing country fields remain missing; this audit does not approve production admission or APK publication."]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pbf", type=Path)
    parser.add_argument("corpus", type=Path)
    parser.add_argument("geometry_base", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--extractor", type=Path, required=True)
    args = parser.parse_args()
    require(not args.output.exists(), "Audit output already exists; retain it and choose another path")
    result = audit(args.pbf, args.corpus, args.geometry_base, args.extractor)
    with args.output.open("x", encoding="utf-8") as handle:
        json.dump(result, handle, ensure_ascii=True, indent=2)
        handle.write("\n")
    print(json.dumps({key: result[key] for key in
                     ("status", "source_way_rows", "unique_original_source_ways_found",
                      "unique_original_referenced_nodes_found", "app_rows_added")}))


if __name__ == "__main__":
    main()
