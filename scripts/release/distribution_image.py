#!/usr/bin/env python3
"""Closed native payload preparation and full installed-image comparison."""
from __future__ import annotations

import hashlib
import json
import os
import re
import stat
import tempfile
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

from scripts.release.github_distribution import MAX_FILE, ROOT, canonical, digest, require, run

NATIVE_NAMES = {
    "macos-arm64": "libskiko-macos-arm64.dylib", "macos-x64": "libskiko-macos-x64.dylib",
    "windows-x64": "skiko-windows-x64.dll", "linux-x64": "libskiko-linux-x64.so",
}
NATIVE_SUFFIXES = (".dylib", ".jnilib", ".dll", ".so")


def app_directory(image: Path, platform: str) -> Path:
    return image / ("Contents/app" if platform.startswith("macos-") else "app" if platform == "windows-x64" else "lib/app")


def verified_skiko_jar(platform: str) -> Path:
    version = json.loads((ROOT / "config/third-party-notices.json").read_text())["upstream_versions"]["skiko"]
    name = "skiko-awt-runtime-" + platform
    metadata = ET.parse(ROOT / "gradle/verification-metadata.xml").getroot()
    artifacts = metadata.findall(f".//v:component[@group='org.jetbrains.skiko'][@name='{name}'][@version='{version}']/v:artifact[@name='{name}-{version}.jar']/v:sha256",
                                 {"v": "https://schema.gradle.org/dependency-verification"})
    require(len(artifacts) == 1, "Native runtime must have one reviewed Maven checksum")
    expected = artifacts[0].get("value")
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.jetbrains.skiko" / name / version
    matches = [path for path in cache.glob(f"*/{name}-{version}.jar") if digest(path) == expected]
    require(len(matches) == 1, "Original strictly verified native JAR is missing or ambiguous")
    return matches[0]


def verify_compose_native(native: Path, original: bytes, platform: str) -> dict:
    """Retain only pinned bytes or an independently proven packaging signature.

    jpackage re-signs its generated macOS image. Rehashing that copy alone would
    hide code changes; replacing it with vendor bytes would undo the generated
    image's signing policy. Compare complete canonical signatures on DISPOSABLE
    copies instead. Never strip or replace a valid vendor signature in an input
    image or Maven cache, and never repair an invalid generated signature.
    """
    require(native.is_file() and not native.is_symlink() and native.stat().st_size <= 128 * 1024 * 1024,
            "Missing, redirected or oversized extracted native code")
    expected, before = hashlib.sha256(original).hexdigest(), digest(native)
    if before == expected:
        return {"method": "exact-pinned-bytes", "original_sha256": expected}
    require(platform.startswith("macos-"), "Extracted native code differs from the pinned JAR")
    metadata = run(["codesign", "--display", "--verbose=4", str(native)], success_codes=(0, 1))
    if metadata.strip().endswith(": code object is not signed at all"):
        require(platform == "macos-x64", "Only Intel packaging may produce unsigned native code")
    else:
        flags = re.search(r"\bflags=0x([0-9a-fA-F]+)\b", metadata)
        require(flags is not None and int(flags[1], 16) & 2 != 0,
                "Refusing to replace or normalize a non-ad-hoc native signature")
        run(["codesign", "--verify", "--strict", str(native)])
    with tempfile.TemporaryDirectory(prefix="parlor-native-equivalence-") as temporary:
        reference, generated = (Path(temporary) / name for name in ("reference.dylib", "generated.dylib"))
        reference.write_bytes(original)
        generated.write_bytes(native.read_bytes())
        for scratch in (reference, generated):
            text = run(["codesign", "--display", "--verbose=4", str(scratch)], success_codes=(0, 1))
            if not text.strip().endswith(": code object is not signed at all"):
                run(["codesign", "--verify", "--strict", str(scratch)])
                run(["codesign", "--remove-signature", str(scratch)])
            # Removing signatures alone leaves differing __LINKEDIT allocation
            # sizes. A common ad-hoc identity canonicalizes only signing data;
            # the COMPLETE resulting files must match, not just code sections.
            run(["codesign", "--force", "--sign", "-", "--identifier", "me.parlor.native-equivalence", str(scratch)])
            run(["codesign", "--verify", "--strict", str(scratch)])
        payload = digest(reference)
        require(payload == digest(generated), "Generated native executable payload differs from the pinned JAR")
    require(digest(native) == before, "Native input changed during equivalence verification")
    return {"method": "disposable-signature-roundtrip", "original_sha256": expected, "canonical_sha256": payload}


def prepare_skiko(image: Path, platform: str) -> dict:
    """Extract the selected, verified JNI payload; omit unused native architectures.

    Compose already extracts macOS JNI libraries. On other hosts we perform the
    same transformation explicitly so signing never misses code inside a JAR.
    We never edit Gradle's cache or a signed vendor JAR. Other JARs are unchanged.
    """
    directory = app_directory(image, platform)
    native_name = NATIVE_NAMES[platform]
    version = json.loads((ROOT / "config/third-party-notices.json").read_text())["upstream_versions"]["skiko"]
    jars = list(directory.glob(f"skiko-awt-runtime-*-{version}-*.jar"))
    require(len(jars) == 1, "Expected exactly one reviewed Skiko runtime JAR")
    jar = jars[0]
    # Verify the generated native copy against the exact Maven-pinned JAR before
    # refreshing Compose's stale sibling checksum. Only signing data may differ.
    # We transform this disposable app image, never the dependency cache.
    original = verified_skiko_jar(platform)
    before = digest(original)
    native = directory / native_name
    expected_names = {native_name}
    if platform.startswith("macos-"):
        expected_names = {NATIVE_NAMES["macos-arm64"], NATIVE_NAMES["macos-x64"]}
    # Compose also unpacks the Windows ICU data beside Skiko. It is required at
    # runtime and must match the original pinned JAR, not disappear or be ignored.
    auxiliary = {"icudtl.dat"} if platform == "windows-x64" else set()
    auxiliary_sha256 = {}
    native_verification = {}
    removable = expected_names | {name + ".sha256" for name in expected_names} | auxiliary
    removed = []
    with zipfile.ZipFile(original) as archive:
        entries = archive.infolist()
        names = {entry.filename for entry in entries}
        require(len(entries) <= 1000 and len(names) == len(entries), "Invalid native JAR entries")
        require(not any(name.upper().endswith((".SF", ".RSA", ".DSA", ".EC")) for name in names),
                "Refusing to modify a signed vendor JAR")
        require({name for name in names if name.endswith(NATIVE_SUFFIXES)} <= expected_names, "Unreviewed JNI payload")
        with zipfile.ZipFile(jar) as prepared:
            leftovers = {entry.filename for entry in prepared.infolist()} - removable
            require(leftovers == names - removable and all(prepared.read(name) == archive.read(name) for name in leftovers),
                    "Packager changed non-native vendor resources")
        require(auxiliary <= names, "Pinned native runtime is missing required auxiliary data")
        for name in auxiliary:
            info = archive.getinfo(name)
            require(0 < info.file_size <= 128 * 1024 * 1024 and not info.flag_bits & 1, "Invalid native auxiliary data size")
            checksum = hashlib.sha256(archive.read(name)).hexdigest()
            require(digest(directory / name) == checksum, "Extracted native auxiliary data differs from the pinned JAR")
            auxiliary_sha256[name] = checksum
        for name in expected_names & names:
            info = archive.getinfo(name)
            require(0 < info.file_size <= 128 * 1024 * 1024 and not info.flag_bits & 1, "Invalid JNI payload size")
            content = archive.read(name)
            checksum = hashlib.sha256(content).hexdigest()
            require(archive.read(name + ".sha256").decode("ascii").strip().lower() == checksum, "Native JAR checksum differs")
            if name == native_name:
                require(not native.is_symlink(), "Redirected native output")
                if not native.exists() and not platform.startswith("macos-"):
                    native.write_bytes(content)
                native_verification = verify_compose_native(native, content, platform)
                native.chmod(0o755)
                checksum_file = directory / (name + ".sha256")
                require(not checksum_file.is_symlink(), "Redirected native checksum")
                checksum_file.write_text(digest(native), encoding="ascii")
        require(native.is_file() and not native.is_symlink(), "Selected native architecture was not extracted")
        require((directory / (native_name + ".sha256")).read_text(encoding="ascii").strip().lower() == digest(native),
                "Extracted native checksum differs")
        removed = sorted(names & removable)
        if removed:
            temporary = jar.with_suffix(".prepared")
            require(not temporary.exists(), "Native JAR staging already exists")
            try:
                with zipfile.ZipFile(temporary, "x", compression=zipfile.ZIP_DEFLATED) as output:
                    for entry in entries:
                        require(entry.file_size <= 128 * 1024 * 1024, "Overlarge runtime JAR entry")
                        if entry.filename not in removable:
                            output.writestr(entry, archive.read(entry))
            except BaseException:
                temporary.unlink(missing_ok=True)
                raise
    if removed:
        os.replace(temporary, jar)
    cfg = directory / "Parlor.cfg"
    text = cfg.read_text(encoding="utf-8")
    option = "java-options=-Dskiko.library.path=$APPDIR"
    require(text.count("[JavaOptions]") == 1 and text.count("skiko.library.path") <= 1, "Unexpected native launcher configuration")
    if "skiko.library.path" in text:
        require(option in text.splitlines(), "Unsafe native loading directory")
    else:
        cfg.write_text(text.replace("[JavaOptions]", "[JavaOptions]\n" + option), encoding="utf-8")
    verify_no_embedded_native(image)
    return {"original_jar_sha256": before, "prepared_jar_sha256": digest(jar),
            "native_filename": native_name, "native_sha256": digest(native), "auxiliary_sha256": auxiliary_sha256,
            "native_verification": native_verification, "omitted_entries": removed}


def verify_no_embedded_native(image: Path) -> None:
    for jar in image.rglob("*.jar"):
        with zipfile.ZipFile(jar) as archive:
            require(len(archive.infolist()) <= 100000, "Excessive application JAR entries")
            require(not any(entry.filename.lower().endswith(NATIVE_SUFFIXES) for entry in archive.infolist()),
                    "Native code remains hidden inside an application JAR")


def materialize_linux_runtime_notices(image: Path, platform: str) -> list[dict]:
    """jpackage's Linux PathGroup.copy dereferences files, including JDK legal links.

    Canonicalize only bounded, internal notice aliases BEFORE probing/hashing the
    source image. Do not ignore differences in the installed image or touch the
    upstream JDK. Native libraries and executable links are never normalized.
    """
    if platform != "linux-x64":
        return []
    legal = image / "lib/runtime/legal"
    require(legal.is_dir() and not legal.is_symlink(), "JDK legal notices are missing or redirected")
    root = legal.resolve(strict=True)
    aliases = []
    for path in sorted(legal.rglob("*")):
        if path.is_symlink():
            target = path.resolve(strict=True)
            require(target.is_relative_to(root) and target.is_file() and target.stat().st_size <= 1024 * 1024,
                    "Runtime notice alias escapes legal files or exceeds its bound")
            aliases.append((path, target, os.readlink(path)))
    records = []
    for path, target, original_link in aliases:
        content = target.read_bytes()
        checksum = hashlib.sha256(content).hexdigest()
        temporary = path.with_name(path.name + ".parlor-notice")
        require(not temporary.exists() and not temporary.is_symlink(), "Notice staging already exists")
        try:
            with temporary.open("xb") as output:
                output.write(content)
            temporary.chmod(0o644)
            os.replace(temporary, path)  # Replaces the alias itself, never its target.
        finally:
            temporary.unlink(missing_ok=True)
        require(not path.is_symlink() and hashlib.sha256(path.read_bytes()).hexdigest() == checksum,
                "Materialized notice bytes changed")
        records.append({"path": path.relative_to(image).as_posix(), "original_link": original_link, "sha256": checksum})
    return records


def inventory(image: Path) -> dict:
    """Content custody, not timestamps. JVM legal symlinks must stay inside the app.

    jpackage's .jpackage.xml is a non-executable build recipe intentionally not
    installed on all platforms. No executable/resource/signature is excluded.
    """
    require(image.is_dir() and not image.is_symlink(), "Invalid application image")
    root = image.resolve(strict=True)
    result, total = {}, 0
    paths = sorted(image.rglob("*"))
    require(len(paths) <= 50000, "Application image has too many paths")
    for path in paths:
        relative = path.relative_to(image).as_posix()
        if path.name == ".jpackage.xml":
            continue
        metadata = path.lstat()
        if stat.S_ISLNK(metadata.st_mode):
            require(path.resolve(strict=True).is_relative_to(root), "Application symlink escapes its image")
            result[relative] = {"link": os.readlink(path)}
        elif stat.S_ISREG(metadata.st_mode):
            require(metadata.st_size <= MAX_FILE, "Application file is overlarge")
            total += metadata.st_size
            require(total <= 3 * MAX_FILE, "Application image exceeds its bound")
            # Some legal notice files can be empty; unlike downloadable binaries that is valid here.
            value = hashlib.sha256()
            with path.open("rb") as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b""):
                    value.update(block)
            result[relative] = {"bytes": metadata.st_size, "sha256": value.hexdigest()}
        else:
            require(stat.S_ISDIR(metadata.st_mode), "Nonregular application payload")
    require(bool(result), "Empty application image")
    return result


def inventory_digest(image: Path) -> str:
    return hashlib.sha256(canonical(inventory(image))).hexdigest()


def require_same_image(original: Path, installed: Path) -> None:
    expected, actual = inventory(original), inventory(installed)
    differences = [("missing", name) for name in sorted(expected.keys() - actual.keys())]
    differences += [("unexpected", name) for name in sorted(actual.keys() - expected.keys())]
    differences += [("changed", name) for name in sorted(expected.keys() & actual.keys()) if expected[name] != actual[name]]
    # Package-relative paths only: never log resource contents or native tool
    # output. A closed, bounded diff makes a packaging failure actionable.
    require(not differences, "Installed payload differs from the verified application image: " +
            json.dumps(differences[:20], ensure_ascii=True) + (" (more differences omitted)" if len(differences) > 20 else ""))
