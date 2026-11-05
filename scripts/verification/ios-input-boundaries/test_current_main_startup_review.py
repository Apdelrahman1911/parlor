import json
from pathlib import Path
import subprocess
import tempfile
import unittest

import review_current_main_startup as startup


class CurrentMainStartupReviewTest(unittest.TestCase):
    def test_encryption_command_uses_only_the_public_recipient(self):
        argv = startup.envelope_command(Path('native.json'), Path('native.cms'), startup.CERTIFICATE)
        self.assertEqual(argv[:7], ['/usr/bin/openssl', 'smime', '-encrypt', '-aes256', '-binary', '-outform', 'DER'])
        self.assertNotIn('-passin', argv)
        self.assertNotIn('-inkey', argv)
        cert = startup.CERTIFICATE.read_text()
        self.assertIn('BEGIN CERTIFICATE', cert)
        self.assertNotIn('PRIVATE KEY', cert)

    def test_actual_cms_envelope_is_not_plaintext_or_readable_without_the_private_key(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root/'test.txt'; sealed = root/'test.cms'
            source.write_bytes(b'public regression sample only')
            result = subprocess.run(startup.envelope_command(source, sealed, startup.CERTIFICATE),
                                    capture_output=True, timeout=10)
            self.assertEqual(result.returncode, 0)
            self.assertNotIn(source.read_bytes(), sealed.read_bytes())
            result = subprocess.run(['/usr/bin/openssl', 'smime', '-decrypt', '-inform', 'DER',
                                     '-in', str(sealed), '-recip', str(startup.CERTIFICATE)],
                                    capture_output=True, timeout=10)
            self.assertNotEqual(result.returncode, 0)

    def test_native_platform_rows_are_bounded_and_exclude_application_output(self):
        row = dict(processID=38943, eventMessage='service state: spawning', timestamp='2026-11-05 13:16:18.000000+0000')
        self.assertEqual(startup.validate_native_rows(json.dumps([row]).encode()), 1)
        for invalid in ([dict(row, processID=41116)], [dict(row, processID='38943')], [],
                        [dict(row, timestamp='2026-11-05 13:16:18+0100')]):
            with self.subTest(value=invalid), self.assertRaises(ValueError):
                startup.validate_native_rows(json.dumps(invalid).encode())
        with self.assertRaises(ValueError):
            startup.validate_native_rows(b' ' * (4 * 1024**2 + 1))

    def test_no_replay_or_previous_numeric_decode_or_private_upload(self):
        source = Path(startup.__file__).read_text()
        workflow = (startup.frozen.ROOT/'.github/workflows/ios-main-typing-archive-review.yml').read_text()
        self.assertNotIn('simctl', source)
        self.assertNotIn('typeText(', source)
        self.assertNotIn('frozen.reduce_rows(', source)
        self.assertNotIn('frozen.predicate(', source)
        self.assertIn('review_current_main_startup.py', workflow)
        uploads = workflow.split('          path: ')[-1]
        self.assertIn('/receipt.json', uploads)
        self.assertIn('/native-startup.cms', uploads)
        self.assertNotIn('private-native-startup', uploads)
        self.assertNotIn('original-diagnostics', uploads)
        self.assertNotIn('*.json', uploads)
        self.assertNotIn('*.log', uploads)
        self.assertNotIn('contents: write', workflow)
        self.assertNotIn('setup-java', workflow)

    def test_only_the_missing_host_context_is_read_not_the_prior_daemon_query(self):
        self.assertIn('com.apple.CoreSimulator', startup.PREDICATE)
        self.assertIn('dyld', startup.PREDICATE)
        self.assertNotIn('processIdentifier == 38943', startup.PREDICATE)
        self.assertNotIn('process == "kbd"', startup.PREDICATE)
        self.assertNotIn('process == "xpcproxy_sim"', startup.PREDICATE)
        self.assertIn('HOST_PREEXEC_CONTEXT_UNAVAILABLE_IN_ORIGINAL_SIMULATOR_ARCHIVE',
                      Path(startup.__file__).read_text())

    def test_exact_workflow_and_all_companions_remain_frozen(self):
        import sys
        sys.path.insert(0, str(startup.frozen.ROOT))
        from scripts.release import workflow_contract as contract
        contract.verify_current_main_typing_review()
        self.assertIn('scripts/verification/ios-input-boundaries/current_main_startup_custody.crt',
                      contract.CURRENT_MAIN_TYPING_CONTROLS)
        self.assertIn('scripts/verification/ios-input-boundaries/review_current_main_startup.py',
                      contract.CURRENT_MAIN_TYPING_CONTROLS)


if __name__ == '__main__':
    unittest.main()
