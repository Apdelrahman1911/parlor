#!/usr/bin/env python3
"""Run the checked-in disposable Android harness in one isolated audit lane.

Does not read real signing material, use a user AVD, or change application code.
The checked-in harness creates its own short-lived synthetic signing key.
"""
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import signal
import socket
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

from run_gradle_cycle import ROOT, OUT, identity, owned_outputs, collect_reports


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def main():
    name = 'android-managed-01'
    dest = OUT / 'evidence' / name
    if dest.exists():
        raise SystemExit('Refusing to overwrite an execution receipt')
    sdk = Path('/Users/abdelrahman/Library/Android/sdk')
    script = ROOT / 'scripts/android/run_release_managed_device_smoke.sh'
    with (OUT / 'build-lane.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        outputs = owned_outputs()
        if any(p.exists() for p in outputs):
            raise SystemExit('Pre-existing build outputs: ownership review required')
        if not (sdk / 'emulator/emulator').is_file():
            raise SystemExit('Required emulator absent; do not install implicitly')
        if not (sdk / 'system-images/android-35/google_apis/arm64-v8a/package.xml').is_file():
            raise SystemExit('Required existing ARM64 image absent')
        before_ps = subprocess.check_output(['ps', '-axo', 'pid=,pgid=,command='], text=True)
        if re.search(r'org\.gradle\.launcher\.daemon\.bootstrap\.GradleDaemon 8\.13(?:\s|$)', before_ps):
            raise SystemExit('Unowned Gradle8.13 process present; cannot safely --stop')
        dest.mkdir(parents=True)
        temporary = Path(tempfile.mkdtemp(prefix='parlor-audit-android-managed-01-'))
        for child in ['android-user', 'avd', 'tmp']:
            (temporary / child).mkdir(mode=0o700)
        with socket.socket() as selected:
            selected.bind(('127.0.0.1', 0))
            adb_port = selected.getsockname()[1]
        env = {key: os.environ[key] for key in ['HOME', 'USER', 'LOGNAME', 'PATH', 'LANG', 'LC_ALL', 'SHELL'] if key in os.environ}
        java = subprocess.check_output(['/usr/libexec/java_home', '-v', '21'], text=True).strip()
        env.update(JAVA_HOME=java, PATH=java + '/bin:' + env.get('PATH', ''),
                   ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk),
                   ANDROID_USER_HOME=str(temporary / 'android-user'),
                   ANDROID_AVD_HOME=str(temporary / 'avd'),
                   ANDROID_ADB_SERVER_PORT=str(adb_port),
                   ADB_SERVER_SOCKET=f'tcp:localhost:{adb_port}',
                   TMPDIR=str(temporary / 'tmp') + '/', PYTHONDONTWRITEBYTECODE='1')
        opts = ['-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8 -XX:+UseParallelGC',
                '-Dorg.gradle.parallel=false', '-Dorg.gradle.caching=false',
                '-Dorg.gradle.configuration-cache=false',
                '-Dorg.gradle.project.kotlin.compiler.execution.strategy=in-process']
        opts += ['-Dorg.gradle.project.parlor.android.signing.' + key + '=' for key in
                 ['storeFile', 'storePassword', 'keyAlias', 'keyPassword']]
        env['GRADLE_OPTS'] = shlex.join(opts)
        receipt = dict(cycle=name, started_at=now(), source_before=identity(),
                       command=['bash', str(script.relative_to(ROOT))],
                       script_sha256=hashlib.sha256(script.read_bytes()).hexdigest(),
                       execution_kind='gradle-via-checked-in-disposable-signing-harness',
                       owned_temporary_directory=str(temporary), outputs_before=[],
                       adb_port=adb_port, signing='Synthetic two-day local test key only; no Store credentials or publication.',
                       platform_qualification='Local Darwin/ARM64 API35 image revision9, not qualified Linux/x86_64 CI.',
                       environment_policy='Safe whitelist; isolated AVD/Android-user/TMP/ADB port; JDK21;3g;no parallel/cache; in-process Kotlin; production signing blank.',
                       status='RUNNING')
        (dest / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
        child = None
        errors = []
        try:
            with (dest / 'gradle.log').open('w') as log:
                child = subprocess.Popen(receipt['command'], cwd=ROOT, env=env,
                                         stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
                receipt['owned_process_group'] = child.pid
                try:
                    receipt['exit_code'] = child.wait(timeout=1500)
                except (subprocess.TimeoutExpired, KeyboardInterrupt) as error:
                    receipt['interruption'] = type(error).__name__
                    os.killpg(child.pid, signal.SIGTERM)
                    try:
                        receipt['exit_code'] = child.wait(timeout=45)
                    except subprocess.TimeoutExpired:
                        os.killpg(child.pid, signal.SIGKILL)
                        receipt['exit_code'] = child.wait()
            receipt['finished_at'] = now()
        except BaseException as error:
            receipt['error'] = type(error).__name__
            receipt['finished_at'] = now()
            if child is not None and child.poll() is None:
                os.killpg(child.pid, signal.SIGTERM)
                try:
                    child.wait(timeout=45)
                except subprocess.TimeoutExpired:
                    os.killpg(child.pid, signal.SIGKILL)
                    child.wait()
        finally:
            receipt.setdefault('finished_at', now())
            def finalize_command(command, logname, timeout):
                try:
                    with (dest / logname).open('w') as log:
                        return subprocess.run(command, cwd=ROOT, env=env, stdout=log,
                                              stderr=subprocess.STDOUT, timeout=timeout).returncode
                except Exception as error:
                    errors.append(logname + ': ' + type(error).__name__)
                    return None

            # Checked-in script also stops Gradle. Repeat immediately here so a
            # failed script/trap cannot evade the task-wide finalizer.
            receipt['stop_exit_code'] = finalize_command(['./gradlew', '--stop'], 'stop.log', 90)
            receipt['stopped_at'] = now()
            try:
                collect_reports(dest, outputs)
                instrumented = []
                for output in outputs:
                    for p in output.glob('outputs/androidTest-results/**/*.xml'):
                        target = dest / 'reports' / p.relative_to(ROOT)
                        target.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copyfile(p, target)
                        xml = ET.parse(target).getroot()
                        instrumented.append(dict(file=str(p.relative_to(ROOT)), suite=xml.get('name'),
                                                 tests=xml.get('tests'), failures=xml.get('failures'),
                                                 errors=xml.get('errors'), skipped=xml.get('skipped'),
                                                 cases=[dict(name=x.get('name'), classname=x.get('classname'),
                                                             failed=x.find('failure') is not None or x.find('error') is not None,
                                                             skipped=x.find('skipped') is not None) for x in xml.findall('testcase')]))
                (dest / 'instrumented-test-receipts.json').write_text(json.dumps(instrumented, indent=2) + '\n')
                # No APK or disposable signing key retained. Hash only APKs.
                apk_receipts = []
                for p in (ROOT / 'composeApp/build/outputs').glob('**/*.apk'):
                    apk_receipts.append(dict(path=str(p.relative_to(ROOT)), bytes=p.stat().st_size,
                                             sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
                (dest / 'artifact-receipts.json').write_text(json.dumps(apk_receipts, indent=2) + '\n')
            except Exception as error:
                receipt['report_collection_error'] = type(error).__name__ + ': ' + str(error)
            # Only the dedicated server port and our own child process group.
            receipt['adb_stop_exit_code'] = finalize_command(
                [str(sdk / 'platform-tools/adb'), '-P', str(adb_port), 'kill-server'], 'adb-stop.log', 30)
            if child is not None:
                try:
                    os.killpg(child.pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass
                except OSError as error:
                    errors.append('Owned process group termination: ' + str(error))
            # Give task-owned native workers a bounded opportunity to exit
            # before removing their private temporary filesystem.
            time.sleep(1)
            receipt['avd_entries_created'] = sorted(p.name for p in (temporary / 'avd').iterdir())
            removed = []
            for p in outputs:
                try:
                    if p.is_symlink():
                        raise RuntimeError('Refusing output symlink: ' + str(p))
                    if p.exists():
                        shutil.rmtree(p)
                        removed.append(str(p.relative_to(ROOT)))
                except Exception as error:
                    errors.append(str(error))
            # Ephemeral signing material stays inside this task-owned directory.
            # Do not read or preserve those bytes.
            try:
                shutil.rmtree(temporary)
            except Exception as error:
                errors.append(str(error))
            receipt.update(removed_outputs=removed, cleanup_errors=errors,
                           remaining_outputs=[str(p.relative_to(ROOT)) for p in outputs if p.exists()],
                           temporary_directory_removed=not temporary.exists(),
                           cleanup_method='Precise newly created module/build-logic output directories and isolated AVD/user/TMP; no global cache or source removal.',
                           cleanup_completed_at=now(), source_after=identity())
            after_ps = subprocess.check_output(['ps', '-axo', 'pid=,pgid=,command='], text=True)
            owned_remaining = [line.strip() for line in after_ps.splitlines() if str(temporary) in line or
                               (child is not None and len(line.split(None, 2)) >= 2 and line.split(None, 2)[1] == str(child.pid))]
            receipt['owned_processes_remaining'] = owned_remaining
            receipt['status'] = 'PASS' if receipt.get('exit_code') == 0 else 'FAIL'
            log_path = dest / 'gradle.log'
            log_path.write_text(log_path.read_text().replace('parlor-managed-device-only', '[EPHEMERAL_TEST_PASSWORD]'))
            (dest / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
            print(json.dumps(receipt, indent=2), flush=True)
        return 0 if receipt['status'] == 'PASS' and not errors and not owned_remaining else 1


if __name__ == '__main__':
    signal.signal(signal.SIGTERM, lambda _signum, _frame: (_ for _ in ()).throw(KeyboardInterrupt()))
    raise SystemExit(main())
