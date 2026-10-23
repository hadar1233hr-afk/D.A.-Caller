#!/usr/bin/env python3
"""Build a distributed/offline ISRAEL-ONLY business telephone SQLite DB.
Sources: Geofabrik OSM regional extract, clipped to ISR ADM0; licensed municipal business directories.
No scraping of individuals, social networks, Google Maps, or proprietary phonebooks.
License: source-based ODbL for merged database; attribution shipped with app.
"""
import argparse
import csv
import io
import json
import math
import re
import sqlite3
import time
from pathlib import Path
import requests
import osmium
from shapely.geometry import Point, shape
from shapely.prepared import prep

UA = {"User-Agent":"DA-Israel-Business-Book/1.0 (public open-data compilation)"}
ISR_BOUNDARY_API = "https://www.geoboundaries.org/api/current/gbOpen/ISR/ADM0/"
KS_URL = "https://kfar-saba.datacity.org.il/dataset/fa4b0331-6879-4fa6-b84a-7450a7a80a15/resource/09f50b32-40b1-4374-822c-c8930a7059d0/download/-.csv"
BEER_RESOURCE = "7d4c61e2-2416-453e-8efb-bd02ec89db35"
BEER_API = "https://data.gov.il/api/3/action/datastore_search"

def phone_numbers(raw):
    if raw is None:return
    for segment in re.split(r"[;|,\n/]",str(raw)):
        for part in re.findall(r"(?:(?:\+|00)972|0)[0-9 ()\-]{7,17}", segment):
            v=re.sub(r"[^0-9+]","",part.strip())
            if v.startswith("00972"):v="0"+v[5:]
            elif v.startswith("+972"):v="0"+v[4:]
            elif v.startswith("972"):v="0"+v[3:]
            if re.fullmatch(r"0[2-9][0-9]{7,8}",v) and len(set(v))>3:
                yield v

def category(tags):
    if tags.get("amenity") in ("restaurant","fast_food","cafe","food_court","ice_cream","bar"):
        return "מסעדות ובתי קפה"
    if tags.get("shop") in ("supermarket","convenience","bakery","butcher","greengrocer","deli","beverages","pastry"):
        return "מזון וסופרמרקטים"
    if tags.get("shop") in ("clothes","shoes","jewelry","fashion_accessories","boutique","department_store"):
        return "אופנה והנעלה"
    if tags.get("shop") in ("electronics","computer","mobile_phone","appliance","electrical"):
        return "מחשבים וחשמל"
    if tags.get("shop") in ("hairdresser","beauty","cosmetics","massage"):
        return "טיפוח ויופי"
    if tags.get("shop") in ("car","car_repair","car_parts","tyres","motorcycle"):
        return "רכב ומוסכים"
    if tags.get("shop"):return "חנויות וקניות"
    if tags.get("tourism") in ("hotel","guest_house","hostel","apartment","motel"):
        return "מלונות ואירוח"
    if tags.get("healthcare") or tags.get("amenity") in ("pharmacy","hospital","clinic","doctors","dentist","veterinary"):
        return "בריאות ופארם"
    if tags.get("amenity") in ("school","college","kindergarten","university","library"):
        return "חינוך ולמידה"
    if tags.get("amenity") in ("bank","atm","bureau_de_change","post_office"):
        return "בנקים ודואר"
    if tags.get("amenity") in ("fuel","charging_station") or tags.get("shop")=="fuel":
        return "דלק ותחבורה"
    if tags.get("leisure") or tags.get("tourism"):
        return "ספורט ופנאי"
    if tags.get("craft"):return "בעלי מקצוע"
    if tags.get("office"):return "משרדים ושירותים"
    if tags.get("amenity") in ("townhall","police","fire_station","social_facility","community_centre"):
        return "מוסדות ושירותים"
    return "עסקים ושירותים"

def category_from_license(label):
    t=(label or "").lower()
    if any(k in t for k in ("מסעד","בית אוכל","קפה","קייטר","מזון מוכן","פיצה","מאפ","קונדיטור")):
        return "מסעדות ובתי קפה"
    if any(k in t for k in ("מרכול","מכול","סופר","ירקות","פירות","מזון","פיצוח","איטליז")):
        return "מזון וסופרמרקטים"
    if any(k in t for k in ("מוסך","רכב","דלק","צמיג")):return "רכב ומוסכים"
    if any(k in t for k in ("קוסמט","מספר","יופי")):return "טיפוח ויופי"
    if any(k in t for k in ("רוקח","בית מרקחת","מרפא")):return "בריאות ופארם"
    if any(k in t for k in ("הלבש","הנעלה","ביגוד","נעל")):return "אופנה והנעלה"
    if any(k in t for k in ("חשמל","מחשב","סלולר")):return "מחשבים וחשמל"
    if any(k in t for k in ("חנות","קמעונ","מכירת","מכירה","קיוסק")):return "חנויות וקניות"
    if any(k in t for k in ("בנייה","ייצור","אלומניום","מסגר","נגר")):return "בעלי מקצוע"
    return "עסקים ושירותים"

def is_appropriate(tags):
    # Only public business/organizational points. Exclude residential units & private contacts.
    if tags.get("access")=="private" and not tags.get("shop"):return False
    if tags.get("amenity") in ("place_of_worship","brothel","prison"):return False
    if tags.get("shop") in ("erotic","weapons","cannabis","tobacco","alcohol"):return False
    if tags.get("building") in ("house","residential","apartments") and not tags.get("shop") and not tags.get("office"):return False
    return any(tags.get(t) for t in ("shop","amenity","office","craft","tourism","healthcare","leisure","industrial","emergency"))

def boundary():
    r=requests.get(ISR_BOUNDARY_API,headers=UA,timeout=50)
    r.raise_for_status()
    data=r.json()
    url=data.get("gjDownloadURL")
    assert data.get("boundaryISO")=="ISR" and url and data.get("boundaryLicense"), "Invalid country boundary response"
    geo=requests.get(url,headers=UA,timeout=90)
    geo.raise_for_status()
    content=geo.json()
    features=content.get("features",[])
    assert features, "Missing ISR polygon"
    from shapely.ops import unary_union
    return prep(unary_union([shape(f["geometry"]) for f in features]))

class Scan(osmium.SimpleHandler):
    def __init__(self,db,israel):
        super().__init__()
        self.db=db;self.israel=israel
        self.records=0;self.places=[]
    def node(self,obj):
        tags={x.k:x.v for x in obj.tags}
        pt=self.location(obj)
        if pt is None:return
        if tags.get("place") in ("city","town","village","hamlet") and tags.get("name"):
            if self.israel.covers(Point(pt[0],pt[1])):
                self.places.append((pt[0],pt[1],tags.get("name:he") or tags["name"]))
        self.add(tags,pt)
    def way(self,obj):
        tags={x.k:x.v for x in obj.tags}
        if not is_appropriate(tags):return
        pt=None
        for n in obj.nodes:
            if n.location.valid():
                pt=(n.location.lon,n.location.lat);break
        if pt:self.add(tags,pt)
    def relation(self,obj):
        # Multipolygon geometry is more complex; avoid attaching an unreliable country
        # to a relation lacking trustworthy coordinates.
        return
    def location(self,obj):
        try:
            p=obj.location
            return (p.lon,p.lat) if p.valid() else None
        except Exception:return None
    def add(self,tags,pt):
        if not is_appropriate(tags):return
        if not self.israel.covers(Point(pt[0],pt[1])):return
        name=next((tags[k].strip() for k in ("name:he","name","brand","operator")
                   if tags.get(k) and len(tags[k].strip())>1 and tags[k].lower() not in ("yes","private","unnamed")),"")
        if not name:return
        vals=[tags.get(k,"") for k in ("contact:phone","phone","contact:mobile","mobile")]
        phones=list(dict.fromkeys(n for val in vals for n in phone_numbers(val)))
        if not phones:return
        city=tags.get("addr:city") or tags.get("addr:place") or tags.get("is_in:city") or ""
        street=tags.get("addr:street","");house=tags.get("addr:housenumber","")
        address=" ".join(x for x in (street,house) if x)
        kind=category(tags)
        for ph in phones:
            self.db.execute("INSERT OR IGNORE INTO businesses(name,phone,city,category,address,source,lon,lat) VALUES(?,?,?,?,?,?,?,?)",
                            (name,ph,city,kind,address,"OpenStreetMap • ODbL",pt[0],pt[1]))
            self.records+=1

def read_municipal_kfar_saba(db):
    try:
        r=requests.get(KS_URL,headers=UA,timeout=45);r.raise_for_status()
        reader=csv.DictReader(io.StringIO(r.content.decode("utf-8-sig",errors="replace")))
        n=0
        for x in reader:
            name=(x.get("שם עסק") or x.get("shm \`sq") or "").strip()
            if not name:continue
            address=(x.get("כתובת") or " ".join(str(x.get(t,"")) for t in ("רחוב","מספר בית"))).strip()
            kind=category_from_license((x.get("מהות") or "")+" "+(x.get("קבוצה") or ""))
            for ph in phone_numbers(x.get("טלפון","")):
                cur=db.execute("INSERT OR IGNORE INTO businesses(name,phone,city,category,address,source,lon,lat) VALUES(?,?,?,?,?,?,NULL,NULL)",
                    (name,ph,"כפר סבא",kind,address,"עיריית כפר סבא • ODbL • 2021"))
                n+=cur.rowcount
        print("Kfar Saba new listings",n,flush=True)
        return n
    except Exception as e:
        print("Kfar Saba optional source unavailable:",str(e)[:240],flush=True)
        return 0

def read_municipal_beer_sheva(db):
    try:
        off=0;n=0
        while True:
            r=requests.get(BEER_API,headers=UA,params={"resource_id":BEER_RESOURCE,"limit":1000,"offset":off},timeout=55)
            r.raise_for_status()
            payload=r.json()
            if not payload.get("success"):raise ValueError("CKAN response reports failure")
            rows=payload["result"]["records"]
            for x in rows:
                name=(x.get("שם עסק") or "").strip()
                street=(x.get("שם רחוב") or "").strip()
                house=str(x.get("בית") or "").strip()
                label=x.get("תאור רישיון") or ""
                if not name:continue
                for ph in phone_numbers(x.get("טלפון בעסק") or ""):
                    cur=db.execute("INSERT OR IGNORE INTO businesses(name,phone,city,category,address,source,lon,lat) VALUES(?,?,?,?,?,?,NULL,NULL)",
                        (name,ph,"באר שבע",category_from_license(label)," ".join([street,house]).strip(),"עיריית באר שבע • data.gov.il"))
                    n+=cur.rowcount
            off+=len(rows)
            if not rows or len(rows)<1000 or off>=10000:break
        print("Beer Sheva new listings",n,"records reviewed",off,flush=True)
        return n
    except Exception as e:
        print("Beer Sheva optional source unavailable:",str(e)[:240],flush=True)
        return 0

def fill_cities(db,places):
    # Nearest mapped locality; only used where source left city blank.
    # Restrict candidate distance to 12 km and mark approximate in source.
    places=list(dict(((round(x,5),round(y,5),n),(x,y,n)) for x,y,n in places).values())
    entries=db.execute("SELECT id,lon,lat,source FROM businesses WHERE city='' AND lon IS NOT NULL").fetchall()
    updated=0
    for rowid,lon,lat,source in entries:
        best=(float("inf"),None)
        for x,y,name in places:
            dx=(lon-x)*math.cos(math.radians(lat))*111.2
            dy=(lat-y)*111.2
            d2=dx*dx+dy*dy
            if d2<best[0]:best=(d2,name)
        if best[1] and best[0] <= 12*12:
            cur=db.execute("UPDATE OR IGNORE businesses SET city=?,source=? WHERE id=?",(best[1],source+" • יישוב משוער",rowid))
            if cur.rowcount==0:
                db.execute("DELETE FROM businesses WHERE id=?",(rowid,))
            else:
                updated+=1
    print("Approximate localities assigned",updated,flush=True)

def main(inp,target):
    path=Path(target);path.parent.mkdir(parents=True,exist_ok=True)
    if path.exists():path.unlink()
    poly=boundary()
    db=sqlite3.connect(path)
    db.execute("CREATE TABLE businesses(id INTEGER PRIMARY KEY, name TEXT NOT NULL, phone TEXT NOT NULL, city TEXT NOT NULL DEFAULT '', category TEXT NOT NULL DEFAULT 'עסקים ושירותים', address TEXT NOT NULL DEFAULT '', source TEXT NOT NULL DEFAULT '', lon REAL,lat REAL, UNIQUE(name,phone,city))")
    db.execute("CREATE INDEX idx_business_name ON businesses(name)")
    db.execute("CREATE INDEX idx_business_category_city ON businesses(category,city)")
    db.execute("CREATE INDEX idx_business_phone ON businesses(phone)")
    scan=Scan(db,poly)
    print("Scanning OSM and clipping to ISR country polygon...",flush=True)
    scan.apply_file(inp,locations=True)
    db.commit()
    print("OSM public phone candidates",scan.records,flush=True)
    fill_cities(db,scan.places)
    db.commit()
    a=read_municipal_kfar_saba(db)
    b=read_municipal_beer_sheva(db)
    db.commit()
    # Remove coordinate columns from distributed DB; accurate geocoding is out of scope.
    # Retain only the fields actually used by the offline app.
    db.execute("CREATE TABLE compact (id INTEGER PRIMARY KEY, name TEXT NOT NULL,phone TEXT NOT NULL,city TEXT NOT NULL DEFAULT '',category TEXT NOT NULL DEFAULT 'עסקים ושירותים',address TEXT NOT NULL DEFAULT '',source TEXT NOT NULL DEFAULT '')")
    db.execute("INSERT INTO compact SELECT id,name,phone,city,category,address,source FROM businesses")
    db.execute("DROP TABLE businesses")
    db.execute("ALTER TABLE compact RENAME TO businesses")
    db.execute("CREATE INDEX idx_business_name_final ON businesses(name)")
    db.execute("CREATE INDEX idx_business_category_city_final ON businesses(category,city)")
    db.execute("CREATE INDEX idx_business_phone_final ON businesses(phone)")
    db.execute("PRAGMA user_version=1")
    count=db.execute("SELECT count(*) FROM businesses").fetchone()[0]
    distinct=db.execute("SELECT count(DISTINCT phone) FROM businesses").fetchone()[0]
    cities=db.execute("SELECT count(DISTINCT city) FROM businesses WHERE city<>''").fetchone()[0]
    print("BUNDLED ISRAEL BUSINESS PHONE BOOK: listings",count,"distinct phone numbers",distinct,"cities",cities,flush=True)
    if count<1000 or distinct<1000:
        raise RuntimeError("Directory unexpectedly too small; do not publish an empty or corrupted APK")
    check=db.execute("PRAGMA integrity_check").fetchone()[0]
    if check!="ok":raise RuntimeError("SQLite integrity test: "+check)
    db.commit();db.close()
    print("Public directory database",path.stat().st_size,"bytes",flush=True)
    # Save metrics as build output for release notes and user inspection.
    Path(target+".stats.json").write_text(json.dumps(
        {"listings":count,"unique_phone_numbers":distinct,"places_with_city_labels":cities,
         "kfar_saba_added":a,"beer_sheva_added":b},ensure_ascii=False,indent=2),encoding="utf8")

if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--pbf",required=True);p.add_argument("--db",required=True)
    a=p.parse_args();main(a.pbf,a.db)
