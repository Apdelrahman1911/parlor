"""Read only our synthetic child environment; use its exact audit token to stop it."""
from __future__ import annotations
import datetime, hashlib, json, os, subprocess, sys, time, uuid
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.ci.darwin_worker_identity import DarwinWorkerBackend

def main():
    report = {"started_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "scope": "Darwin identity, own synthetic child tracking and audit-token signal; NOT Xcode XPC propagation or qualified-CI evidence",
              "source": {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in (
                  "scripts/ci/darwin_worker_identity.py", __file__.removeprefix(str(ROOT) + "/"))}}
    marker = "github_" + str(uuid.uuid4())
    child = None
    try:
        backend = DarwinWorkerBackend()
        # Never alter the parent/CI tracking marker or inspect an unrelated environment.
        child = subprocess.Popen(["/usr/bin/python3", "-B", "-c", "import time; time.sleep(15)"],
                                 env={"PATH": "/usr/bin:/bin", "RUNNER_TRACKING_ID": marker},
                                 stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(0.2)  # Allow exec; then immutable identity is checked by the backend.
        identity = backend.read(child.pid)
        if identity is None or identity.uid != os.getuid() or identity.pid != child.pid:
            raise RuntimeError("Owned child was not identified")
        if backend.tracking_id(identity) != marker:
            raise RuntimeError("Owned child tracking mismatch")
        if backend.read(child.pid) != identity:
            raise RuntimeError("Owned child generation changed")
        report["identity"] = {"pid": identity.pid, "uid": identity.uid, "started": identity.started,
                              "executable": identity.command, "marker_sha256": hashlib.sha256(marker.encode()).hexdigest()}
        report["signal_delivered"] = backend.signal(identity, 15)
        report["child_exit_code"] = child.wait(timeout=20)
        if not report["signal_delivered"] or report["child_exit_code"] != -15:
            raise RuntimeError("Expected audit-token-bound TERM was not observed")
        if backend.read(child.pid) is not None:
            raise RuntimeError("Owned child still exists after reap")
        report["result"] = "PASS"
    except BaseException as error:
        report.update(result="FAIL", error=type(error).__name__, reason=str(error))
    finally:
        # No numeric PID/PGID fallback. Even on API denial the bounded child
        # exits after15s and remains ours/unreaped until this wait completes.
        if child is not None:
            try:
                report["cleanup_child_exit_code"] = child.wait(timeout=25)
                report["child_reaped"] = True
            except BaseException as error:
                report.update(result="FAIL", cleanup_error=type(error).__name__,
                              child_reaped=False, cleanup_child_pid=child.pid)
        report["finished_at"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        print(json.dumps(report, indent=2))
    return 0 if report.get("result") == "PASS" else 1

if __name__ == "__main__":
    raise SystemExit(main())
