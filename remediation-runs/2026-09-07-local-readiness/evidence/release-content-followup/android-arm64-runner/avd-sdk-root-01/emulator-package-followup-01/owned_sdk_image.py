"""DRAFT: authentic owned APFS image/emulator packages and CLI20 SDK selection.

No subprocesses or SDK writes occur on import. The owning runner must attest
and finally remove its complete temporary root after stopping its workers.
Native execution/pure tests require the coordinator's build/runtime lane.
"""
from __future__ import annotations

import ctypes
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import stat


IMAGE_RELATIVE = Path("system-images/android-35/google_apis/arm64-v8a")
EMULATOR_RELATIVE = Path("emulator")
# Both selected public packages share these ceilings; no full-SDK scan.
MAX_ENTRIES = 512
MAX_IMAGE_BYTES = 6 * 1024 ** 3
MIN_FREE_BYTES = 8 * 1024 ** 3
METADATA_HASHES = {
    "package.xml": "9bbfa7f3f8b7386ae31ee3130748aeb32a37c3404d2be5fb8ac7f0f731ea9642",
    "source.properties": "bd5cb4bc86ef478b0f37a034aa2b460165b391e8cb6504cb13b8ca1c462c4848",
}
# Exact installed Emulator36.6.11 metadata, hardware definitions and tools.
# Clone the COMPLETE authentic package, not a synthetic metadata facade.
EMULATOR_HASHES = {
    "package.xml": "5c06a9b6ea0d9f436d351e8a9b2420619082b30222d650643a572ca5dd6ab7c8",
    "source.properties": "a0cdf354b08f5d21b426dd6d8d58a3c6e5d1527d84740bba1df88b4249ca32e4",
    "lib/hardware-properties.ini": "13a89cc7cfa84c5fe48cd7eb8d296bca321dcd0d90f7bcb751efd963ae2c653d",
    "emulator": "c12c8d72da94cb5abd7f9d2578923d05ff94ea827f0ee593c6e563c96edc0d42",
    "mksdcard": "76352e7a02e3e1cd3b577029d0fd59e312e3931aa6774776a941fe4a979c8eca",
}
EMULATOR_EXECUTABLES = ("emulator", "mksdcard")
CLI20_PROPERTIES_SHA256 = "215e11e90893196549e86dfd6a024f20848322dc7a3d694bc4847b8e6d849ad1"
JAVA_ENV_OPTIONS = (
    "JAVA_OPTS", "AVDMANAGER_OPTS", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS",
    "_JAVA_OPTIONS", "CLASSPATH",
)


def _identity(info):
    return (info.st_dev, info.st_ino, info.st_mode, info.st_size,
            info.st_mtime_ns, info.st_ctime_ns)


def _inventory(source):
    """No links, special files, unbounded tree, or implicit directory following."""
    rows, logical_bytes = {}, 0

    def visit(path, depth):
        nonlocal logical_bytes
        if depth > 16 or len(rows) >= MAX_ENTRIES:
            raise ValueError("Public image inventory exceeds reviewed bounds")
        info = path.lstat()
        relative = path.relative_to(source).as_posix()
        rows[relative] = _identity(info)
        if stat.S_ISDIR(info.st_mode):
            # Parent replacement is also detected by the final inventory.
            with os.scandir(path) as entries:
                names = []
                for entry in entries:
                    names.append(entry.name)
                    if len(names) > MAX_ENTRIES:
                        raise ValueError("Public image directory exceeds entry bound")
            for name in sorted(names):
                visit(path / name, depth + 1)
        elif stat.S_ISREG(info.st_mode) and info.st_nlink == 1:
            logical_bytes += info.st_size
            if logical_bytes > MAX_IMAGE_BYTES:
                raise ValueError("Public image exceeds logical-size bound")
        else:
            raise ValueError("Public image contains a link or special file: " + relative)

    visit(source, 0)
    return rows, logical_bytes


def _digest_fd(fd, length, checkpoint):
    os.lseek(fd, 0, os.SEEK_SET)
    digest, total = hashlib.sha256(), 0
    while True:
        checkpoint()
        block = os.read(fd, min(1024 * 1024, length - total + 1))
        if not block:
            break
        total += len(block)
        if total > length:
            raise ValueError("Image file grew during bounded hashing")
        digest.update(block)
    if total != length:
        raise ValueError("Image file shrank during bounded hashing")
    return digest.hexdigest()


def _clone_open_file(source_fd, destination_directory_fd, name):
    """One atomic CoW clone. No cp/copyfile/full-copy fallback exists."""
    if platform.system() != "Darwin":
        raise RuntimeError("The reviewed clone adapter requires Darwin")
    libc = ctypes.CDLL("/usr/lib/libSystem.B.dylib", use_errno=True)
    native = libc.fclonefileat
    native.argtypes = [ctypes.c_int, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint32]
    native.restype = ctypes.c_int
    # sys/clonefile.h: NOFOLLOW=1, NOOWNERCOPY=2. Source is an already
    # attested regular-file descriptor; destination is one leaf in an owned fd.
    result = native(source_fd, destination_directory_fd, os.fsencode(name), 0x0003)
    if result != 0:
        error = ctypes.get_errno()
        raise OSError(error, "Owned image clone failed; no copying fallback", name)


def _clone_package(source, destination, inventory, pins, owned, checkpoint, clone_one):
    destination.parent.mkdir(parents=True, mode=0o700, exist_ok=True)
    copied = []
    for relative, expected in inventory.items():
        checkpoint()
        owned.attest()
        original, target = source / relative, destination / relative
        if _identity(original.lstat()) != expected:
            raise ValueError("Public package changed since inventory: " + relative)
        if stat.S_ISDIR(expected[2]):
            target.mkdir(mode=0o700)
            continue
        with os.fdopen(os.open(original, os.O_RDONLY | os.O_NOFOLLOW), "rb") as input_file:
            if _identity(os.fstat(input_file.fileno())) != expected:
                raise ValueError("Public package changed before clone: " + relative)
            digest = _digest_fd(input_file.fileno(), expected[3], checkpoint)
            if relative in pins and digest != pins[relative]:
                raise ValueError("Installed package metadata differs from reviewed revision: " + relative)
            parent_fd = os.open(target.parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
            try:
                if os.fstat(parent_fd).st_dev != expected[0]:
                    raise RuntimeError("Public package clone would cross filesystems")
                clone_one(input_file.fileno(), parent_fd, target.name)
            finally:
                os.close(parent_fd)
            info = target.lstat()
            if (not stat.S_ISREG(info.st_mode) or info.st_nlink != 1
                    or (info.st_dev, info.st_ino) == expected[:2]):
                raise ValueError("Clone did not create an independent regular file")
            # Keep owner rw and existing safe read/execute bits, never setid,
            # sticky or group/other write bits. The entire owned root is0700.
            mode = 0o600 | (expected[2] & 0o155)
            target.chmod(mode)
            if stat.S_IMODE(target.lstat().st_mode) != mode:
                raise ValueError("Owned package permissions were not applied")
            with os.fdopen(os.open(target, os.O_RDONLY | os.O_NOFOLLOW), "rb") as output_file:
                if _digest_fd(output_file.fileno(), expected[3], checkpoint) != digest:
                    raise ValueError("Owned clone differs from its source: " + relative)
            if _identity(os.fstat(input_file.fileno())) != expected:
                raise ValueError("Source changed while cloning: " + relative)
            copied.append({"path": relative, "size": expected[3], "sha256": digest,
                           "mode": format(mode, "04o")})
    return copied


def clone_installed_image(sdk, owned, checkpoint=lambda: None, *, clone_one=None):
    """Clone the selected image AND complete authentic emulator into owned/sdk.

    Source descriptors are read-only. Existing SDK/profile paths are never
    modified or scanned by the CLI. Only these two package roots are inventoried.
    Both packages share the512-entry/6GiB ceiling. Failure leaves partial OWNED
    outputs for the caller's ownership-attested finalizer; no copy fallback.
    """
    clone_one = clone_one or _clone_open_file
    owned.attest()
    sdk = Path(sdk).resolve(strict=True)
    plans = []
    entries, logical_bytes = 0, 0
    for relative, pins, executables in (
            (IMAGE_RELATIVE, METADATA_HASHES, ()),
            (EMULATOR_RELATIVE, EMULATOR_HASHES, EMULATOR_EXECUTABLES)):
        checkpoint()
        source = sdk / relative
        if source.resolve(strict=True) != source or source.is_symlink():
            raise ValueError("Public package path contains symlinks")
        inventory, package_bytes = _inventory(source)
        for required in pins:
            info = inventory.get(required)
            if info is None or not stat.S_ISREG(info[2]):
                raise ValueError("Required public package metadata is absent or not regular: " + required)
        for executable in executables:
            if not inventory[executable][2] & stat.S_IXUSR:
                raise ValueError("Required emulator tool lacks owner execute permission: " + executable)
        entries += len(inventory)
        logical_bytes += package_bytes
        if entries > MAX_ENTRIES or logical_bytes > MAX_IMAGE_BYTES:
            raise ValueError("Selected SDK packages exceed shared entry/size bounds")
        plans.append((relative, source, inventory, package_bytes, pins))
    if shutil.disk_usage(owned.path).free < MIN_FREE_BYTES:
        raise RuntimeError("Insufficient host headroom before owned package clone")
    destination_sdk = owned.path / "sdk"
    destination_sdk.mkdir(mode=0o700)  # Existing directory is never reused.
    packages = []
    for relative, source, inventory, package_bytes, pins in plans:
        copied = _clone_package(source, destination_sdk / relative, inventory, pins,
                                owned, checkpoint, clone_one)
        packages.append({
            "path": relative.as_posix(), "source": str(source),
            "destination": str(destination_sdk / relative), "entries": len(inventory),
            "logical_bytes": package_bytes, "files": copied,
            "files_sha256": hashlib.sha256(json.dumps(copied, sort_keys=True).encode()).hexdigest(),
        })
    # Recheck BOTH sources after the last package, not just each one before it.
    for _relative, source, inventory, _bytes, _pins in plans:
        checkpoint()
        if _inventory(source)[0] != inventory:
            raise ValueError("Public package changed during selected SDK clone")
    owned.attest()
    image = packages[0]
    return {
        "source_image": image["source"], "owned_sdk": str(destination_sdk),
        "owned_image": image["destination"], "logical_bytes": image["logical_bytes"],
        "method": "fclonefileat per regular file; no fallback; source+clone SHA256 compared",
        "files": image["files"], "files_sha256": image["files_sha256"],
        "owned_emulator": packages[1]["destination"], "packages": packages,
        "selected_entries": entries, "selected_logical_bytes": logical_bytes,
    }


def cli20_invocation(java, avdmanager, owned, destination_sdk, env, arguments):
    """Use the installed public classpath, but only the OWNED SDK for scanning.

    Direct argv avoids the wrapper's baked-in SDK and shell/JAVA_OPTS parsing.
    Java 21 executable/toolchain attestation is the caller's responsibility.
    """
    owned.attest()
    destination_sdk = Path(destination_sdk)
    if destination_sdk != owned.path / "sdk" or destination_sdk.resolve(strict=True) != destination_sdk:
        raise ValueError("Unexpected owned SDK root")
    cli = Path(avdmanager).resolve(strict=True).parent.parent
    metadata = cli / "source.properties"
    if hashlib.sha256(metadata.read_bytes()).hexdigest() != CLI20_PROPERTIES_SHA256:
        raise ValueError("Command-line tools version differs from reviewed CLI20")
    classpath = cli / "lib/avdmanager-classpath.jar"
    if not classpath.is_file():
        raise ValueError("Installed CLI20 classpath is missing")
    # The installed CLI derives sdkRoot = toolsdir.parent.parent.
    toolsdir = destination_sdk / "cmdline-tools/owned-placeholder"
    toolsdir.mkdir(parents=True, mode=0o700)
    effective_env = {key: value for key, value in env.items() if key not in JAVA_ENV_OPTIONS}
    effective_env.update(ANDROID_HOME=str(destination_sdk), ANDROID_SDK_ROOT=str(destination_sdk))
    argv = [str(Path(java).resolve(strict=True)), "-Xmx256m",
            "-Dcom.android.sdkmanager.toolsdir=" + str(toolsdir),
            "-Duser.home=" + str(owned.path / "home"),
            "-Djava.io.tmpdir=" + str(owned.path / "tmp"),
            "-XX:ErrorFile=" + str(owned.path / "tmp/hs_err_pid%p.log"),
            "-classpath", str(classpath), "com.android.sdklib.tool.AvdManagerCli"]
    return argv + list(arguments), effective_env


def validate_created_image_path(config, destination_sdk):
    """Reject unexpected AVD output rather than silently redirecting an image."""
    if (config.get("image.sysdir.1") != IMAGE_RELATIVE.as_posix() + "/"
            or "image.sysdir.2" in config):
        raise ValueError("AVD image path did not match the isolated SDK image")
    image = Path(destination_sdk) / IMAGE_RELATIVE
    if image.resolve(strict=True) != image:
        raise ValueError("Created AVD image escapes through a symlink")
    return str(image)
