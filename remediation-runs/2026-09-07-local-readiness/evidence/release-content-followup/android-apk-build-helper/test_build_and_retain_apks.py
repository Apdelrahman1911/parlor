"""Pure metadata/signature guards only: no Gradle, Java, SDK, keys or workers."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


SPEC = importlib.util.spec_from_file_location('apk_fixture', Path(__file__).with_name('build_and_retain_apks.py'))
FIXTURE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(FIXTURE)
FINGERPRINT = 'ab' * 32


class ApkMetadataTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='parlor-apk-metadata-test-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        for variant, (package, _) in FIXTURE.EXPECTED.items():
            folder = self.root / variant
            folder.mkdir()
            (folder / 'output.apk').write_bytes(b'Synthetic metadata fixture, not an installable APK')
            self.write(variant, {'variantName': variant, 'applicationId': package,
                                'artifactType': {'type': 'APK', 'kind': 'Directory'},
                                'elements': [{'type': 'SINGLE', 'filters': [], 'outputFile': 'output.apk'}]})

    def write(self, variant, value):
        (self.root / variant / 'output-metadata.json').write_text(json.dumps(value))

    def read(self, variant):
        return json.loads((self.root / variant / 'output-metadata.json').read_text())

    def test_two_exact_metadata_bound_outputs(self):
        actual = FIXTURE.discover_apks(self.root)
        self.assertEqual(set(actual), {'release', 'releaseAndroidTest'})
        self.assertEqual(actual['release']['path'], self.root / 'release/output.apk')
        self.assertEqual(actual['releaseAndroidTest']['application_id'], 'com.parlor.app.test')

    def test_missing_metadata_rejected(self):
        (self.root / 'releaseAndroidTest/output-metadata.json').unlink()
        with self.assertRaisesRegex(RuntimeError, 'exactly two'):
            FIXTURE.discover_apks(self.root)

    def test_duplicate_variant_rejected(self):
        duplicate = self.read('release')
        self.write('releaseAndroidTest', duplicate)
        with self.assertRaisesRegex(RuntimeError, 'duplicate'):
            FIXTURE.discover_apks(self.root)

    def test_wrong_application_identity_rejected(self):
        metadata = self.read('release')
        metadata['applicationId'] = 'com.parlor.app.debug'
        self.write('release', metadata)
        with self.assertRaisesRegex(RuntimeError, 'identity mismatch'):
            FIXTURE.discover_apks(self.root)

    def test_wrong_artifact_type_rejected(self):
        metadata = self.read('release')
        metadata['artifactType']['type'] = 'BUNDLE'
        self.write('release', metadata)
        with self.assertRaisesRegex(RuntimeError, 'not an APK'):
            FIXTURE.discover_apks(self.root)

    def test_filtered_apk_rejected(self):
        metadata = self.read('release')
        metadata['elements'][0]['filters'] = [{'filterType': 'ABI', 'value': 'arm64-v8a'}]
        self.write('release', metadata)
        with self.assertRaisesRegex(RuntimeError, 'split/filtered'):
            FIXTURE.discover_apks(self.root)

    def test_multiple_elements_rejected(self):
        metadata = self.read('release')
        metadata['elements'].append(metadata['elements'][0].copy())
        self.write('release', metadata)
        with self.assertRaisesRegex(RuntimeError, 'one APK per variant'):
            FIXTURE.discover_apks(self.root)

    def test_path_traversal_rejected(self):
        metadata = self.read('release')
        metadata['elements'][0]['outputFile'] = '../releaseAndroidTest/output.apk'
        self.write('release', metadata)
        with self.assertRaisesRegex(RuntimeError, 'Unsafe APK'):
            FIXTURE.discover_apks(self.root)

    def test_stale_extra_apk_rejected(self):
        (self.root / 'release/stale.apk').write_bytes(b'Old synthetic output')
        with self.assertRaisesRegex(RuntimeError, 'Extra/missing'):
            FIXTURE.discover_apks(self.root)

    def test_symlinked_apk_rejected(self):
        artifact = self.root / 'release/output.apk'
        artifact.unlink()
        artifact.symlink_to('../releaseAndroidTest/output.apk')
        with self.assertRaisesRegex(RuntimeError, 'unsafe'):
            FIXTURE.discover_apks(self.root)

    def test_empty_apk_rejected(self):
        (self.root / 'release/output.apk').write_bytes(b'')
        with self.assertRaisesRegex(RuntimeError, 'Empty APK'):
            FIXTURE.discover_apks(self.root)


class ApkSignerTest(unittest.TestCase):
    def output(self, fingerprint=FINGERPRINT, count=1):
        return f'Number of signers: {count}\nSigner #1 certificate SHA-256 digest: {fingerprint}\n'

    def test_one_exact_signer_accepted(self):
        self.assertEqual(FIXTURE.approved_certificate(self.output(), FINGERPRINT), FINGERPRINT)

    def test_uppercase_hex_is_same_public_digest(self):
        self.assertEqual(FIXTURE.approved_certificate(self.output(FINGERPRINT.upper()), FINGERPRINT), FINGERPRINT)

    def test_wrong_signer_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'expected disposable signer'):
            FIXTURE.approved_certificate(self.output('cd' * 32), FINGERPRINT)

    def test_missing_count_rejected(self):
        with self.assertRaises(RuntimeError):
            FIXTURE.approved_certificate(f'Signer #1 certificate SHA-256 digest: {FINGERPRINT}\n', FINGERPRINT)

    def test_multiple_signers_rejected(self):
        with self.assertRaises(RuntimeError):
            FIXTURE.approved_certificate(self.output(count=2), FINGERPRINT)

    def test_additional_digest_rejected_even_with_claimed_single_signer(self):
        output = self.output() + f'Signer #2 certificate SHA-256 digest: {FINGERPRINT}\n'
        with self.assertRaises(RuntimeError):
            FIXTURE.approved_certificate(output, FINGERPRINT)


if __name__ == '__main__':
    unittest.main()
