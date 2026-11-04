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
}


def cache_operations(help_text: str) -> list[str]:
    """Inspect successful top-level usage, never invoke a guessed selector.

    Xcode 26.3's bare dyld_shared_cache returns runtime usage with exit 117.
    Its advertised update/remove operations cannot provide read-only state.
    """
    if 'Usage: simctl runtime <operation> <arguments>' not in help_text:
        raise ValueError('Missing or unrelated native runtime usage')
    return re.findall(r'^\s*dyld_shared_cache ([a-z_]+)\b', help_text, re.MULTILINE)


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
                   commands=[], application_executed=False,
                   explicit_cache_mutation_requested=False,
                   background_cache_effects_measured=False,
                   limitation='A current runner inventory cannot establish historical startup causality.')

    def save() -> None:
        temporary = target / 'receipt.tmp'
        temporary.write_text(json.dumps(receipt, indent=2) + '\n')
        temporary.replace(target / 'receipt.json')

    def query(label: str) -> str:
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
        if completed.returncode != 0:
            raise RuntimeError('Native query failed: ' + label)
        return stdout.read_text() + '\n' + stderr.read_text()

    save()
    try:
        version = query('xcode')
        path = query('simctl-path')
        require_toolchain(version, path)
        help_text = query('runtime-help')
        receipt['advertised_cache_operations'] = cache_operations(help_text)
        json.loads(query('runtime-images'))
        json.loads(query('available-runtimes'))
        receipt['remaining_boundary'] = (
            'Runtime image inventory is not cache validity. No cache operation is requested; '
            'advertised_cache_operations records the native usage, not an executed cache query.'
        )
        receipt['status'] = 'COLLECTED_READ_ONLY_NOT_RUNTIME_QUALIFICATION'
    except Exception as error:
        receipt.update(status='FAILED_INTAKE', error=type(error).__name__ + ': ' + str(error))
        raise
    finally:
        save()


if __name__ == '__main__':
    main()
