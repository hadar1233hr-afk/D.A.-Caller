#!/usr/bin/env python3
"""Inventory uncovered source categories; never geographic or app admission.

Named phone-bearing amenity/leisure/club objects rejected by the unchanged
production category contract are source evidence for a later strict whitelist.
Explicit private/residential/inactive listings are conservatively excluded.
An absent access tag is not interpreted as public access or commercial status.
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

HERE = Path(__file__).resolve().parent
STATUS = "CATEGORY_GAP_INVENTORY_ONLY_NO_GEOGRAPHIC_OR_APP_ADMISSION"
CATEGORY_KEYS = ("amenity", "leisure", "club")
COUNTRY_KEYS = ("addr:country", "country", "is_in:country_code", "is_in:country")
INACTIVE = {"no", "vacant", "closed", "disused", "abandoned", "demolished", "razed", "removed"}
LIFECYCLE = ("disused", "abandoned", "demolished", "razed", "removed", "destroyed")
RESIDENTIAL_BUILDINGS = {
    "apartments", "residential", "house", "detached", "semidetached_house",
    "terrace", "bungalow", "dormitory", "static_caravan", "cabin", "hut",
}
EDUCATION = {"school", "kindergarten", "college", "university", "childcare", "language_school", "music_school"}


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def folded(tags: dict, key: str) -> str:
    value = tags.get(key, "")
    return value.strip().casefold() if isinstance(value, str) else ""


def conservative_exclusion(tags: dict) -> str | None:
    if folded(tags, "access") in {"private", "no"}:
        return "explicit-private-or-no-access"
    if folded(tags, "building") in RESIDENTIAL_BUILDINGS or folded(tags, "landuse") == "residential":
        return "explicit-residential-building-or-landuse"
    if folded(tags, "residential") in {"yes", "1", "true"}:
        return "explicit-residential-flag"
    if any(folded(tags, key) in {"yes", "1", "true"} for key in LIFECYCLE):
        return "explicit-inactive-lifecycle-flag"
    if any(folded(tags, key) in INACTIVE for key in CATEGORY_KEYS if key in tags):
        return "explicit-inactive-category-value"
    if any(tags.get(prefix + ":" + key, "").strip()
           for prefix in LIFECYCLE for key in CATEGORY_KEYS):
        return "lifecycle-prefixed-category-needs-exclusion"
    return None


def review_ambiguities(tags: dict) -> list[str]:
    result = []
    if not tags.get("access", "").strip():
        result.append("public-access-not-explicitly-sourced")
    elif folded(tags, "access") not in {"yes", "public", "customers", "permissive", "designated"}:
        result.append("nonstandard-or-restricted-access-needs-review")
    if tags.get("access:conditional", "").strip():
        result.append("conditional-access-needs-review")
    if folded(tags, "leisure") in {"swimming_pool", "sauna", "sports_centre", "fitness_station"}:
        result.append("leisure-facility-publicness-and-commercial-status-not-proven")
    if tags.get("club", "").strip():
        result.append("club-membership-and-commercial-status-not-proven")
    if folded(tags, "amenity") in EDUCATION:
        result.append("educational-institution-not-necessarily-commercial-business")
    return result


def policy_checks() -> list[str]:
    """Small synthetic exclusion/privacy contracts, not real-source validation."""
    cases = [
        ({"access": "private"}, "explicit-private-or-no-access"),
        ({"access": " No "}, "explicit-private-or-no-access"),
        ({"building": "house"}, "explicit-residential-building-or-landuse"),
        ({"building": "apartments"}, "explicit-residential-building-or-landuse"),
        ({"landuse": "residential"}, "explicit-residential-building-or-landuse"),
        ({"residential": "yes"}, "explicit-residential-flag"),
        ({"disused": "yes"}, "explicit-inactive-lifecycle-flag"),
        ({"abandoned": "1"}, "explicit-inactive-lifecycle-flag"),
        ({"amenity": "closed"}, "explicit-inactive-category-value"),
        ({"leisure": "disused"}, "explicit-inactive-category-value"),
        ({"leisure": "fitness_centre", "disused:leisure": "fitness_centre"}, "lifecycle-prefixed-category-needs-exclusion"),
        ({"access": "customers", "building": "commercial", "leisure": "fitness_centre"}, None),
        ({"leisure": "swimming_pool"}, None),
    ]
    checks = []
    for index, (tags, expected) in enumerate(cases):
        before = dict(tags)
        if conservative_exclusion(tags) != expected or tags != before:
            raise AssertionError("Synthetic exclusion contract failed: " + str(index))
        checks.append("synthetic-exclusion-" + str(index + 1))
    if "public-access-not-explicitly-sourced" not in review_ambiguities({"leisure": "fitness_centre"}):
        raise AssertionError("Missing access must not be promoted to public")
    checks.append("missing-access-not-inferred-public")
    if not review_ambiguities({"leisure": "swimming_pool", "access": "customers"}):
        raise AssertionError("Source leisure status needs separate review")
    checks.append("pool-commercial-status-not-inferred")
    if "club-membership-and-commercial-status-not-proven" not in review_ambiguities({"club": "sport"}):
        raise AssertionError("Club not automatically a commercial business")
    checks.append("club-status-not-inferred")
    if "educational-institution-not-necessarily-commercial-business" not in review_ambiguities({"amenity": "school"}):
        raise AssertionError("School not automatically a business")
    checks.append("school-status-not-inferred")
    if "conditional-access-needs-review" not in review_ambiguities({"access:conditional": "private @ (Mo-Fr)"}):
        raise AssertionError("Conditional access must remain visible")
    checks.append("conditional-access-retained-for-review")
    return checks


def run(pbf: Path, receipt_path: Path, output: Path) -> dict:
    if output.exists():
        raise ValueError("Use a fresh inventory output; no overwrite")
    extractor_path = HERE.parent / "extract_osm_businesses.py"
    bound_paths = {path: sha256(path) for path in (pbf, receipt_path, extractor_path, Path(__file__))}
    source_hash = bound_paths[pbf]
    receipt = json.loads(receipt_path.read_text())
    if (receipt.get("pbf_sha256") != source_hash or receipt.get("pbf_bytes") != pbf.stat().st_size
            or receipt.get("publisher_provenance_independently_verified") is not False):
        raise ValueError("Exact truthful owner-upload source/receipt consistency required")
    extract = load_module("category_gap_existing_extractor", extractor_path)
    osmium = extract.osmium
    rows, counts = [], collections.Counter()
    source_categories, excluded_categories = collections.Counter(), collections.Counter()
    exclusions, ambiguities = collections.Counter(), collections.Counter()

    class Inventory(osmium.SimpleHandler):
        def node(self, obj): self.collect("node", obj)
        def way(self, obj): self.collect("way", obj)
        def relation(self, obj): self.collect("relation", obj)

        def collect(self, kind, obj):
            counts["prefiltered_" + kind + "s"] += 1
            tags = dict(obj.tags)
            fields = {key: tags[key] for key in CATEGORY_KEYS if tags.get(key, "").strip()}
            name = next((extract.clean(tags[key]) for key in ("name:he", "name", "name:en")
                         if tags.get(key, "").strip()), "")
            phones = {key: tags[key] for key in ("contact:phone", "phone") if tags.get(key, "").strip()}
            if not fields or not name or not phones:
                return
            counts["named_phone_bearing_prefiltered_" + kind + "s"] += 1
            if extract.category(tags) is not None:
                counts["already_covered_by_existing_category_" + kind + "s"] += 1
                return
            counts["existing_category_rejected_named_phone_" + kind + "s"] += 1
            reason = conservative_exclusion(tags)
            if reason:
                exclusions[reason] += 1
                excluded_categories.update(key + ":" + value for key, value in fields.items())
                return
            source_categories.update(key + ":" + value for key, value in fields.items())
            review = review_ambiguities(tags)
            ambiguities.update(review)
            candidates, seen, rejected = [], set(), collections.Counter()
            for raw in phones.values():
                valid, invalid = extract.phones(raw)
                rejected.update(invalid)
                for phone in valid:
                    identity = phone["phone"], phone["extension"]
                    if identity not in seen:
                        candidates.append(phone); seen.add(identity)
            stamp = obj.timestamp.isoformat() if obj.timestamp else None
            if not stamp:
                raise ValueError("Exact source timestamp required")
            row = dict(status=STATUS, source_id=f"osm:{kind}:{obj.id}",
                       source_url=f"https://www.openstreetmap.org/{kind}/{obj.id}",
                       source_updated_at=stamp, source_version=obj.version,
                       source_tags=tags, source_name=name, gap_category_fields=fields,
                       raw_phone_fields=phones, phone_candidates=candidates,
                       phone_rejections=dict(sorted(rejected.items())),
                       source_country_fields={key: tags[key] for key in COUNTRY_KEYS if tags.get(key)},
                       country_inferred=False, review_ambiguities=review,
                       public_business_status="NOT_ESTABLISHED_BY_CATEGORY_INVENTORY",
                       geographic_admission=False, app_accepted=False)
            if kind == "node":
                valid = obj.location.valid()
                lon, lat = (obj.location.lon, obj.location.lat) if valid else (None, None)
                valid = (valid and math.isfinite(lon) and math.isfinite(lat)
                         and -180 <= lon <= 180 and -90 <= lat <= 90)
                row.update(longitude=lon, latitude=lat, source_coordinate_valid=valid)
            elif kind == "way":
                row["node_refs"] = [ref.ref for ref in obj.nodes]
            else:
                row["members"] = [{"type": member.type, "ref": member.ref, "role": member.role}
                                  for member in obj.members]
            rows.append(row)
            counts["inventory_" + kind + "s"] += 1

    osmium.apply(str(pbf), osmium.filter.EntityFilter(osmium.osm.NODE | osmium.osm.WAY | osmium.osm.RELATION),
                 osmium.filter.KeyFilter(*CATEGORY_KEYS), Inventory())
    rows.sort(key=lambda row: (row["source_id"].split(":")[1], int(row["source_id"].rsplit(":", 1)[1])))
    if any(sha256(path) != value for path, value in bound_paths.items()):
        raise ValueError("Bound source/receipt/helper changed during inventory")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="category-gap-", dir=output.parent) as temporary:
        temp = Path(temporary); corpus = temp / "source-category-gap-objects.ndjson"
        with corpus.open("x", encoding="utf-8") as stream:
            for row in rows: stream.write(json.dumps(row, ensure_ascii=True, sort_keys=True) + "\n")
        report = dict(status=STATUS, observed_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(),
                      source_pbf_sha256=source_hash, source_pbf_bytes=pbf.stat().st_size,
                      owner_upload_receipt_sha256=bound_paths[receipt_path],
                      executed_helper_sha256=bound_paths[Path(__file__)],
                      unchanged_category_phone_extractor_sha256=bound_paths[extractor_path],
                      source_objects=len(rows), source_object_types=dict(collections.Counter(row["source_id"].split(":")[1] for row in rows)),
                      counts=dict(sorted(counts.items())),
                      gap_category_field_observations=dict(sorted(source_categories.items())),
                      excluded_category_field_observations=dict(sorted(excluded_categories.items())),
                      exclusion_reasons=dict(sorted(exclusions.items())), ambiguity_observations=dict(sorted(ambiguities.items())),
                      objects_with_numbering_valid_phone_candidate=sum(bool(row["phone_candidates"]) for row in rows),
                      numbering_valid_phone_rows=sum(len(row["phone_candidates"]) for row in rows),
                      distinct_base_phone_candidates=len({phone["phone"] for row in rows for phone in row["phone_candidates"]}),
                      source_country_fields_missing=sum(not row["source_country_fields"] for row in rows),
                      source_object_update_years=dict(sorted(collections.Counter(row["source_updated_at"][:4] for row in rows).items())),
                      corpus_sha256=sha256(corpus), app_records_added=0, geographically_admitted_records=0,
                      inferred_country_tags=0, source_records_merged=0,
                      synthetic_privacy_policy_checks=policy_checks(),
                      license_id="ODbL-1.0", attribution="© OpenStreetMap contributors",
                      limits=["Combined Israel-and-Palestine source inventory, not an Israel-only admitted directory.",
                              "Category/access tags do not establish commercial status, public availability, current activity or phone ownership.",
                              "Source category observations may count multiple fields on one object; do not add them as distinct businesses.",
                              "Conservative private/residential/inactive exclusions are not proof of actual closure or private ownership.",
                              "All exact source tags and historical dates are retained; no calls or network lookups were performed.",
                              "No Android/app/source/production category/signing/CI/publication guards changed."])
        (temp / "report.json").write_text(json.dumps(report, ensure_ascii=True, indent=2) + "\n")
        (temp / "LICENSE-NOTICE.txt").write_text("© OpenStreetMap contributors\nODbL1.0 https://opendatacommons.org/licenses/odbl/1-0/\nResearch source inventory only; not geographically or app admitted.\n")
        Path(temporary).rename(output)
    print(json.dumps({key: report[key] for key in ("source_objects", "source_object_types", "objects_with_numbering_valid_phone_candidate", "gap_category_field_observations", "exclusion_reasons", "app_records_added")}))
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    collect = sub.add_parser("inventory")
    for name in ("pbf", "receipt", "output"): collect.add_argument(name, type=Path)
    sub.add_parser("check-policy")
    args = parser.parse_args()
    if args.command == "check-policy":
        result = policy_checks(); print(json.dumps({"synthetic_checks": len(result), "checks": result, "status": "PASS"}))
    else:
        run(args.pbf, args.receipt, args.output)


if __name__ == "__main__": main()
