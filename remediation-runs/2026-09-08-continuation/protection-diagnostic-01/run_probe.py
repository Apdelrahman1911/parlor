#!/usr/bin/env python3
"""One approved synthetic native observation, not an app/protection qualification.

No work on import. Root alone dispatches. Existing bounded direct-child and
journal-owned simulator utilities retain their original ownership rules.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import platform
import re
import shutil
import signal
import stat
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.ci import native_continuation as native
from scripts.ci import owned_ci_simulator as simulator
from scripts.ci.native_process_probe import Commands

HERE = "remediation-runs/2026-09-08-continuation/protection-diagnostic-01"
SCOPE = "ios-protection-probe"
DEVELOPER = "/Applications/Xcode_26.3.app/Contents/Developer"
RUNTIME = "com.apple.CoreSimulator.SimRuntime.iOS-26-2"
DEVICE = "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro"
READS = ("fm", "url", "fcntl", "attrlist", "filesystem")
EVENTS = ("directory-create", "directory-baseline", "complete-write", "complete-baseline",
          "none-write", "none-baseline", "default-write", "default-baseline", "complete-replace",
          "complete-after-replace", "none-kernel-set", "none-after-kernel-set", "none-url-set", "none-after-url-set")
STRICT = ("directory-baseline", "complete-baseline", "complete-after-replace", "none-after-url-set")
CONTROL_PATHS = tuple(HERE + "/" + name for name in
    ("run_probe.py", "ProtectionSampler.h", "ProtectionSampler.m", "ProbeMain.m", "test_probe.py", "README.md")) + (
    ".github/workflows/production-verification.yml", "scripts/ci/native_process_probe.py",
    "scripts/ci/native_continuation.py", "scripts/ci/owned_ci_simulator.py", "scripts/ci/verification_hygiene.py",
    "composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSnapshotFileSystem.kt",
    "gradlew", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties")
require = native.require


def failure(error):
    code = str(error) if type(error) is RuntimeError and re.fullmatch(r"[a-z][a-z0-9-]{1,96}", str(error)) else "external-error"
    return dict(type=type(error).__name__, code=code)


def owned_directory(path):
    value = native.custody(path)
    info = path.lstat()
    require(stat.S_ISDIR(info.st_mode) and stat.S_IMODE(info.st_mode) == 0o700, "private-directory-custody")
    return value


def controls(root=ROOT):
    files = [dict(path=name, sha256=native.sha(native.file_bytes(root / name))) for name in sorted(CONTROL_PATHS)]
    return dict(files=files, control_sha256=native.sha(json.dumps(files, separators=(",", ":")).encode()))


def child_environment(env):
    # No tokens, loader overrides, Gradle options, PYTHONPATH, or manufactured
    # SIMULATOR_UDID. JDK21 is supplied by the workflow solely for required stops.
    home, java = Path(env["HOME"]), Path(env["JAVA_HOME"])
    require(home.is_absolute() and home.resolve() == home and java.is_absolute() and java.is_dir(), "host-environment")
    require(env.get("DEVELOPER_DIR") == DEVELOPER, "qualified-developer-required")
    return dict(HOME=str(home), JAVA_HOME=str(java), DEVELOPER_DIR=DEVELOPER,
        PATH=str(java / "bin") + ":/usr/bin:/bin:/usr/sbin:/sbin", LC_ALL="C", LANG="en_US.UTF-8",
        PYTHONDONTWRITEBYTECODE="1", GIT_OPTIONAL_LOCKS="0", GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL="/dev/null")


def context(env, execute, root=ROOT):
    require(env.get("GITHUB_ACTIONS") == "true" and env.get("GITHUB_EVENT_NAME") == "workflow_dispatch" and
        env.get("GITHUB_JOB") == SCOPE and env.get("PARLOR_DISPATCH_SCOPE") == SCOPE, "exact-probe-dispatch-required")
    require(env.get("GITHUB_REPOSITORY") == native.REPOSITORY and env.get("GITHUB_REF") == "refs/heads/" + native.BRANCH and
        env.get("GITHUB_WORKFLOW_REF") == native.REPOSITORY + "/" + native.WORKFLOW + "@refs/heads/" + native.BRANCH,
        "repository-branch-workflow")
    commit = env.get("PARLOR_FROZEN_SOURCE_SHA", "")
    require(native.HEX40.fullmatch(commit) and commit == env.get("GITHUB_SHA") == env.get("GITHUB_WORKFLOW_SHA"), "frozen-source")
    require(all(native.POSITIVE.fullmatch(env.get(key, "")) for key in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT")), "run-identity")
    require(root.resolve() == root and Path(env.get("GITHUB_WORKSPACE", "")).resolve() == root, "checkout-root")
    git = lambda *args: execute(["/usr/bin/git", *args], "git-binding", 20).decode().strip()
    require(git("rev-parse", "--show-toplevel") == str(root) and git("rev-parse", "HEAD") == commit and
        git("branch", "--show-current") == native.BRANCH and git("rev-parse", "--is-shallow-repository") == "false" and
        git("status", "--porcelain=v1", "--untracked-files=no") == "", "clean-full-history-frozen-branch")
    tree = git("rev-parse", "HEAD^{tree}")
    require(native.HEX40.fullmatch(tree), "source-tree")
    return dict(repository=native.REPOSITORY, branch=native.BRANCH, workflow=native.WORKFLOW, job=SCOPE,
        root=str(root), source_sha=commit, tree=tree, run_id=int(env["GITHUB_RUN_ID"]), run_attempt=int(env["GITHUB_RUN_ATTEMPT"]))


def upload_binding(env, cleanup=False):
    prefix = "PARLOR_PROTECTION_" + ("CLEANUP_" if cleanup else "")
    identifier, digest = env.get(prefix + "ARTIFACT_ID", ""), env.get(prefix + "ARTIFACT_DIGEST", "")
    require(env.get(prefix + "UPLOAD_OUTCOME") == "success" and native.POSITIVE.fullmatch(identifier) and
        native.HEX64.fullmatch(digest), "successful-immutable-upload-required")
    return dict(artifact_id=int(identifier), artifact_digest=digest, outcome="success")


def method(value):
    require(isinstance(value, dict) and set(value) == {"receiver_class", "selector", "implementation"} and
        all(isinstance(value[k], str) and 0 < len(value[k]) <= 256 for k in ("receiver_class", "selector")), "method-shape")
    image_identity(value["implementation"])


def image_identity(value):
    require(isinstance(value, dict) and
        isinstance(value.get("uuid"), str) and simulator.UUID.fullmatch(value["uuid"]) and
        value.get("platforms") == [7] and value.get("cputype") == 0x100000C and
        type(value.get("image_offset")) is int and 0 < value["image_offset"] < 2**32 and
        isinstance(value.get("image_basename"), str) and 0 < len(value["image_basename"]) <= 256, "simulator-image-identity")


def error_record(value):
    require(isinstance(value, dict) and set(value) == {"present", "code", "domain"} and
        type(value["present"]) is bool and type(value["code"]) is int and
        (value["domain"] in {"cocoa", "posix", "other"} if value["present"] else value["domain"] == "none" and value["code"] == 0),
        "native-error-shape")


def validate_report(raw, request, binary_uuid):
    require(isinstance(raw, bytes) and 0 < len(raw) <= 1024 * 1024, "native-output-bounds")
    rows = [native.decode(line) for line in raw.splitlines()]
    require(len(rows) == len(EVENTS) + 1 and all(isinstance(row, dict) for row in rows), "complete-native-event-count")
    require([row.get("id") for row in rows[:-1]] == list(EVENTS), "native-event-order")
    final, samples, operations = rows[-1], {}, {}
    require(final.get("kind") == "final" and final.get("schema") == 1 and final.get("collection_status") == "PASS" and
        final.get("scope") == "synthetic-native-metadata-only" and final.get("production_snapshots_observed") is False and
        final.get("historical_a37_strict_result_changed") is False and final.get("runtime_version") == [26, 2, 0] and
        final.get("context") == request["native_context"], "native-context-scope-or-collection")
    image_identity(final["main_image_before"])
    require(final["main_image_before"] == final.get("main_image_after") and
        final["main_image_before"]["uuid"] == binary_uuid, "built-runtime-image-binding")
    implementations = {}
    for row in rows[:-1]:
        if row.get("kind") == "sample":
            value = row["sample"]
            require(value.get("schema") == 1 and value.get("collection_status") == "PASS" and
                value.get("descriptor_closed") is True and len(value.get("reads", [])) == 5, "native-sample-collection")
            identity = value["identity"]
            require(set(identity) == {"device", "inode", "uid", "mode", "links", "size", "type"} and
                all(type(identity[k]) is int and identity[k] >= 0 for k in identity if k != "type") and
                identity["device"] == request["fixtures"]["device"] and identity["uid"] == request["fixtures"]["uid"] and
                identity["inode"] > 0 and identity["links"] >= 1 and identity["size"] <= 16 * 1024 * 1024,
                "sample-inode-shape-or-volume")
            is_directory = row["id"] == "directory-baseline"
            require(identity["type"] == ("directory" if is_directory else "regular") and
                (stat.S_ISDIR(identity["mode"]) if is_directory else stat.S_ISREG(identity["mode"]) and identity["links"] == 1),
                "sample-file-type")
            for kind, witness in zip(READS, value["reads"]):
                require(witness == dict(kind=kind, before=identity, after=identity, descriptor_and_path_same_inode=True), "same-inode-read-witness")
            for kind in ("fm", "url"):
                query = value[kind]
                error_record(query.get("native_error"))
                require(type(query.get("dictionary_present")) is bool and type(query.get("key_present")) is bool and
                    query.get("protection") in {"missing", "null", "non-string", "complete", "none", "unless-open",
                        "until-first-authentication", "when-user-inactive", "other-string", "NOT_APPLICABLE_DIRECTORY"} and
                    type(query.get("native_error", {}).get("present")) is bool, "foundation-query-shape")
                require(query["dictionary_present"] or not query["key_present"], "nil-dictionary-cannot-contain-key")
                require(query["key_present"] is (query["protection"] not in {"missing", "NOT_APPLICABLE_DIRECTORY"}), "key-presence-value")
                require(query["implementation_before"] == query["implementation_after"], "getter-method-changed")
                method(query["implementation_before"])
                require(query["implementation_before"]["selector"] == ("attributesOfItemAtPath:error:" if kind == "fm" else
                    "resourceValuesForKeys:error:"), "actual-getter-selector")
                key = (query["implementation_before"]["receiver_class"], query["implementation_before"]["selector"])
                require(key not in implementations or implementations[key] == query["implementation_before"], "method-changed-between-stages")
                implementations[key] = query["implementation_before"]
            require(value["url"].get("fresh_url") is True and value["url"].get("is_directory") is is_directory and
                value["url"].get("volume_support") in {"supported", "unsupported", "missing", "non-number"} and
                (not is_directory or value["url"]["protection"] == "NOT_APPLICABLE_DIRECTORY"), "fresh-regular-url")
            for kind in ("fcntl", "attrlist"):
                query = value[kind]
                require(type(query.get("sdk_available")) is bool, "public-sdk-availability")
                if not query["sdk_available"]:
                    require(query == dict(sdk_available=False, status="PUBLIC_SDK_SYMBOL_UNAVAILABLE"), "unavailable-api-not-success")
                    continue
                result, code = query.get("return_value"), query.get("errno")
                require(type(result) is int and result >= -1 and type(code) is int and
                    (code > 0 if result == -1 else code == 0), "native-return-errno")
                if kind == "fcntl":
                    require(type(query.get("command")) is int and query["command"] > 0 and
                        (query.get("class") is None if result == -1 else type(query.get("class")) is int) and
                        query.get("class") == (None if result == -1 else result), "fcntl-return-is-class")
                else:
                    present = query.get("protection_returned")
                    returned, requested = query.get("returned_common_mask"), query.get("requested_common_mask")
                    protection_mask, returned_mask = query.get("data_protection_mask"), query.get("returned_attributes_mask")
                    require(result in {-1, 0} and all(type(v) is int and 0 <= v < 2**32 for v in
                        (returned, requested, protection_mask, returned_mask)) and protection_mask > 0 and returned_mask > 0 and
                        protection_mask & returned_mask == 0 and requested == protection_mask | returned_mask and
                        not returned & ~requested and present is (result == 0 and bool(returned & protection_mask)), "returned-attribute-mask")
                    require(query.get("attribute_set_bytes") == 20 and type(query.get("attribute_set_bytes")) is int and
                        type(present) is bool and (not present or result == 0 and type(query.get("class")) is int and query["class"] >= 0) and
                        (present or query.get("class") is None) and (result != 0 or query.get("length") == (28 if present else 24)),
                        "returned-attribute-not-default-zero")
            filesystem = value["filesystem"]
            require(type(filesystem.get("return_value")) is int and filesystem["return_value"] in {-1, 0} and type(filesystem.get("errno")) is int and
                (filesystem["errno"] > 0 if filesystem["return_value"] == -1 else filesystem["errno"] == 0), "filesystem-return-errno")
            if filesystem["return_value"] == 0:
                require(isinstance(filesystem.get("type"), str) and 0 < len(filesystem["type"]) <= 32 and
                    isinstance(filesystem.get("fsid"), list) and len(filesystem["fsid"]) == 2 and
                    all(type(v) is int for v in filesystem["fsid"]) and type(filesystem.get("flags")) is int and filesystem["flags"] >= 0,
                    "filesystem-identity")
                flag = filesystem.get("content_protection_flag")
                require(filesystem.get("content_protection_capability") == "PUBLIC_SDK_SYMBOL_UNAVAILABLE" if flag is None else
                    type(flag) is int and flag > 0 and filesystem.get("content_protection_capability") ==
                    ("supported" if filesystem["flags"] & flag else "unsupported"), "filesystem-capability")
            samples[row["id"]] = value
        else:
            require(row.get("kind") == "operation", "native-event-kind")
            if row["id"] != "none-kernel-set":
                require(type(row.get("returned")) is bool, "operation-return-type")
                error_record(row.get("native_error"))
                require(row.get("implementation_before") == row.get("implementation_after"), "operation-method-changed")
                method(row["implementation_before"])
                expected = "createDirectoryAtPath:withIntermediateDirectories:attributes:error:" if row["id"] == "directory-create" else (
                    "setResourceValue:forKey:error:" if row["id"] == "none-url-set" else "writeToFile:options:error:")
                require(row["implementation_before"]["selector"] == expected, "actual-operation-selector")
                if row["id"] in {"directory-create", "none-url-set"}:
                    require(row.get("requested_protection") == "complete", "explicit-complete-request")
                if row["id"] != "none-url-set":
                    require(row.get("returned") is True and row["native_error"]["present"] is False, "required-fixture-creation")
            else:
                require(type(row.get("sdk_available")) is bool and (row["sdk_available"] or row == dict(kind="operation", id="none-kernel-set",
                    sdk_available=False, status="PUBLIC_SDK_COMMAND_OR_CLASS_UNAVAILABLE")), "kernel-set-availability")
                if row["sdk_available"]:
                    require(type(row.get("command")) is int and row["command"] > 0 and type(row.get("sdk_class")) is int and
                        row["sdk_class"] >= 0 and type(row.get("return_value")) is int and row["return_value"] in {-1, 0} and type(row.get("errno")) is int and
                        (row["errno"] > 0 if row["return_value"] == -1 else row["errno"] == 0), "kernel-set-observation")
            operations[row["id"]] = row
    options = final["sdk_options"]
    require(set(options) == {"atomic", "complete", "none"} and all(type(v) is int and v > 0 for v in options.values()) and
        len(set(options.values())) == 3 and not options["atomic"] & options["complete"] and not options["atomic"] & options["none"], "sdk-write-options")
    for identifier, flag in (("complete-write", "complete"), ("complete-replace", "complete"), ("none-write", "none"), ("default-write", None)):
        require(operations[identifier]["requested_options"] == options["atomic"] | (options[flag] if flag else 0) and
            operations[identifier].get("observer_descriptor_held_across_write") is False, "exact-atomic-write-request")
    for name in ("complete", "none", "default"):
        require(samples[name + "-baseline"]["identity"]["size"] == operations[name + "-write"]["payload_bytes"], "baseline-size")
    require(len({samples[name + "-baseline"]["identity"]["inode"] for name in ("complete", "none", "default")}) == 3, "distinct-control-files")
    original, replaced = samples["complete-baseline"]["identity"], samples["complete-after-replace"]["identity"]
    require(replaced["size"] == operations["complete-replace"]["payload_bytes"] != original["size"] and
        final["replacement"] == dict(before=original, after=replaced, observer_descriptor_held=False,
            named_inode_changed=original["inode"] != replaced["inode"]), "atomic-transition-not-same-inode-assumption")
    require(samples["none-baseline"]["identity"] == samples["none-after-kernel-set"]["identity"] ==
        samples["none-after-url-set"]["identity"], "negative-control-inode-changed")
    passed = sum(samples[name]["fm"]["dictionary_present"] and samples[name]["fm"]["key_present"] and
        samples[name]["fm"]["protection"] == "complete" and not samples[name]["fm"]["native_error"]["present"] for name in STRICT)
    strict = dict(required_sample_ids=list(STRICT), pass_count=passed, fail_count=len(STRICT) - passed,
                  status="PASS" if passed == len(STRICT) else "FAIL")
    require(final["strict_synthetic_complete"] == dict(required_sample_ids=list(STRICT), **{"pass": passed, "fail": len(STRICT)-passed},
        status=strict["status"]), "strict-failures-must-not-be-waived")
    return dict(collection_status="CAPTURED_SYNTHETIC_METADATA_NOT_APP_QUALIFICATION", strict_synthetic_complete=strict,
        events=rows, historical_a37_strict_result_changed=False)


class Backend:
    def __init__(self, probe):
        self.probe = probe

    def run(self, *arguments):
        operation = arguments[0]
        allowed = {"list": 45, "create": 90, "boot": 90, "bootstatus": 180, "shutdown": 90, "delete": 90}
        require(operation in allowed, "closed-simulator-lifecycle-command")
        require(arguments == ("list", "devices", "--json") if operation == "list" else
            arguments == ("create", "Parlor-ci-" + self.probe.request["claim"]["nonce"], DEVICE, RUNTIME) if operation == "create" else
            len(arguments) == (3 if operation == "bootstatus" else 2) and simulator.UUID.fullmatch(arguments[1]) and
            (operation != "bootstatus" or arguments[2] == "-b"), "closed-simulator-arguments")
        row, out, _ = self.probe.capture(["/usr/bin/xcrun", "simctl", *arguments], "simctl-" + operation, allowed[operation], retain=False)
        require(row["status"] == "EXITED" and row.get("direct_child_reaped"), "simulator-command-not-completed")
        return row["exit_code"], out.decode()

    def inventory(self):
        code, raw = self.run("list", "devices", "--json")
        require(code == 0, "simulator-inventory")
        return native.decode(raw)


class Probe:
    def __init__(self, env, mode):
        self.env, self.mode, self.state = env, mode, None
        self.approved = env.get("PARLOR_APPROVED_PROBE_CONTROL_SHA256", "")
        self.control = controls()
        require(native.HEX64.fullmatch(self.approved) and self.approved == self.control["control_sha256"], "approved-control-mismatch")
        require(platform.system() == "Darwin" and platform.machine() == "arm64" and os.getuid() > 0 and os.getuid() == os.geteuid(), "native-nonroot-arm64-macos-required")
        self.commands = Commands(child_environment(env), time.monotonic() + (480 if mode == "run" else 300), self.save)
        self.source = context(env, self.execute)
        temporary = Path(env["RUNNER_TEMP"])
        require(temporary.is_absolute() and temporary.resolve() == temporary and temporary.is_dir() and
            temporary not in ROOT.parents and ROOT not in temporary.parents and temporary != ROOT, "separate-owned-temp")
        self.base = temporary / ("parlor-protection-probe-{}-{}".format(self.source["run_id"], self.source["run_attempt"]))
        self.evidence, self.resources, self.cleanup_dir = (self.base / name for name in ("evidence", "resources", "cleanup"))
        self.backend = Backend(self)

    def save(self):
        if self.state is not None:
            require(owned_directory(self.base) == self.request["base"] and owned_directory(self.evidence) == self.request["evidence"], "evidence-custody-changed")
            directory = self.evidence if self.mode == "run" else self.cleanup_dir
            self.state["commands"] = self.commands.rows
            self.state["preservation"] = self.commands.preservation
            self.state["signals"] = self.commands.signals
            temporary = directory / "state.writing"
            native.write_new(temporary, native.json_bytes(self.state))
            temporary.replace(directory / "state.json")
        return True

    def capture(self, arguments, label, timeout=30, retain=True):
        row, out, err = self.commands.capture(arguments, label, timeout)
        if self.state is not None and retain:
            directory = self.evidence if self.mode == "run" else self.cleanup_dir
            index = len(self.commands.rows)
            for extension, raw in (("stdout", out), ("stderr", err)):
                name = f"{index:02d}-{label}.{extension}"
                native.write_new(directory / name, raw)
                self.state["logs"].append(dict(path=name, bytes=len(raw), sha256=native.sha(raw)))
            self.save()
        return row, out, err

    def execute(self, arguments, label="command", timeout=30, retain=True):
        row, out, _ = self.capture(arguments, label, timeout, retain)
        require(row["status"] == "EXITED" and row.get("exit_code") == 0 and row.get("direct_child_reaped"), "required-command-failed")
        return out

    def bindings(self):
        require(controls() == self.control and context(self.env, self.execute) == self.source, "source-or-control-changed")

    def prepare(self):
        self.base.mkdir(mode=0o700)
        for directory in (self.evidence, self.resources, self.resources / "fixtures", self.resources / "tmp"):
            directory.mkdir(mode=0o700)
        self.request = dict(schema=1, source=self.source, controls=self.control, base=owned_directory(self.base),
            evidence=owned_directory(self.evidence), resources=owned_directory(self.resources), fixtures=owned_directory(self.resources / "fixtures"),
            claim=dict(task=SCOPE, cycle=str(self.source["run_id"]) + "-" + str(self.source["run_attempt"]), nonce=uuid.uuid4().hex),
            runtime=RUNTIME, device_type=DEVICE, developer_dir=DEVELOPER)
        native.write_new(self.evidence / "request.json", native.json_bytes(self.request))
        self.state = dict(status="RUNNING", logs=[], errors=[], started_at=native.now())
        self.commands.environment["TMPDIR"] = str(self.resources / "tmp") + "/"
        self.save()

    def platform_binding(self):
        require(self.execute(["/usr/bin/sw_vers", "-productVersion"], "macos-version").decode().startswith("15."), "qualified-macos15")
        self.execute(["/usr/bin/sw_vers", "-buildVersion"], "macos-build")
        self.execute(["/usr/bin/uname", "-r"], "host-kernel")
        require(self.execute(["/usr/bin/xcodebuild", "-version"], "xcode-version").decode() == "Xcode 26.3\nBuild version 17C529\n", "qualified-xcode")
        require(self.execute(["/usr/bin/xcode-select", "-p"], "developer-selection").decode().strip() == DEVELOPER, "selected-developer")
        require(self.execute(["/usr/bin/xcrun", "--sdk", "iphonesimulator", "--show-sdk-version"], "sdk-version").strip() == b"26.2", "qualified-sdk")
        sdk = Path(self.execute(["/usr/bin/xcrun", "--sdk", "iphonesimulator", "--show-sdk-path"], "sdk-path").decode().strip()).resolve(strict=True)
        require(Path(DEVELOPER) in sdk.parents, "sdk-outside-qualified-xcode")
        runtimes = native.decode(self.execute(["/usr/bin/xcrun", "simctl", "list", "runtimes", "--json"], "runtimes", 90, False))["runtimes"]
        selected = [row for row in runtimes if row.get("identifier") == RUNTIME]
        require(len(selected) == 1 and selected[0].get("isAvailable") is True and selected[0].get("version") == "26.2" and
            selected[0].get("buildversion") == "23C54", "actual-qualified-runtime")
        self.state["platform"] = dict(xcode="26.3", build="17C529", sdk="26.2", sdk_path=str(sdk), runtime=selected[0], architecture="arm64")
        headers = []
        for relative in ("usr/include/sys/fcntl.h", "usr/include/sys/attr.h", "usr/include/sys/mount.h",
            "System/Library/Frameworks/Foundation.framework/Headers/NSData.h",
            "System/Library/Frameworks/Foundation.framework/Headers/NSFileManager.h",
            "System/Library/Frameworks/Foundation.framework/Headers/NSURL.h"):
            path = (sdk / relative).resolve(strict=True)
            require(Path(DEVELOPER) in path.parents and path.is_file() and 0 < path.stat().st_size <= 1024 * 1024, "public-sdk-header")
            raw = path.read_bytes()
            symbols = ("F_GETPROTECTIONCLASS", "F_SETPROTECTIONCLASS", "PROTECTION_CLASS_A", "fgetattrlist(", "attribute_set_t",
                "ATTR_CMN_RETURNED_ATTRS", "ATTR_CMN_DATA_PROTECT_FLAGS", "MNT_CPROTECT", "NSDataWritingAtomic", "NSDataWritingFileProtection",
                "NSFileProtectionKey", "NSFileProtectionComplete", "NSURLFileProtection", "NSURLVolumeSupportsFileProtectionKey")
            declarations = [dict(line=i+1, text=line) for i, line in enumerate(raw.decode().splitlines())
                            if any(symbol in line for symbol in symbols)]
            require(len(declarations) <= 64 and all(len(row["text"]) <= 2048 for row in declarations), "bounded-public-declarations")
            headers.append(dict(path=str(path), bytes=len(raw), sha256=native.sha(raw), declarations=declarations))
        self.state["sdk_headers"] = headers
        row, out, err = self.capture([str(Path(self.commands.environment["JAVA_HOME"]) / "bin/java"), "-version"], "jdk-version")
        require(row["status"] == "EXITED" and row.get("exit_code") == 0 and row.get("direct_child_reaped") and
            re.search(rb'(?:openjdk|java) version "21[."]', out + err), "jdk21-required-for-stops")
        return sdk

    def create_simulator(self):
        self.bindings()
        prefix = self.evidence / "owned"
        plan_path, result_path = simulator.paths(prefix)
        require(not any(p.exists() or p.is_symlink() for p in (*simulator.paths(prefix), simulator.adoption_path(prefix))), "fresh-simulator-journal")
        baseline = simulator.devices(self.backend.inventory())
        name = "Parlor-ci-" + self.request["claim"]["nonce"]
        require(not any(name in str(row.get("name", "")) for row in baseline), "fresh-simulator-name")
        # Same existing journal schema and cleanup authority; select the reviewed
        # runtime/type explicitly instead of the generic helper's first iPhone.
        plan = dict(**self.request["claim"], name=name, runtime=RUNTIME, device_type=DEVICE,
                    baseline=sorted(row["udid"] for row in baseline), planned_at=native.now())
        simulator.write_new(plan_path, plan)
        code, out = self.backend.run("create", name, DEVICE, RUNTIME)
        identity = simulator.checked_uuid(out.strip()) if code == 0 and simulator.UUID.fullmatch(out.strip()) else None
        outcome = dict(task=SCOPE, nonce=plan["nonce"], exit_code=code, udid=identity, finished_at=native.now())
        simulator.write_new(result_path, outcome)
        require(code == 0 and identity and simulator.owned_device(plan, outcome, simulator.devices(self.backend.inventory())), "fresh-simulator-create")
        self.state["owned_uuid"] = identity
        return identity

    def compile_and_run(self, sdk, identity):
        self.bindings()
        native_context = dict(source_sha=self.source["source_sha"], control_sha256=self.approved, nonce=self.request["claim"]["nonce"],
            simulator_udid=identity, fixture_root_device=self.request["fixtures"]["device"], fixture_root_inode=self.request["fixtures"]["inode"])
        self.request["native_context"] = native_context
        native.write_new(self.evidence / "native-context.json", native.json_bytes(native_context))
        macros = dict(PROBE_ROOT=str(self.resources / "fixtures"), PROBE_SOURCE=self.source["source_sha"], PROBE_CONTROL=self.approved,
            PROBE_UDID=identity, PROBE_NONCE=self.request["claim"]["nonce"])
        header = "".join("#define " + key + " @" + json.dumps(value) + "\n" for key, value in macros.items())
        header += "#define PROBE_ROOT_DEVICE {}ULL\n#define PROBE_ROOT_INODE {}ULL\n".format(
            native_context["fixture_root_device"], native_context["fixture_root_inode"])
        native.write_new(self.resources / "OwnedProbeContext.h", header.encode())
        for name in ("ProtectionSampler.h", "ProtectionSampler.m", "ProbeMain.m"):
            native.write_new(self.resources / name, native.file_bytes(ROOT / HERE / name))
        compiler = ["/usr/bin/xcrun", "--sdk", "iphonesimulator", "clang", "-target", "arm64-apple-ios16.0-simulator", "-isysroot", str(sdk)]
        self.execute(compiler[:4] + ["--version"], "clang-version")
        # Preserve the actual installed SDK's relevant macro declarations, not
        # copied private XNU constants or a guess about available class values.
        raw = self.execute(compiler + ["-dM", "-E", "-x", "objective-c", "-I", str(self.resources), str(self.resources / "ProbeMain.m")],
                           "sdk-macros", 60, False)
        names = {"F_GETPROTECTIONCLASS", "F_SETPROTECTIONCLASS", "PROTECTION_CLASS_A", "ATTR_CMN_RETURNED_ATTRS",
                 "ATTR_CMN_DATA_PROTECT_FLAGS", "MNT_CPROTECT", "TARGET_OS_SIMULATOR", "TARGET_OS_IOS", "__arm64__"}
        macros_found = [line for line in raw.decode().splitlines() if len(line.split()) >= 2 and line.split()[1] in names]
        native.write_new(self.evidence / "public-sdk-macros.txt", ("\n".join(macros_found) + "\n").encode())
        image = self.resources / "ProtectionProbe"
        self.execute(compiler + ["-fobjc-arc", "-fblocks", "-fno-modules", "-O0", "-Wall", "-Wextra", "-I", str(self.resources),
            str(self.resources / "ProbeMain.m"), str(self.resources / "ProtectionSampler.m"), "-framework", "Foundation", "-o", str(image)], "compile", 90)
        self.execute(["/usr/bin/codesign", "--force", "--sign", "-", "--timestamp=none", str(image)], "adhoc-sign", 30)
        self.execute(["/usr/bin/codesign", "--verify", "--strict", str(image)], "adhoc-verify", 30)
        raw = self.execute(["/usr/bin/xcrun", "dwarfdump", "--uuid", str(image)], "built-uuid")
        match = re.fullmatch(rb"UUID: ([0-9A-Fa-f-]{36}) \(arm64\) " + re.escape(str(image).encode()) + rb"\n", raw)
        require(match is not None, "built-arm64-uuid")
        binary_uuid = simulator.checked_uuid(match.group(1).decode()).lower()
        image_hash = native.sha(native.file_bytes(image, maximum=16 * 1024 * 1024))
        self.state["built_image"] = dict(uuid=binary_uuid, sha256=image_hash, scope="standalone-O0-ad-hoc-simulator-not-Parlor-Debug")
        for operation, extra in (("boot", ()), ("bootstatus", ("-b",))):
            code, _ = self.backend.run(operation, identity, *extra)
            require(code == 0, "owned-simulator-boot")
        self.bindings()
        child = dict(self.commands.environment, SIMCTL_CHILD_TMPDIR=str(self.resources / "tmp") + "/")
        row, out, err = self.commands.capture(["/usr/bin/xcrun", "simctl", "spawn", identity, str(image)], "native-observation", 40, environment=child)
        native.write_new(self.evidence / "native.stdout.jsonl", out)
        native.write_new(self.evidence / "native.stderr.txt", err)
        require(row["status"] == "EXITED" and row.get("exit_code") == 0 and row.get("direct_child_reaped"), "native-collection-process")
        report = validate_report(out, self.request, binary_uuid)
        require(native.sha(native.file_bytes(image, maximum=16 * 1024 * 1024)) == image_hash, "built-image-mutated")
        native.write_new(self.evidence / "native-report.json", native.json_bytes(report))
        self.state["collection_status"] = report["collection_status"]
        self.state["strict_synthetic_complete"] = report["strict_synthetic_complete"]

    def stop(self):
        # Never run clean/configuration/build tasks: this lane made no project
        # outputs. Global Gradle caches and unrelated workers are not deleted.
        row, _, _ = self.capture([str(ROOT / "gradlew"), "--stop"], "gradle-stop", 60)
        require(row["status"] == "EXITED" and row.get("exit_code") == 0 and row.get("direct_child_reaped"), "required-gradle-stop")

    def run(self):
        self.prepare()
        for signum in (signal.SIGINT, signal.SIGTERM):
            signal.signal(signum, self.commands.interrupted)
        try:
            sdk = self.platform_binding()
            self.compile_and_run(sdk, self.create_simulator())
            self.bindings()
            self.state["status"] = "CAPTURED"
        except BaseException as error:
            self.state["status"] = "FAIL"
            self.state["errors"].append(dict(stage="collection", **failure(error)))
        finally:
            self.commands.finalizing = True
            self.commands.deadline = time.monotonic() + 90
            for child, row in self.commands.handles:
                self.commands.retire(child, row)
            try:
                self.save()  # Evidence before the immediate post-cycle stop.
            except BaseException as error:
                self.state["status"] = "FAIL"
                self.state["errors"].append(dict(stage="evidence-preservation", **failure(error)))
            try:
                self.stop()
            except BaseException as error:
                self.state["status"] = "FAIL"
                self.state["errors"].append(dict(stage="post-cycle-stop", **failure(error)))
            if self.commands.signals:
                self.state["status"] = "FAIL"
            self.state["finished_at"] = native.now()
            self.save()
        return 0 if self.state["status"] == "CAPTURED" and not self.commands.preservation["failures"] else 1

    def load_request(self):
        self.request = native.decode(native.file_bytes(self.evidence / "request.json"))
        require(self.request["source"] == self.source and self.request["controls"] == self.control and
            self.request["runtime"] == RUNTIME and self.request["device_type"] == DEVICE and self.request["developer_dir"] == DEVELOPER and
            owned_directory(self.base) == self.request["base"] and owned_directory(self.evidence) == self.request["evidence"] and
            self.request["claim"].get("task") == SCOPE and self.request["claim"].get("cycle") ==
                str(self.source["run_id"]) + "-" + str(self.source["run_attempt"]) and
            re.fullmatch(r"[0-9a-f]{32}", self.request["claim"].get("nonce", "")), "owned-request-binding")

    def cleanup(self):
        self.load_request()
        self.cleanup_dir.mkdir(mode=0o700)
        self.state = dict(status="FAIL", logs=[], errors=[], started_at=native.now(), cleanup_scope="owned-simulator-and-private-native-scratch-only")
        self.commands.finalizing = True
        for signum in (signal.SIGINT, signal.SIGTERM):
            signal.signal(signum, self.commands.interrupted)
        try:
            self.save()
            self.stop()
            self.state["evidence_upload"] = upload_binding(self.env)
            self.bindings()
            # The uploaded main bundle remains immutable. Copy its exact fsynced
            # intent/results into the cleanup bundle; new adoption/retirement
            # receipts belong to the second immutable artifact, not a lost tail.
            self.state["creation_journal_copies"] = []
            for path in (*simulator.paths(self.evidence / "owned"), simulator.adoption_path(self.evidence / "owned")):
                if path.exists() or path.is_symlink():
                    raw = native.file_bytes(path)
                    native.write_new(self.cleanup_dir / path.name, raw)
                    self.state["creation_journal_copies"].append(dict(name=path.name, bytes=len(raw), sha256=native.sha(raw)))
            self.save()
            self.state["simulator"] = simulator.cleanup(self.cleanup_dir / "owned", self.request["claim"], self.backend)
            require(self.state["simulator"]["result"] in {"PASS", "NOT_CREATED"}, "simulator-cleanup-incomplete")
            owned_uuid = self.state["simulator"].get("udid")
            if owned_uuid:
                devices_root = Path(self.commands.environment["HOME"]) / "Library/Developer/CoreSimulator/Devices"
                require(devices_root.resolve() == devices_root and devices_root.is_dir(), "device-set-custody")
                require(not (devices_root / owned_uuid).exists() and not (devices_root / owned_uuid).is_symlink(), "owned-device-directory-remains")
            prior = native.decode(native.file_bytes(self.evidence / "state.json"))
            require(not prior.get("preservation", {}).get("failures") and all(row.get("status") == "EXITED" and
                row.get("direct_child_reaped") is True and not row.get("child_cleanup_error_type") for row in prior["commands"]),
                "timed-out-or-unsettled-tool-scratch-retained")
            require(owned_directory(self.resources) == self.request["resources"] and
                owned_directory(self.resources / "fixtures") == self.request["fixtures"], "scratch-custody")
            entries = []
            for path in self.resources.rglob("*"):
                info = path.lstat()
                require(len(entries) < 128 and not path.is_symlink() and path.resolve() == path and info.st_uid == os.getuid() and
                    info.st_dev == self.request["resources"]["device"] and
                    (stat.S_ISDIR(info.st_mode) or stat.S_ISREG(info.st_mode) and info.st_nlink == 1 and info.st_size <= 16 * 1024 * 1024),
                    "unsafe-scratch-entry-retained")
                entries.append(dict(path=str(path.relative_to(self.resources)), device=info.st_dev, inode=info.st_ino,
                    uid=info.st_uid, mode=info.st_mode, bytes=info.st_size))
            self.state["removed_entries"] = entries
            self.save()
            require(shutil.rmtree.avoids_symlink_attacks, "descriptor-safe-tree-removal-required")
            shutil.rmtree(self.resources)
            require(not self.resources.exists() and not self.resources.is_symlink(), "scratch-remains")
            self.state["scratch_absent"] = True
            self.bindings()
            self.state["status"] = "PASS"
        except BaseException as error:
            self.state["errors"].append(dict(stage="cleanup", **failure(error)))
        finally:
            self.commands.deadline = max(self.commands.deadline, time.monotonic() + 65)
            try:
                self.stop()
            except BaseException as error:
                self.state["status"] = "FAIL"
                self.state["errors"].append(dict(stage="final-stop", **failure(error)))
            self.state["finished_at"] = native.now()
            self.save()
        return 0 if self.state["status"] == "PASS" and not self.commands.preservation["failures"] else 1

    def assert_result(self):
        self.load_request()
        require(self.env.get("PARLOR_PROTECTION_RUN_OUTCOME") == "success" and
            self.env.get("PARLOR_PROTECTION_CLEANUP_OUTCOME") == "success", "required-step-outcomes")
        main_upload, cleanup_upload = upload_binding(self.env), upload_binding(self.env, cleanup=True)
        require(main_upload["artifact_id"] != cleanup_upload["artifact_id"], "distinct-immutable-artifacts")
        prior = native.decode(native.file_bytes(self.evidence / "state.json"))
        cleanup = native.decode(native.file_bytes(self.cleanup_dir / "state.json"))
        require(prior["status"] == "CAPTURED" and cleanup["status"] == "PASS" and cleanup.get("scratch_absent") is True and
            cleanup.get("evidence_upload") == main_upload and not prior.get("errors") and not cleanup.get("errors") and
            not prior["preservation"]["failures"] and not cleanup["preservation"]["failures"], "captured-and-cleaned-receipts-required")
        self.request["native_context"] = native.decode(native.file_bytes(self.evidence / "native-context.json"))
        report = validate_report(native.file_bytes(self.evidence / "native.stdout.jsonl"), self.request, prior["built_image"]["uuid"])
        require(report == native.decode(native.file_bytes(self.evidence / "native-report.json")), "collected-report-changed")
        print(json.dumps(dict(collection=report["collection_status"], strict_synthetic_complete=report["strict_synthetic_complete"],
            cleanup="PASS", production_and_historical_a37_qualification="NOT_ESTABLISHED", evidence_upload=main_upload,
            cleanup_upload=cleanup_upload), sort_keys=True))
        return 0


def main(arguments=None):
    arguments = sys.argv[1:] if arguments is None else arguments
    require(arguments in (["controls"], ["run"], ["cleanup"], ["assert-result"]), "closed-probe-cli")
    if arguments == ["controls"]:
        print(json.dumps(controls(), indent=2))
        return 0
    os.umask(0o077)
    probe = Probe(dict(os.environ), arguments[0])
    return {"run": probe.run, "cleanup": probe.cleanup, "assert-result": probe.assert_result}[arguments[0]]()


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print(json.dumps(dict(status="FAIL", error=failure(error), scope=SCOPE)), file=sys.stderr)
        sys.exit(1)
