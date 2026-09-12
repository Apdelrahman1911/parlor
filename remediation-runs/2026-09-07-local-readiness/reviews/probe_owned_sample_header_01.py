#!/usr/bin/env python3
"""Observe sample's header for our own disposable sleep process, not Parlor.

Root invokes through the coordinated receipt/stop/cleanup wrapper. This proves
only the installed tool's observed format; it does not recreate native08.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import stat
import subprocess
import tempfile


def main():
    root = Path(os.environ['TMPDIR']).resolve(strict=True)
    if root.name != 'tmp' or root.parent.name != 'scratch' or root.parent.parent.name != 'normal-sample-header-probe-01':
        raise RuntimeError('This observer requires its exact root-owned cycle scratch')
    executable = Path('/bin/sleep')
    tool = Path('/usr/bin/sample')
    result = dict(scope='Owned host sleep process only; not iOS app or repair verification',
                  executable_sha256=hashlib.sha256(executable.read_bytes()).hexdigest(),
                  sample_sha256=hashlib.sha256(tool.read_bytes()).hexdigest())
    allocation = Path(tempfile.mkdtemp(prefix='owned-sample-header-', dir=root))
    allocated = allocation.lstat()
    identity = (allocated.st_dev, allocated.st_ino, allocated.st_uid)
    if not stat.S_ISDIR(allocated.st_mode) or allocated.st_uid != os.getuid():
        raise RuntimeError('Unexpected owned allocation')
    raw = allocation / 'sample.txt'
    child = None
    try:
        child = subprocess.Popen([str(executable), '60'], stdin=subprocess.DEVNULL,
                                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if child.poll() is not None:
            raise RuntimeError('Owned target exited before observation')
        result['owned_pid'] = child.pid
        command = [str(tool), str(child.pid), '1', '10', '-file', str(raw)]
        completed = subprocess.run(command, capture_output=True, timeout=15)
        result['sample_exit_code'] = completed.returncode
        if child.poll() is not None or completed.returncode != 0:
            raise RuntimeError('Owned host target or sampling command failed')
        if raw.is_symlink() or not raw.is_file() or not 0 < raw.stat().st_size <= 16 * 1024 * 1024:
            raise RuntimeError('Missing, redirected or unbounded owned sample')
        text = raw.read_text()
        # Retain only tool-generated headers for the explicitly spawned target;
        # never raw stacks, mappings, arbitrary process lists or app state.
        headers = [line for line in text.splitlines() if re.match(r'^(?:Process|Path):', line)]
        if len(headers) > 8 or any(len(line.encode()) > 4096 for line in headers):
            raise RuntimeError('Unexpected header budget')
        result['headers'] = headers
        result['process_matches'] = re.findall(r'^Process:\s+[^\r\n]+ \[([0-9]+)\]\s*$', text, flags=re.MULTILINE)
        result['path_matches'] = re.findall(r'^Path:\s+([^\r\n]+?)\s*$', text, flags=re.MULTILINE)
        result['raw_sample_sha256'] = hashlib.sha256(raw.read_bytes()).hexdigest()
    finally:
        if child is not None:
            if child.poll() is None:
                child.terminate()
                try:
                    child.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    child.kill()
                    child.wait(timeout=5)
            result['target_reaped'] = child.returncode is not None
        actual = allocation.lstat()
        if (actual.st_dev, actual.st_ino, actual.st_uid) != identity or not stat.S_ISDIR(actual.st_mode):
            raise RuntimeError('Owned allocation changed; do not delete a substituted path')
        if raw.exists():
            if raw.is_symlink() or not raw.is_file():
                raise RuntimeError('Refuse redirected raw sample cleanup')
            raw.unlink()
        allocation.rmdir()  # Never recursively delete unknown contents.
        result['owned_temporary_removed'] = not allocation.exists()
        print(json.dumps(result, indent=2), flush=True)


if __name__ == '__main__':
    def interrupted(number, _frame):
        raise KeyboardInterrupt('signal ' + str(number))
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    main()
