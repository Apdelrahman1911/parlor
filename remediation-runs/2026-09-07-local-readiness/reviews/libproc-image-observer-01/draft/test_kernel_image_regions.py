"""Synthetic mapping/ownership controls. No libproc or native tool is executed."""
import copy
import hashlib
import json
from pathlib import Path
import unittest
from unittest.mock import Mock, patch

import external_image_provenance as sample
import kernel_image_regions as kernel
import run_normal_ios_launch as runner
import test_image_diagnostic_orchestration as orchestration
import test_normal_launch_controls as fixtures


def rows(selected, pid=fixtures.PID):
    return {path: dict(schema_version=1, status='PASS', pid=pid,
        requested_start=image['sample_start'], requested_end_exclusive=image['sample_end_inclusive'] + 1,
        region_start=image['sample_start'], region_size=8192, region_offset=0, protection=5,
        max_protection=5, region_flags=0, native_structure_bytes=1328, native_return_bytes=1328,
        path_exact=True, vnode_identity_matches=True, artifact_bytes=image['bytes'])
        for path, image in selected.items()}


class KernelImageRegionTests(unittest.TestCase):
    def setUp(self):
        self.selected = sample.parse_sample(fixtures.sample_fixture(), fixtures.PID,
                                            fixtures.EXE, fixtures.artifact_fixture())
        self.rows = rows(self.selected)

    def test_every_selected_image_preserves_precise_method_identity_and_file_hashes(self):
        result = kernel.bind_regions(fixtures.PID, self.selected, self.rows)
        self.assertEqual('PASS', result['status'])
        self.assertEqual(set(self.selected), {row['path'] for row in result['images']})
        for row in result['images']:
            self.assertEqual(self.selected[row['path']]['sha256'], row['sha256'])
            self.assertEqual(self.rows[row['path']], row['kernel_region'])
        self.assertIn('not read Mach-O headers/UUIDs or hash mapped pages', result['limitation'])
        self.assertIn('not a universal Mach-O rule', result['limitation'])
        self.assertIn('not atomic lifetime', result['limitation'])
        self.assertNotIn('vmmap executable-region address', result['method'])

    def test_all_optional_selected_images_are_required_and_extra_rows_are_rejected(self):
        selected = copy.deepcopy(self.selected)
        path = str(fixtures.EXE.parent / '__preview.dylib')
        selected[path] = dict(next(iter(selected.values())), kind='other-app-image', relative_path='__preview.dylib')
        native = rows(selected)
        self.assertEqual(4, len(kernel.bind_regions(fixtures.PID, selected, native)['images']))
        for malformed in (self.rows, dict(native, unknown=native[path]), {}):
            with self.subTest(keys=list(malformed)), self.assertRaises(RuntimeError):
                kernel.bind_regions(fixtures.PID, selected, malformed)

    def test_closed_schema_and_each_independent_native_guard_fail_closed(self):
        path = next(iter(self.rows))
        mutations = dict(schema_version=2, status='FAIL', pid=fixtures.PID + 1,
            requested_start=1, requested_end_exclusive=1, region_start=1, region_size=0,
            region_offset=1, protection=7, max_protection=8, region_flags=1,
            native_structure_bytes=0, native_return_bytes=1, path_exact=False,
            vnode_identity_matches=False, artifact_bytes=1)
        for key, value in mutations.items():
            native = copy.deepcopy(self.rows); native[path][key] = value
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                kernel.bind_regions(fixtures.PID, self.selected, native)
        for key in kernel.FIELDS:
            native = copy.deepcopy(self.rows); del native[path][key]
            with self.subTest(missing=key), self.assertRaises(RuntimeError):
                kernel.bind_regions(fixtures.PID, self.selected, native)
        native = copy.deepcopy(self.rows); native[path]['private_path'] = 'synthetic'
        with self.assertRaises(RuntimeError): kernel.bind_regions(fixtures.PID, self.selected, native)

    def test_integer_types_holes_overflow_and_noncontaining_regions_are_rejected(self):
        path = next(iter(self.rows))
        for key in kernel.FIELDS - {'status', 'path_exact', 'vnode_identity_matches'}:
            for value in (True, -1, 2**64, '1', None):
                native = copy.deepcopy(self.rows); native[path][key] = value
                with self.subTest(key=key, value=value), self.assertRaises(RuntimeError):
                    kernel.bind_regions(fixtures.PID, self.selected, native)
        for key, value in (('region_start', self.rows[path]['requested_start'] + 4096),
                           ('region_size', 16385), ('path_exact', 1), ('vnode_identity_matches', 1)):
            native = copy.deepcopy(self.rows); native[path][key] = value
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                kernel.bind_regions(fixtures.PID, self.selected, native)
        for field, value in (('sample_start', True), ('sample_start', 0), ('sample_end_inclusive', 2**64 - 1)):
            selected = copy.deepcopy(self.selected); selected[path][field] = value
            with self.subTest(field=field), self.assertRaises(RuntimeError):
                kernel.bind_regions(fixtures.PID, selected, self.rows)

    def test_observer_selection_is_explicit_unique_and_does_not_consume_signing_flags(self):
        self.assertEqual(('vmmap', []), kernel.selected_observer([]))
        self.assertEqual(('libproc', ['--simulator-signing=adhoc']),
            kernel.selected_observer(['--image-observer=libproc', '--simulator-signing=adhoc']))
        for arguments in (['--image-observer=unknown'], ['--image-observer=vmmap'],
                          ['--image-observer=libproc', '--image-observer=libproc']):
            with self.assertRaises(RuntimeError): kernel.selected_observer(arguments)
        _observer, remaining = kernel.selected_observer(['--image-observer=libproc', '--unknown'])
        with self.assertRaises(RuntimeError): runner.selected_mode(remaining)

    def test_native_source_uses_real_abi_exact_bounds_and_closed_path_free_output(self):
        source = (Path(__file__).parent / 'kernel_image_region.c.in').read_text()
        for guard in ('memset(&info, 0, sizeof(info))', 'PROC_PIDREGIONPATHINFO, start',
                      'returned != (int)sizeof(info)', 'region->pri_address != start',
                      'region->pri_size > end - start', 'region->pri_offset != 0',
                      'VM_PROT_READ | VM_PROT_EXECUTE', 'PROC_REGION_SUBMAP',
                      'strnlen(info.prp_vip.vip_path', 'strcmp(info.prp_vip.vip_path, argv[4])',
                      'file->vst_dev != (uint32_t)before.st_dev', 'file->vst_ino != (uint64_t)before.st_ino',
                      'file->vst_size != before.st_size', 'before.st_ino != after.st_ino',
                      'strnlen(text, 21)', 'getuid() != geteuid()'):
            self.assertIn(guard, source)
        self.assertEqual(1, source.count('proc_pidinfo('))
        for forbidden in ('proc_regionfilename(', 'task_for_pid(', 'mach_vm_read', 'system(', 'popen(', 'fork('):
            self.assertNotIn(forbidden, source)
        self.assertNotIn('printf(argv', source)

    def context(self):
        fixture = orchestration.ImageDiagnosticOrchestrationTests(methodName='runTest')
        fixture.setUp(); self.addCleanup(fixture.doCleanups)
        context = fixture.fixture(); lane = context['lane']; lane.image_observer = 'libproc'
        helper = lane.temporary / 'kernel-image-region'; helper.write_bytes(b'synthetic-helper')
        lane.prepare_region_helper = Mock(return_value=helper)
        lane.receipt['kernel_region_helper_sha256'] = hashlib.sha256(helper.read_bytes()).hexdigest()
        original = lane.require
        def require(arguments, filename, **options):
            if arguments[0] != helper:
                return original(arguments, filename, **options)
            context['commands'].append((list(map(str, arguments)), filename, options))
            self.assertEqual(10, options['timeout']); self.assertEqual(4096, options['limit'])
            selected = sample.parse_sample(context['raw_sample'], fixtures.PID, context['executable'], context['artifacts'])
            observation = rows(selected)[arguments[4]]
            self.assertEqual(str(observation['requested_start']), arguments[2])
            self.assertEqual(str(observation['requested_end_exclusive']), arguments[3])
            (lane.destination / filename).write_text(json.dumps(observation))
        lane.require = Mock(side_effect=require)
        return fixture, context, helper

    def test_actual_orchestration_queries_every_selected_row_without_vmmap_or_fallback(self):
        fixture, context, helper = self.context(); lane = context['lane']
        with fixture.mocked_native(context): lane.observe_external_images()
        observed = json.loads((lane.destination / 'separate-external-image-provenance.json').read_text())
        self.assertEqual('PASS', lane.receipt['provenance_status'])
        self.assertEqual('libproc', observed['image_observer'])
        self.assertEqual(3, len(observed['images']))
        self.assertEqual(3, sum(row[0][0] == str(helper) for row in context['commands']))
        self.assertEqual([], fixture.vmmap_commands(context))
        self.assertNotIn('temporary_vmmap_sha256', observed)
        self.assertEqual(kernel.LIMITATION, lane.receipt['external_provenance_limitation'])
        fixture.assert_raw_removed(lane)

    def test_query_failure_cancellation_or_bad_sample_never_waives_the_original_error(self):
        for sample_failure in (False, True):
            for error in (RuntimeError('synthetic observer failure'), KeyboardInterrupt()):
                fixture, context, _helper = self.context(); lane = context['lane']
                query = patch.object(runner, 'parse_sample', side_effect=error) if sample_failure else \
                    patch.object(lane, 'observe_kernel_regions', side_effect=error)
                with fixture.mocked_native(context), query, self.assertRaises(type(error)) as raised:
                    lane.observe_external_images()
                self.assertIs(error, raised.exception)
                self.assertEqual([], fixture.vmmap_commands(context))
                self.assertFalse((lane.destination / 'separate-external-image-provenance.json').exists())
                fixture.assert_raw_removed(lane)

    def test_generation_change_helper_change_and_bad_json_fail_before_provenance(self):
        for fault in ('generation', 'helper', 'helper-after', 'json'):
            fixture, context, helper = self.context(); lane = context['lane']
            selected = sample.parse_sample(context['raw_sample'], fixtures.PID, context['executable'], context['artifacts'])
            if fault == 'generation':
                context['backend'].read.return_value = fixtures.Record(command=str(context['executable']), token=(0, 0, 0, 0, 0, fixtures.PID, 0, 2))
            elif fault == 'helper':
                helper.write_bytes(b'changed-helper')
            elif fault == 'helper-after':
                prior = lane.require
                def mutate_after_query(arguments, filename, **options):
                    prior(arguments, filename, **options)
                    if arguments[4] == sorted(selected)[-1]:
                        helper.write_bytes(b'changed-after-final-invocation')
                lane.require = Mock(side_effect=mutate_after_query)
            else:
                def malformed(_arguments, filename, **_options):
                    (lane.destination / filename).write_text('{"status":"PASS","status":"FAIL"}')
                lane.require = Mock(side_effect=malformed)
            with fixture.mocked_native(context), self.assertRaises((RuntimeError, ValueError)):
                lane.observe_kernel_regions(helper, context['record'], context['backend'], fixtures.PID, selected)
            if fault == 'helper-after':
                self.assertEqual(len(selected), lane.require.call_count)
            self.assertFalse((lane.destination / 'separate-external-image-provenance.json').exists())

    def test_helper_compile_rejects_stale_outputs_and_is_bound_into_controls(self):
        fixture, context, helper = self.context(); lane = context['lane']
        with self.assertRaises(RuntimeError): runner.Lane.prepare_region_helper(lane)
        paths = {row['path'] for row in runner.control_manifest()}
        for name in ('kernel_image_region.c.in', 'kernel_image_regions.py', 'test_kernel_image_regions.py'):
            self.assertIn(str((runner.HERE / name).relative_to(runner.ROOT)), paths)
        self.assertTrue(helper.exists())  # Refusal never deletes someone else's pre-existing output.


if __name__ == '__main__':
    unittest.main()
