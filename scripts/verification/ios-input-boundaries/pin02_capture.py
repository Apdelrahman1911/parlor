"""Copy-only post-failure export plans and bounded, independently owned retention.

No build, launch, signal, Simulator allocation, input action or cleanup is performed.
An IPC acknowledgement is deliberately not an accepted evidence source.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import stat

import pin01_copy
import pin01_receipts

HERE = Path(__file__).resolve().parent
CONTAINER = 'iosApp/iosApp/ComposeContainerViewController.swift'
DRIVER = 'iosApp/iosAppUITests/IOSAppLaunchUITests.swift'
MODIFIED = (CONTAINER, DRIVER)
UUID = re.compile(r'[0-9A-F]{8}(?:-[0-9A-F]{4}){3}-[0-9A-F]{12}\Z')
MAX_BYTES = pin01_receipts.MAX_BYTES + 4096
KEYS = {'schema', 'token', 'simulator', 'bundle', 'boot', 'process', 'installedMs',
        'capturedMs', 'snapshot'}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def render(sources, digests):
    """Return changes for two exact, separately owned source copies only.

    Requires the separately planned PIN01 bridge in the app copy. The original
    driver, successful path, tap, predicates and deadlines stay byte-for-byte
    inside this additive plan. Only an already-failed public transition signals.
    """
    require(set(sources) == set(digests) == set(MODIFIED), 'Exact two source bindings required')
    for path, source in sources.items():
        require(type(source) is str and 'PIN02' not in source, 'Unknown/repeated capture copy')
        require(hashlib.sha256(source.encode()).hexdigest() == digests[path], 'Frozen source changed')
    output = dict(sources)
    output[CONTAINER] = pin01_copy.once(output[CONTAINER],
        '    private let contentController: UIViewController\n',
        '    private let contentController: UIViewController\n'
        '#if DEBUG && targetEnvironment(simulator)\n'
        '    private var pin02Capture: PIN02Capture?\n'
        '#endif\n')
    output[CONTAINER] = pin01_copy.once(output[CONTAINER],
        '        super.init(nibName: nil, bundle: nil)\n',
        '        super.init(nibName: nil, bundle: nil)\n'
        '#if DEBUG && targetEnvironment(simulator)\n'
        '        pin02Capture = PIN02Capture.install(snapshot: { MainViewControllerKt.PIN01InputSnapshot() })\n'
        '#endif\n')
    output[CONTAINER] = 'import ComposeApp\n' + output[CONTAINER] + '\n' + (HERE / 'PIN02Capture.swift.in').read_text()
    # Every app instance gets an opaque request owner before its original launch;
    # no app observation, wait, preference or permission action is inserted here.
    anchor = '        let app = XCUIApplication()\n'
    require(output[DRIVER].count(anchor) == 4, 'Actual four launch owners changed')
    output[DRIVER] = output[DRIVER].replace(anchor, anchor +
        '        PIN02FailureRequest.configure(app)\n')
    anchor = ('        if result != .completed {\n'
              '            // Capture after the single tap, not before: evidence collection')
    output[DRIVER] = pin01_copy.once(output[DRIVER], anchor,
        anchor.replace('// Capture after', 'PIN02FailureRequest.captureAfterFailedTransition(app)\n'
                       '            // Capture after'))
    output[DRIVER] += '\n' + (HERE / 'PIN02FailureRequest.swift.in').read_text()
    return output


def decode_capture(raw, *, token, simulator, bundle):
    """Validate retention, not input delivery or source/runtime/cleanup custody.

    Overflow/invalid/empty numeric buffers may be safely retained but NEVER qualify.
    Shape-checking uses a separate copy; the original bytes/counters are unchanged.
    """
    require(type(raw) is bytes and 0 < len(raw) <= MAX_BYTES, 'Bounded capture bytes required')
    value = json.loads(raw, object_pairs_hook=pin01_receipts.reject_duplicates,
                       parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite capture')))
    require(type(value) is dict and set(value) == KEYS, 'Unknown/private/missing capture field')
    require(type(value['schema']) is int and value['schema'] == 1, 'Unknown capture schema')
    for key, expected in (('token', token), ('simulator', simulator)):
        require(type(expected) is str and UUID.fullmatch(expected), 'Explicit UUID owner required')
        require(type(value[key]) is str and value[key] == expected, 'Foreign request or Simulator')
    require(type(bundle) is str and re.fullmatch(r'[A-Za-z0-9.-]{1,128}\.debug', bundle) and
            value['bundle'] == bundle, 'Exact Debug product required')
    require(type(value['boot']) is str and UUID.fullmatch(value['boot']), 'Explicit process boot required')
    require(type(value['process']) is int and 0 < value['process'] <= 2**31 - 1, 'Invalid process')
    times = [value[key] for key in ('installedMs', 'capturedMs')]
    require(all(type(x) in (int, float) and 0 <= x <= 1e12 for x in times) and
            times[0] <= times[1], 'Invalid/reversed capture clock')
    snapshot = value['snapshot']
    require(type(snapshot) is dict and set(snapshot) == {'schema', 'nativeMode', 'overflow', 'invalid', 'rows'},
            'Unknown/private snapshot field')
    require(all(type(snapshot[key]) is int and 0 <= snapshot[key] <= 512 for key in ('overflow', 'invalid')),
            'Invalid bounded counters')
    require(type(snapshot['rows']) is list, 'Rows required')
    shape = dict(snapshot, overflow=0, invalid=0)
    if not shape['rows']:
        shape['rows'] = [[times[0], 4, 0, 0, 0, 0, 0, 0, 0, 0]]
    pin01_receipts.decode(json.dumps(shape, allow_nan=False).encode())
    # Compose may record before its returned child gets an outer container and
    # exporter. Only the upper capture bound applies; installedMs is NOT app boot.
    require(all(row[0] <= times[1] for row in snapshot['rows']), 'Rows newer than capture')
    complete = bool(snapshot['rows']) and snapshot['overflow'] == snapshot['invalid'] == 0
    return {'capture': value, 'numeric_buffer_complete': complete,
            'source_runtime_product_cleanup_verified': False, 'native_input_neutrality': False,
            'original_ios26_2_root_cause_proven': False}


def retain_file(source, destination, *, token, simulator, bundle):
    """Retain one caller-owned sandbox file; no directory scan, deletion or ACK fallback.

    Caller must independently bind the app sandbox and source/driver/product/run.
    Parent directory ownership is not inferred by this function. Repeated/foreign
    destinations, links, oversized/changing files and invalid/private bytes reject.
    """
    source, destination = Path(source), Path(destination)
    fd = os.open(source, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        before = os.fstat(fd)
        require(stat.S_ISREG(before.st_mode) and before.st_nlink == 1 and
                0 < before.st_size <= MAX_BYTES, 'Unsafe capture file')
        chunks, size = [], 0
        while True:
            part = os.read(fd, min(8192, MAX_BYTES + 1 - size))
            if not part:
                break
            chunks.append(part); size += len(part)
            require(size <= MAX_BYTES, 'Capture grew beyond bound')
        raw = b''.join(chunks)
        after = os.fstat(fd)
        require((before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns) ==
                (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns) and
                len(raw) == before.st_size, 'Capture changed during retention')
    finally:
        os.close(fd)
    reviewed = decode_capture(raw, token=token, simulator=simulator, bundle=bundle)
    # Never clobber earlier evidence; preserve partial writes for inspection too.
    with destination.open('xb') as output:
        output.write(raw); output.flush(); os.fsync(output.fileno())
    return dict(reviewed, retained_sha256=hashlib.sha256(raw).hexdigest(), retained_bytes=len(raw),
                result='SCOPED_NUMERIC_FILE_RETAINED_REQUIRES_NATIVE_CUSTODY')
