#!/usr/bin/env python3
"""One HTTPS-only synthetic jarsigner invocation; no Store artifact/key or production edits.

Root-lane/independent review and successful Darwin owned-child probe are required.
Sectigo documents its hostname for RFC3161 over HTTP, not an HTTPS guarantee.
This authorized probe NEVER falls back to HTTP or follows a redirect. JDK21 can
internally retry a failed write of the same synthetic request; see the README.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
import time
import uuid
import zipfile


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
JDK = Path('/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home').resolve()
TSA = 'https://timestamp.sectigo.com'
PASSWORD = 'parlor-disposable-fixture-only'
HELPER = ROOT / 'scripts/release/PrepareAndroidUploadTrust.java'
VALIDATOR = ROOT / 'scripts/release/validate_android_artifact.sh'
PROCESS_CONTROL = HERE.parent / 'android-arm64-runner/darwin_owned_processes.py'
PINS = {
    HELPER: 'a74b92902839047353aa4b5816bc226e43526960da7c4d2df117e25c9375c211',
    VALIDATOR: 'e0100ed6274e41113e18d1cdaa5310242f749d2f337fdb0c5b85920a011cece6',
    PROCESS_CONTROL: '148a71180b6768858f47d7cf5607104169898dafdee48ecbf41937cd46e71a1d',
}
MAX_LOG_BYTES = 1024 * 1024


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def bindings():
    result = {}
    for path, expected in PINS.items():
        if path.is_symlink() or not path.is_file() or digest(path) != expected:
            raise RuntimeError('Pinned source/control mismatch: ' + str(path))
        result[str(path)] = expected
    for path in (JDK / 'release', JDK / 'lib/security/cacerts', JDK / 'conf/security/java.security',
                 HERE / 'TimestampEvidence.java', HERE / 'OneTimestampInvocation.java', Path(__file__)):
        result[str(path)] = digest(path)
    return result


def load_process_control():
    name = 'parlor_tsa_owned_processes'
    spec = importlib.util.spec_from_file_location(name, PROCESS_CONTROL)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module.OwnedProcesses()


def owned_scratch_identity(path):
    info = path.lstat()
    if path.is_symlink() or not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid():
        raise RuntimeError('Unsafe task scratch ownership')
    return info.st_dev, info.st_ino, info.st_uid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', required=True, type=Path)
    args = parser.parse_args()
    initial = bindings()  # Fail before creating processes or signing fixtures.
    if not shutil.rmtree.avoids_symlink_attacks:
        raise RuntimeError('FD-safe tree removal unavailable')
    evidence = args.evidence_dir.absolute()
    evidence.mkdir(mode=0o700, parents=False, exist_ok=False)
    registry, scratch, identity, marker = None, None, None, None
    marker_written, creating_worker, pending_signal = False, False, None
    receipt = {'scope': 'Synthetic timestamped-JAR signature policy only; not an AAB/Store gate',
               'author': '/root/release_fix_review', 'started_epoch': time.time(),
               'tsa_url': TSA, 'attempts': 0,
               'attempt_unit': 'One jarsigner invocation, NOT exactly one outbound POST',
               'internal_retry_limit': 'JDK21 may retry a failed write of the same synthetic request once',
               'https_support': 'Unverified; official docs name HTTP endpoint',
               'before_bindings': initial, 'commands': [], 'assertions': [], 'status': 'RUNNING'}
    saved_handlers = {}

    def cancelled(signum, _frame):
        nonlocal pending_signal
        if creating_worker:
            pending_signal = signum
            return  # Register a successfully created child before cancellation.
        raise InterruptedError('Cancelled by signal ' + str(signum))

    def run(label, command, *, timeout=45):
        nonlocal creating_worker
        if len(registry.launches) >= 64:
            raise RuntimeError('Fixture command ceiling exceeded before launch')
        log = evidence / (label + '.log')
        entry = {'label': label, 'command': command, 'started_epoch': time.time(), 'log': str(log)}
        receipt['commands'].append(entry)
        process = None
        try:
            with log.open('xb') as output:
                creating_worker = True
                try:
                    process = subprocess.Popen(command, env=environment, cwd=scratch,
                                               stdin=subprocess.DEVNULL, stdout=output,
                                               stderr=subprocess.STDOUT, start_new_session=True)
                    launch = registry.register(process, label, command)
                finally:
                    creating_worker = False
                if pending_signal is not None:
                    raise InterruptedError('Cancelled during child creation by signal ' + str(pending_signal))
                deadline = time.monotonic() + timeout
                while launch.poll() is None:
                    if time.monotonic() >= deadline:
                        raise TimeoutError('Bounded command timeout: ' + label)
                    if log.stat().st_size > MAX_LOG_BYTES:
                        raise RuntimeError('Bounded command log exceeded: ' + label)
                    time.sleep(0.05)
                registry.finish(launch)
            if log.stat().st_size > MAX_LOG_BYTES:
                raise RuntimeError('Bounded command log exceeded: ' + label)
            return process.returncode, log
        finally:
            entry['finished_epoch'] = time.time()
            entry['exit_code_at_return'] = process.returncode if process else None

    def require(label, command, *, timeout=45):
        code, log = run(label, command, timeout=timeout)
        if code != 0:
            raise RuntimeError(f'{label} failed with exit {code}; inspect {log.name}')
        return log

    try:
        registry = load_process_control()  # Native self-capability check; no signal yet.
        scratch = Path(tempfile.mkdtemp(prefix='pl-tsa-', dir='/private/tmp'))
        identity = owned_scratch_identity(scratch)
        receipt['owned_scratch'] = str(scratch)
        receipt['owned_identity'] = identity
        marker = {'nonce': str(uuid.uuid4()), 'identity': identity}
        (scratch / 'owner.json').write_text(json.dumps(marker))
        marker_written = True
        for folder in ('home', 'tmp'):
            (scratch / folder).mkdir(mode=0o700)
        environment = {
            'PATH': str(JDK / 'bin') + ':/usr/bin:/bin:/usr/sbin:/sbin',
            'JAVA_HOME': str(JDK), 'HOME': str(scratch / 'home'),
            'TMPDIR': str(scratch / 'tmp'), 'LANG': 'C', 'LC_ALL': 'C',
            'JAVA_TOOL_OPTIONS': f'-Xmx256m -Duser.home={scratch / "home"} '
                                 f'-Djava.io.tmpdir={scratch / "tmp"} '
                                 '-Dhttp.maxRedirects=0 -Dsun.net.http.retryPost=false '
                                 '-Dsun.net.client.defaultReadTimeout=15000',
        }
        for signum in (signal.SIGINT, signal.SIGTERM):
            saved_handlers[signum] = signal.signal(signum, cancelled)
        # The early receipt allows a parent finalizer to retain scratch ownership on interruption.
        (evidence / 'started.json').write_text(json.dumps(receipt, indent=2) + '\n')
        require('jdk-version', [str(JDK / 'bin/java'), '-version'])
        key, public, archive = scratch / 'fixture.p12', scratch / 'fixture.der', scratch / 'fixture.jar'
        require('create-disposable-key', [str(JDK / 'bin/keytool'), '-genkeypair', '-alias', 'fixture',
                '-keystore', str(key), '-storetype', 'PKCS12', '-storepass', PASSWORD, '-keypass', PASSWORD,
                '-dname', 'CN=Disposable Parlor RFC3161 Fixture', '-keyalg', 'RSA', '-keysize', '2048',
                '-sigalg', 'SHA256withRSA', '-validity', '3650'])
        require('export-public-certificate', [str(JDK / 'bin/keytool'), '-exportcert', '-alias', 'fixture',
                '-keystore', str(key), '-storepass', PASSWORD, '-file', str(public)])
        expected = digest(public)
        with zipfile.ZipFile(archive, 'x') as jar:
            jar.writestr('base/assets/synthetic.txt', 'Disposable synthetic timestamp fixture only.')
            jar.writestr('META-INF/ordinary-payload.txt', 'This synthetic entry must also be signed.')
        receipt['attempts'] = 1
        require('one-https-rfc3161-attempt', [str(JDK / 'bin/java'), '--add-modules=jdk.jartool',
                '--add-exports=jdk.jartool/sun.security.tools.jarsigner=ALL-UNNAMED',
                str(HERE / 'OneTimestampInvocation.java'), '-keystore', str(key),
                '-storepass', PASSWORD, '-keypass', PASSWORD, '-sigalg', 'SHA256withRSA',
                '-digestalg', 'SHA-256', '-tsadigestalg', 'SHA-256', '-tsa', TSA,
                '-sigfile', 'UPLOAD', str(archive), 'fixture'], timeout=45)
        key.unlink()  # No verification step needs the private disposable key.
        receipt['timestamped_jar_sha256'] = digest(archive)
        source = VALIDATOR.read_text()
        begin, end = 'unzip -tqq "$aab"', 'java -jar "$bundletool" dump manifest'
        if source.count(begin) != 1 or source.count(end) != 1:
            raise RuntimeError('Production signature fragment boundary drift')
        fragment = source[source.index(begin):source.index(end)]
        receipt['production_fragment_sha256'] = hashlib.sha256(fragment.encode()).hexdigest()
        script = 'set -euo pipefail\naab=$1\ntemporary_dir=$2\nexpected_certificate=$3\nrepo_root=$4\n' + fragment

        def verify(label, bundle, fingerprint):
            output = scratch / label
            output.mkdir()
            code, log = run(label, ['/bin/bash', '-c', script, 'synthetic-signature-check',
                                    str(bundle), str(output), fingerprint, str(ROOT)])
            # Public synthetic diagnostics only; no private key or artifact is copied out.
            for name in ('upload-trust.txt', 'jarsigner.txt'):
                produced = output / name
                if produced.exists():
                    if produced.stat().st_size > MAX_LOG_BYTES:
                        raise RuntimeError('Signature diagnostic exceeds bound')
                    shutil.copyfile(produced, evidence / (label + '-' + name))
            return code, output

        code, positive = verify('positive-exact-production-fragment', archive, expected)
        if code != 0 or not (positive / 'jarsigner.txt').is_file():
            raise RuntimeError('Timestamped approved self-signed fixture did not pass strict production verification')
        log = require('inspect-real-timestamp', [str(JDK / 'bin/java'), str(HERE / 'TimestampEvidence.java'),
                                               str(archive), expected])
        rows = [line for line in log.read_text().splitlines() if line.startswith('{')]
        if len(rows) != 1:
            raise RuntimeError('Missing structural timestamp evidence')
        receipt['timestamp'] = json.loads(rows[0])
        receipt['assertions'].append('PASS: real timestamped approved self-signed fixture + strict production fragment')
        wrong = ('0' if expected[0] != '0' else '1') + expected[1:]
        code, negative = verify('wrong-approved-fingerprint', archive, wrong)
        if code == 0 or 'Android upload certificate mismatch' not in (negative / 'upload-trust.txt').read_text():
            raise RuntimeError('Wrong fingerprint not rejected at the intended boundary')
        receipt['assertions'].append('PASS: timestamp did not bypass approved fingerprint')
        tampered = scratch / 'tampered.jar'
        with zipfile.ZipFile(archive) as original, zipfile.ZipFile(tampered, 'x') as changed:
            for item in original.infolist():
                changed.writestr(item, b'tampered synthetic bytes' if item.filename == 'base/assets/synthetic.txt'
                                 else original.read(item))
        code, negative = verify('tampered-timestamped-payload', tampered, expected)
        if code == 0 or 'digest error' not in (negative / 'upload-trust.txt').read_text():
            raise RuntimeError('Tampered timestamped payload not rejected by digest verification')
        receipt['assertions'].append('PASS: timestamp did not bypass signed payload integrity')
        receipt['status'] = 'PASS'
    except BaseException as error:
        receipt['status'] = 'FAIL'
        receipt['failure'] = type(error).__name__ + ': ' + str(error)
    finally:
        # A second INT/TERM must not abort owned cleanup midway.
        for signum in saved_handlers:
            signal.signal(signum, lambda *_: None)
        cleanup_errors = []
        if registry is not None:
            try:
                cleanup_errors.extend(registry.shutdown())
            except BaseException as error:
                cleanup_errors.append({'process_cleanup_error': str(error)})
            receipt['workers'] = registry.receipts()
            receipt['token_signals'] = registry.events
        try:
            receipt['after_bindings'] = bindings()
            receipt['bindings_unchanged'] = receipt['after_bindings'] == initial
            if not receipt['bindings_unchanged']:
                cleanup_errors.append({'error': 'Source/control/JDK public security binding changed'})
        except BaseException as error:
            cleanup_errors.append({'binding_error': str(error)})
        if scratch is not None and not cleanup_errors:
            try:
                if owned_scratch_identity(scratch) != identity:
                    raise RuntimeError('Owned scratch inode/UID changed; preserved')
                if marker_written:
                    fd = os.open(scratch / 'owner.json', os.O_RDONLY | os.O_NOFOLLOW)
                    with os.fdopen(fd) as marker_file:
                        actual = json.loads(marker_file.read(1024))
                    if actual != json.loads(json.dumps(marker)):
                        raise RuntimeError('Owned scratch marker changed; preserved')
                shutil.rmtree(scratch)
                receipt['scratch_removed'] = not scratch.exists()
            except BaseException as error:
                cleanup_errors.append({'scratch_cleanup_error': str(error)})
        for entry in receipt['commands']:
            log = Path(entry['log'])
            if log.exists():
                original_size = log.stat().st_size
                if original_size > MAX_LOG_BYTES:
                    with log.open('r+b') as output:
                        output.truncate(MAX_LOG_BYTES)
                    entry['truncated_from_bytes'] = original_size
                    cleanup_errors.append({'oversized_command_log': entry['label']})
                entry['sha256'] = digest(log)
        receipt['cleanup_errors'] = cleanup_errors
        if cleanup_errors:
            receipt['status'] = 'FAIL'
        receipt['finished_epoch'] = time.time()
        (evidence / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
        for signum, handler in saved_handlers.items():
            signal.signal(signum, handler)
    print(receipt['status'] + ': ' + str(evidence / 'receipt.json'))
    return 0 if receipt['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
