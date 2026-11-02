import unittest
from pathlib import Path
from unittest.mock import patch
from scripts.release import workflow_contract as c


class DeliveryFocusWorkflowTests(unittest.TestCase):
    def test_exact_free_scope_is_not_shipping_qualification(self):
        source = (c.WORKFLOWS/c.DELIVERY_FOCUS_BOUNDARY).read_text()
        c.verify_delivery_focus_boundary(source)
        for forbidden in ('secrets.', 'continue-on-error', 'macos-15-large', 'cache/save',
                          'productionCheck', '-retry-tests', 'pull_request:'):
            self.assertNotIn(forbidden, source)

    def test_source_runner_capture_policy_and_budgets_fail_closed(self):
        source = (c.WORKFLOWS/c.DELIVERY_FOCUS_BOUNDARY).read_text()
        for before, after in (('macos-15', 'macos-15-large'), ('contents: read', 'contents: write'),
            ('timeout-minutes: 90', 'timeout-minutes: 120'), ('run_pin56.py', 'run_pin55.py'),
            ('5029b3f48c2d012b34dd2748f809dc775276a622', '706e0ecb8766bb8de1671f64e2250c7a53b0bf9c'),
            ('original-delivery-and-current-focus-boundary', 'original-first-responder-boundary'),
            ('if: always()', 'if: success()'), ('/compose/*.json', '/source/**')):
            with self.subTest(after=after), self.assertRaises(RuntimeError):
                c.verify_delivery_focus_boundary(source.replace(before, after))

    def test_changed_companion_or_actual_acceptance_receipt_rejects(self):
        source = (c.WORKFLOWS/c.DELIVERY_FOCUS_BOUNDARY).read_text()
        original = Path.read_bytes
        targets = [c.ROOT/'scripts/verification/ios-input-boundaries'/name for name in
                   c.DELIVERY_FOCUS_BOUNDARY_COMPANIONS] + [c.ROOT/c.DELIVERY_FOCUS_BOUNDARY_RECEIPT]
        for target in targets:
            def changed(path):
                return original(path)+b'changed' if path == target else original(path)
            with self.subTest(target=target), patch.object(Path, 'read_bytes', changed), self.assertRaises(RuntimeError):
                c.verify_delivery_focus_boundary(source)
