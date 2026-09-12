#!/usr/bin/env python3
"""Read-only, bounded verification of the reviewed notice supplement.

Python 3.9+, POSIX (the repository's macOS/Linux verification hosts). No network,
extraction, build, Git mutation or legal-completeness claim. See THIRD_PARTY_NOTICES.md.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import struct
import sys
import unicodedata
from urllib.parse import urlsplit
import zipfile
import zlib


MANIFEST_PATH = "config/third-party-notices.json"
RESOURCE_DIRECTORY = "composeApp/src/commonMain/composeResources/files/legal"
NAMESPACE = "com.parlor.app.resources"
CATALOG_PATH = "gradle/libs.versions.toml"
BUILD_PATH = "composeApp/build.gradle.kts"
RESOURCE_SUFFIX = "composeResources/" + NAMESPACE + "/files/legal"
AAB_PREFIX = "base/assets/" + RESOURCE_SUFFIX
APP_PREFIX = "compose-resources/" + RESOURCE_SUFFIX
SCOPE = "REVIEWED_INPUT_NOTICE_SUPPLEMENT_NOT_EXHAUSTIVE_OR_LEGAL_APPROVAL"
EXACT_BYTE_POLICY = "Every manifest resource must occur once at the platform Compose resource path and match source bytes, length and SHA-256; no normalization."
MAX_MANIFEST_BYTES = 128 * 1024
MAX_SOURCE_CONFIG_BYTES = 128 * 1024
MAX_NOTICE_BYTES = 64 * 1024
MAX_TOTAL_NOTICE_BYTES = 256 * 1024
MAX_ARCHIVE_BYTES = 2 * 1024 * 1024 * 1024
MAX_DIRECTORY_BYTES = 16 * 1024 * 1024
MAX_PACKAGE_ENTRIES = 20000
MAX_PATH_BYTES = 1024
MAX_PACKAGE_DEPTH = 24
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
SAFE_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,95}\Z")
NOTICE_NAMES = frozenset({
    "APACHE-2.0.txt", "Skiko-NOTICE.txt", "Skia-LICENSE.txt", "ICU-LICENSE.txt",
    "HarfBuzz-COPYING.txt", "Expat-COPYING.txt", "WebP-COPYING.txt", "WebP-PATENTS.txt",
    "DNG-SDK-NOTICE.txt", "DNG-SDK-PATENTS.txt", "libjpeg-turbo-LICENSE.md",
    "libjpeg-turbo-README.ijg.txt", "Wuffs-Skia-wrapper-LICENSE.txt",
    "Kotlin-ThreeTenBP-LICENSE.txt", "Kotlin-Harmony-NOTICE.txt",
    "Kotlin-MPMC-QUEUE-LICENSE.txt", "libpng-LICENSE.txt", "zlib-LICENSE.txt",
    "Kotlin-Boost-LICENSE.txt", "Kotlin-libbacktrace-LICENSE.txt",
    "Kotlin-Unicode-LICENSE.txt", "HarfBuzz-hb-ucd-ISC-LICENSE.txt",
    "Unicode-V3-LICENSE.txt", "BouncyCastle-1.85-LICENSE.md", "SLF4J-2.0.16-LICENSE.txt",
    "INDEX.txt",
})
CLASSIFICATIONS = frozenset({
    "INDEX", "SHARED_LICENSE_TEXT", "NATIVE_INPUT_NOTICE", "NATIVE_INPUT_LICENSE",
    "NATIVE_INPUT_COMPANION_TERMS", "NATIVE_INPUT_CUSTOM_LICENSE", "STDLIB_INPUT_LICENSE",
    "RUNTIME_INPUT_NOTICE", "OPTIONAL_BINARY_ACKNOWLEDGEMENT", "OBJECT_CODE_NOTICE_EXCEPTION",
    "GENERATED_DATA_LICENSE", "ANDROID_INPUT_LICENSE",
})
UPSTREAM_KEYS = frozenset({
    "skiko", "skiko_commit", "skia_pack", "skia_pack_commit", "skia_commit",
    "kotlin_native", "kotlin_commit", "icu_commit", "harfbuzz_commit", "expat_commit",
    "webp_commit", "dng_sdk_commit", "libjpeg_turbo_commit", "libpng_commit", "zlib_commit",
    "wuffs", "harfbuzz_unicode_data", "kotlin_unicode_data", "unicode_license_accessed",
    "bouncycastle", "slf4j",
})
REQUIRED_ACKNOWLEDGEMENTS = (
    "This software is based in part on the work of the Independent JPEG Group.",
    "This product includes DNG technology under license by Adobe Systems Incorporated.",
)


class VerificationError(ValueError):
    """A bounded, deliberately non-secret diagnostic."""


def require(condition, message):
    if not condition:
        raise VerificationError(message)


def exact_keys(value, keys, label):
    require(type(value) is dict and set(value) == set(keys), "Invalid " + label + " fields")


def text_value(value, limit=1000):
    require(isinstance(value, str) and 0 < len(value) <= limit, "Invalid text field")
    require(all(ord(c) >= 32 and ord(c) != 127 for c in value), "Control character in metadata")
    require(not any(s in value for s in ("/Users/", "/home/", ".gradle/caches", "file://")),
            "Private/local path is not notice provenance")


def digest_value(value):
    require(isinstance(value, str) and SHA256.fullmatch(value), "Invalid SHA-256 field")


def integer(value, lower, upper, label):
    require(type(value) is int and lower <= value <= upper, "Invalid " + label)


def public_url(value):
    text_value(value, 1500)
    parsed = urlsplit(value)
    require(parsed.scheme == "https" and parsed.hostname is not None and parsed.path.startswith("/")
            and parsed.username is None and parsed.password is None and parsed.port in (None, 443)
            and not parsed.fragment, "Provenance must be a public HTTPS URL")
    require(parsed.hostname in {"raw.githubusercontent.com", "chromium.googlesource.com",
                               "android.googlesource.com", "skia.googlesource.com",
                               "www.unicode.org", "repo.maven.apache.org"},
            "Unreviewed provenance origin")


def safe_relative(value, directory=False):
    require(isinstance(value, str) and 0 < len(value.encode("utf-8")) <= MAX_PATH_BYTES,
            "Invalid package path length")
    require(not value.startswith("/") and "\\" not in value and ":" not in value
            and all(ord(c) >= 32 and ord(c) != 127 for c in value), "Unsafe package path")
    value = value[:-1] if directory and value.endswith("/") else value
    parts = value.split("/")
    require(len(parts) <= MAX_PACKAGE_DEPTH and all(p not in ("", ".", "..") for p in parts),
            "Unsafe package path components")
    return value


def alias(value):
    return unicodedata.normalize("NFC", value).casefold()


def no_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "Duplicate JSON key")
        result[key] = value
    return result


def read_manifest(raw):
    try:
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=no_duplicate_keys,
                           parse_constant=lambda _: require(False, "Non-finite JSON value"))
    except (UnicodeError, json.JSONDecodeError, RecursionError) as error:
        raise VerificationError("Malformed notice manifest") from error
    exact_keys(value, {"schema_version", "scope", "resource_directory", "resource_namespace",
                       "resource_count", "catalog", "upstream_versions", "files"}, "manifest")
    integer(value["schema_version"], 1, 1, "schema version")
    require(value["scope"] == SCOPE and value["resource_directory"] == RESOURCE_DIRECTORY
            and value["resource_namespace"] == NAMESPACE, "Unreviewed notice scope or namespace")
    integer(value["resource_count"], len(NOTICE_NAMES), len(NOTICE_NAMES), "resource count")
    catalog = value["catalog"]
    exact_keys(catalog, {"path", "sha256", "line_ending_policy", "pins"}, "catalog")
    require(catalog["path"] == CATALOG_PATH, "Unreviewed catalog path")
    require(catalog["line_ending_policy"] == "CRLF_TO_LF", "Unreviewed catalog hash policy")
    digest_value(catalog["sha256"])
    pins = catalog["pins"]
    require(type(pins) is dict and 1 <= len(pins) <= 64, "Invalid catalog pins")
    for key, version in pins.items():
        require(re.fullmatch(r"[a-z][a-z0-9-]{0,63}", key), "Invalid catalog pin key")
        text_value(version, 64)
    upstream = value["upstream_versions"]
    exact_keys(upstream, UPSTREAM_KEYS, "upstream versions")
    for key, version in upstream.items():
        text_value(version, 64)
        require(re.fullmatch(r"[A-Za-z0-9_.-]+", version), "Invalid upstream version")
        if key.endswith("_commit"):
            require(re.fullmatch(r"[0-9a-f]{40}", version), "Invalid upstream commit")
    require(pins.get("kotlin") == upstream["kotlin_native"], "Kotlin input pin mismatch")
    files = value["files"]
    require(type(files) is list and len(files) == len(NOTICE_NAMES), "Incorrect manifest file count")
    names = set()
    total = 0
    for entry in files:
        exact_keys(entry, {"name", "kind", "bytes", "sha256", "components", "platforms",
                           "classification", "applicability", "provenance"}, "notice entry")
        name = entry["name"]
        require(isinstance(name, str) and SAFE_NAME.fullmatch(name) and name in NOTICE_NAMES,
                "Unreviewed notice filename")
        require(alias(name) not in names, "Duplicate or aliased notice name")
        names.add(alias(name))
        integer(entry["bytes"], 1, MAX_NOTICE_BYTES, "notice size")
        total += entry["bytes"]
        digest_value(entry["sha256"])
        components = entry["components"]
        require(type(components) is list and 1 <= len(components) <= 8, "Invalid component list")
        for component in components:
            text_value(component, 160)
        require(len(components) == len(set(components)), "Duplicate component label")
        platforms = entry["platforms"]
        require(type(platforms) is list and 1 <= len(platforms) <= 3
                and all(p in ("android", "ios", "desktop-development") for p in platforms)
                and len(platforms) == len(set(platforms)), "Invalid platform applicability")
        require(isinstance(entry["classification"], str) and entry["classification"] in CLASSIFICATIONS,
                "Unreviewed notice classification")
        text_value(entry["applicability"])
        if name == "INDEX.txt":
            require(entry["kind"] == "index" and entry["classification"] == "INDEX"
                    and entry["provenance"] == {"method": "authored-index"}, "Invalid index provenance")
        else:
            require(entry["kind"] == "upstream-text" and entry["classification"] != "INDEX",
                    "Invalid upstream text kind")
            validate_provenance(entry)
    require(total <= MAX_TOTAL_NOTICE_BYTES, "Notice aggregate exceeds byte limit")
    return value


def validate_provenance(entry):
    provenance = entry["provenance"]
    require(type(provenance) is dict, "Invalid provenance")
    method = provenance.get("method")
    keys = {"method", "url", "accessed_on", "source_sha256", "source_bytes"}
    if method == "source-lines":
        keys.add("line_range")
    elif method == "archive-member":
        keys.update({"archive_sha256", "archive_bytes", "member"})
    else:
        require(method == "full-text", "Unreviewed provenance method")
    exact_keys(provenance, keys, "provenance")
    public_url(provenance["url"])
    require(isinstance(provenance["accessed_on"], str)
            and re.fullmatch(r"\d{4}-\d{2}-\d{2}", provenance["accessed_on"]), "Invalid access date")
    digest_value(provenance["source_sha256"])
    integer(provenance["source_bytes"], entry["bytes"], 1024 * 1024, "upstream source size")
    if method == "source-lines":
        lines = provenance["line_range"]
        require(type(lines) is list and len(lines) == 2, "Invalid source line range")
        integer(lines[0], 1, 100000, "source first line")
        integer(lines[1], lines[0], 100000, "source last line")
    else:
        require(provenance["source_bytes"] == entry["bytes"]
                and provenance["source_sha256"] == entry["sha256"], "Verbatim provenance mismatch")
    if method == "archive-member":
        digest_value(provenance["archive_sha256"])
        integer(provenance["archive_bytes"], entry["bytes"], MAX_ARCHIVE_BYTES, "upstream archive size")
        safe_relative(provenance["member"])


def directory_flags():
    require(hasattr(os, "O_NOFOLLOW") and hasattr(os, "O_DIRECTORY"), "POSIX no-follow support required")
    return os.O_RDONLY | os.O_NOFOLLOW | os.O_DIRECTORY | getattr(os, "O_CLOEXEC", 0)


@contextmanager
def subdirectory(parent_fd, parts):
    fd = os.dup(parent_fd)
    try:
        for part in parts:
            require(part not in ("", ".", "..") and "/" not in part, "Unsafe filesystem component")
            child = os.open(part, directory_flags(), dir_fd=fd)
            os.close(fd)
            fd = child
        yield fd
    finally:
        os.close(fd)


@contextmanager
def directory(path):
    path = Path(path)
    require(".." not in path.parts, "Parent traversal in input path")
    absolute = path.absolute()
    fd = os.open(absolute.anchor, directory_flags())
    try:
        with subdirectory(fd, absolute.parts[1:]) as selected:
            yield selected
    finally:
        os.close(fd)


def file_identity(value):
    return (value.st_dev, value.st_ino, value.st_mode, value.st_size, value.st_mtime_ns, value.st_ctime_ns)


@contextmanager
def regular_file(parent_fd, name, limit):
    fd = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK | getattr(os, "O_CLOEXEC", 0),
                 dir_fd=parent_fd)
    try:
        before = os.fstat(fd)
        require(stat.S_ISREG(before.st_mode) and 0 < before.st_size <= limit, "Nonregular, empty or overlarge input")
        with os.fdopen(os.dup(fd), "rb") as stream:
            yield stream, before.st_size
        require(file_identity(before) == file_identity(os.fstat(fd))
                == file_identity(os.stat(name, dir_fd=parent_fd, follow_symlinks=False)),
                "Input changed while being verified")
    finally:
        os.close(fd)


def read_file(parent_fd, relative, limit):
    parts = safe_relative(relative).split("/")
    with subdirectory(parent_fd, parts[:-1]) as parent:
        with regular_file(parent, parts[-1], limit) as (stream, size):
            raw = stream.read(limit + 1)
            require(len(raw) == size, "Input size changed or exceeded limit")
            return raw


def list_entries(fd, limit):
    rows = []
    with os.scandir(fd) as entries:
        for entry in entries:
            require(len(rows) < limit, "Directory entry limit exceeded")
            rows.append((entry.name, entry.stat(follow_symlinks=False)))
    return sorted(rows)


def catalog_versions(raw):
    # Deliberately accept only the simple [versions] form this repository uses.
    # Hash binding also forces review of changes elsewhere in the catalog.
    result, section, seen = {}, False, False
    for raw_line in raw.decode("utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("["):
            if line == "[versions]":
                require(not seen, "Duplicate catalog versions section")
                section, seen = True, True
            else:
                section = False
            continue
        if section:
            match = re.fullmatch(r'([a-z][a-z0-9-]*)\s*=\s*"([^"\r\n]+)"\s*(?:#.*)?', line)
            require(match is not None and match[1] not in result, "Unreviewed catalog version syntax")
            result[match[1]] = match[2]
    require(seen and result, "Missing catalog version pins")
    return result


def source_receipt(root):
    with directory(root) as root_fd:
        manifest_raw = read_file(root_fd, MANIFEST_PATH, MAX_MANIFEST_BYTES)
        manifest = read_manifest(manifest_raw)
        catalog_raw = read_file(root_fd, CATALOG_PATH, MAX_SOURCE_CONFIG_BYTES)
        # Git may check out this text configuration with CRLF. This limited
        # catalog policy NEVER applies to upstream notices or package bytes.
        catalog = catalog_raw.replace(b"\r\n", b"\n")
        require(hashlib.sha256(catalog).hexdigest() == manifest["catalog"]["sha256"]
                and catalog_versions(catalog) == manifest["catalog"]["pins"], "Catalog changed; notice review required")
        build = read_file(root_fd, BUILD_PATH, MAX_SOURCE_CONFIG_BYTES).decode("utf-8")
        # Not a Gradle interpreter: this pins the explicit declaration used by
        # this build; actual package inspection independently checks its output.
        declarations = re.findall(r'^\s*packageOfResClass\s*=\s*"([^"\r\n]+)"\s*$', build, re.M)
        require(declarations == [NAMESPACE], "Compose resource namespace declaration changed")
        with subdirectory(root_fd, RESOURCE_DIRECTORY.split("/")) as legal:
            actual = list_entries(legal, len(NOTICE_NAMES) + 1)
            require({name for name, _ in actual} == NOTICE_NAMES and len(actual) == len(NOTICE_NAMES),
                    "Source notice set has missing, extra or aliased paths")
            require(all(stat.S_ISREG(metadata.st_mode) for _, metadata in actual), "Notice source is not regular")
            raw_files = {entry["name"]: read_file(legal, entry["name"], MAX_NOTICE_BYTES)
                         for entry in manifest["files"]}
        for entry in manifest["files"]:
            raw = raw_files[entry["name"]]
            require(len(raw) == entry["bytes"] and hashlib.sha256(raw).hexdigest() == entry["sha256"],
                    "Source notice bytes differ from reviewed manifest")
            decoded = raw.decode("utf-8")
            require("\x00" not in decoded, "NUL in notice text")
        index = raw_files["INDEX.txt"].decode("utf-8")
        require(all(sentence in index for sentence in REQUIRED_ACKNOWLEDGEMENTS), "Missing index acknowledgement")
        require(all(name in index for name in NOTICE_NAMES - {"INDEX.txt"}), "Index omits a notice file")
    rows = [{k: entry[k] for k in ("name", "bytes", "sha256")} for entry in manifest["files"]]
    return ({"status": "PASS", "manifest_sha256": hashlib.sha256(manifest_raw).hexdigest(),
             "catalog_sha256": manifest["catalog"]["sha256"],
             "catalog_raw_sha256": hashlib.sha256(catalog_raw).hexdigest(),
             "catalog_hash_policy": "CRLF_TO_LF", "resource_namespace": NAMESPACE,
             "resource_directory": RESOURCE_DIRECTORY, "resource_count": len(rows),
             "total_bytes": sum(row["bytes"] for row in rows), "files": rows}, raw_files)


def register_path(nodes, relative, is_directory):
    parts = relative.split("/")
    for index in range(len(parts)):
        path = "/".join(parts[:index + 1])
        kind = "directory" if index < len(parts) - 1 or is_directory else "file"
        previous = nodes.get(alias(path))
        require(previous is None or previous == (path, kind), "Package case alias or file/directory collision")
        nodes[alias(path)] = (path, kind)
        require(len(nodes) <= 2 * MAX_PACKAGE_ENTRIES, "Package path-node limit exceeded")


def notice_name(relative, prefix, is_directory=False):
    if relative == prefix:
        require(is_directory, "Notice directory is a file")
        return None
    if relative.startswith(prefix + "/"):
        name = relative[len(prefix) + 1:]
        require(not is_directory and name in NOTICE_NAMES, "Unexpected packaged notice path")
        return name
    require(not (relative == RESOURCE_SUFFIX or relative.endswith("/" + RESOURCE_SUFFIX)
                 or (RESOURCE_SUFFIX + "/") in relative), "Notice namespace at an unreviewed package root")
    return None


def package_row(name, relative, raw, expected):
    require(raw == expected, "Packaged notice bytes differ from source")
    return {"name": name, "path": relative, "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


def verify_app(path, source_files):
    rows, nodes, count = [], {}, [0]

    def walk(fd, prefix="", depth=0):
        require(depth <= MAX_PACKAGE_DEPTH, "App directory nesting limit exceeded")
        for name, metadata in list_entries(fd, MAX_PACKAGE_ENTRIES - count[0]):
            count[0] += 1
            require(count[0] <= MAX_PACKAGE_ENTRIES, "App entry limit exceeded")
            relative = safe_relative(prefix + name)
            is_directory = stat.S_ISDIR(metadata.st_mode)
            require(is_directory or stat.S_ISREG(metadata.st_mode), "Symlink or nonregular app entry")
            register_path(nodes, relative, is_directory)
            selected = notice_name(relative, APP_PREFIX, is_directory)
            if is_directory:
                with subdirectory(fd, [name]) as child:
                    walk(child, relative + "/", depth + 1)
            elif selected is not None:
                raw = read_file(fd, name, MAX_NOTICE_BYTES)
                rows.append(package_row(selected, relative, raw, source_files[selected]))

    with directory(path) as app:
        walk(app)
    return finish_package("ios-app", rows, count[0])


def zip_directory_bounds(stream, size):
    # Bound and count the central directory BEFORE ZipFile allocates ZipInfo
    # objects; the EOCD's claimed count alone is not a trustworthy memory bound.
    tail_size = min(size, 65535 + 22)
    stream.seek(size - tail_size)
    tail = stream.read(tail_size)
    marker = tail.rfind(b"PK\x05\x06")
    require(marker >= 0 and len(tail) - marker >= 22, "Missing ZIP end record")
    fields = struct.unpack("<4s4H2LH", tail[marker:marker + 22])
    _, disk, directory_disk, disk_count, count, length, offset, comment = fields
    require(disk == directory_disk == 0 and disk_count == count
            and 0 < count <= MAX_PACKAGE_ENTRIES and count != 65535,
            "Unsupported multipart, ZIP64 or oversized ZIP directory")
    require(0 < length <= MAX_DIRECTORY_BYTES and offset != 0xffffffff
            and offset + length == size - tail_size + marker
            and marker + 22 + comment == len(tail), "Invalid ZIP directory bounds")
    stream.seek(offset)
    consumed = actual = 0
    while consumed < length:
        header = stream.read(46)
        require(len(header) == 46 and header[:4] == b"PK\x01\x02", "Malformed ZIP central entry")
        name_length, extra_length, comment_length = struct.unpack("<3H", header[28:34])
        require(0 < name_length <= MAX_PATH_BYTES, "Invalid ZIP name length")
        variable = name_length + extra_length + comment_length
        consumed += 46 + variable
        actual += 1
        require(consumed <= length and actual <= MAX_PACKAGE_ENTRIES, "ZIP central directory limit exceeded")
        stream.seek(variable, os.SEEK_CUR)
    require(actual == count and consumed == length, "ZIP directory count mismatch")
    return offset, count


def no_zip64_extra(raw):
    cursor = 0
    while cursor < len(raw):
        require(cursor + 4 <= len(raw), "Malformed ZIP extra field")
        tag, length = struct.unpack("<2H", raw[cursor:cursor + 4])
        cursor += 4
        require(tag != 1 and cursor + length <= len(raw), "Unsupported ZIP64 or malformed extra field")
        cursor += length


def zip_data_bounds(stream, item, central_offset):
    require(0 <= item.header_offset < central_offset, "Invalid ZIP local header offset")
    stream.seek(item.header_offset)
    header = stream.read(30)
    require(len(header) == 30 and header[:4] == b"PK\x03\x04", "Missing ZIP local header")
    flags, method = struct.unpack("<2H", header[6:10])
    crc, compressed, uncompressed, name_length, extra_length = struct.unpack("<3L2H", header[14:30])
    require(flags == item.flag_bits and method == item.compress_type, "ZIP local/central method mismatch")
    require(0 < name_length <= MAX_PATH_BYTES and item.header_offset + 30 + name_length + extra_length <= central_offset,
            "Invalid ZIP local header bounds")
    name = stream.read(name_length).decode("utf-8" if flags & 0x800 else "cp437")
    require(name == item.orig_filename, "ZIP local/central name mismatch")
    no_zip64_extra(stream.read(extra_length))
    start = stream.tell()
    end = start + item.compress_size
    require(end <= central_offset, "ZIP member exceeds data region")
    if flags & 8:
        stream.seek(end)
        first = stream.read(4)
        descriptor = stream.read(12) if first == b"PK\x07\x08" else first + stream.read(8)
        require(len(descriptor) == 12 and struct.unpack("<3L", descriptor) == (item.CRC, item.compress_size, item.file_size),
                "ZIP data descriptor mismatch")
        end = stream.tell()
        require(end <= central_offset, "ZIP data descriptor exceeds data region")
    else:
        require((crc, compressed, uncompressed) == (item.CRC, item.compress_size, item.file_size),
                "ZIP local/central size or CRC mismatch")
    return start, end


def read_zip_notice(stream, item, start):
    integer(item.file_size, 1, MAX_NOTICE_BYTES, "ZIP notice size")
    integer(item.compress_size, 1, 2 * MAX_NOTICE_BYTES, "ZIP notice compressed size")
    stream.seek(start)
    compressed = stream.read(item.compress_size)
    require(len(compressed) == item.compress_size, "Truncated ZIP notice")
    if item.compress_type == zipfile.ZIP_STORED:
        raw = compressed
    else:
        decompressor = zlib.decompressobj(-15)
        try:
            raw = decompressor.decompress(compressed, MAX_NOTICE_BYTES + 1)
        except zlib.error as error:
            raise VerificationError("Malformed compressed notice") from error
        require(decompressor.eof and not decompressor.unused_data and not decompressor.unconsumed_tail,
                "Truncated, trailing or overlarge compressed notice")
    require(len(raw) == item.file_size and zlib.crc32(raw) & 0xffffffff == item.CRC,
            "ZIP notice length or CRC mismatch")
    return raw


def verify_aab(path, source_files):
    rows, nodes, names, ranges = [], {}, set(), []
    with directory(path.parent) as parent:
        with regular_file(parent, path.name, MAX_ARCHIVE_BYTES) as (stream, size):
            offset, count = zip_directory_bounds(stream, size)
            stream.seek(0)
            with zipfile.ZipFile(stream) as archive:
                entries = archive.infolist()
                require(len(entries) == count, "ZIP directory interpretation mismatch")
                for item in entries:
                    require(item.filename == item.orig_filename, "Truncated ZIP member name")
                    relative = safe_relative(item.filename, directory=item.is_dir())
                    require(relative not in names, "Duplicate ZIP member")
                    names.add(relative)
                    # Bits 1/2 describe DEFLATE's compression level, not encryption.
                    allowed_flags = 0x80e if item.compress_type == zipfile.ZIP_DEFLATED else 0x808
                    require(not item.flag_bits & ~allowed_flags and item.volume == 0,
                            "Encrypted, multipart or unsupported ZIP flags")
                    require(item.compress_type in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED),
                            "Unsupported ZIP compression")
                    kind = stat.S_IFMT(item.external_attr >> 16)
                    require(kind in (0, stat.S_IFDIR if item.is_dir() else stat.S_IFREG), "Symlink or nonregular ZIP entry")
                    require(not item.is_dir() or item.file_size == 0, "Nonempty ZIP directory")
                    register_path(nodes, relative, item.is_dir())
                    no_zip64_extra(item.extra)
                    start, end = zip_data_bounds(stream, item, offset)
                    ranges.append((item.header_offset, end))
                    selected = notice_name(relative, AAB_PREFIX, item.is_dir())
                    if selected is not None:
                        raw = read_zip_notice(stream, item, start)
                        rows.append(package_row(selected, relative, raw, source_files[selected]))
                previous_end = 0
                for start, end in sorted(ranges):
                    require(start >= previous_end, "Overlapping ZIP entries")
                    previous_end = end
    return finish_package("android-aab", rows, count)


def finish_package(format_name, rows, inspected_entries):
    require(len(rows) == len(NOTICE_NAMES) and {row["name"] for row in rows} == NOTICE_NAMES,
            "Package notice set is incomplete or duplicated")
    return {"status": "PASS", "format": format_name, "exact_byte_policy": EXACT_BYTE_POLICY,
            "resource_count": len(rows), "total_bytes": sum(row["bytes"] for row in rows),
            "inspected_entry_count": inspected_entries, "files": sorted(rows, key=lambda row: row["name"]),
            "limitations": "Notice delivery only; not a complete-artifact digest, final-link, UI, signature, device, Store or legal-completeness verdict."}


def verify(root, package=None):
    source, source_files = source_receipt(root)
    packaged = None
    if package is not None:
        path = Path(package)
        if path.suffix == ".app":
            packaged = verify_app(path, source_files)
        elif path.suffix == ".aab":
            packaged = verify_aab(path, source_files)
        else:
            raise VerificationError("Package must be an Android .aab or iOS .app")
    return {"status": "PASS", "source": source, "package": packaged}


class Arguments(argparse.ArgumentParser):
    def error(self, message):
        raise VerificationError("Invalid command arguments; use --help")


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    json_output = "--json" in argv
    try:
        parser = Arguments(description=__doc__)
        parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
        parser.add_argument("--package", type=Path, help="Inspect an unsigned AAB or a complete built/installed iOS .app")
        parser.add_argument("--json", action="store_true", help="Emit one bounded JSON object")
        args = parser.parse_args(argv)
        result = verify(args.root, args.package)
        code = 0
    except (ValueError, OSError, UnicodeError, zipfile.BadZipFile, EOFError,
            struct.error, RecursionError, NotImplementedError) as error:
        # Never echo package paths, raw metadata, notice content or OS filenames.
        detail = str(error) if isinstance(error, VerificationError) else type(error).__name__
        result = {"status": "FAIL", "source": None, "package": None, "error": detail[:240]}
        code = 1
    if json_output:
        print(json.dumps(result, sort_keys=True, ensure_ascii=True))
    elif code:
        print("FAIL: " + result["error"], file=sys.stderr)
    else:
        print("PASS: reviewed source notice bytes" + (" and requested package bytes" if result["package"] else " (package not requested)"))
    return code


if __name__ == "__main__":
    sys.exit(main())
