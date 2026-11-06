"""Explicit extra-family contracts; synthetic metadata never actual business proof."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import stage_expanded_research_sqlite as expanded
from check_research_sqlite import sample, line


class ExpandedFamilyContract(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.evidence=Path(self.temp.name)
        self.corpus=self.evidence/'extra.ndjson';self.record=sample();self.record['geographic_scope']='synthetic-extra'
        self.corpus.write_bytes(line(self.record));self.source='d'*64;self.size=123
        self.report=dict(source_pbf_sha256=self.source,source_pbf_bytes=self.size,
            ndjson_sha256=hashlib.sha256(self.corpus.read_bytes()).hexdigest(),raw_phone_rows=1,
            source_nodes=1,scope='synthetic-extra',
            policy_module_sha256=expanded.base.digest(expanded.HERE/'extended_business_category.py'),
            research_script_sha256=expanded.base.digest(expanded.HERE/'research_extra_category_nodes.py'),
            unchanged_core_extractor_sha256=expanded.base.digest(expanded.HERE.parent/'extract_osm_businesses.py'))
        self.audit=dict(status='PASS_RESEARCH_EXTRA_CATEGORY_NODE_SOURCE_AND_POINT_AUDIT_ONLY',
            source_pbf_sha256=self.source,source_pbf_bytes=self.size,
            research_ndjson_sha256=self.report['ndjson_sha256'],source_node_rows=1,
            unique_original_source_nodes_found=1,reviewed_policy_sha256=self.report['policy_module_sha256'],
            reviewed_producer_sha256=self.report['research_script_sha256'],
            reference_extractor_sha256=self.report['unchanged_core_extractor_sha256'],
            audit_script_sha256=expanded.base.digest(expanded.HERE/'audit_extra_category_nodes.py'),
            checks_passed={key:1 for key in expanded.NODE_CHECKS},
            app_records_added=0,source_mutations=0,automatic_merges=0)
        self.save()

    def tearDown(self):self.temp.cleanup()

    def save(self):
        report=self.evidence/'extra-category-node-report.json';report.write_text(json.dumps(self.report))
        self.audit['research_report_sha256']=expanded.base.digest(report)
        (self.evidence/'extra-category-node-independent-audit.json').write_text(json.dumps(self.audit))

    def validate(self):return expanded.validate_extra('extra_node',self.corpus,self.evidence,self.source,self.size)

    def test_distinct_family_preserves_exact_scope_null_country_and_original_line(self):
        rows,binding=self.validate();self.assertEqual('extra_node',binding['kind'])
        self.assertEqual(line(self.record),rows[0][1]);self.assertIsNone(rows[0][0]['country_tag'])
        self.assertEqual('synthetic-extra',rows[0][0]['geographic_scope'])

    def test_base_audit_status_cannot_be_renamed_to_approve_extra_family(self):
        self.audit['status']='INDEPENDENT_ACTUAL_SOURCE_NODE_AUDIT_PASS_RESEARCH_ONLY';self.save()
        with self.assertRaises(ValueError):self.validate()

    def test_missing_point_proof_rejected_despite_correct_counts_and_policy(self):
        del self.audit['checks_passed']['independent_source_point_inclusion_margin_exclusion_rows'];self.save()
        with self.assertRaises(ValueError):self.validate()

    def test_current_policy_and_producer_and_core_dependency_must_match(self):
        for field in ('reviewed_policy_sha256','reviewed_producer_sha256','reference_extractor_sha256'):
            original=copy.deepcopy(self.audit);self.audit[field]='0'*64;self.save()
            with self.assertRaises(ValueError):self.validate()
            self.audit=original

    def test_count_booleans_are_not_valid_source_counts_or_zero_flags(self):
        for field,value in [('source_node_rows',True),('app_records_added',False),('automatic_merges',False)]:
            original=copy.deepcopy(self.audit);self.audit[field]=value;self.save()
            with self.assertRaises(ValueError):self.validate()
            self.audit=original

    def test_contradictory_app_counter_aliases_cannot_be_hidden_by_first_zero(self):
        self.audit['app_rows_added']=1;self.save()
        with self.assertRaises(ValueError):self.validate()
        self.audit['app_rows_added']=0;self.save()
        self.assertEqual(1,len(self.validate()[0]))

    def test_changed_corpus_or_wrong_source_cannot_reuse_audit(self):
        original=self.corpus.read_bytes();self.corpus.write_bytes(line(sample(name='Changed')))
        with self.assertRaises(ValueError):self.validate()
        self.corpus.write_bytes(original)
        with self.assertRaises(ValueError):expanded.validate_extra('extra_node',self.corpus,self.evidence,'e'*64,self.size)

    def test_changed_scope_cannot_be_relabelled_into_original_source_family(self):
        self.record['geographic_scope']='explicit-il';self.corpus.write_bytes(line(self.record))
        self.report['ndjson_sha256']=hashlib.sha256(self.corpus.read_bytes()).hexdigest()
        self.audit['research_ndjson_sha256']=self.report['ndjson_sha256'];self.save()
        with self.assertRaises(ValueError):self.validate()

    def test_extra_filenames_required_instead_of_base_cohort_artifacts(self):
        (self.evidence/'extra-category-node-independent-audit.json').rename(self.evidence/'node-geography-source-audit.json')
        with self.assertRaises(FileNotFoundError):self.validate()

    def test_same_source_across_families_rejected_without_merging(self):
        rows=[(self.record,line(self.record))]
        with self.assertRaises(ValueError):expanded.unique_source_families({'node':rows,'extra_node':rows})

    def test_same_source_multiple_phones_in_own_family_is_not_discarded(self):
        other=copy.deepcopy(self.record);other['phone']=other['e164']='+97235551234'
        rows=[(self.record,line(self.record)),(other,line(other))]
        before=copy.deepcopy(rows);expanded.unique_source_families({'extra_node':rows});self.assertEqual(before,rows)

    def test_existing_output_never_overwritten(self):
        out=self.evidence/'retained';out.mkdir();mark=out/'original';mark.write_bytes(b'original')
        with self.assertRaises(ValueError):expanded.build(self.evidence/'missing',{},self.evidence,out)
        self.assertEqual(b'original',mark.read_bytes())

    def way_fixture(self):
        row=copy.deepcopy(self.record);row.update(source_id='osm:way:1',source_url='https://www.openstreetmap.org/way/1')
        self.corpus.write_bytes(line(row))
        policy=expanded.base.digest(expanded.HERE/'extended_business_category.py')
        report=dict(source_pbf_sha256=self.source,source_pbf_bytes=self.size,
            ndjson_sha256=hashlib.sha256(self.corpus.read_bytes()).hexdigest(),raw_phone_rows=1,
            source_ways=1,scope='synthetic-extra',extractor_sha256=policy,
            research_script_sha256=expanded.base.digest(expanded.HERE/'research_way_geography.py'))
        path=self.evidence/'extra-category-way-report.json';path.write_text(json.dumps(report))
        audit=dict(status='PASS_RESEARCH_SOURCE_AND_COMPLETE_POLYGON_AUDIT_ONLY',
            source_pbf_sha256=self.source,source_pbf_bytes=self.size,research_ndjson_sha256=report['ndjson_sha256'],
            research_report_sha256=expanded.base.digest(path),source_way_rows=1,
            unique_original_source_ways_found=1,reference_extractor_sha256=policy,
            reviewed_way_helper_sha256=report['research_script_sha256'],
            audit_script_sha256=expanded.base.digest(expanded.HERE/'audit_way_geography.py'),
            checks_passed={key:1 for key in expanded.WAY_CHECKS},app_rows_added=0,source_mutations=0,automatic_merges=0)
        return audit

    def test_extra_way_requires_whole_polygon_proof_not_only_counts(self):
        audit=self.way_fixture();path=self.evidence/'extra-category-way-independent-audit.json'
        path.write_text(json.dumps(audit))
        rows,binding=expanded.validate_extra('extra_way',self.corpus,self.evidence,self.source,self.size)
        self.assertEqual(1,len(rows));self.assertEqual('extra_way',binding['kind'])
        del audit['checks_passed']['independent_complete_polygon_valid_area_containment_margin_exclusion_rows']
        path.write_text(json.dumps(audit))
        with self.assertRaises(ValueError):expanded.validate_extra('extra_way',self.corpus,self.evidence,self.source,self.size)

    def test_extra_way_cannot_reuse_original_core_policy_as_extension_proof(self):
        audit=self.way_fixture();audit['reference_extractor_sha256']=expanded.base.digest(expanded.HERE.parent/'extract_osm_businesses.py')
        (self.evidence/'extra-category-way-independent-audit.json').write_text(json.dumps(audit))
        with self.assertRaises(ValueError):expanded.validate_extra('extra_way',self.corpus,self.evidence,self.source,self.size)


if __name__=='__main__':unittest.main(verbosity=2)
