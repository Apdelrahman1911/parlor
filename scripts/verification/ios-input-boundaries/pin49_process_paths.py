"""Timed host child: public libproc paths for current UID, no argv/environment/AX."""
import ctypes
import errno
import json
import os
from pathlib import Path
import re
import sys

MAX_PROCESSES = 4096
UUID = re.compile(r'[A-F0-9]{8}(?:-[A-F0-9]{4}){3}-[A-F0-9]{12}')


def collect(proc, simulator):
    if not UUID.fullmatch(simulator) or ctypes.sizeof(ctypes.c_int) != 4:
        raise ValueError('Explicit owned Simulator and native int ABI')
    values = (ctypes.c_int * (MAX_PROCESSES+1))()
    width = ctypes.sizeof(ctypes.c_int)
    size = proc.proc_listpids(4, os.geteuid(), values, ctypes.sizeof(values))  # PROC_UID_ONLY; bytes.
    if not width <= size <= MAX_PROCESSES*width or size % width:
        raise ValueError('Complete bounded own-UID PID list required')
    pids = list(values[:size//width])
    if any(p < 0 for p in pids) or len(set(pids)) != len(pids):
        raise ValueError('Positive unique current-UID PIDs')
    own_seen, disappeared, paths = False, 0, []
    marker = '/Library/Developer/CoreSimulator/Devices/'+simulator+'/data/Containers/Bundle/Application/'
    for pid in pids:
        if pid == 0: continue  # Public libproc may include the kernel/sentinel PID; never inspect it.
        buffer = ctypes.create_string_buffer(4096)
        ctypes.set_errno(0)
        count = proc.proc_pidpath(pid, buffer, len(buffer))
        error = ctypes.get_errno()
        if count == 0 and error in (errno.ESRCH, errno.ENOENT):
            disappeared += 1
            continue
        if not 0 < count < len(buffer):
            raise ValueError('Live own-UID native executable path unavailable')
        path = os.fsdecode(buffer.value)
        if not path.startswith('/') or '\n' in path or '\r' in path:
            raise ValueError('One absolute native image path required')
        if pid == os.getpid(): own_seen = True
        if marker in path and path.endswith('/Parlor.app/Parlor'):
            paths.append({'pid': pid, 'image': path})
    if not own_seen or len(paths) > 1:
        raise ValueError('Actual self capability and at most one owned app')
    return {'schema': 1, 'selector': 'PROC_UID_ONLY', 'simulator': simulator,
            'own_process_seen': own_seen, 'enumerated': len(pids), 'disappeared': disappeared, 'paths': paths}


def load_proc():
    if sys.platform != 'darwin' or ctypes.sizeof(ctypes.c_void_p) != 8:
        raise ValueError('Reviewed 64-bit Apple host only')
    proc = ctypes.CDLL('/usr/lib/libproc.dylib', use_errno=True)
    proc.proc_listpids.argtypes = [ctypes.c_uint32, ctypes.c_uint32, ctypes.c_void_p, ctypes.c_int]
    proc.proc_listpids.restype = ctypes.c_int
    proc.proc_pidpath.argtypes = [ctypes.c_int, ctypes.c_void_p, ctypes.c_uint32]
    proc.proc_pidpath.restype = ctypes.c_int
    return proc


if __name__ == '__main__':
    try:
        if len(sys.argv) != 2: raise ValueError('One exact owned Simulator required')
        print(json.dumps(collect(load_proc(), sys.argv[1]), sort_keys=True))
    except BaseException as error:
        print(json.dumps({'schema': 1, 'error_type': type(error).__name__}))
        sys.exit(1)
