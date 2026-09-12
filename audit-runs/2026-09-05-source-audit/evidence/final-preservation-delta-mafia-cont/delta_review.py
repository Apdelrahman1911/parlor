#!/usr/bin/env python3
"""Pure AST/path-double checks. Never executes the preservation helper itself."""
import ast
import datetime
import hashlib
import json
from pathlib import Path, PurePosixPath

HERE = Path(__file__).resolve().parent
SOURCE = HERE / 'final_preservation_check.py'
OLD = HERE.parent / 'final-preservation-review-mafia-cont/final_preservation_check.py'
raw = SOURCE.read_bytes()
old = OLD.read_bytes()
assert hashlib.sha256(raw).hexdigest() == '433f254527e4409d9b7dcf9f2851427951a350986b4cd8cd5ec9290a54282d3c'
assert hashlib.sha256(old).hexdigest() == 'd9e852c61ad156b749b107e7476917f9b83cff07add328cb16e05408bd927d4b'
expected = old.decode().replace("ROOT / 'composeApp/build', ROOT / 'build-logic/build'", "ROOT / 'composeApp/build', ROOT / 'iosApp/build', ROOT / 'build-logic/build'")
expected = expected.replace("device_directory_exists=(Path.home() / 'Library/Developer/CoreSimulator/Devices' / u).exists())", "device_directory_exists=(Path.home() / 'Library/Developer/CoreSimulator/Devices' / u).exists() or\n                          (Path.home() / 'Library/Developer/CoreSimulator/Devices' / u).is_symlink())")
assert expected.encode() == raw, 'Unexpected helper change outside requested two deltas'
tree = ast.parse(raw, str(SOURCE))
guarded = next(n for n in tree.body if isinstance(n, ast.Try))
results = []


def assignment(name):
    return next(n for n in guarded.body if isinstance(n, ast.Assign) and
                any(isinstance(t, ast.Name) and t.id == name for t in n.targets))


def run(names, values):
    # Only the named assignment expressions are compiled; no top-level import,
    # receipt write, subprocess, filesystem traversal, or helper main executes.
    nodes = [assignment(name) for name in names]
    exec(compile(ast.Module(body=nodes, type_ignores=[]), str(SOURCE), 'exec'), values)
    return values


def check(name, condition):
    assert condition, name
    results.append(dict(name=name, status='PASS'))


class MockPath:
    existing = set()
    symlinks = set()
    reads = []

    def __init__(self, value):
        self.path = PurePosixPath(str(value))

    def __truediv__(self, other):
        return MockPath(self.path / other)

    def __str__(self):
        return str(self.path)

    def relative_to(self, other):
        return self.path.relative_to(other.path)

    def exists(self):
        self.reads.append(('exists', str(self)))
        return str(self) in self.existing

    def is_symlink(self):
        self.reads.append(('is_symlink', str(self)))
        return str(self) in self.symlinks

    @classmethod
    def home(cls):
        return cls('/synthetic-home')


root = MockPath('/synthetic-repo')
outputs = run(['outputs'], dict(ROOT=root))['outputs']
check('fixed output allowlist includes iosApp/build without changing old paths',
      [str(p.relative_to(root)) for p in outputs] ==
      ['build', 'composeApp/build', 'iosApp/build', 'build-logic/build', 'build-logic/convention/build'])


def output_check(existing=(), symlinks=()):
    MockPath.existing, MockPath.symlinks, MockPath.reads = set(existing), set(symlinks), []
    return run(['remaining_outputs'], dict(ROOT=root, outputs=outputs))['remaining_outputs']


check('absent output directories stay empty', output_check() == [])
check('existing iosApp/build detected',
      output_check(existing=['/synthetic-repo/iosApp/build']) == ['iosApp/build'])
check('dangling iosApp/build symlink detected',
      output_check(symlinks=['/synthetic-repo/iosApp/build']) == ['iosApp/build'])
check('old root output symlink detection preserved',
      output_check(symlinks=['/synthetic-repo/build']) == ['build'])

device = '/synthetic-home/Library/Developer/CoreSimulator/Devices/owned-uuid'


def device_check(existing=(), symlinks=()):
    MockPath.existing, MockPath.symlinks, MockPath.reads = set(existing), set(symlinks), []
    records = run(['device_records'], dict(Path=MockPath, devices={'owned-uuid'}))['device_records']
    return records


missing = device_check()
check('absent owned simulator record stays false', missing == [dict(uuid='owned-uuid', device_directory_exists=False)])
directory = device_check(existing=[device])
check('existing owned simulator directory detected', directory == [dict(uuid='owned-uuid', device_directory_exists=True)])
dangling = device_check(symlinks=[device])
check('dangling owned simulator symlink detected', dangling == [dict(uuid='owned-uuid', device_directory_exists=True)])
check('only exact owned simulator path observed in metadata checks',
      MockPath.reads == [('exists', device), ('is_symlink', device)])
unowned = device_check(existing=[device.replace('owned-uuid', 'unowned-uuid')])
check('unowned simulator path not traversed or treated as owned',
      not unowned[0]['device_directory_exists'] and all(p == device for _, p in MockPath.reads))

clean = dict(remaining_outputs=[], remaining_temp=[], owned_matches=[], attested_still_alive=[],
             port_check_errors=[], adb_port_checks=[], gradle8=[], running_receipts=[], device_records=[])
check('iosApp output blocks cleanup verdict',
      not run(['cleanup_ok'], dict(clean, remaining_outputs=['iosApp/build']))['cleanup_ok'])
check('dangling owned simulator blocks cleanup verdict',
      not run(['cleanup_ok'], dict(clean, device_records=dangling))['cleanup_ok'])
check('missing owned simulator does not block cleanup verdict',
      run(['cleanup_ok'], dict(clean, device_records=missing))['cleanup_ok'])

receipt = dict(recorded_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
               reviewer='/root/mafia_cont', source_sha256=hashlib.sha256(raw).hexdigest(),
               prior_sha256=hashlib.sha256(old).hexdigest(), exact_two_change_delta=True,
               assertions=len(results), results=results,
               execution='Only selected AST assignments and synthetic Path doubles; no actual checker, process, Gradle, Xcode, or simulator command.',
               limits='No actual OS/runtime cleanup asserted. All path-existence/symlink answers are synthetic.',
               cleanup='No generated build outputs or persistent processes. Compact source snapshots/test/JSON are required audit evidence.')
(HERE / 'delta-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps(dict(assertions=len(results), status='PASS', source_sha256=receipt['source_sha256'])))
