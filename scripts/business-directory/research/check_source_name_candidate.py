"""Source-name selection contracts; fixtures are retained source bytes, not app data."""
from contextlib import closing
import copy
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3
import tempfile
import unittest
from unittest import mock

import derive_source_name_candidate as candidate

PARENT = Path('/workspace/work/business-data/business-candidate-owner-261103-v2')
REPORT = candidate.REPO / 'docs/evidence/business-acquisition/owner-261103/text-readiness-report.json'


class SourceNameContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.proofs = {p['original_record']['source_id']: p for p in json.loads(REPORT.read_text())['rows']}
        cls.lines = {}
        with closing(candidate.read_only(PARENT / 'candidate-businesses.sqlite')) as db:
            for row in db.execute('SELECT original_line FROM candidate_raw_records'):
                original = json.loads(row[0])
                if original['source_id'] in cls.proofs:
                    cls.lines[original['source_id']] = row[0]

    def selected(self, source='osm:node:5801536067', proof=None, decision='WITHHELD_SOURCE_TEXT', tag='name'):
        return candidate.select_original_name(self.lines[source], self.proofs[source] if proof is None else proof,
                                              decision, tag)

    def test_exact_default_source_names_selected_without_repair(self):
        for source in candidate.SELECTED:
            values, event = self.selected(source)
            proof = self.proofs[source]
            self.assertEqual(proof['independent_source_node']['source_fields']['name'], values[1])
            self.assertEqual(proof['original_record']['source_names']['name'], values[1])
            self.assertNotEqual(proof['original_record']['name'], values[1])
            self.assertEqual('name', event['selected_source_tag'])
            self.assertEqual('WITHHELD_SOURCE_TEXT', event['old_decision'])
            self.assertIs(event['app_admitted'], False)

    def test_original_raw_and_proof_remain_unchanged(self):
        for source in candidate.SELECTED:
            before = copy.deepcopy(self.proofs[source]); line = self.lines[source]
            self.selected(source)
            self.assertEqual(before, self.proofs[source]); self.assertEqual(line, self.lines[source])
            self.assertIn('name:he', before['original_record']['source_names'])

    def test_original_country_scope_phone_and_source_values_not_relabelled(self):
        for source in candidate.SELECTED:
            values, event = self.selected(source); original = self.proofs[source]['original_record']
            self.assertIsNone(event['original_country']); self.assertEqual({}, event['original_country_fields'])
            self.assertEqual(original['geographic_scope'], event['original_scope'])
            self.assertEqual(original['phone'], values[2]); self.assertEqual(original['phone'], values[3])
            self.assertEqual(original['source_url'], values[7]); self.assertEqual(original['source_id'], values[8])
            self.assertEqual(original['source_updated_at'], values[10])
            self.assertEqual(candidate.production_packager().normalized_phone(values[2]), values[11])

    def test_other_language_metadata_or_caller_field_is_not_selected(self):
        for tag in ('name:en', 'name:ar', 'name:he', 'name:pronunciation', 'replacement', None):
            with self.subTest(tag=tag):
                with self.assertRaises(ValueError): self.selected(tag=tag)

    def test_wrong_historical_disposition_is_not_reinterpreted(self):
        for disposition in ('PROJECTED_FOR_REVIEW_NOT_ADMITTED', 'WITHHELD_PHONE_FORMAT_REVIEW', '', None):
            with self.assertRaises(ValueError): self.selected(decision=disposition)

    def test_unreviewed_city_address_row_stays_withheld(self):
        with self.assertRaises(ValueError): self.selected('osm:node:5066707334')

    def test_original_line_mutation_or_serialization_change_is_rejected(self):
        source = 'osm:node:5801536067'; proof = self.proofs[source]
        for line in (self.lines[source] + b' ', json.dumps(proof['original_record']).encode(), b'{}'):
            with self.assertRaises(ValueError):
                candidate.select_original_name(line, proof, 'WITHHELD_SOURCE_TEXT')

    def test_other_source_proof_cannot_supply_a_display_name(self):
        with self.assertRaises(ValueError): self.selected(proof=self.proofs['osm:node:12543641901'])

    def test_source_proof_identity_url_timestamp_mismatches_fail(self):
        for key, value in (('source_id', 'osm:node:1'), ('source_url', 'https://example.com/other'),
                           ('source_updated_at', '2026-01-01T00:00:00+00:00')):
            proof = copy.deepcopy(self.proofs['osm:node:5801536067'])
            proof['independent_source_node'][key] = value
            with self.subTest(key=key):
                with self.assertRaises(ValueError): self.selected(proof=proof)

    def test_name_only_withholding_and_false_admission_required(self):
        for key, value in (('failed_fields', ['name', 'city']), ('failed_fields', []), ('app_accepted', 0), ('app_accepted', True)):
            proof = copy.deepcopy(self.proofs['osm:node:5801536067']); proof[key] = value
            with self.assertRaises(ValueError): self.selected(proof=proof)

    def test_different_raw_tag_value_or_format_control_not_repaired(self):
        for key, value in (('name', 'Invented name'), ('name', '\u200eUnsafe'), ('name:he', 'changed preferred')):
            proof = copy.deepcopy(self.proofs['osm:node:5801536067'])
            proof['independent_source_node']['source_fields'][key] = value
            with self.assertRaises(ValueError): self.selected(proof=proof)

    def test_original_phone_identity_and_stable_ids_are_exact_and_distinct(self):
        ids = []
        for source in candidate.SELECTED:
            values, event = self.selected(source)
            expected = int.from_bytes(hashlib.sha256((source+':'+values[11]).encode()).digest()[:8], 'big') & ((1<<63)-1)
            self.assertEqual(expected, values[0]); self.assertGreater(values[0], 0)
            self.assertEqual(expected, event['new_candidate_business_id']); ids.append(expected)
        self.assertEqual(2, len(set(ids)))

    def test_no_previous_projector_or_country_admission_called(self):
        with mock.patch.object(candidate.reference, 'project_record', side_effect=AssertionError('old projector called')):
            with mock.patch.object(candidate.production_packager(), 'record_values', side_effect=AssertionError('old country guard called')):
                for source in candidate.SELECTED: self.selected(source)

    def test_original_rows_still_fail_unchanged_production_contract(self):
        for source in candidate.SELECTED:
            self.selected(source)
            with self.assertRaises(ValueError):
                candidate.production_packager().record_values(self.proofs[source]['original_record'])

    def test_exact_text_rejects_normalization_trim_control_and_surrogate(self):
        for value in (' source ', 'e\u0301', '\u200eText', 'Text\x00', '\ud800', 123, None):
            with self.subTest(value=ascii(value)):
                with self.assertRaises(ValueError): candidate.exact_text(value, 300)
        self.assertEqual('دار كنا الضيافة', candidate.exact_text('دار كنا الضيافة', 300))

    def test_native_utf16_limit_without_truncation(self):
        self.assertEqual('😀'*150, candidate.exact_text('😀'*150, 300))
        with self.assertRaises(ValueError): candidate.exact_text('😀'*151, 300)

    def test_existing_output_preserved_before_missing_inputs_are_read(self):
        with tempfile.TemporaryDirectory() as temp:
            output=Path(temp)/'retained'; output.mkdir(); marker=output/'marker'; marker.write_bytes(b'keep')
            with self.assertRaises(ValueError): candidate.build(Path(temp)/'missing', Path(temp)/'missing.json', output)
            self.assertEqual(b'keep', marker.read_bytes())

    def test_version_type_path_and_bounds_rejected_before_reading_inputs(self):
        with tempfile.TemporaryDirectory() as temp:
            for version in (True, 1, '', '../asset', 'x'*81):
                with self.assertRaises(ValueError): candidate.build(Path(temp)/'missing', Path(temp)/'missing.json', Path(temp)/'out', version)

    def test_corrupt_parent_and_source_report_fail_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); parent=root/'parent';parent.mkdir()
            for name in candidate.PARENT_SHA:
                (parent/name).write_bytes(b'corrupt')
            with self.assertRaisesRegex(ValueError, 'parent checksum'): candidate.validate_inputs(parent, REPORT)
            bad=root/'report.json';bad.write_text('{}')
            with self.assertRaisesRegex(ValueError, 'report checksum'): candidate.validate_inputs(PARENT, bad)

    def test_midvalidation_parent_drift_cannot_replace_fixed_hash_anchor(self):
        with tempfile.TemporaryDirectory() as temp:
            parent=Path(temp)/'parent';parent.mkdir()
            for name in candidate.PARENT_SHA: shutil.copyfile(PARENT/name,parent/name)
            original_digest=candidate.digest;changed=False
            def drift(path):
                nonlocal changed
                if Path(path)==parent/'candidate-reader-manifest.json' and not changed:
                    changed=True;Path(path).write_bytes(b'changed after pinned-buffer validation')
                return original_digest(path)
            with mock.patch.object(candidate,'digest',side_effect=drift):
                with self.assertRaisesRegex(ValueError,'changed'): candidate.validate_inputs(parent,REPORT)
            self.assertTrue(changed)

    def test_executing_code_snapshot_precedes_input_validation(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);code=root/'code.py';code.write_bytes(b'original executing-code snapshot')
            original_validation=candidate.validate_inputs
            def mutate(parent,report):
                result=original_validation(parent,report);code.write_bytes(b'code changed during input validation');return result
            with mock.patch.object(candidate,'__file__',str(code)),mock.patch.object(candidate,'validate_inputs',side_effect=mutate):
                with self.assertRaisesRegex(ValueError,'changed'): candidate.build(PARENT,REPORT,root/'candidate')
            self.assertFalse((root/'candidate').exists())

    def test_snapshot_source_or_code_mutation_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)/'bound';p.write_bytes(b'original');snapshots={p:candidate.digest(p)}
            candidate.require_unchanged(snapshots);p.write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError, 'changed'): candidate.require_unchanged(snapshots)

    def test_selection_schema_cannot_claim_admitted_app_row(self):
        with sqlite3.connect(':memory:') as db:
            candidate.reference.create_candidate_schema(db); candidate.create_selection_schema(db)
            values, event = self.selected();source='osm:node:5801536067';line=self.lines[source]
            db.execute('INSERT INTO candidate_raw_records VALUES (?,?,?,?,?)', (event['record_key'],'node',0,line,candidate.SELECTED[source]))
            db.execute('INSERT INTO businesses VALUES (?,?,?,?,?,?,?,?,?,?,?,?)', values)
            args=(event['record_key'],values[0],'WITHHELD_SOURCE_TEXT','name',event['original_preferred_value'],values[1],source,values[7],values[10],candidate.SELECTED[source],candidate.REPORT_SHA)
            with self.assertRaises(sqlite3.IntegrityError):
                db.execute('INSERT INTO candidate_source_name_selections VALUES (?,?,?,?,?,?,?,?,?,?,?,1)',args)
            db.execute('INSERT INTO candidate_source_name_selections VALUES (?,?,?,?,?,?,?,?,?,?,?,0)',args)
            self.assertEqual(0,db.execute('SELECT sum(app_admitted) FROM candidate_source_name_selections').fetchone()[0])

    def test_midbuild_source_change_retains_no_partial_candidate(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);source=root/'source-report.json';shutil.copyfile(REPORT,source);out=root/'candidate'
            original_write=candidate.write_json
            def mutate(path,value):
                original_write(path,value)
                if path.name=='candidate-reader-manifest.json': source.write_bytes(b'changed after validation')
            with mock.patch.object(candidate,'write_json',side_effect=mutate):
                with self.assertRaisesRegex(ValueError,'changed'): candidate.build(PARENT,source,out)
            self.assertFalse(out.exists())


if __name__ == '__main__': unittest.main(verbosity=2)
