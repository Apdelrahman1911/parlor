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


class HostProbeTests(unittest.TestCase):
    def test_controls_cli_and_selection_are_disjoint_before_native_work(self):
        manifest = probe.controls()
        self.assertEqual([row['path'] for row in manifest['files']], sorted(set(probe.CONTROL_PATHS)))
        self.assertTrue(set(probe.common.CONTROL_PATHS) <= set(probe.CONTROL_PATHS))
        for name in ('run_host_probe.py', 'test_host_probe.py', 'README.md', 'HostImageProbe.m.in', 'image_hook.py', 'test_image_hook.py'):
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


if __name__ == '__main__':
    unittest.main()
