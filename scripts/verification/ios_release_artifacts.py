#!/usr/bin/env python3
"""Read-only unsigned iOS Simulator Release package inspection.

Reuses the streamed artifact checks from the archived ios_release_wrapper_v1
inspector. The caller must prove a fresh, successful build and stop its workers
before invoking this tool; existing artifact bytes alone cannot prove freshness.
No build, signing, simulator, network, or Store operation is performed here.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import plistlib
import re
import stat
import subprocess
import sys
import tempfile

MACHO_MAGICS = {bytes.fromhex(value) for value in (
    "feedface", "cefaedfe", "feedfacf", "cffaedfe", "cafebabe", "bebafeca", "cafebabf", "bfbafeca")}
MAX_FILES = 8192
MAX_TOTAL_BYTES = 2 * 1024 * 1024 * 1024
MAX_PLIST_BYTES = 1024 * 1024
MAX_TOOL_BYTES = 65536
MAX_GIT_BYTES = 32 * 1024 * 1024
MAX_REPORT_BYTES = 8 * 1024 * 1024
REQUIRED_NATIVE = {"Parlor", "Frameworks/ComposeApp.framework/ComposeApp"}
UUID = r"[0-9A-Fa-f]{8}(?:-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}"
RAW_ROOTS = {
    "game-modes/whodunit/src/commonMain/composeResources/": "com.parlor.games.whodunit.resources",
    "shared/design-system/src/commonMain/composeResources/": "parlor.shared.design_system.generated.resources",
}
EXCLUDED_ROOTS = ("audit-runs/", "remediation-runs/", "project-code-audit/", "design/")
BUILD_ROOTS = ("composeApp/", "shared/", "game-modes/", "build-logic/", "gradle/", "config/",
               "iosApp/", "scripts/", ".github/", ".run/")
LIMITATION = (
    "Unsigned simulator artifact inspection only. Fresh successful build/source binding is the caller's responsibility. "
    "No runtime, physical-device, signed Store candidate, publication, or legal approval. "
    "The separate third_party_notices checker verifies the legal supplement bytes."
)


def canonical_directory(path: Path) -> Path:
    path = path.absolute()
    if not path.is_dir() or path.is_symlink() or path.resolve(strict=True) != path:
        raise RuntimeError("Expected an existing canonical, non-symlinked directory")
    return path


def protected_name(relative: str) -> bool:
    name = PurePosixPath(relative).name.lower()
    return (name.endswith((".keystore", ".jks", ".p12", ".p8", ".mobileprovision", ".key", ".pem")) or
            name in {"local.properties", "credentials.json", "service-account.json"})


def fingerprint(path: Path) -> tuple:
    value = path.lstat()
    if not stat.S_ISREG(value.st_mode) or path.resolve(strict=True) != path.absolute():
        raise RuntimeError("Refuse nonregular or redirected artifact/source file")
    return value.st_dev, value.st_ino, value.st_size, value.st_mtime_ns, value.st_ctime_ns, value.st_mode


def file_record(path: Path, relative: str) -> dict:
    before = fingerprint(path)
    if before[2] > MAX_TOTAL_BYTES:
        raise RuntimeError("File exceeds bounded inspection size")
    value = hashlib.sha256()
    count = 0
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            count += len(block)
            if count > MAX_TOTAL_BYTES:
                raise RuntimeError("File grew beyond bounded inspection size")
            value.update(block)
    if fingerprint(path) != before or count != before[2]:
        raise RuntimeError("File changed while it was inspected")
    return {"path": relative, "bytes": count, "sha256": value.hexdigest(), "executable": bool(before[5] & 0o111)}


def bounded_file(path: Path) -> bytes:
    before = fingerprint(path)
    if before[2] > MAX_PLIST_BYTES:
        raise RuntimeError("Oversized public configuration input")
    with path.open("rb") as stream:
        data = stream.read(MAX_PLIST_BYTES + 1)
    if len(data) > MAX_PLIST_BYTES or fingerprint(path) != before:
        raise RuntimeError("Public configuration changed or exceeded its bound")
    return data


def read_plist(path: Path) -> dict:
    value = plistlib.loads(bounded_file(path))
    if not isinstance(value, dict):
        raise RuntimeError("Public plist must be a dictionary")
    return value


def bounded_command(arguments: list[str], *, cwd: Path | None = None,
                    environment: dict | None = None, limit: int = MAX_TOOL_BYTES) -> bytes:
    # Spool rather than retaining potentially large native/Git output in RAM.
    # The acceptance limit is checked after execution, not a streaming disk cap.
    # subprocess.run kills and waits for its child on timeout/interruption;
    # these fixed inspection commands do not start build/native service workers.
    with tempfile.TemporaryFile() as output:
        result = subprocess.run(arguments, cwd=cwd, env=environment, stdout=output,
                                stderr=subprocess.STDOUT, timeout=30, check=False)
        if result.returncode != 0:
            raise RuntimeError("Inspection command failed: " + Path(arguments[0]).name)
        output.seek(0)
        data = output.read(limit + 1)
        if len(data) > limit:
            raise RuntimeError("Inspection command output exceeded its acceptance bound")
        return data


def native_probe(path: Path) -> dict:
    if sys.platform != "darwin":
        raise RuntimeError("Actual native metadata inspection requires macOS/Xcode")
    commands = {
        "file": ["/usr/bin/file", "-b", str(path)],
        "archs": ["/usr/bin/xcrun", "lipo", "-archs", str(path)],
        "build": ["/usr/bin/xcrun", "vtool", "-show-build", str(path)],
        "uuid": ["/usr/bin/xcrun", "dwarfdump", "--uuid", str(path)],
    }
    return {key: bounded_command(command).decode("utf-8") for key, command in commands.items()}


def version_tuple(value: str) -> tuple:
    result = tuple(int(part) for part in value.split("."))
    return result + (0,) * (3 - len(result))


def verify_native_probe(relative: str, path: Path, probe: dict) -> dict:
    if (not isinstance(probe, dict) or set(probe) != {"file", "archs", "build", "uuid"} or
            any(not isinstance(value, str) or len(value.encode()) > MAX_TOOL_BYTES for value in probe.values())):
        raise RuntimeError("Native inspection requires bounded file/lipo/vtool/dwarfdump outputs")
    if "Mach-O" not in probe["file"] or probe["archs"].split() != ["arm64"]:
        raise RuntimeError("Every shipped native binary must be an arm64 Mach-O")
    platforms = re.findall(r"^\s*platform\s+(\S+)\s*$", probe["build"], re.MULTILINE)
    minimum = re.findall(r"^\s*minos\s+([0-9]+(?:\.[0-9]+){1,2})\s*$", probe["build"], re.MULTILINE)
    if (not platforms or any(value != "IOSSIMULATOR" for value in platforms) or not minimum or
            len(platforms) != len(minimum) or any(version_tuple(value) > (16, 0, 0) for value in minimum)):
        raise RuntimeError("Native platform/deployment is incompatible with the iOS Simulator app floor")
    if relative == "Parlor" and "executable" not in probe["file"]:
        raise RuntimeError("Main app artifact is not executable Mach-O code")
    uuid_lines = probe["uuid"].splitlines()
    match = re.fullmatch(r"UUID: (" + UUID + r") \(arm64\) " + re.escape(str(path)),
                        uuid_lines[0]) if len(uuid_lines) == 1 else None
    if match is None:
        raise RuntimeError("Native UUID must identify exactly this arm64 artifact path")
    return {"architectures": ["arm64"], "platforms": platforms,
            "minimum_os_versions": sorted(set(minimum)), "uuid": match.group(1).lower(),
            "file_description": probe["file"].strip(), "evidence_kind": "native-file-lipo-vtool-dwarfdump-metadata-only"}


def source_versions(source: Path) -> dict:
    text = bounded_file(source / "config/parlor-version.xcconfig").decode("utf-8")
    result = {}
    for name in ("PARLOR_VERSION_NAME", "PARLOR_BUILD_NUMBER"):
        values = re.findall(r"^" + name + r"\s*=\s*(\S+)\s*$", text, re.MULTILINE)
        if len(values) != 1:
            raise RuntimeError("Version must come from one source xcconfig assignment")
        result[name] = values[0]
    return result


def source_identity_policy(source: Path) -> dict:
    policy = json.loads(bounded_file(source / "config/release-policy.json"))
    ios = policy["applications"]["ios"]
    # This tool is validation-only. It cannot approve the known Store collision.
    if (ios["store_bundle_id"] != "com.parlor.app" or ios["debug_bundle_id"] != "com.parlor.app.debug" or
            ios["store_identity_ownership"] != {"status": "blocked", "reason": "public_store_collision",
                                               "verified_at": None, "verification_reference": None}):
        raise RuntimeError("Unreviewed application identity or Store-ownership policy change")
    return ios


def artifact_paths(app: Path) -> list[Path]:
    files = []
    for visited, path in enumerate(app.rglob("*"), 1):
        if visited > MAX_FILES * 2 or path.is_symlink():
            raise RuntimeError("Artifact entry bound exceeded or generated symlink found")
        if path.is_dir():
            if path.suffix == ".appex":
                raise RuntimeError("Unreviewed app extension is outside the wrapper contract")
            continue
        if protected_name(path.name):
            raise RuntimeError("Unsigned app unexpectedly contains signing/private material")
        if len(files) >= MAX_FILES:
            raise RuntimeError("Artifact file-count bound exceeded")
        fingerprint(path)
        files.append(path)
    return sorted(files)


def raw_resource_path(relative: str) -> str | None:
    is_case = relative.startswith("game-modes/whodunit/src/commonMain/composeResources/files/cases/") and relative.endswith(".json")
    is_font = "/src/commonMain/composeResources/" in relative and Path(relative).suffix in {".ttf", ".otf"}
    if not (is_case or is_font):
        return None
    for prefix, namespace in RAW_ROOTS.items():
        if relative.startswith(prefix):
            return "compose-resources/composeResources/" + namespace + "/" + relative[len(prefix):]
    raise RuntimeError("Raw resource has an unreviewed Compose package namespace")


def inspect_release_artifacts(app: Path, source: Path, binary_probe, source_paths: list[str]) -> dict:
    app, source = canonical_directory(app), canonical_directory(source)
    entries, native = [], []
    paths = artifact_paths(app)
    initial = {str(path.relative_to(app)): fingerprint(path) for path in paths}
    total = sum(value[2] for value in initial.values())
    if total > MAX_TOTAL_BYTES:
        raise RuntimeError("Release app exceeds bounded inspection size")
    for path in paths:
        relative = path.relative_to(app).as_posix()
        entry = file_record(path, relative)
        with path.open("rb") as stream:
            magic = stream.read(4)
        if magic in MACHO_MAGICS:
            if len(native) >= 64:
                raise RuntimeError("Native artifact-count bound exceeded")
            entry["kind"] = "mach-o"
            native.append(dict(entry, **verify_native_probe(relative, path, binary_probe(path))))
        else:
            entry["kind"] = "resource-or-metadata"
            if relative in REQUIRED_NATIVE or path.suffix in {".dylib", ".so", ".a"} or entry["executable"]:
                raise RuntimeError("Executable/native-named artifact lacks a recognized Mach-O header")
        entries.append(entry)
    if not REQUIRED_NATIVE <= {row["path"] for row in native} or not os.access(app / "Parlor", os.X_OK):
        raise RuntimeError("Executable app and exact embedded ComposeApp framework are required")
    versions, identities = source_versions(source), source_identity_policy(source)
    info = read_plist(app / "Info.plist")
    expected = {"CFBundleIdentifier": identities["store_bundle_id"], "CFBundleExecutable": "Parlor",
                "CFBundleShortVersionString": versions["PARLOR_VERSION_NAME"], "CFBundleVersion": versions["PARLOR_BUILD_NUMBER"],
                "MinimumOSVersion": "16.0", "CFBundleSupportedPlatforms": ["iPhoneSimulator"],
                "DTPlatformName": "iphonesimulator", "CFBundleLocalizations": ["en", "ar"]}
    if any(info.get(key) != value for key, value in expected.items()):
        raise RuntimeError("Release plist identity/version/platform/deployment/localizations differ from source contract")
    source_info = read_plist(source / "iosApp/iosApp/Info.plist")
    for key in ("NSBonjourServices", "NSLocalNetworkUsageDescription"):
        if info.get(key) != source_info.get(key):
            raise RuntimeError("Shipped local-network declaration differs from source")
    source_privacy = source / "iosApp/iosApp/PrivacyInfo.xcprivacy"
    if read_plist(app / "PrivacyInfo.xcprivacy") != read_plist(source_privacy):
        raise RuntimeError("Shipped privacy manifest differs structurally from source")
    by_path = {row["path"]: row for row in entries}
    matches = []
    for relative in sorted(source_paths):
        packaged = raw_resource_path(relative)
        if packaged is None:
            continue
        expected_file = file_record(source / relative, relative)
        shipped = by_path.get(packaged)
        if shipped is None or shipped["sha256"] != expected_file["sha256"] or shipped["bytes"] != expected_file["bytes"]:
            raise RuntimeError("Exact raw case/font bytes are absent from their shipping Compose namespace")
        matches.append({"source_path": relative, "source_sha256": expected_file["sha256"], "artifact_path": packaged})
    if not matches or not any(row["source_path"].endswith(".json") for row in matches) or not any(
            Path(row["source_path"]).suffix in {".ttf", ".otf"} for row in matches):
        raise RuntimeError("Expected raw case and font source bindings are missing")
    after = {str(path.relative_to(app)): fingerprint(path) for path in artifact_paths(app)}
    if initial != after:
        raise RuntimeError("Package changed during artifact inspection")
    return {"schema_version": 1, "status": "PASS", "execution_kind": "unsigned-simulator-release-artifact-inspection",
            "app_identity": expected, "identity_policy": identities, "native_binaries": native,
            "files": entries, "file_count": len(entries), "total_bytes": total,
            "source_privacy_sha256": file_record(source_privacy, "PrivacyInfo.xcprivacy")["sha256"],
            "shipped_privacy_sha256": by_path["PrivacyInfo.xcprivacy"]["sha256"],
            "privacy_structurally_matches_source": True, "raw_resource_matches": matches, "limitation": LIMITATION}


def source_identity(source: Path) -> dict:
    source = canonical_directory(source)
    environment = {key: value for key, value in os.environ.items() if not key.startswith("GIT_")}
    environment.update(GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull, GIT_TERMINAL_PROMPT="0")

    def git(*arguments):
        return bounded_command(["git", "--literal-pathspecs", "-C", str(source), *arguments],
                               environment=environment, limit=MAX_GIT_BYTES)

    if Path(git("rev-parse", "--show-toplevel").decode().strip()) != source:
        raise RuntimeError("Source is not the exact Git checkout root")
    head, tree = (git("rev-parse", item).decode().strip() for item in ("HEAD", "HEAD^{tree}"))
    if any(re.fullmatch(r"[0-9a-f]{40}", value) is None for value in (head, tree)):
        raise RuntimeError("Missing full source commit/tree identity")
    tracked = set(git("ls-files", "--cached", "-z").decode("utf-8").split("\0")) - {""}
    untracked = set(git("ls-files", "--others", "--exclude-standard", "-z").decode("utf-8").split("\0")) - {""}
    paths, excluded = [], 0
    for relative in sorted(tracked | untracked):
        if (relative.startswith(EXCLUDED_ROOTS) or protected_name(relative) or
                (relative not in tracked and not relative.startswith(BUILD_ROOTS) and
                 relative not in {"AGENTS.md", ".gitattributes", ".gitignore", "build.gradle.kts", "settings.gradle.kts", "gradle.properties"})):
            excluded += 1
            continue
        parsed = PurePosixPath(relative)
        if parsed.is_absolute() or ".." in parsed.parts or "\\" in relative or any(ord(char) < 32 for char in relative):
            raise RuntimeError("Unsafe source-binding path")
        paths.append(relative)
    if not paths or len(paths) > MAX_FILES or sum(len(path.encode()) + 1 for path in paths) > 256 * 1024:
        raise RuntimeError("Source path inventory exceeds its bound or is empty")
    records, total = [], 0
    for relative in paths:
        total += fingerprint(source / relative)[2]
        if total > MAX_TOTAL_BYTES:
            raise RuntimeError("Source inventory exceeds bounded inspection size")
        records.append(file_record(source / relative, relative))
    # An explicit safe path list avoids reading excluded signing/personal files.
    diff = git("diff", "--binary", "--no-ext-diff", "--no-textconv", "HEAD", "--", *paths)
    encoded = json.dumps(records, sort_keys=True, separators=(",", ":")).encode()
    return {"root": str(source), "head": head, "tree": tree, "diff_sha256": hashlib.sha256(diff).hexdigest(),
            "diff_scope": "tracked differences for included non-protected source paths",
            "source_manifest_sha256": hashlib.sha256(encoded).hexdigest(), "files": records,
            "untracked_included": sorted(set(paths) & untracked), "excluded_paths": excluded,
            "exclusions": "Audit/design material, protected signing/local config, and unrelated untracked personal files are not read."}


def verify_package(app: Path, source: Path, *, binary_probe=None, identity_reader=None) -> dict:
    source = canonical_directory(source)
    identity_reader = identity_reader or source_identity
    before = identity_reader(source)
    result = inspect_release_artifacts(app, source, binary_probe or native_probe,
                                       [row["path"] for row in before["files"]])
    after = identity_reader(source)
    # Other reviewers may create excluded audit evidence while we inspect. Such
    # files are not source inputs and must not invalidate an otherwise stable tree.
    if {key: value for key, value in after.items() if key != "excluded_paths"} != {
            key: value for key, value in before.items() if key != "excluded_paths"}:
        raise RuntimeError("Source changed during package verification")
    result.update(source=before, source_unchanged_during_inspection=True,
                  inspector_sha256=file_record(Path(__file__).absolute(), "ios_release_artifacts.py")["sha256"])
    return result


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app", type=Path, required=True)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--json", action="store_true")
    arguments = parser.parse_args(argv)
    try:
        result = verify_package(arguments.app, arguments.source)
        encoded = json.dumps(result, sort_keys=True, allow_nan=False)
        if len(encoded.encode()) > MAX_REPORT_BYTES:
            raise RuntimeError("Artifact report exceeds its acceptance bound")
    except (OSError, ValueError, RuntimeError, KeyError, TypeError, subprocess.SubprocessError) as failure:
        result = {"status": "FAIL", "error": str(failure)[:512], "limitation": LIMITATION}
        encoded = json.dumps(result, sort_keys=True)
    print(encoded if arguments.json else result["status"] + ": " + result.get("error", LIMITATION))
    return 0 if result["status"] == "PASS" else 2


if __name__ == "__main__":
    raise SystemExit(main())
