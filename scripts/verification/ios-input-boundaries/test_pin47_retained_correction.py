"""Actual runner-path and retained-reader controls; fixtures never certify native behavior."""
import ast
import copy
import inspect
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import pin47_failure_runloop as boundary
import read_pin47_retained_correction as reader
import run_pin03 as runner
from test_pin47_passive_samples import passing_evidence

LOG = ('pin47_failure_runloop.py", line 189, in accept_native_export\n'
       "KeyError: 'attachment_test_ids'\n")


def original_incomplete_fixture(out):
    state = passing_evidence(out)
    state.update(result='INCOMPLETE', acceptance_error_type='KeyError')
    state['arms'][0].pop('attachment_test_ids')
    (out/'state.json').write_text(reader.json.dumps(state))
    return state


class RetainedCorrectionControls(unittest.TestCase):
    def test_real_runner_records_zero_only_for_verified_pass_and_exactly_once(self):
        row = dict(test_exit=0, methods=dict.fromkeys(boundary.SAMPLE_CASES, 'Passed'))
        runner.record_successful_attachment_selection(row)
        self.assertEqual(row['attachment_test_ids'], [])
        with self.assertRaises(ValueError):
            runner.record_successful_attachment_selection(row)
        mutations = (dict(test_exit=65), dict(test_exit=False), dict(methods={}),
                     dict(methods={'one':'Failed'}), dict(methods={'one':'Skipped'}), dict(methods=None))
        for mutation in mutations:
            row = dict(test_exit=0, methods=dict.fromkeys(boundary.SAMPLE_CASES, 'Passed')) | mutation
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                runner.record_successful_attachment_selection(row)
            self.assertNotIn('attachment_test_ids', row)

    def test_actual_main_records_success_after_verification_not_inside_failed_exit_branch(self):
        tree = ast.parse(inspect.getsource(runner.main))
        guards = [n for n in ast.walk(tree) if isinstance(n, ast.If) and
                  any(isinstance(v, ast.Call) and isinstance(v.func, ast.Name) and
                      v.func.id == 'record_successful_attachment_selection' for v in ast.walk(n))]
        self.assertEqual(len(guards), 1)
        self.assertEqual(ast.unparse(guards[0].test), "arm != 'original' and code == 0")
        assignments = [n for n in ast.walk(tree) if isinstance(n, ast.Assign) and
                       ast.unparse(n.targets[0]) == "row['methods']"]
        self.assertEqual(len(assignments), 1)
        self.assertIn('selected_verify_results(', ast.unparse(assignments[0].value))
        self.assertLess(assignments[0].lineno, guards[0].lineno)

    def test_unchanged_strict_reader_rejects_original_and_accepts_separate_single_ledger_derivation(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve();state = original_incomplete_fixture(out)
            before = (out/'state.json').read_bytes();saved = copy.deepcopy(state)
            receipt = reader.verify_boundary(out, state, boundary, runner, LOG)
            self.assertEqual(state, saved);self.assertEqual((out/'state.json').read_bytes(), before)
            self.assertEqual(receipt['native'], []);self.assertFalse(receipt['shipping_qualified'])
            self.assertFalse((out/'failure-runloop.json').exists())
            self.assertTrue((out/'corrected-diagnostic-observations.json').exists())

    def test_missing_wrong_failure_or_existing_ledger_cannot_be_normalized(self):
        mutations = (lambda s:s.update(result=boundary.RESULT),
                     lambda s:s.update(acceptance_error_type='ValueError'),
                     lambda s:s['arms'][0].update(attachment_test_ids=[]),
                     lambda s:s['arms'][0].update(test_exit=65),
                     lambda s:s['arms'][0].update(methods={}))
        for mutate in mutations:
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve();state = original_incomplete_fixture(out);mutate(state)
                with self.assertRaises(ValueError):reader.verify_boundary(out, state, boundary, runner, LOG)
                self.assertFalse((out/'corrected-diagnostic-observations.json').exists())

    def test_all_later_source_command_cleanup_and_outcome_checks_remain_required(self):
        mutations = (lambda s:s['arms'][0].update(cleanup='FAIL'),
                     lambda s:s['commands'][1]['command'].append('-retry-tests-on-failure'),
                     lambda s:s['commands'][1].update(timeout_seconds=2701),
                     lambda s:s.update(runtime='foreign-runtime'))
        for mutate in mutations:
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve();state = original_incomplete_fixture(out);mutate(state)
                with self.assertRaises(ValueError):reader.verify_boundary(out, state, boundary, runner, LOG)
                self.assertFalse((out/'corrected-diagnostic-observations.json').exists())

    def test_skipped_or_lost_actual_case_is_not_a_zero_failure_cohort(self):
        for key,value in (('skippedTests',1), ('totalTestCount',9), ('failedTests',1)):
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve();state = original_incomplete_fixture(out)
                p = out/'compose/summary.json';data = reader.load(p.read_bytes());data[key] = value
                p.write_text(reader.json.dumps(data))
                with self.assertRaises(ValueError):reader.verify_boundary(out, state, boundary, runner, LOG)

    def test_actual_log_boundary_and_api_endpoint_allowance_are_exact(self):
        for log in ('', LOG.replace('line 189','line 188'), LOG.replace('attachment_test_ids','other')):
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve();state = original_incomplete_fixture(out)
                with self.assertRaises(ValueError):reader.verify_boundary(out, state, boundary, runner, log)
        self.assertEqual(reader.api_command(f'actions/jobs/{reader.JOB}/logs')[-1], '--allow-escape-sequences')
        for endpoint in (f'actions/runs/{reader.RUN}', f'actions/artifacts/{reader.ARTIFACT}/zip'):
            self.assertNotIn('--allow-escape-sequences', reader.api_command(endpoint))
        with self.assertRaises(ValueError):reader.api_command('actions/jobs/1/logs')

    def test_exact_failed_api_and_original_artifact_are_required(self):
        raw = b'fabricated transport control, not native evidence'
        run = dict(id=reader.RUN,head_sha=reader.HARNESS,head_branch=reader.BRANCH,run_attempt=1,
                   event='push',status='completed',conclusion='failure',repository=dict(id=1247560518))
        artifact = dict(id=reader.ARTIFACT,name=f'passive-failure-samples-{reader.RUN}-1',expired=False,
                        workflow_run=dict(id=reader.RUN,head_sha=reader.HARNESS,repository_id=1247560518,
                                          head_repository_id=1247560518),size_in_bytes=len(raw),digest='sha256:'+reader.sha(raw))
        artifacts = dict(total_count=1,artifacts=[artifact])
        with patch.multiple(reader,ZIP_SIZE=len(raw),ZIP_SHA256=reader.sha(raw)):
            reader.verify_api(run,artifacts,raw)
            for key,value in (('conclusion','success'),('head_sha','0'*40),('run_attempt',2),('status','in_progress')):
                with self.assertRaises(ValueError):reader.verify_api(run|{key:value},artifacts,raw)
            for key,value in (('expired',True),('digest','sha256:'+'0'*64),('id',1)):
                with self.assertRaises(ValueError):reader.verify_api(run,dict(total_count=1,artifacts=[artifact|{key:value}]),raw)
            with self.assertRaises(ValueError):reader.verify_api(run,artifacts,raw+b'changed')

    def test_original_supporting_manifest_is_exact_and_complete(self):
        raw = (reader.HERE/reader.MANIFEST).read_bytes();data = reader.load(raw)
        self.assertEqual(reader.sha(raw),reader.MANIFEST_SHA256)
        self.assertEqual(data['harness'],reader.HARNESS);self.assertEqual(len(data['inputs']),455)


if __name__ == '__main__':
    unittest.main()
