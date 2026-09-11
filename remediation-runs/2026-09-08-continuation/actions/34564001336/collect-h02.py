#!/usr/bin/env python3
"""One H02 custody collection. Imports reviewed ZIP helpers, never full-D main.

No build, dispatch, control edits, resource cleanup or qualification assertion.
All destination files are new. A failed/partial collection is retained.
"""
import hashlib
import importlib.util
import json
import os
import re
import selectors
import signal
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path

DEST = Path(__file__).absolute().parent
ROOT = DEST.parents[3]
N = ROOT / "remediation-runs/2026-09-08-continuation"
PACKET = N / "actions/protection-host-image-02"
HELPER = N / "reviews/full-d-compact-collector-03.py"
HELPER_SHA = "53d6a05f4a2c591f25e3bda2104b0c15e39cd472d6519d24e6fd1aff5234f7a8"
REPO = "Apdelrahman1911/parlor"
BRANCH = "fix/local-readiness-2026-09-07"
SOURCE = "22ea6bebb95317e148d89b03c40cbb7efc9d36a4"
TREE = "e1a94305eb7af228bb2a80841f6adb3bc00162aa"
CONTROL = "91e18dec83251b4e040fc9f706d652a660bdb8d46414ef08321589e8c575d5b0"
RUN, ATTEMPT = 34564001336, 1
PROFILE = "host-macos15-arm64-xcode26.3-sdk26.2-attribution-and-host-origin-setter"
WORKFLOW = ".github/workflows/production-verification.yml"
MIB = 1024 * 1024
EXPECTED_ARTIFACTS = {
    f"ios-protection-probe-{RUN}-{ATTEMPT}": "main",
    f"ios-protection-probe-cleanup-{RUN}-{ATTEMPT}": "cleanup",
}


def require(value, message):
    if not value:
        raise ValueError(message)


def now():
    return datetime.now(timezone.utc).isoformat()


def sha(path):
    with path.open("rb") as f:
        h = hashlib.sha256()
        for chunk in iter(lambda: f.read(MIB), b""):
            h.update(chunk)
        return h.hexdigest()


def new_json(path, value):
    with path.open("x", encoding="utf-8") as f:
        json.dump(value, f, indent=2)
        f.write("\n")


def read_json(path):
    def unique(pairs):
        out = {}
        for key, value in pairs:
            require(key not in out, "Duplicate JSON key")
            out[key] = value
        return out
    return json.loads(path.read_bytes(), object_pairs_hook=unique)


receipt = dict(
    kind="FOCUSED_BOUNDED_HOST_ONLY_COLLECTION_NOT_RUNTIME_REVIEW",
    collector="/root/probe_collector", started_at=now(), status="RUNNING",
    run_id=RUN, run_attempt=ATTEMPT, source=SOURCE, tree=TREE,
    profile=PROFILE, selection="protection-host-only", control_sha256=CONTROL,
    collector_file=Path(__file__).name, collector_sha256=sha(Path(__file__)),
    helper_path=str(HELPER.relative_to(ROOT)), helper_sha256=sha(HELPER),
    helper_functions=["archive_members", "extract_selected"], helper_main_invoked=False,
    bounds=dict(metadata_bytes=4*MIB, artifact_compressed_bytes=16*MIB,
                artifacts_compressed_total_bytes=32*MIB, members_per_archive=1500,
                expanded_per_archive_bytes=64*MIB, expanded_file_bytes=8*MIB,
                job_log_bytes=16*MIB, download_timeout_seconds=180,
                stderr_bytes_per_command=256*1024),
    downloads_bounded_while_streaming=True, commands=[], archives=[],
    limits="Custody collection only; API success and report CAPTURED/PASS labels are not strict protection, runtime, cleanup or full qualification acceptance. No evidence stitching.",
)


def download(endpoint, name, limit):
    args = ["gh", "api", endpoint]
    path, errpath = DEST/name, DEST/(name + ".stderr")
    row = dict(command=args, started_at=now(), stdout_file=name,
               stderr_file=errpath.name, byte_limit=limit, timeout_seconds=180)
    receipt["commands"].append(row)
    proc = None
    try:
        with path.open("xb") as out, errpath.open("xb") as err, selectors.DefaultSelector() as selector:
            proc = subprocess.Popen(args, stdin=subprocess.DEVNULL,
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                    start_new_session=True, cwd=ROOT)
            row["owned_pid"] = proc.pid
            selector.register(proc.stdout, selectors.EVENT_READ, (out, limit, "stdout"))
            selector.register(proc.stderr, selectors.EVENT_READ, (err, 256*1024, "stderr"))
            counts = {"stdout": 0, "stderr": 0}
            deadline = time.monotonic() + 180
            while selector.get_map():
                remaining = deadline - time.monotonic()
                require(remaining > 0, "Download timeout: " + name)
                for key, _ in selector.select(min(remaining, 0.5)):
                    chunk = os.read(key.fd, 65536)
                    if not chunk:
                        selector.unregister(key.fileobj)
                        key.fileobj.close()
                        continue
                    stream, maximum, channel = key.data
                    allowed = maximum - counts[channel]
                    stream.write(chunk[:allowed])
                    counts[channel] += min(len(chunk), allowed)
                    require(len(chunk) <= allowed, "Streaming byte bound: " + name + " " + channel)
            remaining = deadline - time.monotonic()
            require(remaining > 0, "Download wait timeout: " + name)
            row["exit_code"] = proc.wait(timeout=remaining)
            row["direct_child_reaped"] = True
            require(row["exit_code"] == 0, "Download failed: " + name)
    except BaseException as error:
        row.update(error_type=type(error).__name__, error=str(error))
        raise
    finally:
        if proc is not None:
            if proc.poll() is None:
                os.killpg(proc.pid, signal.SIGKILL)
            row["exit_code"] = proc.wait(timeout=10)
            row["direct_child_reaped"] = True
            for stream in (proc.stdout, proc.stderr):
                if stream is not None:
                    stream.close()
        row["finished_at"] = now()
        for p, field in ((path, "stdout"), (errpath, "stderr")):
            if p.is_file():
                row[field + "_bytes"] = p.stat().st_size
                row[field + "_sha256"] = sha(p)
    return path


def metadata(endpoint, name):
    return read_json(download(endpoint, name, 4*MIB))


def bound_source(value):
    require(value["repository"] == REPO and value["branch"] == BRANCH and
            value["workflow"] == WORKFLOW and value["job"] == "ios-protection-probe" and
            value["source_sha"] == SOURCE and value["tree"] == TREE and
            type(value["run_id"]) is int and value["run_id"] == RUN and
            type(value["run_attempt"]) is int and value["run_attempt"] == ATTEMPT,
            "Archived host source context mismatch")


try:
    require(DEST.resolve() == DEST and not any(p.is_symlink() for p in DEST.parents), "Redirected destination")
    require(receipt["helper_sha256"] == HELPER_SHA, "Reviewed helper changed")
    spec = importlib.util.spec_from_file_location("h02_reviewed_archive_helpers", HELPER)
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)

    request = read_json(PACKET/"request.json")
    controls = read_json(PACKET/"controls.json")
    freeze = read_json(PACKET/"freeze.json")
    dispatch = read_json(PACKET/"dispatch-receipt.json")
    expected_request = dict(ref=BRANCH, inputs=dict(verification_scope="ios-protection-probe",
        native_selection="protection-host-only", frozen_source_sha=SOURCE,
        approved_probe_control_sha256=CONTROL))
    require(request == expected_request, "Exact dispatched H02 request mismatch")
    require(freeze["source"] == SOURCE and freeze["tree"] == TREE and
            freeze["controls_sha256"] == CONTROL and freeze["request_sha256"] == sha(PACKET/"request.json"),
            "Freeze binding mismatch")
    require(dispatch["source"] == SOURCE and dispatch["tree"] == TREE and
            dispatch["run_id"] == RUN and dispatch["run_attempt"] == ATTEMPT and
            dispatch["request_sha256"] == sha(PACKET/"request.json") and dispatch["dispatch_exit_code"] == 0,
            "Dispatch binding mismatch")
    require(controls["control_sha256"] == CONTROL and len(controls["files"]) == 30 and
            hashlib.sha256(json.dumps(controls["files"], separators=(",", ":")).encode()).hexdigest() == CONTROL,
            "Thirty-control inventory digest mismatch")
    receipt["packet"] = {p: dict(path=str((PACKET/p).relative_to(ROOT)), sha256=sha(PACKET/p))
                         for p in ("request.json", "controls.json", "freeze.json", "dispatch-receipt.json", "dispatched-run.json")}
    tree = subprocess.check_output(["git", "rev-parse", SOURCE+"^{tree}"], cwd=ROOT, timeout=30, text=True).strip()
    require(tree == TREE, "Local frozen tree mismatch")
    receipt["frozen_tree_verified"] = tree

    base = f"repos/{REPO}/actions"
    run = metadata(f"{base}/runs/{RUN}", "run.json")
    attempt = metadata(f"{base}/runs/{RUN}/attempts/{ATTEMPT}", "attempt.json")
    jobs = metadata(f"{base}/runs/{RUN}/attempts/{ATTEMPT}/jobs?per_page=100", "jobs.json")
    arts = metadata(f"{base}/runs/{RUN}/artifacts?per_page=100", "artifacts.json")
    for value in (run, attempt):
        require(value["id"] == RUN and value["run_attempt"] == ATTEMPT and
                value["head_sha"] == SOURCE and value["head_branch"] == BRANCH and
                value["event"] == "workflow_dispatch" and value["status"] == "completed" and
                value["conclusion"] == "success" and value["repository"]["full_name"] == REPO and
                value["path"] == WORKFLOW and value["head_commit"]["tree_id"] == TREE,
                "Terminal run/attempt/source/workflow/tree binding mismatch")
    receipt.update(run_conclusion=run["conclusion"], run_url=run["html_url"],
                   api_success_only_not_strict_pass=True)
    expected_jobs = set(helper.JOB_NAMES.values()) | {"iOS strict protection diagnostic"}
    require(jobs["total_count"] == len(jobs["jobs"]) == 7 and
            {job["name"] for job in jobs["jobs"]} == expected_jobs and
            len({job["id"] for job in jobs["jobs"]}) == 7, "Seven exact unique jobs required")
    for job in jobs["jobs"]:
        require(job["run_id"] == RUN and job["run_attempt"] == ATTEMPT and job["head_sha"] == SOURCE and
                job["status"] == "completed", "Job run/attempt/source binding")
        if job["name"] != "iOS strict protection diagnostic":
            require(job["conclusion"] == "skipped" and job["steps"] == [], "Six full jobs must be skipped")
    probe = next(job for job in jobs["jobs"] if job["name"] == "iOS strict protection diagnostic")
    require(probe["conclusion"] == "success", "Probe job API conclusion")
    names = [s["name"] for s in probe["steps"]]
    require(len(set(names)) == len(names), "Duplicate probe step names")
    mandatory = ["Check out source", "Run independently approved strict protection diagnostic",
                 "Upload strict protection diagnostic evidence",
                 "Finalize strict protection diagnostic resources and custody",
                 "Upload strict protection diagnostic cleanup",
                 "Assert diagnostic collection and cleanup outcomes"]
    for name in mandatory:
        require(names.count(name) == 1 and all(s["status"] == "completed" and s["conclusion"] == "success"
                for s in probe["steps"] if s["name"] == name), "Missing/non-success probe API step: " + name)
    receipt["jobs"] = [dict(id=j["id"], name=j["name"], status=j["status"], conclusion=j["conclusion"])
                       for j in jobs["jobs"]]
    receipt["probe_job_id"] = probe["id"]
    new_json(DEST/"job.json", probe)
    require(arts["total_count"] == len(arts["artifacts"]) == 2 and
            {a["name"] for a in arts["artifacts"]} == set(EXPECTED_ARTIFACTS) and
            len({a["id"] for a in arts["artifacts"]}) == 2, "Exactly two expected distinct artifacts required")
    for art in arts["artifacts"]:
        require(type(art["size_in_bytes"]) is int and 0 < art["size_in_bytes"] <= 16*MIB and
                art["expired"] is False and art["workflow_run"]["id"] == RUN and
                art["workflow_run"]["head_sha"] == SOURCE and art["workflow_run"]["head_branch"] == BRANCH and
                re.fullmatch(r"sha256:[0-9a-f]{64}", art["digest"]), "Artifact source/bounds/digest binding")
    for art in sorted(arts["artifacts"], key=lambda a: EXPECTED_ARTIFACTS[a["name"]] != "main"):
        label = EXPECTED_ARTIFACTS[art["name"]]
        new_json(DEST/f"artifact-{art['id']}.json", art)
        path = download(f"{base}/artifacts/{art['id']}/zip", label+".zip", 16*MIB)
        digest = sha(path)
        require(path.stat().st_size == art["size_in_bytes"] and art["digest"] == "sha256:"+digest,
                "API ZIP size/digest mismatch")
        rows = helper.archive_members(path, classifier=lambda name: ("HOST_ONLY_RAW_CANDIDATE_NOT_QUALIFICATION", True),
                                      max_members=1500, max_total=64*MIB, max_file=8*MIB)
        receipt["archives"].append(dict(artifact_id=art["id"], artifact_name=art["name"],
            label=label, archive=path.name, bytes=path.stat().st_size, sha256=digest,
            api_digest_matches=True, members=len(rows), regular_members=sum(not r["directory"] for r in rows),
            expanded_bytes=sum(r["bytes"] for r in rows), whole_table_paths_types_sizes_validated=True,
            all_members_crc_and_sha256_verified=True, original_zip_retained=True,
            member_inventory=label+"-members.json", extraction_complete=False))
        # Preserve the fully validated inventory before any extraction starts.
        new_json(DEST/(label+"-members-pre-extraction.json"), rows)
    # Both entire archives are validated before extracting every regular member.
    for archive in receipt["archives"]:
        label = archive["label"]
        rows = read_json(DEST/(label+"-members-pre-extraction.json"))
        helper.extract_selected(DEST/archive["archive"], DEST/label, rows)
        new_json(DEST/archive["member_inventory"], rows)
        archive.update(extraction_complete=True, extracted_regular_members=sum(r["extracted"] for r in rows),
                       member_inventory_sha256=sha(DEST/archive["member_inventory"]))
    path = download(f"{base}/jobs/{probe['id']}/logs", "job.log", 16*MIB)
    receipt["job_log"] = dict(path=path.name, bytes=path.stat().st_size, sha256=sha(path), job_id=probe["id"])

    archived_request = read_json(DEST/"main/request.json")
    require(archived_request["schema"] == 1 and archived_request["selection"] == "protection-host-only" and
            archived_request["profile"] == PROFILE and archived_request["controls"] == controls,
            "Archived host profile/control context mismatch")
    bound_source(archived_request["source"])
    binding = archived_request["binding"]
    require(set(binding) == {"run_token", "source_sha", "control_sha256", "run_id", "run_attempt"} and
            re.fullmatch(r"[0-9a-f]{32}", binding["run_token"]) and binding == dict(
                run_token=binding["run_token"], source_sha=SOURCE, control_sha256=CONTROL,
                run_id=str(RUN), run_attempt=str(ATTEMPT)), "Exact five-field host native binding mismatch")
    checked = []
    for name in ("host-image.stdout.json", "host-image-summary.json", "host-setter.stdout.json", "host-setter-bindings.json"):
        value = read_json(DEST/"main"/name)
        require(value["binding"] == binding, "Host native report binding mismatch: " + name)
        checked.append(name)
    state = read_json(DEST/"main/state.json")
    cleanup = read_json(DEST/"cleanup/state.json")
    for value in (state, cleanup):
        require(value["profile"] == PROFILE and value["selection"] == "protection-host-only", "Host state scope mismatch")
    log = path.read_text(encoding="utf-8", errors="strict")
    for text in ("PARLOR_PROTECTION_SELECTION: protection-host-only", "PARLOR_FROZEN_SOURCE_SHA: "+SOURCE,
                 "PARLOR_APPROVED_PROBE_CONTROL_SHA256: "+CONTROL):
        require(text in log, "Missing host-only source/control job-log context")
    receipt.update(status="COLLECTED_NOT_RUNTIME_REVIEW", native_binding=binding,
        report_bindings_checked=checked, raw_reported_main_status=state["status"],
        raw_reported_cleanup_status=cleanup["status"],
        profile_binding_verified=True, missing_expected_artifacts=[], unexpected_artifacts=[])
except BaseException as error:
    receipt.update(status="COLLECTION_FAILED", error_type=type(error).__name__, error=str(error))
    raise
finally:
    receipt["finished_at"] = now()
    new_json(DEST/"download-receipt.json", receipt)
    print(json.dumps(dict(run_id=RUN, status=receipt["status"],
        receipt=str(DEST/"download-receipt.json"), receipt_sha256=sha(DEST/"download-receipt.json"))))
