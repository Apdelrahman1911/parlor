"""Original-context source/metric rejection controls; not runtime causality."""
import hashlib
import importlib
import json
import platform
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import pin01_receipts
import pin06_public_setup_pair as pair
import pin14_original_resources as resources
import pin13_original_cohort as cohort
import run_pin03

old = sys.modules.get('_parlor_original_cohort_controls') or importlib.import_module('test_pin13_original_cohort')
VM = ('Mach Virtual Memory Statistics: (page size of 16384 bytes)\n' +
      ''.join(k+': 12.\n' for k in resources.VM_FIELDS)).encode()
SWAP = b'total = 10.00M  used = 4.00M  free = 6.00M  (encrypted)\n'


def evidence(out,failed=False):
    state=old.evidence(out,failed=failed);state['profile']=resources.PROFILE
    state['build_profile']=dict(gradle_heap_bytes=6*1024**3,gradle_workers='original-default',
        gradle_parallel=True,boot_after_build=False,xcode_jobs='original-default')
    row=state['arms'][0];row['kernel_resources']={}
    raw=json.dumps(resources.decode_resources(VM,SWAP,b'7516192768\n',b'3\n'),sort_keys=True).encode()
    for stage in resources.STAGES:
        (out/'compose'/('kernel-'+stage+'.json')).write_bytes(raw)
        row['kernel_resources'][stage]=hashlib.sha256(raw).hexdigest()
    return state


class OriginalResourceControls(unittest.TestCase):
    def test_copied_application_driver_and_native_selection_remain_identical_to_pin13(self):
        args=({p:pair.fixture(p) for p in pair.MODIFIED},pair.INPUTS)
        self.assertEqual(resources.renderer_for_profile(resources.PROFILE,resources.SOURCE,pair.RUNTIME,pair.DEVICE)(*args,arm='compose'),
                         cohort.renderer_for_profile(cohort.PROFILE,cohort.SOURCE,pair.RUNTIME,pair.DEVICE)(*args,arm='compose'))
        self.assertIs(resources.test_command,cohort.test_command)
        self.assertIs(resources.verify_results,cohort.verify_results)
        self.assertEqual(run_pin03.completed_exit_code('ORIGINAL_RESOURCE_CONTEXT_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'),0)
        self.assertEqual(run_pin03.completed_exit_code('RESOURCE_CONTEXT_QUALIFIED'),1)

    def test_original_gradle_settings_are_not_overridden_and_default_xcode_jobs_remain_original(self):
        environment=resources.build_environment({'RUNNER_TRACKING_ID':'owned'},Path('/registry'),Path('/tmp'))
        self.assertEqual(environment['RUNNER_TRACKING_ID'],'owned')
        for marker in ('Xmx3g','workers.max','parallel=false','jvmargs'):
            self.assertNotIn(marker,environment['GRADLE_OPTS'])
        self.assertIn('kotlin.compiler.execution.strategy=in-process',environment['GRADLE_OPTS'])
        self.assertIn('org.gradle.daemon.registry.base=/registry',environment['GRADLE_OPTS'])
        command=resources.build_command(['xcodebuild','-quiet'])
        self.assertNotIn('-quiet',command);self.assertNotIn('-jobs',command)
        self.assertEqual(command[-1],'build-for-testing')
        self.assertNotIn('-retry-tests-on-failure',command)

    def test_kernel_parser_retains_only_bounded_numeric_selected_fields(self):
        value=resources.decode_resources(VM,SWAP,b'7516192768\n',b'3\n')
        self.assertEqual(value['logical_cpus'],3);self.assertEqual(value['swap_mib'],[10.0,4.0,6.0])
        for vm,swap,mem,cpu in (
            (VM+b'Pages free: 2.\n',SWAP,b'7',b'3'),(VM,SWAP,b'private',b'3'),
            (VM,SWAP,b'7',b'0'),(VM,SWAP.replace(b'(encrypted)',b'(unknown)'),b'7',b'3'),
            (VM.replace(b'Pageouts: 12.\n',b''),SWAP,b'7',b'3'),
            (VM.replace(b'12.',b'-1.'),SWAP,b'7',b'3'),(b'x'*16385,SWAP,b'7',b'3'),
        ):
            with self.assertRaises(ValueError):resources.decode_resources(vm,swap,mem,cpu)
        for mutate in (lambda v:v.update(private='x'),lambda v:v['pages'].update(private=1),
                       lambda v:v.update(logical_cpus=True),lambda v:v.update(swap_mib=[float('nan'),0,0])):
            value=resources.decode_resources(VM,SWAP,b'7',b'3');mutate(value)
            with self.assertRaises(ValueError):resources.validate_resources(value)

    def test_full_original_native_acceptance_and_all_three_metrics_required_for_success_or_target_failure(self):
        for failed in (False,True):
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out,failed)
                self.assertEqual(resources.accept_native_export(out,state),
                                 'ORIGINAL_RESOURCE_CONTEXT_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION')
        for mutate in (lambda o,s:s['build_profile'].update(gradle_heap_bytes=3*1024**3),
                       lambda o,s:s['arms'][0]['kernel_resources'].pop('after-tests'),
                       lambda o,s:(o/'compose/kernel-after-build.json').write_text('{}'),
                       lambda o,s:(o/'compose/test.log').write_text('')):
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out);mutate(out,state)
                with self.assertRaises(ValueError):resources.accept_native_export(out,state)

    @unittest.skipUnless(platform.system()=='Darwin','Actual standard macOS kernel commands required')
    def test_actual_darwin_kernel_observation_contract(self):
        values=[subprocess.check_output(argv,timeout=10) for argv in (
            ['/usr/bin/vm_stat'],['/usr/sbin/sysctl','-n','vm.swapusage'],
            ['/usr/sbin/sysctl','-n','hw.memsize'],['/usr/sbin/sysctl','-n','hw.logicalcpu'])]
        resources.validate_resources(resources.decode_resources(*values))
