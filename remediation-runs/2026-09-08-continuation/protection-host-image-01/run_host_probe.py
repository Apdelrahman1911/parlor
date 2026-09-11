#!/usr/bin/env python3
"""Host attribution plus a separate host-origin setter; never qualification."""
from __future__ import annotations

import importlib.util
import difflib
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
HERE = Path(__file__).resolve().parent
SHARED = HERE.parent / "protection-diagnostic-01"
SPEC = importlib.util.spec_from_file_location("_host_image_reviewed_helpers", SHARED / "run_probe.py")
common = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(common)
SPEC = importlib.util.spec_from_file_location("_host_image_hook", HERE / "image_hook.py")
hook = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(hook)
native, require = common.native, common.require
SELECTION = "protection-host-only"
PROFILE = "host-macos15-arm64-xcode26.3-sdk26.2-attribution-and-host-origin-setter"
COLLECTED = "CAPTURED_HOST_IMAGE_PREDICATES_NOT_APP_QUALIFICATION"
IMAGE = "HostImageProbe"
SETTER_IMAGE = "HostSetterProbe"
SETTER_SCOPE = "HOST_ORIGIN_ONLY"
SETTER_COLLECTED = "CAPTURED_HOST_ORIGIN_SETTER_NOT_PROTECTION_PASS"
SETTER_PAYLOAD = b"Parlor public host-only setter fixture.\n"
SETTER_INPUTS = ("HostProtectionSampler.m", "HostSetterProbe.m", "OwnedHostSetterContext.h", "ProtectionSampler.h")
CONTROL_PATHS = tuple(sorted(set(common.CONTROL_PATHS) | {
    str(HERE.relative_to(ROOT) / name) for name in
    ("run_host_probe.py", "test_host_probe.py", "README.md", "HostImageProbe.m.in", "image_hook.py", "test_image_hook.py", "HostSetterProbe.m.in")
} | {"scripts/release/workflow_contract.py", "scripts/release/tests/test_workflow_contract.py", "docs/RELEASE_GATES.md"}))


def controls(root=ROOT):
    rows = [dict(path=name, sha256=native.sha(native.file_bytes(root / name))) for name in CONTROL_PATHS]
    return dict(files=rows, control_sha256=native.sha(json.dumps(rows, separators=(",", ":")).encode()))


def validate_report(raw, request, built, command, observed_platform):
    require(0 < len(raw) <= 256 * 1024, "bounded-host-report")
    record = native.decode(raw)
    summary = hook.validate(record, request["binding"])
    require(record["process_id"] == command["owned_pid"] and record["uid"] == request["uid"] and
        record["runtime_version"] == observed_platform["runtime_version"], "actual-host-process-binding")
    require(summary["main_image"]["uuid"] == built["uuid"] and
        summary["main_image"]["image_basename"] == built["image_basename"] == IMAGE and
        summary["main_image"]["platforms"] == [1], "compiled-host-image-binding")
    return summary


def setter_identity(path):
    native.custody(path)
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode) and stat.S_IMODE(info.st_mode) == 0o600 and info.st_nlink == 1 and
        info.st_size == len(SETTER_PAYLOAD), "host-setter-public-fixture")
    return common.receipts.identity(dict(device=info.st_dev, inode=info.st_ino, uid=info.st_uid, mode=info.st_mode,
        links=info.st_nlink, size=info.st_size, type="regular"))


def setter_sources(resources, binding, fixture):
    encoded = lambda value: json.dumps(json.dumps(value, separators=(",", ":"), sort_keys=True))
    header = ("#define HOST_SET_ROOT @" + json.dumps(str(resources / "host-fixtures")) + "\n" +
        "#define HOST_SET_ROOT_DEVICE {}ULL\n#define HOST_SET_ROOT_INODE {}ULL\n".format(fixture["root"]["device"], fixture["root"]["inode"]) +
        "#define HOST_SET_BINDING_JSON @" + encoded(binding) + "\n#define HOST_SET_EXPECTED_JSON @" + encoded(fixture["identity"]) + "\n").encode()
    return {"OwnedHostSetterContext.h": header, "HostSetterProbe.m": native.file_bytes(HERE / "HostSetterProbe.m.in"),
        "ProtectionSampler.h": native.file_bytes(SHARED / "ProtectionSampler.h"),
        "HostProtectionSampler.m": common.host_sampler.transform(native.file_bytes(SHARED / "ProtectionSampler.m"))}


def setter_build_commands(resources, sdk):
    image = resources / SETTER_IMAGE
    return (
        ("host-setter-compile", ["/usr/bin/xcrun", "--sdk", "macosx", "clang", "-target", "arm64-apple-macosx15.0",
            "-isysroot", str(sdk), "-fobjc-arc", "-fblocks", "-fno-modules", "-O0", "-Wall", "-Wextra", "-I", str(resources),
            str(resources / "HostSetterProbe.m"), str(resources / "HostProtectionSampler.m"), "-framework", "Foundation", "-o", str(image)], 90),
        ("host-setter-adhoc-sign", ["/usr/bin/codesign", "--force", "--sign", "-", "--timestamp=none", str(image)], 30),
        ("host-setter-adhoc-verify", ["/usr/bin/codesign", "--verify", "--strict", str(image)], 30),
        ("host-setter-built-uuid", ["/usr/bin/xcrun", "dwarfdump", "--uuid", str(image)], 30))


def setter_command_order(commands, resources, sdk):
    expected = (("host-image-observation", [str(resources / IMAGE)], 30),) + setter_build_commands(resources, sdk) + (
        ("host-setter-observation", [str(resources / SETTER_IMAGE)], 30),)
    labels = {label for label, _, _ in expected}
    selected = [row for row in commands if row.get("label") in labels]
    require(len(selected) == len(expected), "host-origin-setter-command-count")
    for row, (label, arguments, timeout) in zip(selected, expected):
        require(row.get("label") == label and row.get("command") == arguments and row.get("timeout_seconds") == timeout and
            row.get("status") == "EXITED" and row.get("exit_code") == 0 and row.get("direct_child_reaped") is True and
            row.get("ownership") == "direct-unreaped-Popen" and not row.get("child_cleanup_error_type"), "host-image-before-setter-order")
    return selected[-1]


def preceding_image(evidence, commands):
    indices = [index for index, row in enumerate(commands) if row.get("label") == "host-image-observation"]
    require(len(indices) == 1, "single-preceding-host-image-observation")
    return dict(command_index=indices[0], **{name: native.sha(native.file_bytes(evidence / name))
        for name in ("host-image.stdout.json", "host-image.stderr.txt", "host-image-summary.json")})


def validate_error_chain(value, root):
    require(isinstance(value, dict) and set(value) == {"nodes", "termination"} and
        type(value["nodes"]) is list and len(value["nodes"]) <= 4 and
        type(value["termination"]) is str and
        value["termination"] in {"no-error", "no-underlying", "non-error", "cycle", "depth-limit"},
        "host-origin-error-chain-shape")
    nodes, termination = value["nodes"], value["termination"]
    for node in nodes:
        common.error_record(node)
        require(node["present"] is True, "host-origin-error-chain-present")
    require((not nodes and termination == "no-error") if not root["present"] else
        bool(nodes) and nodes[0] == root and termination != "no-error", "host-origin-error-chain-root")
    require(termination != "depth-limit" or len(nodes) == 4, "host-origin-error-chain-depth")


def validate_setter_report(raw, request, fixture, built, command, observed_platform):
    require(isinstance(raw, bytes) and 0 < len(raw) <= 256 * 1024, "bounded-host-origin-setter-report")
    value = native.decode(raw)
    common.receipts.metadata_only(value)
    require(isinstance(fixture, dict) and set(fixture) == {"root", "identity"} and set(fixture["root"]) == {"device", "inode", "uid"} and
        all(type(item) is int and item > 0 for item in fixture["root"].values()) and
        common.receipts.identity(fixture["identity"])["mode"] == (stat.S_IFREG | 0o600) and
        fixture["identity"]["size"] == len(SETTER_PAYLOAD) and fixture["root"]["device"] == fixture["identity"]["device"] and
        fixture["root"]["uid"] == fixture["identity"]["uid"] == request["uid"], "host-origin-setter-fixture-binding")
    require(isinstance(value, dict) and set(value) == {"schema", "kind", "collection_status", "binding", "fixture_root_device", "fixture_root_inode",
        "scope", "target_leaf", "process_id", "uid", "runtime_version", "read_only", "simulator_fixture_observed",
        "production_snapshots_observed", "historical_a37_strict_result_changed", "protection_qualified", "main_image_before", "main_image_after",
        "before", "operation", "after"}, "host-origin-setter-shape")
    require(type(value["schema"]) is int and value["schema"] == 2 and value["kind"] == "host-origin-protection-setter" and
        value["collection_status"] == "PASS" and value["scope"] == SETTER_SCOPE and value["target_leaf"] == "probe.bin" and
        value["binding"] == request["binding"] and type(value["fixture_root_device"]) is int and type(value["fixture_root_inode"]) is int and
        value["fixture_root_device"] == fixture["root"]["device"] and value["fixture_root_inode"] == fixture["root"]["inode"] and
        all(value[key] is False for key in ("read_only", "simulator_fixture_observed", "production_snapshots_observed",
            "historical_a37_strict_result_changed", "protection_qualified")), "host-origin-setter-context")
    require(command.get("label") == "host-setter-observation" and command.get("status") == "EXITED" and command.get("exit_code") == 0 and
        command.get("direct_child_reaped") is True and command.get("ownership") == "direct-unreaped-Popen" and
        not command.get("child_cleanup_error_type") and type(command.get("owned_pid")) is int and 0 < command["owned_pid"] < 2**31 and
        type(value["process_id"]) is int and value["process_id"] == command["owned_pid"] and
        type(value["uid"]) is int and value["uid"] == request["uid"] > 0 and
        type(value["runtime_version"]) is list and len(value["runtime_version"]) == 3 and value["runtime_version"][0] == 15 and
        all(type(item) is int and 0 <= item < 1000 for item in value["runtime_version"]) and
        value["runtime_version"] == observed_platform["runtime_version"], "host-origin-setter-process")
    common.receipts.image(value["main_image_before"], 1)
    require(value["main_image_before"] == value["main_image_after"] and value["main_image_before"]["uuid"] == built["uuid"] and
        value["main_image_before"]["image_basename"] == built["image_basename"] == SETTER_IMAGE, "host-origin-setter-main-image")
    for stage in ("before", "after"):
        require(common.receipts.validate_native(value[stage], "file", platform=1) == fixture["identity"], "host-origin-setter-full-identity")
    for kind in ("fm", "url"):
        require(value["before"][kind]["implementation_before"] == value["after"][kind]["implementation_before"], "host-origin-setter-getter-change")
    operation = value["operation"]
    require(isinstance(operation, dict) and set(operation) == {"id", "requested_protection", "returned", "native_error", "error_chain",
        "implementation_before", "implementation_after"} and operation["id"] == "host-origin-fm-set" and
        operation["requested_protection"] == "complete" and type(operation["returned"]) is bool, "host-origin-setter-operation")
    common.error_record(operation["native_error"])
    validate_error_chain(operation["error_chain"], operation["native_error"])
    implementation = operation["implementation_before"]
    require(isinstance(implementation, dict) and set(implementation) == {"receiver_class", "selector", "implementation"} and
        implementation == operation["implementation_after"] and implementation["selector"] == "setAttributes:ofItemAtPath:error:" and
        implementation["receiver_class"] == value["before"]["fm"]["implementation_before"]["receiver_class"], "host-origin-setter-imp")
    common.receipts.image(implementation["implementation"], 1, host_method="fm")
    require(implementation["implementation"]["image_basename"] == "Foundation", "host-origin-setter-foundation")
    return dict(status=SETTER_COLLECTED, scope=SETTER_SCOPE, same_inode=True, identity=fixture["identity"], before=value["before"],
        operation=operation, after=value["after"], simulator_fixture_observed=False, production_snapshots_observed=False,
        historical_a37_strict_result_changed=False, protection_qualified=False, runtime_implementation_causality_proven=False)


def setter_retirement(resources, prior):
    fixture = prior["host_setter_fixture"]
    require([row["name"] for row in prior["host_setter_inputs_before_compile"]] == list(SETTER_INPUTS), "host-origin-setter-input-paths")
    inputs = [dict(name=name, **common.host_file_binding(resources / name)) for name in SETTER_INPUTS]
    image = common.host_file_binding(resources / SETTER_IMAGE)
    require(common.owned_directory(resources / "host-fixtures") == fixture["root"] and
        setter_identity(resources / "host-fixtures/probe.bin") == fixture["identity"] and
        inputs == prior["host_setter_inputs_before_compile"] == prior.get("host_setter_inputs_after_set", inputs) and
        image == prior["host_setter_built_image"]["file"] == prior.get("host_setter_image_after_set", image), "host-origin-setter-retirement-custody")
    return dict(inputs=inputs, image=image, fixture=fixture)


class HostProbe:
    # Reuse only these reviewed host-neutral primitives, not the simulator class.
    save = common.Probe.save
    execute = common.Probe.execute
    stop = common.Probe.stop

    def __init__(self, env, mode):
        self.env, self.mode, self.state = dict(env), mode, None
        require(env.get("PARLOR_PROTECTION_SELECTION") == SELECTION, "explicit-host-only-selection-required")
        self.approved, self.control = env.get("PARLOR_APPROVED_PROBE_CONTROL_SHA256", ""), controls()
        require(native.HEX64.fullmatch(self.approved) and self.approved == self.control["control_sha256"], "approved-control-mismatch")
        require(platform.system() == "Darwin" and platform.machine() == "arm64" and
            os.getuid() > 0 and os.getuid() == os.geteuid(), "native-nonroot-arm64-macos-required")
        self.commands = common.Commands(common.child_environment(env), time.monotonic() + (300 if mode == "run" else 180), self.save)
        self.source = common.context(env, self.execute)
        temporary = Path(env["RUNNER_TEMP"])
        require(temporary.is_absolute() and temporary.resolve() == temporary and temporary.is_dir() and
            temporary != ROOT and ROOT not in temporary.parents and temporary not in ROOT.parents, "separate-owned-temp")
        self.base = temporary / "parlor-protection-probe-{}-{}".format(self.source["run_id"], self.source["run_attempt"])
        self.evidence, self.resources, self.cleanup_dir = (self.base / name for name in ("evidence", "resources", "cleanup"))

    def capture(self, arguments, label, timeout=30, retain=True):
        try:
            return common.Probe.capture(self, arguments, label, timeout, retain)
        except BaseException as error:
            self.commands.preservation_error("host-command-output", error)
            raise

    def retain(self, name, raw):
        try:
            native.write_new(self.evidence / name, raw)
            self.state["logs"].append(dict(path=name, bytes=len(raw), sha256=native.sha(raw)))
            self.save()
        except BaseException as error:
            self.commands.preservation_error("host-report-output", error)
            raise

    def bindings(self):
        require(controls() == self.control and common.context(self.env, self.execute) == self.source, "source-or-control-changed")

    def prepare(self):
        self.base.mkdir(mode=0o700)
        for path in (self.evidence, self.resources, self.resources / "tmp"):
            path.mkdir(mode=0o700)
        self.request = dict(schema=1, selection=SELECTION, profile=PROFILE, source=self.source, controls=self.control,
            uid=os.getuid(), base=common.owned_directory(self.base), evidence=common.owned_directory(self.evidence),
            resources=common.owned_directory(self.resources), temporary=common.owned_directory(self.resources / "tmp"),
            binding=dict(run_token=uuid.uuid4().hex, source_sha=self.source["source_sha"], control_sha256=self.approved,
                run_id=str(self.source["run_id"]), run_attempt=str(self.source["run_attempt"])))
        native.write_new(self.evidence / "request.json", native.json_bytes(self.request))
        self.state = dict(status="RUNNING", profile=PROFILE, selection=SELECTION, logs=[], errors=[], started_at=native.now())
        self.commands.environment["TMPDIR"] = str(self.resources / "tmp") + "/"
        self.save()

    def platform_binding(self):
        version = self.execute(["/usr/bin/sw_vers", "-productVersion"], "macos-version").decode().strip()
        require(re.fullmatch(r"15\.[0-9]{1,3}(?:\.[0-9]{1,3})?", version), "qualified-macos15")
        runtime_version = [int(part) for part in version.split(".")]
        runtime_version += [0] * (3 - len(runtime_version))
        build = self.execute(["/usr/bin/sw_vers", "-buildVersion"], "macos-build").decode().strip()
        require(re.fullmatch(r"[0-9A-Za-z]{1,32}", build), "macos-build-identity")
        require(self.execute(["/usr/bin/xcodebuild", "-version"], "xcode-version").decode() ==
            "Xcode 26.3\nBuild version 17C529\n", "qualified-xcode")
        require(self.execute(["/usr/bin/xcode-select", "-p"], "developer-selection").decode().strip() ==
            common.DEVELOPER, "selected-developer")
        require(self.execute(["/usr/bin/xcrun", "--sdk", "macosx", "--show-sdk-version"], "host-sdk-version").strip() ==
            b"26.2", "qualified-host-sdk")
        sdk = Path(self.execute(["/usr/bin/xcrun", "--sdk", "macosx", "--show-sdk-path"], "host-sdk-path").decode().strip()).resolve(strict=True)
        require(sdk.is_dir() and Path(common.DEVELOPER) / "Platforms/MacOSX.platform" in sdk.parents, "host-sdk-outside-qualified-xcode")
        row, out, err = self.capture([str(Path(self.commands.environment["JAVA_HOME"]) / "bin/java"), "-version"], "jdk-version")
        require(row["status"] == "EXITED" and row.get("exit_code") == 0 and row.get("direct_child_reaped") and
            re.search(rb'(?:openjdk|java) version "21[."]', out + err), "jdk21-required-for-stops")
        self.state["platform"] = dict(profile=PROFILE, macos_version=version, macos_build=build,
            runtime_version=runtime_version, architecture="arm64", xcode="26.3", xcode_build="17C529", sdk="26.2", sdk_path=str(sdk))
        self.save()
        return sdk

    def compile_and_run(self, sdk):
        self.bindings()
        template = native.file_bytes(HERE / "HostImageProbe.m.in")
        text = template.decode()
        for token, key in (("TOKEN", "run_token"), ("SOURCE", "source_sha"), ("CONTROLS", "control_sha256"),
                           ("RUN_ID", "run_id"), ("RUN_ATTEMPT", "run_attempt")):
            marker = "__HOST_IMAGE_" + token + "__"
            require(text.count(marker) == 1, "host-template-binding")
            text = text.replace(marker, json.dumps(self.request["binding"][key]))
        require("__HOST_IMAGE_" not in text, "unexpanded-host-binding")
        sampler = native.file_bytes(SHARED / "ProtectionSampler.m")
        sources = {"HostImageProbe.m": text.encode(), "ProtectionSampler.h": native.file_bytes(SHARED / "ProtectionSampler.h"),
            "ProtectionSampler.m": hook.transform(sampler)}
        for name, raw in sources.items():
            native.write_new(self.resources / name, raw)
        self.state["copied_sources"] = {name: native.sha(raw) for name, raw in sources.items()}
        self.save()
        difference = "".join(line for name, original in (("HostImageProbe.m", template), ("ProtectionSampler.m", sampler))
            for line in difflib.unified_diff(original.decode().splitlines(True), sources[name].decode().splitlines(True),
                fromfile="reviewed/" + name, tofile="owned/" + name)).encode()
        require(0 < len(difference) <= 32 * 1024, "bounded-host-copy-diff")
        self.retain("host-copy.patch", difference)
        compiler = ["/usr/bin/xcrun", "--sdk", "macosx", "clang"]
        self.execute(compiler + ["--version"], "host-clang-version")
        image = self.resources / IMAGE
        self.execute(compiler + ["-target", "arm64-apple-macosx15.0", "-isysroot", str(sdk), "-fobjc-arc", "-fblocks",
            "-fno-modules", "-O0", "-Wall", "-Wextra", "-I", str(self.resources), str(self.resources / "HostImageProbe.m"),
            str(self.resources / "ProtectionSampler.m"), "-framework", "Foundation", "-o", str(image)], "host-compile", 90)
        self.execute(["/usr/bin/codesign", "--force", "--sign", "-", "--timestamp=none", str(image)], "host-adhoc-sign")
        self.execute(["/usr/bin/codesign", "--verify", "--strict", str(image)], "host-adhoc-verify")
        raw = self.execute(["/usr/bin/xcrun", "dwarfdump", "--uuid", str(image)], "host-built-uuid")
        match = re.fullmatch(rb"UUID: ([0-9A-Fa-f]{8}(?:-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}) \(arm64\) " +
            re.escape(str(image).encode()) + rb"\n", raw)
        require(match is not None, "built-host-arm64-uuid")
        built = dict(uuid=match[1].decode().lower(), image_basename=IMAGE, custody=native.custody(image),
            sha256=native.sha(native.file_bytes(image, maximum=16 * 1024 * 1024)), profile=PROFILE)
        self.state["built_image"] = built
        self.save()
        row, out, err = self.capture([str(image)], "host-image-observation", 30, False)
        self.retain("host-image.stdout.json", out)
        self.retain("host-image.stderr.txt", err)
        require(row["status"] == "EXITED" and row.get("exit_code") == 0 and row.get("direct_child_reaped"), "host-collection-process")
        summary = validate_report(out, self.request, built, row, self.state["platform"])
        require(native.custody(image) == built["custody"] and
            native.sha(native.file_bytes(image, maximum=16 * 1024 * 1024)) == built["sha256"] and
            all(native.sha(native.file_bytes(self.resources / name)) == digest for name, digest in self.state["copied_sources"].items()),
            "compiled-host-inputs-mutated")
        self.retain("host-image-summary.json", native.json_bytes(summary))
        self.state["collection_status"] = summary["status"]
        require(self.state["collection_status"] == COLLECTED, "host-collection-not-qualification")

    def capture_setter(self):
        row, out, err = self.capture([str(self.resources / SETTER_IMAGE)], "host-setter-observation", 30, False)
        self.retain("host-setter.stdout.json", out)
        self.retain("host-setter.stderr.txt", err)
        require(row["status"] == "EXITED" and row.get("exit_code") == 0 and row.get("direct_child_reaped"), "host-origin-setter-collection-process")
        return row, out

    def compile_and_run_setter(self, sdk):
        require(self.state.get("collection_status") == COLLECTED and "host_setter_fixture" not in self.state and
            not any(row.get("label", "").startswith("host-setter-") for row in self.commands.rows), "host-image-required-before-single-setter")
        baseline = preceding_image(self.evidence, self.commands.rows)
        command = self.commands.rows[baseline["command_index"]]
        require(command.get("command") == [str(self.resources / IMAGE)] and command.get("timeout_seconds") == 30 and
            command.get("status") == "EXITED" and command.get("exit_code") == 0 and command.get("direct_child_reaped") is True and
            command.get("ownership") == "direct-unreaped-Popen" and not command.get("child_cleanup_error_type"), "completed-preceding-host-image")
        summary = validate_report(native.file_bytes(self.evidence / "host-image.stdout.json"), self.request,
            self.state["built_image"], command, self.state["platform"])
        require(summary == native.decode(native.file_bytes(self.evidence / "host-image-summary.json")), "validated-preceding-host-image")
        original_inputs = lambda: dict(image=common.host_file_binding(self.resources / IMAGE), sources={
            name: common.host_file_binding(self.resources / name) for name in sorted(self.state["copied_sources"])})
        original = original_inputs()
        require(native.custody(self.resources / IMAGE) == self.state["built_image"]["custody"] and
            original["image"]["sha256"] == self.state["built_image"]["sha256"] and
            all(original["sources"][name]["sha256"] == digest for name, digest in self.state["copied_sources"].items()),
            "host-image-inputs-before-setter")
        require(common.owned_directory(self.resources) == self.request["resources"] and
            common.owned_directory(self.resources / "tmp") == self.request["temporary"], "host-origin-setter-scratch-custody")
        require(sdk == Path(self.state["platform"]["sdk_path"]) and sdk.resolve(strict=True) == sdk and
            Path(common.DEVELOPER + "/Platforms/MacOSX.platform") in sdk.parents, "host-origin-setter-bound-sdk")
        root = self.resources / "host-fixtures"
        root.mkdir(mode=0o700)
        native.write_new(root / "probe.bin", SETTER_PAYLOAD)
        fixture = dict(root=common.owned_directory(root), identity=setter_identity(root / "probe.bin"))
        require(fixture["root"]["device"] == fixture["identity"]["device"] == self.request["resources"]["device"] and
            fixture["root"]["uid"] == fixture["identity"]["uid"] == self.request["uid"], "host-origin-setter-fixture-custody")
        self.state["host_setter_fixture"] = fixture
        sources = setter_sources(self.resources, self.request["binding"], fixture)
        for name, raw in sources.items():
            if name == "ProtectionSampler.h":
                require(native.file_bytes(self.resources / name) == raw, "host-origin-setter-shared-header")
            else:
                native.write_new(self.resources / name, raw)
        inputs = lambda: [dict(name=name, **common.host_file_binding(self.resources / name)) for name in SETTER_INPUTS]
        before = inputs()
        require(all(row["sha256"] == native.sha(sources[row["name"]]) for row in before), "host-origin-setter-source-copy")
        self.state["host_setter_inputs_before_compile"] = before
        self.retain("OwnedHostSetterContext.h", sources["OwnedHostSetterContext.h"])
        self.retain("host-setter-bindings.json", native.json_bytes(dict(scope=SETTER_SCOPE, binding=self.request["binding"], fixture=fixture,
            payload_sha256=native.sha(SETTER_PAYLOAD), preceding_image=baseline, original_sampler_sha256=common.host_sampler.SAMPLER_SHA256, inputs=before)))
        image = self.resources / SETTER_IMAGE
        for label, arguments, timeout in setter_build_commands(self.resources, sdk):
            raw = self.execute(arguments, label, timeout)
        match = re.fullmatch(rb"UUID: ([0-9A-Fa-f-]{36}) \(arm64\) " + re.escape(str(image).encode()) + rb"\n", raw)
        require(match is not None, "host-origin-setter-built-uuid")
        built = dict(uuid=common.simulator.checked_uuid(match[1].decode()).lower(), image_basename=SETTER_IMAGE,
            file=common.host_file_binding(image), scope=SETTER_SCOPE)
        self.state["host_setter_built_image"] = built
        self.save()
        require(inputs() == before, "host-origin-setter-compiler-inputs-changed")
        try:
            row, out = self.capture_setter()
        finally:
            self.state["host_setter_inputs_after_set"] = inputs()
            self.state["host_setter_image_after_set"] = common.host_file_binding(image)
            self.save()
            require(self.state["host_setter_inputs_after_set"] == before and self.state["host_setter_image_after_set"] == built["file"] and
                common.owned_directory(root) == fixture["root"] and setter_identity(root / "probe.bin") == fixture["identity"] and
                original_inputs() == original and preceding_image(self.evidence, self.commands.rows) == baseline, "host-origin-setter-custody-changed")
        require(setter_command_order(self.commands.rows, self.resources, sdk) == row, "host-origin-setter-command-binding")
        result = validate_setter_report(out, self.request, fixture, built, row, self.state["platform"])
        self.retain("host-setter-report.json", native.json_bytes(result))
        self.state["host_setter_collection_status"] = result["status"]
        self.save()

    def run(self):
        self.prepare()
        previous = {item: signal.signal(item, self.commands.interrupted) for item in (signal.SIGINT, signal.SIGTERM)}
        try:
            sdk = self.platform_binding()
            self.compile_and_run(sdk)
            self.compile_and_run_setter(sdk)
            self.bindings()
            self.state["status"] = "CAPTURED"
        except BaseException as error:
            self.state["status"] = "FAIL"
            self.state["errors"].append(dict(stage="collection", **common.failure(error)))
        finally:
            self.commands.finalizing = True
            self.commands.deadline = time.monotonic() + 90
            for child, row in self.commands.handles:
                self.commands.retire(child, row)
            self.commands.checkpoint()  # Preserve before the mandatory immediate native-cycle stop.
            try:
                self.stop()
            except BaseException as error:
                self.state["status"] = "FAIL"
                self.state["errors"].append(dict(stage="post-cycle-stop", **common.failure(error)))
            if self.commands.signals or self.commands.preservation["failures"]:
                self.state["status"] = "FAIL"
            self.state["finished_at"] = native.now()
            self.commands.checkpoint()
            for item, handler in previous.items(): signal.signal(item, handler)
        return 0 if self.state["status"] == "CAPTURED" and not self.commands.preservation["failures"] else 1

    def load_request(self):
        self.request = native.decode(native.file_bytes(self.evidence / "request.json"))
        require(self.request["schema"] == 1 and self.request["selection"] == SELECTION and self.request["profile"] == PROFILE and
            self.request["source"] == self.source and self.request["controls"] == self.control and self.request["uid"] == os.getuid() and
            common.owned_directory(self.base) == self.request["base"] and common.owned_directory(self.evidence) == self.request["evidence"],
            "owned-host-request-binding")
        binding = self.request["binding"]
        require(set(binding) == {"run_token", "source_sha", "control_sha256", "run_id", "run_attempt"} and
            re.fullmatch(r"[0-9a-f]{32}", binding["run_token"]) and binding == dict(run_token=binding["run_token"],
                source_sha=self.source["source_sha"], control_sha256=self.approved,
                run_id=str(self.source["run_id"]), run_attempt=str(self.source["run_attempt"])), "host-native-request-binding")

    def cleanup(self):
        self.load_request()
        self.cleanup_dir.mkdir(mode=0o700)
        self.state = dict(status="FAIL", profile=PROFILE, selection=SELECTION, logs=[], errors=[], started_at=native.now(),
            cleanup_scope="exact-owned-host-native-scratch-only")
        self.commands.finalizing = True
        previous = {item: signal.signal(item, self.commands.interrupted) for item in (signal.SIGINT, signal.SIGTERM)}
        try:
            self.save()
            self.stop()
            self.state["evidence_upload"] = common.upload_binding(self.env)
            self.bindings()
            prior = native.decode(native.file_bytes(self.evidence / "state.json"))
            require(prior["profile"] == PROFILE and prior["selection"] == SELECTION and not prior["preservation"]["failures"] and
                all(row.get("status") == "EXITED" and row.get("direct_child_reaped") is True and
                    not row.get("child_cleanup_error_type") for row in prior["commands"]), "unsettled-host-command-scratch-retained")
            require(common.owned_directory(self.resources) == self.request["resources"] and
                common.owned_directory(self.resources / "tmp") == self.request["temporary"], "host-scratch-custody")
            entries, total = [], 0
            for path in self.resources.rglob("*"):
                info = path.lstat()
                total += info.st_size
                require(len(entries) < 128 and total <= 64 * 1024 * 1024 and not path.is_symlink() and path.resolve() == path and
                    info.st_uid == os.getuid() and info.st_dev == self.request["resources"]["device"] and
                    (stat.S_ISDIR(info.st_mode) or stat.S_ISREG(info.st_mode) and info.st_nlink == 1 and info.st_size <= 16 * 1024 * 1024),
                    "unsafe-host-scratch-entry-retained")
                entries.append(dict(path=str(path.relative_to(self.resources)), device=info.st_dev, inode=info.st_ino,
                    uid=info.st_uid, mode=info.st_mode, bytes=info.st_size))
            self.state["removed_entries"] = entries
            if "host_setter_built_image" in prior:
                self.state["host_setter_retirement"] = setter_retirement(self.resources, prior)
            self.save()
            require(not self.commands.preservation["failures"] and shutil.rmtree.avoids_symlink_attacks, "descriptor-safe-host-removal-required")
            shutil.rmtree(self.resources)
            require(not self.resources.exists() and not self.resources.is_symlink(), "host-scratch-remains")
            self.state["scratch_absent"] = True
            self.bindings()
            self.state["status"] = "PASS"
        except BaseException as error:
            self.state["errors"].append(dict(stage="cleanup", **common.failure(error)))
        finally:
            self.commands.deadline = max(self.commands.deadline, time.monotonic() + 65)
            try:
                self.stop()
            except BaseException as error:
                self.state["status"] = "FAIL"
                self.state["errors"].append(dict(stage="final-stop", **common.failure(error)))
            if self.commands.signals or self.commands.preservation["failures"]: self.state["status"] = "FAIL"
            self.state["finished_at"] = native.now()
            self.commands.checkpoint()
            for item, handler in previous.items(): signal.signal(item, handler)
        return 0 if self.state["status"] == "PASS" and not self.commands.preservation["failures"] else 1

    def assert_result(self):
        self.load_request()
        require(self.env.get("PARLOR_PROTECTION_RUN_OUTCOME") == "success" and
            self.env.get("PARLOR_PROTECTION_CLEANUP_OUTCOME") == "success", "required-step-outcomes")
        first, second = common.upload_binding(self.env), common.upload_binding(self.env, cleanup=True)
        require(first["artifact_id"] != second["artifact_id"], "distinct-immutable-artifacts")
        prior = native.decode(native.file_bytes(self.evidence / "state.json"))
        cleanup = native.decode(native.file_bytes(self.cleanup_dir / "state.json"))
        require(all(value["profile"] == PROFILE and value["selection"] == SELECTION and not value["errors"] and
            not value["preservation"]["failures"] for value in (prior, cleanup)) and prior["status"] == "CAPTURED" and
            prior["collection_status"] == COLLECTED and cleanup["status"] == "PASS" and cleanup.get("scratch_absent") is True and
            cleanup.get("evidence_upload") == first and not self.resources.exists() and not self.resources.is_symlink(),
            "captured-and-cleaned-host-receipts-required")
        rows = [row for row in prior["commands"] if row["label"] == "host-image-observation"]
        require(len(rows) == 1 and rows[0]["status"] == "EXITED" and rows[0].get("exit_code") == 0 and
            rows[0].get("direct_child_reaped") is True, "completed-host-observation-required")
        summary = validate_report(native.file_bytes(self.evidence / "host-image.stdout.json"), self.request,
            prior["built_image"], rows[0], prior["platform"])
        require(summary == native.decode(native.file_bytes(self.evidence / "host-image-summary.json")), "host-report-changed")
        fixture = prior["host_setter_fixture"]
        require(set(fixture) == {"root", "identity"} and set(fixture["root"]) == {"device", "inode", "uid"} and
            all(type(value) is int and value > 0 for value in fixture["root"].values()) and
            fixture["root"]["device"] == fixture["identity"]["device"] == self.request["resources"]["device"] and
            fixture["root"]["uid"] == fixture["identity"]["uid"] == self.request["uid"] and
            common.receipts.identity(fixture["identity"])["mode"] == (stat.S_IFREG | 0o600) and
            fixture["identity"]["size"] == len(SETTER_PAYLOAD), "retained-host-origin-fixture")
        sources = setter_sources(self.resources, self.request["binding"], fixture)
        inputs = prior["host_setter_inputs_before_compile"]
        require(native.file_bytes(self.evidence / "OwnedHostSetterContext.h") == sources["OwnedHostSetterContext.h"] and
            inputs == prior["host_setter_inputs_after_set"] and len(inputs) == len(SETTER_INPUTS) and
            native.decode(native.file_bytes(self.evidence / "host-setter-bindings.json")) == dict(scope=SETTER_SCOPE,
                binding=self.request["binding"], fixture=fixture, payload_sha256=native.sha(SETTER_PAYLOAD),
                preceding_image=preceding_image(self.evidence, prior["commands"]),
                original_sampler_sha256=common.host_sampler.SAMPLER_SHA256, inputs=inputs), "retained-host-origin-source-bindings")
        for row, name in zip(inputs, SETTER_INPUTS):
            require(row["name"] == name and row["sha256"] == native.sha(sources[name]) and row["bytes"] == len(sources[name]) and
                row["device"] == self.request["resources"]["device"] and row["uid"] == self.request["uid"] and
                type(row["inode"]) is int and row["inode"] > 0 and stat.S_ISREG(row["mode"]) and row["links"] == 1,
                "retained-host-origin-input-identity")
        image = prior["host_setter_built_image"]["file"]
        require(prior["host_setter_image_after_set"] == image and native.HEX64.fullmatch(image["sha256"]) and
            image["device"] == self.request["resources"]["device"] and image["uid"] == self.request["uid"] and
            type(image["inode"]) is int and image["inode"] > 0 and stat.S_ISREG(image["mode"]) and image["links"] == 1 and
            0 < image["bytes"] <= 16 * 1024 * 1024 and
            cleanup.get("host_setter_retirement") == dict(inputs=inputs, image=image, fixture=fixture), "retained-host-origin-image-retirement")
        retired_files = [(SETTER_IMAGE, image)] + [(row["name"], row) for row in inputs] + [
            ("host-fixtures/probe.bin", dict(fixture["identity"], bytes=fixture["identity"]["size"]))]
        for name, identity in retired_files:
            require([row for row in cleanup["removed_entries"] if row.get("path") == name] == [dict(path=name,
                **{key: identity[key] for key in ("device", "inode", "uid", "mode", "bytes")})], "retired-host-origin-entry-binding")
        command = setter_command_order(prior["commands"], self.resources, Path(prior["platform"]["sdk_path"]))
        setter = validate_setter_report(native.file_bytes(self.evidence / "host-setter.stdout.json"), self.request, fixture,
            prior["host_setter_built_image"], command, prior["platform"])
        require(setter == native.decode(native.file_bytes(self.evidence / "host-setter-report.json")) and
            prior["host_setter_collection_status"] == setter["status"], "host-origin-setter-report-changed")
        print(json.dumps(dict(collection=summary["status"], results=summary["results"], cleanup="PASS",
            host_origin_setter=setter["status"],
            application_protection_qualification="NOT_ESTABLISHED", evidence_upload=first, cleanup_upload=second), sort_keys=True))
        return 0


def main(arguments=None):
    arguments = sys.argv[1:] if arguments is None else arguments
    require(arguments in (["controls"], ["run"], ["cleanup"], ["assert-result"]), "closed-host-probe-cli")
    if arguments == ["controls"]:
        print(json.dumps(controls(), indent=2))
        return 0
    os.umask(0o077)
    probe = HostProbe(dict(os.environ), arguments[0])
    return {"run": probe.run, "cleanup": probe.cleanup, "assert-result": probe.assert_result}[arguments[0]]()


if __name__ == "__main__":
    def interrupted(_signum, _frame):
        raise KeyboardInterrupt()
    signal.signal(signal.SIGTERM, interrupted)
    try:
        sys.exit(main())
    except BaseException as error:
        if isinstance(error, SystemExit): raise
        print(json.dumps(dict(status="FAIL", error=common.failure(error), profile=PROFILE)), file=sys.stderr)
        sys.exit(1)
