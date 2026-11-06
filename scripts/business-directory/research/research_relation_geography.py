#!/usr/bin/env python3
"""Role-aware complete multipolygon research; never production admission.

Every source way must be consumed once using its original edges. Open segments
are joined only at matching source node references with degree two. Inner holes
must belong to exactly one outer shell and remain real holes. No repair, inferred
country, synthetic closing edge, centroid or recursive relation guess is used.
"""
from __future__ import annotations

import argparse
import collections
import ctypes
import datetime as dt
import importlib.util
import json
import math
from pathlib import Path
import re
import tempfile

from research_node_geography import (
    COUNTRY_FIELDS, GeometryScope, IL_TAGS, RELATIONS, country_decision, sha256,
    validate_geometry_provenance,
)
from research_way_geography import complete_ring, inventory_inputs

STATUS = "RESEARCH_ONLY_NOT_APP_ACCEPTED"
SCOPE = "research-complete-role-aware-multipolygon-source-IL-minus-filled-WB-and-Gaza"


class AssemblyReject(ValueError):
    pass


def assemble_role(members: list, ways: dict, nodes: dict, role: str) -> list:
    """Return exact source rings/traversal, never an invented closing edge."""
    pending, rings = {}, []
    for member in members:
        if member["role"] != role:
            continue
        way_id = member["ref"]
        source = ways.get(str(way_id))
        if not isinstance(source, dict) or source.get("source_id") != f"osm:way:{way_id}":
            raise AssemblyReject("reject-missing-or-mismatched-member-way")
        refs = source.get("node_refs")
        if (not isinstance(refs, list) or len(refs) < 2
                or any(type(ref) is not int or ref <= 0 for ref in refs)):
            raise AssemblyReject("reject-invalid-member-way-references")
        if refs[0] == refs[-1]:
            error, coordinates = complete_ring(refs, nodes)
            if error:
                raise AssemblyReject(error)
            rings.append({"node_refs": list(refs), "coordinates": coordinates,
                          "member_traversal": [{"ref": way_id, "direction": "source"}]})
        else:
            pending[way_id] = list(refs)
    incident = collections.defaultdict(set)
    for way_id, refs in pending.items():
        incident[refs[0]].add(way_id)
        incident[refs[-1]].add(way_id)
    if any(len(value) != 2 for value in incident.values()):
        raise AssemblyReject("reject-open-or-ambiguous-role-endpoints")
    while pending:
        first = min(pending)
        refs = pending.pop(first)
        assembled = list(refs)
        traversal = [{"ref": first, "direction": "source"}]
        while assembled[-1] != assembled[0]:
            available = incident[assembled[-1]] & pending.keys()
            if len(available) != 1:
                raise AssemblyReject("reject-open-or-ambiguous-role-traversal")
            way_id = next(iter(available))
            refs = pending.pop(way_id)
            forward = refs[0] == assembled[-1]
            segment = refs if forward else list(reversed(refs))
            if segment[0] != assembled[-1]:
                raise AssemblyReject("reject-disconnected-source-segment")
            assembled.extend(segment[1:])
            traversal.append({"ref": way_id, "direction": "source" if forward else "reversed"})
        error, coordinates = complete_ring(assembled, nodes)
        if error:
            raise AssemblyReject(error)
        rings.append({"node_refs": assembled, "coordinates": coordinates,
                      "member_traversal": traversal})
    return rings


def source_rings(members: object, ways: dict, nodes: dict) -> dict:
    if not isinstance(members, list) or not members:
        raise AssemblyReject("reject-missing-relation-members")
    seen = set()
    for member in members:
        if (not isinstance(member, dict) or member.get("type") != "w"
                or member.get("role") not in {"outer", "inner"}
                or type(member.get("ref")) is not int or member["ref"] <= 0):
            raise AssemblyReject("reject-nested-non-way-or-ambiguous-role-member")
        if member["ref"] in seen:
            raise AssemblyReject("reject-repeated-source-member-way")
        seen.add(member["ref"])
    result = {role: assemble_role(members, ways, nodes, role) for role in ("outer", "inner")}
    if not result["outer"]:
        raise AssemblyReject("reject-no-source-outer-shell")
    consumed = [part["ref"] for role in result.values() for ring in role
                for part in ring["member_traversal"]]
    if len(consumed) != len(seen) or set(consumed) != seen:
        raise AssemblyReject("reject-unconsumed-or-repeated-source-edge")
    return result


def ring_wkt(coordinates: list) -> str:
    return "(" + ",".join(f"{lon} {lat}" for lon, lat in coordinates) + ")"


class RelationScope(GeometryScope):
    def __init__(self, rings: dict, margin_degrees: float = 0.001):
        super().__init__(rings, margin_degrees)
        self.g.GEOSisEmpty.argtypes = [ctypes.c_void_p]
        self.g.GEOSisEmpty.restype = ctypes.c_char
        self.g.GEOSArea.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_double)]
        self.g.GEOSArea.restype = ctypes.c_int

    def read_valid_area(self, text: str):
        polygon = self.g.GEOSWKTReader_read(self.reader, text.encode())
        if not polygon:
            raise AssemblyReject("reject-source-polygon-construction")
        area = ctypes.c_double()
        valid, empty = self.g.GEOSisValid(polygon), self.g.GEOSisEmpty(polygon)
        if valid not in (b"\x00", b"\x01") or empty not in (b"\x00", b"\x01"):
            self.g.GEOSGeom_destroy(polygon)
            raise RuntimeError("GEOS validation failed")
        if self.g.GEOSArea(polygon, ctypes.byref(area)) != 1:
            self.g.GEOSGeom_destroy(polygon)
            raise RuntimeError("GEOS source polygon area failed")
        if valid != b"\x01" or empty != b"\x00" or not math.isfinite(area.value) or area.value <= 0:
            self.g.GEOSGeom_destroy(polygon)
            raise AssemblyReject("reject-invalid-empty-or-zero-area-multipolygon")
        return polygon

    def build_geometry(self, source: dict) -> tuple[object, dict]:
        shells, holes, shell_boundaries = [], [], []
        try:
            for ring in source["outer"]:
                shell = self.read_valid_area("POLYGON (" + ring_wkt(ring["coordinates"]) + ")")
                shells.append(shell)
                boundary = self.g.GEOSBoundary(shell)
                if not boundary:
                    raise RuntimeError("GEOS shell boundary failed")
                shell_boundaries.append(boundary)
            for ring in source["inner"]:
                holes.append(self.read_valid_area("POLYGON (" + ring_wkt(ring["coordinates"]) + ")"))
            assignments = []
            for hole in holes:
                containing = [i for i, shell in enumerate(shells)
                              if self.predicate("GEOSContains", shell, hole)
                              and self.distance(shell_boundaries[i], hole) > 0]
                if len(containing) != 1:
                    raise AssemblyReject("reject-orphan-touching-or-ambiguous-inner-hole")
                assignments.append(containing[0])
            parts = []
            for index, shell in enumerate(source["outer"]):
                ring_texts = [ring_wkt(shell["coordinates"])] + [
                    ring_wkt(source["inner"][j]["coordinates"]) for j, outer in enumerate(assignments)
                    if outer == index]
                parts.append("(" + ",".join(ring_texts) + ")")
            polygon = self.read_valid_area("MULTIPOLYGON (" + ",".join(parts) + ")")
            return polygon, {**source, "inner_to_outer_indices": assignments,
                             "outer_count": len(shells), "inner_count": len(holes)}
        finally:
            for pointer in shell_boundaries + holes + shells:
                self.g.GEOSGeom_destroy(pointer)

    def classify_relation(self, members: object, ways: dict, nodes: dict, tags: dict) -> tuple[str, dict | None]:
        if tags.get("type") != "multipolygon" or str(tags.get("area", "")).strip().lower() == "no":
            return "reject-not-source-area-multipolygon", None
        polygon = None
        try:
            source = source_rings(members, ways, nodes)
            polygon, geometry = self.build_geometry(source)
            if not self.predicate("GEOSContains", self.polygons["source_IL"], polygon):
                return "reject-not-wholly-inside-source-IL", None
            if self.distance(self.boundaries["source_IL"], polygon) <= self.margin:
                return "reject-near-source-IL-boundary", None
            for name in RELATIONS:
                if name == "source_IL":
                    continue
                if (self.predicate("GEOSIntersects", self.polygons[name], polygon)
                        or self.distance(self.polygons[name], polygon) <= self.margin):
                    return "reject-intersects-or-near-" + name, None
            return "research-qualified-complete-role-aware-multipolygon", geometry
        except AssemblyReject as error:
            return str(error), None
        finally:
            if polygon:
                self.g.GEOSGeom_destroy(polygon)


def run(pbf: Path, inventory: Path, geometry_base: Path, output: Path, extractor_path: Path) -> dict:
    if output.exists():
        raise ValueError("Output exists; retain it and choose another path")
    source_hash = sha256(pbf)
    rings, geometry_provenance = validate_geometry_provenance(geometry_base, source_hash)
    objects, nodes, inventory_hashes = inventory_inputs(inventory, pbf, source_hash)
    ways = json.loads((inventory / "relation-member-ways.json").read_text())
    spec = importlib.util.spec_from_file_location("relation_existing_extractor", extractor_path)
    extract = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(extract)
    scope = RelationScope(rings)
    rows, decisions, counts = [], [], collections.Counter()
    observed = dt.datetime.now(dt.timezone.utc).isoformat()
    try:
        for obj in objects:
            if not obj.get("source_id", "").startswith("osm:relation:"):
                continue
            source_id = obj["source_id"]
            match = re.fullmatch(r"osm:relation:([1-9][0-9]*)", source_id)
            if not match or obj["source_url"] != f"https://www.openstreetmap.org/relation/{match[1]}":
                raise ValueError("Exact source relation ID/URL required")
            stamp = dt.datetime.fromisoformat(obj["source_updated_at"].replace("Z", "+00:00"))
            if stamp.tzinfo is None:
                raise ValueError("Source relation timestamp needs timezone")
            tags = obj["source_tags"]
            country_method, country = country_decision(tags)
            counts["source_relations_examined"] += 1
            if country_method == "reject-explicit-non-IL":
                decision, geometry = country_method, None
            else:
                decision, geometry = scope.classify_relation(obj["members"], ways, nodes, tags)
            counts[decision] += 1
            decisions.append({"source_id": source_id, "source_url": obj["source_url"],
                              "name": obj["preferred_source_name"], "source_updated_at": obj["source_updated_at"],
                              "decision": decision, "app_accepted": False})
            if geometry is None:
                continue
            category = extract.category(tags)
            name = next((extract.clean(tags[k]) for k in ("name:he", "name", "name:en") if tags.get(k, "").strip()), "")
            if not category or not name:
                raise ValueError("Admitted inventory relation lacks original category/name")
            raw_phones = {k: tags[k] for k in ("contact:phone", "phone") if tags.get(k)}
            phones, seen = [], set()
            for raw in raw_phones.values():
                accepted, rejected = extract.phones(raw)
                counts.update("phone_rejected_" + reason for reason in rejected)
                for phone in accepted:
                    key = phone["phone"], phone["extension"]
                    if key not in seen and (phone["e164"] or country in IL_TAGS):
                        phones.append(phone)
                        seen.add(key)
            if not phones:
                counts["reject-no-qualifying-source-phone"] += 1
                continue
            city = extract.clean(tags.get("addr:city", ""))
            address = extract.clean(tags.get("addr:full", "")) or " ".join(
                x for x in (extract.clean(tags.get("addr:street", "")), extract.clean(tags.get("addr:housenumber", "")), city) if x)
            source_ways = {str(member["ref"]): ways[str(member["ref"])] for member in obj["members"]}
            used_nodes = {ref for way in source_ways.values() for ref in way["node_refs"]}
            counts["emitted_source_relations"] += 1
            for phone in phones:
                rows.append(dict(
                    id=source_id + ":" + phone["phone"] + (";ext=" + phone["extension"] if phone["extension"] else ""),
                    source_id=source_id, source_url=obj["source_url"], source_updated_at=obj["source_updated_at"],
                    source_version=obj["source_version"], source_tags=tags, source_members=obj["members"],
                    source_member_ways=source_ways, source_geometry_nodes={str(ref): nodes[str(ref)] for ref in sorted(used_nodes)},
                    relation_geometry=geometry, name=name, **phone, source_phones=raw_phones,
                    category=category, city=city, address=address, country_tag=country,
                    source_country_fields={k: tags[k] for k in COUNTRY_FIELDS if tags.get(k)},
                    country_admission=country_method, country_inferred=False, geographic_scope=SCOPE,
                    geometry_admission=decision, margin_degrees=scope.margin,
                    geometry_input_sha256=geometry_provenance["input_sha256"]["research-region-outer-rings.json"],
                    imported_at=observed, license_id="ODbL-1.0", verification="source-listed-not-phone-confirmed", status=STATUS,
                ))
    finally:
        scope.close()
    if sha256(pbf) != source_hash:
        raise ValueError("PBF changed during relation research")
    rows.sort(key=lambda r: (int(r["source_id"].rsplit(":", 1)[1]), r["phone"], r["extension"]))
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="relation-geography-", dir=output.parent) as temporary:
        temp = Path(temporary)
        ndjson = temp / "research-relation-businesses.ndjson"
        with ndjson.open("x", encoding="utf-8") as f:
            for row in rows:
                f.write(json.dumps(row, ensure_ascii=True, sort_keys=True) + "\n")
        report = dict(status=STATUS, scope=SCOPE, observed_at_utc=observed,
                      source_pbf_sha256=source_hash, source_pbf_bytes=pbf.stat().st_size,
                      inventory_sha256=inventory_hashes, geometry_provenance=geometry_provenance,
                      research_script_sha256=sha256(Path(__file__)), extractor_sha256=sha256(extractor_path),
                      reference_way_helper_sha256=sha256(Path(__file__).with_name("research_way_geography.py")),
                      reference_node_helper_sha256=sha256(Path(__file__).with_name("research_node_geography.py")),
                      raw_phone_rows=len(rows), source_relations=len({r["source_id"] for r in rows}),
                      unique_base_phones=len({r["phone"] for r in rows}), counts=dict(sorted(counts.items())),
                      decisions=decisions, ndjson_sha256=sha256(ndjson), app_records_added=0,
                      source_rows_merged=0, repaired_rings=0, inferred_country_tags=0,
                      license_id="ODbL-1.0", attribution="© OpenStreetMap contributors",
                      limits=["Source geometry is not official/legal country membership or surveyed premises.",
                              "All roles/dependencies must be complete; nested/non-way/ambiguous members are omitted.",
                              "Open source segments join only at equal source node IDs with degree2; no closing edge guessed.",
                              "Inner holes retain unique original outer assignment, without filling or topology repair.",
                              "Angular0.001degree distances are not meters; no centroid/vertex-only admission.",
                              "Source names/phones and object dates do not establish current ownership, activity or complete coverage.",
                              "Research only; production packager, app, personal contacts, signing and CI safeguards unchanged."])
        (temp / "report.json").write_text(json.dumps(report, ensure_ascii=True, indent=2) + "\n")
        (temp / "LICENSE-NOTICE.txt").write_text("© OpenStreetMap contributors\nODbL1.0 https://opendatacommons.org/licenses/odbl/1-0/\nOwner-uploaded Geofabrik/OSM source snapshot. Research-only; not phone-confirmed.\n")
        Path(temporary).rename(output)
    return report


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ("pbf", "inventory", "geometry_base", "output"):
        p.add_argument(name, type=Path)
    p.add_argument("--extractor", type=Path, required=True)
    a = p.parse_args()
    report = run(a.pbf, a.inventory, a.geometry_base, a.output, a.extractor)
    print(json.dumps({k: report[k] for k in ("raw_phone_rows", "source_relations", "unique_base_phones", "counts", "decisions", "app_records_added")}))


if __name__ == "__main__":
    main()
