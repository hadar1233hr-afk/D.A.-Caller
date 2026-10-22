#!/usr/bin/env python3
"""Build an OFFLINE, ODbL-attributed directory of publicly tagged organization phones.
Input: Geofabrik OpenStreetMap regional extract. Never scrapes private phone books.
Requires: pip install osmium
"""
import argparse
import csv
import datetime
import os
import re
import sqlite3
from pathlib import Path
import osmium

POI_TAGS = ("shop", "amenity", "office", "craft", "tourism", "healthcare",
            "leisure", "public_transport", "emergency", "industrial")
NAME_KEYS = ("name:he", "name", "brand", "operator")
PHONE_KEYS = ("phone", "contact:phone", "contact:mobile")
SOURCE = "© OpenStreetMap contributors | ODbL 1.0"
SKIP_VALUES = {"yes", "unknown", "-", "no", "none", "n/a", "0"}

def normalize(phone):
    phone = phone.strip()
    if phone.lower().startswith("tel:"):
        phone = phone[4:].strip()
    # A leading international prefix from the other national numbering plan
    # should never be mapped to a local Israeli number.
    if phone.startswith("+970") or phone.startswith("00970"):
        return ""
    phone = re.sub(r"[^0-9+]", "", phone)
    if phone.startswith("00972"):
        phone = "0" + phone[5:]
    elif phone.startswith("+972"):
        phone = "0" + phone[4:]
    elif phone.startswith("972"):
        phone = "0" + phone[3:]
    elif phone.startswith("+"):
        return ""
    if not phone.startswith("0") or not (9 <= len(phone) <= 10):
        return ""
    if not re.fullmatch(r"0[2-9][0-9]{7,8}", phone):
        return ""
    if len(set(phone)) <= 2:  # obviously malformed placeholders
        return ""
    return phone

def candidate_numbers(value):
    for part in re.split(r"[;,|\n]", value):
        part=part.strip()
        if not part:
            continue
        # If a field contains prose and an explicit number, extract it.
        values = re.findall(r"(?:\+972|00972|0)[0-9\s()\-]{7,18}", part)
        if not values:
            values=[part]
        for v in values:
            n=normalize(v)
            if n:
                yield n

class Directory(osmium.SimpleHandler):
    def __init__(self, db):
        super().__init__()
        self.db = db
        self.objects = 0
        self.phone_rows = 0
        self.unique = set()
    def node(self, obj):
        self.add(obj.tags)
    def way(self, obj):
        self.add(obj.tags)
    def relation(self, obj):
        self.add(obj.tags)
    def add(self, raw):
        tags={t.k:t.v for t in raw}
        if not any(tags.get(k) for k in POI_TAGS):
            return
        self.objects+=1
        name=next((tags[k].strip() for k in NAME_KEYS
                   if tags.get(k) and tags[k].strip().lower() not in SKIP_VALUES), "")
        if not name:
            return
        # Exclude objects explicitly marked private and residential services.
        if tags.get("access")=="private" and "shop" not in tags:
            return
        kind="service" if any(tags.get(k) for k in ("amenity","healthcare","emergency","public_transport")) else "business"
        for key in PHONE_KEYS:
            if key not in tags:
                continue
            for phone in candidate_numbers(tags[key]):
                self.phone_rows+=1
                if phone in self.unique:
                    continue
                self.unique.add(phone)
                self.db.execute(
                    "INSERT OR IGNORE INTO entries(phone,name,kind,source,updated) VALUES (?,?,?,?,?)",
                    (phone,name,kind,SOURCE,0))

def import_kfar_saba(db):
    """Optional old municipal business licensing data, also ODbL. No personal directory scraping."""
    import urllib.request
    url = ("https://kfar-saba.datacity.org.il/dataset/"
           "fa4b0331-6879-4fa6-b84a-7450a7a80a15/resource/"
           "09f50b32-40b1-4374-822c-c8930a7059d0/download/-.csv")
    try:
        with urllib.request.urlopen(url,timeout=28) as response:
            blob=response.read(6_000_000).decode("utf-8-sig",errors="replace")
        rows=list(csv.DictReader(blob.splitlines()))
        n=0
        for r in rows:
            name=(r.get("שם עסק") or "").strip()
            raw=r.get("טלפון") or ""
            if not name: continue
            for phone in candidate_numbers(raw):
                cur=db.execute("INSERT OR IGNORE INTO entries(phone,name,kind,source,updated) VALUES (?,?,?,?,?)",
                               (phone,name,"business","כפר סבא: עסקים בעלי רישיון (ODbL; נתוני 2021)",0))
                n+=cur.rowcount
        print("Kfar Saba additional unique entries:",n,flush=True)
    except Exception as ex:
        print("Optional municipal source unavailable; skipping:",str(ex)[:220],flush=True)

def build(pbf, target):
    dbpath=Path(target);dbpath.parent.mkdir(parents=True,exist_ok=True)
    if dbpath.exists(): dbpath.unlink()
    db=sqlite3.connect(dbpath)
    db.execute("PRAGMA journal_mode=DELETE")
    db.execute("CREATE TABLE entries (phone TEXT PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL DEFAULT 'business', source TEXT NOT NULL DEFAULT 'local', updated INTEGER NOT NULL DEFAULT 0)")
    db.execute("CREATE INDEX idx_name ON entries(name)")
    scraper=Directory(db)
    scraper.apply_file(pbf,locations=False)
    print("OSM POI objects inspected:",scraper.objects,flush=True)
    print("OSM phone fields parsed:",scraper.phone_rows,flush=True)
    print("OSM unique contact numbers:",len(scraper.unique),flush=True)
    db.commit()
    import_kfar_saba(db)
    db.commit()
    total=db.execute("SELECT count(*) FROM entries").fetchone()[0]
    print("PACKAGED REAL PUBLIC TELEPHONE NUMBERS:",total,flush=True)
    if total < 100:
        raise RuntimeError("Input unexpectedly produced fewer than 100 phone records; refuse publishing a blank APK")
    db.execute("PRAGMA integrity_check")
    db.commit()
    db.close()
    print("SQLite asset:",target,dbpath.stat().st_size,"bytes",flush=True)

if __name__=="__main__":
    ap=argparse.ArgumentParser()
    ap.add_argument("--pbf",required=True)
    ap.add_argument("--db",required=True)
    args=ap.parse_args()
    build(args.pbf,args.db)
