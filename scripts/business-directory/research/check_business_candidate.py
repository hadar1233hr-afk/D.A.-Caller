"""Candidate projection contracts; synthetic rows are not admitted businesses."""
import copy
import hashlib
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
from unittest import mock
import zipfile

import prepare_business_candidate as candidate
from phone_identity_review import production_packager


def source_row(identifier=1, name="בֵּית קָפֶה", phone="+972502345678", extension=""):
    source_id=f"osm:node:{identifier}"
    return dict(id=source_id+":"+phone+(";ext="+extension if extension else ""),
                source_id=source_id, source_url=f"https://www.openstreetmap.org/node/{identifier}",
                source_updated_at="2026-01-01T00:00:00+00:00", source_version=1,
                name=name, phone=phone, e164=phone, original_phone=phone,
                extension=extension, city="", address="", category="amenity:cafe",
                country_tag=None, source_country_fields={}, geographic_scope="synthetic-research-only",
                source_names={"name":name}, status="RESEARCH_ONLY_NOT_APP_ACCEPTED",
                license_id="ODbL-1.0", verification="source-listed-not-phone-confirmed")


class CandidateProjectionContract(unittest.TestCase):
    def test_projection_preserves_original_source_null_country_and_research_scope(self):
        row=source_row();before=copy.deepcopy(row);result=candidate.project_record(row)
        self.assertEqual("PROJECTED_FOR_REVIEW_NOT_ADMITTED",result['decision'])
        self.assertEqual(before,row);self.assertIsNone(row['country_tag'])
        self.assertEqual('synthetic-research-only',row['geographic_scope'])
        self.assertEqual(row['name'],result['values'][1]);self.assertEqual(row['phone'],result['values'][2])

    def test_candidate_projection_does_not_make_original_country_guard_pass(self):
        row=source_row();candidate.project_record(row)
        with self.assertRaisesRegex(ValueError,'Country membership'):
            production_packager().record_values(row)

    def test_reader_phone_display_and_canonical_keep_same_base_identity(self):
        row=source_row();values=candidate.project_record(row)['values'];core=production_packager()
        self.assertEqual(12,len(values));self.assertEqual(row['phone'],values[2]);self.assertEqual(row['phone'],values[3])
        self.assertEqual('0502345678',values[11]);self.assertEqual(values[11],core.normalized_phone(values[2]))
        self.assertEqual(values[11],core.normalized_phone(values[3]))

    def test_uan_proposal_is_withheld_without_rewriting_original_number(self):
        row=source_row(phone='+9721599555535');before=copy.deepcopy(row)
        result=candidate.project_record(row)
        self.assertEqual('WITHHELD_PHONE_FORMAT_REVIEW',result['decision']);self.assertIsNone(result['values'])
        self.assertEqual(before,row)

    def test_source_formatting_controls_are_preserved_and_withheld(self):
        row=source_row(name='\u200eמקור');before=copy.deepcopy(row);result=candidate.project_record(row)
        self.assertEqual('WITHHELD_SOURCE_TEXT',result['decision']);self.assertIsNone(result['values'])
        self.assertEqual(before,row)

    def test_city_or_address_control_is_withheld_even_with_clean_name(self):
        for field in ('city','address'):
            row=source_row();row[field]='Original\u200e';before=copy.deepcopy(row)
            self.assertEqual('WITHHELD_SOURCE_TEXT',candidate.project_record(row)['decision'])
            self.assertEqual(before,row)

    def test_nonempty_extension_is_not_appended_or_silently_collapsed(self):
        row=source_row(extension='123');before=copy.deepcopy(row);result=candidate.project_record(row)
        self.assertEqual('WITHHELD_EXTENSION',result['decision']);self.assertIsNone(result['values'])
        self.assertEqual(before,row);self.assertEqual('+972502345678',row['phone'])

    def test_java_utf16_name_boundary_is_enforced_without_truncation(self):
        allowed=source_row(name='😀'*150);blocked=source_row(name='😀'*151)
        self.assertEqual(300,len(allowed['name'].encode('utf-16-le'))//2)
        self.assertEqual('PROJECTED_FOR_REVIEW_NOT_ADMITTED',candidate.project_record(allowed)['decision'])
        result=candidate.project_record(blocked)
        self.assertEqual('WITHHELD_UTF16_NAME',result['decision']);self.assertIsNone(result['values'])
        self.assertEqual('😀'*151,blocked['name'])

    def test_source_text_normalization_is_not_a_silent_name_change(self):
        row=source_row(name=' Original ');before=copy.deepcopy(row);result=candidate.project_record(row)
        self.assertEqual('WITHHELD_SOURCE_NORMALIZATION',result['decision']);self.assertIsNone(result['values'])
        self.assertEqual(before,row)

    def test_shared_phone_on_distinct_sources_keeps_distinct_stable_ids(self):
        first=candidate.project_record(source_row(1))['values']
        second=candidate.project_record(source_row(2))['values']
        self.assertNotEqual(first[0],second[0]);self.assertEqual(first[11],second[11])
        self.assertGreater(first[0],0);self.assertLess(first[0],1<<63)
        self.assertEqual(first[0],candidate.stable_id('osm:node:1',first[11]))

    def test_same_source_different_base_phones_keeps_separate_identities(self):
        first=candidate.project_record(source_row())['values']
        second=candidate.project_record(source_row(phone='+97235551234'))['values']
        self.assertNotEqual(first[0],second[0]);self.assertNotEqual(first[11],second[11])

    def test_malformed_source_identity_url_license_and_status_fail_closed(self):
        for key,value in [('source_id','osm:node:0'),('source_url','https://example.com/unrelated'),
                          ('license_id','UNKNOWN'),('verification','confirmed-by-phone'),
                          ('status','APP_ACCEPTED')]:
            row=source_row();row[key]=value
            with self.subTest(key=key):
                with self.assertRaises(ValueError):candidate.project_record(row)

    def test_source_date_requires_valid_timezone(self):
        for value in ('2026-01-01T00:00:00','not-a-date',''):
            row=source_row();row['source_updated_at']=value
            with self.assertRaises(ValueError):candidate.project_record(row)

    def test_foreign_or_unsourced_country_is_not_relabelled_for_projection(self):
        for country,fields in [('PS',{'addr:country':'PS'}),('IL',{}),
                               ('IL',{'addr:country':'IL','country':'DE'})]:
            row=source_row();row.update(country_tag=country,source_country_fields=fields)
            before=copy.deepcopy(row)
            with self.assertRaises(ValueError):candidate.project_record(row)
            self.assertEqual(before,row)

    def test_explicit_il_source_tag_remains_original_and_does_not_change_scope(self):
        row=source_row();row.update(country_tag='IL',source_country_fields={'addr:country':'IL'})
        before=copy.deepcopy(row);self.assertEqual('PROJECTED_FOR_REVIEW_NOT_ADMITTED',candidate.project_record(row)['decision'])
        self.assertEqual(before,row)

    def test_candidate_stable_id_matches_existing_core_identity_contract(self):
        row=source_row();row.update(country_tag='IL',source_country_fields={'addr:country':'IL'},geographic_scope='explicit-il')
        expected=production_packager().record_values(row)
        self.assertEqual(expected[0],candidate.project_record(row)['values'][0])

    def test_existing_candidate_output_cannot_be_overwritten(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);out=root/'retained';out.mkdir();marker=out/'original';marker.write_bytes(b'keep')
            with self.assertRaises(ValueError):
                candidate.build(root/'missing.pbf',root/'missing.sqlite',root/'manifest.json',root/'evidence',out)
            self.assertEqual(b'keep',marker.read_bytes())


class CandidateSchemaContract(unittest.TestCase):
    def setUp(self):
        self.database=sqlite3.connect(':memory:');candidate.create_candidate_schema(self.database)

    def tearDown(self):self.database.close()

    def test_reader_schema_has_expected_columns_version_and_real_fts4(self):
        self.assertEqual(1,self.database.execute('PRAGMA user_version').fetchone()[0])
        names=[row[1] for row in self.database.execute('PRAGMA table_info(businesses)')]
        self.assertEqual(['id','name','phone','phone_display','city','category','address','source_url',
                          'source_id','source_license','source_updated_at','normalized_phone'],names)
        sql=self.database.execute("SELECT sql FROM sqlite_master WHERE name='business_search'").fetchone()[0]
        self.assertIn('using fts4',sql.lower())

    def test_app_admitted_cannot_be_set_even_for_review_ledger(self):
        self.database.execute('INSERT INTO candidate_raw_records(record_key,corpus_kind,ordinal,original_line,line_sha) VALUES (?,?,?,?,?)',
                              ('source-key','synthetic',0,b'{"source":"synthetic"}\n','0'*64))
        with self.assertRaises(sqlite3.IntegrityError):
            self.database.execute('INSERT INTO candidate_decisions(record_key,decision,candidate_business_id,app_admitted) VALUES (?,?,?,?)',
                                  ('source-key','WITHHELD_SOURCE_TEXT',None,1))
        self.database.execute('INSERT INTO candidate_decisions(record_key,decision,candidate_business_id,app_admitted) VALUES (?,?,?,?)',
                              ('source-key','WITHHELD_SOURCE_TEXT',None,0))
        self.assertEqual(0,self.database.execute('SELECT sum(app_admitted) FROM candidate_decisions').fetchone()[0])

    def test_ftsid_identity_and_implicit_app_match_keep_source_display(self):
        row=source_row();values=candidate.project_record(row)['values'];core=production_packager()
        self.database.execute('INSERT INTO businesses VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',values)
        self.database.execute('INSERT INTO business_search(docid,name,city,category,phone) VALUES (?,?,?,?,?)',
                              (values[0],core.search_text(values[1]),'',core.search_text(values[5]),values[2]))
        matched=self.database.execute('SELECT b.source_id,b.name FROM businesses b WHERE b.id IN '
            '(SELECT docid FROM business_search WHERE business_search MATCH ?)',('"בית*" "קפה*"',)).fetchall()
        self.assertEqual([(row['source_id'],row['name'])],matched)
        self.assertEqual(values[0],self.database.execute('SELECT docid FROM business_search').fetchone()[0])
        self.assertEqual('ok',self.database.execute('PRAGMA integrity_check').fetchone()[0])

    def test_missing_extra_swapped_fts_content_is_rejected(self):
        values=candidate.project_record(source_row())['values'];core=production_packager()
        self.database.execute('INSERT INTO businesses VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',values)
        with self.assertRaisesRegex(ValueError,'FTS'):
            candidate.verify_candidate_database(self.database,{values[0]:values},1)
        self.database.execute('INSERT INTO business_search(docid,name,city,category,phone) VALUES (?,?,?,?,?)',
                              (values[0],'wrong source','',values[5],values[2]))
        with self.assertRaisesRegex(ValueError,'FTS'):
            candidate.verify_candidate_database(self.database,{values[0]:values},1)
        self.database.execute('DELETE FROM business_search')
        self.database.execute('INSERT INTO business_search(docid,name,city,category,phone) VALUES (?,?,?,?,?)',
                              (values[0],core.search_text(values[1]),'',core.search_text(values[5]),values[2]))
        self.database.execute('INSERT INTO business_search(docid,name,city,category,phone) VALUES (?,?,?,?,?)',
                              (values[0]+1,'phantom','','',''))
        with self.assertRaisesRegex(ValueError,'FTS'):
            candidate.verify_candidate_database(self.database,{values[0]:values},1)

    def test_duplicate_stable_id_cannot_replace_retained_business(self):
        values=candidate.project_record(source_row())['values']
        self.database.execute('INSERT INTO businesses VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',values)
        with self.assertRaises(sqlite3.IntegrityError):
            self.database.execute('INSERT INTO businesses VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',values)
        self.assertEqual(1,self.database.execute('SELECT count(*) FROM businesses').fetchone()[0])


class CandidateInputContract(unittest.TestCase):
    def test_corrupted_reviewed_input_hash_fails_before_output_creation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);files=[]
            for name in ('research.sqlite','manifest.json','audit.json','source.pbf'):
                path=root/name;path.write_bytes(b'corrupted');files.append(path)
            with self.assertRaisesRegex(ValueError,'checksum'):
                candidate.validate_known_inputs(*files)
            self.assertEqual(4,len(list(root.iterdir())))

    def test_midbuild_source_mutation_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            path=Path(temporary)/'original';path.write_bytes(b'original')
            snapshot={path:candidate.digest(path)};path.write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError,'changed'):
                candidate.require_unchanged(snapshot)

    def test_zero_hash_identity_is_rejected(self):
        with mock.patch.object(candidate.hashlib,'sha256') as hashed:
            hashed.return_value.digest.return_value=b'\x00'*32
            with self.assertRaisesRegex(ValueError,'stable'):
                candidate.stable_id('osm:node:1','0502345678')

    def test_reader_manifest_rejects_boolean_string_and_unbounded_counters(self):
        valid=dict(format='DA_CALLER_BUSINESSES',schema_version=1,dataset_version='candidate-v1',
                   database_sha256='a'*64,record_count=2,source_name='Synthetic contract only',
                   source_url='https://example.com/source',license='ODbL-1.0',
                   license_url='https://opendatacommons.org/licenses/odbl/1-0/',updated_at='2026-01-01')
        candidate.validate_reader_manifest(valid)
        for key,value in [('record_count',True),('record_count','2'),('record_count',-1),
                          ('record_count',2000001),('schema_version',True),('schema_version','1'),
                          ('source_url','http://example.com'),('license_url','https://user@example.com'),
                          ('database_sha256','A'*64),('database_sha256',10**63),
                          ('dataset_version','../asset'),('dataset_version',1)]:
            item=copy.deepcopy(valid);item[key]=value
            with self.subTest(key=key,value=value):
                with self.assertRaises(ValueError):candidate.validate_reader_manifest(item)

    def test_truthful_transfer_validates_zip_pbf_without_country_scoped_manifest(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);pbf=root/'source.pbf';pbf.write_bytes(b'synthetic bytes, not business data')
            archive=root/'source.zip'
            with zipfile.ZipFile(archive,'w') as zipped:zipped.write(pbf,pbf.name)
            receipt=dict(source='owner-uploaded GitHub Release asset',asset_id=712722687,
                url='https://github.com/hadar1233hr-afk/D.A.-Caller/releases/download/v0.11.0-beta.9-debug/israel-and-palestine-261103.osm.zip',
                github_digest_matches=True,zip_crc='PASS',publisher_provenance_independently_verified=False,
                bytes=archive.stat().st_size,sha256=candidate.digest(archive),pbf_bytes=pbf.stat().st_size,pbf_sha256=candidate.digest(pbf))
            with mock.patch.object(candidate,'PBF_SHA',candidate.digest(pbf)),mock.patch.object(candidate,'ARCHIVE_SHA',candidate.digest(archive)):
                result=candidate.validate_owner_transfer(receipt,pbf,archive)
                self.assertIs(result['production_admission'],False);self.assertNotIn('geographic_scope',result)
                for key,value in [('http_status',200),('publisher_provenance_independently_verified',True),
                                  ('asset_id',True),('bytes',True),('github_digest_matches',1),
                                  ('pbf_sha256','0'*64)]:
                    invalid=copy.deepcopy(receipt);invalid[key]=value
                    with self.subTest(key=key):
                        with self.assertRaises(ValueError):candidate.validate_owner_transfer(invalid,pbf,archive)

    def test_forged_zip_with_other_source_payload_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);pbf=root/'source.pbf';pbf.write_bytes(b'original source')
            archive=root/'source.zip'
            with zipfile.ZipFile(archive,'w') as zipped:zipped.writestr(pbf.name,b'forged source!!')
            receipt=dict(source='owner-uploaded GitHub Release asset',asset_id=712722687,
                url='https://github.com/hadar1233hr-afk/D.A.-Caller/releases/download/v0.11.0-beta.9-debug/israel-and-palestine-261103.osm.zip',
                github_digest_matches=True,zip_crc='PASS',publisher_provenance_independently_verified=False,
                bytes=archive.stat().st_size,sha256=candidate.digest(archive),pbf_bytes=pbf.stat().st_size,pbf_sha256=candidate.digest(pbf))
            with mock.patch.object(candidate,'PBF_SHA',candidate.digest(pbf)),mock.patch.object(candidate,'ARCHIVE_SHA',candidate.digest(archive)):
                with self.assertRaises(ValueError):candidate.validate_owner_transfer(receipt,pbf,archive)


if __name__=='__main__':unittest.main(verbosity=2)
