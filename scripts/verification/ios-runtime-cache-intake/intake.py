"""Read-only pinned-Xcode cache inventory; never application qualification."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time


DEVELOPER = '/Applications/Xcode_26.3.app/Contents/Developer'
AGENTS_SHA = 'd9505fa9e8338a8a1ca85a63e87ffc72a4a0013468116f6f6d7a5c4c7dfbc02b'
COMMANDS = {
    'xcode': ['xcrun', 'xcodebuild', '-version'],
    'simctl-path': ['xcrun', '--find', 'simctl'],
    'runtime-help': ['xcrun', 'simctl', 'help', 'runtime'],
    'runtime-images': ['xcrun', 'simctl', 'runtime', 'list', '--json'],
    'available-runtimes': ['xcrun', 'simctl', 'list', 'runtimes', '--json'],
    # No action is requested: inspect the native subcommand usage only.
    'cache-help': ['xcrun', 'simctl', 'runtime', 'dyld_shared_cache'],
    'cache-print': ['xcrun', 'simctl', 'runtime', 'dyld_shared_cache', 'print'],
}


def cache_print_advertised(help_text: str) -> bool:
    """Do not guess a selector or execute a mutating cache operation."""
    return bool(re.search(r'^\s*print\s+(?:Print|Display|Show)\b', help_text, re.MULTILINE))


def require_cache_usage(code: int, text: str) -> None:
    if code not in {0, 64} or 'dyld_shared_cache' not in text or not text.strip():
        raise ValueError('Missing or unrelated native cache usage')


def require_toolchain(version: str, simctl_path: str) -> None:
    if version.strip().splitlines() != ['Xcode 26.3', 'Build version 17C529']:
        raise ValueError('Unqualified Xcode version/build')
    path = Path(simctl_path.strip())
    if not path.is_absolute() or str(path) != DEVELOPER + '/usr/bin/simctl':
        raise ValueError('simctl is not from the pinned developer directory')


def output_path(raw: str, runner_temp: str) -> Path:
    root, path = Path(runner_temp), Path(raw)
    if not root.is_absolute() or root.resolve() != root:
        raise ValueError('Runner temporary root must be canonical')
    if not path.is_absolute() or path.parent != root or path.exists() or path.is_symlink():
        raise ValueError('Only one new direct child of runner temp is allowed')
    return path


def main() -> None:
    if sys.platform != 'darwin' or os.environ.get('DEVELOPER_DIR') != DEVELOPER:
        raise RuntimeError('This intake requires the pinned Apple environment')
    workspace = Path(__file__).resolve().parents[3]
    if hashlib.sha256((workspace / 'AGENTS.md').read_bytes()).hexdigest() != AGENTS_SHA:
        raise RuntimeError('AGENTS.md changed')
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=workspace, text=True).strip()
    tree = subprocess.check_output(['git', 'rev-parse', 'HEAD^{tree}'], cwd=workspace, text=True).strip()
    if subprocess.check_output(['git', 'status', '--porcelain'], cwd=workspace):
        raise RuntimeError('Source is not clean')
    target = output_path(os.environ['PARLOR_CACHE_INTAKE_OUTPUT'], os.environ['RUNNER_TEMP'])
    target.mkdir(mode=0o700)
    receipt = dict(status='INCOMPLETE', head=head, tree=tree,
                   run=os.environ['GITHUB_RUN_ID'], attempt=os.environ['GITHUB_RUN_ATTEMPT'],
                   commands=[], application_executed=False, cache_modified=False,
                   limitation='A current runner inventory cannot establish historical startup causality.')

    def save() -> None:
        temporary = target / 'receipt.tmp'
        temporary.write_text(json.dumps(receipt, indent=2) + '\n')
        temporary.replace(target / 'receipt.json')

    def query(label: str, *, usage: bool = False) -> str:
        args = COMMANDS[label]
        started = time.monotonic()
        stdout, stderr = target / (label + '.stdout'), target / (label + '.stderr')
        with stdout.open('xb') as out, stderr.open('xb') as err:
            completed = subprocess.run(args, stdout=out, stderr=err, timeout=45, check=False)
        if stdout.stat().st_size + stderr.stat().st_size > 1024 * 1024:
            raise RuntimeError('Oversize native inventory')
        row = dict(label=label, argv=args, exit_code=completed.returncode,
                   elapsed_seconds=time.monotonic() - started,
                   stdout_sha256=hashlib.sha256(stdout.read_bytes()).hexdigest(),
                   stderr_sha256=hashlib.sha256(stderr.read_bytes()).hexdigest())
        receipt['commands'].append(row)
        save()
        # Apple's no-action help may return EX_USAGE. This is recorded usage,
        # not a successful test or accepted cache operation.
        if completed.returncode not in ({0, 64} if usage else {0}):
            raise RuntimeError('Native query failed: ' + label)
        return stdout.read_text() + '\n' + stderr.read_text()

    save()
    try:
        version = query('xcode')
        path = query('simctl-path')
        require_toolchain(version, path)
        query('runtime-help')
        json.loads(query('runtime-images'))
        json.loads(query('available-runtimes'))
        help_text = query('cache-help', usage=True)
        require_cache_usage(receipt['commands'][-1]['exit_code'], help_text)
        receipt['cache_print_advertised'] = cache_print_advertised(help_text)
        if receipt['cache_print_advertised']:
            query('cache-print')
        else:
            receipt['remaining_boundary'] = 'Native usage did not advertise the closed read-only print selector'
        receipt['status'] = 'COLLECTED_READ_ONLY_NOT_RUNTIME_QUALIFICATION'
    except Exception as error:
        receipt.update(status='FAILED_INTAKE', error=type(error).__name__ + ': ' + str(error))
        raise
    finally:
        save()


if __name__ == '__main__':
    main()
