"""Read the new independently failed main keyboard trace with Apple's decoder; no app or input replay.

Only closed numeric/categorical observations are eligible for public upload.
Native raw messages and diagnostic files stay in the ephemeral runner directory.
"""
import argparse
import hashlib
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
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.ci.native_process_probe import Commands, child_environment

BRANCH = 'refs/heads/diagnostic/ios-main-typing-retained-2026-11-05'
REPO = 'Apdelrahman1911/parlor'
SPECS = (
    dict(run=42030154806, artifact=13418233344, bytes=134503819,
         digest='7433c4d2e5d38a53a42d4beaecfa50ff9e2b8da66e8b1f1d496c0dafc7649079',
         head='40b02d74ce8e1529120e2a9bf7ee160ccb09e2c2', conclusion='failure', event='push',
         workflow='.github/workflows/production-verification.yml',
         artifact_name='ios-runtime-verification', prefix='parlor/parlor/build/ci-evidence/ios-ui-tests.xcresult/',
         start='2026-11-05 13:16:17', end='2026-11-05 13:16:58', apps=[41116]),
)

def require(value):
    if not value:
        raise ValueError('retained-keyboard-evidence-rejected')


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def stream_sha(stream):
    # Apple's /usr/bin/python3 is 3.9; avoid the Python 3.11 file_digest API.
    digest = hashlib.sha256()
    for chunk in iter(lambda: stream.read(1024 * 1024), b''):
        digest.update(chunk)
    return digest.hexdigest()


def validate_metadata(run, artifact, spec):
    require(run['id'] == spec['run'] and run['status'] == 'completed' and
            run['conclusion'] == spec['conclusion'] and run['run_attempt'] == 1 and
            run['head_sha'] == spec['head'] and run['event'] == spec['event'] and
            run['path'] == spec['workflow'] and run['repository']['full_name'] == REPO and
            run['head_repository']['full_name'] == REPO)
    require(artifact['id'] == spec['artifact'] and not artifact['expired'] and
            artifact['size_in_bytes'] == spec['bytes'] and artifact['name'] == spec['artifact_name'] and
            artifact['digest'] == 'sha256:' + spec['digest'] and
            artifact['workflow_run']['id'] == spec['run'] and
            artifact['workflow_run']['head_sha'] == spec['head'])


def unpack(archive, destination, spec):
    require(archive.stat().st_size == spec['bytes'])
    with archive.open('rb') as stream:
        require(stream_sha(stream) == spec['digest'])
    require(destination.is_dir() and not destination.is_symlink() and not any(destination.iterdir()))
    manifest = {}
    with zipfile.ZipFile(archive) as z:
        entries = z.infolist()
        require(0 < len(entries) <= 5000 and len({r.filename for r in entries}) == len(entries) and
                sum(r.file_size for r in entries) <= 200 * 1024**2)
        for r in entries:
            name, mode = PurePosixPath(r.filename), r.external_attr >> 16
            require(not name.is_absolute() and '..' not in name.parts and '\\' not in r.filename and
                    str(name) == r.filename.rstrip('/') and all(ord(c) >= 32 for c in r.filename) and
                    (not stat.S_IFMT(mode) or stat.S_ISREG(mode) or stat.S_ISDIR(mode)))
            if r.is_dir() or not r.filename.startswith(spec['prefix']):
                continue
            relative = r.filename.removeprefix(spec['prefix'])
            require(relative and r.file_size <= 128 * 1024**2)
            target = destination / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            raw = z.read(r)
            with target.open('xb') as f:
                f.write(raw)
            manifest[relative] = sha(raw)
    require('Info.plist' in manifest and len(manifest) > 50)
    return manifest


def directory_manifest(root):
    require(root.is_dir() and not root.is_symlink())
    result = {}
    for path in root.rglob('*'):
        require(not path.is_symlink() and (path.is_dir() or path.is_file()))
        if path.is_dir():
            continue
        require(path.stat().st_size <= 256 * 1024**2 and len(result) < 1000)
        with path.open('rb') as stream:
            result[str(path.relative_to(root))] = dict(
                sha256=stream_sha(stream), bytes=path.stat().st_size)
    require(sum(r['bytes'] for r in result.values()) <= 512 * 1024**2)
    return result


def timestamp_us(value):
    require(isinstance(value, str) and re.fullmatch(
        r'2026-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?(?:\+0000|\+00:00|Z)', value))
    # Normalize Apple's compact UTC offset for Python 3.9's stricter ISO parser.
    normalized = value[:-5] + '+00:00' if value.endswith('+0000') else value.replace('Z', '+00:00')
    dt = datetime.fromisoformat(normalized)
    require(dt.utcoffset().total_seconds() == 0)
    delta = dt - datetime(1970, 1, 1, tzinfo=timezone.utc)
    return (delta.days * 86400 + delta.seconds) * 1000000 + delta.microseconds


def reduce_rows(raw, spec):
    require(len(raw) <= 2 * 1024**2)
    rows = json.loads(raw)
    require(isinstance(rows, list) and len(rows) <= 2000)
    result = []
    for r in rows:
        require(isinstance(r, dict))
        pid, message = r.get('processID'), r.get('eventMessage', '')
        require(type(pid) is int and pid > 0 and isinstance(message, str))
        category = None
        if pid in spec['apps'] and r.get('subsystem') == 'com.apple.UIKit' and r.get('category') == 'KeyboardUI':
            category = {'Keyboard receives keyEvent type: 4; subtype: 0': 'key-event',
                        'Keyboard inserts text': 'insert-text'}.get(message)
        elif 'com.apple.TextInput.kbd' in r.get('subsystem', ''):
            if message in ('service state: spawning', 'service state: xpcproxy', 'service state: running'):
                category = message.removeprefix('service state: ')
            elif re.fullmatch(r'xpcproxy_sim spawned with pid \d+', message):
                category = 'proxy-spawn'
            elif re.fullmatch(r'Successfully spawned kbd\[\d+\] because ipc \(mach\)', message):
                category = 'daemon-running'
        if category is None:
            continue
        tid = r.get('threadID')
        require(type(tid) is int and tid > 0)
        result.append(dict(time_us=timestamp_us(r['timestamp']), pid=pid, thread=tid, category=category))
    require(result and len(result) <= 100 and len({tuple(r.values()) for r in result}) == len(result))
    result.sort(key=lambda r: r['time_us'])
    apps = []
    for pid in spec['apps']:
        events = [r for r in result if r['pid'] == pid and r['category'] == 'key-event']
        insertions = [r for r in result if r['pid'] == pid and r['category'] == 'insert-text']
        require(len(events) == len(insertions) == 10)
        apps.append(dict(pid=pid, events=len(events), insertions=len(insertions),
            first_event_us=events[0]['time_us'], last_event_us=events[-1]['time_us'],
            first_insertion_us=insertions[0]['time_us'], last_insertion_us=insertions[-1]['time_us'],
            same_thread=len({r['thread'] for r in events + insertions}) == 1,
            first_event_to_first_insertion_us=insertions[0]['time_us']-events[0]['time_us']))
    lifecycle = [r for r in result if r['category'] not in ('key-event', 'insert-text')]
    require(all(sum(r['category'] == category for r in lifecycle) == 1
                for category in ('proxy-spawn', 'xpcproxy', 'daemon-running', 'running')))
    return dict(rows=result, applications=apps,
        proxy_to_daemon_running_us=next(r['time_us'] for r in lifecycle if r['category'] == 'daemon-running') -
                                  next(r['time_us'] for r in lifecycle if r['category'] == 'proxy-spawn'))


def predicate(spec):
    apps = ' OR '.join('processIdentifier == ' + str(pid) for pid in spec['apps'])
    return ('((' + apps + ') AND subsystem == "com.apple.UIKit" AND category == "KeyboardUI" '
            'AND (eventMessage == "Keyboard receives keyEvent type: 4; subtype: 0" OR '
            'eventMessage == "Keyboard inserts text")) OR '
            '(subsystem CONTAINS "com.apple.TextInput.kbd")')


def validate_log_help(row, stdout, stderr):
    # Apple log's explicit help reports EX_USAGE (64) and writes to stderr.
    # Only this read-only usage command admits 64; every decode still requires 0.
    require(row['command'] == ['/usr/bin/log', 'help', 'show'] and
            row['status'] == 'EXITED' and row['direct_child_reaped'] and
            row['exit_code'] in (0, 64))
    helptext = (stdout + stderr).decode()
    # The installed short usage is not a complete capability listing. The
    # actual fixed-argument decode below (exit 0 + exact rows) is authoritative.
    require('log show' in helptext and '--predicate' in helptext and '--style' in helptext)
    return dict(sha256=sha(stdout + stderr),
                options={token: token in helptext for token in
                         ('--archive', '--start', '--end', '--predicate', '--style', '--timezone')})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    require(platform.system() == 'Darwin' and os.environ.get('GITHUB_REPOSITORY') == REPO and
            os.environ.get('GITHUB_REF') == BRANCH and os.environ.get('GITHUB_EVENT_NAME') == 'push' and
            os.environ.get('GITHUB_RUN_ATTEMPT') == '1')
    out = args.output
    require(out.is_absolute() and not out.exists())
    out.mkdir(mode=0o700)
    receipt = dict(result='RUNNING', source=os.environ['GITHUB_SHA'], captures=[], commands=[],
                   application_executed=False, input_replayed=False, shipping_qualified=False,
                   application_cause_established=False, physical_verified=False)
    def checkpoint(**_):
        receipt['commands'] = commands.rows
        (out / 'receipt.json').write_text(json.dumps(receipt, indent=2)+'\n')
    env = child_environment(os.environ)
    commands = Commands(env, time.monotonic() + 480, checkpoint)
    def run(argv, label, timeout=60, auth=False):
        selected = dict(env, GH_TOKEN=os.environ['GH_TOKEN']) if auth else env
        row, raw, _ = commands.capture(argv, label, timeout, root=ROOT, environment=selected)
        require(row['status'] == 'EXITED' and row['exit_code'] == 0 and row['direct_child_reaped'])
        return raw
    try:
        xcode = run(['/usr/bin/xcrun', 'xcodebuild', '-version'], 'toolchain').decode()
        require(xcode == 'Xcode 26.3\nBuild version 17C529\n')
        helptext = run(['/usr/bin/xcrun', 'xcresulttool', 'help', 'export', 'diagnostics'], 'diagnostics-help').decode()
        require(all(token in helptext for token in ('--path', '--output-path')))
        row, stdout, stderr = commands.capture(['/usr/bin/log', 'help', 'show'], 'log-show-help', 60, root=ROOT)
        receipt['installed_log_usage'] = validate_log_help(row, stdout, stderr)
        expected = json.loads(Path(__file__).with_name('current_main_typing_manifest.json').read_text())
        for spec in SPECS:
            base = f'repos/{REPO}/actions/'
            original = json.loads(run(['gh', 'api', base+'runs/'+str(spec['run'])], 'run-'+str(spec['run']), auth=True))
            artifact = json.loads(run(['gh', 'api', base+'artifacts/'+str(spec['artifact'])], 'artifact-'+str(spec['run']), auth=True))
            validate_metadata(original, artifact, spec)
            archive = out / (str(spec['run'])+'.zip')
            started = time.monotonic()
            with archive.open('xb') as f:
                downloaded = subprocess.run(['gh', 'api', base+'artifacts/'+str(spec['artifact'])+'/zip'],
                    stdout=f, stderr=subprocess.PIPE, env=dict(env, GH_TOKEN=os.environ['GH_TOKEN']),
                    stdin=subprocess.DEVNULL, timeout=90, check=False)
            require(downloaded.returncode == 0)
            inputs = out / (str(spec['run'])+'.xcresult'); inputs.mkdir(mode=0o700)
            manifest = unpack(archive, inputs, spec)
            require(manifest == expected[str(spec['run'])])
            exported = out / ('diagnostics-'+str(spec['run']))
            run(['/usr/bin/xcrun', 'xcresulttool', 'export', 'diagnostics', '--path', str(inputs),
                 '--output-path', str(exported)], 'export-'+str(spec['run']), 120)
            matches = list(exported.rglob('system.logarchive'))
            if not matches:
                receipt['captures'].append(dict(run=spec['run'], artifact=spec['artifact'],
                    original_head=spec['head'], archive_sha256=spec['digest'], archive_bytes=spec['bytes'],
                    original_result_manifest_sha256=sha(json.dumps(manifest, sort_keys=True).encode()),
                    native_decoded=False, native_archive_status='UNAVAILABLE_IN_ORIGINAL_RESULT'))
                checkpoint()
                continue
            require(len(matches) == 1)
            observed = directory_manifest(matches[0])
            raw = run(['/usr/bin/log', 'show', '--archive', str(matches[0]), '--start', spec['start'],
                       '--end', spec['end'], '--timezone', 'UTC', '--info', '--debug', '--style', 'json',
                       '--predicate', predicate(spec)], 'decode-'+str(spec['run']), 90)
            reduced = reduce_rows(raw, spec)
            require(all(sha((inputs/name).read_bytes()) == digest for name,digest in manifest.items()))
            receipt['captures'].append(dict(run=spec['run'], artifact=spec['artifact'],
                original_head=spec['head'], archive_sha256=spec['digest'], archive_bytes=spec['bytes'],
                original_result_manifest_sha256=sha(json.dumps(manifest, sort_keys=True).encode()),
                native_archive_manifest_sha256=sha(json.dumps(observed, sort_keys=True).encode()),
                native_archive_files=len(observed), native_output_sha256=sha(raw),
                native_decoded=True, review_seconds=round(time.monotonic()-started, 3), **reduced))
            checkpoint()
        receipt['result'] = 'ARCHIVE_ONLY_READ_COMPLETE_NOT_APPLICATION_QUALIFICATION'
    except BaseException as error:
        receipt.update(result='REJECTED', error_type=type(error).__name__)
        raise
    finally:
        receipt['all_commands_reaped'] = all(row.get('direct_child_reaped') is True for row in commands.rows)
        receipt['preservation'] = commands.preservation
        checkpoint()
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
