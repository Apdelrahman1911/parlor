"""Paired source/outcome rejection fixtures, not executed native evidence."""
import copy
import importlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

import pin06_public_setup_pair as pair
import pin13_original_cohort as cohort
import pin16_aggregate_context as aggregate
import pin17_observer_comparison as comparison
import run_pin03

prior = sys.modules.get('_parlor_aggregate_context_controls') or importlib.import_module('test_pin16_aggregate_context')


def evidence(out, original_failed=False, observed_failed=False):
    state = prior.evidence(out, observed_failed)
    state['profile'] = comparison.PROFILE
    row = copy.deepcopy(state['arms'][0])
    row.update(arm='original', simulator='00000000-0000-4000-8000-000000000099', captures=[],
               companion_captures=[], root_companion_captures=[])
    row.pop('attachment_test_ids', None)
    dest = out/'original'; dest.mkdir()
    for name in ('summary.json', 'tests.json', 'cleanup.json', 'product.json', 'test.log',
                 'kernel-before-build.json', 'kernel-after-tests.json'):
        raw = (out/'compose'/name).read_bytes().replace(state['arms'][0]['simulator'].encode(), row['simulator'].encode())
        (dest/name).write_bytes(raw)
    # Produce each arm's independent native outcomes rather than copying a pass.
    for name in ('summary.json', 'tests.json'):
        data = json.loads((dest/name).read_bytes())
        def change(value):
            if isinstance(value, dict):
                if value.get('nodeType') == 'Test Case' and value.get('nodeIdentifier') in cohort.PUBLIC:
                    value['result'] = 'Failed' if original_failed and value['nodeIdentifier'].endswith('InEnglish()') else 'Passed'
                for child in value.values(): change(child)
            elif isinstance(value, list):
                for child in value: change(child)
        change(data)
        if name == 'summary.json':
            failed = int(original_failed)
            data.update(passedTests=23-failed, failedTests=failed, result='Failed' if failed else 'Passed')
        (dest/name).write_text(json.dumps(data))
    row.update(test_exit=65 if original_failed else 0, combined_exit=65 if original_failed else 0,
               result='JOURNEYS_FAILED' if original_failed else 'JOURNEYS_PASSED',
               methods={p:'Failed' if original_failed and p.endswith('InEnglish()') else 'Passed' for p in cohort.PUBLIC})
    (dest/'inputs.json').write_text(json.dumps(state['original_inputs']))
    native = next(c for c in state['commands'] if c['label']=='compose-build-and-tests')
    stop = next(c for c in state['commands'] if c['label']=='compose-gradle-stop')
    state['commands'].remove(stop); state['commands'].append(stop)
    original_commands = []
    for command in state['commands']:
        if not command['label'].startswith('compose'): continue
        c = copy.deepcopy(command)
        c['label'] = 'original'+c['label'][len('compose'):]
        c['command'] = [p.replace(state['arms'][0]['simulator'], row['simulator']) for p in c['command']]
        if c['label'] == 'original-build-and-tests': c['exit_code'] = row['test_exit']
        original_commands.append(c)
    # Order is itself a mandatory boundary, not inferred from JSON case sets.
    original_commands.sort(key=lambda c:c['label']=='original-gradle-stop')
    index = state['commands'].index(native)
    state['commands'][index:index] = original_commands
    state['arms'].insert(0, row)
    return state


class ObserverComparisonControls(unittest.TestCase):
    def test_original_has_no_source_delta_and_observed_is_byte_identical_to_pin16(self):
        inputs = {p: pair.fixture(p) for p in pair.MODIFIED}
        render = comparison.renderer_for_profile(comparison.PROFILE, comparison.SOURCE, pair.RUNTIME, pair.DEVICE)
        self.assertEqual(render(inputs, pair.INPUTS, arm='original'), inputs)
        self.assertEqual(render(inputs, pair.INPUTS, arm='compose'),
            aggregate.renderer_for_profile(aggregate.PROFILE, comparison.SOURCE, pair.RUNTIME, pair.DEVICE)(
                inputs, pair.INPUTS, arm='compose'))
        bad = dict(inputs); bad[next(iter(bad))] += '\n'
        with self.assertRaises(ValueError): render(bad, pair.INPUTS, arm='original')
        with self.assertRaises(ValueError): render(inputs, pair.INPUTS, arm='native')
        with self.assertRaises(ValueError): run_pin03.main(observer_comparison=True, aggregate_context=True)
        self.assertEqual(run_pin03.completed_exit_code(comparison.RESULT), 0)
        self.assertEqual(run_pin03.completed_exit_code('PAIRED_OBSERVER_QUALIFIED'), 1)

    def test_all_equal_and_different_native_outcomes_are_preserved_without_causal_claim(self):
        for original_failed, observed_failed in ((False, False), (True, False), (False, True), (True, True)):
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve(); state = evidence(out, original_failed, observed_failed)
                self.assertEqual(comparison.accept_native_export(out, state), comparison.RESULT)
                result = json.loads((out/'observer-comparison.json').read_bytes())
                self.assertEqual(result['public_outcomes_equal'], original_failed == observed_failed)
                for key in ('application_root_cause', 'shipping_qualified', 'native_timing_neutrality',
                            'full60_journey_neutrality', 'physical_verified'): self.assertFalse(result[key])
                self.assertEqual(comparison.accept_native_export(out, state), comparison.RESULT)

    def test_missing_reversed_duplicate_foreign_reused_or_unreaped_boundaries_reject(self):
        changes = (
            lambda s: s['arms'].pop(0), lambda s: s['arms'].reverse(),
            lambda s: s['arms'][0].update(simulator=s['arms'][1]['simulator']),
            lambda s: s['arms'][0].update(cleanup='FAIL'),
            lambda s: s['commands'].pop(0),
            lambda s: s['commands'].append(copy.deepcopy(s['commands'][2])),
            lambda s: s['commands'][2].update(direct_child_reaped=False),
            lambda s: s['commands'][2].update(timeout_seconds=3601),
            lambda s: s['commands'][2]['command'].append('-retry-tests-on-failure'),
            lambda s: s['commands'].append(s['commands'].pop(2)),
        )
        for index, change in enumerate(changes):
            with self.subTest(mutation=index), tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve(); state = evidence(out); change(state)
                with self.assertRaises((ValueError, KeyError)): comparison.accept_native_export(out, state)

    def test_modified_original_inputs_product_outcomes_order_or_fabricated_captures_reject(self):
        changes = (
            lambda o,s: (o/'original/inputs.json').write_text('{}'),
            lambda o,s: (o/'original/product.json').write_text('{}'),
            lambda o,s: (o/'original/test.log').write_text(''),
            lambda o,s: (o/'original/capture-fake.json').write_text('{}'),
            lambda o,s: s['arms'][0]['methods'].clear(),
            lambda o,s: (o/'observer-comparison.json').write_text('{}'),
        )
        for change in changes:
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve(); state = evidence(out); change(out,state)
                with self.assertRaises((ValueError, KeyError)): comparison.accept_native_export(out, state)


if __name__ == '__main__':
    unittest.main()
