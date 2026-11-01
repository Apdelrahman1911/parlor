"""Host-only bounded OS samples of the owned public-setup Simulator process.

No shipping/copy-source change, AX query, gesture, assertion or retry. Sampling
can perturb scheduling; it is not qualification and never proves neutrality.
"""
import hashlib
import json
import math
import os
from pathlib import Path
import plistlib
import re
import subprocess
import threading
import time

import frozen_sources

MAX_REPORT = 4 * 1024 * 1024
MAX_SAMPLES = 180
MAX_DISCOVERIES = 3600
SAMPLE_SECONDS = 5
INTERVAL_MS = 10
BUNDLE = 'me.parlor.ios.debug'
UUID = re.compile(r'[A-F0-9]{8}(?:-[A-F0-9]{4}){3}-[A-F0-9]{12}')


def require(ok, reason):
    if not ok:
        raise ValueError(reason)


def environment():
    # Never forward GitHub credentials, tracking tokens or injected loader flags.
    return {k: os.environ[k] for k in ('HOME', 'PATH', 'TMPDIR') if k in os.environ} | {'LC_ALL': 'C'}


def sample_command(pid, target, seconds=SAMPLE_SECONDS):
    require(type(pid) is int and pid > 0 and seconds in (1, SAMPLE_SECONDS), 'Bounded positive owned PID')
    return ['/usr/bin/sample', str(pid), str(seconds), str(INTERVAL_MS), '-mayDie', '-file', str(target)]


def read_sample(path, pid):
    raw = frozen_sources._read(path, MAX_REPORT)
    text = raw.decode('utf-8')
    require(re.search(r'Analysis of sampling .+ \(pid '+str(pid)+r'\)', text) is not None,
            'OS sample must identify the admitted PID')
    require('Call graph:' in text and 'Thread_' in text, 'Actual native thread stacks required')
    return {'sha256': hashlib.sha256(raw).hexdigest(), 'bytes': len(raw)}


def owned_images(raw, simulator, executable_sha, home):
    """Admit only the exact built Debug image installed on the one owned device."""
    require(UUID.fullmatch(simulator) is not None, 'Canonical actual owned Simulator')
    base = home/'Library/Developer/CoreSimulator/Devices'/simulator/'data/Containers/Bundle/Application'
    candidates = []
    for line in raw.decode().splitlines():
        if not line.startswith('f'):
            continue
        path = Path(line[1:])
        if not path.is_absolute() or path.name != 'Parlor' or path.parent.name != 'Parlor.app':
            continue
        if path.parent.parent.parent != base or UUID.fullmatch(path.parent.parent.name) is None:
            continue
        if path.resolve() != path or not path.is_file():
            continue
        info = plistlib.loads(frozen_sources._read(path.parent/'Info.plist', 65536))
        require((info.get('CFBundleIdentifier'), info.get('CFBundleExecutable')) == (BUNDLE, 'Parlor'),
                'Only the owned Debug bundle can be sampled')
        digest = hashlib.sha256(frozen_sources._read(path, 32 * 1024 * 1024)).hexdigest()
        require(digest == executable_sha, 'Installed image must equal actual original built product')
        candidates.append({'image': str(path), 'executable_sha256': digest})
    require(len(candidates) <= 1, 'Unambiguous owned native executable')
    return candidates


def tool_control(directory):
    require(not directory.exists(), 'Fresh capability output')
    directory.mkdir(mode=0o700)
    report = directory/'sample-control.txt'
    with (directory/'control.log').open('xb') as log:
        child = subprocess.Popen(['/bin/sleep', '4'], env=environment())
        try:
            result = subprocess.run(sample_command(child.pid, report, 1), stdout=log, stderr=log,
                                    env=environment(), timeout=8)
            require(result.returncode == 0, 'OS sampler attach capability failed')
            receipt = read_sample(report, child.pid)
        finally:
            if child.poll() is None:
                child.terminate()
            child.wait(timeout=5)
    value = {'schema': 1, 'pid': child.pid, 'exit': result.returncode, 'child_reaped': True,
             'report': receipt, 'simulator_attach_verified': False, 'physical_verified': False}
    with (directory/'capability.json').open('x') as output:
        json.dump(value, output, indent=2)
    return value


class NativeSampler:
    """One host observer; serial bounded samples, never sample unrelated processes."""
    def __init__(self, directory, simulator, executable_sha):
        require(UUID.fullmatch(simulator) is not None and re.fullmatch('[0-9a-f]{64}', executable_sha),
                'Explicit owned device and exact product digest')
        require(not directory.exists(), 'Fresh native sample directory')
        directory.mkdir(mode=0o700)
        self.directory, self.simulator, self.executable_sha = directory, simulator, executable_sha
        self.stop = threading.Event()
        self.rows, self.error, self.discoveries = [], None, 0
        self.thread = threading.Thread(target=self._observe, name='parlor-owned-os-sampler')
        self.started_ms = time.monotonic() * 1000
        self.home = Path(os.environ['HOME']).resolve(strict=True)

    def _discover(self):
        self.discoveries += 1
        require(self.discoveries <= MAX_DISCOVERIES, 'Finite OS process-discovery budget')
        result = subprocess.run(['/bin/ps', '-axo', 'pid=,ucomm='], env=environment(),
                                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=3)
        require(result.returncode == 0 and len(result.stdout) <= 1024 * 1024, 'Bounded native process names')
        found = []
        for line in result.stdout.decode().splitlines():
            parts = line.split()
            if len(parts) != 2 or parts[1] != 'Parlor' or not parts[0].isdigit():
                continue
            pid = int(parts[0])
            files = subprocess.run(['/usr/sbin/lsof', '-a', '-p', str(pid), '-d', 'txt', '-Fn'],
                                   env=environment(), stdout=subprocess.PIPE,
                                   stderr=subprocess.DEVNULL, timeout=3)
            # The public test may terminate between enumeration and image inspection.
            if files.returncode == 1:
                continue
            require(files.returncode == 0 and len(files.stdout) <= 1024 * 1024, 'Bounded native image inventory')
            for image in owned_images(files.stdout, self.simulator, self.executable_sha, self.home):
                found.append(dict(pid=pid, **image))
        require(len(found) <= 1, 'Only one owned application may be live')
        return found

    def _sample(self, image):
        require(len(self.rows) < MAX_SAMPLES, 'Finite OS stack budget')
        index = len(self.rows) + 1
        report = self.directory/('sample-%03d.txt' % index)
        log = self.directory/('sample-%03d.log' % index)
        row = dict(index=index, **image, started_ms=time.monotonic() * 1000,
                   started_unix=time.time(), command=sample_command(image['pid'], report),
                   status='STARTED', report=report.name)
        self.rows.append(row)
        self._checkpoint()
        with log.open('xb') as stream:
            result = subprocess.run(row['command'], env=environment(), stdout=stream, stderr=stream, timeout=8)
        row.update(ended_ms=time.monotonic() * 1000, ended_unix=time.time(), exit=result.returncode,
                   sampler_child_reaped=True)
        if result.returncode == 0:
            row.update(status='SAMPLED', **read_sample(report, image['pid']))
        else:
            # A nonzero sampler exit is not a successful observation. Preserve it;
            # admit only a confirmed app exit, not an attach/permissions error.
            alive = subprocess.run(['/bin/ps', '-p', str(image['pid']), '-o', 'pid='], env=environment(),
                                   stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=3)
            require(alive.returncode == 1 and not alive.stdout.strip(), 'Native sampler failed on a live application')
            row['status'] = 'APPLICATION_EXITED_DURING_SAMPLE'
            if report.exists():
                raw = frozen_sources._read(report, MAX_REPORT)
                row.update(sha256=hashlib.sha256(raw).hexdigest(), bytes=len(raw))
        self._checkpoint()

    def _observe(self):
        try:
            while not self.stop.is_set():
                found = self._discover()
                if found and not self.stop.is_set():
                    self._sample(found[0])
                else:
                    self.stop.wait(0.25)  # Host discovery only; no app polling/query.
        except BaseException as error:
            self.error = type(error).__name__ + ': ' + str(error)
        finally:
            self._checkpoint()

    def _checkpoint(self):
        receipt = {'schema': 1, 'simulator': self.simulator, 'bundle': BUNDLE,
                   'producer_directory': str(self.directory),
                   'executable_sha256': self.executable_sha, 'started_ms': self.started_ms,
                   'discoveries': self.discoveries, 'samples': self.rows, 'error': self.error,
                   'sample_seconds': SAMPLE_SECONDS, 'interval_ms': INTERVAL_MS,
                   'original_sources_changed': False, 'additional_ax_queries': False,
                   'native_timing_neutrality': False, 'shipping_qualified': False, 'physical_verified': False}
        target = self.directory/'samples.json'
        temporary = self.directory/'samples.writing'
        with temporary.open('w') as output:
            json.dump(receipt, output, indent=2)
        temporary.replace(target)

    def __enter__(self):
        self.thread.start()
        return self

    def __exit__(self, kind, value, traceback):
        self.stop.set()
        self.thread.join(timeout=12)
        require(not self.thread.is_alive(), 'Owned native sampler must finish and reap before Simulator cleanup')
        self._checkpoint()
        if kind is None:
            require(self.error is None, self.error or 'Native sampler failed')
            require(any(row['status'] == 'SAMPLED' for row in self.rows), 'At least one actual owned-process stack required')


def execute_observed(capture, arguments, label, timeout, **kwargs):
    """Exact existing test command, wrapped only by an external OS observer."""
    if label != 'compose-tests':
        return capture(arguments, label, timeout, **kwargs)
    require(timeout == 900, 'Original process deadline unchanged')
    destinations = [str(a).removeprefix('platform=iOS Simulator,id=') for a in arguments
                    if str(a).startswith('platform=iOS Simulator,id=')]
    require(len(destinations) == 1 and UUID.fullmatch(destinations[0]), 'Exact original owned test destination')
    log = Path(kwargs['environment']['PARLOR_INPUT_LOG'])
    require(log.name == 'test.log' and log.parent.name == 'compose', 'Original public pair only')
    products = json.loads(frozen_sources._read(log.parent/'product.json', 2 * 1024 * 1024))
    with NativeSampler(log.parent/'native-stacks', destinations[0], products['Parlor']):
        return capture(arguments, label, timeout, **kwargs)


def verify_receipt(directory, simulator, executable_sha, *, producer_directory=None):
    """Read back every actual report; unchanged receipt cannot invent coverage."""
    receipt = json.loads(frozen_sources._read(directory/'samples.json', 2 * 1024 * 1024))
    producer_directory = producer_directory or directory
    require(receipt['producer_directory'] == str(producer_directory), 'Explicit producer sample-path custody')
    require(receipt['schema'] == 1 and receipt['simulator'] == simulator and receipt['bundle'] == BUNDLE
            and receipt['executable_sha256'] == executable_sha and receipt['error'] is None,
            'Exact completed native observer/source identity')
    for flag in ('original_sources_changed', 'additional_ax_queries', 'native_timing_neutrality',
                 'shipping_qualified', 'physical_verified'):
        require(receipt[flag] is False, 'No source/query/neutrality/qualification attestation')
    require((receipt['sample_seconds'], receipt['interval_ms']) == (SAMPLE_SECONDS, INTERVAL_MS)
            and 0 < receipt['discoveries'] <= MAX_DISCOVERIES
            and 0 < len(receipt['samples']) <= MAX_SAMPLES, 'Exact bounded real sampling parameters')
    actual = []
    previous = receipt['started_ms']
    for index, row in enumerate(receipt['samples'], 1):
        path = directory/('sample-%03d.txt' % index)
        require(all(type(row[k]) in (int, float) and math.isfinite(row[k]) and row[k] >= 0
                    for k in ('started_ms', 'ended_ms', 'started_unix', 'ended_unix')), 'Finite actual sample clocks')
        require(row['index'] == index and row['report'] == path.name and row['sampler_child_reaped'] is True
                and row['executable_sha256'] == executable_sha and type(row['pid']) is int and row['pid'] > 0
                and previous <= row['started_ms'] <= row['ended_ms'] <= receipt['started_ms']+912000
                and row['command'] == sample_command(row['pid'], producer_directory/path.name), 'Exact ordered real OS commands and ownership')
        previous = row['ended_ms']
        if row['status'] == 'SAMPLED':
            require(row['exit'] == 0 and read_sample(path, row['pid']) == {k: row[k] for k in ('sha256', 'bytes')},
                    'Exact actual stack bytes and native PID')
            actual.append(row['pid'])
        else:
            require(row['status'] == 'APPLICATION_EXITED_DURING_SAMPLE' and row['exit'] != 0,
                    'No fake successful stack from a vanished process')
            if 'sha256' in row:
                raw = frozen_sources._read(path, MAX_REPORT)
                require(len(raw) == row['bytes'] and hashlib.sha256(raw).hexdigest() == row['sha256'],
                        'Original incomplete OS report preserved')
            else:
                require(not path.exists(), 'No unrecorded OS report')
    require(actual and {p.name for p in directory.glob('sample-*.txt')} ==
            {row['report'] for row in receipt['samples'] if 'sha256' in row}, 'All actual native stacks retained')
    return {'sampled_reports': len(actual), 'sampled_processes': sorted(set(actual)),
            'receipt_sha256': hashlib.sha256((directory/'samples.json').read_bytes()).hexdigest(),
            'application_root_cause': False, 'shipping_qualified': False, 'physical_verified': False}
