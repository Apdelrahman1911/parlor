"""Synthetic otool text and mocked real Lane command paths; no native execution.

The new independent file-geometry preflight is mocked only in these failure-only
handler controls. test_image_text_extent/native/kernel controls exercise that
preflight; these retain the original failure, cancellation and cleanup oracles.
"""
from contextlib import contextmanager, nullcontext
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

import image_text_layout_diagnostic as diagnostic
import run_normal_ios_launch as runner

START, END, REGION_SIZE = 4398891008, 4441608992, 42729472
IMAGE_UUID = '72b87ad3-9d33-3644-b607-48c95f9b1b40'


def failure_row():
    return dict(schema_version=1, status='FAIL', reason='not-selected-executable-region', native_error=0,
        region_diagnostic=dict(requested_start=START, requested_end_exclusive=END,
            pri_address=START, pri_size=REGION_SIZE, pri_offset=0, pri_protection=5,
            pri_max_protection=7, pri_flags=0, native_structure_bytes=1272,
            native_return_bytes=1272, query_errno=0))


def native_text(path, uuid=IMAGE_UUID):
    return (f'{path}:\nLoad command 0\n      cmd LC_SEGMENT_64\n  cmdsize 72\n'
        '  segname __TEXT\n   vmaddr 0x0000000000000000\n   vmsize 0x00000000028bd320\n'
        '  fileoff 0\n filesize 42717984\n  maxprot 0x00000007\n initprot 0x00000005\n'
        '   nsects 0\n    flags 0x0\nLoad command 1\n      cmd LC_UUID\n  cmdsize 24\n'
        f'     uuid {uuid.upper()}\n').encode()


class ImageTextLayoutDiagnosticTests(unittest.TestCase):
    def setUp(self):
        allocation = tempfile.TemporaryDirectory(prefix='parlor-layout-diagnostic-control-')
        self.addCleanup(allocation.cleanup)
        self.base, self.ordinal = Path(allocation.name).resolve(), 0

    def context(self):
        self.ordinal += 1
        root = self.base / str(self.ordinal); root.mkdir()
        lane = runner.Lane.__new__(runner.Lane)
        lane.temporary, lane.destination = root / 'owned', root / 'evidence'
        lane.temporary.mkdir(); lane.destination.mkdir()
        lane.environment, lane.watched_outputs = {}, []
        lane.save = Mock()
        lane.owner = SimpleNamespace(register=Mock(), refresh=Mock(), stop=Mock())
        lane.receipt = dict(commands=[], source_before=dict(commit='c' * 40, tree='d' * 40,
            source_manifest_sha256='e' * 64), approved_control_sha256='f' * 64, provenance_status='FAIL')
        image = lane.temporary / 'Parlor.app/Frameworks/ComposeApp.framework/ComposeApp'
        image.parent.mkdir(parents=True); image.write_bytes(b'synthetic-private-image-bytes' * 32)
        helper = lane.temporary / 'kernel-image-region'; helper.write_bytes(b'synthetic-region-helper')
        lane.receipt['kernel_region_helper_sha256'] = runner.digest(helper)
        selected = dict(origin='installed-app', relative_path='Frameworks/ComposeApp.framework/ComposeApp',
            kind='compose-framework', bytes=image.stat().st_size, sha256=runner.digest(image), uuid=IMAGE_UUID,
            observed_tool_uuid=IMAGE_UUID, sample_start=START, sample_end_inclusive=END - 1)
        context = SimpleNamespace(lane=lane, image=image, helper=helper, selected=selected,
            failure=failure_row(), native=native_text(image), primary=None, native_calls=0,
            kernel_calls=0, checks=0, unsafe_at=None, kernel_error=None, native_error=None,
            kernel_exit=1, native_exit=0, native_child=None, native_output_stat=None,
            after_kernel=None, after_native=None, page_size=16384)
        original = lane.require
        def require(arguments, *args, **kwargs):
            try:
                return original(arguments, *args, **kwargs)
            except BaseException as error:
                if str(arguments[0]) == str(helper):
                    context.primary = error
                raise
        lane.require = require
        return context

    @contextmanager
    def mocked_native(self, context):
        def child(arguments, *, stdout, **_options):
            if arguments[0] == str(context.helper):
                context.kernel_calls += 1
                if context.kernel_error is not None:
                    raise context.kernel_error
                stdout.write((json.dumps(context.failure) + '\n').encode()); stdout.flush()
                if context.after_kernel is not None:
                    context.after_kernel()
                code = context.kernel_exit
            else:
                self.assertEqual(['xcrun', 'otool', '-arch', 'arm64', '-l', str(context.image)], arguments)
                context.native_calls += 1
                context.native_output_stat = runner.os.fstat(stdout.fileno())
                if context.native_error is not None:
                    raise context.native_error
                stdout.write(context.native); stdout.flush()
                if context.after_native is not None:
                    context.after_native()
                if context.native_child is not None:
                    return context.native_child
                code = context.native_exit
            return SimpleNamespace(pid=51001, returncode=code, poll=lambda: code)
        def unchanged(_before, _after):
            context.checks += 1
            if context.unsafe_at == context.checks:
                raise RuntimeError('synthetic unsafe target')
            return dict(safe=True)
        with patch.object(runner.subprocess, 'Popen', side_effect=child), \
             patch.object(runner, 'defer_parent_signals', side_effect=nullcontext), \
             patch.object(runner.image_text_extent, 'inspect_artifact', return_value={'failure-path-fixture': True}), \
             patch.object(runner, 'unchanged_target', side_effect=unchanged), \
             patch.object(runner.os, 'sysconf', return_value=context.page_size):
            yield

    def invoke(self, context):
        with self.mocked_native(context), self.assertRaises(BaseException) as raised:
            context.lane.observe_kernel_regions(context.helper, object(), Mock(), 82598,
                                               {str(context.image): context.selected})
        self.assertIs(context.primary, raised.exception)
        self.assertNotEqual('PASS', context.lane.receipt['provenance_status'])
        self.assertFalse((context.lane.destination / 'separate-external-image-provenance.json').exists())
        return raised.exception

    def result_path(self, context):
        return context.lane.destination / 'kernel-region-01-layout.json'

    def assert_no_result(self, context):
        self.assertFalse(self.result_path(context).exists())
        self.assertNotIn('kernel_region_layout_failure', context.lane.receipt)

    def test_exact_closed_layout_uuid_host_page_size_and_no_interval_normalization(self):
        context = self.context()
        value = diagnostic.make_diagnostic(context.native, context.image, context.selected, context.failure, 16384)
        self.assertEqual('OBSERVED_NOT_PROVENANCE', value['status'])
        self.assertFalse(value['proves_provenance'])
        self.assertEqual(END - START, value['native_layout']['text_segment']['vmsize'])
        self.assertNotEqual(REGION_SIZE, value['native_layout']['text_segment']['vmsize'])
        self.assertEqual(IMAGE_UUID, value['native_layout']['uuid'])
        self.assertEqual(dict(value=16384, api='os.sysconf(SC_PAGE_SIZE)', target_page_size_proven=False),
                         value['host_page_size'])
        self.assertEqual(context.failure['region_diagnostic'], value['original_region_diagnostic'])
        self.assertNotIn(str(context.image), json.dumps(value))
        self.assertNotIn('synthetic-private-image', json.dumps(value))

    def test_native_fields_retain_extremes_and_unknown_protection_without_acceptance(self):
        context = self.context()
        raw = context.native.replace(b'0x00000000028bd320', b'0xffffffffffffffff').replace(
            b'maxprot 0x00000007', b'maxprot 0xffffffff').replace(b'initprot 0x00000005', b'initprot 0x0')
        layout = diagnostic.parse_text_layout(raw, context.image, context.selected)
        self.assertEqual(2**64 - 1, layout['text_segment']['vmsize'])
        self.assertEqual(2**32 - 1, layout['text_segment']['maxprot'])
        self.assertEqual(0, layout['text_segment']['initprot'])

    def test_only_completed_sole_span_failure_is_eligible(self):
        context = self.context()
        mutations = dict(requested_start=START + 1, requested_end_exclusive=END + 1,
            pri_address=START + 4096, pri_size=END - START, pri_offset=1, pri_protection=7,
            pri_max_protection=8, pri_flags=1, native_structure_bytes=0, native_return_bytes=1271, query_errno=1)
        for key, value in mutations.items():
            candidate = copy.deepcopy(context.failure); candidate['region_diagnostic'][key] = value
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                diagnostic.span_failure(candidate, context.selected)
        for key in diagnostic.REGION_FIELDS:
            candidate = copy.deepcopy(context.failure); candidate['region_diagnostic'][key] = True
            with self.subTest(boolean=key), self.assertRaises(RuntimeError):
                diagnostic.span_failure(candidate, context.selected)
        for key, value in [('status', 'PASS'), ('native_error', True), ('schema_version', True), ('extra', 0)]:
            candidate = copy.deepcopy(context.failure); candidate[key] = value
            with self.subTest(outer=key), self.assertRaises(RuntimeError):
                diagnostic.span_failure(candidate, context.selected)

    def test_otool_framing_duplicates_missing_fields_and_numeric_overflow_rejected(self):
        context = self.context()
        mutations = [context.native.replace(str(context.image).encode(), b'/private/other-image'),
            context.native.replace(b'Load command 1', b'Load command 2'),
            context.native.replace(b'LC_UUID', b'LC_SOURCE_VERSION'),
            context.native.replace(b'__TEXT', b'__DATA'),
            context.native.replace(b'vmaddr 0x0000000000000000', b'vmaddr -1'),
            context.native.replace(b'0x00000000028bd320', b'0x10000000000000000'),
            context.native.replace(b'maxprot 0x00000007', b'maxprot 0x100000000'),
            context.native.replace(b'nsects 0', b'nsects 1'),
            context.native.replace(b'cmdsize 24', b'cmdsize 25'),
            context.native + context.native.split(b'Load command 1', 1)[1].join([b'Load command 2', b'']),
            context.native.replace(b'filesize 42717984', b'fileoff 42717984'), b'\0' + context.native,
            b'x' * (diagnostic.MAX_OUTPUT_BYTES + 1)]
        for raw in mutations:
            with self.subTest(raw=hashlib.sha256(raw).hexdigest()), self.assertRaises(RuntimeError):
                diagnostic.parse_text_layout(raw, context.image, context.selected)
        wrong = dict(context.selected, uuid='11111111-2222-3333-4444-555555555555')
        with self.assertRaises(RuntimeError): diagnostic.parse_text_layout(context.native, context.image, wrong)

    def test_section_fields_and_unrelated_commands_cannot_substitute_for_segment(self):
        context = self.context()
        raw = context.native.replace(b'cmdsize 72', b'cmdsize 152').replace(b'nsects 0', b'nsects 1')
        raw = raw.replace(b'Load command 1', b'Section\n sectname __text\n segname __TEXT\n'
            b' addr 0x100\n size 0x200\nLoad command 1')
        self.assertEqual(END - START, diagnostic.parse_text_layout(raw, context.image, context.selected)['text_segment']['vmsize'])
        with self.assertRaises(RuntimeError):
            diagnostic.parse_text_layout(raw.replace(b'segname __TEXT', b'segname __DATA', 1), context.image, context.selected)

    def test_descriptor_file_hash_and_no_follow_bounds(self):
        context = self.context()
        identity = diagnostic.artifact_fingerprint(context.image, context.selected)
        self.assertEqual(context.selected['bytes'], identity[2])
        wrong = dict(context.selected, sha256='0' * 64)
        with self.assertRaises(RuntimeError): diagnostic.artifact_fingerprint(context.image, wrong)
        link = context.lane.temporary / 'image-link'; link.symlink_to(context.image)
        with self.assertRaises(RuntimeError): diagnostic.artifact_fingerprint(link, context.selected)
        with self.assertRaises(RuntimeError): diagnostic.owned_bytes(context.image, context.selected['bytes'] - 1)
        with self.assertRaises(RuntimeError): diagnostic.owned_bytes(context.image.parent, 4096)

    def test_real_command_failure_collects_one_bound_diagnostic_and_rethrows_original(self):
        context = self.context(); error = self.invoke(context)
        self.assertEqual('Required owned command failed; consult its retained receipt', str(error))
        self.assertEqual((1, 1), (context.kernel_calls, context.native_calls))
        value = json.loads(self.result_path(context).read_text())
        for key in ('artifact_rehashed_before_and_after', 'helper_unchanged', 'lifetime_before_and_after_verified',
                    'original_failure_preserved'):
            self.assertIs(True, value[key])
        self.assertEqual(context.selected['sha256'], value['artifact']['sha256'])
        self.assertEqual(context.selected['bytes'], value['artifact']['bytes'])
        self.assertEqual(runner.digest(self.result_path(context)), context.lane.receipt['kernel_region_layout_failure']['sha256'])
        self.assertEqual(context.failure, json.loads((context.lane.destination / 'kernel-region-01.json').read_text()))
        self.assertTrue(context.lane.receipt['kernel_region_layout_raw_removed'])
        self.assertFalse((context.lane.temporary / 'kernel-region-01-layout.txt').exists())
        self.assertNotIn('output_file_identity', context.lane.receipt['commands'][0])
        command = context.lane.receipt['commands'][1]
        self.assertEqual('temporary-only/kernel-region-01-layout.txt', command['log'])
        opened = context.native_output_stat
        self.assertEqual(dict(device=opened.st_dev, inode=opened.st_ino, uid=opened.st_uid,
                              file_type=runner.stat.S_IFMT(opened.st_mode)), command['output_file_identity'])
        self.assertEqual(0, command['exit_code'])
        self.assertNotIn('command_cleanup_error', command)

    def test_kernel_timeout_cancellation_and_abnormal_exit_never_run_layout_tool(self):
        for failure in (subprocess.TimeoutExpired('synthetic', 10), KeyboardInterrupt(), RuntimeError('budget')):
            context = self.context(); context.kernel_error = failure
            self.invoke(context)
            self.assertEqual(0, context.native_calls); self.assert_no_result(context)
        context = self.context(); context.kernel_exit = 2
        self.invoke(context)
        self.assertEqual(0, context.native_calls); self.assert_no_result(context)

    def test_other_native_failure_and_incomplete_reply_do_not_trigger_layout_work(self):
        for change in ('wrong-start', 'incomplete', 'nonzero-error', 'unknown-field'):
            context = self.context()
            if change == 'wrong-start': context.failure['region_diagnostic']['pri_address'] += 4096
            elif change == 'incomplete': context.failure['region_diagnostic']['native_return_bytes'] = 1
            elif change == 'nonzero-error': context.failure['region_diagnostic']['query_errno'] = 5
            else: context.failure['private'] = 'unbound'
            self.invoke(context)
            self.assertEqual(0, context.native_calls); self.assert_no_result(context)

    def test_unsafe_target_before_diagnostic_does_no_extra_native_work(self):
        context = self.context(); context.unsafe_at = 3
        self.invoke(context)
        self.assertEqual(0, context.native_calls); self.assert_no_result(context)
        self.assertEqual('RuntimeError', context.lane.receipt['kernel_region_layout_diagnostic_error_type'])

    def test_changed_artifact_or_helper_before_tool_fails_fast(self):
        for which in ('image', 'helper'):
            context = self.context()
            context.after_kernel = lambda: getattr(context, which).write_bytes(b'changed')
            self.invoke(context)
            self.assertEqual(0, context.native_calls); self.assert_no_result(context)

    def test_host_page_size_must_be_measured_valid_not_guessed(self):
        for value in (None, True, 0, -1, 12345, 2**32):
            context = self.context(); context.page_size = value
            self.invoke(context)
            self.assertEqual(0, context.native_calls); self.assert_no_result(context)
        context = self.context(); context.page_size = 4096
        self.invoke(context)
        value = json.loads(self.result_path(context).read_text())
        self.assertEqual(4096, value['host_page_size']['value'])
        self.assertFalse(value['host_page_size']['target_page_size_proven'])

    def test_prelaunch_failures_retain_raw_but_completed_owned_failures_remove_it(self):
        for error in (KeyboardInterrupt(), subprocess.TimeoutExpired('synthetic', 10), RuntimeError('output-budget')):
            context = self.context(); context.native_error = error
            self.invoke(context); self.assert_no_result(context)
            self.assertEqual(1, context.native_calls)
            self.assertFalse(context.lane.receipt['kernel_region_layout_raw_removed'])
            self.assertTrue((context.lane.temporary / 'kernel-region-01-layout.txt').is_file())
            self.assertNotIn('exit_code', context.lane.receipt['commands'][1])
            self.assertEqual(type(error).__name__, context.lane.receipt['kernel_region_layout_diagnostic_error_type'])
        context = self.context(); context.native_exit = 1
        self.invoke(context); self.assert_no_result(context)
        self.assertTrue(context.lane.receipt['kernel_region_layout_raw_removed'])
        context = self.context(); context.native = b'X' * (diagnostic.MAX_OUTPUT_BYTES + 1)
        self.invoke(context); self.assert_no_result(context)
        self.assertTrue(context.lane.receipt['kernel_region_layout_raw_removed'])

    def test_postlaunch_timeout_stop_failure_retains_raw_for_outer_finalizer(self):
        for cleanup_error in (RuntimeError('synthetic stop failure'), KeyboardInterrupt()):
            context = self.context()
            context.native_child = SimpleNamespace(pid=51002, returncode=None, poll=Mock(return_value=None))
            context.lane.owner.stop.side_effect = cleanup_error
            with patch.object(runner.time, 'monotonic', side_effect=[0, 0, 11]), \
                 patch.object(diagnostic, 'artifact_fingerprint', wraps=diagnostic.artifact_fingerprint) as fingerprint:
                self.invoke(context)
            command = context.lane.receipt['commands'][1]
            self.assertEqual('TimeoutExpired', command['primary_error']['type'])
            self.assertEqual('stop-owned-command-workers', command['command_cleanup_error']['stage'])
            self.assertEqual(type(cleanup_error).__name__, command['command_cleanup_error']['type'])
            self.assertTrue(command['finished_at'])
            self.assertNotIn('exit_code', command)
            context.lane.owner.register.assert_any_call(context.native_child, 'command')
            context.lane.owner.stop.assert_called_once()
            context.native_child.poll.assert_called_once_with()
            self.assertEqual((1, 1, 3), (context.kernel_calls, context.native_calls, context.checks))
            self.assertEqual(1, fingerprint.call_count)
            self.assertEqual('TimeoutExpired', context.lane.receipt['kernel_region_layout_diagnostic_error_type'])
            self.assertFalse(context.lane.receipt['kernel_region_layout_raw_removed'])
            self.assertEqual(context.native, (context.lane.temporary / 'kernel-region-01-layout.txt').read_bytes())
            self.assert_no_result(context)
        # Even a resolved leader exit does not excuse a failed owned-worker stop.
        context = self.context(); context.native = b'X' * (diagnostic.MAX_OUTPUT_BYTES + 1)
        context.lane.owner.stop.side_effect = RuntimeError('synthetic stop failure after output budget')
        self.invoke(context)
        command = context.lane.receipt['commands'][1]
        self.assertEqual(0, command['exit_code'])
        self.assertEqual('RuntimeError', command['command_cleanup_error']['type'])
        self.assertFalse(context.lane.receipt['kernel_region_layout_raw_removed'])
        self.assertEqual(context.native, (context.lane.temporary / 'kernel-region-01-layout.txt').read_bytes())
        self.assert_no_result(context)

    def test_postlaunch_timeout_unresolved_exit_retains_raw_for_outer_finalizer(self):
        context = self.context()
        context.native_child = SimpleNamespace(pid=51002, returncode=None, poll=Mock(return_value=None))
        with patch.object(runner.time, 'monotonic', side_effect=[0, 0, 11]), \
             patch.object(diagnostic, 'artifact_fingerprint', wraps=diagnostic.artifact_fingerprint) as fingerprint:
            self.invoke(context)
        command = context.lane.receipt['commands'][1]
        self.assertEqual('TimeoutExpired', command['primary_error']['type'])
        self.assertTrue(command['finished_at'])
        self.assertIsNone(command['exit_code'])
        self.assertNotIn('command_cleanup_error', command)
        context.lane.owner.register.assert_any_call(context.native_child, 'command')
        context.lane.owner.stop.assert_called_once()
        self.assertEqual(2, context.native_child.poll.call_count)
        self.assertEqual((1, 1, 3), (context.kernel_calls, context.native_calls, context.checks))
        self.assertEqual(1, fingerprint.call_count)
        self.assertFalse(context.lane.receipt['kernel_region_layout_raw_removed'])
        self.assertEqual(context.native, (context.lane.temporary / 'kernel-region-01-layout.txt').read_bytes())
        self.assert_no_result(context)

    def test_postlaunch_timeout_reaped_child_removes_exact_owned_raw(self):
        context = self.context()
        context.native_child = SimpleNamespace(pid=51002, returncode=None)
        context.native_child.poll = Mock(side_effect=lambda: context.native_child.returncode)
        context.lane.owner.stop.side_effect = lambda _predicate: setattr(context.native_child, 'returncode', -15)
        with patch.object(runner.time, 'monotonic', side_effect=[0, 0, 11]):
            self.invoke(context)
        command = context.lane.receipt['commands'][1]
        self.assertEqual('TimeoutExpired', command['primary_error']['type'])
        self.assertEqual(-15, command['exit_code'])
        self.assertNotIn('command_cleanup_error', command)
        context.lane.owner.register.assert_any_call(context.native_child, 'command')
        context.lane.owner.stop.assert_called_once()
        self.assertEqual(2, context.native_child.poll.call_count)
        self.assertEqual((1, 1, 3), (context.kernel_calls, context.native_calls, context.checks))
        self.assertTrue(context.lane.receipt['kernel_region_layout_raw_removed'])
        self.assertFalse((context.lane.temporary / 'kernel-region-01-layout.txt').exists())
        self.assert_no_result(context)

    def test_same_uid_regular_replacement_is_neither_decoded_nor_unlinked(self):
        context = self.context()
        raw = context.lane.temporary / 'kernel-region-01-layout.txt'
        created = context.lane.temporary / 'created-layout-output.txt'
        def replace():
            raw.rename(created)
            raw.write_bytes(context.native)  # Valid text cannot substitute for the created output inode.
        context.after_native = replace
        with patch.object(diagnostic, 'make_diagnostic', wraps=diagnostic.make_diagnostic) as decode:
            self.invoke(context)
        command = context.lane.receipt['commands'][1]
        original, replacement = created.lstat(), raw.lstat()
        self.assertEqual(original.st_uid, replacement.st_uid)
        self.assertNotEqual(original.st_ino, replacement.st_ino)
        self.assertEqual(dict(device=original.st_dev, inode=original.st_ino, uid=original.st_uid,
                              file_type=runner.stat.S_IFMT(original.st_mode)), command['output_file_identity'])
        self.assertEqual(0, command['exit_code'])
        self.assertEqual(context.native, raw.read_bytes())
        self.assertEqual(context.native, created.read_bytes())
        self.assertFalse(context.lane.receipt['kernel_region_layout_raw_removed'])
        decode.assert_not_called()
        self.assert_no_result(context)

    def test_unsafe_target_after_tool_prevents_hashing_or_binding_further(self):
        context = self.context(); context.unsafe_at = 4
        with patch.object(diagnostic, 'artifact_fingerprint', wraps=diagnostic.artifact_fingerprint) as fingerprint:
            self.invoke(context)
        self.assertEqual(1, fingerprint.call_count)
        self.assertEqual(4, context.checks)
        self.assert_no_result(context)
        self.assertTrue(context.lane.receipt['kernel_region_layout_raw_removed'])

    def test_changed_artifact_helper_or_failure_record_after_tool_cannot_publish(self):
        for which in ('image', 'helper', 'failure'):
            context = self.context()
            def change():
                path = context.lane.destination / 'kernel-region-01.json' if which == 'failure' else getattr(context, which)
                path.write_bytes(b'changed')
            context.after_native = change
            self.invoke(context); self.assert_no_result(context)
            self.assertTrue(context.lane.receipt['kernel_region_layout_raw_removed'])

    def test_invalid_native_uuid_or_output_keeps_failure_not_diagnostic_pass(self):
        for raw in (b'unknown-native-format\n', native_text('/private/unowned-image'),
                    native_text('/irrelevant', '11111111-2222-3333-4444-555555555555')):
            context = self.context(); context.native = raw
            self.invoke(context); self.assert_no_result(context)
            self.assertTrue(context.lane.receipt['kernel_region_layout_raw_removed'])

    def test_stale_output_or_redirection_is_not_overwritten_or_deleted(self):
        for suffix in ('.txt', '.json'):
            context = self.context()
            parent = context.lane.temporary if suffix == '.txt' else context.lane.destination
            stale = parent / ('kernel-region-01-layout' + suffix); stale.write_bytes(b'stale-owned-boundary')
            self.invoke(context)
            self.assertEqual(0, context.native_calls)
            self.assertEqual(b'stale-owned-boundary', stale.read_bytes())

    def test_explicitly_reviewed_acceptance_pins_and_diagnostics_cannot_supply_pass(self):
        root = Path(__file__).parent
        pins = {'kernel_image_region.c.in': '1cdb0a7812c7bbb71879651d060a9993cf9acab7288e9b2e1568dc8466f27df0',
                'kernel_image_regions.py': '920d915789dcb3b1b3d94db66451637d089f977e52e9c2d4cac99b065e28e10f'}
        for name, expected in pins.items():
            self.assertEqual(expected, hashlib.sha256((root / name).read_bytes()).hexdigest())
        context = self.context()
        with patch.object(runner, 'bind_regions') as bind:
            self.invoke(context)
        bind.assert_not_called()


if __name__ == '__main__':
    unittest.main()
