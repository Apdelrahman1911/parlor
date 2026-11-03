#!/usr/bin/env python3
"""Free unused preinstalled toolchains on the standard, ephemeral Linux CI runner.

Never touches Android, Java, Kotlin/Gradle caches, checkout outputs, or evidence.
Four fixed roots only; no caller-controlled deletion paths or background sampler.
"""
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess

TOOL_ROOTS = tuple(Path(p) for p in (
    '/usr/share/dotnet', '/usr/local/.ghcup', '/usr/local/swift', '/opt/hostedtoolcache/CodeQL'))
MIN_FREE_BYTES = 20 * 1024**3
TASK_KEYS = ('GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT', 'GITHUB_JOB')


def runner_task(env):
    required = {'GITHUB_ACTIONS':'true', 'RUNNER_ENVIRONMENT':'github-hosted',
                'RUNNER_OS':'Linux', 'RUNNER_ARCH':'X64', 'ImageOS':'ubuntu24',
                'GITHUB_REPOSITORY':'Apdelrahman1911/parlor', 'GITHUB_JOB':'desktop-android'}
    if any(env.get(k) != v for k,v in required.items()):
        raise RuntimeError('Capacity cleanup is restricted to the standard hosted Ubuntu24 full-verification lane')
    task = {k:env.get(k, '') for k in TASK_KEYS}
    if any(not task[k].isascii() or not task[k].isdigit() or not 1 <= len(task[k]) <= 20
           for k in TASK_KEYS[:2]):
        raise RuntimeError('Invalid hosted task identity')
    return task


def receipt_path(env, task):
    raw = env.get('RUNNER_TEMP', '')
    temp = Path(raw)
    if not temp.is_absolute() or temp.is_symlink() or temp.resolve(strict=True) != temp or not temp.is_dir():
        raise RuntimeError('Canonical existing runner temporary directory required')
    result = temp / ('parlor-verification-' + '-'.join(task.values()) + '-hosted-disk.json')
    if result.exists() or result.is_symlink():
        raise RuntimeError('Existing capacity evidence cannot be overwritten')
    return result


def checked_tools(roots=TOOL_ROOTS):
    found = []
    for path in roots:
        try:
            entry = path.lstat()
        except FileNotFoundError:
            found.append((path, False))
            continue
        if (not stat.S_ISDIR(entry.st_mode) or path.is_symlink() or
                path.resolve(strict=True) != path or path.is_mount() or
                entry.st_dev != path.parent.stat().st_dev or entry.st_uid not in (0, os.getuid())):
            raise RuntimeError('Refusing redirected, foreign, mounted or non-directory unused-tool root')
        found.append((path, True))
    return found


def delete_tool(path):
    # GNU rm never follows links in the tree or crosses filesystem boundaries.
    result = subprocess.run(['sudo', '-n', 'rm', '-rf', '--one-file-system', '--', str(path)],
                            stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, timeout=120)
    if result.returncode != 0 or path.exists() or path.is_symlink():
        raise RuntimeError('Unused-tool cleanup failed; no automatic deletion retry')


def reclaim(roots=TOOL_ROOTS, delete=delete_tool, usage=shutil.disk_usage):
    # Validate every root before the first deletion, not halfway through cleanup.
    tools = checked_tools(roots)
    before = usage('/')
    entries = []
    for path, exists in tools:
        if exists:
            delete(path)
        entries.append({'tool_root':str(path), 'result':'REMOVED' if exists else 'NOT_INSTALLED'})
    after = usage('/')
    return {'before':dict(zip(('total','used','free'), before)),
            'after':dict(zip(('total','used','free'), after)),
            'reclaimed_bytes':after.free-before.free, 'minimum_free_bytes':MIN_FREE_BYTES,
            'tools':entries, 'capacity_sufficient':after.free >= MIN_FREE_BYTES}


def main():
    task = runner_task(os.environ)
    output = receipt_path(os.environ, task)
    result = {'schema':1, 'task':task, 'scope':'Unused standard Linux toolchains only; no build, SDK, cache or evidence deletion'}
    try:
        result.update(reclaim())
        result['result'] = 'PASS' if result['capacity_sufficient'] else 'FAIL'
        if not result['capacity_sufficient']:
            result['error'] = 'Insufficient free root space before dependency restore; full verification was not started'
    except (OSError, RuntimeError, subprocess.TimeoutExpired) as error:
        result.update(result='FAIL', error=str(error) if isinstance(error, RuntimeError) else type(error).__name__)
    # Exclusive private receipt. It is subsequently uploaded with verification evidence.
    fd = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'w') as stream:
        json.dump(result, stream, indent=2);stream.write('\n');stream.flush();os.fsync(stream.fileno())
    print('Hosted Linux capacity:', result['result'],
          'free bytes:', result.get('after', {}).get('free', 'unavailable'),
          'reclaimed bytes:', result.get('reclaimed_bytes', 'unavailable'))
    return 0 if result['result'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
