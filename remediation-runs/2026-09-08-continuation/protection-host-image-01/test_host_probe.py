"""Focused inert driver controls; no native process or platform evidence."""
import copy
import json
import os
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

import run_host_probe as probe
from test_image_hook import BINDING, fixture


def report_inputs():
    record = fixture()
    return (record, dict(binding=dict(BINDING), uid=501),
            dict(uuid=record['results'][0]['observation']['uuid'], image_basename=probe.IMAGE),
            dict(owned_pid=record['process_id']), dict(runtime_version=record['runtime_version']))


def setter_report_inputs():
    # Borrow one already controlled synthetic sample, not a paired execution or
    # simulator context. The host-only record below contains no simulator ID.
    samples = probe.common.load_helper('_host_origin_test_sample', probe.SHARED / 'test_probe.py')
    rows, original_request, _ = samples.fixture()
    host, _, _ = samples.host_fixture(rows, original_request)
    sample = host['samples'][2]['native']
    sample['identity']['size'] = len(probe.SETTER_PAYLOAD)
    for witness in sample['reads']:
        witness.update(before=copy.deepcopy(sample['identity']), after=copy.deepcopy(sample['identity']))
    sample['fm'].update(key_present=False, protection='missing')
    implementation = copy.deepcopy(sample['fm']['implementation_before'])
    implementation['selector'] = 'setAttributes:ofItemAtPath:error:'
    main = copy.deepcopy(host['main_image_before'])
    main['image_basename'] = probe.SETTER_IMAGE
    selected = dict(root=dict(device=7, inode=100, uid=501), identity=copy.deepcopy(sample['identity']))
    record = dict(schema=1, kind='host-origin-protection-setter', collection_status='PASS', binding=dict(BINDING),
        fixture_root_device=7, fixture_root_inode=100, scope=probe.SETTER_SCOPE, target_leaf='probe.bin',
        process_id=1235, uid=501, runtime_version=[15, 7, 9], read_only=False, simulator_fixture_observed=False,
        production_snapshots_observed=False, historical_a37_strict_result_changed=False, protection_qualified=False,
        main_image_before=main, main_image_after=copy.deepcopy(main), before=sample, after=copy.deepcopy(sample),
        operation=dict(id='host-origin-fm-set', requested_protection='complete', returned=False,
            native_error=dict(present=True, code=4, domain='cocoa'), implementation_before=implementation,
            implementation_after=copy.deepcopy(implementation)))
    command = dict(label='host-setter-observation', owned_pid=1235, ownership='direct-unreaped-Popen',
        status='EXITED', exit_code=0, direct_child_reaped=True)
    return record, dict(binding=dict(BINDING), uid=501), selected, dict(uuid=main['uuid'], image_basename=probe.SETTER_IMAGE), command, \
        dict(runtime_version=[15, 7, 9])


class HostProbeTests(unittest.TestCase):
    def test_controls_cli_and_selection_are_disjoint_before_native_work(self):
        manifest = probe.controls()
        self.assertEqual([row['path'] for row in manifest['files']], sorted(set(probe.CONTROL_PATHS)))
        self.assertTrue(set(probe.common.CONTROL_PATHS) <= set(probe.CONTROL_PATHS))
        for name in ('run_host_probe.py', 'test_host_probe.py', 'README.md', 'HostImageProbe.m.in', 'image_hook.py', 'test_image_hook.py',
                     'HostSetterProbe.m.in'):
            self.assertIn(str(probe.HERE.relative_to(probe.ROOT) / name), probe.CONTROL_PATHS)
        self.assertEqual(probe.HostProbe.__bases__, (object,))
        self.assertFalse(hasattr(probe.HostProbe, 'create_simulator'))
        self.assertIs(probe.HostProbe.stop, probe.common.Probe.stop)
        with patch.object(probe, 'HostProbe') as constructor, patch('builtins.print'):
            self.assertEqual(probe.main(['controls']), 0)
            with self.assertRaises(RuntimeError): probe.main(['run', 'extra'])
            constructor.assert_not_called()
        for selection in (None, 'paired', 'protection-application-only'):
            with self.subTest(selection=selection), patch.object(probe, 'controls') as control, patch.object(probe.common, 'Commands') as commands:
                with self.assertRaisesRegex(RuntimeError, 'host-only-selection'):
                    probe.HostProbe({'PARLOR_PROTECTION_SELECTION': selection}, 'run')
                control.assert_not_called()
                commands.assert_not_called()

    def test_approval_and_nonroot_host_admission_precede_commands(self):
        env = dict(PARLOR_PROTECTION_SELECTION=probe.SELECTION, PARLOR_APPROVED_PROBE_CONTROL_SHA256='a' * 64)
        with patch.object(probe, 'controls', return_value=dict(control_sha256='b' * 64)), \
                patch.object(probe.common, 'Commands') as commands, self.assertRaisesRegex(RuntimeError, 'approved-control'):
            probe.HostProbe(env, 'run')
        commands.assert_not_called()
        with patch.object(probe, 'controls', return_value=dict(control_sha256='a' * 64)), \
                patch.object(probe.platform, 'system', return_value='Linux'), \
                patch.object(probe.common, 'Commands') as commands, self.assertRaisesRegex(RuntimeError, 'nonroot-arm64-macos'):
            probe.HostProbe(env, 'run')
        commands.assert_not_called()

    def test_actual_hook_bridge_binds_process_host_and_binary_without_erasing_rejections(self):
        record, request, built, command, host = report_inputs()
        summary = probe.validate_report(json.dumps(record).encode(), request, built, command, host)
        self.assertEqual(summary['status'], probe.COLLECTED)
        self.assertEqual([row['original_result'] for row in summary['results']], ['PASS', 'REJECTED', 'REJECTED'])
        self.assertFalse(summary['protection_qualified'])
        self.assertFalse(summary['original_guard_changed'])
        for field, bad in (('process_id', 124), ('uid', 502), ('runtime_version', [15, 7, 8])):
            with self.subTest(field=field), self.assertRaisesRegex(RuntimeError, 'actual-host-process-binding'):
                probe.validate_report(json.dumps({**record, field: bad}).encode(), request, built, command, host)
        with self.assertRaisesRegex(RuntimeError, 'compiled-host-image-binding'):
            probe.validate_report(json.dumps(record).encode(), request, {**built, 'uuid': 'f' * 36}, command, host)
        changed = copy.deepcopy(record)
        changed['binding']['control_sha256'] = 'd' * 64
        with self.assertRaises(RuntimeError):
            probe.validate_report(json.dumps(changed).encode(), request, built, command, host)

    def test_platform_observes_host_sdk_without_a_simulator(self):
        with TemporaryDirectory() as raw:
            developer = Path(raw).resolve() / 'Developer'
            sdk = developer / 'Platforms/MacOSX.platform/Developer/SDKs/MacOSX26.2.sdk'
            sdk.mkdir(parents=True)
            lane = object.__new__(probe.HostProbe)
            lane.state, lane.save = {}, Mock()
            lane.commands = SimpleNamespace(environment={'JAVA_HOME': '/synthetic/jdk'})
            responses = {'macos-version': b'15.7.9\n', 'macos-build': b'24G999\n',
                'xcode-version': b'Xcode 26.3\nBuild version 17C529\n', 'developer-selection': str(developer).encode(),
                'host-sdk-version': b'26.2\n', 'host-sdk-path': str(sdk).encode()}
            lane.execute = Mock(side_effect=lambda arguments, label: responses[label])
            lane.capture = Mock(return_value=(dict(status='EXITED', exit_code=0, direct_child_reaped=True), b'', b'openjdk version "21.0.9"'))
            with patch.object(probe.common, 'DEVELOPER', str(developer)):
                self.assertEqual(lane.platform_binding(), sdk)
                self.assertEqual(lane.state['platform']['runtime_version'], [15, 7, 9])
                self.assertEqual(lane.state['platform']['profile'], probe.PROFILE)
                responses['host-sdk-version'] = b'26.1\n'
                with self.assertRaisesRegex(RuntimeError, 'qualified-host-sdk'):
                    lane.platform_binding()
            self.assertIn(['/usr/bin/xcrun', '--sdk', 'macosx', '--show-sdk-version'], [call.args[0] for call in lane.execute.call_args_list])
            self.assertFalse(any('simctl' in call.args[0] for call in lane.execute.call_args_list))

    def test_one_host_compile_retains_exact_copy_diff_and_raw_failed_capture(self):
        for wrong_pid in (False, True):
            with self.subTest(wrong_pid=wrong_pid), TemporaryDirectory() as raw:
                lane = object.__new__(probe.HostProbe)
                root = Path(raw).resolve()
                lane.resources, lane.evidence = root / 'resources', root / 'evidence'
                lane.resources.mkdir(); lane.evidence.mkdir()
                record, lane.request, built, command, host = report_inputs()
                lane.state, lane.save, lane.bindings = dict(logs=[], platform=host), Mock(), Mock()
                lane.commands = SimpleNamespace(preservation_error=Mock())
                calls = []
                def execute(arguments, label, timeout=30):
                    calls.append((arguments, label, timeout))
                    image = lane.resources / probe.IMAGE
                    if label == 'host-compile':
                        self.assertTrue((lane.evidence / 'host-copy.patch').is_file())
                        probe.native.write_new(image, b'synthetic-not-executable')
                    if label == 'host-built-uuid':
                        return ('UUID: ' + built['uuid'] + ' (arm64) ' + str(image) + '\n').encode()
                    return b''
                lane.execute = execute
                if wrong_pid: record['process_id'] += 1
                raw_record = json.dumps(record).encode()
                lane.capture = Mock(return_value=({**command, 'status': 'EXITED', 'exit_code': 0, 'direct_child_reaped': True}, raw_record, b''))
                if wrong_pid:
                    with self.assertRaisesRegex(RuntimeError, 'actual-host-process-binding'):
                        lane.compile_and_run(Path('/synthetic/MacOSX26.2.sdk'))
                else:
                    lane.compile_and_run(Path('/synthetic/MacOSX26.2.sdk'))
                    self.assertEqual(lane.state['collection_status'], probe.COLLECTED)
                self.assertEqual((lane.evidence / 'host-image.stdout.json').read_bytes(), raw_record)
                self.assertIn(b'ParlorHostImageCapture', (lane.evidence / 'host-copy.patch').read_bytes())
                copied = (lane.resources / 'ProtectionSampler.m').read_bytes()
                self.assertEqual(copied.count(probe.hook.GUARD), 1)
                self.assertEqual((lane.resources / 'ProtectionSampler.h').read_bytes(), (probe.SHARED / 'ProtectionSampler.h').read_bytes())
                self.assertNotIn(b'__HOST_IMAGE_', (lane.resources / 'HostImageProbe.m').read_bytes())
                compile_calls = [call for call in calls if call[1] == 'host-compile']
                self.assertEqual(len(compile_calls), 1)
                self.assertEqual(compile_calls[0][0][:4], ['/usr/bin/xcrun', '--sdk', 'macosx', 'clang'])
                self.assertIn('arm64-apple-macosx15.0', compile_calls[0][0])
                self.assertEqual(compile_calls[0][2], 90)
                lane.capture.assert_called_once_with([str(lane.resources / probe.IMAGE)], 'host-image-observation', 30, False)
                self.assertFalse(any('simctl' in args or 'iphonesimulator' in args or any('gradlew' in part for part in args) for args, _, _ in calls))

    def test_failed_native_cycle_preserves_before_the_mandatory_stop(self):
        lane = object.__new__(probe.HostProbe)
        lane.state = dict(status='RUNNING', errors=[])
        lane.prepare, lane.bindings, lane.platform_binding = Mock(), Mock(), Mock(return_value=Path('/synthetic/sdk'))
        events = []
        def compile_failure(_sdk):
            events.append('compile')
            raise RuntimeError('synthetic-compile-failure')
        lane.compile_and_run = compile_failure
        lane.stop = lambda: events.append('stop')
        lane.commands = SimpleNamespace(handles=[], signals=[], preservation=dict(failures=0), interrupted=Mock(),
            checkpoint=lambda: events.append('preserve'))
        with patch.object(probe.signal, 'signal', return_value=None):
            self.assertEqual(lane.run(), 1)
        self.assertEqual(events, ['compile', 'preserve', 'stop', 'preserve'])
        self.assertEqual(lane.state['status'], 'FAIL')

    def cleanup_fixture(self, root):
        lane = object.__new__(probe.HostProbe)
        lane.mode, lane.base = 'cleanup', root / 'base'
        lane.evidence, lane.resources, lane.cleanup_dir = (lane.base / name for name in ('evidence', 'resources', 'cleanup'))
        for path in (lane.base, lane.evidence, lane.resources, lane.resources / 'tmp'):
            path.mkdir(mode=0o700)
        probe.native.write_new(lane.resources / probe.IMAGE, b'owned-synthetic-not-executable')
        lane.source = dict(source_sha=BINDING['source_sha'], run_id=123, run_attempt=1)
        lane.approved, lane.control = BINDING['control_sha256'], dict(control_sha256=BINDING['control_sha256'], files=[])
        lane.request = dict(schema=1, selection=probe.SELECTION, profile=probe.PROFILE, source=lane.source, controls=lane.control,
            uid=os.getuid(), base=probe.common.owned_directory(lane.base), evidence=probe.common.owned_directory(lane.evidence),
            resources=probe.common.owned_directory(lane.resources), temporary=probe.common.owned_directory(lane.resources / 'tmp'), binding=dict(BINDING))
        probe.native.write_new(lane.evidence / 'request.json', probe.native.json_bytes(lane.request))
        prior = dict(status='CAPTURED', selection=probe.SELECTION, profile=probe.PROFILE,
            preservation=dict(failures=0), commands=[dict(status='EXITED', direct_child_reaped=True)])
        probe.native.write_new(lane.evidence / 'state.json', probe.native.json_bytes(prior))
        lane.env = dict(PARLOR_PROTECTION_UPLOAD_OUTCOME='success', PARLOR_PROTECTION_ARTIFACT_ID='456', PARLOR_PROTECTION_ARTIFACT_DIGEST='e' * 64)
        lane.bindings = Mock()
        lane.commands = SimpleNamespace(rows=[], signals=[], preservation=dict(failures=0), deadline=0, interrupted=Mock(),
            checkpoint=lambda: lane.save())
        lane.stop = Mock()
        return lane, prior

    def test_cleanup_requires_uploaded_settled_owned_host_scratch_and_never_claims_simulator_cleanup(self):
        for mutation in (None, 'upload', 'timeout', 'symlink'):
            with self.subTest(mutation=mutation), TemporaryDirectory() as raw:
                root = Path(raw).resolve()
                lane, prior = self.cleanup_fixture(root)
                if mutation == 'upload': lane.env['PARLOR_PROTECTION_UPLOAD_OUTCOME'] = 'failure'
                elif mutation == 'timeout':
                    prior['commands'][0]['status'] = 'TIMEOUT'
                    (lane.evidence / 'state.json').write_bytes(probe.native.json_bytes(prior))
                elif mutation == 'symlink': (lane.resources / 'foreign-link').symlink_to(root)
                with patch.object(probe.signal, 'signal', return_value=None):
                    self.assertEqual(lane.cleanup(), 0 if mutation is None else 1)
                self.assertEqual(lane.stop.call_count, 2)
                self.assertEqual(lane.resources.exists(), mutation is not None)
                self.assertTrue(lane.evidence.is_dir())
                self.assertTrue((lane.cleanup_dir / 'state.json').is_file())
                self.assertNotIn('simulator', lane.state)
                self.assertNotIn('NOT_CREATED', json.dumps(lane.state))

    def test_cleanup_rejects_a_foreign_route_request_before_removal(self):
        with TemporaryDirectory() as raw:
            lane, _ = self.cleanup_fixture(Path(raw).resolve())
            lane.request['selection'] = 'paired'
            (lane.evidence / 'request.json').write_bytes(probe.native.json_bytes(lane.request))
            with self.assertRaisesRegex(RuntimeError, 'owned-host-request-binding'):
                lane.cleanup()
            lane.stop.assert_not_called()
            self.assertTrue(lane.resources.is_dir())
            self.assertFalse(lane.cleanup_dir.exists())

    def test_host_origin_setter_bool_error_and_class_do_not_promote_any_qualification(self):
        record, request, selected, built, command, host = setter_report_inputs()
        for returned, error_present, protection in ((False, True, 'missing'), (False, False, 'none'),
                                                   (True, True, 'missing'), (True, False, 'complete')):
            with self.subTest(returned=returned, error=error_present, protection=protection):
                value = copy.deepcopy(record)
                value['operation'].update(returned=returned, native_error=dict(present=error_present,
                    code=4 if error_present else 0, domain='cocoa' if error_present else 'none'))
                value['after']['fm'].update(protection=protection, key_present=protection != 'missing')
                result = probe.validate_setter_report(json.dumps(value).encode(), request, selected, built, command, host)
                self.assertEqual((result['status'], result['scope']), (probe.SETTER_COLLECTED, probe.SETTER_SCOPE))
                self.assertEqual(result['operation'], value['operation'])
                self.assertEqual(result['after']['fm']['protection'], protection)
                for key in ('simulator_fixture_observed', 'production_snapshots_observed', 'historical_a37_strict_result_changed',
                            'protection_qualified', 'runtime_implementation_causality_proven'):
                    self.assertIs(result[key], False)
                self.assertNotIn('strict_synthetic_complete', result)
                self.assertNotIn('simulator_udid', json.dumps(result))

    def test_host_origin_setter_rejects_identity_context_process_and_method_drift(self):
        record, request, selected, built, command, host = setter_report_inputs()
        for stage in ('before', 'after'):
            for key in ('device', 'inode', 'uid', 'mode', 'links', 'size'):
                with self.subTest(stage=stage, key=key):
                    value = copy.deepcopy(record)
                    value[stage]['identity'][key] += 1
                    for witness in value[stage]['reads']:
                        witness.update(before=copy.deepcopy(value[stage]['identity']), after=copy.deepcopy(value[stage]['identity']))
                    with self.assertRaises(RuntimeError):
                        probe.validate_setter_report(json.dumps(value).encode(), request, selected, built, command, host)
        mutations = [lambda value: value.update(target_leaf='other.bin'), lambda value: value.update(scope='PAIRED'),
            lambda value: value.update(fixture_root_inode=101), lambda value: value.update(process_id=1236),
            lambda value: value['binding'].update(run_token='0' * 32), lambda value: value['binding'].update(simulator_udid='invented'),
            lambda value: value['operation'].update(returned=1), lambda value: value['operation']['native_error'].update(domain='none'),
            lambda value: [value['operation'][key].update(selector='attributesOfItemAtPath:error:') for key in
                ('implementation_before', 'implementation_after')],
            lambda value: value['operation']['implementation_after']['implementation'].update(image_offset=999),
            lambda value: [value['operation'][key]['implementation'].update(image_basename='CoreFoundation') for key in
                ('implementation_before', 'implementation_after')],
            lambda value: [value[key].update(platforms=[1, 6]) for key in ('main_image_before', 'main_image_after')],
            lambda value: value.update(collection_status='FAIL')]
        for index, mutate in enumerate(mutations):
            with self.subTest(mutation=index):
                value = copy.deepcopy(record)
                mutate(value)
                with self.assertRaises(RuntimeError):
                    probe.validate_setter_report(json.dumps(value).encode(), request, selected, built, command, host)

    def test_host_origin_setter_requires_prior_image_and_one_bounded_command_sequence(self):
        lane = object.__new__(probe.HostProbe)
        lane.state, lane.commands, lane.execute = {}, SimpleNamespace(rows=[]), Mock()
        with self.assertRaisesRegex(RuntimeError, 'host-image-required-before-single-setter'):
            lane.compile_and_run_setter(Path('/synthetic/sdk'))
        lane.execute.assert_not_called()
        resources, sdk = Path('/synthetic/resources'), Path('/synthetic/sdk')
        expected = (('host-image-observation', [str(resources / probe.IMAGE)], 30),) + probe.setter_build_commands(resources, sdk) + (
            ('host-setter-observation', [str(resources / probe.SETTER_IMAGE)], 30),)
        commands = [dict(label=label, command=arguments, timeout_seconds=timeout, status='EXITED', exit_code=0,
            direct_child_reaped=True, ownership='direct-unreaped-Popen') for label, arguments, timeout in expected]
        self.assertEqual(probe.setter_command_order(commands, resources, sdk), commands[-1])
        changed = copy.deepcopy(commands)
        changed[-1]['timeout_seconds'] = 31
        for rows in (commands[1:], commands[::-1], commands + [commands[-1]], changed):
            with self.subTest(rows=rows), self.assertRaises(RuntimeError):
                probe.setter_command_order(rows, resources, sdk)

    def test_host_origin_setter_retains_failed_raw_output_and_sticky_preservation_failure(self):
        with TemporaryDirectory() as raw:
            lane = object.__new__(probe.HostProbe)
            lane.evidence, lane.resources = Path(raw).resolve(), Path(raw).resolve() / 'resources'
            lane.state, lane.save = dict(logs=[]), Mock()
            lane.commands = probe.common.Commands({}, 100)
            lane.capture = Mock(return_value=(dict(status='TIMEOUT', exit_code=-15, direct_child_reaped=True),
                b'{"collection_status":"FAIL"}', b'synthetic-partial-result'))
            with self.assertRaisesRegex(RuntimeError, 'host-origin-setter-collection-process'):
                lane.capture_setter()
            lane.capture.assert_called_once_with([str(lane.resources / probe.SETTER_IMAGE)], 'host-setter-observation', 30, False)
            self.assertEqual((lane.evidence / 'host-setter.stdout.json').read_bytes(), b'{"collection_status":"FAIL"}')
            self.assertEqual((lane.evidence / 'host-setter.stderr.txt').read_bytes(), b'synthetic-partial-result')
            with patch.object(probe.native, 'write_new', side_effect=OSError('synthetic-preservation-failure')):
                with self.assertRaises(OSError): lane.capture_setter()
            self.assertEqual(lane.commands.preservation, dict(failures=1,
                errors=[dict(operation='host-report-output', error_type='OSError')]))

    def test_host_origin_fixture_is_exclusive_and_binary_sources_and_fixture_bind_retirement(self):
        with TemporaryDirectory() as raw:
            resources = Path(raw).resolve()
            root = resources / 'host-fixtures'
            root.mkdir(mode=0o700)
            probe.native.write_new(root / 'probe.bin', probe.SETTER_PAYLOAD)
            with self.assertRaises(FileExistsError): probe.native.write_new(root / 'probe.bin', b'cannot-overwrite')
            for name in (*probe.SETTER_INPUTS, probe.SETTER_IMAGE):
                probe.native.write_new(resources / name, b'synthetic-owned-input')
            for changed in (probe.SETTER_IMAGE, 'HostSetterProbe.m', 'host-fixtures/probe.bin'):
                inputs = [dict(name=name, **probe.common.host_file_binding(resources / name)) for name in probe.SETTER_INPUTS]
                image = probe.common.host_file_binding(resources / probe.SETTER_IMAGE)
                selected = dict(root=probe.common.owned_directory(root), identity=probe.setter_identity(root / 'probe.bin'))
                prior = dict(host_setter_fixture=selected, host_setter_inputs_before_compile=inputs,
                    host_setter_inputs_after_set=copy.deepcopy(inputs), host_setter_built_image=dict(file=image), host_setter_image_after_set=image)
                self.assertEqual(probe.setter_retirement(resources, prior), dict(inputs=inputs, image=image, fixture=selected))
                (resources / changed).write_bytes(b'changed-after-collection')
                with self.subTest(changed=changed), self.assertRaises(RuntimeError): probe.setter_retirement(resources, prior)


if __name__ == '__main__':
    unittest.main()
