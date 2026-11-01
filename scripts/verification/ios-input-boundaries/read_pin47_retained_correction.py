#!/usr/bin/env python3
"""Validate the retained all-pass PIN47 run without replay or rewriting evidence."""
import argparse
import copy
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tarfile

from read_pin37_order_correction import extract, load, require, sha

RUN = 41345380934
JOB = 124963973655
HARNESS = '0523684af4bcc664dbb3bf343b99f66ab171c4d9'
BRANCH = 'diagnostic/ios-passive-failure-samples-2026-11-01'
ARTIFACT = 13099894501
ZIP_SIZE = 13692160
ZIP_SHA256 = 'de717073c8dc36288cf6ce1cd420a94b345253c59c28b7f3aeea325d3752a8a3'
STATE_SHA256 = '293af102777e444500e5fb8bd843c0eefcad6f618ad0e9c0eb17573866e89fdf'
JOB_LOG_SHA256 = '620c15fe10b430eabdd561e4d5c3dc98ae46d1e903f4d301a04f8a9831e069da'
MANIFEST = 'fixtures/PIN47-passive-retained-source-manifest.json'
MANIFEST_SHA256 = 'a6236882d8164b8414b36caea9842703ef820e3a90dead099c7ff807f3b07fd8'
REPOSITORY = 'Apdelrahman1911/parlor'
HERE = Path(__file__).resolve().parent
RESULT = 'CORRECTED_PIN47_RETAINED_DIAGNOSTIC_VALIDATION_NOT_QUALIFICATION'


def api_command(endpoint):
    permitted = (f'actions/runs/{RUN}', f'actions/runs/{RUN}/artifacts',
                 f'actions/artifacts/{ARTIFACT}/zip', f'actions/jobs/{JOB}', f'actions/jobs/{JOB}/logs')
    require(endpoint in permitted, 'Only this retained run can be read')
    return ['gh', 'api', 'repos/'+REPOSITORY+'/'+endpoint] + (
        ['--allow-escape-sequences'] if endpoint == f'actions/jobs/{JOB}/logs' else [])


def verify_api(run, artifacts, raw):
    require(run['id'] == RUN and run['head_sha'] == HARNESS and run['head_branch'] == BRANCH
            and run['run_attempt'] == 1 and run['event'] == 'push' and run['status'] == 'completed'
            and run['conclusion'] == 'failure' and run['repository']['id'] == 1247560518,
            'Original terminal failed collection remains failed')
    require(artifacts['total_count'] == len(artifacts['artifacts']) == 1, 'One original artifact')
    a = artifacts['artifacts'][0]
    require(a['id'] == ARTIFACT and a['name'] == f'passive-failure-samples-{RUN}-1'
            and not a['expired'] and a['workflow_run']['id'] == RUN
            and a['workflow_run']['head_sha'] == HARNESS
            and a['workflow_run']['repository_id'] == a['workflow_run']['head_repository_id'] == 1247560518
            and a['size_in_bytes'] == len(raw) == ZIP_SIZE
            and a['digest'] == 'sha256:'+ZIP_SHA256 and sha(raw) == ZIP_SHA256,
            'Exact original artifact identity, bytes, digest and size')


def verify_boundary(out, state, original, runner, log):
    """Derive the one omitted ledger; unchanged strict admission validates everything."""
    require(state['result'] == 'INCOMPLETE' and state['acceptance_error_type'] == 'KeyError'
            and not (out/'failure-runloop.json').exists(), 'Original incomplete result is retained')
    require("KeyError: 'attachment_test_ids'" in log
            and "pin47_failure_runloop.py\", line 189, in accept_native_export" in log,
            'Exact identified post-execution collector failure')
    row = state['arms'][0]
    require(row['test_exit'] == row['build_exit'] == 0 and row['cleanup'] == 'PASS'
            and row['result'] == 'JOURNEYS_PASSED' and 'attachment_test_ids' not in row,
            'Only the actual all-pass missing-ledger boundary is eligible')
    control = original.select_profile(original.SAMPLE_PROFILE)
    methods = control.verify_results(original.load(out/'compose/summary.json'),
        original.load(out/'compose/tests.json'), row['simulator'], row['test_exit'])
    require(methods == row['methods'] == dict.fromkeys(original.SAMPLE_CASES, 'Passed'),
            'All ten actual complete successful native outcomes; no failed or skipped method waiver')
    try:
        control.accept_native_export(out, state)
    except KeyError as error:
        require(error.args == ('attachment_test_ids',), 'No unrelated frozen rejection may be waived')
    else:
        raise ValueError('Original strict reader must reject the missing ledger')
    before = (out/'state.json').read_bytes()
    original_in_memory = copy.deepcopy(state)
    derived = copy.deepcopy(state)
    runner.record_successful_attachment_selection(derived['arms'][0])
    require(derived == state | {'arms': [row | {'attachment_test_ids': []}]},
            'Exactly one separately derived success-selection ledger, not normalized state')
    require(control.accept_native_export(out, derived) == original.RESULT,
            'Every unchanged source, outcome, device, product, command, privacy and cleanup gate')
    receipt_path = out/'failure-runloop.json'
    receipt = load(receipt_path.read_bytes())
    require(receipt['methods'] == methods and receipt['failed_sample_tokens'] == {}
            and receipt['native'] == [] and all(receipt[k] is False for k in (
                'application_root_cause', 'shipping_qualified', 'native_timing_neutrality', 'physical_verified')),
            'Zero failure exports; no application cause or qualification')
    receipt_path.rename(out/'corrected-diagnostic-observations.json')
    require((out/'state.json').read_bytes() == before and state == original_in_memory,
            'Neither original file nor in-memory state may be repaired')
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = HERE.parents[2]
    out = args.output.absolute()
    require(out.parent.resolve() == out.parent and not out.exists(), 'Fresh canonical owned output')
    out.mkdir(mode=0o700)
    def git(*argv):
        return subprocess.check_output(['git', *argv], cwd=root)
    def api(endpoint):
        return subprocess.check_output(api_command(endpoint), cwd=root)
    run_raw = api(f'actions/runs/{RUN}')
    artifacts_raw = api(f'actions/runs/{RUN}/artifacts')
    raw = api(f'actions/artifacts/{ARTIFACT}/zip')
    verify_api(load(run_raw), load(artifacts_raw), raw)
    for name, data in (('original-run-api.json', run_raw), ('original-artifacts-api.json', artifacts_raw),
                       ('original-artifact.zip', raw)):
        with (out/name).open('xb') as f:
            f.write(data)
    job_raw = api(f'actions/jobs/{JOB}')
    log_raw = api(f'actions/jobs/{JOB}/logs')
    job = load(job_raw)
    require(job['id'] == JOB and job['run_id'] == RUN and job['head_sha'] == HARNESS
            and job['name'] == 'Ten fixed passive AR EN samples on original iOS26.2'
            and job['conclusion'] == 'failure' and job['labels'] == ['macos-15'],
            'Exact original standard free native job')
    step = [s for s in job['steps'] if s['name'] == 'Verify exact new fixed-wrapper and outcome controls once']
    log = log_raw.decode()
    require(len(step) == 1 and step[0]['conclusion'] == 'success' and sha(log_raw) == JOB_LOG_SHA256
            and 'Ran 5 tests' in log and 'OK (skipped=' not in log and ' ... skipped ' not in log,
            'All five actual Apple controls passed, zero skips; exact original log custody')
    (out/'original-job-api.json').write_bytes(job_raw)
    (out/'original-job.log').write_bytes(log_raw)
    manifest_raw = (HERE/MANIFEST).read_bytes()
    require(sha(manifest_raw) == MANIFEST_SHA256, 'Pinned original supporting manifest')
    manifest = load(manifest_raw)
    require(manifest['harness'] == HARNESS and len(manifest['inputs']) == 455, 'All455 original supporting inputs')
    oldroot = out/'original-harness'
    for name, expected in manifest['inputs'].items():
        p = Path(name)
        require(not p.is_absolute() and '..' not in p.parts, 'Owned original source path')
        value = git('show', HARNESS+':'+name)
        require(sha(value) == expected, 'Every original supporting byte matches exact Git source')
        target = oldroot/p
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(value)
    sys.path[:0] = [str(oldroot/'scripts/verification/ios-input-boundaries'), str(oldroot)]
    import pin47_failure_runloop as original
    import run_pin03 as original_runner
    spec = importlib.util.spec_from_file_location('pin47_corrected_runner', HERE/'run_pin03.py')
    runner = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(runner)
    require((runner.ROOTS, runner.PREFIXES) == (original_runner.ROOTS, original_runner.PREFIXES),
            'Original application selection unchanged')
    require((HERE/'pin47_failure_runloop.py').read_bytes() ==
            (oldroot/'scripts/verification/ios-input-boundaries/pin47_failure_runloop.py').read_bytes(),
            'Strict decoder and copied native owner/driver renderer unchanged')
    leaf = original.runtime.pair_control.leaf
    plan = original.renderer_for_profile(original.SAMPLE_PROFILE, original.SOURCE, original.RUNTIME,
        original.DEVICE)({p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED}, leaf.pair.INPUTS, arm='compose')
    archive = git('archive', original.SOURCE, *original_runner.ROOTS,
                  *(p.rstrip('/') for p in original_runner.PREFIXES))
    complete = {}
    with tarfile.open(fileobj=io.BytesIO(archive), mode='r:') as source:
        for member in source:
            if member.isdir() or not original_runner.selected(member.name):
                continue
            require(member.isfile() and member.name not in complete and member.size <= 64*1024*1024,
                    'Entire regular bounded original application archive')
            complete[member.name] = sha(source.extractfile(member).read())
    require(len(complete) == 1214 and len(plan) == 7, 'All1214 original inputs and exact seven-file copied plan')
    evidence = out/'corrective-readback'
    extract(raw, evidence)
    state_raw = (evidence/'state.json').read_bytes()
    state = load(state_raw)
    require(sha(state_raw) == STATE_SHA256 and (state['harness'], state['source'], state['source_tree']) ==
            (HARNESS, original.SOURCE, original.TREE) and state['profile'] == original.SAMPLE_PROFILE
            and state['original_inputs'] == complete and state['archive_sha256'] == sha(archive)
            and len(state['arms']) == 1 and state['arms'][0]['arm'] == 'compose',
            'Exact original state, full source archive, profile and single arm')
    require(load((evidence/'compose/inputs.json').read_bytes()) == complete | {
        p:sha(b.encode()) for p,b in plan.items()}, 'Entire original plus copied Swift plan custody')
    receipt = verify_boundary(evidence, state, original, runner, log)
    result = dict(result=RESULT, original_run=RUN, original_conclusion='failure', original_result='INCOMPLETE',
        original_harness=HARNESS, correction_harness=git('rev-parse', 'HEAD').decode().strip(),
        source=original.SOURCE, source_tree=original.TREE, supporting_inputs=455, original_inputs=1214,
        actual_apple_controls=5, skipped_controls=0, methods=receipt['methods'], actual_xctest_exit=0,
        artifact=ARTIFACT, artifact_sha256=sha(raw), original_state_sha256=sha(state_raw),
        original_job_log_sha256=sha(log_raw), copied_application_plan_unchanged=True,
        original_state_unchanged=True, derived_attachment_test_ids=[], corrected_observations_sha256=sha(
            (evidence/'corrected-diagnostic-observations.json').read_bytes()),
        native_build_or_journey_replayed=False, failure_exports=0, owned_cleanup=state['arms'][0]['cleanup'],
        application_root_cause=False, shipping_qualified=False, native_timing_neutrality=False, physical_verified=False)
    with (out/'corrected-pin47-validation.json').open('x') as f:
        json.dump(result, f, indent=2)
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
