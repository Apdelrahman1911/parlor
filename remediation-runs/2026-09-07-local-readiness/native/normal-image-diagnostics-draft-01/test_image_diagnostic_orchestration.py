"""Mocked command/lifetime orchestration only; never native runtime evidence."""
from contextlib import contextmanager, ExitStack
import hashlib
import inspect
import json
from pathlib import Path
import subprocess
import tempfile
import textwrap
import types
import unittest
from unittest.mock import Mock, patch

import external_image_provenance as provenance
import run_normal_ios_launch as runner
import test_normal_launch_controls as runner_fixtures
from test_normal_launch_controls import PID, Record


class ImageDiagnosticOrchestrationTests(unittest.TestCase):
    def setUp(self):
        allocation = tempfile.TemporaryDirectory(prefix='parlor-image-diagnostics-fixture-')
        self.addCleanup(allocation.cleanup)
        self.base = Path(allocation.name).resolve()
        self.ordinal = 0

    def fixture(self):
        self.ordinal += 1
        self.root = self.base / str(self.ordinal)
        self.root.mkdir()
        lane = runner_fixtures.RunnerGuards.lane(self)
        lane.name, lane.baseline, lane.save = 'synthetic-image-observation', {}, Mock()
        app = lane.temporary / 'Parlor.app'
        artifacts = {}
        for index, (relative, kind) in enumerate((('Parlor', 'launcher'), ('Parlor.debug.dylib', 'debug-dylib'),
                                                 ('Frameworks/ComposeApp.framework/ComposeApp', 'compose-framework'))):
            path = app / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            data = ('synthetic-owned-bytes-' + kind).encode()
            path.write_bytes(data)
            artifacts[str(path)] = dict(origin='installed-app', relative_path=relative,
                sha256=hashlib.sha256(data).hexdigest(), bytes=len(data),
                uuid=f'{index + 1:08x}-2222-3333-4444-555555555555', kind=kind)
        executable = app / 'Parlor'
        lane.artifact_inventory = Mock(return_value=(executable, artifacts))
        record = Record(command=str(executable))
        backend = Mock()
        backend.read.return_value = record
        head = f'Process: Parlor [{PID}]\nPath: {executable}\n'
        raw_sample = head + '\nPRIVATE_STACK_SENTINEL\n\nBinary Images:\n' + ''.join(
            f'0x{(i+1)*0x100000:x} - 0x{(i+1)*0x100000+0x3fff:x} +image (1) <{row["uuid"]}> {path}\n'
            for i, (path, row) in enumerate(artifacts.items()))
        raw_vmmap = head + ''.join(
            f'__TEXT {(i+1)*0x100000:x}-{(i+1)*0x100000+0x2000:x} [8K] r-x/r-x SM=COW {path}\n'
            for i, path in enumerate(artifacts))
        context = dict(lane=lane, executable=executable, artifacts=artifacts, record=record, backend=backend,
                       raw_sample=raw_sample, raw_vmmap=raw_vmmap, commands=[], sample_error=None, vmmap_error=None)

        def require(arguments, filename, **options):
            context['commands'].append((list(map(str, arguments)), filename, dict(options)))
            if list(arguments[:3]) == ['xcrun', 'simctl', 'launch']:
                (lane.destination / filename).write_text(f'{provenance.APP_ID}: {PID}\n')
            elif arguments[0] == '/usr/bin/sample':
                self.assertEqual(45, options['timeout'])
                self.assertEqual(65536, options['limit'])
                Path(arguments[-1]).write_text(context['raw_sample'])
                if context['sample_error'] is not None:
                    raise context['sample_error']
            elif arguments[0] == '/usr/bin/vmmap':
                self.assertEqual(['/usr/bin/vmmap', '-w', str(PID)], arguments)
                self.assertEqual(45, options['timeout'])
                self.assertEqual(16 * 1024 * 1024, options['limit'])
                options['output'].write_text(context['raw_vmmap'])
                if context['vmmap_error'] is not None:
                    raise context['vmmap_error']
            else:
                self.fail('Unexpected native command in a pure synthetic fixture')
        lane.require = Mock(side_effect=require)
        return context

    @contextmanager
    def mocked_native(self, context):
        with ExitStack() as stack:
            stack.enter_context(patch.object(runner, 'darwin_backend', return_value=context['backend']))
            stack.enter_context(patch.object(runner, 'owned_tool_paths', return_value=None))
            stack.enter_context(patch.object(runner.time, 'time', side_effect=[1000.0, 1001.0]))
            stack.enter_context(patch.object(runner.time, 'sleep'))
            stack.enter_context(patch.object(runner.subprocess, 'Popen', side_effect=AssertionError('No native process allowed')))
            stack.enter_context(patch.object(runner.subprocess, 'run', side_effect=AssertionError('No native process allowed')))
            yield

    def assert_original(self, action, original):
        try:
            action()
        except BaseException as observed:
            self.assertIs(original, observed)
        else:
            self.fail('A failed sample became successful')

    def assert_raw_removed(self, lane):
        for name in ('owned-app.sample.txt', 'owned-app.vmmap.txt'):
            self.assertFalse((lane.temporary / name).exists())
        self.assertTrue(lane.receipt['raw_external_stack_mapping_files_removed'])

    def vmmap_commands(self, context):
        return [row for row in context['commands'] if row[0][0] == '/usr/bin/vmmap']

    def test_original_real_unbound_sample_rejection_gathers_sibling_diagnostics_not_provenance(self):
        context = self.fixture(); lane = context['lane']
        owned = str(context['executable'].parent / 'Frameworks/ComposeApp.framework/ComposeApp')
        context['raw_sample'] = context['raw_sample'].replace(owned, '/UNBOUND_PRIVATE_ORIGIN/ComposeApp')
        with self.mocked_native(context), patch.object(runner, 'bind_vmmap') as bind:
            with self.assertRaisesRegex(RuntimeError, '^Runtime contains an unbound application/framework image$'):
                lane.observe_external_images()
        bind.assert_not_called()
        self.assertEqual('FAIL', lane.receipt['provenance_status'])
        self.assertTrue(lane.receipt['sample_command_lifetime_verified'])
        event = lane.receipt['failure_only_vmmap_after_sample']
        self.assertEqual('COLLECTED_FAILURE_ONLY_NOT_PROVENANCE', event['status'])
        self.assertTrue(event['lifetime_before_verified'])
        self.assertTrue(event['lifetime_after_verified'])
        self.assertFalse(event['sample_selection_available'])
        self.assertFalse(event['proves_provenance'])
        self.assertEqual(1, len(self.vmmap_commands(context)))
        self.assertFalse((lane.destination / 'separate-external-image-provenance.json').exists())
        for tool in ('sample', 'vmmap'):
            record = lane.destination / (tool + '-images-failure.json')
            self.assertEqual(runner.digest(record), lane.receipt[tool + '_images_failure_sha256'])
            result = json.loads(record.read_text())
            self.assertFalse(result['proves_provenance'])
            self.assertFalse(result['sample_selection_available'])
            self.assertNotIn('PRIVATE_STACK_SENTINEL', record.read_text())
            self.assertNotIn('UNBOUND_PRIVATE_ORIGIN', record.read_text())
        self.assert_raw_removed(lane)

    def test_successful_sample_and_vmmap_have_unchanged_provenance_and_no_diagnostics(self):
        context = self.fixture(); lane = context['lane']
        expected_selected = provenance.parse_sample(context['raw_sample'], PID, context['executable'], context['artifacts'])
        expected = provenance.bind_vmmap(context['raw_vmmap'], PID, context['executable'], expected_selected)
        with self.mocked_native(context), patch.object(runner, 'image_diagnostic') as collect:
            lane.observe_external_images()
        collect.assert_not_called()
        result = json.loads((lane.destination / 'separate-external-image-provenance.json').read_text())
        for key, value in expected.items(): self.assertEqual(value, result[key])
        self.assertEqual('PASS', lane.receipt['provenance_status'])
        self.assertNotIn('failure_only_vmmap_after_sample', lane.receipt)
        self.assertEqual(1, len(self.vmmap_commands(context)))
        self.assertEqual([], list(lane.destination.glob('*-failure.json')))
        self.assert_raw_removed(lane)

    def test_successful_sample_vmmap_rejection_has_successful_sample_interval_reference(self):
        context = self.fixture(); lane = context['lane']
        context['raw_vmmap'] = context['raw_vmmap'].replace('300000-302000', '301000-303000')
        with self.mocked_native(context), self.assertRaisesRegex(RuntimeError, 'not the same sampled image mapping'):
            lane.observe_external_images()
        result = json.loads((lane.destination / 'vmmap-images-failure.json').read_text())
        self.assertTrue(result['sample_selection_available'])
        self.assertFalse(result['proves_provenance'])
        self.assertFalse(result['candidates'][-1]['successful_sample_interval_relations'][0]['same_start'])
        self.assertNotIn('failure_only_vmmap_after_sample', lane.receipt)
        self.assertNotEqual('PASS', lane.receipt['provenance_status'])
        self.assert_raw_removed(lane)

    def test_successful_sample_command_is_required_before_any_sibling_invocation(self):
        for error in (RuntimeError('sample command failed'), subprocess.TimeoutExpired('synthetic', 45), KeyboardInterrupt()):
            context = self.fixture(); lane = context['lane']; context['sample_error'] = error
            with self.mocked_native(context): self.assert_original(lane.observe_external_images, error)
            self.assertEqual([], self.vmmap_commands(context))
            self.assertNotIn('failure_only_vmmap_after_sample', lane.receipt)
            self.assert_raw_removed(lane)

    def test_sample_parser_cancellation_never_becomes_optional_diagnostic_work(self):
        context = self.fixture(); lane = context['lane']; original = KeyboardInterrupt()
        with self.mocked_native(context), patch.object(runner, 'parse_sample', side_effect=original):
            self.assert_original(lane.observe_external_images, original)
        self.assertEqual([], self.vmmap_commands(context))
        self.assertNotIn('failure_only_vmmap_after_sample', lane.receipt)
        self.assert_raw_removed(lane)

    def test_post_sample_changed_lifetime_never_reaches_parser_or_vmmap(self):
        context = self.fixture(); lane = context['lane']
        context['backend'].read.side_effect = [context['record']] * 3 + [None]
        with self.mocked_native(context), patch.object(runner, 'parse_sample') as parse:
            with self.assertRaisesRegex(RuntimeError, 'changed generation'): lane.observe_external_images()
        parse.assert_not_called()
        self.assertEqual([], self.vmmap_commands(context))
        self.assertNotIn('sample_command_lifetime_verified', lane.receipt)
        self.assert_raw_removed(lane)

    def test_failure_only_preflight_detects_exit_reuse_and_exec_before_invocation(self):
        for changed in (None, Record(started=(1001, 0)), Record(command='/UNRELATED_PROCESS'),
                        Record(token=(0, 0, 0, 0, 0, PID, 0, 2))):
            context = self.fixture(); lane = context['lane']; original = RuntimeError('original sample rejection')
            context['backend'].read.side_effect = [context['record']] * 4 + [changed]
            with self.mocked_native(context), patch.object(runner, 'parse_sample', side_effect=original):
                self.assert_original(lane.observe_external_images, original)
            event = lane.receipt['failure_only_vmmap_after_sample']
            self.assertEqual('NOT_RUN_PREFLIGHT_FAILED', event['status'])
            self.assertFalse(event['command_attempted'])
            self.assertFalse(event['lifetime_before_verified'])
            self.assertEqual([], self.vmmap_commands(context))
            self.assertNotIn('UNRELATED_PROCESS', json.dumps(lane.receipt))
            self.assert_raw_removed(lane)

    def test_failure_only_postflight_failure_preserves_primary_and_does_not_collect_unattested_maps(self):
        context = self.fixture(); lane = context['lane']; original = RuntimeError('original sample rejection')
        context['backend'].read.side_effect = [context['record']] * 5 + [None]
        with self.mocked_native(context), patch.object(runner, 'parse_sample', side_effect=original):
            self.assert_original(lane.observe_external_images, original)
        event = lane.receipt['failure_only_vmmap_after_sample']
        self.assertTrue(event['command_attempted'])
        self.assertTrue(event['command_succeeded'])
        self.assertTrue(event['lifetime_before_verified'])
        self.assertFalse(event['lifetime_after_verified'])
        self.assertEqual('FAILED_COMMAND_OR_LIFETIME', event['status'])
        self.assertNotIn('vmmap_images_failure_sha256', lane.receipt)
        self.assert_raw_removed(lane)

    def test_failure_only_command_error_timeout_and_interruption_keep_primary_and_postflight(self):
        for failure in (RuntimeError('PRIVATE_COMMAND_ERROR'), subprocess.TimeoutExpired('synthetic', 45), KeyboardInterrupt()):
            context = self.fixture(); lane = context['lane']; original = RuntimeError('original sample rejection')
            context['vmmap_error'] = failure
            with self.mocked_native(context), patch.object(runner, 'parse_sample', side_effect=original):
                self.assert_original(lane.observe_external_images, original)
            event = lane.receipt['failure_only_vmmap_after_sample']
            self.assertEqual('FAILED_COMMAND_OR_LIFETIME', event['status'])
            self.assertTrue(event['lifetime_before_verified'])
            self.assertTrue(event['lifetime_after_verified'])
            self.assertFalse(event['command_succeeded'])
            self.assertEqual(type(failure).__name__, event['command_error_type'])
            self.assertNotIn('PRIVATE_COMMAND_ERROR', json.dumps(lane.receipt))
            self.assert_raw_removed(lane)

    def test_failure_only_command_and_postflight_errors_are_both_recorded(self):
        context = self.fixture(); lane = context['lane']; original = RuntimeError('original sample rejection')
        context['vmmap_error'] = RuntimeError('PRIVATE_COMMAND_ERROR')
        context['backend'].read.side_effect = [context['record']] * 5 + [None]
        with self.mocked_native(context), patch.object(runner, 'parse_sample', side_effect=original):
            self.assert_original(lane.observe_external_images, original)
        event = lane.receipt['failure_only_vmmap_after_sample']
        self.assertEqual('RuntimeError', event['command_error_type'])
        self.assertEqual('RuntimeError', event['postflight_error_type'])
        self.assert_raw_removed(lane)

    def test_diagnostic_collection_failure_preserves_primary_without_serializing_exception_text(self):
        context = self.fixture(); lane = context['lane']; original = RuntimeError('original sample rejection')
        with self.mocked_native(context), patch.object(runner, 'parse_sample', side_effect=original), \
             patch.object(runner, 'image_diagnostic', side_effect=ValueError('PRIVATE_DIAGNOSTIC_ERROR')):
            self.assert_original(lane.observe_external_images, original)
        self.assertEqual('ValueError', lane.receipt['sample_images_diagnostic_error'])
        self.assertEqual('ValueError', lane.receipt['vmmap_images_diagnostic_error'])
        self.assertEqual('FAILED_DIAGNOSTIC_COLLECTION', lane.receipt['failure_only_vmmap_after_sample']['status'])
        self.assertNotIn('PRIVATE_DIAGNOSTIC_ERROR', json.dumps(lane.receipt))
        self.assert_raw_removed(lane)

    def test_diagnostic_write_failure_keeps_original_exception_object_and_no_provenance(self):
        context = self.fixture(); lane = context['lane']; original = RuntimeError('original sample rejection')
        with self.mocked_native(context), patch.object(runner, 'parse_sample', side_effect=original), \
             patch.object(runner, 'write_json', side_effect=OSError('PRIVATE_SERIALIZATION_ERROR')):
            self.assert_original(lane.observe_external_images, original)
        for tool in ('sample', 'vmmap'):
            self.assertEqual('OSError', lane.receipt[tool + '_header_diagnostic_error'])
            self.assertEqual('OSError', lane.receipt[tool + '_images_diagnostic_error'])
        self.assertNotIn('PRIVATE_SERIALIZATION_ERROR', json.dumps(lane.receipt))
        self.assertNotIn('provenance_sha256', lane.receipt)
        self.assert_raw_removed(lane)

    def test_interrupting_sample_diagnostics_preserves_primary_and_prevents_vmmap(self):
        for producer in ('image_diagnostic', 'write_json'):
            context = self.fixture(); lane = context['lane']; original = RuntimeError('original sample rejection')
            with self.mocked_native(context), patch.object(runner, 'parse_sample', side_effect=original), \
                 patch.object(runner, producer, side_effect=KeyboardInterrupt()):
                self.assert_original(lane.observe_external_images, original)
            self.assertTrue(lane.receipt['sample_failure_diagnostics_interrupted'])
            self.assertEqual('KeyboardInterrupt', lane.receipt['sample_failure_diagnostics_interrupt_type'])
            self.assertEqual('NOT_RUN_PRIOR_DIAGNOSTIC_INTERRUPTION', lane.receipt['failure_only_vmmap_after_sample']['status'])
            self.assertEqual([], self.vmmap_commands(context))
            self.assert_raw_removed(lane)

    def test_interrupting_vmmap_diagnostics_preserves_primary_and_cleanup(self):
        context = self.fixture(); lane = context['lane']; original = RuntimeError('original sample rejection')
        collect = runner.image_diagnostic
        def interrupted(raw, tool, *arguments, **options):
            if tool == 'vmmap': raise KeyboardInterrupt()
            return collect(raw, tool, *arguments, **options)
        with self.mocked_native(context), patch.object(runner, 'parse_sample', side_effect=original), \
             patch.object(runner, 'image_diagnostic', side_effect=interrupted):
            self.assert_original(lane.observe_external_images, original)
        event = lane.receipt['failure_only_vmmap_after_sample']
        self.assertEqual('FAILED_DIAGNOSTIC_COLLECTION', event['status'])
        self.assertEqual('KeyboardInterrupt', event['diagnostic_error_type'])
        self.assertEqual(1, len(self.vmmap_commands(context)))
        self.assert_raw_removed(lane)

    def test_failure_only_empty_output_is_not_collected_or_credited(self):
        context = self.fixture(); lane = context['lane']; original = RuntimeError('original sample rejection')
        context['raw_vmmap'] = ''
        with self.mocked_native(context), patch.object(runner, 'parse_sample', side_effect=original):
            self.assert_original(lane.observe_external_images, original)
        self.assertEqual('FAILED_DIAGNOSTIC_COLLECTION', lane.receipt['failure_only_vmmap_after_sample']['status'])
        self.assertNotIn('vmmap_images_failure_sha256', lane.receipt)
        self.assert_raw_removed(lane)

    def test_overbudget_watch_does_not_start_sibling_after_sample_failure(self):
        context = self.fixture(); lane = context['lane']; original = RuntimeError('original sample rejection')
        collect = lane.collect_external_failure_diagnostics
        def fill_after_diagnostics(*arguments, **options):
            collect(*arguments, **options)
            path = lane.temporary / 'owned-app.stdout'
            path.write_bytes(b'12345')
            lane.watched_outputs = [(path, 4)]
        lane.collect_external_failure_diagnostics = Mock(side_effect=fill_after_diagnostics)
        with self.mocked_native(context), patch.object(runner, 'parse_sample', side_effect=original):
            self.assert_original(lane.observe_external_images, original)
        self.assertEqual('NOT_RUN_PREFLIGHT_FAILED', lane.receipt['failure_only_vmmap_after_sample']['status'])
        self.assertEqual([], self.vmmap_commands(context))
        self.assert_raw_removed(lane)

    def test_run_keeps_original_failure_and_all_finalization_even_when_diagnostics_succeed(self):
        context = self.fixture(); lane = context['lane']; original = RuntimeError('original sample rejection')
        lane.prepare, lane.run_xctest = Mock(), Mock()
        with self.mocked_native(context), patch.object(runner, 'parse_sample', side_effect=original), \
             patch.object(runner, 'normalized_identity', return_value={'frozen': True}), \
             patch.object(runner, 'control_hash', return_value=lane.approved), patch('builtins.print'):
            self.assertEqual(1, lane.run())
        self.assertEqual('FAIL', lane.receipt['status'])
        self.assertEqual('FAIL', lane.receipt['provenance_status'])
        self.assertEqual('PASS', lane.receipt['runtime_evidence_status'])
        self.assertEqual('PASS', lane.receipt['notice_package_status'])
        self.assertEqual('PASS', lane.receipt['cleanup_status'])
        self.assertEqual({'type': 'RuntimeError', 'message': 'original sample rejection'}, lane.receipt['error'])
        lane.stop_gradle.assert_called_once_with('stop-final')
        lane.shutdown_device.assert_called_once()
        lane.delete_device.assert_called_once()
        lane.owner.stop.assert_called_once()
        lane.owner.secondary.cleanup.assert_called_once()
        self.assertFalse(lane.temporary.exists())
        self.assertEqual([], lane.watched_outputs)
        lane.save.assert_called_once()

    def test_original_exception_contract_kills_removing_post_diagnostic_reraise(self):
        context = self.fixture(); lane = context['lane']; original = RuntimeError('original sample rejection')
        source = textwrap.dedent(inspect.getsource(runner.Lane.observe_external_images))
        statement = 'raise  # Preserve the original sample exception, including its identity.'
        self.assertEqual(1, source.count(statement))
        with self.mocked_native(context), patch.object(runner, 'parse_sample', side_effect=original):
            namespace = dict(vars(runner))  # Only already-mocked native entry points.
            exec(compile(source.replace(statement, 'pass  # Synthetic removed failure propagation'), '<synthetic-failure-mutation>', 'exec'), namespace)
            mutated = types.MethodType(namespace['observe_external_images'], lane)
            with self.assertRaises(AssertionError): self.assert_original(mutated, original)
        self.assert_raw_removed(lane)


if __name__ == '__main__':
    unittest.main()
