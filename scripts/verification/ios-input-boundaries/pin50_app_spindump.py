"""Target-only app stackshots, with private capture and post-XCTest analysis.

Reuses admitted41423553336 native tool and PIN49 exact-owned process discovery.
No new app hooks, AX queries, actions or assertion deadlines. Observation may
perturb scheduling; it is diagnostic evidence, never shipping qualification.
"""
import datetime
import hashlib
import json
import math
from pathlib import Path
import re
import time

import frozen_sources
import pin49_native_stacks as base
import spindump_attach_control as native

MAX_PRIVATE_TOTAL = 256 * 1024 * 1024
PROFILE = 'owned-app-spindump-offline-v1'


def read_log(path):
    base.require(path.is_file() and not path.is_symlink(), 'Actual owned public log required')
    raw = base.read_live_log(path)
    base.require(len(raw) <= 256*1024, 'Bounded public stage log')
    return raw


def read_report(path, pid, image):
    raw = native.owned_file(path, native.MAX_TEXT)
    text = raw.decode('utf-8')
    base.require(re.findall(r'^Process:\s+(.+?)\s+\[(\d+)\]\s*$', text, re.M) == [('Parlor', str(pid))],
                 'Actual report must identify only the exact owned Parlor PID')
    base.require(re.findall(r'^Path:\s+(.+?)\s*$', text, re.M) == [image, image],
                 'Actual command and process images must match the exact owned installation')
    threads = re.findall(r'^\s*Thread\s+0x[0-9a-fA-F]+', text, re.M)
    frames = re.findall(r'\[0x[0-9a-fA-F]+\]', text)
    base.require(threads and frames, 'Actual native threads and frames, not an empty report')
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest(),
                thread_rows=len(threads), frame_entries=len(frames))


class AppSpindump(base.NativeSampler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.private = self.directory.parent.parent/'private-native-captures'
        base.require(not self.private.exists(), 'Exclusive private capture staging outside public uploads')
        self.private.mkdir(mode=0o700)
        self.private_bytes = 0
        self.original_returned_unix = None
        self.offline_finished_unix = None

    def _sample(self, image):
        base.require(len(self.rows) < base.MAX_SAMPLES, 'Original finite180 capture budget')
        index = len(self.rows)+1
        stage = self.private/('capture-%03d' % index)
        stage.mkdir(mode=0o700)
        raw = stage/native.PRIVATE_CAPTURE
        row = dict(index=index, **image, started_ms=time.monotonic()*1000, started_unix=time.time(),
                   commands=[], status='STARTED', report='sample-%03d.txt' % index,
                   private_directory=str(stage))
        self.rows.append(row)
        self._checkpoint()
        try:
            native.command(stage, row, 'record', native.capture_command(image['pid'], raw))
            data = native.owned_file(raw, native.MAX_RAW)
            self.private_bytes += len(data)
            base.require(self.private_bytes <= MAX_PRIVATE_TOTAL, 'Finite aggregate private native storage')
            row['raw_metadata'] = dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest(), uploaded=False)
            row['status'] = 'CAPTURED_PENDING_OFFLINE_ANALYSIS'
        finally:
            row.update(ended_ms=time.monotonic()*1000, ended_unix=time.time())
            log = stage/'record.log'
            if log.exists():
                with (self.directory/('sample-%03d-record.log' % index)).open('xb') as output:
                    output.write(native.owned_file(log, 256*1024) if log.stat().st_size else b'')
            self._checkpoint()

    def _checkpoint(self):
        # Base receipt preserves the existing clocks, original-launch activation,
        # finite discovery budget, kernel responses and non-qualification flags.
        super()._checkpoint()
        path = self.directory/'samples.json'
        value = json.loads(path.read_text())
        value.update(schema=50, profile=PROFILE, private_directory=str(self.private),
                     private_bytes=self.private_bytes, max_private_bytes=MAX_PRIVATE_TOTAL,
                     original_command_returned_unix=self.original_returned_unix,
                     offline_finished_unix=self.offline_finished_unix)
        target = self.directory/'samples.writing'
        target.write_text(json.dumps(value, indent=2))
        target.replace(path)

    def _analyze(self):
        for row in self.rows:
            if row['status'] != 'CAPTURED_PENDING_OFFLINE_ANALYSIS':
                continue  # Preserve actual failed capture, never invent successful evidence.
            stage = Path(row['private_directory'])
            raw = stage/native.PRIVATE_CAPTURE
            report = self.directory/row['report']
            try:
                data = native.owned_file(raw, native.MAX_RAW)
                base.require(row['raw_metadata'] == dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest(), uploaded=False),
                             'Exact private captured bytes before offline analysis')
                native.command(stage, row, 'analysis', native.analysis_command(raw, report))
                row['report_metadata'] = read_report(report, row['pid'], row['image'])
                row['status'] = 'SAMPLED'
                raw.unlink()  # Retire opaque bytes only after exact plain-report admission.
                row['private_capture_retired'] = True
            except BaseException as error:
                row['analysis_error'] = type(error).__name__+': '+str(error)
                if self.error is None:
                    self.error = row['analysis_error']
            finally:
                log = stage/'analysis.log'
                if log.exists():
                    with (self.directory/('sample-%03d-analysis.log' % row['index'])).open('xb') as output:
                        output.write(native.owned_file(log, 256*1024) if log.stat().st_size else b'')
                self._checkpoint()
        self.offline_finished_unix = time.time()
        self._checkpoint()

    def __exit__(self, kind, value, traceback):
        self.original_returned_unix = time.time()
        super().__exit__(kind, value, traceback)  # Stop observer/reap helper first.
        self._analyze()  # Original XCTest has returned; no analysis during its actions.
        # Errors are admitted only AFTER original decoding and owned cleanup.


def execute_observed(capture, arguments, label, timeout, *, kernel_channel=None, **kwargs):
    if label != 'compose-tests':
        return capture(arguments, label, timeout, **kwargs)
    base.require(timeout == 900, 'Original900s test process deadline remains unchanged')
    destinations = [str(a).removeprefix('platform=iOS Simulator,id=') for a in arguments
                    if str(a).startswith('platform=iOS Simulator,id=')]
    base.require(len(destinations) == 1 and base.UUID.fullmatch(destinations[0]), 'One exact original device')
    log = Path(kwargs['environment']['PARLOR_INPUT_LOG'])
    base.require(log.name == 'test.log' and log.parent.name == 'compose', 'Original public pair only')
    product = json.loads(frozen_sources._read(log.parent/'product.json', 2*1024*1024))
    base.require(kernel_channel is not None, 'Reuse the one existing prebuild-ready helper')
    with AppSpindump(log.parent/'native-stacks', destinations[0], product['Parlor'],
                     kernel_channel=kernel_channel, original_log=log):
        return capture(arguments, label, timeout, **kwargs)


def verify_receipt(directory, simulator, executable_sha, *, producer_directory=None, build_started_at=None, original_log=None):
    """Dedicated real spindump admission; never fake/normalize a sample receipt."""
    r = json.loads(frozen_sources._read(directory/'samples.json', 2*1024*1024))
    producer = producer_directory or directory
    base.require(r['producer_directory'] == str(producer) and r['schema'] == 50 and r['profile'] == PROFILE
                 and r['simulator'] == simulator and r['executable_sha256'] == executable_sha
                 and r['bundle'] == base.BUNDLE and r['error'] is None, 'Exact completed source/device/profile')
    for flag in ('original_sources_changed', 'additional_ax_queries', 'native_timing_neutrality', 'shipping_qualified', 'physical_verified'):
        base.require(r[flag] is False, 'No source/query/neutrality/shipping/physical attestation')
    base.require((r['sample_seconds'], r['interval_ms'], r['sample_cadence_seconds'], r['discovery_cadence_seconds']) == (1,10,5,1)
                 and 0 < r['discoveries'] <= base.MAX_DISCOVERIES and 0 < len(r['samples']) <= base.MAX_SAMPLES,
                 'Original finite real sampling and discovery bounds')
    private = producer.parent.parent/'private-native-captures'
    base.require(r['private_directory'] == str(private) and r['max_private_bytes'] == MAX_PRIVATE_TOTAL
                 and type(r['private_bytes']) is int and 0 < r['private_bytes'] <= MAX_PRIVATE_TOTAL,
                 'Owned nonuploaded staging and finite aggregate bytes')
    activation = r['activation']
    log = frozen_sources._read(original_log or directory.parent/'test.log', base.MAX_TEST_LOG)
    base.require(type(activation) is dict and type(activation['log_bytes']) is int and 0 < activation['log_bytes'] <= len(log)
                 and base.original_home_marker(log[:activation['log_bytes']]) ==
                     {k:activation[k] for k in ('kind','log_bytes','log_prefix_sha256','first_method_offset','marker_offset','marker_end')}
                 and activation['kernel_requests_before_activation'] == 0, 'Exact existing first-launch log activation')
    for k in ('observed_ms', 'observed_unix'):
        base.require(type(activation[k]) in (int,float) and math.isfinite(activation[k]) and activation[k] > 0,
                     'Finite actual original-launch activation clocks')
    base.require(r['started_ms'] <= activation['observed_ms'] <= r['started_ms']+900000,
                 'Actual activation within original900s observer window')
    base.kernel.verify(r['kernel_child'], simulator, r['discoveries'])
    if build_started_at is not None:
        child = r['kernel_child']
        build_unix = datetime.datetime.fromisoformat(build_started_at.replace('Z','+00:00')).timestamp()
        base.require(child['started_before_original_build'] is True and 0 < child['started_unix'] <= child['ready_unix'] <= build_unix,
                     'Actual ready helper before original build')
    for k in ('original_command_returned_unix','offline_finished_unix'):
        base.require(type(r[k]) in (int,float) and math.isfinite(r[k]) and r[k] > 0, 'Finite actual offline ordering clocks')
    base.require(r['original_command_returned_unix'] <= r['offline_finished_unix'], 'Analysis after original return')
    previous = activation['observed_ms']
    raw_total = 0
    for index, row in enumerate(r['samples'],1):
        path = directory/('sample-%03d.txt' % index)
        stage = private/('capture-%03d' % index)
        raw = stage/native.PRIVATE_CAPTURE
        base.require(row['index'] == index and row['report'] == path.name and row['private_directory'] == str(stage)
                     and row['status'] == 'SAMPLED' and row['private_capture_retired'] is True
                     and type(row['pid']) is int and row['pid'] > 0 and row['executable_sha256'] == executable_sha,
                     'Every actual exact-owned capture must complete analysis')
        installed = Path(row['image'])
        base.require(installed.is_absolute() and '..' not in installed.parts and installed.name == 'Parlor'
                     and installed.parent.name == 'Parlor.app' and base.UUID.fullmatch(installed.parent.parent.name)
                     and str(installed.parent.parent.parent).endswith('/Library/Developer/CoreSimulator/Devices/'+simulator+
                                                                     '/data/Containers/Bundle/Application'),
                     'Exact owned Simulator Debug installation path, not an arbitrary report target')
        for k in ('started_ms','ended_ms','started_unix','ended_unix'):
            base.require(type(row[k]) in (int,float) and math.isfinite(row[k]) and row[k] > 0, 'Finite actual capture clocks')
        base.require(previous <= row['started_ms'] <= row['ended_ms'] <= r['started_ms']+912000,
                     'Original serial sample window and12s cleanup join unchanged')
        previous = row['ended_ms']
        cmds = row['commands']
        base.require(len(cmds) == 2 and [c['label'] for c in cmds] == ['record','analysis']
                     and [c['command'] for c in cmds] == [native.capture_command(row['pid'],raw),native.analysis_command(raw,producer/path.name)],
                     'Exact documented target-only live and unprivileged offline commands')
        for c in cmds:
            base.require(c['status'] == 'COMPLETE' and c['exit'] == 0 and c['owned_child_reaped'] is True
                         and type(c['owned_command_pid']) is int and c['owned_command_pid'] > 0
                         and c['owned_command_pid'] != row['pid'] and type(c['seconds']) in (int,float)
                         and math.isfinite(c['seconds']) and 0 <= c['seconds'] < 8 and c['started_unix'] <= c['ended_unix'],
                         'Each actual native command keeps8s, succeeds and reaps')
        base.require(row['started_unix'] <= cmds[0]['started_unix'] <= cmds[0]['ended_unix'] <= row['ended_unix']
                     and r['original_command_returned_unix'] <= cmds[1]['started_unix'] <= cmds[1]['ended_unix'] <= r['offline_finished_unix'],
                     'No offline analysis overlaps original test execution')
        meta = row['raw_metadata']
        base.require(type(meta['bytes']) is int and 0 < meta['bytes'] <= native.MAX_RAW
                     and re.fullmatch('[a-f0-9]{64}',meta['sha256']) and meta['uploaded'] is False
                     and row['private_output']['record'] == dict(**{k:meta[k] for k in ('bytes','sha256','uploaded')},bounded=True),
                     'Bounded nonuploaded opaque capture metadata')
        raw_total += meta['bytes']
        base.require(row['report_metadata'] == read_report(path,row['pid'],row['image']), 'Actual report bytes/process/image/thread readback')
        for label in ('record','analysis'):
            p = directory/('sample-%03d-%s.log' % (index,label))
            data = read_log(p)
            base.require(native.public_log(data) == data and b'Spindump binary format' not in data,
                         'Only admitted public native stage logs, never opaque output')
    base.require(raw_total == r['private_bytes'] and {p.name for p in directory.glob('sample-*.txt')} ==
                 {row['report'] for row in r['samples']} and not list(directory.rglob('*.opaque-output')),
                 'All actual plain stacks and no opaque public payload')
    return dict(profile=PROFILE, sampled_reports=len(r['samples']), sampled_processes=sorted({s['pid'] for s in r['samples']}),
                receipt_sha256=hashlib.sha256((directory/'samples.json').read_bytes()).hexdigest(),
                application_root_cause=False, shipping_qualified=False, physical_verified=False)
