#!/usr/bin/env python3
"""Inspect one unsigned verification AAB. Never signs, builds, uploads or publishes.

Uses the policy-hashed bundletool and Android build-tools 36.0.0 dexdump. The
caller binds the build to its source receipt and owns the Gradle build lane.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import signal
import stat
import struct
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
import zipfile

import third_party_notices as notices

MAX_ENTRY = 128 * 1024 * 1024
MAX_TOTAL = 1024 * 1024 * 1024
MAX_TOOL_OUTPUT = 128 * 1024 * 1024
ANDROID = "{http://schemas.android.com/apk/res/android}"
NATIVE = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40), "x86": (1, 3), "x86_64": (2, 62)}
FORBIDDEN = ("com.parlor.app.debug.", "com.parlor.engine.testing.", "com.parlor.networking.testing.",
             "org.junit.", "junit.", "org.opentest4j.", "io.ktor.client.engine.mock.",
             "kotlinx.coroutines.test.", "app.cash.turbine.", "com.lemonappdev.konsist.")
METADATA = {"BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb",
            "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map",
            "BUNDLE-METADATA/com.android.tools/r8.json", "base/manifest/AndroidManifest.xml",
            "base/dex/classes.dex", "BundleConfig.pb"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def regular(path, limit):
    path = Path(path).absolute()
    require(path.resolve(strict=True) == path and not path.is_symlink(), "Redirected input path")
    value = path.stat()
    require(stat.S_ISREG(value.st_mode) and 0 < value.st_size <= limit, "Invalid input type or size")
    return path


def run_tool(command, output, limit=MAX_TOOL_OUTPUT, timeout=120):
    """Reap this direct trusted-tool child on every path; never signal a group."""
    with output.open("xb") as stream:
        process = None
        try:
            process = subprocess.Popen(command, stdout=stream, stderr=subprocess.STDOUT)
            started = time.monotonic()
            while process.poll() is None:
                require(time.monotonic() - started < timeout, "Artifact tool timed out")
                require(output.stat().st_size <= limit, "Artifact tool output limit exceeded")
                time.sleep(0.05)
            require(process.wait() == 0, "Artifact tool failed")
            require(output.stat().st_size <= limit, "Artifact tool output limit exceeded")
        finally:
            if process is not None and process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()


def inspect_manifest(raw, version_name, version_code):
    require(len(raw) <= 1024 * 1024 and b"<!DOCTYPE" not in raw and b"<!ENTITY" not in raw,
            "Invalid manifest size or declarations")
    root = ET.fromstring(raw)
    require(root.tag == "manifest" and root.get("package") == "com.parlor.app", "Wrong release identity")
    require(root.get(ANDROID + "versionName") == version_name
            and root.get(ANDROID + "versionCode") == str(version_code), "Wrong packaged version")
    require(root.get(ANDROID + "sharedUserId") is None, "Unexpected shared user")
    sdk = root.find("uses-sdk")
    require(sdk is not None and sdk.get(ANDROID + "minSdkVersion") == "26"
            and sdk.get(ANDROID + "targetSdkVersion") == "36", "Wrong packaged SDK levels")
    apps = root.findall("application")
    require(len(apps) == 1, "Expected one application")
    app = apps[0]
    require(app.get(ANDROID + "name") == "com.parlor.app.ParlorApplication", "Wrong application class")
    require(all(app.get(ANDROID + key) == "false" for key in ("allowBackup", "usesCleartextTraffic")),
            "Backup or cleartext policy mismatch")
    require(all(app.get(ANDROID + key) in (None, "false") for key in ("debuggable", "testOnly")),
            "Debug/test-only release artifact")
    permissions = [item.get(ANDROID + "name") for item in root.findall("uses-permission")]
    expected_permissions = {"android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
                            "android.permission.ACCESS_WIFI_STATE", "android.permission.CHANGE_WIFI_MULTICAST_STATE",
                            "com.parlor.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}
    require(len(permissions) == len(expected_permissions) and set(permissions) == expected_permissions,
            "Unexpected packaged permissions")
    require(not root.findall("uses-permission-sdk-23"), "Alternate permissions require review")
    exported, launchers = [], []
    for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
        for component in app.findall(tag):
            name = component.get(ANDROID + "name")
            if component.get(ANDROID + "exported") == "true":
                exported.append((tag, name, component.get(ANDROID + "permission", "")))
            for intent in component.findall("intent-filter"):
                actions = {node.get(ANDROID + "name") for node in intent.findall("action")}
                categories = {node.get(ANDROID + "name") for node in intent.findall("category")}
                if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories:
                    launchers.append((tag, name, component.get(ANDROID + "exported")))
    require(sorted(exported) == sorted([("activity", "com.parlor.app.MainActivity", ""),
            ("receiver", "androidx.profileinstaller.ProfileInstallReceiver", "android.permission.DUMP")]),
            "Unexpected exported surface")
    require(launchers == [("activity", "com.parlor.app.MainActivity", "true")], "Unexpected launcher")
    return {"application_id": "com.parlor.app", "version_name": version_name, "version_code": version_code,
            "min_sdk": 26, "target_sdk": 36, "permissions": sorted(permissions), "exported": exported}


def inspect_elf(header, path):
    parts = path.split("/")
    require(len(parts) == 4 and parts[:2] == ["base", "lib"] and parts[2] in NATIVE
            and parts[3] == "libandroidx.graphics.path.so", "Unreviewed native artifact")
    require(len(header) >= 64 and header[:4] == b"\x7fELF" and header[5:7] == b"\x01\x01",
            "Malformed native ELF header")
    kind, machine = struct.unpack_from("<HH", header, 16)
    require(kind == 3 and (header[4], machine) == NATIVE[parts[2]], "Native ABI mismatch")
    return {"abi": parts[2], "elf_class": header[4], "machine": machine}


def bounded_lines(stream):
    while True:
        line = stream.readline(65537)
        if not line:
            return
        require(len(line) <= 65536, "Overlarge tool/mapping line")
        yield line


def inspect_mapping(stream):
    count, application = 0, False
    for line in bounded_lines(stream):
        if line[:1].isspace() or line.startswith("#") or not line.strip():
            continue
        match = re.fullmatch(r"(\S+) -> (\S+):\s*", line)
        require(match is not None, "Unrecognized R8 class mapping")
        name = match[1]
        require(not name.startswith(FORBIDDEN), "Test/Debug class in R8 input mapping")
        count += 1
        application |= name == "com.parlor.app.MainActivity"
    require(count > 0 and application, "Mapping lacks application entry point")
    return {"class_mappings": count, "test_fixture_namespaces_absent": True}


def inspect_dex_output(stream, expected):
    classes, application = set(), False
    for line in bounded_lines(stream):
        match = re.fullmatch(r"\s*Class descriptor\s*:\s*'([^']+)'\s*", line)
        if match is None:
            continue
        descriptor = match[1]
        require(descriptor.startswith("L") and descriptor.endswith(";"), "Invalid DEX class descriptor")
        require(descriptor not in classes and len(classes) < 1000000, "Duplicate/excessive DEX classes")
        classes.add(descriptor)
        name = descriptor[1:-1].replace("/", ".")
        require(not name.startswith(FORBIDDEN), "Test/Debug class in DEX")
        application |= name == "com.parlor.app.MainActivity"
    require(len(classes) == expected, "DEX descriptor discovery mismatch")
    return {"class_definitions": len(classes), "contains_main_activity": application,
            "test_fixture_namespaces_absent": True}


def expected_resources(root):
    result = {}
    for source_dir, prefix, extension in (
        ("game-modes/whodunit/src/commonMain/composeResources/files/cases",
         "base/assets/composeResources/com.parlor.games.whodunit.resources/files/cases", ".json"),
        ("shared/design-system/src/commonMain/composeResources/font",
         "base/assets/composeResources/parlor.shared.design_system.generated.resources/font", ".ttf"),
    ):
        for path in sorted((root / source_dir).iterdir()):
            require(path.suffix == extension, "Unreviewed raw resource type")
            path = regular(path, MAX_ENTRY)
            result[prefix + "/" + path.name] = {"bytes": path.stat().st_size, "sha256": sha256(path)}
    require(len(result) == 9, "Bundled story/font catalog changed; artifact review required")
    return result


def inspect(root, aab, bundletool, dexdump):
    root = root.resolve(strict=True)
    aab = regular(aab, 512 * 1024 * 1024)
    bundletool = regular(bundletool, MAX_ENTRY)
    dexdump = regular(dexdump, MAX_ENTRY)
    policy = json.loads((root / "config/release-policy.json").read_text())
    require(sha256(bundletool) == policy["tools"]["bundletool"]["sha256"], "Unreviewed bundletool bytes")
    require(dexdump.name == "dexdump" and dexdump.parent.name == "36.0.0", "Unreviewed dexdump path")
    require(os.access(dexdump, os.X_OK), "dexdump is not executable")
    sdk_properties = regular(dexdump.parent / "source.properties", 65536).read_text()
    require(re.search(r"^Pkg.Revision\s*=\s*36\.0\.0\s*$", sdk_properties, re.M), "Unreviewed build-tools revision")
    versions = dict(re.findall(r"^(PARLOR_VERSION_NAME|PARLOR_BUILD_NUMBER)\s*=\s*(\S+)\s*$",
                              (root / "config/parlor-version.xcconfig").read_text(), re.M))
    before = sha256(aab)
    notice_result = notices.verify(root, aab)
    expected = expected_resources(root)
    rows, natives, dex, total = [], [], [], 0
    with tempfile.TemporaryDirectory(prefix="parlor-unsigned-aab-") as temporary:
        scratch = Path(temporary)
        manifest_path = scratch / "manifest.xml"
        run_tool(["java", "-Xmx256m", "-jar", str(bundletool), "validate", "--bundle=" + str(aab)],
                 scratch / "bundletool-validate.log", 1024 * 1024)
        run_tool(["java", "-Xmx256m", "-jar", str(bundletool), "dump", "manifest", "--bundle=" + str(aab),
                  "--module=base"], manifest_path, 1024 * 1024)
        manifest = inspect_manifest(manifest_path.read_bytes(), versions["PARLOR_VERSION_NAME"],
                                    int(versions["PARLOR_BUILD_NUMBER"]))
        with zipfile.ZipFile(aab) as archive:
            entries = archive.infolist()  # The bounded notice pass already checked the same hashed file.
            names = {item.filename for item in entries}
            require(len(names) == len(entries) and len(names) <= notices.MAX_PACKAGE_ENTRIES
                    and METADATA <= names, "Missing or duplicate AAB metadata")
            require(not any(re.fullmatch(r"META-INF/[^/]+\.(SF|RSA|DSA|EC)", name, re.I) for name in names),
                    "This gate accepts only unsigned verification artifacts")
            for item in entries:
                require(0 <= item.file_size <= MAX_ENTRY and total + item.file_size <= MAX_TOTAL,
                        "AAB expanded-byte limit exceeded")
                digest, size, header = hashlib.sha256(), 0, b""
                is_dex = re.fullmatch(r"base/dex/classes(?:[2-9]|[1-9][0-9]+)?\.dex", item.filename) is not None
                require(not item.filename.endswith(".dex") or is_dex, "Unreviewed DEX path")
                target = scratch / item.filename.rsplit("/", 1)[-1] if is_dex else None
                output = target.open("xb") if target is not None else None
                try:
                    with archive.open(item) as stream:
                        for block in iter(lambda: stream.read(1024 * 1024), b""):
                            size += len(block)
                            require(size <= item.file_size, "AAB entry changed size")
                            digest.update(block)
                            if len(header) < 112:
                                header += block[:112 - len(header)]
                            if output is not None:
                                output.write(block)
                finally:
                    if output is not None:
                        output.close()
                require(size == item.file_size, "Truncated AAB entry")
                total += size
                row = {"path": item.filename, "bytes": size, "sha256": digest.hexdigest()}
                rows.append(row)
                if header.startswith(b"\x7fELF") or item.filename.startswith("base/lib/") and not item.is_dir():
                    natives.append(dict(row, **inspect_elf(header, item.filename)))
                if is_dex:
                    require(len(header) == 112 and header[:4] == b"dex\n", "Invalid DEX header")
                    expected_count = struct.unpack_from("<I", header, 96)[0]
                    dump = target.with_suffix(".txt")
                    run_tool([str(dexdump), "-l", "plain", str(target)], dump)
                    with dump.open(encoding="utf-8") as text:
                        dex.append(dict(row, **inspect_dex_output(text, expected_count)))
                    target.unlink()
                    dump.unlink()
            mapping_name = "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map"
            with archive.open(mapping_name) as mapping_stream:
                with io.TextIOWrapper(mapping_stream, encoding="utf-8") as text:
                    mapping = inspect_mapping(text)
        by_path = {row["path"]: {k: row[k] for k in ("bytes", "sha256")} for row in rows}
        require(all(by_path.get(name) == value for name, value in expected.items()), "Packaged story/font bytes differ")
        for name in by_path:
            if "/files/cases/" in name or "/font/" in name:
                require(name in expected, "Unexpected shipped story/font resource")
        require(len(natives) == 4 and {item["abi"] for item in natives} == set(NATIVE), "Missing native ABI")
        require(dex and any(item["contains_main_activity"] for item in dex), "DEX lacks application entry point")
    require(sha256(aab) == before, "Artifact changed during inspection")
    return {"status": "PASS", "scope": "unsigned AAB verification; not runtime, Store signing or publication",
            "artifact_sha256": before, "artifact_bytes": aab.stat().st_size, "entries": rows,
            "expanded_bytes": total, "manifest": manifest, "native_images": natives, "dex": dex,
            "r8_mapping": mapping, "raw_resources": expected, "notices": notice_result,
            "tools": {"bundletool_sha256": sha256(bundletool), "dexdump_sha256": sha256(dexdump),
                      "build_tools": "36.0.0"},
            "limitations": "Input-graph review remains separate. Namespace checks plus original R8 mappings detect reviewed test-fixture packages, not arbitrary malicious renaming. The caller must bind this artifact to its exact build/source receipt."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--package", type=Path, required=True)
    parser.add_argument("--bundletool", type=Path, required=True)
    parser.add_argument("--dexdump", type=Path, required=True)
    args = parser.parse_args()
    try:
        value = inspect(args.root, args.package, args.bundletool, args.dexdump)
    except (ValueError, OSError, ET.ParseError, zipfile.BadZipFile) as error:
        print(json.dumps({"status": "FAIL", "error_type": type(error).__name__}))
        return 1
    print(json.dumps(value, sort_keys=True))
    return 0


if __name__ == "__main__":
    def cancelled(_signum, _frame):
        raise KeyboardInterrupt("Artifact inspection cancelled")
    signal.signal(signal.SIGTERM, cancelled)
    sys.exit(main())
