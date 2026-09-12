"""Per-cycle Apple CI ownership: stop Gradle, owned simulator, and Xcode workers.

Restricted to the closed isolated GitHub `ios` / `ios-release` job-cycle map.
Never changes RUNNER_TRACKING_ID,
signals a numeric PID/PGID, reuses a device profile, or removes build evidence.
The final output cleaner separately requires these receipts and retained uploads.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
import time
import uuid
from pathlib import Path

from scripts.ci import owned_ci_simulator as simulator
from scripts.ci import verification_hygiene as common
from scripts.ci.darwin_worker_identity import DarwinWorkerBackend, MAX_PROCESSES

CYCLES = common.APPLE_CYCLES
WORKER_PATHS = (
    "Contents/Developer/usr/bin/xcodebuild",
    "Contents/Developer/usr/bin/ibtoold",
    "Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib/"
    "sourcekitd.framework/Versions/A/XPCServices/SourceKitService.xpc/Contents/MacOS/SourceKitService",
    "Contents/SharedFrameworks/XCBuild.framework/Versions/A/PlugIns/"
    "XCBBuildService.bundle/Contents/MacOS/XCBBuildService",
)
MAX_WORKERS = 64


def digest(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def source(root: Path) -> dict:
    identity = common.source_identity(root)
    return {key: identity[key] for key in ("root", "head", "tree")}


def configuration(root: Path) -> dict:
    policy = simulator.read_record(root / "config/release-policy.json")
    expected = policy["toolchains"]["apple"]["developer_dir"]
    if os.environ.get("DEVELOPER_DIR") != expected:
        raise RuntimeError("Apple cleanup requires the exact policy-selected Xcode")
    developer = Path(expected).resolve(strict=True)
    if developer.name != "Developer" or developer.parent.name != "Contents":
        raise RuntimeError("Unexpected Xcode application layout")
    application = developer.parent.parent
    workers = []
    for relative in WORKER_PATHS:
        path = (application / relative).resolve(strict=True)
        if application not in path.parents or not path.is_file() or not os.access(path, os.X_OK):
            raise RuntimeError("Missing or redirected pinned Xcode worker executable")
        workers.append(str(path))
    tracking = os.environ.get("RUNNER_TRACKING_ID", "")
    if not re.fullmatch(r"github_[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}", tracking):
        raise RuntimeError("Missing exact GitHub runner process-tracking identity")
    return {"application": str(application), "developer_dir": str(developer),
            "workers": workers, "tracking_sha256": digest(tracking), "uid": os.getuid()}


def cycle_prefix(prefix: Path, cycle: str) -> Path:
    if cycle not in CYCLES:
        raise RuntimeError("Unknown Apple verification cycle")
    return Path(str(prefix) + "-" + cycle)


def claim_path(prefix: Path) -> Path:
    return Path(str(prefix) + "-ownership.json")


def prepare(root: Path, prefix: Path, task: dict, cycle: str, backend, config: dict) -> dict:
    if cycle not in common.apple_cycles(task):
        raise RuntimeError("Apple verification cycle is not assigned to this job")
    if common.git(root, "status", "--porcelain").strip():
        raise RuntimeError("Apple cycle must begin with unchanged source")
    baseline = backend.lifetimes()
    if len(baseline) > MAX_PROCESSES:
        raise RuntimeError("Excessive process ownership baseline")
    claim = {"schema": 1, "task": task, "source": source(root), "cycle": cycle,
             "nonce": uuid.uuid4().hex, "prepared_at": common.timestamp(),
             "not_before_us": time.time_ns() // 1000, "baseline": baseline, **config}
    common.write_new(claim_path(prefix), claim)
    return claim


def validate_claim(root: Path, prefix: Path, task: dict, cycle: str, config: dict) -> dict:
    if cycle not in common.apple_cycles(task):
        raise RuntimeError("Apple verification cycle is not assigned to this job")
    claim = simulator.read_record(claim_path(prefix))
    if (claim.get("schema") != 1 or claim.get("task") != task or
            claim.get("source") != source(root) or claim.get("cycle") != cycle or
            any(claim.get(key) != value for key, value in config.items()) or
            not re.fullmatch(r"[0-9a-f]{32}", str(claim.get("nonce", ""))) or
            common.git(root, "status", "--porcelain").strip()):
        raise RuntimeError("Apple cycle source/task/toolchain/tracking ownership changed")
    baseline = claim.get("baseline")
    if not isinstance(baseline, list) or len(baseline) > MAX_PROCESSES:
        raise RuntimeError("Invalid process ownership baseline")
    for value in baseline:
        if (not isinstance(value, list) or len(value) != 3 or
                type(value[0]) is not int or value[0] <= 0 or value[1] != config["uid"] or
                not isinstance(value[2], list) or len(value[2]) != 2 or
                any(type(part) is not int for part in value[2]) or
                value[2][0] <= 0 or not 0 <= value[2][1] < 1000000):
            raise RuntimeError("Invalid native process lifetime")
    if type(claim.get("not_before_us")) is not int or claim["not_before_us"] <= 0:
        raise RuntimeError("Missing Apple cycle start boundary")
    return claim


def stop_workers(claim: dict, backend, tracking: str, clock=time.monotonic, sleep=time.sleep) -> dict:
    if digest(tracking) != claim["tracking_sha256"]:
        raise RuntimeError("Runner tracking identity changed; no worker signal authorized")
    baseline = {(value[0], value[1], tuple(value[2])) for value in claim["baseline"]}
    application = Path(claim["application"])
    known, denied, excluded, events, errors = {}, set(), set(), [], []
    quarantined_pids = set()
    observed = set()
    started, quiet = clock(), 0

    def error(identity, message: str) -> None:
        key = identity.lifetime if identity is not None else None
        if key not in denied:
            denied.add(key)
            if identity is not None:
                quarantined_pids.add(identity.pid)
            errors.append({"pid": identity.pid if identity is not None else None, "error": message})

    for iteration in range(121):
        alive = []
        try:
            candidates = backend.snapshot(application, baseline)
            # Re-open previously admitted lifetimes even if they exec outside Xcode.
            for identity, _ in known.values():
                current = backend.read(identity.pid)
                if current is not None and current != identity:
                    error(identity, "Admitted worker changed generation/executable; signal grant revoked")
                elif current is not None and current not in candidates:
                    candidates.append(current)
            if len(candidates) > MAX_WORKERS or len(known) + len(denied) + len(excluded) > MAX_WORKERS:
                raise RuntimeError("Xcode worker cleanup ceiling exceeded")
            for identity in candidates:
                key = identity.lifetime
                if key in baseline or key in denied or key in excluded or identity.pid in quarantined_pids:
                    continue
                if key not in observed:
                    if len(observed) >= MAX_WORKERS:
                        raise RuntimeError("Xcode worker-history ceiling exceeded")
                    observed.add(key)
                if (identity.uid != claim["uid"] or application not in Path(identity.command).parents):
                    error(identity, "Unexpected worker ownership or executable")
                    continue
                if identity.started[0] * 1000000 + identity.started[1] < claim["not_before_us"]:
                    excluded.add(key)  # Predates the cycle, including a baseline-scan race.
                    continue
                previous = known.get(key)
                if previous is not None and previous[0] != identity:
                    error(identity, "Worker identity changed after admission")
                    continue
                try:
                    marker = backend.tracking_id(identity)
                    current = backend.read(identity.pid)
                    if current is None:
                        continue
                    if current != identity:
                        raise RuntimeError("Worker changed during tracking attestation")
                    if marker is None:
                        raise RuntimeError("New Xcode worker has no readable task-tracking marker")
                    if marker != tracking:
                        if previous is not None:
                            raise RuntimeError("Admitted worker changed task-tracking marker")
                        excluded.add(key)
                        continue
                    if identity.command not in claim["workers"]:
                        raise RuntimeError("Unrecognized task-tracked Xcode worker executable")
                    alive.append(identity)
                    last_signal = previous[1] if previous is not None else None
                    signum = 15 if last_signal is None else 9 if clock() - started >= 5 and last_signal == 15 else None
                    if signum is not None:
                        sent = backend.signal(identity, signum)
                        known[key] = (identity, signum)
                        events.append({"pid": identity.pid, "started": identity.started,
                                       "executable": str(Path(identity.command).relative_to(application)),
                                       "signal": signum, "delivered": sent})
                except (OSError, ValueError, RuntimeError) as failure:
                    if backend.read(identity.pid) is not None:
                        error(identity, str(failure))
        except (OSError, ValueError, RuntimeError) as failure:
            error(None, str(failure))
            break
        quiet = quiet + 1 if not alive else 0
        if quiet >= 2:
            break
        if clock() - started >= 12 or iteration == 120:
            for identity in alive:
                error(identity, "Task-owned Xcode worker survived bounded TERM/KILL cleanup")
            break
        sleep(0.1)
    return {"result": "FAIL" if errors else "PASS", "events": events, "errors": errors,
            "excluded_lifetimes": len(excluded), "baseline_lifetimes": len(baseline),
            "scope": "New exact-Xcode workers with unchanged exact runner marker and native audit identity"}


def finish(root: Path, prefix: Path, task: dict, cycle: str, preparation: str, run: str,
           backend_factory=DarwinWorkerBackend, simulator_factory=simulator.SimctlBackend) -> dict:
    receipt = {"schema": 1, "task": task, "cycle": cycle, "started_at": common.timestamp(),
               "prepare_outcome": preparation, "run_outcome": run, "errors": []}
    # First operation even on failed/skipped/cancelled cycles: release Gradle RAM.
    receipt["gradle_stop"] = common.stop_gradle(root)
    try:
        if cycle not in common.apple_cycles(task):
            raise RuntimeError("Apple verification cycle is not assigned to this job")
        receipt["source"] = source(root)
        if receipt["gradle_stop"]["exit_code"] != 0:
            raise RuntimeError("Gradle stop failed; do not race ongoing compilation")
        files = [claim_path(prefix), *simulator.paths(prefix), simulator.adoption_path(prefix)]
        if preparation == "skipped" and run == "skipped":
            if any(path.exists() or path.is_symlink() for path in files):
                raise RuntimeError("Skipped cycle cannot reuse historical resource claims")
            receipt.update(result="NOT_RUN", workers={"result": "NOT_RUN"}, simulator={"result": "NOT_CREATED"})
            return receipt
        if preparation != "success" or run not in {"success", "failure", "cancelled", "skipped"}:
            raise RuntimeError("Fresh Apple cycle preparation did not succeed")
        claim = validate_claim(root, prefix, task, cycle, configuration(root))
        receipt["claim_sha256"] = hashlib.sha256(claim_path(prefix).read_bytes()).hexdigest()
        # Quiesce cancellation-surviving xcodebuild before touching its device.
        backend = backend_factory()
        receipt["workers_before_simulator"] = stop_workers(claim, backend, os.environ.get("RUNNER_TRACKING_ID", ""))
        if receipt["workers_before_simulator"]["result"] != "PASS":
            raise RuntimeError("Xcode work is not quiescent; retain device and build evidence")
        try:
            if cycle != "apple-ui" and any(path.exists() or path.is_symlink() for path in
                                           (*simulator.paths(prefix), simulator.adoption_path(prefix))):
                raise RuntimeError("Simulator journal is not allowed in this cycle")
            receipt["simulator"] = simulator.cleanup(prefix, claim, simulator_factory()) if cycle == "apple-ui" else {"result": "NOT_APPLICABLE"}
        except (OSError, ValueError, RuntimeError, KeyError, TypeError, AttributeError, subprocess.SubprocessError) as failure:
            receipt["errors"].append({"component": "simulator", "error": str(failure)})
        try:
            # simctl may itself have requested XPC work; verify quiescence again.
            receipt["workers"] = stop_workers(claim, backend, os.environ.get("RUNNER_TRACKING_ID", ""))
            if receipt["workers"]["result"] != "PASS":
                receipt["errors"].append({"component": "workers", "error": "See bounded native-worker receipt"})
        except (OSError, ValueError, RuntimeError) as failure:
            receipt["errors"].append({"component": "workers", "error": str(failure)})
    except (OSError, ValueError, RuntimeError, KeyError, TypeError, AttributeError, subprocess.SubprocessError) as failure:
        receipt["errors"].append({"error": str(failure)})
    finally:
        receipt["finished_at"] = common.timestamp()
    receipt["result"] = "FAIL" if receipt["errors"] else "PASS"
    return receipt


def main() -> int:
    root, prefix, task = common.context()
    mode, cycle = sys.argv[1:]
    owned = cycle_prefix(prefix, cycle)
    if mode != "finish" and cycle not in common.apple_cycles(task):
        raise RuntimeError("Apple verification cycle is not assigned to this job")
    if mode == "prepare":
        config = configuration(root)
        prepare(root, owned, task, cycle, DarwinWorkerBackend(), config)
        return 0
    if mode == "create-simulator":
        if task["GITHUB_JOB"] != "ios" or cycle != "apple-ui":
            raise RuntimeError("Only the ios/apple-ui cycle may create a simulator")
        claim = validate_claim(root, owned, task, cycle, configuration(root))
        print(simulator.create(owned, claim, simulator.SimctlBackend()))
        return 0
    if mode == "finish":
        result = finish(root, owned, task, cycle, os.environ.get("PARLOR_APPLE_PREPARE_OUTCOME", ""),
                        os.environ.get("PARLOR_APPLE_RUN_OUTCOME", ""))
        print(json.dumps(result))
        common.write_new(Path(str(prefix) + "-stop-" + cycle + ".json"), result)
        return 0 if result["result"] in {"PASS", "NOT_RUN"} else 1
    raise RuntimeError("Unknown Apple ownership mode")


if __name__ == "__main__":
    raise SystemExit(main())
