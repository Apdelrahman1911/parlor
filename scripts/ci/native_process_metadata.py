#!/usr/bin/env python3
"""Bounded metadata-only observation, NOT a production ownership backend.

Run only as the probe's timed, owned child. No argv, task ports, signals, native
calls on import, or permission fallback. Global enumeration can be partial when
the kernel denies another UID; the receipt explicitly counts that limitation.
"""
from __future__ import annotations

import ctypes
import errno
import hashlib
import json
import os
import platform
import sys

from darwin_worker_identity import ProcBsdInfo

MAX_PROCESSES = 4096
FIELDS = ("pid", "ppid", "pgid", "uid", "start_sec", "start_usec", "status")
EXPECTED_OFFSETS = {"pid": 12, "ppid": 16, "uid": 20, "pgid": 100,
                    "start_sec": 120, "start_usec": 128}


def collect(proc):
    offsets = {name: getattr(ProcBsdInfo, name).offset for name in EXPECTED_OFFSETS}
    if ctypes.sizeof(ProcBsdInfo) != 136 or offsets != EXPECTED_OFFSETS:
        raise RuntimeError("unsupported-bsd-metadata-layout")
    values = (ctypes.c_int * (MAX_PROCESSES + 1))()
    width = ctypes.sizeof(ctypes.c_int)
    ctypes.set_errno(0)
    count = proc.proc_listpids(1, 0, values, ctypes.sizeof(values))  # PROC_ALL_PIDS, bytes.
    if not width <= count <= MAX_PROCESSES * width or count % width:
        raise RuntimeError("incomplete-or-unbounded-global-pid-enumeration")
    pids = list(values[:count // width])
    if any(pid < 0 for pid in pids) or len(set(pids)) != len(pids):
        raise RuntimeError("invalid-global-pid-enumeration")
    rows, errors, own = [], {}, None
    for pid in pids:
        if pid == 0:
            continue
        info = ProcBsdInfo()
        ctypes.set_errno(0)
        size = proc.proc_pidinfo(pid, 3, 0, ctypes.byref(info), ctypes.sizeof(info))  # PROC_PIDTBSDINFO.
        error = ctypes.get_errno()
        if size != ctypes.sizeof(info):
            kind = ("disappeared" if size == 0 and error in (errno.ESRCH, errno.ENOENT) else
                    "denied" if size == 0 and error in (errno.EPERM, errno.EACCES) else "other_or_short")
            key = kind + ":" + str(error)
            errors[key] = errors.get(key, 0) + 1
            continue
        if info.pid != pid or not info.start_sec or info.start_usec >= 1_000_000:
            raise RuntimeError("inconsistent-bsd-metadata-result")
        row = {name: int(getattr(info, name)) for name in FIELDS}
        rows.append(row)
        if pid == os.getpid():
            own = row
    if own is None or (own["uid"], own["ppid"], own["pgid"]) != (os.geteuid(), os.getppid(), os.getpgid(0)):
        raise RuntimeError("self-bsd-metadata-observation-mismatch")
    return dict(schema_version=1, kind="LIBPROC_METADATA_OBSERVATION_ONLY", struct_size=136,
                field_offsets=offsets, selector="PROC_ALL_PIDS", enumerated=len(pids),
                observed=len(rows), error_counts=errors, complete=not errors,
                own_process=own, metadata_sha256=hashlib.sha256(json.dumps(
                    sorted(rows, key=lambda row: row["pid"]), separators=(",", ":")).encode()).hexdigest(),
                scope="Metadata only; denied/disappeared rows are not missing-worker proof. No argv, task ports, adoption or signal.")


def main():
    if sys.argv[1:] != ["observe"] or platform.system() != "Darwin" or platform.machine() != "arm64":
        raise RuntimeError("explicit-arm64-macos-metadata-probe-required")
    proc = ctypes.CDLL("/usr/lib/libproc.dylib", use_errno=True)
    proc.proc_listpids.argtypes = [ctypes.c_uint32, ctypes.c_uint32, ctypes.c_void_p, ctypes.c_int]
    proc.proc_listpids.restype = ctypes.c_int
    proc.proc_pidinfo.argtypes = [ctypes.c_int, ctypes.c_int, ctypes.c_uint64, ctypes.c_void_p, ctypes.c_int]
    proc.proc_pidinfo.restype = ctypes.c_int
    print(json.dumps(collect(proc), sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except BaseException as error:
        # No unexpected native exception text or process data reaches the log.
        print(json.dumps(dict(kind="LIBPROC_METADATA_OBSERVATION_ONLY", status="FAILED", error_type=type(error).__name__)))
        raise SystemExit(1)
