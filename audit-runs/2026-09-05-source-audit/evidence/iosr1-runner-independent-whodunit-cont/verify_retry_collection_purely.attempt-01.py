#!/usr/bin/env python3
"""Execute AST-extracted collection only, with in-memory paths and no processes."""
import ast
import datetime
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
from types import SimpleNamespace

BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[3]
source = BASE / 'draft-7e6abf8cb71e.py'
raw = source.read_bytes()
expected_sha = '7e6abf8cb71ea66fe73123323f6cbc907381af595a0a3127679627bd9fc1b5aa'
assert hashlib.sha256(raw).hexdigest() == expected_sha
tree = ast.parse(raw.decode())
block = next(n for n in ast.walk(tree) if isinstance(n, ast.Try)
             and any(s.lineno == 490 for s in n.body))
statements = [s for s in block.body if 490 <= s.lineno <= 537]
assert len(statements) == 11
compiled = compile(ast.Module(body=statements, type_ignores=[]), str(source), 'exec')
uuid = '00000000-0000-0000-0000-000000000002'
container = '/synthetic-home/Library/Developer/CoreSimulator/Devices/' + uuid + '/data/Containers/Data/Application/AUDIT'
checks = []


def scenario(name, *, extraction=0, failed_test=False, oversized=False,
             symlink=False, wrong_owner=False, missing=False):
    operations = []
    actual_container = container.replace(uuid, 'different-owner') if wrong_owner else container
    result_path = actual_container + '/tmp/parlor-audit-iosr1-result.json'
    content = 'x' * 8193 if oversized else '{"harness_status":"observation_complete"}'
    files = {'/synthetic-evidence/embedded-gradle-stop.txt': 'build_exit=0\nstop_exit=0\n'}
    if not missing:
        files[result_path] = content

    class MemoryPath:
        def __init__(self, value):
            self.value = str(value)

        def __str__(self):
            return self.value

        def __truediv__(self, value):
            return MemoryPath(str(PurePosixPath(self.value) / value))

        def __eq__(self, other):
            return isinstance(other, MemoryPath) and self.value == other.value

        @property
        def parent(self):
            return MemoryPath(str(PurePosixPath(self.value).parent))

        @classmethod
        def home(cls):
            return cls('/synthetic-home')

        def resolve(self):
            return self

        def relative_to(self, other):
            return PurePosixPath(self.value).relative_to(str(other))

        def exists(self):
            return self.value == '/synthetic-temp/Results.xcresult' or self.value in files

        def is_file(self):
            return self.value in files

        def is_dir(self):
            return False

        def is_symlink(self):
            return symlink and self.value == result_path

        def stat(self):
            return SimpleNamespace(st_size=len(files[self.value].encode()))

        def read_text(self):
            operations.append('read:' + self.value)
            return files[self.value]

    def command(args, log_name):
        operations.append('command:' + log_name)
        if log_name in ('xcresult-summary.json', 'xcresult-tests.json'):
            files['/synthetic-evidence/' + log_name] = '{}'
            return extraction
        if log_name == 'owned-container.log':
            assert args[:4] == ['xcrun', 'simctl', 'get_app_container', uuid]
            files['/synthetic-evidence/' + log_name] = actual_container
        return 0

    def copyfile(src, dst):
        operations.append('copy:' + str(src))
        files[str(dst)] = files[str(src)]

    def verify_xctest(summary, tests, device):
        operations.append('verify_xctest')
        assert device == uuid
        if failed_test:
            raise RuntimeError('synthetic failed XCTest')
        return {'result': 'Passed'}

    def verify_probe(report):
        operations.append('verify_probe')
        assert report['harness_status'] == 'observation_complete'

    receipt = {}
    namespace = {'command': command, 'results': MemoryPath('/synthetic-temp/Results.xcresult'),
                 'receipt': receipt, 'dest': MemoryPath('/synthetic-evidence'), 'uuid': uuid,
                 'APP_ID': 'com.parlor.app.debug', 'Path': MemoryPath,
                 'shutil': SimpleNamespace(copyfile=copyfile), 'temp': MemoryPath('/synthetic-temp'),
                 'json': json, 'verify_xctest': verify_xctest, 'verify_probe': verify_probe,
                 'xcode': 0, 'digest': lambda _: (_ for _ in ()).throw(AssertionError('not expected'))}
    error = None
    try:
        exec(compiled, namespace)
    except (RuntimeError, ValueError) as caught:
        error = type(caught).__name__ + ': ' + str(caught)
    copied = '/synthetic-evidence/probe-result.json' in files
    if extraction or failed_test:
        assert copied and error and receipt.get('runtime_evidence_status') != 'PASS'
        if failed_test:
            assert operations.index('copy:' + result_path) < operations.index('verify_xctest')
        else:
            assert 'verify_xctest' not in operations
    elif oversized or symlink or wrong_owner:
        assert not copied and error and receipt.get('runtime_evidence_status') != 'PASS'
        assert 'verify_xctest' not in operations
    elif missing:
        assert not copied and error and receipt['runtime_evidence_status'] == 'FAIL'
    else:
        assert copied and not error and receipt['runtime_evidence_status'] == 'PASS'
        assert operations.index('copy:' + result_path) < operations.index('verify_xctest')
        assert operations.index('verify_xctest') < operations.index('verify_probe')
    checks.append({'name': name, 'status': 'PASS', 'copied': copied,
                   'expected_error': error, 'operations': operations})


scenario('successful observations still require XCTest and probe validation')
scenario('failed XCTest does not destroy completed bounded probe before cleanup', failed_test=True)
scenario('failed xcresult extraction still preserves completed bounded probe', extraction=1)
scenario('oversized probe refuses copy', oversized=True)
scenario('probe file symlink refuses copy', symlink=True)
scenario('another device container refuses read/copy', wrong_owner=True)
scenario('missing probe never becomes runtime PASS', missing=True)

header = ROOT / 'audit-runs/2026-09-05-source-audit/evidence/iosr1-apphost-01/ComposeApp.generated.h'
header_raw = header.read_bytes()
match = re.search(r'__attribute__\(\(swift_name\("IOSR1AppHostProbe"\)\)\)\s*@interface [^\n]+.*?\n@end', header_raw.decode(), re.S)
assert match and re.search(r'-\s*\(void\)startOnJson:\(void\s*\(\^\)\(NSString\s*\*\)\)onJson', match.group(0))
assert all(x in match.group(0) for x in ('swift_name("shared")', 'swift_name("start(onJson:)")', 'swift_name("cancel()")', 'NSString'))
checks.append({'name': 'actual cycle01 generated header satisfies unchanged fail-closed callback recognizer', 'status': 'PASS'})

environment_update = next(n for n in ast.walk(tree) if isinstance(n, ast.Call)
                          and isinstance(n.func, ast.Attribute) and isinstance(n.func.value, ast.Name)
                          and n.func.value.id == 'env' and n.func.attr == 'update' and n.lineno == 348)
environment_keywords = {k.arg: ast.literal_eval(k.value) for k in environment_update.keywords
                        if isinstance(k.value, ast.Constant)}
assert 'EXPANDED_CODE_SIGN_IDENTITY' not in {k.arg for k in environment_update.keywords}
assert environment_keywords['CODE_SIGNING_ALLOWED'] == environment_keywords['CODE_SIGNING_REQUIRED'] == 'NO'
assert environment_keywords['CODE_SIGN_IDENTITY'] == environment_keywords['DEVELOPMENT_TEAM'] == ''
checks.append({'name': 'retry omits expanded identity while keeping explicit unsigned simulator constraints', 'status': 'PASS'})

receipt = {'recorded_at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'reviewer': '/root/whodunit_cont',
           'runner_sha256': expected_sha, 'runner_snapshot': str(source.relative_to(ROOT)),
           'test_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
           'actual_cycle01_header_sha256': hashlib.sha256(header_raw).hexdigest(),
           'status': 'PASS', 'checks': checks,
           'limitations': 'AST statements490-537 run only with entirely fake commands/in-memory paths/validation spies. Native tools, app or OS not run. Existing separate pure tests exercise actual validators. Header read as evidence, not new compilation or Swift linking.',
           'cleanup': 'No task processes, temporary filesystem, or build outputs created. Only this compact audit evidence retained.'}
output = BASE / 'retry-collection-synthetic-7e6abf8cb71e.json'
output.write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps({'status': 'PASS', 'checks': len(checks), 'evidence': str(output.relative_to(ROOT))}, indent=2))
