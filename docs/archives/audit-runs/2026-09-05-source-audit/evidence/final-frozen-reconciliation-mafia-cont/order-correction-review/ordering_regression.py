#!/usr/bin/env python3
"""Test hash-bound audit helper ASTs using only an in-memory fake environment."""
import ast
import collections
import copy
import datetime
import fnmatch
import hashlib
import json
from pathlib import Path, PurePosixPath
import posixpath
from types import SimpleNamespace

HERE = Path(__file__).resolve().parent
AUDIT = HERE.parents[2]
OLD = AUDIT / 'evidence/final-generation-order-history-01/assemble_coverage.py'
NEW = HERE / 'inputs/assemble_coverage.py'
HASHES = {OLD: '09b2a9003e9c0c3cf0e78bd79ca495fcea7a7778b636d8f3a4d4257881619c29',
          NEW: 'e7105933194aa93bbe9bd0db33f6c932907deb139cc9d4a809392235933531c2'}
TEXT = {p: p.read_text() for p in HASHES}
for path, expected in HASHES.items():
    assert hashlib.sha256(TEXT[path].encode()).hexdigest() == expected
TREES = {p: ast.parse(text, str(p)) for p, text in TEXT.items()}
checks = []


def check(name, condition):
    assert condition, name
    checks.append(dict(name=name, status='PASS'))


def assigned(node, name):
    return isinstance(node, ast.Assign) and any(
        isinstance(n, ast.Name) and n.id == name for target in node.targets for n in ast.walk(target))


def bounds(tree):
    main = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'main')
    start = next(i for i, n in enumerate(main.body) if assigned(n, 'current_rows'))
    end = next(i for i, n in enumerate(main.body) if isinstance(n, ast.Expr) and
               isinstance(n.value, ast.Call) and isinstance(n.value.func, ast.Attribute) and
               n.value.func.attr == 'write_text' and any(isinstance(v, ast.Constant) and
               v.value == 'evidence/final-state.json' for v in ast.walk(n)))
    return main, start, end


old_main, old_start, old_end = bounds(TREES[OLD])
new_main, new_start, new_end = bounds(TREES[NEW])
old_block = old_main.body[old_start:old_end + 1]
new_block = new_main.body[new_start:new_end + 1]
dump = lambda nodes: ast.dump(ast.Module(body=nodes, type_ignores=[]), include_attributes=False)
check('moved source-comparison block AST unchanged', dump(old_block) == dump(new_block))
expected_tree = copy.deepcopy(TREES[OLD])
expected_main, start, end = bounds(expected_tree)
block = expected_main.body[start:end + 1]
del expected_main.body[start:end + 1]
insertion = next(i for i, n in enumerate(expected_main.body) if isinstance(n, ast.For)
                 and isinstance(n.target, ast.Name) and n.target.id == 'file')
expected_main.body[insertion:insertion] = block
check('entire module AST is only source-comparison block relocation',
      ast.dump(expected_tree, include_attributes=False) == ast.dump(TREES[NEW], include_attributes=False))
old_lines = TEXT[OLD].splitlines(True)
block_text = ''.join(old_lines[old_block[0].lineno - 1:old_block[-1].end_lineno])
comment = ('    # Publish this invocation\'s source-comparison receipt before resolving\n'
           '    # audit-material versions: final-state itself has historical read receipts.\n'
           '    # Resolving them first would bind a digest that this invocation overwrites.\n')
insertion_line = '    for file in sorted((OUT / "coverage").glob("reviews-*.jsonl")):\n'
expected_text = TEXT[OLD].replace(block_text, '', 1).replace(insertion_line, comment + block_text + insertion_line, 1)
check('exact textual delta is unchanged28-line block moved plus3comments',
      len(block_text.splitlines()) == 28 and TEXT[NEW] == expected_text and
      len(TEXT[OLD].splitlines()) == 280 and len(TEXT[NEW].splitlines()) == 283)


class MemoryPath:
    def __init__(self, fs, value):
        self.fs, self.value = fs, posixpath.normpath(str(value))

    def __truediv__(self, value):
        return MemoryPath(self.fs, PurePosixPath(self.value) / value)

    def __str__(self):
        return self.value

    def __eq__(self, other):
        return isinstance(other, MemoryPath) and self.fs is other.fs and self.value == other.value

    def __lt__(self, other):
        return self.value < other.value

    def __hash__(self):
        return hash((id(self.fs), self.value))

    @property
    def parents(self):
        return [MemoryPath(self.fs, p) for p in PurePosixPath(self.value).parents]

    @property
    def suffix(self):
        return PurePosixPath(self.value).suffix

    def relative_to(self, other):
        return PurePosixPath(self.value).relative_to(other.value)

    def resolve(self):
        return MemoryPath(self.fs, self.fs['resolved'].get(self.value, self.value))

    def is_file(self):
        self.fs['calls'].append(('is_file', self.value))
        return self.value in self.fs['files']

    def read_bytes(self):
        self.fs['calls'].append(('read_bytes', self.value))
        return self.fs['files'][self.value]

    def read_text(self):
        self.fs['calls'].append(('read_text', self.value))
        return self.fs['files'][self.value].decode('utf-8')

    def write_text(self, text):
        self.fs['calls'].append(('write_text', self.value))
        self.fs['files'][self.value] = text.encode('utf-8')
        return len(text)

    def glob(self, pattern):
        assert pattern == 'reviews-*.jsonl'
        assert self.value == '/synthetic/parlor/audit-runs/test/coverage'
        return [MemoryPath(self.fs, path) for path in self.fs['files']
                if str(PurePosixPath(path).parent) == self.value and
                fnmatch.fnmatch(PurePosixPath(path).name, pattern)]


ROOT = '/synthetic/parlor'
OUT = ROOT + '/audit-runs/test'
SOURCE_PATH = 'composeApp/src/commonMain/kotlin/Synthetic.kt'
IMAGE_PATH = 'assets/synthetic.png'
NOTE_PATH = 'audit-runs/test/evidence/note.txt'
SHA = lambda data: hashlib.sha256(data).hexdigest()


def receipt(path, payload, **overrides):
    return dict(dict(path=path, sha256=SHA(payload), status='READ_COMPLETE', reviewed_ranges=[[1, 2]],
                     reviewer='synthetic-reviewer'), **overrides)


def make_fixture(extras=(), extra_files=None):
    source = b'first\nsecond\n'
    binary = b'\x89PNG\r\n\x1a\n'
    inventory = [dict(path=SOURCE_PATH, kind='text', lines=2, sha256=SHA(source)),
                 dict(path=IMAGE_PATH, kind='binary', lines=None, sha256=SHA(binary)),
                 dict(path='protected.synthetic', kind='protected', lines=None, sha256=None, status='PROTECTED_EXCLUSION')]
    receipts = [receipt(SOURCE_PATH, source), receipt(IMAGE_PATH, binary, status='BINARY_INSPECTED', reviewed_ranges=[])]
    baseline = dict(branch='main', commit='synthetic-sha', tree='synthetic-tree', refs='synthetic-refs',
                    stashes='', status_including_audit_path='?? AGENTS.md\n?? audit-runs/test/')
    files = {OUT + '/baseline.json': json.dumps(baseline).encode(),
             OUT + '/coverage/inventory.jsonl': '\n'.join(map(json.dumps, inventory)).encode(),
             OUT + '/coverage/reviews-fixture.jsonl': '\n'.join(map(json.dumps, receipts + list(extras))).encode(),
             OUT + '/canonical-register.json': b'{"findings":[]}',
             OUT + '/evidence/focused-storage-01/gradle.log': b'not-a-graph-line\nAUDIT_synthetic-graph\n',
             ROOT + '/' + SOURCE_PATH: source, ROOT + '/' + IMAGE_PATH: binary}
    files.update({ROOT + '/' + path: data for path, data in (extra_files or {}).items()})
    git_values = {('branch', '--show-current'): baseline['branch'],
                  ('rev-parse', 'HEAD'): baseline['commit'], ('rev-parse', 'HEAD^{tree}'): baseline['tree'],
                  ('for-each-ref', '--format=%(refname) %(objectname)'): baseline['refs'],
                  ('stash', 'list', '--format=%gd %H'): baseline['stashes'],
                  ('status', '--porcelain=v1', '--untracked-files=no'): '',
                  ('status', '--porcelain=v1', '--untracked-files=all'): baseline['status_including_audit_path']}
    return dict(files=files, resolved={}, calls=[], current_rows=copy.deepcopy(inventory),
                git_values=git_values, print=[], clock=0, diff_rc=0)


def invoke(fs, source=NEW):
    root, out = MemoryPath(fs, ROOT), MemoryPath(fs, OUT)

    def run_path(path):
        assert path == OUT + '/audit_inventory.py'
        fs['calls'].append(('mock_scan', path))
        return {'scan': lambda: (copy.deepcopy(fs['current_rows']), ['synthetic-pruned-path'])}

    def git(*args):
        fs['calls'].append(('mock_git', args))
        return fs['git_values'][args]

    def run(argv, **kwargs):
        assert argv == ['git', 'diff', '--check'] and kwargs == dict(cwd=root, capture_output=True)
        fs['calls'].append(('mock_git_diff_check', tuple(argv)))
        return SimpleNamespace(returncode=fs['diff_rc'])

    def now():
        fs['clock'] += 1
        return 'synthetic-timestamp-' + str(fs['clock'])

    values = dict(collections=collections, hashlib=hashlib, json=json, ROOT=root, OUT=out,
                  INVENTORY=out / 'coverage/inventory.jsonl', Path=lambda p: MemoryPath(fs, p),
                  runpy=SimpleNamespace(run_path=run_path), subprocess=SimpleNamespace(run=run),
                  now=now, git=git, print=fs['print'].append)
    nodes = [copy.deepcopy(n) for n in TREES[source].body if isinstance(n, ast.FunctionDef)
             and n.name in {'digest', 'merged', 'missing', 'applicability', 'main'}]
    exec(compile(ast.Module(body=nodes, type_ignores=[]), 'hash-bound-in-memory-audit-test', 'exec'), values)
    status = values['main']()
    return dict(status=status, summary=json.loads(fs['files'][OUT + '/coverage/summary.json']),
                state=json.loads(fs['files'][OUT + '/evidence/final-state.json']),
                output=[json.loads(line) for line in fs['files'][OUT + '/coverage/FINAL_COVERAGE.jsonl'].splitlines()],
                receipts=[json.loads(line) for line in fs['files'][OUT + '/coverage/AUDIT_MATERIAL_REVIEW_RECEIPTS.jsonl'].splitlines()],
                fs=fs)


def run_case(extras=(), extra_files=None, mutate=None, source=NEW):
    fs = make_fixture(extras, extra_files)
    if mutate:
        mutate(fs)
    return invoke(fs, source)


legacy = ['VERIFICATION.md', 'verification-ledger.json', 'CLEANUP_LEDGER.json', 'evidence/final-state.json']
legacy_data = {name: ('historical ' + name + '\nread line\n').encode() for name in legacy}
legacy_receipts = [receipt('audit-runs/test/' + name, data,
                          version_snapshot_ref='audit-runs/test/history/' + name) for name, data in legacy_data.items()]
legacy_files = {'audit-runs/test/' + name: data for name, data in legacy_data.items()}
legacy_files.update({'audit-runs/test/history/' + name: data for name, data in legacy_data.items()})
legacy_files['audit-runs/test/evidence/final-state.json'] = b'previous generated state\n'
old = run_case(legacy_receipts, legacy_files, source=OLD)
old_final = next(r for r in old['receipts'] if r['path'].endswith('/evidence/final-state.json'))
check('old helper deterministically records stale current digest for its own final-state',
      old['status'] == 0 and old_final['current_path_sha256'] != SHA(old['fs']['files'][OUT + '/evidence/final-state.json']))
old_calls = old['fs']['calls']
check('old resolver reads final-state before overwriting it',
      old_calls.index(('read_bytes', OUT + '/evidence/final-state.json')) <
      old_calls.index(('write_text', OUT + '/evidence/final-state.json')))
new = run_case(legacy_receipts, legacy_files)
check('corrected helper writes final-state before receipt resolution',
      new['fs']['calls'].index(('write_text', OUT + '/evidence/final-state.json')) <
      new['fs']['calls'].index(('read_bytes', OUT + '/evidence/final-state.json')))
new_final = next(r for r in new['receipts'] if r['path'].endswith('/evidence/final-state.json'))
check('corrected own-final-state receipt archive-binds exact historical bytes and actual current digest',
      new['status'] == 0 and not new_final['attests_current_version'] and
      new_final['verified_review_version_path'] == 'audit-runs/test/history/evidence/final-state.json' and
      new_final['current_path_sha256'] == SHA(new['fs']['files'][OUT + '/evidence/final-state.json']))
check('ordering correction leaves canonical source coverage bytes unchanged', new['output'] == old['output'])
check('audit-material receipts excluded from finite source accounting',
      new['summary']['inventory_entries'] == 3 and new['summary']['text_lines'] == 2 and
      new['summary']['verbatim_read_line_count'] == 2 and new['summary']['audit_material_read_receipts_excluded_from_source_counts'] == 4)
for name in legacy[:3]:
    new['fs']['files'][OUT + '/' + name] = ('newly regenerated ' + name + '\n').encode()
check('later verifier overwrite reproduces three stale current attestations without a final pass',
      all(r['attests_current_version'] and r['current_path_sha256'] != SHA(new['fs']['files'][ROOT + '/' + r['path']])
          for r in new['receipts'] if not r['path'].endswith('/evidence/final-state.json')))
last = invoke(new['fs'])
check('final corrected pass archive-binds all4historical rows with actual current digests',
      last['status'] == 0 and len(last['receipts']) == 4 and all(
          not r['attests_current_version'] and '/history/' in r['verified_review_version_path'] and
          r['current_path_sha256'] == SHA(last['fs']['files'][ROOT + '/' + r['path']]) and
          SHA(last['fs']['files'][ROOT + '/' + r['verified_review_version_path']]) == r['sha256']
          for r in last['receipts']))
check('repeat corrected pass leaves source ledger and candidate-independent counts unchanged',
      last['output'] == old['output'] and last['summary']['inventory_entries'] == 3 and last['summary']['text_lines'] == 2)

note = b'one\ntwo\n'
base_note = receipt(NOTE_PATH, note)
good = run_case([base_note], {NOTE_PATH: note})
check('unchanged current text receipt still attests only matching current bytes', good['status'] == 0 and good['receipts'][0]['attests_current_version'])
changed_note = dict(base_note, version_snapshot_ref='audit-runs/test/history/note.txt')
archive = {'audit-runs/test/history/note.txt': note, NOTE_PATH: b'changed\n'}


def blocked(name, item, files, expected_error, mutate=None):
    result = run_case([item], files, mutate)
    check(name, result['status'] == 1 and any(expected_error in e for e in result['summary']['errors']))


blocked('changed receipt without historical reference stays rejected', base_note, {NOTE_PATH: b'changed'}, 'Changed task-evidence read receipt')
blocked('missing historical file stays rejected', changed_note, {NOTE_PATH: b'changed'}, 'Invalid historical task-evidence snapshot')
blocked('wrong historical digest stays rejected', changed_note,
        {NOTE_PATH: b'changed', 'audit-runs/test/history/note.txt': b'wrong'}, 'Invalid historical task-evidence snapshot')
blocked('outside-audit historical archive stays rejected', dict(changed_note, version_snapshot_ref='elsewhere/note.txt'),
        {NOTE_PATH: b'changed', 'elsewhere/note.txt': note}, 'Invalid historical task-evidence snapshot')
blocked('escaping historical symlink stays rejected', changed_note, archive, 'Invalid historical task-evidence snapshot',
        lambda fs: fs['resolved'].update({OUT + '/history/note.txt': '/unrelated/note.txt'}))
blocked('missing current path cannot borrow historical evidence', changed_note,
        {'audit-runs/test/history/note.txt': note}, 'Invalid task-evidence receipt path')
blocked('escaping current symlink stays rejected before content inspection', base_note, {NOTE_PATH: note},
        'Invalid task-evidence receipt path', lambda fs: fs['resolved'].update({ROOT + '/' + NOTE_PATH: '/unrelated/note.txt'}))
blocked('out-of-range current audit text receipt stays rejected', dict(base_note, reviewed_ranges=[[1, 3]]),
        {NOTE_PATH: note}, 'Invalid task-evidence source range')
blocked('out-of-range historical audit text receipt stays rejected', dict(changed_note, reviewed_ranges=[[0, 2]]),
        archive, 'Invalid task-evidence source range')
blocked('baseline source hash mismatch stays rejected', receipt(SOURCE_PATH, b'wrong'), {}, 'Read receipt hash mismatch')
blocked('baseline source range mismatch stays rejected', receipt(SOURCE_PATH, b'first\nsecond\n', reviewed_ranges=[[1, 3]]),
        {}, 'Invalid source range')
blocked('unknown non-audit receipt path stays rejected', receipt('unlisted.txt', note), {}, 'Unknown read-receipt path')

binary_data = b'\x89PNG\r\n\x1a\n'
binary_path = 'audit-runs/test/evidence/image.png'
binary = receipt(binary_path, binary_data, status='BINARY_VISUAL_INSPECTION', reviewed_ranges=[],
                 total_lines=None, reviewed_line_count=0, read_method='Synthetic binary envelope; not image-validity certification')
valid_binary = run_case([binary], {binary_path: binary_data})
check('audit binary is accepted without text decoding or source coverage credit', valid_binary['status'] == 0 and
      ('read_text', ROOT + '/' + binary_path) not in valid_binary['fs']['calls'] and valid_binary['output'] == old['output'])
check('other explicit binary classification with null optional counts remains accepted',
      run_case([dict(binary, status='BINARY_INSPECTED', reviewed_ranges=None, reviewed_line_count=None)], {binary_path: binary_data})['status'] == 0)
for key, value in [('reviewed_ranges', [[1, 1]]), ('total_lines', 0), ('reviewed_line_count', 1),
                   ('read_method', ''), ('read_method', ' \t'), ('read_method', None), ('read_method', True)]:
    blocked('malformed binary metadata rejected: ' + key + '=' + repr(value), dict(binary, **{key: value}),
            {binary_path: binary_data}, 'Invalid binary task-evidence receipt')
try:
    run_case([dict(binary, status='READ_COMPLETE')], {binary_path: binary_data})
except UnicodeDecodeError:
    check('binary mislabelled text fails strict decode rather than inferred exemption', True)
else:
    raise AssertionError('Expected strict text decoding failure')

for label, mutate in [
    ('changed baseline source', lambda fs: fs['current_rows'][0].update(sha256=SHA(b'changed'))),
    ('missing baseline source', lambda fs: fs['current_rows'].pop(0)),
    ('added non-audit source', lambda fs: fs['current_rows'].append(dict(path='added.txt', kind='text', lines=1, sha256=SHA(b'a')))),
    ('changed branch', lambda fs: fs['git_values'].update({('branch', '--show-current'): 'different'})),
    ('changed refs', lambda fs: fs['git_values'].update({('for-each-ref', '--format=%(refname) %(objectname)'): 'different'})),
    ('changed stashes', lambda fs: fs['git_values'].update({('stash', 'list', '--format=%gd %H'): 'different'})),
    ('changed untracked listing', lambda fs: fs['git_values'].update({('status', '--porcelain=v1', '--untracked-files=all'): 'different'})),
    ('tracked modification', lambda fs: fs['git_values'].update({('status', '--porcelain=v1', '--untracked-files=no'): ' M synthetic.kt'})),
]:
    before, after = run_case(mutate=mutate, source=OLD), run_case(mutate=mutate)
    check('preservation rejection retained: ' + label, before['status'] == after['status'] == 1 and
          sorted(before['summary']['errors']) == sorted(after['summary']['errors']))

for document in ['README.md', 'RESEARCH_AND_CLEANUP.md']:
    original = AUDIT / 'evidence/final-generation-order-history-01/documentation' / document
    current = HERE / 'inputs' / document
    check('documentation change is append-only: ' + document, current.read_bytes().startswith(original.read_bytes()))
readme = (HERE / 'inputs/README.md').read_text()
check('README explicitly requires preservation-checker-last after verification generation',
      '`final_preservation_check.py`, `assemble_verification.py`, and\n**`final_preservation_check.py` again, last**' in readme)
old_receipts = [json.loads(l) for l in (AUDIT / 'evidence/final-generation-order-history-01/documentation/evidence/final-root-closeout-source-reads.jsonl').read_text().splitlines()]
new_receipts = [json.loads(l) for l in (HERE / 'inputs/evidence/final-root-closeout-source-reads.jsonl').read_text().splitlines()]
altered = [(before, after) for before, after in zip(old_receipts, new_receipts) if before != after]
check('one root historical read merely adds exact snapshot binding, does not alter original attestation',
      len(old_receipts) == len(new_receipts) == 13 and len(altered) == 1 and
      all(altered[0][1][k] == v for k, v in altered[0][0].items()) and
      altered[0][1]['sha256'] == HASHES[OLD] and
      (AUDIT.parents[1] / altered[0][1]['version_snapshot_ref']).read_bytes() == TEXT[OLD].encode())

result = dict(recorded_at=datetime.datetime.now(datetime.timezone.utc).isoformat(), reviewer='/root/mafia_cont',
              classification='APPROVED_AUDIT_HELPER_ORDERING_DELTA', source_hashes={str(p): sha for p, sha in HASHES.items()},
              test_script_sha256=SHA(Path(__file__).read_bytes()), assertions=len(checks), results=checks,
              scope='Exact AST/text delta inspection plus hash-bound function AST invocation against a wholly in-memory filesystem, clock, Git, subprocess and inventory. No actual helper script, production code, Gradle, Xcode, process query, device, server or build executed.',
              limitations=['Does not approve actual regenerated ledgers before a fresh independent reconciliation.',
                           'Only ordering and unchanged validation/accounting semantics established; no application finding count or behavior changes.',
                           'Historical/current bindings remain valid only if no subsequent writer alters indexed paths.',
                           'Binary fixture validates metadata/decoding/accounting, not image pixels or artifact validity.'],
              cleanup='No generated build outputs or persistent workers. Compact audit-only snapshots, test source and receipt retained.')
with (HERE / 'ordering-regression-receipt.json').open('x') as handle:
    json.dump(result, handle, indent=2)
    handle.write('\n')
print(json.dumps(dict(status='PASS', assertions=len(checks), source_sha256=HASHES[NEW])))
