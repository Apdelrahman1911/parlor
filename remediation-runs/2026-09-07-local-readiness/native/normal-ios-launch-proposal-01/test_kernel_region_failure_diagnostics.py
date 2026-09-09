"""Root-executed synthetic C controls, NOT Darwin ABI or app-runtime evidence.

The adjacent candidate is compiled with closed fake libproc/identity/file inputs;
no real PID, vnode, process-map, or application is queried. The old observer is
recovered by an exact inverse delta and SHA-pinned, not read from an unbound
external baseline. The production Python binder is separately SHA-pinned.

Run only in the root's reviewed control lane with an explicit owned TMPDIR.
All generated sources, compiler temporaries, and executables are task-owned and
finally removed. Compiler and fixture children have live output caps, finite
deadlines, checked return codes, and process-group cleanup on failure.
"""
import copy
import hashlib
import json
import os
from pathlib import Path
import selectors
import shutil
import signal
import stat
import subprocess
import tempfile
import time
import types
import unittest


HERE = Path(__file__).resolve().parent
ORIGINAL_SHA256 = 'd42aa932e5fe83defab173670aeea6f59507fa1b10029159efc04e1bbd67fdfc'
BINDER_SHA256 = '4b87bf30ab492bf45554b2952ef52982343d5f16c66dce247412a45801b0287b'
BASE_FIELDS = {'schema_version', 'status', 'reason', 'native_error'}
REASON = 'not-selected-executable-region'
BASE = dict(pid=4242, start=65536, end=90113, address=65536, size=8192,
            offset=0, protection=5, max_protection=5, flags=0,
            returned='complete', query_errno=0, fault='none')
ARGUMENTS = ('pid', 'start', 'end', 'address', 'size', 'offset', 'protection',
             'max_protection', 'flags', 'returned', 'query_errno', 'fault')
SPAN = BASE['end'] - BASE['start']
PREDICATES = (
    ('region->pri_address != start', dict(address=BASE['start'] + 1)),
    ('region->pri_size == 0', dict(size=0)),
    ('region->pri_size > end - start', dict(size=SPAN + 1)),
    ('region->pri_offset != 0', dict(offset=1)),
    ('(region->pri_flags & PROC_REGION_SUBMAP) != 0', dict(flags=1)),
    ('region->pri_protection != (VM_PROT_READ | VM_PROT_EXECUTE)', dict(protection=7)),
)

ORIGINAL_GUARD = '''    if (region->pri_address != start || region->pri_size == 0 || region->pri_size > end - start ||
        region->pri_offset != 0 || (region->pri_flags & PROC_REGION_SUBMAP) != 0 ||
        region->pri_protection != (VM_PROT_READ | VM_PROT_EXECUTE))
        return failed("not-selected-executable-region", 0);'''
DIAGNOSTIC_GUARD = r'''    if (region->pri_address != start || region->pri_size == 0 || region->pri_size > end - start ||
        region->pri_offset != 0 || (region->pri_flags & PROC_REGION_SUBMAP) != 0 ||
        region->pri_protection != (VM_PROT_READ | VM_PROT_EXECUTE)) {
        /* Uncorroborated failure snapshot, not path/vnode/lifetime evidence. */
        /* native_error retains its literal sentinel; query_errno is captured errno. */
        printf("{\"schema_version\":1,\"status\":\"FAIL\",\"reason\":\"not-selected-executable-region\",\"native_error\":0"
            ",\"region_diagnostic\":{\"requested_start\":%" PRIu64 ",\"requested_end_exclusive\":%" PRIu64
            ",\"pri_address\":%" PRIu64 ",\"pri_size\":%" PRIu64 ",\"pri_offset\":%" PRIu64
            ",\"pri_protection\":%u,\"pri_max_protection\":%u,\"pri_flags\":%u"
            ",\"native_structure_bytes\":%zu,\"native_return_bytes\":%d,\"query_errno\":%d}}\n",
            start, end, region->pri_address, region->pri_size, region->pri_offset,
            region->pri_protection, region->pri_max_protection, region->pri_flags,
            sizeof(info), returned, native_error);
        return 1;
    }'''

# These deliberately minimal synthetic declarations are NOT Darwin ABI claims.
HEADERS = {
    'libproc.h': '''#ifndef FIXTURE_LIBPROC_H
#define FIXTURE_LIBPROC_H
#include <stdint.h>
#define PROC_PIDREGIONPATHINFO 8
int proc_pidinfo(int, int, uint64_t, void *, int);
#endif
''',
    'mach/vm_prot.h': '''#ifndef FIXTURE_VM_PROT_H
#define FIXTURE_VM_PROT_H
#define VM_PROT_READ 1
#define VM_PROT_WRITE 2
#define VM_PROT_EXECUTE 4
#endif
''',
    'sys/proc_info.h': '''#ifndef FIXTURE_PROC_INFO_H
#define FIXTURE_PROC_INFO_H
#include <stdint.h>
#ifndef MAXPATHLEN
#define MAXPATHLEN 1024
#endif
#define PROC_REGION_SUBMAP 1U
struct proc_regioninfo {
    uint64_t pri_address, pri_size, pri_offset;
    uint32_t pri_protection, pri_max_protection, pri_flags;
};
struct vinfo_stat {
    uint32_t vst_dev, vst_mode;
    uint64_t vst_ino;
    int64_t vst_size;
};
struct fixture_vnode_info { struct vinfo_stat vi_stat; };
struct fixture_vnode_path {
    struct fixture_vnode_info vip_vi;
    char vip_path[MAXPATHLEN];
};
struct proc_regionwithpathinfo {
    struct proc_regioninfo prp_prinfo;
    struct fixture_vnode_path prp_vip;
};
#endif
''',
}

HARNESS = r'''#define _XOPEN_SOURCE 700
#define _DARWIN_C_SOURCE 1
#include <errno.h>
#include <inttypes.h>
#include <libproc.h>
#include <limits.h>
#include <mach/vm_prot.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/proc_info.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

static const char fixture_path[] = "/synthetic-private-artifact-not-for-output";
static const char *fixture_fault = "none";
static uint64_t request_pid, request_start;
static int queries, lstats, realpaths, bad_api, query_entry_errno = -1;
static int reply_bytes, reply_errno;
static struct proc_regionwithpathinfo fixture_info;

static int fault(const char *name) { return strcmp(fixture_fault, name) == 0; }
static uid_t fixture_getuid(void) { return (uid_t)1000; }
static uid_t fixture_geteuid(void) { return (uid_t)(fault("identity") ? 1001 : 1000); }
static pid_t fixture_getpid(void) { return (pid_t)3123; }

static int fixture_lstat(const char *path, struct stat *value) {
    lstats++;
    if (strcmp(path, fixture_path) != 0 || lstats > 2) bad_api++;
    memset(value, 0, sizeof(*value));
    value->st_mode = S_IFREG | 0600;
    value->st_uid = (uid_t)1000;
    value->st_dev = (dev_t)7;
    value->st_ino = (ino_t)11;
    value->st_size = (off_t)8192;
    if (lstats == 1) {
        if (fault("before_lstat")) { errno = ENOENT; return -1; }
        if (fault("before_kind")) value->st_mode = S_IFDIR | 0700;
        if (fault("before_uid")) value->st_uid++;
        if (fault("before_size")) value->st_size = 0;
    } else {
        if (fault("after_lstat")) { errno = EIO; return -1; }
        if (fault("after_kind")) value->st_mode = S_IFDIR | 0700;
        if (fault("after_dev")) value->st_dev++;
        if (fault("after_inode")) value->st_ino++;
        if (fault("after_size")) value->st_size++;
    }
    errno = EBUSY; /* A successful unrelated operation need not clear errno. */
    return 0;
}

static char *fixture_realpath(const char *path, char *resolved) {
    realpaths++;
    if (strcmp(path, fixture_path) != 0 || realpaths > 1) bad_api++;
    if (fault("realpath")) { errno = ENOENT; return NULL; }
    strcpy(resolved, fault("canonical") ? "/synthetic-other-canonical-path" : fixture_path);
    errno = ERANGE;
    return resolved;
}

int proc_pidinfo(int pid, int flavor, uint64_t address, void *buffer, int bytes) {
    queries++;
    query_entry_errno = errno;
    if ((uint64_t)pid != request_pid || flavor != PROC_PIDREGIONPATHINFO ||
        address != request_start || !buffer || bytes != (int)sizeof(fixture_info)) {
        bad_api++;
        errno = EINVAL;
        return -1;
    }
    /* A short/failed reply leaves poison beyond its reported prefix. */
    memset(buffer, 0xa5, (size_t)bytes);
    if (reply_bytes > 0) {
        size_t copied = (size_t)reply_bytes;
        if (copied > sizeof(fixture_info)) copied = sizeof(fixture_info);
        memcpy(buffer, &fixture_info, copied);
    }
    errno = reply_errno;
    return reply_bytes;
}

#define getuid fixture_getuid
#define geteuid fixture_geteuid
#define getpid fixture_getpid
#define lstat fixture_lstat
#define realpath fixture_realpath
#define main observed_main
#include "observer.c.in"
#undef main
#undef getuid
#undef geteuid
#undef getpid
#undef lstat
#undef realpath

int main(int argc, char **argv) {
    if (argc != 13) return 90;
    request_pid = (uint64_t)strtoull(argv[1], NULL, 10);
    request_start = (uint64_t)strtoull(argv[2], NULL, 10);
    fixture_info.prp_prinfo.pri_address = (uint64_t)strtoull(argv[4], NULL, 10);
    fixture_info.prp_prinfo.pri_size = (uint64_t)strtoull(argv[5], NULL, 10);
    fixture_info.prp_prinfo.pri_offset = (uint64_t)strtoull(argv[6], NULL, 10);
    fixture_info.prp_prinfo.pri_protection = (uint32_t)strtoul(argv[7], NULL, 10);
    fixture_info.prp_prinfo.pri_max_protection = (uint32_t)strtoul(argv[8], NULL, 10);
    fixture_info.prp_prinfo.pri_flags = (uint32_t)strtoul(argv[9], NULL, 10);
    reply_bytes = strcmp(argv[10], "complete") == 0 ? (int)sizeof(fixture_info) : (int)strtol(argv[10], NULL, 10);
    reply_errno = (int)strtol(argv[11], NULL, 10);
    fixture_fault = argv[12];
    struct vinfo_stat *vnode = &fixture_info.prp_vip.vip_vi.vi_stat;
    vnode->vst_dev = 7;
    vnode->vst_ino = 11;
    vnode->vst_size = 8192;
    vnode->vst_mode = S_IFREG | 0600;
    if (fault("vnode_dev")) vnode->vst_dev++;
    if (fault("vnode_inode")) vnode->vst_ino++;
    if (fault("vnode_size")) vnode->vst_size++;
    if (fault("vnode_kind")) vnode->vst_mode = S_IFDIR | 0700;
    strcpy(fixture_info.prp_vip.vip_path, fault("path") ? "/synthetic-unmatched-private-path" : fixture_path);
    if (fault("unterminated_path")) memset(fixture_info.prp_vip.vip_path, 'P', sizeof(fixture_info.prp_vip.vip_path));
    char *arguments[] = {"synthetic-observer", argv[1], argv[2], argv[3], (char *)fixture_path, NULL};
    int result = observed_main(5, arguments);
    fprintf(stderr, "{\"queries\":%d,\"lstats\":%d,\"realpaths\":%d,\"query_entry_errno\":%d,\"abi_bytes\":%zu,\"bad_api\":%d}\n",
            queries, lstats, realpaths, query_entry_errno, sizeof(fixture_info), bad_api);
    return result;
}
'''


def replace_once(source, old, new):
    if source.count(old) != 1:
        raise AssertionError('Mutation/inverse-delta anchor is not unique')
    return source.replace(old, new, 1)


def pinned_original(source):
    original = replace_once(source, DIAGNOSTIC_GUARD, ORIGINAL_GUARD)
    if hashlib.sha256(original.encode()).hexdigest() != ORIGINAL_SHA256:
        raise AssertionError('Candidate contains changes outside the reviewed failure-only delta')
    return original


def pinned_binder():
    path = HERE / 'kernel_image_regions.py'
    if not path.is_file():
        path = HERE.parents[3] / 'remediation-runs/2026-09-07-local-readiness/native/normal-ios-launch-proposal-01/kernel_image_regions.py'
    if path.is_symlink() or path.resolve(strict=True) != path:
        raise AssertionError('Redirected Python binder input')
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != BINDER_SHA256:
        raise AssertionError('Python binder differs from its reviewed byte binding')
    module = types.ModuleType('_pinned_kernel_region_failure_binder')
    module.__file__ = str(path)
    exec(compile(raw, str(path), 'exec'), module.__dict__)
    return module


def closed_json(raw):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise AssertionError('Duplicate JSON key')
            result[key] = value
        return result
    return json.loads(raw.decode('ascii'), object_pairs_hook=unique)


def bounded_child(arguments, directory, environment, timeout, stdout_limit, stderr_limit):
    """No shell; stream caps are enforced while reading, not after capture."""
    selector = selectors.DefaultSelector()
    process = None
    chunks = {'stdout': bytearray(), 'stderr': bytearray()}
    caps = {'stdout': stdout_limit, 'stderr': stderr_limit}
    deadline = time.monotonic() + timeout
    try:
        process = subprocess.Popen(arguments, cwd=directory, env=environment,
                                   stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                   stderr=subprocess.PIPE, start_new_session=True)
        for name in chunks:
            stream = getattr(process, name)
            os.set_blocking(stream.fileno(), False)
            selector.register(stream, selectors.EVENT_READ, name)
        while selector.get_map():
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise AssertionError('Synthetic child exceeded its finite deadline')
            for key, _events in selector.select(remaining):
                name = key.data
                allowance = caps[name] - len(chunks[name])
                block = os.read(key.fileobj.fileno(), min(8192, allowance + 1))
                if not block:
                    selector.unregister(key.fileobj)
                else:
                    chunks[name].extend(block)
                    if len(chunks[name]) > caps[name]:
                        raise AssertionError('Synthetic child exceeded its ' + name + ' budget')
        result = process.wait(timeout=max(0.001, deadline - time.monotonic()))
        return types.SimpleNamespace(returncode=result, stdout=bytes(chunks['stdout']),
                                     stderr=bytes(chunks['stderr']))
    except BaseException:
        if process is not None:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.wait(timeout=5)
        raise
    finally:
        selector.close()
        if process is not None:
            for stream in (process.stdout, process.stderr):
                if stream is not None:
                    stream.close()


class KernelRegionFailureDiagnosticTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        candidate = HERE / 'kernel_image_region.c.in'
        if candidate.is_symlink() or candidate.resolve(strict=True) != candidate:
            raise AssertionError('Redirected candidate source')
        cls.source = candidate.read_text()
        cls.original = pinned_original(cls.source)
        cls.kernel = pinned_binder()
        owned_text = os.environ.get('TMPDIR')
        if not owned_text:
            raise AssertionError('Root must supply its explicit owned TMPDIR')
        owned = Path(owned_text)
        if (not owned.is_absolute() or owned.is_symlink() or not owned.is_dir() or
                owned.resolve(strict=True) != owned or owned.stat().st_uid != os.getuid() or
                owned.stat().st_mode & 0o077):
            raise AssertionError('TMPDIR is not an exact private current-user directory')
        allocation = tempfile.TemporaryDirectory(prefix='parlor-region-diagnostic-control-', dir=owned)
        cls.addClassCleanup(allocation.cleanup)
        cls.directory = Path(allocation.name)
        temporary = cls.directory / 'compiler-tmp'
        temporary.mkdir(mode=0o700)
        cls.environment = {key: os.environ[key] for key in
                           ('PATH', 'DEVELOPER_DIR', 'SDKROOT', 'SYSTEMROOT') if key in os.environ}
        cls.environment.update(TMPDIR=str(temporary), HOME=str(cls.directory), LC_ALL='C', LANG='C')
        compiler = shutil.which('clang') or shutil.which('cc')
        if not compiler:
            raise AssertionError('Synthetic controls require a C compiler; never skip them')
        cls.compiler = str(Path(compiler).resolve(strict=True))
        cls.candidate_binary = cls.build('candidate', cls.source)
        cls.original_binary = cls.build('original-pinned', cls.original)

    @classmethod
    def build(cls, name, source):
        directory = cls.directory / name
        directory.mkdir(mode=0o700)
        for relative, text in dict(HEADERS, **{'observer.c.in': source, 'harness.c': HARNESS}).items():
            path = directory / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            with path.open('x') as stream:
                stream.write(text)
        executable = directory / 'synthetic-observer'
        if executable.exists() or executable.is_symlink():
            raise AssertionError('Refuse a stale synthetic executable')
        command = [cls.compiler, '-std=c11', '-Wall', '-Wextra', '-Werror', '-I', str(directory),
                   str(directory / 'harness.c'), '-o', str(executable)]
        result = bounded_child(command, directory, cls.environment, 30, 65536, 65536)
        if result.returncode != 0 or result.stdout or result.stderr:
            raise AssertionError('Synthetic compiler failed or emitted diagnostics: ' +
                                 repr((result.returncode, result.stdout, result.stderr)))
        metadata = executable.lstat()
        if (executable.is_symlink() or executable.resolve(strict=True) != executable or
                not stat.S_ISREG(metadata.st_mode) or metadata.st_uid != os.getuid() or
                not 0 < metadata.st_size <= 1024 * 1024):
            raise AssertionError('Invalid task-owned synthetic executable')
        return executable

    def observe(self, binary=None, **changes):
        configuration = dict(BASE, **changes)
        arguments = [str(binary or self.candidate_binary)] + [str(configuration[key]) for key in ARGUMENTS]
        result = bounded_child(arguments, self.directory, self.environment, 5, 4096, 4096)
        self.assertIn(result.returncode, (0, 1), 'Fixture or observer crashed')
        self.assertEqual(1, result.stdout.count(b'\n'))
        self.assertEqual(1, result.stderr.count(b'\n'))
        row, telemetry = closed_json(result.stdout), closed_json(result.stderr)
        self.assertEqual({'queries', 'lstats', 'realpaths', 'query_entry_errno', 'abi_bytes', 'bad_api'}, set(telemetry))
        self.assertEqual(0, telemetry['bad_api'])
        if telemetry['queries']:
            self.assertEqual(0, telemetry['query_entry_errno'])
        return types.SimpleNamespace(row=row, telemetry=telemetry, result=result, configuration=configuration)

    def assert_completed_failure(self, observation):
        row, telemetry, cfg = observation.row, observation.telemetry, observation.configuration
        self.assertEqual(1, observation.result.returncode)
        self.assertEqual(BASE_FIELDS | {'region_diagnostic'}, set(row))
        self.assertEqual(dict(schema_version=1, status='FAIL', reason=REASON, native_error=0),
                         {key: row[key] for key in BASE_FIELDS})
        self.assertIs(type(row['schema_version']), int)
        self.assertIs(type(row['native_error']), int)
        self.assertEqual(1, telemetry['queries'])
        self.assertEqual(1, telemetry['lstats'])  # No post-query vnode/lstat claim.
        expected = dict(requested_start=cfg['start'], requested_end_exclusive=cfg['end'],
                        pri_address=cfg['address'], pri_size=cfg['size'], pri_offset=cfg['offset'],
                        pri_protection=cfg['protection'], pri_max_protection=cfg['max_protection'],
                        pri_flags=cfg['flags'], native_structure_bytes=telemetry['abi_bytes'],
                        native_return_bytes=telemetry['abi_bytes'], query_errno=cfg['query_errno'])
        self.assertEqual(expected, row['region_diagnostic'])
        self.assertTrue(all(type(value) is int for value in row['region_diagnostic'].values()))
        self.assertTrue(0 < telemetry['abi_bytes'] <= 65536)
        self.assertLessEqual(len(observation.result.stdout), 1024)
        self.assertNotIn(b'synthetic-private', observation.result.stdout)
        self.assertNotIn(b'path', observation.result.stdout)
        self.assertNotIn(b'vnode', observation.result.stdout)

    def assert_success(self, observation):
        self.assertEqual(0, observation.result.returncode)
        self.assertEqual('PASS', observation.row['status'])
        self.assertEqual(self.kernel.FIELDS, set(observation.row))
        self.assertEqual(1, observation.telemetry['queries'])
        self.assertEqual(2, observation.telemetry['lstats'])
        self.assertNotIn('region_diagnostic', observation.row)
        self.assertNotIn('query_errno', observation.row)

    def assert_old_failure(self, observation, reason, queries):
        self.assertEqual(1, observation.result.returncode)
        self.assertEqual(BASE_FIELDS, set(observation.row))
        error = observation.configuration['query_errno'] if reason == 'region-query-incomplete' else 0
        self.assertEqual(dict(schema_version=1, status='FAIL', reason=reason, native_error=error), observation.row)
        self.assertEqual(queries, observation.telemetry['queries'])

    def binding_inputs(self, row):
        selected = {'/synthetic-selected-image-' + str(index): dict(
            sample_start=BASE['start'], sample_end_inclusive=BASE['end'] - 1, bytes=8192)
            for index in range(3)}
        return selected, {path: copy.deepcopy(row) for path in selected}

    def branch_mutant(self, name, old, new):
        branch = replace_once(DIAGNOSTIC_GUARD, old, new)
        return self.build(name, replace_once(self.source, DIAGNOSTIC_GUARD, branch))

    def test_only_delta_is_the_completed_guard_failure_body(self):
        self.assertEqual(ORIGINAL_SHA256, hashlib.sha256(pinned_original(self.source).encode()).hexdigest())
        self.assertEqual(self.original.split(ORIGINAL_GUARD)[0], self.source.split(DIAGNOSTIC_GUARD)[0])
        self.assertEqual(self.original.split(ORIGINAL_GUARD)[1], self.source.split(DIAGNOSTIC_GUARD)[1])

    def test_source_keeps_one_query_complete_reply_gate_and_no_new_private_surface(self):
        self.assertEqual(1, self.source.count('proc_pidinfo('))
        complete = self.source.index('if (returned != (int)sizeof(info)) return failed("region-query-incomplete", native_error);')
        self.assertLess(self.source.index('int native_error = errno;'), complete)
        self.assertLess(complete, self.source.index('const struct proc_regioninfo *region'))
        self.assertLess(complete, self.source.index('const struct vinfo_stat *file'))
        self.assertEqual(5, DIAGNOSTIC_GUARD.split('printf(', 1)[0].count('||'))
        for predicate, _witness in PREDICATES:
            self.assertIn(predicate, DIAGNOSTIC_GUARD)
        for forbidden in ('sysconf(', 'task_for_pid(', 'proc_regionfilename(', 'mach_vm_read',
                          'vmmap', 'system(', 'popen(', 'fork('):
            self.assertNotIn(forbidden, self.source)
        for forbidden in ('%s', 'argv[4]', 'vip_path', 'vst_', 'path_exact', 'vnode_identity_matches'):
            self.assertNotIn(forbidden, DIAGNOSTIC_GUARD)

    def test_each_of_six_original_predicates_still_fails_with_exact_raw_values(self):
        for predicate, witness in PREDICATES:
            with self.subTest(predicate=predicate):
                self.assert_old_failure(self.observe(self.original_binary, **witness), REASON, 1)
                self.assert_completed_failure(self.observe(**witness))

    def test_unchanged_success_bytes_include_original_non_guard_boundaries(self):
        cases = ({}, dict(size=1), dict(size=SPAN), dict(max_protection=0),
                 dict(max_protection=7), dict(max_protection=2**32 - 1),
                 dict(flags=2), dict(flags=2**32 - 2), dict(query_errno=11))
        for changes in cases:
            with self.subTest(changes=changes):
                original, candidate = self.observe(self.original_binary, **changes), self.observe(**changes)
                self.assert_success(original)
                self.assert_success(candidate)
                self.assertEqual(original.result.stdout, candidate.result.stdout)

    def test_no_advancing_rounding_or_combined_fault_acceptance(self):
        cases = [dict(address=BASE['start'] - 1), dict(address=BASE['start'] + 4096),
                 dict(size=SPAN + 1), dict(size=((SPAN + 4095) // 4096) * 4096),
                 dict(size=((SPAN + 16383) // 16384) * 16384),
                 dict(address=BASE['start'] + 1, size=0, offset=1, flags=1, protection=7)]
        for protection in (0, 1, 3, 4, 6, 2**32 - 1):
            cases.append(dict(protection=protection))
        for changes in cases:
            with self.subTest(changes=changes):
                self.assert_old_failure(self.observe(self.original_binary, **changes), REASON, 1)
                self.assert_completed_failure(self.observe(**changes))

    def test_failure_schema_is_closed_numeric_raw_and_bounded_at_type_extremes(self):
        for error in (-(2**31), 0, 2**31 - 1):
            with self.subTest(error=error):
                self.assert_completed_failure(self.observe(
                    start=2**64 - 2, end=2**64 - 1, address=2**64 - 1,
                    size=2**64 - 1, offset=2**64 - 1, protection=2**32 - 1,
                    max_protection=2**32 - 1, flags=2**32 - 1, query_errno=error))

    def test_captured_errno_is_not_the_historical_literal_native_error(self):
        for error in (-1, 0, 5, 11, 22):
            with self.subTest(error=error):
                observation = self.observe(size=0, query_errno=error)
                self.assert_completed_failure(observation)
                self.assertEqual(0, observation.row['native_error'])
                self.assertEqual(error, observation.row['region_diagnostic']['query_errno'])

    def test_incomplete_replies_preserve_old_error_only_output_without_struct_data(self):
        structure_bytes = self.observe(size=0).telemetry['abi_bytes']
        for returned in (-1, 0, 1, structure_bytes - 1, structure_bytes + 1, 2**31 - 1):
            for error in (0, 22):
                changes = dict(returned=returned, query_errno=error)
                with self.subTest(changes=changes):
                    original, candidate = self.observe(self.original_binary, **changes), self.observe(**changes)
                    self.assert_old_failure(candidate, 'region-query-incomplete', 1)
                    self.assertEqual(1, candidate.telemetry['lstats'])
                    self.assertEqual(original.result.stdout, candidate.result.stdout)

    def test_invalid_request_and_owned_artifact_checks_remain_before_query(self):
        requests = [dict(pid=1), dict(pid=3123), dict(pid=2**31), dict(start=0),
                    dict(end=BASE['start']), dict(fault='identity')]
        artifacts = [dict(fault=fault) for fault in
                     ('before_lstat', 'before_kind', 'before_uid', 'before_size', 'realpath', 'canonical')]
        for reason, cases in (('invalid-request', requests), ('invalid-owned-artifact', artifacts)):
            for changes in cases:
                with self.subTest(reason=reason, changes=changes):
                    original, candidate = self.observe(self.original_binary, **changes), self.observe(**changes)
                    self.assert_old_failure(candidate, reason, 0)
                    self.assertEqual(original.result.stdout, candidate.result.stdout)

    def test_path_vnode_and_final_lstat_checks_remain_unchanged_without_diagnostics(self):
        faults = ('path', 'unterminated_path', 'vnode_dev', 'vnode_inode', 'vnode_size', 'vnode_kind',
                  'after_lstat', 'after_kind', 'after_dev', 'after_inode', 'after_size')
        for fault in faults:
            with self.subTest(fault=fault):
                original, candidate = self.observe(self.original_binary, fault=fault), self.observe(fault=fault)
                self.assert_old_failure(candidate, 'mapping-artifact-mismatch', 1)
                self.assertEqual(original.result.stdout, candidate.result.stdout)

    def test_pinned_python_binder_still_accepts_only_the_unchanged_success_contract(self):
        success = self.observe().row
        selected, observations = self.binding_inputs(success)
        self.assertEqual('PASS', self.kernel.bind_regions(BASE['pid'], selected, observations)['status'])
        path = next(iter(selected))
        for field, value in (('status', 'FAIL'), ('region_size', 0), ('region_size', SPAN + 1),
                             ('region_start', BASE['start'] + 1), ('region_offset', 1), ('protection', 7),
                             ('region_flags', 1), ('max_protection', 0), ('native_return_bytes', 1),
                             ('path_exact', False), ('vnode_identity_matches', False)):
            changed = copy.deepcopy(observations)
            changed[path][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(RuntimeError):
                self.kernel.bind_regions(BASE['pid'], selected, changed)

    def test_extended_failures_and_success_shape_smuggling_never_bind(self):
        success = self.observe().row
        for predicate, witness in PREDICATES:
            failure = self.observe(**witness).row
            cases = [failure, dict(failure, status='PASS'),
                     dict(success, region_diagnostic=failure['region_diagnostic']),
                     dict(success, status='FAIL'), dict(success, query_errno=0)]
            for index, row in enumerate(cases):
                selected, observations = self.binding_inputs(row)
                with self.subTest(predicate=predicate, case=index), self.assertRaises(RuntimeError):
                    self.kernel.bind_regions(BASE['pid'], selected, observations)

    def test_each_removed_predicate_mutant_is_killed_by_its_failure_witness(self):
        for index, (predicate, witness) in enumerate(PREDICATES):
            with self.subTest(predicate=predicate):
                # Valid requests have end > start; avoid a compiler-specific
                # constant-logical-operand warning in the disabled predicate.
                mutant = self.build('removed-predicate-' + str(index), replace_once(self.source, predicate, 'start > end'))
                with self.assertRaises(AssertionError):
                    self.assert_completed_failure(self.observe(mutant, **witness))

    def test_page_rounding_acceptance_mutants_are_killed(self):
        for page in (4096, 16384):
            with self.subTest(page=page):
                rounded_guard = 'region->pri_size > ((end - start + UINT64_C(' + str(page - 1) + ')) & ~UINT64_C(' + str(page - 1) + '))'
                mutant = self.build('rounding-' + str(page), replace_once(self.source, 'region->pri_size > end - start', rounded_guard))
                with self.assertRaises(AssertionError):
                    self.assert_completed_failure(self.observe(mutant, size=((SPAN + page - 1) // page) * page))

    def test_failure_status_exit_schema_raw_value_and_errno_mutants_are_killed(self):
        mutations = (
            ('status', r'\"status\":\"FAIL\"', r'\"status\":\"PASS\"'),
            ('reason', r'\"reason\":\"not-selected-executable-region\"', r'\"reason\":\"other\"'),
            ('exit', '        return 1;', '        return 0;'),
            ('legacy-sentinel', r'\"native_error\":0', r'\"native_error\":11'),
            ('uncaptured-errno', 'sizeof(info), returned, native_error);', 'sizeof(info), returned, 0);'),
            ('clamped-size', 'region->pri_size, region->pri_offset,', 'end - start, region->pri_offset,'),
            ('extra-field', r'\"pri_flags\":%u', r'\"pri_flags\":%u,\"unknown\":0'),
        )
        for name, old, new in mutations:
            with self.subTest(mutation=name):
                mutant = self.branch_mutant('failure-' + name, old, new)
                with self.assertRaises(AssertionError):
                    self.assert_completed_failure(self.observe(mutant, size=0, query_errno=11))
        leaked = replace_once(DIAGNOSTIC_GUARD, r'\"query_errno\":%d}}\n"', r'\"query_errno\":%d,\"path\":\"%s\"}}\n"')
        leaked = replace_once(leaked, 'sizeof(info), returned, native_error);', 'sizeof(info), returned, native_error, argv[4]);')
        mutant = self.build('failure-private-path', replace_once(self.source, DIAGNOSTIC_GUARD, leaked))
        with self.assertRaises(AssertionError):
            self.assert_completed_failure(self.observe(mutant, size=0, query_errno=11))

    def test_extra_query_and_incomplete_struct_use_mutants_are_killed(self):
        extra = '    returned = proc_pidinfo((int)pid, PROC_PIDREGIONPATHINFO, start, &info, (int)sizeof(info));\n    int native_error = errno;'
        mutant = self.build('extra-query', replace_once(self.source, '    int native_error = errno;', extra))
        with self.assertRaises(AssertionError):
            self.assert_completed_failure(self.observe(mutant, size=0))
        incomplete = replace_once(self.source,
            'if (returned != (int)sizeof(info)) return failed("region-query-incomplete", native_error);',
            'if (returned != (int)sizeof(info)) native_error += 0;')
        mutant = self.build('incomplete-struct-use', incomplete)
        with self.assertRaises(AssertionError):
            self.assert_old_failure(self.observe(mutant, returned=0, query_errno=22), 'region-query-incomplete', 1)

    def test_success_diagnostic_mutant_is_killed(self):
        contaminated = replace_once(self.source, r'\"status\":\"PASS\",\"pid\":%',
                                    r'\"status\":\"PASS\",\"query_errno\":0,\"pid\":%')
        mutant = self.build('success-diagnostic', contaminated)
        with self.assertRaises(AssertionError):
            self.assert_success(self.observe(mutant))


if __name__ == '__main__':
    unittest.main()
