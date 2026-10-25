"""Private Windows command owner; never starts a tool outside its kill-on-close job.

The supervisor owns the sole (noninheritable) Job Object handle. Terminating it,
including a runner timeout, closes that handle and terminates its descendants.
Assigning the supervisor BEFORE spawning avoids the assign-after-launch race.
No breakaway flag is enabled. Nested jobs require Windows 8+, including supported
Windows CI hosts. A restrictive outer job fails closed before any tool starts.
"""
from __future__ import annotations

import ctypes
from ctypes import wintypes
import os
import subprocess
import sys


class BasicLimits(ctypes.Structure):
    _fields_ = [("PerProcessUserTimeLimit", ctypes.c_int64), ("PerJobUserTimeLimit", ctypes.c_int64),
                ("LimitFlags", wintypes.DWORD), ("MinimumWorkingSetSize", ctypes.c_size_t),
                ("MaximumWorkingSetSize", ctypes.c_size_t), ("ActiveProcessLimit", wintypes.DWORD),
                ("Affinity", ctypes.c_size_t), ("PriorityClass", wintypes.DWORD), ("SchedulingClass", wintypes.DWORD)]


class IoCounters(ctypes.Structure):
    _fields_ = [(name, ctypes.c_uint64) for name in ("ReadOperationCount", "WriteOperationCount", "OtherOperationCount",
                                                  "ReadTransferCount", "WriteTransferCount", "OtherTransferCount")]


class ExtendedLimits(ctypes.Structure):
    _fields_ = [("BasicLimitInformation", BasicLimits), ("IoInfo", IoCounters),
                ("ProcessMemoryLimit", ctypes.c_size_t), ("JobMemoryLimit", ctypes.c_size_t),
                ("PeakProcessMemoryUsed", ctypes.c_size_t), ("PeakJobMemoryUsed", ctypes.c_size_t)]


def own_job(kernel):
    kernel.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
    kernel.CreateJobObjectW.restype = wintypes.HANDLE
    kernel.SetInformationJobObject.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD]
    kernel.SetInformationJobObject.restype = wintypes.BOOL
    kernel.GetCurrentProcess.argtypes = []
    kernel.GetCurrentProcess.restype = wintypes.HANDLE
    kernel.AssignProcessToJobObject.argtypes = [wintypes.HANDLE, wintypes.HANDLE]
    kernel.AssignProcessToJobObject.restype = wintypes.BOOL
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    kernel.CloseHandle.restype = wintypes.BOOL
    job = kernel.CreateJobObjectW(None, None)  # NULL security attributes: handle cannot be inherited.
    if not job:
        raise RuntimeError("Cannot create isolated Windows command job")
    limits = ExtendedLimits()
    limits.BasicLimitInformation.LimitFlags = 0x00002000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
    if not kernel.SetInformationJobObject(job, 9, ctypes.byref(limits), ctypes.sizeof(limits)):
        kernel.CloseHandle(job)
        raise RuntimeError("Cannot configure isolated Windows command job")
    if not kernel.AssignProcessToJobObject(job, kernel.GetCurrentProcess()):
        kernel.CloseHandle(job)
        raise RuntimeError("Cannot enter isolated Windows command job")
    return job  # Keep open until OS process teardown; closing it here would kill this supervisor.


def main(command: list[str]) -> int:
    if sys.platform != "win32" or not command:
        raise RuntimeError("Windows command supervisor needs a Windows host and command")
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    job = own_job(kernel)
    # Keep the handle live until os._exit; the OS then closes it and kills any
    # remaining child, including after successful early exit of the root tool.
    return subprocess.call(command)


if __name__ == "__main__":
    try:
        status = main(sys.argv[1:])
    except BaseException:
        # No command, environment, path, or raw native exception in diagnostics.
        status = 125
    os._exit(status if status < 0x80000000 else status - 0x100000000)
