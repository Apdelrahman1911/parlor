"""Closed-diagnostic parser/source contracts, not Kotlin compilation or iOS runtime.

Run only in the root-owned lane, in an isolated full-control copy with this draft
applied. All receipt fixtures are synthetic. No app/save payload is retained.
"""
import copy
from pathlib import Path
import re
import unittest
from unittest import mock

import l08_receipts as receipts
from test_l08_receipts import matrix
from test_l08_host_receipts import host_matrix
from test_native_readiness import TOKEN, framework_inventory

HERE = Path(__file__).resolve().parent
METADATA_STAGES = (
    'metadata-path', 'metadata-existence', 'metadata-directory-backup',
    'metadata-file-backup', 'metadata-protection-constants', 'metadata-directory-protection',
    'metadata-file-protection', 'metadata-encrypted-read', 'metadata-encrypted-header',
)
NEW_STAGES = frozenset(METADATA_STAGES) | {
    'reachable-checkpoint', 'real-store-load', 'loaded-envelope-equality',
}
LEGACY_STAGES = frozenset({
    'context', 'actual-bindings', 'initial-absence', 'real-store-save', 'owned-legacy-seed',
    'restart-envelope', 'production-recovery', 'protected-metadata', 'legacy-neighbor-isolation',
    'actual-home-controller', 'actual-terminal-writer-delete', 'retained-corrupt-neighbor',
    'dual-copy-retained-after-restart', 'explicit-retry-discard', 'final-restart-absence',
})


def failure_record(scenario='l08-storage', stage='real-store-save', reason='fixture_or_boundary_failure'):
    record = copy.deepcopy(matrix()[0][0] if scenario == 'l08-storage' else host_matrix()[0][0])
    record['observation'] = dict(schema_version=1, status='FAIL', run_token=TOKEN,
        boot_ordinal=record['boot_ordinal'], action=record['action'], stage=stage, reason=reason)
    return record


class L08DiagnosticsTests(unittest.TestCase):
    def test_every_emitted_stage_is_a_closed_literal_with_no_silent_schema_drift(self):
        source = (HERE / 'L08StorageProbe.kt.in').read_text()
        emitted = set(re.findall(r'(?:stage\s*=\s*|enterStage\()"([a-z-]+)"', source))
        self.assertEqual(receipts.STORAGE_FAILURE_STAGES, LEGACY_STAGES | NEW_STAGES)
        self.assertEqual(emitted, receipts.STORAGE_FAILURE_STAGES)
        self.assertIsInstance(receipts.STORAGE_FAILURE_STAGES, frozenset)

    def test_all_legacy_and_diagnostic_stages_are_preservable_only_as_closed_failures(self):
        for stage in sorted(receipts.STORAGE_FAILURE_STAGES):
            for reason in ('cancelled', 'fixture_or_boundary_failure'):
                with self.subTest(stage=stage, reason=reason):
                    record = failure_record(stage=stage, reason=reason)
                    before = copy.deepcopy(record)
                    receipts.validate_preservable_operation(record, 'l08-storage')
                    self.assertEqual(record, before)
                    self.assertEqual(record['observation']['status'], 'FAIL')

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_no_diagnostic_failure_can_become_a_successful_native_storage_result(self, _binding):
        for stage in sorted(receipts.STORAGE_FAILURE_STAGES):
            with self.subTest(stage=stage):
                records, scenario, log = matrix()
                records[0] = failure_record(stage=stage)
                with self.assertRaises(RuntimeError):
                    receipts.verify_storage(records, scenario, TOKEN, log, 'disabled', {}, {}, [framework_inventory()])

    def test_unknown_dynamic_non_string_or_sensitive_failure_metadata_is_rejected(self):
        changes = [('stage', value) for value in (
            '', 'metadata-unknown', '/private/synthetic/path', 'metadata-path-secret', True, 1, None, [], {},
        )] + [('reason', value) for value in ('raw exception text', True, 1, None, [], {})]
        changes += [(key, 'synthetic-rejection-only') for key in (
            'exception', 'message', 'path', 'private_state', 'payload', 'key_material', 'seed', 'checks',
        )]
        for key, value in changes:
            with self.subTest(key=key, value=value):
                record = failure_record()
                record['observation'][key] = value
                with self.assertRaises(RuntimeError):
                    receipts.validate_preservable_operation(record, 'l08-storage')

    def test_storage_diagnostics_do_not_expand_host_failure_stages(self):
        for stage in NEW_STAGES | (LEGACY_STAGES - {'context'}):
            with self.subTest(stage=stage), self.assertRaises(RuntimeError):
                receipts.validate_preservable_operation(failure_record('l08-host', stage), 'l08-host')
        for stage in {'context', *receipts.HOST_PLAN}:
            receipts.validate_preservable_operation(failure_record('l08-host', stage), 'l08-host')

    def test_save_load_and_equality_remain_separate_required_boundaries(self):
        source = (HERE / 'L08StorageProbe.kt.in').read_text()
        save = source.split('if (action == "save") {', 1)[1].split('} else {', 1)[0]
        ordered = ('stage = "real-store-save"', 'check(store.save(envelope) is Result.Success)',
                   'assertEnvelope(store, id, envelope)', 'paths.protectedMetadata(alias)')
        positions = [save.index(text) for text in ordered]
        self.assertEqual(positions, sorted(positions))
        helper = source.split('private suspend fun assertEnvelope(', 1)[1].split('private suspend fun checkCorrupted(', 1)[0]
        ordered = ('stage = "real-store-load"', 'val loaded = store.load(id)', 'check(loaded is Result.Success)',
                   'stage = "loaded-envelope-equality"', 'check(loaded.data == expected)',
                   'finally { loaded.data.payload.fill(0) }')
        positions = [helper.index(text) for text in ordered]
        self.assertEqual(positions, sorted(positions))

    def test_metadata_checks_remain_bounded_strict_and_observation_only(self):
        source = (HERE / 'L08StorageProbe.kt.in').read_text()
        helper = source.split('fun protectedMetadata(alias: String) {', 1)[1].split('private fun excluded(', 1)[0]
        checks = (
            'val path = file(protected, alias)', 'check(files.fileExistsAtPath(path))',
            'check(excluded(protected))', 'check(excluded(path))',
            'val key = checkNotNull(NSFileProtectionKey)',
            'check(files.attributesOfItemAtPath(protected, null)?.get(key) == protection)',
            'check(files.attributesOfItemAtPath(path, null)?.get(key) == protection)',
            'val bytes = readBoundedSnapshotBytes(path, 256 * 1024 + 128)',
            'check(bytes.take(7).toByteArray().contentEquals("PARSNAP".encodeToByteArray()))',
        )
        self.assertEqual(len(checks), len(METADATA_STAGES))
        for index, (stage, check) in enumerate(zip(METADATA_STAGES, checks)):
            with self.subTest(stage=stage):
                marker = 'enterStage("' + stage + '")'
                self.assertEqual(helper.count(marker), 1)
                self.assertEqual(helper.count(check), 1)
                self.assertLess(helper.index(marker), helper.index(check))
                if index + 1 < len(METADATA_STAGES):
                    self.assertLess(helper.index(check), helper.index('enterStage("' + METADATA_STAGES[index + 1] + '")'))
        self.assertIn('val protection = checkNotNull(NSFileProtectionComplete)', helper)
        self.assertIn('finally { bytes.fill(0) }', helper)
        self.assertNotIn('setResourceValue', helper)
        self.assertNotIn('createFileAtPath', helper)
        self.assertNotIn('createDirectoryAtPath', helper)
        self.assertIn('val paths = OwnedSnapshotPaths(token, ::enterStage)', source)

    def test_nested_metadata_stage_restores_only_after_all_checks_and_zeroization(self):
        source = (HERE / 'L08StorageProbe.kt.in').read_text()
        helper = source.split('fun protectedMetadata(alias: String) {', 1)[1].split('private fun excluded(', 1)[0]
        self.assertIn('val previous = enterStage("metadata-path")', helper)
        self.assertEqual(helper.count('enterStage(previous)'), 1)
        self.assertGreater(helper.index('enterStage(previous)'), helper.index('finally { bytes.fill(0) }'))
        self.assertNotIn('finally { enterStage(previous)', helper)

    def test_new_command_resets_context_and_cancellation_still_propagates(self):
        source = (HERE / 'L08StorageProbe.kt.in').read_text()
        command = source.split('fun command(action: String, onJson: (String) -> Unit) {', 1)[1].split('private suspend fun execute(', 1)[0]
        self.assertLess(command.index('stage = "context"'), command.index('val env = NSProcessInfo.processInfo.environment'))
        self.assertIn('val checks = withTimeout(60_000) { execute(token, boot, action) }', command)
        cancelled = command.split('catch (cancelled: CancellationException) {', 1)[1].split('catch (_: Exception)', 1)[0]
        self.assertIn('onJson(failed(token, boot, action, "cancelled"))', cancelled)
        self.assertIn('throw cancelled', cancelled)
        self.assertIn('expected?.payload?.fill(0)', command)
        self.assertIn('scope.cancel()', command)


if __name__ == '__main__': unittest.main()
