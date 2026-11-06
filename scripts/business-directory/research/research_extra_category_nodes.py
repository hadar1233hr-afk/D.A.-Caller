#!/usr/bin/env python3
"""Source-spatial NODE research for explicitly reviewed additional categories."""
import argparse
import collections
import datetime as dt
import json
from pathlib import Path
import tempfile

import extended_business_category as policy
from research_node_geography import (
    COUNTRY_FIELDS, GeometryScope, IL_TAGS, country_decision, sha256,
    validate_geometry_provenance,
)

SCOPE = "research-extra-category-node-source-IL-minus-filled-WB-and-Gaza"


def run(pbf: Path, geometry_base: Path, output: Path) -> dict:
    if output.exists():
        raise ValueError("Output exists; retain original research artifacts")
    source_sha = sha256(pbf)
    rings, provenance = validate_geometry_provenance(geometry_base, source_sha)
    receipt = json.loads((geometry_base / "owner-upload-receipt.json").read_text())
    if receipt.get("pbf_bytes") != pbf.stat().st_size:
        raise ValueError("Owner PBF byte count mismatch")
    counts, rows = collections.Counter(), []
    scope = GeometryScope(rings)
    observed = dt.datetime.now(dt.timezone.utc).isoformat()

    class Handler(policy.osmium.SimpleHandler):
        def node(self, obj):
            tags = dict(obj.tags)
            decision, category = policy.category_decision(tags)
            counts[decision] += 1
            if not category:
                return
            name = next((policy.clean(tags[k]) for k in ("name:he", "name", "name:en")
                         if tags.get(k, "").strip()), "")
            raw_phones = {k: tags[k] for k in ("contact:phone", "phone") if tags.get(k)}
            if not name or not raw_phones:
                counts["omit-missing-source-name-or-phone"] += 1
                return
            counts["named_extra_category_nodes_with_phone_tag"] += 1
            country_method, country = country_decision(tags)
            if country_method == "reject-explicit-non-IL":
                counts[country_method] += 1
                return
            if not obj.location.valid():
                counts["reject-invalid-source-coordinate"] += 1
                return
            lon, lat = obj.location.lon, obj.location.lat
            geometry_decision = scope.classify(lon, lat)
            counts[geometry_decision] += 1
            if geometry_decision != "research-qualified-source-interior":
                return
            phones, seen = [], set()
            for raw in raw_phones.values():
                candidates, rejected = policy.phones(raw)
                counts.update("phone_rejected_" + reason for reason in rejected)
                for phone in candidates:
                    key = phone["phone"], phone["extension"]
                    if key not in seen and (phone["e164"] or country in IL_TAGS):
                        seen.add(key)
                        phones.append(phone)
            if not phones:
                counts["omit-no-qualifying-source-phone"] += 1
                return
            timestamp = obj.timestamp.isoformat() if obj.timestamp else None
            if not timestamp:
                raise ValueError("Source timestamp missing")
            city = policy.clean(tags.get("addr:city", ""))
            address = policy.clean(tags.get("addr:full", "")) or " ".join(
                value for value in (policy.clean(tags.get("addr:street", "")),
                                    policy.clean(tags.get("addr:housenumber", "")), city) if value)
            source_id = f"osm:node:{obj.id}"
            counts["emitted_source_nodes"] += 1
            for phone in phones:
                rows.append(dict(
                    id=source_id + ":" + phone["phone"] + (";ext=" + phone["extension"] if phone["extension"] else ""),
                    source_id=source_id, source_url=f"https://www.openstreetmap.org/node/{obj.id}",
                    source_updated_at=timestamp, source_version=obj.version,
                    name=name, **phone, category=category, city=city, address=address,
                    source_tags=tags, source_phones=raw_phones,
                    source_country_fields={k: tags[k] for k in COUNTRY_FIELDS if tags.get(k)},
                    country_tag=country, country_admission=country_method, country_inferred=False,
                    longitude=lon, latitude=lat, geographic_scope=SCOPE,
                    category_admission=decision, geometry_admission=geometry_decision,
                    margin_degrees=scope.margin,
                    geometry_input_sha256=provenance["input_sha256"]["research-region-outer-rings.json"],
                    imported_at=observed, license_id="ODbL-1.0",
                    verification="source-listed-not-phone-confirmed", status="RESEARCH_ONLY_NOT_APP_ACCEPTED"))

    try:
        # Explicit leisure filter: the unchanged core NODE runner does not visit
        # leisure-only objects. No source extractor is patched or replaced.
        policy.osmium.apply(str(pbf), policy.osmium.filter.EntityFilter(policy.osmium.osm.NODE),
                            policy.osmium.filter.KeyFilter("leisure", "amenity"), Handler())
    finally:
        scope.close()
    rows.sort(key=lambda row: (int(row["source_id"].rsplit(":", 1)[1]), row["phone"], row["extension"]))
    if sha256(pbf) != source_sha:
        raise ValueError("Source PBF changed during research")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="extra-category-node-", dir=output.parent) as temporary:
        temp = Path(temporary)
        ndjson = temp / "research-extra-category-nodes.ndjson"
        with ndjson.open("x", encoding="utf-8") as handle:
            for row in rows:
                handle.write(json.dumps(row, ensure_ascii=True, sort_keys=True) + "\n")
        report = dict(status="RESEARCH_ONLY_NOT_APP_ACCEPTED", scope=SCOPE,
            source_pbf_sha256=source_sha, source_pbf_bytes=pbf.stat().st_size,
            observed_at_utc=observed, raw_phone_rows=len(rows),
            source_nodes=len({r["source_id"] for r in rows}),
            unique_base_phones=len({r["phone"] for r in rows}),
            counts=dict(sorted(counts.items())), ndjson_sha256=sha256(ndjson),
            source_category_counts=dict(collections.Counter(r["category"] for r in rows)),
            source_country_tags=dict(collections.Counter(r["country_tag"] or "<missing>" for r in rows)),
            source_years=dict(sorted(collections.Counter(r["source_updated_at"][:4] for r in rows).items())),
            research_script_sha256=sha256(Path(__file__)), policy_module_sha256=sha256(Path(policy.__file__)),
            unchanged_core_extractor_sha256=sha256(policy.CORE_PATH),
            node_geometry_reference_sha256=sha256(Path(__file__).with_name("research_node_geography.py")),
            geometry_provenance=provenance, app_records_added=0, automatic_source_merges=0,
            license_id="ODbL-1.0", attribution="© OpenStreetMap contributors",
            limits=["Additional categories are a narrow source review, not confirmation of public access or active commercial operation.",
                    "Unclear access, lifecycle conflicts, residential objects, clubs, generic schools and pools are held out.",
                    "Private commercial ownership is not interpreted as private access.",
                    "Only actual source NODE coordinates reviewed; additional ways/relations are not admitted here.",
                    "Country tags remain original/missing; source geometry and angular margin are not official borders or meters.",
                    "Source-listed names/phones and numbering-plan validity do not prove current ownership or completeness.",
                    "No app, original extractor, production packager, signing, CI or release guard edits."])
        (temp / "report.json").write_text(json.dumps(report, ensure_ascii=True, indent=2) + "\n")
        (temp / "LICENSE-NOTICE.txt").write_text(
            "© OpenStreetMap contributors\nODbL1.0 https://opendatacommons.org/licenses/odbl/1-0/\n"
            "Owner-upload source research only; no phone/currentness/publicness confirmation.\n")
        Path(temporary).rename(output)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("pbf", "geometry_base", "output"):
        parser.add_argument(name, type=Path)
    args = parser.parse_args()
    report = run(args.pbf, args.geometry_base, args.output)
    print(json.dumps({key: report[key] for key in
                     ("raw_phone_rows", "source_nodes", "unique_base_phones", "source_category_counts", "counts")}))


if __name__ == "__main__":
    main()
