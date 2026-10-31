"""Read the retained PIN19 diagnostic call-graph failure; never launch/build a Simulator.

Only exact public catalog method attachments and numeric query counters may be
published. The failed experiment remains failed: its observe-only helper admitted a tap.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import stat
import subprocess
import sys
import tarfile
import time
import types
import zipfile

import public_attachments as attachments
import pin19_settled_catalog_idle as idle
import pin06_public_setup_pair as pair
import run_pin03

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.ci.native_process_probe import Commands, child_environment

REPOSITORY = 'Apdelrahman1911/parlor'
BRANCH = 'refs/heads/diagnostic/ios-settled-failure-readback-2026-10-31'
RUN, ARTIFACT = 41234285221, 13055530647
HARNESS = 'ee7773d1c2a489c73eb274c3c4b81388e674889b'
SOURCE = '5029b3f48c2d012b34dd2748f809dc775276a622'
TREE = 'fcf9abf3dba3ee40c4c9aec7966260f8d848ff53'
DIGEST = 'a1e49e58d3e0db6fcfe80fa1ec5006caf7571a5eb6b6d68d330553d731b18ef1'
BYTES, MAX_EXPANDED = 72737120, 512 * 1024 * 1024
METHOD = 'IOSAppLaunchUITests/testPIN19SettledWordCatalogIdleCapture()'
FAILURE = 'Public setup control is not reachable: Open Word Impostor'
SIMULATOR = 'BD92BA41-02F8-41C2-B359-122C2A26CA05'
sha = lambda raw: hashlib.sha256(raw).hexdigest()


def require(ok):
    if not ok:
        raise ValueError('Exact failed PIN19 public readback boundary rejected')


def load(raw):
    def unique(pairs):
        value = {}
        for key, item in pairs:
            require(key not in value)
            value[key] = item
        return value
    return json.loads(raw, object_pairs_hook=unique, parse_constant=lambda _: require(False))


def validate_metadata(run, artifact):
    require(run['id'] == RUN and run['head_sha'] == HARNESS and run['run_attempt'] == 1 and
            run['status'] == 'completed' and run['conclusion'] == 'failure' and run['event'] == 'push' and
            run['head_branch'] == 'diagnostic/ios-settled-catalog-idle-2026-10-31' and
            run['path'] == '.github/workflows/ios-native-root-observations.yml' and
            run['repository']['full_name'] == run['head_repository']['full_name'] == REPOSITORY)
    require(artifact['id'] == ARTIFACT and artifact['name'] == 'native-settled-catalog-idle-41234285221-1' and
            artifact['expired'] is False and artifact['size_in_bytes'] == BYTES and artifact['digest'] == 'sha256:' + DIGEST and
            artifact['workflow_run']['id'] == RUN and artifact['workflow_run']['head_sha'] == HARNESS)


def original_idle_renderer():
    # Read this repository's exact original, reviewed pure renderer. Current
    # renderer fixes must not rewrite the historical failed product's manifest.
    raw = subprocess.check_output(['git', 'show', HARNESS +
        ':scripts/verification/ios-input-boundaries/pin19_settled_catalog_idle.py'], cwd=ROOT)
    require(sha(raw) == 'c5afd6981aa61b85455918208d1d73f99174e19db2c886a02b4fde761b423128')
    original = types.ModuleType('_pin19_original_renderer')
    exec(compile(raw, 'original-pin19-renderer', 'exec'), original.__dict__)
    return original.renderer_for_profile(original.PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE)


def validate_source_inputs(state, observed):
    archive = subprocess.check_output(['git', 'archive', SOURCE, *run_pin03.ROOTS,
                                      *(p.rstrip('/') for p in run_pin03.PREFIXES)], cwd=ROOT)
    complete, sources = {}, {}
    with tarfile.open(fileobj=io.BytesIO(archive), mode='r:') as tar:
        for entry in tar:
            if entry.isdir() or not run_pin03.selected(entry.name):
                continue
            require(entry.isfile() and entry.name not in complete and entry.size <= 64 * 1024 * 1024)
            raw = tar.extractfile(entry).read()
            complete[entry.name] = sha(raw)
            if entry.name in pair.MODIFIED:
                sources[entry.name] = raw.decode()
    require(complete == state['original_inputs'])
    plan = original_idle_renderer()(sources, {name: complete[name] for name in pair.MODIFIED}, arm='native')
    expected = complete | {name: sha(value.encode()) for name, value in plan.items()}
    require(observed == expected)
    return dict(original_input_count=len(complete), observed_input_count=len(expected))


def unpack(raw, destination):
    require(len(raw) == BYTES and sha(raw) == DIGEST and destination.is_dir() and
            not destination.is_symlink() and not any(destination.iterdir()))
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        entries = archive.infolist()
        require(0 < len(entries) <= 4096 and len({e.filename for e in entries}) == len(entries) and
                sum(e.file_size for e in entries) <= MAX_EXPANDED)
        for entry in entries:
            name, mode = PurePosixPath(entry.filename), entry.external_attr >> 16
            require(not name.is_absolute() and '..' not in name.parts and
                    str(name) == entry.filename.rstrip('/') and
                    (not stat.S_IFMT(mode) or stat.S_ISREG(mode) or stat.S_ISDIR(mode)))
        state = load(archive.read('state.json'))
        require((state['harness'], state['source'], state['source_tree']) == (HARNESS, SOURCE, TREE) and
                state['profile'] == 'synthetic-settled-catalog-idle' and state['result'] == 'NOT_QUALIFIED')
        arms = state['arms']
        require(len(arms) == 1 and arms[0]['arm'] == 'native' and arms[0]['build_exit'] == 0 and
                arms[0]['test_exit'] == 65 and arms[0]['cleanup'] == 'PASS' and arms[0]['simulator'] == SIMULATOR)
        summary, tests = load(archive.read('native/summary.json')), load(archive.read('native/tests.json'))
        verify_results(summary, tests)
        source_binding = validate_source_inputs(state, load(archive.read('native/inputs.json')))
        manifest = {}
        for entry in entries:
            if entry.is_dir() or not entry.filename.startswith('native/tests.xcresult/'):
                continue
            require(entry.file_size <= 64 * 1024 * 1024)
            target = destination / entry.filename
            target.parent.mkdir(parents=True, exist_ok=True)
            content = archive.read(entry)
            with target.open('xb') as output:
                output.write(content)
            manifest[entry.filename] = sha(content)
        require(bool(manifest))
    return summary, tests, manifest, source_binding


def verify_results(summary, tests):
    counts = dict(totalTestCount=1, failedTests=1, passedTests=0, skippedTests=0, expectedFailures=0)
    require(all(type(summary.get(k)) is int and summary[k] == v for k, v in counts.items()) and
            summary.get('result') == 'Failed' and len(summary['testFailures']) == 1 and
            summary['testFailures'][0]['testIdentifierString'] == METHOD and
            summary['testFailures'][0]['failureText'] == 'failed - ' + FAILURE)
    configs, devices = summary.get('devicesAndConfigurations', []), tests.get('devices', [])
    require(len(configs) == len(devices) == 1)
    for device in (configs[0]['device'], devices[0]):
        require((device['deviceId'], device['architecture'], device['platform'], device['osVersion'], device['modelName']) ==
                (SIMULATOR, 'arm64', 'iOS Simulator', '18.5', 'iPhone 16 Pro'))
    cases = []
    def visit(nodes, depth=0):
        require(type(nodes) is list and depth <= 12)
        for node in nodes:
            require(type(node) is dict and len(cases) < 2)
            if node.get('nodeType') == 'Test Case':
                cases.append(node)
            visit(node.get('children', []), depth + 1)
    visit(tests['testNodes'])
    require(len(cases) == 1 and cases[0]['nodeIdentifier'] == METHOD and cases[0]['result'] == 'Failed')


class PublicTapEligibilityError(ValueError):
    def __init__(self, title):
        super().__init__('Public tap label shape is not source-bound')
        self.metadata = dict(bytes=len(title.encode()),
            expected_prefix=title.startswith('Tap "Open Word Impostor'),
            button_suffix=title.endswith('" Button'),
            has_line_break='\n' in title or '\r' in title,
            contains_public_word_title='Word Impostor' in title)


def public_observations(activities, summary, tests):
    # Failure is attested by the mandatory exact native summary/test tree.
    # Activity views need not repeat its failure association flag.
    verify_results(summary, tests)
    require(activities.get('testIdentifier') == METHOD)
    counts = dict(nodes=0, failures=0, taps=0, typing=0, swipes=0, queries=0)
    errors = {k: 0 for k in ('no_matches', 'multiple_matches', 'snapshot_failure', 'query_timeout')}
    spellings = dict(no_matches='no matches found', multiple_matches='multiple matching elements found',
                     snapshot_failure='failed to get matching snapshot', query_timeout='timed out')
    codes = set()
    def visit(node, depth=0):
        counts['nodes'] += 1
        require(counts['nodes'] <= 16384 and depth <= 24)
        if isinstance(node, dict):
            if 'title' in node:
                title = node['title']
                require(type(title) is str and len(title.encode()) <= 4096)
                if title.startswith('Tap '):
                    # Exact original public catalog description, never Host,
                    # permission, typing, room or private-game interactions.
                    if not (title.startswith('Tap "Open Word Impostor') and title.endswith('" Button') and
                            len(title.encode()) <= 1024 and '\n' not in title and '\r' not in title):
                        raise PublicTapEligibilityError(title)
                    counts['taps'] += 1
                counts['typing'] += int(title.startswith('Type '))
                counts['swipes'] += int(title.startswith('Swipe up'))
                counts['queries'] += int('Open Word Impostor' in title and
                                         title.startswith(('Find the ', 'Checking existence of ', 'Get number of matches')))
                for key, spelling in spellings.items():
                    errors[key] += int(spelling in title.lower())
                for value in re.findall(r'\bXCTestErrorDomain\s+Code=(-?\d{1,10})\b', title):
                    code = int(value)
                    require(-(2**31) <= code < 2**31 and len(codes | {code}) <= 16)
                    codes.add(code)
                if node.get('isAssociatedWithFailure') is True:
                    require(FAILURE in title)
                    counts['failures'] += 1
            for value in node.values():
                visit(value, depth + 1)
        elif isinstance(node, list):
            for value in node:
                visit(value, depth + 1)
    visit(activities)
    require(counts['failures'] <= 1 and counts['taps'] == 1 and counts['typing'] == 0)
    return dict(counts=counts, error_markers=errors, xctest_error_codes=sorted(codes),
                failure_provenance='Exact original summary and test tree; activity failure flags reported without substitution',
                scope='Fixed public failure categories only; no labels, values, activity graph or causal proof')


def original_object_digest(path, expected):
    # Empty xcresult bookkeeping objects are legitimate only when the original
    # immutable archive already attests their exact empty content. Images keep
    # the existing nonempty read_regular policy unchanged.
    if expected != sha(b''):
        return sha(attachments.read_regular(path, 64 * 1024 * 1024))
    identity = lambda st: (st.st_dev, st.st_ino, st.st_mode, st.st_nlink, st.st_size, st.st_mtime_ns, st.st_ctime_ns)
    before = path.lstat()
    require(stat.S_ISREG(before.st_mode) and before.st_nlink == 1 and before.st_size == 0)
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'rb') as stream:
        require(identity(os.fstat(stream.fileno())) == identity(before))
        require(stream.read(1) == b'')
        require(identity(os.fstat(stream.fileno())) == identity(before))
    require(identity(path.lstat()) == identity(before))
    return sha(b'')


def main():
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    require(platform.system() == 'Darwin' and platform.machine() == 'arm64' and
            os.environ.get('GITHUB_REPOSITORY') == REPOSITORY and os.environ.get('GITHUB_REF') == BRANCH and
            os.environ.get('GITHUB_EVENT_NAME') == 'push' and os.environ.get('RUNNER_ENVIRONMENT') == 'github-hosted' and
            os.environ.get('GITHUB_JOB') == 'review')
    require(not subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT).strip())
    require(subprocess.check_output(['/usr/bin/xcodebuild', '-version']).decode() == 'Xcode 26.3\nBuild version 17C529\n')
    require(subprocess.check_output(['git', 'rev-parse', SOURCE + '^{tree}'], cwd=ROOT).decode().strip() == TREE)
    out = args.output.absolute()
    require(out.parent == Path(os.environ['RUNNER_TEMP']).resolve(strict=True) and not out.exists() and not out.is_symlink())
    out.mkdir(mode=0o700)
    receipt = dict(run=RUN, artifact=ARTIFACT, digest=DIGEST, harness=HARNESS, source=SOURCE, tree=TREE,
                   reviewer=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip(),
                   result='NOT_REVIEWED', idle_stimulus_source_reached=True, unexpected_observer_tap=True,
                   positive_baseline_verified=False, shipping_qualified=False, physical_verified=False)
    def api(path):
        r = subprocess.run(['gh', 'api', 'repos/' + REPOSITORY + '/' + path], capture_output=True, timeout=90)
        require(r.returncode == 0 and len(r.stdout) <= 128 * 1024)
        return load(r.stdout)
    validate_metadata(api('actions/runs/' + str(RUN)), api('actions/artifacts/' + str(ARTIFACT)))
    archive = out / 'frozen.zip'
    with archive.open('xb') as output:
        r = subprocess.run(['gh', 'api', f'repos/{REPOSITORY}/actions/artifacts/{ARTIFACT}/zip'],
                           stdout=output, stderr=subprocess.PIPE, timeout=90)
    require(r.returncode == 0 and archive.stat().st_size == BYTES)
    inputs = out / 'inputs'; inputs.mkdir(mode=0o700)
    original_summary, original_tests, manifest, source_binding = unpack(attachments.read_regular(archive, BYTES), inputs)
    (out / 'extracted-inputs.json').write_text(json.dumps(manifest, indent=2))
    receipt['source_binding'] = source_binding
    def checkpoint():
        (out / 'receipt.json').write_text(json.dumps(receipt | {'commands': commands.rows}, indent=2))
    commands = Commands(child_environment(os.environ), time.monotonic() + 360, checkpoint)
    result = inputs / 'native/tests.xcresult'
    def read(argv, label):
        row, raw, stderr = commands.capture(['/usr/bin/xcrun', 'xcresulttool', *argv], label, 60, root=ROOT)
        require(row['status'] == 'EXITED' and row.get('exit_code') == 0 and row.get('direct_child_reaped') and
                not stderr and len(raw) <= 2 * 1024 * 1024)
        return raw
    try:
        decoded = {}
        for kind in ('summary', 'tests', 'test-details', 'activities'):
            argv = ['get', 'test-results', kind, '--path', str(result)]
            if kind in ('test-details', 'activities'):
                argv += ['--test-id', METHOD]
            decoded[kind] = load(read(argv, kind))
        require(decoded['summary'] == original_summary and decoded['tests'] == original_tests)
        verify_results(decoded['summary'], decoded['tests'])
        receipt['public_observations'] = public_observations(decoded['activities'], decoded['summary'], decoded['tests'])
        for kind in ('summary', 'tests'):
            (out / (kind + '.json')).write_text(json.dumps(decoded[kind], indent=2))
        attachments.verify_export_help(read(['help', 'export', 'attachments'], 'attachments-help'))
        exported = out / 'decoded-attachments'; exported.mkdir(mode=0o700)
        read(['export', 'attachments', '--path', str(result), '--output-path', str(exported), '--test-id', METHOD],
             'attachments-export')
        rows = attachments.curate_public_screenshots(exported, out)
        require(all(row['sha256'] in manifest.values() for row in rows))
        receipt['public_screenshots'] = rows
        receipt['result'] = 'FROZEN_DIAGNOSTIC_HELPER_FAILURE_DECODED_NOT_QUALIFICATION'
    except BaseException as error:
        for image in out.glob('public-settlement-*.png'):
            image.unlink()
        receipt.update(result='REJECTED', error_type=type(error).__name__)
        if isinstance(error, PublicTapEligibilityError):
            receipt['public_tap_eligibility'] = error.metadata
        raise
    finally:
        require(all(original_object_digest(inputs / name, digest) == digest
                    for name, digest in manifest.items()))
        receipt['result_inputs_unchanged'] = True
        checkpoint()
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
