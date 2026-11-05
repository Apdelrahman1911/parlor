"""Read-only identity inventory for the exact cancellation-surviving SwiftBuild helper."""
import argparse
import json
import os
from pathlib import Path
import platform
import re
import subprocess

from xcode_worker_inventory import inspect_component

ROOT = Path(__file__).resolve().parents[3]
BRANCH = 'refs/heads/diagnostic/swiftbuild-worker-inventory-2026-11-05'
DEVELOPER = '/Applications/Xcode_26.3.app/Contents/Developer'
RELATIVE = ('Contents/SharedFrameworks/SwiftBuild.framework/Versions/A/PlugIns/'
            'SWBBuildService.bundle/Contents/MacOS/SWBBuildService')
XCODE = 'Xcode 26.3\nBuild version 17C529\n'


def validate_environment(env, system, machine):
    expected = {'GITHUB_ACTIONS': 'true', 'RUNNER_ENVIRONMENT': 'github-hosted',
                'GITHUB_REPOSITORY': 'Apdelrahman1911/parlor', 'GITHUB_REF': BRANCH,
                'GITHUB_EVENT_NAME': 'push', 'GITHUB_JOB': 'inventory', 'DEVELOPER_DIR': DEVELOPER}
    architecture = {'arm64': 'ARM64', 'x86_64': 'X64'}.get(machine)
    if system != 'Darwin' or architecture is None or env.get('RUNNER_ARCH') != architecture or \
            any(env.get(k) != v for k, v in expected.items()):
        raise RuntimeError('Exact isolated standard-hosted inventory context required')
    for key in ('GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT'):
        if not re.fullmatch(r'[1-9][0-9]{0,19}', env.get(key, '')):
            raise RuntimeError('Exact run/attempt binding required')
    sha = env.get('GITHUB_SHA', '')
    if not re.fullmatch(r'[0-9a-f]{40}', sha) or env.get('GITHUB_WORKFLOW_SHA') != sha:
        raise RuntimeError('Executing workflow and source must share the exact SHA')
    return architecture


def source_binding(root, env, run=subprocess.check_output):
    def git(*args):
        return run(['git', *args], cwd=root, timeout=30).decode().strip()
    if git('status', '--porcelain') or git('rev-parse', 'HEAD') != env['GITHUB_SHA']:
        raise RuntimeError('Inventory source must remain clean and exactly frozen')
    return {'head': env['GITHUB_SHA'], 'tree': git('rev-parse', 'HEAD^{tree}')}


def collect(application, inspect=inspect_component):
    if application.is_symlink() or application.resolve(strict=True) != application:
        raise RuntimeError('Canonical pinned Xcode application required')
    candidate = application / RELATIVE
    # Absence is evidence, not an implicit alternative path or cleanup grant.
    if not candidate.exists() and not candidate.is_symlink():
        return {'result': 'COMPONENT_ABSENT', 'relative_executable': RELATIVE,
                'cleanup_authorized': False, 'components': []}
    row = inspect(application, candidate)
    if row['relative_executable'] != RELATIVE or row['cleanup_authorized'] is not False:
        raise RuntimeError('Exact inspected component identity required')
    signed = row['signature_verify_exit'] == row['signature_display_exit'] == 0
    return {'result': 'SIGNED_COMPONENT_OBSERVED' if signed else 'SIGNATURE_REJECTED',
            'relative_executable': RELATIVE, 'cleanup_authorized': False, 'components': [row]}


def main():
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    architecture = validate_environment(os.environ, platform.system(), platform.machine())
    before = source_binding(ROOT, os.environ)
    policy = json.loads((ROOT / 'config/release-policy.json').read_text())
    if policy['toolchains']['apple']['developer_dir'] != DEVELOPER:
        raise RuntimeError('Pinned Apple policy changed')
    xcode = subprocess.check_output(['/usr/bin/xcodebuild', '-version'], timeout=30).decode()
    if xcode != XCODE:
        raise RuntimeError('Exact reviewed Xcode required')
    temporary = Path(os.environ['RUNNER_TEMP']).resolve(strict=True)
    output = args.output.absolute()
    if output.parent != temporary or output.exists() or output.is_symlink():
        raise RuntimeError('Fresh direct runner-temporary output required')
    result = collect(Path(DEVELOPER).parent.parent)
    after = source_binding(ROOT, os.environ)
    if after != before:
        raise RuntimeError('Source changed during native inspection')
    receipt = dict(schema=1, source=before, run=os.environ['GITHUB_RUN_ID'],
                   attempt=os.environ['GITHUB_RUN_ATTEMPT'], architecture=architecture,
                   xcode=xcode, **result, native_journeys_executed=False,
                   production_qualified=False, process_signals_authorized=False,
                   scope='Installed exact SwiftBuild helper bytes/signature only; no process or input correction')
    output.mkdir(mode=0o700)
    with (output / 'swiftbuild-worker-inventory.json').open('x') as stream:
        json.dump(receipt, stream, indent=2)
    print(json.dumps(receipt, indent=2))
    return 1 if result['result'] == 'SIGNATURE_REJECTED' else 0


if __name__ == '__main__':
    raise SystemExit(main())
