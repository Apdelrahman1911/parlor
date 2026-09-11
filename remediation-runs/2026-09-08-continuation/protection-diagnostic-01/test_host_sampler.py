"""Synthetic byte-policy regressions only; no compiler or native execution."""
from pathlib import Path
import unittest

import host_sampler


class HostSamplerCopyTests(unittest.TestCase):
    def source(self):
        return (Path(__file__).resolve().parent / 'ProtectionSampler.m').read_bytes()

    def test_only_host_preamble_and_exact_platform_predicate_change(self):
        source = self.source()
        copied = host_sampler.transform(source)
        self.assertEqual(copied.replace(host_sampler.PREAMBLE, b'').replace(
            host_sampler.HOST_PLATFORM, host_sampler.ORIGINAL_PLATFORM), source)
        self.assertEqual(copied.count(host_sampler.ORIGINAL_GUARD.replace(
            host_sampler.ORIGINAL_PLATFORM, host_sampler.HOST_PLATFORM)), 1)
        self.assertEqual(self.source(), source)

    def test_pin_drift_double_transform_and_wrong_type_fail(self):
        source = self.source()
        for altered in (source + b'\n', source.replace(b'uuid &&', b'YES &&'),
                        host_sampler.transform(source), source.decode(), None):
            with self.subTest(kind=type(altered).__name__), self.assertRaises(RuntimeError):
                host_sampler.transform(altered)

    def test_policy_is_native_host_only_and_exact_public_platform_arrays(self):
        copied = host_sampler.transform(self.source())
        self.assertIn(b'!TARGET_OS_OSX || TARGET_OS_SIMULATOR || TARGET_OS_MACCATALYST || !defined(__arm64__)', copied)
        self.assertIn(b'PLATFORM_MACOS != 1 || PLATFORM_MACCATALYST != 6', copied)
        self.assertEqual(host_sampler.HOST_PLATFORM, b'([platforms isEqualToArray:@[@(PLATFORM_MACOS)]] ||\n'
            b'               [platforms isEqualToArray:@[@(PLATFORM_MACOS), @(PLATFORM_MACCATALYST)]])')
        self.assertNotIn(b'ptrauth', copied)


if __name__ == '__main__':
    unittest.main()
