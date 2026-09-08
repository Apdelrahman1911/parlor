#!/usr/bin/env python3
"""Disposable Linux guest control. Never imported by the application/build graph."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import shutil
import signal
import stat
import subprocess
import sys
import tarfile
import time
import urllib.request
import xml.etree.ElementTree as ET


JDK_URL = ('https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/'
           'OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.12.1_1.tar.gz')
JDK_SHA = '23e37e026f12f3e706f18938ff611db3032d075b09d0879a25d06718c773e223'
JDK_BYTES = 205641175
MAX_LOG = 8 * 1024 * 1024
MAX_EVIDENCE = 32 * 1024 * 1024
MAX_SOURCE = 64 * 1024 * 1024
SHA = re.compile(r'[0-9a-f]{64}')
COMMIT = re.compile(r'[0-9a-f]{40}')
KNOWN_SKIPS = {
    'peer_can_join_a_hosted_room_and_membership_appears_on_host',
    'peer_to_host_message_round_trips_and_host_to_peer_message_arrives_back',
    'host_broadcast_reaches_every_peer',
}
LOOPBACK_CLASS = 'com.parlor.transport.p2p.P2pKitRoomTransportLoopbackTest'
DISCOVERY_CLASS = 'com.parlor.transport.p2p.JupiterDiscoveryContractTest'
DESKTOP_ARGS = ['./gradlew', 'productionDesktopCheck', '--dependency-verification=strict',
                '--no-daemon', '--no-parallel', '--max-workers=1', '--no-configuration-cache',
                '--no-build-cache', '--console=plain', '-Pkotlin.compiler.execution.strategy=in-process',
                '-Dorg.gradle.jvmargs=-Xmx1280m -Dfile.encoding=UTF-8 -XX:+UseParallelGC']
CANCELLED = False


def cancel(_signum, _frame):
    global CANCELLED
    CANCELLED = True


def sha256(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()


def json_unique(data):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('Duplicate JSON field')
            result[key] = value
        return result
    return json.loads(data, object_pairs_hook=pairs)


def read_regular(path, limit):
    path = Path(path)
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(descriptor, 'rb') as stream:
        before = os.fstat(stream.fileno())
        if not stat.S_ISREG(before.st_mode) or before.st_size > limit:
            raise ValueError('Expected bounded regular evidence/source file')
        data = stream.read(limit + 1)
        after = os.fstat(stream.fileno())
    fields = lambda item: (item.st_dev, item.st_ino, item.st_size, item.st_mtime_ns)
    if fields(before) != fields(after) or len(data) != before.st_size:
        raise ValueError('Evidence/source file changed during read')
    return data


def safe_member(name):
    if (not isinstance(name, str) or len(name) > 4096
            or any(ord(character) < 32 or ord(character) == 127 for character in name)):
        raise ValueError('Malformed archive/source path')
    path = PurePosixPath(name)
    if (not name or len(path.parts) > 64 or path.is_absolute() or '\\' in name
            or any(p in ('', '.', '..') for p in name.split('/'))):
        raise ValueError('Unsafe archive member')
    return path


def manifest_records(source, expected_digest):
    if (not isinstance(source, dict) or not isinstance(expected_digest, str)
            or not SHA.fullmatch(expected_digest)
            or any(not isinstance(source.get(key), str) or not COMMIT.fullmatch(source[key])
                   for key in ('commit', 'tree'))
            or not isinstance(source.get('diff_sha256'), str)
            or not SHA.fullmatch(source['diff_sha256'])):
        raise ValueError('Full commit/tree/diff/source identity required')
    records = source.get('source_manifest')
    if not isinstance(records, list) or not 1 <= len(records) <= 2048:
        raise ValueError('Source manifest count outside bounds')
    seen = set()
    for row in records:
        if (not isinstance(row, list) or len(row) != 2 or not isinstance(row[1], str)
                or not SHA.fullmatch(row[1])):
            raise ValueError('Malformed source manifest row')
        relative = str(safe_member(row[0]))
        if relative in seen:
            raise ValueError('Duplicate source manifest path')
        seen.add(relative)
    actual = hashlib.sha256(json.dumps(records, separators=(',', ':')).encode()).hexdigest()
    if actual != expected_digest or source.get('source_manifest_sha256') != actual:
        raise ValueError('Source manifest digest mismatch')
    return records


def extract_bounded(archive_path, destination, *, byte_limit, allow_links=False):
    """No devices, hard links, duplicate paths, or extraction through link parents."""
    destination = Path(destination).absolute()
    if destination.parent.is_symlink() or destination.parent.resolve() != destination.parent:
        raise ValueError('Archive destination must have canonical owned parents')
    destination.mkdir(mode=0o700, exist_ok=False)
    with tarfile.open(archive_path, 'r:gz') as archive:
        members, names, links, kinds, total = [], set(), set(), {}, 0
        for member in archive:
            if len(members) >= 10_000:
                raise ValueError('Archive entry ceiling exceeded')
            relative = safe_member(member.name.rstrip('/') if member.isdir() else member.name)
            if str(relative) in names:
                raise ValueError('Duplicate archive member')
            names.add(str(relative))
            kinds[str(relative)] = member.isdir()
            if member.issym() and allow_links:
                link = PurePosixPath(member.linkname)
                if link.is_absolute() or not member.linkname or '\\' in member.linkname or '\x00' in member.linkname:
                    raise ValueError('Unsafe archive symlink')
                resolved = (destination / relative.parent / member.linkname).resolve()
                if not resolved.is_relative_to(destination):
                    raise ValueError('Archive symlink escapes owned destination')
                links.add(str(relative))
            elif not member.isfile() and not member.isdir():
                raise ValueError('Unsupported archive node')
            if (member.isdir() or member.issym()) and member.size != 0:
                raise ValueError('Non-file archive member has a payload')
            total += member.size
            if member.size < 0 or total > byte_limit:
                raise ValueError('Archive expanded byte ceiling exceeded')
            members.append((member, relative))
        for _member, relative in members:
            if any(str(parent) in links for parent in relative.parents):
                raise ValueError('Archive attempts extraction through a symlink')
            if any(str(parent) in kinds and not kinds[str(parent)]
                   for parent in relative.parents if str(parent) != '.'):
                raise ValueError('Archive member ancestor is not a directory')
        for member, relative in members:
            target = destination / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            if member.isdir():
                target.mkdir(mode=0o755, exist_ok=True)
            elif member.isfile():
                stream = archive.extractfile(member)
                if stream is None:
                    raise ValueError('Missing archive stream')
                with stream, target.open('xb') as output:
                    remaining = member.size
                    while remaining:
                        data = stream.read(min(1024 * 1024, remaining))
                        if not data:
                            raise ValueError('Truncated archive member')
                        output.write(data)
                        remaining -= len(data)
                target.chmod(0o755 if member.mode & 0o111 else 0o644)
        for member, relative in members:
            if member.issym():
                target = destination / relative
                target.symlink_to(member.linkname)
                if not target.resolve().is_relative_to(destination):
                    raise ValueError('Transitive archive link escapes owned destination')
    return {'files': len(members), 'expanded_bytes': total}


def source_matches(repo, records, *, allow_gradle_caches=False):
    expected = {relative: checksum for relative, checksum in records}
    if len(expected) != len(records):
        return False
    allowed_cache_parents = {str(path.parent.relative_to(repo)) for path in generated_outputs(repo, records)}
    actual, total, entries = {}, 0, 0
    walked = 0
    for directory, directories, files in os.walk(repo, followlinks=False):
        walked += 1
        if walked > 10_000:
            return False
        parent = Path(directory)
        for name in list(directories):
            path = parent / name
            if path.is_symlink():
                return False
            if (allow_gradle_caches and name in ('.gradle', '.kotlin')
                    and str(parent.relative_to(repo)) in allowed_cache_parents):
                directories.remove(name)  # Known recreatable local Gradle metadata only.
        for name in files:
            path = parent / name
            relative = str(path.relative_to(repo))
            entries += 1
            if relative not in expected or entries > 2048:
                return False
            data = read_regular(path, 8 * 1024 * 1024)
            total += len(data)
            if total > MAX_SOURCE:
                return False
            actual[relative] = hashlib.sha256(data).hexdigest()
    return actual == expected


def group_members(group):
    result = []
    for directory in Path('/proc').iterdir():
        if not directory.name.isdecimal():
            continue
        try:
            value = (directory / 'stat').read_text()
        except FileNotFoundError:
            continue
        fields = value[value.rfind(')') + 2:].split()
        if len(fields) >= 20 and fields[0] != 'Z' and int(fields[2]) == group:
            result.append(int(directory.name))
            if len(result) > 512:
                raise RuntimeError('Owned guest process-group ceiling exceeded')
    return result


def non_reaping_status(process):
    return os.waitid(os.P_PID, process.pid, os.WEXITED | os.WNOHANG | os.WNOWAIT)


def stop_waitable_group(process):
    # This runs only inside the fresh guest. A still-waitable direct child pins
    # its numeric process group until capture/shutdown completes; no historical
    # or user-supplied process id can authorize this operation.
    non_reaping_status(process)
    for signum in (signal.SIGTERM, signal.SIGKILL):
        if not group_members(process.pid):
            break
        try:
            os.killpg(process.pid, signum)
        except ProcessLookupError:
            # The waitable leader still pins the group id; its last living
            # member may have exited between /proc inspection and signaling.
            pass
        deadline = time.monotonic() + 5
        while group_members(process.pid) and time.monotonic() < deadline:
            time.sleep(0.05)
    if group_members(process.pid):
        raise RuntimeError('Owned guest command group survived shutdown')
    process.wait(timeout=5)


class Commands:
    def __init__(self, root, evidence, environment, deadline):
        self.root, self.evidence, self.environment = root, evidence, environment
        self.receipts, self.deadline = [], deadline

    def run(self, label, args, *, timeout=90, cwd=None, cleanup=False, check=True):
        if CANCELLED and not cleanup:
            raise InterruptedError('Guest cancellation before command creation')
        if len(self.receipts) >= 64:
            raise RuntimeError('Guest command ceiling exceeded')
        if not cleanup and time.monotonic() >= self.deadline:
            raise TimeoutError('Guest total foreground deadline exceeded')
        path = self.evidence / (f'{len(self.receipts):03d}-' + label + '.log')
        receipt = {'label': label, 'args': args, 'started_epoch': time.time(), 'log': path.name}
        process = None
        try:
            with path.open('x') as output:
                process = subprocess.Popen(args, cwd=cwd or self.root, env=self.environment,
                                           stdin=subprocess.DEVNULL, stdout=output,
                                           stderr=subprocess.STDOUT, start_new_session=True)
                deadline = min(time.monotonic() + timeout,
                               float('inf') if cleanup else self.deadline)
                while non_reaping_status(process) is None:
                    if CANCELLED and not cleanup:
                        raise InterruptedError('Guest cancellation requested')
                    if time.monotonic() >= deadline:
                        raise TimeoutError('Guest command deadline exceeded: ' + label)
                    if path.stat().st_size > MAX_LOG or shutil.disk_usage(self.root).free < 64 * 1024 * 1024:
                        raise RuntimeError('Guest log/free-space boundary reached')
                    time.sleep(0.1)
                if path.stat().st_size > MAX_LOG:
                    raise RuntimeError('Completed guest command exceeded the log ceiling')
                if group_members(process.pid):
                    raise RuntimeError('Foreground guest command retained background descendants')
                receipt['exit_code'] = process.wait(timeout=5)
            if check and receipt['exit_code'] != 0:
                raise RuntimeError(label + ' failed; see ' + path.name)
            return receipt['exit_code']
        except BaseException as error:
            receipt['failure'] = type(error).__name__ + ': ' + str(error)
            if process is not None and process.returncode is None:
                try:
                    stop_waitable_group(process)
                    receipt['exit_code'] = process.returncode
                except BaseException as cleanup_error:
                    receipt['cleanup_failure'] = type(cleanup_error).__name__ + ': ' + str(cleanup_error)
                    raise RuntimeError('Guest command and owned-group cleanup failed') from error
            raise
        finally:
            receipt['finished_epoch'] = time.time()
            if path.exists():
                receipt['sha256'] = sha256(path)
            self.receipts.append(receipt)


class HttpsOnly(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        if not new_url.startswith('https://'):
            raise ValueError('Non-HTTPS tool redirect refused')
        return super().redirect_request(request, response, code, message, headers, new_url)


def install_jdk(root, deadline):
    path = root / 'jdk.tar.gz'
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), HttpsOnly())
    if shutil.disk_usage(root).free < 900 * 1024 * 1024:
        raise RuntimeError('Insufficient owned guest space for pinned JDK staging')
    deadline = min(deadline, time.monotonic() + 300)
    if time.monotonic() >= deadline:
        raise TimeoutError('Guest total foreground deadline reached before JDK request')
    with opener.open(JDK_URL, timeout=30) as response, path.open('xb') as output:
        if response.status != 200 or not response.url.startswith('https://'):
            raise ValueError('Pinned JDK request did not return a complete HTTPS response')
        total = 0
        while True:
            if CANCELLED:
                raise InterruptedError('JDK download cancelled')
            if time.monotonic() >= deadline:
                raise TimeoutError('Pinned JDK download deadline exceeded')
            block = response.read(1024 * 1024)
            if not block:
                break
            total += len(block)
            if total > JDK_BYTES:
                raise ValueError('Pinned JDK download size exceeded')
            output.write(block)
    if path.stat().st_size != JDK_BYTES or sha256(path) != JDK_SHA:
        raise ValueError('Pinned JDK bytes/digest mismatch')
    extraction = extract_bounded(path, root / 'jdk', byte_limit=600 * 1024 * 1024, allow_links=True)
    path.unlink()
    roots = list((root / 'jdk').iterdir())
    if len(roots) != 1 or roots[0].name != 'jdk-21.0.12.1+1' or roots[0].is_symlink():
        raise ValueError('Unexpected pinned JDK archive root')
    return roots[0], extraction


PYTHON_TEST_CONTROL = '''import json, pathlib, sys, unittest
repo, result_path = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
loader = unittest.TestLoader()
suite = loader.discover(str(repo / "scripts/release/tests"), pattern="test_*.py")
def ids(item):
    if isinstance(item, unittest.TestSuite):
        return [name for child in item for name in ids(child)]
    return [item.id()]
discovered = ids(suite)
class Result(unittest.TextTestResult):
    def __init__(self, *a, **kw):
        super().__init__(*a, **kw)
        self.outcomes = []
    def addSuccess(self, test):
        super().addSuccess(test)
        self.outcomes.append({"id": test.id(), "status": "PASS"})
    def addFailure(self, test, err):
        super().addFailure(test, err)
        self.outcomes.append({"id": test.id(), "status": "FAIL"})
    def addError(self, test, err):
        super().addError(test, err)
        self.outcomes.append({"id": test.id(), "status": "ERROR"})
    def addSkip(self, test, reason):
        super().addSkip(test, reason)
        self.outcomes.append({"id": test.id(), "status": "SKIP", "reason": reason})
result = unittest.TextTestRunner(verbosity=2, resultclass=Result).run(suite)
ok = (result.wasSuccessful() and not result.skipped and not result.expectedFailures
      and not loader.errors and bool(discovered) and len(discovered) == len(set(discovered))
      and result.testsRun == len(discovered) and len(result.outcomes) == len(discovered)
      and all(row["status"] == "PASS" for row in result.outcomes))
result_path.write_text(json.dumps({"status": "PASS" if ok else "FAIL", "discovered": discovered,
                                  "tests_run": result.testsRun, "outcomes": result.outcomes,
                                  "loader_errors": loader.errors,
                                  "expected_failures": [test.id() for test, _ in result.expectedFailures],
                                  "unexpected_successes": [test.id() for test in result.unexpectedSuccesses]},
                                 indent=2) + "\\n")
sys.exit(0 if ok else 1)
'''


def validate_python(value):
    if not isinstance(value, dict):
        raise ValueError('Missing Python test receipt')
    discovered, outcomes = value.get('discovered'), value.get('outcomes')
    if (not isinstance(discovered, list) or not 1 <= len(discovered) <= 10_000
            or any(not isinstance(name, str) or not 1 <= len(name) <= 2048 for name in discovered)
            or len(discovered) != len(set(discovered))
            or not isinstance(outcomes, list) or len(outcomes) != len(discovered)
            or type(value.get('tests_run')) is not int or value['tests_run'] != len(discovered)
            or value.get('loader_errors') != [] or value.get('expected_failures') != []
            or value.get('unexpected_successes') != [] or value.get('status') != 'PASS'):
        raise ValueError('Python test discovery/count/status did not establish execution')
    if (any(not isinstance(row, dict) or row.get('status') != 'PASS'
            or not isinstance(row.get('id'), str) for row in outcomes)
            or sorted(row['id'] for row in outcomes) != sorted(discovered)):
        raise ValueError('Python test outcomes are missing, duplicated, skipped, or failed')
    return {'executed': len(outcomes), 'passed': len(outcomes), 'skipped': 0}


def parse_xml(data, relative):
    safe_member(relative)
    text = data.decode('utf-8')
    if len(data) > 2 * 1024 * 1024 or '\x00' in text or '<!DOCTYPE' in text.upper() or '<!ENTITY' in text.upper():
        raise ValueError('XML evidence size/encoding/entity boundary exceeded')
    suite = ET.fromstring(text)
    if suite.tag != 'testsuite':
        raise ValueError('Expected one Gradle testsuite per XML file')
    counts = {}
    for key in ('tests', 'failures', 'errors', 'skipped'):
        value = suite.get(key, '')
        if not re.fullmatch(r'\d{1,6}', value):
            raise ValueError('Malformed XML suite count')
        counts[key] = int(value)
    cases, names = [], set()
    direct_cases = suite.findall('testcase')
    if len(direct_cases) != len(suite.findall('.//testcase')) or len(direct_cases) != counts['tests']:
        raise ValueError('Missing or nested XML test descriptors')
    for case in direct_cases:
        descriptor = (case.get('classname'), case.get('name'))
        if (any(not isinstance(item, str) or not 1 <= len(item) <= 2048 for item in descriptor)
                or descriptor in names):
            raise ValueError('Missing or duplicate XML test descriptor')
        names.add(descriptor)
        if any(len(case.findall(key)) != len(case.findall('.//' + key)) for key in ('failure', 'error', 'skipped')):
            raise ValueError('Nested XML test outcomes are not a valid Gradle receipt')
        outcomes = [(node, status) for key, status in (('failure', 'FAIL'), ('error', 'ERROR'), ('skipped', 'SKIP'))
                    for node in case.findall(key)]
        if len(outcomes) > 1:
            raise ValueError('Ambiguous XML test outcome')
        status = outcomes[0][1] if outcomes else 'PASS'
        result = {'class': descriptor[0], 'name': descriptor[1], 'status': status}
        if status == 'SKIP':
            result['reported_reason'] = ''.join(outcomes[0][0].itertext())[:8192]
        cases.append(result)
    for key, status in (('failures', 'FAIL'), ('errors', 'ERROR'), ('skipped', 'SKIP')):
        if counts[key] != sum(case['status'] == status for case in cases):
            raise ValueError('XML aggregate and individual outcomes disagree')
    return {'path': relative, 'sha256': hashlib.sha256(data).hexdigest(), **counts, 'cases': cases}


def collect_xml(repo, evidence):
    result, total = [], 0
    for path in sorted(repo.glob('**/build/test-results/**/TEST-*.xml')):
        if path.is_symlink() or len(result) >= 512:
            raise ValueError('Untrusted/excessive XML results')
        data = read_regular(path, 2 * 1024 * 1024)
        total += len(data)
        if total > 16 * 1024 * 1024:
            raise ValueError('Combined XML evidence size boundary exceeded')
        saved = evidence / 'xml' / path.relative_to(repo)
        saved.parent.mkdir(parents=True, exist_ok=True)
        saved.write_bytes(data)
        result.append(parse_xml(data, str(path.relative_to(repo))))
    return result


def validate_desktop(xml, records):
    if not isinstance(xml, list) or not xml:
        raise ValueError('No Desktop XML receipts')
    modules = {relative.split('/src/', 1)[0] for relative, _ in records
               if '/src/' in relative and relative.endswith('.kt')
               and ('/src/commonTest/' in relative or '/src/desktopTest/' in relative)}
    covered, descriptors, skipped, passed, discovery = set(), set(), [], 0, False
    for suite in xml:
        path = suite['path']
        if '/build/test-results/desktopTest/TEST-' not in path:
            raise ValueError('Unexpected test target in Desktop receipt')
        module = path.split('/build/', 1)[0]
        if suite['tests']:
            covered.add(module)
        for case in suite['cases']:
            descriptor = (module, case['class'], case['name'])
            if descriptor in descriptors:
                raise ValueError('Duplicate Desktop test descriptor across receipts')
            descriptors.add(descriptor)
            if case['status'] not in ('PASS', 'SKIP'):
                raise ValueError('Desktop test failed or errored')
            if case['status'] == 'SKIP':
                # Gradle may decorate JVM method names with () or the KMP target.
                name = re.sub(r'(?:\(\))?(?:\[desktop\])?$', '', case['name'])
                if module != 'shared/transport-p2p' or case['class'] != LOOPBACK_CLASS or name not in KNOWN_SKIPS:
                    raise ValueError('Unexpected skipped Desktop test; independent disposition required')
                skipped.append({**case, 'method': name, 'source_disposition':
                                'Existing disabled multi-device fixtures; NOT physical LAN evidence'})
            else:
                passed += 1
                discovery = discovery or (module == 'shared/transport-p2p' and case['class'] == DISCOVERY_CLASS)
    if (modules != covered or not passed or not discovery or len(skipped) != len(KNOWN_SKIPS)
            or {row['method'] for row in skipped} != KNOWN_SKIPS):
        raise ValueError('Desktop module/discovery/known-skip coverage is incomplete')
    return {'passed': passed, 'skipped': skipped, 'modules': sorted(covered),
            'discovery_contract_executed': discovery}


def generated_outputs(repo, records):
    parents = {Path('.'), Path('build-logic'), Path('build-logic/convention')}
    parents.update(Path(relative).parent for relative, _ in records if relative.endswith('/build.gradle.kts'))
    return sorted({repo / parent / 'build' for parent in parents}, key=lambda p: len(p.parts), reverse=True)


def remaining_java():
    found = []
    for directory in Path('/proc').iterdir():
        if not directory.name.isdecimal():
            continue
        try:
            if (directory / 'comm').read_text().strip() == 'java':
                fields = (directory / 'stat').read_text().rsplit(')', 1)[1].split()
                if fields[0] != 'Z':
                    found.append({'pid': int(directory.name), 'started_ticks': fields[19]})
        except FileNotFoundError:
            continue
    return found


def remove_generated_output(repo, path):
    relative = path.relative_to(repo)
    cursor = repo
    for part in relative.parts:
        cursor = cursor / part
        try:
            info = cursor.lstat()
        except FileNotFoundError:
            return False
        if not stat.S_ISDIR(info.st_mode) or cursor.is_symlink() or info.st_uid != os.getuid():
            raise RuntimeError('Generated output has an unowned/symlink/non-directory ancestor')
    shutil.rmtree(path)
    return True


def write_evidence_bundle(root, evidence):
    paths, total = [], 0
    for path in sorted(evidence.rglob('*')):
        if path.is_symlink():
            raise ValueError('Evidence symlink refused')
        if path.is_file():
            total += path.stat().st_size
            if len(paths) >= 2048 or total > MAX_EVIDENCE:
                raise ValueError('Compact guest evidence ceiling exceeded')
            paths.append(path)
        elif not path.is_dir():
            raise ValueError('Unexpected evidence node')
    temporary = root / 'evidence.tar.gz.partial'
    with tarfile.open(temporary, 'x:gz') as archive:
        for path in paths:
            archive.add(path, arcname=str(path.relative_to(evidence)), recursive=False)
    temporary.rename(root / 'evidence.tar.gz')


def main(args):
    root = Path(args.root)
    if (platform.system() != 'Linux' or platform.machine() != 'aarch64'
            or root.parent != Path('/home/parlor') or not re.fullmatch(r'run-[0-9a-f]{32}', root.name)
            or root.is_symlink() or root.resolve() != root or root.stat().st_uid != os.getuid()
            or root.stat().st_mode & 0o077
            or {path.name for path in root.iterdir()} != {'source.tar.gz', 'source.json', 'guest_verification.py'}):
        raise RuntimeError('Only the new owned Linux ARM64 guest root is allowed')
    signal.signal(signal.SIGINT, cancel)
    signal.signal(signal.SIGTERM, cancel)
    signal.signal(signal.SIGHUP, cancel)
    evidence = root / 'evidence'
    evidence.mkdir(mode=0o700)
    report = {'schema': 1, 'status': 'FAIL', 'started_epoch': time.time(), 'cleanup_errors': [],
              'run_token': root.name.removeprefix('run-'),
              'source_manifest_sha256': args.source_manifest_sha256,
              'source_archive_sha256': args.archive_sha256,
              'control_sha256': sha256(__file__),
              'platform': {'system': platform.system(), 'machine': platform.machine()},
              'gradle': {'status': 'NOT_RUN'}, 'python': {'status': 'NOT_RUN'}}
    source, repo, commands, outputs, gradle_started = None, root / 'source', None, [], False
    deadline = time.monotonic() + 1800

    def cleanup_error(label, error):
        report['status'] = 'FAIL'
        report['cleanup_errors'].append({label: type(error).__name__ + ': ' + str(error)})

    def stop_gradle(label):
        code = commands.run(label, ['./gradlew', '--stop'], cwd=repo, timeout=90,
                            cleanup=True, check=False)
        report['gradle'][label + '_exit_code'] = code
        if code != 0:
            raise RuntimeError(label + ' returned ' + str(code))

    try:
        archive = root / 'source.tar.gz'
        if (not SHA.fullmatch(args.archive_sha256) or archive.is_symlink()
                or archive.stat().st_size > MAX_SOURCE + 1024 * 1024 or sha256(archive) != args.archive_sha256):
            raise ValueError('Source archive digest mismatch')
        source = json_unique(read_regular(root / 'source.json', 2 * 1024 * 1024))
        records = manifest_records(source, args.source_manifest_sha256)
        report['source'] = {key: source[key] for key in ('commit', 'tree', 'diff_sha256', 'source_manifest_sha256')}
        report['source_extraction'] = extract_bounded(archive, repo, byte_limit=MAX_SOURCE)
        archive.unlink()
        if not source_matches(repo, records):
            raise ValueError('Extracted source differs from frozen manifest')
        report['source_before_matches'] = True
        outputs = generated_outputs(repo, records)
        if any(path.exists() or path.is_symlink() for path in outputs):
            raise RuntimeError('Fresh guest source unexpectedly contains build outputs')
        temporary = root / 'tmp'
        temporary.mkdir(mode=0o700)
        environment = {'HOME': '/home/parlor', 'PATH': '/usr/bin:/bin:/usr/sbin:/sbin',
                       'LANG': 'C.UTF-8', 'LC_ALL': 'C.UTF-8', 'TMPDIR': str(temporary),
                       'GRADLE_USER_HOME': str(root / 'gradle-home'), 'PYTHONDONTWRITEBYTECODE': '1',
                       'DEBIAN_FRONTEND': 'noninteractive'}
        commands = Commands(root, evidence, environment, deadline)
        memory = int(re.search(r'^MemTotal:\s+(\d+)', Path('/proc/meminfo').read_text(), re.M)[1]) * 1024
        mounts = [line.split()[2] for line in Path('/proc/mounts').read_text().splitlines()]
        if os.cpu_count() != 2 or not 2 * 1024**3 < memory <= 3 * 1024**3 or any(item in mounts for item in ('virtiofs', '9p', 'fuse.sshfs')):
            raise RuntimeError('Guest resource/no-host-mount attestation failed')
        report['guest_attestation'] = {'cpus': os.cpu_count(), 'memory_bytes': memory,
                                       'mount_types': sorted(set(mounts)), 'os_release': Path('/etc/os-release').read_text()}
        apt = ['sudo', '-n', 'env', 'DEBIAN_FRONTEND=noninteractive', 'apt-get',
               '-o', 'Acquire::Retries=0', '-o', 'Acquire::http::Timeout=30',
               '-o', 'Acquire::https::Timeout=30']
        commands.run('apt-metadata', apt + ['update'], timeout=180)
        commands.run('guest-prerequisites', apt + ['install', '-y', '--no-upgrade', '--no-install-recommends',
                                                 'git', 'unzip', 'perl', 'openssl', 'libfontconfig1', 'libfreetype6',
                                                 'libx11-6', 'libxi6', 'libxrender1', 'libxtst6'], timeout=240)
        commands.run('apt-cache-clean', apt + ['clean'])
        commands.run('guest-package-versions', ['/usr/bin/dpkg-query', '-W',
                     '-f=${Package}\t${Version}\n', 'git', 'unzip', 'perl', 'openssl', 'libfontconfig1',
                     'libfreetype6', 'libx11-6', 'libxi6', 'libxrender1', 'libxtst6'])
        jdk, extraction = install_jdk(root, deadline)
        report['jdk'] = {'url': JDK_URL, 'sha256': JDK_SHA, 'bytes': JDK_BYTES, 'extraction': extraction}
        environment.update(JAVA_HOME=str(jdk), PATH=str(jdk / 'bin') + ':' + environment['PATH'])
        commands.run('java-version', [str(jdk / 'bin/java'), '-version'])
        commands.run('python-version', ['/usr/bin/python3', '--version'])
        commands.run('gnu-stat-version', ['/usr/bin/stat', '--version'])
        commands.run('git-version', ['/usr/bin/git', '--version'])
        control = root / 'python_tests.py'
        control.write_text(PYTHON_TEST_CONTROL)
        python_code = commands.run('release-python-tests', ['/usr/bin/python3', '-B', str(control), str(repo),
                                              str(evidence / 'python-tests.json')], cwd=repo, timeout=600, check=False)
        report['python'] = json_unique(read_regular(evidence / 'python-tests.json', 2 * 1024 * 1024))
        report['python_summary'] = validate_python(report['python'])
        if python_code != 0:
            raise RuntimeError('Python process exit and test receipt disagree')
        # Python fixtures own their own temporary signing material. Fail closed
        # on any leftover Java process before starting the separate Gradle cycle.
        if remaining_java():
            raise RuntimeError('Python fixture left Java workers in the owned guest')
        if shutil.disk_usage(root).free < 512 * 1024 * 1024:
            raise RuntimeError('Guest free space insufficient for a bounded Desktop attempt')
        gradle_started = True
        report['gradle']['started'] = True
        try:
            code = commands.run('productionDesktopCheck', DESKTOP_ARGS,
                                cwd=repo, timeout=1500, check=False)
            report['gradle'].update(exit_code=code, status='PASS' if code == 0 else 'FAIL')
        finally:
            # Stop first, before parsing/retaining reports or deleting outputs.
            try:
                stop_gradle('immediate-gradle-stop')
            except BaseException as error:
                cleanup_error('immediate_gradle_stop', error)
            try:
                report['gradle']['xml'] = collect_xml(repo, evidence)
            except BaseException as error:
                report['gradle']['xml_capture_failure'] = type(error).__name__ + ': ' + str(error)
                raise
        if report['gradle'].get('exit_code') != 0:
            raise RuntimeError('Desktop gate failed or produced no executable test receipts')
        report['desktop_summary'] = validate_desktop(report['gradle'].get('xml'), records)
        if not report['cleanup_errors']:
            report['status'] = 'PASS'
    except BaseException as error:
        report['failure'] = type(error).__name__ + ': ' + str(error)
    finally:
        if (commands is not None and gradle_started
                and report['gradle'].get('immediate-gradle-stop_exit_code') != 0):
            try:
                stop_gradle('failure-gradle-stop')
            except BaseException as error:
                cleanup_error('failure_gradle_stop', error)
        # Do not delete live workers' build files. The host must close the
        # exclusively owned VM before its remaining disk can be removed.
        may_remove_outputs = False
        try:
            wait_until = time.monotonic() + 10
            while remaining_java() and time.monotonic() < wait_until:
                time.sleep(0.1)
            report['java_before_output_cleanup'] = remaining_java()
            may_remove_outputs = not report['java_before_output_cleanup']
        except BaseException as error:
            cleanup_error('java_before_output_cleanup', error)
        removed = []
        for path in outputs:
            try:
                if may_remove_outputs and remove_generated_output(repo, path):
                    removed.append(str(path.relative_to(repo)))
            except BaseException as error:
                cleanup_error('output_' + str(path.relative_to(repo)), error)
        report['removed_build_outputs'] = removed
        try:
            report['remaining_build_outputs'] = [str(path.relative_to(repo)) for path in outputs
                                                 if path.exists() or path.is_symlink()]
            if report['remaining_build_outputs']:
                raise RuntimeError('Guest build outputs retained for ownership-safe VM shutdown')
            if source is not None and repo.is_dir():
                report['source_after_matches'] = source_matches(repo, source['source_manifest'],
                                                               allow_gradle_caches=True)
                if not report['source_after_matches']:
                    raise RuntimeError('Source inventory/bytes changed during guest execution')
        except BaseException as error:
            cleanup_error('source_and_outputs', error)
        if commands is not None and gradle_started:
            try:
                stop_gradle('post-cleanup-gradle-stop')
            except BaseException as error:
                cleanup_error('post_cleanup_gradle_stop', error)
        try:
            wait_until = time.monotonic() + 10
            while remaining_java() and time.monotonic() < wait_until:
                time.sleep(0.1)
            report['remaining_java'] = remaining_java()
            if report['remaining_java']:
                raise RuntimeError('Java workers retained for ownership-safe VM shutdown')
            report['control_after_sha256'] = sha256(__file__)
            if report['control_after_sha256'] != report['control_sha256']:
                raise RuntimeError('Guest control changed during execution')
            if source is not None and repo.is_dir():
                report['source_after_matches'] = source_matches(repo, source['source_manifest'],
                                                               allow_gradle_caches=True)
                if not report['source_after_matches']:
                    raise RuntimeError('Final source inventory/bytes changed after Gradle stop')
        except BaseException as error:
            cleanup_error('final_workers_and_control', error)
        if CANCELLED:
            cleanup_error('cancellation', InterruptedError('Guest cancellation was requested'))
        report['commands'] = commands.receipts if commands is not None else []
        report['finished_epoch'] = time.time()
        (evidence / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
        try:
            write_evidence_bundle(root, evidence)
        except BaseException as error:
            cleanup_error('evidence_bundle', error)
            # The host also copies this small standalone receipt if bundling
            # failed. A partial bundle must never be accepted as completion.
            (evidence / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
    print(report['status'] + ': Linux guest verification')
    return 0 if report['status'] == 'PASS' else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('root', 'archive-sha256', 'source-manifest-sha256'):
        parser.add_argument('--' + name, required=True)
    raise SystemExit(main(parser.parse_args()))
