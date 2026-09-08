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


def protection_read(value='missing', dictionary=True, key=False, query='returned', error=None):
    return dict(query=query, dictionary_present=dictionary, key_present=key, value=value,
                native_error=error if error is not None else dict(present=False, domain='none', code=0))


def protection_failure(target='directory'):
    result = failure_record(stage='metadata-' + target + '-protection')
    complete = protection_read('complete', key=True)
    volume = protection_read('supported', key=True)
    result['observation']['protection_metadata'] = dict(schema_version=1, failed_target=target,
        directory=dict(role='required-failed' if target == 'directory' else 'required-passed',
            attributes=protection_read() if target == 'directory' else copy.deepcopy(complete),
            volume=copy.deepcopy(volume)),
        file=dict(role='diagnostic-only' if target == 'directory' else 'required-failed',
            attributes=copy.deepcopy(complete) if target == 'directory' else protection_read(),
            volume=copy.deepcopy(volume)))
    return result


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
            'check(directory.complete)',
            'check(file.complete)',
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
        self.assertIn('val paths = OwnedSnapshotPaths(token, ::enterStage, ::recordProtectionDiagnostic)', source)

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


    def test_closed_metadata_distinguishes_nil_error_missing_and_public_protection_classes(self):
        observations = [
            protection_read('unavailable', dictionary=False),
            protection_read('unavailable', dictionary=False, error=dict(present=True, domain='cocoa', code=260)),
            protection_read(),
        ] + [protection_read(value, key=True) for value in sorted(receipts.PROTECTION_ATTRIBUTE_VALUES - {'complete'})]
        for target in ('directory', 'file'):
            for observation in observations:
                with self.subTest(target=target, observation=observation):
                    record = protection_failure(target)
                    record['observation']['protection_metadata'][target]['attributes'] = observation
                    before = copy.deepcopy(record)
                    receipts.validate_preservable_operation(record, 'l08-storage')
                    self.assertEqual(record, before)
                    self.assertEqual(record['observation']['status'], 'FAIL')

    def test_additional_file_getter_is_explicitly_diagnostic_only_and_can_remain_unknown(self):
        record = protection_failure()
        metadata = record['observation']['protection_metadata']
        self.assertEqual(metadata['file']['role'], 'diagnostic-only')
        metadata['file']['attributes'] = protection_read('unobserved', dictionary=False,
            query='observer-exception', error=dict(present=False, domain='unobserved', code=0))
        receipts.validate_preservable_operation(record, 'l08-storage')
        metadata['file']['role'] = 'required-passed'
        with self.assertRaises(RuntimeError): receipts.validate_preservable_operation(record, 'l08-storage')

    def test_volume_unavailable_supported_unsupported_and_error_remain_observations(self):
        readings = [protection_read('supported', key=True), protection_read('unsupported', key=True),
                    protection_read('non-number', key=True), protection_read(),
                    protection_read('unavailable', dictionary=False),
                    protection_read('unavailable', dictionary=False, error=dict(present=True, domain='posix', code=13))]
        readings += [protection_read('unobserved', dictionary=False, query=query,
            error=dict(present=False, domain='unobserved', code=0)) for query in ('constant-unavailable', 'observer-exception')]
        for reading in readings:
            for target in ('directory', 'file'):
                with self.subTest(reading=reading, target=target):
                    record = protection_failure()
                    record['observation']['protection_metadata'][target]['volume'] = reading
                    receipts.validate_preservable_operation(record, 'l08-storage')
                    self.assertEqual(record['observation']['status'], 'FAIL')

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_supported_volume_and_complete_diagnostic_sibling_never_satisfy_the_storage_gate(self, _binding):
        for target in ('directory', 'file'):
            rows, scenario, log = matrix()
            rows[0] = protection_failure(target)
            with self.assertRaises(RuntimeError):
                receipts.verify_storage(rows, scenario, TOKEN, log, 'disabled', {}, {}, [framework_inventory()])
        record = matrix()[0][0]
        record['observation']['protection_metadata'] = protection_failure()['observation']['protection_metadata']
        with self.assertRaises(RuntimeError): receipts.validate_preservable_operation(record, 'l08-storage')

    def test_metadata_is_rejected_on_host_cancellation_and_other_storage_stages(self):
        value = protection_failure()['observation']['protection_metadata']
        candidates = [('l08-host', failure_record('l08-host', 'context')),
                      ('l08-storage', failure_record(stage='metadata-directory-protection', reason='cancelled'))]
        candidates += [('l08-storage', failure_record(stage=stage)) for stage in
                       sorted(receipts.STORAGE_FAILURE_STAGES - {'metadata-directory-protection', 'metadata-file-protection'})]
        for scenario, record in candidates:
            with self.subTest(scenario=scenario, stage=record['observation']['stage']):
                record['observation']['protection_metadata'] = copy.deepcopy(value)
                with self.assertRaises(RuntimeError): receipts.validate_preservable_operation(record, scenario)

    def test_metadata_identity_roles_and_required_comparison_cannot_disagree(self):
        changes = [
            lambda item: item.update(schema_version=True),
            lambda item: item.update(failed_target='file'),
            lambda item: item.update(failed_target='/private/path'),
            lambda item: item['directory'].update(role='diagnostic-only'),
            lambda item: item['file'].update(role='required-passed'),
            lambda item: item['directory'].update(attributes=protection_read('complete', key=True)),
            lambda item: item['directory'].update(attributes=protection_read('unobserved', dictionary=False,
                query='observer-exception', error=dict(present=False, domain='unobserved', code=0))),
        ]
        for change in changes:
            record = protection_failure()
            change(record['observation']['protection_metadata'])
            with self.assertRaises(RuntimeError): receipts.validate_preservable_operation(record, 'l08-storage')
        record = protection_failure('file')
        record['observation']['protection_metadata']['directory']['attributes'] = protection_read()
        with self.assertRaises(RuntimeError): receipts.validate_preservable_operation(record, 'l08-storage')

    def test_nested_unknown_fields_and_raw_native_values_never_enter_evidence(self):
        for field in ('path', 'message', 'description', 'userInfo', 'private_state', 'payload', 'seed', 'raw_value'):
            for keys in ((), ('directory',), ('file', 'attributes'), ('directory', 'volume'),
                         ('file', 'volume', 'native_error')):
                record = protection_failure()
                item = record['observation']['protection_metadata']
                for key in keys: item = item[key]
                item[field] = 'SYNTHETIC_REJECTION_ONLY'
                with self.subTest(field=field, keys=keys), self.assertRaises(RuntimeError):
                    receipts.validate_preservable_operation(record, 'l08-storage')
        changes = [('query', value) for value in (True, None, [], {}, 'raw_native_exception')]
        changes += [('value', value) for value in (True, None, {}, [], '/private/synthetic/path')]
        changes += [(field, value) for field in ('dictionary_present', 'key_present') for value in (0, 1, None, 'true')]
        for field, value in changes:
            record = protection_failure()
            record['observation']['protection_metadata']['directory']['attributes'][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(RuntimeError):
                receipts.validate_preservable_operation(record, 'l08-storage')

    def test_native_error_values_are_closed_bounded_and_do_not_coerce_booleans(self):
        for code in (-(2 ** 63), -1, 0, 1, 2 ** 63 - 1):
            for domain in ('cocoa', 'posix', 'other'):
                record = protection_failure()
                record['observation']['protection_metadata']['directory']['attributes']['native_error'] = dict(
                    present=True, domain=domain, code=code)
                receipts.validate_preservable_operation(record, 'l08-storage')
        cases = [('code', value) for value in (True, False, -(2 ** 63) - 1, 2 ** 63, 1.0, None, '13')]
        cases += [('domain', value) for value in (True, None, [], {}, 'raw_exception_domain')]
        cases += [('present', value) for value in (0, 1, None, 'false')]
        for field, value in cases:
            record = protection_failure()
            record['observation']['protection_metadata']['directory']['attributes']['native_error'][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(RuntimeError):
                receipts.validate_preservable_operation(record, 'l08-storage')

    def test_missing_or_unknown_read_cannot_claim_an_observed_value(self):
        changes = [dict(dictionary_present=False, key_present=True), dict(dictionary_present=False, value='none'),
                   dict(value='none'), dict(query='observer-exception'), dict(query='constant-unavailable')]
        for change in changes:
            record = protection_failure()
            record['observation']['protection_metadata']['directory']['attributes'].update(change)
            with self.subTest(change=change), self.assertRaises(RuntimeError):
                receipts.validate_preservable_operation(record, 'l08-storage')

    def test_producer_keeps_required_getter_order_equality_and_failure_only_scope(self):
        source = (HERE / 'L08StorageProbe.kt.in').read_text()
        metadata = source.split('fun protectedMetadata(alias: String) {', 1)[1].split('private class ProtectionRead', 1)[0]
        ordered = ('enterStage("metadata-directory-protection")',
                   'val directory = protectionAttributes(protected, key, protection)',
                   'if (!directory.complete) protectionFailure("directory", path, key, protection, directory)',
                   'check(directory.complete)', 'enterStage("metadata-file-protection")',
                   'val file = protectionAttributes(path, key, protection)',
                   'if (!file.complete) protectionFailure("file", path, key, protection, directory, file)',
                   'check(file.complete)', 'enterStage("metadata-encrypted-read")')
        positions = [metadata.index(value) for value in ordered]
        self.assertEqual(positions, sorted(positions))
        reader = source.split('private fun protectionAttributes(', 1)[1].split('private fun protectionValue(', 1)[0]
        self.assertEqual(reader.count('files.attributesOfItemAtPath(path, error.ptr)'), 1)
        self.assertIn('val value = values?.get(key)', reader)
        self.assertIn('ProtectionRead(value == protection,', reader)
        self.assertIn('error.value = null', reader)
        failed = source.split('private fun failed(', 1)[1].split('internal fun requireContext(', 1)[0]
        self.assertIn('if (reason == "fixture_or_boundary_failure")', failed)
        self.assertIn('protectionDiagnostic?.let { put("protection_metadata", it) }', failed)
        self.assertEqual(source.count('protectionDiagnostic = null'), 2)

    def test_extra_queries_are_read_only_bounded_and_failure_unknown_does_not_become_success(self):
        source = (HERE / 'L08StorageProbe.kt.in').read_text()
        helpers = source.split('private fun protectionFailure(', 1)[1].split('private fun excluded(', 1)[0]
        self.assertIn('file?.observation ?: additionalFileObservation(path, key, protection)', helpers)
        self.assertIn('"diagnostic-only"', helpers)
        self.assertIn('volumeProtection(protected)', helpers)
        self.assertIn('volumeProtection(path)', helpers)
        self.assertEqual(helpers.count('resourceValuesForKeys(listOf(key), error.ptr)'), 1)
        self.assertIn('NSURL.fileURLWithPath(path)', helpers)
        self.assertIn('val key = NSURLVolumeSupportsFileProtectionKey', helpers)
        self.assertIn('value is NSNumber -> if (value.boolValue) "supported" else "unsupported"', helpers)
        self.assertEqual(helpers.count('catch (cancelled: CancellationException)'), 2)
        self.assertEqual(helpers.count('throw cancelled'), 2)
        self.assertEqual(helpers.count('unknownMetadata("observer-exception")'), 2)
        self.assertIn('unknownMetadata("constant-unavailable")', helpers)
        for forbidden in ('localizedDescription', 'userInfo', 'put("path"', 'setResourceValue',
                          'setAttributes', 'createFile', 'createDirectory', 'removeItem',
                          'readBoundedSnapshotBytes', 'while (', 'repeat(', 'enterStage('):
            self.assertNotIn(forbidden, helpers)

    def test_newer_public_enum_is_runtime_guarded_and_arbitrary_strings_stay_redacted(self):
        source = (HERE / 'L08StorageProbe.kt.in').read_text()
        classify = source.split('private fun protectionValue(', 1)[1].split('private fun protectionFailure(', 1)[0]
        self.assertIn('isOperatingSystemAtLeastVersion(cValue<NSOperatingSystemVersion>', classify)
        self.assertIn('majorVersion = 17; minorVersion = 0; patchVersion = 0', classify)
        self.assertIn('}) && value == NSFileProtectionCompleteWhenUserInactive', classify)
        self.assertIn('value is String -> "other-string"', classify)
        self.assertNotIn('value.toString()', classify)


if __name__ == '__main__': unittest.main()
