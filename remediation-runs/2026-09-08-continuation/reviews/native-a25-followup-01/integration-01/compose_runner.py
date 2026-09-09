#!/usr/bin/env python3
"""Hash/anchor-bound composition; import never builds, launches, signals or deletes."""
import ast
import difflib
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import sys
import types

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
CAMPAIGN = ROOT / 'remediation-runs/2026-09-07-local-readiness'
COMPANION = CAMPAIGN / 'l08-storage-functional-companion-02'
DRAFT = CAMPAIGN / 'native/l08-app-foundation-draft-01'
DRIVER = COMPANION / 'run_ios_readiness.py'
DRIVER_SHA256 = '9580071ecec4abc8ba375ca01e722eee4245f36132e6a5b8790f5d8bb8456687'
DRAFT_FREEZE = ROOT / 'remediation-runs/2026-09-08-continuation/reviews/a27-failfast-foundation-draft-freeze-01.json'
DRAFT_FREEZE_SHA256 = '72aaebc7d11c9ce04141919976d8b49d77daa16eacf6f3a55c9870385c7104fd'
TOOLCHAIN_HELPER = ROOT / 'scripts/verification/ios-readiness/toolchain_profiles.py'
SUPPORT_CONTROLS = tuple(TOOLCHAIN_HELPER.parent / name for name in (
    'test_toolchain_profiles.py', 'test_native_command_failures.py', 'test_foundation_toolchain_binding.py',
    'simulator_lifecycle.py', 'test_simulator_lifecycle.py', 'test_simulator_lifecycle_integration.py'))
REFERENCE_PINS = {
    'run_control.py': '58e8daf875eaf8061ed017e10cc8c8065eb49292866b6741227814282a1b8e9f',
    'FoundationProtectionControl.m': 'a6b025d7b77c3b1b1de57b93869d0beff6b4cc9e365eb5672ffd2be9d52013f2',
}
OWN_FILES = ('compose_runner.py', 'test_composition.py', 'run_controls.py', 'README.md')
UUID = re.compile(r'[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}\Z')
TEMP = re.compile(r'parlor-audit-ios-readiness-[0-9]{2}-[A-Za-z0-9_-]{1,64}\Z')
COLLECTION = 'COLLECTION_VALIDATED_NOT_L08_PASS'

# Literal exact replacements: the independent reviewer receives the full rendered diff.
# No AST rewriting, wildcard replacement, gate substitution or on-disk runner clone.
TRANSFORMS = (
    ('''def control_files():
    if BINDING is None:
        raise RuntimeError('An explicit campaign source binding is required')
    return sorted([path for path in HERE.iterdir() if path.is_file() and
                   (path.suffix in {'.py', '.in', '.md'} or path.name == 'inherited-controls.json')]) + [TOOLCHAIN_HELPER, LIFECYCLE_HELPER, *LIFECYCLE_TESTS, BINDING]
''', '''def control_files():
    return foundation.control_files(BINDING)
'''),
    ("execution_kind='manifest-owned-copy-ios-l08-storage-functional-companion'",
     "execution_kind='manifest-owned-copy-ios-l08-app-foundation-composition'"),
    ('''        copied_source_manifest = None
''', '''        copied_source_manifest = None
        receipt['app_foundation_preservation'] = dict(status='NOT_RUN', files=[])
        receipt['app_foundation_collection'] = dict(status='NOT_RUN')
'''),
    ('''            save()  # Durable incomplete receipt before any allocation/native work.
''', '''            save()  # Durable incomplete receipt before any allocation/native work.
            foundation.record_transform(dest, receipt)
            save()
'''),
    ('''                temp = Path(raw_temp)
                receipt['allocated_temporary_path'] = raw_temp
''', '''                temp = Path(raw_temp)
                receipt['app_foundation_raw_custody'] = foundation.capture_custody(temp)
                receipt['allocated_temporary_path'] = raw_temp
'''),
    ('''            temp = temp.resolve()
            receipt['owned_temporary_directory'] = str(temp)
''', '''            temp = temp.resolve()
            receipt['app_foundation_copy_custody'] = foundation.canonical_custody(
                temp, receipt['app_foundation_raw_custody'])
            receipt['owned_temporary_directory'] = str(temp)
'''),
    ('''            changes = changed + list(MODIFIED_KOTLIN)
            diff = ''.join(''.join(difflib.unified_diff((ROOT / path).read_text().splitlines(True),
                (temp / 'copy' / path).read_text().splitlines(True),
                fromfile='original/' + path, tofile='audit-copy/' + path)) for path in changes)
            for addition in ADDITIONS:
                diff += ''.join(difflib.unified_diff([], (temp / 'copy' / addition).read_text().splitlines(True),
                              fromfile='/dev/null', tofile='audit-copy/' + addition))
            (dest / 'copied-source.diff').write_text(diff)
            copied_source_manifest = inspect_copied_manifest(temp / 'copy', manifest, changed)
            write_json(dest / 'copied-source-manifest.json', copied_source_manifest)
''', '''            copied_source_manifest = inspect_copied_manifest(temp / 'copy', manifest, changed)
            copied_source_manifest, app_foundation_binding = foundation.copy.apply_owned_adapter(
                temp / 'copy', copied_source_manifest, receipt['app_foundation_copy_custody'],
                receipt['source_before'], approved, mode,
                Path.home() / 'Library/Developer/CoreSimulator/Devices', toolchain=toolchain_name)
            receipt['app_foundation_copy_binding'] = app_foundation_binding
            write_json(dest / 'app-foundation-copy-binding.json', app_foundation_binding)
            changes = changed + list(MODIFIED_KOTLIN)
            diff = ''.join(''.join(difflib.unified_diff((ROOT / path).read_text().splitlines(True),
                (temp / 'copy' / path).read_text().splitlines(True),
                fromfile='original/' + path, tofile='audit-copy/' + path)) for path in changes)
            for addition in (*ADDITIONS, *foundation.copy.ADDITIONS):
                diff += ''.join(difflib.unified_diff([], (temp / 'copy' / addition).read_text().splitlines(True),
                              fromfile='/dev/null', tofile='audit-copy/' + addition))
            (dest / 'copied-source.diff').write_text(diff)
            write_json(dest / 'copied-source-manifest.json', copied_source_manifest)
'''),
    ('''                    lambda: preserve_postbuild_raw(receipt, dest, temp, uuid, command, save))
''', '''                    lambda: preserve_postbuild_raw(receipt, dest, temp, uuid, command, save,
                        extra_container=lambda container: foundation.preserve(receipt, dest, uuid, container)))
'''),
    ('''            available_records = available_run_records(dest, mode)
''', '''            foundation.bind_available(receipt, dest, uuid, built_inventory, installed_inventory)
            save()
            available_records = available_run_records(dest, mode)
'''),
    ('''            receipt['runtime_evidence_status'] = (
                'FUNCTIONAL_COMPANION_VERIFIED' if runtime_complete(receipt, xcode) else 'FAIL')
''', '''            foundation.require_collection(receipt)
            receipt['runtime_evidence_status'] = (
                'FUNCTIONAL_COMPANION_VERIFIED' if runtime_complete(receipt, xcode) else 'FAIL')
'''),
    ('''                if uuid is not None:
                    # XCTest itself defers app.terminate(). Shutting down the
''', '''                if uuid is not None:
                    if gradle_attempted:
                        foundation_preserved = stage('preserve-app-foundation-before-device-cleanup', lambda:
                              foundation.preserve_before_cleanup(receipt, dest, uuid, command)) is True
                        receipt['postbuild_evidence_preserved'] = (
                            receipt.get('postbuild_evidence_preserved') is True and foundation_preserved)
                        def persist_preservation_ack():
                            save()
                            return True
                        if stage('persist-postbuild-evidence-ack', persist_preservation_ack) is not True:
                            receipt['postbuild_evidence_preserved'] = False
                    # XCTest itself defers app.terminate(). Shutting down the
'''),
    ('''                receipt.update(classify_final_companion(receipt))
''', '''                receipt.update(classify_final_companion(receipt))
                foundation.restrict_final(receipt)
'''),
)


def require(value, reason):
    if not value:
        raise RuntimeError('App Foundation composition: ' + reason)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def raw_file(path, maximum):
    path = Path(path).absolute()
    before = path.lstat()
    require(path.resolve() == path and stat.S_ISREG(before.st_mode) and before.st_uid == os.getuid() and
            before.st_nlink == 1 and 0 < before.st_size <= maximum, 'bounded-owned-file')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    try:
        opened = os.fstat(fd)
        require((opened.st_dev, opened.st_ino, opened.st_size) ==
                (before.st_dev, before.st_ino, before.st_size), 'file-replaced')
        with os.fdopen(fd, 'rb', closefd=False) as source:
            data = source.read(maximum + 1)
        after = os.fstat(fd)
        require(0 < len(data) <= maximum and opened.st_size == after.st_size == len(data) and
                opened.st_mtime_ns == after.st_mtime_ns, 'file-mutated')
        return data
    finally:
        os.close(fd)


def render_source(original):
    require(isinstance(original, str) and digest(original.encode()) == DRIVER_SHA256, 'companion-driver-drift')
    for before, after in TRANSFORMS:
        require(original.count(before) == 1, 'missing-or-ambiguous-composition-anchor')
        original = original.replace(before, after)
    ast.parse(original, filename=str(DRIVER))
    return original


def frozen_draft_files(freeze):
    require(isinstance(freeze, dict) and freeze.get('schema_version') == 2 and
            freeze.get('directory') == str(DRAFT.relative_to(ROOT)), 'draft-freeze-root')
    records = freeze.get('source_files')
    require(isinstance(records, list) and len(records) == 9, 'draft-freeze-inventory')
    draft_files = []
    for row in records:
        require(isinstance(row, dict) and isinstance(row.get('path'), str) and
                bool(row['path']) and PurePosixPath(row['path']).parts == (row['path'],) and
                '\\' not in row['path'] and row['path'] not in ('.', '..') and
                isinstance(row.get('sha256'), str) and re.fullmatch(r'[a-f0-9]{64}', row['sha256']),
                'draft-freeze-relative-path')
        path = DRAFT / row['path']
        require(path.parent == DRAFT and digest(raw_file(path, 131072)) == row['sha256'], 'frozen-draft-drift')
        draft_files.append(path)
    require(len(set(draft_files)) == 9, 'draft-freeze-inventory')
    return draft_files


def control_files(binding):
    require(binding is not None, 'explicit-source-binding-required')
    require(digest(raw_file(DRIVER, 131072)) == DRIVER_SHA256, 'companion-driver-drift')
    freeze_bytes = raw_file(DRAFT_FREEZE, 65536)
    require(digest(freeze_bytes) == DRAFT_FREEZE_SHA256, 'draft-freeze-drift')
    draft_files = frozen_draft_files(json.loads(freeze_bytes))
    references = [CAMPAIGN / 'reviews/l08-native-foundation-control-03' / name for name in REFERENCE_PINS]
    for path in references:
        require(digest(raw_file(path, 131072)) == REFERENCE_PINS[path.name], 'reference-drift')
    inherited = [p for p in COMPANION.iterdir() if p.is_file() and
                 (p.suffix in {'.py', '.in', '.md'} or p.name == 'inherited-controls.json')]
    files = inherited + draft_files + references + [TOOLCHAIN_HELPER, DRAFT_FREEZE, Path(binding)] + \
        list(SUPPORT_CONTROLS) + [HERE / name for name in OWN_FILES]
    require(len(set(files)) == len(files), 'duplicate-control-input')
    return sorted(files)


def control_manifest(binding):
    return [dict(path=str(path.relative_to(ROOT)), sha256=digest(raw_file(path, 4 * 1024 * 1024)))
            for path in control_files(binding)]


def capture_custody(path):
    path = Path(path).absolute(); value = path.lstat()
    require(TEMP.fullmatch(path.name) and stat.S_ISDIR(value.st_mode) and value.st_uid == os.getuid() and
            stat.S_IMODE(value.st_mode) == 0o700 and not path.is_symlink(), 'fresh-allocation-custody')
    return dict(path=str(path), device=value.st_dev, inode=value.st_ino, uid=value.st_uid)


def canonical_custody(path, before):
    path = Path(path).absolute(); after = capture_custody(path)
    require(isinstance(before, dict) and set(before) == {'path', 'device', 'inode', 'uid'} and
            Path(before['path']).resolve() == path and path.resolve() == path and
            all(after[key] == before[key] for key in ('device', 'inode', 'uid')), 'canonical-allocation-replaced')
    return after


class Hooks:
    def __init__(self, source, copy_module, receipt_module):
        self.source, self.copy, self.schema = source, copy_module, receipt_module

    control_files = staticmethod(control_files)
    capture_custody = staticmethod(capture_custody)
    canonical_custody = staticmethod(canonical_custody)

    def record_transform(self, evidence, receipt):
        original = raw_file(DRIVER, 131072).decode()
        require(self.source == render_source(original), 'executing-source-drift')
        diff = ''.join(difflib.unified_diff(original.splitlines(True), self.source.splitlines(True),
            fromfile=str(DRIVER.relative_to(ROOT)), tofile='in-memory/composed-run_ios_readiness.py'))
        with (evidence / 'composed-runner.diff').open('x') as output:
            output.write(diff)
        receipt['app_foundation_composition'] = dict(original_sha256=DRIVER_SHA256,
            generated_runner_sha256=digest(self.source.encode()), diff_sha256=digest(diff.encode()),
            diff='composed-runner.diff', original_strict_l08='UNCHANGED_NOT_SATISFIED')

    def context(self, raw):
        value = self.schema.base_validator().decode_json(raw, 262144)
        require(isinstance(value, dict) and set(value) == {'schemaVersion', 'runToken', 'scenario', 'completed', 'observations'} and
                type(value['schemaVersion']) is int and value['schemaVersion'] == 6 and value['scenario'] == 'readiness' and
                type(value['completed']) is bool and isinstance(value['runToken'], str) and UUID.fullmatch(value['runToken']) and
                isinstance(value['observations'], list) and 1 <= len(value['observations']) <= 256, 'readiness-context')
        first = value['observations'][0]
        require(isinstance(first, dict) and first.get('phase') == 'before_main' and first.get('fixture') == 'existing' and
                type(first.get('ordinal')) is int and first['ordinal'] == 1 and
                isinstance(first.get('boot'), str) and UUID.fullmatch(first['boot']), 'first-readiness-boot')
        return dict(run_token=value['runToken'], process_boot=first['boot'])

    def preserve(self, receipt, evidence, device, container):
        state = receipt['app_foundation_preservation']
        if state['status'] != 'NOT_RUN':
            return
        state.update(status='RUNNING', files=[])
        try:
            prefix = Path.home() / 'Library/Developer/CoreSimulator/Devices' / device / 'data/Containers/Data/Application'
            require(UUID.fullmatch(device) and container.parent == prefix and UUID.fullmatch(container.name) and
                    container.is_dir() and container.resolve() == container and container.stat().st_uid == os.getuid(),
                    'owned-app-container')
            target = evidence / 'probe-readiness-result.json'
            raw = raw_file(target if target.exists() or target.is_symlink() else
                           container / 'tmp/parlor-dsc01-readiness-result.json', 262144)
            context = self.context(raw)
            if not target.exists():
                with target.open('xb') as output:
                    output.write(raw)
            receipt['app_foundation_context'] = context
            state['files'] = self.schema.preserve_available(container, evidence, device, context['run_token'],
                                                           toolchain=receipt.get('toolchain_profile'))
            state['status'] = 'PRESERVED' if self.schema.RESULT_NAME in state['files'] else 'UNAVAILABLE'
        except FileNotFoundError:
            state.update(status='UNAVAILABLE', reason='required-context-or-result-unavailable')
        except Exception as error:
            state.update(status='FAIL', reason='owned-receipt-preservation', error_type=type(error).__name__)
        finally:
            # Record partial retention without rereading/overwriting/claiming validation.
            state['files'] = [name for name in (self.schema.RESULT_NAME, self.schema.FAILURE_NAME)
                              if (evidence / name).is_file() and not (evidence / name).is_symlink()]

    def preserve_before_cleanup(self, receipt, evidence, device, command):
        state = receipt['app_foundation_preservation']
        if state['status'] == 'NOT_RUN':
            log = 'app-foundation-final-container.log'
            if command(['xcrun', 'simctl', 'get_app_container', device, 'com.parlor.app.debug', 'data'], log, 45) == 0:
                raw = raw_file(evidence / log, 4096).decode().strip()
                require(raw.startswith('/') and '\n' not in raw and '\r' not in raw, 'container-query-shape')
                self.preserve(receipt, evidence, device, Path(raw).resolve(strict=True))
            else:
                state.update(status='UNAVAILABLE', reason='owned-container-unavailable')
        require(state['status'] not in {'RUNNING', 'FAIL'}, 'preservation-incomplete-before-cleanup')
        return True  # Retention outcome only; collection/runtime verdict is separate.

    def bind_available(self, receipt, evidence, device, built, installed):
        try:
            require(receipt['app_foundation_preservation']['status'] == 'PRESERVED', 'native-observation-unavailable')
            toolchain = self.schema.binding_toolchain(receipt.get('app_foundation_copy_binding'),
                                                       receipt.get('toolchain_profile'))
            value = self.schema.parse_record(self.schema.read_owned_receipt(
                evidence / self.schema.RESULT_NAME, evidence, 32768), toolchain)
            context = receipt['app_foundation_context']
            require(value['process_boot'] == context['process_boot'], 'wrong-first-readiness-boot')
            health_path = evidence / 'parlor-native-readiness-boot-1.json'
            health = (self.schema.base_validator().decode_json(raw_file(health_path, 49152), 49152)
                      if health_path.exists() or health_path.is_symlink() else None)
            log = raw_file(evidence / 'xcodebuild.log', 64 * 1024 * 1024).decode()
            receipt['app_foundation_collection'] = self.schema.bind_collection(value,
                receipt['app_foundation_copy_binding'], built, installed,
                receipt.get('native_uuid_inventory', []), log, device, context['run_token'], health,
                toolchain=toolchain)
        except Exception as error:
            receipt['app_foundation_collection'] = dict(status='FAIL', reason='collection-binding', error_type=type(error).__name__)

    @staticmethod
    def require_collection(receipt):
        require(receipt.get('app_foundation_collection', {}).get('status') == COLLECTION, 'required-collection-not-validated')

    @staticmethod
    def restrict_final(receipt):
        if receipt.get('app_foundation_collection', {}).get('status') != COLLECTION:
            receipt['status'] = 'FAIL'  # Never upgrades any original FAIL/PARTIALLY_VERIFIED result.


def checked_binding(raw):
    path = Path(raw).absolute()
    require(path.is_file() and not path.is_symlink() and path.resolve() == path and
            path.parent.parent == ROOT / 'remediation-runs' and re.fullmatch(r'[a-z0-9-]+', path.parent.name) and
            re.fullmatch(r'[a-z0-9-]+\.json', path.name), 'explicit-campaign-binding')
    return path


def load_frozen_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    manifest_only = len(sys.argv) == 3 and sys.argv[1] == '--control-manifest'
    require(manifest_only or (len(sys.argv) in (4, 5, 6, 7) and re.fullmatch(r'ios-readiness-[0-9]{2}', sys.argv[1])), 'usage')
    binding = checked_binding(sys.argv[2])
    manifest = control_manifest(binding)
    approved = digest(json.dumps(manifest, separators=(',', ':')).encode())
    source = render_source(raw_file(DRIVER, 131072).decode())
    if manifest_only:
        print(json.dumps(dict(control_sha256=approved, files=manifest,
                             generated_runner_sha256=digest(source.encode())), indent=2))
        return 0
    require(sys.argv[3] == approved, 'independent-control-approval-mismatch')
    # No companion imports until every inherited/new input matches explicit approval.
    copy_module = load_frozen_module('parlor_l08_frozen_copy', DRAFT / 'app_foundation_copy.py')
    receipt_module = load_frozen_module('parlor_l08_frozen_receipts', DRAFT / 'app_foundation_receipts.py')
    runner = types.ModuleType('parlor_l08_foundation_composed_runner')
    runner.__file__ = str(DRIVER)  # Preserves original HERE/FIXTURE; no duplicated fixture tree.
    runner.__dict__['foundation'] = Hooks(source, copy_module, receipt_module)
    old_path = list(sys.path)
    try:
        sys.path.insert(0, str(COMPANION))
        exec(compile(source, str(DRIVER), 'exec'), runner.__dict__)
        return runner.main()  # Original lane, approvals, worker/Gradle/device ownership and finalizer.
    finally:
        sys.path[:] = old_path


if __name__ == '__main__':
    import signal
    def interrupted(sig, _frame):
        raise KeyboardInterrupt('signal ' + str(sig))
    signal.signal(signal.SIGTERM, interrupted)
    raise SystemExit(main())
