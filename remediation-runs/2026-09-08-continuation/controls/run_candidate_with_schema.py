#!/usr/bin/env python3
"""Owned Linux schema prerequisites around the unchanged approved consumer.

Only the fixed candidate adapter or its fixed synthetic control driver can run.
No pip/global install, arbitrary command interface, schema waiver or publication.
The outer run_gradle_cycle.py lane owns process/Gradle/output finalization.
"""
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import importlib.metadata
import importlib.util
import io
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import shutil
import signal
import stat
import sys
import tempfile
import urllib.request
import zipfile


SELF = Path(__file__).absolute()
ROOT = SELF.parents[3]
CAMPAIGN = ROOT / 'remediation-runs/2026-09-07-local-readiness'
REVIEWS = CAMPAIGN / 'reviews'
ADAPTER = REVIEWS / 'run_candidate_input_01.py'
DRIVER = REVIEWS / 'run_schema_consumer_controls_01.py'
CONTROL_PINS = {
    ADAPTER: '2a306819fb41b1883139fb42355d493f02e1d93a687f54d06f43ffbb758afa60',
    DRIVER: '78222a79d0d19cdce28077506ce1fd74481cc419fdf9dce9e9e5b6fd8840336f',
    REVIEWS / 'dependency-candidate-consumer-03/candidate_consumer.py': 'eb6e2baaac22739b4652bc63c6b57a24374ee57fb81d4711b981b872e8ac1c7f',
    REVIEWS / 'dependency-candidate-consumer-03/test_candidate_consumer.py': '92eef687dbf037b310b00c9b678a5718d95cf5e8313094861c398554755a3535',
    SELF.parents[1] / 'reviews/schema-base-prerequisites-research-01.json': '54c9f96db0ead50008cab5c8c960c0540351922f949757e03dc91b8c5328b13e',
}
# Official PyPI metadata/bytes and dependency closure are retained in the pinned
# research record. The only native wheel is explicitly CPython3.12/Linux x86_64.
WHEELS = (
    ('jsonschema', '4.25.1', ('jsonschema', 'jsonschema-4.25.1.dist-info'),
     'https://files.pythonhosted.org/packages/bf/9c/8c95d856233c1f82500c2450b8c68576b4cf1c871db3afac5c34ff84e6fd/jsonschema-4.25.1-py3-none-any.whl',
     90040, '3fba0169e345c7175110351d456342c364814cfcf3b964ba4587f22915230a63'),
    ('referencing', '0.36.2', ('referencing', 'referencing-0.36.2.dist-info'),
     'https://files.pythonhosted.org/packages/c1/b1/3baf80dc6d2b7bc27a95a67752d0208e410351e3feb4eb78de5f77454d8d/referencing-0.36.2-py3-none-any.whl',
     26775, 'e8699adbbf8b5c7de96d8ffa0eb5c158b3beafce084968e2ea8bb08c6794dcd0'),
    ('attrs', '25.3.0', ('attr', 'attrs', 'attrs-25.3.0.dist-info'),
     'https://files.pythonhosted.org/packages/77/06/bb80f5f86020c4551da315d78b3ab75e8228f89f0162f2c3a819e407941a/attrs-25.3.0-py3-none-any.whl',
     63815, '427318ce031701fea540783410126f03899a97ffc6f61596ad581ac2e40e3bc3'),
    ('jsonschema-specifications', '2025.4.1', ('jsonschema_specifications', 'jsonschema_specifications-2025.4.1.dist-info'),
     'https://files.pythonhosted.org/packages/01/0e/b27cdbaccf30b890c40ed1da9fd4a3593a5cf94dae54fb34f8a4b74fcd3f/jsonschema_specifications-2025.4.1-py3-none-any.whl',
     18437, '4653bffbd6584f7de83a67e0d620ef16900b390ddc7939d56684d6c81e33f1af'),
    ('rpds-py', '0.27.1', ('rpds', 'rpds_py-0.27.1.dist-info'),
     'https://files.pythonhosted.org/packages/ed/7b/8f4fee9ba1fb5ec856eb22d725a4efa3deb47f769597c809e03578b0f9d9/rpds_py-0.27.1-cp312-cp312-manylinux_2_17_x86_64.manylinux2014_x86_64.whl',
     386883, '466bfe65bd932da36ff279ddd92de56b042f2266d752719beb97b08526268ec5'),
    ('typing-extensions', '4.15.0', ('typing_extensions.py', 'typing_extensions-4.15.0.dist-info'),
     'https://files.pythonhosted.org/packages/18/67/36e9267722cc04a6b9f15c7f3441c2363321a3ea07da7ae0c0707beb2a9c/typing_extensions-4.15.0-py3-none-any.whl',
     44614, 'f0fa19c6845758ab08074a0cfa8b7aecb71c999ca73d62883bc25cc018c4e548'),
)
MODULES = ('jsonschema', 'referencing', 'attr', 'attrs', 'jsonschema_specifications', 'rpds', 'typing_extensions')


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def control_hashes():
    result = {}
    for path, expected in {**CONTROL_PINS, SELF: None}.items():
        require(path.resolve(strict=True) == path and path.is_file(), 'Control path type')
        digest = sha(path.read_bytes())
        require(expected is None or digest == expected, 'Control bytes differ')
        result[str(path.relative_to(ROOT))] = digest
    return result


def owned_destination():
    tmp = Path(os.environ['TMPDIR']).absolute()
    require(tmp.resolve(strict=True) == tmp and tmp.name == 'tmp' and tmp.parent.name == 'scratch'
            and tmp.parents[2] == CAMPAIGN / 'evidence', 'Coordinated owned TMPDIR required')
    return tmp, tmp.parents[1]


def require_platform():
    require(sys.platform == 'linux' and platform.machine() == 'x86_64'
            and sys.implementation.name == 'cpython' and sys.version_info[:2] == (3, 12),
            'Bootstrap is reviewed only for Linux x86_64 CPython3.12')
    libc, version = platform.libc_ver()
    require(libc == 'glibc' and re.fullmatch(r'[0-9]+\.[0-9]+', version) is not None
            and tuple(map(int, version.split('.'))) >= (2, 17), 'Pinned wheel requires glibc>=2.17')
    require(sys.dont_write_bytecode, 'Run this bootstrap with -B')


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise ValueError('Public wheel redirect refused')


def fetch_wheel(wheel):
    _, _, _, url, size, digest = wheel
    with urllib.request.build_opener(NoRedirect()).open(url, timeout=30) as response:
        require(response.status == 200 and response.geturl() == url, 'Public wheel origin')
        raw = response.read(size + 1)
    require(len(raw) == size and sha(raw) == digest, 'Public wheel byte identity')
    return raw


def extract_wheel(raw, wheel, owned):
    distribution, version, roots, url, size, digest = wheel
    require(len(raw) == size and sha(raw) == digest, 'Unverified wheel extraction refused')
    members, total = [], 0
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        require(0 < len(archive.infolist()) <= 512, 'Wheel entry bound')
        for item in archive.infolist():
            name = item.filename
            parts = name.split('/')
            require(0 < len(name) <= 240 and not name.startswith('/') and not name.endswith('.pth')
                    and not any(c in name for c in ('\\', ':', '\x00'))
                    and all(ord(c) >= 32 and ord(c) != 127 for c in name)
                    and all(part not in ('', '.', '..') for part in parts)
                    and PurePosixPath(name).parts[0] in roots, 'Wheel member path')
            require(stat.S_IFMT(item.external_attr >> 16) in (0, stat.S_IFREG) and not item.is_dir()
                    and 0 <= item.file_size <= 2 * 1024 * 1024, 'Wheel member type/size')
            total += item.file_size
            require(total <= 8 * 1024 * 1024, 'Wheel expansion bound')
            data = archive.read(item)
            require(len(data) == item.file_size, 'Wheel member length')
            path = owned.joinpath(*parts)
            path.parent.mkdir(parents=True, exist_ok=True)
            with path.open('xb') as stream:
                stream.write(data)
            members.append([name, len(data), sha(data)])
    return {'distribution': distribution, 'version': version, 'url': url, 'bytes': size,
            'sha256': digest, 'expanded_bytes': total, 'members': members}


def recheck_packages(owned, packages):
    expected = {name: (size, digest) for package in packages for name, size, digest in package['members']}
    require(len(expected) == sum(len(package['members']) for package in packages), 'Wheel member collision')
    actual = {}
    for path in owned.rglob('*'):
        require(not path.is_symlink(), 'Owned package symlink')
        if path.is_dir():
            continue
        require(path.is_file(), 'Owned package type')
        relative = str(path.relative_to(owned))
        require(relative in expected and path.stat().st_size == expected[relative][0], 'Owned package inventory')
        actual[relative] = (path.stat().st_size, sha(path.read_bytes()))
    require(actual == expected, 'Owned package bytes changed')


def verify_imports(owned, report):
    paths = {}
    for name in (*MODULES, 'rpds.rpds'):
        module = sys.modules.get(name)
        require(module is not None and getattr(module, '__file__', None), 'Expected schema module was not imported')
        path = Path(module.__file__).resolve(strict=True)
        require(owned in path.parents, 'Schema module came from outside owned packages')
        paths[name] = {'path': str(path.relative_to(owned)), 'sha256': sha(path.read_bytes())}
    report['imported_modules'] = paths


@contextmanager
def base_packages(tmp, report):
    require(not any(name in sys.modules for name in (*MODULES, 'lark', 'rfc3987_syntax')),
            'Schema libraries must load after owned setup')
    owned = Path(tempfile.mkdtemp(prefix='schema-base-', dir=tmp)).resolve(strict=True)
    identity = owned.lstat()
    old_path = sys.path[:]
    try:
        packages = []
        report['packages'] = packages
        for wheel in WHEELS:
            report['pending_distribution'] = wheel[0]
            packages.append(extract_wheel(fetch_wheel(wheel), wheel, owned))
        report.pop('pending_distribution')
        sys.path.insert(0, str(owned))
        importlib.invalidate_caches()
        versions = {}
        for name, version, *_ in WHEELS:
            distribution = importlib.metadata.distribution(name)
            require(distribution.version == version and Path(distribution.locate_file('')).resolve(strict=True) == owned,
                    'Schema distribution version/origin differs')
            versions[name] = version
        report['versions'] = versions
        # find_spec(top-level) verifies import resolution without prematurely
        # loading jsonschema before the unchanged helper's optional parsers.
        for name in MODULES:
            spec = importlib.util.find_spec(name)
            require(spec is not None and spec.origin and owned in Path(spec.origin).resolve(strict=True).parents,
                    'Schema import resolution is not owned')
        yield owned
        verify_imports(owned, report)
        recheck_packages(owned, packages)
    finally:
        sys.path[:] = old_path
        importlib.invalidate_caches()
        after = owned.lstat()
        require(stat.S_ISDIR(after.st_mode) and (after.st_dev, after.st_ino, after.st_uid)
                == (identity.st_dev, identity.st_ino, identity.st_uid), 'Base parser cleanup ownership changed')
        shutil.rmtree(owned)
        report['base_package_cleanup'] = not owned.exists()


def execute_fixed(mode, arguments):
    path = DRIVER if mode == 'controls' else ADAPTER
    spec = importlib.util.spec_from_file_location('parlor_fixed_schema_entry', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    old_argv = sys.argv[:]
    try:
        sys.argv[:] = [str(path), *arguments]
        return module.main()
    finally:
        sys.argv[:] = old_argv


def coupled_result(mode, destination):
    def read(name):
        path = destination / name
        require(path.resolve(strict=True) == path and path.is_file() and path.stat().st_size <= 4 * 1024 * 1024,
                'Inner receipt path/type/size')
        return json.loads(path.read_text())
    if mode == 'controls':
        value = read('schema-consumer-controls.json')
        require(value['status'] == 'PASS_SYNTHETIC_CONTROLS' and value['tests_run'] == 16
                and value['failures'] == [] and value['errors'] == [] and value['skipped'] == []
                and value.get('ephemeral_parser_cleanup') is True and 'error_type' not in value,
                'Synthetic control receipt did not pass')
    else:
        candidate, prerequisites = read('candidate-input-verification.json'), read('candidate-prerequisites.json')
        require(candidate['status'] == 'PASS_SCOPED_CANDIDATE_INPUTS'
                and prerequisites['status'] == 'PASS_SCOPED_CANDIDATE_INPUT_EXECUTION'
                and prerequisites.get('consumer_exit_code') == 0 and 'error_type' not in prerequisites
                and prerequisites.get('ephemeral_parser_cleanup') is True, 'Coupled candidate receipts did not pass')


def main(argv=None):
    arguments = list(sys.argv[1:] if argv is None else argv)
    mode = 'controls' if arguments == ['--controls'] else 'candidate'
    if mode == 'controls':
        arguments = []
    else:
        require(len(arguments) == 2 and re.fullmatch(r'[a-f0-9]{64}', arguments[1]) is not None,
                'Expected explicit binding and reviewed SHA256, or --controls')
    tmp, destination = owned_destination()
    report_path = destination / 'schema-base-prerequisites.json'
    require(not report_path.exists() and not report_path.is_symlink(), 'Existing bootstrap receipt')
    report = {'schema_version': 1, 'status': 'FAIL', 'mode': mode, 'started_at': datetime.now(timezone.utc).isoformat(),
              'scope': 'Owned prerequisites only; unchanged scoped consumer or synthetic tests, not application runtime/legal/Store proof'}
    code = 1
    try:
        require_platform()
        report['controls_before'] = control_hashes()
        with base_packages(tmp, report):
            code = execute_fixed(mode, arguments)
            report['inner_exit_code'] = code
            require(type(code) is int and code == 0, 'Fixed consumer/control execution failed')
            coupled_result(mode, destination)
        report['controls_after'] = control_hashes()
        require(report['controls_before'] == report['controls_after'] and report.get('base_package_cleanup') is True,
                'Control integrity or base cleanup incomplete')
        report['status'] = 'PASS_OWNED_SCHEMA_PREREQUISITES'
    except BaseException as error:
        report['error_type'] = type(error).__name__
        code = 1
    finally:
        report['finished_at'] = datetime.now(timezone.utc).isoformat()
        with report_path.open('x') as stream:
            json.dump(report, stream, indent=2)
            stream.write('\n')
        print(json.dumps({key: report[key] for key in ('status', 'mode', 'error_type', 'inner_exit_code', 'base_package_cleanup')
                          if key in report}), flush=True)
    return code


if __name__ == '__main__':
    def interrupted(_signum, _frame):
        raise KeyboardInterrupt('Owned schema prerequisite execution interrupted')
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    raise SystemExit(main())
