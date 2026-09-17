#!/usr/bin/env python3
"""Build/probe native installers with the wrapper and JDK 21; never publish."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform as host
import plistlib
import shutil
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

from scripts.release.github_distribution import OUT, ROOT, PLATFORMS, canonical, digest, filename, require, run, version
from scripts.release.distribution_image import inventory_digest, prepare_skiko, require_same_image, verify_no_embedded_native
# Reuse the existing standalone artifact inspector without duplicating its privacy/manifest allowlists.
sys.path.insert(0, str(ROOT / "scripts/verification"))
from scripts.verification.android_release_artifacts import inspect_manifest

WORK = OUT / "work"
WINDOWS_UPGRADE_UUID = "fd36ca1f-9707-486a-8104-22b16cb475a6"


def check_host(platform: str) -> None:
    expected = {"android": ("Linux", "x86_64"), "linux-x64": ("Linux", "x86_64"),
                "windows-x64": ("Windows", "amd64"), "macos-arm64": ("Darwin", "arm64"), "macos-x64": ("Darwin", "x86_64")}
    system, arch = expected[platform]
    require(host.system() == system and host.machine().lower() == arch.lower(), "Wrong packaging host/architecture")


def java_tool(name: str) -> str:
    home = Path(os.environ["JAVA_HOME"])
    tool = home / "bin" / (name + (".exe" if host.system() == "Windows" else ""))
    require(tool.is_file(), "JDK tool is missing")
    return str(tool)


def gradle(tasks: list[str]) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    # This step never receives signing credentials. Build logs are not in the frozen/public bundle.
    command = [str(ROOT / ("gradlew.bat" if host.system() == "Windows" else "gradlew")), *tasks,
               "--dependency-verification=strict", "--no-daemon", "--console=plain", "--stacktrace"]
    with (OUT / "build.log").open("w", encoding="utf-8") as output:
        result = subprocess.run(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, timeout=5400, check=False)
    if result.returncode:
        print((OUT / "build.log").read_text(encoding="utf-8")[-18000:])
    require(result.returncode == 0, "Wrapper build failed; see build/github-distribution/build.log")


def image_path(platform: str) -> Path:
    return ROOT / "composeApp/build/compose/binaries/main/app" / ("Parlor.app" if platform.startswith("macos-") else "Parlor")


def launcher(image: Path, platform: str) -> Path:
    if platform.startswith("macos-"):
        return image / "Contents/MacOS/Parlor"
    return image / ("Parlor.exe" if platform.startswith("windows-") else "bin/Parlor")


def probe(image: Path, platform: str) -> None:
    # The opt-in entry returns before Koin/storage/network initialization. Still isolate its home and native extraction.
    with tempfile.TemporaryDirectory(prefix="parlor-runtime-probe-") as temporary:
        env = {key: value for key, value in os.environ.items()
               if not key.startswith("GH_DIST_") and key not in {"GH_TOKEN", "GITHUB_TOKEN", "JDK_JAVA_OPTIONS"}}
        env["JAVA_TOOL_OPTIONS"] = f'-Duser.home="{temporary}" -Djava.io.tmpdir="{temporary}"'
        text = run([str(launcher(image, platform)), "--verify-distribution"], timeout=60, env=env)
        require("PARLOR_DISTRIBUTION_SMOKE_OK" in text, "Packaged crypto/native runtime probe did not complete")


def prepare_image(image: Path, platform: str) -> None:
    if platform.startswith("macos-"):
        # Prove the disposable JDK/Compose-produced ad-hoc app is valid BEFORE our
        # documented resource transformation. Never repair an unknown signature.
        run(["codesign", "--verify", "--strict", str(image)])
        metadata = run(["codesign", "--display", "--verbose=4", str(image)])
        flags = re.search(r"flags=0x([0-9a-f]+)", metadata)
        require(flags is not None and int(flags[1], 16) & 2 != 0, "Expected a fresh ad-hoc rehearsal app, not a signed release")
        localize_mac(image)
    native = prepare_skiko(image, platform)
    if platform.startswith("macos-"):
        run(["codesign", "--force", "--sign", "-", str(image)])
        run(["codesign", "--verify", "--strict", str(image)])
    verify_no_embedded_native(image)
    (OUT / "prepared-image.json").write_bytes(canonical({
        "platform": platform, "native": native, "image_sha256": inventory_digest(image),
    }))


def inspect_notices(archives: list[Path], apk: bool = False) -> None:
    manifest = json.loads((ROOT / "config/third-party-notices.json").read_text(encoding="utf-8"))
    prefix = ("assets/" if apk else "") + "composeResources/com.parlor.app.resources/files/legal/"
    found = {}
    for path in archives:
        with zipfile.ZipFile(path) as archive:
            require(len(archive.infolist()) <= 100000, "Excessive packaged ZIP entries")
            for entry in archive.infolist():
                if entry.filename.startswith(prefix) and not entry.is_dir():
                    name = entry.filename[len(prefix):]
                    require(name not in found and entry.file_size <= 1024 * 1024, "Duplicate or oversized packaged notice")
                    found[name] = (entry.file_size, hashlib.sha256(archive.read(entry)).hexdigest())
    expected = {item["name"]: (item["bytes"], item["sha256"]) for item in manifest["files"]}
    require(found == expected, "Packaged notices differ from the reviewed source")


def android_tool(name: str) -> str:
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    if name == "apkanalyzer":
        tool = sdk / "cmdline-tools/latest/bin/apkanalyzer"
    else:
        tool = sdk / "build-tools/36.0.0" / name
    require(tool.is_file(), "Pinned Android SDK tool is missing")
    return str(tool)


def inspect_apk(path: Path) -> None:
    manifest = run([android_tool("apkanalyzer"), "manifest", "print", str(path)])
    inspect_manifest(manifest.encode(), version()["name"], version()["build"])
    inspect_notices([path], apk=True)


def localize_mac(image: Path) -> None:
    for language in ("en", "ar"):
        # Reuse the reviewed bilingual native LAN rationale; do not copy iOS bundle-name substitutions.
        source = (ROOT / f"iosApp/iosApp/{language}.lproj/InfoPlist.strings").read_text(encoding="utf-8")
        lines = [line for line in source.splitlines() if line.startswith('"NSLocalNetworkUsageDescription"')]
        require(len(lines) == 1, "Missing native Local Network translation")
        target = image / "Contents/Resources" / f"{language}.lproj"
        target.mkdir(exist_ok=True)
        (target / "InfoPlist.strings").write_text(lines[0] + "\n", encoding="utf-8")
    inspect_mac_metadata(image)


def inspect_mac_metadata(image: Path) -> None:
    with (image / "Contents/Info.plist").open("rb") as stream:
        info = plistlib.load(stream)
    require(info.get("CFBundleIdentifier") == "me.parlor.desktop" and info.get("CFBundleShortVersionString") == version()["name"],
            "macOS app identity or version differs")
    require(info.get("CFBundleVersion") == str(version()["build"]), "macOS build number differs from the version source")
    require(info.get("LSMinimumSystemVersion") == "11.0", "macOS minimum version must cover the JDK/Skiko runtime")
    require(info.get("NSBonjourServices") == ["_p2pkit2._tcp"] and bool(info.get("NSLocalNetworkUsageDescription")), "macOS LAN declarations differ")


def package(platform: str) -> Path:
    image = image_path(platform)
    final = WORK / filename(platform, version())
    require(image.is_dir() and not image.is_symlink(), "Missing app image")
    require(not (OUT / "frozen").exists(), "Cannot repackage frozen bytes")
    if platform.startswith("macos-"):
        stage = WORK / "dmg-root"
        if stage.exists():
            shutil.rmtree(stage)  # Owned, reproducible package stage only, never an installed app.
        stage.mkdir()
        shutil.copytree(image, stage / image.name, symlinks=True)
        (stage / "Applications").symlink_to("/Applications")
        run(["hdiutil", "create", "-quiet", "-ov", "-volname", "Parlor", "-srcfolder", str(stage), "-format", "UDZO", str(final)], timeout=600)
        run(["hdiutil", "verify", str(final)], timeout=180)
    else:
        output = WORK / "installer"
        output.mkdir(exist_ok=True)
        for old in output.glob("*.*"):
            require(old.suffix in {".msi", ".deb"} and not old.is_symlink(), "Unexpected installer output")
            old.unlink()
        command = [java_tool("jpackage"), "--type", PLATFORMS[platform], "--dest", str(output), "--name", "Parlor",
                   "--app-image", str(image), "--app-version", version()["name"], "--vendor", "Parlor"]
        if platform.startswith("windows-"):
            command += ["--win-per-user-install", "--win-menu", "--win-shortcut", "--win-upgrade-uuid", WINDOWS_UPGRADE_UUID]
        else:
            command += ["--linux-package-name", "parlor", "--linux-app-category", "Game", "--linux-app-release", str(version()["build"])]
        run(command, timeout=600)
        files = list(output.glob(f"*.{PLATFORMS[platform]}"))
        require(len(files) == 1, "Expected one native installer")
        shutil.copyfile(files[0], final)
    return final


def windows_installer_properties(path: Path) -> dict[str, str]:
    """Read MSI identity without installing/executing it. All native handles are scoped."""
    import ctypes
    from ctypes import wintypes

    require(host.system() == "Windows", "MSI inspection requires Windows")
    msi = ctypes.WinDLL("msi")
    handle, result = wintypes.UINT, wintypes.UINT
    signatures = {
        "MsiOpenDatabaseW": [wintypes.LPCWSTR, wintypes.LPCWSTR, ctypes.POINTER(handle)],
        "MsiDatabaseOpenViewW": [handle, wintypes.LPCWSTR, ctypes.POINTER(handle)],
        "MsiViewExecute": [handle, handle], "MsiViewFetch": [handle, ctypes.POINTER(handle)],
        "MsiRecordGetStringW": [handle, wintypes.UINT, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD)],
        "MsiCloseHandle": [handle],
    }
    for name, arguments in signatures.items():
        function = getattr(msi, name)
        function.argtypes, function.restype = arguments, result
    database, view = handle(), handle()
    try:
        require(msi.MsiOpenDatabaseW(str(path), None, ctypes.byref(database)) == 0, "Cannot inspect MSI database")
        query = "SELECT `Property`, `Value` FROM `Property` WHERE `Property` = 'ProductName' OR `Property` = 'ProductVersion' OR `Property` = 'UpgradeCode'"
        require(msi.MsiDatabaseOpenViewW(database, query, ctypes.byref(view)) == 0 and msi.MsiViewExecute(view, 0) == 0,
                "Cannot query MSI identity")
        values = {}
        for _ in range(4):
            record = handle()
            status = msi.MsiViewFetch(view, ctypes.byref(record))
            if status == 259:  # ERROR_NO_MORE_ITEMS
                return values
            try:
                require(status == 0, "Cannot read MSI identity")
                fields = []
                for field in (1, 2):
                    buffer, size = ctypes.create_unicode_buffer(256), wintypes.DWORD(256)
                    require(msi.MsiRecordGetStringW(record, field, buffer, ctypes.byref(size)) == 0, "Invalid or excessive MSI identity")
                    fields.append(buffer.value)
                require(fields[0] not in values, "Duplicate MSI identity property")
                values[fields[0]] = fields[1]
            finally:
                if record.value:
                    msi.MsiCloseHandle(record)
        raise RuntimeError("Excessive MSI identity rows")
    finally:
        for item in (view, database):
            if item.value:
                msi.MsiCloseHandle(item)


def inspect_windows_metadata(path: Path) -> None:
    properties = windows_installer_properties(path)
    require(properties.get("ProductName") == "Parlor", "MSI application identity differs")
    require(properties.get("ProductVersion") == version()["name"], "MSI product version differs")
    require(properties.get("UpgradeCode", "").lower() == "{" + WINDOWS_UPGRADE_UUID + "}", "MSI upgrade identity differs")


def inspect_installer(platform: str, path: Path, verify_image=None) -> None:
    if platform.startswith("macos-"):
        with tempfile.TemporaryDirectory(prefix="parlor-dmg-inspect-") as temporary:
            mount = Path(temporary) / "mount"
            mount.mkdir()
            attached = False
            try:
                run(["hdiutil", "attach", "-readonly", "-nobrowse", "-mountpoint", str(mount), str(path)], timeout=120)
                attached = True
                app = mount / "Parlor.app"
                require_same_image(image_path(platform), app)
                inspect_mac_metadata(app)
                if verify_image is not None:
                    verify_image(app)
                inspect_notices(list(app.rglob("*.jar")))
                probe(app, platform)
            finally:
                if attached:
                    run(["hdiutil", "detach", str(mount)], timeout=60)
    elif platform == "linux-x64":
        require(run(["dpkg-deb", "-f", str(path), "Package"]).strip() == "parlor", "Debian package identity differs")
        require(run(["dpkg-deb", "-f", str(path), "Architecture"]).strip() == "amd64", "Debian architecture differs")
        require(run(["dpkg-deb", "-f", str(path), "Version"]).strip() == f"{version()['name']}-{version()['build']}",
                "Debian version/build differs")
        with tempfile.TemporaryDirectory(prefix="parlor-deb-inspect-") as temporary:
            run(["dpkg-deb", "-x", str(path), temporary])
            apps = list(Path(temporary).glob("opt/*/bin/Parlor"))
            require(len(apps) == 1, "Debian launcher is missing or ambiguous")
            require_same_image(image_path(platform), apps[0].parent.parent)
            if verify_image is not None:
                verify_image(apps[0].parent.parent)
            probe(apps[0].parent.parent, platform)
            inspect_notices(list(Path(temporary).rglob("*.jar")))
    else:
        inspect_windows_metadata(path)
        with tempfile.TemporaryDirectory(prefix="parlor-msi-inspect-") as temporary:
            run(["msiexec.exe", "/a", str(path), "/qn", f"TARGETDIR={temporary}"], timeout=180)
            apps = list(Path(temporary).rglob("Parlor.exe"))
            require(len(apps) == 1, "MSI launcher is missing or ambiguous")
            require_same_image(image_path(platform), apps[0].parent)
            if verify_image is not None:
                verify_image(apps[0].parent)
            probe(apps[0].parent, platform)
            inspect_notices(list(Path(temporary).rglob("*.jar")))


def build(platform: str) -> None:
    check_host(platform)
    require(not WORK.exists() and not (OUT / "frozen").exists(), "Refusing to overwrite a previous package build")
    WORK.mkdir(parents=True)
    if platform == "android":
        gradle([":composeApp:verifyApplicationIdentities", ":composeApp:assembleRelease"])
        apks = list((ROOT / "composeApp/build/outputs/apk/release").glob("*.apk"))
        require(len(apks) == 1, "Expected one release APK")
        final = WORK / filename(platform, version())
        shutil.copyfile(apks[0], final)
        inspect_apk(final)
    else:
        gradle(["productionDesktopCheck", ":composeApp:createDistributable"])
        image = image_path(platform)
        prepare_image(image, platform)
        inspect_notices(list(image.rglob("*.jar")))
        probe(image, platform)
        final = package(platform)
        inspect_installer(platform, final)
    receipt = {"passed": True, "signing": "unsigned-rehearsal", "artifact_sha256": digest(final), "package_inspection": True}
    if platform != "android":
        receipt["image_sha256"] = inventory_digest(image)
    (OUT / "validation.json").write_bytes(canonical(receipt))
    print(f"Verified unsigned build rehearsal: {final.name}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=["build", "package", "inspect"])
    parser.add_argument("--platform", choices=list(PLATFORMS), required=True)
    args = parser.parse_args()
    if args.operation == "build":
        build(args.platform)
    elif args.operation == "package":
        package(args.platform)
    else:
        inspect_installer(args.platform, WORK / filename(args.platform, version()))


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, subprocess.SubprocessError) as error:
        print(f"Packaging failed: {type(error).__name__}: {error}", file=sys.stderr)
        raise SystemExit(2)
