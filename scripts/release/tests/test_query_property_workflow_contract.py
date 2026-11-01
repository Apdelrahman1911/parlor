import unittest
from scripts.release import workflow_contract as c


class PropertyBoundaryWorkflowControls(unittest.TestCase):
    def test_one_free_mac_original_pair_only_with_retained_original_and_separate_timings(self):
        text=(c.WORKFLOWS/c.QUERY_PROPERTY_BOUNDARY).read_text();c.verify_query_property_boundary(text)
        self.assertEqual(text.count('runs-on: macos-15'),1)
        for token in ('java-version: "21"','query-property-boundaries.json','failure-runloop.json','compose/tests.xcresult','compose/attachments'):
            self.assertIn(token,text)
        for forbidden in ('run_pin45.py','run_pin46','run_pin39','-retry-tests','-skip-testing','actions/cache/save','macos-latest-large'):
            self.assertNotIn(forbidden,text)

    def test_paid_runner_changed_actions_source_architecture_permissions_budget_or_evidence_reject(self):
        text=(c.WORKFLOWS/c.QUERY_PROPERTY_BOUNDARY).read_text()
        for before,after in (('macos-15','macos-15-large'),('contents: read','contents: write'),
                             ('timeout-minutes: 90','timeout-minutes: 240'),('java-version: "21"','java-version: "17"'),
                             ('failure-owned-query-property-boundary','failure-owned-main-runloop-fixed-samples'),
                             ('query-property-boundaries.json','absent.json'),('iPhone-17-Pro','iPhone-16-Pro'),
                             ('5029b3f48c2d012b34dd2748f809dc775276a622','0'*40)):
            with self.subTest(after=after),self.assertRaises(RuntimeError):c.verify_query_property_boundary(text.replace(before,after))
