"""Source/copy contract tests only; root executes in the shared lane, never native proof."""
import ast
import hashlib
import json
from pathlib import Path
import re
import tempfile
import unittest

import copied_sources as copies
import l08_copy as strict
import l08_functional_copy as companion

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
CANONICAL = ROOT / 'scripts/verification/ios-readiness'
CLONE_MANIFEST = HERE.parent / 'reviews/l08-functional-companion-clone-manifest-01.json'
CLONE_MANIFEST_SHA256 = '3036231a1bcfc9d567b8381ee33d89cd381377bea2b10790ddde0f90baf682fb'
SITES = ('save', 'load', 'dual-before-legacy-seed', 'dual-after-tag-damage', 'legacy-load-dual', 'retained-dual')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition, reason):
    if not condition:
        raise RuntimeError(reason)


def section(text, first, last=None):
    require(text.count(first) == 1 and (last is None or text.count(last) == 1), 'Ambiguous source section')
    start = text.index(first)
    end = len(text) if last is None else text.index(last, start)
    return text[start:end]


def assert_probe_contract(candidate, original):
    """Compare complete executable regions, not just whether check names occur somewhere."""
    for site in SITES:
        require(len(re.findall(r'protectedMetadata\((?:alias|"dual"), "' + site + r'"\)', candidate)) == 1,
                'Missing or duplicate metadata checkpoint: ' + site)
    restored = re.sub(r'protectedMetadata\((alias|"dual"), "(?:' + '|'.join(SITES) + r')"\)',
                      r'protectedMetadata(\1)', candidate)
    restored = restored.replace('"owned_metadata_and_header"', '"protected_metadata"')
    restored = restored.replace('recordProtectionComparison', 'recordProtectionDiagnostic')
    for first, last in (
        ('    private val aliases', '    fun command('),
        ('    private suspend fun execute(', '    private fun recordProtectionDiagnostic('),
        ('internal fun requireContext(', '/** Exact app-container/token paths only'),
        ('/** Exact app-container/token paths only', '    fun protectedMetadata('),
        ('    private fun excluded(', None),
    ):
        require(section(restored, first, last) == section(original, first, last),
                'A nonmetadata functional/ownership boundary changed: ' + first)
    cancellation = section(candidate, '            } catch (cancelled:', '    private suspend fun execute(')
    require(cancellation.replace('completeComparisons.clear()', 'protectionDiagnostic = null') ==
            section(original, '            } catch (cancelled:', '    private suspend fun execute('),
            'Cancellation, payload clearing or controller/scope cleanup changed')
    expected = section(original, '    fun protectedMetadata(', '    private class ProtectionRead(')
    expected = strict.once(expected, 'fun protectedMetadata(alias: String)', 'fun protectedMetadata(alias: String, site: String)')
    for target, args in (('directory', 'directory'), ('file', 'directory, file')):
        expected = strict.once(expected,
            '        if (!' + target + '.complete) protectionFailure("' + target + '", path, key, protection, ' + args + ')\n'
            '        check(' + target + '.complete)',
            '        recordComparison(site, alias, "' + target + '", ' + target + ')')
    require(section(candidate, '    fun protectedMetadata(', '    private class ProtectionRead(') == expected,
            'Path/existence/backup/header or one of the actual Complete comparisons changed')
    require(section(candidate, '    private class ProtectionRead(', '    private fun recordComparison(') ==
            section(original, '    private class ProtectionRead(', '    private fun protectionFailure('),
            'The actual getter, required equality or exact native values changed')
    require(section(candidate, '    private fun metadataRead(', '    private fun excluded(') ==
            section(original, '    private fun metadataRead(', '    private fun unknownMetadata('),
            'Native primitive metadata is not preserved exactly')
    require(section(candidate, '    private fun recordComparison(', '    private fun metadataRead(') == '''    private fun recordComparison(site: String, alias: String, target: String, reading: ProtectionRead) {
        recordProtectionComparison(buildJsonObject {
            put("site", site); put("alias", alias); put("target", target)
            put("attributes", reading.observation)
            put("comparison", if (reading.complete) "PASS" else "FAIL")
        })
    }

''', 'A comparison must record the actual equality, never repair or relabel it')
    require(section(candidate, '    private fun recordProtectionComparison(', '    private fun checks(') == '''    private fun recordProtectionComparison(value: JsonObject) {
        check(completeComparisons.size < 4)
        check(stage == "metadata-directory-protection" || stage == "metadata-file-protection")
        completeComparisons.add(buildJsonObject {
            put("ordinal", completeComparisons.size + 1)
            value.forEach { (key, item) -> put(key, item) }
        }) // Closed metadata only, including an unsuccessful required Complete comparison.
    }

''', 'Comparison recording must remain a bounded command-local append')
    require(candidate.count('completeComparisons.clear()') == 2 and
            candidate.count('JsonArray(completeComparisons.toList())') == 2,
            'Commands and failures must retain their own comparison prefix without cross-command reuse')


class L08FunctionalCopyTests(unittest.TestCase):
    def test_all_original_controls_and_exact_clone_allowlist_are_preserved(self):
        self.assertEqual(sha(CLONE_MANIFEST), CLONE_MANIFEST_SHA256)
        manifest = json.loads(CLONE_MANIFEST.read_text())
        self.assertEqual(len(manifest['files']), 65)
        changed = set(manifest['permitted_changed_clones'])
        self.assertEqual(changed, {'copied_sources.py', 'run_ios_readiness.py'})
        allowed = {row['path'] for row in manifest['files']} | set(manifest['permitted_new_files'])
        self.assertEqual(len(allowed), 75)
        self.assertEqual({path.name for path in HERE.iterdir()}, allowed)
        self.assertTrue(all(path.is_file() and not path.is_symlink() for path in HERE.iterdir()))
        for row in manifest['files']:
            with self.subTest(path=row['path']):
                self.assertEqual(sha(CANONICAL / row['path']), row['original_sha256'])
                if row['path'] not in changed:
                    self.assertEqual(sha(HERE / row['path']), row['original_sha256'])
        original = (CANONICAL / 'copied_sources.py').read_text()
        self.assertEqual((HERE / 'copied_sources.py').read_text(), strict.once(original,
            'from l08_copy import L08_ADDITIONS, L08_ADDITIONAL_MODIFIED, instrument_l08_kotlin',
            'from l08_functional_copy import L08_ADDITIONS, L08_ADDITIONAL_MODIFIED, instrument_l08_kotlin'))

    def test_only_the_new_probe_registration_replaces_the_strict_probe_in_owned_copy(self):
        expected = dict(strict.L08_ADDITIONS)
        del expected[companion.OLD_PROBE]
        expected[companion.NEW_PROBE] = 'L08StorageFunctionalProbe.kt.in'
        self.assertEqual(companion.L08_ADDITIONS, expected)
        self.assertEqual(companion.L08_ADDITIONAL_MODIFIED, strict.L08_ADDITIONAL_MODIFIED)
        self.assertEqual(len(companion.L08_ADDITIONS), 9)
        self.assertNotIn(companion.OLD_PROBE, copies.ADDITIONS)
        self.assertIn(companion.NEW_PROBE, copies.ADDITIONS)
        for path in copies.ADDITIONS:
            self.assertFalse((ROOT / path).exists(), 'Copy-only seam must not enter a shipping source tree')

    def test_current_source_transforms_once_with_single_bridge_and_shared_context(self):
        before = {path: sha(ROOT / path) for path in copies.MODIFIED_KOTLIN}
        with tempfile.TemporaryDirectory(prefix='parlor-functional-copy-') as raw:
            owned = Path(raw).resolve()
            for path in copies.MODIFIED_KOTLIN:
                target = owned / path; target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((ROOT / path).read_bytes())
            copies.instrument_kotlin(owned)
            self.assertTrue((owned / companion.NEW_PROBE).is_file())
            self.assertFalse((owned / companion.OLD_PROBE).exists())
            main = (owned / strict.MAIN).read_text()
            self.assertEqual(main.count('fun L08StorageFunctionalCommand('), 1)
            self.assertEqual(main.count('L08StorageFunctionalProbe.command(action, onJson)'), 1)
            self.assertNotIn('fun L08StorageCommand(', main)
            self.assertIn('L08HostProbe.command(action, onJson)', main)
            self.assertEqual(sum((owned / path).read_text().count('fun requireContext(') for path in copies.ADDITIONS), 1)
            self.assertEqual((owned / companion.NEW_PROBE).read_bytes(), (HERE / 'L08StorageFunctionalProbe.kt.in').read_bytes())
            for path, template in companion.L08_ADDITIONS.items():
                self.assertEqual((owned / path).read_bytes(), (HERE / template).read_bytes())
            with self.assertRaises(RuntimeError): companion.instrument_l08_kotlin(owned)
        self.assertFalse(owned.exists())
        self.assertEqual(before, {path: sha(ROOT / path) for path in copies.MODIFIED_KOTLIN})

    def test_functional_probe_preserves_complete_nonmetadata_execution_regions(self):
        assert_probe_contract((HERE / 'L08StorageFunctionalProbe.kt.in').read_text(),
                              (CANONICAL / 'L08StorageProbe.kt.in').read_text())

    def test_each_source_boundary_mutation_is_rejected_not_just_missing_check_names(self):
        original = (CANONICAL / 'L08StorageProbe.kt.in').read_text()
        candidate = (HERE / 'L08StorageFunctionalProbe.kt.in').read_text()
        changes = [
            ('check(store is FileBackedSnapshotStore && filesystem is IosSnapshotFileSystem)', 'check(true)'),
            ('check(koin.get<RoomTransport>() is P2pKitRoomTransport)', 'check(true)'),
            ('check(loaded is Result.Failure && loaded.error == DataError.NotFound)', 'check(loaded is Result.Failure)'),
            ('check(loaded.error == DataError.NotFound)', 'check(loaded.error == DataError.CorruptedData)'),
            ('awaitAbsent(store, id)', 'store.delete(id)'),
            ('check(excluded(protected))', 'check(true)'), ('check(excluded(path))', 'check(true)'),
            ('check(bytes.take(7).toByteArray().contentEquals("PARSNAP".encodeToByteArray()))', 'check(true)'),
            ('ProtectionRead(value == protection,', 'ProtectionRead(true,'),
            ('recordComparison(site, alias, "directory", directory)', 'Unit'),
            ('recordComparison(site, alias, "file", file)', 'Unit'),
            ('check(completeComparisons.size < 4)', 'check(completeComparisons.size < 5)'),
            ('put("comparison", if (reading.complete) "PASS" else "FAIL")', 'put("comparison", "PASS")'),
            ('throw cancelled', 'return@launch'), ('expected?.payload?.fill(0)', 'expected = null'),
        ]
        changes += [('protectedMetadata(' + ('alias' if site in ('save', 'load', 'dual-before-legacy-seed') else '"dual"') +
                     ', "' + site + '")', 'Unit') for site in SITES]
        for old, new in changes:
            self.assertIn(old, candidate)
            with self.subTest(change=old), self.assertRaises(RuntimeError):
                assert_probe_contract(candidate.replace(old, new, 1), original)

    def test_swift_namespace_preserves_shared_host_helpers_and_exact_distinct_test(self):
        probe = (CANONICAL / 'DSC01Probe.swift.in').read_text()
        transformed = companion.instrument_probe_swift(probe)
        self.assertIn('"readiness", "l08-storage-functional", "l08-host"', transformed)
        self.assertNotIn('"l08-storage"', transformed)
        self.assertEqual(transformed.count('if probe.isL08StorageFunctionalScenario { L08StorageFunctionalControls(probe: probe) }'), 1)
        for invalid in ('', probe + probe, transformed):
            with self.assertRaises(RuntimeError): companion.instrument_probe_swift(invalid)
        ui = (CANONICAL / 'IOSAppLaunchUITests.swift.in').read_text()
        changed = companion.instrument_ui_test(ui)
        self.assertEqual(changed.count('func ' + companion.NEW_TEST + '()'), 1)
        self.assertNotIn('func ' + companion.OLD_TEST + '()', changed)
        ordered = ['verifyActualNativeReadiness', 'verifyL08FunctionalStoreResume', 'verifyL08StartedHosts']
        locations = [changed.index('try ' + name + '(app)') for name in ordered]
        self.assertEqual(locations, sorted(locations))
        self.assertIn('continueAfterFailure = false', changed)
        for invalid in ('', ui + ui, changed):
            with self.assertRaises(RuntimeError): companion.instrument_ui_test(invalid)
        launch = (HERE / 'L08StorageFunctionalLaunch.swift.in').read_text()
        tests = (HERE / 'L08StorageFunctionalUITests.swift.in').read_text()
        self.assertIn('raw.count <= 4096', launch)
        self.assertIn('encoded.count <= 16384', launch)
        self.assertEqual(launch.count('func sampleL08Ui()'), 1)
        self.assertEqual(tests.count('private func l08TapExactPublicControl('), 1)
        self.assertEqual(tests.count('private func l08WaitRecovery('), 1)
        self.assertIn('PARLOR_L08_STORAGE_FUNCTIONAL_BOOT ', tests)
        self.assertNotIn('PARLOR_L08_STORAGE_BOOT ', tests)
        self.assertIn('XCTAssertEqual(result["functional_status"] as? String, "PASS")', tests)
        self.assertIn('["PASS", "FAIL"].contains', tests)

    def test_all_new_python_controls_parse_without_launching_any_worker(self):
        for name in ('l08_functional_copy.py', 'l08_functional_receipts.py', 'l08_functional_runner.py',
                     'test_l08_functional_copy.py', 'test_l08_functional_receipts.py', 'test_l08_functional_runner.py'):
            ast.parse((HERE / name).read_text(), filename=name)


if __name__ == '__main__': unittest.main()
