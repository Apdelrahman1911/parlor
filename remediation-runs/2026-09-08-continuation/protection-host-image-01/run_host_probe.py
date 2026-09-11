#!/usr/bin/env python3
"""One host-only attribution observation; never an app/protection qualification."""
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
PROFILE = "host-macos15-arm64-xcode26.3-sdk26.2-attribution-only"
COLLECTED = "CAPTURED_HOST_IMAGE_PREDICATES_NOT_APP_QUALIFICATION"
IMAGE = "HostImageProbe"
CONTROL_PATHS = tuple(sorted(set(common.CONTROL_PATHS) | {
    str(HERE.relative_to(ROOT) / name) for name in
    ("run_host_probe.py", "test_host_probe.py", "README.md", "HostImageProbe.m.in", "image_hook.py", "test_image_hook.py")
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

    def run(self):
        self.prepare()
        previous = {item: signal.signal(item, self.commands.interrupted) for item in (signal.SIGINT, signal.SIGTERM)}
        try:
            self.compile_and_run(self.platform_binding())
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
        print(json.dumps(dict(collection=summary["status"], results=summary["results"], cleanup="PASS",
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
