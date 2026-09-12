#!/usr/bin/env python3
"""Unsigned app-wrapper test; root-owned simulator, build lane and finalizer."""
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
    if not re.fullmatch(r"[a-z0-9-]+", name):
        raise SystemExit("Invalid cycle")
    dest = OUT / "evidence" / name
    dest.mkdir(parents=True, exist_ok=False)
    with (OUT / "build-lane.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        outputs = BUILD["owned_outputs"]() + [ROOT / "iosApp/build"]
        if any(p.exists() for p in outputs):
            raise SystemExit("Pre-existing output; ownership review required")
        # Xcode's build log may export every environment variable. Whitelist,
        # rather than record/print inherited credentials or account variables.
        env = {k: os.environ[k] for k in ["HOME", "USER", "LOGNAME", "TMPDIR"] if k in os.environ}
        env["PATH"] = "/usr/bin:/bin:/usr/sbin:/sbin"
        env["JAVA_HOME"] = subprocess.check_output(["/usr/libexec/java_home", "-v", "21"], text=True).strip()
        env["LANG"] = "en_US.UTF-8"
        env["PYTHONDONTWRITEBYTECODE"] = "1"
        env["GRADLE_OPTS"] = " ".join([
            "-Dorg.gradle.parallel=false", "-Dorg.gradle.workers.max=1",
            "-Dorg.gradle.configuration-cache=false", "-Dorg.gradle.caching=false",
            "-Dorg.gradle.project.kotlin.compiler.execution.strategy=in-process",
            *["-Dorg.gradle.project.parlor.android.signing." + key + "="
              for key in ["storeFile", "storePassword", "keyAlias", "keyPassword"]],
        ])
        receipt = {"cycle": name, "started_at": now(), "source_before": BUILD["identity"](),
                   "commands": [], "status": "RUNNING", "private_signing_inputs": "not supplied; code signing disabled",
                   "env_policy": "safe whitelist, repository6g heap, one Gradle worker, no parallel, no cache", "outputs_before": []}
        uuid = None
        temp = Path(tempfile.mkdtemp(prefix="parlor-audit-" + name + "-"))
        results = temp / "Results.xcresult"
        receipt["owned_temporary_directory"] = str(temp)

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
                        process.wait(timeout=45)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
                    entry["exit_code"] = "INTERRUPTED_OR_TIMEOUT"
                    raise
                finally:
                    entry["finished_at"] = now()
                    save()
            return entry["exit_code"]

        rc = 1
        save()
        try:
            create_rc = command(["xcrun", "simctl", "create", "Parlor-Audit-" + name,
                                 "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro",
                                 "com.apple.CoreSimulator.SimRuntime.iOS-26-5"], "create.log")
            if create_rc != 0:
                raise RuntimeError("Simulator creation failed")
            uuid = (dest / "create.log").read_text().strip()
            if not re.fullmatch(r"[0-9A-Fa-f-]{36}", uuid):
                uuid = None
                raise RuntimeError("Unexpected simulator response; inspect ownership receipt")
            receipt["owned_uuid"] = uuid
            if command(["xcrun", "simctl", "boot", uuid], "boot.log") != 0:
                raise RuntimeError("Simulator boot failed")
            if command(["xcrun", "simctl", "bootstatus", uuid, "-b"], "bootstatus.log", 300) != 0:
                raise RuntimeError("Simulator boot incomplete")
            args = ["xcodebuild", "-project", "iosApp/iosApp.xcodeproj", "-scheme", "iosApp",
                    "-configuration", "Debug", "-sdk", "iphonesimulator", "-destination", "id=" + uuid,
                    "-derivedDataPath", str(temp / "DerivedData"), "-resultBundlePath", str(results),
                    "-parallel-testing-enabled", "NO", "-maximum-concurrent-test-simulator-destinations", "1",
                    "-disable-concurrent-destination-testing", "-jobs", "1", "-test-timeouts-enabled", "YES",
                    "-default-test-execution-time-allowance", "90", "-maximum-test-execution-time-allowance", "180",
                    "CODE_SIGNING_ALLOWED=NO", "CODE_SIGNING_REQUIRED=NO", "CODE_SIGN_IDENTITY=",
                    "DEVELOPMENT_TEAM=", "ONLY_ACTIVE_ARCH=YES", "COMPILER_INDEX_STORE_ENABLE=NO", "test"]
            rc = command(args, "xcodebuild.log", 2400)
            receipt["xcodebuild_exit_code"] = rc
            # Finish the build lane before inspecting its retained artifacts.
            receipt["immediate_stop_exit_code"] = command(["./gradlew", "--stop"], "stop-immediate.log")
            if results.exists():
                for view in ["summary", "tests"]:
                    command(["xcrun", "xcresulttool", "get", "test-results", view, "--path", str(results)],
                            "xcresult-" + view + ".json")
            app = temp / "DerivedData/Build/Products/Debug-iphonesimulator/Parlor.app"
            if app.is_dir():
                info = plistlib.loads((app / "Info.plist").read_bytes())
                receipt["app_identity"] = {k: info.get(k) for k in ["CFBundleIdentifier", "CFBundleVersion", "CFBundleShortVersionString", "MinimumOSVersion", "CFBundleExecutable"]}
                binaries = [app / info["CFBundleExecutable"], app / "Frameworks/ComposeApp.framework/ComposeApp"]
                receipt["binaries"] = [{"path": str(p.relative_to(app)), "bytes": p.stat().st_size,
                                        "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in binaries if p.is_file()]
                command(["otool", "-L", str(binaries[0])], "app-linkage.txt")
                receipt["packaged_resources"] = sorted(str(p.relative_to(app)) for p in app.rglob("*") if p.is_file())
                # These supplemental launches use only the new simulator/app container.
                # Screenshot success is not a UI assertion or physical gesture proof.
                for locale, country in [("en", "en_US"), ("ar", "ar_EG")]:
                    command(["xcrun", "simctl", "terminate", uuid, "com.parlor.app.debug"], "terminate-before-" + locale + ".log")
                    launch_rc = command(["xcrun", "simctl", "launch", uuid, "com.parlor.app.debug",
                                         "-AppleLanguages", "(" + locale + ")", "-AppleLocale", country], "launch-" + locale + ".log")
                    if launch_rc == 0:
                        time.sleep(5)
                        command(["xcrun", "simctl", "io", uuid, "screenshot", str(dest / ("home-" + locale + ".png"))], "screenshot-" + locale + ".log")
                        command(["xcrun", "simctl", "terminate", uuid, "com.parlor.app.debug"], "terminate-after-" + locale + ".log")
            receipt["status"] = "PASS" if rc == 0 else "FAIL"
        except BaseException as error:
            receipt["status"] = "FAIL"
            receipt["error"] = type(error).__name__ + ": " + str(error)
            rc = 1
        finally:
            receipt["stop_exit_code"] = command(["./gradlew", "--stop"], "stop-final.log")
            if uuid is not None:
                receipt["shutdown_exit_code"] = command(["xcrun", "simctl", "shutdown", uuid], "shutdown.log")
                receipt["delete_exit_code"] = command(["xcrun", "simctl", "delete", uuid], "delete.log")
                listing = subprocess.run(["xcrun", "simctl", "list", "devices", "-j"], env=env, capture_output=True, text=True)
                if listing.returncode == 0:
                    receipt["owned_device_absent"] = not any(d["udid"] == uuid for rows in json.loads(listing.stdout)["devices"].values() for d in rows)
            receipt["removed_outputs"] = []
            receipt["cleanup_errors"] = []
            for output in outputs:
                try:
                    if output.is_symlink():
                        raise RuntimeError("Refusing symlink cleanup")
                    if output.exists():
                        shutil.rmtree(output)
                        receipt["removed_outputs"].append(str(output.relative_to(ROOT)))
                except Exception as error:
                    receipt["cleanup_errors"].append(str(output) + ": " + str(error))
            shutil.rmtree(temp)
            receipt["temporary_directory_removed"] = not temp.exists()
            receipt["remaining_outputs"] = [str(p.relative_to(ROOT)) for p in outputs if p.exists()]
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
