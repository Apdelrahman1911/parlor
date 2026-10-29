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

import public_attachments

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
METHOD = public_attachments.METHOD
DRIVER = 'iosApp/iosAppUITests/IOSAppLaunchUITests.swift'
DRIVER_SHA256 = 'c65fe65aee8442aa06f02007b57daaf3e9113f5f61046edb59e361c2e4a6e115'
NUMBER = r'-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?'
# This frozen original arm used six sequential-query fields. The newer
# coherent-snapshot driver has a seventh error-code field; do not import its
# schema merely because that driver happens to be in the reviewer's checkout.
FRAME = r'Optional\(\((' + NUMBER + r', ){3}' + NUMBER + r'\)\)'
SAMPLE = re.compile(
    r'at=' + NUMBER + r', querySeconds=' + NUMBER +
    r', frame=(?:nil|' + FRAME + r'), enabled=(?:true|false), '
    r'stable=(?:true|false), hittable=(?:nil|Optional\((?:true|false)\))\Z')


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
        original_inputs = json.loads(archive.read('original/inputs.json'))
        require(state['original_inputs'][DRIVER] == original_inputs[DRIVER] == DRIVER_SHA256)
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


class SettlementSampleError(ValueError):
    """Bounded numeric rejection metadata only; never the rejected log string."""
    def __init__(self, samples, parts, field):
        super().__init__('Public numeric log candidate rejected')
        self.metadata = dict(bytes=len(samples.encode()), samples=len(parts),
                             field=field if field in ('emittedOutput', 'name', 'title', 'text', 'message') else 'other')


def stderr_summary(raw):
    """Closed-vocabulary error tokens, never raw stderr, paths or arbitrary words.

    This explains a failed CLI read without hypothesizing its message or silently
    accepting it. Unknown tokens are redacted, not retained; failure stays sticky.
    """
    allowed = frozenset(('error', 'no', 'console', 'action', 'build', 'log', 'logs',
                         'data', 'was', 'were', 'is', 'are', 'not', 'found', 'available',
                         'result', 'bundle', 'missing', 'unavailable', ':', '.', '-'))
    tokens = []
    if len(raw) <= 4096:
        for token in re.findall(r'[A-Za-z]+|[^\sA-Za-z]', raw.decode('ascii', errors='replace'))[:128]:
            normalized = token.lower()
            tokens.append(normalized if normalized in allowed else '[redacted]')
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest(), tokens=tokens,
                bounded=len(raw) <= 4096)


class FrozenLogReadError(ValueError):
    def __init__(self, row, stderr):
        super().__init__('Frozen log CLI read failed')
        self.metadata = dict(exit_code=row.get('exit_code'),
                             direct_child_reaped=row.get('direct_child_reaped') is True,
                             stderr=stderr_summary(stderr))


class ConsoleLogUnavailable(FrozenLogReadError):
    """Exact installed-tool absence, not a decoder error or successful log read."""


def log_payload(row, raw, stderr, kind):
    require(kind in ('action', 'console'))
    exited = row['status'] == 'EXITED' and row.get('direct_child_reaped') is True
    if exited and row.get('exit_code') == 0 and not stderr:
        return json.loads(raw)
    # Actual Xcode26.3 observation40828492426: byte-exact absence of this
    # optional view. Do not accept other errors, missing action data, truncated
    # output, timeouts, unreaped children or strings merely quoting this error.
    if kind == 'console' and exited and row.get('exit_code') == 1 and not raw and \
            stderr == b'Error: No console log available\n':
        raise ConsoleLogUnavailable(row, stderr)
    raise FrozenLogReadError(row, stderr)


def settlement_lines(value):
    """Retain only the original driver's bounded numeric diagnostic grammar."""
    lines = []
    prefix = 'Public control settlement: '

    def visit(node, field='other'):
        if isinstance(node, dict):
            for key, child in node.items():
                visit(child, key)
        elif isinstance(node, list):
            for child in node:
                visit(child, field)
        elif isinstance(node, str):
            for line in node.splitlines():
                # Action logs can contain source/context strings quoting the
                # marker. A marker embedded in another string is not an emitted
                # diagnostic line; never parse arbitrary source as numeric data.
                if not line.startswith(prefix):
                    continue
                samples = line[len(prefix):]
                parts = samples.split('; ')
                if not (0 < len(parts) <= 12 and len(samples) <= 16384 and
                        all(SAMPLE.fullmatch(part) for part in parts)):
                    raise SettlementSampleError(samples, parts, field)
                require(len(lines) < 16)
                lines.append(prefix + samples)
    visit(value)
    return lines


def collect_log_samples(read_view):
    lines, errors, counts, unavailable = [], {}, {}, {}
    for kind in ('action', 'console'):
        try:
            found = settlement_lines(read_view(kind))
            counts[kind] = len(found)
            lines.extend(found)
        except ValueError as error:
            # A bad read cannot suppress the other independent observation.
            if kind == 'console' and isinstance(error, ConsoleLogUnavailable):
                unavailable[kind] = error.metadata
                counts[kind] = 0
                continue
            errors[kind] = dict(error_type=type(error).__name__)
            if isinstance(error, SettlementSampleError):
                errors[kind]['candidate'] = error.metadata
            elif isinstance(error, FrozenLogReadError):
                errors[kind]['command'] = error.metadata
    require(len(lines) <= 32)
    # XCTest emitted output belongs to the action view (installed CLI help).
    # A console-only sample cannot substitute for a failed/empty action read.
    return dict(lines=lines, errors=errors, counts=counts, unavailable=unavailable,
                complete=bool(counts.get('action')) and not errors)


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
    require(hashlib.sha256(subprocess.check_output(['git', 'show', SOURCE + ':' + DRIVER],
                                                  cwd=ROOT)).hexdigest() == DRIVER_SHA256)
    out = args.output.absolute()
    require(out.parent == Path(os.environ['RUNNER_TEMP']).resolve(strict=True) and
            not out.exists() and not out.is_symlink())
    out.mkdir(mode=0o700)
    receipt = dict(run=RUN, artifact=ARTIFACT, digest=DIGEST, source=SOURCE, producer=HEAD,
                   driver_sha256=DRIVER_SHA256, settlement_schema='six-field-sequential-query',
                   reviewer=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip(),
                   result='NOT_REVIEWED', public_attachments='NOT_REVIEWED',
                   production_qualified=False, app_execution=False)
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

    def read_view(kind):
        row, raw, stderr = commands.capture(
            ['/usr/bin/xcrun', 'xcresulttool', *log_command(result, kind)], kind + '-log', 60)
        return log_payload(row, raw, stderr, kind)

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
        collected = collect_log_samples(read_view)
        lines, errors = collected['lines'], collected['errors']
        for kind, count in collected['counts'].items():
            receipt[kind + '_settlement_lines'] = count
        (out / 'settlement-lines.json').write_text(json.dumps(lines, indent=2))
        receipt['log_errors'] = errors
        receipt['unavailable_log_views'] = collected['unavailable']
        receipt['unique_settlement_lines'] = len(set(lines))
        # Original command receipts have duplicate '*-tests' labels. Keep rows,
        # not a dict that silently substitutes JSON decoding time for XCTest.
        receipt['execution_times'] = [dict(label=r['label'], seconds=r['elapsed_seconds'])
            for r in state['commands'] if 'elapsed_seconds' in r and
            any('test-without-building' == argument for argument in r['command'])]
        receipt['result'] = 'LOG_SAMPLES_REJECTED' if errors else \
            'FROZEN_ACTION_DECODED_CONSOLE_UNAVAILABLE' if collected['complete'] and collected['unavailable'] else \
            'FROZEN_RESULT_DECODED' if collected['complete'] else 'NO_PUBLIC_ACTION_SETTLEMENT_SAMPLES'
        require(collected['complete'])
        # This exact failed method stopped before any text/room operation.
        # Export only its already recorded public failure attachments. Raw
        # manifests and recordings stay on this runner; curate bounded PNGs.
        receipt['public_attachments'] = 'EXPORT_STARTED'
        public_attachments.verify_public_failure(json.loads((out / 'activities.json').read_bytes()), METHOD)
        helptext = read(['help', 'export', 'attachments'], 'attachments-help')
        (out / 'attachments-help.txt').write_bytes(helptext)
        public_attachments.verify_export_help(helptext)
        exported = out / 'decoded-attachments'; exported.mkdir(mode=0o700)
        read(public_attachments.attachment_command(result, exported, method=METHOD), 'attachments-export')
        receipt['public_screenshots'] = public_attachments.curate_public_screenshots(exported, out)
        receipt['public_attachments'] = 'CURATED_PUBLIC_PNGS'
    finally:
        for name, digest in manifest.items():
            require(hashlib.sha256((inputs / name).read_bytes()).hexdigest() == digest)
        receipt['result_inputs_unchanged'] = True
        checkpoint()
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
