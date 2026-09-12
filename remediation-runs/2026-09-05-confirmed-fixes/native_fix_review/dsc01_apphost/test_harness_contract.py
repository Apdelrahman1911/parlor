"""Pure receipt validators and disposable shell stubs; no real builds/devices."""
import copy
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import uuid

import run_dsc01_apphost_cycle as runner


def preferences(setting='system', app=None, installed='none', previous=None, language='en'):
    return dict(setting=setting, appLanguages=app or [], hasAppOverride=bool(app),
                ownerPresent=installed!='none', ownerInstalled=installed,
                ownerPreviousPresent=bool(previous), ownerPrevious=previous or [], preferredLanguage=language)


def valid_report():
    rows=[]
    boots=[str(uuid.uuid4()) for _ in range(4)]
    baseline=preferences()
    ar=preferences('ar',['ar'],'ar',language='ar')
    os_ar=preferences(app=['ar-EG','en'],language='ar')
    en=preferences('en',['en'],'en',['ar-EG','en'])
    def add(index, phase, pref):
        rows.append(dict(ordinal=len(rows)+1, phase=phase, fixture=['fresh','continue','previous-ar','continue'][index],
                         boot=boots[index], preferences=copy.deepcopy(pref),
                         nativeDirection='unavailable' if phase=='before_main' else 'force_rtl' if pref['preferredLanguage']=='ar' else 'force_ltr',
                         controllerCreations=0 if phase=='before_main' else 1))
    for index, first, samples in [(0,baseline,[baseline,ar]), (1,ar,[ar,baseline]),
                                  (2,os_ar,[os_ar,en]), (3,en,[en,os_ar])]:
        add(index,'before_main',first)
        for value in samples: add(index,'sample',value)
    add(3,'complete',os_ar); add(3,'sample',os_ar)
    return dict(schemaVersion=1,runToken=str(uuid.uuid4()),completed=True,observations=rows)


class ReceiptContractTest(unittest.TestCase):
    def test_complete_synthetic_schema_is_accepted_not_runtime_evidence(self):
        proof=runner.verify_probe(valid_report())
        self.assertEqual(4,proof['actual_process_boots'])
        self.assertFalse(proof['active_session_retention_test'])
        self.assertFalse(proof['physical_device_evidence'])

    def test_missing_no_disposal_precondition_is_rejected(self):
        report=valid_report(); report['observations'][3]['preferences']['ownerPresent']=False
        with self.assertRaises(RuntimeError): runner.verify_probe(report)

    def test_changed_boot_identity_cannot_impersonate_restart(self):
        report=valid_report(); old=report['observations'][3]['boot']; initial=report['observations'][0]['boot']
        for event in report['observations']:
            if event['boot']==old: event['boot']=initial
        with self.assertRaises(RuntimeError): runner.verify_probe(report)

    def test_unknown_data_is_not_exported_as_trusted_observation(self):
        report=valid_report(); report['observations'][0]['preferences']['roleMap']={}
        with self.assertRaises(RuntimeError): runner.verify_probe(report)

    def test_missing_system_restoration_or_wrong_native_direction_fails(self):
        for mutation in ('system','direction'):
            report=valid_report()
            for event in report['observations']:
                if event['fixture']=='continue' and event['preferences']['setting']=='system':
                    if mutation=='system': event['preferences']['setting']='ar'
                    else: event['nativeDirection']='force_ltr'
            with self.assertRaises(RuntimeError): runner.verify_probe(report)

    def test_test_summary_with_skip_cannot_pass(self):
        with self.assertRaises(RuntimeError):
            runner.verify_xctest(dict(result='Passed',totalTestCount=1,passedTests=0,
                                      failedTests=0,skippedTests=1,expectedFailures=0),{},'synthetic')


class CopiedPhaseContractTest(unittest.TestCase):
    def run_fixture(self,build=0,stop=0,normalize=0,missing_root=False):
        with tempfile.TemporaryDirectory(prefix='parlor-dsc01-phase-test-') as temporary:
            path=Path(temporary).resolve(); commands=path/'commands.txt'; receipt=path/'stop-receipt.txt'
            wrapper=path/'gradlew'
            wrapper.write_text('#!/bin/sh\nif [ "$1" = "--stop" ]; then\n printf "stop\\n" >> "$COMMAND_LOG"\n exit "$STOP_EXIT"\nfi\nprintf "build\\n" >> "$COMMAND_LOG"\nexit "$BUILD_EXIT"\n')
            wrapper.chmod(0o700)
            normalizer=path/'scripts/release/normalize_embedded_apple_framework.sh'
            normalizer.parent.mkdir(parents=True)
            normalizer.write_text('#!/bin/sh\nprintf "normalize\\n" >> "$COMMAND_LOG"\nexit "$NORMALIZE_EXIT"\n')
            normalizer.chmod(0o700)
            # Stale output cannot turn a failed build into a successful phase.
            (path/'stale-framework').write_text('synthetic stale artifact')
            phase=(Path(__file__).parent/'copied-kotlin-phase.sh.in').read_text()
            phase=phase.replace('__PARLOR_SOURCE_ROOT__',str(path/'absent' if missing_root else path))
            phase=phase.replace('__PARLOR_STOP_RECEIPT__',str(receipt))
            env={'PATH':'/usr/bin:/bin','BUILD_EXIT':str(build),'STOP_EXIT':str(stop),
                 'NORMALIZE_EXIT':str(normalize),'COMMAND_LOG':str(commands),
                 'TARGET_BUILD_DIR':str(path),'FRAMEWORKS_FOLDER_PATH':'stale-framework'}
            result=subprocess.run(['/bin/sh','-c',phase],env=env,capture_output=True,text=True,timeout=10)
            return result.returncode, commands.read_text() if commands.exists() else '', receipt.read_text() if receipt.exists() else ''

    def test_failed_build_with_stale_output_stops_and_never_normalizes(self):
        self.assertEqual((47,'build\nstop\n','build_exit=47\nstop_exit=0\n'),self.run_fixture(build=47))

    def test_failed_directory_change_never_builds_or_normalizes(self):
        self.assertEqual((1,'',''),self.run_fixture(missing_root=True))

    def test_failed_stop_cannot_normalize_or_look_green(self):
        self.assertEqual((23,'build\nstop\n','build_exit=0\nstop_exit=23\n'),self.run_fixture(stop=23))

    def test_normalization_failure_propagates_after_immediate_stop(self):
        self.assertEqual((19,'build\nstop\nnormalize\n','build_exit=0\nstop_exit=0\n'),self.run_fixture(normalize=19))

    def test_successful_phase_stops_before_normalizing(self):
        self.assertEqual((0,'build\nstop\nnormalize\n','build_exit=0\nstop_exit=0\n'),self.run_fixture())


if __name__=='__main__':
    unittest.main()
