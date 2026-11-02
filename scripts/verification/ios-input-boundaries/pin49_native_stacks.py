"""Host-only bounded OS samples of the owned public-setup Simulator process.

No shipping/copy-source change, AX query, gesture, assertion or retry. Sampling
can perturb scheduling; it is not qualification and never proves neutrality.
"""
import hashlib
import datetime
import json
import math
import os
from pathlib import Path
import plistlib
import re
import stat
import subprocess
import sys
import threading
import time

import frozen_sources
import pin49_kernel_channel as kernel

MAX_REPORT = 4 * 1024 * 1024
MAX_SAMPLES = 180
MAX_DISCOVERIES = 3600
SAMPLE_SECONDS = 1
SAMPLE_CADENCE_SECONDS = 5
DISCOVERY_CADENCE_SECONDS = 1
INTERVAL_MS = 10
SAMPLE_DEADLINE_SECONDS = 8
TOOL_STACK_AFTER_SECONDS = 2
TOOL_STACK_DEADLINE_SECONDS = 3
BUNDLE = 'me.parlor.ios.debug'
UUID = re.compile(r'[A-F0-9]{8}(?:-[A-F0-9]{4}){3}-[A-F0-9]{12}')
MAX_TEST_LOG = 4 * 1024 * 1024
FIRST_METHOD = b"Test Case '-[iosAppUITests.IOSAppLaunchUITests testAdditionalGamesExposeOnlyMultiplayerSetupInArabic]' started."
HOME_WAITER = re.compile(rb'^    t = +[0-9]+\.[0-9]+s Waiting 30\.0s for "parlor-home-brand" StaticText to exist$', re.M)


def original_home_marker(raw):
    """Read only already-produced original log; never query/control the app."""
    require(type(raw) is bytes and len(raw) <= MAX_TEST_LOG, 'Bounded existing original test log')
    first = raw.find(FIRST_METHOD)
    if first < 0: return None
    following = raw.find(b"Test Case '-[", first+len(FIRST_METHOD))
    marker = HOME_WAITER.search(raw, first+len(FIRST_METHOD))
    if marker is None: return None
    require(following < 0 or marker.start() < following, 'First original method owns observer activation')
    require(b'Launch '+BUNDLE.encode() in raw[first:marker.start()], 'Existing first application launch before observer activation')
    return dict(kind='original-first-home-waiter', log_bytes=len(raw), log_prefix_sha256=hashlib.sha256(raw).hexdigest(),
                first_method_offset=first, marker_offset=marker.start(), marker_end=marker.end())


def read_live_log(path):
    require(path.resolve() == path.absolute(), 'Owned unredirected original log')
    try: fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    except FileNotFoundError: return b''
    try:
        before = os.fstat(fd)
        require(stat.S_ISREG(before.st_mode) and before.st_nlink == 1 and before.st_size <= MAX_TEST_LOG,
                'Bounded regular live original log')
        chunks, size = [], 0
        while size < before.st_size:
            part = os.read(fd, min(8192, before.st_size-size))
            require(part, 'Original log may append, never truncate a read prefix')
            chunks.append(part); size += len(part)
        return b''.join(chunks)
    finally: os.close(fd)


def require(ok, reason):
    if not ok:
        raise ValueError(reason)


def environment():
    # Never forward GitHub credentials, tracking tokens or injected loader flags.
    return {k: os.environ[k] for k in ('HOME', 'PATH', 'TMPDIR') if k in os.environ} | {'LC_ALL': 'C'}


def sample_command(pid, target, seconds=SAMPLE_SECONDS):
    require(type(pid) is int and pid > 0 and seconds in (1, SAMPLE_SECONDS), 'Bounded positive owned PID')
    # Actual macOS15.8 help/man in41406518327: -mayDie reads symbols
    # immediately. Omit that eager mode for the bounded live-process window;
    # retain normal report processing,8s deadline and strict actual-stack/PID
    # admission. Early app exit is still incomplete, never a waived report.
    return ['/usr/bin/sample', str(pid), str(seconds), str(INTERVAL_MS), '-file', str(target)]


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
        # lsof -Fn emits n for file names; f (when selected) is a descriptor.
        if not line.startswith('n'):
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
    def __init__(self, directory, simulator, executable_sha, *, kernel_channel=None, original_log=None):
        require(UUID.fullmatch(simulator) is not None and re.fullmatch('[0-9a-f]{64}', executable_sha),
                'Explicit owned device and exact product digest')
        require(not directory.exists(), 'Fresh native sample directory')
        directory.mkdir(mode=0o700)
        self.directory, self.simulator, self.executable_sha = directory, simulator, executable_sha
        self.stop = threading.Event()
        self.rows, self.error, self.discoveries = [], None, 0
        self.kernel = kernel_channel
        self.original_log, self.activation = original_log, None
        self.thread = threading.Thread(target=self._observe, name='parlor-owned-os-sampler')
        self.started_ms = time.monotonic() * 1000
        self.home = Path(os.environ['HOME']).resolve(strict=True)

    def _discover(self):
        self.discoveries += 1
        require(self.discoveries <= MAX_DISCOVERIES, 'Finite OS process-discovery budget')
        receipt = self.kernel.discover()
        require(receipt['schema'] == 1 and receipt['selector'] == 'PROC_UID_ONLY'
                and receipt['simulator'] == self.simulator and receipt['own_process_seen'] is True,
                'Actual current-UID kernel path capability')
        found = []
        for candidate in receipt['paths']:
            # Common strict image/bundle/byte validator; the path came from libproc,
            # not a fabricated ps row, command-line arguments or arbitrary file.
            for image in owned_images(('n'+candidate['image']).encode(), self.simulator, self.executable_sha, self.home):
                found.append(dict(pid=candidate['pid'], **image))
        require(len(found) <= 1, 'Only one owned application may be live')
        return found

    def _sample(self, image):
        require(len(self.rows) < MAX_SAMPLES, 'Finite OS stack budget')
        index = len(self.rows) + 1
        report = self.directory/('sample-%03d.txt' % index)
        log = self.directory/('sample-%03d.log' % index)
        row = dict(index=index, **image, started_ms=time.monotonic() * 1000,
                   started_unix=time.time(), command=sample_command(image['pid'], report),
                   status='STARTED', report=report.name, sampler_deadline_seconds=SAMPLE_DEADLINE_SECONDS)
        self.rows.append(row)
        self._checkpoint()
        with log.open('xb') as stream:
            exit_code = self._run_owned_sample(row, stream)
        row.update(ended_ms=time.monotonic() * 1000, ended_unix=time.time(), exit=exit_code,
                   sampler_child_reaped=True)
        if exit_code == 0:
            row.update(status='SAMPLED', **read_sample(report, image['pid']))
        else:
            # A nonzero sampler exit is not a successful observation. Preserve it;
            # admit only a confirmed app exit, not an attach/permissions error.
            require(not any(current['pid'] == image['pid'] for current in self._discover()),
                    'Native sampler failed on a live owned application')
            row['status'] = 'APPLICATION_EXITED_DURING_SAMPLE'
            if report.exists():
                raw = frozen_sources._read(report, MAX_REPORT)
                row.update(sha256=hashlib.sha256(raw).hexdigest(), bytes=len(raw))
        self._checkpoint()
        if row['collector_stack'] is not None:
            require(row['collector_stack']['status'] == 'SAMPLED', 'Owned slow-tool observation remains incomplete')

    def _run_owned_sample(self, row, stream):
        """Diagnose the first slow owned tool inside, never beyond, its8s bound.

        Both eager and normal sampling stalled without a completion marker.
        Sampling our own Popen child distinguishes tool stages, not app cause.
        No unrelated process, original test action, added app query or retry.
        """
        started = time.monotonic()
        deadline = started + SAMPLE_DEADLINE_SECONDS
        row['sampler_started_ms'] = started*1000
        child = subprocess.Popen(row['command'], env=environment(), stdout=stream, stderr=stream)
        row['sampler_pid'] = child.pid
        row['collector_stack'] = None
        try:
            initial_remaining = deadline-time.monotonic()
            if initial_remaining <= 0:
                raise subprocess.TimeoutExpired(row['command'], SAMPLE_DEADLINE_SECONDS)
            if row['index'] == 1:
                try:
                    child.wait(timeout=min(TOOL_STACK_AFTER_SECONDS, initial_remaining))
                except subprocess.TimeoutExpired:
                    # The only additional target is this exact owned sample
                    # child, not another app/runner/System process.
                    if child.poll() is None:
                        if time.monotonic() >= deadline:
                            raise subprocess.TimeoutExpired(row['command'], SAMPLE_DEADLINE_SECONDS)
                        target = self.directory/'collector-001.txt'
                        command = sample_command(child.pid, target)
                        observation = dict(pid=child.pid, command=command, report=target.name,
                                           started_ms=time.monotonic()*1000, started_unix=time.time(),
                                           deadline_seconds=TOOL_STACK_DEADLINE_SECONDS, status='STARTED')
                        row['collector_stack'] = observation
                        self._checkpoint()
                        try:
                            with (self.directory/'collector-001.log').open('xb') as output:
                                observed = subprocess.run(command, env=environment(), stdout=output, stderr=output,
                                                      timeout=min(TOOL_STACK_DEADLINE_SECONDS, max(0, deadline-time.monotonic())))
                            observation.update(ended_ms=time.monotonic()*1000, ended_unix=time.time(),
                                               exit=observed.returncode, child_reaped=True)
                            require(observation['ended_ms']-observation['started_ms'] < TOOL_STACK_DEADLINE_SECONDS*1000,
                                    'Owned slow-tool observation exceeded its own deadline')
                            require(observed.returncode == 0, 'Owned slow-tool stack collection failed')
                            observation.update(status='SAMPLED', **read_sample(target, child.pid))
                        except (subprocess.TimeoutExpired, ValueError) as error:
                            # Still allow the ORIGINAL primary8s window to
                            # finish; retain any real report before rejecting
                            # the incomplete auxiliary observation. No waiver.
                            observation.update(status='FAILED', error=type(error).__name__+': '+str(error))
                            if isinstance(error, subprocess.TimeoutExpired):
                                # subprocess.run kills/reaps this exact child
                                # before surfacing TimeoutExpired.
                                observation.update(ended_ms=time.monotonic()*1000, ended_unix=time.time(),
                                                   child_reaped=True)
            remaining = deadline-time.monotonic()
            if remaining <= 0:
                raise subprocess.TimeoutExpired(row['command'], SAMPLE_DEADLINE_SECONDS)
            result = child.wait(timeout=remaining)
            row['sampler_observed_end_ms'] = time.monotonic()*1000
            if row['sampler_observed_end_ms'] >= deadline*1000:
                raise subprocess.TimeoutExpired(row['command'], SAMPLE_DEADLINE_SECONDS)
            return result
        finally:
            if child.poll() is None:
                child.kill()
            # Cleanup is not extra sampling time; the original command cannot
            # continue after its deadline. Reap the exact owned child.
            child.wait(timeout=3)
            row['sampler_child_reaped'] = child.poll() is not None
            self._checkpoint()

    def _observe(self):
        try:
            # Xcode's cold startup previously exhausted discovery before any
            # app existed. Keep the same prebuild helper asleep until the exact
            # original log reaches its first Home waiter; no app/XCTest change.
            require(self.original_log is not None, 'Exact existing original test log required')
            while not self.stop.is_set():
                marker = original_home_marker(read_live_log(self.original_log))
                if marker is not None:
                    self.activation = dict(**marker, observed_ms=time.monotonic()*1000,
                                           observed_unix=time.time(), kernel_requests_before_activation=self.kernel.requests)
                    require(self.kernel.requests == 0, 'No native discovery before original launch marker')
                    self._checkpoint()
                    break
                self.stop.wait(DISCOVERY_CADENCE_SECONDS)
            while not self.stop.is_set():
                found = self._discover()
                if found and not self.stop.is_set():
                    started = time.monotonic()
                    self._sample(found[0])
                    # Short bounded stack windows leave the existing8s child
                    # budget for attach/report work. No increased deadline.
                    # Preserve maximum180 reports over the original900s window.
                    self.stop.wait(max(0, SAMPLE_CADENCE_SECONDS-(time.monotonic()-started)))
                else:
                    self.stop.wait(DISCOVERY_CADENCE_SECONDS)  # Host only; no app polling/query.
        except BaseException as error:
            self.error = type(error).__name__ + ': ' + str(error)
        finally:
            self._checkpoint()

    def _checkpoint(self):
        receipt = {'schema': 1, 'simulator': self.simulator, 'bundle': BUNDLE,
                   'producer_directory': str(self.directory),
                   'executable_sha256': self.executable_sha, 'started_ms': self.started_ms,
                   'discoveries': self.discoveries, 'samples': self.rows, 'error': self.error,
                   'activation': self.activation,
                   'kernel_child': self.kernel.receipt() if self.kernel else None,
                   'sample_seconds': SAMPLE_SECONDS, 'interval_ms': INTERVAL_MS,
                   'sample_cadence_seconds': SAMPLE_CADENCE_SECONDS,
                   'discovery_cadence_seconds': DISCOVERY_CADENCE_SECONDS,
                   'original_sources_changed': False, 'additional_ax_queries': False,
                   'native_timing_neutrality': False, 'shipping_qualified': False, 'physical_verified': False}
        target = self.directory/'samples.json'
        temporary = self.directory/'samples.writing'
        with temporary.open('w') as output:
            json.dump(receipt, output, indent=2)
        temporary.replace(target)

    def __enter__(self):
        try:
            if self.kernel is None:
                self.kernel = kernel.KernelChannel(self.simulator, environment())
                self.kernel.start()
            else:
                require(self.kernel.simulator == self.simulator and self.kernel.before_build
                        and self.kernel.ready is not None and not self.kernel.reaped
                        and self.kernel.child.poll() is None and self.kernel.requests == 0,
                        'One actual prebuild-ready owned child; no late restart or stale requests')
            self.thread.start()
        except BaseException:
            self.kernel.close(abort=True)
            self._checkpoint()
            raise
        return self

    def __exit__(self, kind, value, traceback):
        self.stop.set()
        self.thread.join(timeout=12)
        try:
            require(not self.thread.is_alive(), 'Owned native sampler must finish and reap before Simulator cleanup')
        finally:
            if self.kernel: self.kernel.close(abort=self.error is not None)
            self._checkpoint()
        # Validate in final verify_receipt, AFTER original XCTest result decoding
        # and cleanup, so an observer error cannot erase actual native outcomes.
        # Final receipt still fails closed on any error or absent stack report.


def execute_observed(capture, arguments, label, timeout, *, kernel_channel=None, **kwargs):
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
    options = {'kernel_channel': kernel_channel} if kernel_channel is not None else {}
    with NativeSampler(log.parent/'native-stacks', destinations[0], products['Parlor'], original_log=log, **options):
        return capture(arguments, label, timeout, **kwargs)


def verify_receipt(directory, simulator, executable_sha, *, producer_directory=None, build_started_at=None, original_log=None):
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
            and (receipt['sample_cadence_seconds'], receipt['discovery_cadence_seconds']) == (5, 1)
            and 0 < receipt['discoveries'] <= MAX_DISCOVERIES
            and 0 < len(receipt['samples']) <= MAX_SAMPLES, 'Exact bounded real sampling parameters')
    activation = receipt['activation']
    raw_log = frozen_sources._read(original_log or directory.parent/'test.log', MAX_TEST_LOG)
    require(type(activation) is dict and type(activation['log_bytes']) is int
            and 0 < activation['log_bytes'] <= len(raw_log)
            and original_home_marker(raw_log[:activation['log_bytes']]) ==
                {k: activation[k] for k in ('kind','log_bytes','log_prefix_sha256','first_method_offset','marker_offset','marker_end')}
            and activation['kernel_requests_before_activation'] == 0
            and type(activation['observed_ms']) in (int,float) and math.isfinite(activation['observed_ms'])
            and type(activation['observed_unix']) in (int,float) and math.isfinite(activation['observed_unix'])
            and activation['observed_unix'] > 0
            and receipt['started_ms'] <= activation['observed_ms'] <= receipt['started_ms']+900000,
            'Actual unchanged first-launch log marker before any kernel discovery')
    actual = []
    kernel.verify(receipt['kernel_child'], simulator, receipt['discoveries'])
    if build_started_at is not None:
        child = receipt['kernel_child']
        build_unix = datetime.datetime.fromisoformat(build_started_at.replace('Z', '+00:00')).timestamp()
        require(child['started_before_original_build'] is True
                and 0 < child['started_unix'] <= child['ready_unix'] <= build_unix,
                'Actual native readiness precedes original source-specific build intent')
    previous = activation['observed_ms']
    for index, row in enumerate(receipt['samples'], 1):
        path = directory/('sample-%03d.txt' % index)
        require(all(type(row[k]) in (int, float) and math.isfinite(row[k]) and row[k] >= 0
                    for k in ('started_ms', 'ended_ms', 'started_unix', 'ended_unix')), 'Finite actual sample clocks')
        require(all(type(row[k]) in (int,float) and math.isfinite(row[k]) for k in ('sampler_started_ms','sampler_observed_end_ms'))
                and row['started_ms'] <= row['sampler_started_ms'] <= row['sampler_observed_end_ms'] <= row['ended_ms']
                and row['sampler_observed_end_ms']-row['sampler_started_ms'] < SAMPLE_DEADLINE_SECONDS*1000,
                'Actual primary sampler observation stayed within its original deadline')
        require(row['index'] == index and row['report'] == path.name and row['sampler_child_reaped'] is True
                and type(row['sampler_pid']) is int and row['sampler_pid'] > 0 and row['sampler_pid'] != row['pid']
                and row['sampler_deadline_seconds'] == SAMPLE_DEADLINE_SECONDS
                and row['executable_sha256'] == executable_sha and type(row['pid']) is int and row['pid'] > 0
                and previous <= row['started_ms'] <= row['ended_ms'] <= receipt['started_ms']+912000
                and row['command'] == sample_command(row['pid'], producer_directory/path.name), 'Exact ordered real OS commands and ownership')
        previous = row['ended_ms']
        observer = row['collector_stack']
        if observer is not None:
            collector_path = directory/'collector-001.txt'
            require(index == 1 and observer['pid'] == row['sampler_pid'] and observer['report'] == collector_path.name
                    and observer['command'] == sample_command(row['sampler_pid'], producer_directory/collector_path.name)
                    and observer['status'] == 'SAMPLED' and observer['exit'] == 0 and observer['child_reaped'] is True
                    and observer['deadline_seconds'] == TOOL_STACK_DEADLINE_SECONDS
                    and all(type(observer[k]) in (int,float) and math.isfinite(observer[k]) for k in ('started_ms','ended_ms','started_unix','ended_unix'))
                    and 0 < observer['started_unix'] <= observer['ended_unix']
                    and observer['ended_ms']-observer['started_ms'] < TOOL_STACK_DEADLINE_SECONDS*1000
                    and row['sampler_started_ms']+TOOL_STACK_AFTER_SECONDS*1000 <= observer['started_ms']
                    <= observer['ended_ms'] <= row['sampler_observed_end_ms']
                    and read_sample(collector_path,row['sampler_pid']) == {k:observer[k] for k in ('sha256','bytes')},
                    'Actual owned sampler-child stack, never an unrelated process or incomplete tool report')
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
    require({p.name for p in directory.glob('collector-*.txt')} ==
            {row['collector_stack']['report'] for row in receipt['samples'] if row['collector_stack'] is not None},
            'All actual owned collector stacks retained')
    return {'sampled_reports': len(actual), 'sampled_processes': sorted(set(actual)),
            'receipt_sha256': hashlib.sha256((directory/'samples.json').read_bytes()).hexdigest(),
            'application_root_cause': False, 'shipping_qualified': False, 'physical_verified': False}
