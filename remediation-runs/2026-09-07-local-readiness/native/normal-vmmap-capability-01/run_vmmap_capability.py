#!/usr/bin/env python3
"""One root-run, harmless-child vmmap attempt; never application provenance."""
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import signal
import stat
import subprocess
import sys
import tempfile
import threading
import time

HERE = Path(__file__).resolve().parent
EVIDENCE = HERE.parents[1] / 'evidence'
CONTROLS = {'README.md', 'run_vmmap_capability.py', 'run_control_tests.py',
            'test_vmmap_capability.py'}
ENV = {'PATH': '/usr/bin:/bin', 'LC_ALL': 'C'}
SLEEP = ['/bin/sleep', '60']
LIMIT = 256 * 1024
SECONDS = 25
PUBLIC_WORDS = frozenset(('vmmap sleep process pid task port for could cannot not '
    'obtain get failed failure error operation permitted permission denied is '
    'because it no longer appears to be running examine the target this often '
    'protected debuggable a an requested access invalid argument resource '
    'shortage kernel mach rights unavailable unable read memory map of must '
    'run as root sudo insufficient privilege privileges entitlement entitlements '
    'requires unsupported supported architecture architectures rosetta translated '
    '(os/kern) (os/errno) /bin/sleep /usr/bin/vmmap').split())


class ProbeFailure(Exception):
    """Only closed, source-authored reason codes enter retained evidence."""


class Signals:
    def __enter__(self):
        self.pending = []
        self.saved = {s: signal.getsignal(s) for s in (signal.SIGINT, signal.SIGTERM)}
        for s in self.saved:
            signal.signal(s, self.receive)
        return self

    def receive(self, signum, _frame):
        if signum not in self.pending:
            self.pending.append(signum)

    def check(self):
        if self.pending:
            raise ProbeFailure('INTERRUPTED')

    def __exit__(self, *_):
        for s, handler in self.saved.items():
            signal.signal(s, handler)


def control_manifest():
    present = {p.name for p in HERE.iterdir() if p.suffix in {'.py', '.md'}}
    if present != CONTROLS:
        raise ProbeFailure('CONTROL_SET_CHANGED')
    rows = []
    for name in sorted(CONTROLS):
        p = HERE / name
        if p.is_symlink() or not p.is_file():
            raise ProbeFailure('CONTROL_NOT_REGULAR')
        rows.append({'path': name, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()})
    digest = hashlib.sha256(json.dumps(rows, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    return {'files': rows, 'sha256': digest}


def checked_tmp(raw):
    p = Path(raw)
    if (not raw or not p.is_absolute() or str(p) != raw or p.resolve(strict=True) != p or
            p.name != 'tmp' or p.parent.name != 'scratch' or
            p.parent.parent.parent != EVIDENCE or
            not re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*', p.parent.parent.name)):
        raise ProbeFailure('NOT_OUTER_CYCLE_TMPDIR')
    for directory in (p, p.parent, p.parent.parent, EVIDENCE):
        value = directory.lstat()
        if (not stat.S_ISDIR(value.st_mode) or value.st_uid != os.getuid() or
                value.st_mode & 0o022):
            raise ProbeFailure('UNSAFE_TMPDIR_OWNER')
    return p


def file_identity(value):
    return value.st_dev, value.st_ino, value.st_uid, stat.S_IFMT(value.st_mode)


class OwnedOutput:
    def __init__(self, parent):
        self.parent, self.fd, self.name, self.identity = parent, None, None, None
        self.parent_identity = file_identity(parent.lstat())

    def allocate(self):
        # Signals are deferred through both descriptor/name acquisition and fstat.
        self.fd, self.name = tempfile.mkstemp(prefix='owned-sleep-vmmap-', dir=self.parent)
        self.identity = file_identity(os.fstat(self.fd))

    def read(self):
        os.lseek(self.fd, 0, os.SEEK_SET)
        raw = bytearray()
        while len(raw) <= LIMIT:
            part = os.read(self.fd, min(8192, LIMIT + 1 - len(raw)))
            if not part:
                return bytes(raw)
            raw.extend(part)
        raise ProbeFailure('RAW_FILE_EXCEEDED_BOUND')

    def cleanup(self):
        if self.fd is None:
            return
        try:
            expected = self.identity or file_identity(os.fstat(self.fd))
            current = Path(self.name).lstat()
            if (file_identity(self.parent.lstat()) != self.parent_identity or
                    file_identity(os.fstat(self.fd)) != expected or
                    file_identity(current) != expected or current.st_uid != os.getuid() or
                    not stat.S_ISREG(current.st_mode) or current.st_nlink != 1):
                raise ProbeFailure('RAW_FILE_OWNERSHIP_CHANGED')
            os.unlink(self.name)
        finally:
            os.close(self.fd)
            self.fd = None


def write_all(fd, chunk):
    while chunk:
        count = os.write(fd, chunk)
        if count <= 0:
            raise ProbeFailure('RAW_WRITE_FAILED')
        chunk = chunk[count:]


def capture(mapper, output, deadline, signals):
    """Do not poll/wait/reap the sleep child anywhere in this function."""
    count = 0
    os.set_blocking(mapper.stdout.fileno(), False)
    with selectors.DefaultSelector() as selector:
        selector.register(mapper.stdout, selectors.EVENT_READ)
        while True:
            signals.check()
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise ProbeFailure('VMMAP_TIMEOUT')
            if not selector.select(min(0.2, remaining)):
                continue
            try:
                part = os.read(mapper.stdout.fileno(), min(8192, LIMIT - count + 1))
            except BlockingIOError:
                continue
            if not part:
                break
            write_all(output, part[:LIMIT - count])
            count += len(part)
            if count > LIMIT:
                raise ProbeFailure('VMMAP_OUTPUT_LIMIT')
    signals.check()
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise ProbeFailure('VMMAP_TIMEOUT')
    try:
        return mapper.wait(timeout=remaining)
    except subprocess.TimeoutExpired:
        raise ProbeFailure('VMMAP_TIMEOUT') from None


def retire(process):
    """Only retained Popen objects may be signalled; no numeric PID kill API."""
    if process is None:
        return {'started': False, 'reaped': True}
    try:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=3)
        else:
            process.wait(timeout=0)
        return {'started': True, 'pid': process.pid, 'reaped': True,
                'exit_code': process.returncode}
    finally:
        if process.stdout is not None:
            process.stdout.close()


def diagnostic(raw, child_pid):
    """Closed vocabulary only. Unknown text/paths/addresses become hashes."""
    lines = []
    for line in raw.split(b'\n', 16)[:16]:
        tokens = []
        for token in line[:1024].split()[:64]:
            word = token.decode('ascii', errors='replace').lower().rstrip('.,:;')
            if child_pid is not None and word.strip('[]()') == str(child_pid):
                tokens.append('OWNED_SLEEP_PID')
            elif word in PUBLIC_WORDS:
                tokens.append(word)
            else:
                tokens.append({'sha256': hashlib.sha256(token).hexdigest(), 'bytes': len(token)})
        lines.append({'bytes': len(line), 'sha256': hashlib.sha256(line).hexdigest(),
                      'tokens': tokens, 'bounded': len(line) > 1024 or len(line.split()) > 64})
    return {'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest(),
            'lines': lines, 'line_limit_reached': raw.count(b'\n') >= 16,
            'interpretation': 'TOKEN_DIAGNOSTIC_ONLY_NO_INFERRED_FAILURE_CAUSE'}


def run_probe(parent):
    report = {'kind': 'OWNED_SLEEP_VMMAP_CAPABILITY_NOT_APP_PROVENANCE',
              'proves_app_provenance': False, 'mapping_identity_validated': False,
              'timeout_seconds': SECONDS, 'output_limit_bytes': LIMIT, 'errors': []}
    child = mapper = None
    output = OwnedOutput(parent)
    with Signals() as signals:
        try:
            output.allocate()
            signals.check()
            child = subprocess.Popen(SLEEP, env=ENV, stdin=subprocess.DEVNULL,
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                     close_fds=True)
            report['sleep_pid'] = child.pid
            if child.poll() is not None:
                raise ProbeFailure('SLEEP_EXITED_BEFORE_VMMAP')
            signals.check()
            command = ['/usr/bin/vmmap', '-w', str(child.pid)]
            report['command'] = command
            deadline = time.monotonic() + SECONDS
            mapper = subprocess.Popen(command, env=ENV, stdin=subprocess.DEVNULL,
                                      stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                      close_fds=True)
            report['vmmap_exit_code'] = capture(mapper, output.fd, deadline, signals)
            # Mapper is now reaped. Until here the live reference + default
            # SIGCHLD + single-threaded owner deliberately kept sleep unreaped.
            if child.poll() is not None:
                raise ProbeFailure('SLEEP_EXITED_DURING_VMMAP')
        except BaseException as error:
            report['errors'].append(str(error) if isinstance(error, ProbeFailure) else type(error).__name__)
        finally:
            for name, process in (('mapper', mapper), ('sleep', child)):
                if name == 'sleep' and not report.get('mapper_cleanup', {}).get('reaped'):
                    report['sleep_cleanup'] = {'reaped': False, 'reason': 'MAPPER_NOT_REAPED'}
                    report['errors'].append('SLEEP_RETAINED_UNREAPED')
                    continue
                try:
                    report[name + '_cleanup'] = retire(process)
                except BaseException as error:
                    report['errors'].append(name.upper() + '_CLEANUP_' + type(error).__name__)
            try:
                if output.fd is not None:
                    raw = output.read()
                    report['captured_bytes'] = len(raw)
                    report['captured_sha256'] = hashlib.sha256(raw).hexdigest()
                    if report.get('vmmap_exit_code') != 0 or report['errors']:
                        report['failure_diagnostic'] = diagnostic(raw, report.get('sleep_pid'))
            except BaseException as error:
                report['errors'].append('DIAGNOSTIC_' + type(error).__name__)
            try:
                output.cleanup()
                report['raw_output_removed'] = True
            except BaseException as error:
                report['raw_output_removed'] = False
                report['retained_raw_basename'] = Path(output.name).name if output.name else None
                report['errors'].append('OUTPUT_CLEANUP_' + type(error).__name__)
            report['signals'] = list(signals.pending)
    success = report.get('vmmap_exit_code') == 0 and not report['errors'] and not report['signals']
    report['status'] = 'COMMAND_SUCCEEDED_NOT_APP_PROVENANCE' if success else 'FAILED_OR_INTERRUPTED'
    return report, (128 + report['signals'][0] if report['signals'] else (0 if success else 1))


def main():
    before = control_manifest()
    if sys.argv[1:] == ['--manifest']:
        print(json.dumps(before, sort_keys=True))
        return 0
    if len(sys.argv) != 2 or not re.fullmatch(r'[0-9a-f]{64}', sys.argv[1]) or sys.argv[1] != before['sha256']:
        raise ProbeFailure('REVIEWED_CONTROL_HASH_REQUIRED')
    if (sys.platform != 'darwin' or threading.active_count() != 1 or
            signal.getsignal(signal.SIGCHLD) != signal.SIG_DFL):
        raise ProbeFailure('DARWIN_SINGLE_OWNER_DEFAULT_SIGCHLD_REQUIRED')
    parent = checked_tmp(os.environ.get('TMPDIR', ''))
    report, code = run_probe(parent)
    report['controls_before'] = before
    try:
        report['controls_after'] = control_manifest()
    except Exception as error:
        report['controls_after'] = {'error_type': type(error).__name__}
    report['controls_unchanged'] = report['controls_before'] == report['controls_after']
    if not report['controls_unchanged']:
        report['errors'].append('CONTROL_DRIFT')
        report['status'] = 'FAILED_OR_INTERRUPTED'
    print(json.dumps(report, sort_keys=True), flush=True)
    return code if report['controls_unchanged'] else 1


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except ProbeFailure as error:
        print(json.dumps({'status': 'REFUSED', 'reason': str(error), 'proves_app_provenance': False}), flush=True)
        raise SystemExit(1) from None
    except Exception as error:
        # In particular, invalid external TMPDIR must not leak paths via traceback.
        print(json.dumps({'status': 'REFUSED', 'error_type': type(error).__name__,
                          'proves_app_provenance': False}), flush=True)
        raise SystemExit(1) from None
