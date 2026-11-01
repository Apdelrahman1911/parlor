#!/usr/bin/env python3
"""Revalidate one retained native run; never rerun XCTest or repair old evidence."""
import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import stat
import subprocess
import sys
import tarfile
import zipfile

RUN = 41289077117
JOB = 124796372819
HARNESS = 'af1085320102ad424ae4c102d96870ce7100e72d'
BRANCH = 'diagnostic/ios-public-scroll-natural-2026-11-01'
ARTIFACT = 13076628176
ZIP_SIZE = 2987482
ZIP_SHA256 = 'c7aaeaefae43916bca92b7cdc6c8aeeec3af7cf0b07b835b6e9326aec1763300'
SUPPORT_SHA256 = '94879c8440e99cb6b4dd47254a0cdb804de4572f72b1f7ac6b7479b63c550da3'
REPOSITORY = 'Apdelrahman1911/parlor'
EXTRAS = (
    '.github/workflows/ios-catalog-input-boundary.yml',
    '.github/workflows/ios-native-root-observations.yml',
    '.github/workflows/ios-original-touch-delivery.yml',
    '.github/workflows/ios-original-touch-export-control.yml',
    '.github/workflows/ios-overscroll-getter-boundary.yml',
    '.github/workflows/ios-public-scroll-export.yml',
    '.github/workflows/ios-public-scroll-natural.yml',
    '.github/workflows/ios-public-session-stress.yml',
    '.github/workflows/ios-settled-failure-review.yml',
    '.github/workflows/ios-touch-original-cohort.yml',
    'scripts/ci/NATIVE_CONTINUATION.md', 'scripts/ci/apple_release_gate.py',
    'scripts/ci/apple_verification_hygiene.py', 'scripts/ci/darwin_host_capability.py',
    'scripts/ci/darwin_worker_identity.py', 'scripts/ci/linux_exe_reader.py',
    'scripts/ci/linux_process_probe.py', 'scripts/ci/native_continuation.py',
    'scripts/ci/native_process_metadata.py', 'scripts/ci/native_process_probe.py',
    'scripts/ci/owned_ci_simulator.py', 'scripts/ci/verification_hygiene.py',
    'scripts/generate_review_inventory.py',
    'scripts/release/tests/test_native_root_workflow_contract.py',
    'scripts/release/tests/test_review_inventory.py',
    'scripts/release/tests/test_workflow_contract.py', 'scripts/release/workflow_contract.py',
    'scripts/verification/ios-readiness/test_public_catalog_input.py',
)
ORDER = (
    'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInArabic()',
    'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish()',
)


def require(ok, message):
    if not ok:
        raise ValueError(message)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def load(raw):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'Duplicate JSON key')
            result[key] = value
        return result
    return json.loads(raw, object_pairs_hook=unique,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite JSON')))


def verify_api(run, artifacts, raw):
    require(run['id'] == RUN and run['head_sha'] == HARNESS and run['head_branch'] == BRANCH
        and run['run_attempt'] == 1 and run['event'] == 'push'
        and run['status'] == 'completed' and run['conclusion'] == 'failure'
        and run['repository']['id'] == 1247560518, 'Exact original failed run remains failed')
    require(artifacts['total_count'] == len(artifacts['artifacts']) == 1, 'One original artifact')
    a = artifacts['artifacts'][0]
    require(a['id'] == ARTIFACT and a['name'] == f'public-scroll-natural-{RUN}-1'
        and not a['expired'] and a['workflow_run']['id'] == RUN
        and a['workflow_run']['head_sha'] == HARNESS
        and a['workflow_run']['repository_id'] == a['workflow_run']['head_repository_id'] == 1247560518
        and a['size_in_bytes'] == len(raw) == ZIP_SIZE
        and a['digest'] == 'sha256:' + ZIP_SHA256 and sha(raw) == ZIP_SHA256,
        'Pinned original artifact API digest/source/size identity')


def extract(raw, out):
    require(not out.exists(), 'Exclusive evidence extraction; no replay')
    out.mkdir()
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        entries = z.infolist()
        require(len(entries) <= 10000 and len({e.filename for e in entries}) == len(entries)
            and sum(e.file_size for e in entries) <= 1024*1024*1024, 'Bounded unique evidence archive')
        for entry in entries:
            path = Path(entry.filename)
            mode = entry.external_attr >> 16
            require(not path.is_absolute() and '..' not in path.parts
                and not stat.S_ISLNK(mode) and (not stat.S_IFMT(mode) or
                    stat.S_ISREG(mode) or stat.S_ISDIR(mode)), 'Only owned regular evidence paths')
            if entry.is_dir():
                continue
            attachment = path.parts[:2] == ('compose', 'attachments')
            selected = str(path) == 'compose/test.log' or (
                path.suffix == '.json' and 'tests.xcresult' not in path.parts)
            if not selected and not attachment:
                continue
            limit = 64*1024*1024 if attachment else 4*1024*1024 if path.name == 'test.log' else 2*1024*1024
            require(0 < entry.file_size <= limit, 'Bounded retained evidence member')
            target = out/path
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open('xb') as f:
                f.write(z.read(entry))


def verify_boundary(out, state, old, corrected, log):
    require(state['result'] == 'INCOMPLETE' and state['acceptance_error_type'] == 'ValueError'
        and not (out/'public-scroll-observations.json').exists(), 'Original incomplete state stays original')
    require('pin37_public_scroll_natural.py", line 101, in accept_native_export' in log
        and 'ValueError: Original two methods once in original order' in log,
        'Exact identified original post-XCTest admission failure')
    require(corrected.CASES == ORDER and old.CASES == ORDER[::-1], 'Exact corrected order, not unordered admission')
    row = state['arms'][0]
    require(row['test_exit'] == row['build_exit'] == 0 and row['cleanup'] == 'PASS'
        and row['result'] == 'JOURNEYS_PASSED' and row['methods'] == dict.fromkeys(ORDER, 'Passed'),
        'Both actual original journeys and build/cleanup passed')
    try:
        old.accept_native_export(out, state)
    except ValueError as error:
        require(str(error) == 'Original two methods once in original order',
            'All unchanged prior gates must pass before exact frozen rejection')
    else:
        raise ValueError('Frozen original admission must still reject this evidence')
    before = sha((out/'state.json').read_bytes())
    result = corrected.accept_native_export(out, state)
    require(result == corrected.RESULT and before == sha((out/'state.json').read_bytes())
        and state['result'] == 'INCOMPLETE', 'Corrective validation never rewrites original state/result')
    receipt = load((out/'public-scroll-observations.json').read_bytes())
    require(receipt['observations'] == [] and receipt['methods'] == dict.fromkeys(ORDER, 'Passed')
        and all(receipt[k] is False for k in ('application_root_cause', 'shipping_qualified',
            'native_timing_neutrality', 'physical_verified')), 'No failure exports or application qualification')
    # This receipt was created by corrected validation, NOT produced by the old native run.
    (out/'public-scroll-observations.json').rename(out/'corrected-diagnostic-observations.json')
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[3]
    out = args.output.absolute()
    require(out.parent.resolve() == out.parent and not out.exists(), 'Fresh canonical owned output')
    out.mkdir()
    def git(*argv):
        return subprocess.check_output(['git', *argv], cwd=root)
    def api(endpoint):
        return subprocess.check_output(['gh', 'api', 'repos/'+REPOSITORY+'/'+endpoint], cwd=root)
    run_raw = api(f'actions/runs/{RUN}')
    artifact_raw = api(f'actions/runs/{RUN}/artifacts')
    raw = api(f'actions/artifacts/{ARTIFACT}/zip')
    verify_api(load(run_raw), load(artifact_raw), raw)
    for name, data in (('original-run-api.json', run_raw), ('original-artifacts-api.json', artifact_raw),
                       ('original-artifact.zip', raw)):
        with (out/name).open('xb') as f:
            f.write(data)
    job_raw = api(f'actions/jobs/{JOB}')
    job = load(job_raw)
    require(job['id'] == JOB and job['run_id'] == RUN and job['head_sha'] == HARNESS
        and job['name'] == 'Original EN AR public scroll boundaries' and job['conclusion'] == 'failure',
        'Exact original failed native job')
    preflight = [s for s in job['steps'] if s['name'] == 'Verify source privacy ownership and actual generated Swift drivers']
    log_raw = api(f'actions/jobs/{JOB}/logs')
    log = log_raw.decode()
    require(len(preflight) == 1 and preflight[0]['conclusion'] == 'success'
        and 'Ran 428 tests' in log and 'OK (skipped=' not in log, '428 actual Apple controls passed without skips')
    (out/'original-job-api.json').write_bytes(job_raw)
    (out/'original-job.log').write_bytes(log_raw)
    oldroot = out/'original-harness'
    names = git('ls-tree', '-r', '--name-only', HARNESS, 'scripts/verification/ios-input-boundaries').decode().splitlines()
    manifest = {}
    for name in sorted(set(names) | set(EXTRAS)):
        value = git('show', HARNESS+':'+name)
        manifest[name] = sha(value)
        target = oldroot/name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(value)
    require(len(manifest) == 210 and sha(json.dumps(manifest, sort_keys=True, separators=(',', ':')).encode())
        == SUPPORT_SHA256, 'All210 original supporting inputs independently bound')
    sys.path.insert(0, str(oldroot))
    sys.path.insert(0, str(oldroot/'scripts/verification/ios-input-boundaries'))
    import pin37_public_scroll_natural as old
    import run_pin03
    spec = importlib.util.spec_from_file_location('pin37_order_corrected', Path(__file__).with_name('pin37_public_scroll_natural.py'))
    corrected = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(corrected)
    require((old.SOURCE, old.TREE, old.PROFILE) == (corrected.SOURCE, corrected.TREE, corrected.PROFILE),
        'Same original application and diagnostic profile')
    plan = lambda module: module.renderer_for_profile(module.PROFILE, module.SOURCE, module.pair.RUNTIME,
        module.pair.DEVICE)({p:module.pair.fixture(p) for p in module.pair.MODIFIED}, module.pair.INPUTS, arm='compose')
    original_plan = plan(old)
    require(len(original_plan) == 16 and original_plan == plan(corrected), 'Entire16-file app/driver plan unchanged')
    archive = git('archive', old.SOURCE, *run_pin03.ROOTS, *(p.rstrip('/') for p in run_pin03.PREFIXES))
    complete = {}
    with tarfile.open(fileobj=io.BytesIO(archive), mode='r:') as source:
        for member in source:
            if member.isdir() or not run_pin03.selected(member.name):
                continue
            require(member.isfile() and member.name not in complete and member.size <= 64*1024*1024,
                'Entire regular bounded original application archive')
            complete[member.name] = sha(source.extractfile(member).read())
    require(len(complete) == 1214, 'All1214 original application inputs')
    evidence = out/'corrective-readback'
    extract(raw, evidence)
    state_raw = (evidence/'state.json').read_bytes()
    state = load(state_raw)
    require((state['harness'], state['source'], state['source_tree']) == (HARNESS, old.SOURCE, old.TREE)
        and state['profile'] == old.PROFILE and state['original_inputs'] == complete
        and state['archive_sha256'] == sha(archive) and len(state['arms']) == 1
        and state['arms'][0]['arm'] == 'compose', 'Exact original source/archive/state identity')
    require(load((evidence/'compose/inputs.json').read_bytes()) == complete | {
        p:sha(b.encode()) for p,b in original_plan.items()}, 'All original plus exact copied plan')
    product = (evidence/'compose/product.json').read_bytes()
    require(load(product) and sha(product) == state['arms'][0]['product_manifest_sha256'],
        'Recorded original product manifest custody, not live binary attestation')
    observations = verify_boundary(evidence, state, old, corrected, log)
    receipt = dict(result='CORRECTED_PIN37_DIAGNOSTIC_VALIDATION_NOT_QUALIFICATION',
        original_run=RUN, original_conclusion='failure', original_result=state['result'],
        original_harness=HARNESS, correction_harness=git('rev-parse', 'HEAD').decode().strip(),
        original_source=old.SOURCE, original_tree=old.TREE, artifact=ARTIFACT,
        artifact_sha256=ZIP_SHA256, supporting_input_count=210, original_input_count=1214,
        original_state_sha256=sha(state_raw), corrected_reader_sha256=sha(Path(__file__).read_bytes()),
        observations=observations, native_tests_reexecuted=False, application_root_cause=False,
        shipping_qualified=False, physical_verified=False)
    with (out/'corrected-public-scroll-validation.json').open('x') as f:
        json.dump(receipt, f, indent=2)
    print(json.dumps(receipt, indent=2))
    return 0


if __name__ == '__main__':
    sys.exit(main())
