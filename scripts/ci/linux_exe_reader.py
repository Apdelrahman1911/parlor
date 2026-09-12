#!/usr/bin/env python3
"""Fixed sudo-only reader: one caller-UID /proc lifetime, never a process scan.

Only stat/status and two exe link/stat observations are read. No target argv,
environment, file contents, process control, or caller-supplied paths/commands.
The sole environment input, sudo's SUDO_UID, authenticates the original caller.
The exact ordered target UID tuple is a lifetime constraint, not ownership.
"""
from __future__ import annotations

import json
import os
import re
import signal
import stat
import sys

MAX_RECORD_BYTES = 8192
MAX_RESPONSE_BYTES = 65536
SECONDS = 1.0


class ReaderError(RuntimeError):
    """Fixed public error code, never an exception message or target path."""


def decimal(value: str, lower: int, upper: int) -> int:
    if (not isinstance(value, str) or len(value) > 20 or
            re.fullmatch(r"0|[1-9][0-9]*", value) is None or not lower <= int(value) < upper):
        raise ReaderError("INVALID_REQUEST")
    return int(value)


def identity(directory: int, pid: int) -> tuple[int, tuple[int, ...]]:
    def record(name: str) -> bytes:
        descriptor = os.open(name, os.O_RDONLY | os.O_CLOEXEC | os.O_NOFOLLOW, dir_fd=directory)
        with os.fdopen(descriptor, "rb") as stream:
            value = stream.read(MAX_RECORD_BYTES + 1)
        if len(value) > MAX_RECORD_BYTES:
            raise ReaderError("PROC_RECORD_LIMIT")
        return value

    # Same bounded stat/starttime and four-UID grammar as verification_hygiene.
    prefix, separator, tail = record("stat").rpartition(b") ")
    fields = tail.split()
    if (not separator or not prefix.startswith(str(pid).encode("ascii") + b" (") or
            len(fields) < 20 or re.fullmatch(rb"[RSDZTWtXxKPI]", fields[0]) is None or
            not fields[19].isdigit() or not 0 <= int(fields[19]) < 2**64):
        raise ReaderError("PROC_STAT_INVALID")
    lines = [line for line in record("status").splitlines() if line.startswith(b"Uid:")]
    match = re.fullmatch(rb"Uid:\s+([0-9]+)\s+([0-9]+)\s+([0-9]+)\s+([0-9]+)\s*", lines[0]) if len(lines) == 1 else None
    if not match or any(int(value) >= 2**32 for value in match.groups()):
        raise ReaderError("PROC_UID_INVALID")
    return int(fields[19]), tuple(int(value) for value in match.groups())


def read_executable(uid: int, pid: int, starttime: int, uids: tuple[int, ...]) -> dict:
    if (sys.platform != "linux" or os.getuid() != 0 or os.geteuid() != 0 or
            type(uid) is not int or not 0 < uid < 2**32 or
            type(pid) is not int or not 0 < pid < 2**31 or
            type(starttime) is not int or not 0 <= starttime < 2**64 or
            type(uids) is not tuple or len(uids) != 4 or
            any(type(value) is not int or not 0 <= value < 2**32 for value in uids) or uid not in uids or
            decimal(os.environ.get("SUDO_UID", ""), 1, 2**32) != uid):
        raise ReaderError("CALLER_UID_MISMATCH")
    flags = os.O_RDONLY | os.O_DIRECTORY | os.O_CLOEXEC | os.O_NOFOLLOW
    proc = os.open("/proc", flags)
    try:
        directory = os.open(str(pid), flags, dir_fd=proc)
    finally:
        os.close(proc)
    try:
        expected = (starttime, uids)
        if identity(directory, pid) != expected:
            raise ReaderError("PROC_LIFETIME_OR_UID_CHANGED")
        observations = []
        for _ in range(2):
            path = os.readlink("exe", dir_fd=directory)
            metadata = os.stat("exe", dir_fd=directory, follow_symlinks=True)
            observations.append({"path": path, "device": metadata.st_dev,
                                 "inode": metadata.st_ino, "mode": metadata.st_mode})
        if identity(directory, pid) != expected:
            raise ReaderError("PROC_LIFETIME_OR_UID_CHANGED")
        first, last = observations
        if (first != last or not stat.S_ISREG(first["mode"]) or not first["path"].startswith("/") or
                len(os.fsencode(first["path"])) > 4096 or first["path"].endswith(" (deleted)")):
            raise ReaderError("PROC_EXECUTABLE_AMBIGUOUS")
        return {"schema": 2, "uid": uid, "pid": pid, "starttime": starttime, "uids": list(uids), "observations": observations}
    finally:
        os.close(directory)


def expired(_signal, _frame) -> None:
    raise ReaderError("READER_DEADLINE")


def main() -> int:
    # A parent timeout on sudo alone does not bound its privileged child.
    # This timer addresses only this helper; it never signals a target process.
    signal.signal(signal.SIGALRM, expired)
    signal.setitimer(signal.ITIMER_REAL, SECONDS)
    try:
        if len(sys.argv) != 8:
            raise ReaderError("INVALID_REQUEST")
        values = [decimal(value, lower, upper) for value, lower, upper in
                  zip(sys.argv[1:], (1, 1, 0, 0, 0, 0, 0), (2**32, 2**31, 2**64, 2**32, 2**32, 2**32, 2**32))]
        result = read_executable(*values[:3], tuple(values[3:]))
        encoded = json.dumps(result, ensure_ascii=True).encode("ascii") + b"\n"
        if len(encoded) > MAX_RESPONSE_BYTES:
            raise ReaderError("RESPONSE_LIMIT")
        sys.stdout.buffer.write(encoded)
        sys.stdout.buffer.flush()
        return 0
    except (OSError, RuntimeError, ValueError, TypeError) as error:
        # Parent treats every nonzero status as failure; never serialize raw errors.
        print(json.dumps({"error": str(error) if isinstance(error, ReaderError) else type(error).__name__}))
        return 1
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)


if __name__ == "__main__":
    raise SystemExit(main())
