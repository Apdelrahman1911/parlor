"""Installed Xcode profiler interfaces only; no recording, target or app replay.

Both owned sample reports in41412185488 are absent; the auxiliary reached
symbol processing. Research a documented alternate collector, not guessed flags.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time

DEVELOPER = '/Applications/Xcode_26.3.app/Contents/Developer'
COMMANDS = (
    ('os-version', ['/usr/bin/sw_vers']),
    ('profiler-path', ['/usr/bin/xcrun', '--find', 'xctrace']),
    ('help', ['/usr/bin/xcrun', 'xctrace', 'help']),
    ('record-help', ['/usr/bin/xcrun', 'xctrace', 'help', 'record']),
    ('export-help', ['/usr/bin/xcrun', 'xctrace', 'help', 'export']),
    ('templates', ['/usr/bin/xcrun', 'xctrace', 'list', 'templates']),
)
MAX_OUTPUT = 256 * 1024


def review(directory):
    if sys.platform != 'darwin':
        raise RuntimeError('Installed Xcode facts require an actual macOS runner')
    if directory.resolve() != directory.absolute() or directory.exists():
        raise ValueError('Fresh owned unredirected tool output')
    directory.mkdir(mode=0o700)
    env = {k: os.environ[k] for k in ('HOME', 'TMPDIR') if k in os.environ}
    env.update(PATH='/usr/bin:/bin:/usr/sbin:/sbin', LC_ALL='C', DEVELOPER_DIR=DEVELOPER)
    rows = []
    for label, command in COMMANDS:
        started = time.monotonic()
        result = subprocess.run(command, env=env, stdin=subprocess.DEVNULL,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=8)
        elapsed = time.monotonic()-started
        raw = result.stdout
        if not 0 < len(raw) <= MAX_OUTPUT:
            raise ValueError('Bounded actual installed profiler evidence')
        with (directory/(label+'.log')).open('xb') as output:
            output.write(raw)
        rows.append(dict(label=label, command=command, exit=result.returncode, seconds=elapsed,
                         bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()))
        if not 0 <= elapsed < 8:
            raise ValueError('Late observation cannot extend installed-tool deadline')
    receipt = dict(schema=1, platform=sys.platform, developer_dir=DEVELOPER, command_timeout_seconds=8,
                   commands=rows, apps_launched=0, accessibility_queries=0, processes_sampled=0,
                   recordings_started=0, application_root_cause=False, shipping_qualified=False,
                   physical_verified=False)
    with (directory/'receipt.json').open('x') as output:
        json.dump(receipt, output, indent=2)
    print(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    review(Path(sys.argv[1]))
