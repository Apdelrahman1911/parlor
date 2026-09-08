"""Campaign-only, Darwin26 owned-process tracking; never signals a numeric PID/PGID.

Public SDK ABI and exact-host XNU source receipts accompany this draft. Kernel
audit-token signaling checks the PID generation while holding the target proc
reference. Historical Popen objects cannot authorize recycled process groups.
Direct children stay unreaped via waitid(WNOWAIT) until descendant capture and
shutdown have finished. No ps/environment inspection or global process search.
"""
from __future__ import annotations

import ctypes
from dataclasses import asdict, dataclass
import errno
import os
import platform
import signal
import time


MAX_PROCESSES = 512
MAX_LAUNCHES = 256


class ProcBsdInfo(ctypes.Structure):
    _fields_ = [(name, ctypes.c_uint32) for name in (
        'flags', 'status', 'xstatus', 'pid', 'ppid', 'uid', 'gid', 'ruid',
        'rgid', 'svuid', 'svgid', 'reserved',
    )] + [('comm', ctypes.c_char * 16), ('name', ctypes.c_char * 32)] + [
        (name, ctypes.c_uint32) for name in ('nfiles', 'pgid', 'pjobc', 'tdev', 'tpgid')
    ] + [('nice', ctypes.c_int32), ('start_sec', ctypes.c_uint64), ('start_usec', ctypes.c_uint64)]


class SigInfo(ctypes.Structure):
    _fields_ = [(name, ctypes.c_int32) for name in ('signo', 'error', 'code', 'pid')]
    _fields_ += [('uid', ctypes.c_uint32), ('status', ctypes.c_int32),
                ('addr', ctypes.c_void_p), ('value', ctypes.c_void_p),
                ('band', ctypes.c_long), ('padding', ctypes.c_ulong * 7)]


@dataclass(frozen=True)
class Identity:
    pid: int
    parent: int
    group: int
    uid: int
    started: tuple[int, int]
    command: str
    token: tuple[int, ...]

    @property
    def lifetime(self):
        return self.pid, self.uid, self.started


class DarwinBackend:
    def __init__(self):
        if platform.system() != 'Darwin' or platform.machine() != 'arm64':
            raise RuntimeError('Only the reviewed Darwin ARM64 ABI is supported')
        if ctypes.sizeof(ProcBsdInfo) != 136 or ctypes.sizeof(SigInfo) != 104:
            raise RuntimeError('Unexpected public Darwin process ABI size')
        self.lib = ctypes.CDLL('/usr/lib/libSystem.B.dylib', use_errno=True)
        self.proc = ctypes.CDLL('/usr/lib/libproc.dylib', use_errno=True)
        self.self_port = ctypes.c_uint32.in_dll(self.lib, 'mach_task_self_').value
        self.proc.proc_pidinfo.argtypes = [ctypes.c_int, ctypes.c_int, ctypes.c_uint64,
                                          ctypes.c_void_p, ctypes.c_int]
        self.proc.proc_pidinfo.restype = ctypes.c_int
        self.proc.proc_pidpath.argtypes = [ctypes.c_int, ctypes.c_void_p, ctypes.c_uint32]
        self.proc.proc_pidpath.restype = ctypes.c_int
        for name in ('proc_listpgrppids', 'proc_listchildpids'):
            function = getattr(self.proc, name)
            function.argtypes = [ctypes.c_int, ctypes.c_void_p, ctypes.c_int]
            function.restype = ctypes.c_int
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
        self.lib.waitid.argtypes = [ctypes.c_int, ctypes.c_uint32,
                                   ctypes.POINTER(SigInfo), ctypes.c_int]
        self.lib.waitid.restype = ctypes.c_int
        # Capability/read-only sanity check before creating any task workers.
        own = self.read(os.getpid())
        if own is None or own.uid != os.getuid() or own.token[5] != os.getpid():
            raise RuntimeError('Cannot attest current process with public Darwin APIs')

    def _basic(self, pid):
        info = ProcBsdInfo()
        ctypes.set_errno(0)
        count = self.proc.proc_pidinfo(pid, 3, 0, ctypes.byref(info), ctypes.sizeof(info))
        if count == 0 and ctypes.get_errno() in (errno.ESRCH, errno.ENOENT):
            return None
        if count != ctypes.sizeof(info):
            raise OSError(ctypes.get_errno(), 'Incomplete PROC_PIDTBSDINFO')
        if info.pid != pid:
            raise RuntimeError('PID mismatch in process info')
        # Zombies need no signal and cannot obtain a task-name port.
        return None if info.status == 5 else info

    def _token(self, pid):
        port = ctypes.c_uint32()
        code = self.lib.task_name_for_pid(self.self_port, pid, ctypes.byref(port))
        if code != 0 or not port.value:
            if self._basic(pid) is None:
                return None
            raise RuntimeError(f'task_name_for_pid denied for attested candidate {pid}: {code}')
        try:
            token = (ctypes.c_uint32 * 8)()
            count = ctypes.c_uint32(8)
            code = self.lib.task_info(port.value, 15, token, ctypes.byref(count))
            if code != 0:
                if self._basic(pid) is None:
                    return None
                raise RuntimeError(f'TASK_AUDIT_TOKEN failed for candidate {pid}: {code}')
            if count.value != 8 or token[5] != pid:
                raise RuntimeError('Malformed kernel audit token')
            return tuple(token)
        finally:
            if self.lib.mach_port_deallocate(self.self_port, port.value) != 0:
                raise RuntimeError('Owned task-name port deallocation failed')

    def read(self, pid):
        for _ in range(3):
            before = self._basic(pid)
            if before is None:
                return None
            token = self._token(pid)
            if token is None:
                return None
            path = ctypes.create_string_buffer(4096)
            ctypes.set_errno(0)
            length = self.proc.proc_pidpath(pid, path, len(path))
            after = self._basic(pid)
            if after is None:
                return None
            later_token = self._token(pid)
            if later_token is None:
                return None
            if (before.start_sec, before.start_usec, before.uid, before.comm) != (
                    after.start_sec, after.start_usec, after.uid, after.comm) or token != later_token:
                continue
            if length <= 0 or length >= len(path):
                raise OSError(ctypes.get_errno(), 'Cannot attest process executable path')
            return Identity(pid, after.ppid, after.pgid, after.uid,
                            (after.start_sec, after.start_usec), os.fsdecode(path.value), token)
        raise RuntimeError(f'Unstable process identity for candidate {pid}; no signal authorized')

    def _list(self, function, owner):
        values = (ctypes.c_int * (MAX_PROCESSES + 1))()
        ctypes.set_errno(0)
        # Unlike proc_listpids, these two public convenience wrappers return
        # NUMBER OF PIDS, not bytes (exact-tag libproc.c lines76-96).
        count = function(owner, values, ctypes.sizeof(values))
        error = ctypes.get_errno()
        # libproc maps the underlying syscall's -1 to zero while retaining
        # errno; zero only denotes an empty list when errno is also zero.
        if count < 0 or (count == 0 and error != 0):
            raise OSError(error, 'Failed bounded process-list result')
        if count > MAX_PROCESSES:
            raise RuntimeError('Task descendant ceiling exceeded')
        return [values[index] for index in range(count) if values[index] > 0]

    def group_members(self, group):
        return self._list(self.proc.proc_listpgrppids, group)

    def children(self, parent):
        return self._list(self.proc.proc_listchildpids, parent)

    def wait_status(self, pid):
        info = SigInfo()
        ctypes.set_errno(0)
        # Public SDK: P_PID=1; WNOHANG=1; WEXITED=4; WNOWAIT=32.
        if self.lib.waitid(1, pid, ctypes.byref(info), 1 | 4 | 32) != 0:
            raise OSError(ctypes.get_errno(), 'Owned child is no longer waitable; group grant revoked')
        if info.pid == 0:
            return None
        if info.pid != pid or info.code not in (1, 2, 3):
            raise RuntimeError('Unexpected waitid child-status record')
        return info.status if info.code == 1 else -info.status

    def signal(self, identity, signum):
        # Kernel verifies PID+pidversion and retains a proc reference through
        # the signal. There is deliberately NO os.kill/killpg fallback.
        token = (ctypes.c_uint32 * 8)(*identity.token)
        code = self.proc.proc_signal_with_audittoken(token, signum)
        if code not in (0, errno.ESRCH):
            raise OSError(code, 'Audit-token-bound signaling failed')
        return code == 0


class Launch:
    def __init__(self, registry, process, label, args):
        self.registry, self.process = registry, process
        self.pid, self.label, self.args = process.pid, label, list(args)
        self.retired = False

    def poll(self):
        if self.retired:
            return self.process.returncode
        self.registry.refresh()
        return self.registry.backend.wait_status(self.pid)

    @property
    def returncode(self):
        return self.poll()


class OwnedProcesses:
    def __init__(self, backend=None, clock=time.monotonic, sleep=time.sleep):
        self.backend = backend or DarwinBackend()
        self.clock, self.sleep = clock, sleep
        self.launches, self.known, self.events = [], {}, []

    def register(self, process, label, args):
        if len(self.launches) >= MAX_LAUNCHES:
            raise RuntimeError('Launch ceiling must be checked before creating a worker')
        launch = Launch(self, process, label, args)
        # Register before any fallible native inspection or cancellation point.
        self.launches.append(launch)
        self.refresh()
        return launch

    def _adopt(self, identity, owner):
        if identity.pid == os.getpid() or identity.uid != os.getuid():
            raise RuntimeError('Unexpected process ownership within task-created ancestry')
        key = identity.lifetime
        if key not in self.known and len(self.known) >= MAX_PROCESSES:
            raise RuntimeError('Owned process-history ceiling exceeded')
        previous = self.known.get(key)
        if previous and previous[1] != owner:
            raise RuntimeError('Process was attested under conflicting launch owners')
        self.known[key] = (identity, owner)

    def refresh(self):
        # Only our direct, STILL WAITABLE child can grant a numeric group scan.
        # No Popen.poll/communicate/wait occurs until retire() below.
        for launch in self.launches:
            if launch.retired:
                continue
            try:
                self.backend.wait_status(launch.pid)
            except BaseException:
                launch.retired = True  # Never query/re-authorize this PGID again.
                raise
            for pid in self.backend.group_members(launch.pid):
                identity = self.backend.read(pid)
                if identity is not None and identity.group == launch.pid:
                    self._adopt(identity, launch.pid)
        # Retain observed children even after reparenting/session changes.
        # Each new child requires unchanged parent lifetime AND audit token on
        # both sides of enumeration. Never follow a recycled parent's children.
        for _ in range(8):
            added = False
            for key, (record, owner) in list(self.known.items()):
                current = self.backend.read(record.pid)
                if current is None or current.lifetime != key:
                    continue
                self.known[key] = (current, owner)
                candidates = [self.backend.read(pid) for pid in self.backend.children(record.pid)]
                after = self.backend.read(record.pid)
                if after is None or after.lifetime != key or after.token != current.token:
                    continue
                for child in candidates:
                    if child is None or child.parent != record.pid:
                        continue
                    added = added or child.lifetime not in self.known
                    self._adopt(child, owner)
            if not added:
                break
        else:
            raise RuntimeError('Task descendant depth ceiling exceeded')

    def live(self, owner=None):
        self.refresh()
        result = []
        for key, (record, belongs_to) in list(self.known.items()):
            if owner is not None and belongs_to != owner:
                continue
            current = self.backend.read(record.pid)
            if current is not None and current.lifetime == key:
                self.known[key] = (current, belongs_to)
                result.append(current)
        return result

    def stop(self, launch, term_seconds=10, kill_seconds=10):
        if launch.retired and not self.live(launch.pid):
            # A retired launch never grants a numeric group scan. Known
            # descendants still receive lifetime/token-bound cleanup below.
            return
        for signum, seconds in ((signal.SIGTERM, term_seconds), (signal.SIGKILL, kill_seconds)):
            deadline = self.clock() + seconds
            while True:
                remaining = self.live(launch.pid)
                if not remaining:
                    break
                for record in remaining:
                    current = self.backend.read(record.pid)
                    if current is None or current.lifetime != record.lifetime:
                        continue
                    if current.command != record.command or current.token != record.token:
                        continue  # Legitimate exec may be re-attested next pass.
                    sent = self.backend.signal(record, signum)
                    self.events.append({'pid': record.pid, 'started': record.started,
                                        'command': record.command, 'signal': int(signum),
                                        'generation': record.token[7], 'sent': sent})
                if self.clock() >= deadline:
                    break
                self.sleep(0.1)
            if not self.live(launch.pid):
                break
        remaining = self.live(launch.pid)
        if remaining:
            raise RuntimeError('Attested workers survived shutdown: ' + str([p.pid for p in remaining]))
        if launch.retired:
            return
        status = self.backend.wait_status(launch.pid)
        if status is None:
            raise RuntimeError('Direct worker remains running but was not safely attested')
        # Capture completed child's process group for the final time, while its
        # PID is still pinned by WNOWAIT, then immediately revoke the grant.
        self.refresh()
        if self.live(launch.pid):
            raise RuntimeError('New descendant appeared before retirement')
        launch.retired = True
        launch.process.wait(timeout=1)

    def finish(self, launch):
        if launch.poll() is None:
            raise RuntimeError('Cannot finish a running command')
        self.refresh()
        remaining = self.live(launch.pid)
        if remaining:
            # A foreground command is not permitted to leave daemon children.
            self.stop(launch)
            raise RuntimeError('Foreground command left owned child workers; stopped them')
        launch.retired = True
        launch.process.wait(timeout=1)

    def shutdown(self):
        errors = []
        for launch in reversed(self.launches):
            try:
                self.stop(launch)
            except BaseException as error:
                errors.append({'label': launch.label, 'pid': launch.pid, 'error': str(error)})
        survivors = self.live()
        if survivors:
            errors.append({'remaining_attested_workers': [asdict(p) for p in survivors]})
        return errors

    def receipts(self):
        return [{'pid': launch.pid, 'label': launch.label, 'retired': launch.retired,
                 'exit_code': launch.process.returncode} for launch in self.launches]
