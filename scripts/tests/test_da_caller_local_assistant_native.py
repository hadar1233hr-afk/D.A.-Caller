"""Synthetic evidence-schema contracts only; these never establish native PASS."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

ROOT=Path(__file__).resolve().parents[2]
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path);value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value
gate=module('assistant_native_gate',ROOT/'scripts/verify-local-assistant-native.py')
modern=module('assistant_native_legacy_gate',ROOT/'scripts/verify-modern-features-native.py')
# Reuse the existing synthetic PNG encoder; no native screenshots are fabricated.
fixtures=module('assistant_native_png_fixtures',ROOT/'scripts/tests/test_da_caller_modern_native.py')


def raw_runtime():
    return '\n'.join([
        'INSTRUMENTATION_STATUS: numtests=1','INSTRUMENTATION_STATUS: class='+gate.OWNER,
        'INSTRUMENTATION_STATUS: test='+gate.RUNTIME_METHOD,'INSTRUMENTATION_STATUS: current=1','INSTRUMENTATION_STATUS_CODE: 1',
        'INSTRUMENTATION_STATUS: numtests=1','INSTRUMENTATION_STATUS: class='+gate.OWNER,
        'INSTRUMENTATION_STATUS: test='+gate.RUNTIME_METHOD,'INSTRUMENTATION_STATUS: current=1','INSTRUMENTATION_STATUS_CODE: 0',
        'INSTRUMENTATION_RESULT: stream=','Time: 2.0','OK (1 test)','INSTRUMENTATION_CODE: -1',''])


def fixture(method,phase,images):
    light=phase=='runtime-denied' or method=='testAssistantProductionSearchDisambiguationAndCancel'
    amoled=not light and method!='testAssistantLocalHistoryBusinessAndUnsupportedCommands'
    unsigned=0xff000000 if amoled else 0xfff6f0e8 if light else 0xff241a17
    value=dict(method=method,phase=phase,api=33,density_dpi=240,font_scale=1.3,package='com.dacaller.app',version_name='0.15.0',version_code=24,
        emulator_only=True,no_network_permission=True,preferences_restored=True,navigation_preferences_restored=True,appops_restored=True,call_history_cache_restored=True,cleanup_completed=True,
        physical_carrier_audio_verified=False,matching_key_upgrade_verified=False,
        fixture_scope='Isolated disposable-emulator provider rows; no owner data',
        text_input_method='Production EditText.setText on UI thread; real native pointer for actions',
        telecom_shell_identity_used=False,telecom_state_observation='Target READ_PHONE_STATE permission',
        fixture_contacts_deleted=0,fixture_call_rows_deleted=0,confirmed_call_rows_deleted=0,
        pointers=[dict(bounds='[24,80][456,152]',x=240,y=116,native_pointer=True,full_native_ax_geometry=True) for _ in range(3)],
        palette=dict(rtl=not light,dark=not light,amoled=amoled,background_argb=unsigned-(1<<32),input_state_saving_disabled=True,
            ime_personalized_learning_request_disabled=True,external_keyboard_retention='NOT_TESTED'))
    names={gate.RUNTIME_FRAME} if phase=='runtime-denied' else gate.METHODS[method]
    value['captures']={name:dict(sha256=hashlib.sha256(images[name].read_bytes()).hexdigest(),width=480,height=854,method='UiAutomation.takeScreenshot') for name in names}
    if phase=='runtime-denied':value.update(read_contacts_granted=False,read_call_log_granted=False,no_sensitive_results=True,no_unconfirmed_call=True,live_same_process_runtime_revoke='NOT_TESTED_PROCESS_MAY_BE_KILLED')
    elif method=='testAssistantProductionSearchDisambiguationAndCancel':value.update(fixture_contacts=2,actual_contact_rows=2,fixture_contacts_deleted=2,no_call_before_confirmation=True,cancel_did_not_dial=True,edit_invalidated_results=True)
    elif method=='testAssistantLocalHistoryBusinessAndUnsupportedCommands':value.update(fixture_call_rows=2,fixture_call_rows_deleted=2,missed_filter_exact=True,callback_latest_incoming_or_missed=True,unsupported_honest=True,unicode_numeric_dial_refused=True,rtl=True,dark=True,no_unconfirmed_call=True,business_license='ODbL-1.0',business_source_url='https://example.invalid/source',business_source_date='2026-11-03')
    elif method==gate.RUNTIME_METHOD:value.update(runtime_read_contacts_flag_remained_granted=True,appops_denial_checked=True,cached_selection_refused=True,pending_confirmation_refused=True,call_log_effective_denial_refused=True,no_unconfirmed_call=True,rtl=True,amoled=True,fixture_contacts_deleted=1,live_same_process_runtime_revoke='NOT_TESTED_PROCESS_MAY_BE_KILLED')
    else:value.update(no_call_before_confirmation=True,same_call_ended_by_native_button=True,exactly_one_confirmed_call=True,confirmed_call_rows_deleted=1,rtl=True,amoled=True,number='0500099951',telecom_handle='0500099951',token='synthetic-schema-only-token',actual_outgoing_rows=[dict(id=42,number='0500099951',type=2)])
    return value


class AssistantNativeContracts(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
        data=fixtures.image((246,240,232));self.images={}
        for name in gate.REQUIRED:
            path=self.root/name;path.write_bytes(data);self.images[name]=path
    def test_complete_synthetic_profiles_are_accepted_without_claiming_execution(self):
        for method in gate.METHODS:
            with self.subTest(method=method):gate.proof(fixture(method,'main',self.images),method,'main',self.images,modern)
        gate.proof(fixture(gate.RUNTIME_METHOD,'runtime-denied',self.images),gate.RUNTIME_METHOD,'runtime-denied',self.images,modern)
    def test_real_runtime_statuses_require_one_exact_started_passed_case(self):
        self.assertEqual(1,gate.runtime_log(raw_runtime())['tests'])
        changes=[raw_runtime().replace('STATUS_CODE: 0','STATUS_CODE: -2'),raw_runtime().replace('OK (1 test)','FAILURES!!!'),
            raw_runtime().replace(gate.RUNTIME_METHOD,'testOtherCase'),raw_runtime().replace('numtests=1','numtests=2'),
            raw_runtime().replace('current=1','current=2'),raw_runtime().replace('INSTRUMENTATION_CODE: -1','INSTRUMENTATION_CODE: 0'),
            raw_runtime().replace('INSTRUMENTATION_STATUS_CODE: 1',''),raw_runtime()+'INSTRUMENTATION_STATUS_CODE: 0\n',
            raw_runtime().replace('INSTRUMENTATION_STATUS: class='+gate.OWNER,'INSTRUMENTATION_STATUS: class=wrong\nINSTRUMENTATION_STATUS: class='+gate.OWNER,1),
            raw_runtime()+'INSTRUMENTATION_CODE: 0\n',raw_runtime()+'INSTRUMENTATION_CODE: -1\n',
            raw_runtime().replace('OK (1 test)','OK (1 test)\nOK (1 test)'),raw_runtime()+'unexpected trailing text\n',
            raw_runtime().replace('OK (1 test)','OK (1 test)\nOK (2 tests)'),
            raw_runtime().replace('INSTRUMENTATION_CODE: -1','INSTRUMENTATION_CODE: unknown\nINSTRUMENTATION_CODE: -1'),
            'INSTRUMENTATION_FAILED: Process crashed\n'+raw_runtime(),'OK (1 test)\nINSTRUMENTATION_CODE: -1\n','']
        for raw in changes:
            with self.subTest(raw=raw[:80]),self.assertRaises(AssertionError):gate.runtime_log(raw)
    def test_metadata_version_identity_and_unperformed_claims_cannot_be_relaxed(self):
        method=next(iter(gate.METHODS));base=fixture(method,'main',self.images)
        changes={'method':'testFake','phase':'runtime-denied','api':32,'density_dpi':True,'font_scale':1.0,'package':'com.other.app',
            'version_name':'0.14.0','version_code':23,'emulator_only':False,'no_network_permission':False,'preferences_restored':False,
            'cleanup_completed':False,'physical_carrier_audio_verified':True,'matching_key_upgrade_verified':True,
            'fixture_scope':'User contacts','text_input_method':'Fake screenshots','telecom_shell_identity_used':1,
            'telecom_state_observation':'Broader shell provider identity','captures':{},'pointers':[]}
        for key,bad in changes.items():
            value=copy.deepcopy(base);value[key]=bad
            with self.subTest(key=key),self.assertRaises(AssertionError):gate.proof(value,method,'main',self.images,modern)
    def test_capture_bytes_geometry_focus_input_privacy_and_theme_are_bound(self):
        method=next(iter(gate.METHODS));base=fixture(method,'main',self.images)
        for key,bad in [('bounds','[0,0][71,71]'),('bounds','[0,760][100,840]'),('x',0),('y',False),('native_pointer',False),('full_native_ax_geometry',False)]:
            value=copy.deepcopy(base);value['pointers'][0][key]=bad
            with self.subTest(pointer=key),self.assertRaises(AssertionError):gate.proof(value,method,'main',self.images,modern)
        for key,bad in [('rtl',True),('dark',True),('amoled',True),('background_argb',-1),('input_state_saving_disabled',False),('ime_personalized_learning_request_disabled',False),('external_keyboard_retention','VERIFIED')]:
            value=copy.deepcopy(base);value['palette'][key]=bad
            with self.subTest(palette=key),self.assertRaises(AssertionError):gate.proof(value,method,'main',self.images,modern)
        value=copy.deepcopy(base);value['captures'][next(iter(value['captures']))]['sha256']='0'*64
        with self.assertRaises(AssertionError):gate.proof(value,method,'main',self.images,modern)
    def test_confirmation_provider_scope_and_permission_phases_are_not_interchangeable(self):
        cases=[(method,'main') for method in gate.METHODS]+[(gate.RUNTIME_METHOD,'runtime-denied')]
        for method,phase in cases:
            original=fixture(method,phase,self.images)
            case_flags=[key for key,value in original.items() if value is True and key not in {'emulator_only','no_network_permission','preferences_restored','cleanup_completed'}]
            for key in case_flags:
                value=copy.deepcopy(original);value[key]=False
                with self.subTest(method=method,phase=phase,key=key),self.assertRaises(AssertionError):gate.proof(value,method,phase,self.images,modern)
        value=fixture(gate.RUNTIME_METHOD,'runtime-denied',self.images);value['read_contacts_granted']=True
        with self.assertRaises(AssertionError):gate.proof(value,gate.RUNTIME_METHOD,'runtime-denied',self.images,modern)
        value=fixture('testAssistantExplicitConfirmedCallUsesActualTelecom','main',self.images);value['actual_outgoing_rows'][0]['number']='0500099952'
        with self.assertRaises(AssertionError):gate.proof(value,'testAssistantExplicitConfirmedCallUsesActualTelecom','main',self.images,modern)
    def test_duplicate_json_keys_and_duplicate_native_files_refuse(self):
        p=self.root/'proof.json';p.write_text('{"phase":"main","phase":"runtime-denied"}')
        with self.assertRaises(AssertionError):gate.load(p)
        p.write_text('{"sample":NaN}')
        with self.assertRaises(AssertionError):gate.load(p)
        (self.root/'nested').mkdir();(self.root/'nested'/'proof.json').write_text('{}')
        with self.assertRaises(AssertionError):gate.one(self.root,'proof.json')

    def test_runtime_flag_and_appops_observations_require_actual_unambiguous_values(self):
        valid='\n'.join(permission+': granted=true, flags=[]' for permission in gate.READ_PERMISSIONS)
        self.assertEqual(dict.fromkeys(gate.READ_PERMISSIONS,True),gate.permission_flags(valid))
        for raw in ('',valid.replace('granted=true','granted=unknown'),valid+'\nandroid.permission.READ_CONTACTS: granted=false'):
            with self.subTest(raw=raw),self.assertRaises(AssertionError):gate.permission_flags(raw)
        for operation in gate.READ_PERMISSIONS.values():
            self.assertEqual('allow',gate.appop_mode(operation+': allow; time=3s',operation))
            self.assertEqual('default',gate.appop_mode('No operations.',operation))
            for raw in ('Unknown package',operation+': unknown',operation+': allow\n'+operation+': ignore'):
                with self.subTest(operation=operation,raw=raw),self.assertRaises(AssertionError):gate.appop_mode(raw,operation)

    def test_exact_fixture_cleanup_cannot_delete_missing_or_additional_rows(self):
        for method,phase in [(method,'main') for method in gate.METHODS]+[(gate.RUNTIME_METHOD,'runtime-denied')]:
            base=fixture(method,phase,self.images)
            for key in ('fixture_contacts_deleted','fixture_call_rows_deleted','confirmed_call_rows_deleted'):
                for invalid in (-1,True,base[key]+1):
                    value=copy.deepcopy(base);value[key]=invalid
                    with self.subTest(method=method,phase=phase,key=key,invalid=invalid),self.assertRaises(AssertionError):gate.proof(value,method,phase,self.images,modern)
        for method,key in (('testAssistantProductionSearchDisambiguationAndCancel','fixture_contacts'),
                           ('testAssistantProductionSearchDisambiguationAndCancel','actual_contact_rows'),
                           ('testAssistantLocalHistoryBusinessAndUnsupportedCommands','fixture_call_rows')):
            value=fixture(method,'main',self.images);value[key]=2.0
            with self.subTest(method=method,key=key),self.assertRaises(AssertionError):gate.proof(value,method,'main',self.images,modern)

    def test_real_phase_orchestration_restores_state_and_keeps_stdout_even_on_failure(self):
        # Mocked adb is a host control-flow contract only, never native proof.
        for outcome in ('pass','native-failure','timeout'):
            destination=self.root/outcome;state=dict.fromkeys(gate.READ_PERMISSIONS,True)
            operations={operation:'allow' for operation in gate.READ_PERMISSIONS.values()};calls=[]
            def adb(command,**kwargs):
                calls.append(command)
                args=command[1:]
                if args==['shell','getprop','ro.kernel.qemu']:return '1\n'
                if args==['shell','getprop','ro.build.version.sdk']:return '33\n'
                if args==['shell','dumpsys','package',gate.PACKAGE]:return '\n'.join(permission+': granted='+str(granted).lower() for permission,granted in state.items())
                if args[:4]==['shell','cmd','appops','get']:return args[-1]+': '+operations[args[-1]]
                if args[:4]==['shell','cmd','appops','set']:operations[args[-2]]=args[-1];return ''
                if args[:2]==['shell','pm']:
                    self.assertEqual(['--user','0',gate.PACKAGE],args[3:6]);state[args[-1]]=args[2]=='grant';return ''
                if args==['shell','am','force-stop',gate.PACKAGE]:return ''
                self.fail('Unexpected host command: '+repr(command))
            raw=raw_runtime().encode() if outcome=='pass' else b'INSTRUMENTATION_FAILED: fixture failure\n'
            run=mock.Mock(return_value=gate.subprocess.CompletedProcess([],0,raw,b''))
            if outcome=='timeout':run.side_effect=gate.subprocess.TimeoutExpired('adb',180,output=raw,stderr=b'fixture timeout')
            with mock.patch.object(gate.subprocess,'check_output',side_effect=adb),mock.patch.object(gate.subprocess,'run',run):
                if outcome=='pass':gate.run_runtime_phase(destination)
                else:
                    with self.assertRaises((AssertionError,gate.subprocess.TimeoutExpired)):gate.run_runtime_phase(destination)
            self.assertEqual(dict.fromkeys(gate.READ_PERMISSIONS,True),state)
            self.assertEqual(dict.fromkeys(gate.READ_PERMISSIONS.values(),'allow'),operations)
            self.assertEqual(raw,(destination/'runtime-permission-phase.txt').read_bytes())
            observed=json.loads((destination/'runtime-permission-host-phase.json').read_text())
            self.assertTrue(observed['state_restored']);self.assertEqual(outcome=='pass',observed['native_status_passed'])
            command=run.call_args.args[0]
            self.assertIn(gate.OWNER+'#'+gate.RUNTIME_METHOD,command);self.assertIn('runtime-denied',command)
            self.assertTrue(any(command[1:4]==['shell','pm','revoke'] for command in calls))

    def test_runtime_phase_refuses_real_devices_before_any_permission_mutation(self):
        with mock.patch.object(gate.subprocess,'check_output',return_value='0\n') as adb,mock.patch.object(gate.subprocess,'run') as run,self.assertRaises(AssertionError):
            gate.run_runtime_phase(self.root/'physical-refused')
        self.assertEqual(1,adb.call_count);run.assert_not_called()

if __name__=='__main__':unittest.main()
