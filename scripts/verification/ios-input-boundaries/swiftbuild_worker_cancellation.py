"""Isolated public-xcodebuild cancellation control; never executes Parlor or a Simulator."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.ci import apple_verification_hygiene as apple
from scripts.ci import verification_hygiene as common
from scripts.ci.darwin_worker_identity import DarwinWorkerBackend

BRANCH = 'refs/heads/verification/swiftbuild-holding-boundary-2026-11-06'
DEVELOPER = '/Applications/Xcode_26.3.app/Contents/Developer'
SERVICE = ('Contents/SharedFrameworks/SwiftBuild.framework/Versions/A/PlugIns/'
           'SWBBuildService.bundle/Contents/MacOS/SWBBuildService')
IMAGE = '044787e793e5d2db4dff1cc99eed8e98a2d36e10dc6a1d3abee9aebd95f5f7f9'
PROJECT = r'''// !$*UTF8*$!
{
 archiveVersion = 1; classes = {}; objectVersion = 56;
 objects = {
  000000000000000000000001 = {isa = PBXProject; attributes = {}; buildConfigurationList = 000000000000000000000006; compatibilityVersion = "Xcode 14.0"; developmentRegion = en; hasScannedForEncodings = 0; mainGroup = 000000000000000000000002; productRefGroup = 000000000000000000000003; projectDirPath = ""; projectRoot = ""; targets = (000000000000000000000004,);};
  000000000000000000000002 = {isa = PBXGroup; children = (000000000000000000000003,); sourceTree = "<group>";};
  000000000000000000000003 = {isa = PBXGroup; children = (); name = Products; sourceTree = "<group>";};
  000000000000000000000004 = {isa = PBXAggregateTarget; buildConfigurationList = 000000000000000000000007; buildPhases = (000000000000000000000005,); dependencies = (); name = OwnedCancellationProbe; productName = OwnedCancellationProbe;};
  000000000000000000000005 = {isa = PBXShellScriptBuildPhase; alwaysOutOfDate = 1; buildActionMask = 2147483647; files = (); inputPaths = (); outputPaths = ("$(OBJROOT)/parlor-worker-ready",); runOnlyForDeploymentPostprocessing = 0; shellPath = /bin/sh; shellScript = "/usr/bin/touch \"${OBJROOT}/parlor-worker-ready\"; /bin/sleep 30";};
  000000000000000000000006 = {isa = XCConfigurationList; buildConfigurations = (000000000000000000000008,); defaultConfigurationIsVisible = 0; defaultConfigurationName = Debug;};
  000000000000000000000007 = {isa = XCConfigurationList; buildConfigurations = (000000000000000000000009,); defaultConfigurationIsVisible = 0; defaultConfigurationName = Debug;};
  000000000000000000000008 = {isa = XCBuildConfiguration; buildSettings = {}; name = Debug;};
  000000000000000000000009 = {isa = XCBuildConfiguration; buildSettings = {}; name = Debug;};
 }; rootObject = 000000000000000000000001;
}
'''


def validate_environment(env, system, machine):
    expected = dict(GITHUB_ACTIONS='true', RUNNER_ENVIRONMENT='github-hosted',
                    GITHUB_REPOSITORY='Apdelrahman1911/parlor', GITHUB_REF=BRANCH,
                    GITHUB_EVENT_NAME='push', GITHUB_JOB='ios-release-simulator', DEVELOPER_DIR=DEVELOPER)
    architecture = {'arm64': 'ARM64', 'x86_64': 'X64'}.get(machine)
    if system != 'Darwin' or architecture is None or env.get('RUNNER_ARCH') != architecture or \
            any(env.get(k) != v for k, v in expected.items()):
        raise RuntimeError('Exact standard-hosted isolated cancellation context required')
    for key in ('GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT'):
        if not re.fullmatch(r'[1-9][0-9]{0,19}', env.get(key, '')):
            raise RuntimeError('Exact run/attempt required')
    sha = env.get('GITHUB_SHA', '')
    if not re.fullmatch(r'[0-9a-f]{40}', sha) or env.get('GITHUB_WORKFLOW_SHA') != sha:
        raise RuntimeError('Workflow and source must share an exact SHA')
    return architecture


def command(fixture):
    return ['/usr/bin/xcodebuild', '-quiet', '-project', str(fixture / 'Probe.xcodeproj'),
            '-target', 'OwnedCancellationProbe', '-configuration', 'Debug', '-sdk', 'macosx',
            'OBJROOT=' + str(fixture / 'objects'), 'SYMROOT=' + str(fixture / 'products'),
            'CODE_SIGNING_ALLOWED=NO', 'build']


def attest_service(claim, backend, tracking):
    baseline = {(v[0], v[1], tuple(v[2])) for v in claim['baseline']}
    expected = str(Path(claim['application']) / SERVICE)
    for value in backend.snapshot(Path(claim['application']), baseline):
        if value.lifetime in baseline or value.command != expected or value.uid != claim['uid'] or \
                value.started[0] * 1000000 + value.started[1] < claim['not_before_us']:
            continue
        marker = backend.tracking_id(value)
        if marker == tracking and backend.read(value.pid) == value:
            return value
    return None


# The ten-second service-acquisition budget starts at the build phase marker,
# not at cold xcodebuild launch. Prior Intel evidence took 13.207428s merely to
# spawn the service, before the holding phase existed. Startup remains bounded
# independently by the fixture's existing finite 30s phase duration; no Parlor
# input, production-finalizer, or workflow timeout is changed.
STARTUP_SECONDS = 30.0
ACQUISITION_SECONDS = 10.0
HOLD_SECONDS = 30.0


def acquire_at_holding_boundary(child, ready, log, observe, *, clock=time.monotonic, sleep=time.sleep):
    started = clock()
    while True:
        check_running(child, log)
        now = clock()
        if now - started >= STARTUP_SECONDS:
            raise RuntimeError('Fixture startup did not reach its explicit holding-phase marker')
        if ready.is_file():
            holding = now
            break
        sleep(0.1)
    # Readiness is not service ownership. Exact lifetime, UID, tracking marker,
    # executable and generation re-attestation remain separately mandatory.
    while True:
        check_running(child, log)
        if clock() - holding >= ACQUISITION_SECONDS:
            raise RuntimeError('No fresh exact task-owned SwiftBuild service at the holding boundary')
        target = observe()
        if target is not None:
            elapsed = clock() - holding
            if elapsed >= ACQUISITION_SECONDS:
                raise RuntimeError('Exact service observation exceeded its acquisition budget')
            return target, dict(startup_seconds=holding - started, acquisition_seconds=elapsed,
                                startup_limit_seconds=STARTUP_SECONDS,
                                acquisition_limit_seconds=ACQUISITION_SECONDS)
        sleep(0.1)


def check_running(child, log):
    if log.stat().st_size > 1024 * 1024:
        raise RuntimeError('Fixture build output exceeded its bound')
    if child.poll() is not None:
        raise RuntimeError('Fixture ended before controlled cancellation')


def retire_fixture(output):
    # Only this fresh synthetic fixture; never application data or receipts.
    for path in (output / 'fixture', output / 'private-build-output.log'):
        if path.is_symlink() or path.resolve(strict=True) != path:
            raise RuntimeError('Redirected fixture data cannot be retired')
    shutil.rmtree(output / 'fixture')
    (output / 'private-build-output.log').unlink()
    if (output / 'fixture').exists() or (output / 'private-build-output.log').exists():
        raise RuntimeError('Synthetic fixture data survived retirement')
    return dict(result='PASS', fixture_absent=True, private_log_absent=True,
                scope='Only fresh synthetic fixture and raw public-build log; all receipts retained')


def main():
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    architecture = validate_environment(os.environ, platform.system(), platform.machine())
    if common.git(ROOT, 'status', '--porcelain').strip() or common.git(ROOT, 'rev-parse', 'HEAD').strip() != os.environ['GITHUB_SHA']:
        raise RuntimeError('Clean frozen cancellation source required')
    if subprocess.check_output(['/usr/bin/xcodebuild', '-version'], timeout=30).decode() != 'Xcode 26.3\nBuild version 17C529\n':
        raise RuntimeError('Pinned Xcode required')
    temporary = Path(os.environ['RUNNER_TEMP']).resolve(strict=True)
    output = args.output.absolute()
    if output.parent != temporary or output.exists() or output.is_symlink():
        raise RuntimeError('Fresh direct runner-temporary output required')
    config = apple.configuration(ROOT)
    service = Path(config['application']) / SERVICE
    if service.resolve(strict=True) != service or service.stat().st_size != 119520 or hashlib.sha256(service.read_bytes()).hexdigest() != IMAGE:
        raise RuntimeError('Exact previously reviewed Apple-signed service bytes required')
    # Initialize the wrapper before the finite holding phase, without a build.
    if common.stop_gradle(ROOT)['exit_code'] != 0:
        raise RuntimeError('Cannot initialize the repository Gradle wrapper')
    output.mkdir(mode=0o700)
    fixture = output / 'fixture'; fixture.mkdir(mode=0o700)
    project = fixture / 'Probe.xcodeproj'; project.mkdir()
    (project / 'project.pbxproj').write_text(PROJECT)
    (fixture / 'objects').mkdir()
    subprocess.run(['/usr/bin/plutil', '-lint', str(project / 'project.pbxproj')], check=True, timeout=30,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    task = {k: os.environ[k] for k in ('GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT', 'GITHUB_JOB')}
    prefix = output / 'apple-wrapper'
    backend = DarwinWorkerBackend()
    claim = apple.prepare(ROOT, prefix, task, 'apple-wrapper', backend, config)
    receipt = dict(schema=1, source=claim['source'], task=task, architecture=architecture,
                   project_sha256=hashlib.sha256(PROJECT.encode()).hexdigest(), component_sha256=IMAGE,
                   app_builds=0, simulators=0, input_events=0, fixture_kind='public-xcodebuild-aggregate-holding-phase',
                   production_qualified=False, errors=[])
    child = None
    target = None
    try:
        with (output / 'private-build-output.log').open('xb') as log:
            child = subprocess.Popen(command(fixture), cwd=fixture, stdout=log, stderr=subprocess.STDOUT)
            target, receipt['boundary_timing'] = acquire_at_holding_boundary(
                child, fixture / 'objects/parlor-worker-ready', output / 'private-build-output.log',
                lambda: attest_service(claim, backend, os.environ['RUNNER_TRACKING_ID']))
            receipt['observed_service'] = dict(pid=target.pid, started=target.started, executable=SERVICE)
            receipt['holding_observed_epoch'] = time.time()
            # Same starting boundary as cancellation-surviving xcodebuild/service;
            # the reviewed finalizer itself cancels these exact owned lifetimes.
            receipt['cleanup'] = apple.finish(ROOT, prefix, task, 'apple-wrapper', 'success', 'cancelled')
            receipt['client_exit'] = child.wait(timeout=10)
            if receipt['cleanup']['result'] != 'PASS' or receipt['client_exit'] == 0:
                raise RuntimeError('Controlled cancellation or native finalizer failed')
            events = receipt['cleanup']['workers_before_simulator']['events']
            if not any(e['pid'] == target.pid and tuple(e['started']) == target.started and
                       e['executable'] == SERVICE and e['delivered'] is True for e in events):
                raise RuntimeError('Actual exact-service native signal was not established')
            if backend.read(target.pid) is not None:
                raise RuntimeError('Signalled service is not independently absent')
            receipt['service_absent'] = True
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        receipt['errors'].append(dict(error=type(error).__name__, message=str(error)))
    finally:
        if 'cleanup' not in receipt:
            receipt['cleanup'] = apple.finish(ROOT, prefix, task, 'apple-wrapper', 'success', 'failure')
        if child is not None and child.poll() is not None:
            receipt['client_exit'] = child.wait()
        receipt['source_unchanged'] = common.git(ROOT, 'status', '--porcelain').strip() == '' and apple.source(ROOT) == claim['source']
        receipt['result'] = 'PASS' if not receipt['errors'] and receipt['source_unchanged'] else 'FAIL'
        common.write_new(output / 'swiftbuild-cancellation-result.json', receipt)
        print(json.dumps(receipt, indent=2))
    return 0 if receipt['result'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
