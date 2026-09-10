#!/usr/bin/env python3
"""One-shot ADC custody collector; report paths are candidates, not execution proof.

Reuses the pinned full-D ZIP/path helpers without modification. Only the active
Android job gets a context; its ADC admission must pass before live reports are
extracted. Failed observations/cleanup and historical ZIP members are preserved.
"""
import collections
import hashlib
import json
import os
import pathlib
import re
import runpy
import subprocess
import sys

N = pathlib.Path("remediation-runs/2026-09-08-continuation")
HELPER = N / "reviews/full-d-compact-collector-02.py"
HELPER_SHA = "3af9603aff8e20aa1380f844897ed356426ba29c4dd5172cc5c60edb2dea10de"
if (HELPER.resolve() != HELPER.absolute() or not HELPER.is_file() or
        HELPER.stat().st_size != 17464 or hashlib.sha256(HELPER.read_bytes()).hexdigest() != HELPER_SHA):
    raise ValueError("Unapproved or redirected collection helper")
H = runpy.run_path(str(HELPER))
require, now, save, sha = (H[k] for k in ("require", "now", "new_json", "sha_file"))
MIB = 1024 * 1024
VALIDATION_STEP = "Validate Android cleanup source and controls"
ARTIFACTS = {"desktop-android-verification", "verification-cleanup-desktop-android"}


def main(argv):
    require(len(argv) == 3 and re.fullmatch(r"[1-9][0-9]{0,19}", argv[1]) and
            re.fullmatch(r"[0-9a-f]{40}", argv[2]), "Exact RUN_ID and SOURCE_SHA required")
    run_id, source = int(argv[1]), argv[2]
    base = f"repos/{H['REPO']}/actions"
    dest = N / "actions" / str(run_id)
    require(dest.parent.resolve() == dest.parent.absolute(), "Redirected collection parent")
    dest.mkdir()  # Never overwrite a previous successful or failed collection.
    receipt = dict(kind="COMPACT_ANDROID_CLEANUP_COLLECTION_NOT_RUNTIME_VERDICT", run_id=run_id,
                   run_attempt=1, source_sha=source, collector_sha256=sha(pathlib.Path(__file__)),
                   helper_sha256=HELPER_SHA, started_at=now(), commands=[], archives=[],
                   result="COLLECTION_FAIL", current_test_execution_counts="NOT_COMPUTED_REQUIRES_SEPARATE_REVIEW",
                   extraction_policy="Frozen live roots plus successful checkout/ownership/ADC admission; candidates only. "
                   "Current-named receipts remain unreviewed; historical/unknown/opaque members stay in original ZIPs.",
                   bounds=dict(artifact_compressed=64 * MIB, artifacts_compressed_total=128 * MIB,
                               members_per_artifact=50000, expanded_per_archive=256 * MIB,
                               file_expanded=128 * MIB, metadata=4 * MIB, raw_job_log=64 * MIB))

    def download(endpoint, name, limit=4 * MIB):
        path = dest / name
        row = dict(endpoint=endpoint, path=name, started_at=now())
        receipt["commands"].append(row)
        try:
            with path.open("xb") as output:
                result = subprocess.run(["gh", "api", endpoint], stdout=output,
                                        stderr=subprocess.PIPE, timeout=180)
            row.update(exit_code=result.returncode, stderr=result.stderr.decode("utf-8", "replace"))
            require(result.returncode == 0 and path.stat().st_size <= limit, "Download failure or byte limit")
        except BaseException as error:
            row["error_type"] = type(error).__name__
            raise
        finally:
            row["finished_at"] = now()
            if path.is_file():
                row.update(bytes=path.stat().st_size, sha256=sha(path))
        return path

    def metadata(endpoint, name):
        return json.loads(download(endpoint, name).read_bytes())

    try:
        git_env = dict(os.environ, GIT_OPTIONAL_LOCKS="0")
        tracked_raw = subprocess.check_output(["git", "ls-tree", "-r", "--name-only", "-z", source],
                                              env=git_env, timeout=30)
        require(len(tracked_raw) <= 32 * MIB and tracked_raw.endswith(b"\0"), "Frozen path table limit/format")
        tracked = set(tracked_raw.decode("utf-8").rstrip("\0").split("\0"))
        roots = H["live_build_roots"](tracked)
        tree = subprocess.check_output(["git", "rev-parse", source + "^{tree}"],
                                       env=git_env, text=True, timeout=30).strip()
        require(re.fullmatch(r"[0-9a-f]{40}", tree), "Frozen tree identity")
        with (dest / "frozen-tree-paths.z").open("xb") as output:
            output.write(tracked_raw)
        receipt["frozen_scope"] = dict(tree=tree, tracked_count=len(tracked), live_build_roots=roots,
                                       workspace_prefix=H["WORKSPACE_PREFIX"], manifest="frozen-tree-paths.z",
                                       manifest_sha256=hashlib.sha256(tracked_raw).hexdigest())
        run = metadata(f"{base}/runs/{run_id}", "run.json")
        attempt = metadata(f"{base}/runs/{run_id}/attempts/1", "attempt.json")
        jobs = metadata(f"{base}/runs/{run_id}/attempts/1/jobs?per_page=100", "jobs.json")
        artifacts = metadata(f"{base}/runs/{run_id}/artifacts?per_page=100", "artifacts.json")
        for value in (run, attempt):
            require(value["id"] == run_id and value["run_attempt"] == 1 and value["head_sha"] == source and
                    value["head_branch"] == H["BRANCH"] and value["repository"]["full_name"] == H["REPO"] and
                    value["event"] == "workflow_dispatch" and value["status"] == "completed" and
                    value["path"] == ".github/workflows/production-verification.yml", "Run binding mismatch")
        require(jobs["total_count"] == len(jobs["jobs"]) == 5 and
                {job["name"] for job in jobs["jobs"]} == set(H["JOB_NAMES"].values()), "Job set mismatch")
        for job in jobs["jobs"]:
            require(type(job["id"]) is int and job["id"] > 0 and job["run_id"] == run_id and
                    job["run_attempt"] == 1 and job["head_sha"] == source and
                    job["status"] == "completed", "Job binding mismatch")
            if job["name"] != H["JOB_NAMES"]["desktop-android"]:
                require(job["conclusion"] == "skipped", "Unrelated job executed")
        job = next(job for job in jobs["jobs"] if job["name"] == H["JOB_NAMES"]["desktop-android"])
        context = H["job_context"](job)  # Never call this on skipped jobs with no steps.
        require(any(step["name"] == VALIDATION_STEP for step in job["steps"]), "Missing ADC admission step")
        validation = next(step for step in job["steps"] if step["name"] == VALIDATION_STEP)
        context["android_cleanup_validation"] = validation
        context["live_path_extraction_admitted"] = (context["live_path_extraction_admitted"] and
                validation["status"] == "completed" and validation["conclusion"] == "success")
        receipt.update(run_conclusion=run["conclusion"], run_url=run["html_url"],
                       jobs={"desktop-android": context}, skipped_job_ids=[j["id"] for j in jobs["jobs"] if j != job])
        log = download(f"{base}/jobs/{job['id']}/logs", "desktop-android.log", 64 * MIB)
        receipt["raw_job_log"] = dict(path=log.name, bytes=log.stat().st_size, sha256=sha(log))
        require(artifacts["total_count"] == len(artifacts["artifacts"]) == 2 and
                {art["name"] for art in artifacts["artifacts"]} == ARTIFACTS, "Artifact set mismatch")
        require(all(type(art["size_in_bytes"]) is int and 0 < art["size_in_bytes"] <= 64 * MIB
                    for art in artifacts["artifacts"]) and
                sum(art["size_in_bytes"] for art in artifacts["artifacts"]) <= 128 * MIB, "Artifact byte limit")
        for art in artifacts["artifacts"]:
            require(type(art["id"]) is int and art["id"] > 0 and art["expired"] is False and
                    art["workflow_run"]["id"] == run_id and art["workflow_run"]["head_sha"] == source and
                    art["workflow_run"]["head_branch"] == H["BRANCH"], "Artifact binding mismatch")
            label = art["name"]
            save(dest / f"artifact-{art['id']}.json", art)
            path = download(f"{base}/artifacts/{art['id']}/zip", label + ".zip", 64 * MIB)
            digest = sha(path)
            require(path.stat().st_size == art["size_in_bytes"] and art["digest"] == "sha256:" + digest,
                    "Artifact API size/digest mismatch")
            rows = H["archive_members"](path, lambda name: H["classify_member"](
                name, label, run_id, "desktop-android", context, tracked, roots),
                max_members=50000, max_total=256 * MIB, max_file=128 * MIB)
            archive = dict(artifact_id=art["id"], artifact_name=label, archive=path.name,
                           bytes=path.stat().st_size, sha256=digest, api_digest_matches=True,
                           members=rows, crc_and_bounds_checked=True, original_zip_retained=True,
                           classification_counts=dict(collections.Counter(row["classification"] for row in rows)),
                           extraction_complete=False)
            receipt["archives"].append(archive)
            H["extract_selected"](path, dest / label, rows)
            archive["extraction_complete"] = True
        receipt["result"] = "COLLECTED_NOT_RUNTIME_VERDICT"
    except BaseException as error:
        receipt.update(result="COLLECTION_FAIL", error_type=type(error).__name__, error=str(error))
        raise
    finally:
        try:
            stopped = subprocess.run(["./gradlew", "--stop"], text=True, stdout=subprocess.PIPE,
                                     stderr=subprocess.STDOUT, timeout=90)
            stop = dict(exit_code=stopped.returncode, output=stopped.stdout)
        except (OSError, subprocess.TimeoutExpired) as error:
            stop = dict(exit_code=1, error_type=type(error).__name__)
        receipt.update(stop=stop, finished_at=now())
        save(dest / "download-receipt.json", receipt)
        print(json.dumps({key: receipt[key] for key in ("result", "run_id", "stop")}))
    require(receipt["stop"]["exit_code"] == 0, "Local Gradle stop failed")


if __name__ == "__main__":
    main(sys.argv)
