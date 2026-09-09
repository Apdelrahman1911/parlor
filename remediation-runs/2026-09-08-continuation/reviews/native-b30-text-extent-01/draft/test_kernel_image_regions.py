"""Synthetic mapping/ownership controls. No libproc or native tool is executed."""
import copy
import hashlib
import json
import os
from pathlib import Path
import stat
import unittest
from unittest.mock import Mock, patch

import external_image_provenance as sample
import kernel_image_regions as kernel
import run_normal_ios_launch as runner
import image_text_extent as extent
from test_image_text_extent import macho_bytes, macho_prefix
import test_image_diagnostic_orchestration as orchestration
import test_normal_launch_controls as fixtures


def bindings(selected):
    return {path: dict(layout=extent.parse_text(macho_prefix(image_uuid=image['uuid']), image['bytes']),
        identity=(7, ordinal + 1, image['bytes'], os.getuid(), stat.S_IFREG | 0o600, 0, 0), sha256=image['sha256'])
        for ordinal, (path, image) in enumerate(selected.items())}


def rows(selected, pid=fixtures.PID, *, text=None, region_size=8192):
    text = bindings(selected) if text is None else text
    return {path: dict(schema_version=2, status='PASS', pid=pid,
        requested_start=image['sample_start'], requested_end_exclusive=image['sample_end_inclusive'] + 1,
        region_start=image['sample_start'], region_size=region_size, region_offset=0, protection=5,
        max_protection=5, region_flags=0, native_structure_bytes=1328, native_return_bytes=1328,
        path_exact=True, vnode_identity_matches=True, artifact_bytes=image['bytes'],
        artifact_device=text[path]['identity'][0], artifact_inode=text[path]['identity'][1],
        artifact_uid=text[path]['identity'][3], artifact_text=copy.deepcopy(text[path]['layout']),
        extent_kind=extent.bound_kind(region_size, image, text[path]['layout']))
        for path, image in selected.items()}


class KernelImageRegionTests(unittest.TestCase):
    def setUp(self):
        self.selected = sample.parse_sample(fixtures.sample_fixture(), fixtures.PID,
                                            fixtures.EXE, fixtures.artifact_fixture())
        for image in self.selected.values():
            data = macho_bytes(image_uuid=image['uuid'])
            image.update(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
        self.bindings = bindings(self.selected)
        self.rows = rows(self.selected)

    def test_every_selected_image_preserves_precise_method_identity_and_file_hashes(self):
        result = kernel.bind_regions(fixtures.PID, self.selected, self.rows, self.bindings)
        self.assertEqual('PASS', result['status'])
        self.assertEqual(set(self.selected), {row['path'] for row in result['images']})
        for row in result['images']:
            self.assertEqual(self.selected[row['path']]['sha256'], row['sha256'])
            self.assertEqual(self.rows[row['path']], row['kernel_region'])
        self.assertIn('not read mapped-memory Mach-O headers/UUIDs or hash mapped pages', result['limitation'])
        self.assertIn('not a universal Mach-O rule', result['limitation'])
        self.assertIn('not atomic lifetime', result['limitation'])
        self.assertNotIn('vmmap executable-region address', result['method'])

    def test_all_optional_selected_images_are_required_and_extra_rows_are_rejected(self):
        selected = copy.deepcopy(self.selected)
        path = str(fixtures.EXE.parent / '__preview.dylib')
        selected[path] = dict(next(iter(selected.values())), kind='other-app-image', relative_path='__preview.dylib',
                             sample_start=0x400000, sample_end_inclusive=0x403fff)
        native = rows(selected)
        text = bindings(selected)
        self.assertEqual(4, len(kernel.bind_regions(fixtures.PID, selected, native, text)['images']))
        for malformed in (self.rows, dict(native, unknown=native[path]), {}):
            with self.subTest(keys=list(malformed)), self.assertRaises(RuntimeError):
                kernel.bind_regions(fixtures.PID, selected, malformed, text)

    def test_closed_schema_and_each_independent_native_guard_fail_closed(self):
        path = next(iter(self.rows))
        mutations = dict(schema_version=1, status='FAIL', pid=fixtures.PID + 1,
            requested_start=1, requested_end_exclusive=1, region_start=1, region_size=0,
            region_offset=1, protection=7, max_protection=8, region_flags=1,
            native_structure_bytes=0, native_return_bytes=1, path_exact=False,
            vnode_identity_matches=False, artifact_bytes=1, artifact_device=0, artifact_inode=0,
            artifact_uid=os.getuid() + 1, artifact_text={}, extent_kind=extent.EXACT)
        for key, value in mutations.items():
            native = copy.deepcopy(self.rows); native[path][key] = value
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                kernel.bind_regions(fixtures.PID, self.selected, native, self.bindings)
        for key in kernel.FIELDS:
            native = copy.deepcopy(self.rows); del native[path][key]
            with self.subTest(missing=key), self.assertRaises(RuntimeError):
                kernel.bind_regions(fixtures.PID, self.selected, native, self.bindings)
        native = copy.deepcopy(self.rows); native[path]['private_path'] = 'synthetic'
        with self.assertRaises(RuntimeError): kernel.bind_regions(fixtures.PID, self.selected, native, self.bindings)

    def test_integer_types_holes_overflow_and_noncontaining_regions_are_rejected(self):
        path = next(iter(self.rows))
        for key in kernel.FIELDS - {'status', 'path_exact', 'vnode_identity_matches', 'artifact_text', 'extent_kind'}:
            for value in (True, -1, 2**64, '1', None):
                native = copy.deepcopy(self.rows); native[path][key] = value
                with self.subTest(key=key, value=value), self.assertRaises(RuntimeError):
                    kernel.bind_regions(fixtures.PID, self.selected, native, self.bindings)
        for key, value in (('region_start', self.rows[path]['requested_start'] + 4096),
                           ('region_size', 16385), ('path_exact', 1), ('vnode_identity_matches', 1)):
            native = copy.deepcopy(self.rows); native[path][key] = value
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                kernel.bind_regions(fixtures.PID, self.selected, native, self.bindings)
        for field, value in (('sample_start', True), ('sample_start', 0), ('sample_end_inclusive', 2**64 - 1)):
            selected = copy.deepcopy(self.selected); selected[path][field] = value
            with self.subTest(field=field), self.assertRaises(RuntimeError):
                kernel.bind_regions(fixtures.PID, selected, self.rows, self.bindings)

    def test_observer_selection_is_explicit_unique_and_does_not_consume_signing_flags(self):
        self.assertEqual(('vmmap', []), kernel.selected_observer([]))
        self.assertEqual(('libproc', ['--simulator-signing=adhoc']),
            kernel.selected_observer(['--image-observer=libproc', '--simulator-signing=adhoc']))
        for arguments in (['--image-observer=unknown'], ['--image-observer=vmmap'],
                          ['--image-observer=libproc', '--image-observer=libproc']):
            with self.assertRaises(RuntimeError): kernel.selected_observer(arguments)
        _observer, remaining = kernel.selected_observer(['--image-observer=libproc', '--unknown'])
        with self.assertRaises(RuntimeError): runner.selected_mode(remaining)

    def test_each_image_needs_its_own_closed_geometry_uuid_and_descriptor_binding(self):
        exact = rows(self.selected, region_size=32768)
        for row in exact.values(): row['max_protection'] = 7; row['region_flags'] = 2
        result = kernel.bind_regions(fixtures.PID, self.selected, exact, self.bindings)
        self.assertEqual({extent.EXACT}, {image['kernel_region']['extent_kind'] for image in result['images']})
        for path in self.selected:
            for fault in ('missing', 'uuid', 'inode', 'hash', 'boolean-layout', 'wrong-kind'):
                text, native = copy.deepcopy(self.bindings), copy.deepcopy(exact)
                if fault == 'missing': del text[path]
                elif fault == 'uuid': text[path]['layout']['uuid'] = '11111111-2222-3333-4444-555555555555'
                elif fault == 'inode': native[path]['artifact_inode'] += 1
                elif fault == 'hash': text[path]['sha256'] = '0' * 64
                elif fault == 'boolean-layout': native[path]['artifact_text']['text_segment']['flags'] = False
                else: native[path]['extent_kind'] = extent.PREFIX
                with self.subTest(path=path, fault=fault), self.assertRaises(RuntimeError):
                    kernel.bind_regions(fixtures.PID, self.selected, native, text)

    def test_actual_claimed_native_intervals_must_not_overlap(self):
        paths = list(self.selected)
        for region_size in (8192, 32768):
            selected = copy.deepcopy(self.selected)
            selected[paths[1]].update(sample_start=selected[paths[0]]['sample_start'] + region_size - 1,
                sample_end_inclusive=selected[paths[0]]['sample_start'] + region_size - 1 + 16383)
            native = rows(selected, region_size=region_size)
            with self.subTest(size=region_size), self.assertRaises(RuntimeError):
                kernel.bind_regions(fixtures.PID, selected, native, self.bindings)
            selected[paths[1]]['sample_start'] += 1; selected[paths[1]]['sample_end_inclusive'] += 1
            self.assertEqual('PASS', kernel.bind_regions(fixtures.PID, selected,
                rows(selected, region_size=region_size), self.bindings)['status'])

    def test_native_source_uses_real_abi_exact_bounds_and_closed_path_free_output(self):
        source = (Path(__file__).parent / 'kernel_image_region.c.in').read_text()
        for guard in ('memset(&info, 0, sizeof(info))', 'PROC_PIDREGIONPATHINFO, start',
                      'returned != (int)sizeof(info)', 'region->pri_address != start',
                      'region->pri_size > end - start', 'region->pri_offset != 0',
                      'VM_PROT_READ | VM_PROT_EXECUTE', 'PROC_REGION_SUBMAP',
                      'strnlen(info.prp_vip.vip_path', 'strcmp(info.prp_vip.vip_path, argv[4])',
                      'file->vst_dev != (uint32_t)before.st_dev', 'file->vst_ino != (uint64_t)before.st_ino',
                      'file->vst_size != before.st_size', 'a->st_ino != b->st_ino', 'a->st_uid != b->st_uid',
                      'region->pri_size != text.vmsize', 'O_RDONLY | O_NOFOLLOW | O_NONBLOCK',
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
        for path, artifact in context['artifacts'].items():
            data = macho_bytes(image_uuid=artifact['uuid'])
            Path(path).write_bytes(data)
            artifact.update(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
        helper = lane.temporary / 'kernel-image-region'; helper.write_bytes(b'synthetic-helper')
        lane.prepare_region_helper = Mock(return_value=helper)
        lane.receipt['kernel_region_helper_sha256'] = hashlib.sha256(helper.read_bytes()).hexdigest()
        original = lane.require
        # Keep fake-native row construction outside runner-only inspection hooks.
        native_inspect_artifact = extent.inspect_artifact
        def require(arguments, filename, **options):
            if arguments[0] != helper:
                return original(arguments, filename, **options)
            context['commands'].append((list(map(str, arguments)), filename, options))
            self.assertEqual(10, options['timeout']); self.assertEqual(4096, options['limit'])
            selected = sample.parse_sample(context['raw_sample'], fixtures.PID, context['executable'], context['artifacts'])
            path = arguments[4]
            text = {path: native_inspect_artifact(path, selected[path])}
            observation = rows({path: selected[path]}, text=text,
                               region_size=context.get('region_size', 8192))[path]
            self.assertEqual(str(observation['requested_start']), arguments[2])
            self.assertEqual(str(observation['requested_end_exclusive']), arguments[3])
            (lane.destination / filename).write_text(json.dumps(observation))
            if context.get('after_region') is not None:
                context['after_region'](arguments[4])
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

    def test_actual_orchestration_exact_text_path_is_individual_and_keeps_original_sample_end(self):
        fixture, context, _helper = self.context(); lane = context['lane']; context['region_size'] = 32768
        with fixture.mocked_native(context): lane.observe_external_images()
        observed = json.loads((lane.destination / 'separate-external-image-provenance.json').read_text())
        self.assertEqual(3, len(observed['images']))
        for image in observed['images']:
            row = image['kernel_region']
            self.assertEqual(extent.EXACT, row['extent_kind'])
            self.assertEqual(image['sample_end_inclusive'] + 1, row['requested_end_exclusive'])
            self.assertEqual(16384, row['requested_end_exclusive'] - row['requested_start'])
            self.assertEqual(32768, row['region_size'])
            self.assertEqual(Path(image['path']).stat().st_ino, row['artifact_inode'])
            self.assertEqual(context['artifacts'][image['path']]['uuid'], row['artifact_text']['uuid'])
        self.assertEqual([], fixture.vmmap_commands(context))
        fixture.assert_raw_removed(lane)

    def test_invalid_geometry_before_query_or_replacement_after_query_never_publishes(self):
        for fault in ('invalid-geometry', 'same-byte-replacement', 'unsafe-postflight',
                      'unsafe-prehash', 'unsafe-posthash'):
            fixture, context, helper = self.context(); lane = context['lane']
            first = sorted(context['artifacts'])[0]
            inspections, changed_at_query_counts = [], []
            real_inspect, real_read = extent.inspect_artifact, os.read
            def unsafe_target():
                context['backend'].read.return_value = fixtures.Record(command=str(context['executable']),
                    token=(0, 0, 0, 0, 0, fixtures.PID, 0, 2))
            if fault == 'invalid-geometry':
                data = macho_bytes(image_uuid=context['artifacts'][first]['uuid'], flags=1)
                Path(first).write_bytes(data)
                context['artifacts'][first]['sha256'] = hashlib.sha256(data).hexdigest()
            elif fault in ('same-byte-replacement', 'unsafe-postflight'):
                def after_query(path):
                    if fault == 'same-byte-replacement':
                        original = Path(path); data = original.read_bytes()
                        original.rename(original.with_name(original.name + '.prior'))
                        original.write_bytes(data)
                    else:
                        unsafe_target()
                context['after_region'] = after_query
            def inspect(path, image):
                inspections.append(path)
                ordinal = {'unsafe-prehash': 1, 'unsafe-posthash': 2}.get(fault)
                if len(inspections) != ordinal:
                    return real_inspect(path, image)
                def read(fd, maximum):
                    chunk = real_read(fd, maximum)
                    if chunk and not changed_at_query_counts:
                        changed_at_query_counts.append(sum(row[0][0] == str(helper) for row in context['commands']))
                        unsafe_target()  # Change during real owned-file read/hash, not a fake helper query.
                    return chunk
                with patch.object(os, 'read', side_effect=read):
                    binding = real_inspect(path, image)
                self.assertEqual(image['sha256'], binding['sha256'])
                return binding
            with fixture.mocked_native(context), patch.object(extent, 'inspect_artifact', side_effect=inspect), \
                    patch.object(runner, 'read_json', wraps=runner.read_json) as parse, self.assertRaises(RuntimeError):
                lane.observe_external_images()
            count = sum(row[0][0] == str(helper) for row in context['commands'])
            expected = 0 if fault in ('invalid-geometry', 'unsafe-prehash') else 1
            self.assertEqual(expected, count)
            if fault in ('unsafe-prehash', 'unsafe-posthash'):
                self.assertEqual([expected], changed_at_query_counts)
                self.assertEqual([first] * (expected + 1), inspections)
            # The next image's preflight must not mask a missing post-hash guard.
            parse.assert_not_called()
            self.assertFalse((lane.destination / 'separate-external-image-provenance.json').exists())
            self.assertEqual([], fixture.vmmap_commands(context))
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
