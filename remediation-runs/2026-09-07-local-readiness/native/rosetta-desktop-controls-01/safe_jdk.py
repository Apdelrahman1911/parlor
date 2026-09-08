"""Campaign-only public JDK download/extraction. Importing this module does no I/O."""
import hashlib
from pathlib import Path, PurePosixPath
import os
import stat
import tarfile
import time
import urllib.parse
import urllib.request


URL = ('https://github.com/adoptium/temurin21-binaries/releases/download/'
       'jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_mac_hotspot_21.0.12.1_1.tar.gz')
SHA256 = '44db0f08196daf19a47f90d13388b0c943b67663cb537f998fe29e836fa842ce'
SIZE = 194_316_575
MAX_EXPANDED = 1024 * 1024 * 1024
MAX_ENTRIES = 40_000


class PublicAssetRedirect(urllib.request.HTTPRedirectHandler):
    """GitHub's public release-CDN redirect only; never an HTTP downgrade."""
    max_redirections = 3
    max_repeats = 1

    def redirect_request(self, request, fp, code, message, headers, newurl):
        parsed = urllib.parse.urlsplit(newurl)
        if (parsed.scheme != 'https' or parsed.username or parsed.password
                or parsed.port not in (None, 443)
                or parsed.hostname not in ('github.com', 'release-assets.githubusercontent.com')):
            raise RuntimeError('Unapproved JDK asset redirect')
        return super().redirect_request(request, fp, code, message, headers, newurl)


def download(destination):
    """One bounded public GET; no credentials, resume, alternate host or retry."""
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), PublicAssetRedirect())
    request = urllib.request.Request(URL, headers={'User-Agent': 'Parlor-owned-public-JDK-fixture/1'})
    deadline, count, digest = time.monotonic() + 600, 0, hashlib.sha256()
    with opener.open(request, timeout=30) as response, destination.open('xb') as output:
        if response.status != 200:
            raise RuntimeError('Public JDK download did not return200')
        length = response.headers.get('Content-Length')
        if length is not None and int(length) != SIZE:
            raise RuntimeError('Public JDK Content-Length mismatch')
        while True:
            if time.monotonic() > deadline:
                raise TimeoutError('Public JDK download deadline')
            # read1 performs one buffered/raw read, rather than waiting to fill
            # a MiB while a slow peer repeatedly resets a socket read timeout.
            block = response.read1(min(1024 * 1024, SIZE - count + 1))
            if not block:
                break
            count += len(block)
            if count > SIZE:
                raise RuntimeError('Public JDK download exceeded pinned length')
            output.write(block)
            digest.update(block)
    if count != SIZE or digest.hexdigest() != SHA256:
        raise RuntimeError('Public JDK size/SHA256 mismatch')
    return {'url': URL, 'bytes': count, 'sha256': digest.hexdigest()}


def safe_name(raw):
    if not raw or '\x00' in raw or '\\' in raw or raw.startswith('/'):
        raise RuntimeError('Unsafe archive path')
    while raw.startswith('./'):
        raw = raw[2:]
    parts = PurePosixPath(raw).parts
    if not parts or '..' in parts or len(raw) > 4096:
        raise RuntimeError('Unsafe archive path')
    return PurePosixPath(*parts)


def link_destination(name, target, symbolic):
    if not target or '\x00' in target or '\\' in target or target.startswith('/'):
        raise RuntimeError('Unsafe archive link')
    parts = list(name.parent.parts) if symbolic else []
    for part in PurePosixPath(target).parts:
        if part == '..':
            if not parts:
                raise RuntimeError('Archive link escapes extraction root')
            parts.pop()
        elif part != '.':
            parts.append(part)
    if not parts:
        raise RuntimeError('Archive link points to extraction root')
    return PurePosixPath(*parts)


def extract(archive, destination):
    """Write regular files first, then validated in-tree links; no tar.extractall."""
    destination.mkdir(mode=0o700, exist_ok=False)
    entries, expanded = {}, 0
    with tarfile.open(archive, 'r:gz') as bundle:
        for member in bundle:
            name = safe_name(member.name)
            if name in entries or len(entries) >= MAX_ENTRIES:
                raise RuntimeError('Duplicate/excessive archive entries')
            if not (member.isdir() or member.isreg() or member.issym() or member.islnk()):
                raise RuntimeError('Special archive entry forbidden')
            if member.size < 0:
                raise RuntimeError('Negative archive entry size')
            expanded += member.size
            if expanded > MAX_EXPANDED:
                raise RuntimeError('JDK archive expansion exceeds1GiB')
            entries[name] = member
        for name, member in entries.items():
            for ancestor in name.parents:
                parent = entries.get(ancestor)
                if parent is not None and not parent.isdir():
                    raise RuntimeError('Archive writes through non-directory ancestor')
            if member.issym() or member.islnk():
                target = link_destination(name, member.linkname, member.issym())
                if target not in entries:
                    raise RuntimeError('Archive link target is not inventoried')
                if member.islnk() and not entries[target].isreg():
                    raise RuntimeError('Hard link must target regular inventoried file')
        for name, member in entries.items():
            path = destination.joinpath(*name.parts)
            if member.isdir():
                path.mkdir(mode=0o755, parents=True, exist_ok=True)
            elif member.isreg():
                path.parent.mkdir(mode=0o755, parents=True, exist_ok=True)
                remaining = member.size
                with bundle.extractfile(member) as source, path.open('xb') as output:
                    while remaining:
                        block = source.read(min(1024 * 1024, remaining))
                        if not block:
                            raise RuntimeError('Truncated archive file')
                        output.write(block)
                        remaining -= len(block)
                path.chmod(0o644 | (member.mode & 0o111))
        for name, member in entries.items():
            if not (member.issym() or member.islnk()):
                continue
            path = destination.joinpath(*name.parts)
            path.parent.mkdir(mode=0o755, parents=True, exist_ok=True)
            if member.issym():
                path.symlink_to(member.linkname)
            else:
                target = link_destination(name, member.linkname, False)
                os.link(destination.joinpath(*target.parts), path, follow_symlinks=False)
        resolved_root = destination.resolve()
        for name in entries:
            if not destination.joinpath(*name.parts).resolve(strict=True).is_relative_to(resolved_root):
                raise RuntimeError('Resolved archive path escapes extraction root')
    return {'entries': len(entries), 'expanded_bytes': expanded}


def owned_identity(path):
    value = path.lstat()
    if not stat.S_ISDIR(value.st_mode) or value.st_uid != os.getuid() or path.is_symlink():
        raise RuntimeError('Task scratch ownership mismatch')
    return value.st_dev, value.st_ino, value.st_uid
