"""Synthetic parser and actual Foundation encoder tests, not iOS runtime proof."""
import copy
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from native_failure_receipts import DOMAINS, STAGES, read_failure, validate_failure

HERE = Path(__file__).resolve().parent
TOKEN = '11111111-1111-1111-1111-111111111111'
BOOT = '22222222-2222-2222-2222-222222222222'


def fixture():
    context = dict(schemaVersion=6, scenario='readiness', runToken=TOKEN,
                   observations=[dict(phase='before_main', boot=BOOT)])
    value = dict(schema_version=1, kind='diagnostic_failure', boot_ordinal=1,
                 process_boot=BOOT, run_token=TOKEN, signing_mode='adhoc',
                 stage='image-observation', error_domain_id=3, error_code=13,
                 error_code_redacted=False)
    return value, context


class NativeFailureReceiptTests(unittest.TestCase):
    def test_exact_bounded_stage_domain_code_and_context(self):
        value, context = fixture()
        for stage in STAGES:
            for domain in DOMAINS:
                candidate = dict(value, stage=stage, error_domain_id=domain,
                                 error_code=13 if domain else 0, error_code_redacted=domain == 0)
                self.assertEqual(validate_failure(candidate, 1, context, 'adhoc'), candidate)

    def test_private_unexpected_or_nonfailure_fields_are_rejected(self):
        value, context = fixture()
        for candidate in (dict(value, path='/unowned'), dict(value, description='private'),
                          dict(value, observation={}), dict(value, kind='observation_complete'),
                          dict(value, stage='an-unreviewed-path')):
            with self.subTest(candidate=candidate), self.assertRaises(RuntimeError):
                validate_failure(candidate, 1, context, 'adhoc')

    def test_inexact_types_bounds_domain_or_redaction_are_rejected(self):
        value, context = fixture()
        for update in (dict(schema_version=True), dict(boot_ordinal=True), dict(error_domain_id=True),
                       dict(error_domain_id=5), dict(error_code=True), dict(error_code=2**31),
                       dict(error_code=-(2**31)-1), dict(error_code_redacted=1),
                       dict(error_code_redacted=True), dict(error_domain_id=0),
                       dict(process_boot='invalid'), dict(run_token='invalid')):
            with self.subTest(update=update), self.assertRaises(RuntimeError):
                validate_failure(dict(value, **update), 1, context, 'adhoc')

    def test_wrong_token_boot_mode_ordinal_or_launch_context_are_rejected(self):
        value, context = fixture()
        for changes in (dict(runToken=BOOT), dict(scenario='settings'), dict(schemaVersion=True),
                        dict(observations=[]), dict(observations=[dict(phase='sample', boot=BOOT)]),
                        dict(observations=[dict(phase='before_main', boot=TOKEN)])):
            with self.subTest(changes=changes), self.assertRaises(RuntimeError):
                validate_failure(value, 1, dict(context, **changes), 'adhoc')
        for ordinal, mode in ((True, 'adhoc'), (0, 'adhoc'), (2, 'adhoc'), (1, 'disabled'), (1, 'Store')):
            with self.subTest(ordinal=ordinal, mode=mode), self.assertRaises(RuntimeError):
                validate_failure(value, ordinal, context, mode)

    def test_exclusive_bounded_file_reader(self):
        value, context = fixture()
        with tempfile.TemporaryDirectory(prefix='parlor-native-failure-') as raw:
            path = Path(raw) / 'parlor-native-readiness-failure-1.json'
            path.write_text(json.dumps(value))
            self.assertEqual(read_failure(path, 1, context, 'adhoc'), value)
            for encoded in (json.dumps(value)[:-1] + ',"error_code":13}', 'x' * 1025, ''):
                path.write_text(encoded)
                with self.assertRaises((RuntimeError, ValueError)):
                    read_failure(path, 1, context, 'adhoc')
            path.unlink()
            target = Path(raw) / 'target.json'; target.write_text(json.dumps(value))
            path.symlink_to(target)
            with self.assertRaises(RuntimeError):
                read_failure(path, 1, context, 'adhoc')

    def test_failure_never_replaces_or_passes_success_oracle(self):
        swift = (HERE / 'NativeReadinessLaunch.swift.in').read_text()
        xctest = (HERE / 'NativeReadinessUITests.swift.in').read_text()
        runner = (HERE / 'run_ios_readiness.py').read_text()
        self.assertNotIn('self.nativeReadinessDisplay = "receipt-failed"', swift)
        self.assertIn('try encoded.write(to: url, options: [.withoutOverwriting])', swift)
        self.assertIn('throw NSError(domain: "NativeReadinessDiagnosticFailed", code: 1)', xctest)
        self.assertIn("if receipt.get('native_diagnostic_failures'):\n                raise RuntimeError", runner)
        for secret in ('localizedDescription', 'userInfo', 'failure.domain] ='):
            self.assertNotIn(secret, swift[swift.index('private func nativeReadinessFailureData'):])

    def test_actual_foundation_encoder_redacts_and_preserves_exact_known_codes(self):
        source = (HERE / 'NativeReadinessLaunch.swift.in').read_text()
        start = '// BEGIN BOUNDED FAILURE ENCODER (also executed with Foundation in host controls).'
        end = '// END BOUNDED FAILURE ENCODER.'
        self.assertEqual(source.count(start), 1); self.assertEqual(source.count(end), 1)
        encoder = source.split(start)[1].split(end)[0]
        main = r'''
let token = "11111111-1111-1111-1111-111111111111"
let boot = "22222222-2222-2222-2222-222222222222"
let domains = [NSCocoaErrorDomain, NSPOSIXErrorDomain, "NativeReadinessOwnedImage", "DSC01SyntheticHarness"]
for (index, domain) in domains.enumerated() {
    let data = try nativeReadinessFailureData(stage: "image-observation",
        error: NSError(domain: domain, code: 13, userInfo: [NSLocalizedDescriptionKey: "DO_NOT_RETAIN"]),
        ordinal: 1, boot: boot, token: token, signingMode: "adhoc")
    let value = try JSONSerialization.jsonObject(with: data) as! [String: Any]
    precondition(value["error_domain_id"] as? Int == index + 1)
    precondition(value["error_code"] as? Int == 13 && value["error_code_redacted"] as? Bool == false)
    precondition(!String(decoding: data, as: UTF8.self).contains("DO_NOT_RETAIN"))
}
for error in [NSError(domain: "DO_NOT_RETAIN", code: 13), NSError(domain: NSCocoaErrorDomain, code: Int.max)] {
    let data = try nativeReadinessFailureData(stage: "receipt-write", error: error,
        ordinal: 8, boot: boot, token: token, signingMode: "disabled")
    let value = try JSONSerialization.jsonObject(with: data) as! [String: Any]
    precondition(value["error_code"] as? Int == 0 && value["error_code_redacted"] as? Bool == true)
    precondition(!String(decoding: data, as: UTF8.self).contains("DO_NOT_RETAIN"))
}
do {
    _ = try nativeReadinessFailureData(stage: "/unowned", error: NSError(domain: NSCocoaErrorDomain, code: 1),
        ordinal: 1, boot: boot, token: token, signingMode: "adhoc")
    fatalError("Unsafe stage accepted")
} catch { }
print("FOUNDATION_FAILURE_ENCODER_7_CASES_PASS")
'''
        with tempfile.TemporaryDirectory(prefix='parlor-failure-encoder-') as raw:
            root = Path(raw); swift = root / 'main.swift'; binary = root / 'encoder'
            swift.write_text('import Foundation\n' + encoder + main)
            compiled = subprocess.run(['/usr/bin/xcrun', 'swiftc', '-module-cache-path', str(root / 'module-cache'),
                                       str(swift), '-o', str(binary)], capture_output=True, text=True, timeout=120)
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            executed = subprocess.run([str(binary)], capture_output=True, text=True, timeout=20)
            self.assertEqual(executed.returncode, 0, executed.stderr)
            self.assertEqual(executed.stdout.strip(), 'FOUNDATION_FAILURE_ENCODER_7_CASES_PASS')


if __name__ == '__main__':
    unittest.main()
