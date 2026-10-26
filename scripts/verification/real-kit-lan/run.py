#!/usr/bin/env python3
"""Explicit, source-bound Linux same-machine real-adapter probe; never a release gate."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import signal
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.release import github_distribution as owned  # noqa: E402

HERE = Path(__file__).resolve().parent
CASES = (
    "host_advertises_a_room_code_that_join_resolves_to",
    "peer_can_join_a_hosted_room_and_membership_appears_on_host",
    "peer_to_host_message_round_trips_and_host_to_peer_message_arrives_back",
    "host_broadcast_reaches_every_peer",
)
CASE_TIMEOUT = 120
TOTAL_TIMEOUT = 900
LOG_LIMIT = 1024 * 1024


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def source():
    """Reject dirty/untracked source, then bind both commit and complete tracked tree."""
    require(not owned.run(["git", "status", "--porcelain", "--untracked-files=normal"]).strip(),
            "A clean committed worktree is required; commit or use an isolated worktree")
    return {"commit": owned.run(["git", "rev-parse", "HEAD"]).strip(),
            "tree": owned.run(["git", "rev-parse", "HEAD^{tree}"]).strip()}


def require_same_source(before):
    require(source() == before, "Source changed during verification; result is not qualified")


def remaining(deadline, requested):
    seconds = deadline - time.monotonic()
    require(seconds > 0, "Total probe time bound exceeded")
    return min(seconds, requested)


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def classpath_manifest(value):
    """Hash actual executed artifacts/classes, not just their classpath names."""
    entries = value.split(os.pathsep)
    require(0 < len(entries) <= 200, "Unexpected classpath size")
    result = []
    total_bytes = 0
    for entry in entries:
        path = Path(entry)
        require(path.is_absolute(), "Classpath entries must be absolute")
        # Gradle includes optional empty class/resource output directories.
        files = sorted(p for p in path.rglob('*') if p.is_file()) if path.is_dir() else [path] if path.is_file() else []
        for file in files:
            total_bytes += file.stat().st_size
            require(len(result) < 10000 and total_bytes <= 1024 ** 3, "Classpath custody exceeds bound")
            result.append({"entry": entry, "file": str(file.relative_to(path)) if path.is_dir() else path.name,
                           "sha256": sha256(file)})
    require(result, "Empty runtime classpath")
    return result


def sample_process(pid, row):
    """Sample only this child's process and held socket inodes; omit all addresses."""
    base = Path(f"/proc/{pid}")
    try:
        stat = (base / "stat").read_text().rsplit(')', 1)[1].split()
        row['cpu_seconds_sampled'] = max(row.get('cpu_seconds_sampled', 0),
                                       (int(stat[11]) + int(stat[12])) / os.sysconf('SC_CLK_TCK'))
        status = dict(line.split(':', 1) for line in (base / 'status').read_text().splitlines())
        row['rss_bytes_sampled'] = max(row.get('rss_bytes_sampled', 0), int(status.get('VmRSS', '0').split()[0]) * 1024)
        row['native_threads_sampled'] = max(row.get('native_threads_sampled', 0), int(status.get('Threads', '0')))
        inodes = set()
        for descriptor in (base / 'fd').iterdir():
            try:
                target = str(descriptor.readlink())
            except FileNotFoundError:
                continue
            if target.startswith('socket:['):
                inodes.add(target[8:-1])
        maxima = row.setdefault('owned_socket_maxima', {})
        for protocol in ('tcp', 'tcp6', 'udp', 'udp6'):
            counts = {}
            for line in (base / 'net' / protocol).read_text().splitlines()[1:]:
                fields = line.split()
                if fields[9] in inodes:
                    key = f'{protocol}_state_{fields[3]}'
                    counts[key] = counts.get(key, 0) + 1
            for key, value in counts.items():
                maxima[key] = max(maxima.get(key, 0), value)
    except (OSError, ValueError, IndexError):
        # Sampling races natural process exit; missing samples are not zero-cost evidence.
        row['incomplete_samples'] = row.get('incomplete_samples', 0) + 1


def run_case(command, log, *, timeout, output_limit=LOG_LIMIT, sampler=sample_process, row=None):
    """Shared proven process-group finalizer; no global process matching/stopping."""
    require(timeout > 0 and output_limit > 0, "Positive process bounds required")
    if row is None:
        row = {}
    row.update(root_natural_exit=False, cleanup_completed=False)
    process = None
    started = time.monotonic()
    with log.open('xb') as stream:
        try:
            process = subprocess.Popen(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT,
                                       start_new_session=True)
            while process.poll() is None:
                require(time.monotonic() - started < timeout, "Real-kit process exceeded time bound")
                require(os.fstat(stream.fileno()).st_size <= output_limit, "Real-kit output exceeds bound")
                sampler(process.pid, row)
                time.sleep(.1)
            row['exit_code'] = process.returncode
            row['root_natural_exit'] = True
        except BaseException as error:
            row['failure_type'] = type(error).__name__
            raise
        finally:
            row['elapsed_seconds'] = round(time.monotonic() - started, 3)
            if process is not None:
                owned._stop_owned_command(process)
                row['cleanup_completed'] = process.poll() is not None
                row['final_exit_code'] = process.returncode
        # A descendant may race the root's exit; check only after retiring the group.
        require(os.fstat(stream.fileno()).st_size <= output_limit, "Real-kit output exceeds bound")
    row['elapsed_seconds'] = round(time.monotonic() - started, 3)
    return row


def prepare(out, deadline):
    registry = out / 'owned-gradle-daemons'
    registry.mkdir()
    registry_option = '-Dorg.gradle.daemon.registry.base=' + str(registry)
    wrapper = str(ROOT / 'gradlew')
    cp = out / 'classpath.txt'
    command = [wrapper, ':shared:transport-p2p:realKitLanClasspath', '--init-script', str(HERE / 'classpath.init.gradle'),
               '-Dparlor.realKitClasspath=' + str(cp), '--dependency-verification=strict', '--no-daemon',
               '--no-configuration-cache', '--console=plain', '--max-workers=2',
               '-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g',
               '-Pkotlin.compiler.execution.strategy=in-process', registry_option]
    try:
        require(owned.run_logged(command, out / 'compile.log', timeout=remaining(deadline, 600),
                                 output_limit=32 * LOG_LIMIT) == 0, "Strict compilation failed; inspect local compile.log")
    finally:
        # Gradle's single-use daemon can detach: stop ONLY this fresh registry.
        require(owned.run_logged([wrapper, '--stop', registry_option, '--console=plain'], registry / 'stop.log',
                                 timeout=90, output_limit=LOG_LIMIT) == 0, "Owned Gradle registry cleanup failed")
    require(cp.is_file() and 0 < cp.stat().st_size < LOG_LIMIT, "Runtime classpath not produced")
    classpath = cp.read_text()
    require(owned.run_logged([shutil.which('javac'), '-d', str(out), str(HERE / 'RealAdapterProbe.java')],
                             out / 'javac.log', timeout=remaining(deadline, 30), output_limit=LOG_LIMIT) == 0,
            "Java helper compilation failed")
    return classpath


def check_environment(allowed, iterations):
    require(allowed, "Explicit --allow-local-lan consent is required; this opens real local sockets")
    require(platform.system() == 'Linux', "This opt-in probe supports Linux only; nothing was verified")
    require(1 <= iterations <= 3, "Iterations must be between 1 and 3")
    require(Path('/proc/self/stat').is_file(), "Linux /proc process custody is unavailable")
    require(shutil.which('java') and shutil.which('javac'), "JDK 21 java and javac are required")
    version = owned.run([shutil.which('java'), '-version'])
    require('version "21.' in version, "The probe requires JDK 21")
    compiler = owned.run([shutil.which('javac'), '-version'])
    require(compiler.startswith('javac 21.'), "The helper compiler requires JDK 21")
    return {'java': version.strip(), 'javac': compiler.strip(), 'platform': platform.platform()}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--allow-local-lan', action='store_true')
    parser.add_argument('--iterations', type=int, default=3, help='1–3 iterations of each peer case (default 3)')
    args = parser.parse_args(argv)
    environment = check_environment(args.allow_local_lan, args.iterations)
    before = source()
    out = ROOT / 'build' / 'real-kit-lan' / (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:8])
    out.mkdir(parents=True, exist_ok=False)
    receipt = {'schema': 1, 'source': before, 'scope': 'Linux same-machine real sockets, synthetic identities; NOT physical LAN/mobile/gameplay',
               'status': 'running', 'environment': environment, 'cases': [], 'started_utc': datetime.now(timezone.utc).isoformat()}
    def save():
        (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    save()
    deadline = time.monotonic() + TOTAL_TIMEOUT
    try:
        classpath = prepare(out, deadline)
        receipt['runtime_artifacts'] = classpath_manifest(classpath)
        receipt['helper_sha256'] = sha256(out / 'RealAdapterProbe.class')
        require_same_source(before)
        selected = (CASES[0],) + CASES[1:] * args.iterations
        for index, method in enumerate(selected, 1):
            log = out / f'case-{index}.log'
            row = {'method': method, 'log': log.name, 'passed': False}
            receipt['cases'].append(row)
            save()
            run_case([shutil.which('java'), '-Xmx512m', '-cp', str(out) + os.pathsep + classpath,
                            'RealAdapterProbe', method], log, timeout=remaining(deadline, CASE_TIMEOUT), row=row)
            text = log.read_text()
            row.update(method=method, log=log.name,
                       fixture_pass='\nFIXTURE_PASS\n' in text, teardown_pass='\nTEARDOWN_PASS\n' in text)
            row['passed'] = row['exit_code'] == 0 and row['fixture_pass'] and row['teardown_pass'] and row['cleanup_completed']
            save()
            require(row['passed'], 'Real adapter fixture failed; no success claimed; inspect retained local log')
        require_same_source(before)
        require(classpath_manifest(classpath) == receipt['runtime_artifacts'],
                'Executed runtime artifacts changed during verification')
        receipt['status'] = 'passed'
    except BaseException as error:
        receipt['status'] = 'failed'
        receipt['failure_type'] = type(error).__name__
        raise
    finally:
        receipt['finished_utc'] = datetime.now(timezone.utc).isoformat()
        save()
        print('Local real-kit receipt:', out / 'receipt.json', flush=True)
    print(f'PASS: {len(receipt["cases"])} same-machine cases; physical LAN/mobile remain unverified.')
    return 0


if __name__ == '__main__':
    # TERM follows the same finally-based owned cleanup as Ctrl-C.
    signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(KeyboardInterrupt()))
    try:
        raise SystemExit(main())
    except (RuntimeError, OSError) as error:
        print(f'FAIL: {error}', file=sys.stderr)
        raise SystemExit(1)
