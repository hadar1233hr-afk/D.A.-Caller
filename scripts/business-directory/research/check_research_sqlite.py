"""Meaningful research-search contracts; synthetic source rows, no app tests."""
import copy
import hashlib
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
from unittest import mock

from stage_research_sqlite import (
    DATABASE_APPLICATION_ID, build, create_schema, insert_original, query, record_key,
    search_expression, validate_cohort,
)


def sample(identifier=1, name="בֵּית קָפֶה", phone="+972502345678", extension=""):
    return dict(source_id=f"osm:node:{identifier}", source_url=f"https://www.openstreetmap.org/node/{identifier}",
                source_updated_at="2026-01-01T00:00:00+00:00", name=name, phone=phone, e164=phone,
                extension=extension, city="", address="", category="amenity:cafe", country_tag=None,
                source_country_fields={}, geographic_scope="synthetic-research", source_names={"name": name},
                status="RESEARCH_ONLY_NOT_APP_ACCEPTED", license_id="ODbL-1.0",
                verification="source-listed-not-phone-confirmed")


def line(row):
    return (json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n").encode()


class ResearchSearchContract(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "research.sqlite"
        self.database = sqlite3.connect(self.path)
        create_schema(self.database)

    def tearDown(self):
        self.database.close()
        self.temp.cleanup()

    def add(self, row, ordinal=0):
        decision = insert_original(self.database, "node", ordinal, row, line(row))
        self.database.commit()
        return decision

    def test_raw_bytes_sha_identity_country_and_source_fields_unchanged(self):
        row = sample();before = copy.deepcopy(row);self.add(row)
        raw, digest = self.database.execute("SELECT original_line,line_sha256 FROM raw_records").fetchone()
        self.assertEqual(line(row), raw);self.assertEqual(hashlib.sha256(raw).hexdigest(), digest)
        self.assertEqual(before,row)
        self.assertEqual((None, "{}", row["phone"], ""), self.database.execute(
            "SELECT country_tag,country_fields_json,source_phone,extension FROM research_entries").fetchone())

    def test_cf_source_row_is_retained_but_never_indexed(self):
        row=sample(name="\u200eמקור");decision=self.add(row)
        self.assertEqual("SOURCE_TEXT_WITHHELD",decision["text_status"])
        self.assertEqual(1,self.database.execute("SELECT count(*) FROM raw_records").fetchone()[0])
        self.assertEqual(0,self.database.execute("SELECT count(*) FROM research_search").fetchone()[0])
        self.assertEqual([],query(self.path,"מקור",include_phone_proposals=True))

    def test_city_control_is_withheld_even_when_name_is_clean(self):
        row=sample();row['city']='City\u200e';self.add(row)
        self.assertEqual(0,self.database.execute("SELECT sum(default_searchable) FROM research_entries").fetchone()[0])
        self.assertEqual(row['city'],self.database.execute("SELECT city FROM research_entries").fetchone()[0])

    def test_uan_proposal_separate_default_hidden_explicit_research_optin(self):
        row=sample(name='Source service',phone='+9721599555535');self.add(row)
        self.assertEqual([],query(self.path,'1599555535'))
        found=query(self.path,'1599555535',include_phone_proposals=True)
        self.assertEqual(1,len(found));self.assertEqual(row['phone'],found[0]['source_phone'])
        self.assertEqual('1599555535',found[0]['national_identity_proposal']);self.assertEqual(0,found[0]['app_accepted'])

    def test_extensions_stay_separate_and_distinct_identity_keys(self):
        first=sample(extension='11');second=sample(extension='12');self.add(first,0);self.add(second,1)
        self.assertNotEqual(record_key(first),record_key(second))
        self.assertEqual(['11','12'],[r[0] for r in self.database.execute('SELECT extension FROM research_entries ORDER BY extension')])
        self.assertEqual({first['phone']},{r[0] for r in self.database.execute('SELECT source_phone FROM research_entries')})

    def test_duplicate_source_identity_fails_without_merge(self):
        row=sample();self.add(row)
        with self.assertRaises(sqlite3.IntegrityError):self.add(row,1)
        self.assertEqual(1,self.database.execute('SELECT count(*) FROM raw_records').fetchone()[0])

    def test_different_source_objects_sharing_phone_are_retained(self):
        self.add(sample(1),0);self.add(sample(2),1)
        self.assertEqual(2,len(query(self.path,'0502345678')))

    def test_hebrew_diacritics_and_english_accent_search_preserve_display(self):
        row=sample();self.add(row);self.add(sample(2,'Café original'),1)
        self.assertEqual(row['name'],query(self.path,'בית קפה')[0]['name'])
        self.assertEqual('Café original',query(self.path,'cafe')[0]['name'])

    def test_formatted_national_phone_search_uses_same_source_identity(self):
        row=sample();self.add(row)
        result=query(self.path,'(050) 234-5678')
        self.assertEqual(row['phone'],result[0]['source_phone'])

    def test_punctuation_and_query_operators_are_literal_tokens(self):
        self.add(sample(name='Alpha shop'))
        self.assertEqual([],query(self.path,'"alpha") OR unknown* --'))
        self.assertEqual(1,len(query(self.path,'alpha')))
        self.assertEqual([],query(self.path,'"()---'))

    def test_keyset_pages_do_not_repeat_equal_names_or_phones(self):
        for ordinal in range(5):self.add(sample(ordinal+1,'Same shop'),ordinal)
        first=query(self.path,'same',limit=2);last=first[-1]
        second=query(self.path,'same',limit=2,after=(last['sort_name'],last['record_key']))
        last=second[-1];third=query(self.path,'same',limit=2,after=(last['sort_name'],last['record_key']))
        self.assertEqual(5,len({r['record_key'] for r in first+second+third}))
        self.assertEqual(5,len(first+second+third))

    def test_queries_open_readonly_and_keep_database_bytes(self):
        self.add(sample());before=self.path.read_bytes();query(self.path,'בית')
        self.assertEqual(before,self.path.read_bytes())
        with self.assertRaises(sqlite3.IntegrityError):
            self.database.execute('UPDATE research_entries SET app_accepted=1')

    def test_staging_schema_cannot_masquerade_as_production_businesses(self):
        names={row[0] for row in self.database.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        self.assertNotIn('businesses',names);self.assertNotIn('metadata',names)
        self.assertEqual(DATABASE_APPLICATION_ID,self.database.execute('PRAGMA application_id').fetchone()[0])
        self.assertEqual('ok',self.database.execute('PRAGMA integrity_check').fetchone()[0])
        self.assertEqual([],self.database.execute('PRAGMA foreign_key_check').fetchall())

    def test_invalid_query_bounds_and_wrong_database_are_rejected(self):
        for limit in (0,101,True):
            with self.assertRaises(ValueError):query(self.path,'a',limit=limit)
        with self.assertRaises(ValueError):search_expression('x'*301)
        self.database.execute('PRAGMA application_id=0');self.database.commit()
        with self.assertRaises(ValueError):query(self.path,'a')

    def test_mismatched_source_line_cannot_replace_raw_record(self):
        with self.assertRaises(ValueError):insert_original(self.database,'node',0,sample(),line(sample(2)))

    def test_numbering_invalid_source_is_retained_but_not_searchable(self):
        row=sample(phone='+972501234567');self.add(row)
        self.assertEqual(row['phone'],self.database.execute('SELECT source_phone FROM research_entries').fetchone()[0])
        self.assertEqual([],query(self.path,'בית',include_phone_proposals=True))

    def test_phone_proposal_optin_rejects_truthy_strings_and_numbers(self):
        self.add(sample(phone='+9721599555535'))
        for value in ('false','true',0,1,None):
            with self.assertRaises(ValueError):query(self.path,'1599',include_phone_proposals=value)

    def test_fts_projection_stays_bound_to_source_after_sparse_row_vacuum(self):
        for ordinal,name in enumerate(('First shop','Middle shop','Last shop')):
            self.add(sample(ordinal+1,name),ordinal)
        key=record_key(sample(2,'Middle shop'))
        self.database.execute('DELETE FROM research_search WHERE docid=2')
        self.database.execute('DELETE FROM research_entries WHERE record_key=?',(key,))
        self.database.execute('DELETE FROM raw_records WHERE record_key=?',(key,))
        self.database.commit();self.database.execute('VACUUM')
        self.assertEqual('osm:node:3',query(self.path,'last')[0]['source_id'])
        self.assertEqual([1,3],[r[0] for r in self.database.execute('SELECT search_rowid FROM research_entries ORDER BY search_rowid')])


class ProvenanceContract(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.base=Path(self.temp.name)
        self.corpus=self.base/'source.ndjson';self.corpus.write_bytes(line(sample()))
        self.source_sha='a'*64;self.source_bytes=123
        self.report={'source_pbf_sha256':self.source_sha,'source_pbf_bytes':123,
                     'ndjson_sha256':hashlib.sha256(self.corpus.read_bytes()).hexdigest(),
                     'research_raw_phone_records':1,'research_unique_source_nodes':1,
                     'scope':'synthetic-research'}
        self.audit={'status':'INDEPENDENT_ACTUAL_SOURCE_NODE_AUDIT_PASS_RESEARCH_ONLY',
                    'ndjson_sha256':self.report['ndjson_sha256'],'v2_rows':1,'v2_unique_nodes':1,
                    'all_v2_node_ids_names_categories_phone_values_country_fields_coordinates_timestamps_matched_actual_PBF':True,
                    'input_rows_and_hashes_matched':True}
        self.save()

    def tearDown(self):self.temp.cleanup()

    def save(self):
        (self.base/'node-geography-report.json').write_text(json.dumps(self.report))
        (self.base/'node-geography-source-audit.json').write_text(json.dumps(self.audit))

    def validate(self):
        return validate_cohort('node',self.corpus,self.base,self.source_sha,self.source_bytes)

    def test_bound_synthetic_fixture_preserves_exact_original_line(self):
        rows,binding=self.validate()
        self.assertEqual(self.corpus.read_bytes(),rows[0][1])
        self.assertEqual(self.audit['status'],binding['audit_status'])

    def test_changed_corpus_rejected_before_any_staging_output(self):
        self.corpus.write_bytes(line(sample(name='Changed')))
        with self.assertRaises(ValueError):self.validate()

    def test_false_or_wrong_audit_cannot_approve_source_rows(self):
        for key,value in [('status','PENDING'),('v2_rows',2),('input_rows_and_hashes_matched',False),
                          ('all_v2_node_ids_names_categories_phone_values_country_fields_coordinates_timestamps_matched_actual_PBF',False)]:
            original=self.audit.copy();self.audit[key]=value;self.save()
            with self.assertRaises(ValueError):self.validate()
            self.audit=original

    def test_different_pbf_hash_or_byte_count_cannot_reuse_audit(self):
        for key,value in [('source_pbf_sha256','b'*64),('source_pbf_bytes',124)]:
            original=self.report.copy();self.report[key]=value;self.save()
            with self.assertRaises(ValueError):self.validate()
            self.report=original

    def test_existing_output_never_overwritten_even_if_inputs_missing(self):
        target=self.base/'retained';target.mkdir();marker=target/'original';marker.write_bytes(b'unchanged')
        with self.assertRaises(ValueError):
            build(self.base/'missing',self.corpus,self.corpus,self.base,target)
        self.assertEqual(b'unchanged',marker.read_bytes())

    def test_relation_cohort_requires_true_hole_aware_audit(self):
        row=sample();row.update(source_id='osm:relation:1',source_url='https://www.openstreetmap.org/relation/1')
        corpus=self.base/'relation.ndjson';corpus.write_bytes(line(row))
        report={'source_pbf_sha256':self.source_sha,'source_pbf_bytes':123,
                'ndjson_sha256':hashlib.sha256(corpus.read_bytes()).hexdigest(),
                'raw_phone_rows':1,'source_relations':1,'scope':'synthetic-research'}
        report_path=self.base/'relation-geography-report.json';report_path.write_text(json.dumps(report))
        audit={'status':'PASS_RESEARCH_SOURCE_AND_HOLE_AWARE_RELATION_AUDIT_ONLY',
               'source_pbf_sha256':self.source_sha,'research_ndjson_sha256':report['ndjson_sha256'],
               'research_report_sha256':hashlib.sha256(report_path.read_bytes()).hexdigest(),
               'emitted_phone_rows':1,'emitted_source_relations':1,'checks_passed':{
                   'exact_role_cycles_traversal_source_edges_true_holes_rows':1,
                   'independent_complete_polygon_valid_area_containment_margin_exclusion_rows':1}}
        audit_path=self.base/'relation-geography-independent-audit.json';audit_path.write_text(json.dumps(audit))
        self.assertEqual(1,len(validate_cohort('relation',corpus,self.base,self.source_sha,123)[0]))
        audit['checks_passed']['exact_role_cycles_traversal_source_edges_true_holes_rows']=0
        audit_path.write_text(json.dumps(audit))
        with self.assertRaises(ValueError):validate_cohort('relation',corpus,self.base,self.source_sha,123)

    def test_source_change_during_build_leaves_no_partial_output(self):
        pbf=self.base/'synthetic-source.pbf';pbf.write_bytes(b'synthetic-not-real-PBF')
        self.source_sha=hashlib.sha256(pbf.read_bytes()).hexdigest();self.source_bytes=pbf.stat().st_size
        self.report.update(source_pbf_sha256=self.source_sha,source_pbf_bytes=self.source_bytes);self.save()
        row=sample();row.update(source_id='osm:way:1',source_url='https://www.openstreetmap.org/way/1')
        way=self.base/'way.ndjson';way.write_bytes(line(row))
        report={'source_pbf_sha256':self.source_sha,'source_pbf_bytes':self.source_bytes,
                'ndjson_sha256':hashlib.sha256(way.read_bytes()).hexdigest(),
                'raw_phone_rows':1,'source_ways':1,'scope':'synthetic-research'}
        report_path=self.base/'way-geography-report.json';report_path.write_text(json.dumps(report))
        audit={'status':'PASS_RESEARCH_SOURCE_AND_COMPLETE_POLYGON_AUDIT_ONLY',
               'source_pbf_sha256':self.source_sha,'research_ndjson_sha256':report['ndjson_sha256'],
               'research_report_sha256':hashlib.sha256(report_path.read_bytes()).hexdigest(),
               'source_way_rows':1,'unique_original_source_ways_found':1,'checks_passed':{
                   'independent_complete_polygon_valid_area_containment_margin_exclusion_rows':1}}
        (self.base/'way-geography-independent-audit.json').write_text(json.dumps(audit))
        def change_after_validated(*args):
            result=insert_original(*args);pbf.write_bytes(b'changed-during-build');return result
        target=self.base/'new-stage'
        with mock.patch('stage_research_sqlite.insert_original',side_effect=change_after_validated):
            with self.assertRaises(ValueError):build(pbf,self.corpus,way,self.base,target)
        self.assertFalse(target.exists())


if __name__=='__main__':unittest.main(verbosity=2)
