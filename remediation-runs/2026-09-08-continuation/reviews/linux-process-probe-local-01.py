#!/usr/bin/env python3
"""One real nonroot mechanism check; run inside the existing root-owned lane.

Only fresh /tmp packet permissions are changed. This does not audit the host,
exercise an emulator, or identify the cause of the historical hosted failure.
"""
import hashlib
import json
import os
from pathlib import Path
import pwd
import shutil
import stat
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[3]
FILES = {
    "scripts/ci/linux_process_probe.py": "2696b0d81f1113eeaea20b431191788925053703d2ed6e12d72489041c6f76b6",
    "scripts/ci/verification_hygiene.py": "1fc2fb6f0b3bb7a505edc63277e0fe974a67ccf972753eec0da60386438c16f4",
}
ENTRY = '''import json, os, pathlib, sys
root = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(root))
from scripts.ci import linux_process_probe as probe
assert os.getuid() == os.geteuid() == 65534
binding = probe.hygiene.android_sdk_binding()[1]
rows = []
for mode in (1, 0):
    row = probe.owned_dumpability_control(root / "owned", binding, mode)
    rows.append(row)
    if not row["child_reaped"] or not row["fixture_removed"]:
        break
result = dict(kind="LOCAL_NONROOT_MECHANISM_CONTROL_NOT_HOST_AUDIT", uid=os.getuid(),
    euid=os.geteuid(), binding=binding, controls=rows)
print(json.dumps(result), flush=True)
sys.exit(0 if len(rows) == 2 and all(row["result"] == "CONTROL_PASS" and
    row["child_reaped"] and row["fixture_removed"] for row in rows) else 1)
'''


def main():
    if sys.flags.optimize:
        raise RuntimeError("Optimized Python would disable this invocation's guards")
    assert os.getuid() == os.geteuid() == 0 and sys.platform == "linux"
    assert Path.cwd().resolve() == ROOT and sys.dont_write_bytecode
    destination = Path(os.environ["TMPDIR"]).resolve().parents[1]
    assert destination.parent == ROOT / "remediation-runs/2026-09-07-local-readiness/evidence"
    assert destination.name == "continuation-linux-proc-controls-01"
    output = destination / "nonroot-control.json"
    assert not output.exists()
    account = pwd.getpwnam("nobody")
    assert account.pw_uid == account.pw_gid == 65534
    packet = {name: (ROOT / name).read_bytes() for name in FILES}
    assert all(hashlib.sha256(raw).hexdigest() == FILES[name] for name, raw in packet.items())
    stage = Path(tempfile.mkdtemp(prefix="parlor-linux-proc-local-", dir="/tmp"))
    identity = stage.lstat()
    row = dict(kind="LOCAL_NONROOT_CONTROL_OUTER", result="FAIL", controls=FILES,
               stage=str(stage), stage_removed=False, observation=None)
    try:
        os.chmod(stage, 0o755)
        for relative, raw in packet.items():
            path = stage / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
            path.chmod(0o444)
        for path in (stage / "scripts", stage / "scripts/ci", stage / "sdk", stage / "sdk/emulator"):
            path.mkdir(exist_ok=True)
            path.chmod(0o755)
        entry = stage / "entry.py"
        entry.write_text(ENTRY)
        entry.chmod(0o444)
        owned = stage / "owned"
        owned.mkdir(mode=0o700)
        os.chown(owned, account.pw_uid, account.pw_gid)
        sdk = str(stage / "sdk")
        command = ["/usr/sbin/runuser", "--user", "nobody", "--", "/usr/bin/env", "-i",
                   "PATH=/usr/bin:/bin", "LANG=C.UTF-8", "ANDROID_HOME=" + sdk,
                   "ANDROID_SDK_ROOT=" + sdk, "/usr/bin/python3", "-I", "-B", str(entry)]
        row["command"] = command
        with (destination / "nonroot-control.log").open("xb") as log:
            result = subprocess.run(command, cwd="/", stdin=subprocess.DEVNULL, stdout=log,
                                    stderr=subprocess.STDOUT, timeout=45, check=False)
        row["exit_code"] = result.returncode
        raw = (destination / "nonroot-control.log").read_bytes()
        assert len(raw) < 65536
        row["observation"] = json.loads(raw)
        assert result.returncode == 0
        rows = row["observation"]["controls"]
        assert len(rows) == 2 and all(item["child_reaped"] and item["fixture_removed"] for item in rows)
        assert list(owned.iterdir()) == []
        assert all(hashlib.sha256((stage / name).read_bytes()).hexdigest() == sha for name, sha in FILES.items())
        assert all(hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == sha for name, sha in FILES.items())
        current = stage.lstat()
        assert stage.resolve() == stage and stat.S_ISDIR(current.st_mode)
        assert (current.st_dev, current.st_ino, current.st_uid) == (identity.st_dev, identity.st_ino, 0)
        shutil.rmtree(stage)  # Fresh exact packet; its only nonroot-writable directory is proven empty.
        row["stage_removed"] = not stage.exists()
        assert row["stage_removed"]
        row["result"] = "PASS_MECHANISM_ONLY"
    except BaseException as error:
        row["error_type"] = type(error).__name__
        raise
    finally:
        with output.open("x") as stream:
            json.dump(row, stream, indent=2)
            stream.write("\n")
        print(json.dumps(row), flush=True)


if __name__ == "__main__":
    main()
