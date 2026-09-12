"""Exact copy-only path helpers executed with host Foundation, never app/device proof."""
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
HELPERS_START = '// BEGIN OWNED PATH HELPERS (also executed with Foundation in host controls).'
HELPERS_END = '// END OWNED PATH HELPERS.'
LOADER_START = '// BEGIN OWNED LOADER ENVIRONMENT (also executed with Foundation in host controls).'
LOADER_END = '// END OWNED LOADER ENVIRONMENT.'
CASES = [
    'legacy-foundation-alias-witness-and-canonical-repair',
    'canonical-and-alias-product-identities',
    'installed-app-identity',
    'kgp-built-products-link-stays-owned-copy-build',
    'literal-roots-must-remain-canonical',
    'sibling-prefix-rejected-before-fingerprint',
    'symlink-escape-rejected-before-fingerprint',
    'parent-traversal-rejected-before-fingerprint',
    'unknown-origin-rejected-before-fingerprint',
    'other-owned-output-is-not-a-framework-origin',
    'wrong-app-relative-framework-rejected',
    'absent-noncandidate-skipped-but-absent-compose-fatal',
    'bounded-input-and-canonical-target-validation',
    'relative-component-and-size-guards-retained',
    'allowed-relative-shapes-stay-strict',
    'loader-paths-canonicalized-with-unavailable-and-unknown-redacted',
    'loader-count-entry-and-total-bounds-retained',
    'eligible-fingerprint-errors-remain-fatal',
]


def exact_fragment(source, start, end):
    if source.count(start) != 1 or source.count(end) != 1 or source.index(start) >= source.index(end):
        raise RuntimeError('Exact reviewed helper anchors are missing, reversed or ambiguous')
    return source.split(start, 1)[1].split(end, 1)[0]


def command(arguments, root, name, timeout):
    """No retained worker: own group only while its direct child is unreaped.

    The root-owned build lane additionally observes compiler descendants. Do
    not run this native test concurrently with Gradle/Xcode or another test lane.
    """
    output = root / (name + '.log')
    environment = {key: value for key, value in os.environ.items() if not key.startswith('DYLD_')}
    environment['TMPDIR'] = str(root / 'tmp') + '/'
    with output.open('xb') as log:
        child = subprocess.Popen(arguments, cwd=root, stdout=log, stderr=subprocess.STDOUT,
                                 stdin=subprocess.DEVNULL, env=environment, start_new_session=True)
        try:
            status = child.wait(timeout=timeout)
        except BaseException:
            # wait(timeout) has not reaped a child when returncode is None.
            # Its PID cannot be reused, so this newly-created group is still
            # ours. Never signal a group after reaping its identity anchor.
            if child.returncode is None:
                try:
                    os.killpg(child.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                child.wait(timeout=20)
            raise
    if output.stat().st_size > 65536:
        raise RuntimeError('Owned native control output exceeded its evidence bound')
    return status, output.read_text()


class NativePathControlTests(unittest.TestCase):
    def test_exact_extraction_refuses_missing_reversed_or_duplicated_helpers(self):
        source = (HERE / 'NativeReadinessLaunch.swift.in').read_text()
        for start, end in ((HELPERS_START, HELPERS_END), (LOADER_START, LOADER_END)):
            value = exact_fragment(source, start, end)
            self.assertTrue(value.strip())
            for changed in (source.replace(start, ''), source.replace(end, ''), source + start,
                            source + end, end + '\n' + start):
                with self.subTest(anchor=start), self.assertRaises(RuntimeError):
                    exact_fragment(changed, start, end)

    def test_runtime_wiring_keeps_owned_gate_before_reader_and_roots_immutable(self):
        source = (HERE / 'NativeReadinessLaunch.swift.in').read_text()
        helper = exact_fragment(source, HELPERS_START, HELPERS_END)
        loader = exact_fragment(source, LOADER_START, LOADER_END)
        observe = source.split('private func nativeReadinessLoadedImages()', 1)[1].split(
            'private func nativeReadinessFrameworkFingerprint', 1)[0]
        self.assertNotIn('.resolvingSymlinksInPath()', source)
        self.assertIn('canonical == literal', helper)
        self.assertIn('realpath(input, address)', helper)
        self.assertIn('nativeReadinessCanonicalDyldPath(raw)', observe)
        self.assertIn('nativeReadinessCanonicalOwnedRoot(nativeReadinessTaskRoot)', observe)
        self.assertIn('nativeReadinessCanonicalOwnedRoot(nativeReadinessProductRoot)', observe)
        self.assertIn('productRoot == taskRoot + "/copy/composeApp/build"', observe)
        self.assertIn('fingerprint: nativeReadinessFrameworkFingerprint)', observe)
        self.assertIn('nativeReadinessFrameworkUUID(index)', observe)
        self.assertIn('nativeReadinessCanonicalOwnedRoot(nativeReadinessTaskRoot)', loader)
        self.assertIn('let path = try nativeReadinessCanonicalPath(entry)', loader)
        self.assertLess(helper.index('throw nativeReadinessImageError(13)'), helper.index('try fingerprint('))
        self.assertLess(helper.index('throw nativeReadinessImageError(24)'), helper.index('try fingerprint('))
        self.assertNotIn('FileHandle', helper)
        self.assertNotIn('Data(contentsOf:', helper)
        self.assertIn('guard result.count < 128', observe)
        self.assertIn('count > 0, count <= 4096', observe)

    @unittest.skipUnless(sys.platform == 'darwin', 'Requires selected Apple Foundation; not iOS/device proof')
    def test_actual_host_foundation_alias_and_owned_origin_controls(self):
        launch_path = HERE / 'NativeReadinessLaunch.swift.in'
        driver_path = HERE / 'NativeReadinessPathControls.swift.in'
        source = launch_path.read_text()
        driver = driver_path.read_text()
        before = {path: hashlib.sha256(path.read_bytes()).hexdigest() for path in (launch_path, driver_path)}
        self.assertEqual(driver.count('__PARLOR_PATH_FIXTURE_ROOT__'), 1)
        helpers = exact_fragment(source, HELPERS_START, HELPERS_END)
        loader = exact_fragment(source, LOADER_START, LOADER_END)
        with tempfile.TemporaryDirectory(prefix='parlor-owned-path-controls-') as raw:
            root = Path(raw).resolve(strict=True)
            # A different TMPDIR is an unmet alias-fixture prerequisite, not a
            # successful reproduction of the Darwin /private-vs-/var mismatch.
            self.assertTrue(str(root).startswith('/private/var/'), 'Fixture needs Darwin /private/var temp root')
            self.assertTrue(str(root).isascii())
            (root / 'tmp').mkdir()
            task = root / 'allocation'
            product = task / 'copy/composeApp/build'
            app = root / 'installed/Parlor.app'
            relative = 'xcode-frameworks/Debug/iphonesimulator26.5/ComposeApp.framework/ComposeApp'
            locations = [product / relative, app / 'Frameworks/ComposeApp.framework/ComposeApp',
                         product.with_name('build-sibling') / 'ComposeApp.framework/ComposeApp',
                         root / 'unknown/ComposeApp.framework/ComposeApp',
                         product / 'other/ComposeApp.framework/ComposeApp',
                         app / 'Other/ComposeApp.framework/ComposeApp', root / 'unsafe\nsynthetic-target']
            for path in locations:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('synthetic-only; never read by the fingerprint callback')
            built_products = task / 'DerivedData/Build/Products/Debug-iphonesimulator'
            built_products.mkdir(parents=True)
            (built_products / 'ComposeApp.framework').symlink_to((product / relative).parent, target_is_directory=True)
            (product / 'escape').symlink_to(root / 'unknown', target_is_directory=True)
            (root / 'relocated-root').symlink_to(root / 'unknown', target_is_directory=True)
            (root / 'unsafe-target-link').symlink_to(root / 'unsafe\nsynthetic-target')
            main = root / 'main.swift'
            main.write_text('import Foundation\nimport Darwin\n' + helpers + loader + driver.replace(
                '__PARLOR_PATH_FIXTURE_ROOT__', json.dumps(str(root))))
            binary = root / 'path-controls'
            status, output = command(['/usr/bin/xcrun', 'swiftc', '-module-cache-path', str(root / 'module-cache'),
                                      str(main), '-o', str(binary)], root, 'compile', 120)
            self.assertEqual(status, 0, output)
            status, output = command([str(binary)], root, 'execute', 20)
            self.assertEqual(status, 0, output)
            value = json.loads(output)
            self.assertEqual(value, dict(schema_version=1, status='PASS', cases=CASES,
                host_foundation_only=True, physical_or_app_runtime_evidence=False, unknown_origin_fingerprint_calls=0))
            self.assertEqual(len(CASES), len(set(CASES)))
            # Preserve only a bounded test receipt. The outer root lane owns
            # command/exit/toolchain evidence; this is 18 subcases in ONE test.
            print('PARLOR_HOST_PATH_CONTROLS ' + json.dumps(value, sort_keys=True), flush=True)
        self.assertFalse(Path(raw).exists(), 'Owned Swift binary/module cache/fixtures must be removed')
        self.assertEqual(before, {path: hashlib.sha256(path.read_bytes()).hexdigest() for path in before})


if __name__ == '__main__':
    unittest.main()
