#!/usr/bin/env python3
"""Execute only receipt/accounting AST fragments with an in-memory filesystem."""
import ast
import collections
import copy
import datetime
import hashlib
import json
from pathlib import Path, PurePosixPath
import posixpath
import struct
import zlib

HERE = Path(__file__).resolve().parent
AUDIT = HERE.parents[1]
NEW = HERE / 'assemble_coverage-09b2a9.py'
PROPOSED = HERE / 'assemble_coverage.py'
OLD = AUDIT / 'evidence/final-apphost-closeout-history/assemble_coverage.py'
expected = {NEW: '09b2a9003e9c0c3cf0e78bd79ca495fcea7a7778b636d8f3a4d4257881619c29',
            PROPOSED: '11d6236f6bb1f93f7917d01775f6b71d8186cd90b25a133f847a3f377e61b8ba',
            OLD: 'e8d37238b066bf2a1c30bdae3a2a2e366a3f59123077c0b13e277ef7034a28ae'}
for path, sha in expected.items():
    assert hashlib.sha256(path.read_bytes()).hexdigest() == sha
trees = {path: ast.parse(path.read_bytes(), str(path)) for path in expected}
png_path = AUDIT / 'evidence/iosr1-apphost-02/attachments/37C51B0A-CC33-456F-A23D-7CCB68CDFBBC.png'
png = png_path.read_bytes()
png_sha = hashlib.sha256(png).hexdigest()
assert png_sha == '1898984f1f4c2c56176577271b5e871f8ea607807833215db818d5b9d3f50371'
assert png[:8] == b'\x89PNG\r\n\x1a\n'
cursor, chunks = 8, []
while cursor < len(png):
    size, kind = struct.unpack('>I4s', png[cursor:cursor + 8])
    payload = png[cursor + 8:cursor + 8 + size]
    crc = struct.unpack('>I', png[cursor + 8 + size:cursor + 12 + size])[0]
    assert zlib.crc32(kind + payload) & 0xffffffff == crc
    chunks.append(kind.decode('ascii'))
    cursor += 12 + size
assert cursor == len(png) and chunks[0] == 'IHDR' and chunks[-1] == 'IEND'
width, height = struct.unpack('>II', png[16:24])
assert (width, height) == (1206, 2622)
checks = []


def check(name, condition):
    assert condition, name
    checks.append(dict(name=name, status='PASS'))


class MemoryPath:
    def __init__(self, fs, value):
        self.fs, self.value = fs, posixpath.normpath(str(value))

    def __truediv__(self, value):
        return MemoryPath(self.fs, PurePosixPath(self.value) / value)

    def __str__(self):
        return self.value

    def __eq__(self, other):
        return isinstance(other, MemoryPath) and self.fs is other.fs and self.value == other.value

    def __hash__(self):
        return hash((id(self.fs), self.value))

    @property
    def parents(self):
        return [MemoryPath(self.fs, p) for p in PurePosixPath(self.value).parents]

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


def execute(nodes, values):
    exec(compile(ast.Module(body=nodes, type_ignores=[]), 'reviewed-coverage-fragments', 'exec'), values)
    return values


def fixture(item=None, payload=png, history=None, missing_current=False, escaped=None, source=NEW):
    tree = trees[source]
    main = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'main')
    file_loop = next(n for n in main.body if isinstance(n, ast.For))
    line_loop = copy.deepcopy(next(n for n in file_loop.body if isinstance(n, ast.For)))
    line_loop.iter = ast.copy_location(ast.Name(id='enumerated_lines', ctx=ast.Load()), line_loop.iter)
    text_path = 'composeApp/src/commonMain/kotlin/Synthetic.kt'
    binary_path = 'assets/synthetic.png'
    text = b'first\nsecond\n'
    inventory = [dict(path=text_path, kind='text', lines=2, sha256=hashlib.sha256(text).hexdigest()),
                 dict(path=binary_path, kind='binary', lines=None, sha256=png_sha)]
    base = [dict(path=text_path, sha256=inventory[0]['sha256'], status='READ_COMPLETE', reviewed_ranges=[[1, 2]], reviewer='synthetic'),
            dict(path=binary_path, sha256=png_sha, status='BINARY_INSPECTED', reviewed_ranges=[], reviewer='synthetic')]
    fs = dict(files={'/synthetic/audit/canonical-register.json': b'{"findings":[]}'}, resolved={}, calls=[])
    current = '/synthetic/audit/evidence/image.png'
    if not missing_current:
        fs['files'][current] = payload
    if history:
        fs['files'].update({'/synthetic/' + k: v for k, v in history.items()})
    if escaped:
        fs['resolved'].update(escaped)
    root = MemoryPath(fs, '/synthetic')
    out = root / 'audit'
    values = dict(Path=Path, collections=collections, json=json, hashlib=hashlib,
                  ROOT=root, OUT=out, inventory=inventory, by_path={r['path']: r for r in inventory},
                  receipts=collections.defaultdict(list), errors=[], audit_material_receipts=[],
                  file=out / 'coverage/reviews-fixture.jsonl',
                  enumerated_lines=list(enumerate([json.dumps(v) for v in base + ([item] if item is not None else [])], 1)))
    defs = [n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name in {'digest', 'merged', 'missing', 'applicability'}]
    execute(defs + [line_loop], values)
    # Run unchanged source-accounting logic only; stop before inventory scan,
    # Git commands, report writes, runpy, or the real helper entry point.
    start = next(i for i, n in enumerate(main.body) if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'register' for t in n.targets))
    finish = next(i for i, n in enumerate(main.body) if isinstance(n, ast.Assign) and isinstance(n.targets[0], ast.Tuple) and any(isinstance(t, ast.Name) and t.id == 'current_rows' for t in n.targets[0].elts))
    execute(main.body[start:finish], values)
    values['memory_fs'] = fs
    return values


binary = dict(path='audit/evidence/image.png', sha256=png_sha, status='BINARY_VISUAL_INSPECTION',
              reviewed_ranges=[], total_lines=None, reviewed_line_count=0,
              read_method='Actual PNG signature/IHDR/CRC inspection; prior independent visual receipts separate.', reviewer='synthetic')
baseline = fixture()
baseline_output = baseline['output']
actual = fixture(binary)
check('actual PNG signature, dimensions, complete chunk CRC envelope', width == 1206 and height == 2622 and cursor == len(png))
check('actual PNG accepted as explicit audit-only binary without errors', not actual['errors'] and len(actual['audit_material_receipts']) == 1)
check('actual PNG never decoded as UTF8 text', ('read_text', '/synthetic/audit/evidence/image.png') not in actual['memory_fs']['calls'])
check('baseline source output byte-structurally unchanged after PNG evidence', actual['output'] == baseline_output)
check('PNG not inserted into source receipt map', 'audit/evidence/image.png' not in actual['receipts'])
check('alternate explicit BINARY_INSPECTED status accepted', not fixture(dict(binary, status='BINARY_INSPECTED'))['errors'])
check('null count fields without fictitious ranges accepted', not fixture(dict(binary, reviewed_ranges=None, reviewed_line_count=None))['errors'])
for key, value in [('reviewed_ranges', [[1, 1]]), ('total_lines', 0), ('total_lines', 1),
                   ('reviewed_line_count', 1), ('reviewed_line_count', -1),
                   ('read_method', ''), ('read_method', None), ('read_method', '  \n\t'),
                   ('read_method', True), ('read_method', ['method']), ('read_method', 42)]:
    result = fixture(dict(binary, **{key: value}))
    check('malformed binary receipt blocked: ' + key + '=' + repr(value),
          any('Invalid binary task-evidence receipt' in e for e in result['errors']) and result['output'] == baseline_output)
for method in ['   ', True, ['claimed-method']]:
    prior = fixture(dict(binary, read_method=method), source=PROPOSED)
    check('preserved11d623 malformed-method counterexample: ' + repr(method), not prior['errors'])

try:
    fixture(binary, source=OLD)
except UnicodeDecodeError:
    check('old271-line assembler cannot UTF8-decode actual PNG', True)
else:
    raise AssertionError('Expected historical PNG decode failure')

text = b'one\ntwo\n'
text_receipt = dict(path='audit/evidence/image.png', sha256=hashlib.sha256(text).hexdigest(),
                    status='READ_COMPLETE', reviewed_ranges=[[1, 2]], reviewer='synthetic')
read_text = fixture(text_receipt, payload=text)
check('ordinary audit text still reads complete claimed ranges', not read_text['errors'] and ('read_text', '/synthetic/audit/evidence/image.png') in read_text['memory_fs']['calls'])
check('ordinary text invalid range remains error', any('Invalid task-evidence source range' in e for e in fixture(dict(text_receipt, reviewed_ranges=[[1, 3]]), payload=text)['errors']))
try:
    fixture(dict(binary, status='READ_COMPLETE', reviewed_ranges=[]))
except UnicodeDecodeError:
    check('mislabelled binary-as-text remains failed decoding, not automatic binary exemption', True)
else:
    raise AssertionError('Expected strict text decoding failure')

historical = fixture(dict(binary, version_snapshot_ref='audit/history/image.png'), payload=b'current changed bytes', history={'audit/history/image.png': png})
record = historical['audit_material_receipts'][0]
check('historical PNG hash matched without textual lines', not historical['errors'] and record['verified_review_version_path'] == 'audit/history/image.png')
check('historical inspection does not attest current bytes', record['attests_current_version'] is False and record['current_path_sha256'] != png_sha)
check('changed bytes without version snapshot blocked', any('Changed task-evidence read receipt' in e for e in fixture(binary, payload=b'changed')['errors']))
check('wrong historical hash blocked', any('Invalid historical task-evidence snapshot' in e for e in fixture(dict(binary, version_snapshot_ref='audit/history/image.png'), payload=b'changed', history={'audit/history/image.png': b'wrong'})['errors']))
check('historical path outside audit blocked', any('Invalid historical task-evidence snapshot' in e for e in fixture(dict(binary, version_snapshot_ref='elsewhere/image.png'), payload=b'changed', history={'elsewhere/image.png': png})['errors']))
check('historical symlink escaping audit blocked', any('Invalid historical task-evidence snapshot' in e for e in fixture(dict(binary, version_snapshot_ref='audit/history/image.png'), payload=b'changed', history={'audit/history/image.png': png}, escaped={'/synthetic/audit/history/image.png': '/unrelated/image.png'})['errors']))
check('missing current audit path cannot borrow historical proof', any('Invalid task-evidence receipt path' in e for e in fixture(dict(binary, version_snapshot_ref='audit/history/image.png'), missing_current=True, history={'audit/history/image.png': png})['errors']))
check('current symlink escaping audit blocked before content read', any('Invalid task-evidence receipt path' in e for e in fixture(binary, escaped={'/synthetic/audit/evidence/image.png': '/unrelated/image.png'})['errors']))
historical_text = fixture(dict(text_receipt, version_snapshot_ref='audit/history/old.txt'), payload=b'changed', history={'audit/history/old.txt': text})
check('historical text handling unchanged', not historical_text['errors'] and not historical_text['audit_material_receipts'][0]['attests_current_version'])

# Format validation is external to the ledger parser: an inspection receipt may
# legitimately document malformed bytes. It must never create source lines.
bad_bytes = b'not a valid PNG'
bad = fixture(dict(binary, sha256=hashlib.sha256(bad_bytes).hexdigest(), read_method='Inspected malformed PNG bytes; not a valid image'), payload=bad_bytes)
check('binary metadata classification is not image-validity certification and adds no source lines', not bad['errors'] and bad['output'] == baseline_output)

old_main = next(n for n in trees[OLD].body if isinstance(n, ast.FunctionDef) and n.name == 'main')
new_main = next(n for n in trees[NEW].body if isinstance(n, ast.FunctionDef) and n.name == 'main')
old_tail = old_main.body[next(i for i, n in enumerate(old_main.body) if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'register' for t in n.targets)):]
new_tail = new_main.body[next(i for i, n in enumerate(new_main.body) if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'register' for t in n.targets)):]
check('all baseline accounting, source preservation, summary and return AST unchanged',
      ast.dump(ast.Module(body=old_tail, type_ignores=[]), include_attributes=False) == ast.dump(ast.Module(body=new_tail, type_ignores=[]), include_attributes=False))

receipt = dict(recorded_at=datetime.datetime.now(datetime.timezone.utc).isoformat(), reviewer='/root/mafia_cont',
               approved_source_sha256=expected[NEW], rejected_intermediate_sha256=expected[PROPOSED],
               original_source_sha256=expected[OLD], assertions=len(checks), results=checks,
               actual_png=dict(path=str(png_path), sha256=png_sha, bytes=len(png), width=width, height=height,
                               chunk_crc_validation=True, chunks=chunks, decoded_or_visually_inspected_by_this_reviewer=False),
               scope='Only receipt-loop and source-accounting AST fragments with in-memory paths; actual PNG bytes read for hashing/format envelope. No helper main, report aggregation, Git, runpy, process/device or build command.',
               limits=['No additional screenshot visual/semantic or runtime claim.',
                       'Ledger parser does not certify arbitrary binary format validity or truth of a claimed inspection; independent reviewers/format evidence do.',
                       'Malformed text decoding still raises; outer final checker must retain its existing fail-closed handling.'],
               cleanup='No build outputs/persistent processes; only compact audit test/JSON/source snapshots retained.')
(HERE / 'synthetic-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps(dict(assertions=len(checks), status='PASS', source_sha256=expected[NEW], actual_png_sha256=png_sha)))
