"""Read-only Xcode component discovery; grants no process cleanup authority."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import stat
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.ci import apple_verification_hygiene as apple

BRANCH = 'refs/heads/diagnostic/xcode-worker-inventory-2026-10-29'
MAX_ENTRIES = 750000
MAX_COMPONENTS = 8
MAX_BYTES = 128 * 1024 * 1024


def discover(application):
    """Bounded no-follow traversal; do not guess or admit a worker path."""
    if application.is_symlink() or application.resolve(strict=True) != application:
        raise ValueError('Canonical Xcode application required')
    found, count = [], 0
    def unreadable(error):
        raise error
    for directory, dirs, files in os.walk(application, followlinks=False, onerror=unreadable):
        count += len(dirs) + len(files)
        if count > MAX_ENTRIES:
            raise ValueError('Xcode traversal exceeds bound')
        dirs[:] = [d for d in dirs if not (Path(directory) / d).is_symlink()]
        for name in files:
            if name != 'DTServiceHub':
                continue
            path = Path(directory) / name
            info = path.lstat()
            if not stat.S_ISREG(info.st_mode) or path.resolve(strict=True) != path or \
                    not 0 < info.st_size <= MAX_BYTES or not os.access(path, os.X_OK):
                raise ValueError('Unsafe candidate Xcode component')
            found.append(path)
            if len(found) > MAX_COMPONENTS:
                raise ValueError('Too many candidate Xcode components')
    if not found:
        raise ValueError('No DTServiceHub component discovered')
    return sorted(found)


def inspect_component(application, path, run=subprocess.run):
    if application not in path.parents or path.is_symlink() or path.resolve(strict=True) != path:
        raise ValueError('Only canonical Xcode component files may be inspected')
    before = path.lstat()
    identity = lambda s: (s.st_dev, s.st_ino, s.st_mode, s.st_size, s.st_mtime_ns, s.st_ctime_ns)
    digest = hashlib.sha256()
    with os.fdopen(os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK), 'rb') as stream:
        opened = os.fstat(stream.fileno())
        if not stat.S_ISREG(opened.st_mode) or identity(before) != identity(opened) or not 0 < opened.st_size <= MAX_BYTES:
            raise ValueError('Nonregular or changing component input')
        size = 0
        for chunk in iter(lambda: stream.read(65536), b''):
            size += len(chunk)
            if size > MAX_BYTES:
                raise ValueError('Component grew beyond bound')
            digest.update(chunk)
        if identity(os.fstat(stream.fileno())) != identity(opened) or size != opened.st_size:
            raise ValueError('Component changed while hashing')
    verify = run(['/usr/bin/codesign', '--verify', '--strict', str(path)],
                 stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=45)
    display = run(['/usr/bin/codesign', '-d', '--verbose=4', str(path)],
                  stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=45)
    if len(display.stdout) + len(display.stderr) > 65536:
        raise ValueError('Signature display exceeds bound')
    details = (display.stdout + display.stderr).decode('utf-8', errors='strict').splitlines()
    fields = [(k, v) for line in details if '=' in line for k, v in [line.split('=', 1)]
              if k in ('Identifier', 'TeamIdentifier', 'Authority')]
    after = path.stat()
    if path.is_symlink() or path.resolve(strict=True) != path or identity(before) != identity(after):
        raise ValueError('Component changed during inspection')
    # Same-size writes can share timestamps on coarse filesystems. Re-read the
    # actual bytes as well; metadata equality alone cannot bind the signed image.
    final_hash, final_size = hashlib.sha256(), 0
    with os.fdopen(os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK), 'rb') as stream:
        if identity(os.fstat(stream.fileno())) != identity(after):
            raise ValueError('Component redirected before final hashing')
        for chunk in iter(lambda: stream.read(65536), b''):
            final_size += len(chunk)
            if final_size > MAX_BYTES:
                raise ValueError('Component grew during final hashing')
            final_hash.update(chunk)
        if identity(os.fstat(stream.fileno())) != identity(after):
            raise ValueError('Component changed during final hashing')
    if final_size != size or final_hash.digest() != digest.digest() or identity(path.lstat()) != identity(after):
        raise ValueError('Component bytes changed during signature inspection')
    return dict(relative_executable=str(path.relative_to(application)), bytes=size,
                sha256=digest.hexdigest(), signature_verify_exit=verify.returncode,
                signature_display_exit=display.returncode, signature_fields=fields,
                cleanup_authorized=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if platform.system() != 'Darwin' or platform.machine() != 'arm64' or \
            os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted' or \
            os.environ.get('GITHUB_REPOSITORY') != 'Apdelrahman1911/parlor' or \
            os.environ.get('GITHUB_REF') != BRANCH or os.environ.get('GITHUB_JOB') != 'ios' or \
            os.environ.get('GITHUB_EVENT_NAME') != 'push':
        raise RuntimeError('Explicit standard hosted inventory branch only')
    if subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT).strip():
        raise RuntimeError('Inventory source must be clean')
    source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip()
    if source != os.environ.get('GITHUB_SHA') or source != os.environ.get('GITHUB_WORKFLOW_SHA'):
        raise RuntimeError('Inventory checkout and executing workflow must share the frozen source')
    xcode = subprocess.check_output(['/usr/bin/xcodebuild', '-version']).decode()
    if xcode != 'Xcode 26.3\nBuild version 17C529\n':
        raise RuntimeError('Exact reviewed Xcode required')
    temporary = Path(os.environ['RUNNER_TEMP']).resolve(strict=True)
    output = args.output.absolute()
    if output.parent != temporary or output.exists() or output.is_symlink():
        raise ValueError('Fresh direct child of runner-owned temporary directory required')
    config = apple.configuration(ROOT)
    application = Path(config['application'])
    rows = [inspect_component(application, p) for p in discover(application)]
    if subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip() != source or \
            subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT).strip():
        raise RuntimeError('Inventory source changed during inspection')
    receipt = dict(schema=1, source=source,
                   xcode=xcode, components=rows, cleanup_authorized=False,
                   native_journeys_executed=False, production_qualified=False,
                   scope='Actual installed Xcode component paths/hashes/signatures only; not the failed process identity, cleanup qualification or native input causality')
    output.mkdir(mode=0o700)
    with (output / 'xcode-worker-inventory.json').open('x') as stream:
        json.dump(receipt, stream, indent=2)
    print(json.dumps(receipt, indent=2))
    return 0 if all(r['signature_verify_exit'] == r['signature_display_exit'] == 0 for r in rows) else 1


if __name__ == '__main__':
    sys.exit(main())
