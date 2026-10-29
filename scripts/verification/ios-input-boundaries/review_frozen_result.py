"""Decode one retained failed xcresult; never build, launch, tap or allocate a device.

This explicitly bound investigation consumes an existing public-setup artifact.
It is not a retry, a result mutation, or production qualification.
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
import time
import zipfile

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.ci.native_process_probe import Commands, child_environment

REPOSITORY = 'Apdelrahman1911/parlor'
BRANCH = 'refs/heads/diagnostic/ios-xcresult-review-2026-10-29'
RUN = 40814363116
ARTIFACT = 12874429803
HEAD = 'd7fd8d2d9b2e2e617ee2fa1bd22ef167400bc242'
SOURCE = '841a0b0623b508eae103f79ba7b4e5773e6a8b4d'
DIGEST = '41d75ff88eeb1f3a1e39e1f66b45d1b028ff98bb8cff06f3e026ab23d4ab85dc'
BYTES = 90110713
MAX_BYTES = 128 * 1024 * 1024
METHOD = 'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInArabic()'
NUMBER = r'-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?'
SAMPLE = re.compile(
    r'at=' + NUMBER + r', querySeconds=' + NUMBER +
    r', frame=(?:nil|Optional\([\d.eE()+, -]*\)), enabled=(?:true|false), '
    r'stable=(?:true|false), hittable=(?:nil|Optional\((?:true|false)\)), '
    r'snapshotErrorCode=(?:nil|Optional\(-?\d+\))\Z')


def require(value):
    if not value:
        raise ValueError('Frozen result ownership or bounded-input check failed')


def validate_metadata(run, artifact):
    require(run['id'] == RUN and run['head_sha'] == HEAD and run['run_attempt'] == 1 and
            run['status'] == 'completed' and run['event'] == 'push' and
            run['path'] == '.github/workflows/ios-input-investigation.yml' and
            run['head_branch'] == 'diagnostic/ios-input-probe-2026-10-29' and
            run['repository']['full_name'] == REPOSITORY and
            run['head_repository']['full_name'] == REPOSITORY)
    require(artifact['id'] == ARTIFACT and artifact['name'] == f'ios-input-investigation-{RUN}-1' and
            artifact['expired'] is False and artifact['size_in_bytes'] == BYTES and
            artifact['digest'] == 'sha256:' + DIGEST and
            artifact['workflow_run']['id'] == RUN and artifact['workflow_run']['head_sha'] == HEAD)


def unpack(raw, destination):
    """Check the complete ZIP before copying only the original result, exclusively."""
    require(len(raw) <= MAX_BYTES and hashlib.sha256(raw).hexdigest() == DIGEST)
    require(destination.is_dir() and not destination.is_symlink() and not any(destination.iterdir()))
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        entries = archive.infolist()
        require(0 < len(entries) <= 4096 and len({i.filename for i in entries}) == len(entries) and
                sum(i.file_size for i in entries) <= MAX_BYTES)
        for entry in entries:
            name = PurePosixPath(entry.filename)
            mode = entry.external_attr >> 16
            require(not name.is_absolute() and '..' not in name.parts and
                    str(name) == entry.filename.rstrip('/') and
                    (not stat.S_IFMT(mode) or stat.S_ISREG(mode) or stat.S_ISDIR(mode)))
        state = json.loads(archive.read('state.json'))
        require(state['source'] == SOURCE and state['harness'] == HEAD)
        arms = state['arms']
        require(len(arms) == 3 and arms[0]['arm'] == 'original' and
                arms[0]['result'] == 'JOURNEYS_FAILED' and arms[0]['test_exit'] == 65 and
                arms[0]['cleanup'] == 'PASS')
        summary = json.loads(archive.read('original/summary.json'))
        require(summary['totalTestCount'] == 2 and summary['failedTests'] == 1 and
                summary['passedTests'] == 1 and summary['skippedTests'] == 0 and
                summary['testFailures'][0]['testIdentifierString'] == METHOD)
        manifest = {}
        for entry in entries:
            if entry.is_dir() or not entry.filename.startswith('original/tests.xcresult/'):
                continue
            target = destination / entry.filename
            target.parent.mkdir(parents=True, exist_ok=True)
            content = archive.read(entry)
            with target.open('xb') as output:
                output.write(content)
            manifest[entry.filename] = hashlib.sha256(content).hexdigest()
        require(bool(manifest))
    return state, summary, manifest


def log_command(result, kind):
    require(kind in ('action', 'console'))
    return ['get', 'log', '--path', str(result), '--type', kind]


def settlement_lines(value):
    """Retain only the original driver's bounded numeric diagnostic grammar."""
    lines = []
    prefix = 'Public control settlement: '

    def visit(node):
        if isinstance(node, dict):
            for child in node.values():
                visit(child)
        elif isinstance(node, list):
            for child in node:
                visit(child)
        elif isinstance(node, str):
            for line in node.splitlines():
                if prefix not in line:
                    continue
                samples = line.split(prefix, 1)[1]
                parts = samples.split('; ')
                require(0 < len(parts) <= 12 and len(samples) <= 16384 and
                        all(SAMPLE.fullmatch(part) for part in parts))
                require(len(lines) < 16)
                lines.append(prefix + samples)
    visit(value)
    return lines


def main():
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    require(platform.system() == 'Darwin' and platform.machine() == 'arm64' and
            os.environ.get('GITHUB_REPOSITORY') == REPOSITORY and
            os.environ.get('GITHUB_REF') == BRANCH and os.environ.get('GITHUB_EVENT_NAME') == 'push' and
            os.environ.get('RUNNER_ENVIRONMENT') == 'github-hosted' and
            os.environ.get('GITHUB_JOB') == 'ios')
    require(not subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT).strip())
    require(subprocess.check_output(['/usr/bin/xcodebuild', '-version']).decode() ==
            'Xcode 26.3\nBuild version 17C529\n')
    out = args.output.absolute()
    require(out.parent == Path(os.environ['RUNNER_TEMP']).resolve(strict=True) and
            not out.exists() and not out.is_symlink())
    out.mkdir(mode=0o700)
    receipt = dict(run=RUN, artifact=ARTIFACT, digest=DIGEST, source=SOURCE, producer=HEAD,
                   reviewer=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip(),
                   result='NOT_REVIEWED', production_qualified=False, app_execution=False)
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2))

    def api(path):
        row = subprocess.run(['gh', 'api', 'repos/' + REPOSITORY + '/' + path],
                             capture_output=True, timeout=90)
        require(row.returncode == 0 and len(row.stdout) <= 128 * 1024)
        return json.loads(row.stdout)

    def checkpoint():
        (out / 'receipt.json').write_text(json.dumps(receipt | {'commands': commands.rows}, indent=2))

    # Credentials are used by gh for this exact read-only public artifact only;
    # they are never passed to Xcode, logged, uploaded or written into a result.
    validate_metadata(api('actions/runs/' + str(RUN)), api('actions/artifacts/' + str(ARTIFACT)))
    archive = out / 'frozen.zip'
    with archive.open('xb') as output:
        rc = subprocess.run(['gh', 'api', f'repos/{REPOSITORY}/actions/artifacts/{ARTIFACT}/zip'],
                            stdout=output, stderr=subprocess.PIPE, timeout=90).returncode
    require(rc == 0 and archive.stat().st_size == BYTES)
    inputs = out / 'inputs'; inputs.mkdir()
    state, summary, manifest = unpack(archive.read_bytes(), inputs)
    (out / 'original-summary.json').write_text(json.dumps(summary, indent=2))
    (out / 'extracted-inputs.json').write_text(json.dumps(manifest, indent=2))
    receipt['archive_verified'] = True
    # Commands strips tokens and environment hooks and has bounded output,
    # child retirement, deadlines and evidence preservation. Unique labels here
    # distinguish actual test execution from the old '*-tests' JSON decoder.
    commands = Commands(child_environment(os.environ), time.monotonic() + 360, checkpoint)
    result = inputs / 'original/tests.xcresult'

    def read(argv, label):
        row, raw, _ = commands.capture(['/usr/bin/xcrun', 'xcresulttool', *argv], label, 60)
        require(row['status'] == 'EXITED' and row.get('exit_code') == 0 and row.get('direct_child_reaped'))
        return raw

    try:
        for kind in ('test-details', 'activities'):
            helptext = read(['help', 'get', 'test-results', kind], kind + '-help')
            (out / (kind + '-help.txt')).write_bytes(helptext)
            require(b'--test-id' in helptext and b'--path' in helptext)
            raw = read(['get', 'test-results', kind, '--path', str(result), '--test-id', METHOD], kind)
            json.loads(raw)
            (out / (kind + '.json')).write_bytes(raw)
        helptext = read(['help', 'get', 'log'], 'logs-help')
        (out / 'logs-help.txt').write_bytes(helptext)
        require(all(token in helptext for token in (b'--path', b'--type', b'console', b'action')))
        # Two declared read-only views of the existing result, not fallback
        # execution or a replay. Verify their real installed CLI first.
        lines = []
        for kind in ('action', 'console'):
            decoded = json.loads(read(log_command(result, kind), kind + '-log'))
            found = settlement_lines(decoded)
            receipt[kind + '_settlement_lines'] = len(found)
            lines.extend(found)
        require(len(lines) <= 32)
        (out / 'settlement-lines.json').write_text(json.dumps(lines, indent=2))
        # Original command receipts have duplicate '*-tests' labels. Keep rows,
        # not a dict that silently substitutes JSON decoding time for XCTest.
        receipt['execution_times'] = [dict(label=r['label'], seconds=r['elapsed_seconds'])
            for r in state['commands'] if 'elapsed_seconds' in r and
            any('test-without-building' == argument for argument in r['command'])]
        receipt['result'] = 'FROZEN_RESULT_DECODED' if lines else 'NO_PUBLIC_SETTLEMENT_SAMPLES'
        require(bool(lines))
    finally:
        for name, digest in manifest.items():
            require(hashlib.sha256((inputs / name).read_bytes()).hexdigest() == digest)
        receipt['result_inputs_unchanged'] = True
        checkpoint()
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
