#!/usr/bin/env python3
"""Opt-in, source-bound Linux host/peer processes; not a mobile or release gate."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import time
import uuid

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location('real_kit_runner', HERE / 'run.py')
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)
ROOT = probe.ROOT
LOG_LIMIT = probe.LOG_LIMIT
PAIR_TIMEOUT = 110
TOTAL_TIMEOUT = 900
HELPERS = ('processes.py', 'run.py', 'MultiProcessEndpoint.kt', 'process-helper.init.gradle')


def helper_hashes(deadline):
    result = {}
    for name in HELPERS:
        path = HERE / name
        probe.require(path.is_file() and not path.is_symlink() and 0 < path.stat().st_size <= LOG_LIMIT,
                      'Redirected or unbounded process helper')
        result[name] = probe.sha256(path, deadline)
    return result


def prepare(out, deadline):
    registry = out / 'owned-gradle-daemons'
    registry.mkdir(mode=0o700)
    option = '-Dorg.gradle.daemon.registry.base=' + str(registry)
    wrapper = str(ROOT / 'gradlew')
    cp = out / 'classpath.txt'
    command = [wrapper, ':shared:transport-p2p:realKitProcessClasspath', '--init-script', str(HERE / 'process-helper.init.gradle'),
               '-Dparlor.processHelper=' + str(HERE / 'MultiProcessEndpoint.kt'), '-Dparlor.processClasspath=' + str(cp),
               '--dependency-verification=strict', '--no-daemon', '--no-configuration-cache', '--max-workers=2',
               '-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g', '-Pkotlin.compiler.execution.strategy=in-process',
               '--console=plain', option]
    try:
        probe.require(probe.owned.run_logged(command, out / 'compile.log', timeout=probe.remaining(deadline, 600),
                                            output_limit=32 * LOG_LIMIT) == 0, 'Strict process-helper compilation failed')
    finally:
        # A detached single-use daemon may outlive the work deadline; retire only this registry.
        probe.require(probe.owned.run_logged([wrapper, '--stop', option, '--console=plain'], registry / 'stop.log',
                                            timeout=60, output_limit=LOG_LIMIT) == 0, 'Owned Gradle cleanup failed')
    probe.require(cp.is_file() and not cp.is_symlink() and 0 < cp.stat().st_size < LOG_LIMIT,
                  'Process runtime classpath not produced')
    return cp.read_text()


def endpoint_command(role, namespace, classpath):
    return ['java', '-Xmx512m', '-cp', classpath, 'com.parlor.transport.p2p.MultiProcessEndpoint', role, namespace]


def bounded_log(path):
    with path.open('rb') as stream:
        data = stream.read(LOG_LIMIT + 1)
    probe.require(len(data) <= LOG_LIMIT, 'Endpoint output bound exceeded')
    return data


def advertised_code(data):
    matches = re.findall(rb'(?m)^ROOM_CODE ([A-Z0-9]{6})\r?$', data)
    probe.require(len(matches) <= 1, 'Ambiguous host advertisement')
    return matches[0] if matches else None


def run_pair(classpath, directory, deadline):
    probe.remaining(deadline, PAIR_TIMEOUT)
    directory.mkdir(mode=0o700)
    namespace = uuid.uuid4().hex
    roles = {role: dict(role=role, natural_exit=False) for role in ('host', 'peer')}
    processes, streams = {}, {}
    started = time.monotonic()
    started_at = datetime.now(timezone.utc).isoformat()
    deadline = min(deadline, started + PAIR_TIMEOUT)
    successful = False
    failure_type = None
    try:
        def spawn(role):
            stream = (directory / (role + '.log')).open('xb', buffering=0)
            streams[role] = stream
            process = subprocess.Popen(endpoint_command(role, namespace, classpath), cwd=ROOT,
                                       stdin=subprocess.PIPE, stdout=stream, stderr=subprocess.STDOUT,
                                       start_new_session=True)
            processes[role] = process
            roles[role]['pid'] = process.pid
            return process

        host = spawn('host')
        code = None
        while time.monotonic() < min(deadline, started + 40):
            probe.require(host.poll() is None, 'Host ended before advertisement')
            code = advertised_code(bounded_log(directory / 'host.log'))
            if code is not None:
                break
            probe.sample_process(host.pid, roles['host'])
            time.sleep(.1)
        probe.require(code is not None, 'No advertised room code within bound')
        probe.remaining(deadline, PAIR_TIMEOUT)
        peer = spawn('peer')
        probe.require(host.pid != peer.pid, 'Host and peer must have distinct processes')
        peer.stdin.write(code + b'\n')
        peer.stdin.flush()
        peer.stdin.close()
        host.stdin.close()
        while any(process.poll() is None for process in processes.values()):
            probe.require(time.monotonic() < deadline, 'Pair deadline exceeded')
            for role, process in processes.items():
                probe.require(os.fstat(streams[role].fileno()).st_size <= LOG_LIMIT, 'Endpoint output bound exceeded')
                if process.poll() is None:
                    probe.sample_process(process.pid, roles[role])
            time.sleep(.1)
        for role, process in processes.items():
            roles[role].update(natural_exit=True, exit_code=process.returncode)
            probe.require(process.returncode == 0, 'Endpoint failed; inspect retained role log')
            data = bounded_log(directory / (role + '.log'))
            probe.require(b'ENDPOINT_PASS\n' in data and b'TEARDOWN_PASS\n' in data and b'ENDPOINT_FAIL' not in data,
                          'Endpoint or teardown failed')
            expected = (b'AUTHENTICATED_ACTOR_PASS\n', b'PEER_DISCONNECT_PASS\n') if role == 'host' else (b'BIDIRECTIONAL_PASS\n',)
            probe.require(all(marker in data for marker in expected), 'Required endpoint assertions missing')
        successful = True
    except BaseException as failure:
        failure_type = type(failure).__name__
        raise
    finally:
        for role, stream in streams.items():
            process = processes.get(role)
            try:
                if process is not None:
                    if process.stdin and not process.stdin.closed:
                        process.stdin.close()
                    probe.owned._stop_owned_command(process)
                    roles[role].update(cleanup_completed=process.poll() is not None, final_exit_code=process.returncode)
            except BaseException as cleanup_failure:
                roles[role].update(cleanup_completed=False, cleanup_failure_type=type(cleanup_failure).__name__)
            finally:
                try:
                    stream.close()
                except BaseException as close_failure:
                    roles[role]['stream_close_failure_type'] = type(close_failure).__name__
        # Retire BOTH owned groups before fallible evidence IO; a hash/read error must not leak the other endpoint.
        for role in streams:
            try:
                log = directory / (role + '.log')
                roles[role]['log_sha256'] = probe.sha256(log, time.monotonic() + 30)
                roles[role]['output_within_bound'] = log.stat().st_size <= LOG_LIMIT
            except BaseException as evidence_failure:
                roles[role]['evidence_failure_type'] = type(evidence_failure).__name__
        cleaned = all(row.get('cleanup_completed') and row.get('output_within_bound') and row.get('log_sha256')
                      and not row.get('stream_close_failure_type') for row in roles.values())
        receipt = dict(status='passed' if successful and cleaned else 'failed', failure_type=failure_type,
                       started_at=started_at, finished_at=datetime.now(timezone.utc).isoformat(),
                       elapsed_seconds=round(time.monotonic() - started, 3), roles=roles,
                       scope='Distinct same-machine Linux JVMs: discovery, approval, actor binding, direct response and graceful peer disconnect; NOT game/physical/mobile/process-death recovery.')
        (directory / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
        if failure_type is None:
            probe.require(cleaned, 'Owned endpoint cleanup not verified')
    return receipt


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--allow-local-lan', action='store_true')
    parser.add_argument('--iterations', type=int, choices=(1, 2, 3), default=3)
    args = parser.parse_args(argv)
    deadline = time.monotonic() + TOTAL_TIMEOUT
    environment = probe.check_environment(args.allow_local_lan, args.iterations, deadline)
    before = probe.source(deadline=deadline)
    helpers = helper_hashes(deadline)
    out = ROOT / 'build/real-kit-processes' / (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:8])
    out.mkdir(mode=0o700, parents=True, exist_ok=False)
    receipt = dict(status='running', source=before, helper_sources=helpers, environment=environment, cases=[],
                   started_at=datetime.now(timezone.utc).isoformat(),
                   scope='Opt-in synthetic Linux separate-process experiment; NOT normal CI, physical/mobile performance, background recovery or release qualification.')
    try:
        classpath = prepare(out, deadline)
        probe.require_same_source(before, deadline)
        probe.require(helper_hashes(deadline) == helpers, 'Prepared helper source changed')
        artifacts = probe.classpath_manifest(classpath, deadline=deadline)
        receipt['runtime_artifacts'] = artifacts
        for i in range(args.iterations):
            probe.remaining(deadline, PAIR_TIMEOUT)
            case = run_pair(classpath, out / ('pair-' + str(i + 1)), deadline)
            probe.require(case['status'] == 'passed', 'Pair did not qualify')
            receipt['cases'].append(case)
        probe.require_same_source(before, deadline)
        probe.require(probe.classpath_manifest(classpath, deadline=deadline) == artifacts, 'Executed runtime bytes changed')
        probe.require(helper_hashes(deadline) == helpers, 'Executed helper source changed')
        receipt['status'] = 'passed'
    except BaseException as failure:
        receipt.update(status='failed', failure_type=type(failure).__name__)
        raise
    finally:
        receipt['finished_at'] = datetime.now(timezone.utc).isoformat()
        (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
        print(json.dumps(dict(status=receipt['status'], completed_pairs=len(receipt['cases']), receipt=str(out / 'receipt.json')), indent=2), flush=True)


if __name__ == '__main__':
    main()
