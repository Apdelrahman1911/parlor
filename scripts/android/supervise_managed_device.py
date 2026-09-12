#!/usr/bin/env python3
"""Retire only descendants of one Linux managed-device build, including daemons.

AGP 8.13.2 can return after requesting emulator exit without waiting for it.
Linux child-subreaper adoption keeps that build's detached helpers ours even
after their parents exit. Never select processes by name, UID, SDK path, or age.
This single-threaded program is the sole reaper of its children.
"""
from __future__ import annotations

import argparse
import ctypes
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time

TERM_SECONDS = 10.0
KILL_SECONDS = 5.0
POLL_SECONDS = 0.05
MAX_CHILDREN = 4096


def enable_subreaper() -> None:
    if (sys.platform != "linux" or not hasattr(os, "pidfd_open") or
            not hasattr(signal, "pidfd_send_signal")):
        raise RuntimeError("LINUX_PIDFD_SUPPORT_REQUIRED")
    # Python exposing the APIs does not prove kernel/seccomp admission. Probe
    # only ourselves (signal 0 has no effect) before allocating build workers.
    descriptor = os.pidfd_open(os.getpid())
    try:
        signal.pidfd_send_signal(descriptor, 0)
    finally:
        os.close(descriptor)
    libc = ctypes.CDLL(None, use_errno=True)
    prctl = libc.prctl
    prctl.restype = ctypes.c_int
    prctl.argtypes = [ctypes.c_int, ctypes.c_ulong, ctypes.c_ulong,
                     ctypes.c_ulong, ctypes.c_ulong]
    if prctl(36, 1, 0, 0, 0) != 0:  # PR_SET_CHILD_SUBREAPER
        raise OSError(ctypes.get_errno(), "SUBREAPER_ENABLE_FAILED")
    enabled = ctypes.c_int()
    if prctl(37, ctypes.addressof(enabled), 0, 0, 0) != 0 or enabled.value != 1:
        raise RuntimeError("SUBREAPER_READBACK_FAILED")


def owned_children() -> list[int]:
    # This kernel list contains only children of this thread, not other SDK or
    # runner processes. It is a discovery hint; waitpid below proves ownership.
    path = Path(f"/proc/self/task/{os.getpid()}/children")
    with path.open("rb") as stream:
        data = stream.read(MAX_CHILDREN * 12 + 1)
    fields = data.split()
    if (len(data) > MAX_CHILDREN * 12 or len(fields) > MAX_CHILDREN or
            any(not value.isdigit() or not 0 < int(value) < 2**31 for value in fields)):
        raise RuntimeError("CHILD_ENUMERATION_LIMIT_OR_FORMAT")
    return [int(value) for value in fields]


def signal_owned_child(pid: int, number: int) -> str:
    descriptor = os.pidfd_open(pid)
    try:
        # SIGCHLD is DFL and no other thread reaps: an exited child retains its
        # PID until this call. ECHILD refuses signalling a foreign/reused PID.
        waited, _ = os.waitpid(pid, os.WNOHANG)
        if waited == pid:
            return "reaped"
        if waited != 0:
            raise RuntimeError("CHILD_OWNERSHIP_UNPROVEN")
        try:
            signal.pidfd_send_signal(descriptor, number)
        except ProcessLookupError:
            return "exiting"  # Next reap verifies exit; no delivered signal claim.
        return "signalled"
    finally:
        os.close(descriptor)


def retire_owned_children(*, term_seconds: float = TERM_SECONDS,
                          kill_seconds: float = KILL_SECONDS) -> dict:
    receipt = {"result": "FAIL", "term_signals": 0, "kill_signals": 0,
               "reaped": 0, "remaining": [], "errors": []}
    sent: dict[int, set[int]] = {signal.SIGTERM: set(), signal.SIGKILL: set()}

    def reap() -> bool:
        while True:
            try:
                pid, _ = os.waitpid(-1, os.WNOHANG)
            except ChildProcessError:
                return True  # ECHILD, not an empty/racy /proc sample, proves exit.
            if pid == 0:
                return False
            receipt["reaped"] += 1
            for values in sent.values():
                values.discard(pid)

    try:
        for number, seconds, counter in ((signal.SIGTERM, term_seconds, "term_signals"),
                                         (signal.SIGKILL, kill_seconds, "kill_signals")):
            deadline = time.monotonic() + seconds
            while True:
                if reap():
                    receipt["result"] = "FAIL" if receipt["errors"] else "PASS"
                    return receipt
                # Re-enumerate after every parent exit so late-forked/adopted
                # grandchildren are covered, including helpers that setsid().
                for pid in owned_children():
                    if pid in sent[number]:
                        continue
                    sent[number].add(pid)
                    try:
                        outcome = signal_owned_child(pid, number)
                        if outcome == "reaped":
                            receipt["reaped"] += 1
                            for values in sent.values():
                                values.discard(pid)
                        elif outcome == "signalled":
                            receipt[counter] += 1
                    except (OSError, RuntimeError) as error:
                        code = f"CHILD_SIGNAL_FAILED:{type(error).__name__}"
                        if code not in receipt["errors"]:
                            receipt["errors"].append(code)
                if time.monotonic() >= deadline:
                    break
                time.sleep(POLL_SECONDS)
        if reap():
            receipt["result"] = "FAIL" if receipt["errors"] else "PASS"
        else:
            receipt["remaining"] = owned_children()
            receipt["errors"].append("OWNED_CHILDREN_DID_NOT_EXIT")
    except (OSError, RuntimeError) as error:
        receipt["errors"].append(f"CHILD_RETIREMENT_FAILED:{type(error).__name__}")
    return receipt


def supervise(command: list[str], receipt_path: Path) -> int:
    receipt = {"schema": 1, "runtime_exit_code": None, "interrupted_signal": None,
               "cleanup": {"result": "NOT_STARTED"}, "result": "FAIL", "errors": [],
               "scope": "Only direct/adopted descendants of this Linux build supervisor; "
                        "not preexisting services, other jobs, global emulator absence, or AVD deletion"}
    # Refuse to replace another run's evidence before starting any work.
    receipt_path.parent.mkdir(parents=True, exist_ok=True)
    with receipt_path.open("x", encoding="utf-8") as output:
        started = False
        process = None
        previous = {}

        def interrupted(number, _frame) -> None:
            if receipt["interrupted_signal"] is None:
                receipt["interrupted_signal"] = number

        try:
            if owned_children():
                raise RuntimeError("PREEXISTING_CHILDREN_REFUSED")
            # Reset inherited SIG_IGN/SA_NOCLDWAIT before any child starts. The
            # pidfd+waitpid ownership proof requires children not auto-reaped.
            previous[signal.SIGCHLD] = signal.signal(signal.SIGCHLD, signal.SIG_DFL)
            for number in (signal.SIGINT, signal.SIGTERM):
                previous[number] = signal.signal(number, interrupted)
            enable_subreaper()
            started = True
            if receipt["interrupted_signal"] is None:
                process = subprocess.Popen(command, start_new_session=True)
                while process.poll() is None and receipt["interrupted_signal"] is None:
                    time.sleep(POLL_SECONDS)
                receipt["runtime_exit_code"] = process.returncode
        except (OSError, RuntimeError, ValueError) as error:
            receipt["errors"].append({"code": type(error).__name__,
                                      "errno": getattr(error, "errno", None)})
        finally:
            if started:
                # Do not poll/wait the Popen concurrently with generic reaping.
                receipt["cleanup"] = retire_owned_children()
                if process is not None:
                    process.poll()  # Already reaped; prevents Popen destructor bookkeeping.
            runtime = receipt["runtime_exit_code"]
            if runtime not in (None, 0):
                status = runtime if runtime > 0 else 128 - runtime
            elif receipt["interrupted_signal"] is not None:
                status = 128 + receipt["interrupted_signal"]
            else:
                status = 0 if (runtime == 0 and receipt["cleanup"]["result"] == "PASS" and
                               not receipt["errors"]) else 1
            receipt["exit_code"] = status
            receipt["result"] = "PASS" if status == 0 else "FAIL"
            encoded = json.dumps(receipt, sort_keys=True) + "\n"
            try:
                output.write(encoded)
                output.flush()
                os.fsync(output.fileno())
            except OSError as error:
                status = status or 1
                receipt["exit_code"] = status
                receipt["result"] = "FAIL"
                receipt["errors"].append({"code": "RECEIPT_WRITE_FAILED", "errno": error.errno})
            finally:
                print(json.dumps(receipt, sort_keys=True), flush=True)
                for number, handler in previous.items():
                    signal.signal(number, handler)
    return status


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("a command is required after --")
    try:
        return supervise(command, args.receipt)
    except OSError as error:
        print(json.dumps({"result": "FAIL", "error": type(error).__name__,
                          "errno": error.errno}), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
