#!/usr/bin/env python3
"""Review complete closed-way polygons without changing production admission.

Only source-listed business ways with every ring reference/coordinate available
are considered. Entire polygons, rather than centroids or vertices, must clear
the existing conservative source-geometry policy. No source country is inferred.
"""
from __future__ import annotations

import argparse
import collections
import ctypes
import datetime as dt
import importlib.util
import json
from pathlib import Path
import re
import tempfile

from research_node_geography import (
    COUNTRY_FIELDS, GeometryScope, IL_TAGS, RELATIONS, country_decision, sha256,
    valid_coordinates, validate_geometry_provenance,
)

STATUS = "RESEARCH_ONLY_NOT_APP_ACCEPTED"
SCOPE = "research-complete-closed-way-source-IL-interior-minus-filled-WB-and-Gaza"


def complete_ring(refs: object, nodes: dict) -> tuple[str | None, list[list[float]]]:
    """Keep ordered source references; never close or repair a broken ring."""
    if (not isinstance(refs, list) or len(refs) < 4
            or any(type(ref) is not int or ref <= 0 for ref in refs)
            or refs[0] != refs[-1] or len(set(refs[:-1])) < 3):
        return "reject-open-or-invalid-source-ring", []
    coordinates = []
    for ref in refs:
        node = nodes.get(str(ref))
        if not isinstance(node, dict) or node.get("source_id") != f"osm:node:{ref}":
            return "reject-missing-or-mismatched-source-node", []
        lon, lat = node.get("longitude"), node.get("latitude")
        if node.get("valid_coordinates") is not True or not valid_coordinates(lon, lat):
            return "reject-invalid-source-node-coordinate", []
        coordinates.append([lon, lat])
    if len({tuple(pair) for pair in coordinates[:-1]}) < 3:
        return "reject-collapsed-source-ring", []
    return None, coordinates


class ClosedWayScope(GeometryScope):
    """Apply the source policy to every part of the filled source polygon."""

    def __init__(self, rings: dict, margin_degrees: float = 0.001):
        super().__init__(rings, margin_degrees)
        self.g.GEOSisEmpty.argtypes = [ctypes.c_void_p]
        self.g.GEOSisEmpty.restype = ctypes.c_char
        self.g.GEOSArea.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_double)]
        self.g.GEOSArea.restype = ctypes.c_int

    def classify_ring(self, refs: list[int], nodes: dict, tags: dict) -> tuple[str, list]:
        if str(tags.get("area", "")).strip().lower() == "no":
            return "reject-explicit-area-no", []
        error, coordinates = complete_ring(refs, nodes)
        if error:
            return error, []
        wkt = "POLYGON ((" + ",".join(f"{lon} {lat}" for lon, lat in coordinates) + "))"
        polygon = self.g.GEOSWKTReader_read(self.reader, wkt.encode())
        if not polygon:
            return "reject-source-polygon-construction", []
        try:
            valid = self.g.GEOSisValid(polygon)
            empty = self.g.GEOSisEmpty(polygon)
            if valid not in (b"\x00", b"\x01") or empty not in (b"\x00", b"\x01"):
                raise RuntimeError("GEOS source polygon validation failed")
            area = ctypes.c_double()
            if self.g.GEOSArea(polygon, ctypes.byref(area)) != 1:
                raise RuntimeError("GEOS source polygon area calculation failed")
            if valid != b"\x01" or empty != b"\x00" or not area.value > 0:
                return "reject-invalid-empty-or-zero-area-source-polygon", []
            if not self.predicate("GEOSContains", self.polygons["source_IL"], polygon):
                return "reject-not-wholly-inside-source-IL", []
            if self.distance(self.boundaries["source_IL"], polygon) <= self.margin:
                return "reject-near-source-IL-boundary", []
            for name in RELATIONS:
                if name == "source_IL":
                    continue
                if (self.predicate("GEOSIntersects", self.polygons[name], polygon)
                        or self.distance(self.polygons[name], polygon) <= self.margin):
                    return "reject-intersects-or-near-" + name, []
            return "research-qualified-complete-source-polygon", coordinates
        finally:
            self.g.GEOSGeom_destroy(polygon)


def inventory_inputs(base: Path, pbf: Path, source_hash: str) -> tuple[list, dict, dict]:
    report = json.loads((base / "report.json").read_text())
    if (report.get("source_pbf_sha256") != source_hash
            or report.get("source_pbf_bytes") != pbf.stat().st_size
            or report.get("app_rows_added") != 0
            or report.get("geographically_admitted_records") != 0):
        raise ValueError("Matching research-only source inventory required")
    inputs = {}
    for name in ("source-objects.ndjson", "geometry-nodes.json", "relation-member-ways.json"):
        inputs[name] = sha256(base / name)
        if inputs[name] != report.get("files_sha256", {}).get(name):
            raise ValueError("Retained source inventory changed: " + name)
    inputs["report.json"] = sha256(base / "report.json")
    rows = [json.loads(line) for line in (base / "source-objects.ndjson").read_text().splitlines() if line.strip()]
    if len(rows) != report.get("source_objects") or len({r.get("source_id") for r in rows}) != len(rows):
        raise ValueError("Source inventory count or identity mismatch")
    return rows, json.loads((base / "geometry-nodes.json").read_text()), inputs


def run(pbf: Path, inventory: Path, geometry_base: Path, output: Path, extractor_path: Path) -> dict:
    if output.exists():
        raise ValueError("Output exists; never overwrite retained research evidence")
    source_hash = sha256(pbf)
    rings, geometry = validate_geometry_provenance(geometry_base, source_hash)
    objects, nodes, inputs = inventory_inputs(inventory, pbf, source_hash)
    spec = importlib.util.spec_from_file_location("closed_way_existing_extractor", extractor_path)
    extract = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(extract)
    counts, rows = collections.Counter(), []
    observed = dt.datetime.now(dt.timezone.utc).isoformat()
    scope = ClosedWayScope(rings)
    try:
        for obj in objects:
            source_id = obj.get("source_id", "")
            if source_id.startswith("osm:relation:"):
                counts["relations_omitted_no_role_aware_polygon_assembly"] += 1
                continue
            match = re.fullmatch(r"osm:way:([1-9][0-9]*)", source_id)
            if not match or obj.get("source_url") != f"https://www.openstreetmap.org/way/{match[1]}":
                raise ValueError("Exact source way identity/URL required")
            timestamp = dt.datetime.fromisoformat(obj["source_updated_at"].replace("Z", "+00:00"))
            if timestamp.tzinfo is None:
                raise ValueError("Source way timestamp must retain timezone")
            tags = obj["source_tags"]
            category = extract.category(tags)
            name = next((extract.clean(tags[k]) for k in ("name:he", "name", "name:en") if tags.get(k, "").strip()), "")
            counts["source_named_phone_ways_examined"] += 1
            if not category or not name:
                counts["reject-no-source-business-category-or-name"] += 1
                continue
            country_method, country = country_decision(tags)
            if country_method == "reject-explicit-non-IL":
                counts[country_method] += 1
                continue
            decision, coordinates = scope.classify_ring(obj.get("node_refs"), nodes, tags)
            counts[decision] += 1
            if decision != "research-qualified-complete-source-polygon":
                continue
            phones, seen = [], set()
            raw_phones = {k: tags[k] for k in ("contact:phone", "phone") if tags.get(k)}
            for value in raw_phones.values():
                accepted, rejected = extract.phones(value)
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
                value for value in (extract.clean(tags.get("addr:street", "")),
                                    extract.clean(tags.get("addr:housenumber", "")), city) if value)
            counts["emitted_source_ways"] += 1
            for phone in phones:
                rows.append(dict(
                    id=source_id + ":" + phone["phone"] + (";ext=" + phone["extension"] if phone["extension"] else ""),
                    source_id=source_id, source_url=obj["source_url"],
                    source_updated_at=obj["source_updated_at"], source_version=obj["source_version"],
                    name=name, **phone, city=city, address=address, category=category,
                    source_tags=tags, source_phones=raw_phones,
                    country_tag=country, source_country_fields={k: tags[k] for k in COUNTRY_FIELDS if tags.get(k)},
                    country_admission=country_method, country_inferred=False,
                    geographic_scope=SCOPE, source_node_refs=obj["node_refs"],
                    complete_source_ring_coordinates=coordinates,
                    geometry_input_sha256=geometry["input_sha256"]["research-region-outer-rings.json"],
                    margin_degrees=scope.margin, geometry_admission=decision,
                    imported_at=observed, license_id="ODbL-1.0",
                    verification="source-listed-not-phone-confirmed", status=STATUS,
                ))
    finally:
        scope.close()
    rows.sort(key=lambda row: (int(row["source_id"].rsplit(":", 1)[1]), row["phone"], row["extension"]))
    if sha256(pbf) != source_hash:
        raise ValueError("Source PBF changed during review")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="closed-way-geography-", dir=output.parent) as temporary:
        temp = Path(temporary)
        ndjson = temp / "research-way-businesses.ndjson"
        with ndjson.open("x", encoding="utf-8") as handle:
            for row in rows:
                handle.write(json.dumps(row, ensure_ascii=True, sort_keys=True) + "\n")
        report = dict(
            status=STATUS, scope=SCOPE, observed_at_utc=observed,
            source_pbf_sha256=source_hash, source_pbf_bytes=pbf.stat().st_size,
            input_inventory_sha256=inputs, geometry_provenance=geometry,
            research_script_sha256=sha256(Path(__file__)), extractor_sha256=sha256(extractor_path),
            node_research_sha256=sha256(Path(__file__).with_name("research_node_geography.py")),
            raw_phone_rows=len(rows), source_ways=len({r["source_id"] for r in rows}),
            unique_base_phones=len({r["phone"] for r in rows}),
            counts=dict(sorted(counts.items())), ndjson_sha256=sha256(ndjson),
            source_country_tags=dict(collections.Counter(r["country_tag"] or "<missing>" for r in rows)),
            source_object_years=dict(sorted(collections.Counter(r["source_updated_at"][:4] for r in rows).items())),
            app_rows_added=0, source_rows_merged=0, automatically_repaired_rings=0,
            attribution="© OpenStreetMap contributors", license_id="ODbL-1.0",
            limits=["Full source polygons clear conservative source-geometry rules; no official/legal country or border claim.",
                    "The 0.001-degree angular margin is not a distance in meters.",
                    "No centroid, closing edge, missing node, hole, country tag or phone was invented.",
                    "Standalone closed ways only; all relations omitted without role-aware complete assembly.",
                    "Filled polygons are conservative envelopes for source admission, not surveyed premises.",
                    "No current ownership/reachability, active-business or complete national coverage claim.",
                    "Existing production admission, app, user data, signing, CI and release safeguards are unchanged."],
        )
        (temp / "report.json").write_text(json.dumps(report, ensure_ascii=True, indent=2) + "\n")
        (temp / "LICENSE-NOTICE.txt").write_text(
            "© OpenStreetMap contributors\nODbL1.0 https://opendatacommons.org/licenses/odbl/1-0/\n"
            "Owner-uploaded Geofabrik/OSM source snapshot; source-listed, not phone-confirmed.\n"
            "Research derived data remains separate from app code and personal contacts.\n")
        Path(temporary).rename(output)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pbf", type=Path)
    parser.add_argument("inventory", type=Path)
    parser.add_argument("geometry_base", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--extractor", type=Path, required=True)
    args = parser.parse_args()
    report = run(args.pbf, args.inventory, args.geometry_base, args.output, args.extractor)
    print(json.dumps({k: report[k] for k in ("raw_phone_rows", "source_ways", "unique_base_phones", "counts", "app_rows_added")}))


if __name__ == "__main__":
    main()
