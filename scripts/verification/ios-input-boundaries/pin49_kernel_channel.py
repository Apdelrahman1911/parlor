"""One owned host libproc child, prewarmed before XCTest; every reply bounded3s."""
import json
import math
import os
from pathlib import Path
import select
import subprocess
import sys
import time

TIMEOUT = 3
MAX_REPLY = 65536


def command(simulator):
    return [sys.executable, '-B', str(Path(__file__).with_name('pin49_process_paths.py')), simulator, '--serve']


def duplicate(pairs):
    value = {}
    for key, item in pairs:
        if key in value: raise ValueError('Duplicate kernel channel key')
        value[key] = item
    return value


class KernelChannel:
    def __init__(self, simulator, environment):
        self.simulator, self.environment = simulator, environment
        self.child, self.ready = None, None
        self.requests, self.response_seconds = 0, []
        self.startup_seconds, self.exit, self.reaped = None, None, False

    def _read(self, deadline):
        data = bytearray()
        while len(data) <= MAX_REPLY:
            remaining = deadline-time.monotonic()
            if remaining <= 0 or not select.select([self.child.stdout], [], [], remaining)[0]:
                raise subprocess.TimeoutExpired('owned-kernel-reply', TIMEOUT)
            part = os.read(self.child.stdout.fileno(), min(4096, MAX_REPLY+1-len(data)))
            if not part: raise ValueError('Kernel child ended before its complete reply')
            data.extend(part)
            if b'\n' in data:
                if not data.endswith(b'\n') or data.count(b'\n') != 1 or len(data) > MAX_REPLY:
                    raise ValueError('One bounded kernel reply, no unsolicited messages')
                value = json.loads(data, object_pairs_hook=duplicate)
                if value.get('schema') != 1 or value.get('selector') != 'PROC_UID_ONLY' or \
                        value.get('simulator') != self.simulator or value.get('own_process_seen') is not True or \
                        value.get('helper_pid') != self.child.pid:
                    raise ValueError('Exact owned child/current-UID native path capability')
                return value
        raise ValueError('Bounded kernel reply exceeded')

    def start(self):
        if self.child is not None: raise ValueError('Kernel child may start only once')
        started = time.monotonic()
        self.child = subprocess.Popen(command(self.simulator), stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                      stderr=subprocess.DEVNULL, env=self.environment)
        os.set_blocking(self.child.stdin.fileno(), False)
        os.set_blocking(self.child.stdout.fileno(), False)
        try:
            self.ready = self._read(started+TIMEOUT)
            if self.ready.get('kind') != 'ready' or 'index' in self.ready:
                raise ValueError('Exact initial native readiness before XCTest')
            self.startup_seconds = time.monotonic()-started
        except BaseException:
            self.close(abort=True)
            raise

    def discover(self):
        if self.ready is None or self.reaped or self.requests >= 3600:
            raise ValueError('One ready owned child and finite serial request budget')
        self.requests += 1
        started = time.monotonic()
        try:
            if not select.select([], [self.child.stdin], [], TIMEOUT)[1] or \
                    os.write(self.child.stdin.fileno(), b'DISCOVER\n') != 9:
                raise ValueError('Complete bounded kernel request')
            value = self._read(started+TIMEOUT)
            if value.get('kind') != 'paths' or value.get('index') != self.requests:
                raise ValueError('One exact serial native response, no stale replies')
            self.response_seconds.append(time.monotonic()-started)
            return value
        except BaseException:
            self.close(abort=True)
            raise

    def close(self, *, abort=False):
        if self.child is None or self.reaped: return
        try:
            self.child.stdin.close()  # Healthy protocol exits on EOF; no new request.
            if abort and self.child.poll() is None: self.child.terminate()
            try:
                self.exit = self.child.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.child.kill()
                self.exit = self.child.wait(timeout=3)
        finally:
            self.child.stdout.close()
            self.reaped = self.child.poll() is not None

    def receipt(self):
        return dict(schema=1, pid=self.child.pid if self.child else None, ready=self.ready,
                    requests=self.requests, startup_seconds=self.startup_seconds,
                    response_seconds=self.response_seconds, exit=self.exit, child_reaped=self.reaped,
                    started_before_original_command=True, reply_timeout_seconds=TIMEOUT)


def verify(value, simulator, discoveries):
    ready = value['ready']
    if value['schema'] != 1 or value['exit'] != 0 or value['child_reaped'] is not True or \
            value['started_before_original_command'] is not True or value['reply_timeout_seconds'] != 3 or \
            ready['kind'] != 'ready' or ready['schema'] != 1 or ready['selector'] != 'PROC_UID_ONLY' or \
            ready['simulator'] != simulator or ready['own_process_seen'] is not True or \
            type(value['pid']) is not int or value['pid'] <= 0 or ready['helper_pid'] != value['pid'] or \
            value['requests'] != discoveries or len(value['response_seconds']) != discoveries:
        raise ValueError('Actual prewarmed owned kernel child must exit/reap with every exact response')
    for seconds in [value['startup_seconds'], *value['response_seconds']]:
        if type(seconds) not in (int, float) or not math.isfinite(seconds) or not 0 <= seconds < 3:
            raise ValueError('Original3s native startup/reply bounds preserved')
