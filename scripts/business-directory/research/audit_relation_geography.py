#!/usr/bin/env python3
"""Independent PBF reread and hole-aware relation source/spatial audit.

The producer's assembly/classification and cached inventory are not called.
Original category/phone parsing and geometry-provenance validation are shared
reference contracts; no independent numbering-plan truth is claimed.
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
REGIONS = ("source_IL", "Gaza_admin4", "JudeaSamaria_admin4_outer_filled", "WestBank_historic_outer_filled")
SCOPE = "research-complete-role-aware-multipolygon-source-IL-minus-filled-WB-and-Gaza"


def need(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def clean(value):
    return unicodedata.normalize("NFC", value).strip()


def original_objects(pbf, reference):
    """Inventory source candidate relations, then read exact member dependencies."""
    relations, ways, nodes = {}, {}, {}

    class RelationRead(osmium.SimpleHandler):
        def relation(self, obj):
            tags = dict(obj.tags)
            if (not reference.category(tags)
                    or not any(tags.get(k, "").strip() for k in ("name:he", "name", "name:en"))
                    or not any(tags.get(k) for k in ("contact:phone", "phone"))):
                return
            need(obj.id not in relations, "Duplicate original relation")
            relations[obj.id] = dict(tags=tags, timestamp=obj.timestamp.isoformat(), version=obj.version,
                                     members=[dict(type=m.type, ref=m.ref, role=m.role) for m in obj.members])

    osmium.apply(str(pbf), osmium.filter.EntityFilter(osmium.osm.RELATION),
                 osmium.filter.KeyFilter("shop", "office", "craft", "healthcare", "amenity", "tourism"), RelationRead())
    wanted_ways = {m["ref"] for r in relations.values() for m in r["members"] if m["type"] == "w"}

    class WayRead(osmium.SimpleHandler):
        def way(self, obj):
            need(obj.id not in ways, "Duplicate original member way")
            ways[obj.id] = dict(refs=[n.ref for n in obj.nodes], timestamp=obj.timestamp.isoformat())

    osmium.apply(str(pbf), osmium.filter.EntityFilter(osmium.osm.WAY),
                 osmium.filter.IdFilter(wanted_ways), WayRead())
    need(set(ways) == wanted_ways, "Original member ways missing")
    wanted_nodes = {ref for w in ways.values() for ref in w["refs"]}

    class NodeRead(osmium.SimpleHandler):
        def node(self, obj):
            need(obj.id not in nodes and obj.location.valid(), "Duplicate/invalid original node")
            lon, lat = obj.location.lon, obj.location.lat
            need(math.isfinite(lon) and math.isfinite(lat) and -180 <= lon <= 180 and -90 <= lat <= 90,
                 "Original node coordinate outside world bounds")
            nodes[obj.id] = dict(source_id=f"osm:node:{obj.id}", source_updated_at=obj.timestamp.isoformat(),
                                 longitude=lon, latitude=lat, valid_coordinates=True)

    osmium.apply(str(pbf), osmium.filter.EntityFilter(osmium.osm.NODE),
                 osmium.filter.IdFilter(wanted_nodes), NodeRead())
    need(set(nodes) == wanted_nodes, "Original referenced node missing")
    return relations, ways, nodes


def ring_key(refs):
    """Canonical source ref cycle, allowing only rotation/reversal equivalence."""
    need(len(refs) >= 4 and refs[0] == refs[-1] and len(set(refs[:-1])) >= 3, "Incomplete source ring")
    sequence = tuple(refs[:-1])
    return min(sequence[i:] + sequence[:i] for sequence in (sequence, sequence[::-1]) for i in range(len(sequence)))


def source_cycles(members, ways, nodes):
    """Independent endpoint-edge walk, entirely from original PBF member data."""
    need(members and all(m["type"] == "w" and m["role"] in {"outer", "inner"} for m in members),
         "Nested/non-way/ambiguous original member")
    need(len({m["ref"] for m in members}) == len(members), "Repeated original member way")
    result = {}
    for role in ("outer", "inner"):
        selected = {m["ref"]: ways[m["ref"]]["refs"] for m in members if m["role"] == role}
        rings, open_edges = [], {}
        for identifier, refs in selected.items():
            need(len(refs) >= 2 and all(ref in nodes for ref in refs), "Missing original role coordinates")
            if refs[0] == refs[-1]:
                ring_key(refs)
                rings.append(list(refs))
            else:
                open_edges[identifier] = list(refs)
        adjacency = collections.defaultdict(list)
        for identifier, refs in open_edges.items():
            adjacency[refs[0]].append(identifier)
            adjacency[refs[-1]].append(identifier)
        need(all(len(ids) == 2 for ids in adjacency.values()), "Open or branching source role endpoints")
        unused = set(open_edges)
        while unused:
            first = min(unused)
            start = min(open_edges[first][0], open_edges[first][-1])
            current, refs, consumed = start, [], []
            while True:
                available = [identifier for identifier in adjacency[current] if identifier in unused]
                need(bool(available), "Unclosed original source role")
                identifier = min(available)
                segment = open_edges[identifier]
                segment = segment if segment[0] == current else list(reversed(segment))
                need(segment[0] == current, "Source role adjacency mismatch")
                refs.extend(segment if not refs else segment[1:])
                unused.remove(identifier)
                consumed.append(identifier)
                current = segment[-1]
                if current == start:
                    break
                need(len([identifier for identifier in adjacency[current] if identifier in unused]) == 1,
                     "Ambiguous source role continuation")
            need(consumed, "No original role edges consumed")
            ring_key(refs)
            rings.append(refs)
        result[role] = [dict(node_refs=refs, coordinates=[[nodes[n]["longitude"], nodes[n]["latitude"]] for n in refs])
                        for refs in rings]
    need(bool(result["outer"]), "Original relation has no outer shell")
    return result


def coordinates_wkt(coords):
    return "(" + ",".join(f"{lon} {lat}" for lon, lat in coords) + ")"


class IndependentSpatial:
    """Own GEOS bindings and true-hole MULTIPOLYGON construction."""
    def __init__(self, regions):
        path = ctypes.util.find_library("geos_c")
        need(bool(path), "Existing GEOS C required")
        self.g = g = ctypes.CDLL(path)
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
        for method in ("GEOSContains", "GEOSIntersects"):
            getattr(g, method).argtypes = [pointer, pointer]
            getattr(g, method).restype = ctypes.c_char
        for method in ("GEOSisValid", "GEOSisEmpty"):
            getattr(g, method).argtypes = [pointer]
            getattr(g, method).restype = ctypes.c_char
        g.GEOSArea.argtypes = [pointer, ctypes.POINTER(ctypes.c_double)]
        g.GEOSArea.restype = ctypes.c_int
        g.GEOSDistance.argtypes = [pointer, pointer, ctypes.POINTER(ctypes.c_double)]
        g.GEOSDistance.restype = ctypes.c_int
        self.reader = g.GEOSWKTReader_create()
        need(bool(self.reader), "GEOS reader failed")
        self.regions, self.boundary = {}, None
        for name in REGIONS:
            need(len(regions[name]) == 1, "Exactly one bound source region required")
            self.regions[name] = self.read("POLYGON (" + coordinates_wkt(regions[name][0]["coordinates"]) + ")")
        self.boundary = g.GEOSBoundary(self.regions["source_IL"])
        need(bool(self.boundary), "Source IL boundary failed")

    def area(self, geometry):
        value = ctypes.c_double()
        need(self.g.GEOSArea(geometry, ctypes.byref(value)) == 1 and math.isfinite(value.value), "GEOS area failed")
        return value.value

    def read(self, wkt):
        geometry = self.g.GEOSWKTReader_read(self.reader, wkt.encode())
        need(bool(geometry), "GEOS geometry construction failed")
        try:
            need(self.g.GEOSisValid(geometry) == b"\x01" and self.g.GEOSisEmpty(geometry) == b"\x00"
                 and self.area(geometry) > 0, "Invalid/empty/nonpositive source geometry")
        except BaseException:
            self.g.GEOSGeom_destroy(geometry)
            raise
        return geometry

    def predicate(self, method, a, b):
        result = getattr(self.g, method)(a, b)
        need(result in (b"\x00", b"\x01"), "GEOS predicate failed")
        return result == b"\x01"

    def distance(self, a, b):
        value = ctypes.c_double()
        need(self.g.GEOSDistance(a, b, ctypes.byref(value)) == 1 and math.isfinite(value.value), "GEOS distance failed")
        return value.value

    def construct(self, rings):
        shells, holes, boundaries = [], [], []
        try:
            for source in rings["outer"]:
                shell = self.read("POLYGON (" + coordinates_wkt(source["coordinates"]) + ")")
                shells.append(shell)
                boundaries.append(self.g.GEOSBoundary(shell))
                need(bool(boundaries[-1]), "Source outer boundary failed")
            for source in rings["inner"]:
                holes.append(self.read("POLYGON (" + coordinates_wkt(source["coordinates"]) + ")"))
            assignment = []
            for hole in holes:
                candidates = [i for i, shell in enumerate(shells)
                              if self.predicate("GEOSContains", shell, hole) and self.distance(boundaries[i], hole) > 0]
                need(len(candidates) == 1, "Source inner hole not uniquely/strictly enclosed")
                assignment.append(candidates[0])
            parts = []
            for i, shell in enumerate(rings["outer"]):
                coords = [coordinates_wkt(shell["coordinates"])] + [coordinates_wkt(rings["inner"][j]["coordinates"])
                         for j, outer in enumerate(assignment) if outer == i]
                parts.append("(" + ",".join(coords) + ")")
            geometry = self.read("MULTIPOLYGON (" + ",".join(parts) + ")")
            shell_area = sum(self.area(shell) for shell in shells)
            hole_area = sum(self.area(hole) for hole in holes)
            actual_area = self.area(geometry)
            need(math.isclose(actual_area, shell_area - hole_area, rel_tol=1e-9, abs_tol=1e-15),
                 "Original inner holes were filled or distorted")
            return geometry, assignment, hole_area
        finally:
            for value in boundaries + shells + holes:
                self.g.GEOSGeom_destroy(value)

    def decision(self, geometry):
        if not self.predicate("GEOSContains", self.regions["source_IL"], geometry):
            return "reject-not-wholly-inside-source-IL", {}
        distances = {"source_IL_boundary": self.distance(self.boundary, geometry)}
        if distances["source_IL_boundary"] <= 0.001:
            return "reject-near-source-IL-boundary", distances
        for name in REGIONS[1:]:
            distances[name] = self.distance(self.regions[name], geometry)
            if self.predicate("GEOSIntersects", self.regions[name], geometry) or distances[name] <= 0.001:
                return "reject-intersects-or-near-" + name, distances
        return "research-qualified-complete-role-aware-multipolygon", distances

    def close(self):
        if self.boundary:
            self.g.GEOSGeom_destroy(self.boundary)
        for pointer in self.regions.values():
            self.g.GEOSGeom_destroy(pointer)
        self.g.GEOSWKTReader_destroy(self.reader)


def compare_retained_rings(retained, expected, assignments, actual_members, ways, nodes):
    need(retained["outer_count"] == len(expected["outer"]) and retained["inner_count"] == len(expected["inner"]),
         "Retained ring counts changed")
    observed_members = []
    for role in ("outer", "inner"):
        need(len(retained[role]) == len(expected[role]), "Retained source role ring count changed")
        expected_keys = {ring_key(r["node_refs"]) for r in expected[role]}
        observed_keys = [ring_key(r["node_refs"]) for r in retained[role]]
        need(set(observed_keys) == expected_keys and len(set(observed_keys)) == len(observed_keys), "Retained role cycles differ")
        allowed = {m["ref"] for m in actual_members if m["role"] == role}
        for ring in retained[role]:
            need(ring["coordinates"] == [[nodes[ref]["longitude"], nodes[ref]["latitude"]] for ref in ring["node_refs"]],
                 "Retained ring coordinates differ from original source nodes")
            assembled = []
            for member in ring["member_traversal"]:
                need(member["ref"] in allowed and member["direction"] in {"source", "reversed"}, "Retained traversal changed role")
                observed_members.append(member["ref"])
                segment = ways[member["ref"]]["refs"]
                segment = segment if member["direction"] == "source" else segment[::-1]
                need(not assembled or assembled[-1] == segment[0], "Retained traversal has invented join")
                assembled.extend(segment if not assembled else segment[1:])
            need(assembled == ring["node_refs"], "Retained traversal invents/drops an original edge")
    need(len(observed_members) == len(actual_members) and set(observed_members) == {m["ref"] for m in actual_members},
         "Original source members lost or repeated")
    expected_holes = {ring_key(hole["node_refs"]): ring_key(expected["outer"][assignments[j]]["node_refs"])
                      for j, hole in enumerate(expected["inner"])}
    need(len(retained["inner_to_outer_indices"]) == len(retained["inner"]), "Retained hole assignment count changed")
    for j, hole in enumerate(retained["inner"]):
        index = retained["inner_to_outer_indices"][j]
        need(type(index) is int and 0 <= index < len(retained["outer"]), "Invalid retained hole parent")
        need(ring_key(retained["outer"][index]["node_refs"]) == expected_holes[ring_key(hole["node_refs"])],
             "Retained hole parent changed")


def audit(pbf, corpus, geometry_base, extractor):
    start = time.monotonic()
    source_hash, corpus_hash = digest(pbf), digest(corpus)
    report_path = corpus.with_name("report.json")
    report = json.loads(report_path.read_text())
    need(report["source_pbf_sha256"] == source_hash and report["source_pbf_bytes"] == pbf.stat().st_size
         and report["ndjson_sha256"] == corpus_hash, "Source/corpus research hash mismatch")
    helper = Path(__file__).with_name("research_relation_geography.py")
    node_reference = Path(__file__).with_name("research_node_geography.py")
    need(report["research_script_sha256"] == digest(helper) and report["extractor_sha256"] == digest(extractor),
         "Executed relation/helper reference changed")
    need(report["reference_node_helper_sha256"] == digest(node_reference), "Geometry-provenance reference changed")
    spec = importlib.util.spec_from_file_location("independent_relation_reference", extractor)
    reference = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(reference)
    relations, ways, nodes = original_objects(pbf, reference)
    decisions = {int(r["source_id"].rsplit(":", 1)[1]): r for r in report["decisions"]}
    need(set(decisions) == set(relations) and len(decisions) == len(report["decisions"]),
         "Reported source relation cohort omits/duplicates source candidates")
    rows = [json.loads(line) for line in corpus.read_text().splitlines() if line.strip()]
    need(len(rows) == report["raw_phone_rows"] and len({r["id"] for r in rows}) == len(rows), "Retained row identity/count mismatch")
    source_regions, geometry = validate_geometry_provenance(geometry_base, source_hash)
    spatial, checks, replay = IndependentSpatial(source_regions), collections.Counter(), {}
    expected_rows = {}
    try:
        for identifier, actual in relations.items():
            tags, members = actual["tags"], actual["members"]
            need(tags.get("type") == "multipolygon" and tags.get("area", "").strip().lower() != "no", "Source is not area multipolygon")
            rings = source_cycles(members, ways, nodes)
            geometry_pointer, assignments, hole_area = spatial.construct(rings)
            try:
                decision, distances = spatial.decision(geometry_pointer)
            finally:
                spatial.g.GEOSGeom_destroy(geometry_pointer)
            observed = decisions[identifier]
            name = next((clean(tags[k]) for k in ("name:he", "name", "name:en") if tags.get(k, "").strip()), "")
            need(observed["decision"] == decision and observed["source_updated_at"] == actual["timestamp"]
                 and observed["source_url"] == f"https://www.openstreetmap.org/relation/{identifier}"
                 and observed["name"] == name and observed["app_accepted"] is False, "Reported source relation decision changed")
            replay[identifier] = dict(decision=decision, outer_rings=len(rings["outer"]), inner_holes=len(rings["inner"]),
                                      hole_area_degrees_squared=hole_area, source_distances_degrees=distances)
            checks["all_source_relation_decisions_independently_replayed"] += 1
            raw_phones = {k: tags[k] for k in ("contact:phone", "phone") if tags.get(k)}
            candidates = {}
            for raw in raw_phones.values():
                parsed, _ = reference.phones(raw)
                for phone in parsed:
                    candidates.setdefault((phone["phone"], phone["extension"]), phone)
            country_fields = {k: tags[k] for k in COUNTRY_KEYS if tags.get(k)}
            values = [tags[k].strip().upper() for k in COUNTRY_KEYS if tags.get(k, "").strip()]
            need(all(value in IL_VALUES for value in values), "Source explicit non-IL relation needs separate exclusion audit")
            country = values[0] if values else None
            candidates = {k: v for k, v in candidates.items() if v["e164"] or country in IL_VALUES}
            if decision == "research-qualified-complete-role-aware-multipolygon":
                expected_rows[identifier] = set(candidates)
            for row in (row for row in rows if row["source_id"] == f"osm:relation:{identifier}"):
                need(decision == "research-qualified-complete-role-aware-multipolygon", "Rejected source relation emitted")
                need(row["source_tags"] == tags and row["source_members"] == members
                     and row["source_updated_at"] == actual["timestamp"] and row["source_version"] == actual["version"]
                     and row["source_url"] == f"https://www.openstreetmap.org/relation/{identifier}", "Original relation source fields changed")
                checks["exact_original_relation_tags_members_id_url_version_timestamp_rows"] += 1
                source_way_ids = {m["ref"] for m in members}
                need(set(row["source_member_ways"]) == {str(ref) for ref in source_way_ids}, "Retained member way set changed")
                used_nodes = {ref for member in source_way_ids for ref in ways[member]["refs"]}
                for member in source_way_ids:
                    retained = row["source_member_ways"][str(member)]
                    need(retained["source_id"] == f"osm:way:{member}" and retained["source_updated_at"] == ways[member]["timestamp"]
                         and retained["node_refs"] == ways[member]["refs"], "Retained original member way fields changed")
                need(row["source_geometry_nodes"] == {str(ref): nodes[ref] for ref in used_nodes}, "Original node coordinates/timestamps changed")
                checks["exact_original_member_ways_and_node_fields_rows"] += 1
                compare_retained_rings(row["relation_geometry"], rings, assignments, members, ways, nodes)
                checks["exact_role_cycles_traversal_source_edges_true_holes_rows"] += 1
                need(row["name"] == name and row["category"] == reference.category(tags), "Source name/category changed")
                city = clean(tags.get("addr:city", ""))
                address = clean(tags.get("addr:full", "")) or " ".join(v for v in
                    (clean(tags.get("addr:street", "")), clean(tags.get("addr:housenumber", "")), city) if v)
                need(row["city"] == city and row["address"] == address, "Source city/address changed")
                phone = candidates.get((row["phone"], row["extension"]))
                need(phone is not None and all(row[k] == value for k, value in phone.items())
                     and row["source_phones"] == raw_phones, "Source raw/canonical phone fields changed")
                need(row["source_country_fields"] == country_fields and row["country_tag"] == country
                     and row["country_admission"] == ("explicit-IL" if country else "missing-source-country-tag")
                     and row["country_inferred"] is False, "Source country metadata changed/inferred")
                checks["exact_source_name_address_phone_extension_country_rows"] += 1
                need(row["license_id"] == "ODbL-1.0" and row["status"] == "RESEARCH_ONLY_NOT_APP_ACCEPTED"
                     and row["geographic_scope"] == SCOPE and row["geometry_admission"] == decision
                     and row["margin_degrees"] == 0.001 and row["verification"] == "source-listed-not-phone-confirmed"
                     and row["geometry_input_sha256"] == geometry["input_sha256"]["research-region-outer-rings.json"],
                     "Retained research license/geometry provenance changed")
                checks["research_status_license_geometry_provenance_rows"] += 1
                checks["independent_complete_polygon_valid_area_containment_margin_exclusion_rows"] += 1
        observed_rows = collections.defaultdict(set)
        for row in rows:
            need(re.fullmatch(r"osm:relation:[1-9][0-9]*", row["source_id"]) is not None, "Invalid retained source relation ID")
            identifier = int(row["source_id"].rsplit(":", 1)[1])
            need(identifier in relations, "Unseen source relation emitted")
            observed_rows[identifier].add((row["phone"], row["extension"]))
        need(dict(observed_rows) == expected_rows, "Source-qualified relation phone identities omitted/fabricated")
    finally:
        spatial.close()
    need(digest(pbf) == source_hash and digest(corpus) == corpus_hash, "Source/corpus changed during audit")
    emitted_way_ids = {m["ref"] for identifier in observed_rows for m in relations[identifier]["members"]}
    emitted_node_ids = {ref for identifier in emitted_way_ids for ref in ways[identifier]["refs"]}
    return dict(status="PASS_RESEARCH_SOURCE_AND_HOLE_AWARE_RELATION_AUDIT_ONLY",
        observed_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(), elapsed_seconds=round(time.monotonic() - start, 3),
        source_pbf_sha256=source_hash, source_pbf_bytes=pbf.stat().st_size, research_ndjson_sha256=corpus_hash,
        research_report_sha256=digest(report_path), audit_script_sha256=digest(Path(__file__)),
        reviewed_relation_helper_sha256=digest(helper), reference_extractor_sha256=digest(extractor),
        reference_node_research_sha256=digest(node_reference),
        geometry_input_sha256=geometry["input_sha256"], source_relations_reconsidered=len(relations),
        original_member_ways_reread=len(ways), original_referenced_nodes_reread=len(nodes),
        source_relation_rows=len(rows), unique_original_source_relations_found=len(observed_rows),
        unique_original_member_ways_found=len(emitted_way_ids), unique_original_referenced_nodes_found=len(emitted_node_ids),
        emitted_phone_rows=len(rows), emitted_source_relations=len(observed_rows), checks_passed=dict(sorted(checks.items())),
        all_source_relation_replays={str(key): value for key, value in sorted(replay.items())}, geos_version=spatial.version,
        app_records_added=0, app_rows_added=0, source_mutations=0, automatic_merges=0,
        method=["Actual PBF relation category-key scan confirms all five named-phone source relations",
                "Direct PBF member-way/node ID passes, deriving dependencies from original relation members",
                "Independent endpoint-cycle assembly and exact retained ref/edge/coordinate/hole-parent comparisons",
                "Separate GEOS bindings construct full true-hole multipolygons and replay every acceptance/rejection",
                "Source phone/category parsing and bound geometry-provenance validation reuse disclosed reference contracts"],
        limits=["Source fidelity does not establish current business activity, phone ownership or reachability.",
                "Shared category/phone and source geometry-provenance contracts are not independently reimplemented.",
                "Angular distances and hole areas use degree units, not surveyed meters or legal borders.",
                "Original relation versions are compared; inventory member-way versions were not retained, so no such comparison is claimed.",
                "This audit is not production admission, native Android verification or publication approval."])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pbf", type=Path)
    parser.add_argument("corpus", type=Path)
    parser.add_argument("geometry_base", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--extractor", type=Path, required=True)
    args = parser.parse_args()
    need(not args.output.exists(), "Output already exists; retain it and choose another path")
    result = audit(args.pbf, args.corpus, args.geometry_base, args.extractor)
    with args.output.open("x", encoding="utf-8") as handle:
        json.dump(result, handle, ensure_ascii=True, indent=2)
        handle.write("\n")
    print(json.dumps({k: result[k] for k in ("status", "source_relations_reconsidered", "original_member_ways_reread",
                                            "original_referenced_nodes_reread", "emitted_phone_rows")}))


if __name__ == "__main__":
    main()
