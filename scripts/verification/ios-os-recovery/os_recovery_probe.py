#!/usr/bin/env python3
"""One owned build, two OS-only XCTest invocations; original A/B controls untouched."""
import builtins
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import stat
import sys
import types

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
NORMAL = ROOT / 'remediation-runs/2026-09-07-local-readiness/native/normal-ios-launch-proposal-01'
FUNCTIONAL = ROOT / 'remediation-runs/2026-09-07-local-readiness/l08-storage-functional-companion-02'
SUPPORT = ROOT / 'scripts/verification/ios-readiness'
NORMAL_SHA = '766087ddf72b50f51b1c4072c64afb1a7dd231f047d26e534817cddf9be295bf'
STAGES = ('build', 'bootstrap', 'proof')
BUDGETS = dict(build=2700, bootstrap=600, proof=900)


def private_modules(prefix, directories):
    """Module-local imports; never replace normal/F's shared global import names."""
    cache = {}

    def load(name):
        if name in cache:
            return cache[name]
        paths = [d / (name + '.py') for d in directories if (d / (name + '.py')).is_file()]
        path = paths[0] if paths else None
        if path is None or path.is_symlink() or path.resolve() != path:
            raise RuntimeError('Missing or redirected private OS helper: ' + name)
        module = types.ModuleType(prefix + name)
        module.__file__ = str(path)
        module.__builtins__ = dict(vars(builtins), __import__=import_private)
        cache[name] = module
        sys.modules[module.__name__] = module  # Private names only, including dataclass introspection.
        before = list(sys.path)
        try:
            exec(compile(path.read_bytes(), str(path), 'exec'), module.__dict__)
        finally:
            sys.path[:] = before
        return module

    def import_private(name, globals=None, locals=None, fromlist=(), level=0):
        if not level and '.' not in name and any((d / (name + '.py')).is_file() for d in directories):
            return load(name)
        return builtins.__import__(name, globals, locals, fromlist, level)

    return load


if hashlib.sha256((NORMAL / 'run_normal_ios_launch.py').read_bytes()).hexdigest() != NORMAL_SHA:
    raise RuntimeError('Unreviewed base normal lane')
normal = private_modules('_os_recovery_normal_', (NORMAL, SUPPORT))('run_normal_ios_launch')
functional = private_modules('_os_recovery_functional_', (FUNCTIONAL,))
local = private_modules('_os_recovery_local_', (HERE,))
receipts, copy_controls = local('os_recovery_receipts'), local('os_recovery_copy')
require = receipts.require
_base_manifest = normal.control_manifest


def control_manifest(binding=None):
    rows = {row['path']: row for row in _base_manifest(binding)}
    extra = [p for d in (FUNCTIONAL, HERE) for p in d.iterdir() if p.is_file() and
             (p.suffix in {'.py', '.in', '.md'} or p.name == 'inherited-controls.json')]
    extra += [ROOT / p for p in ('.github/workflows/production-verification.yml', 'scripts/ci/native_continuation.py')]
    for path in extra:
        require(not path.is_symlink() and path.resolve() == path, 'control path')
        rows[str(path.relative_to(ROOT))] = dict(path=str(path.relative_to(ROOT)), sha256=normal.digest(path))
    return [rows[k] for k in sorted(rows)]


def control_hash(binding=None):
    return hashlib.sha256(json.dumps(control_manifest(binding), separators=(',', ':')).encode()).hexdigest()


normal.control_manifest, normal.control_hash = control_manifest, control_hash
fcopy = functional('copied_sources')
normal.transform_copy = lambda root, ignored, phase: copy_controls.transform_copy(
    root, phase, fcopy, functional('l08_functional_copy'), functional('artifact_inventory'))
normal.inventory_copy = lambda root, binding, digest: fcopy.inspect_copied_manifest(root, binding, copy_controls.SWIFT_CHANGED)
normal.CHANGED = set(copy_controls.SWIFT_CHANGED) | set(fcopy.MODIFIED_KOTLIN)
normal.inspect_copied_inputs_after_build = fcopy.inspect_copied_inputs_after_build


def xcode_arguments(lane, stage):
    selectors = tuple(receipts.METHODS) if stage == 'build' else (stage,)
    return ['xcodebuild', '-project', str(lane.temporary / 'copy/iosApp/iosApp.xcodeproj'), '-scheme', 'iosApp',
        '-configuration', 'Debug', '-sdk', lane.environment['SDK_NAME'], '-destination', 'id=' + lane.uuid,
        '-derivedDataPath', str(lane.temporary / 'DerivedData'), '-resultBundlePath', str(lane.temporary / (stage + '.xcresult')),
        *['-only-testing:iosAppUITests/' + receipts.METHODS[k].removesuffix('()') for k in selectors],
        '-parallel-testing-enabled', 'NO', '-maximum-concurrent-test-simulator-destinations', '1',
        '-disable-concurrent-destination-testing', '-jobs', '1', '-test-timeouts-enabled', 'YES',
        '-default-test-execution-time-allowance', '600' if stage == 'proof' else '300',
        '-maximum-test-execution-time-allowance', '720' if stage == 'proof' else '420',
        *[k + '=' + v for k, v in lane.signing.items()], 'ONLY_ACTIVE_ARCH=YES', 'COMPILER_INDEX_STORE_ENABLE=NO',
        'build-for-testing' if stage == 'build' else 'test-without-building']


class RecoveryLane(normal.Lane):
    def prepare(self):
        self.receipt.update(execution_kind='owned-simulator-os-only-two-invocation-recovery',
            image_observer='NOT_RUN_NORMAL_PROVENANCE', bootstrap_status='NOT_RUN', os_subgate_status='NOT_RUN',
            os_provenance_status='NOT_RUN', os_notice_package_status='NOT_RUN',
            os_recovery_stages={k: dict(attempted=False, status='NOT_RUN') for k in STAGES},
            scope='OS-only bootstrap then independent already-satisfied/per-app AR-English-System-restart proof. '
                  'No normal B/libproc, original five-test A, functional20, host42, native8, physical or Store qualification.')
        super().prepare()
        diff = self.destination / 'copied-source.diff'
        diff.write_text(copy_controls.source_diff(self.temporary / 'copy', fcopy))
        self.receipt.update(copied_source_diff_sha256=normal.digest(diff),
            application_source_transformations=len(normal.CHANGED - {copy_controls.UI_TEST, copy_controls.PROJECT}),
            copied_source_additions=len(fcopy.ADDITIONS), ui_test_source_transformations=1)
        self.save()

    def products(self):
        rows, count = [], 0
        roots = [self.temporary / 'DerivedData/Build/Products'] + [self.temporary / 'copy/composeApp/build' / p for p in
            ('bin/iosSimulatorArm64/debugFramework', 'xcode-frameworks/Debug/' + self.environment['SDK_NAME'])]
        require(roots[0].is_dir(), 'build products absent')
        for root in roots:
            if not root.exists():
                continue
            for path in root.rglob('*'):
                count += 1
                require(count <= 20000, 'product inventory budget')
                resolved = path.resolve(strict=True)
                resolved.relative_to(self.temporary)
                if path.is_file():
                    require(0 <= path.stat().st_size <= 512 * 1024 * 1024, 'product byte budget')
                    rows.append(dict(path=str(path.relative_to(self.temporary)), resolved=str(resolved.relative_to(self.temporary)),
                                     bytes=path.stat().st_size, sha256=normal.digest(path)))
        return sorted(rows, key=lambda row: row['path'])

    def fence(self, label):
        self.owner.stop()
        self.verify_workers()
        require(not self.owner.secondary_errors and not self.errors, 'ownership/cleanup problem before transition')
        require(normal.normalized_identity() == self.receipt['source_before'] and control_hash(self.binding) == self.approved,
                'source/control drift before transition')
        require(fcopy.inspect_copied_inputs_after_build(self.temporary / 'copy', self.copy_manifest)['unchanged'], 'copy drift')
        require(self.products() == self.built_products, 'one-build product drift')
        device = self.simulator_metadata('owned-device-' + label)
        require(device and device['udid'] == self.uuid and device['state'] == 'Booted', 'same owned booted simulator')
        self.receipt.setdefault('transition_fences', []).append(dict(stage=label, status='PASS', at=normal.now()))
        self.save()

    def preserve_os(self):
        code = self.command(['xcrun', 'simctl', 'get_app_container', self.uuid, normal.APP_ID, 'data'], 'proof-container.log', timeout=30)
        if code:
            self.receipt['os_data_preservation'] = dict(status='ABSENT', container_exit_code=code)
            raise RuntimeError('OS recovery: proof app-data preservation failed')
        container = Path((self.destination / 'proof-container.log').read_text().strip())
        device = (Path.home() / 'Library/Developer/CoreSimulator/Devices' / self.uuid).resolve(strict=True)
        require(container.is_absolute() and not container.is_symlink() and container.resolve(strict=True) == container, 'owned app data')
        container.relative_to(device)
        parent = container / 'tmp'
        require(parent.is_dir() and not parent.is_symlink() and parent.resolve() == parent, 'owned app result parent')
        files, missing = [], []
        names = [('parlor-dsc01-os-result.json', 262144)] + [
                ('parlor-os-recovery-images-%d.json' % n, 49152) for n in range(1, 9)]
        for name, maximum in names:
            path = parent / name
            if not path.exists() and not path.is_symlink():
                missing.append(name); continue
            raw, fingerprint, _ = normal.image_layout.owned_bytes(path, maximum, capture=True)
            with (self.destination / name).open('xb') as output:
                output.write(raw)
            require(normal.digest(self.destination / name) == fingerprint, 'raw probe preservation hash')
            files.append(dict(path=name, sha256=fingerprint))
        self.receipt['os_data_preservation'] = dict(status='PRESERVED_AVAILABLE_ONLY', files=files, missing=missing)
        require(not any(p.name not in dict(names) for p in parent.glob('parlor-os-recovery-images-*')),
                'unexpected OS image filename')

    def preserve_stage(self, stage):
        row = self.receipt['os_recovery_stages'][stage]
        result = self.temporary / (stage + '.xcresult')
        if result.exists() or result.is_symlink():
            require(result.is_dir() and not result.is_symlink() and result.resolve() == result, 'owned stage result')
            archive = self.destination / (stage + '.xcresult.zip')
            self.watched_outputs.append((archive, 64 * 1024 * 1024))
            self.require(['/usr/bin/ditto', '-c', '-k', '--keepParent', result, archive], stage + '-archive.log', timeout=60)
            state = archive.lstat()
            require(stat.S_ISREG(state.st_mode) and state.st_uid == os.getuid() and state.st_nlink == 1 and
                    archive.resolve() == archive and 0 < state.st_size <= 64 * 1024 * 1024, 'bounded owned stage archive')
            row['result_archive_sha256'] = normal.digest(archive)
            if stage != 'build':
                exits = {view: self.command(['xcrun', 'xcresulttool', 'get', 'test-results', view, '--path', result],
                         stage + '-xcresult-' + view + '.json', timeout=30) for view in ('summary', 'tests')}
                row['extraction_exit_codes'] = exits
                require(not any(exits.values()), 'stage XCTest extraction')
        else:
            row['result_absent'] = True  # Preserve the absence; never invent a successful result.
        if stage == 'proof':
            self.preserve_os()
        # Leave room for the unchanged finalizer inside CI's 256MiB/4096-file custody bound.
        files = list(self.destination.iterdir())
        require(len(files) <= 4000 and all(p.is_file() and not p.is_symlink() for p in files) and
                sum(p.stat().st_size for p in files) <= 224 * 1024 * 1024, 'cumulative stage evidence budget')
        row['preserved'] = True

    def run_stage(self, stage):
        row = self.receipt['os_recovery_stages'][stage]
        row.update(attempted=True, status='RUNNING', action='build-for-testing' if stage == 'build' else 'test-without-building',
                   log=stage + '-xcodebuild.log', result_bundle=stage + '.xcresult', command_completed=False,
                   preserved=False, errors=[])
        if stage == 'bootstrap':
            self.receipt['bootstrap_status'] = 'FAIL'  # Attempted exceptions/timeouts are never NOT_RUN.
        self.receipt['postbuild_evidence_preserved'] = False  # Sticky anew for every attempted invocation.
        self.save()
        primary = None
        try:
            row['exit_code'] = self.command(xcode_arguments(self, stage), row['log'], timeout=BUDGETS[stage], limit=64 * 1024 * 1024)
            row['command_completed'] = True
        except BaseException as error:
            primary = error
            row['errors'].append(dict(stage='command', type=type(error).__name__))
        finally:
            for operation, call in [('stop', lambda: self.stop_gradle('stop-' + stage + '-immediate')),
                                    ('preserve', lambda: self.preserve_stage(stage))]:
                try:
                    call()
                    if operation == 'stop':
                        row['stop_exit_code'] = 0
                except BaseException as error:
                    row['errors'].append(dict(stage=operation, type=type(error).__name__))
                    primary = primary or error
            row['status'] = 'PASS' if primary is None and row.get('exit_code') == 0 and not row.get('result_absent') else 'FAIL'
            self.receipt['postbuild_evidence_preserved'] = all(s.get('preserved') is True for s in
                self.receipt['os_recovery_stages'].values() if s['attempted'])
            self.save()
        if primary is not None:
            raise primary
        return row

    def stage_views(self, stage):
        return [normal.read_json((self.destination / (stage + '-xcresult-' + view + '.json')).read_text())
                for view in ('summary', 'tests')]

    def run_xctest(self):
        self.gradle_attempted = self.receipt['build_attempted'] = True
        self.lifecycle.mark_build_attempted()
        require(self.run_stage('build')['status'] == 'PASS', 'build-for-testing failed')
        require((self.destination / 'embedded-gradle-stop.txt').read_text() == 'build_exit=0\nstop_exit=0\n', 'embedded stop')
        phase = normal.read_phase_receipt(self.destination / normal.RECEIPT_NAME, self.mode,
                                         self.environment['SDK_NAME'], self.temporary / 'copy')
        self.receipt['effective_app_phase'] = dict(mode=phase['mode'], sha256=normal.digest(self.destination / normal.RECEIPT_NAME))
        self.built_products = self.products()
        normal.write_json(self.destination / 'one-build-products.json', self.built_products)
        self.fence('before-bootstrap')
        bootstrap = self.run_stage('bootstrap')
        bootstrap['status'] = 'FAIL'  # Native exit alone does not establish the selected XCTest.
        passed = receipts.ordinary_bootstrap(bootstrap, *self.stage_views('bootstrap'),
            (self.destination / bootstrap['log']).read_text(), self.uuid, functional)
        self.receipt['bootstrap_status'] = bootstrap['status'] = 'PASS' if passed else 'FAIL'
        self.fence('before-proof')
        require(self.run_stage('proof')['status'] == 'PASS', 'proof invocation failed')
        self.receipt['os_recovery_stages']['proof']['status'] = 'FAIL'  # Pending actual OS/image validation.
        self.fence('after-proof')

    def observe_external_images(self):
        self.receipt['os_provenance_status'] = 'FAIL'
        try:
            _, artifacts = self.artifact_inventory()  # Unchanged source notices, UUIDs, signatures, built/installed equality.
        finally:
            status = self.receipt['notice_package_status']
            self.receipt['os_notice_package_status'] = 'FAIL' if status == 'RUNNING' else status
            self.receipt['notice_package_status'] = 'NOT_RUN'
        built = normal.read_json((self.destination / 'built-installed-binary-inventory.json').read_text())
        read = functional('l08_receipts').read_owned_result
        report = read(self.destination / 'parlor-dsc01-os-result.json', self.destination, 262144)
        images = [read(self.destination / ('parlor-os-recovery-images-%d.json' % n), self.destination, 49152)
                  for n in range(1, 9) if (self.destination / ('parlor-os-recovery-images-%d.json' % n)).exists()]
        self.receipt['os_observation_validation'] = receipts.verify_proof(*self.stage_views('proof'),
            (self.destination / 'proof-xcodebuild.log').read_text(), report, images, self.uuid, self.mode,
            built, built, artifacts, functional)
        self.receipt['os_provenance_status'] = 'PASS'
        self.receipt['os_recovery_stages']['proof']['status'] = 'PASS'

    def finalize(self):
        super().finalize()  # Unchanged ownership/lifecycle/quiescence/copy/source/control cleanup.
        self.receipt.update(receipts.classify(self.receipt))

    def run(self):
        try:
            self.prepare()
            self.run_xctest()
            self.observe_external_images()
        except BaseException as error:
            self.receipt['error'] = dict(type=type(error).__name__, message=str(error)[:800])
        finally:
            self.watched_outputs = []
            with normal.defer_parent_signals():
                try:
                    self.finalize()
                except BaseException as error:
                    self.receipt.update(status='FAIL', cleanup_status='FAIL', finalizer_error=type(error).__name__)
                self.receipt['finished_at'] = normal.now()
                self.save()
                print(json.dumps({k: self.receipt.get(k) for k in ('cycle', 'status', 'bootstrap_status', 'os_subgate_status',
                    'os_provenance_status', 'os_notice_package_status', 'cleanup_status', 'error')}, indent=2), flush=True)
        return 0 if self.receipt['status'] == receipts.SUCCESS else 1


def main(arguments=None):
    args = sys.argv[1:] if arguments is None else arguments
    if len(args) == 2 and args[0] == '--control-manifest':
        binding = normal.checked_binding_path(args[1])
        print(json.dumps(dict(control_sha256=control_hash(binding), files=control_manifest(binding)), indent=2))
        return 0
    require(len(args) == 6 and re.fullmatch(r'ios-readiness-[0-9]{2}', args[0]), 'explicit OS-only invocation')
    name, binding, approved = args[0], normal.checked_binding_path(args[1]), args[2]
    toolchain, remaining = normal.selected_toolchain(args[3:])
    lifecycle, remaining = normal.selected_lifecycle(remaining, toolchain)
    mode = normal.selected_mode(remaining)
    require(toolchain == 'qualified-xcode-26.3' and lifecycle == normal.DIRECT_LIFECYCLE and mode == 'adhoc', 'qualified owned ad-hoc lane only')
    require(re.fullmatch(r'[a-f0-9]{64}', approved) and approved == control_hash(binding), 'independently reviewed controls')
    with (binding.parent / 'build-lane.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        return RecoveryLane(name, binding, approved, mode, toolchain=toolchain, lifecycle_mode=lifecycle).run()


if __name__ == '__main__':
    def interrupted(sig, _frame):
        raise KeyboardInterrupt('signal ' + str(sig))
    signal.signal(signal.SIGTERM, interrupted)
    raise SystemExit(main())
