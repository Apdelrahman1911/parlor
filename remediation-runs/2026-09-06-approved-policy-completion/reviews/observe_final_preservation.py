#!/usr/bin/env python3
"""Read-only, additive observation; root runs directly AFTER the build lane is free.

No Gradle, Xcode, simctl, deletion, signals, imports of task runners, or personal
device enumeration. Device observations are exact owned-directory lstat only,
not a new CoreSimulator registry query. This is not a build/test/runtime gate.
"""
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys

RUN = Path(__file__).resolve().parents[1]
ROOT = RUN.parents[1]
BASELINE_SHA = "ed49f68cf2f6d583e28f461428b56afb8cdfbdba619858cc8d541117e3b147df"
REVIEW_SHA = "fd65f41ae0a8daaf39a5360bee385c46445bd8ca7d8bcd09b28a3fff5fdbeb2b"
MANIFEST_SHA = "9ac2be536979f2216b39c10a6d148125a12380987d4efd483bbcbaeb1733318e"
EXCLUDED = ("audit-runs/", "remediation-runs/", "project-code-audit/", "design/")
PROTECTED = (".keystore", ".p12", ".p8", ".mobileprovision", "credentials.json",
             "service-account", "local.properties")
TEMP = re.compile(r"^/(?:private/)?var/folders/[^/]+/[^/]+/T/parlor-audit-[^/]+$")
FIFO = re.compile(r"^/private/var/folders/[^/]+/[^/]+/T/ibtoold-\d+"
                  r"(?:/IB(?:/[A-Fa-f0-9-]{36}\.(?:HostToRemote|RemoteToHost))?)?$")
UUID = re.compile(r"^[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}$")


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def safe_file(path):
    relative = path.relative_to(ROOT)
    if ".." in relative.parts or any(piece in str(relative).lower() for piece in PROTECTED):
        raise ValueError("Protected or invalid repository file")
    if any(parent.is_symlink() for parent in [path, *path.parents] if parent != ROOT and ROOT in parent.parents):
        raise ValueError("Refusing a symlinked repository input: " + str(relative))
    if not stat.S_ISREG(path.lstat().st_mode):
        raise ValueError("Not a regular repository input: " + str(relative))
    return path


def digest(path):
    value = hashlib.sha256()
    with safe_file(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def reference(path):
    return {"path": str(path.relative_to(ROOT)), "sha256": digest(path)}


def read_json(path):
    safe_file(path)
    return json.loads(path.read_text())


def git(*arguments):
    env = dict(os.environ, GIT_OPTIONAL_LOCKS="0", GIT_PAGER="cat")
    return subprocess.check_output(["git", *arguments], cwd=ROOT, env=env, timeout=30)


def source_identity():
    paths = git("ls-files", "--cached", "--others", "--exclude-standard", "-z").decode().split("\0")
    records = []
    for relative in sorted(set(paths)):
        if not relative or relative.startswith(EXCLUDED) or any(part in relative.lower() for part in PROTECTED):
            continue
        records.append([relative, digest(ROOT / relative)])
    return {
        "commit": git("rev-parse", "HEAD").decode().strip(),
        "tree": git("rev-parse", "HEAD^{tree}").decode().strip(),
        "branch": git("branch", "--show-current").decode().strip(),
        "tracked_status": git("status", "--porcelain=v1", "--untracked-files=no").decode().strip(),
        "diff_sha256": hashlib.sha256(git("diff", "--no-ext-diff", "--binary", "HEAD", "--")).hexdigest(),
        "source_manifest": records,
        "source_manifest_sha256": hashlib.sha256(json.dumps(records, separators=(",", ":")).encode()).hexdigest(),
    }


def path_observation(path):
    try:
        value = path.lstat()
        return {"path": str(path), "status": "PRESENT_NOT_TOUCHED", "inode": value.st_ino,
                "device": value.st_dev, "uid": value.st_uid, "mode": value.st_mode}
    except FileNotFoundError:
        return {"path": str(path), "status": "ABSENT"}
    except OSError as error:
        return {"path": str(path), "status": "UNVERIFIED", "error_type": type(error).__name__}


def source_and_baseline(record):
    frozen = read_json(RUN / "source-freeze-02.json")["source"]
    baseline_path = RUN / "baseline.json"
    prior_path = RUN / "reviews/independent-final-source-materialized-01.json"
    if digest(baseline_path) != BASELINE_SHA or digest(prior_path) != REVIEW_SHA:
        raise ValueError("Pinned baseline or independent preservation review changed")
    if frozen["source_manifest_sha256"] != MANIFEST_SHA or len(frozen["source_manifest"]) != 669:
        raise ValueError("Frozen source identity is not the reviewed 669-input version")
    current = source_identity()
    record["source_matches_freeze02"] = current == frozen
    record["source"] = {key: value for key, value in current.items() if key not in ("source_manifest", "tracked_status")}
    record["source_input_count"] = len(current["source_manifest"])
    expected = dict(frozen["source_manifest"])
    record["source_drift"] = [path for path, value in current["source_manifest"] if expected.get(path) != value]
    record["source_missing"] = sorted(set(expected) - dict(current["source_manifest"]).keys())
    baseline = read_json(baseline_path)
    approved = read_json(prior_path)["baseline_preservation"]["authorized_changed"]
    allowed = {item["path"]: item["current_sha256"] for item in approved}
    changed, identical, missing = [], [], []
    for item in baseline["files"]:
        path = ROOT / item["path"]
        try:
            actual = digest(path)
        except FileNotFoundError:
            missing.append(item["path"])
            continue
        if actual == item["sha256"]:
            identical.append(item["path"])
        else:
            changed.append({"path": item["path"], "baseline_sha256": item["sha256"], "current_sha256": actual})
    record["baseline_preservation"] = {
        "baseline": reference(baseline_path), "approved_changes_review": reference(prior_path),
        "files": len(baseline["files"]), "byte_identical": len(identical), "changed": changed, "missing": missing,
        "exactly_13_approved_changes": len(changed) == 13 and {x["path"]: x["current_sha256"] for x in changed} == allowed,
        "groups": [{"prefix": prefix, "baseline_count": sum(x["path"].startswith(prefix) for x in baseline["files"]),
                    "byte_identical": sum(path.startswith(prefix) for path in identical)} for prefix in EXCLUDED],
        "AGENTS_unchanged": "AGENTS.md" in identical, "protected_exclusions": baseline["protected_exclusions"],
    }
    record["git_preservation"] = {}
    for name, arguments in [("refs", ["show-ref"]), ("stashes", ["stash", "list"]),
                            ("index_diff", ["diff", "--no-ext-diff", "--cached", "--binary"])]:
        value = git(*arguments).decode().strip()
        record["git_preservation"][name] = {"unchanged": value == baseline[name],
                                                  "sha256": hashlib.sha256(value.encode()).hexdigest()}
    record["git_preservation"]["qualification"] = "Logical staged diff, refs and stash list only; not Git index stat-cache identity or stash-content inspection. Intentionally dirty approved work is preserved."
    return current


def observed_owned_resources(record):
    paths, owners, devices, histories, inputs, scratch = {}, {}, {}, [], [], []

    def owner(value, evidence):
        started = value.get("start", value.get("started"))
        if not isinstance(value.get("pid"), int) or value["pid"] <= 0 or not isinstance(started, str) or not started:
            raise ValueError("Unrecognized recorded process identity in " + evidence)
        owners.setdefault((value["pid"], " ".join(started.split())), set()).add(evidence)

    def device(data, evidence):
        identity = data.get("owned_uuid")
        if identity is None:
            return
        if not UUID.fullmatch(identity):
            raise ValueError("Invalid owned simulator UUID in " + evidence)
        devices.setdefault(identity, set()).add(evidence)

    receipts = sorted((RUN / "evidence").glob("*/receipt.json"))
    if not receipts:
        raise ValueError("No cycle evidence")
    for path in receipts:
        data = read_json(path)
        native = "execution_kind" in data
        completed_statuses = ("PASS", "FAIL", "PARTIALLY_VERIFIED") if native else ("PASS", "FAIL")
        if data.get("status") not in completed_statuses or not data.get("finished_at") or not data.get("cleanup_completed_at"):
            raise ValueError("Unfinished cycle; wait for the lane: " + path.parent.name)
        inputs.append(reference(path))
        evidence = str(path.relative_to(ROOT))
        scratch.append({**path_observation(path.parent / "scratch"), "evidence": evidence,
                        "applicability": "Normal lane creates this exact path; native cycles normally do not."})
        required = ["gradle_stops", "cleanup_status", "owned_processes_remaining"] if native else ["stop_exit_code", "workers"]
        if any(key not in data for key in [*required, "cleanup_errors", "remaining_outputs"]):
            raise ValueError("Unknown cleanup receipt schema: " + evidence)
        histories.append({"cycle": path.parent.name, "status": data["status"],
                          "stop": data["gradle_stops"] if native else data["stop_exit_code"],
                          "cleanup_status": data.get("cleanup_status"), "cleanup_errors": data["cleanup_errors"],
                          "historical_remaining_outputs": data["remaining_outputs"]})
        for field in ("owned_temporary_directory", "allocated_temporary_path"):
            if data.get(field):
                value = data[field]
                if not TEMP.fullmatch(value):
                    raise ValueError("Unexpected temporary ownership path: " + evidence)
                paths.setdefault(value, set()).add(evidence)
        for value in data.get("ownership_observed", []):
            owner(value, evidence)
        for field in ("terminated_owned_workers", "remaining_owned_workers"):
            for value in data.get("workers", {}).get(field, []):
                owner(value, evidence)
        device(data, evidence)
        simulator = path.parent / "simulator/receipt.json"
        if simulator.exists():
            inputs.append(reference(simulator))
            device(read_json(simulator), str(simulator.relative_to(ROOT)))
        fifo = path.parent / "secondary-fifo-ownership-final.json"
        if fifo.exists():
            inputs.append(reference(fifo))
            fifo_data = read_json(fifo)
            if not isinstance(fifo_data.get("records"), list):
                raise ValueError("Unknown FIFO ownership schema")
            for entry in fifo_data["records"]:
                leaf = Path(entry["path"])
                if not FIFO.fullmatch(str(leaf)) or leaf.parent.name != "IB" or not leaf.name.endswith((".HostToRemote", ".RemoteToHost")):
                    raise ValueError("Unexpected attested FIFO leaf path")
                # Fixed ancestors of this exact attested leaf, never a glob or
                # arbitrary temporary parent. Old metadata can contain leaves only.
                for exact in (leaf, leaf.parent, leaf.parent.parent):
                    paths.setdefault(str(exact), set()).add(str(fifo.relative_to(ROOT)))
                for field in ("process", "parent"):
                    owner(entry[field], str(fifo.relative_to(ROOT)))
                for metadata in entry["metadata"]:
                    value = metadata["path"]
                    if not FIFO.fullmatch(value):
                        raise ValueError("Unexpected FIFO ownership path")
                    paths.setdefault(value, set()).add(str(fifo.relative_to(ROOT)))
    record["cycle_history"] = histories
    record["evidence_inputs"] = inputs
    record["cycle_scratch_directories"] = scratch
    record["owned_temporary_paths"] = [{**path_observation(Path(path)), "evidence": sorted(evidence)}
                                       for path, evidence in sorted(paths.items())]
    device_root = Path.home() / "Library/Developer/CoreSimulator/Devices"
    record["owned_device_directories"] = [{**path_observation(device_root / identity), "uuid": identity,
                                          "evidence": sorted(evidence)} for identity, evidence in sorted(devices.items())]
    record["device_observation_qualification"] = "Exact recorded UUID directory lstat only; no new simctl registry/device query or native service start. Prior owned-device deletion receipts remain separate evidence."
    current = {}
    pids = sorted({identity[0] for identity in owners})
    for offset in range(0, len(pids), 128):
        command = ["ps", "-p", ",".join(map(str, pids[offset:offset + 128])), "-o", "pid=,lstart="]
        result = subprocess.run(command, text=True, capture_output=True, timeout=15)
        if result.returncode not in (0, 1) or result.stderr.strip():
            raise ValueError("Unable to observe exact recorded process IDs")
        for line in result.stdout.splitlines():
            pid, started = line.split(None, 1)
            current[int(pid)] = " ".join(started.split())
    record["owned_process_identities"] = [{"pid": pid, "recorded_start": started, "evidence": sorted(evidence),
        "status": "ABSENT" if pid not in current else "PID_REUSED_UNRELATED_NOT_TOUCHED" if current[pid] != started else "RECORDED_OWNER_STILL_PRESENT_NOT_TOUCHED",
        "current_start": current.get(pid)} for (pid, started), evidence in sorted(owners.items())]
    record["process_observation_qualification"] = "Only recorded PID/start identities were queried; no signals or unrelated argv. Gradle receipts do not retain every historical descendant. This is not a census or cleanup guarantee for unrecorded workers."
    modules = [p.parent for pattern in ("shared/*/build.gradle.kts", "game-modes/*/build.gradle.kts") for p in ROOT.glob(pattern)]
    modules += [ROOT, ROOT / "composeApp", ROOT / "iosApp", ROOT / "build-logic", ROOT / "build-logic/convention"]
    record["original_generated_outputs"] = [path_observation(path / "build") for path in sorted(set(modules))]


def observe(record):
    before = source_and_baseline(record)
    observed_owned_resources(record)
    record["source_stable_during_observation"] = source_identity() == before
    preservation = record["baseline_preservation"]
    bad = not record["source_matches_freeze02"] or not record["source_stable_during_observation"]
    bad |= preservation["files"] != 2180 or preservation["byte_identical"] != 2167 or bool(preservation["missing"])
    bad |= not preservation["exactly_13_approved_changes"] or not preservation["AGENTS_unchanged"]
    bad |= any(not value["unchanged"] for key, value in record["git_preservation"].items() if key != "qualification")
    for field in ("owned_temporary_paths", "owned_device_directories", "original_generated_outputs", "cycle_scratch_directories"):
        bad |= any(item["status"] != "ABSENT" for item in record[field])
    bad |= any(item["status"] == "RECORDED_OWNER_STILL_PRESENT_NOT_TOUCHED" for item in record["owned_process_identities"])
    record["status"] = "FAIL_CURRENT_OBSERVATION" if bad else "PASS_CURRENT_BOUNDED_OBSERVATION"
    record["historical_cleanup_qualification"] = "Original failed receipts, especially apphost-02 UID attestation, are retained as failures; present absence does not retroactively repair historical attestation or establish unobserved ownership."


def main():
    if ROOT != Path("/Users/abdelrahman/Projects/parlor") or len(sys.argv) != 2 or not re.fullmatch(r"\d{2}", sys.argv[1]):
        raise SystemExit("Root only, lane free: /usr/bin/python3 -B reviews/observe_final_preservation.py NN")
    destination = RUN / f"reviews/independent-final-preservation-observation-{sys.argv[1]}.json"
    if destination.exists() or destination.is_symlink():
        raise SystemExit("Refusing to overwrite an observation")
    record = {"started_at": now(), "control_author": "/root/factory_review", "executor": "/root",
              "control": reference(Path(__file__).resolve()),
              "scope": "Bounded read-only source/preservation/owned-path/process observation, not execution of a build or verification suite.",
              "observation_qualification": "Root-executed mechanical observations; not a new independent semantic review or approval of application fixes.",
              "builds_or_background_workers_started": 0, "processes_signalled": [], "paths_deleted": [], "source_edits": []}
    try:
        with (RUN / "build-lane.lock").open("r") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            record["lane_lock"] = "Acquired existing lock nonblocking without writing lock bytes"
            observe(record)
    except (Exception, KeyboardInterrupt) as error:
        record["status"] = "BLOCKED_INCOMPLETE_OBSERVATION"
        record["error"] = {"type": type(error).__name__, "message": str(error)}
    record["finished_at"] = now()
    with destination.open("x") as output:
        json.dump(record, output, indent=2, ensure_ascii=False)
        output.write("\n")
    print(destination.relative_to(ROOT), record["status"], digest(destination))
    return 0 if record["status"] == "PASS_CURRENT_BOUNDED_OBSERVATION" else 1


if __name__ == "__main__":
    sys.exit(main())
