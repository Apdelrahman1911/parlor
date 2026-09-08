#!/usr/bin/env python3
"""Observe public sample user-path rendering for a disposable copied sleep.

This is not an iOS process, Parlor runtime evidence, or a repair verification.
Only a root-wrapper-owned temporary executable is launched and observed.
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


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    root = Path(os.environ['TMPDIR']).resolve(strict=True)
    if (root.name != 'tmp' or root.parent.name != 'scratch' or
            root.parent.parent.name != 'normal-sample-user-path-probe-01'):
        raise RuntimeError('This observer requires its exact root-owned cycle scratch')
    root.relative_to(Path.home().resolve(strict=True))
    original = Path('/bin/sleep')
    tool = Path('/usr/bin/sample')
    result = dict(scope='Owned host copied-sleep only; not iOS or repair verification',
                  original_sha256=sha(original), sample_sha256=sha(tool), observations=[])
    allocation = Path(tempfile.mkdtemp(prefix='owned-user-path-', dir=root))
    state = allocation.lstat()
    identity = (state.st_dev, state.st_ino, state.st_uid)
    if not stat.S_ISDIR(state.st_mode) or state.st_uid != os.getuid():
        raise RuntimeError('Unexpected owned allocation')
    executable = allocation / 'OwnedSleepHeaderProbe'
    raw_paths = [allocation / 'sample-default.txt', allocation / 'sample-fullPaths.txt']
    child = None
    try:
        with executable.open('xb') as output:
            output.write(original.read_bytes())
        executable.chmod(0o700)
        if executable.is_symlink() or sha(executable) != result['original_sha256']:
            raise RuntimeError('Public executable copy differs')
        child = subprocess.Popen([str(executable), '60'], stdin=subprocess.DEVNULL,
                                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        result.update(owned_pid=child.pid, copied_executable=str(executable))
        for raw, options in zip(raw_paths, ([], ['-fullPaths'])):
            if child.poll() is not None:
                raise RuntimeError('Owned target exited before observation')
            completed = subprocess.run([str(tool), str(child.pid), '1', '10', *options,
                                        '-file', str(raw)], capture_output=True, timeout=15)
            row = dict(options=options, sample_exit_code=completed.returncode)
            result['observations'].append(row)
            if child.poll() is not None or completed.returncode != 0:
                raise RuntimeError('Owned target or sampling command failed')
            if raw.is_symlink() or not raw.is_file() or not 0 < raw.stat().st_size <= 16 * 1024 * 1024:
                raise RuntimeError('Missing, redirected or unbounded owned sample')
            text = raw.read_text()
            headers = [line for line in text.splitlines() if re.match(r'^(?:Process|Path):', line)]
            if len(headers) > 8 or any(len(line.encode()) > 4096 for line in headers):
                raise RuntimeError('Unexpected header budget')
            row.update(headers=headers,
                       process_matches=re.findall(r'^Process:\s+[^\r\n]+ \[([0-9]+)\]\s*$', text, flags=re.MULTILINE),
                       path_matches=re.findall(r'^Path:\s+([^\r\n]+?)\s*$', text, flags=re.MULTILINE),
                       raw_sample_sha256=sha(raw))
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
            raise RuntimeError('Owned allocation changed; preserve substituted path')
        for path in [*raw_paths, executable]:
            if path.exists() or path.is_symlink():
                if path.is_symlink() or not path.is_file():
                    raise RuntimeError('Refuse redirected owned-file cleanup')
                path.unlink()
        allocation.rmdir()
        result['owned_temporary_removed'] = not allocation.exists()
        print(json.dumps(result, indent=2), flush=True)


if __name__ == '__main__':
    def interrupted(number, _frame):
        raise KeyboardInterrupt('signal ' + str(number))
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    main()
