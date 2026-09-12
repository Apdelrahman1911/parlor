"""One-off independently approved late-inode cleanup; no generic temp cleanup."""
from contextlib import ExitStack
import datetime
import hashlib
import json
import os
from pathlib import Path
import stat
import subprocess

CAMPAIGN = Path(__file__).resolve().parent.parent
EVIDENCE = CAMPAIGN / "evidence/ios-readiness-03"
FOLLOWUP = EVIDENCE / "secondary-fifo-readonly-followup-01.json"
APPROVAL = CAMPAIGN / "reviews/ios03-secondary-fifo-independent-ownership-review-01.json"
EXPECTED_FOLLOWUP = "24018e5e71863e327c283aa1e6551922ee49caee89bd0fb581547715765ce840"
EXPECTED_APPROVAL = "3e4dd755a8286fae719045d039b99c26a65154cb33c11c1280435caae439a6fc"
ROOT = Path("/private/var/folders/6m/vxwlbjsn7vs6h80_98w6x7p40000gn/T/ibtoold-65831")
NAMES = {"1971E2DC-A034-4F1D-9D64-A6E0DF1DC753.HostToRemote",
         "1971E2DC-A034-4F1D-9D64-A6E0DF1DC753.RemoteToHost"}


def pinned(path, expected):
    if path.is_symlink() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        raise RuntimeError("Ownership approval/evidence differs")
    return json.loads(path.read_text())


def identity(value):
    return dict(dev=value.st_dev, ino=value.st_ino, uid=value.st_uid,
                mode=value.st_mode, birth=value.st_birthtime)


def check(value, expected):
    if identity(value) != {key: expected[key] for key in identity(value)}:
        raise RuntimeError("Inode/type/UID/birth changed; preserve path")
    if stat.S_ISFIFO(value.st_mode) and (value.st_nlink != 1 or value.st_size != 0):
        raise RuntimeError("FIFO is no longer the attested empty single-link object")


def no_symlinks(path):
    current = Path(path.anchor)
    for part in path.parts[1:]:
        current /= part
        if current.is_symlink():
            raise RuntimeError("Path component became symlinked")


def check_processes():
    result = subprocess.run(["ps", "-o", "pid=,lstart=", "-p", "65831,67137"],
                            text=True, capture_output=True, timeout=15)
    if result.returncode != 1 or result.stdout.strip() or result.stderr.strip():
        raise RuntimeError("Original worker PID exists/reused, or query failed")
    result = subprocess.run(["/usr/sbin/lsof", "-nP", "-t", "+D", str(ROOT)],
                            text=True, capture_output=True, timeout=15)
    if result.returncode not in (0, 1) or result.stderr.strip():
        raise RuntimeError("Holder query failed")
    lines = set(result.stdout.splitlines())
    if any(not line.isdigit() for line in lines) or lines - {str(os.getpid())}:
        raise RuntimeError("A non-cleanup process holds the exact owned root")
    # Darwin lsof4.91 can return1 together with matching directory descriptors.
    # Use the existing reviewed numeric-PID parser contract: rc0/1, no stderr,
    # all returned holders accounted for. Our own anchored dirfds are allowed.


def main():
    pinned(APPROVAL, EXPECTED_APPROVAL)
    observed = pinned(FOLLOWUP, EXPECTED_FOLLOWUP)
    rows = {row["path"]: row for row in observed["paths"]}
    expected_paths = {str(ROOT), str(ROOT / "IB")} | {str(ROOT / "IB" / name) for name in NAMES}
    if set(rows) != expected_paths or any(not row["exists"] for row in rows.values()):
        raise RuntimeError("Followup is not the exact approved pair")
    target = EVIDENCE / "secondary-fifo-supplemental-cleanup-02.json"
    receipt = dict(status="RUNNING", at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                   approval_sha256=EXPECTED_APPROVAL, followup_sha256=EXPECTED_FOLLOWUP,
                   method="Corroborated one-off late attestation; exact FD-relative unlink/rmdir only",
                   limitation="Original cleanup FAIL is preserved; late FIFO was not live-inode-attested",
                   steps=[])
    with target.open("x") as stream:
        json.dump(receipt, stream, indent=2)
    def save():
        target.write_text(json.dumps(receipt, indent=2) + "\n")
    try:
        for raw, row in rows.items():
            no_symlinks(Path(raw))
            check(Path(raw).lstat(), row)
        if {p.name for p in ROOT.iterdir()} != {"IB"} or {p.name for p in (ROOT / "IB").iterdir()} != NAMES:
            raise RuntimeError("Unknown children; preserve entire root")
        check_processes()
        with ExitStack() as stack:
            def directory(path, parent_fd=None):
                fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=parent_fd)
                stack.callback(os.close, fd)
                return fd
            parent_fd = directory(ROOT.parent)
            root_fd = directory(ROOT.name, parent_fd)
            inner_fd = directory("IB", root_fd)
            check(os.fstat(root_fd), rows[str(ROOT)])
            check(os.fstat(inner_fd), rows[str(ROOT / "IB")])
            if set(os.listdir(root_fd)) != {"IB"} or set(os.listdir(inner_fd)) != NAMES:
                raise RuntimeError("Anchored children changed")
            check_processes()
            for name in sorted(NAMES):
                check(os.stat(name, dir_fd=inner_fd, follow_symlinks=False), rows[str(ROOT / "IB" / name)])
                os.unlink(name, dir_fd=inner_fd)
                receipt["steps"].append(dict(operation="unlink", path=str(ROOT / "IB" / name)))
                save()
            check(os.stat("IB", dir_fd=root_fd, follow_symlinks=False), rows[str(ROOT / "IB")])
            os.rmdir("IB", dir_fd=root_fd)
            receipt["steps"].append(dict(operation="rmdir", path=str(ROOT / "IB")))
            save()
            check(os.stat(ROOT.name, dir_fd=parent_fd, follow_symlinks=False), rows[str(ROOT)])
            os.rmdir(ROOT.name, dir_fd=parent_fd)
            receipt["steps"].append(dict(operation="rmdir", path=str(ROOT)))
            save()
        if any(Path(raw).exists() or Path(raw).is_symlink() for raw in expected_paths):
            raise RuntimeError("Approved paths remain")
        receipt["status"] = "PASS"
    except BaseException as error:
        receipt["status"] = "FAIL"
        receipt["error"] = type(error).__name__ + ": " + str(error)
        raise
    finally:
        receipt["finished_at"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        save()
        print(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    main()
