"""Explicit, journal-owned simulator lifecycle; not AppHost/build ownership.

No action on import. The only subprocesses this module can request are the six
closed simctl lifecycle forms below. Authority over a CLI is its unreaped Popen
handle; authority over a simulator is the fresh fsynced exact-resource journal.
Neither authority authorizes app/image observations, build workers or shared
CoreSimulator services. Legacy wrappers do not instantiate this helper.
"""
from contextlib import contextmanager
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import selectors
import signal
import stat
import subprocess
import time

DIRECT = 'direct-owned-v1'
LEGACY = 'legacy-apphost'
QUALIFIED = 'qualified-xcode-26.3'
DEVELOPER = '/Applications/Xcode_26.3.app/Contents/Developer'
RUNTIME = 'com.apple.CoreSimulator.SimRuntime.iOS-26-2'
DEVICE_TYPE = 'com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro'
UUID = re.compile(r'[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}\Z')
NAME = re.compile(r'Parlor-Audit-parlor-audit-ios-readiness-[0-9]{2}-[A-Za-z0-9_-]{1,64}\Z')
LIMIT = 2 * 1024 * 1024  # Each live-drained stream, including metadata stderr.
MAX_COMMANDS = 96
MAX_JOURNAL = 1024 * 1024
CLEANUP_SECONDS = 480  # Independent lifecycle ceiling inside the unchanged outer600s grace.
RETIRE_SECONDS = 10
SIGNALS = {signal.SIGINT, signal.SIGTERM}


def require(value, code):
    if not value:
        raise RuntimeError('direct-simulator-' + code)


def failure(error):
    # Only this module's closed diagnostic vocabulary is retained. Never str()
    # an arbitrary exception into evidence (TimeoutExpired may carry raw output).
    result = dict(type=type(error).__name__)
    if type(error) is RuntimeError and re.fullmatch(r'direct-simulator-[a-z-]{1,96}', str(error)):
        result['code'] = str(error).removeprefix('direct-simulator-')
    elif isinstance(error, subprocess.TimeoutExpired):
        result['code'] = 'command-timeout'
    elif isinstance(error, (KeyboardInterrupt, SystemExit)):
        result['code'] = 'cancelled'
    else:
        result['code'] = 'unexpected-exception'
    return result


def encoded(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def selected_lifecycle(arguments, toolchain):
    values = [item for item in arguments if item.startswith('--simulator-lifecycle')]
    require(len(values) <= 1, 'duplicate-selector')
    mode = LEGACY if not values else DIRECT
    require(not values or values == ['--simulator-lifecycle=' + DIRECT], 'unknown-selector')
    require(mode == LEGACY or toolchain == QUALIFIED, 'qualified-profile-required')
    return mode, [item for item in arguments if item not in values]


def validate_selection(mode, toolchain):
    require(mode in (LEGACY, DIRECT), 'unknown-mode')
    require(mode == LEGACY or toolchain == QUALIFIED, 'qualified-profile-required')


@contextmanager
def defer_signals():
    """Protect handle assignment, without passing a blocked mask to the child."""
    pending = []
    mask = signal.pthread_sigmask(signal.SIG_BLOCK, SIGNALS)
    previous = {}
    try:
        require(not SIGNALS.intersection(mask), 'inherited-blocked-cancellation')
        for sig in SIGNALS:
            previous[sig] = signal.signal(sig, lambda value, _frame: pending.append(value) if len(pending) < 16 else None)
        signal.pthread_sigmask(signal.SIG_SETMASK, mask)
        yield pending
    finally:
        signal.pthread_sigmask(signal.SIG_BLOCK, SIGNALS)
        for sig, handler in previous.items():
            signal.signal(sig, handler)
        signal.pthread_sigmask(signal.SIG_SETMASK, mask)


def custody(path, *, private=False):
    path = Path(path)
    info = path.lstat()
    require(path.is_absolute() and path.resolve(strict=True) == path and stat.S_ISDIR(info.st_mode) and
            info.st_uid == os.getuid() == os.geteuid() and
            (not private or stat.S_IMODE(info.st_mode) == 0o700), 'directory-custody')
    return dict(path=str(path), device=info.st_dev, inode=info.st_ino, uid=info.st_uid)


def inventory(raw):
    """Validate the full bounded inventory; retain no unrelated device records."""
    require(isinstance(raw, bytes) and 0 < len(raw) <= LIMIT, 'inventory-bounds')
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, 'inventory-duplicate-key')
            result[key] = value
        return result
    try:
        value = json.loads(raw, object_pairs_hook=pairs)
    except (ValueError, UnicodeError):
        raise RuntimeError('direct-simulator-inventory-json') from None
    require(isinstance(value, dict) and set(value) == {'devices'} and isinstance(value['devices'], dict) and
            0 < len(value['devices']) <= 64, 'inventory-shape')
    result = {}
    for runtime, rows in value['devices'].items():
        require(re.fullmatch(r'com\.apple\.CoreSimulator\.SimRuntime\.[A-Za-z0-9_.-]{1,96}', runtime) and
                isinstance(rows, list) and len(rows) <= 512, 'inventory-runtime')
        for row in rows:
            require(isinstance(row, dict) and isinstance(row.get('udid'), str) and UUID.fullmatch(row['udid']) and
                    isinstance(row.get('name'), str) and 0 < len(row['name']) <= 256 and
                    all(ord(c) >= 32 and ord(c) != 127 for c in row['name']) and
                    row.get('state') in {'Shutdown', 'Booted', 'Creating', 'Booting', 'Shutting Down', 'Deleting'} and
                    type(row.get('isAvailable')) is bool and isinstance(row.get('deviceTypeIdentifier'), str) and
                    re.fullmatch(r'com\.apple\.CoreSimulator\.SimDeviceType\.[A-Za-z0-9_.-]{1,96}',
                                 row['deviceTypeIdentifier']), 'inventory-device')
            key = row['udid'].upper()
            require(key not in result and len(result) < 2048, 'inventory-duplicate-or-count')
            result[key] = dict(udid=key, name=row['name'], state=row['state'], runtime=runtime,
                               device_type=row['deviceTypeIdentifier'], available=row['isAvailable'])
    return result


class OwnedSimulatorLifecycle:
    def __init__(self, *, root, temporary, evidence, cycle, name, toolchain, source, approved,
                 receipt, save, current_bindings, environment):
        validate_selection(DIRECT, toolchain)
        require(re.fullmatch(r'ios-readiness-[0-9]{2}', cycle) and NAME.fullmatch(name) and
                name == 'Parlor-Audit-' + Path(temporary).name and
                Path(temporary).name.startswith('parlor-audit-' + cycle + '-'), 'exact-owned-name')
        require(isinstance(source, dict) and re.fullmatch(r'[a-f0-9]{64}', approved), 'source-control-binding')
        require(environment.get('DEVELOPER_DIR') == DEVELOPER, 'developer-selection')
        self.root, self.temporary, self.evidence = Path(root), Path(temporary), Path(evidence)
        self.cycle, self.name = cycle, name
        self.source_hash, self.approved = sha(encoded(source)), approved
        self.temporary_custody, self.evidence_custody = custody(self.temporary, private=True), custody(self.evidence)
        require(self.evidence.name == cycle and self.root.resolve(strict=True) == self.root, 'evidence-source-root')
        home = Path(environment.get('HOME', ''))
        require(home.is_absolute() and home.resolve(strict=True) == home and
                home == Path.home().resolve(strict=True), 'account-home')
        # The child receives no loader, probe, Gradle, tracking, private signing
        # or arbitrary caller options. DEVELOPER_DIR is a closed reviewed value.
        self.environment = dict(HOME=str(home), PATH='/usr/bin:/bin:/usr/sbin:/sbin',
                                LANG='en_US.UTF-8', LC_ALL='C', DEVELOPER_DIR=DEVELOPER)
        self.device_set = home / 'Library/Developer/CoreSimulator/Devices'
        self.receipt, self.save, self.current_bindings = receipt, save, current_bindings
        self.journal = self.evidence / 'simulator-lifecycle.jsonl'
        self.journal_identity, self.journal_bytes = None, b''
        self.baseline = None
        self.uuid = None
        self.children = []
        self.creation_intent = False
        self.creation_started = False
        self.creation_dispatched = False
        self.creation_outcome = 'NOT_ATTEMPTED'
        self.build_attempted = False
        self.barrier = dict(status='NOT_RUN')
        self.failed, self.evidence_failed, self.cleanup_failed = False, False, False
        self.cleanup_started, self.absent, self.finished = False, False, False
        self.cleanup_deadline = None
        self.errors = []
        self.rows = []
        self.header = dict(schema_version=1, mode=DIRECT, cycle=cycle, nonce=secrets.token_hex(32),
                           repository=str(self.root), source_sha256=self.source_hash, control_sha256=approved,
                           temporary=self.temporary_custody, evidence=self.evidence_custody,
                           name=name, runtime=RUNTIME, device_type=DEVICE_TYPE, developer_dir=DEVELOPER)
        # No file, subprocess or simulator allocation in this constructor.

    def _error(self, stage, error, *, cleanup=False):
        self.failed = True
        if cleanup:
            self.cleanup_failed = True
        if len(self.errors) < 64:
            self.errors.append(dict(stage=stage, **failure(error)))

    def _bindings(self):
        require(custody(self.temporary, private=True) == self.temporary_custody and
                custody(self.evidence) == self.evidence_custody, 'custody-changed')
        source, approved = self.current_bindings()
        require(sha(encoded(source)) == self.source_hash and approved == self.approved, 'source-or-control-changed')
        require(self.header['mode'] == DIRECT and self.header['cycle'] == self.cycle and self.header['name'] == self.name and
                self.header['runtime'] == RUNTIME and self.header['device_type'] == DEVICE_TYPE, 'intent-context-changed')

    def _check_journal(self):
        self._bindings()
        require(self.journal_identity is not None, 'journal-absent')
        before = self.journal.lstat()
        require(stat.S_ISREG(before.st_mode) and before.st_nlink == 1 and before.st_uid == os.getuid() and
                stat.S_IMODE(before.st_mode) == 0o600 and
                (before.st_dev, before.st_ino, before.st_uid) == self.journal_identity and
                before.st_size == len(self.journal_bytes) <= MAX_JOURNAL, 'journal-custody-changed')
        fd = os.open(self.journal, os.O_RDONLY | os.O_NOFOLLOW)
        try:
            current = os.fstat(fd)
            require((current.st_dev, current.st_ino, current.st_size, current.st_mtime_ns) ==
                    (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns), 'journal-replaced')
            with os.fdopen(fd, 'rb', closefd=False) as stream:
                raw = stream.read(MAX_JOURNAL + 1)
            after = os.fstat(fd)
            require(raw == self.journal_bytes and (after.st_size, after.st_mtime_ns) ==
                    (before.st_size, before.st_mtime_ns), 'journal-mutated')
        finally:
            os.close(fd)

    def _append(self, event, *, initial=False):
        try:
            self._bindings()
            if not initial:
                self._check_journal()
            raw = encoded(event) + b'\n'
            require(len(self.journal_bytes) + len(raw) <= MAX_JOURNAL, 'journal-bound')
            flags = os.O_WRONLY | os.O_NOFOLLOW | (os.O_CREAT | os.O_EXCL if initial else os.O_APPEND)
            fd = os.open(self.journal, flags, 0o600)
            try:
                info = os.fstat(fd)
                observed = (info.st_dev, info.st_ino, info.st_uid)
                require(stat.S_ISREG(info.st_mode) and info.st_nlink == 1 and info.st_uid == os.getuid() and
                        (initial or observed == self.journal_identity), 'journal-open-custody')
                if initial:
                    self.journal_identity = observed
                offset = 0
                while offset < len(raw):
                    written = os.write(fd, raw[offset:])
                    require(written > 0, 'journal-short-write')
                    offset += written
                os.fsync(fd)
            finally:
                os.close(fd)
            directory = os.open(self.evidence, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
            try:
                os.fsync(directory)
            finally:
                os.close(directory)
            self.journal_bytes += raw
            self._check_journal()
        except BaseException as error:
            self.evidence_failed = True
            self._error('journal-persistence', error, cleanup=self.cleanup_started)
            raise

    def _initialize(self):
        if self.journal_identity is None:
            self._append(dict(event='allocation', at=now(), intent=self.header), initial=True)
            self._publish()

    def summary(self):
        reaped = sum(row.get('reaped') is True for _, row in self.children)
        return dict(schema_version=1, mode=DIRECT, cycle=self.cycle, source_sha256=self.source_hash,
                    control_sha256=self.approved, temporary_custody=dict(self.temporary_custody),
                    evidence_custody=dict(self.evidence_custody), journal=dict(path=self.journal.name,
                    sha256=sha(self.journal_bytes), bytes=len(self.journal_bytes),
                    identity=list(self.journal_identity) if self.journal_identity is not None else None),
                    creation_intent=self.creation_intent, creation_dispatched=self.creation_dispatched,
                    creation_outcome=self.creation_outcome,
                    owned_uuid=self.uuid, build_attempted=self.build_attempted, postbuild_barrier=dict(self.barrier),
                    commands=self.rows, direct_children=dict(started=len(self.children), reaped=reaped,
                    unreaped=len(self.children) - reaped, status='PASS' if reaped == len(self.children) else 'FAIL'),
                    owned_device_absent=self.absent, evidence_failed=self.evidence_failed,
                    cleanup_budget_seconds=CLEANUP_SECONDS,
                    errors=list(self.errors), cleanup_status=('PASS' if self.finished and not self.cleanup_failed and
                    not self.evidence_failed and self.absent and reaped == len(self.children) else 'FAIL' if self.finished else 'BLOCKED'))

    def _publish(self, *, cleanup=False):
        self.receipt['simulator_lifecycle'] = self.summary()
        try:
            self.save()
        except BaseException as error:
            self.evidence_failed = True
            self._error('receipt-persistence', error, cleanup=cleanup)
            self.receipt['simulator_lifecycle'] = self.summary()
            if not cleanup:
                raise

    def _retire(self, child, row):
        """Never signal a numeric PID/group; only this exact owned Popen handle."""
        secondary = row.setdefault('secondary_errors', [])
        with defer_signals() as interrupted:
            try:
                code = child.wait(timeout=0)
                require(type(code) is int, 'noninteger-wait-result')
                row.update(exit_code=code, reaped=True)
            except BaseException as error:
                if not isinstance(error, subprocess.TimeoutExpired):
                    secondary.append(dict(stage='initial-wait', **failure(error)))
                for action, wait in (('terminate', 5), ('kill', 5)):
                    try:
                        getattr(child, action)()
                    except BaseException as error:
                        secondary.append(dict(stage=action, **failure(error)))
                    try:
                        code = child.wait(timeout=wait)
                        require(type(code) is int, 'noninteger-wait-result')
                        row.update(exit_code=code, reaped=True)
                        break
                    except BaseException as error:
                        secondary.append(dict(stage=action + '-wait', **failure(error)))
        if interrupted:
            secondary.append(dict(stage='retirement-cancelled', type='KeyboardInterrupt', code='cancelled'))
        if row.get('reaped') is not True:
            secondary.append(dict(stage='unreaped-direct-child', type='RuntimeError', code='direct-child-not-reaped'))

    def _capture(self, operation, arguments, timeout, *, cleanup=False):
        # Only callers below can choose argv. Revalidate again at the lowest
        # launch seam so mutations never become generic execution authority.
        require(arguments == self._arguments(operation), 'closed-command')
        require(timeout == (45 if operation == 'inventory' else 300 if operation == 'bootstatus' else 120), 'closed-timeout')
        require(not self.finished and len(self.rows) < MAX_COMMANDS, 'command-count-or-finished')
        require(cleanup or not self.failed, 'sticky-failure-no-new-work')
        if cleanup:
            require(self.cleanup_deadline is not None and
                    self.cleanup_deadline - time.monotonic() >= timeout + RETIRE_SECONDS,
                    'cleanup-budget-no-full-command-allowance')
        self._initialize()
        row = dict(ordinal=len(self.rows) + 1, operation=operation, command=arguments, timeout_seconds=timeout,
                   started_at=now(), status='RUNNING', cleanup=cleanup, handle_registered=False, reaped=False, secondary_errors=[])
        self.rows.append(row)
        self.receipt['commands'].append(row)
        self._append(dict(event='command-intent', row=row.copy()))
        self._publish(cleanup=cleanup)
        child = selector = None
        streams = []
        primary = None
        output = {'stdout': bytearray(), 'stderr': bytearray()}
        counts = dict(stdout=0, stderr=0)
        hashes = {key: hashlib.sha256() for key in output}
        started = time.monotonic()
        deadline = started + timeout
        try:
            if cleanup:
                require(self.cleanup_deadline - time.monotonic() >= timeout + RETIRE_SECONDS,
                        'cleanup-budget-no-full-command-allowance')
            with defer_signals() as interrupted:
                child = subprocess.Popen(arguments, cwd=self.root, env=self.environment, stdin=subprocess.DEVNULL,
                                         stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True)
                self.children.append((child, row))  # Capture before a pending cancellation can escape.
                row['handle_registered'] = True
                require(type(child.pid) is int and child.pid > 0, 'owned-child-pid')
                row['owned_pid'] = child.pid
                if operation == 'create':
                    self.creation_dispatched = True
            self._append(dict(event='command-launched', ordinal=row['ordinal'], owned_pid=row['owned_pid']))
            if interrupted:
                raise KeyboardInterrupt('direct-simulator-launch-cancelled')
            selector = selectors.DefaultSelector()
            for key in ('stdout', 'stderr'):
                pipe = getattr(child, key)
                streams.append(pipe)
                os.set_blocking(pipe.fileno(), False)
                selector.register(pipe, selectors.EVENT_READ, key)
            while selector.get_map():
                remaining = deadline - time.monotonic()
                require(remaining > 0, 'command-timeout')
                for ready, _ in selector.select(min(0.1, remaining)):
                    raw = os.read(ready.fileobj.fileno(), 65536)
                    if not raw:
                        selector.unregister(ready.fileobj)
                        continue
                    key = ready.data
                    counts[key] += len(raw)
                    hashes[key].update(raw)
                    require(counts[key] <= LIMIT, key + '-overflow')
                    output[key].extend(raw)
            remaining = deadline - time.monotonic()
            require(remaining > 0, 'command-timeout')
            row['exit_code'] = child.wait(timeout=remaining)
            require(type(row['exit_code']) is int, 'noninteger-wait-result')
            row['reaped'] = True
            require(row['exit_code'] == 0, 'nonzero-command')
            require(not output['stderr'].strip(), 'diagnostic-stderr')
        except BaseException as error:
            primary = error
            row['primary_error'] = failure(error)
        finally:
            if child is not None:
                try:
                    self._retire(child, row)
                except BaseException as error:
                    row['secondary_errors'].append(dict(stage='retirement', type=type(error).__name__))
                # Cover cancellation/selector construction before stream assignment.
                streams += [pipe for pipe in (child.stdout, child.stderr) if pipe not in streams]
            if selector is not None:
                try:
                    selector.close()
                except BaseException as error:
                    row['secondary_errors'].append(dict(stage='selector-close', type=type(error).__name__))
            for pipe in streams:
                if pipe is not None:
                    try:
                        pipe.close()
                    except BaseException as error:
                        row['secondary_errors'].append(dict(stage='pipe-close', type=type(error).__name__))
            row.update(finished_at=now(), elapsed_seconds=round(time.monotonic() - started, 6),
                       stdout_bytes=counts['stdout'], stderr_bytes=counts['stderr'],
                       stdout_sha256=hashes['stdout'].hexdigest(), stderr_sha256=hashes['stderr'].hexdigest())
            if primary is None and (row['secondary_errors'] or not row.get('reaped')):
                primary = RuntimeError('direct-simulator-capture-retirement-incomplete')
                row['primary_error'] = failure(primary)
            row['status'] = 'FAILED' if primary is not None else 'EXITED0'
            if primary is not None:
                self._error('command-' + operation, primary, cleanup=cleanup)
            try:
                self._append(dict(event='command-result', row=row.copy()))
                self._publish(cleanup=cleanup)
            except BaseException as error:
                if primary is None:
                    primary = error
                    row['primary_error'] = failure(error)
                    row['status'] = 'FAILED'
                else:
                    row['secondary_errors'].append(dict(stage='result-persistence', type=type(error).__name__))
        if primary is not None:
            raise primary
        return bytes(output['stdout'])

    def _arguments(self, operation):
        prefix = ['/usr/bin/xcrun', 'simctl']
        if operation == 'inventory':
            return prefix + ['list', 'devices', '-j']
        if operation == 'create':
            require(self.creation_intent and self.creation_started, 'create-without-intent')
            return prefix + ['create', self.name, DEVICE_TYPE, RUNTIME]
        require(operation in {'boot', 'bootstatus', 'shutdown', 'delete'} and
                isinstance(self.uuid, str) and UUID.fullmatch(self.uuid), 'operation-or-uuid')
        return prefix + [operation, self.uuid] + (['-b'] if operation == 'bootstatus' else [])

    def _owned(self, values):
        matching = [row for row in values.values() if self.name in row['name']]
        require(len(matching) <= 1 and (not matching or matching[0]['name'] == self.name), 'ambiguous-or-cloned-name')
        item = matching[0] if matching else None
        if self.uuid is not None and self.uuid in values:
            require(item is not None and values[self.uuid] == item, 'renamed-uuid')
        if item is not None:
            require(item['runtime'] == RUNTIME and item['device_type'] == DEVICE_TYPE and item['available'] and
                    (self.uuid is None or item['udid'] == self.uuid), 'device-identity-changed')
            require(self.baseline is not None and sha(item['udid'].encode()) not in self.baseline and self.creation_intent,
                    'preexisting-or-unjournaled-device')
        return item

    def metadata(self, label):
        require(re.fullmatch(r'owned-device-[a-z-]{1,64}', label), 'metadata-label')
        self._initialize()
        try:
            values = inventory(self._capture('inventory', self._arguments('inventory'), 45, cleanup=self.cleanup_started))
            item = self._owned(values)
            # Only our exact identity is durable; raw foreign names/UUIDs are not.
            self._append(dict(event='metadata', label=label, match=item))
            self._publish(cleanup=self.cleanup_started)
            return item
        except BaseException as error:
            self._error('metadata-validation', error, cleanup=self.cleanup_started)
            self._publish(cleanup=True)
            raise

    def command(self, arguments, filename, timeout=120):
        args = list(map(str, arguments))
        require(args[:2] == ['xcrun', 'simctl'] and len(args) >= 3, 'closed-route')
        operation = args[2]
        require(operation in {'create', 'boot', 'bootstatus'} and
                filename == operation + '.log', 'closed-route-operation')
        if operation == 'create':
            require(not self.creation_started and not self.cleanup_started and self.uuid is None, 'duplicate-create')
            require(args == ['xcrun', 'simctl', 'create', self.name, DEVICE_TYPE, RUNTIME] and timeout == 120,
                    'closed-create')
            try:
                self._initialize()
                values = inventory(self._capture('inventory', self._arguments('inventory'), 45))
                require(not any(self.name in row['name'] for row in values.values()), 'preexisting-name')
                self.baseline = sorted(sha(value.encode()) for value in values)
                self._append(dict(event='pre-create-intent', baseline_uuid_sha256=self.baseline,
                                  name=self.name, runtime=RUNTIME, device_type=DEVICE_TYPE))
                self.creation_intent = True
                self.creation_started = True
                self.creation_outcome = 'INTERRUPTED_OR_FAILED'
                self._publish()
            except BaseException as error:
                self._error('precreate-intent', error)
                self._publish(cleanup=True)
                raise
            try:
                raw = self._capture('create', self._arguments('create'), 120)
                value = raw.decode('ascii').strip()
                require(UUID.fullmatch(value), 'create-stdout-uuid')
                value = value.upper()
                require(sha(value.encode()) not in self.baseline, 'create-returned-preexisting-uuid')
                self.uuid = value
                require(self.metadata('owned-device-after-create') is not None, 'created-device-not-present')
                self.creation_outcome = 'EXITED0_AND_IDENTITY_VERIFIED'
                self._append(dict(event='create-outcome', status=self.creation_outcome, uuid=self.uuid))
                self._write_log(filename, (self.uuid + '\n').encode())
                self._publish()
                return 0
            except BaseException as error:
                self._error('create-result', error)
                # Never manufacture an exit receipt during partial-create recovery.
                self._publish(cleanup=True)
                raise
        require(args == ['xcrun', 'simctl'] + self._arguments(operation)[2:], 'closed-uuid-or-options')
        require(timeout == (300 if operation == 'bootstatus' else 120), 'closed-timeout')
        require(not self.cleanup_started and not self.failed, 'no-work-after-failure-or-cleanup')
        self._capture(operation, self._arguments(operation), timeout, cleanup=self.cleanup_started)
        self._write_log(filename, encoded(dict(operation=operation, status='EXITED0',
                                              retention='Raw lifecycle output not retained')) + b'\n')
        self._publish(cleanup=self.cleanup_started)
        return 0

    def _write_log(self, filename, raw):
        try:
            self._check_journal()
            fd = os.open(self.evidence / filename, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
            try:
                with os.fdopen(fd, 'wb', closefd=False) as stream:
                    stream.write(raw)
                    stream.flush()
                os.fsync(fd)
            finally:
                os.close(fd)
        except BaseException as error:
            self.evidence_failed = True
            self._error('sanitized-log-persistence', error, cleanup=self.cleanup_started)
            raise

    def mark_build_attempted(self):
        require(not self.build_attempted and not self.cleanup_started and not self.failed, 'build-marker-state')
        self.build_attempted = True  # Sticky even if the following durable write fails.
        self.receipt['build_attempted'] = True
        self._append(dict(event='build-attempted', at=now()))
        self._publish()

    def _start_cleanup(self):
        if not self.cleanup_started:
            self.cleanup_deadline = time.monotonic() + CLEANUP_SECONDS
            self.cleanup_started = True

    def recover(self):
        with self._cleanup_phase('cleanup-recover'):
            self._start_cleanup()
            self.retire_direct_children()
            if not self.creation_dispatched:
                require(not self.creation_started, 'create-not-issued-no-resource-authority')
                return None
            self._check_journal()
            item = self.metadata('owned-device-recovery')
            require(item is not None, 'unresolved-create-no-exact-resource')
            if self.uuid is None:
                self.uuid = item['udid']
                self._append(dict(event='recovered-for-cleanup-only', uuid=self.uuid,
                                  creation_outcome=self.creation_outcome))
            self.receipt['owned_uuid'] = self.uuid
            self._publish(cleanup=True)
            return item

    def retire_direct_children(self):
        try:
            for child, row in self.children:
                if row.get('reaped') is not True:
                    before = len(row['secondary_errors'])
                    self._retire(child, row)
                    require(len(row['secondary_errors']) == before, 'new-retirement-failure')
            require(all(row.get('reaped') is True for _, row in self.children), 'direct-child-not-reaped')
        except BaseException as error:
            self._error('final-direct-child-retirement', error, cleanup=True)
            raise

    def prepare_cleanup(self, owner, build_attempted):
        self._start_cleanup()
        require(self.barrier['status'] == 'NOT_RUN', 'cleanup-barrier-already-decided')
        self.barrier = dict(status='FAIL', build_attempted=bool(build_attempted))
        try:
            self.retire_direct_children()
            require(type(build_attempted) is bool and build_attempted == self.build_attempted, 'build-marker-disagreement')
            self._check_journal()
            if self.build_attempted:
                require(self.receipt.get('postbuild_evidence_preserved') is True, 'postbuild-evidence-not-preserved')
                require(owner is not None, 'postbuild-owner-missing')
                owner.stop()  # Original strict AppHost ps/lsof/identity authority, unchanged.
                live = owner.refresh(inspect_files=True, include_outputs=True)
                require(not live and not owner.unknown_holders and not owner.secondary_errors, 'postbuild-workers-or-holders')
                require(isinstance(owner.handles, list) and len(owner.handles) <= 4096, 'postbuild-handle-count')
                count = 0
                for child in owner.handles:
                    code = child.wait(timeout=0)  # No new authority to signal these build handles.
                    require(type(code) is int, 'postbuild-handle-unreaped')
                    count += 1
                self.barrier = dict(status='PASS', build_attempted=True, evidence_preserved=True, strict_stop=True,
                                    fresh_strict_refresh=True, direct_build_handles=count,
                                    direct_build_handles_reaped=count)
            else:
                self.barrier = dict(status='NOT_REQUIRED_PREBUILD', build_attempted=False)
            self._append(dict(event='destructive-cleanup-barrier', result=self.barrier))
            self._publish(cleanup=True)
            return True
        except BaseException as error:
            self.barrier['status'] = 'FAIL'
            self._error('postbuild-quiescence-barrier', error, cleanup=True)
            self._publish(cleanup=True)
            raise

    def _destructive_authority(self):
        require(self.cleanup_started and self.barrier['status'] in {'PASS', 'NOT_REQUIRED_PREBUILD'}, 'destruction-not-authorized')
        self.retire_direct_children()
        self._check_journal()
        require(self.creation_intent and self.uuid is not None, 'unjournaled-destructive-resource')

    @contextmanager
    def _cleanup_phase(self, name):
        try:
            yield
        except BaseException as error:
            self._error(name, error, cleanup=True)
            self._publish(cleanup=True)
            raise

    def shutdown(self):
        with self._cleanup_phase('cleanup-shutdown'):
            self._destructive_authority()
            info = self.metadata('owned-device-before-shutdown')
            require(info is not None, 'shutdown-device-unexpectedly-absent')
            if info['state'] != 'Shutdown':
                self._capture('shutdown', self._arguments('shutdown'), 120, cleanup=True)
                self._write_log('shutdown.log', encoded(dict(operation='shutdown', status='EXITED0')) + b'\n')
                info = self.metadata('owned-device-after-shutdown')
                require(info is not None and info['state'] == 'Shutdown', 'shutdown-incomplete')
                self.receipt['shutdown_exit_code'] = 0
            else:
                self.receipt['shutdown_disposition'] = 'already_shutdown'
            self._append(dict(event='shutdown-verified', uuid=self.uuid))
            self._publish(cleanup=True)

    def delete(self):
        with self._cleanup_phase('cleanup-delete'):
            self._destructive_authority()
            info = self.metadata('owned-device-before-delete')
            require(info is not None and info['state'] == 'Shutdown', 'delete-requires-fresh-shutdown')
            self._capture('delete', self._arguments('delete'), 120, cleanup=True)
            self._write_log('delete.log', encoded(dict(operation='delete', status='EXITED0')) + b'\n')
            self.receipt['delete_exit_code'] = 0
            require(self.metadata('owned-device-after-delete') is None, 'delete-metadata-remains')
            # No filesystem deletion of devices, no prefix/name adoption, no
            # permissive nonzero shutdown/delete interpretation.
            require(self.device_set.resolve(strict=True) == self.device_set and not self.device_set.is_symlink(), 'device-set-redirected')
            device = self.device_set / self.uuid
            require(not device.exists() and not device.is_symlink(), 'device-directory-remains')
            self.absent = True
            self.receipt['owned_device_absent'] = True
            self._append(dict(event='delete-and-absence-verified', uuid=self.uuid,
                              metadata_absent=True, directory_absent=True))
            self._publish(cleanup=True)

    def finish(self):
        self._start_cleanup()
        try:
            self.retire_direct_children()
            self._check_journal()
            require(self.absent, 'resource-retirement-not-proven')
            require(self.barrier['status'] in {'PASS', 'NOT_REQUIRED_PREBUILD'}, 'barrier-not-complete')
            self._append(dict(event='lifecycle-finalized', owned_uuid=self.uuid,
                              owned_device_absent=self.absent, postbuild_barrier=self.barrier,
                              direct_children_reaped=len(self.children), commands_sha256=sha(encoded(self.rows))))
        except BaseException as error:
            self._error('lifecycle-finalization', error, cleanup=True)
            raise
        finally:
            self.finished = True
            self._publish(cleanup=True)
        require(self.summary()['cleanup_status'] == 'PASS', 'cleanup-receipt-failed')
        return True
