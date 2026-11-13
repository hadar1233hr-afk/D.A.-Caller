#!/usr/bin/env python3
"""Resolve extra-category WAY/RELATION dependencies, without geographic admission.

This is an explicit source inventory for the reviewed additional category policy.
It never changes or monkey patches the production extractor or earlier research
helpers. All source references remain exact and unresolved dependencies stay visible.
"""
from __future__ import annotations

import argparse
import collections
import datetime as dt
import json
import math
from pathlib import Path
import tempfile

from way_source_inventory import dependency_status, load_module, sha256, source_name, write_json
from research_node_geography import COUNTRY_FIELDS, country_decision

HERE = Path(__file__).resolve().parent
STATUS = "RESEARCH_EXTRACATEGORY_INVENTORYONLY"


def run(pbf: Path, receipt_path: Path, output: Path) -> dict:
    if output.exists():
        raise ValueError("Output exists; use a fresh source inventory path")
    policy_path = HERE / "extended_business_category.py"
    core_path = HERE.parent / "extract_osm_businesses.py"
    shared_inventory = HERE / "way_source_inventory.py"
    shared_country = HERE / "research_node_geography.py"
    paths = (pbf, receipt_path, policy_path, core_path, shared_inventory, shared_country, Path(__file__))
    bound = {path: sha256(path) for path in paths}
    receipt = json.loads(receipt_path.read_text())
    if (receipt.get("pbf_sha256") != bound[pbf] or receipt.get("pbf_bytes") != pbf.stat().st_size
            or receipt.get("publisher_provenance_independently_verified") is not False):
        raise ValueError("Matching truthful owner-upload source receipt required")
    policy = load_module("extra_inventory_reviewed_policy", policy_path)
    osmium = policy.osmium
    objects, counts = [], collections.Counter()

    class Inventory(osmium.SimpleHandler):
        def way(self, obj): self.collect("way", obj)
        def relation(self, obj): self.collect("relation", obj)

        def collect(self, kind, obj):
            counts["prefiltered_" + kind + "s"] += 1
            tags = dict(obj.tags)
            decision, category = policy.category_decision(tags)
            if not category:
                return
            counts["reviewed_category_" + kind + "s"] += 1
            name = source_name(tags, policy)
            raw_phones = {key: tags[key] for key in ("contact:phone", "phone") if tags.get(key)}
            if not name or not raw_phones:
                return
            counts["named_phone_" + kind + "s"] += 1
            candidates, seen, rejected = [], set(), collections.Counter()
            for value in raw_phones.values():
                phones, invalid = policy.phones(value)
                rejected.update(invalid)
                for phone in phones:
                    identity = phone["phone"], phone["extension"]
                    if identity not in seen:
                        candidates.append(phone); seen.add(identity)
            country_method, country = country_decision(tags)
            timestamp = obj.timestamp.isoformat() if obj.timestamp else None
            if not timestamp:
                raise ValueError("Source timestamp required")
            row = dict(status=STATUS, source_id=f"osm:{kind}:{obj.id}",
                source_url=f"https://www.openstreetmap.org/{kind}/{obj.id}",
                source_updated_at=timestamp, source_version=obj.version,
                source_tags=tags, category=category, category_policy_decision=decision,
                preferred_source_name=name, raw_phone_fields=raw_phones,
                phone_candidates=candidates, phone_rejections=dict(sorted(rejected.items())),
                country_decision=country_method, country_tag=country,
                source_country_fields={key: tags[key] for key in COUNTRY_FIELDS if tags.get(key)},
                country_inferred=False, app_accepted=False,
                public_business_status="SOURCE_LISTING_ONLY_NO_CURRENTNESS_OR_PUBLICNESS_CONFIRMATION")
            if kind == "way":
                row["node_refs"] = [ref.ref for ref in obj.nodes]
            else:
                row["members"] = [{"type": member.type, "ref": member.ref, "role": member.role}
                                  for member in obj.members]
            objects.append(row)

    osmium.apply(str(pbf), osmium.filter.EntityFilter(osmium.osm.WAY | osmium.osm.RELATION),
                 osmium.filter.KeyFilter(*policy.WHITELIST), Inventory())
    print(json.dumps({"stage": "source_objects_selected", "named_phone_counts": {
        key: value for key, value in counts.items() if key.startswith("named_phone_")}}), flush=True)
    ways = {int(row["source_id"].rsplit(":", 1)[1]): row for row in objects
            if row["source_id"].startswith("osm:way:")}
    relations = [row for row in objects if row["source_id"].startswith("osm:relation:")]
    member_ids = {member["ref"] for row in relations for member in row["members"] if member["type"] == "w"}
    member_ways = {}

    class MemberRead(osmium.SimpleHandler):
        def way(self, obj):
            if obj.id in member_ways:
                raise ValueError("Duplicate source member way")
            member_ways[obj.id] = dict(source_id=f"osm:way:{obj.id}",
                source_updated_at=obj.timestamp.isoformat(), node_refs=[ref.ref for ref in obj.nodes])

    if member_ids:
        osmium.apply(str(pbf), osmium.filter.EntityFilter(osmium.osm.WAY),
                     osmium.filter.IdFilter(member_ids), MemberRead())
    needed = {ref for row in ways.values() for ref in row["node_refs"]}
    needed.update(ref for row in member_ways.values() for ref in row["node_refs"])
    needed.update(member["ref"] for row in relations for member in row["members"] if member["type"] == "n")
    nodes = {}

    class NodeRead(osmium.SimpleHandler):
        def node(self, obj):
            if obj.id in nodes:
                raise ValueError("Duplicate selected source node")
            valid = obj.location.valid()
            lon, lat = (obj.location.lon, obj.location.lat) if valid else (None, None)
            valid = (valid and math.isfinite(lon) and math.isfinite(lat)
                     and -180 <= lon <= 180 and -90 <= lat <= 90)
            nodes[obj.id] = dict(source_id=f"osm:node:{obj.id}", source_updated_at=obj.timestamp.isoformat(),
                                 longitude=lon, latitude=lat, valid_coordinates=valid)

    if needed:
        osmium.apply(str(pbf), osmium.filter.EntityFilter(osmium.osm.NODE),
                     osmium.filter.IdFilter(needed), NodeRead())
    for row in list(ways.values()) + list(member_ways.values()):
        row["geometry_availability"] = dependency_status(row["node_refs"], nodes)
    for row in relations:
        refs = {member["ref"] for member in row["members"] if member["type"] == "w"}
        row["geometry_availability"] = dict(referenced_member_ways=len(refs),
            missing_member_way_ids=sorted(refs - member_ways.keys()),
            member_way_nodes_missing_or_invalid=sum(not member_ways[ref]["geometry_availability"]["all_referenced_coordinates_available"]
                                                   for ref in refs & member_ways.keys()),
            nested_relation_refs=[m["ref"] for m in row["members"] if m["type"] == "r"],
            recursive_relation_assembly_checked=False, polygon_topology_checked=False,
            geographic_membership_checked=False, app_accepted=False)
    objects.sort(key=lambda row: row["source_id"])
    if any(sha256(path) != digest for path, digest in bound.items()):
        raise ValueError("Source/policy/reference bytes changed during inventory")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="extra-category-inventory-", dir=output.parent) as temporary:
        temp = Path(temporary)
        with (temp / "source-objects.ndjson").open("x", encoding="utf-8") as stream:
            for row in objects: stream.write(json.dumps(row, ensure_ascii=True, sort_keys=True) + "\n")
        write_json(temp / "geometry-nodes.json", {str(ref): node for ref, node in sorted(nodes.items())})
        write_json(temp / "relation-member-ways.json", {str(ref): way for ref, way in sorted(member_ways.items())})
        report = dict(status=STATUS, observed_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(),
            source_pbf_sha256=bound[pbf], source_pbf_bytes=pbf.stat().st_size,
            owner_upload_receipt_sha256=bound[receipt_path], research_helper_sha256=bound[Path(__file__)],
            extended_category_policy_sha256=bound[policy_path], unchanged_core_extractor_sha256=bound[core_path],
            shared_inventory_functions_sha256=bound[shared_inventory], shared_country_policy_sha256=bound[shared_country],
            category_whitelist={key: sorted(values) for key, values in policy.WHITELIST.items()},
            source_objects=len(objects), source_ways=len(ways), source_relations=len(relations),
            counts=dict(sorted(counts.items())),
            category_counts=dict(sorted(collections.Counter(row["category"] for row in objects).items())),
            ways_with_numbering_valid_phone_candidate=sum(bool(row["phone_candidates"]) for row in ways.values()),
            relations_with_numbering_valid_phone_candidate=sum(bool(row["phone_candidates"]) for row in relations),
            way_structural_rings_with_complete_coordinates=sum(row["geometry_availability"]["structural_ring"]
                and row["geometry_availability"]["all_referenced_coordinates_available"] for row in ways.values()),
            way_missing_or_invalid_coordinate_dependency=sum(not row["geometry_availability"]["all_referenced_coordinates_available"]
                                                           for row in ways.values()),
            relation_member_way_ids_missing=sorted(member_ids - member_ways.keys()),
            geometry_node_ids_requested=len(needed), geometry_nodes_found=len(nodes),
            geometry_node_ids_missing=sorted(needed - nodes.keys()),
            source_object_years=dict(sorted(collections.Counter(row["source_updated_at"][:4] for row in objects).items())),
            files_sha256={path.name: sha256(path) for path in sorted(temp.iterdir())},
            app_rows_added=0, geographically_admitted_records=0,
            license_id="ODbL-1.0", attribution="© OpenStreetMap contributors",
            limits=["Reviewed extra-category source inventory only; no geographic admission or app integration.",
                    "Actual PBF references/coordinates establish dependency availability, not complete polygon topology.",
                    "Source categories/access/privacy holds do not confirm current business operation or phone ownership.",
                    "No missing country tags, geometry or source values were invented; unresolved dependencies stay explicit.",
                    "Original core/research/signing/CI/publication contracts remain unchanged."])
        write_json(temp / "report.json", report)
        (temp / "LICENSE-NOTICE.txt").write_text("© OpenStreetMap contributors\nODbL1.0 https://opendatacommons.org/licenses/odbl/1-0/\nResearch inventory only, no geographic or app admission.\n")
        Path(temporary).rename(output)
    print(json.dumps({key: report[key] for key in ("source_objects", "source_ways", "source_relations",
        "category_counts", "geometry_nodes_found", "way_missing_or_invalid_coordinate_dependency", "app_rows_added")}))
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ("pbf", "receipt", "output"): parser.add_argument(key, type=Path)
    args = parser.parse_args()
    run(args.pbf, args.receipt, args.output)


if __name__ == "__main__": main()
