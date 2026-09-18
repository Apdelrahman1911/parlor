#!/usr/bin/env python3
"""Install the final test APK without adb -t on a disposable CI-only emulator."""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import socket
import subprocess
import tempfile
import time

from scripts.release import desktop_package as packages
from scripts.release.android_test_package import APPLICATION_ID
from scripts.release.github_distribution import ROOT, require, run
from scripts.release.sign_distribution import retire_on_sigterm


def unused_ports(ports: tuple[int, ...]) -> None:
    # Refuse collisions instead of stopping any existing emulator/ADB server.
    sockets = []
    try:
        for port in ports:
            sock = socket.socket()
            sockets.append(sock)
            sock.bind(("127.0.0.1", port))
    finally:
        for sock in sockets:
            sock.close()


@retire_on_sigterm()
def verify_install(apk: Path) -> None:
    packages.check_host("android")
    require(os.environ.get("GITHUB_ACTIONS") == "true", "Android test smoke uses disposable GitHub runners only")
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    policy = json.loads((ROOT / "config/release-policy.json").read_text())["toolchains"]["android_managed_device"]
    image = policy["system_image_package"]
    properties = (sdk / image.replace(";", "/") / "source.properties").read_text()
    require(re.findall(r"(?m)^Pkg\.Revision\s*=\s*([^\s]+)\s*$", properties) == [policy["system_image_revision"]],
            "Unreviewed emulator image revision")
    require(Path("/dev/kvm").exists() and os.access("/dev/kvm", os.R_OK | os.W_OK), "Test APK smoke requires KVM")
    adb, emulator = sdk / "platform-tools/adb", sdk / "emulator/emulator"
    adb_port, emulator_port = 5038, 5660
    unused_ports((adb_port, emulator_port, emulator_port + 1))
    serial = f"emulator-{emulator_port}"
    with tempfile.TemporaryDirectory(prefix="parlor-test-emulator-") as temporary:
        scratch = Path(temporary)
        env = dict(os.environ, ANDROID_AVD_HOME=str(scratch / "avds"), ANDROID_USER_HOME=str(scratch / "android"),
                   ANDROID_ADB_SERVER_PORT=str(adb_port), ADB_SERVER_SOCKET=f"tcp:localhost:{adb_port}")
        Path(env["ANDROID_AVD_HOME"]).mkdir()
        Path(env["ANDROID_USER_HOME"]).mkdir()
        manager = sdk / "cmdline-tools/latest/bin/avdmanager"
        name = "parlor-public-test"
        run([str(manager), "create", "avd", "--name", name, "--package", image, "--device", "pixel_2"], timeout=120, env=env)
        def command(*args: str, timeout: int = 60) -> str:
            return run([str(adb), "-P", str(adb_port), "-s", serial, *args], timeout=timeout, env=env)
        process, server_started = None, False
        with (scratch / "emulator.log").open("wb") as log:
            try:
                run([str(adb), "-P", str(adb_port), "start-server"], env=env)
                server_started = True
                process = subprocess.Popen([str(emulator), "-avd", name, "-port", str(emulator_port), "-no-window",
                    "-no-audio", "-no-snapshot", "-no-boot-anim", "-gpu", "swiftshader_indirect", "-no-metrics"],
                    stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT, env=env)
                deadline = time.monotonic() + 300
                while True:
                    require(process.poll() is None and time.monotonic() < deadline, "Owned test emulator failed to boot")
                    require(log.tell() <= 16 * 1024 * 1024, "Emulator output exceeded its bound")
                    try:
                        if command("shell", "getprop", "sys.boot_completed", timeout=10).strip() == "1":
                            break
                    except RuntimeError:
                        pass  # Only the bounded boot readiness probe is retryable.
                    time.sleep(2)
                command("shell", "input", "keyevent", "82")
                require(command("install", str(apk), timeout=120).strip().endswith("Success"),
                        "Final test APK is not normally installable (no test-only install override is allowed)")
                launch = command("shell", "am", "start", "-W", "-n", APPLICATION_ID + "/com.parlor.app.MainActivity")
                require("Status: ok" in launch and "Error" not in launch, "Final test APK did not launch")
                time.sleep(10)
                require(command("shell", "pidof", APPLICATION_ID).strip().isdigit(), "Test app stopped after launch")
                command("shell", "am", "force-stop", APPLICATION_ID)
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
                    if server_started:
                        run([str(adb), "-P", str(adb_port), "kill-server"], env=env)
