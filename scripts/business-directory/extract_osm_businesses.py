#!/usr/bin/env python3
"""Build an offline, provenance-preserving ODbL business phone directory.

This build tool reads an already acquired public OSM PBF. It never requests
network access, calls a phone, reads personal contacts, or changes an Android DB.
"""
from __future__ import annotations

import argparse
import collections
import datetime as dt
import hashlib
import html
import json
from pathlib import Path
import re
import sqlite3
import tempfile
import unicodedata

import osmium
import phonenumbers

SOURCE_URL = "https://download.geofabrik.de/asia/israel-and-palestine-latest.osm.pbf"
LICENSE_URL = "https://opendatacommons.org/licenses/odbl/1-0/"
ATTRIBUTION = "© OpenStreetMap contributors"
BUSINESS_AMENITIES = {
    "bank", "bureau_de_change", "cafe", "restaurant", "fast_food", "food_court",
    "bar", "pub", "ice_cream", "pharmacy", "clinic", "doctors", "dentist",
    "hospital", "veterinary", "post_office", "car_rental", "car_wash", "fuel",
    "charging_station", "cinema", "theatre", "arts_centre", "community_centre",
    "library", "townhall", "social_facility", "police", "fire_station",
}
BUSINESS_TOURISM = {"hotel", "motel", "hostel", "guest_house", "apartment",
                    "camp_site", "caravan_site", "attraction", "museum", "gallery",
                    "theme_park", "zoo", "aquarium", "information"}
INACTIVE = {"no", "vacant", "closed", "disused", "abandoned", "demolished", "razed"}


def clean(value: str) -> str:
    return unicodedata.normalize("NFC", value).strip()


def category(tags: dict[str, str]) -> str | None:
    if tags.get("access") == "private" or any(tags.get(k) == "yes" for k in
            ("disused", "abandoned", "demolished", "razed")):
        return None
    for key in ("shop", "office", "craft", "healthcare"):
        value = tags.get(key)
        if value and value not in INACTIVE:
            return key + ":" + value
    if tags.get("amenity") in BUSINESS_AMENITIES:
        return "amenity:" + tags["amenity"]
    if tags.get("tourism") in BUSINESS_TOURISM:
        return "tourism:" + tags["tourism"]
    return None


def phones(raw: str) -> tuple[list[dict], list[str]]:
    """Syntax/allocation validation does not establish current phone ownership."""
    result, rejected, seen = [], [], set()
    # RFC3966 extension stays attached to its number. Never expand abbreviated
    # ranges such as 8375777/8 or guess a missing area code.
    for part in re.split(r";(?!\s*ext=)|,|\n", html.unescape(raw), flags=re.I):
        part = part.strip()
        if not part:
            continue
        original = part
        part = re.sub(r"^tel:", "", part, flags=re.I)
        part = "".join(str(unicodedata.digit(c)) if c.isdigit() else c for c in part)
        part = "".join(c for c in part if unicodedata.category(c) != "Cf")
        if re.fullmatch(r"\*[0-9]{4,5}", part):
            key = (part, "")
            if key not in seen:
                result.append({"phone": part, "e164": None, "original_phone": original,
                               "extension": "", "phone_type": "PUBLIC_STAR_SERVICE",
                               "dialing_scope": "IL-national-service"})
                seen.add(key)
            continue
        if "/" in part or "#" in part or not re.fullmatch(r"[0-9+(). \t\-]*(?:;ext=[0-9]+)?", part, flags=re.I):
            rejected.append("ambiguous_or_non_phone_text")
            continue
        try:
            number = phonenumbers.parse(part, "IL")
        except phonenumbers.NumberParseException:
            rejected.append("parse_error")
            continue
        if number.country_code != 972 or not phonenumbers.is_valid_number_for_region(number, "IL"):
            rejected.append("not_current_valid_israeli_number")
            continue
        canonical = phonenumbers.format_number(number, phonenumbers.PhoneNumberFormat.E164)
        number_type = phonenumbers.number_type(number)
        base_number = phonenumbers.PhoneNumber()
        base_number.merge_from(number)
        base_number.extension = None
        national = re.sub(r"\D", "", phonenumbers.format_number(base_number, phonenumbers.PhoneNumberFormat.NATIONAL))
        special = number_type in (phonenumbers.PhoneNumberType.TOLL_FREE,
                                  phonenumbers.PhoneNumberType.SHARED_COST,
                                  phonenumbers.PhoneNumberType.PREMIUM_RATE)
        dial = national if special else canonical
        extension = number.extension or ""
        key = (dial, extension)
        if key not in seen:
            result.append({"phone": dial, "e164": canonical, "original_phone": original,
                           "extension": extension, "phone_type": str(number_type),
                           "dialing_scope": "IL-national-service" if special else "E164"})
            seen.add(key)
    return result, rejected


def normalized_key(value: str) -> str:
    return " ".join(clean(value).casefold().split())


class Extractor(osmium.SimpleHandler):
    def __init__(self, connection: sqlite3.Connection, imported_at: str, scope: str):
        super().__init__()
        self.db, self.imported_at, self.scope = connection, imported_at, scope
        self.counts = collections.Counter()

    def node(self, obj):
        self.collect("node", obj)

    def way(self, obj):
        self.collect("way", obj)

    def relation(self, obj):
        self.collect("relation", obj)

    def collect(self, kind, obj):
        self.counts["objects_seen_" + kind] += 1
        tags = dict(obj.tags)
        kind_category = category(tags)
        if not kind_category:
            return
        self.counts["business_category_objects"] += 1
        name = next((clean(tags[k]) for k in ("name:he", "name", "name:en") if tags.get(k, "").strip()), "")
        if not name:
            self.counts["rejected_missing_source_name"] += 1
            return
        raw_phones = [tags[k] for k in ("contact:phone", "phone") if tags.get(k)]
        if not raw_phones:
            return
        self.counts["named_businesses_with_phone_tag"] += 1
        country = tags.get("addr:country", tags.get("country", "")).strip().upper()
        if country and country not in {"IL", "ISR", "ISRAEL", "ישראל"}:
            self.counts["rejected_explicit_non_il_country"] += 1
            return
        explicit_il = country in {"IL", "ISR", "ISRAEL", "ישראל"}
        if self.scope == "explicit-il" and not explicit_il:
            self.counts["rejected_missing_explicit_il_country"] += 1
            return
        contact_phones, seen = [], set()
        for raw in raw_phones:
            valid, rejected = phones(raw)
            self.counts.update("rejected_phone_" + reason for reason in rejected)
            for phone in valid:
                key = (phone["phone"], phone["extension"])
                if key not in seen:
                    contact_phones.append(phone)
                    seen.add(key)
        if not contact_phones:
            return
        # Star services are country-specific; a +972-free untagged record from
        # the combined extract cannot establish that it is an Israeli service.
        contact_phones = [p for p in contact_phones if p["e164"] or explicit_il]
        if not contact_phones:
            self.counts["rejected_star_without_explicit_il_country"] += 1
            return
        city = clean(tags.get("addr:city", ""))
        street = clean(tags.get("addr:street", ""))
        house = clean(tags.get("addr:housenumber", ""))
        address = clean(tags.get("addr:full", "")) or " ".join(x for x in (street, house, city) if x)
        source_id = f"osm:{kind}:{obj.id}"
        timestamp = obj.timestamp.isoformat() if obj.timestamp else None
        for phone in contact_phones:
            record = dict(id=source_id + ":" + phone["phone"] + (";ext=" + phone["extension"] if phone["extension"] else ""),
                name=name, **phone, category=kind_category, city=city, address=address,
                source_url=f"https://www.openstreetmap.org/{kind}/{obj.id}", source_id=source_id,
                source_updated_at=timestamp, imported_at=self.imported_at,
                license_id="ODbL-1.0", verification="source-listed-not-phone-confirmed",
                country_tag=country or None, geographic_scope=self.scope,
                source_names={k: tags[k] for k in ("name", "name:he", "name:en", "brand", "operator") if tags.get(k)})
            # Merge only exact name+phone+address duplicates with a nonempty
            # address. Same switchboard at distinct branches is not one branch.
            merge_key = json.dumps([normalized_key(name), phone["phone"], phone["extension"], normalized_key(address)], ensure_ascii=False) if address else record["id"]
            existing = self.db.execute("SELECT id,source_urls FROM business_contacts WHERE merge_key=?", (merge_key,)).fetchone()
            if existing:
                urls = json.loads(existing[1])
                if record["source_url"] not in urls:
                    urls.append(record["source_url"])
                    self.db.execute("UPDATE business_contacts SET source_urls=? WHERE id=?", (json.dumps(urls, ensure_ascii=False), existing[0]))
                self.counts["exact_address_duplicates_merged"] += 1
                continue
            self.db.execute("INSERT INTO business_contacts VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", (
                record["id"], name, phone["phone"], city, address, kind_category,
                record["source_url"], timestamp, self.imported_at, "ODbL-1.0", merge_key,
                json.dumps([record["source_url"]], ensure_ascii=False), json.dumps(record, ensure_ascii=False)))
            self.counts["emitted_records"] += 1
        self.counts["accepted_source_objects"] += 1


def schema(db):
    db.executescript("""
    CREATE TABLE business_contacts (
        id TEXT PRIMARY KEY, name TEXT NOT NULL, phone TEXT NOT NULL, city TEXT NOT NULL,
        address TEXT NOT NULL, category TEXT NOT NULL, source_url TEXT NOT NULL,
        source_updated_at TEXT, imported_at TEXT NOT NULL, license_id TEXT NOT NULL,
        merge_key TEXT UNIQUE NOT NULL, source_urls TEXT NOT NULL, record_json TEXT NOT NULL);
    CREATE INDEX business_phone ON business_contacts(phone);
    CREATE VIRTUAL TABLE business_search USING fts4(id, name, phone, city, category,
                                                   tokenize=unicode61);
    CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
    """)


def sha256(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("pbf", type=Path)
    p.add_argument("output", type=Path)
    p.add_argument("--scope", choices=("explicit-il", "il-phone-in-combined-extract"), default="explicit-il")
    p.add_argument("--minimum-records", type=int, default=1)
    args = p.parse_args()
    if args.output.exists():
        p.error("Use a fresh output directory; never mix or overwrite published data")
    if not args.pbf.is_file() or args.pbf.stat().st_size <= 0:
        p.error("Actual source PBF required")
    imported_at = dt.datetime.now(dt.timezone.utc).isoformat()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="business-extract-", dir=args.output.parent) as temporary:
        tmp = Path(temporary)
        db = sqlite3.connect(tmp / "businesses.sqlite")
        schema(db)
        handler = Extractor(db, imported_at, args.scope)
        handler.apply_file(str(args.pbf), locations=False)
        count = db.execute("SELECT count(*) FROM business_contacts").fetchone()[0]
        if count < args.minimum_records:
            raise RuntimeError(f"Only {count} eligible rows; minimum {args.minimum_records}. No output published")
        distinct = db.execute("SELECT count(DISTINCT phone) FROM business_contacts").fetchone()[0]
        manifest = dict(schema_version=1, source_url=SOURCE_URL, source_sha256=sha256(args.pbf),
            source_bytes=args.pbf.stat().st_size, imported_at=imported_at,
            license_id="ODbL-1.0", license_url=LICENSE_URL, attribution=ATTRIBUTION,
            phonenumbers_version=phonenumbers.__version__, scope=args.scope,
            record_count=count, distinct_phone_count=distinct, counts=dict(sorted(handler.counts.items())),
            coverage_claim="No claim of complete Israeli business coverage or confirmed phone ownership",
            scope_note="explicit-il requires source country tag IL. The optional combined-extract mode selects valid Israeli dialing numbers, not a political boundary; it may contain cross-border listed businesses.",
            verification="Source-listed public business phones; syntax/allocation validated, not individually called",
            omitted="No contributor user IDs/names, customer contacts, fax tags or private/residential-only objects imported")
        db.executemany("INSERT INTO metadata VALUES (?,?)", [(k, json.dumps(v, ensure_ascii=False)) for k, v in manifest.items()])
        db.execute("INSERT INTO business_search SELECT id,name,phone,city,category FROM business_contacts")
        db.commit()
        with (tmp / "businesses.ndjson").open("w", encoding="utf-8") as out:
            for record_json, urls in db.execute("SELECT record_json,source_urls FROM business_contacts ORDER BY id"):
                record = json.loads(record_json)
                record["source_urls"] = json.loads(urls)
                out.write(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n")
        db.execute("VACUUM")
        db.close()
        manifest["sqlite_sha256"] = sha256(tmp / "businesses.sqlite")
        manifest["ndjson_sha256"] = sha256(tmp / "businesses.ndjson")
        (tmp / "source-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
        (tmp / "LICENSE-NOTICE.txt").write_text(ATTRIBUTION + "\nODbL 1.0: " + LICENSE_URL + "\nSource: " + SOURCE_URL + "\nThis derived database is offered under ODbL1.0; it is separate from personal contacts and application code.\n")
        Path(temporary).rename(args.output)
        print(json.dumps(manifest, ensure_ascii=False))


if __name__ == "__main__":
    main()
