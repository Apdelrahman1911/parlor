"""Read/stop attested Xcode workers on the isolated macOS ARM64 CI host.

The process identity/audit-token ABI is the reviewed campaign implementation,
reduced to the operations needed here. No PID/PGID kill fallback, process launch,
or native-library call occurs on import. Process environments are never logged.
"""
from __future__ import annotations

import ctypes
import errno
import os
import platform
import struct
import time
from pathlib import Path
from typing import NamedTuple

MAX_PROCESSES = 4096
PROC_UID_ONLY = 4  # Public proc_listpids selector for effective UID.
MAX_ARGUMENT_BYTES = 1024 * 1024  # ARG_MAX payload excludes the leading argc word.
ARGC_BYTES = struct.calcsize("=i")
MAX_ARGUMENT_RECORD_BYTES = MAX_ARGUMENT_BYTES + ARGC_BYTES
TRACKING_KEY = b"RUNNER_TRACKING_ID="


class ProcessIdentity(NamedTuple):
    pid: int
    uid: int
    started: tuple[int, int]
    command: str
    token: tuple[int, ...]

    @property
    def lifetime(self) -> tuple:
        return self.pid, self.uid, self.started


class ProcBsdInfo(ctypes.Structure):
    _fields_ = [(name, ctypes.c_uint32) for name in (
        "flags", "status", "xstatus", "pid", "ppid", "uid", "gid", "ruid",
        "rgid", "svuid", "svgid", "reserved",
    )] + [("comm", ctypes.c_char * 16), ("name", ctypes.c_char * 32)] + [
        (name, ctypes.c_uint32) for name in ("nfiles", "pgid", "pjobc", "tdev", "tpgid")
    ] + [("nice", ctypes.c_int32), ("start_sec", ctypes.c_uint64), ("start_usec", ctypes.c_uint64)]


def argument_record_capacity(argument_bytes: int) -> int:
    if not ARGC_BYTES <= argument_bytes <= MAX_ARGUMENT_BYTES:
        raise RuntimeError("Unsupported bounded process-argument capacity")
    return argument_bytes + ARGC_BYTES


def parse_tracking_id(data: bytes) -> str | None:
    """Parse KERN_PROCARGS2's argc/path/argv/NUL-separated environment, not ps text."""
    if not ARGC_BYTES + 1 <= len(data) <= MAX_ARGUMENT_RECORD_BYTES or not data.endswith(b"\0"):
        raise RuntimeError("Invalid bounded process-argument record")
    argc = struct.unpack_from("=i", data)[0]
    if not 1 <= argc <= 4096:
        raise RuntimeError("Invalid process-argument count")
    try:
        offset = data.index(b"\0", ARGC_BYTES) + 1  # Executable path, followed by NUL padding.
        while offset < len(data) and data[offset] == 0:
            offset += 1
        for _ in range(argc):
            offset = data.index(b"\0", offset) + 1
    except ValueError as error:
        raise RuntimeError("Truncated process-argument record") from error
    values = [item[len(TRACKING_KEY):] for item in data[offset:].split(b"\0")
              if item.startswith(TRACKING_KEY)]
    if len(values) > 1:
        raise RuntimeError("Ambiguous process tracking marker")
    if not values:
        return None
    try:
        return values[0].decode("ascii")
    except UnicodeError as error:
        raise RuntimeError("Invalid process tracking marker") from error


class DarwinWorkerBackend:
    def __init__(self) -> None:
        if platform.system() != "Darwin" or platform.machine() != "arm64":
            raise RuntimeError("Native cleanup requires the reviewed macOS ARM64 CI host")
        if ctypes.sizeof(ProcBsdInfo) != 136:
            raise RuntimeError("Unexpected public Darwin process ABI")
        self.lib = ctypes.CDLL("/usr/lib/libSystem.B.dylib", use_errno=True)
        self.proc = ctypes.CDLL("/usr/lib/libproc.dylib", use_errno=True)
        self.self_port = ctypes.c_uint32.in_dll(self.lib, "mach_task_self_").value
        self.proc.proc_pidinfo.argtypes = [ctypes.c_int, ctypes.c_int, ctypes.c_uint64,
                                          ctypes.c_void_p, ctypes.c_int]
        self.proc.proc_pidinfo.restype = ctypes.c_int
        self.proc.proc_pidpath.argtypes = [ctypes.c_int, ctypes.c_void_p, ctypes.c_uint32]
        self.proc.proc_pidpath.restype = ctypes.c_int
        self.proc.proc_listpids.argtypes = [ctypes.c_uint32, ctypes.c_uint32,
                                           ctypes.c_void_p, ctypes.c_int]
        self.proc.proc_listpids.restype = ctypes.c_int
        self.lib.task_name_for_pid.argtypes = [ctypes.c_uint32, ctypes.c_int,
                                              ctypes.POINTER(ctypes.c_uint32)]
        self.lib.task_name_for_pid.restype = ctypes.c_int
        self.lib.task_info.argtypes = [ctypes.c_uint32, ctypes.c_int, ctypes.c_void_p,
                                     ctypes.POINTER(ctypes.c_uint32)]
        self.lib.task_info.restype = ctypes.c_int
        self.lib.mach_port_deallocate.argtypes = [ctypes.c_uint32, ctypes.c_uint32]
        self.lib.mach_port_deallocate.restype = ctypes.c_int
        self.proc.proc_signal_with_audittoken.argtypes = [ctypes.c_void_p, ctypes.c_int]
        self.proc.proc_signal_with_audittoken.restype = ctypes.c_int
        self.lib.sysctl.argtypes = [ctypes.POINTER(ctypes.c_int), ctypes.c_uint32,
                                   ctypes.c_void_p, ctypes.POINTER(ctypes.c_size_t),
                                   ctypes.c_void_p, ctypes.c_size_t]
        self.lib.sysctl.restype = ctypes.c_int
        # KERN_PROCARGS2 rejects a buffer larger than ARG_MAX (+ its argc word).
        self.argument_capacity = argument_record_capacity(os.sysconf("SC_ARG_MAX"))
        own = self.read(os.getpid())
        if own is None or own.uid != os.getuid() or own.token[5] != os.getpid():
            raise RuntimeError("Native identity capability check failed")

    def _basic(self, pid: int):
        info = ProcBsdInfo()
        ctypes.set_errno(0)
        size = self.proc.proc_pidinfo(pid, 3, 0, ctypes.byref(info), ctypes.sizeof(info))
        error = ctypes.get_errno()
        if size == 0 and error in (errno.ESRCH, errno.ENOENT):
            return None
        if size != ctypes.sizeof(info) or info.pid != pid:
            raise RuntimeError(
                "Cannot read complete native process identity "
                f"(pid={pid}, size={size}, expected={ctypes.sizeof(info)}, "
                f"returned_pid={info.pid}, errno={error})"
            )
        return None if info.status == 5 else info

    def _path(self, pid: int) -> str:
        buffer = ctypes.create_string_buffer(4096)
        size = self.proc.proc_pidpath(pid, buffer, len(buffer))
        if not 0 < size < len(buffer):
            raise RuntimeError("Cannot attest native worker executable")
        return os.fsdecode(buffer.value)

    def _token(self, pid: int):
        for attempt in range(3):
            port = ctypes.c_uint32()
            result = self.lib.task_name_for_pid(self.self_port, pid, ctypes.byref(port))
            if result == 0 and port.value:
                break
            if self._basic(pid) is None:
                return None
            if result != 0 or attempt == 2:
                raise RuntimeError("Cannot acquire a live worker task-name port")
            # XNU can return success + MACH_PORT_NULL on task teardown/copyout.
            # NULL does not prove exit. Retry only this result, never denial.
            time.sleep(0.01)
        try:
            token, count = (ctypes.c_uint32 * 8)(), ctypes.c_uint32(8)
            result = self.lib.task_info(port.value, 15, token, ctypes.byref(count))
            if result != 0:
                if self._basic(pid) is None:
                    return None
                raise RuntimeError("Cannot attest live worker audit token")
            if count.value != 8 or token[5] != pid:
                raise RuntimeError("Invalid worker audit token")
            return tuple(token)
        finally:
            if self.lib.mach_port_deallocate(self.self_port, port.value) != 0:
                raise RuntimeError("Task-name port deallocation failed")

    def read(self, pid: int) -> ProcessIdentity | None:
        for _ in range(3):
            before = self._basic(pid)
            if before is None:
                return None
            if before.uid != os.getuid():
                raise RuntimeError("Refusing another user's worker")
            token = self._token(pid)
            if token is None:
                return None
            try:
                command = self._path(pid)
            except RuntimeError:
                if self._basic(pid) is None:
                    return None
                raise
            after = self._basic(pid)
            if after is None:
                return None
            later = self._token(pid)
            if later is None:
                return None
            if ((before.start_sec, before.start_usec, before.uid, before.comm) !=
                    (after.start_sec, after.start_usec, after.uid, after.comm) or token != later):
                continue
            return ProcessIdentity(pid, after.uid, (after.start_sec, after.start_usec), command, token)
        raise RuntimeError("Unstable native worker identity; no signal authorized")

    def _pids(self) -> list[int]:
        # PROC_PIDTBSDINFO requires same-user privilege. Select effective UID in
        # the kernel before requesting BSD identity; never scan unrelated users
        # then treat their expected EPERM as missing/cleaned task workers.
        values = (ctypes.c_int * (MAX_PROCESSES + 1))()
        uid, pid_bytes = os.geteuid(), ctypes.sizeof(ctypes.c_int)
        ctypes.set_errno(0)
        size = self.proc.proc_listpids(PROC_UID_ONLY, uid, values, ctypes.sizeof(values))
        error = ctypes.get_errno()
        # proc_listpids returns BYTES, unlike proc_listallpids. One spare entry
        # makes a full/truncated buffer fail instead of omitting unknown workers.
        if not pid_bytes <= size <= MAX_PROCESSES * pid_bytes or size % pid_bytes:
            raise RuntimeError(
                "Cannot obtain bounded native worker inventory "
                f"(uid={uid}, bytes={size}, capacity={ctypes.sizeof(values)}, errno={error})"
            )
        pids = list(values[:size // pid_bytes])
        if any(pid < 0 for pid in pids) or len(set(pids)) != len(pids):
            raise RuntimeError("Invalid native process inventory records")
        return [pid for pid in pids if pid > 0 and pid != os.getpid()]

    def lifetimes(self) -> list[tuple]:
        result = []
        for pid in self._pids():
            info = self._basic(pid)
            if info is not None and info.uid == os.getuid():
                result.append((pid, info.uid, (info.start_sec, info.start_usec)))
        return result

    def snapshot(self, application: Path, baseline: set[tuple]) -> list[ProcessIdentity]:
        result = []
        for pid in self._pids():
            if pid <= 0 or pid == os.getpid():
                continue
            info = self._basic(pid)
            if info is None or info.uid != os.getuid():
                continue
            if (pid, info.uid, (info.start_sec, info.start_usec)) in baseline:
                continue
            try:
                path = Path(self._path(pid))
            except RuntimeError:
                if self._basic(pid) is None:
                    continue
                raise
            if application not in path.parents:
                continue
            identity = self.read(pid)
            if identity is not None and application in Path(identity.command).parents:
                result.append(identity)
        return result

    def tracking_id(self, identity: ProcessIdentity) -> str | None:
        if self.read(identity.pid) != identity:
            raise RuntimeError("Worker changed before tracking attestation")
        mib = (ctypes.c_int * 3)(1, 49, identity.pid)  # CTL_KERN, KERN_PROCARGS2.
        buffer = ctypes.create_string_buffer(self.argument_capacity)
        size = ctypes.c_size_t(self.argument_capacity)
        if self.lib.sysctl(mib, 3, buffer, ctypes.byref(size), None, 0) != 0:
            raise RuntimeError("Cannot attest worker tracking marker")
        if not 0 < size.value <= self.argument_capacity:
            raise RuntimeError("Invalid bounded worker environment")
        tracking = parse_tracking_id(buffer.raw[:size.value])
        if self.read(identity.pid) != identity:
            raise RuntimeError("Worker changed during tracking attestation")
        return tracking

    def signal(self, identity: ProcessIdentity, signum: int) -> bool:
        if signum not in (15, 9):
            raise RuntimeError("Unsupported worker cleanup signal")
        current = self.read(identity.pid)
        if current is None:
            return False
        if current != identity:
            raise RuntimeError("Worker identity changed; no signal authorized")
        token = (ctypes.c_uint32 * 8)(*identity.token)
        result = self.proc.proc_signal_with_audittoken(token, signum)
        if result not in (0, errno.ESRCH):
            raise RuntimeError("Audit-token-bound worker signal failed")
        return result == 0
