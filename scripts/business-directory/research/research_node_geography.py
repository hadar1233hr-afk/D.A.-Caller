#!/usr/bin/env python3
"""Offline, NODE-only source-spatial research; never an application importer.

Source geometry is not an official/legal country determination or proof of phone
ownership. Missing country tags remain missing. All outputs are research-only.
"""
from __future__ import annotations
import argparse, collections, ctypes, ctypes.util, datetime as dt, hashlib
import importlib.util, json, math, tempfile
from pathlib import Path

RELATIONS = {'source_IL': 1473946, 'Gaza_admin4': 1473938,
             'JudeaSamaria_admin4_outer_filled': 1803010,
             'WestBank_historic_outer_filled': 12331025}
IL_TAGS = frozenset({'IL', 'ISR', 'ISRAEL', 'ישראל'})
SCOPE = 'research-node-source-IL-interior-minus-filled-WB-and-Gaza'

def sha256(path: Path) -> str:
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()

COUNTRY_FIELDS = ('addr:country', 'country', 'is_in:country_code', 'is_in:country')

def country_decision(tags: dict) -> tuple[str, str | None]:
    # ANY explicit conflict wins over a preferred IL field or an empty addr tag.
    # Only the original extractor's existing IL aliases qualify; no guessing.
    countries = [tags[key].strip().upper() for key in COUNTRY_FIELDS
                 if isinstance(tags.get(key), str) and tags[key].strip()]
    country = countries[0] if countries else None
    if any(value not in IL_TAGS for value in countries):
        return 'reject-explicit-non-IL', country
    return ('explicit-IL' if country else 'missing-source-country-tag'), country

def valid_coordinates(lon, lat) -> bool:
    return (isinstance(lon, (int, float)) and not isinstance(lon, bool)
            and isinstance(lat, (int, float)) and not isinstance(lat, bool)
            and math.isfinite(lon) and math.isfinite(lat)
            and -180 <= lon <= 180 and -90 <= lat <= 90)

class GeometryScope:
    """GEOS predicates in source lon/lat units; angular margin is NOT meters."""
    def __init__(self, rings: dict, margin_degrees: float = 0.001):
        if not 0 < margin_degrees <= 0.01:
            raise ValueError('Positive conservative angular margin <=0.01 required')
        self.margin = margin_degrees
        library = ctypes.util.find_library('geos_c')
        if not library:
            raise RuntimeError('Preinstalled GEOS C library required; no fallback guesses')
        self.g = g = ctypes.CDLL(library)
        ptr = ctypes.c_void_p
        g.initGEOS.argtypes = [ptr, ptr]; g.initGEOS(None, None)
        g.GEOSWKTReader_create.restype = ptr
        g.GEOSWKTReader_read.argtypes = [ptr, ctypes.c_char_p]; g.GEOSWKTReader_read.restype = ptr
        g.GEOSWKTReader_destroy.argtypes = [ptr]
        g.GEOSGeom_destroy.argtypes = [ptr]
        g.GEOSisValid.argtypes = [ptr]; g.GEOSisValid.restype = ctypes.c_char
        g.GEOSBoundary.argtypes = [ptr]; g.GEOSBoundary.restype = ptr
        g.GEOSContains.argtypes = [ptr, ptr]; g.GEOSContains.restype = ctypes.c_char
        g.GEOSIntersects.argtypes = [ptr, ptr]; g.GEOSIntersects.restype = ctypes.c_char
        g.GEOSDistance.argtypes = [ptr, ptr, ctypes.POINTER(ctypes.c_double)]; g.GEOSDistance.restype = ctypes.c_int
        self.reader = g.GEOSWKTReader_create()
        self.polygons, self.boundaries = {}, {}
        try:
            for name in RELATIONS:
                values = rings.get(name)
                if not isinstance(values, list) or len(values) != 1:
                    raise ValueError(f'Exactly one validated source outer ring required: {name}')
                coords = values[0].get('coordinates')
                if (not isinstance(coords, list) or len(coords) < 4 or coords[0] != coords[-1]
                    or any(not isinstance(p, list) or len(p) != 2 or not valid_coordinates(*p) for p in coords)):
                    raise ValueError(f'Complete valid closed coordinates required: {name}')
                wkt = 'POLYGON ((' + ','.join(f'{p[0]} {p[1]}' for p in coords) + '))'
                geom = g.GEOSWKTReader_read(self.reader, wkt.encode())
                if not geom or g.GEOSisValid(geom) != b'\x01':
                    if geom: g.GEOSGeom_destroy(geom)
                    raise ValueError(f'GEOS-valid source polygon required: {name}')
                self.polygons[name] = geom
                boundary = g.GEOSBoundary(geom)
                if not boundary: raise ValueError('Failed source boundary construction')
                self.boundaries[name] = boundary
        except BaseException:
            self.close(); raise
    def predicate(self, name, first, second):
        result = getattr(self.g, name)(first, second)
        if result not in (b'\x00', b'\x01'):
            raise RuntimeError('GEOS predicate failed; no fallback acceptance')
        return result == b'\x01'
    def distance(self, first, second):
        value = ctypes.c_double()
        if self.g.GEOSDistance(first, second, ctypes.byref(value)) != 1 or not math.isfinite(value.value):
            raise RuntimeError('GEOS distance failed; no fallback acceptance')
        return value.value
    def classify(self, lon: float, lat: float) -> str:
        if not valid_coordinates(lon, lat): return 'reject-invalid-coordinates'
        point = self.g.GEOSWKTReader_read(self.reader, f'POINT ({lon} {lat})'.encode())
        if not point: raise RuntimeError('GEOS point construction failed')
        try:
            if not self.predicate('GEOSContains', self.polygons['source_IL'], point):
                return 'reject-outside-or-on-source-IL'
            if self.distance(self.boundaries['source_IL'], point) <= self.margin:
                return 'reject-near-source-IL-boundary'
            for name in RELATIONS:
                if name == 'source_IL': continue
                if (self.predicate('GEOSIntersects', self.polygons[name], point)
                    or self.distance(self.polygons[name], point) <= self.margin):
                    return 'reject-inside-or-near-' + name
            return 'research-qualified-source-interior'
        finally: self.g.GEOSGeom_destroy(point)
    def close(self):
        for value in getattr(self, 'boundaries', {}).values(): self.g.GEOSGeom_destroy(value)
        for value in getattr(self, 'polygons', {}).values(): self.g.GEOSGeom_destroy(value)
        self.boundaries = {}; self.polygons = {}
        if getattr(self, 'reader', None): self.g.GEOSWKTReader_destroy(self.reader); self.reader = None

def validate_geometry_provenance(base: Path, source_hash: str) -> tuple[dict, dict]:
    ring_path = base/'research-region-outer-rings.json'
    inventory = json.loads((base/'research-region-geometry-inventory.json').read_text())
    if inventory.get('source_pbf_sha256') != source_hash:
        raise ValueError('Source geometry inventory is for a different PBF')
    inputs = {name: sha256(base/name) for name in (
        'research-region-outer-rings.json', 'research-region-geometry-inventory.json',
        'country-relation-inventory.json', 'disputed-region-relation-inventory.json')}
    relations = {}
    for filename, key in [('country-relation-inventory.json', 'country_level_or_il_ps_iso_relations'),
                          ('disputed-region-relation-inventory.json', 'relations')]:
        data = json.loads((base/filename).read_text())
        if data.get('source_pbf_sha256') != source_hash: raise ValueError('Relation inventory/source mismatch')
        for row in data[key]:
            if row['id'] in RELATIONS.values(): relations[row['id']] = row
    provenance = {}
    for name, identifier in RELATIONS.items():
        row = inventory['countries'][name]
        if (row.get('relation_id') != identifier or row.get('missing_way_ids')
            or row.get('missing_referenced_nodes') or row.get('ring_assembly_error')
            or row.get('endpoint_degrees_other_than_two') or not row.get('all_member_ways_used')
            or row.get('closed_outer_ring_count') != 1 or row.get('geos_is_valid') is not True):
            raise ValueError('Incomplete/invalid source geometry: '+name)
        if name == 'source_IL' and row.get('all_source_roles', {}).get('inner', 0):
            raise ValueError('IL inclusion holes cannot be silently filled')
        source = relations[identifier]
        if name == 'source_IL' and source['tags'].get('ISO3166-1') != 'IL':
            raise ValueError('IL source relation has wrong ISO tag')
        if not source.get('timestamp'): raise ValueError('Relation timestamp missing')
        provenance[name] = {'relation_id': identifier, 'source_url': f'https://www.openstreetmap.org/relation/{identifier}',
            'source_updated_at': source['timestamp'], 'source_tags': {k: source['tags'].get(k) for k in
            ('name:en', 'ISO3166-1', 'type', 'boundary', 'admin_level', 'disputed')},
            'all_members_complete': True, 'inner_holes_filled_for_exclusion_only': row.get('inner_holes_filled_for_conservative_exclusion', False)}
    audit = json.loads((base/'geography-research-review.json').read_text())
    receipt = json.loads((base/'owner-upload-receipt.json').read_text())
    if (audit.get('source_pbf_sha256') != source_hash
        or receipt.get('pbf_sha256') != source_hash
        or receipt.get('publisher_provenance_independently_verified') is not False
        or audit.get('conservative_exclusion_geometry') != inventory):
        raise ValueError('Source audit/owner-transfer/geometry inventory identity mismatch')
    for name, digest in inputs.items():
        recorded = audit.get('work_input_sha256', {}).get(name)
        if recorded is not None and recorded != digest:
            raise ValueError('Retained source geometry input hash changed: '+name)
    if audit.get('work_input_sha256', {}).get('research-region-outer-rings.json') != inputs['research-region-outer-rings.json']:
        raise ValueError('Source outer rings lack independent retained hash binding')
    inputs['geography-research-review.json'] = sha256(base/'geography-research-review.json')
    inputs['owner-upload-receipt.json'] = sha256(base/'owner-upload-receipt.json')
    return json.loads(ring_path.read_text()), {'input_sha256': inputs, 'relations': provenance}

def run(pbf: Path, geometry_base: Path, output: Path, extractor_path: Path, margin_degrees: float = 0.001) -> dict:
    if output.exists(): raise ValueError('Output exists; research outputs are immutable')
    source_hash = sha256(pbf)
    rings, geometry = validate_geometry_provenance(geometry_base, source_hash)
    if json.loads((geometry_base/'owner-upload-receipt.json').read_text()).get('pbf_bytes') != pbf.stat().st_size:
        raise ValueError('Actual source byte count differs from owner-transfer receipt')
    spec = importlib.util.spec_from_file_location('existing_business_extractor', extractor_path)
    extract = importlib.util.module_from_spec(spec); spec.loader.exec_module(extract)
    osmium = extract.osmium
    imported_at = dt.datetime.now(dt.timezone.utc).isoformat()
    scope = GeometryScope(rings, margin_degrees)
    counts = collections.Counter(); rows = []
    class Handler(osmium.SimpleHandler):
        def node(self, obj):
            counts['prefiltered_nodes_seen'] += 1
            tags = dict(obj.tags); category = extract.category(tags)
            if not category: return
            counts['qualifying_business_category_nodes'] += 1
            name = next((extract.clean(tags[k]) for k in ('name:he', 'name', 'name:en') if tags.get(k, '').strip()), '')
            if not name: counts['reject-missing-source-name'] += 1; return
            raw_phones = [tags[k] for k in ('contact:phone', 'phone') if tags.get(k)]
            if not raw_phones: return
            counts['named_business_nodes_with_phone_tag'] += 1
            country_method, country_tag = country_decision(tags)
            if country_method == 'reject-explicit-non-IL': counts[country_method] += 1; return
            if not obj.location.valid(): counts['reject-invalid-coordinates'] += 1; return
            lon, lat = obj.location.lon, obj.location.lat
            decision = scope.classify(lon, lat); counts[decision] += 1
            if decision != 'research-qualified-source-interior': return
            contact_phones, seen = [], set()
            for raw in raw_phones:
                valid, rejected = extract.phones(raw); counts.update('rejected_phone_'+reason for reason in rejected)
                for phone in valid:
                    key = (phone['phone'], phone['extension'])
                    if key not in seen: contact_phones.append(phone); seen.add(key)
            # Preserve original rule: untagged star numbers do not gain an invented IL tag.
            contact_phones = [p for p in contact_phones if p['e164'] or country_tag in IL_TAGS]
            if not contact_phones: counts['reject-no-qualifying-phone'] += 1; return
            city = extract.clean(tags.get('addr:city', '')); street = extract.clean(tags.get('addr:street', ''))
            house = extract.clean(tags.get('addr:housenumber', '')); address = extract.clean(tags.get('addr:full', '')) or ' '.join(x for x in (street, house, city) if x)
            timestamp = obj.timestamp.isoformat() if obj.timestamp else None
            if not timestamp: counts['reject-missing-source-timestamp'] += 1; return
            counts['emitted_source_nodes'] += 1; counts['emitted_nodes_'+country_method] += 1
            for phone in contact_phones:
                source_id = f'osm:node:{obj.id}'
                rows.append(dict(id=source_id+':'+phone['phone']+(';ext='+phone['extension'] if phone['extension'] else ''),
                    name=name, **phone, category=category, city=city, address=address,
                    source_url=f'https://www.openstreetmap.org/node/{obj.id}', source_id=source_id,
                    source_updated_at=timestamp, imported_at=imported_at, license_id='ODbL-1.0',
                    verification='source-listed-not-phone-confirmed', country_tag=country_tag,
                    geographic_scope=SCOPE, source_country_fields={k:tags[k] for k in COUNTRY_FIELDS if tags.get(k)}, source_names={k:tags[k] for k in ('name','name:he','name:en','brand','operator') if tags.get(k)},
                    longitude=lon, latitude=lat, country_admission=country_method,
                    geometry_input_sha256=geometry['input_sha256']['research-region-outer-rings.json'],
                    margin_degrees=margin_degrees, status='RESEARCH_ONLY_NOT_APP_ACCEPTED'))
    try:
        handler = Handler()
        # C++ node/category-key filters avoid a Python callback for every object.
        osmium.apply(str(pbf), osmium.filter.EntityFilter(osmium.osm.NODE),
            osmium.filter.KeyFilter('shop','office','craft','healthcare','amenity','tourism'), handler)
    finally: scope.close()
    rows.sort(key=lambda r: (int(r['source_id'].rsplit(':',1)[1]), r['phone'], r['extension']))
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='node-geography-',dir=output.parent) as temporary:
        tmp = Path(temporary); ndjson = tmp/'research-node-businesses.ndjson'
        with ndjson.open('w') as stream:
            for row in rows: stream.write(json.dumps(row,ensure_ascii=False,sort_keys=True)+'\n')
        address_keys = collections.Counter((extract.normalized_key(r['name']),r['phone'],r['extension'],extract.normalized_key(r['address'])) for r in rows if r['address'])
        report = dict(status='RESEARCH_ONLY_NOT_APP_ACCEPTED_NOT_PUBLISHED',scope=SCOPE,
            imported_at=imported_at,source_pbf_sha256=source_hash,source_pbf_bytes=pbf.stat().st_size,
            research_raw_phone_records=len(rows),research_unique_phones=len({r['phone'] for r in rows}),
            research_unique_source_nodes=len({r['source_id'] for r in rows}),
            counts=dict(sorted(counts.items())),source_country_tags=dict(collections.Counter(r['country_tag'] or '<missing>' for r in rows)),
            category_counts=dict(collections.Counter(r['category'] for r in rows)),source_object_years=dict(collections.Counter(r['source_updated_at'][:4] for r in rows)),
            exact_address_duplicate_rows_beyond_first=sum(n-1 for n in address_keys.values()),
            cross_object_merging_applied=False,ndjson_sha256=sha256(ndjson),extractor_sha256=sha256(extractor_path),
            research_script_sha256=sha256(Path(__file__)),geometry_provenance=geometry,angular_margin_degrees=margin_degrees,
            primary_explicit_IL_package_records_unchanged=24,new_app_bundled_records=0,
            omitted_geometry='All ways and relations; only actual point coordinates examined. Borders and conservative angular margin excluded.',
            limits=['Research admission using source-declared geometry is not official/legal sovereign or country membership verification.',
                    'Both full filled WestBank outer envelopes are excluded as a union, plus full source Gaza; partial PS patches alone are not used.',
                    'Angular distance uses source lon/lat degrees, not projected/geodesic meters.',
                    'Missing source country tags remain null; no IL tag is fabricated from phone or geometry.',
                    'Phone syntax/allocation validation and source object timestamps do not confirm phone ownership or currentness.',
                    'No national completeness/official-border guarantee, no actual calls, no Android/user-contact/archive changes.'])
        (tmp/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
        tmp.rename(output)
    return report

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('pbf',type=Path);parser.add_argument('geometry_base',type=Path);parser.add_argument('output',type=Path)
    parser.add_argument('--extractor',type=Path,default=Path('/workspace/D.A.-Caller/scripts/business-directory/extract_osm_businesses.py'))
    parser.add_argument('--margin-degrees',type=float,default=0.001)
    args=parser.parse_args(); report=run(args.pbf,args.geometry_base,args.output,args.extractor,args.margin_degrees)
    print(json.dumps({k:report[k] for k in ('status','research_raw_phone_records','research_unique_phones','research_unique_source_nodes','source_country_tags','new_app_bundled_records')},ensure_ascii=False))
if __name__=='__main__':main()
