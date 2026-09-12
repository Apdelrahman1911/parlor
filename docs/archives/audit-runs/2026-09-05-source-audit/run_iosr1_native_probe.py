#!/usr/bin/env python3
"""Audit-only, unsigned native-equivalent probe with task-owned simulator cleanup."""
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import plistlib
import re
import runpy
import shutil
import signal
import subprocess
import sys
import tempfile
import time

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[1]
BUILD = runpy.run_path(str(OUT / "run_gradle_cycle.py"))


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def main():
    name = sys.argv[1]
    linker_default = sys.argv[2:] == ["--linker-default"]
    if sys.argv[2:] and not linker_default:
        raise SystemExit("Only --linker-default is supported")
    if not re.fullmatch(r"[a-z0-9-]+", name):
        raise SystemExit("Invalid cycle name")
    dest = OUT / "evidence" / name
    dest.mkdir(exist_ok=False)
    with (OUT / "build-lane.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        if any(p.exists() for p in BUILD["owned_outputs"]()):
            raise SystemExit("Pre-existing build output; separate ownership review required")
        env = {k: os.environ[k] for k in ["HOME", "USER", "LOGNAME", "TMPDIR"] if k in os.environ}
        env.update(PATH="/usr/bin:/bin:/usr/sbin:/sbin", LANG="en_US.UTF-8", PYTHONDONTWRITEBYTECODE="1")
        env["JAVA_HOME"] = subprocess.check_output(["/usr/libexec/java_home", "-v", "21"], text=True).strip()
        temp = Path(tempfile.mkdtemp(prefix="parlor-audit-" + name + "-"))
        app = temp / "Probe.app"
        app.mkdir()
        source = OUT / "reproducers/IOSR1NativeProbe.m"
        receipt = {"started_at": now(), "source_before": BUILD["identity"](), "cycle": name,
                   "status": "RUNNING", "commands": [], "owned_temp": str(temp),
                   "reproducer": str(source.relative_to(OUT)),
                   "reproducer_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                   "scope": "Native equivalent APIs, not actual KMP/Home error attribution. Unsigned isolated simulator only.",
                   "signing": (
                       "Default simulator linker ad-hoc code-directory only, like CODE_SIGNING_ALLOWED=NO Xcode link. "
                       "No codesign signing operation, identities, certificates, private keys, entitlements or Store artifact."
                       if linker_default else
                       "No signing operation or entitlements; linker automatic ad-hoc signing explicitly disabled."
                   )}
        uuid = None
        bundle = "audit.parlor.iosr1-native"

        def save():
            (dest / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")

        def command(args, logname, timeout=120):
            entry = {"command": args, "started_at": now(), "log": logname}
            receipt["commands"].append(entry)
            save()
            with (dest / logname).open("w") as log:
                process = subprocess.Popen(args, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
                try:
                    entry["exit_code"] = process.wait(timeout=timeout)
                except BaseException:
                    process.terminate()
                    try:
                        process.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
                    entry["exit_code"] = "TIMEOUT_OR_INTERRUPTED"
                    raise
                finally:
                    entry["finished_at"] = now()
                    save()
            return entry["exit_code"]

        rc = 1
        save()
        try:
            info = {"CFBundleIdentifier": bundle, "CFBundleName": "Parlor Audit Probe",
                    "CFBundleExecutable": "Probe", "CFBundlePackageType": "APPL",
                    "CFBundleVersion": "1", "CFBundleShortVersionString": "1.0",
                    "MinimumOSVersion": "16.0", "LSRequiresIPhoneOS": True,
                    "UIDeviceFamily": [1, 2], "UILaunchScreen": {},
                    "UISupportedInterfaceOrientations": ["UIInterfaceOrientationPortrait"]}
            (app / "Info.plist").write_bytes(plistlib.dumps(info))
            sdk = subprocess.check_output(["xcrun", "--sdk", "iphonesimulator", "--show-sdk-path"], env=env, text=True).strip()
            args = ["xcrun", "--sdk", "iphonesimulator", "clang", "-arch", "arm64", "-isysroot", sdk,
                    "-mios-simulator-version-min=16.0", "-fobjc-arc", "-framework", "UIKit",
                    "-framework", "Foundation", "-framework", "Security",
                    *([] if linker_default else ["-Wl,-no_adhoc_codesign"]),
                    str(source), "-o", str(app / "Probe")]
            compile_rc = command(args, "compile.log")
            receipt["compile_exit_code"] = compile_rc
            receipt["immediate_stop_exit_code"] = command(["./gradlew", "--stop"], "stop-immediate.log")
            if compile_rc != 0:
                raise RuntimeError("Native-equivalent probe did not compile")
            receipt["binary_sha256"] = hashlib.sha256((app / "Probe").read_bytes()).hexdigest()
            command(["codesign", "-d", "--entitlements", ":-", str(app)], "signature-inspection.log")
            if command(["xcrun", "simctl", "create", "Parlor-Audit-" + name,
                        "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro",
                        "com.apple.CoreSimulator.SimRuntime.iOS-26-5"], "create.log") != 0:
                raise RuntimeError("Simulator create failed")
            uuid = (dest / "create.log").read_text().strip()
            if not re.fullmatch(r"[0-9A-Fa-f-]{36}", uuid):
                uuid = None
                raise RuntimeError("Unexpected simulator response; review ownership before cleanup")
            receipt["owned_uuid"] = uuid
            for args, log in [(["xcrun", "simctl", "boot", uuid], "boot.log"),
                              (["xcrun", "simctl", "bootstatus", uuid, "-b"], "bootstatus.log"),
                              (["xcrun", "simctl", "install", uuid, str(app)], "install.log"),
                              (["xcrun", "simctl", "launch", uuid, bundle], "launch.log")]:
                if command(args, log, timeout=300) != 0:
                    raise RuntimeError("Probe runtime operation failed: " + log)
            if command(["xcrun", "simctl", "get_app_container", uuid, bundle, "data"], "owned-container.log") != 0:
                raise RuntimeError("Synthetic container lookup failed")
            container = Path((dest / "owned-container.log").read_text().strip())
            if uuid not in str(container) or not container.is_dir():
                raise RuntimeError("Container ownership not established")
            result_path = container / "Documents/probe-result.json"
            for _ in range(100):
                if result_path.exists():
                    break
                time.sleep(0.2)
            if not result_path.is_file():
                raise RuntimeError("No numeric probe result; native launch is not a completed probe")
            result = json.loads(result_path.read_text())
            (dest / "probe-result.json").write_text(json.dumps(result, indent=2) + "\n")
            receipt["probe_result"] = result
            receipt["status"] = "PASS"  # witness executed, NOT application readiness
            rc = 0
        except BaseException as error:
            receipt["status"] = "FAIL"
            receipt["error"] = type(error).__name__ + ": " + str(error)
        finally:
            # Stop is deliberate even though this probe compiles no Gradle tasks.
            receipt["final_stop_exit_code"] = command(["./gradlew", "--stop"], "stop-final.log")
            if uuid is not None:
                command(["xcrun", "simctl", "terminate", uuid, bundle], "terminate.log")
                receipt["shutdown_exit_code"] = command(["xcrun", "simctl", "shutdown", uuid], "shutdown.log")
                receipt["delete_exit_code"] = command(["xcrun", "simctl", "delete", uuid], "delete.log")
                devices = subprocess.run(["xcrun", "simctl", "list", "devices", "-j"], env=env, capture_output=True, text=True)
                if devices.returncode == 0:
                    receipt["owned_device_absent"] = not any(d["udid"] == uuid for ds in json.loads(devices.stdout)["devices"].values() for d in ds)
                processes = subprocess.run(["ps", "-axo", "pid,ppid,command"], env=env, capture_output=True, text=True)
                receipt["owned_uuid_processes_remaining"] = [l for l in processes.stdout.splitlines() if uuid in l]
            shutil.rmtree(temp)
            receipt["owned_temporary_outputs_removed"] = not temp.exists()
            receipt["remaining_build_outputs"] = [str(p.relative_to(ROOT)) for p in BUILD["owned_outputs"]() if p.exists()]
            receipt["source_after"] = BUILD["identity"]()
            receipt["finished_at"] = now()
            save()
            print(json.dumps(receipt, indent=2), flush=True)
        return rc


if __name__ == "__main__":
    def interrupted(signum, frame):
        raise KeyboardInterrupt("signal " + str(signum))
    signal.signal(signal.SIGTERM, interrupted)
    sys.exit(main())
