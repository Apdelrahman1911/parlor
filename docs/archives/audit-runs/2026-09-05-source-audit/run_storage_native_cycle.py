#!/usr/bin/env python3
"""Focused audit-only iOS storage run; fresh owned simulator plus mandatory finalizers."""
import datetime
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[1]


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def main():
    name = sys.argv[1]
    if not re.fullmatch(r"[a-z0-9-]+", name):
        raise SystemExit("Invalid cycle name")
    dest = OUT / "evidence" / (name + "-simulator")
    dest.mkdir(parents=True, exist_ok=False)
    env = os.environ.copy()
    env["PATH"] = "/usr/bin:/bin:/usr/sbin:/sbin:" + env.get("PATH", "")
    env["JAVA_HOME"] = subprocess.check_output(
        ["/usr/libexec/java_home", "-v", "21"], text=True
    ).strip()
    env["PARLOR_AUDIT_GRADLE_HEAP"] = "6g"
    receipt = {"started_at": now(), "cycle": name, "commands": [], "status": "RUNNING"}
    uuid = None
    process = None

    def save():
        (dest / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")

    def command(args, logfile, timeout=120):
        with (dest / logfile).open("w") as log:
            try:
                result = subprocess.run(
                    args, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT,
                    timeout=timeout,
                )
                rc = result.returncode
            except subprocess.TimeoutExpired:
                rc = "TIMEOUT"
        receipt["commands"].append({"command": args, "exit_code": rc, "at": now(), "log": logfile})
        save()
        return rc

    save()
    rc = 1
    try:
        create = command([
            "xcrun", "simctl", "create", "Parlor-Audit-" + name,
            "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro",
            "com.apple.CoreSimulator.SimRuntime.iOS-26-5",
        ], "create.log")
        if create != 0:
            raise RuntimeError("Owned simulator creation failed")
        uuid = (dest / "create.log").read_text().strip()
        if not re.fullmatch(r"[0-9A-Fa-f-]{36}", uuid):
            uuid = None
            raise RuntimeError("Unexpected creation response; inspect receipt before cleanup")
        receipt["owned_uuid"] = uuid
        save()
        if command(["xcrun", "simctl", "boot", uuid], "boot.log") != 0:
            raise RuntimeError("Owned simulator boot failed")
        if command(["xcrun", "simctl", "bootstatus", uuid, "-b"], "bootstatus.log", 300) != 0:
            raise RuntimeError("Owned simulator did not finish boot")
        env["PARLOR_AUDIT_SIMULATOR_UDID"] = uuid
        args = [
            "/usr/bin/python3", "-B", str(OUT / "run_gradle_cycle.py"), name,
            ":composeApp:iosSimulatorArm64Test", "--tests", "*STC01LegacyBackupEligibilityAuditTest*", "--no-build-cache",
            "--console=plain", "-I", str(OUT / "reproducers/OwnedIosSimulator-session_cont.init.gradle"),
            "-I", str(OUT / "repro_storage_native.init.gradle"),
        ]
        with (dest / "cycle-runner.log").open("w") as log:
            process = subprocess.Popen(args, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
            try:
                rc = process.wait(timeout=3600)
            except BaseException:
                process.terminate()  # runner SIGTERM handler executes stop/cleanup finally
                process.wait(timeout=180)
                raise
        receipt["gradle_runner_exit_code"] = rc
        receipt["status"] = "PASS" if rc == 0 else "FAIL"
    except BaseException as error:
        receipt["status"] = "FAIL"
        receipt["error"] = type(error).__name__ + ": " + str(error)
        rc = 1
    finally:
        # Redundant stop is deliberate: this finalizer covers startup and runner failures too.
        receipt["stop_exit_code"] = command(["./gradlew", "--stop"], "stop.log")
        if uuid is not None:
            receipt["shutdown_exit_code"] = command(["xcrun", "simctl", "shutdown", uuid], "shutdown.log")
            receipt["delete_exit_code"] = command(["xcrun", "simctl", "delete", uuid], "delete.log")
            devices = subprocess.run(["xcrun", "simctl", "list", "devices", "-j"], text=True, capture_output=True)
            if devices.returncode == 0:
                matches = [d for rows in json.loads(devices.stdout)["devices"].values() for d in rows if d["udid"] == uuid]
                receipt["owned_device_absent"] = not matches
            else:
                receipt["device_absence_check_exit_code"] = devices.returncode
            scan = subprocess.run(["ps", "-axo", "pid,ppid,command"], text=True, capture_output=True)
            receipt["owned_uuid_processes_remaining"] = [line for line in scan.stdout.splitlines() if uuid in line]
        receipt["finished_at"] = now()
        save()
        print(json.dumps(receipt, indent=2), flush=True)
    return rc


if __name__ == "__main__":
    def interrupted(signum, frame):
        raise KeyboardInterrupt("signal " + str(signum))
    signal.signal(signal.SIGTERM, interrupted)
    sys.exit(main())
