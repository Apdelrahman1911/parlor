"""Bounded free-space experiment on one standard ephemeral GitHub Linux host.

Never a generic disk cleaner. No local invocation, device resizing, test changes,
or fabricated small-runner claim. Keep the owned allocated file through upload.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys

SOURCE = '9511ee3abe0f7ffd30c4c87a02eba18523cf7021'
BRANCH = 'refs/heads/verification/android-capacity-envelope-2026-11-06'
MINIMUM = 26 * 1024 ** 3
TARGET = MINIMUM + 16 * 1024 ** 2
TOLERANCE = 16 * 1024 ** 2


def context():
    expected = dict(GITHUB_ACTIONS='true', RUNNER_ENVIRONMENT='github-hosted',
                    RUNNER_OS='Linux', RUNNER_ARCH='X64', ImageOS='ubuntu24',
                    GITHUB_REPOSITORY='Apdelrahman1911/parlor',
                    GITHUB_JOB='desktop-android', GITHUB_REF=BRANCH, GITHUB_EVENT_NAME='push')
    if any(os.environ.get(k) != v for k, v in expected.items()):
        raise RuntimeError('Exact free hosted capacity experiment context required')
    if subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip() != SOURCE or \
            subprocess.check_output(['git', 'status', '--porcelain']):
        raise RuntimeError('Exact clean frozen application source required')
    task = {k: os.environ[k] for k in ('GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT', 'GITHUB_JOB')}
    if any(not task[k].isascii() or not task[k].isdigit() or not 1 <= len(task[k]) <= 20
           for k in ('GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT')):
        raise RuntimeError('Invalid task identity')
    temp = Path(os.environ['RUNNER_TEMP'])
    if temp.resolve(strict=True) != temp or temp.is_symlink() or not temp.is_dir():
        raise RuntimeError('Canonical runner temporary directory required')
    prefix = temp / ('parlor-capacity-envelope-' + task['GITHUB_RUN_ID'] + '-' + task['GITHUB_RUN_ATTEMPT'])
    return task, prefix


def new_receipt(path, data):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, 'w') as output:
        json.dump(data, output, indent=2); output.write('\n'); output.flush(); os.fsync(output.fileno())


def identity(item):
    return dict(device=item.st_dev, inode=item.st_ino, uid=item.st_uid,
                mode=stat.S_IMODE(item.st_mode), bytes=item.st_size, allocated_bytes=item.st_blocks * 512)


def reserve_length(free):
    if type(free) is not int or free < TARGET:
        raise RuntimeError('Insufficient space for the controlled reserve before dependency restore')
    return (free - TARGET) // 4096 * 4096


def require_identity(item, expected):
    if not stat.S_ISREG(item.st_mode) or item.st_nlink != 1 or identity(item) != expected:
        raise RuntimeError('Owned capacity reservation changed; deletion refused')


def reserve(task, prefix):
    path = prefix.with_suffix('.bin')
    before = shutil.disk_usage(prefix.parent)
    length = reserve_length(before.free)
    descriptor = os.open(path, os.O_RDWR | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    try:
        if length: os.posix_fallocate(descriptor, 0, length)
        os.fsync(descriptor)
        item = os.fstat(descriptor)
        if item.st_size != length or item.st_blocks * 512 < length:
            raise RuntimeError('Reservation must consume real blocks, not a sparse logical size')
        after = shutil.disk_usage(prefix.parent)
        if not MINIMUM <= after.free <= TARGET + TOLERANCE:
            raise RuntimeError('Actual free space is outside the documented 26GiB envelope')
        receipt = dict(result='PASS', source=SOURCE, workflow_head=os.environ['GITHUB_SHA'], task=task,
                       file=path.name, identity=identity(item), before=before._asdict(), after=after._asdict(),
                       minimum_bytes=MINIMUM, target_bytes=TARGET, tolerance_bytes=TOLERANCE,
                       experiment='Allocated free-space reservation; not the historical smaller filesystem',
                       control_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
        new_receipt(prefix.with_suffix('.start.json'), receipt)
        print(json.dumps(receipt, indent=2))
    finally:
        os.close(descriptor)


def finish(task, prefix):
    start = json.loads(prefix.with_suffix('.start.json').read_text())
    if start['source'] != SOURCE or start['task'] != task or start['workflow_head'] != os.environ['GITHUB_SHA']:
        raise RuntimeError('Reservation source/task binding differs')
    path = prefix.with_suffix('.bin')
    require_identity(path.lstat(), start['identity'])
    before = shutil.disk_usage(prefix.parent)
    path.unlink()
    if path.exists() or path.is_symlink(): raise RuntimeError('Reservation survived retirement')
    after = shutil.disk_usage(prefix.parent)
    result = dict(result='PASS', source=SOURCE, workflow_head=os.environ['GITHUB_SHA'], task=task,
                  retained_through_evidence_upload=True,
                  source_evidence_upload=os.environ.get('CAPACITY_SOURCE_UPLOAD_OUTCOME'),
                  final_identity=start['identity'], before=before._asdict(), after=after._asdict(),
                  reservation_absent=True,
                  start_sha256=hashlib.sha256(prefix.with_suffix('.start.json').read_bytes()).hexdigest())
    new_receipt(prefix.with_suffix('.finish.json'), result)
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    task, prefix = context()
    if sys.argv[1:] == ['reserve']: reserve(task, prefix)
    elif sys.argv[1:] == ['finish']: finish(task, prefix)
    else: raise SystemExit('Only reserve or finish is supported')
