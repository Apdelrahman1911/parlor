"""Hash-bound original source fixtures for hermetic software controls only.

Native investigation still archives the explicitly selected actual Git commit.
These full-file fixtures never replace native application/build inputs.
"""
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import stat

DIRECTORY = Path(__file__).resolve().parent / 'fixtures'
MANIFEST_SHA256 = '102c6b34cfebbdc4d165d030dc7daf8bbecb40b6e3999ec3e013a6000d57410d'
MAX_SOURCE_BYTES = 128 * 1024


def _read(path, maximum):
    if path.resolve() != path.absolute():
        raise ValueError('Redirected frozen control fixture')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        before = os.fstat(fd)
        if not stat.S_ISREG(before.st_mode) or before.st_nlink != 1 or not 0 < before.st_size <= maximum:
            raise ValueError('Invalid frozen control fixture')
        chunks, size = [], 0
        while True:
            chunk = os.read(fd, min(8192, maximum + 1 - size))
            if not chunk:
                break
            chunks.append(chunk)
            size += len(chunk)
            if size > maximum:
                raise ValueError('Oversized frozen control fixture')
        after = os.fstat(fd)
        identity = lambda value: (value.st_dev, value.st_ino, value.st_size,
                                  value.st_mtime_ns, value.st_ctime_ns)
        if identity(before) != identity(after) or size != before.st_size:
            raise ValueError('Changing frozen control fixture')
        return b''.join(chunks)
    finally:
        os.close(fd)


def load(source, path, expected_sha256, *, directory=DIRECTORY):
    """Read exact pinned bytes without Git, network, CWD inference or fallback."""
    raw = _read(directory / 'manifest.json', 32 * 1024)
    if hashlib.sha256(raw).hexdigest() != MANIFEST_SHA256:
        raise ValueError('Frozen source manifest changed')
    manifest = json.loads(raw)
    if type(source) is not str or source not in manifest['sources'] or type(path) is not str:
        raise ValueError('Unknown frozen control source')
    parsed = PurePosixPath(path)
    inputs = manifest['sources'][source]['inputs']
    if parsed.is_absolute() or '..' in parsed.parts or str(parsed) != path or path not in inputs:
        raise ValueError('Unknown frozen control path')
    if type(expected_sha256) is not str or inputs[path] != expected_sha256:
        raise ValueError('Frozen control digest does not match its source binding')
    value = _read(directory / source / (path + '.source'), MAX_SOURCE_BYTES)
    if hashlib.sha256(value).hexdigest() != expected_sha256:
        raise ValueError('Frozen control source bytes changed')
    return value
