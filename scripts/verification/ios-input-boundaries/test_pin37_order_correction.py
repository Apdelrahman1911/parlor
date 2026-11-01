"""Portable corrective-reader regressions; fixtures are not native evidence."""
import copy
import io
import json
from pathlib import Path
import stat
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
import zipfile

import pin37_public_scroll_natural as corrected
import read_pin37_order_correction as reader
import test_pin37_public_scroll_natural as fixture

LOG = ('pin37_public_scroll_natural.py", line 101, in accept_native_export\n'
       'ValueError: Original two methods once in original order\n')


def successful(out):
    state = fixture.evidence(out)
    d = out/'compose'
    row = state['arms'][0]
    state.update(result='INCOMPLETE', acceptance_error_type='ValueError')
    row.update(test_exit=0, result='JOURNEYS_PASSED', methods=dict.fromkeys(reader.ORDER, 'Passed'),
        attachment_test_ids=[], captures=[], companion_captures=[], root_companion_captures=[], motion_companion_captures=[])
    next(c for c in state['commands'] if c['label'] == 'compose-tests')['exit_code'] = 0
    summary = json.loads((d/'summary.json').read_bytes())
    summary.update(result='Passed', passedTests=2, failedTests=0, testFailures=[])
    (d/'summary.json').write_text(json.dumps(summary))
    tests = json.loads((d/'tests.json').read_bytes())
    for node in tests['testNodes']:
        node['result'] = 'Passed'
    (d/'tests.json').write_text(json.dumps(tests))
    for prefix in ('capture-', 'native-accessibility-', 'native-root-', 'native-frame-', 'catalog-motion-',
                   'public-text-', 'native-input-', 'input-attachment-', 'public-lifecycle-',
                   'catalog-overscroll-', 'native-touch-', 'public-scroll-'):
        for path in d.glob(prefix+'*.json'):
            path.unlink()
    import shutil
    shutil.rmtree(d/'attachments')
    (out/'state.json').write_text(json.dumps(state))
    return state


class OrderCorrectionControls(unittest.TestCase):
    def test_raw_escape_allowance_is_exact_log_only_and_never_general_api(self):
        self.assertEqual(reader.api_command(f'actions/jobs/{reader.JOB}/logs'),
            ['gh', 'api', f'repos/{reader.REPOSITORY}/actions/jobs/{reader.JOB}/logs',
             '--allow-escape-sequences'])
        for endpoint in (f'actions/runs/{reader.RUN}', f'actions/runs/{reader.RUN}/artifacts',
                         f'actions/artifacts/{reader.ARTIFACT}/zip', f'actions/jobs/{reader.JOB}',
                         'actions/jobs/1/logs'):
            self.assertNotIn('--allow-escape-sequences', reader.api_command(endpoint))


    def old(self, error='Original two methods once in original order'):
        return SimpleNamespace(CASES=reader.ORDER[::-1], accept_native_export=Mock(side_effect=ValueError(error)))

    def test_all_existing_corrected_gates_pass_and_original_state_is_not_repaired(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve()
            state = successful(out)
            before = (out/'state.json').read_bytes()
            result = reader.verify_boundary(out, state, self.old(), corrected, LOG)
            self.assertEqual(result['observations'], [])
            self.assertEqual(state['result'], 'INCOMPLETE')
            self.assertEqual(before, (out/'state.json').read_bytes())
            self.assertFalse((out/'public-scroll-observations.json').exists())
            self.assertTrue((out/'corrected-diagnostic-observations.json').is_file())
            self.assertFalse(result['shipping_qualified'])

    def test_correction_cannot_waive_an_earlier_frozen_rejection(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve()
            state = successful(out)
            with self.assertRaisesRegex(ValueError, 'All unchanged prior gates'):
                reader.verify_boundary(out, state, self.old('Missing source custody'), corrected, LOG)
            self.assertFalse((out/'corrected-diagnostic-observations.json').exists())

    def test_original_admission_must_reject_not_be_silently_changed(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve()
            state = successful(out)
            old = self.old()
            old.accept_native_export = Mock(return_value=corrected.RESULT)
            with self.assertRaisesRegex(ValueError, 'must still reject'):
                reader.verify_boundary(out, state, old, corrected, LOG)

    def test_failed_skipped_or_incomplete_native_outcomes_are_not_accepted(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve()
            state = successful(out)
            for key, value in (('test_exit', 65), ('build_exit', 65), ('cleanup', 'FAIL'),
                               ('methods', dict.fromkeys(reader.ORDER, 'Failed'))):
                changed = copy.deepcopy(state)
                changed['arms'][0][key] = value
                with self.subTest(key=key), self.assertRaises(ValueError):
                    reader.verify_boundary(out, changed, self.old(), corrected, LOG)
            summary = json.loads((out/'compose/summary.json').read_bytes())
            summary.update(skippedTests=1)
            (out/'compose/summary.json').write_text(json.dumps(summary))
            with self.assertRaises(ValueError):
                reader.verify_boundary(out, state, self.old(), corrected, LOG)

    def test_missing_or_other_failure_boundary_cannot_be_reclassified(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve()
            state = successful(out)
            for log in ('', LOG.replace('line 101', 'line 99'), LOG.replace('original order', 'other failure')):
                with self.subTest(log=log), self.assertRaises(ValueError):
                    reader.verify_boundary(out, state, self.old(), corrected, log)
            state['result'] = corrected.RESULT
            with self.assertRaises(ValueError):
                reader.verify_boundary(out, state, self.old(), corrected, LOG)

    def test_exact_api_binding_rejects_replayed_success_expiry_digest_or_foreign_source(self):
        # A small transport fixture substitutes only the byte-size/digest constants.
        # Production constants remain pinned to the independently retained ZIP.
        raw = b'portable fixture, never native evidence'
        digest = reader.sha(raw)
        run = dict(id=reader.RUN, head_sha=reader.HARNESS, head_branch=reader.BRANCH, run_attempt=1,
            event='push', status='completed', conclusion='failure', repository=dict(id=1247560518))
        a = dict(id=reader.ARTIFACT, name=f'public-scroll-natural-{reader.RUN}-1', expired=False,
            workflow_run=dict(id=reader.RUN, head_sha=reader.HARNESS, repository_id=1247560518,
                head_repository_id=1247560518), size_in_bytes=len(raw), digest='sha256:'+digest)
        artifacts = dict(total_count=1, artifacts=[a])
        with patch.multiple(reader, ZIP_SIZE=len(raw), ZIP_SHA256=digest):
            reader.verify_api(run, artifacts, raw)
            for key, value in (('run_attempt', 2), ('conclusion', 'success'), ('head_sha', '0'*40),
                               ('event', 'workflow_dispatch'), ('head_branch', 'foreign')):
                changed = run | {key:value}
                with self.subTest(key=key), self.assertRaises(ValueError):
                    reader.verify_api(changed, artifacts, raw)
            for key, value in (('expired', True), ('digest', 'sha256:'+'0'*64), ('id', reader.ARTIFACT+1)):
                changed = dict(total_count=1, artifacts=[a | {key:value}])
                with self.subTest(key=key), self.assertRaises(ValueError):
                    reader.verify_api(run, changed, raw)
            for foreign in (b'', raw+b'changed'):
                with self.assertRaises(ValueError):
                    reader.verify_api(run, artifacts, foreign)
            changed = copy.deepcopy(artifacts)
            changed['artifacts'][0]['workflow_run']['head_repository_id'] = 1
            with self.assertRaises(ValueError):
                reader.verify_api(run, changed, raw)

    def test_archive_paths_links_duplicates_and_exclusive_outputs_fail_closed(self):
        for name, mode in (('../escape.json', stat.S_IFREG), ('/escape.json', stat.S_IFREG),
                           ('state.json', stat.S_IFLNK), ('state.json', stat.S_IFIFO)):
            with tempfile.TemporaryDirectory() as temporary:
                raw = io.BytesIO()
                with zipfile.ZipFile(raw, 'w') as z:
                    entry = zipfile.ZipInfo(name)
                    entry.external_attr = (mode | 0o600) << 16
                    z.writestr(entry, '{}')
                with self.subTest(name=name, mode=mode), self.assertRaises(ValueError):
                    reader.extract(raw.getvalue(), Path(temporary).resolve()/'readback')
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve()
            with self.assertRaisesRegex(ValueError, 'Exclusive evidence extraction'):
                reader.extract(b'', out)

    def test_duplicate_and_nonfinite_json_never_admitted(self):
        for raw in ('{"a":1,"a":2}', '{"a":NaN}', '{"a":Infinity}'):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                reader.load(raw)


if __name__ == '__main__':
    unittest.main()
