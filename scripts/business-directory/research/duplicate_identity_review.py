#!/usr/bin/env python3
"""Research duplicate review: flag identity ambiguity, never merge source rows."""
from __future__ import annotations
import argparse,collections,hashlib,json,math
from pathlib import Path

def normalized_text(value: str) -> str:
    if not isinstance(value,str):raise ValueError('Source identity fields must be strings')
    return ' '.join(value.casefold().split())

def coordinate_pair(row: dict):
    lon,lat=row.get('longitude'),row.get('latitude')
    if any(not isinstance(v,(int,float)) or isinstance(v,bool) or not math.isfinite(v) for v in (lon,lat)):
        return None
    return (lon,lat) if -180<=lon<=180 and -90<=lat<=90 else None

def approximate_distance_m(first: dict,second: dict):
    a,b=coordinate_pair(first),coordinate_pair(second)
    if a is None or b is None:return None
    lon1,lat1,lon2,lat2=map(math.radians,(*a,*b))
    h=math.sin((lat2-lat1)/2)**2+math.cos(lat1)*math.cos(lat2)*math.sin((lon2-lon1)/2)**2
    return 6371008.8*2*math.asin(math.sqrt(min(1,max(0,h))))

def location_review(rows: list[dict]) -> dict:
    if len(rows)>1000:return {'classification':'large_group_manual_review','approximate_max_distance_m':None}
    distances=[approximate_distance_m(a,b) for i,a in enumerate(rows) for b in rows[i+1:]]
    if not distances or any(v is None for v in distances):
        return {'classification':'coordinates_unavailable_manual_review','approximate_max_distance_m':None}
    distance=max(distances)
    label=('nearly_coincident_manual_review' if distance<=5 else
           'nearby_manual_review' if distance<=25 else 'separated_manual_review')
    return {'classification':label,'approximate_max_distance_m':round(distance,3)}

def review(rows: list[dict]) -> dict:
    by_identity=collections.defaultdict(list);by_phone=collections.defaultdict(list)
    input_ids=collections.Counter()
    for row in rows:
        for field in ('source_id','name','phone','extension','address'):
            if not isinstance(row.get(field),str):raise ValueError('Missing/invalid source identity field: '+field)
        input_ids[(row['source_id'],row['phone'],row['extension'])]+=1
        by_phone[row['phone']].append(row)
        if normalized_text(row['address']):
            key=(normalized_text(row['name']),row['phone'],row['extension'],normalized_text(row['address']))
            by_identity[key].append(row)
    groups=[]
    for values in by_identity.values():
        if len({r['source_id'] for r in values})<2:continue
        groups.append({'source_ids':sorted({r['source_id'] for r in values}),
            'name':values[0]['name'],'phone':values[0]['phone'],'extension':values[0]['extension'],
            'address':values[0]['address'],'source_rows':len(values),**location_review(values),
            'action':'RETAIN_ALL_SOURCE_ROWS_MANUAL_REVIEW_NO_AUTOMATIC_MERGE'})
    groups.sort(key=lambda g:g['source_ids'])
    shared=[]
    for phone,values in by_phone.items():
        identifiers={r['source_id'] for r in values}
        if len(identifiers)>1:
            shared.append({'phone':phone,'source_nodes':len(identifiers),
                'names':sorted({r['name'] for r in values}),'source_ids':sorted(identifiers),
                'action':'RETAIN_DISTINCT_SOURCE_IDENTITIES_NOT_PROOF_OF_DUPLICATE_BUSINESS'})
    shared.sort(key=lambda g:g['phone'])
    return {'status':'RESEARCH_IDENTITY_REVIEW_ONLY_NO_SOURCE_OR_APP_MUTATION','input_phone_rows':len(rows),
        'distinct_source_ids':len({r['source_id'] for r in rows}),
        'rows_without_address':sum(not normalized_text(r['address']) for r in rows),
        'same_name_phone_extension_address_groups':groups,
        'group_location_classes':dict(collections.Counter(g['classification'] for g in groups)),
        'same_identity_rows_beyond_first':sum(g['source_rows']-1 for g in groups),
        'shared_phone_groups':shared,'shared_phones_with_multiple_source_ids':len(shared),
        'repeated_source_phone_extension_rows':sum(n-1 for n in input_ids.values()),
        'rows_removed_or_merged':0,'app_rows_added':0,
        'limits':['Approximate spherical distance between source coordinates, not measured premises proximity.',
                  'A shared phone, source name/address or close point is not proof of the same physical business.',
                  'Source addresses may be incomplete or outdated; no address, phone ownership or currentness confirmed.']}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('source',type=Path);parser.add_argument('output',type=Path)
    args=parser.parse_args();blob=args.source.read_bytes();rows=[json.loads(l) for l in blob.splitlines() if l.strip()]
    result=review(rows);result['input_ndjson_sha256']=hashlib.sha256(blob).hexdigest()
    with args.output.open('x') as output:output.write(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:result[k] for k in ('input_phone_rows','group_location_classes','shared_phones_with_multiple_source_ids','rows_removed_or_merged')}))
if __name__=='__main__':main()
