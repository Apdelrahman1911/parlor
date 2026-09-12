# A37 retrospective functional/host recipe — not executed by author

Use root's existing Linux lane, after accepting A37 archive/custody and the existing
15e freeze/preflight admission. This is a one-off data evaluation of completed
subscenarios, not a new harness, XCTest rerun or amendment of the native receipt.

Unchanged entrypoints: `l08_functional_receipts.py:129` and `l08_receipts.py:269`;
original runner calls at1162–1169. `available_run_records` (runner helper13–41)
retains bounded/unique JSON, filename, token, mode and operation checks;
`framework_subset` (receipts246–254) checks the full provenance union and object
identity before selecting. The composed runner does not replace these validators.
All required files exist: eight native +20 functional +42 host rows; completed
scenario probes; full log with13 functional/3 host boot markers; both complete
bundle inventories; two framework inventories. Counts are inspection, not PASS.

Run from `/root/projects/Parlor/parlor`; retain stdout separately as retrospective
results. The only relocated control is the exact retained generated CI binding;
do not recreate it at its historical canonical pathname.

```bash
python3 -I -B - <<'PY_A37'
import hashlib, json, sys
from pathlib import Path
R = Path.cwd()
N = R / 'remediation-runs/2026-09-08-continuation'
F = R / 'remediation-runs/2026-09-07-local-readiness/l08-storage-functional-companion-02'
E = N / 'actions/34442662032/native-evidence/ios-readiness-37'
P = E.parent / 'reviewed-preflight'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
load = lambda p: json.loads(p.read_bytes())
assert sha(E/'receipt.json') == 'b254bef327572c116bb8ff899152997ecb13446fcc502fb3cbc62d1f7f0cdd52'
assert sha(E/'input-manifest.json') == '481537a4dae4ebd4c4b034a346e320f55a22dcb98e6733609129d0e475a3bff9'
r, m = load(E/'receipt.json'), load(E/'input-manifest.json')
s = m['source']
assert s == r['source_before'] == r['source_after'] == load(P/'ios-readiness-source-ci.json')['source_identity']
assert (s['commit'], s['tree'], s['source_manifest_sha256']) == (
    '15e8982b35e39006868868a11c6f4e1864e613a7', '8f3fd56f86d7934fddaff068539d69fe26d4aaad',
    '51edfdd6aea7b293afe9061a30d05054f1e0338661eedd8e225bd3ba2ebbb7fd')
assert all(r[k] is True for k in ('source_unchanged', 'controls_unchanged', 'copied_sources_unchanged'))
control = '281ef2fd40d28ac1041d49bc353ff083aa8e50a6c5775e09d09e3887d803ec1f'
assert control == r['approved_control_sha256'] == r['controls_after_sha256'] == m['control_sha256']
assert hashlib.sha256(json.dumps(m['files'], separators=(',', ':')).encode()).hexdigest() == control
assert len(m['files']) == 99 and m['files'] == load(P/'l08-controls.json')['files']
for row in m['files']:
    q = P/'ios-readiness-source-ci.json' if row['path'] == str((N/'ios-readiness-source-ci.json').relative_to(R)) else R/row['path']
    assert q.is_file() and not q.is_symlink() and q.resolve() == q and sha(q) == row['sha256'], row['path']
assert r['cycle'] == 'ios-readiness-37' and r['status'] == r['runtime_evidence_status'] == 'FAIL'
mode = r['signing_mode']
assert mode == 'adhoc'
sig = r['native_signature_inventory']
assert sig and all(x['display_exit_code'] == x['verify_exit_code'] == 0 for x in sig)
assert all(r['public_signature_inspection'][k] == 0 for k in ('display_exit_code', 'entitlements_exit_code', 'verify_exit_code'))
assert (E/'embedded-gradle-stop.txt').read_text() == 'build_exit=0\nstop_exit=0\n'
assert sha(E/'xcodebuild.log') == '67f61c5dc8f96681663ab1261069528294a94950f75a6c143d124905196531aa'
assert sha(E/'executed-framework-inventory.json') == r['executed_framework_inventory_sha256']
sys.path.insert(0, str(F))
from l08_functional_runner import available_run_records
from l08_functional_receipts import verify_storage_functional
from l08_receipts import framework_subset, read_owned_result, verify_host
runs = available_run_records(E, mode)
functional = [x for x in runs if x.get('scenario') == 'l08-storage-functional']
host = [x for x in runs if x.get('scenario') == 'l08-host']
assert (len(runs), len(functional), len(host)) == (70, 20, 42)
native = read_owned_result(E/'probe-readiness-result.json', E, 262144)
token = native['runToken']
assert token == r['app_foundation_context']['run_token']
built = read_owned_result(E/'built-app-binary-inventory.json', E)
installed = read_owned_result(E/'installed-app-binary-inventory.json', E)
assert {(x['path'], x['sha256']) for x in sig} == {(x['resolved_path'], x['sha256']) for x in built['images']}
frameworks = read_owned_result(E/'executed-framework-inventory.json', E)['images']
log = (E/'xcodebuild.log').read_text()
functional_result = verify_storage_functional(functional,
    read_owned_result(E/'probe-l08-storage-functional-result.json', E, 262144),
    token, log, mode, built, installed, framework_subset(functional, runs, frameworks))
host_result = verify_host(host,
    read_owned_result(E/'probe-l08-host-result.json', E, 262144),
    token, log, mode, built, installed, framework_subset(host, runs, frameworks))
print(json.dumps(dict(evaluation='RETROSPECTIVE_DATA_ONLY_NOT_XCTEST', data_run_id=34442662032,
    source_commit=s['commit'], control_sha256=control, original_run_status=r['status'],
    original_matrix_calls='NOT_REACHED_IN_A37', functional=functional_result, host=host_result), indent=2))
PY_A37
```

Both calls retain complete ordered matrices, all native geometry/preferences,
process/token/signing context, loader provenance, per-process byte/UUID binding,
real boot-log corroboration and every functional/host invariant. Do not copy/reload
selected dictionaries after `runs`: subset membership deliberately uses `is`.

Limits: mode/signature assertions retain the available ad-hoc provenance, not new
codesign execution. `read_phase_receipt` (`simulator_signing.py:122–148`) calls
`owned_source`/`observed_phase`, which require the now-deleted hosted copy and
DerivedData directories; it cannot be replayed unchanged on Linux. Do not recreate
those paths or claim its outer-run gate completed. Likewise full OS validation
lacks `probe-os-result.json`; the unchanged five-successful-XCTest guard already
failed. These do not prevent the two data-only calls above. Preserve whole A37
FAIL, original strict L08 NOT_SATISFIED_BY_COMPANION, and every returned Complete
comparison failure. No native runtime/signing/Store or full-campaign PASS follows.
