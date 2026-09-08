#!/usr/bin/env python3
"""Audit-only synthetic native witnesses. No app, user defaults, or signing inputs."""
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import uuid

A = Path(__file__).resolve().parents[1]
ROOT = A.parents[1]


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def run(command, log, env, cwd, timeout=180):
    with log.open('w') as f:
        result = subprocess.run(command, cwd=cwd, env=env, stdout=f,
                                stderr=subprocess.STDOUT, timeout=timeout)
    return result.returncode


def preference_witness(work, evidence, env, receipt):
    suite = 'com.parlor.audit.DSC01.' + str(uuid.uuid4())
    original = A / 'reproducers/DSC01PreferenceOwnership.swift.txt'
    source = work / 'PreferenceOwnership.swift'
    shutil.copyfile(original, source)
    receipt['synthetic_suite'] = suite
    receipt['source_sha256'] = hashlib.sha256(source.read_bytes()).hexdigest()
    try:
        for phase in ['seed', 'restore']:
            command = ['xcrun', 'swift', '-module-cache-path', str(work / 'module-cache'),
                       str(source), phase, suite]
            code = run(command, evidence / (phase + '.log'), env, work)
            receipt[phase] = {'command': command, 'exit_code': code}
            if code:
                break
    finally:
        # Operate only on the exact fresh UUID suite, even if Swift traps or times out.
        result = subprocess.run(['/usr/bin/defaults', 'delete', suite], env=env,
                                text=True, capture_output=True)
        (evidence / 'suite-cleanup.log').write_text(result.stdout + result.stderr)
        check = subprocess.run(['/usr/bin/defaults', 'read', suite], env=env,
                               text=True, capture_output=True)
        receipt['suite_absent_after'] = check.returncode != 0 and 'does not exist' in check.stderr
        receipt['suite_cleanup_exit_code'] = result.returncode
        if not receipt['suite_absent_after']:
            raise RuntimeError('Synthetic defaults suite cleanup could not be verified')


def xcode_phase_witness(work, evidence, env, receipt):
    original = ROOT / 'iosApp/iosApp.xcodeproj/project.pbxproj'
    parsed = json.loads(subprocess.check_output(
        ['plutil', '-convert', 'json', '-o', '-', str(original)], text=True))
    phase = next(v for v in parsed['objects'].values()
                 if v.get('isa') == 'PBXShellScriptBuildPhase' and v.get('name') == 'Compile Kotlin Framework')
    script = phase['shellScript']
    assert phase['shellPath'] == '/bin/sh'
    receipt['project_sha256'] = hashlib.sha256(original.read_bytes()).hexdigest()
    receipt['script_sha256'] = hashlib.sha256(script.encode()).hexdigest()
    (evidence / 'unchanged-inline-phase.sh').write_text(script)

    # A fake failing wrapper models a build-tool failure; it never invokes Gradle.
    wrapper = work / 'gradlew'
    wrapper.write_text('#!/bin/sh\necho "AUDIT synthetic Gradle failure (exit42)"\nexit 42\n')
    wrapper.chmod(0o755)
    normalizer = work / 'scripts/release/normalize_embedded_apple_framework.sh'
    normalizer.parent.mkdir(parents=True)
    shutil.copy2(ROOT / 'scripts/release/normalize_embedded_apple_framework.sh', normalizer)
    framework = work / 'built/Frameworks/ComposeApp.framework'
    framework.mkdir(parents=True)
    (framework / 'ComposeApp').write_text('Synthetic pre-existing framework placeholder; not executable code.\n')
    project = work / 'iosApp/AuditProbe.xcodeproj'
    project.mkdir(parents=True)
    project_text = '''// !$*UTF8*$!
{ archiveVersion = 1; classes = {}; objectVersion = 56; objects = {
A00000000000000000000001 = { isa = PBXProject; attributes = {}; buildConfigurationList = A00000000000000000000004; compatibilityVersion = "Xcode 14.0"; developmentRegion = en; hasScannedForEncodings = 0; knownRegions = (en); mainGroup = A00000000000000000000002; productRefGroup = A00000000000000000000002; projectDirPath = ""; projectRoot = ""; targets = (A00000000000000000000003); };
A00000000000000000000002 = { isa = PBXGroup; children = (); sourceTree = "<group>"; };
A00000000000000000000003 = { isa = PBXAggregateTarget; buildConfigurationList = A00000000000000000000004; buildPhases = (A00000000000000000000006); buildRules = (); dependencies = (); name = AuditProbe; productName = AuditProbe; };
A00000000000000000000004 = { isa = XCConfigurationList; buildConfigurations = (A00000000000000000000005); defaultConfigurationIsVisible = 0; defaultConfigurationName = Debug; };
A00000000000000000000005 = { isa = XCBuildConfiguration; buildSettings = { CODE_SIGNING_ALLOWED = NO; SDKROOT = macosx; }; name = Debug; };
A00000000000000000000006 = { isa = PBXShellScriptBuildPhase; alwaysOutOfDate = 1; buildActionMask = 2147483647; files = (); inputPaths = (); name = "Compile Kotlin Framework"; outputPaths = (); runOnlyForDeploymentPostprocessing = 0; shellPath = /bin/sh; shellScript = SCRIPT_PLACEHOLDER; };
}; rootObject = A00000000000000000000001; }
'''.replace('SCRIPT_PLACEHOLDER', json.dumps(script))
    (project / 'project.pbxproj').write_text(project_text)
    schemes = project / 'xcshareddata/xcschemes'
    schemes.mkdir(parents=True)
    (schemes / 'AuditProbe.xcscheme').write_text('''<?xml version="1.0" encoding="UTF-8"?>
<Scheme version="1.3"><BuildAction parallelizeBuildables="NO" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="NO"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="A00000000000000000000003" BuildableName="AuditProbe" BlueprintName="AuditProbe" ReferencedContainer="container:AuditProbe.xcodeproj"/></BuildActionEntry></BuildActionEntries></BuildAction></Scheme>
''')
    env['OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED'] = 'NO'
    command = ['xcodebuild', '-project', str(project), '-scheme', 'AuditProbe',
               '-configuration', 'Debug', '-derivedDataPath', str(work / 'DerivedData'),
               '-sdk', 'macosx', 'CODE_SIGNING_ALLOWED=NO',
               'TARGET_BUILD_DIR=' + str(work / 'built'),
               'FRAMEWORKS_FOLDER_PATH=Frameworks', 'build']
    receipt['command'] = command
    receipt['exit_code'] = run(command, evidence / 'xcodebuild.log', env, work)
    for generated in (work / 'DerivedData').glob('**/Script-*.sh'):
        shutil.copyfile(generated, evidence / ('generated-' + generated.name))
    receipt['stale_placeholder_survived'] = (framework / 'ComposeApp').is_file()
    # Expected product requirement is failure propagation, not a successful fixture build.
    log = (evidence / 'xcodebuild.log').read_text()
    receipt['wrapper_failure_observed'] = 'AUDIT synthetic Gradle failure (exit42)' in log
    receipt['failure_propagation_requirement_passed'] = receipt['exit_code'] != 0


def main():
    mode = sys.argv[1]
    if mode not in ['preference', 'xcode-phase']:
        raise SystemExit('Expected preference or xcode-phase')
    evidence = A / 'evidence' / ('native-' + mode + '-01')
    evidence.mkdir(exist_ok=False)
    env = os.environ.copy()
    env['PYTHONDONTWRITEBYTECODE'] = '1'
    env['JAVA_HOME'] = subprocess.check_output(['/usr/libexec/java_home', '-v', '21'], text=True).strip()
    for key in list(env):
        if key.startswith(('PARLOR_ANDROID_', 'MOBILE_RELEASE_')):
            env.pop(key)
    receipt = {'started_at': now(), 'mode': mode,
               'commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
               'fixture_only': True, 'signing_or_store_operations': False}
    with (A / 'build-lane.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        work = Path(tempfile.mkdtemp(prefix='parlor-audit-' + mode + '-'))
        try:
            receipt['xcode_version'] = subprocess.check_output(['xcodebuild', '-version'], text=True).strip()
            {'preference': preference_witness, 'xcode-phase': xcode_phase_witness}[mode](work, evidence, env, receipt)
        except BaseException as error:
            receipt['error'] = repr(error)
            raise
        finally:
            receipt['finished_at'] = now()
            receipt['stop_exit_code'] = run(['./gradlew', '--stop'], evidence / 'stop.log', env, ROOT)
            shutil.rmtree(work)
            receipt['task_temporary_directory_removed'] = not work.exists()
            receipt['cleanup_completed_at'] = now()
            receipt['tracked_status_after'] = subprocess.check_output(
                ['git', 'status', '--porcelain=v1', '--untracked-files=no'], cwd=ROOT, text=True).strip()
            (evidence / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
            print(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    main()
