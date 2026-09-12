"""Pure control tests with explicit imported-source binding; no native workers."""
import hashlib
import importlib
import json
from pathlib import Path
import sys
import unittest


CAMPAIGN = Path(__file__).resolve().parents[1]
ORIGINAL = CAMPAIGN / 'evidence/release-content-followup/android-arm64-runner'
DRAFT = ORIGINAL / 'darwin-null-port-retry-01'
PINS = {
    ORIGINAL / 'darwin_owned_processes.py': '148a71180b6768858f47d7cf5607104169898dafdee48ecbf41937cd46e71a1d',
    ORIGINAL / 'owned_arm64_smoke.py': '38ea4c0000a2c426f8475b4616e77ee0d8bb25bb424dc7516348c2095cd08b48',
    ORIGINAL / 'test_darwin_owned_processes.py': '30e28929a5305fbef65c855d56e5e2c16d9fc2c5b30c1c7e3bc5f0ef5350f9c9',
    ORIGINAL / 'test_owned_arm64_smoke.py': '404e2fa8f88f140cb5a1d40395e8422bcc35b8714dbe6020e60cef8ca7f4b29a',
    DRAFT / 'darwin_owned_processes.py': '2f2ad2a5d68cea999709a4c3c3e384cd9e6767258ad34aed4181ee2118962378',
    DRAFT / 'test_task_name_null_retry.py': '0fb15e65e59812213a0952c90ed90fb07057da92bd39bb46bae911d9588623b6',
}


def hashes():
    actual = {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in PINS}
    assert actual == {str(path): digest for path, digest in PINS.items()}, 'Reviewed controls changed'
    return actual


before = hashes()
sys.path[:0] = [str(DRAFT), str(ORIGINAL)]
expected = {
    'darwin_owned_processes': DRAFT / 'darwin_owned_processes.py',
    'owned_arm64_smoke': ORIGINAL / 'owned_arm64_smoke.py',
    'test_darwin_owned_processes': ORIGINAL / 'test_darwin_owned_processes.py',
    'test_owned_arm64_smoke': ORIGINAL / 'test_owned_arm64_smoke.py',
    'test_task_name_null_retry': DRAFT / 'test_task_name_null_retry.py',
}
modules = {name: importlib.import_module(name) for name in expected}
imports = {name: str(Path(module.__file__).resolve()) for name, module in modules.items()}
assert imports == {name: str(path) for name, path in expected.items()}, 'Unexpected imported code'
suite = unittest.TestSuite(
    unittest.defaultTestLoader.loadTestsFromModule(module)
    for name, module in modules.items() if name.startswith('test_')
)
result = unittest.TextTestRunner(verbosity=2).run(suite)
after = hashes()
summary = {'source_before': before, 'source_after': after, 'imports': imports,
           'executed': result.testsRun, 'failures': len(result.failures), 'errors': len(result.errors),
           'skips': len(result.skipped), 'successful': result.wasSuccessful()}
print(json.dumps(summary, indent=2))
raise SystemExit(0 if result.wasSuccessful() and result.testsRun == 61 and not result.skipped else 1)
