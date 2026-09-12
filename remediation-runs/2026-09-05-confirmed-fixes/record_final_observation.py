#!/usr/bin/env python3
"""Post-cycle, read-only preservation/resource observation; create a new evidence file."""
import argparse
import datetime
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess

RUN = Path(__file__).resolve().parent
ROOT = RUN.parents[1]
FINAL = RUN / "final"
FROZEN = "e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text())


def ref(path):
    return {"path": str(path.relative_to(ROOT)), "sha256": sha(path)}


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", default="final-observation.json")
    parser.add_argument("--additional-receipt", type=Path, action="append", default=[])
    args = parser.parse_args()
    output = FINAL / args.output
    if output.parent != FINAL or output.exists() or output.is_symlink():
        raise RuntimeError("Observation must be a new task-owned final filename")
    with (RUN / "build-lane.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        runner = RUN / "run_gradle_cycle.py"
        if sha(runner) != "d2d18c2311d59554db88be3f92e609ec5ca46fc8af4c79702836f9e4c130240f":
            raise RuntimeError("Unreviewed build lane")
        spec = importlib.util.spec_from_file_location("lane", runner)
        lane = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(lane)
        identity = lane.identity()
        if identity["source_manifest_sha256"] != FROZEN:
            raise RuntimeError("Frozen source drift; do not claim final preservation")
        baseline = read(RUN / "baseline.json")
        old = {item["path"]: item for item in baseline["files"]}
        changed, missing = [], []
        for name, item in old.items():
            path = ROOT / name
            if not path.is_file() or path.is_symlink():
                missing.append(name)
            elif sha(path) != item["sha256"]:
                changed.append({"path": name, "kind": item["kind"], "sha256": sha(path)})
        untracked_changed = [x for x in changed if x["kind"] != "tracked"]
        checks = {
            "head_unchanged": identity["commit"] == baseline["commit"],
            "branch_unchanged": identity["branch"] == baseline["branch"],
            "refs_unchanged": git("show-ref") == baseline["refs"].strip(),
            "stash_unchanged": git("stash", "list") == baseline["stashes"].strip(),
            "index_empty": not git("diff", "--cached", "--name-only"),
            "all_original_files_present": not missing,
            "all_preexisting_untracked_unchanged": not untracked_changed,
        }
        outputs = lane.owned_outputs() + [ROOT / "iosApp/build"]
        remaining = [str(p.relative_to(ROOT)) for p in outputs if p.exists() or p.is_symlink()]
        scratch = [str(p.relative_to(ROOT)) for p in (RUN / "evidence").glob("*/scratch") if p.exists()]
        process_data = lane.processes()
        worker_names = ("org.gradle.launcher.daemon.bootstrap.GradleDaemon", "GradleWorkerMain",
                        "org.jetbrains.kotlin.daemon.KotlinCompileDaemon")
        workers = [{"pid": p["pid"], "started": p["started"],
                    "kind": next(name for name in worker_names if name in p["command"])}
                   for p in process_data.values() if p["pid"] != os.getpid()
                   and any(name in p["command"] for name in worker_names)
                   and ("java" in Path(p["command"].split()[0]).name.lower())]
        cycles = []
        for path in sorted((RUN / "evidence").glob("*/receipt.json")) + args.additional_receipt:
            path = path.resolve()
            if not path.is_relative_to(RUN):
                raise RuntimeError("Additional receipt outside this task")
            record = read(path)
            if record.get("status") == "RUNNING":
                raise RuntimeError("Cycle still running: " + str(path))
            cycles.append({"receipt": ref(path), "cycle": record.get("cycle", path.parent.name),
                           **{key: record.get(key) for key in (
                               "status", "exit_code", "finished_at", "stop_exit_code",
                               "cleanup_completed_at", "cleanup_status", "cleanup_errors",
                               "remaining_outputs", "workers")}})
        if remaining or scratch or not all(checks.values()):
            raise RuntimeError("Final preservation/output observation not clear")
        # Observed unrelated workers, if any, are reported but never terminated.
        data = {
            "recorded_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "reviewer": "/root", "source_manifest_sha256": FROZEN,
            "tracked_diff_sha256": identity["diff_sha256"], "base_head": identity["commit"],
            "base_tree": identity["tree"], "branch": identity["branch"],
            "worktree_is_intentionally_dirty": True, "checks": checks,
            "baseline": ref(RUN / "baseline.json"), "baseline_files": len(old),
            "unchanged_original_files": len(old) - len(changed), "modified_original_files": changed,
            "missing_original_files": missing, "preexisting_untracked_changed": untracked_changed,
            "new_source_files": sorted(set(dict(identity["source_manifest"])) - set(old)),
            "generated_outputs_remaining": remaining, "cycle_scratch_remaining": scratch,
            "observed_gradle_kotlin_workers": workers,
            "worker_note": "Observation only. No process was stopped by this recorder; per-cycle ownership cleanup "
                           "is recorded in the original receipts. Unrelated processes and global caches remain untouched.",
            "disk_free_bytes": shutil.disk_usage(ROOT).free,
            "retained_remediation_evidence_bytes": sum(p.stat().st_size for p in RUN.rglob("*")
                                                       if p.is_file() and not p.is_symlink()),
            "cycles_including_post_materialization": cycles,
            "final_deliverables": [ref(p) for p in sorted(FINAL.iterdir()) if p.is_file() and p != output],
            "recorder": ref(Path(__file__)),
            "protected_exclusions": baseline["protected_exclusions"],
            "limitations": "No whole-system RAM benchmark or private/user-data inspection. A process observation is "
                           "point-in-time; no release/signing/Store readiness claim. Application source freeze remains "
                           "independent of evidence-only harness/control files.",
        }
        with output.open("x") as stream:
            json.dump(data, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
        print(json.dumps({"status": "PASS", "observation": ref(output), "checks": checks,
                          "remaining_outputs": remaining, "observed_workers": workers}, indent=2))


if __name__ == "__main__":
    main()
