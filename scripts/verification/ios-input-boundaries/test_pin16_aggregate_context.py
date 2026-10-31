"""Original aggregate-context rejection fixtures, not native executions."""
import copy
import hashlib
import importlib
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock
import xml.etree.ElementTree as ET

import pin06_public_setup_pair as pair
import pin15_combined_xcode as combined
import pin16_aggregate_context as aggregate
import run_pin03

prior = sys.modules.get('_parlor_combined_xcode_controls') or importlib.import_module('test_pin15_combined_xcode')
from scripts.ci import apple_verification_hygiene as apple


def reports():
    raws, inputs = {}, {}
    for c in aggregate.case_manifest()['classes']:
        inputs[c['source']] = c['source_sha256']
        s = ET.Element('testsuite', dict(name=c['classname'], tests=str(len(c['methods'])),
                                        failures='0', errors='0', skipped='0'))
        for m in c['methods']:
            ET.SubElement(s, 'testcase', dict(name=m+'[iosSimulatorArm64]', classname=c['classname']))
        raws[c['path']] = ET.tostring(s)
    return raws, inputs


def evidence(out, failed=False):
    state = prior.evidence(out, failed); state['profile'] = aggregate.PROFILE
    raws, inputs = reports(); state['original_inputs'] = dict(state['original_inputs']); state['original_inputs'].update(inputs)
    path = out/'compose/inputs.json'; copied = json.loads(path.read_bytes()); copied.update(inputs)
    path.write_text(json.dumps(copied))
    for p, b in raws.items():
        target = out/'aggregate/xml'/p; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(b)
    row = dict(result='ORIGINAL774_EXECUTED_NOT_QUALIFICATION', original_uninstrumented_inputs=True,
               exit_code=0, stop_exit=0, cleanup='PASS', workers=dict(result='PASS'),
               reports=aggregate.verify_prelude_xml(raws, state['original_inputs']),
               retained_reports={p: hashlib.sha256(b).hexdigest() for p, b in raws.items()})
    for stage in ('before', 'after'):
        raw = b'[]'; (out/'aggregate'/('simulators-'+stage+'.json')).write_bytes(raw)
        row['simulators_'+stage] = hashlib.sha256(raw).hexdigest()
    state['aggregate_prelude'] = row
    wrapper = ['/bin/bash', '-c', 'exec "$@" > "$PARLOR_INPUT_LOG" 2>&1', '--']
    commands = [dict(label='original-kmp-aggregate', command=wrapper+aggregate.PRELUDE_COMMAND,
                     exit_code=0, timeout_seconds=2700, status='EXITED', direct_child_reaped=True, signals=[]),
                dict(label='original-kmp-stop', command=wrapper+['/owned/gradlew', '--stop'],
                     exit_code=0, timeout_seconds=120, status='EXITED', direct_child_reaped=True, signals=[])]
    state['commands'] = commands + state['commands']
    for c in state['commands']:
        c.setdefault('command', [])  # Older abstract fixtures omit unrelated argv.
    return state


class AggregateContextControls(unittest.TestCase):
    def test_renderer_driver_observers_actions_queries_deadlines_and_all23_cases_are_identical_to_pin15(self):
        args = ({p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS)
        self.assertEqual(aggregate.renderer_for_profile(aggregate.PROFILE, aggregate.SOURCE, pair.RUNTIME, pair.DEVICE)(*args, arm='compose'),
                         combined.renderer_for_profile(combined.PROFILE, combined.SOURCE, pair.RUNTIME, pair.DEVICE)(*args, arm='compose'))
        self.assertIs(aggregate.test_command, combined.test_command)
        self.assertIs(aggregate.verify_results, combined.verify_results)
        self.assertIs(aggregate.build_environment, combined.build_environment)
        self.assertEqual(run_pin03.completed_exit_code('ORIGINAL_AGGREGATE_CONTEXT_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'), 0)
        self.assertEqual(run_pin03.completed_exit_code('ORIGINAL_AGGREGATE_CONTEXT_QUALIFIED'), 1)
        with self.assertRaises(ValueError): run_pin03.main(aggregate_context=True, combined_execution=True)

    def test_all149_original_source_bound_reports_and774_methods_are_required_without_skips(self):
        raws, inputs = reports()
        self.assertEqual(sum(r['methods'] for r in aggregate.verify_prelude_xml(raws, inputs)), 774)
        path = next(iter(raws))
        for change in (
            lambda r, i: r.pop(path), lambda r, i: r.update(foreign=r[path]),
            lambda r, i: r.__setitem__(path, r[path].replace(b'failures="0"', b'failures="1"')),
            lambda r, i: r.__setitem__(path, r[path].replace(b'skipped="0"', b'skipped="1"')),
            lambda r, i: r.__setitem__(path, r[path].replace(b'iosSimulatorArm64', b'iosArm64')),
            lambda r, i: r.__setitem__(path, b'<!DOCTYPE testsuite>'+r[path]),
            lambda r, i: r.__setitem__(path, r[path].replace(b'<testcase', b'<skipped', 1)),
            lambda r, i: i.__setitem__(next(iter(i)), '0'*64),
        ):
            r, i = copy.deepcopy(raws), copy.deepcopy(inputs); change(r, i)
            with self.assertRaises(ValueError): aggregate.verify_prelude_xml(r, i)

    def test_complete_original_aggregate_and_native_success_or_retained_target_failure_have_no_qualification_claim(self):
        for failed in (False, True):
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve()
                self.assertEqual(aggregate.accept_native_export(out, evidence(out, failed)),
                                 'ORIGINAL_AGGREGATE_CONTEXT_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION')

    def test_missing_replayed_retargeted_later_longer_changed_or_unreaped_aggregate_rejects(self):
        for change in (
            lambda s: s['commands'].pop(0),
            lambda s: s['commands'].append(copy.deepcopy(s['commands'][0])),
            lambda s: s['commands'][0].update(timeout_seconds=2701),
            lambda s: s['commands'][0]['command'].append('--tests=*OneTest'),
            lambda s: s['commands'][0].update(direct_child_reaped=False),
            lambda s: s['commands'].append(s['commands'].pop(0)),
            lambda s: s['aggregate_prelude'].update(original_uninstrumented_inputs=False),
            lambda s: s['aggregate_prelude'].update(exit_code=1),
            lambda s: s['aggregate_prelude'].update(cleanup='FAIL'),
            lambda s: s['aggregate_prelude']['reports'].pop(),
        ):
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve(); s = evidence(out); change(s)
                with self.assertRaises(ValueError): aggregate.accept_native_export(out, s)

    def test_inventory_retains_only_bounded_boot_state_runtime_and_uuid_without_names_or_profile_values(self):
        entry = dict(udid='00000000-0000-4000-8000-000000000001', state='Booted', isAvailable=True,
                     name='DO NOT EXPORT', runtime='unused')
        raw = json.dumps(dict(devices={'com.apple.CoreSimulator.SimRuntime.iOS-18-5':[entry]})).encode()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)
            aggregate.observe_simulators(lambda *a: (0, raw), path, 'before')
            kept = (path/'simulators-before.json').read_bytes()
            self.assertNotIn(b'DO NOT EXPORT', kept)
            self.assertEqual(json.loads(kept)[0][2:], [1, 1])
            self.assertEqual(aggregate.validate_simulator_observation(kept), json.loads(kept))
            for bad in (b'{}', raw.replace(b'Booted', b'Unknown'), b'x'*1048577):
                with self.assertRaises((ValueError, RuntimeError, KeyError)):
                    aggregate.observe_simulators(lambda *a: (0, bad), path, 'after')

    def test_redirected_or_modified_xml_inventory_and_foreign_private_fields_reject(self):
        for change in (
            lambda o, s: (o/'aggregate/simulators-before.json').write_text('["private"]'),
            lambda o, s: (o/'aggregate/xml'/aggregate.case_manifest()['classes'][0]['path']).write_text('<testsuite/>'),
            lambda o, s: s['aggregate_prelude']['retained_reports'].clear(),
        ):
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve(); state = evidence(out); change(out, state)
                with self.assertRaises(ValueError): aggregate.accept_native_export(out, state)
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve(); state = evidence(out)
            target = out/'aggregate/xml'/aggregate.case_manifest()['classes'][0]['path']
            original = target.read_bytes(); target.unlink(); other = out/'other.xml'; other.write_bytes(original)
            target.symlink_to(other)
            with self.assertRaises(ValueError): aggregate.accept_native_export(out, state)
        for values in ({'private':1}, [['private']], [[True, 'runtime', 0, 0]]):
            with self.assertRaises(ValueError): aggregate.validate_simulator_observation(json.dumps(values).encode())

    def test_real_collector_restores_once_only_original_workload_and_preserves_reports_and_cleanup_on_failure(self):
        for code in (0, 1):
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve(); source = out/'source'; source.mkdir()
                raws, inputs = reports()
                for p, b in raws.items():
                    target = source/p; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(b)
                state = dict(original_inputs=inputs); calls = []; commands = SimpleNamespace(finalizing=False)
                def command(argv, label, *unused):
                    calls.append((argv, label, commands.finalizing))
                    if label.startswith('aggregate-inventory'): return 0, b'{"devices":{}}'
                    return (code if label == 'original-kmp-aggregate' else 0), b''
                with mock.patch.object(apple, 'prepare', return_value={}), mock.patch.object(apple, 'DarwinWorkerBackend'), \
                     mock.patch.object(apple, 'stop_workers', return_value={'result':'PASS'}):
                    if code:
                        with self.assertRaises(ValueError):
                            aggregate.execute_prelude(source, out, state, command, lambda: None, source, {}, {},
                                                      {'RUNNER_TRACKING_ID':'owned'}, commands)
                    else:
                        aggregate.execute_prelude(source, out, state, command, lambda: None, source, {}, {},
                                                  {'RUNNER_TRACKING_ID':'owned'}, commands)
                self.assertEqual([c[0] for c in calls if c[1]=='original-kmp-aggregate'], [aggregate.PRELUDE_COMMAND])
                self.assertEqual([c[2] for c in calls if c[1]=='original-kmp-stop'], [True])
                self.assertEqual(len(list((out/'aggregate/xml').rglob('*.xml'))), 149)
                self.assertEqual(state['aggregate_prelude']['cleanup'], 'PASS')
                self.assertFalse(commands.finalizing)


if __name__ == '__main__':
    unittest.main()
