#!/usr/bin/env python3
"""Read-only final observation, with evaluated cleanup and preserved historical limits.

Evidence infrastructure only. V1 and original cycle receipts remain unchanged.
Run outside a build cycle after all task-owned workers have finished. No deletion,
process signals, preference reads, builds, or simulator commands occur here.
"""
import argparse
import datetime
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

RUN = Path(__file__).resolve().parent
ROOT = RUN.parents[1]
FINAL = RUN / "final"
FROZEN = "e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7"
BASELINE = "ea1cd2652288f36b25c6e6fbec271a7713e564e010ad854a49dbecc0004be8ae"
LEGACY = "1a1c0d0f2d68cf0f4be5b93033918fdae373c7ca1cd07c8686e680b3d77a5772"
UUID = r"[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}"


def safe_path(path, boundary=ROOT, require_file=True):
    path = Path(path)
    if not path.is_absolute() or ".." in path.parts:
        raise RuntimeError("Noncanonical evidence/source path")
    path.relative_to(boundary)
    for component in [path, *path.parents]:
        if component.is_symlink():
            raise RuntimeError("Refuse evidence/source symlink ancestry")
    if require_file and not path.is_file():
        raise RuntimeError("Missing evidence/source file")
    return path


def sha(path):
    return hashlib.sha256(safe_path(path).read_bytes()).hexdigest()


def read(path):
    return json.loads(safe_path(path).read_text())


def ref(path):
    return {"path": str(safe_path(path).relative_to(ROOT)), "sha256": sha(path)}


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def cleanup_evaluation(record, legacy_digest=None, legacy_scan=None, simulator=None):
    """Pure evaluation. A failed test is not necessarily failed cleanup, or vice versa."""
    problems, limits = [], []

    def need(condition, reason):
        if not condition:
            problems.append(reason)

    need(record.get("status") in {"PASS", "FAIL"}, "Cycle is incomplete/unknown")
    need(isinstance(record.get("cleanup_completed_at"), str), "No cleanup completion timestamp")
    for field in ("cleanup_errors", "remaining_outputs"):
        need(record.get(field) == [], "Nonempty or missing " + field)
    if record.get("execution_kind") == "source-original-kotlin-unsigned-ios-dsc01-apphost-matrix":
        schema = "dsc01-apphost"
        need(record.get("cleanup_status") == "PASS", "App-host cleanup did not pass")
        stops = record.get("gradle_stops")
        need(isinstance(stops, list) and len(stops) >= 2 and all(
            type(s.get("exit_code")) is int and s["exit_code"] == 0 for s in stops
        ), "Missing or unsuccessful app-host Gradle stops")
        if isinstance(stops, list):
            need({"stop-xcode-immediate", "stop-final"}.issubset({s.get("label") for s in stops}),
                 "Missing immediate/final app-host stop stage")
        for field in ("owned_processes_remaining", "unknown_holders", "secondary_attestation_errors"):
            need(record.get(field) == [], "Nonempty or missing " + field)
        need(record.get("temporary_directory_removed") is True, "App-host temporary root retained")
        need(record.get("owned_device_absent") is True, "App-host simulator absence unverified")
        secondary = record.get("secondary_cleanup", {})
        need(isinstance(secondary, dict) and secondary.get("status") == "PASS" and
             secondary.get("remaining") == [], "Secondary FIFO cleanup incomplete")
        stages = record.get("finalization_stages", [])
        need(bool(stages) and all(s.get("status") == "PASS" for s in stages), "Finalization stage failed/missing")
    else:
        schema = "gradle-cycle"
        need(type(record.get("stop_exit_code")) is int and record["stop_exit_code"] == 0,
             "Gradle stop missing or unsuccessful")
        if legacy_digest == LEGACY:
            schema = "legacy-shell-cycle"
            need(record.get("cycle") == "ios-b1-red" and record.get("process_scan_exit_code") == 1 and
                 legacy_scan == "", "Pinned legacy scan does not match its recorded limitation")
            limits.append("Original ios-b1-red has no PID/start ownership ledger and no recorded scan command. "
                          "Its empty scan/exit1 are retained, not treated as proof of historical worker absence.")
        else:
            workers = record.get("workers")
            need(isinstance(workers, dict) and workers.get("remaining_owned_workers") == [],
                 "Owned-worker finalization missing or has survivors")
            need(record.get("retained_outputs") == [], "Retained-output disposition missing/nonempty")
        needs_simulator = any("owned_ios_simulator.init.gradle" in str(x) for x in record.get("command", []))
        if needs_simulator:
            need(simulator is not None, "Missing associated owned-simulator receipt")
        if simulator is not None:
            need(simulator.get("cleanup_errors") == [], "Simulator cleanup errors/missing disposition")
            need(simulator.get("owned_device_absent") is True, "Simulator absence unverified")
            need(simulator.get("preexisting_devices_preserved") is True, "Pre-existing devices not verified preserved")
            need(simulator.get("remaining_owned_uuid_processes") == [], "Owned simulator processes unresolved")
    return {"schema": schema, "status": "FAIL" if problems else "PASS", "problems": problems,
            "historical_pid_evidence_complete": not limits, "limitations": limits}


def cycle_observation(path):
    path = safe_path(path, RUN)
    record = read(path)
    if record.get("status") == "RUNNING":
        raise RuntimeError("Build/test cycle still running")
    digest = sha(path)
    legacy_scan = None
    associated = []
    if digest == LEGACY:
        scan = path.parent / "processes-after.txt"
        legacy_scan = safe_path(scan, RUN).read_text()
        associated.append({"kind": "legacy-limited-process-scan", "reference": ref(scan)})
    simulator_path = path.parent / "simulator/receipt.json"
    simulator = None
    if simulator_path.exists() or simulator_path.is_symlink():
        simulator = read(safe_path(simulator_path, RUN))
        associated.append({"kind": "owned-simulator", "reference": ref(simulator_path), "record": simulator})
    evaluated = cleanup_evaluation(record, digest, legacy_scan, simulator)
    # These are observation-only existence checks of previously recorded task
    # paths. No arbitrary preference/container contents are opened or deleted.
    if evaluated["schema"] == "dsc01-apphost":
        temporary = Path(record.get("owned_temporary_directory", "/"))
        if not temporary.name.startswith("parlor-audit-dsc01-apphost-") or temporary.exists() or temporary.is_symlink():
            evaluated["problems"].append("Recorded task-owned app-host root not verified absent")
        secondary = path.parent / "secondary-fifo-ownership-final.json"
        associated.append({"kind": "secondary-fifo-final-ledger", "reference": ref(secondary)})
    device_record = simulator if simulator is not None else record if evaluated["schema"] == "dsc01-apphost" else None
    if device_record is not None:
        identifier = device_record.get("owned_uuid", "")
        if not re.fullmatch(UUID, identifier):
            evaluated["problems"].append("Missing/invalid task-owned simulator identity")
        else:
            device = Path.home() / "Library/Developer/CoreSimulator/Devices" / identifier
            if device.exists() or device.is_symlink():
                evaluated["problems"].append("Previously task-owned simulator directory still present")
    evaluated["status"] = "FAIL" if evaluated["problems"] else "PASS"
    fields = ("status", "exit_code", "xcodebuild_exit_code", "runtime_evidence_status", "error", "finished_at",
              "stop_exit_code", "cleanup_completed_at", "cleanup_status", "cleanup_errors", "remaining_outputs",
              "retained_outputs", "workers", "gradle_stops", "temporary_directory_removed", "owned_device_absent",
              "owned_processes_remaining", "unknown_holders", "secondary_cleanup", "secondary_attestation_errors")
    return {"receipt": ref(path), "cycle": record.get("cycle", path.parent.name), "cleanup_evaluation": evaluated,
            "recorded_fields": {key: record[key] for key in fields if key in record}, "associated": associated}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", default="final-observation.json")
    parser.add_argument("--additional-receipt", type=Path, action="append", default=[])
    args = parser.parse_args()
    if Path(args.output).name != args.output or args.output in {".", ".."}:
        raise RuntimeError("Observation must use one new final filename")
    output = safe_path(FINAL / args.output, FINAL, require_file=False)
    if output.exists():
        raise RuntimeError("Refuse replacement of existing observation")
    with (RUN / "build-lane.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        runner = RUN / "run_gradle_cycle.py"
        if sha(runner) != "d2d18c2311d59554db88be3f92e609ec5ca46fc8af4c79702836f9e4c130240f":
            raise RuntimeError("Unreviewed build lane")
        if sha(RUN / "baseline.json") != BASELINE:
            raise RuntimeError("Original baseline identity changed")
        spec = importlib.util.spec_from_file_location("lane", runner)
        lane = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(lane)
        identity = lane.identity()
        if identity["source_manifest_sha256"] != FROZEN:
            raise RuntimeError("Frozen application source drift")
        baseline = read(RUN / "baseline.json")
        old = {item["path"]: item for item in baseline["files"]}
        changed, missing = [], []
        for name, item in old.items():
            path = safe_path(ROOT / name, ROOT, require_file=False)
            if not path.is_file():
                missing.append(name)
            elif sha(path) != item["sha256"]:
                changed.append({"path": name, "kind": item["kind"], "sha256": sha(path)})
        untracked_changed = [x for x in changed if x["kind"] != "tracked"]
        checks = {"head_unchanged": identity["commit"] == baseline["commit"],
                  "branch_unchanged": identity["branch"] == baseline["branch"],
                  "refs_unchanged": git("show-ref") == baseline["refs"].strip(),
                  "stash_unchanged": git("stash", "list") == baseline["stashes"].strip(),
                  "index_empty": not git("diff", "--cached", "--name-only"),
                  "all_original_files_present": not missing,
                  "all_preexisting_untracked_unchanged": not untracked_changed}
        remaining = [str(p.relative_to(ROOT)) for p in lane.owned_outputs() + [ROOT / "iosApp/build"]
                     if p.exists() or p.is_symlink()]
        scratch = [str(p.relative_to(ROOT)) for p in (RUN / "evidence").glob("*/scratch")
                   if p.exists() or p.is_symlink()]
        names = ("org.gradle.launcher.daemon.bootstrap.GradleDaemon", "GradleWorkerMain",
                 "org.jetbrains.kotlin.daemon.KotlinCompileDaemon")
        workers = [{"pid": p["pid"], "started": p["started"], "kind": next(n for n in names if n in p["command"])}
                   for p in lane.processes().values() if p["pid"] != os.getpid()
                   and any(n in p["command"] for n in names) and "java" in Path(p["command"].split()[0]).name.lower()]
        # Validate before resolving; a symlink alias is never used for deduplication.
        paths = set()
        for p in sorted((RUN / "evidence").glob("*/receipt.json")) + args.additional_receipt:
            p = p if p.is_absolute() else ROOT / p
            paths.add(safe_path(p, RUN))
        cycles = [cycle_observation(p) for p in sorted(paths)]
        preservation_ok = all(checks.values())
        cleanup_ok = not remaining and not scratch and all(c["cleanup_evaluation"]["status"] == "PASS" for c in cycles)
        status = "PASS" if preservation_ok and cleanup_ok else "FAIL"
        limitations = [x for c in cycles for x in c["cleanup_evaluation"]["limitations"]]
        data = {"recorded_at": datetime.datetime.now(datetime.timezone.utc).isoformat(), "reviewer": "/root",
                "observation_status": status, "preservation_verdict": "PASS" if preservation_ok else "FAIL",
                "cleanup_verdict": "PASS" if cleanup_ok else "FAIL",
                "historical_cleanup_evidence_complete": not limitations, "historical_cleanup_limits": limitations,
                "source_manifest_sha256": FROZEN, "tracked_diff_sha256": identity["diff_sha256"],
                "base_head": identity["commit"], "base_tree": identity["tree"], "branch": identity["branch"],
                "worktree_is_intentionally_dirty": True, "checks": checks, "baseline": ref(RUN / "baseline.json"),
                "baseline_files": len(old), "unchanged_original_files": len(old) - len(changed) - len(missing),
                "modified_original_files": changed, "missing_original_files": missing,
                "preexisting_untracked_changed": untracked_changed,
                "new_source_files": sorted(set(dict(identity["source_manifest"])) - set(old)),
                "generated_outputs_remaining": remaining, "cycle_scratch_remaining": scratch,
                "observed_gradle_kotlin_workers": workers,
                "worker_note": "Point-in-time observation only; unrelated workers never terminated. Modern cycle "
                               "ownership ledgers separately evaluated. Legacy PID attestation is not invented.",
                "disk_free_bytes": shutil.disk_usage(ROOT).free,
                "retained_remediation_evidence_bytes": sum(p.stat().st_size for p in RUN.rglob("*")
                                                            if p.is_file() and not p.is_symlink()),
                "cycles_including_post_materialization": cycles,
                "final_deliverables": [ref(p) for p in sorted(FINAL.iterdir()) if p.is_file() or p.is_symlink()],
                "recorder": ref(Path(__file__)), "protected_exclusions": baseline["protected_exclusions"],
                "limitations": "Evaluated cleanup is not test success or whole-system RAM proof. Known historical "
                               "limits remain explicit. Source identity excludes evidence-only controls. No "
                               "private/player data inspected and no release/signing/Store readiness claim."}
        with output.open("x") as stream:
            json.dump(data, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
        print(json.dumps({"status": status, "observation": ref(output), "checks": checks,
                          "cleanup_verdict": data["cleanup_verdict"], "remaining_outputs": remaining,
                          "historical_cleanup_evidence_complete": not limitations, "observed_workers": workers}, indent=2))
        return 0 if status == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
