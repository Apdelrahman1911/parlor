"""Read installed Apple tool facts only; no app launch, AX query or sampling.

The first owned Simulator sample in41401509909 exceeded8s even with1s
planned dwell. Inspect the actual tool before choosing a replacement collector.
Command exit codes (including usage/man absence) are evidence, not app results.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time


COMMANDS = (
    ('os-version', ['/usr/bin/sw_vers']),
    ('help', ['/usr/bin/sample', '-help']),
    ('man', ['/usr/bin/man', '-P', '/bin/cat', 'sample']),
    ('string-options', ['/usr/bin/strings', '-a', '/usr/bin/sample']),
)
MAX_OUTPUT = 4 * 1024 * 1024


def selected_strings(raw):
    """Retain option/stage strings, never copy the whole Apple executable."""
    return b'\n'.join(line for line in raw.splitlines() if
                      re.search(rb'^\s*-[A-Za-z]|[Ss]ymbol|[Ss]ampl|[Uu]nwind|task_for_pid', line)) + b'\n'


def review(directory):
    if sys.platform != 'darwin':
        raise RuntimeError('Installed Apple tool facts require an actual macOS runner')
    if directory.resolve() != directory.absolute() or directory.exists():
        raise ValueError('Fresh owned unredirected tool output')
    directory.mkdir(mode=0o700)
    env = {k: os.environ[k] for k in ('HOME', 'TMPDIR') if k in os.environ}
    env.update(PATH='/usr/bin:/bin:/usr/sbin:/sbin', LC_ALL='C', MANWIDTH='120')
    rows = []
    for label, command in COMMANDS:
        started = time.monotonic()
        # No app identifier/PID, privileged option, injected loader or credential.
        completed = subprocess.run(command, env=env, stdin=subprocess.DEVNULL,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=8)
        raw = completed.stdout
        if len(raw) > MAX_OUTPUT:
            raise ValueError('Bounded installed-tool evidence')
        saved = selected_strings(raw) if label == 'string-options' else raw
        with (directory/(label+'.log')).open('xb') as f:
            f.write(saved)
        rows.append(dict(label=label, command=command, exit=completed.returncode,
                         seconds=time.monotonic()-started, original_bytes=len(raw),
                         original_sha256=hashlib.sha256(raw).hexdigest(),
                         retained_bytes=len(saved), retained_sha256=hashlib.sha256(saved).hexdigest()))
    tool = Path('/usr/bin/sample').read_bytes()
    value = dict(schema=1, platform=sys.platform, command_timeout_seconds=8,
                 sampler_sha256=hashlib.sha256(tool).hexdigest(), commands=rows,
                 apps_launched=0, accessibility_queries=0, processes_sampled=0,
                 application_root_cause=False, shipping_qualified=False, physical_verified=False)
    with (directory/'receipt.json').open('x') as f:
        json.dump(value, f, indent=2)
    print(json.dumps(value, indent=2))


if __name__ == '__main__':
    review(Path(sys.argv[1]))
