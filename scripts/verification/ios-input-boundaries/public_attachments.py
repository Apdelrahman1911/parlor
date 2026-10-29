"""Read-only public pre-input failure screenshots; never app execution or qualification."""
import hashlib
import os
import stat
import struct
import zlib

TITLE = 'Failed public control settlement before tap'
METHOD = 'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInArabic()'
MAX_TOTAL = 128 * 1024 * 1024
MAX_IMAGE = 16 * 1024 * 1024


def attachment_command(result, destination, *, method):
    require(method == METHOD)
    # --only-failures selects failure-associated attachments, not all attachments
    # of a failed test. This screenshot activity precedes the failing assertion
    # and has isAssociatedWithFailure=false. Select the exact eligible test.
    return ['export', 'attachments', '--path', str(result), '--output-path', str(destination),
            '--test-id', method]


def verify_export_help(raw):
    require(all(token in raw for token in (b'--path', b'--output-path', b'--test-id')))


def require(condition):
    if not condition:
        raise ValueError('Public attachment source, privacy or bounded-file check failed')


def verify_public_failure(activities, method):
    require(activities.get('testIdentifier') == method)
    titles, failures = [], []
    def visit(node):
        if isinstance(node, dict):
            if 'title' in node:
                require(isinstance(node['title'], str) and len(titles) < 256)
                titles.append(node['title'])
                if node.get('isAssociatedWithFailure') is True:
                    failures.append(node['title'])
            for child in node.values():
                visit(child)
        elif isinstance(node, list):
            for child in node:
                visit(child)
    visit(activities)
    require(titles.count(TITLE) == 1 and len(failures) == 1 and
            failures[0].startswith('XCTAssertEqual failed:') and
            'Public setup control must stop scrolling before it is tapped' in failures[0] and
            not any(title.startswith('Type ') for title in titles))


def read_regular(path, maximum):
    before = path.lstat()
    require(stat.S_ISREG(before.st_mode) and before.st_nlink == 1 and 0 < before.st_size <= maximum)
    identity = lambda v: (v.st_dev, v.st_ino, v.st_mode, v.st_nlink, v.st_size, v.st_mtime_ns, v.st_ctime_ns)
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'rb') as stream:
        require(identity(os.fstat(stream.fileno())) == identity(before))
        raw = stream.read(maximum + 1)
        require(identity(os.fstat(stream.fileno())) == identity(before))
    require(identity(path.lstat()) == identity(before) and len(raw) == before.st_size)
    return raw


def png_geometry(raw):
    require(raw[:16] == b'\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR' and len(raw) >= 33)
    width, height = struct.unpack('>II', raw[16:24])
    require(0 < width <= 4096 and 0 < height <= 4096)
    offset, chunks, image_data = 8, 0, False
    while offset + 12 <= len(raw):
        length = struct.unpack('>I', raw[offset:offset + 4])[0]
        end = offset + length + 12
        kind = raw[offset + 4:offset + 8]
        require(end <= len(raw) and chunks < 1024 and
                len(kind) == 4 and all(65 <= c <= 90 or 97 <= c <= 122 for c in kind))
        require(zlib.crc32(raw[offset + 4:end - 4]) & 0xffffffff == struct.unpack('>I', raw[end - 4:end])[0])
        require(kind in (b'IHDR', b'IDAT', b'IEND', b'PLTE') or 97 <= kind[0] <= 122)
        if kind == b'IHDR':
            require(chunks == 0 and length == 13 and raw[24] in (1, 2, 4, 8, 16) and
                    raw[25] in (0, 2, 3, 4, 6) and raw[26:28] == b'\x00\x00' and raw[28] in (0, 1))
        if kind == b'IDAT':
            require(length > 0)
            image_data = True
        if kind == b'IEND':
            require(length == 0 and end == len(raw) and image_data)
            return width, height
        offset, chunks = end, chunks + 1
    require(False)


def curate_public_screenshots(directory, destination):
    require(directory.is_dir() and not directory.is_symlink() and
            destination.is_dir() and not destination.is_symlink())
    entries = sorted(directory.rglob('*'))
    require(0 < len(entries) <= 64 and not any(p.is_symlink() for p in entries))
    files = [p for p in entries if not p.is_dir()]
    require(sum(p.lstat().st_size for p in files) <= MAX_TOTAL)
    images = []
    for path in files:
        # Other exported attachments stay private to this runner and are never
        # uploaded. In particular, do not publish recordings or raw manifests.
        require(stat.S_ISREG(path.lstat().st_mode) and path.lstat().st_nlink == 1)
        if path.suffix.lower() != '.png':
            continue
        raw = read_regular(path, MAX_IMAGE)
        width, height = png_geometry(raw)
        require(len(images) < 8)
        images.append((raw, dict(bytes=len(raw), width=width, height=height,
                                 sha256=hashlib.sha256(raw).hexdigest())))
    require(bool(images))
    targets = [destination / ('public-settlement-%02d.png' % (i + 1)) for i in range(len(images))]
    require(not any(p.exists() or p.is_symlink() for p in targets))
    for target, (raw, _) in zip(targets, images):
        with target.open('xb') as output:
            output.write(raw)
    return [metadata | {'path': target.name} for target, (_, metadata) in zip(targets, images)]
