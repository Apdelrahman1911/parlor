"""Three synthetic controls; no compiler, native process or storage execution."""
import copy
from pathlib import Path
import unittest

import image_hook as hook


BINDING = dict(run_token='a' * 32, source_sha='b' * 40, control_sha256='c' * 64,
               run_id='123', run_attempt='1')


def fixture():
    results = []
    for target, selector in (('main', 'not-applicable'), ('filemanager', 'attributesOfItemAtPath:error:'),
                              ('url', 'resourceValuesForKeys:error:')):
        main = target == 'main'
        observation = dict(predicates=dict.fromkeys(hook.PREDICATES, True),
            platforms=[1] if main else [1, 6], platform_count=1 if main else 2,
            uuid='01234567-89ab-cdef-0123-456789abcdef', image_basename='HostImageProbe' if main else 'Foundation',
            cputype=0x100000c, cpusubtype=0, command_bytes_consumed=256, command_bytes_declared=256,
            relative_offset=128)
        observation['predicates']['single_platform'] = main
        results.append(dict(target=target, receiver_class='not-applicable' if main else 'NSFileManager' if target == 'filemanager' else 'NSURL',
            selector=selector, original_result='PASS' if main else 'REJECTED',
            failure='none' if main else 'image-executable-address', hook_count=1, observation=observation))
    return dict(schema=1, kind='host-image-predicate-diagnostic', collection_status=hook.CAPTURED,
        binding=dict(BINDING), process_id=123, uid=501, runtime_version=[15, 7, 9],
        production_snapshots_observed=False, protection_qualified=False, original_guard_changed=False,
        results=results, failure='none')


class HostImageHookTests(unittest.TestCase):
    def test_transform_preserves_all_original_bytes_and_guard(self):
        source = (Path(__file__).resolve().parent.parent / 'protection-diagnostic-01/ProtectionSampler.m').read_bytes()
        transformed = hook.transform(source)
        self.assertEqual(transformed.count(hook.GUARD), 1)
        self.assertEqual(transformed.replace(hook.PREAMBLE, b'').replace(hook.HOOK, b''), source)
        self.assertIn(hook.HOOK + hook.GUARD, transformed)
        with self.assertRaises(RuntimeError):
            hook.transform(source + b'\n')

    def test_closed_capture_does_not_pass_failed_original_predicate(self):
        summary = hook.validate(fixture(), BINDING)
        self.assertEqual(summary['status'], hook.CAPTURED)
        self.assertEqual(summary['results'][1]['original_result'], 'REJECTED')
        self.assertEqual(summary['results'][1]['failed_predicates'], ['single_platform'])
        self.assertFalse(summary['protection_qualified'])
        self.assertFalse(summary['original_guard_changed'])

    def test_wrong_types_bindings_or_masked_failure_are_rejected(self):
        mutations = (
            lambda r: r['binding'].update(control_sha256='d' * 64),
            lambda r: r.update(protection_qualified=True),
            lambda r: r.update(uid=True),
            lambda r: r['results'][1]['observation']['predicates'].update(single_platform=0),
            lambda r: r['results'][1]['observation'].update(platform_count=True),
            lambda r: r['results'][1]['observation'].update(platform_count=1),
            lambda r: r['results'][1].update(original_result='PASS', failure='none'),
            lambda r: r['results'][1].update(failure='none'),
            lambda r: r['results'][1]['observation'].update(relative_offset=2**40),
            lambda r: r['results'][1]['observation'].update(image_basename='/private/not-metadata'),
            lambda r: r['results'][0]['observation'].update(platforms=[6]),
            lambda r: r['results'][1].update(hook_count=2),
        )
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index):
                record = copy.deepcopy(fixture())
                mutation(record)
                with self.assertRaises(RuntimeError):
                    hook.validate(record, BINDING)


if __name__ == '__main__':
    unittest.main()
