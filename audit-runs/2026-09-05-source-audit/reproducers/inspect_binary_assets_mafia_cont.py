"""Read-only inspection of the audit's explicitly named public binary inputs."""
from pathlib import Path, PurePosixPath
import binascii
import datetime
import hashlib
import json
import platform
import struct
import sys
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "audit-runs/2026-09-05-source-audit/evidence/binary-assets-wrapper"
PNG_PATHS = [
    "assets/branding/parlor-app-icon-master.png",
    "composeApp/src/androidMain/res/drawable-nodpi/ic_launcher_foreground.png",
    "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/ParlorAppIcon.png",
]


def identity(path):
    raw = (ROOT / path).read_bytes()
    return raw, {"path": path, "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


def png(path):
    raw, result = identity(path)
    assert raw[:8] == b"\x89PNG\r\n\x1a\n"
    offset, chunks, compressed = 8, [], bytearray()
    while offset < len(raw):
        assert offset + 12 <= len(raw)
        size = struct.unpack_from(">I", raw, offset)[0]
        kind = raw[offset + 4:offset + 8]
        end = offset + size + 12
        assert end <= len(raw)
        content = raw[offset + 8:offset + 8 + size]
        crc = struct.unpack_from(">I", raw, offset + size + 8)[0]
        assert binascii.crc32(kind + content) & 0xffffffff == crc
        chunks.append({"type": kind.decode("ascii"), "length": size, "offset": offset})
        if kind == b"IHDR":
            width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", content)
        if kind == b"IDAT":
            compressed.extend(content)
        offset = end
        if kind == b"IEND":
            assert size == 0 and offset == len(raw)
            break
    assert chunks[0]["type"] == "IHDR" and chunks[-1]["type"] == "IEND"
    assert depth == 8 and color == 2 and compression == filtering == interlace == 0
    assert not any(c["type"] == "tRNS" for c in chunks)
    stride, expected = width * 3, (width * 3 + 1) * height
    assert expected < 16 * 1024 * 1024
    inflater = zlib.decompressobj()
    scanlines = inflater.decompress(compressed, expected + 1)
    assert inflater.eof and not inflater.unused_data and not inflater.unconsumed_tail
    assert len(scanlines) == expected
    previous = bytearray(stride)
    filters = set()
    bounds = [width, height, -1, -1]
    reference = None
    for y in range(height):
        row_start = y * (stride + 1)
        mode = scanlines[row_start]
        assert 0 <= mode <= 4
        filters.add(mode)
        row = bytearray(scanlines[row_start + 1:row_start + 1 + stride])
        for x in range(stride):
            a = row[x - 3] if x >= 3 else 0
            b = previous[x]
            c = previous[x - 3] if x >= 3 else 0
            if mode == 1:
                predictor = a
            elif mode == 2:
                predictor = b
            elif mode == 3:
                predictor = (a + b) // 2
            elif mode == 4:
                p = a + b - c
                da, db, dc = abs(p - a), abs(p - b), abs(p - c)
                predictor = a if da <= db and da <= dc else b if db <= dc else c
            else:
                predictor = 0
            row[x] = (row[x] + predictor) & 255
        if reference is None:
            reference = bytes(row[:3])
        for x in range(width):
            if row[x * 3:x * 3 + 3] != reference:
                bounds = [min(bounds[0], x), min(bounds[1], y), max(bounds[2], x), max(bounds[3], y)]
        previous = row
    result.update({"width": width, "height": height, "bit_depth": depth,
                   "color_type": color, "interlace": interlace,
                   "alpha_or_transparent_color_chunk": False,
                   "chunks": chunks, "all_chunk_bounds_and_crcs_valid": True,
                   "decompressed_scanline_bytes": len(scanlines), "row_filters": sorted(filters),
                   "first_pixel_rgb": list(reference), "non_first_color_bounds_inclusive": bounds})
    return result


def wrapper():
    path = "gradle/wrapper/gradle-wrapper.jar"
    _, result = identity(path)
    with zipfile.ZipFile(ROOT / path) as archive:
        infos = archive.infolist()
        assert len({i.filename for i in infos}) == len(infos)
        assert sum(i.file_size for i in infos) < 1024 * 1024
        assert all(not PurePosixPath(i.filename).is_absolute() and ".." not in PurePosixPath(i.filename).parts for i in infos)
        assert all(not (i.flag_bits & 1) for i in infos)
        assert archive.testzip() is None
        classes, other = [], []
        for info in infos:
            content = archive.read(info)
            entry = {"name": info.filename, "bytes": len(content), "sha256": hashlib.sha256(content).hexdigest()}
            if info.filename.endswith(".class"):
                magic, minor, major = struct.unpack(">IHH", content[:8])
                assert magic == 0xcafebabe
                entry.update({"class_major": major, "class_minor": minor})
                classes.append(entry)
            else:
                other.append(entry)
        result.update({"entries": len(infos), "all_entry_crcs_valid": True,
                       "uncompressed_bytes": sum(i.file_size for i in infos),
                       "classes": classes, "other_entries": other,
                       "manifest": archive.read("META-INF/MANIFEST.MF").decode("utf-8")})
    expected = (OUT / "gradle-8.13-wrapper.jar.sha256").read_text().strip()
    result.update({"official_wrapper_sha256": expected, "official_wrapper_sha256_matches": result["sha256"] == expected})
    assert result["official_wrapper_sha256_matches"]
    props = dict(line.split("=", 1) for line in (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text().splitlines())
    expected_zip = (OUT / "gradle-8.13-bin.zip.sha256").read_text().strip()
    result.update({"configured_distribution_sha256": props["distributionSha256Sum"],
                   "official_bin_distribution_sha256": expected_zip,
                   "configured_distribution_pin_matches": props["distributionSha256Sum"] == expected_zip,
                   "cached_or_downloaded_distribution_zip_inspected": False})
    assert result["configured_distribution_pin_matches"]
    return result


def main():
    result = {"reviewer": "/root/mafia_cont", "started_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "python": sys.version, "platform": platform.platform(), "method": "bounded read-only PNG chunk/CRC/decode and JAR ZIP/class metadata inspection"}
    try:
        result["png"] = [png(path) for path in PNG_PATHS]
        result["master_ios_bytes_equal"] = (ROOT / PNG_PATHS[0]).read_bytes() == (ROOT / PNG_PATHS[2]).read_bytes()
        assert result["master_ios_bytes_equal"]
        result["wrapper"] = wrapper()
        result["result"] = "PASS"
    except Exception as error:
        result.update({"result": "FAIL", "error_type": type(error).__name__, "reason": str(error)})
        raise
    finally:
        result["finished_utc"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        (OUT / "inspection.json").write_text(json.dumps(result, indent=2) + "\n")
    print("PASS: three PNG structural/decode checks, master/iOS byte equality, JAR ZIP metadata and official wrapper/distribution pin checks")


if __name__ == "__main__":
    main()
