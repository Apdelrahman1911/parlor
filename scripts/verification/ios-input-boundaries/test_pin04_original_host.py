"""Frozen original-source software controls; never build, launch, or contact a device."""
import hashlib
from pathlib import Path
import subprocess
import tempfile
import unittest

import pin01_copy
import pin02_capture
import pin03_catalog_copy as catalog
import pin04_original_host as original
import run_pin03 as runner

ROOT = Path(__file__).resolve().parents[3]


class OriginalHostControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Local immutable history only; full source history is required by the workflow.
        cls.sources = {path: subprocess.check_output(
            ['git', 'show', original.FROZEN_SOURCE + ':' + path], cwd=ROOT, timeout=15).decode()
            for path in original.FROZEN_INPUTS}
        cls.digests = {path: hashlib.sha256(value.encode()).hexdigest()
                       for path, value in cls.sources.items()}

    def test_original_driver_and_all_seven_source_bindings_match_the_failed_commit(self):
        self.assertEqual(self.digests, original.FROZEN_INPUTS)
        self.assertEqual(original.render(self.sources, self.digests, arm='original'), self.sources)
        self.assertIn('control.tap()', self.sources[pin02_capture.DRIVER])
        self.assertIn('timeout: 10', self.sources[pin02_capture.DRIVER])

    def test_observed_driver_restores_byte_exactly_and_requests_capture_only_after_failed_searches(self):
        for arm in ('compose', 'native'):
            with self.subTest(arm=arm):
                plan = original.render(self.sources, self.digests, arm=arm)
                driver = plan[pin02_capture.DRIVER]
                suffix = '\n' + (pin02_capture.HERE / 'PIN02FailureRequest.swift.in').read_text()
                self.assertTrue(driver.endswith(suffix))
                stripped = driver[:-len(suffix)]
                self.assertEqual(stripped.count('        PIN02FailureRequest.configure(app)\n'), 4)
                self.assertEqual(stripped.count('        PIN02FailureRequest.captureAfterFailedTransition(app)\n'), 1)
                stripped = stripped.replace('        PIN02FailureRequest.configure(app)\n', '')
                stripped = stripped.replace('        PIN02FailureRequest.captureAfterFailedTransition(app)\n', '')
                self.assertEqual(stripped, self.sources[pin02_capture.DRIVER])
                self.assertIn('                tapSettledControl(button)\n                return', driver)
                self.assertIn('        PIN02FailureRequest.captureAfterFailedTransition(app)\n' +
                              original.UNREACHABLE, driver)
                self.assertEqual(plan[catalog.HOME], self.sources[catalog.HOME])
                self.assertEqual(plan[pin01_copy.CONTENT].count('child.view.addGestureRecognizer(observer)'),
                                 int(arm == 'native'))
                for forbidden in ('consume(', 'delay(', 'Thread(', 'println(', 'displayName', 'GameId'):
                    self.assertNotIn(forbidden, plan[pin01_copy.ADDITION])

    def test_changed_frozen_inputs_repeated_plans_and_unknown_arms_fail_closed(self):
        for path in self.sources:
            with self.subTest(path=path), self.assertRaises(ValueError):
                original.render(self.sources | {path: self.sources[path] + '\n'}, self.digests, arm='compose')
        plan = original.render(self.sources, self.digests, arm='native')
        with self.assertRaises(ValueError):
            original.render(plan, self.digests, arm='native')
        for arm in ('', None, False, 'all'):
            with self.assertRaises(ValueError):
                original.render(self.sources, self.digests, arm=arm)
        with self.assertRaises(ValueError):
            original.render(self.sources, self.digests | {pin02_capture.DRIVER: '0' * 64}, arm='original')

    def test_original_profile_never_substitutes_a_newer_source_runtime_or_device(self):
        selected = ('original-host', original.FROZEN_SOURCE, original.RUNTIME, original.DEVICE)
        self.assertIs(runner.renderer_for_profile(*selected), original.render)
        self.assertIs(runner.renderer_for_profile('catalog', *selected[1:]), catalog.render)
        for index, wrong in enumerate(('unknown', '841a0b0623b508eae103f79ba7b4e5773e6a8b4d',
                                       'com.apple.CoreSimulator.SimRuntime.iOS-18-5',
                                       'com.apple.CoreSimulator.SimDeviceType.iPhone-16-Pro')):
            changed = list(selected); changed[index] = wrong
            with self.subTest(index=index), self.assertRaises(ValueError):
                runner.renderer_for_profile(*changed)

    def test_all_three_original_copy_plans_preserve_the_frozen_root(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw).resolve()
            for path, value in self.sources.items():
                target = root / path; target.parent.mkdir(parents=True, exist_ok=True); target.write_text(value)
            previous = self.digests
            for arm in catalog.ARMS:
                previous = runner.apply_arm(root, self.sources, self.digests, arm,
                                            previous=previous, renderer=original.render)
                runner.verify_inputs(root, previous)
            self.assertEqual(self.digests, original.FROZEN_INPUTS)
            self.assertEqual((ROOT / pin02_capture.DRIVER).read_text().count('PIN02'), 0)


if __name__ == '__main__':
    unittest.main()
