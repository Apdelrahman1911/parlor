#!/usr/bin/env python3
"""Reconcile immutable A31 observations with unchanged validators, not a runtime rerun."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
F = ROOT / 'remediation-runs/2026-09-07-local-readiness/l08-storage-functional-companion-02'
E = ROOT / 'remediation-runs/2026-09-08-continuation/actions/34384037292/native-evidence/ios-readiness-31'
SOURCE = '965eb79cea7dd40ced8efa0b1fcd98596f7a952b'
OUT = Path(sys.argv[1]).resolve(strict=True)
assert OUT.is_dir() and OUT.is_relative_to(ROOT / 'remediation-runs/2026-09-07-local-readiness/evidence')
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip() == SOURCE
assert not subprocess.check_output(['git', 'diff', '--name-only', 'HEAD', '--'], cwd=ROOT)

def bindings(paths):
    result = []
    for path in sorted(set(paths)):
        assert path.is_file() and not path.is_symlink() and path.stat().st_size <= 2 * 1024 * 1024
        result.append({'path': str(path.relative_to(ROOT)), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    return result

inputs = list(E.iterdir()) + list(F.glob('*.py')) + list((ROOT / 'scripts/verification/ios-readiness').glob('*.py')) + [Path(__file__).resolve()]
before = bindings(inputs)
sys.path.insert(0, str(F))
import run_ios_readiness as runner
import l08_functional_receipts as functional
import l08_receipts as host
import native_readiness_receipts as native
import prerequisite_receipts as prerequisite
import probe_validation as probes
from artifact_inventory import bind_loaded_images

read = lambda name: json.loads((E / name).read_text())
log = (E / 'xcodebuild.log').read_text()
receipt = read('receipt.json')
assert receipt['source_before']['commit'] == SOURCE and receipt['source_after'] == receipt['source_before']
assert receipt['status'] == 'FAIL' and receipt['cleanup_status'] == 'PASS'
built, installed = read('built-app-binary-inventory.json'), read('installed-app-binary-inventory.json')
inventories = read('executed-framework-inventory.json')['images']
native_rows = [read(f'parlor-native-readiness-boot-{i}.json') for i in range(1, 9)]
storage_rows = [read(name) for name in functional.functional_result_names()]
host_rows = [read(name) for name in host.host_result_names()]
all_rows = native_rows + storage_rows + host_rows
native_report = read('probe-readiness-result.json')
settings = read('probe-settings-result.json')
token = native_report['runToken']
subset = lambda rows: host.framework_subset(rows, all_rows, inventories)
calls = []

def record(name, check, expected_failure=False):
    try:
        result = check()
        calls.append({'name': name, 'verification_status': 'RETURNED', 'result': result,
                      'expected_failure': expected_failure})
    except Exception as error:
        calls.append({'name': name, 'verification_status': 'FAIL', 'error_type': type(error).__name__,
                      'error': str(error), 'expected_failure': expected_failure})

record('catalogue_single_tap_routes', lambda: runner.verify_catalog_receipts(log))
record('functional_storage', lambda: functional.verify_storage_functional(storage_rows,
       read('probe-l08-storage-functional-result.json'), token, log, 'adhoc', built, installed, subset(storage_rows)))
record('retained_host', lambda: host.verify_host(host_rows, read('probe-l08-host-result.json'),
       token, log, 'adhoc', built, installed, subset(host_rows)))
record('settings', lambda: probes.verify_settings(settings, runner.verify_legacy_settings_probe))
for game in ('whodunit', 'mafia'):
    record('local_' + game, lambda game=game: probes.verify_local(read('probe-' + game + '-result.json'), game))
record('mafia_public_start', lambda: prerequisite.verify_start(log))
record('native_readiness', lambda: native.verify_native_readiness(native_rows, native_report, settings,
       log, native.read_synthetic_seed_cleanup(E / 'parlor-native-synthetic-seed-cleanup.json'), 'adhoc'))
record('native_loaded_images', lambda: bind_loaded_images(built, installed, native_rows, subset(native_rows)))
# These original gates remain failures. No exception is translated into a PASS.
record('original_xctest_aggregate', lambda: runner.verify_xctest(read('xcresult-summary.json'),
       read('xcresult-tests.json'), receipt['owned_uuid']), expected_failure=True)
record('actual_os_multilingual_prerequisite', lambda: prerequisite.verify_os_prerequisite(log), expected_failure=True)
after = bindings(inputs)
assert before == after
unexpected = [c['name'] for c in calls if (c['verification_status'] == 'FAIL') != c['expected_failure']]
result = {'schema_version': 1, 'kind': 'OFFLINE_EXISTING_VALIDATORS_ON_ACTUAL_A31_DATA_NOT_NEW_RUNTIME',
          'source_commit': SOURCE, 'run_id': 34384037292, 'run_attempt': 1,
          'original_A31_status': 'FAIL', 'readiness': 'NOT_READY',
          'status': 'RECONCILIATION_FINISHED' if not unexpected else 'RECONCILIATION_HAS_UNEXPECTED_RESULTS',
          'inputs_before': before, 'inputs_after_identical': True, 'calls': calls,
          'unexpected_results': unexpected, 'os_report_present': (E / 'probe-os-result.json').exists(),
          'limits': 'Only returned subgate results accept their historical scope. Main XCTest, OS and strict protection are not cleared. No app, simulator, or native code was executed here.'}
with (OUT / 'reconciliation.json').open('x') as stream:
    json.dump(result, stream, indent=2)
    stream.write('\n')
print(json.dumps({'status': result['status'], 'calls': [{k: c[k] for k in ('name', 'verification_status')} for c in calls],
                  'unexpected_results': unexpected, 'readiness': 'NOT_READY'}))
sys.exit(bool(unexpected))
