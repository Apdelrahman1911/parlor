"""Timed host child: public libproc paths for current UID, no argv/environment/AX."""
import ctypes
import errno
import json
import os
from pathlib import Path
import re
import sys
import time

MAX_PROCESSES = 4096
UUID = re.compile(r'[A-F0-9]{8}(?:-[A-F0-9]{4}){3}-[A-F0-9]{12}')


def collect(proc, simulator):
    if not UUID.fullmatch(simulator) or ctypes.sizeof(ctypes.c_int) != 4:
        raise ValueError('Explicit owned Simulator and native int ABI')
    started = time.monotonic()
    values = (ctypes.c_int * (MAX_PROCESSES+1))()
    width = ctypes.sizeof(ctypes.c_int)
    size = proc.proc_listpids(4, os.geteuid(), values, ctypes.sizeof(values))  # PROC_UID_ONLY; bytes.
    if not width <= size <= MAX_PROCESSES*width or size % width:
        raise ValueError('Complete bounded own-UID PID list required')
    pids = list(values[:size//width])
    if any(p < 0 for p in pids) or len(set(pids)) != len(pids):
        raise ValueError('Positive unique current-UID PIDs')
    listed_seconds = time.monotonic()-started
    own_seen, disappeared, paths = False, 0, []
    name_queries, path_lookups, name_seconds, path_seconds = 0, 0, 0.0, 0.0
    marker = '/Library/Developer/CoreSimulator/Devices/'+simulator+'/data/Containers/Bundle/Application/'
    for pid in pids:
        if pid == 0: continue  # Public libproc may include the kernel/sentinel PID; never inspect it.
        # proc_name reads public BSD process metadata. Resolve expensive native
        # executable paths only for Parlor candidates, not every runner service.
        # A name is a filter, NEVER ownership: device/bundle/image bytes still
        # govern admission in NativeSampler._discover. No unrelated names retained.
        name = ctypes.create_string_buffer(64)
        ctypes.set_errno(0)
        query_started = time.monotonic()
        name_count = proc.proc_name(pid, name, len(name))
        name_seconds += time.monotonic()-query_started
        name_queries += 1
        error = ctypes.get_errno()
        if name_count == 0 and error in (errno.ESRCH, errno.ENOENT):
            disappeared += 1
            continue
        if not 0 < name_count < len(name) or name_count != len(name.value):
            raise ValueError('Complete live own-UID native process name required')
        if pid == os.getpid(): own_seen = True
        if name.value != b'Parlor': continue
        buffer = ctypes.create_string_buffer(4096)
        ctypes.set_errno(0)
        query_started = time.monotonic()
        count = proc.proc_pidpath(pid, buffer, len(buffer))
        path_seconds += time.monotonic()-query_started
        path_lookups += 1
        error = ctypes.get_errno()
        if count == 0 and error in (errno.ESRCH, errno.ENOENT):
            disappeared += 1
            continue
        if not 0 < count < len(buffer):
            raise ValueError('Live own-UID native executable path unavailable')
        path = os.fsdecode(buffer.value)
        if not path.startswith('/') or '\n' in path or '\r' in path:
            raise ValueError('One absolute native image path required')
        if marker in path and path.endswith('/Parlor.app/Parlor'):
            paths.append({'pid': pid, 'image': path})
    if not own_seen or len(paths) > 1:
        raise ValueError('Actual self capability and at most one owned app')
    return {'schema': 1, 'selector': 'PROC_UID_ONLY', 'simulator': simulator,
            'own_process_seen': own_seen, 'enumerated': len(pids), 'disappeared': disappeared, 'paths': paths,
            'name_prefilter': 'Parlor', 'name_queries': name_queries, 'path_lookups': path_lookups,
            'collection_seconds': time.monotonic()-started, 'list_seconds': listed_seconds,
            'name_seconds': name_seconds, 'path_seconds': path_seconds}


def load_proc():
    if sys.platform != 'darwin' or ctypes.sizeof(ctypes.c_void_p) != 8:
        raise ValueError('Reviewed 64-bit Apple host only')
    proc = ctypes.CDLL('/usr/lib/libproc.dylib', use_errno=True)
    proc.proc_listpids.argtypes = [ctypes.c_uint32, ctypes.c_uint32, ctypes.c_void_p, ctypes.c_int]
    proc.proc_listpids.restype = ctypes.c_int
    proc.proc_pidpath.argtypes = [ctypes.c_int, ctypes.c_void_p, ctypes.c_uint32]
    proc.proc_pidpath.restype = ctypes.c_int
    proc.proc_name.argtypes = [ctypes.c_int, ctypes.c_void_p, ctypes.c_uint32]
    proc.proc_name.restype = ctypes.c_int
    return proc


def serve(proc, simulator, stream, emit):
    # One owned host child starts BEFORE XCTest, then accepts only finite serial
    # discovery requests. No interpreter/process startup in the observed window.
    emit(dict(kind='ready', helper_pid=os.getpid(), **collect(proc, simulator)))
    for index in range(1, 3601):
        request = stream.readline(10)
        if request == b'': return
        if request != b'DISCOVER\n': raise ValueError('One exact bounded discovery request')
        emit(dict(kind='paths', index=index, helper_pid=os.getpid(), **collect(proc, simulator)))
    if stream.readline(10) != b'': raise ValueError('Finite serial discovery budget')


if __name__ == '__main__':
    try:
        if len(sys.argv) == 3 and sys.argv[2] == '--serve':
            serve(load_proc(), sys.argv[1], sys.stdin.buffer,
                  lambda value: print(json.dumps(value, sort_keys=True), flush=True))
        elif len(sys.argv) == 2:
            print(json.dumps(collect(load_proc(), sys.argv[1]), sort_keys=True))
        else: raise ValueError('One exact owned Simulator and explicit server mode only')
    except BaseException as error:
        print(json.dumps({'schema': 1, 'error_type': type(error).__name__}))
        sys.exit(1)
