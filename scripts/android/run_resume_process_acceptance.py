#!/usr/bin/env python3
"""Force-stop/relaunch real Debug Home between independently selected native phases.

Only on a disposable hosted Linux emulator, inside the existing child-subreaper
with --isolated-avd. No local device, user AVD, Store key, alternate dependency,
state injection route, process-name cleanup, retries of actions, or test replay.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import stat
import subprocess
import time
from uuid import uuid4
import xml.etree.ElementTree as ET

from scripts.android import resume_process_contract as contract
from scripts.release.android_test_smoke import unused_ports
from scripts.release.github_distribution import run, run_logged

ROOT = Path(__file__).resolve().parents[2]
ADB_PORT, EMULATOR_PORT = 5039, 5662
SERIAL = f"emulator-{EMULATOR_PORT}"
PHASE_SECONDS = 90
EMULATOR_LOG_LIMIT = 16 * 1024 * 1024
ANDROID = "{http://schemas.android.com/apk/res/android}"
SOURCE_PATHS = (
    "composeApp/build.gradle.kts",
    "shared/design-system/src/androidMain/kotlin/com/parlor/designsystem/localization/LocalAppLocale.android.kt",
    "shared/design-system/src/androidMain/kotlin/com/parlor/designsystem/localization/LocaleAwareApplication.kt",
    "shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/localization/LocaleDefaults.kt",
    "shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/localization/ScopedLocaleOverrides.kt",
    "composeApp/src/androidMain/kotlin/com/parlor/app/ParlorApplication.kt",
    "scripts/android/run_resume_process_acceptance.py",
    "scripts/android/resume_process_contract.py",
    "composeApp/src/androidNavigationRuntimeTest/kotlin/com/parlor/app/storage/AndroidResumeProcessPhases.kt",
    "composeApp/src/androidNavigationRuntimeTest/kotlin/com/parlor/app/storage/AndroidLegalResumeFixtures.kt",
    "composeApp/src/androidNavigationRuntimeTest/kotlin/com/parlor/app/storage/AndroidPublicRecoveryUi.kt",
    "composeApp/src/androidNavigationRuntimeTest/kotlin/com/parlor/app/storage/AndroidRecoveryFailureEvidence.kt",
    "composeApp/src/androidNavigationRuntimeTest/kotlin/com/parlor/app/storage/AndroidLocaleConfigurationRuntimeTest.kt",
    "composeApp/src/androidNavigationRuntimeTest/kotlin/com/parlor/app/storage/AndroidRecoveryHeadingRuntimeTest.kt",
    "composeApp/src/androidNavigationRuntimeTest/kotlin/com/parlor/app/storage/AndroidRecoveryRevealObservation.kt",
    "composeApp/src/androidNavigationRuntimeTest/kotlin/com/parlor/app/storage/AndroidRecoveryRevealRuntimeTest.kt",
    "composeApp/src/androidNavigationRuntimeTest/kotlin/com/parlor/app/storage/AndroidRecoveryClickOwner.kt",
    "composeApp/src/androidNavigationRuntimeTest/kotlin/com/parlor/app/storage/AndroidProcessCredentialFixture.kt",
    ".github/workflows/production-verification.yml",
    "scripts/release/workflow_contract.py",
    "config/release-policy.json",
)


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def inspect_manifests(app_xml: str, test_xml: str, application_id: str) -> str:
    contract.require(re.fullmatch(r"[a-z][a-z0-9_.]+\.debug", application_id) is not None,
                     "Process acceptance is Debug-only")
    app, test = ET.fromstring(app_xml), ET.fromstring(test_xml)
    contract.require(app.get("package") == application_id and test.get("package") == application_id + ".test",
                     "APK identities do not match the reviewed isolated Debug pair")
    apps = app.findall("application")
    contract.require(len(apps) == 1 and apps[0].get(ANDROID + "debuggable") == "true"
                     and apps[0].get(ANDROID + "testOnly", "false") == "false", "Target is not the normal Debug app")
    instrumentation = test.findall("instrumentation")
    contract.require(len(instrumentation) == 1
                     and instrumentation[0].get(ANDROID + "name") == "android.test.InstrumentationTestRunner"
                     and instrumentation[0].get(ANDROID + "targetPackage") == application_id,
                     "Test APK must use the existing runner targeting only this Debug app")
    return application_id + ".test/android.test.InstrumentationTestRunner"


def case_journey(fixture: contract.Fixture, application_id: str, component: str, command, phase) -> dict:
    """Each action executes once. Only process observation is separate from it."""
    prepared = retired_phase(fixture, "prepare", application_id, component, command, phase)
    launch = command("shell", "am", "start", "-W", "-n", application_id + "/com.parlor.app.MainActivity")
    contract.require(re.findall(r"(?m)^Status: (\w+)$", launch) == ["ok"] and "Error" not in launch,
                     "Actual app did not launch between instrumentation phases")
    pid = contract.observed_pid(command(*contract.pid_observation_command(application_id)))
    contract.require(pid is not None, "Launched app is absent after instrumentation retirement; never retry launch")
    command("shell", "am", "force-stop", application_id)
    absence = command(*contract.pid_observation_command(application_id))
    contract.require_absent(absence)
    verified = retired_phase(fixture, "verify", application_id, component, command, phase)
    return contract.complete_case(fixture, prepared, verified, pid, absence)


def retired_phase(fixture: contract.Fixture, method: str, application_id: str, component: str, command, phase) -> dict:
    # am instrument -w waits for its result watcher, not process death. Android
    # reports that result before its "finished inst" package force-stop. Do not
    # let that teardown race a single normal launch or the next instrumentation.
    # Retirement consumes the existing phase budget; no action/deadline retry.
    deadline = time.monotonic() + PHASE_SECONDS
    result = phase(fixture, method, component)
    retirement = await_instrumentation_exit(command, application_id, result["pid"], deadline)
    result = dict(result, instrumentation_retirement=retirement)
    contract.require_retired_phase(result)
    return result


def await_instrumentation_exit(command, application_id: str, phase_pid: int, deadline: float) -> dict:
    contract.live_pid(str(phase_pid))
    arguments = contract.pid_observation_command(application_id)
    started = time.monotonic()
    observations = 0
    while True:
        remaining = deadline - time.monotonic()
        contract.require(0 < remaining <= PHASE_SECONDS, "Instrumentation did not retire within its original phase budget")
        contract.require(observations < contract.MAX_RETIREMENT_OBSERVATIONS,
                         "Instrumentation retirement observation budget exhausted")
        # A transport failure propagates. Only this exact process's continued
        # existence permits another bounded observation, never another action.
        pid = contract.observed_pid(command(*arguments, timeout=min(10, remaining)))
        observations += 1
        observed_at = time.monotonic()
        contract.require(observed_at < deadline, "Instrumentation retirement exceeded its original phase budget")
        if pid is None:
            return {"result": "PASS", "pid": phase_pid, "observations": observations,
                    "elapsed_seconds": observed_at - started}
        contract.require(pid == phase_pid, "Unexpected replacement process during instrumentation retirement")
        time.sleep(min(0.25, deadline - observed_at))


@contextmanager
def owned_emulator(sdk: Path, avd: Path, output: Path, policy: dict):
    """Allocate a fresh AVD, never attach to a developer's ADB/device/AVD."""
    unused_ports((ADB_PORT, EMULATOR_PORT, EMULATOR_PORT + 1))
    android_home = avd / "android-user"
    android_home.mkdir(mode=0o700)
    env = dict(os.environ, ANDROID_USER_HOME=str(android_home), ANDROID_ADB_SERVER_PORT=str(ADB_PORT),
               ADB_SERVER_SOCKET=f"tcp:localhost:{ADB_PORT}")
    adb, emulator = sdk / "platform-tools/adb", sdk / "emulator/emulator"
    manager = sdk / "cmdline-tools/latest/bin/avdmanager"
    name = "parlor-process-acceptance"
    run([str(manager), "create", "avd", "--name", name, "--package", policy["system_image_package"],
         "--device", "pixel_2"], timeout=120, env=env)
    process, server_owned = None, False

    def command(*args: str, timeout: int = 60) -> str:
        contract.require(process is not None and process.poll() is None, "Owned emulator is not running")
        contract.require((output / "emulator.log").stat().st_size <= EMULATOR_LOG_LIMIT,
                         "Emulator log exceeded its bound")
        return run([str(adb), "-P", str(ADB_PORT), "-s", SERIAL, *args], timeout=timeout, env=env)

    def phase(fixture: contract.Fixture, method: str, component: str) -> dict:
        log = output / f"{fixture.game}-{fixture.language}-{fixture.entry_kind}-{method}.log"
        contract.require(not log.exists() and not log.is_symlink(), "Native phase already has evidence; no replay")
        started = time.monotonic()
        code = run_logged([str(adb), "-P", str(ADB_PORT), "-s", SERIAL,
                           *fixture.arguments(method, component)], log, timeout=PHASE_SECONDS,
                          output_limit=contract.MAX_PHASE_OUTPUT, env=env)
        contract.require(code == 0, "Native phase command failed; retain its log, never retry")
        result = contract.phase_result(log.read_bytes(), fixture, method)
        return dict(result, log=log.name, log_sha256=digest(log), elapsed_seconds=time.monotonic() - started)

    with (output / "emulator.log").open("xb") as log:
        try:
            started = run([str(adb), "-P", str(ADB_PORT), "start-server"], env=env)
            contract.require("daemon not running" in started and "daemon started successfully" in started,
                             "Private ADB server was not freshly started; do not adopt or stop it")
            server_owned = True
            process = subprocess.Popen([str(emulator), "-avd", name, "-port", str(EMULATOR_PORT),
                                        "-no-window", "-no-audio", "-no-snapshot", "-no-boot-anim",
                                        "-gpu", "swiftshader", "-no-metrics"],
                                       stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT, env=env)
            deadline = time.monotonic() + 300
            while True:
                contract.require(process.poll() is None and time.monotonic() < deadline, "Owned emulator did not boot")
                contract.require(log.tell() <= EMULATOR_LOG_LIMIT, "Emulator log exceeded its bound")
                try:
                    ready = command("shell", "getprop", "sys.boot_completed", timeout=10).strip() == "1"
                except RuntimeError:
                    ready = False  # Only boot observation; never replay an input/storage action.
                if ready:
                    break
                time.sleep(2)
            contract.require(command("shell", "getprop", "ro.kernel.qemu").strip() == "1", "Not the owned emulator")
            contract.require(command("shell", "getprop", "ro.build.version.sdk").strip() == str(policy["api_level"]),
                             "Emulator API differs from the pinned navigation fixture")
            command("shell", "input", "keyevent", "82")  # Unlock this fresh empty test device, before installing the app.
            yield command, phase
        finally:
            try:
                if process is not None and process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=5)
            finally:
                if server_owned:
                    run([str(adb), "-P", str(ADB_PORT), "kill-server"], env=env)
            contract.require(log.tell() <= EMULATOR_LOG_LIMIT, "Final emulator output exceeded its bound")
    # The enclosing child-subreaper additionally proves all adopted descendants
    # exited, then (and only after PASS) retires its own AVD namespace.


def preflight(output: Path) -> tuple[Path, Path, dict, dict]:
    contract.require(os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("RUNNER_OS") == "Linux"
                     and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted"
                     and os.environ.get("GITHUB_REPOSITORY") == "Apdelrahman1911/parlor",
                     "Process acceptance uses disposable hosted Parlor Linux runners only")
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    avd = Path(os.environ["ANDROID_AVD_HOME"])
    info = avd.lstat()
    contract.require(not avd.is_symlink() and avd.resolve(strict=True) == avd
                     and avd.name.startswith("parlor-managed-avd-") and stat.S_ISDIR(info.st_mode)
                     and stat.S_IMODE(info.st_mode) == 0o700 and info.st_uid == os.getuid()
                     and not list(avd.iterdir()), "Need the supervisor's fresh private empty AVD home")
    contract.require(Path("/dev/kvm").is_char_device() and os.access("/dev/kvm", os.R_OK | os.W_OK), "KVM unavailable")
    source = {"head": run(["git", "rev-parse", "HEAD"]).strip(),
              "tree": run(["git", "rev-parse", "HEAD^{tree}"]).strip()}
    contract.require(not run(["git", "status", "--porcelain"]).strip()
                     and source["head"] == os.environ["GITHUB_SHA"], "Native acceptance source must be the clean checkout")
    source["files"] = {path: digest(ROOT / path) for path in SOURCE_PATHS}
    config = json.loads((ROOT / "config/release-policy.json").read_text())
    policy = config["toolchains"]["android_navigation_device"]
    properties = (sdk / policy["system_image_package"].replace(";", "/") / "source.properties").read_text()
    contract.require(re.findall(r"(?m)^Pkg\.Revision\s*=\s*([^\s]+)\s*$", properties) == [policy["system_image_revision"]],
                     "Unreviewed Android image revision")
    contract.require(output.resolve().is_relative_to((ROOT / "build/ci-evidence").resolve()),
                     "Process receipts must stay inside the existing reviewed artifact selection")
    return sdk, avd, config, source


def execute(output: Path) -> dict:
    sdk, avd, config, source = preflight(output)
    output.mkdir(parents=True, mode=0o700, exist_ok=False)
    record = {"schema": 1, "result": "FAIL", "source": source, "cases": [],
              "scope": "Android EN/AR original-game local and multiplayer Home deletion across actual app force-stop "
                       "and new native process; synthetic credentials do not prove authenticated admission or rejoin",
              "physical_device_verified": False, "ios_protection_verified": False}
    previous = {}

    def interrupted(_number, _frame):
        raise InterruptedError("Process acceptance interrupted; no success inferred")

    try:
        for number in (signal.SIGTERM, signal.SIGINT):
            previous[number] = signal.signal(number, interrupted)
        candidates = list((ROOT / "composeApp/build/outputs/apk/debug").glob("*.apk"))
        tests = list((ROOT / "composeApp/build/outputs/apk/androidTest/debug").glob("*.apk"))
        contract.require(len(candidates) == len(tests) == 1, "Need exactly the already-built Debug app and test APK")
        app, test = candidates[0], tests[0]
        analyzer = sdk / "cmdline-tools/latest/bin/apkanalyzer"
        application_id = config["applications"]["android"]["debug_application_id"]
        component = inspect_manifests(run([str(analyzer), "manifest", "print", str(app)]),
                                      run([str(analyzer), "manifest", "print", str(test)]), application_id)
        record["apks"] = {"app": {"sha256": digest(app), "bytes": app.stat().st_size},
                          "test": {"sha256": digest(test), "bytes": test.stat().st_size}}
        record["application_id"] = application_id
        with owned_emulator(sdk, avd, output, config["toolchains"]["android_navigation_device"]) as (command, phase):
            contract.require(command("install", str(app), timeout=120).strip().endswith("Success"),
                             "Debug application failed normal installation")
            contract.require(command("install", "-t", str(test), timeout=120).strip().endswith("Success"),
                             "Dedicated instrumentation test APK failed installation")
            for game in contract.GAMES:
                for language in contract.LANGUAGES:
                    for entry_kind in contract.ENTRY_KINDS:
                        fixture = contract.Fixture(str(uuid4()), game, language, entry_kind)
                        record["cases"].append(case_journey(fixture, application_id, component, command, phase))
        contract.require(len(record["cases"]) == 12 and all(c["result"] == "PASS" for c in record["cases"]),
                         "Incomplete native process matrix")
        contract.require(digest(app) == record["apks"]["app"]["sha256"]
                         and digest(test) == record["apks"]["test"]["sha256"], "APK changed during execution")
        record["result"] = "PASS"
    finally:
        for number, handler in previous.items():
            signal.signal(number, handler)
        # Never overwrite a prior success or fabricate an outcome on interruption.
        with (output / "result.json").open("x") as stream:
            stream.write(json.dumps(record, indent=2) + "\n")
    return record


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    execute(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
