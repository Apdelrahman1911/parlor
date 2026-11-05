"""Read the missing native pre-exec context from the frozen failed main.

No application execution, input replay or public native-log disclosure. Only
CMS-enveloped ciphertext is uploaded; the custody private key stays off GitHub.
The earlier successful numeric keyboard decode is NOT repeated here.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import time

import review_current_main_typing as frozen
from scripts.ci.native_process_probe import Commands, child_environment

SPEC = frozen.SPECS[0]
CERTIFICATE = Path(__file__).with_name('current_main_startup_custody.crt')
# Native platform processes only, in the existing failing public Name interval.
# Includes launchd's original service-startup records and the proxy/daemon
# processes; no application console, UI hierarchy, screenshots or gameplay.
PREDICATE = ('processIdentifier == 38943 OR process == "xpcproxy_sim" OR process == "kbd" '
             'OR process == "amfid" OR subsystem CONTAINS "com.apple.TextInput.kbd" '
             'OR subsystem CONTAINS "AppleMobileFileIntegrity"')


def envelope_command(source, destination, certificate):
    return ['/usr/bin/openssl', 'smime', '-encrypt', '-aes256', '-binary', '-outform', 'DER',
            '-in', str(source), '-out', str(destination), str(certificate)]


def validate_native_rows(raw):
    frozen.require(0 < len(raw) <= 4 * 1024**2)
    rows = json.loads(raw)
    frozen.require(isinstance(rows, list) and 0 < len(rows) <= 5000)
    for row in rows:
        frozen.require(isinstance(row, dict) and type(row.get('processID')) is int and
                       row['processID'] > 0 and row['processID'] != 41116 and
                       isinstance(row.get('eventMessage'), str))
        frozen.timestamp_us(row['timestamp'])
    return len(rows)


def main():
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument('--output', type=Path, required=True)
    out = parser.parse_args().output
    frozen.require(platform.system() == 'Darwin' and os.environ.get('GITHUB_REPOSITORY') == frozen.REPO and
                   os.environ.get('GITHUB_REF') == frozen.BRANCH and os.environ.get('GITHUB_EVENT_NAME') == 'push' and
                   os.environ.get('GITHUB_RUN_ATTEMPT') == '1' and os.environ.get('RUNNER_ENVIRONMENT') == 'github-hosted')
    frozen.require(out.is_absolute() and out.parent == Path(os.environ['RUNNER_TEMP']) and not out.exists())
    out.mkdir(mode=0o700)
    receipt = dict(result='RUNNING_NOT_QUALIFIED', source=os.environ['GITHUB_SHA'],
                   original_run=SPEC['run'], original_head=SPEC['head'], original_artifact=SPEC['artifact'],
                   application_executed=False, input_replayed=False, previous_numeric_decode_repeated=False,
                   shipping_qualified=False, physical_verified=False, plaintext_published=False,
                   certificate_sha256=hashlib.sha256(CERTIFICATE.read_bytes()).hexdigest())
    def checkpoint(**_):
        receipt['commands'] = commands.rows
        (out / 'receipt.json').write_text(json.dumps(receipt, indent=2)+'\n')
    env = child_environment(os.environ)
    commands = Commands(env, time.monotonic() + 480, checkpoint)
    def run(argv, label, timeout=60, auth=False):
        selected = dict(env, GH_TOKEN=os.environ['GH_TOKEN']) if auth else env
        row, raw, stderr = commands.capture(argv, label, timeout, root=frozen.ROOT, environment=selected)
        frozen.require(row['status'] == 'EXITED' and row['exit_code'] == 0 and row['direct_child_reaped'])
        return raw
    try:
        frozen.require(run(['/usr/bin/xcrun', 'xcodebuild', '-version'], 'toolchain') == b'Xcode 26.3\nBuild version 17C529\n')
        base = 'repos/' + frozen.REPO + '/actions/'
        original = json.loads(run(['gh', 'api', base+'runs/'+str(SPEC['run'])], 'original-run', auth=True))
        artifact = json.loads(run(['gh', 'api', base+'artifacts/'+str(SPEC['artifact'])], 'original-artifact', auth=True))
        frozen.validate_metadata(original, artifact, SPEC)
        archive = out / 'frozen.zip'
        with archive.open('xb') as stream:
            result = subprocess.run(['gh', 'api', base+'artifacts/'+str(SPEC['artifact'])+'/zip'],
                                    stdout=stream, stderr=subprocess.PIPE, stdin=subprocess.DEVNULL,
                                    env=dict(env, GH_TOKEN=os.environ['GH_TOKEN']), timeout=90, check=False)
        frozen.require(result.returncode == 0)
        inputs = out / 'frozen.xcresult'; inputs.mkdir(mode=0o700)
        manifest = frozen.unpack(archive, inputs, SPEC)
        expected = json.loads(Path(frozen.__file__).with_name('current_main_typing_manifest.json').read_text())
        frozen.require(manifest == expected[str(SPEC['run'])])
        exported = out / 'original-diagnostics'
        run(['/usr/bin/xcrun', 'xcresulttool', 'export', 'diagnostics', '--path', str(inputs),
             '--output-path', str(exported)], 'export-original', 120)
        archives = list(exported.rglob('system.logarchive')); frozen.require(len(archives) == 1)
        observed = frozen.directory_manifest(archives[0])
        frozen.require(frozen.sha(json.dumps(observed, sort_keys=True).encode()) ==
                       '39a952963d7323c55c4abe9c1e0171e8254ae0758110b94f6e720c295766e377')
        raw = run(['/usr/bin/log', 'show', '--archive', str(archives[0]), '--start', SPEC['start'],
                   '--end', SPEC['end'], '--timezone', 'UTC', '--info', '--debug', '--style', 'json',
                   '--predicate', PREDICATE], 'missing-native-startup-context', 90)
        count = validate_native_rows(raw)
        plaintext = out / 'private-native-startup.json'
        with plaintext.open('xb') as stream: stream.write(raw)
        ciphertext = out / 'native-startup.cms'
        run(envelope_command(plaintext, ciphertext, CERTIFICATE), 'seal-native-custody')
        sealed = ciphertext.read_bytes(); frozen.require(0 < len(sealed) <= 5 * 1024**2 and sealed != raw)
        frozen.require(all(frozen.sha((inputs/name).read_bytes()) == digest for name, digest in manifest.items()))
        receipt.update(result='ENCRYPTED_NATIVE_CONTEXT_CAPTURED_NOT_QUALIFIED', native_rows=count,
                       plaintext_sha256=frozen.sha(raw), plaintext_bytes=len(raw),
                       ciphertext_sha256=frozen.sha(sealed), ciphertext_bytes=len(sealed),
                       original_archive_sha256=SPEC['digest'], native_archive_manifest_sha256=
                       frozen.sha(json.dumps(observed, sort_keys=True).encode()))
    except BaseException as error:
        receipt.update(result='REJECTED_NOT_QUALIFIED', error_type=type(error).__name__)
        raise
    finally:
        receipt['all_commands_reaped'] = all(row.get('direct_child_reaped') is True for row in commands.rows)
        receipt['preservation'] = commands.preservation
        checkpoint()
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
