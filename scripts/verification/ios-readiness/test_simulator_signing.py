"""Pure flags/path/receipt fixtures; no native signing or build executes."""
import copy
import json
from pathlib import Path
import tempfile
import unittest

import simulator_signing as subject


def fixture(root, mode='disabled'):
    temporary = root / 'parlor-audit-ios-readiness-99-fixture'
    source = temporary / 'copy'; (source / 'iosApp').mkdir(parents=True, exist_ok=True)
    products = temporary / 'DerivedData/Build/Products/Debug-iphonesimulator'; products.mkdir(parents=True, exist_ok=True)
    environment = {**subject.CONTEXT, **subject.signing_overrides(mode),
        'SDK_NAME': 'iphonesimulator26.5', 'CODE_SIGN_STYLE': 'Manual' if mode == 'adhoc' else 'Automatic',
        'SRCROOT': str(source / 'iosApp'), 'PROJECT_DIR': str(source / 'iosApp'), 'TARGET_BUILD_DIR': str(products),
        'AD_HOC_CODE_SIGNING_ALLOWED': 'YES', 'CODE_SIGN_INJECT_BASE_ENTITLEMENTS': 'YES',
        'ENTITLEMENTS_REQUIRED': 'YES' if mode == 'adhoc' else 'NO', 'ENTITLEMENTS_DESTINATION': '__entitlements',
        'ENABLE_DEBUG_DYLIB': 'YES'}
    if mode == 'adhoc': environment['EXPANDED_CODE_SIGN_IDENTITY'] = '-'
    return source, environment


class ExplicitSimulatorSigningTests(unittest.TestCase):
    def test_disabled_default_is_identical_and_adhoc_requires_exact_explicit_option(self):
        self.assertEqual(subject.selected_mode([]), 'disabled')
        self.assertEqual(subject.selected_mode(['--simulator-signing=adhoc']), 'adhoc')
        self.assertEqual(subject.signing_overrides(), dict(CODE_SIGNING_ALLOWED='NO', CODE_SIGNING_REQUIRED='NO',
            CODE_SIGN_IDENTITY='', DEVELOPMENT_TEAM=''))
        self.assertEqual(subject.signing_overrides('adhoc'), dict(CODE_SIGNING_ALLOWED='YES', CODE_SIGNING_REQUIRED='YES',
            CODE_SIGN_IDENTITY='-', CODE_SIGN_STYLE='Manual', DEVELOPMENT_TEAM='', PROVISIONING_PROFILE='',
            PROVISIONING_PROFILE_SPECIFIER='', CODE_SIGN_ENTITLEMENTS='', OTHER_CODE_SIGN_FLAGS=''))
        for option in (['adhoc'], ['--simulator-signing=automatic'], ['--simulator-signing=adhoc'] * 2,
                       ['--simulator-signing=adhoc', '-allowProvisioningUpdates']):
            with self.subTest(option=option), self.assertRaises(RuntimeError): subject.selected_mode(option)
        with self.assertRaises(RuntimeError): subject.signing_overrides('automatic')

    def test_effective_phase_records_only_owned_public_context_not_raw_environment(self):
        with tempfile.TemporaryDirectory(prefix='parlor-signing-control-test-') as raw:
            root = Path(raw).resolve()
            for mode in subject.MODES:
                source, environment = fixture(root, mode)
                environment['UNRELATED_SYNTHETIC_SECRET'] = 'never-retain-this-sentinel'
                observed = subject.observed_phase(mode, 'iphonesimulator26.5', source, environment)
                self.assertEqual(observed['mode'], mode)
                self.assertEqual(observed['settings']['EXPANDED_CODE_SIGN_IDENTITY'], '-' if mode == 'adhoc' else None)
                self.assertEqual(observed['settings']['CODE_SIGN_INJECT_BASE_ENTITLEMENTS'], 'YES')
                self.assertNotIn('never-retain-this-sentinel', json.dumps(observed))
                self.assertEqual(set(observed['settings']), set(subject.FIELDS))

    def test_unexpected_real_identity_profile_target_architecture_sdk_or_path_is_refused(self):
        with tempfile.TemporaryDirectory(prefix='parlor-signing-control-test-') as raw:
            for mode in subject.MODES:
                source, environment = fixture(Path(raw).resolve(), mode)
                changes = [('CONFIGURATION', 'Release'), ('PLATFORM_NAME', 'iphoneos'), ('ACTION', 'install'),
                    ('ARCHS', 'x86_64'), ('PRODUCT_BUNDLE_IDENTIFIER', 'com.parlor.app'), ('TARGET_NAME', 'unrelated'),
                    ('SDK_NAME', 'iphonesimulator26.3'), ('CODE_SIGNING_ALLOWED', 'NO' if mode == 'adhoc' else 'YES'),
                    ('SRCROOT', str(source)), ('PROJECT_DIR', str(source)), ('TARGET_BUILD_DIR', str(source)),
                    ('CODE_SIGN_IDENTITY', 'not-authorized-synthetic-identity'),
                    ('EXPANDED_CODE_SIGN_IDENTITY', ''), ('EXPANDED_CODE_SIGN_IDENTITY', 'synthetic-not-authorized')]
                changes += [(key, 'synthetic-not-authorized-never-read') for key in subject.EMPTY_FIELDS]
                for key, value in changes:
                    with self.subTest(mode=mode, key=key, value=value), self.assertRaises((RuntimeError, OSError)):
                        subject.observed_phase(mode, 'iphonesimulator26.5', source, {**environment, key: value})
                if mode == 'adhoc':
                    del environment['EXPANDED_CODE_SIGN_IDENTITY']
                    with self.assertRaises(RuntimeError): subject.observed_phase(mode, 'iphonesimulator26.5', source, environment)
                for sdk in ('iphonesimulator', 'iphoneos26.5', 'iphonesimulator26.5/../iphoneos', ''):
                    with self.subTest(sdk=sdk), self.assertRaises(RuntimeError): subject.observed_phase(mode, sdk, source, environment)

    def test_empty_regular_fields_may_be_absent_but_disabled_expanded_identity_must_be_absent(self):
        with tempfile.TemporaryDirectory(prefix='parlor-signing-control-test-') as raw:
            source, environment = fixture(Path(raw).resolve())
            for key in subject.EMPTY_FIELDS + ('CODE_SIGN_IDENTITY',): environment.pop(key, None)
            observed = subject.observed_phase('disabled', 'iphonesimulator26.5', source, environment)
            self.assertIsNone(observed['settings']['CODE_SIGN_IDENTITY'])
            for expanded in ('', '-'):
                with self.assertRaises(RuntimeError):
                    subject.observed_phase('disabled', 'iphonesimulator26.5', source, {**environment, 'EXPANDED_CODE_SIGN_IDENTITY': expanded})

    def test_exact_receipt_is_exclusive_bounded_mode_bound_and_not_replaceable_by_stale_evidence(self):
        with tempfile.TemporaryDirectory(prefix='parlor-signing-control-test-') as raw:
            root = Path(raw).resolve(); source, environment = fixture(root, 'adhoc')
            value = subject.observed_phase('adhoc', 'iphonesimulator26.5', source, environment)
            path = root / subject.RECEIPT_NAME
            with self.assertRaises(RuntimeError): subject.read_phase_receipt(path, 'adhoc', 'iphonesimulator26.5', source)
            subject.write_phase_receipt(path, value)
            self.assertEqual(subject.read_phase_receipt(path, 'adhoc', 'iphonesimulator26.5', source), value)
            prior = path.read_bytes()
            with self.assertRaises(FileExistsError): subject.write_phase_receipt(path, value)
            self.assertEqual(path.read_bytes(), prior)
            with self.assertRaises(RuntimeError): subject.read_phase_receipt(path, 'disabled', 'iphonesimulator26.5', source)
            for raw_value in (b'', b'x' * 8193, b'[]', b'{"mode":"adhoc","mode":"adhoc"}', b'\xff'):
                path.write_bytes(raw_value)
                with self.assertRaises(RuntimeError): subject.read_phase_receipt(path, 'adhoc', 'iphonesimulator26.5', source)
            for key, field in [('schema_version', True), ('mode', 'disabled'), ('source_root', str(root)), ('scope', 'forged')]:
                changed = copy.deepcopy(value); changed[key] = field; path.write_text(json.dumps(changed))
                with self.assertRaises(RuntimeError): subject.read_phase_receipt(path, 'adhoc', 'iphonesimulator26.5', source)
            outside = root / 'synthetic-existing.json'; outside.write_bytes(prior); path.unlink(); path.symlink_to(outside)
            with self.assertRaises(RuntimeError): subject.read_phase_receipt(path, 'adhoc', 'iphonesimulator26.5', source)
            with self.assertRaises(RuntimeError): subject.write_phase_receipt(path, value)
            self.assertEqual(outside.read_bytes(), prior)

    def test_renderer_checks_every_anchor_mode_cycle_destination_and_safe_literal(self):
        template = Path(__file__).with_name('copied-kotlin-phase.sh.in').read_text()
        with tempfile.TemporaryDirectory(prefix='parlor-signing-control-test-') as raw:
            root = Path(raw).resolve(); source, _ = fixture(root)
            destination = root / 'evidence/ios-readiness-99'; destination.mkdir(parents=True)
            for mode in subject.MODES:
                rendered = subject.render_owned_kotlin_phase(template, source.parent, destination, 'iphonesimulator26.5', mode)
                self.assertNotIn('__PARLOR_', rendered)
                self.assertIn('"' + mode + '" "iphonesimulator26.5"', rendered)
                self.assertLess(rendered.index('simulator_signing.py'), rendered.index('./gradlew --no-daemon'))
                for old in ('__PARLOR_SOURCE_ROOT__', '__PARLOR_STOP_RECEIPT__', '__PARLOR_SIGNING_MODE__',
                            '__PARLOR_SIMULATOR_SDK__', '__PARLOR_SIGNING_RECEIPT__'):
                    with self.assertRaises(RuntimeError):
                        subject.render_owned_kotlin_phase(template.replace(old, 'stale'), source.parent, destination, 'iphonesimulator26.5', mode)
            wrong = root / 'ios-readiness-98'; wrong.mkdir()
            with self.assertRaises(RuntimeError): subject.render_owned_kotlin_phase(template, source.parent, wrong, 'iphonesimulator26.5', 'adhoc')
            with self.assertRaises(RuntimeError): subject.render_owned_kotlin_phase(template, source.parent, destination, 'iphoneos26.5', 'adhoc')


if __name__ == '__main__':
    unittest.main()
