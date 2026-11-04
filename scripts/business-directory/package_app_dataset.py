#!/usr/bin/env python3
"""Package an acquired OSM extract into DA CALLER's independent public database.

This is a build tool, not an Android/network importer. Its inputs are the exact
extractor outputs and successful source acquisition receipt. No business data is
invented, no personal contacts are accessed, and no production assets are written
automatically. Publish the redistribution files alongside any app that ships the
generated assets. Public-source phone ownership is not independently confirmed.
"""
from __future__ import annotations

import argparse
import collections
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import shutil
import sqlite3
import tempfile
import unicodedata

SOURCE_URL = "https://download.geofabrik.de/asia/israel-and-palestine-latest.osm.pbf"
SOURCE_PAGE = "https://download.geofabrik.de/asia/israel-and-palestine.html"
LICENSE_URL = "https://opendatacommons.org/licenses/odbl/1-0/"
ATTRIBUTION = "© OpenStreetMap contributors"
MAX_RECORDS = 2_000_000
MAX_DATABASE_BYTES = 512 * 1024 * 1024


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def bounded_text(value: object, maximum: int, *, empty: bool = False) -> str:
    if not isinstance(value, str):
        raise ValueError("Expected source text")
    text = unicodedata.normalize("NFC", value).strip()
    if (not text and not empty) or len(text) > maximum or any(
        unicodedata.category(char) in {"Cc", "Cf"} for char in text
    ):
        raise ValueError("Invalid source text length/control character")
    return text


def normalized_phone(raw: object) -> str:
    """Match the app's canonical base dial identity, never extensions/ranges."""
    if not isinstance(raw, str) or not re.fullmatch(r"[+*0-9() .\-]+", raw):
        return ""
    dial = re.sub(r"[^0-9+*]", "", raw)
    if dial.startswith("+972"):
        dial = "0" + dial[4:]
    elif dial.startswith("00972"):
        dial = "0" + dial[5:]
    elif dial.startswith("972"):
        dial = "0" + dial[3:]
    return dial if re.fullmatch(r"0(?:[2-489][0-9]{7}|[57][0-9]{8})|\*[0-9]{3,6}|1[0-9]{2,9}", dial) else ""


def search_text(value: str) -> str:
    # SQLite unicode61 does not consistently treat Hebrew niqqud as part of a
    # token. Mirror the Android query normalization only in the search index;
    # the business/source strings remain unchanged for faithful display.
    return "".join(char for char in unicodedata.normalize("NFKD", value)
                   if not unicodedata.category(char).startswith("M"))


def record_values(record: dict) -> tuple:
    name = bounded_text(record.get("name"), 300)
    phone = bounded_text(record.get("phone"), 80)
    canonical = normalized_phone(phone)
    if not canonical:
        raise ValueError("Invalid app dial identity")
    source_id = bounded_text(record.get("source_id"), 100)
    match = re.fullmatch(r"osm:(node|way|relation):([1-9][0-9]*)", source_id)
    if not match:
        raise ValueError("Missing exact source object")
    source_url = f"https://www.openstreetmap.org/{match[1]}/{match[2]}"
    if record.get("source_url") != source_url or record.get("license_id") != "ODbL-1.0":
        raise ValueError("Source/license mismatch")
    if record.get("geographic_scope") != "explicit-il" or record.get("country_tag") not in {"IL", "ISR", "ISRAEL", "ישראל"}:
        raise ValueError("Country membership not explicitly sourced")
    if record.get("verification") != "source-listed-not-phone-confirmed":
        raise ValueError("Unknown verification provenance")
    source_updated = bounded_text(record.get("source_updated_at"), 80)
    stamp = dt.datetime.fromisoformat(source_updated.replace("Z", "+00:00"))
    if stamp.tzinfo is None:
        raise ValueError("Source timestamp has no timezone")
    # Extensions remain in the downloadable source NDJSON; the app calls only
    # the source-listed base number and never appends extension digits silently.
    identity = source_id + ":" + canonical
    row_id = int.from_bytes(hashlib.sha256(identity.encode()).digest()[:8], "big") & ((1 << 63) - 1)
    if row_id == 0:
        raise ValueError("Invalid stable public row identity")
    return (row_id, name, phone, phone,
            bounded_text(record.get("city", ""), 300, empty=True),
            bounded_text(record.get("category", ""), 300, empty=True),
            bounded_text(record.get("address", ""), 1200, empty=True),
            source_url, source_id, "ODbL-1.0", source_updated, canonical)


def create_schema(database: sqlite3.Connection) -> None:
    database.executescript("""
    PRAGMA user_version=1;
    CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL);
    CREATE TABLE businesses(
      id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT NOT NULL,
      phone_display TEXT NOT NULL,city TEXT NOT NULL,category TEXT NOT NULL,
      address TEXT NOT NULL,source_url TEXT NOT NULL,source_id TEXT NOT NULL,
      source_license TEXT NOT NULL,source_updated_at TEXT NOT NULL,
      normalized_phone TEXT NOT NULL);
    CREATE INDEX business_name ON businesses(name COLLATE NOCASE,id);
    CREATE INDEX business_phone ON businesses(normalized_phone COLLATE NOCASE);
    CREATE VIRTUAL TABLE business_search USING fts4(
      name,city,category,phone,tokenize=unicode61);
    """)


def package(source: Path, receipt_path: Path, output: Path, dataset_version: str,
            minimum_records: int = 1) -> dict:
    if output.exists():
        raise ValueError("Output already exists; never overwrite a dataset")
    if not re.fullmatch(r"[A-Za-z0-9._-]{1,80}", dataset_version):
        raise ValueError("Invalid dataset version")
    if not 1 <= minimum_records <= MAX_RECORDS:
        raise ValueError("Invalid minimum record count")
    extraction = json.loads((source / "source-manifest.json").read_text())
    receipt = json.loads(receipt_path.read_text())
    # A checksum authenticates consistency of retained build inputs, not the
    # truth/current ownership of a phone. The publisher must review provenance.
    if (receipt.get("status") != "DOWNLOADED" or receipt.get("http_status") != 200
        or receipt.get("source_url") != SOURCE_URL or receipt.get("source_license") != "ODbL-1.0"
        or not isinstance(receipt.get("actual_source_bytes"), int)
        or receipt["actual_source_bytes"] <= 0
        or not re.fullmatch(r"[a-f0-9]{64}", str(receipt.get("source_sha256", "")))
        or receipt["source_sha256"] != extraction.get("source_sha256")
        or receipt["actual_source_bytes"] != extraction.get("source_bytes")
        or extraction.get("source_url") != SOURCE_URL
        or extraction.get("license_id") != "ODbL-1.0"
        or extraction.get("scope") != "explicit-il"):
        raise ValueError("Successful matching acquisition receipt and explicit IL scope required")
    for file, hash_key in (("businesses.sqlite", "sqlite_sha256"), ("businesses.ndjson", "ndjson_sha256")):
        if sha256(source / file) != extraction.get(hash_key):
            raise ValueError("Extracted source checksum mismatch")
    measured = extraction.get("record_count")
    if not isinstance(measured, int) or not 1 <= measured <= MAX_RECORDS:
        raise ValueError("Invalid measured source count")
    imported_at = bounded_text(extraction.get("imported_at"), 80)
    if dt.datetime.fromisoformat(imported_at.replace("Z", "+00:00")).tzinfo is None:
        raise ValueError("Import timestamp has no timezone")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="business-package-", dir=output.parent) as temporary:
        tmp = Path(temporary)
        assets = tmp / "app-assets" / "business-directory"
        assets.mkdir(parents=True)
        database_path = assets / "israel-businesses.sqlite"
        counts = collections.Counter()
        with sqlite3.connect(database_path) as database:
            create_schema(database)
            with (source / "businesses.ndjson").open(encoding="utf-8") as records:
                for line in records:
                    counts["source_records"] += 1
                    if counts["source_records"] > MAX_RECORDS or len(line) > 65536:
                        raise ValueError("Source record bound exceeded")
                    try:
                        values = record_values(json.loads(line))
                    except (ValueError, TypeError, KeyError):
                        counts["rejected_invalid_source_record"] += 1
                        continue
                    existing = database.execute("SELECT source_id,normalized_phone FROM businesses WHERE id=?", values[:1]).fetchone()
                    if existing:
                        if existing != (values[8], values[11]):
                            raise ValueError("Public row hash collision")
                        counts["duplicate_base_number_extensions"] += 1
                        continue
                    database.execute("INSERT INTO businesses VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", values)
                    counts["packaged_records"] += 1
            if counts["source_records"] != measured:
                raise ValueError("Measured extraction row count does not match NDJSON")
            if counts["packaged_records"] < minimum_records:
                raise ValueError("No sufficiently populated valid dataset; no output published")
            for row_id, name, city, category, phone in database.execute("SELECT id,name,city,category,phone FROM businesses"):
                database.execute("INSERT INTO business_search(docid,name,city,category,phone) VALUES (?,?,?,?,?)",
                    (row_id, search_text(name), search_text(city), search_text(category), phone))
            database.execute("INSERT INTO metadata VALUES ('dataset_version',?)", (dataset_version,))
            database.execute("INSERT INTO metadata VALUES ('attribution',?)", (ATTRIBUTION,))
            database.execute("INSERT INTO metadata VALUES ('source_sha256',?)", (receipt["source_sha256"],))
            database.execute("INSERT INTO metadata VALUES ('phone_verification',?)", ("source-listed-not-phone-confirmed",))
            unique = database.execute("SELECT count(DISTINCT normalized_phone) FROM businesses").fetchone()[0]
            if database.execute("PRAGMA quick_check(1)").fetchone()[0] != "ok":
                raise ValueError("Generated database integrity failure")
        if database_path.stat().st_size > MAX_DATABASE_BYTES:
            raise ValueError("Generated database exceeds app cache bound")
        manifest = dict(format="DA_CALLER_BUSINESSES", schema_version=1,
            dataset_version=dataset_version, database_sha256=sha256(database_path),
            record_count=counts["packaged_records"], distinct_phone_count=unique,
            source_name=ATTRIBUTION, source_url=SOURCE_PAGE, license="ODbL-1.0",
            license_url=LICENSE_URL, updated_at=imported_at,
            verification="Source-listed public business phones; current ownership not confirmed",
            coverage="Partial source coverage only; no complete-country or million-number claim",
            timestamp_note="updated_at is extraction time; individual source object dates are separate",
            scope="explicit-il")
        (assets / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        notice = (ATTRIBUTION + "\nOpen Database License (ODbL) 1.0: " + LICENSE_URL
            + "\nSource: " + SOURCE_PAGE
            + "\nDataset: " + dataset_version
            + "\nThis public derived database is offered under ODbL1.0, independently of application code and private contacts."
            + "\nThe exact machine-readable derived SQLite database and source NDJSON must remain publicly downloadable alongside the distributing application."
            + "\nNames/numbers are source-listed; format validation does not confirm current ownership or complete coverage.\n")
        (assets / "LICENSE-NOTICE.txt").write_text(notice, encoding="utf-8")
        redistribution = tmp / "redistribution"
        redistribution.mkdir()
        for item in assets.iterdir():
            shutil.copyfile(item, redistribution / item.name)
        for filename in ("businesses.ndjson", "source-manifest.json"):
            shutil.copyfile(source / filename, redistribution / filename)
        shutil.copyfile(receipt_path, redistribution / "acquisition-receipt.json")
        report = dict(status="PACKAGED_NOT_PUBLISHED", dataset_version=dataset_version,
            counts=dict(sorted(counts.items())), distinct_phone_count=unique,
            database_sha256=manifest["database_sha256"], source_sha256=receipt["source_sha256"],
            production_installation="Not automatic; copy app-assets only after source/license and native review",
            license_requirement="Publish exact redistribution/ contents with the app; do not hide derived data behind access controls")
        (tmp / "packaging-report.json").write_text(json.dumps(report, indent=2) + "\n")
        tmp.rename(output)
        return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("receipt", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--dataset-version", required=True)
    parser.add_argument("--minimum-records", type=int, default=1)
    args = parser.parse_args()
    print(json.dumps(package(args.source, args.receipt, args.output,
        args.dataset_version, args.minimum_records), indent=2))


if __name__ == "__main__":
    main()
