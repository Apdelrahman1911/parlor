#!/usr/bin/env python3
"""One-shot compact receipt collector; reuses the reviewed ZIP validator unchanged."""
import hashlib
import json
import pathlib
import re
import runpy
import subprocess
import sys

N = pathlib.Path("remediation-runs/2026-09-08-continuation")
HELPER = N / "reviews/full-d-compact-collector-02.py"
assert hashlib.sha256(HELPER.read_bytes()).hexdigest() == "3af9603aff8e20aa1380f844897ed356426ba29c4dd5172cc5c60edb2dea10de"
H = runpy.run_path(str(HELPER))
require, now, save, sha = (H[k] for k in ("require", "now", "new_json", "sha_file"))
require(len(sys.argv) == 3 and re.fullmatch(r"[1-9][0-9]*", sys.argv[1]) and
        re.fullmatch(r"[0-9a-f]{40}", sys.argv[2]), "Exact run and source required")
RUN, SOURCE = int(sys.argv[1]), sys.argv[2]
BASE = "repos/Apdelrahman1911/parlor/actions"
DEST = N / "actions" / str(RUN)
require(DEST.parent.resolve() == DEST.parent.absolute(), "Redirected collection parent")
DEST.mkdir()
receipt = dict(kind="COMPACT_LINUX_PROBE_COLLECTION_NOT_RUNTIME_VERDICT", run_id=RUN,
               source_sha=SOURCE, collector_sha256=sha(pathlib.Path(__file__)), started_at=now(),
               commands=[], archives=[], result="COLLECTION_FAIL")


def download(endpoint, name, limit=4 * 1024 * 1024):
    target = DEST / name
    with target.open("xb") as stream:
        result = subprocess.run(["gh", "api", endpoint], stdout=stream, stderr=subprocess.PIPE, timeout=120)
    receipt["commands"].append(dict(endpoint=endpoint, path=name, exit_code=result.returncode,
                                    bytes=target.stat().st_size, sha256=sha(target),
                                    stderr=result.stderr.decode("utf-8", "replace")))
    require(result.returncode == 0 and target.stat().st_size <= limit, "Download failure or limit")
    return target


try:
    run = json.loads(download(f"{BASE}/runs/{RUN}", "run.json").read_bytes())
    attempt = json.loads(download(f"{BASE}/runs/{RUN}/attempts/1", "attempt.json").read_bytes())
    jobs = json.loads(download(f"{BASE}/runs/{RUN}/attempts/1/jobs?per_page=100", "jobs.json").read_bytes())
    artifacts = json.loads(download(f"{BASE}/runs/{RUN}/artifacts?per_page=100", "artifacts.json").read_bytes())
    for value in (run, attempt):
        require(value["id"] == RUN and value["run_attempt"] == 1 and value["head_sha"] == SOURCE and
                value["head_branch"] == H["BRANCH"] and value["repository"]["full_name"] == H["REPO"] and
                value["event"] == "workflow_dispatch" and value["status"] == "completed" and
                value["path"] == ".github/workflows/production-verification.yml", "Run binding mismatch")
    require(jobs["total_count"] == len(jobs["jobs"]) == 5 and
            {j["name"] for j in jobs["jobs"]} == set(H["JOB_NAMES"].values()), "Job set mismatch")
    for job in jobs["jobs"]:
        require(job["run_id"] == RUN and job["run_attempt"] == 1 and job["head_sha"] == SOURCE and
                job["status"] == "completed", "Job binding mismatch")
        if job["name"] != H["JOB_NAMES"]["desktop-android"]:
            require(job["conclusion"] == "skipped", "Unrelated job executed")
    job = next(j for j in jobs["jobs"] if j["name"] == H["JOB_NAMES"]["desktop-android"])
    prefix = f"parlor-verification-{RUN}-1-desktop-android-"
    expected = {f"linux-process-probe-{RUN}-1": {prefix + s + ".json" for s in
                ("ownership", "linux-probe", "stop-linux-probe")},
                f"linux-process-probe-cleanup-{RUN}-1": {prefix + "cleanup.json"}}
    require(artifacts["total_count"] == len(artifacts["artifacts"]) == 2 and
            {a["name"] for a in artifacts["artifacts"]} == set(expected), "Artifact set mismatch")
    for artifact in artifacts["artifacts"]:
        require(not artifact["expired"] and artifact["workflow_run"]["id"] == RUN and
                artifact["workflow_run"]["head_sha"] == SOURCE and
                0 < artifact["size_in_bytes"] <= 4 * 1024 * 1024, "Artifact binding or size mismatch")
        label = artifact["name"]
        path = download(f"{BASE}/artifacts/{artifact['id']}/zip", label + ".zip")
        require(path.stat().st_size == artifact["size_in_bytes"] and
                artifact["digest"] == "sha256:" + sha(path), "Artifact API digest mismatch")
        rows = H["archive_members"](path, lambda name: ("CURRENT_NAMED_RECEIPT_UNREVIEWED", name in expected[label]),
                                    max_members=20, max_total=4 * 1024 * 1024, max_file=1024 * 1024)
        require({r["path"] for r in rows if not r["directory"]} == expected[label], "Receipt member set mismatch")
        H["extract_selected"](path, DEST / label, rows)
        receipt["archives"].append(dict(id=artifact["id"], name=label, sha256=sha(path), members=rows,
                                         original_zip_retained=True, api_digest_matches=True))
    download(f"{BASE}/jobs/{job['id']}/logs", "desktop-android.log")
    receipt.update(result="COLLECTED_NOT_RUNTIME_VERDICT", run_conclusion=run["conclusion"])
finally:
    try:
        stopped = subprocess.run(["./gradlew", "--stop"], text=True, stdout=subprocess.PIPE,
                                 stderr=subprocess.STDOUT, timeout=90)
        stop = dict(exit_code=stopped.returncode, output=stopped.stdout)
    except (OSError, subprocess.TimeoutExpired) as error:
        stop = dict(exit_code=1, error_type=type(error).__name__)
    receipt.update(stop=stop, finished_at=now())
    save(DEST / "download-receipt.json", receipt)
    print(json.dumps({k: receipt[k] for k in ("result", "run_id", "stop")}))
require(receipt["stop"]["exit_code"] == 0, "Local Gradle stop failed")
