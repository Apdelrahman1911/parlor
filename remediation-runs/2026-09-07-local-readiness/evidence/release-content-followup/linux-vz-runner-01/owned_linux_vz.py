#!/usr/bin/env python3
"""Campaign-only Linux ARM64 verification in a new no-mount, owned Lima VZ VM.

Root must independently approve this control and its exact inputs before use.
No Store operation, host source modification, VM reuse, or numeric host signal.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import shutil
import signal
import stat
import sys
import tarfile
import time
import types


HERE = Path(__file__).resolve().parent
REPO = HERE.parents[4]
CONTROLS = HERE.parent / 'android-arm64-runner'
MAX_SOURCE_BYTES = 64 * 1024 * 1024
MAX_FILE_BYTES = 8 * 1024 * 1024
MAX_FOOTPRINT = 6 * 1024**3
MIN_HOST_FREE = 8 * 1024**3
SHA = re.compile(r'[0-9a-f]{64}')
SAFE_ROOTS = {'.github', '.run', 'assets', 'build-logic', 'composeApp', 'config', 'docs',
              'game-modes', 'gradle', 'iosApp', 'release', 'scripts', 'shared'}
SAFE_TOP = {'AGENTS.md', 'CLAUDE.md', '.gitignore', '.gitattributes', '.editorconfig', 'README.md',
            'ARCHITECTURE.md', 'PROBLEMS_PARLOR.md', 'whodunit-game-design.md',
            'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
            'gradlew', 'gradlew.bat', 'LICENSE', 'LICENSE.md', 'LICENSE.txt'}
PROTECTED = ('.keystore', '.p12', '.p8', '.pem', '.jks', '.mobileprovision',
             'credentials.', 'service-account', 'local.properties', 'xcuserdata',
             '.xcuserstate', '.env', 'release/private', '.mobile-release', '/build/',
             '.ds_store', '/.git/', '/.ssh/')
EXCLUDED_EVIDENCE = ('audit-runs/', 'remediation-runs/', 'project-code-audit/', 'design/')
NO_MASTER = ['-F', '/dev/null', '-o', 'ControlMaster=no', '-o', 'ControlPath=none',
             '-o', 'ControlPersist=no', '-o', 'IdentityAgent=none',
             '-o', 'ForwardAgent=no', '-o', 'UseKeychain=no']
CONTROL_HASHES = {
    'darwin_owned_processes.py': '2f2ad2a5d68cea999709a4c3c3e384cd9e6767258ad34aed4181ee2118962378',
    'owned_arm64_smoke.py': '38ea4c0000a2c426f8475b4616e77ee0d8bb25bb424dc7516348c2095cd08b48',
}
SELF_CONTROLS = {'owned_linux_vz.py', 'guest_verification.py', 'test_linux_vz_controls.py'}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def checked_relative(value):
    if (not isinstance(value, str) or not value or len(value) > 4096 or '\\' in value
            or any(ord(character) < 32 or ord(character) == 127 for character in value)):
        raise ValueError('Malformed source path')
    path = PurePosixPath(value)
    if path.is_absolute() or len(path.parts) > 64 or any(part in ('', '.', '..') for part in value.split('/')):
        raise ValueError('Source path escapes the approved relative namespace')
    if (len(path.parts) == 1 and value not in SAFE_TOP
            or len(path.parts) > 1 and path.parts[0] not in SAFE_ROOTS):
        raise ValueError('Source path is outside the public staging allowlist: ' + value)
    if any(term in value.lower() for term in PROTECTED):
        raise ValueError('Protected or generated path cannot be staged: ' + value)
    return path


def canonical_scoped_file(path, scope):
    path, scope = Path(path).absolute(), Path(scope).absolute()
    if (not path.is_relative_to(scope) or path == scope or scope.resolve() != scope
            or path.resolve(strict=True) != path):
        raise ValueError('Control/receipt must have canonical parents within its approved public scope')
    return path


def read_control(path, limit=2 * 1024 * 1024):
    path = Path(path)
    if not path.is_absolute() or path.resolve(strict=True) != path:
        raise ValueError('Control must use a canonical absolute path without symlink ancestors')
    parent = os.open('/', os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        for part in path.parts[1:-1]:
            child = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=parent)
            os.close(parent)
            parent = child
        descriptor = os.open(path.name, os.O_RDONLY | os.O_NOFOLLOW, dir_fd=parent)
    finally:
        os.close(parent)
    with os.fdopen(descriptor, 'rb') as stream:
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode) or info.st_size > limit or info.st_uid != os.getuid():
            raise ValueError('Control must be an owned bounded regular file')
        data = stream.read(limit + 1)
        after = os.fstat(stream.fileno())
    fields = lambda item: (item.st_dev, item.st_ino, item.st_size, item.st_mtime_ns)
    if fields(info) != fields(after) or len(data) != info.st_size:
        raise ValueError('Control changed during read')
    return data


def load_source_receipt(path, expected_hash, guest):
    path = canonical_scoped_file(path, REPO / 'remediation-runs/2026-09-07-local-readiness/evidence')
    data = read_control(path)
    if not isinstance(expected_hash, str) or not SHA.fullmatch(expected_hash) or digest(data) != expected_hash:
        raise ValueError('Root-selected source receipt digest mismatch')
    value = guest.json_unique(data)
    if not isinstance(value, dict):
        raise ValueError('Expected a completed source receipt object')
    source = value.get('source_after')
    if (value.get('source_before') != source or value.get('source_changed_during_cycle') is not False
            or not isinstance(value.get('finished_at'), str)):
        raise ValueError('A completed, unchanged-source cycle receipt is required')
    records = guest.manifest_records(source, source.get('source_manifest_sha256') if isinstance(source, dict) else None)
    for relative, _ in records:
        checked_relative(relative)
    return source, digest(data)


def read_beneath(root_descriptor, relative):
    """Open each ancestor without following symlinks; never read a private escape."""
    parts = checked_relative(relative).parts
    descriptor = os.dup(root_descriptor)
    try:
        for part in parts[:-1]:
            child = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=descriptor)
            os.close(descriptor)
            descriptor = child
        child = os.open(parts[-1], os.O_RDONLY | os.O_NOFOLLOW, dir_fd=descriptor)
        with os.fdopen(child, 'rb') as stream:
            before = os.fstat(stream.fileno())
            if not stat.S_ISREG(before.st_mode) or before.st_uid != os.getuid() or before.st_size > MAX_FILE_BYTES:
                raise ValueError('Source is not an owned bounded regular file: ' + relative)
            data = stream.read(MAX_FILE_BYTES + 1)
            after = os.fstat(stream.fileno())
        identity = lambda item: (item.st_dev, item.st_ino, item.st_size, item.st_mtime_ns)
        if identity(before) != identity(after) or len(data) != before.st_size:
            raise ValueError('Source changed while staging: ' + relative)
        return data, 0o755 if before.st_mode & 0o111 else 0o644
    finally:
        os.close(descriptor)


def stage_source(root, source, target):
    descriptor = os.open(root, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    total, modes = 0, []
    try:
        with Path(target).open('xb') as output, tarfile.open(fileobj=output, mode='w:gz') as archive:
            for relative, expected in source['source_manifest']:
                data, mode = read_beneath(descriptor, relative)
                if digest(data) != expected:
                    raise ValueError('Source no longer matches approved receipt: ' + relative)
                total += len(data)
                modes.append([relative, mode])
                if total > MAX_SOURCE_BYTES:
                    raise ValueError('Source staging byte ceiling exceeded')
                info = tarfile.TarInfo(relative)
                info.size, info.mode, info.mtime = len(data), mode, 0
                archive.addfile(info, io.BytesIO(data))
    finally:
        os.close(descriptor)
    return {'sha256': digest(Path(target).read_bytes()), 'source_bytes': total,
            'files': len(source['source_manifest']),
            'mode_manifest_sha256': digest(json.dumps(modes, separators=(',', ':')).encode())}


def verify_current_source(commands, source, phase, expected_modes=None, *, cleanup=False):
    git = ['/usr/bin/git', '--no-optional-locks', '-c', 'core.fsmonitor=false', '--no-pager', '-C', str(REPO)]
    for key, args in (('commit', ['rev-parse', '--verify', 'HEAD']),
                      ('tree', ['rev-parse', '--verify', 'HEAD^{tree}']),
                      ('branch', ['branch', '--show-current'])):
        actual = commands.command('source-' + key + '-' + phase, git + args, cleanup=cleanup).strip()
        if actual != source.get(key):
            raise ValueError('Current checkout no longer matches root receipt: ' + key)
    # Inspect names first. A protected/local-only tracked change cannot cause
    # this runner to read its diff or stage it as application source.
    changed = commands.command('source-changed-names-' + phase,
                               git + ['diff', '--name-only', '-z', 'HEAD', '--'], cleanup=cleanup)
    for relative in changed.split('\x00'):
        if relative:
            checked_relative(relative)
    difference = commands.command('source-public-diff-' + phase,
                                  git + ['diff', '--binary', '--no-ext-diff', '--no-textconv', 'HEAD', '--'],
                                  cleanup=cleanup)
    if digest(difference.encode()) != source['diff_sha256']:
        raise ValueError('Current public diff differs from root receipt')
    listing = commands.command('source-inventory-' + phase,
                               git + ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], cleanup=cleanup)
    current = set()
    for relative in listing.split('\x00'):
        if not relative or relative.startswith(EXCLUDED_EVIDENCE):
            continue
        if any(term in relative.lower() for term in PROTECTED):
            continue  # Exclude by name without reading protected material.
        checked_relative(relative)
        path = REPO / relative
        if path.is_symlink():
            raise ValueError('Unstaged symlink in application inventory')
        if path.exists():
            current.add(relative)
    if current != {relative for relative, _ in source['source_manifest']}:
        raise ValueError('New/missing build-consumed source files differ from root receipt')
    descriptor = os.open(REPO, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    modes, total = [], 0
    try:
        for relative, expected in source['source_manifest']:
            data, mode = read_beneath(descriptor, relative)
            if digest(data) != expected:
                raise ValueError('Source bytes changed: ' + relative)
            total += len(data)
            if total > MAX_SOURCE_BYTES:
                raise ValueError('Source inventory byte ceiling exceeded')
            modes.append([relative, mode])
    finally:
        os.close(descriptor)
    mode_digest = digest(json.dumps(modes, separators=(',', ':')).encode())
    if expected_modes is not None and mode_digest != expected_modes:
        raise ValueError('Executable source modes changed during verification')
    return {'commit_tree_branch_diff_and_file_inventory_match': True,
            'mode_manifest_sha256': mode_digest, 'files': len(current)}


def private_environment(owned):
    owned.attest()
    for relative in ('home', 'lima', 'tmp', 'cache', 'bin'):
        (owned.path / relative).mkdir(mode=0o700)
    for tool in ('ssh', 'scp'):
        # Both entry points are needed: Lima's scp backend does not honor SSH's
        # executable alias when it invokes scp. Prefix options win in OpenSSH.
        wrapper = owned.path / 'bin' / tool
        wrapper.write_text('#!/bin/sh\nexec /usr/bin/' + tool + ' '
                           + ' '.join(NO_MASTER) + ' "$@"\n')
        wrapper.chmod(0o700)
    return {'HOME': str(owned.path / 'home'), 'LIMA_HOME': str(owned.path / 'lima'),
            'TMPDIR': str(owned.path / 'tmp'), 'XDG_CACHE_HOME': str(owned.path / 'cache'),
            'PATH': str(owned.path / 'bin') + ':/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin',
            'SSH': str(owned.path / 'bin/ssh'), 'LC_ALL': 'C', 'LANG': 'C',
            'GIT_CONFIG_NOSYSTEM': '1', 'GIT_CONFIG_GLOBAL': '/dev/null', 'GIT_TERMINAL_PROMPT': '0',
            'PYTHONDONTWRITEBYTECODE': '1'}


def verify_no_multiplexing(text):
    # The root-executed, no-connection probe of this exact installed OpenSSH
    # omits ControlPath=none and UseKeychain=no. Their CLI values are fixed in
    # NO_MASTER before any Lima arguments; absent -G fields are not invented.
    expected = {'controlmaster': {'false', 'no'}, 'controlpath': {'none'},
                'controlpersist': {'no', 'false'}, 'identityagent': {'none'},
                'forwardagent': {'no', 'false'}, 'usekeychain': {'no', 'false'}}
    found = {}
    for line in text.splitlines():
        key, _, value = line.partition(' ')
        if key in expected:
            if key in found or value.lower() not in expected[key]:
                raise ValueError('Effective SSH multiplexing remains enabled or ambiguous')
            found[key] = value
    required = {'controlmaster', 'controlpersist', 'identityagent', 'forwardagent'}
    if not required.issubset(found):
        raise ValueError('SSH -G did not attest every emitted no-background option')
    return {'emitted': found, 'omitted_by_installed_ssh': sorted(set(expected) - set(found))}


def allocated_bytes(path):
    total, entries = 0, 0
    for directory, directories, files in os.walk(path, followlinks=False):
        for name in [*directories, *files]:
            try:
                info = (Path(directory) / name).lstat()
            except FileNotFoundError:
                continue  # A task-owned transient log/socket may disappear.
            entries += 1
            if entries > 100_000:
                raise RuntimeError('Owned output entry ceiling exceeded')
            total += info.st_blocks * 512
    return total


class Footprint:
    def __init__(self, owned, evidence):
        self.owned, self.evidence = owned, evidence
        self.last, self.peak, self.failed, self.closing = 0.0, 0, False, False

    def check(self, force=False):
        if self.closing or self.failed or (not force and time.monotonic() - self.last < 1):
            return
        self.last = time.monotonic()
        self.owned.attest()
        current = allocated_bytes(self.owned.path) + allocated_bytes(self.evidence)
        self.peak = max(self.peak, current)
        if current > MAX_FOOTPRINT or shutil.disk_usage(self.owned.path).free < MIN_HOST_FREE:
            self.failed = True  # Allow the ownership finalizer to run without repeating this exception.
            raise RuntimeError('Owned 6 GiB footprint or host free-space boundary reached')


def load_approved_module(name, path, expected):
    # Compile the attested source bytes, not an unchecked pre-existing pyc.
    # A prior import cannot silently stand in for the reviewed implementation.
    if name in sys.modules:
        raise RuntimeError('Control module name already exists before owned import: ' + name)
    data = read_control(path)
    if digest(data) != expected:
        raise RuntimeError('Root-approved control hash required: ' + str(path))
    module = types.ModuleType(name)
    module.__file__ = str(path)
    module.__audit_source_sha256__ = expected
    sys.modules[name] = module
    try:
        exec(compile(data, str(path), 'exec'), module.__dict__)
        if digest(read_control(path)) != expected:
            raise RuntimeError('Control changed during import')
    except BaseException:
        sys.modules.pop(name, None)
        raise
    return module


def verify_approval(path, expected):
    data = read_control(canonical_scoped_file(path, HERE), 64 * 1024)
    if not isinstance(expected, str) or not SHA.fullmatch(expected) or digest(data) != expected:
        raise ValueError('Explicit root-selected control approval digest required')
    # Duplicate key detection is needed before the guest helper is imported.
    def pairs(items):
        value = {}
        for key, item in items:
            if key in value:
                raise ValueError('Duplicate approval field')
            value[key] = item
        return value
    value = json.loads(data, object_pairs_hook=pairs)
    if not isinstance(value, dict):
        raise ValueError('Approval must be an object')
    files = value.get('controls')
    if value.get('schema') != 1 or not isinstance(files, dict) or set(files) != SELF_CONTROLS:
        raise ValueError('Approval must bind the exact host, guest, and pure tests')
    for name, expected_file in files.items():
        if not isinstance(expected_file, str) or not SHA.fullmatch(expected_file) or digest(read_control(HERE / name)) != expected_file:
            raise ValueError('Reviewed control differs from approved freeze: ' + name)
    if value.get('shared_controls') != CONTROL_HASHES:
        raise ValueError('Native controls differ from root-approved dependency hashes')
    for name, checksum in CONTROL_HASHES.items():
        if digest(read_control(CONTROLS / name)) != checksum:
            raise ValueError('Shared native control changed: ' + name)
    return value


def load_controls(approval):
    load_approved_module('darwin_owned_processes', CONTROLS / 'darwin_owned_processes.py',
                         CONTROL_HASHES['darwin_owned_processes.py'])
    commands = load_approved_module('owned_arm64_smoke', CONTROLS / 'owned_arm64_smoke.py',
                                   CONTROL_HASHES['owned_arm64_smoke.py'])
    guest = load_approved_module('owned_linux_guest', HERE / 'guest_verification.py',
                                approval['controls']['guest_verification.py'])
    return commands, guest


def validate_guest_evidence(directory, guest, source, archive_hash, control_hash, run_token):
    report = guest.json_unique(guest.read_regular(directory / 'result.json', 4 * 1024 * 1024))
    expected_source = {key: source[key] for key in ('commit', 'tree', 'diff_sha256', 'source_manifest_sha256')}
    if (not isinstance(report, dict) or report.get('schema') != 1 or report.get('source') != expected_source
            or report.get('run_token') != run_token
            or report.get('platform') != {'system': 'Linux', 'machine': 'aarch64'}
            or report.get('source_manifest_sha256') != source['source_manifest_sha256']
            or report.get('source_archive_sha256') != archive_hash
            or report.get('control_sha256') != control_hash
            or report.get('control_after_sha256') != control_hash):
        raise ValueError('Guest source/platform/control identity is missing or inconsistent')
    attestation, jdk = report.get('guest_attestation', {}), report.get('jdk', {})
    if (not isinstance(attestation, dict) or not isinstance(jdk, dict)
            or attestation.get('cpus') != 2 or type(attestation.get('memory_bytes')) is not int
            or not 2 * 1024**3 < attestation['memory_bytes'] <= 3 * 1024**3
            or not isinstance(attestation.get('mount_types'), list)
            or any(item in attestation['mount_types'] for item in ('virtiofs', '9p', 'fuse.sshfs'))
            or jdk.get('sha256') != guest.JDK_SHA or jdk.get('url') != guest.JDK_URL
            or jdk.get('bytes') != guest.JDK_BYTES):
        raise ValueError('Guest resource/no-host-mount/JDK attestation mismatch')
    receipts, logs, by_label = report.get('commands'), set(), {}
    if not isinstance(receipts, list) or not 1 <= len(receipts) <= 64:
        raise ValueError('Guest command receipts are missing or excessive')
    for index, row in enumerate(receipts):
        if (not isinstance(row, dict) or not isinstance(row.get('label'), str)
                or not re.fullmatch(r'[a-zA-Z0-9-]{1,80}', row['label'])
                or not isinstance(row.get('args'), list) or not 1 <= len(row['args']) <= 64
                or any(not isinstance(item, str) or len(item) > 8192 for item in row['args'])
                or row['label'] in by_label):
            raise ValueError('Malformed guest command receipt')
        by_label[row['label']] = index
        path = guest.safe_member(row.get('log'))
        if (len(path.parts) != 1 or str(path) in logs
                or str(path) != f'{index:03d}-' + row['label'] + '.log'):
            raise ValueError('Duplicate/escaping guest command log')
        logs.add(str(path))
        if digest(guest.read_regular(directory / path, guest.MAX_LOG)) != row.get('sha256'):
            raise ValueError('Guest command log no longer matches execution receipt')
        if (type(row.get('exit_code')) is not int
                and not (row.get('exit_code') is None and row.get('failure'))):
            raise ValueError('Guest command lacks an actual exit or explicit launch/cancellation failure')

    def command_passed(label):
        row = receipts[by_label[label]]
        return row.get('exit_code') == 0 and not row.get('failure') and not row.get('cleanup_failure')

    preservation = (report.get('source_before_matches') is True and report.get('source_after_matches') is True
                    and report.get('remaining_java') == [] and report.get('java_before_output_cleanup') == []
                    and report.get('remaining_build_outputs') == [] and report.get('cleanup_errors') == [])
    result = {'status': 'FAIL', 'source_and_cleanup_verified': preservation,
              'guest_receipt_sha256': digest(guest.read_regular(directory / 'result.json', 4 * 1024 * 1024))}
    # A real Linux Python result remains useful if the subsequent Desktop gate
    # cannot configure or fit. Do not throw away that narrower receipt, and do
    # not upgrade a failed/unexecuted Desktop command into PASS.
    python_label = 'release-python-tests'
    if python_label not in by_label:
        result['python'] = {'status': 'BLOCKED', 'reason': 'Release Python tests were not invoked'}
    else:
        try:
            guest_root = '/home/parlor/run-' + run_token
            expected_args = ['/usr/bin/python3', '-B', guest_root + '/python_tests.py', guest_root + '/source',
                             guest_root + '/evidence/python-tests.json']
            if receipts[by_label[python_label]]['args'] != expected_args or not command_passed(python_label):
                raise ValueError('Release Python command/exit did not establish successful execution')
            python = guest.json_unique(guest.read_regular(directory / 'python-tests.json', 2 * 1024 * 1024))
            if python != report.get('python'):
                raise ValueError('Python report and standalone execution receipt disagree')
            summary = guest.validate_python(python)
            if not preservation:
                raise ValueError('Guest source preservation or owned cleanup could not be verified')
            result['python'] = {'status': 'PASS', **summary}
        except (ValueError, OSError) as error:
            result['python'] = {'status': 'FAIL', 'reason': str(error),
                                'command_exit_code': receipts[by_label[python_label]].get('exit_code')}

    xml, total = [], 0
    for path in sorted((directory / 'xml').rglob('TEST-*.xml')):
        data = guest.read_regular(path, 2 * 1024 * 1024)
        total += len(data)
        if len(xml) >= 512 or total > 16 * 1024 * 1024:
            raise ValueError('Desktop XML evidence ceiling exceeded')
        xml.append(guest.parse_xml(data, str(path.relative_to(directory / 'xml'))))
    gradle = report.get('gradle')
    if not isinstance(gradle, dict) or xml != gradle.get('xml', []):
        raise ValueError('Guest XML summary differs from actual retained test reports')
    if 'productionDesktopCheck' not in by_label:
        if xml or gradle.get('exit_code') is not None:
            raise ValueError('Desktop artifacts/status exist without an invocation receipt')
        result['desktop'] = {'status': 'BLOCKED', 'reason': 'Desktop gate was not invoked',
                             'launch_attempted': gradle.get('started') is True,
                             'guest_failure': report.get('failure')}
    else:
        try:
            required = ('release-python-tests', 'productionDesktopCheck', 'immediate-gradle-stop', 'post-cleanup-gradle-stop')
            if any(label not in by_label for label in required):
                raise ValueError('Missing test/stop command execution receipts')
            order = [by_label[label] for label in required]
            if order != sorted(order) or order[2] != order[1] + 1:
                raise ValueError('Gradle was not stopped immediately after the build command')
            if receipts[by_label['productionDesktopCheck']]['args'] != guest.DESKTOP_ARGS:
                raise ValueError('The actual command differs from the bounded strict Desktop gate')
            for label in required[2:]:
                if receipts[by_label[label]]['args'] != ['./gradlew', '--stop'] or not command_passed(label):
                    raise ValueError('Gradle stop did not execute successfully')
            if not command_passed('productionDesktopCheck') or gradle.get('exit_code') != 0:
                raise ValueError('The actual Desktop gate command failed')
            summary = guest.validate_desktop(xml, source['source_manifest'])
            if not preservation:
                raise ValueError('Guest source preservation or owned cleanup could not be verified')
            result['desktop'] = {'status': 'PASS', **summary}
        except ValueError as error:
            result['desktop'] = {'status': 'FAIL', 'reason': str(error),
                                 'command_exit_code': receipts[by_label['productionDesktopCheck']].get('exit_code')}
    if (all(result[key]['status'] == 'PASS' for key in ('python', 'desktop'))
            and all(command_passed(label) for label in by_label)
            and report.get('status') == 'PASS' and not report.get('failure')):
        result['status'] = 'PASS'
    else:
        result['guest_failure'] = report.get('failure')
    return result


def verify_no_holders(commands, owned):
    # VZ can involve OS-managed XPC helpers that are not descendants. Inspect
    # only holders of this freshly owned tree; never signal an unrelated helper.
    owned.attest()
    output = commands.command('owned-tree-holders', ['/usr/sbin/lsof', '-nP', '+D', str(owned.path)],
                              timeout=45, check=False, cleanup=True)
    receipt = commands.receipts[-1]
    if receipt['exit_code'] != 1 or output.strip():
        raise RuntimeError('Owned tree still has holders or lsof could not establish absence; preserving it')
    return {'command_exit': 1, 'output_empty': True}


def stop_foreground(commands, launch, executable):
    """Request graceful VZ close with one fresh attested token, never a PID file."""
    registry = commands.processes
    if launch is None or launch.retired:
        return
    registry.refresh()
    records = registry.live(launch.pid)
    record = next((entry for entry in records if entry.pid == launch.pid), None)
    if record is not None:
        if record.command != str(executable):
            raise RuntimeError('Foreground hostagent executable identity changed')
        again = registry.backend.read(record.pid)
        if again is None:
            return
        if again.lifetime != record.lifetime or again.token != record.token or again.command != record.command:
            raise RuntimeError('Foreground hostagent identity unstable before graceful shutdown')
        sent = registry.backend.signal(record, signal.SIGINT)
        registry.events.append({'pid': record.pid, 'started': record.started,
                                'command': record.command, 'signal': int(signal.SIGINT),
                                'generation': record.token[7], 'sent': sent})
    deadline = time.monotonic() + 60
    while launch.poll() is None and time.monotonic() < deadline:
        time.sleep(0.1)
    # Root-owned registry.shutdown() follows, including token-bound escalation.


def finish_task_workers(commands, foreground, executable, owned, source, report):
    """Independent finalization boundaries; earlier failures never skip later ones."""
    try:
        stop_foreground(commands, foreground, executable)
    except BaseException as error:
        report['cleanup_errors'].append({'graceful_vz_stop': str(error)})
    try:
        report['cleanup_errors'].extend(commands.processes.shutdown())
    except BaseException as error:
        report['cleanup_errors'].append({'owned_workers': str(error)})
    if source is not None:
        try:
            report['host_source_after'] = verify_current_source(commands, source, 'after',
                expected_modes=report.get('host_source_before', {}).get('mode_manifest_sha256'), cleanup=True)
        except BaseException as error:
            report['status'] = 'FAIL'
            report['source_preservation_failure'] = type(error).__name__ + ': ' + str(error)
    if owned is not None and not report['cleanup_errors']:
        try:
            report['owned_tree_no_holders'] = verify_no_holders(commands, owned)
        except BaseException as error:
            report['cleanup_errors'].append({'owned_tree_holders': str(error)})
    # Metadata/holder probes own fresh children too. Earlier failures must not
    # bypass their own registry finalizer, even if deletion will be refused.
    try:
        report['cleanup_errors'].extend(commands.processes.shutdown())
    except BaseException as error:
        report['cleanup_errors'].append({'final_probe_workers': str(error)})
    report['commands'] = commands.receipts
    report['workers'] = commands.processes.receipts()
    report['token_bound_signals'] = commands.processes.events


def run(args):
    evidence = Path(args.evidence_dir).absolute()
    evidence_scope = REPO / 'remediation-runs/2026-09-07-local-readiness/evidence'
    if not evidence.is_relative_to(evidence_scope) or evidence.resolve() != evidence:
        raise ValueError('Use a fresh canonical directory in this campaign evidence scope')
    evidence.mkdir(parents=True, exist_ok=False)
    evidence.chmod(0o700)
    report = {'status': 'FAIL', 'scope': 'Owned Linux ARM64 VM, not x86/KVM or physical/Store evidence',
              'started_epoch': time.time(), 'cleanup_errors': [], 'tests': {}, 'controls': CONTROL_HASHES}
    controls, guest, approval, source = None, None, None, None
    owned, commands, foreground, footprint, executable = None, None, None, None, None
    try:
        if (REPO != Path('/Users/abdelrahman/Projects/parlor') or platform.system() != 'Darwin'
                or platform.machine() != 'arm64'):
            raise RuntimeError('Runner repository/Apple Silicon host anchor changed')
        approval = verify_approval(args.approval_file, args.approval_sha256)
        report['control_approval_sha256'] = args.approval_sha256
        controls, guest = load_controls(approval)
        report['actual_imports'] = [{'name': module.__name__, 'file': module.__file__,
                                    'source_sha256': module.__audit_source_sha256__}
                                   for module in (sys.modules['darwin_owned_processes'], controls, guest)]
        source, source_receipt_hash = load_source_receipt(args.source_receipt, args.source_receipt_sha256, guest)
        report.update(source={k: v for k, v in source.items() if k != 'source_manifest'},
                      source_receipt_sha256=source_receipt_hash)
        signal.signal(signal.SIGINT, controls.request_cancel)
        signal.signal(signal.SIGTERM, controls.request_cancel)
        owned = controls.OwnedDirectory()
        report['temporary_root'] = str(owned.path)
        environment = private_environment(owned)
        commands = controls.Commands(evidence, environment)
        footprint = Footprint(owned, evidence)
        original_refresh = commands.processes.refresh

        def bounded_refresh():
            footprint.check()
            return original_refresh()

        commands.processes.refresh = bounded_refresh
        footprint.check(force=True)
        report['host_source_before'] = verify_current_source(commands, source, 'before')
        executable = Path(args.limactl).resolve(strict=True)
        if str(executable) != '/opt/homebrew/Cellar/lima/2.1.4/bin/limactl':
            raise RuntimeError('Only the reviewed installed Lima 2.1.4 is allowed')
        report['limactl_sha256'] = digest(read_control(executable, 128 * 1024 * 1024))
        version = commands.command('lima-version', [str(executable), '--version'])
        if not re.fullmatch(r'limactl version 2\.1\.4(?: [^\n]*)?', version.strip()):
            raise RuntimeError('Unexpected Lima version')
        report['lima_version'] = version.strip()
        effective = commands.command('ssh-no-background', [str(owned.path / 'bin/ssh'), '-G',
                                      '-o', 'ControlMaster=auto', '-o', 'ControlPersist=yes',
                                      '-o', 'ControlPath=/must-not-be-used', 'parlor-synthetic'])
        report['ssh_effective_control'] = verify_no_multiplexing(effective)
        config = HERE.parent / 'linux-vz-research/pinned-no-mount-proposal.yaml'
        config_bytes = read_control(config, 64 * 1024)
        if digest(config_bytes) != '78eaf03f6b7d05ae399cb8cf510d301db767328e2dabe5d02fc00f691acb49fa':
            raise RuntimeError('Pinned no-mount VM config changed')
        staged_config = owned.path / 'vm.yaml'
        staged_config.write_bytes(config_bytes)
        commands.command('lima-validate', [str(executable), 'validate', str(staged_config)])
        source_archive = owned.path / 'source.tar.gz'
        report['source_archive'] = stage_source(REPO, source, source_archive)
        if report['source_archive']['mode_manifest_sha256'] != report['host_source_before']['mode_manifest_sha256']:
            raise ValueError('Source executable modes changed between attestation and staging')
        manifest_path = owned.path / 'source.json'
        manifest_path.write_text(json.dumps(source, separators=(',', ':')))
        guest_control = owned.path / 'guest_verification.py'
        guest_bytes = read_control(HERE / 'guest_verification.py')
        report['guest_control_sha256'] = digest(guest_bytes)
        if report['guest_control_sha256'] != approval['controls']['guest_verification.py']:
            raise ValueError('Guest control changed before staging')
        guest_control.write_bytes(guest_bytes)
        name = 'pv-' + owned.token[:12]
        report['instance_name'] = name
        for relative in (name, '_config/base.yaml', '_config/default.yaml', '_config/override.yaml'):
            path = owned.path / 'lima' / relative
            if path.exists() or path.is_symlink():
                raise RuntimeError('Fresh owned Lima home unexpectedly contains an instance/override')
        commands.command('lima-create', [str(executable), 'create', '--tty=false', '--name', name,
                                       str(staged_config)], timeout=600)
        if controls.CANCELLED:
            raise InterruptedError('Cancelled before foreground VM launch')
        foreground = commands.worker('lima-foreground', [str(executable), 'start', '--foreground',
                                                        '--tty=false', name])
        shell = [str(executable), 'shell', '--tty=false', '--preserve-env=false', '--start=false',
                 '--reconnect=false', '--workdir=/home/parlor', name]
        deadline = time.monotonic() + 420
        while time.monotonic() < deadline:
            if controls.CANCELLED or foreground.poll() is not None:
                raise RuntimeError('VM start cancelled or foreground hostagent exited')
            ready = commands.command('guest-ready', shell + ['/bin/echo', 'PARLOR_GUEST_READY'],
                                     timeout=15, check=False)
            if ready.strip() == 'PARLOR_GUEST_READY':
                break
            time.sleep(5)
        else:
            raise TimeoutError('Owned VZ boot deadline exceeded')
        disk = owned.path / 'lima' / name / 'disk'
        if disk.is_symlink() or not disk.is_file() or disk.stat().st_size != 4 * 1024**3:
            raise RuntimeError('Actual raw VM disk does not match the 4 GiB ceiling')
        guest_root = '/home/parlor/run-' + owned.token
        commands.command('guest-directory', shell + ['/bin/mkdir', '-m', '700', guest_root])
        for path in (source_archive, manifest_path, guest_control):
            commands.command('guest-stage', [str(executable), 'cp', '--backend=scp', str(path),
                                             name + ':' + guest_root + '/' + path.name], timeout=120)
        invocation = shell + ['/usr/bin/python3', '-B', guest_root + '/guest_verification.py',
                              '--root', guest_root, '--archive-sha256', report['source_archive']['sha256'],
                              '--source-manifest-sha256', source['source_manifest_sha256']]
        guest_failure = None
        try:
            commands.command('guest-verification', invocation, timeout=2400, check=False)
            report['guest_exit_code'] = commands.receipts[-1]['exit_code']
        except BaseException as error:
            guest_failure = error
            report['guest_invocation_failure'] = type(error).__name__ + ': ' + str(error)
        finally:
            # A failing guest run still owns useful evidence. Retain a standalone
            # failure receipt even if a compact bundle could not be completed.
            report['guest_collection_errors'] = []
            for remote, local in (('evidence/result.json', 'guest-result.json'),
                                  ('evidence.tar.gz', 'guest-evidence.tar.gz')):
                try:
                    commands.command('guest-receipt', [str(executable), 'cp', '--backend=scp',
                                     name + ':' + guest_root + '/' + remote, str(evidence / local)],
                                     timeout=120, cleanup=True)
                except BaseException as error:
                    report['guest_collection_errors'].append({remote: type(error).__name__ + ': ' + str(error)})
        if report['guest_collection_errors']:
            raise RuntimeError('Guest evidence collection incomplete; see preserved per-copy failures')
        bundle = evidence / 'guest-evidence.tar.gz'
        if bundle.is_symlink() or bundle.stat().st_size > guest.MAX_EVIDENCE + 1024 * 1024:
            raise ValueError('Guest evidence compressed size/type boundary exceeded')
        report['guest_bundle_sha256'] = digest(read_control(bundle, guest.MAX_EVIDENCE + 1024 * 1024))
        guest.extract_bounded(bundle, evidence / 'guest', byte_limit=guest.MAX_EVIDENCE)
        if read_control(evidence / 'guest-result.json', 4 * 1024 * 1024) != read_control(evidence / 'guest/result.json', 4 * 1024 * 1024):
            raise ValueError('Standalone and bundled guest completion receipts differ')
        report['tests'] = validate_guest_evidence(evidence / 'guest', guest, source,
                                                report['source_archive']['sha256'], report['guest_control_sha256'], owned.token)
        bundle.unlink()  # Raw logs/XML and their verified completion receipt remain under guest/.
        (evidence / 'guest-result.json').unlink()
        if guest_failure is not None:
            raise guest_failure
        if report.get('guest_exit_code') != 0:
            raise RuntimeError('Guest process did not exit successfully')
        if report['tests']['status'] != 'PASS':
            raise RuntimeError('Guest gates did not all pass; inspect separately bound Python/Desktop dispositions')
        report['status'] = 'PASS'
    except BaseException as error:
        report['failure'] = type(error).__name__ + ': ' + str(error)
    finally:
        if footprint is not None:
            footprint.closing = True
            report['peak_owned_allocated_bytes'] = footprint.peak
        if commands is not None:
            # Release the task-owned VM before running final metadata probes.
            # Source preservation is independently attempted even on cancellation.
            finish_task_workers(commands, foreground, executable, owned, source, report)
        if approval is not None:
            try:
                verify_approval(args.approval_file, args.approval_sha256)
                report['controls_after_match_approval'] = True
            except BaseException as error:
                report['status'] = 'FAIL'
                report['control_preservation_failure'] = type(error).__name__ + ': ' + str(error)
        if report.get('source_preservation_failure') or report.get('control_preservation_failure'):
            report['tests']['binding_invalidated'] = 'Source/control preservation did not match the approved freeze'
            report['tests']['status'] = 'FAIL'
            for key in ('python', 'desktop'):
                gate = report['tests'].get(key)
                if gate is not None and gate.get('status') == 'PASS':
                    gate.update(status='BLOCKED', reason='Host source/control binding invalidated after execution')
        if controls is not None and controls.CANCELLED:
            report['status'] = 'FAIL'
            report['cancellation_requested'] = True
        if owned is not None and not report['cleanup_errors']:
            try:
                owned.remove()
                report['temporary_root_removed'] = True
            except BaseException as error:
                report['cleanup_errors'].append({'owned_outputs_preserved': str(error)})
        if report['cleanup_errors']:
            report['status'] = 'FAIL'
        report['finished_epoch'] = time.time()
        (evidence / 'run.json').write_text(json.dumps(report, indent=2) + '\n')
    print(report['status'] + ': ' + str(evidence / 'run.json'))
    return 0 if report['status'] == 'PASS' else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('evidence-dir', 'source-receipt', 'source-receipt-sha256', 'limactl',
                 'approval-file', 'approval-sha256'):
        parser.add_argument('--' + name, required=True)
    return run(parser.parse_args())


if __name__ == '__main__':
    raise SystemExit(main())
