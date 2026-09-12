#!/usr/bin/env python3
"""One-shot administrative C04 recheck only; does not invoke the consumer/build."""
import datetime
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import stat

ROOT = Path('/root/projects/Parlor/parlor')
C = 'remediation-runs/2026-09-07-local-readiness'
N = 'remediation-runs/2026-09-08-continuation'
AUTHOR = N + '/reviews/candidate-input-binding-author-inspection-04.json'
REVIEW = N + '/reviews/candidate-input-binding-independent-04.json'
BINDING = N + '/candidate-input-binding-04.json'
OUT = N + '/reviews/candidate-input-current-bindings-04.json'
DESTINATIONS = [C + '/evidence/final-candidate-input-linux-04', OUT,
                N + '/reviews/candidate-input-04-controller.log']
HELD = {
    AUTHOR: '91f76f850dafe424d6aa9564c44b623ad645cb60728f59d7d3f3edf2cf65ba6b',
    REVIEW: '189086dea69cd62fef4c56dc85c9e3034d31ead889c5d282fed053308a4fa987',
    BINDING: '641d4a894084f6d4fc76360d964b21e1920faa74912bff1d463a1e826351bc0d',
    C + '/run_gradle_cycle.py': '1b9e882514bfe5386785d5a71ae32fdc2bc3aa4cc1dd24532709899fbf4f41bc',
}


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def identity_fields(value):
    # A read may legitimately update atime; all ownership/content identity
    # fields, including nanosecond change/modification times, stay mandatory.
    return (value.st_dev, value.st_ino, value.st_mode, value.st_nlink,
            value.st_uid, value.st_gid, value.st_size, value.st_mtime_ns, value.st_ctime_ns)


def read_owned(relative):
    path = Path(relative)
    require(not path.is_absolute() and '..' not in path.parts, 'nonrelative input')
    path = ROOT / path
    for parent in [path, *path.parents]:
        if parent == ROOT:
            break
        require(not parent.is_symlink(), 'symlink input component')
    before = path.lstat()
    require(stat.S_ISREG(before.st_mode) and before.st_uid == os.getuid()
            and before.st_size <= 16 * 1024 * 1024, 'unowned/nonregular/oversize input')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    try:
        opened = os.fstat(fd)
        require(identity_fields(opened) == identity_fields(before), 'input changed before open')
        with os.fdopen(os.dup(fd), 'rb') as stream:
            data = stream.read(16 * 1024 * 1024 + 1)
        require(identity_fields(os.fstat(fd)) == identity_fields(opened)
                and identity_fields(path.lstat()) == identity_fields(opened)
                and len(data) == before.st_size, 'input changed while reading')
    finally:
        os.close(fd)
    return data


def digest(data):
    return hashlib.sha256(data).hexdigest()


def rows(paths):
    result = []
    for path in sorted(paths):
        raw = read_owned(path)
        result.append([path, digest(raw), len(raw)])
    return result


def require_absent():
    for path in DESTINATIONS:
        require(not os.path.lexists(ROOT / path), 'refusing existing C04 destination')


def descendants(relative):
    parent = ROOT / relative
    require(parent.is_dir() and not parent.is_symlink(), 'invalid input directory')
    result = set()
    for path in parent.rglob('*'):
        require(not path.is_symlink(), 'symlink in input directory')
        if path.is_dir():
            continue
        result.add(str(path.relative_to(ROOT)))
    return result


require(Path.cwd() == ROOT, 'wrong checkout')
require_absent()
for path, expected in HELD.items():
    require(digest(read_owned(path)) == expected, 'held input changed: ' + path)
author = json.loads(read_owned(AUTHOR))
review = json.loads(read_owned(REVIEW))
binding = json.loads(read_owned(BINDING))
require(review['findings'] == [], 'binding not independently cleared')
pins = author['current_control_pins'] + author['official_schema_pins']
require(len(pins) == 14 and len({pin['path'] for pin in pins}) == 14,
        'incomplete control/schema pin set')
for pin in pins:
    data = read_owned(pin['path'])
    require(digest(data) == pin['sha256'] and len(data) == pin['bytes'], 'pin drift')

spec = importlib.util.spec_from_file_location('c04_admission_lane', ROOT / (C + '/run_gradle_cycle.py'))
lane = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lane)


def capture():
    # JSON receipts represent tuple rows as arrays. Normalize the COMPLETE
    # fresh objects, not a hash or selected fields; full equality stays required.
    return json.loads(json.dumps({'source': lane.identity(),
                                 'runner': lane.runner_identity(False)}, allow_nan=False))


current = capture()
require(current == {key: author['current_full_capture'][key] for key in ('source', 'runner')},
        'complete current source/runner differs from approved author capture')
require(current['source']['tracked_status'] == '', 'tracked checkout dirty')
require(current['source']['commit'] == '08d1adfbd7284f190fbab5f7baa846944bca5b68'
        and current['source']['tree'] == 'c19beea4647ce882c02d8751d1bf82c22484b52b'
        and current['source']['branch'] == 'fix/local-readiness-2026-09-07', 'wrong frozen source')
require({key: current['source'][key] for key in binding['source']} == binding['source'],
        'binding source mismatch')
for key in ('export_receipt', 'render_receipt'):
    data = read_owned(binding[key]['path'])
    require(digest(data) == binding[key]['sha256'], 'producer receipt changed')
    producer = json.loads(data)
    require(producer['source_before'] == producer['source_after'] == current['source']
            and producer['runner_before'] == producer['runner_after'] == current['runner'],
            'complete producer/current mismatch')

fixed = {BINDING, *(binding[key]['path'] for key in
                    ('export_receipt', 'render_receipt', 'lane', 'consumer')),
         'scripts/verification/resolved_dependencies.init.gradle',
         'scripts/verification/dependency_inventory.py',
         'scripts/verification/third_party_notices.py', 'gradle/verification-metadata.xml',
         'config/third-party-notices.json', 'gradle/libs.versions.toml', 'composeApp/build.gradle.kts'}
directories = [binding['graphs'], binding['report'], binding['schemas'],
               'composeApp/src/commonMain/composeResources/files/legal']
directory_sets = {path: descendants(path) for path in directories}
require([len(directory_sets[path]) for path in directories] == [5, 465, 3, 26],
        'input directory closure changed')
closure = rows(fixed.union(*directory_sets.values()))
closure_sha = digest(json.dumps(closure, separators=(',', ':')).encode())
require(len(closure) == review['prospective_input_closure']['count'] == 511
        and closure_sha == review['prospective_input_closure']['manifest_sha256']
        == 'a1cd2e26dd6d6264dea7213df114772643575a362787f9ee2ca340576e94b94a',
        'prospective exact 511-file closure mismatch')

# Observe the existing lane lock without creating/deleting or retaining it.
lock_path = ROOT / (C + '/build-lane.lock')
lock_before = lock_path.lstat()
require(stat.S_ISREG(lock_before.st_mode) and lock_before.st_uid == os.getuid(), 'invalid lane lock')
lock = os.open(lock_path, os.O_RDONLY | os.O_NOFOLLOW)
try:
    require(os.fstat(lock) == lock_before, 'lane lock changed')
    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    require(capture() == current, 'complete source/runner changed during admission')
    require(rows({row[0] for row in closure}) == closure, 'input bytes changed during admission')
    require(all(descendants(path) == paths for path, paths in directory_sets.items()),
            'input directory set changed during admission')
    for path, expected in HELD.items():
        require(digest(read_owned(path)) == expected, 'held input changed during admission')
    for pin in pins:
        data = read_owned(pin['path'])
        require(digest(data) == pin['sha256'] and len(data) == pin['bytes'], 'postread pin drift')
    require_absent()
    record = {
        'schema_version': 1, 'kind': 'C04_FRESH_COMPLETE_PREEXECUTION_RECHECK_NOT_CONSUMER_RESULT',
        'coordinator': '/root', 'recorded_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'capture': current, 'all14_control_and_schema_pins': pins,
        'binding': {'path': BINDING, 'sha256': HELD[BINDING]},
        'independent_binding_review': {'path': REVIEW, 'sha256': HELD[REVIEW]},
        'full_capture_comparison': 'complete JSON-normalized canonical source and runner equality, twice',
        'candidate_paths_absent': DESTINATIONS,
        'prospective_input_closure': {'count': len(closure), 'sha256': closure_sha,
                                     'bytes': sum(row[2] for row in closure), 'files': closure},
        'directory_sets_rechecked': True, 'existing_lane_lock_exclusively_observed': True,
        'approved_command_NOT_YET_EXECUTED': review['decision']['one_candidate_command'],
        'scope': 'Administrative evidence only. Consumer/app/native/test outcomes are not asserted.',
        'cleanup': 'Read-only Git children exited, -B disables bytecode; lock released on exit. No build/worker/scratch allocated.',
    }
    data = (json.dumps(record, indent=2) + '\n').encode()
    fd = os.open(ROOT / OUT, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o644)
    with os.fdopen(fd, 'wb') as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())
    print(json.dumps({'path': OUT, 'sha256': digest(data), 'bytes': len(data),
                      'source': current['source']['commit'], 'closure': closure_sha,
                      'candidate': 'NOT_RUN'}))
finally:
    os.close(lock)
