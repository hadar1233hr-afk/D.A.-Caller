#!/usr/bin/env python3
"""Inventory source WAY/RELATION business geometry without accepting app records.

This offline research preserves source tags and references. Coordinates establish
dependency availability only: no centroid, border admission or business ownership
is inferred. Complete polygon validation remains a separate review step.
"""
from __future__ import annotations

import argparse
import collections
import datetime as dt
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import tempfile


STATUS = "RESEARCH_INVENTORY_ONLY_NOT_GEOGRAPHICALLY_ADMITTED_OR_APP_ACCEPTED"
HERE = Path(__file__).resolve().parent


def sha256(path: Path) -> str:
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def source_name(tags: dict, extract) -> str:
    return next((extract.clean(tags[key]) for key in ("name:he", "name", "name:en")
                 if tags.get(key, "").strip()), "")


def structural_ring(node_refs: list[int]) -> bool:
    """Reference closure is necessary, never sufficient for a valid polygon."""
    return (len(node_refs) >= 4 and node_refs[0] == node_refs[-1]
            and len(set(node_refs[:-1])) >= 3)


def dependency_status(refs: list[int], nodes: dict[int, dict]) -> dict:
    missing = sorted(set(refs) - nodes.keys())
    invalid = sorted({ref for ref in refs if ref in nodes and not nodes[ref]["valid_coordinates"]})
    return {"reference_closed": bool(refs and refs[0] == refs[-1]),
            "structural_ring": structural_ring(refs),
            "referenced_node_count": len(refs),
            "unique_referenced_nodes": len(set(refs)),
            "missing_node_ids": missing, "invalid_coordinate_node_ids": invalid,
            "all_referenced_coordinates_available": not missing and not invalid,
            "polygon_topology_checked": False, "geographic_membership_checked": False,
            "app_accepted": False}


def write_json(path: Path, value) -> None:
    with path.open("x", encoding="utf-8") as handle:
        json.dump(value, handle, ensure_ascii=True, sort_keys=True, indent=2)
        handle.write("\n")


def run(pbf: Path, receipt_path: Path, output: Path) -> dict:
    if output.exists():
        raise ValueError("Inventory output exists; use a fresh path")
    source_hash = sha256(pbf)
    receipt = json.loads(receipt_path.read_text())
    if (receipt.get("pbf_sha256") != source_hash
            or receipt.get("pbf_bytes") != pbf.stat().st_size
            or receipt.get("publisher_provenance_independently_verified") is not False):
        raise ValueError("Exact owner-upload source/receipt consistency required")
    extractor_path = HERE.parent / "extract_osm_businesses.py"
    node_research_path = HERE / "research_node_geography.py"
    extract = load_module("way_inventory_existing_extractor", extractor_path)
    node_research = load_module("way_inventory_country_review", node_research_path)
    osmium = extract.osmium
    counts = collections.Counter()
    inventory = []

    class Inventory(osmium.SimpleHandler):
        def way(self, obj):
            self.collect("way", obj)

        def relation(self, obj):
            self.collect("relation", obj)

        def collect(self, kind, obj):
            counts["prefiltered_" + kind + "s"] += 1
            tags = dict(obj.tags)
            category = extract.category(tags)
            if not category:
                return
            counts["public_category_" + kind + "s"] += 1
            name = source_name(tags, extract)
            if not name:
                counts["missing_source_name_" + kind + "s"] += 1
                return
            raw_phones = {key: tags[key] for key in ("contact:phone", "phone") if tags.get(key)}
            if not raw_phones:
                return
            counts["named_phone_bearing_" + kind + "s"] += 1
            country_decision, country_tag = node_research.country_decision(tags)
            counts[kind + "_country_" + country_decision] += 1
            valid, seen, rejections = [], set(), collections.Counter()
            for raw in raw_phones.values():
                phones, rejected = extract.phones(raw)
                rejections.update(rejected)
                for phone in phones:
                    identity = phone["phone"], phone["extension"]
                    if identity not in seen:
                        valid.append(phone)
                        seen.add(identity)
            timestamp = obj.timestamp.isoformat() if obj.timestamp else None
            if not timestamp:
                raise ValueError("Source object timestamp missing")
            record = {"status": STATUS, "source_id": f"osm:{kind}:{obj.id}",
                      "source_url": f"https://www.openstreetmap.org/{kind}/{obj.id}",
                      "source_updated_at": timestamp, "source_version": obj.version,
                      "source_tags": tags, "category": category, "preferred_source_name": name,
                      "raw_phone_fields": raw_phones, "phone_candidates": valid,
                      "phone_rejections": dict(sorted(rejections.items())),
                      "country_decision": country_decision, "country_tag": country_tag,
                      "source_country_fields": {key: tags[key] for key in node_research.COUNTRY_FIELDS
                                                if tags.get(key)},
                      "country_inferred": False, "app_accepted": False}
            if kind == "way":
                refs = [node.ref for node in obj.nodes]
                record["node_refs"] = refs
                counts["source_way_reference_closed" if refs and refs[0] == refs[-1]
                       else "source_way_reference_open"] += 1
                counts["source_way_structural_ring" if structural_ring(refs)
                       else "source_way_not_structural_ring"] += 1
            else:
                record["members"] = [{"type": member.type, "ref": member.ref, "role": member.role}
                                     for member in obj.members]
                counts.update("relation_member_type_" + member.type for member in obj.members)
            inventory.append(record)

    osmium.apply(str(pbf), osmium.filter.EntityFilter(osmium.osm.WAY | osmium.osm.RELATION),
                 osmium.filter.KeyFilter("shop", "office", "craft", "healthcare", "amenity", "tourism"),
                 Inventory())
    print(json.dumps({"stage": "source_inventory_complete", "counts": dict(sorted(counts.items()))}), flush=True)
    candidate_ways = {int(row["source_id"].rsplit(":", 1)[1]): row
                      for row in inventory if row["source_id"].startswith("osm:way:")}
    relations = [row for row in inventory if row["source_id"].startswith("osm:relation:")]
    member_way_ids = {member["ref"] for row in relations for member in row["members"] if member["type"] == "w"}
    member_ways = {}

    class MemberWays(osmium.SimpleHandler):
        def way(self, obj):
            if obj.id in member_ways:
                raise ValueError("Repeated member-way source ID")
            member_ways[obj.id] = {"source_id": f"osm:way:{obj.id}",
                                   "source_updated_at": obj.timestamp.isoformat(),
                                   "node_refs": [node.ref for node in obj.nodes]}

    if member_way_ids:
        osmium.apply(str(pbf), osmium.filter.EntityFilter(osmium.osm.WAY),
                     osmium.filter.IdFilter(member_way_ids), MemberWays())
    node_ids = {ref for row in candidate_ways.values() for ref in row["node_refs"]}
    node_ids.update(ref for row in member_ways.values() for ref in row["node_refs"])
    node_ids.update(member["ref"] for row in relations for member in row["members"] if member["type"] == "n")
    nodes = {}

    class SourceNodes(osmium.SimpleHandler):
        def node(self, obj):
            if obj.id in nodes:
                raise ValueError("Repeated selected source node ID")
            valid = obj.location.valid()
            lon, lat = (obj.location.lon, obj.location.lat) if valid else (None, None)
            valid = (valid and math.isfinite(lon) and math.isfinite(lat)
                     and -180 <= lon <= 180 and -90 <= lat <= 90)
            nodes[obj.id] = {"source_id": f"osm:node:{obj.id}",
                             "source_updated_at": obj.timestamp.isoformat(),
                             "longitude": lon, "latitude": lat, "valid_coordinates": valid}

    if node_ids:
        osmium.apply(str(pbf), osmium.filter.EntityFilter(osmium.osm.NODE),
                     osmium.filter.IdFilter(node_ids), SourceNodes())
    for row in candidate_ways.values():
        row["geometry_availability"] = dependency_status(row["node_refs"], nodes)
    for row in member_ways.values():
        row["geometry_availability"] = dependency_status(row["node_refs"], nodes)
    for row in relations:
        member_way_refs = {member["ref"] for member in row["members"] if member["type"] == "w"}
        row["geometry_availability"] = {
            "referenced_member_ways": len(member_way_refs),
            "missing_member_way_ids": sorted(member_way_refs - member_ways.keys()),
            "member_way_nodes_missing_or_invalid": sum(
                not member_ways[ref]["geometry_availability"]["all_referenced_coordinates_available"]
                for ref in member_way_refs & member_ways.keys()),
            "nested_relation_refs": [m["ref"] for m in row["members"] if m["type"] == "r"],
            "recursive_relation_assembly_checked": False,
            "polygon_topology_checked": False, "geographic_membership_checked": False,
            "app_accepted": False}
    inventory.sort(key=lambda row: row["source_id"])
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="way-inventory-", dir=output.parent) as temporary:
        temp = Path(temporary)
        with (temp / "source-objects.ndjson").open("x", encoding="utf-8") as handle:
            for record in inventory:
                handle.write(json.dumps(record, ensure_ascii=True, sort_keys=True) + "\n")
        write_json(temp / "geometry-nodes.json", {str(ref): value for ref, value in sorted(nodes.items())})
        write_json(temp / "relation-member-ways.json", {str(ref): value for ref, value in sorted(member_ways.items())})
        summary = {"status": STATUS, "observed_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
                   "source_pbf_sha256": source_hash, "source_pbf_bytes": pbf.stat().st_size,
                   "owner_upload_receipt_sha256": sha256(receipt_path),
                   "research_helper_sha256": sha256(Path(__file__)),
                   "unchanged_extractor_sha256": sha256(extractor_path),
                   "unchanged_country_research_sha256": sha256(node_research_path),
                   "counts": dict(sorted(counts.items())), "source_objects": len(inventory),
                   "source_ways": len(candidate_ways), "source_relations": len(relations),
                   "ways_with_numbering_valid_phone_candidate": sum(bool(row["phone_candidates"]) for row in candidate_ways.values()),
                   "relations_with_numbering_valid_phone_candidate": sum(bool(row["phone_candidates"]) for row in relations),
                   "way_structural_rings_with_complete_coordinates": sum(
                       row["geometry_availability"]["structural_ring"]
                       and row["geometry_availability"]["all_referenced_coordinates_available"]
                       for row in candidate_ways.values()),
                   "way_missing_or_invalid_coordinate_dependency": sum(
                       not row["geometry_availability"]["all_referenced_coordinates_available"]
                       for row in candidate_ways.values()),
                   "ways_tagged_area_no": sum(
                       row["source_tags"].get("area", "").strip().lower() == "no"
                       for row in candidate_ways.values()),
                   "way_source_area_tags": dict(sorted(collections.Counter(
                       row["source_tags"].get("area", "<missing>")
                       for row in candidate_ways.values()).items())),
                   "way_complete_reference_rings_not_tagged_area_no": sum(
                       row["geometry_availability"]["structural_ring"]
                       and row["geometry_availability"]["all_referenced_coordinates_available"]
                       and row["source_tags"].get("area", "").strip().lower() != "no"
                       for row in candidate_ways.values()),
                   "relation_member_way_ids_requested": len(member_way_ids),
                   "relation_member_way_ids_missing": sorted(member_way_ids - member_ways.keys()),
                   "geometry_node_ids_requested": len(node_ids), "geometry_nodes_found": len(nodes),
                   "geometry_node_ids_missing": sorted(node_ids - nodes.keys()),
                   "source_object_years": dict(sorted(collections.Counter(row["source_updated_at"][:4] for row in inventory).items())),
                   "files_sha256": {p.name: sha256(p) for p in sorted(temp.iterdir()) if p.is_file()},
                   "app_rows_added": 0, "geographically_admitted_records": 0,
                   "limits": ["Reference closure and available nodes do not prove valid polygon topology or country membership.",
                              "Relations require reviewed role-aware recursive assembly; none is admitted here.",
                              "Phone metadata validity does not prove current assignment, ownership or reachability.",
                              "All raw country fields retained; no country derived from coordinates or phone prefixes.",
                              "Public-category/source-listed objects do not establish current active businesses or national coverage."],
                   "license_id": "ODbL-1.0", "attribution": "© OpenStreetMap contributors"}
        if sha256(pbf) != source_hash:
            raise ValueError("PBF changed during inventory")
        write_json(temp / "report.json", summary)
        Path(temporary).rename(output)
    print(json.dumps({key: summary[key] for key in ("source_objects", "source_ways", "source_relations",
                     "way_structural_rings_with_complete_coordinates", "geometry_nodes_found", "app_rows_added")}), flush=True)
    return summary


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pbf", type=Path)
    parser.add_argument("receipt", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    run(args.pbf, args.receipt, args.output)


if __name__ == "__main__":
    main()
