#!/usr/bin/env python3
"""Pure, process-denied control tests. Root owns execution; never boots a VM.

All files are synthetic and scoped by TemporaryDirectory. Native launches,
signals, URL openers, and production entry points are denied unless the test
explicitly substitutes an in-memory fake. These are control tests, not Linux,
Gradle, VZ, P2pKit, signing, Store, or application-runtime evidence.
"""
from __future__ import annotations

import contextlib
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tarfile
import tempfile
import types
import unittest
from unittest import mock
import urllib.request


HERE = Path(__file__).resolve().parent


def denied(*_args, **_kwargs):
    raise AssertionError('Pure control tests must not launch or signal processes or use the network')


def load_pure(name, filename):
    module = types.ModuleType(name)
    module.__file__ = str(HERE / filename)
    sys.modules[name] = module
    with mock.patch.object(subprocess, 'Popen', side_effect=denied), \
            mock.patch.object(urllib.request, 'build_opener', side_effect=denied), \
            mock.patch.object(os, 'kill', side_effect=denied), \
            mock.patch.object(os, 'killpg', side_effect=denied):
        exec(compile((HERE / filename).read_bytes(), module.__file__, 'exec'), module.__dict__)
    return module


host = load_pure('linux_vz_host_pure_tests', 'owned_linux_vz.py')
guest = load_pure('linux_vz_guest_pure_tests', 'guest_verification.py')


def checksum(data):
    return hashlib.sha256(data).hexdigest()


def make_source(files):
    records = [[name, checksum(data)] for name, data in sorted(files.items())]
    return {'commit': 'a' * 40, 'tree': 'b' * 40, 'branch': 'synthetic-branch',
            'diff_sha256': checksum(b''), 'source_manifest': records,
            'source_manifest_sha256': checksum(json.dumps(records, separators=(',', ':')).encode())}


def python_receipt():
    identifier = 'test_synthetic.Synthetic.test_executed'
    return {'status': 'PASS', 'discovered': [identifier], 'tests_run': 1,
            'outcomes': [{'id': identifier, 'status': 'PASS'}], 'loader_errors': [],
            'expected_failures': [], 'unexpected_successes': []}


def xml_bytes(cases):
    import xml.etree.ElementTree as ET
    counts = {status: sum(row[2] == status for row in cases) for status in ('FAIL', 'ERROR', 'SKIP')}
    suite = ET.Element('testsuite', name='synthetic', tests=str(len(cases)),
                       failures=str(counts['FAIL']), errors=str(counts['ERROR']), skipped=str(counts['SKIP']))
    for class_name, name, status in cases:
        case = ET.SubElement(suite, 'testcase', classname=class_name, name=name)
        if status != 'PASS':
            ET.SubElement(case, {'FAIL': 'failure', 'ERROR': 'error', 'SKIP': 'skipped'}[status]).text = 'synthetic'
    return ET.tostring(suite, encoding='utf-8')


def desktop_cases():
    return [(guest.DISCOVERY_CLASS, 'testSignatureContract()', 'PASS')] + [
        (guest.LOOPBACK_CLASS, name + '()[desktop]', 'SKIP') for name in sorted(guest.KNOWN_SKIPS)]


class PureCase(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='parlor-linux-vz-pure-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.stack = contextlib.ExitStack()
        self.addCleanup(self.stack.close)
        for target, attribute in ((subprocess, 'Popen'), (urllib.request, 'build_opener'),
                                  (os, 'kill'), (os, 'killpg')):
            self.stack.enter_context(mock.patch.object(target, attribute, side_effect=denied))
        self.stack.enter_context(mock.patch.object(guest, 'CANCELLED', False))

    def write(self, relative, data=b'synthetic'):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return path

    def archive(self, rows):
        path = self.root / 'fixture.tar.gz'
        with tarfile.open(path, 'w:gz') as archive:
            for name, kind, value in rows:
                info = tarfile.TarInfo(name)
                if kind == 'file':
                    info.size, info.mode = len(value), 0o755
                    archive.addfile(info, io.BytesIO(value))
                else:
                    info.type = {'dir': tarfile.DIRTYPE, 'symlink': tarfile.SYMTYPE,
                                 'hardlink': tarfile.LNKTYPE, 'fifo': tarfile.FIFOTYPE}[kind]
                    if kind in ('symlink', 'hardlink'):
                        info.linkname = value
                    archive.addfile(info)
        return path


class SourceBoundaryTests(PureCase):
    def test_public_source_including_credential_store_is_allowed(self):
        for path in ('AGENTS.md', 'CLAUDE.md', 'assets/source.svg', '.github/workflows/test.yml',
                     'shared/storage/src/commonMain/kotlin/ResumableCredentialStore.kt'):
            with self.subTest(path=path):
                self.assertEqual(path, str(host.checked_relative(path)))

    def test_protected_generated_or_escaping_paths_are_rejected(self):
        for path in ('../secret', '/etc/passwd', 'docs//file', 'docs/./file', 'docs/../file',
                     'docs/file\n', 'docs\\file', 'local.properties', 'release/upload.jks',
                     'release/credentials.json', 'release/service-account.json', 'release/private/data',
                     'composeApp/build/result', 'iosApp/xcuserdata/settings', '.ssh/id_rsa',
                     'remediation-runs/run.json', 'design/index.html'):
            with self.subTest(path=path), self.assertRaises(ValueError):
                host.checked_relative(path)

    def test_control_leaf_and_ancestor_symlinks_are_rejected(self):
        control = self.write('real/control.py')
        self.assertEqual(b'synthetic', host.read_control(control))
        self.write('real/other.py')
        (self.root / 'alias').symlink_to(self.root / 'real', target_is_directory=True)
        (self.root / 'leaf.py').symlink_to(control)
        for path in (self.root / 'alias/control.py', self.root / 'leaf.py'):
            with self.subTest(path=path), self.assertRaises((ValueError, OSError)):
                host.read_control(path)

    def test_control_size_owner_and_concurrent_metadata_changes_fail_closed(self):
        path = self.write('control.py')
        with self.assertRaises(ValueError):
            host.read_control(path, limit=2)
        with mock.patch.object(host.os, 'getuid', return_value=os.getuid() + 1), self.assertRaises(ValueError):
            host.read_control(path)
        original = os.fstat
        count = 0

        def changing(fd):
            nonlocal count
            count += 1
            info = original(fd)
            return types.SimpleNamespace(st_dev=info.st_dev, st_ino=info.st_ino, st_size=info.st_size,
                                         st_mtime_ns=info.st_mtime_ns + count, st_uid=info.st_uid,
                                         st_mode=info.st_mode)

        with mock.patch.object(host.os, 'fstat', side_effect=changing), self.assertRaisesRegex(ValueError, 'changed'):
            host.read_control(path)

    def test_control_receipts_cannot_escape_the_selected_scope(self):
        path = self.write('approved/receipt.json', b'{}')
        self.assertEqual(path, host.canonical_scoped_file(path, self.root / 'approved'))
        with self.assertRaises(ValueError):
            host.canonical_scoped_file(path, self.root / 'different')
        (self.root / 'alias').symlink_to(self.root / 'approved', target_is_directory=True)
        with self.assertRaises(ValueError):
            host.canonical_scoped_file(self.root / 'alias/receipt.json', self.root)

    def test_descriptor_relative_source_reads_reject_ancestor_and_leaf_links(self):
        path = self.write('docs/real.md')
        (self.root / 'docs/link.md').symlink_to(path)
        (self.root / 'config').symlink_to(self.root / 'docs', target_is_directory=True)
        fd = os.open(self.root, os.O_RDONLY | os.O_DIRECTORY)
        self.addCleanup(os.close, fd)
        self.assertEqual((b'synthetic', 0o644), host.read_beneath(fd, 'docs/real.md'))
        for relative in ('docs/link.md', 'config/real.md'):
            with self.subTest(relative=relative), self.assertRaises(OSError):
                host.read_beneath(fd, relative)

    def test_manifest_requires_exact_nonduplicate_digest_and_full_source_identity(self):
        source = make_source({'build.gradle.kts': b'x'})
        self.assertEqual(source['source_manifest'], guest.manifest_records(source, source['source_manifest_sha256']))
        for mutate in (lambda value: value.update(commit='abc'),
                       lambda value: value['source_manifest'].append(value['source_manifest'][0]),
                       lambda value: value['source_manifest'][0].__setitem__(1, '0' * 64),
                       lambda value: value['source_manifest'][0].__setitem__(0, '../private')):
            modified = copy.deepcopy(source)
            mutate(modified)
            with self.subTest(modified=modified), self.assertRaises(ValueError):
                guest.manifest_records(modified, source['source_manifest_sha256'])

    def test_duplicate_json_fields_are_rejected(self):
        with self.assertRaises(ValueError):
            guest.json_unique(b'{"status":"FAIL","status":"PASS"}')

    def test_staging_preserves_bytes_executable_modes_and_manifest_hashes(self):
        source = make_source({'gradlew': b'#!/bin/sh\nexit 0\n', 'build.gradle.kts': b'// fixture\n'})
        for relative, _ in source['source_manifest']:
            self.write('source/' + relative, {'gradlew': b'#!/bin/sh\nexit 0\n',
                                             'build.gradle.kts': b'// fixture\n'}[relative])
        (self.root / 'source/gradlew').chmod(0o755)
        archive = self.root / 'staged.tar.gz'
        result = host.stage_source(self.root / 'source', source, archive)
        self.assertEqual(2, result['files'])
        self.assertEqual(checksum(archive.read_bytes()), result['sha256'])
        guest.extract_bounded(archive, self.root / 'expanded', byte_limit=1024)
        self.assertTrue(guest.source_matches(self.root / 'expanded', source['source_manifest']))
        self.assertTrue((self.root / 'expanded/gradlew').stat().st_mode & 0o100)

    def test_source_inventory_detects_extra_missing_and_changed_files(self):
        path = self.write('source/build.gradle.kts', b'original')
        source = make_source({'build.gradle.kts': b'original'})
        self.assertTrue(guest.source_matches(path.parent, source['source_manifest']))
        extra = self.write('source/extra.kt')
        self.assertFalse(guest.source_matches(path.parent, source['source_manifest']))
        extra.unlink()
        path.write_bytes(b'changed')
        self.assertFalse(guest.source_matches(path.parent, source['source_manifest']))
        path.unlink()
        self.assertFalse(guest.source_matches(path.parent, source['source_manifest']))

    def test_only_known_gradle_cache_parents_may_be_ignored_after_cleanup(self):
        path = self.write('source/build.gradle.kts', b'x')
        source = make_source({'build.gradle.kts': b'x'})
        self.write('source/.gradle/cache/entry')
        self.assertFalse(guest.source_matches(path.parent, source['source_manifest']))
        self.assertTrue(guest.source_matches(path.parent, source['source_manifest'], allow_gradle_caches=True))
        self.write('source/unknown/.gradle/extra.kt')
        self.assertFalse(guest.source_matches(path.parent, source['source_manifest'], allow_gradle_caches=True))

    def test_source_inventory_never_follows_symlink_directories(self):
        path = self.write('source/build.gradle.kts', b'x')
        (path.parent / 'shared').symlink_to(self.root, target_is_directory=True)
        source = make_source({'build.gradle.kts': b'x'})
        self.assertFalse(guest.source_matches(path.parent, source['source_manifest']))

    def test_root_receipt_requires_digest_completed_cycle_and_unchanged_source(self):
        source = make_source({'build.gradle.kts': b'x'})
        receipt = {'source_before': source, 'source_after': source,
                   'source_changed_during_cycle': False, 'finished_at': 'synthetic-completion'}
        relative = 'remediation-runs/2026-09-07-local-readiness/evidence/cycle/receipt.json'
        path = self.write(relative, json.dumps(receipt).encode())
        with mock.patch.object(host, 'REPO', self.root):
            self.assertEqual(source, host.load_source_receipt(path, checksum(path.read_bytes()), guest)[0])
            with self.assertRaises(ValueError):
                host.load_source_receipt(path, '0' * 64, guest)
            receipt['source_changed_during_cycle'] = True
            path.write_text(json.dumps(receipt))
            with self.assertRaises(ValueError):
                host.load_source_receipt(path, checksum(path.read_bytes()), guest)

    def test_generated_cleanup_is_precise_and_refuses_symlink_ancestors(self):
        self.write('source/shared/session/build/generated.txt')
        kept = self.write('source/shared/session/source.kt')
        repo = self.root / 'source'
        self.assertTrue(guest.remove_generated_output(repo, repo / 'shared/session/build'))
        self.assertTrue(kept.is_file())
        (repo / 'shared/alias').symlink_to(kept.parent, target_is_directory=True)
        with self.assertRaises(RuntimeError):
            guest.remove_generated_output(repo, repo / 'shared/alias/build')
        self.assertFalse(guest.remove_generated_output(repo, repo / 'build'))

    def test_current_source_check_binds_git_identity_inventory_modes_and_cleanup_probes(self):
        path = self.write('build.gradle.kts', b'x')
        source = make_source({'build.gradle.kts': b'x'})
        replies = {'commit': source['commit'], 'tree': source['tree'], 'branch': source['branch'],
                   'changed-names': '', 'public-diff': '', 'inventory': 'build.gradle.kts\x00remediation-runs/receipt.json\x00'}

        def command(label, _args, **_kwargs):
            return replies[label.removeprefix('source-').rsplit('-', 1)[0]]

        commands = types.SimpleNamespace(command=mock.Mock(side_effect=command))
        with mock.patch.object(host, 'REPO', self.root):
            before = host.verify_current_source(commands, source, 'before')
            host.verify_current_source(commands, source, 'after', expected_modes=before['mode_manifest_sha256'], cleanup=True)
            self.assertTrue(all(call.kwargs['cleanup'] for call in commands.command.call_args_list[-6:]))
            path.chmod(0o755)
            with self.assertRaisesRegex(ValueError, 'modes'):
                host.verify_current_source(commands, source, 'after', expected_modes=before['mode_manifest_sha256'])
            path.chmod(0o644)
            self.write('shared/new/resource.txt')
            replies['inventory'] += 'shared/new/resource.txt\x00'
            with self.assertRaisesRegex(ValueError, 'build-consumed'):
                host.verify_current_source(commands, source, 'after')


class ArchiveBoundaryTests(PureCase):
    def test_regular_files_and_bounded_internal_jdk_link_are_accepted(self):
        archive = self.archive([('jdk/lib/data', 'file', b'bytes'), ('jdk/link', 'symlink', 'lib/data')])
        result = guest.extract_bounded(archive, self.root / 'out', byte_limit=5, allow_links=True)
        self.assertEqual(5, result['expanded_bytes'])
        self.assertEqual(b'bytes', (self.root / 'out/jdk/link').read_bytes())

    def test_traversal_absolute_and_unsafe_nodes_are_rejected(self):
        for name, kind, value in (('../escape', 'file', b'x'), ('/absolute', 'file', b'x'),
                                  ('file', 'hardlink', 'other'), ('pipe', 'fifo', ''),
                                  ('dir\\file', 'file', b'x')):
            with self.subTest(name=name, kind=kind):
                archive = self.archive([(name, kind, value)])
                output = self.root / ('out-' + str(len(list(self.root.iterdir()))))
                with self.assertRaises(ValueError):
                    guest.extract_bounded(archive, output, byte_limit=32)
        self.assertFalse((self.root / 'escape').exists())

    def test_duplicate_members_and_file_or_link_ancestors_are_rejected(self):
        scenarios = [([('same', 'file', b'a'), ('same', 'file', b'b')], False),
                     ([('parent', 'file', b'a'), ('parent/child', 'file', b'b')], False),
                     ([('parent', 'symlink', 'other'), ('parent/child', 'file', b'b')], True)]
        for index, (rows, links) in enumerate(scenarios):
            with self.subTest(index=index), self.assertRaises(ValueError):
                guest.extract_bounded(self.archive(rows), self.root / str(index), byte_limit=32, allow_links=links)

    def test_symlinks_require_opt_in_and_never_escape(self):
        for index, (link, allow) in enumerate((('inside', False), ('../../escape', True), ('/etc/passwd', True))):
            with self.subTest(link=link), self.assertRaises(ValueError):
                guest.extract_bounded(self.archive([('link', 'symlink', link)]), self.root / str(index),
                                      byte_limit=32, allow_links=allow)

    def test_expansion_ceiling_and_symlink_destination_parent_are_rejected(self):
        archive = self.archive([('file', 'file', b'1234')])
        with self.assertRaises(ValueError):
            guest.extract_bounded(archive, self.root / 'out', byte_limit=3)
        (self.root / 'alias').symlink_to(self.root / 'out', target_is_directory=True)
        with self.assertRaises(ValueError):
            guest.extract_bounded(archive, self.root / 'alias/child', byte_limit=4)


class EnvironmentAndOwnershipTests(PureCase):
    def test_private_environment_wraps_ssh_and_scp_without_inheriting_secrets(self):
        owned = types.SimpleNamespace(path=self.root, attest=mock.Mock())
        with mock.patch.dict(os.environ, {'SSH_AUTH_SOCK': '/private-agent', 'AWS_SECRET_ACCESS_KEY': 'synthetic'}):
            environment = host.private_environment(owned)
        owned.attest.assert_called_once_with()
        self.assertNotIn('SSH_AUTH_SOCK', environment)
        self.assertNotIn('AWS_SECRET_ACCESS_KEY', environment)
        self.assertEqual(str(self.root / 'home'), environment['HOME'])
        self.assertEqual(str(self.root / 'lima'), environment['LIMA_HOME'])
        for tool in ('ssh', 'scp'):
            script = (self.root / 'bin' / tool).read_text()
            self.assertIn('exec /usr/bin/' + tool + ' ' + ' '.join(host.NO_MASTER) + ' "$@"', script)
            self.assertFalse((self.root / 'bin' / tool).stat().st_mode & 0o077)

    def test_actual_disabled_ssh_rendering_with_omitted_fields_is_accepted(self):
        text = 'controlmaster false\ncontrolpersist no\nidentityagent none\nforwardagent no\n'
        result = host.verify_no_multiplexing(text)
        self.assertEqual(['controlpath', 'usekeychain'], result['omitted_by_installed_ssh'])
        result = host.verify_no_multiplexing(text + 'controlpath none\nusekeychain no\n')
        self.assertEqual([], result['omitted_by_installed_ssh'])

    def test_persistence_zero_yes_missing_or_ambiguous_ssh_fields_are_rejected(self):
        text = 'controlmaster false\ncontrolpersist no\nidentityagent none\nforwardagent no\n'
        candidates = [text.replace('controlpersist no', 'controlpersist 0'),
                      text.replace('controlpersist no', 'controlpersist yes'),
                      text.replace('identityagent none\n', ''), text + 'controlmaster false\n',
                      text + 'controlpath /stale/socket\n', text + 'usekeychain yes\n']
        for candidate in candidates:
            with self.subTest(candidate=candidate), self.assertRaises(ValueError):
                host.verify_no_multiplexing(candidate)

    def test_footprint_failure_does_not_block_finalizer_checks(self):
        owned = types.SimpleNamespace(path=self.root, attest=mock.Mock())
        footprint = host.Footprint(owned, self.root)
        with mock.patch.object(host, 'allocated_bytes', return_value=host.MAX_FOOTPRINT), \
                mock.patch.object(host.shutil, 'disk_usage', return_value=types.SimpleNamespace(free=host.MIN_HOST_FREE)):
            with self.assertRaises(RuntimeError):
                footprint.check(force=True)
            self.assertTrue(footprint.failed)
            footprint.check(force=True)
        footprint.closing = True
        with mock.patch.object(host, 'allocated_bytes', side_effect=AssertionError('must not repeat')):
            footprint.check(force=True)

    def test_low_free_space_stops_before_a_build(self):
        owned = types.SimpleNamespace(path=self.root, attest=mock.Mock())
        with mock.patch.object(host, 'allocated_bytes', return_value=0), \
                mock.patch.object(host.shutil, 'disk_usage', return_value=types.SimpleNamespace(free=0)), \
                self.assertRaises(RuntimeError):
            host.Footprint(owned, self.root).check(force=True)

    def test_holder_probe_requires_empty_exit_one_and_never_signals(self):
        owned = types.SimpleNamespace(path=self.root, attest=mock.Mock())
        commands = types.SimpleNamespace(receipts=[{'exit_code': 1}], command=mock.Mock(return_value=''))
        self.assertTrue(host.verify_no_holders(commands, owned)['output_empty'])
        self.assertTrue(commands.command.call_args.kwargs['cleanup'])
        for code, text in ((0, ''), (1, 'remaining-holder'), (2, 'permission denied')):
            commands.receipts = [{'exit_code': code}]
            commands.command.return_value = text
            with self.subTest(code=code, text=text), self.assertRaises(RuntimeError):
                host.verify_no_holders(commands, owned)

    def test_graceful_stop_uses_exact_attested_token_not_pidfile(self):
        executable = Path('/public/limactl')
        record = types.SimpleNamespace(pid=42, command=str(executable), lifetime=(42, 123),
                                       token=(0, 1, 2, 3, 4, 5, 6, 7), started=123)
        backend = types.SimpleNamespace(read=mock.Mock(return_value=record), signal=mock.Mock(return_value=True))
        registry = types.SimpleNamespace(refresh=mock.Mock(), live=mock.Mock(return_value=[record]),
                                         backend=backend, events=[])
        launch = types.SimpleNamespace(pid=42, retired=False, poll=mock.Mock(return_value=0))
        host.stop_foreground(types.SimpleNamespace(processes=registry), launch, executable)
        backend.signal.assert_called_once_with(record, signal.SIGINT)
        self.assertEqual(7, registry.events[0]['generation'])
        self.assertEqual(int(signal.SIGINT), registry.events[0]['signal'])

    def test_graceful_stop_rejects_exec_or_lifetime_change_before_signal(self):
        original = types.SimpleNamespace(pid=42, command='/public/limactl', lifetime=(42, 1),
                                         token=(0,) * 8, started=1)
        for replacement in (types.SimpleNamespace(**{**vars(original), 'lifetime': (42, 2)}),
                            types.SimpleNamespace(**{**vars(original), 'token': (1,) * 8})):
            backend = types.SimpleNamespace(read=mock.Mock(return_value=replacement), signal=mock.Mock())
            registry = types.SimpleNamespace(refresh=mock.Mock(), live=mock.Mock(return_value=[original]),
                                             backend=backend, events=[])
            launch = types.SimpleNamespace(pid=42, retired=False, poll=mock.Mock(return_value=0))
            with self.subTest(replacement=replacement), self.assertRaises(RuntimeError):
                host.stop_foreground(types.SimpleNamespace(processes=registry), launch, Path(original.command))
            backend.signal.assert_not_called()

    def test_approved_import_compiles_current_bytes_without_pyc_or_old_module(self):
        path = self.write('synthetic_control.py', b'VALUE = 73\n')
        name = 'synthetic_approved_linux_control'
        self.addCleanup(sys.modules.pop, name, None)
        module = host.load_approved_module(name, path, checksum(path.read_bytes()))
        self.assertEqual(73, module.VALUE)
        with self.assertRaises(RuntimeError):
            host.load_approved_module(name, path, checksum(path.read_bytes()))
        with self.assertRaises(RuntimeError):
            host.load_approved_module(name + '_wrong', path, '0' * 64)

    def test_finalizer_stops_vm_before_source_probes_and_cleans_probe_workers(self):
        events = []
        registry = types.SimpleNamespace(shutdown=mock.Mock(side_effect=lambda: events.append('shutdown') or []),
                                         receipts=mock.Mock(return_value=[]), events=[])
        commands = types.SimpleNamespace(processes=registry, receipts=[])
        report = {'status': 'PASS', 'cleanup_errors': []}
        with mock.patch.object(host, 'stop_foreground', side_effect=lambda *args: events.append('stop-vm')), \
                mock.patch.object(host, 'verify_current_source', side_effect=lambda *args, **kwargs: events.append('source') or {}) as source, \
                mock.patch.object(host, 'verify_no_holders', side_effect=lambda *args: events.append('holders') or {}):
            host.finish_task_workers(commands, object(), Path('/public/limactl'), object(), {}, report)
        self.assertEqual(['stop-vm', 'shutdown', 'source', 'holders', 'shutdown'], events)
        self.assertTrue(source.call_args.kwargs['cleanup'])
        self.assertEqual([], report['cleanup_errors'])

    def test_finalizer_preserves_failures_and_does_not_skip_second_shutdown(self):
        for failure in ('vm', 'first-shutdown', 'source', 'holders'):
            registry = types.SimpleNamespace(shutdown=mock.Mock(side_effect=[RuntimeError('first stop'), []]
                                                              if failure == 'first-shutdown' else [[], []]),
                                             receipts=mock.Mock(return_value=[]), events=[])
            commands = types.SimpleNamespace(processes=registry, receipts=[])
            report = {'status': 'PASS', 'cleanup_errors': []}
            with mock.patch.object(host, 'stop_foreground', side_effect=RuntimeError('vm stop') if failure == 'vm' else None), \
                    mock.patch.object(host, 'verify_current_source', side_effect=RuntimeError('source changed') if failure == 'source' else None), \
                    mock.patch.object(host, 'verify_no_holders', side_effect=RuntimeError('holder retained') if failure == 'holders' else None) as holders:
                host.finish_task_workers(commands, object(), Path('/public/limactl'), object(), {}, report)
            with self.subTest(failure=failure):
                self.assertEqual(2, registry.shutdown.call_count)
                self.assertTrue(report['cleanup_errors'] or report.get('source_preservation_failure'))
                if failure in ('vm', 'first-shutdown'):
                    holders.assert_not_called()

    def test_approval_requires_exact_local_and_shared_controls_and_root_selected_hash(self):
        directory, shared = self.root / 'controls', self.root / 'shared'
        checksums, shared_checksums = {}, {}
        for filename in host.SELF_CONTROLS:
            path = self.write('controls/' + filename, b'# synthetic reviewed bytes\n')
            checksums[filename] = checksum(path.read_bytes())
        for filename in host.CONTROL_HASHES:
            path = self.write('shared/' + filename, b'# synthetic reviewed shared bytes\n')
            shared_checksums[filename] = checksum(path.read_bytes())
        value = {'schema': 1, 'controls': checksums, 'shared_controls': shared_checksums}
        path = self.write('controls/approval.json', json.dumps(value).encode())
        with mock.patch.object(host, 'HERE', directory), mock.patch.object(host, 'CONTROLS', shared), \
                mock.patch.object(host, 'CONTROL_HASHES', shared_checksums):
            self.assertEqual(value, host.verify_approval(path, checksum(path.read_bytes())))
            with self.assertRaises(ValueError):
                host.verify_approval(path, '0' * 64)
            (directory / 'guest_verification.py').write_text('# unapproved change\n')
            with self.assertRaises(ValueError):
                host.verify_approval(path, checksum(path.read_bytes()))


class GuestCommandTests(PureCase):
    def commands(self):
        evidence = self.root / 'evidence'
        evidence.mkdir()
        return guest.Commands(self.root, evidence, {'PATH': '/synthetic'}, 1e20)

    def fake_process(self, exit_code=0):
        process = types.SimpleNamespace(pid=123, returncode=None)

        def wait(timeout):
            self.assertGreater(timeout, 0)
            process.returncode = exit_code
            return exit_code

        process.wait = mock.Mock(side_effect=wait)
        return process

    def test_completed_foreground_command_has_actual_exit_and_log_hash(self):
        commands = self.commands()
        process = self.fake_process()
        with mock.patch.object(guest.subprocess, 'Popen', return_value=process) as spawn, \
                mock.patch.object(guest, 'non_reaping_status', return_value=object()), \
                mock.patch.object(guest, 'group_members', return_value=[]):
            self.assertEqual(0, commands.run('synthetic', ['/synthetic/tool']))
        self.assertTrue(spawn.call_args.kwargs['start_new_session'])
        self.assertEqual(checksum(b''), commands.receipts[0]['sha256'])
        self.assertEqual(0, commands.receipts[0]['exit_code'])
        process.wait.assert_called_once_with(timeout=5)

    def test_cancel_before_creation_never_launches_a_child(self):
        commands = self.commands()
        with mock.patch.object(guest, 'CANCELLED', True), self.assertRaises(InterruptedError):
            commands.run('cancelled', ['/synthetic/tool'])
        self.assertEqual([], commands.receipts)
        self.assertEqual([], list(commands.evidence.iterdir()))

    def test_completed_command_cannot_bypass_the_log_ceiling_by_exiting_quickly(self):
        commands, process = self.commands(), self.fake_process()

        def spawn(*_args, **kwargs):
            kwargs['stdout'].write('oversized log')
            kwargs['stdout'].flush()
            return process

        def stop(child):
            child.returncode = 0

        with mock.patch.object(guest.subprocess, 'Popen', side_effect=spawn), \
                mock.patch.object(guest, 'MAX_LOG', 3), \
                mock.patch.object(guest, 'non_reaping_status', return_value=object()), \
                mock.patch.object(guest, 'stop_waitable_group', side_effect=stop), \
                self.assertRaisesRegex(RuntimeError, 'log ceiling'):
            commands.run('fast-output', ['/synthetic/tool'])
        self.assertIn('log ceiling', commands.receipts[0]['failure'])

    def test_timeout_runs_group_cleanup_and_retains_failure_receipt(self):
        commands, process = self.commands(), self.fake_process(-15)

        def stop(child):
            self.assertIs(process, child)
            child.returncode = -15

        with mock.patch.object(guest.subprocess, 'Popen', return_value=process), \
                mock.patch.object(guest, 'non_reaping_status', return_value=None), \
                mock.patch.object(guest.time, 'monotonic', side_effect=[1, 1, 100]), \
                mock.patch.object(guest, 'stop_waitable_group', side_effect=stop) as cleanup, \
                self.assertRaises(TimeoutError):
            commands.run('timeout', ['/synthetic/tool'], timeout=3)
        cleanup.assert_called_once_with(process)
        self.assertEqual(-15, commands.receipts[0]['exit_code'])
        self.assertIn('TimeoutError', commands.receipts[0]['failure'])

    def test_group_cleanup_failure_remains_explicit(self):
        commands, process = self.commands(), self.fake_process()
        with mock.patch.object(guest.subprocess, 'Popen', return_value=process), \
                mock.patch.object(guest, 'non_reaping_status', return_value=object()), \
                mock.patch.object(guest, 'group_members', return_value=[123]), \
                mock.patch.object(guest, 'stop_waitable_group', side_effect=RuntimeError('synthetic retained child')), \
                self.assertRaisesRegex(RuntimeError, 'cleanup failed'):
            commands.run('background', ['/synthetic/tool'])
        self.assertIn('retained child', commands.receipts[0]['cleanup_failure'])
        self.assertIsNone(process.returncode)

    def test_guest_group_is_attested_waitable_before_signaling_or_reaping(self):
        process = self.fake_process(-15)
        events, members = [], [123]

        def send(group, signum):
            self.assertEqual(123, group)
            self.assertIsNone(process.returncode)
            events.append(('signal', signum))
            members.clear()

        with mock.patch.object(guest, 'non_reaping_status', side_effect=lambda child: events.append(('waitable', child.pid))), \
                mock.patch.object(guest, 'group_members', side_effect=lambda group: list(members)), \
                mock.patch.object(guest.os, 'killpg', side_effect=send):
            guest.stop_waitable_group(process)
        self.assertEqual([('waitable', 123), ('signal', signal.SIGTERM)], events)
        process.wait.assert_called_once_with(timeout=5)

    def test_lost_waitable_authority_prevents_all_group_signals(self):
        process = self.fake_process()
        with mock.patch.object(guest, 'non_reaping_status', side_effect=ChildProcessError('not owned')), \
                self.assertRaises(ChildProcessError):
            guest.stop_waitable_group(process)
        process.wait.assert_not_called()

    def test_natural_group_exit_between_check_and_signal_is_tolerated_only_with_owned_leader(self):
        process, members = self.fake_process(0), [123]

        def gone(_group, _signum):
            members.clear()
            raise ProcessLookupError('synthetic natural exit')

        with mock.patch.object(guest, 'non_reaping_status', return_value=object()), \
                mock.patch.object(guest, 'group_members', side_effect=lambda group: list(members)), \
                mock.patch.object(guest.os, 'killpg', side_effect=gone):
            guest.stop_waitable_group(process)
        self.assertEqual(0, process.returncode)


class TestReceiptTests(PureCase):
    def test_python_positive_receipt_requires_all_executed_descriptors(self):
        self.assertEqual({'executed': 1, 'passed': 1, 'skipped': 0}, guest.validate_python(python_receipt()))

    def test_python_zero_skipped_expected_failure_count_or_duplicate_is_not_green(self):
        candidates = []
        for update in ({'discovered': [], 'outcomes': [], 'tests_run': 0}, {'tests_run': 2},
                       {'expected_failures': ['test_expected']}, {'loader_errors': ['error']},
                       {'unexpected_successes': ['test_unexpected']}, {'status': 'FAIL'}):
            value = python_receipt()
            value.update(update)
            candidates.append(value)
        value = python_receipt()
        value['outcomes'][0]['status'] = 'SKIP'
        candidates.append(value)
        value = python_receipt()
        value['discovered'] *= 2
        value['outcomes'] *= 2
        value['tests_run'] = 2
        candidates.append(value)
        for value in candidates:
            with self.subTest(value=value), self.assertRaises(ValueError):
                guest.validate_python(value)

    def test_generated_python_harness_executes_a_real_synthetic_test(self):
        calls = []

        class Synthetic(unittest.TestCase):
            def test_recorded(self):
                calls.append('executed')

        loader = types.SimpleNamespace(discover=mock.Mock(return_value=unittest.TestSuite([Synthetic('test_recorded')])), errors=[])
        path = self.root / 'result.json'
        with mock.patch.object(unittest, 'TestLoader', return_value=loader), \
                mock.patch.object(sys, 'argv', ['python_tests.py', str(self.root), str(path)]), \
                contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as exit_result:
            exec(compile(guest.PYTHON_TEST_CONTROL, '<synthetic-harness>', 'exec'), {})
        self.assertEqual(0, exit_result.exception.code)
        self.assertEqual(['executed'], calls)
        self.assertEqual(1, guest.validate_python(json.loads(path.read_text()))['executed'])

    def test_generated_python_harness_cannot_pass_zero_discovery(self):
        loader = types.SimpleNamespace(discover=mock.Mock(return_value=unittest.TestSuite()), errors=[])
        path = self.root / 'result.json'
        with mock.patch.object(unittest, 'TestLoader', return_value=loader), \
                mock.patch.object(sys, 'argv', ['python_tests.py', str(self.root), str(path)]), \
                contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as exit_result:
            exec(compile(guest.PYTHON_TEST_CONTROL, '<synthetic-harness>', 'exec'), {})
        self.assertEqual(1, exit_result.exception.code)
        self.assertEqual('FAIL', json.loads(path.read_text())['status'])

    def test_xml_counts_and_cases_are_independently_checked(self):
        data = xml_bytes([('Synthetic', 'works', 'PASS'), ('Synthetic', 'fails', 'FAIL')])
        result = guest.parse_xml(data, 'module/build/test-results/desktopTest/TEST-Synthetic.xml')
        self.assertEqual((2, 1), (result['tests'], result['failures']))
        with self.assertRaises(ValueError):
            guest.parse_xml(data.replace(b'failures="1"', b'failures="0"'), 'TEST-fixture.xml')
        with self.assertRaises(ValueError):
            guest.parse_xml(data.replace(b'tests="2"', b'tests="3"'), 'TEST-fixture.xml')

    def test_xml_entities_duplicate_or_ambiguous_descriptors_are_rejected(self):
        data = xml_bytes([('Synthetic', 'works', 'PASS')])
        candidates = [b'<!DOCTYPE testsuite []>' + data,
                      xml_bytes([('Synthetic', 'works', 'PASS'), ('Synthetic', 'works', 'PASS')]),
                      data.replace(b' />', b'><failure/><error/></testcase>', 1),
                      data.replace(b' />', b'><nested><failure/></nested></testcase>', 1)]
        for candidate in candidates:
            with self.subTest(candidate=candidate), self.assertRaises((ValueError, guest.ET.ParseError)):
                guest.parse_xml(candidate, 'TEST-fixture.xml')

    def test_desktop_accepts_only_exact_known_physical_skips_and_real_discovery(self):
        source = make_source({'shared/transport-p2p/src/desktopTest/Fixture.kt': b'x'})
        suite = guest.parse_xml(xml_bytes(desktop_cases()), 'shared/transport-p2p/build/test-results/desktopTest/TEST-fixture.xml')
        summary = guest.validate_desktop([suite], source['source_manifest'])
        self.assertEqual(1, summary['passed'])
        self.assertEqual(3, len(summary['skipped']))
        self.assertTrue(summary['discovery_contract_executed'])

    def test_desktop_rejects_duplicate_skip_manifestations_missing_modules_and_missing_discovery(self):
        source = make_source({'shared/transport-p2p/src/desktopTest/Fixture.kt': b'x'})
        scenarios = [desktop_cases() + [(guest.LOOPBACK_CLASS, next(iter(guest.KNOWN_SKIPS)), 'SKIP')],
                     desktop_cases()[1:], desktop_cases()[:-1],
                     desktop_cases() + [('Unexpected', 'newly_skipped', 'SKIP')]]
        for cases in scenarios:
            suite = guest.parse_xml(xml_bytes(cases), 'shared/transport-p2p/build/test-results/desktopTest/TEST-fixture.xml')
            with self.subTest(cases=cases), self.assertRaises(ValueError):
                guest.validate_desktop([suite], source['source_manifest'])
        source['source_manifest'].append(['shared/new/src/commonTest/Unrun.kt', '0' * 64])
        suite = guest.parse_xml(xml_bytes(desktop_cases()), 'shared/transport-p2p/build/test-results/desktopTest/TEST-fixture.xml')
        with self.assertRaises(ValueError):
            guest.validate_desktop([suite], source['source_manifest'])

    def test_skips_and_discovery_cannot_be_spoofed_from_another_module(self):
        source = make_source({'shared/other/src/desktopTest/Fixture.kt': b'x'})
        suite = guest.parse_xml(xml_bytes(desktop_cases()), 'shared/other/build/test-results/desktopTest/TEST-fixture.xml')
        with self.assertRaises(ValueError):
            guest.validate_desktop([suite], source['source_manifest'])


class GuestEvidenceTests(PureCase):
    token, archive_hash, control_hash = '1' * 32, '2' * 64, '3' * 64

    def setUp(self):
        super().setUp()
        self.source = make_source({'shared/transport-p2p/src/desktopTest/Fixture.kt': b'x'})
        guest_root = '/home/parlor/run-' + self.token
        self.rows = [
            ('release-python-tests', ['/usr/bin/python3', '-B', guest_root + '/python_tests.py', guest_root + '/source',
                                      guest_root + '/evidence/python-tests.json'], 0),
            ('productionDesktopCheck', guest.DESKTOP_ARGS, 0),
            ('immediate-gradle-stop', ['./gradlew', '--stop'], 0),
            ('post-cleanup-gradle-stop', ['./gradlew', '--stop'], 0),
        ]
        relative = 'shared/transport-p2p/build/test-results/desktopTest/TEST-fixture.xml'
        data = xml_bytes(desktop_cases())
        self.xml_path = self.write('xml/' + relative, data)
        self.report = {'schema': 1, 'status': 'PASS', 'run_token': self.token,
                       'source': {key: self.source[key] for key in ('commit', 'tree', 'diff_sha256', 'source_manifest_sha256')},
                       'source_manifest_sha256': self.source['source_manifest_sha256'],
                       'source_archive_sha256': self.archive_hash, 'control_sha256': self.control_hash,
                       'control_after_sha256': self.control_hash, 'platform': {'system': 'Linux', 'machine': 'aarch64'},
                       'guest_attestation': {'cpus': 2, 'memory_bytes': 2900000000, 'mount_types': ['ext4', 'proc']},
                       'jdk': {'url': guest.JDK_URL, 'sha256': guest.JDK_SHA, 'bytes': guest.JDK_BYTES},
                       'source_before_matches': True, 'source_after_matches': True,
                       'remaining_java': [], 'java_before_output_cleanup': [], 'remaining_build_outputs': [],
                       'cleanup_errors': [], 'python': python_receipt(),
                       'gradle': {'exit_code': 0, 'xml': [guest.parse_xml(data, relative)], 'started': True}}
        self.write('python-tests.json', json.dumps(self.report['python']).encode())
        self.save()

    def save(self):
        receipts = []
        for index, (label, args, code) in enumerate(self.rows):
            path = self.write(f'{index:03d}-' + label + '.log', b'synthetic log\n')
            receipts.append({'label': label, 'args': list(args), 'exit_code': code,
                             'log': path.name, 'sha256': checksum(path.read_bytes())})
        self.report['commands'] = receipts
        self.write('result.json', json.dumps(self.report).encode())

    def validate(self):
        return host.validate_guest_evidence(self.root, guest, self.source, self.archive_hash,
                                            self.control_hash, self.token)

    def test_complete_bound_receipts_pass_both_narrow_gates(self):
        result = self.validate()
        self.assertEqual('PASS', result['status'])
        self.assertEqual('PASS', result['python']['status'])
        self.assertEqual('PASS', result['desktop']['status'])

    def test_python_pass_survives_a_later_real_desktop_command_failure(self):
        label, args, _ = self.rows[1]
        self.rows[1] = label, args, 1
        self.report['status'] = 'FAIL'
        self.report['failure'] = 'Synthetic actual Desktop configuration failure'
        self.report['gradle']['exit_code'] = 1
        self.save()
        result = self.validate()
        self.assertEqual('FAIL', result['status'])
        self.assertEqual('PASS', result['python']['status'])
        self.assertEqual('FAIL', result['desktop']['status'])

    def test_python_pass_survives_a_blocked_uninvoked_desktop_gate(self):
        self.rows = self.rows[:1]
        self.xml_path.unlink()
        self.report.update(status='FAIL', failure='Synthetic pre-build disk guard')
        self.report['gradle'] = {'status': 'NOT_RUN'}
        self.save()
        result = self.validate()
        self.assertEqual('PASS', result['python']['status'])
        self.assertEqual('BLOCKED', result['desktop']['status'])
        self.assertEqual('FAIL', result['status'])

    def test_wrong_source_control_token_or_platform_is_rejected(self):
        for key, replacement in (('run_token', '9' * 32), ('control_after_sha256', '9' * 64),
                                 ('source_archive_sha256', '9' * 64),
                                 ('platform', {'system': 'Darwin', 'machine': 'arm64'})):
            original = self.report[key]
            self.report[key] = replacement
            self.save()
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.validate()
            self.report[key] = original

    def test_log_hash_or_xml_summary_tamper_is_rejected(self):
        self.write(self.report['commands'][0]['log'], b'tampered')
        with self.assertRaisesRegex(ValueError, 'log'):
            self.validate()
        self.save()
        self.report['gradle']['xml'][0]['tests'] += 1
        self.save()
        with self.assertRaisesRegex(ValueError, 'XML'):
            self.validate()

    def test_missing_immediate_stop_filtered_build_or_failed_stop_cannot_pass_desktop(self):
        scenarios = [self.rows[:2] + self.rows[3:],
                     [self.rows[0], ('productionDesktopCheck', guest.DESKTOP_ARGS + ['--tests', '*One*'], 0), *self.rows[2:]],
                     [*self.rows[:2], ('immediate-gradle-stop', ['./gradlew', '--stop'], 1), self.rows[3]],
                     [*self.rows[:2], ('intervening-command', ['/bin/true'], 0), *self.rows[2:]]]
        for rows in scenarios:
            self.rows = rows
            self.save()
            with self.subTest(rows=rows):
                result = self.validate()
                self.assertEqual('FAIL', result['desktop']['status'])
                self.assertEqual('FAIL', result['status'])

    def test_python_wrong_invocation_or_skipped_test_cannot_pass(self):
        label, args, code = self.rows[0]
        self.rows[0] = label, args + ['--invented'], code
        self.save()
        self.assertEqual('FAIL', self.validate()['python']['status'])
        self.rows[0] = label, args, code
        self.report['python']['outcomes'][0]['status'] = 'SKIP'
        self.write('python-tests.json', json.dumps(self.report['python']).encode())
        self.save()
        self.assertEqual('FAIL', self.validate()['python']['status'])

    def test_source_or_cleanup_failure_prevents_gate_passes(self):
        for key, value in (('source_after_matches', False), ('remaining_java', [{'pid': 123}]),
                           ('remaining_build_outputs', ['build']), ('cleanup_errors', [{'stop': 'failed'}])):
            original = self.report[key]
            self.report[key] = value
            self.save()
            with self.subTest(key=key):
                result = self.validate()
                self.assertNotEqual('PASS', result['python']['status'])
                self.assertNotEqual('PASS', result['desktop']['status'])
                self.assertEqual('FAIL', result['status'])
            self.report[key] = original


if __name__ == '__main__':
    unittest.main(verbosity=2)
