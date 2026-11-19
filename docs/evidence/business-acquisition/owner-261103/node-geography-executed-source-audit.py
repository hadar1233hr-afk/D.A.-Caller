import json,hashlib,importlib.util,collections,datetime,sys
from pathlib import Path
import osmium
base=Path('/workspace/work/business-data');out=base/'node-geography-owner-261103-v2';report=json.loads((out/'report.json').read_text())
rows=[json.loads(line) for line in (out/'research-node-businesses.ndjson').read_text().splitlines()];old=[json.loads(line) for line in (base/'node-geography-owner-261103/research-node-businesses.ndjson').read_text().splitlines()]
primary=[json.loads(line) for line in (base/'extracted-owner-261103-explicit-il/businesses.ndjson').read_text().splitlines()]
byid=collections.defaultdict(list)
for row in rows:byid[int(row['source_id'].rsplit(':',1)[1])].append(row)
oldids={r['id'] for r in old};newids={r['id'] for r in rows};removed=[r for r in old if r['id'] not in newids]
primary_ids={r['source_id'] for r in primary};wanted=set(byid)|{int(r['source_id'].rsplit(':',1)[1]) for r in removed}|{int(r['source_id'].rsplit(':',1)[1]) for r in primary if r['source_id'].startswith('osm:node:')}
spec=importlib.util.spec_from_file_location('extract','/workspace/D.A.-Caller/scripts/business-directory/extract_osm_businesses.py');e=importlib.util.module_from_spec(spec);spec.loader.exec_module(e)
fields=('addr:country','country','is_in:country_code','is_in:country');aliases={'IL','ISR','ISRAEL','ישראל'}
found=set();country_conflicts={};baseline_conflicts=[]
class Audit(osmium.SimpleHandler):
 def node(self,n):
  found.add(n.id);tags=dict(n.tags)
  source_fields={k:tags[k] for k in fields if tags.get(k)}
  countries=[tags[k].strip().upper() for k in fields if tags.get(k,'').strip()]
  conflicting=[k for k in fields if tags.get(k,'').strip() and tags[k].strip().upper() not in aliases]
  if conflicting:
   country_conflicts[n.id]=source_fields
   if f'osm:node:{n.id}' in primary_ids:baseline_conflicts.append({'source_id':f'osm:node:{n.id}','source_country_fields':source_fields,'conflicting_fields':conflicting,'status':'REQUIRES_COUNTRY_REVIEW_NOT_PUBLICATION_APPROVED'})
  if n.id not in byid:return
  assert not conflicting
  assert n.location.valid()
  category=e.category(tags);name=next((e.clean(tags[k]) for k in ('name:he','name','name:en') if tags.get(k,'').strip()),'')
  actualphones=[]
  for k in ('contact:phone','phone'):
   if tags.get(k):actualphones+=e.phones(tags[k])[0]
  for r in byid[n.id]:
   assert r['name']==name and r['category']==category
   assert r['source_id']==f'osm:node:{n.id}' and r['source_url']==f'https://www.openstreetmap.org/node/{n.id}'
   assert r['source_updated_at']==n.timestamp.isoformat()
   assert (r['longitude'],r['latitude'])==(n.location.lon,n.location.lat)
   assert r['source_country_fields']==source_fields
   assert r['country_tag']==(countries[0] if countries else None)
   assert any(all(r[k]==p[k] for k in ['phone','e164','extension','phone_type','dialing_scope','original_phone']) for p in actualphones)
   assert r['status']=='RESEARCH_ONLY_NOT_APP_ACCEPTED'
   assert r['geometry_input_sha256']==report['geometry_provenance']['input_sha256']['research-region-outer-rings.json']
osmium.apply(str(base/'israel-and-palestine-261103.osm.pbf'),osmium.filter.EntityFilter(osmium.osm.NODE),osmium.filter.IdFilter(wanted),Audit())
assert wanted==found;assert len(rows)==report['research_raw_phone_records'];assert len(byid)==report['research_unique_source_nodes'];assert len({r['phone'] for r in rows})==report['research_unique_phones'];assert hashlib.sha256((out/'research-node-businesses.ndjson').read_bytes()).hexdigest()==report['ndjson_sha256']
removed_audit=[]
for r in removed:
 ident=int(r['source_id'].rsplit(':',1)[1]);assert ident in country_conflicts
 removed_audit.append({'source_id':r['source_id'],'source_country_fields':country_conflicts[ident],'present_in_primary24':r['source_id'] in primary_ids,'reason':'Explicit non-IL field/conflict rejected in v2'})
result={'status':'INDEPENDENT_ACTUAL_SOURCE_NODE_AUDIT_PASS_RESEARCH_ONLY','audited_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'v2_rows':len(rows),'v2_unique_nodes':len(byid),'v2_unique_phones':len({r['phone'] for r in rows}),'all_v2_node_ids_names_categories_phone_values_country_fields_coordinates_timestamps_matched_actual_PBF':True,'input_rows_and_hashes_matched':True,'v1_provisional_rows':len(old),'v1_to_v2_removed_rows':len(removed),'v1_to_v2_removed_source_nodes':len({r['source_id'] for r in removed}),'removed_audit':removed_audit,'primary24_node_country_conflicts_requiring_review':baseline_conflicts,'overlap_source_nodes_with_primary24':len(set(r['source_id'] for r in rows)&primary_ids),'new_app_bundled_rows':0,'ndjson_sha256':report['ndjson_sha256'],'research_script_sha256':report['research_script_sha256'],'audit_script_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'limits':'Research-only source geometry/country aliases, not official legal membership, no national completeness or current phone ownership claim. Primary24 output unchanged but country conflicts identified for review.'}
(out/'source-node-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:result[k] for k in ['status','v2_rows','v2_unique_nodes','v2_unique_phones','v1_to_v2_removed_rows','overlap_source_nodes_with_primary24']}));print('primary24 country conflict source nodes',len(baseline_conflicts))
