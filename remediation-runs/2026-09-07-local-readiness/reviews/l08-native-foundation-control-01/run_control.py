#!/usr/bin/env python3
"""PROPOSAL: native-only Simulator metadata control; never an L08 PASS.

Nothing executes on import. A separately authored, hash-bound approval and
--execute are mandatory. Only root's shared build lane may execute this draft.
"""
from __future__ import annotations

import argparse
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import signal
import stat
import subprocess
import tempfile
import time
import uuid


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
CAMPAIGN = ROOT / "remediation-runs/2026-09-07-local-readiness"
XCODE = Path("/Applications/Xcode.app/Contents/Developer")
SDK = XCODE / "Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk"
SIMCTL = Path("/Library/Developer/PrivateFrameworks/CoreSimulator.framework/Versions/A/Resources/bin/simctl")
CORE_PLIST = SIMCTL.parent.parent / "Info.plist"
CLANG = XCODE / "Toolchains/XcodeDefault.xctoolchain/usr/bin/clang"
DWARF = XCODE / "Toolchains/XcodeDefault.xctoolchain/usr/bin/dwarfdump"
RUNTIME = "com.apple.CoreSimulator.SimRuntime.iOS-26-5"
DEVICE_TYPE = "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro"
UUID = re.compile(r"[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}\Z")
HASH = re.compile(r"[0-9a-f]{64}\Z")
ROW_KINDS = (
    ("directory-create", "operation"), ("directory-after-create", "directory"),
    ("directory-backup", "operation"), ("directory-after-backup", "directory"),
    ("atomic-write", "operation"), ("atomic-after-write", "file"),
    ("atomic-backup", "operation"), ("atomic-after-backup", "file"),
    ("directory-set", "operation"), ("directory-after-set", "directory"),
    ("direct-write", "operation"), ("direct-after-write", "file"),
    ("default-write", "operation"), ("file-before-set", "file"),
    ("file-set", "operation"), ("file-after-set", "file"),
)


class BoundaryError(RuntimeError):
    """Closed local error code only; never arbitrary native error text."""


def require(condition, code):
    if not condition:
        raise BoundaryError(code)


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def sha256(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "duplicate-json-key")
        result[key] = value
    return result


def decode_json(raw, limit=1024 * 1024):
    require(len(raw) <= limit, "json-size")
    return json.loads(raw.decode("utf-8", errors="strict"), object_pairs_hook=no_duplicates,
                      parse_constant=lambda _: (_ for _ in ()).throw(BoundaryError("json-number")))


def write_json(path, value):
    # Exclusive, additive evidence only; no receipt from an old cycle is replaced.
    with path.open("x", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2, sort_keys=True)
        stream.write("\n")


def validate_error(value):
    require(isinstance(value, dict) and set(value) == {"present", "domain", "code"}, "native-error-shape")
    require(type(value["present"]) is bool and type(value["code"]) is int, "native-error-types")
    require(value["domain"] in {"none", "cocoa", "posix", "other"}, "native-error-domain")
    require(abs(value["code"]) < 2**63, "native-error-bound")
    require(value["present"] or (value["domain"] == "none" and value["code"] == 0), "absent-native-error")
    require(not value["present"] or value["domain"] != "none", "present-native-error")


def validate_query(value, volume=False, backup=False):
    keys = {"returned", "dictionary_present", "key_present", "value", "exception", "native_error"}
    require(isinstance(value, dict) and set(value) == keys, "query-shape")
    require(all(type(value[key]) is bool for key in ("returned", "dictionary_present", "key_present")), "query-booleans")
    validate_error(value["native_error"])
    require(value["exception"] in {"none", "objc-exception"}, "query-exception")
    if not value["returned"]:
        require(not value["dictionary_present"] and not value["key_present"] and
                value["value"] == "unobserved" and value["exception"] == "objc-exception" and
                not value["native_error"]["present"], "exception-query")
        return
    require(value["exception"] == "none", "returned-query-exception")
    if not value["dictionary_present"]:
        require(not value["key_present"] and value["value"] == "unavailable", "absent-dictionary")
    elif not value["key_present"]:
        require(value["value"] == "missing", "absent-key")
    else:
        allowed = {"excluded", "included", "non-number"} if backup else {"supported", "unsupported", "non-number"} if volume else {
            "null", "non-string", "complete", "none", "unless-open",
            "until-first-authentication", "when-user-inactive", "other-string"}
        require(value["value"] in allowed, "query-value")


def validate_observations(raw, token, source_hash, image_uuid):
    """Valid collection is NOT storage success or a reinterpretation of L08."""
    value = decode_json(raw, 32768)
    expected = {"schema_version", "run_token", "source_sha256", "simulator_only", "app_container",
                "hardware_protection_verified", "l08_requirements_waived", "pid", "rows",
                "runtime_version", "main_image", "foundation_image", "status"}
    require(isinstance(value, dict) and set(value) == expected, "observation-shape")
    require(type(value["schema_version"]) is int and value["schema_version"] == 1, "schema-version")
    require(value["run_token"] == token and value["source_sha256"] == source_hash, "source-token")
    require(value["status"] == "OBSERVATIONS_COMPLETE", "collection-status")
    require(value["simulator_only"] is True and value["app_container"] is False and
            value["hardware_protection_verified"] is False and value["l08_requirements_waived"] is False,
            "claim-boundary")
    require(type(value["pid"]) is int and 1 < value["pid"] < 2**31, "native-pid")
    require(value["runtime_version"] == [26, 5, 0] and
            all(type(part) is int for part in value["runtime_version"]), "runtime-version")
    for key in ("main_image", "foundation_image"):
        image = value[key]
        require(isinstance(image, dict) and set(image) == {"status", "uuid", "platform"}, "image-shape")
        require(image["status"] == "observed" and isinstance(image["uuid"], str) and
                UUID.fullmatch(image["uuid"]) is not None and type(image["platform"]) is int and
                image["platform"] == 7, "image-ios-simulator")
    require(value["main_image"]["uuid"] == image_uuid.lower(), "image-uuid-binding")
    require(isinstance(value["rows"], list) and len(value["rows"]) == len(ROW_KINDS), "row-count")
    for row, (identifier, kind) in zip(value["rows"], ROW_KINDS):
        require(isinstance(row, dict) and row.get("id") == identifier, "row-order")
        if kind == "operation":
            require(set(row) == {"id", "kind", "returned", "result", "exception", "native_error"} and
                    row["kind"] == "operation", "operation-shape")
            require(type(row["returned"]) is bool and type(row["result"]) is bool, "operation-booleans")
            validate_error(row["native_error"])
            require(row["exception"] == ("none" if row["returned"] else "objc-exception"), "operation-exception")
            require(row["returned"] or (row["result"] is False and not row["native_error"]["present"]), "failed-operation")
        else:
            require(set(row) == ({"id", "kind", "fm", "volume", "backup", "url"} if kind == "file" else
                                {"id", "kind", "fm", "volume", "backup"}) and row["kind"] == "observation", "metadata-shape")
            validate_query(row["fm"])
            validate_query(row["volume"], volume=True)
            validate_query(row["backup"], backup=True)
            if kind == "file":
                validate_query(row["url"])
    return value


def device_entries(data):
    require(isinstance(data, dict) and isinstance(data.get("devices"), dict), "device-list-shape")
    result = []
    for runtime, items in data["devices"].items():
        require(isinstance(runtime, str) and isinstance(items, list) and len(items) <= 1, "device-runtime-items")
        for item in items:
            require(isinstance(item, dict), "device-entry-shape")
            result.append((runtime, item))
    require(len(result) <= 1, "device-set-not-exclusively-owned")
    return result


def selected_device(data, name, expected_uuid=None):
    """One token-named device in this fresh PRIVATE set, never a default set."""
    entries = device_entries(data)
    require(len(entries) == 1, "device-set-not-exclusively-owned")
    runtime, item = entries[0]
    require(runtime == RUNTIME and item.get("name") == name and
            item.get("deviceTypeIdentifier") == DEVICE_TYPE and item.get("isAvailable") is True and
            isinstance(item.get("udid"), str) and UUID.fullmatch(item["udid"]) is not None,
            "device-identity")
    require(expected_uuid is None or item["udid"].upper() == expected_uuid.upper(), "device-uuid")
    require(item.get("state") in {"Shutdown", "Booted", "Booting", "Shutting Down"}, "device-state")
    return item


def sim_command(device_set, *arguments):
    require(device_set.is_absolute() and device_set.name == "devices", "device-set-path")
    require(arguments and arguments[0] in {"list", "create", "boot", "bootstatus", "spawn", "shutdown", "delete"},
            "sim-command")
    require("all" not in arguments and "booted" not in arguments and "--standalone" not in arguments,
            "ambiguous-sim-target")
    if arguments[0] in {"boot", "bootstatus", "spawn", "shutdown", "delete"}:
        require(len(arguments) >= 2 and UUID.fullmatch(arguments[1]) is not None, "exact-sim-target")
    return [str(SIMCTL), "--set", str(device_set), *map(str, arguments)]


def fingerprint(path):
    info = path.lstat()
    require(stat.S_ISDIR(info.st_mode) and info.st_uid == os.getuid() and
            stat.S_IMODE(info.st_mode) == 0o700 and path.resolve() == path, "owned-root")
    return (info.st_dev, info.st_ino, info.st_uid)


def remove_owned_tree(path, expected):
    """Caller must prove shutdown/no holders first. Descriptor-relative, no traversal."""
    require(path.parent == Path("/private/tmp") and path.name.startswith("parlor-foundation-control-"), "cleanup-root-prefix")
    require(fingerprint(path) == expected, "cleanup-root-identity")
    count = [0]

    def contents(fd, device):
        for name in os.listdir(fd):
            count[0] += 1
            require(count[0] <= 100000 and name not in {".", ".."}, "cleanup-entry-limit")
            before = os.stat(name, dir_fd=fd, follow_symlinks=False)
            require(before.st_uid == os.getuid() and before.st_dev == device, "cleanup-entry-owner")
            if stat.S_ISDIR(before.st_mode):
                child = os.open(name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
                try:
                    opened = os.fstat(child)
                    require((opened.st_dev, opened.st_ino) == (before.st_dev, before.st_ino), "cleanup-directory-race")
                    contents(child, device)
                    current = os.stat(name, dir_fd=fd, follow_symlinks=False)
                    require((current.st_dev, current.st_ino) == (before.st_dev, before.st_ino), "cleanup-directory-replaced")
                    os.rmdir(name, dir_fd=fd)
                finally:
                    os.close(child)
            else:
                require(any(check(before.st_mode) for check in (stat.S_ISREG, stat.S_ISLNK, stat.S_ISFIFO, stat.S_ISSOCK)),
                        "cleanup-entry-type")
                os.unlink(name, dir_fd=fd)  # Symlinks themselves only; never their targets.

    fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        opened = os.fstat(fd)
        require((opened.st_dev, opened.st_ino, opened.st_uid) == expected, "cleanup-opened-root")
        contents(fd, expected[0])
        require(fingerprint(path) == expected, "cleanup-final-root")
        path.rmdir()
    finally:
        os.close(fd)
    require(not os.path.lexists(path), "cleanup-root-remains")


def child_signals():
    # Runner is single-threaded. Finalizer ignores repeated parent interruptions;
    # every new direct child restores ordinary signal/mask semantics before exec.
    signal.signal(signal.SIGINT, signal.SIG_DFL)
    signal.signal(signal.SIGTERM, signal.SIG_DFL)
    signal.pthread_sigmask(signal.SIG_SETMASK, set())


class Lane:
    def __init__(self, dest, jdk):
        self.dest, self.jdk = dest, jdk
        self.scratch = None
        self.scratch_identity = None
        self.device_uuid = None
        self.creation_attempted = False
        self.compilation_attempted = False
        self.token = uuid.uuid4().hex
        self.name = "ParlorFoundationControl-" + self.token
        self.receipt = dict(schema_version=1, started_at=now(), token=self.token,
                            status="NOT_EXECUTED", l08_status="UNCHANGED_FAILED_UNRESOLVED",
                            hardware_protection_verified=False, app_container=False,
                            commands=[], cleanup_errors=[])
        self.env = {"PATH": "/usr/bin:/bin:/usr/sbin:/sbin", "LC_ALL": "C", "LANG": "C",
                    "DEVELOPER_DIR": str(XCODE), "JAVA_HOME": str(jdk),
                    "PYTHONDONTWRITEBYTECODE": "1"}

    def command(self, label, args, *, timeout=30, accepted=(0,), extra_env=None, limit=1024 * 1024):
        require(re.fullmatch(r"[a-z0-9-]+", label) is not None, "command-label")
        start = now()
        buffers = {"stdout": bytearray(), "stderr": bytearray()}
        error = None
        child = None
        code = None
        try:
            # Defer parent interruption until the returned child handle is owned.
            previous = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGINT, signal.SIGTERM})
            try:
                child = subprocess.Popen(list(map(str, args)), cwd=ROOT,
                                         env={**self.env, **(extra_env or {})},
                                         stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                         start_new_session=True, preexec_fn=child_signals)
            finally:
                signal.pthread_sigmask(signal.SIG_SETMASK, previous)
            # DO NOT poll/reap before signalling: the unreaped direct PID holds
            # its process-group identity even after exit. No external PID adoption.
            with selectors.DefaultSelector() as selector:
                for name, stream in (("stdout", child.stdout), ("stderr", child.stderr)):
                    os.set_blocking(stream.fileno(), False)
                    selector.register(stream, selectors.EVENT_READ, name)
                deadline = time.monotonic() + timeout
                while selector.get_map():
                    require(time.monotonic() < deadline, "command-timeout")
                    for key, _ in selector.select(min(0.2, max(0, deadline - time.monotonic()))):
                        block = os.read(key.fileobj.fileno(), 65536)
                        if not block:
                            selector.unregister(key.fileobj)
                            continue
                        require(sum(map(len, buffers.values())) + len(block) <= limit, "command-output-limit")
                        buffers[key.data].extend(block)
                code = child.wait(timeout=max(0.1, deadline - time.monotonic()))
        except BaseException as problem:
            error = problem
        finally:
            if child is not None and child.returncode is None:
                # Only our own, still-unreaped Popen group can be signalled.
                for signum in (signal.SIGTERM, signal.SIGKILL):
                    try:
                        os.killpg(child.pid, signum)
                    except ProcessLookupError:
                        pass
                    if signum == signal.SIGTERM:
                        time.sleep(0.25)
                try:
                    code = child.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    error = BoundaryError("direct-child-not-reaped")
            if child is not None:
                child.stdout.close()
                child.stderr.close()
            for name, data in buffers.items():
                with (self.dest / (label + "." + name)).open("xb") as stream:
                    stream.write(data)
            self.receipt["commands"].append(dict(label=label, argv=list(map(str, args)), started_at=start,
                finished_at=now(), exit_code=code, stdout_bytes=len(buffers["stdout"]), stderr_bytes=len(buffers["stderr"]),
                error=(str(error) if isinstance(error, BoundaryError) else type(error).__name__) if error else None))
        if error:
            raise error
        require(code in accepted, "command-exit-" + label)
        return code, bytes(buffers["stdout"]), bytes(buffers["stderr"])

    def sim(self, label, *args, **kwargs):
        require(self.scratch is not None and fingerprint(self.scratch) == self.scratch_identity, "sim-root-identity")
        return self.command(label, sim_command(self.scratch / "devices", *args), **kwargs)

    def no_gradle(self, label):
        code, output, error = self.command(label, ["/usr/bin/pgrep", "-f",
            "[o]rg.gradle.launcher.daemon.bootstrap.GradleDaemon"], accepted=(0, 1))
        require(code == 1 and not output.strip() and not error.strip(), "unowned-gradle-daemon")

    def stop_gradle(self, label):
        # This fixture never starts Gradle. An externally started daemon is NOT
        # ours to terminate, even if it disregarded the shared lane lock.
        self.no_gradle(label + "-preflight")
        self.command(label, [str(ROOT / "gradlew"), "--stop"], timeout=90)
        self.no_gradle(label + "-verified")

    def prepare(self):
        require(os.uname().sysname == "Darwin" and os.uname().machine == "arm64", "host-platform")
        _, raw, _ = self.command("xcode-plist", ["/usr/bin/plutil", "-convert", "json", "-o", "-",
                                                XCODE.parent / "version.plist"])
        info = decode_json(raw)
        require(info.get("CFBundleShortVersionString") == "26.5" and info.get("ProductBuildVersion") == "17F42", "xcode-pin")
        _, raw, _ = self.command("coresimulator-plist", ["/usr/bin/plutil", "-convert", "json", "-o", "-", CORE_PLIST])
        require(decode_json(raw).get("CFBundleVersion") == "1051.54", "coresimulator-pin")
        require(SDK.is_dir(), "sdk-pin")
        sdk_settings = decode_json((SDK / "SDKSettings.json").read_bytes())
        require(sdk_settings.get("Version") == "26.5" and sdk_settings.get("CanonicalName") == "iphonesimulator26.5" and
                sdk_settings.get("SupportedTargets", {}).get("iphonesimulator", {}).get("BuildVersionPlatformID") == "7",
                "sdk-settings-pin")
        self.receipt["sdk_settings_sha256"] = sha256(SDK / "SDKSettings.json")
        self.command("jdk-version", [self.jdk / "bin/java", "-version"])
        raw = (self.dest / "jdk-version.stderr").read_bytes()
        require(re.search(rb'version "21(?:\.|\")', raw) is not None, "jdk21-required")
        self.no_gradle("gradle-baseline")
        for key, arguments in {"commit": ["rev-parse", "HEAD"], "tree": ["rev-parse", "HEAD^{tree}"],
                               "branch": ["branch", "--show-current"],
                               "tracked_status": ["status", "--porcelain=v1", "--untracked-files=no"]}.items():
            _, raw, _ = self.command("baseline-" + key.replace("_", "-"), ["/usr/bin/git", *arguments])
            self.receipt[key] = raw.decode("utf-8").strip()
        self.receipt["context_source_hashes"] = {relative: sha256(ROOT / relative) for relative in (
            "composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSnapshotFileSystem.kt",
            "scripts/verification/ios-readiness/L08StorageProbe.kt.in")}
        self.receipt["tool_hashes"] = {str(path): sha256(path) for path in (CLANG, DWARF, SIMCTL, CORE_PLIST)}
        # No signal may land between successful exclusive creation and ownership registration.
        previous = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGINT, signal.SIGTERM})
        try:
            self.scratch = Path(tempfile.mkdtemp(prefix="parlor-foundation-control-", dir="/private/tmp"))
            self.scratch_identity = fingerprint(self.scratch)
            self.receipt["scratch"] = dict(path=str(self.scratch), identity=list(self.scratch_identity))
        finally:
            signal.pthread_sigmask(signal.SIG_SETMASK, previous)
        for name in ("devices", "tmp", "home", "cache", "fixture"):
            (self.scratch / name).mkdir(mode=0o700)
        self.env.update(HOME=str(self.scratch / "home"), TMPDIR=str(self.scratch / "tmp") + "/")
        # No DYLD/SDK/signing/credential environment overrides are inherited.
        _, raw, _ = self.sim("runtimes", "list", "-j", "runtimes")
        matches = [item for item in decode_json(raw).get("runtimes", []) if item.get("identifier") == RUNTIME]
        require(len(matches) == 1 and matches[0].get("version") == "26.5" and
                matches[0].get("buildversion") == "23F77" and matches[0].get("isAvailable") is True, "runtime-pin")
        _, raw, _ = self.sim("device-types", "list", "-j", "devicetypes")
        require(sum(item.get("identifier") == DEVICE_TYPE for item in decode_json(raw).get("devicetypes", [])) == 1,
                "device-type-pin")
        _, raw, _ = self.sim("empty-device-set", "list", "-j", "devices")
        require(not device_entries(decode_json(raw)), "nonempty-new-device-set")

    def run(self):
        self.prepare()
        self.creation_attempted = True
        _, raw, _ = self.sim("create", "create", self.name, DEVICE_TYPE, RUNTIME)
        candidate = raw.decode("ascii").strip()
        require(UUID.fullmatch(candidate) is not None, "create-uuid")
        _, raw, _ = self.sim("created-identity", "list", "-j", "devices")
        self.device_uuid = selected_device(decode_json(raw), self.name, candidate)["udid"]
        self.receipt["device_uuid"] = self.device_uuid
        source = HERE / "FoundationProtectionControl.m"
        source_bytes = source.read_bytes()
        source_hash = hashlib.sha256(source_bytes).hexdigest()
        require(sha256(HERE / "manifest.json") == self.receipt["approved_manifest_sha256"], "manifest-changed-before-compile")
        require(source_hash == decode_json((HERE / "manifest.json").read_bytes())["files"][source.name], "source-changed-before-compile")
        fixture, temporary = self.scratch / "fixture", self.scratch / "tmp"
        dev, inode, _ = fingerprint(fixture)
        temp_dev, temp_inode, _ = fingerprint(temporary)
        macros = dict(CONTROL_FIXTURE_ROOT=str(fixture), CONTROL_RUN_TOKEN=self.token,
                      CONTROL_DEVICE_UDID=self.device_uuid, CONTROL_SOURCE_SHA256=source_hash,
                      CONTROL_ROOT_DEVICE=dev, CONTROL_ROOT_INODE=inode, CONTROL_TEMP_ROOT=str(temporary),
                      CONTROL_TEMP_DEVICE=temp_dev, CONTROL_TEMP_INODE=temp_inode)
        header = "".join("#define " + key + " " + (json.dumps(value) if isinstance(value, str) else str(value)) + "\n"
                         for key, value in macros.items())
        with (self.scratch / "OwnedControlConfig.h").open("x") as stream:
            stream.write(header)
        with (self.scratch / "FoundationProtectionControl.m").open("xb") as stream:
            stream.write(source_bytes)
        image = self.scratch / "FoundationProtectionControl"
        self.compilation_attempted = True
        try:
            self.command("compile", [CLANG, "-fobjc-arc", "-fmodules", "-fmodules-cache-path=" + str(self.scratch / "cache"),
                "-target", "arm64-apple-ios16.0-simulator", "-isysroot", SDK, "-framework", "Foundation",
                "-Wall", "-Wextra", "-Werror", self.scratch / "FoundationProtectionControl.m", "-o", image], timeout=90)
        finally:
            self.stop_gradle("stop-after-compile")
        self.receipt["retained_until_native_inspection"] = "Disposable native image/config/cache inside attested scratch only"
        self.command("adhoc-fixture-sign", ["/usr/bin/codesign", "--force", "--sign", "-", "--timestamp=none", image])
        self.command("fixture-signature-check", ["/usr/bin/codesign", "--verify", "--strict", image])
        _, raw, _ = self.command("image-uuid", [DWARF, "--uuid", image])
        match = re.fullmatch(rb"UUID: ([0-9A-Fa-f-]{36}) \(arm64\) .+\n?", raw)
        require(match is not None and UUID.fullmatch(match.group(1).decode("ascii")) is not None, "single-arm64-image")
        image_uuid = match.group(1).decode("ascii").lower()
        self.receipt["native_image"] = dict(sha256=sha256(image), uuid=image_uuid,
                                            source_sha256=source_hash, config_sha256=hashlib.sha256(header.encode()).hexdigest())
        require(sha256(self.scratch / source.name) == source_hash and
                sha256(self.scratch / "OwnedControlConfig.h") == self.receipt["native_image"]["config_sha256"],
                "compiled-input-changed")
        self.sim("boot", "boot", self.device_uuid, timeout=60)
        self.sim("boot-status", "bootstatus", self.device_uuid, "-b", timeout=150)
        _, raw, _ = self.sim("booted-identity", "list", "-j", "devices")
        require(selected_device(decode_json(raw), self.name, self.device_uuid)["state"] == "Booted", "not-booted")
        # No fabricated SIMULATOR_UDID; simctl/runtime must supply it truthfully.
        _, raw, _ = self.sim("native-observations", "spawn", self.device_uuid, str(image), timeout=40,
            extra_env={"SIMCTL_CHILD_PARLOR_CONTROL_TOKEN": self.token,
                       "SIMCTL_CHILD_TMPDIR": str(temporary) + "/"})
        observations = validate_observations(raw, self.token, source_hash, image_uuid)
        write_json(self.dest / "observations.json", observations)
        self.receipt["status"] = "COLLECTION_VALIDATED_NOT_L08_PASS"

    def cleanup(self):
        # Each independent cleanup action runs even if another action failed.
        errors = self.receipt["cleanup_errors"]
        if self.compilation_attempted:
            try:
                self.stop_gradle("stop-final")
            except BaseException as error:
                errors.append(dict(stage="gradle-stop", error=str(error) if isinstance(error, BoundaryError) else type(error).__name__))
        set_empty = not self.creation_attempted
        if self.creation_attempted:
            try:
                _, raw, _ = self.sim("cleanup-device-identity", "list", "-j", "devices")
                data = decode_json(raw)
                if not device_entries(data):
                    set_empty = True
                else:
                    item = selected_device(data, self.name, self.device_uuid)
                    self.device_uuid = item["udid"]  # Recover only exact token/type/runtime in private set.
                    if item["state"] != "Shutdown":
                        self.sim("shutdown", "shutdown", self.device_uuid, timeout=60)
                    _, raw, _ = self.sim("shutdown-identity", "list", "-j", "devices")
                    require(selected_device(decode_json(raw), self.name, self.device_uuid)["state"] == "Shutdown", "shutdown-incomplete")
                    self.sim("delete", "delete", self.device_uuid, timeout=60)
                    _, raw, _ = self.sim("deleted-identity", "list", "-j", "devices")
                    require(not device_entries(decode_json(raw)), "private-set-not-empty")
                    set_empty = True
            except BaseException as error:
                errors.append(dict(stage="simulator-cleanup", error=str(error) if isinstance(error, BoundaryError) else type(error).__name__))
        if self.scratch is not None:
            try:
                require(set_empty, "owned-device-still-present")
                require(fingerprint(self.scratch) == self.scratch_identity, "cleanup-scratch-changed")
                code, output, stderr = self.command("scratch-holders", ["/usr/sbin/lsof", "-nP", "-t", "+D", self.scratch],
                                                    timeout=45, accepted=(0, 1))
                require(code == 1 and not output.strip() and not stderr.strip(), "scratch-holders-or-query-error")
                remove_owned_tree(self.scratch, self.scratch_identity)
                self.receipt["scratch_removed"] = True
            except BaseException as error:
                errors.append(dict(stage="owned-output-cleanup", error=str(error) if isinstance(error, BoundaryError) else type(error).__name__))
                self.receipt["scratch_removed"] = False
        else:
            self.receipt["scratch_removed"] = True
        if "context_source_hashes" in self.receipt:
            actual = {relative: sha256(ROOT / relative) for relative in self.receipt["context_source_hashes"]}
            self.receipt["context_source_hashes_after"] = actual
            if actual != self.receipt["context_source_hashes"]:
                errors.append(dict(stage="source-preservation", error="context-source-changed-during-control"))
        self.receipt["cleanup_status"] = "PASS" if not errors and self.receipt["scratch_removed"] else "FAIL"
        self.receipt["finished_at"] = now()


def verify_approval(review_path, approved_hash):
    require(HASH.fullmatch(approved_hash) is not None, "approval-hash-format")
    manifest_path = HERE / "manifest.json"
    require(sha256(manifest_path) == approved_hash, "manifest-approval-hash")
    manifest = decode_json(manifest_path.read_bytes())
    require(manifest.get("status") == "DRAFT_NOT_EXECUTED" and isinstance(manifest.get("files"), dict), "manifest-shape")
    require(set(manifest["files"]) == {"FoundationProtectionControl.m", "run_control.py", "test_control.py", "README.md",
                                     "public-tool-evidence.json"}, "manifest-files")
    for name, digest in manifest["files"].items():
        path = HERE / name
        require(not path.is_symlink() and path.is_file() and sha256(path) == digest, "draft-source-changed")
    require(review_path.resolve().is_relative_to(CAMPAIGN / "reviews") and not review_path.is_symlink(), "review-path")
    review = decode_json(review_path.read_bytes())
    require(review.get("manifest_sha256") == approved_hash and review.get("decision") == "APPROVED_FOR_ISOLATED_CONTROL" and
            isinstance(review.get("reviewer"), str) and review["reviewer"].startswith("/root") and
            review["reviewer"] != "/root/native_fix_review", "independent-approval")
    return approved_hash, sha256(review_path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--cycle", required=True)
    parser.add_argument("--jdk-home", type=Path, required=True)
    parser.add_argument("--independent-review", type=Path, required=True)
    parser.add_argument("--approved-manifest-sha256", required=True)
    args = parser.parse_args()
    require(ROOT == Path("/Users/abdelrahman/Projects/parlor"), "repository-root")
    require(args.execute, "explicit-execution-required")
    require(re.fullmatch(r"l08-foundation-control-[0-9]{2}", args.cycle) is not None, "cycle-name")
    require(args.jdk_home.is_absolute() and (args.jdk_home / "bin/java").is_file(), "jdk-home")
    approved_hash, review_hash = verify_approval(args.independent_review, args.approved_manifest_sha256)
    lock_path = CAMPAIGN / "build-lane.lock"
    lock = os.open(lock_path, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        info = os.fstat(lock)
        require(stat.S_ISREG(info.st_mode) and info.st_uid == os.getuid() and info.st_nlink == 1, "lane-lock-owner")
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        dest = CAMPAIGN / "evidence" / args.cycle
        dest.mkdir(mode=0o700)  # No parents=True, exist_ok, or old-cycle overwrite.
        lane = Lane(dest, args.jdk_home)
        lane.receipt.update(approved_manifest_sha256=approved_hash, independent_review_sha256=review_hash)

        def interrupted(_number, _frame):
            raise BoundaryError("runner-interrupted")

        signal.signal(signal.SIGINT, interrupted)
        signal.signal(signal.SIGTERM, interrupted)
        try:
            lane.run()
        except BaseException as error:
            lane.receipt.update(status="CONTROL_FAILED", error=str(error) if isinstance(error, BoundaryError) else type(error).__name__)
        finally:
            signal.signal(signal.SIGINT, signal.SIG_IGN)
            signal.signal(signal.SIGTERM, signal.SIG_IGN)
            try:
                lane.cleanup()
            except BaseException as error:
                lane.receipt.update(cleanup_status="FAIL", finalizer_error=type(error).__name__)
            write_json(dest / "receipt.json", lane.receipt)
        print(json.dumps({key: lane.receipt.get(key) for key in ("status", "cleanup_status", "l08_status")}, sort_keys=True))
        return 0 if lane.receipt["status"] == "COLLECTION_VALIDATED_NOT_L08_PASS" and lane.receipt["cleanup_status"] == "PASS" else 1
    finally:
        os.close(lock)


if __name__ == "__main__":
    raise SystemExit(main())
