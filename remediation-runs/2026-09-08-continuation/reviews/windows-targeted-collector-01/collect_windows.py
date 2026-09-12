#!/usr/bin/env python3
"""Windows-only collection adaptation of the independently reviewed full-D v02.

Original v01/v02 remain unchanged. ZIP/path/CRC/hash/extraction controls and
byte/member bounds are unchanged. Exactly one non-skipped Windows job and four
skipped unrelated jobs are required; only Windows artifacts are selected.
The exact scope argument and API job shape bind the coordinator's intended
scope, not independently recovered dispatch inputs. Preserve the separately
reviewed request/association and raw logs for actual scope/runtime review.
Extracted reports remain candidates, NEVER proof that their tests ran.
"""
import collections
import hashlib
import json
import re
import stat
import subprocess
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath

MIB = 1024 * 1024
REPO = "Apdelrahman1911/parlor"
BRANCH = "fix/local-readiness-2026-09-07"
WORKSPACE_PREFIX = "parlor/parlor/"  # Actual retained full-D34152138368 layout.
JOB_NAMES = {
    "desktop-android": "Common, desktop, and Android release",
    "desktop-linux-arm64": "Desktop strict verification (Linux arm64)",
    "desktop-macos-x64": "Desktop and Kotlin Native strict verification (macOS x64)",
    "desktop-windows-x64": "Desktop, Kotlin Native, and Android resources strict verification (Windows x64)",
    "ios": "iOS tests, release frameworks, and Swift wrapper",
}
WINDOWS_JOB = "desktop-windows-x64"
EXPECTED_SCOPE = "windows-only"
ARTIFACT_JOBS = {
    "desktop-verification-" + WINDOWS_JOB: WINDOWS_JOB,
    "verification-cleanup-" + WINDOWS_JOB: WINDOWS_JOB,
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def now():
    return datetime.now(timezone.utc).isoformat()


def sha_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(MIB), b""):
            digest.update(block)
    return digest.hexdigest()


def new_json(path, value):
    with path.open("x", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2)
        stream.write("\n")


def live_build_roots(tracked):
    roots = {"build", "iosApp/build"}
    for name in tracked:
        path = PurePosixPath(name)
        if path.name in {"build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"}:
            roots.add(str(path.parent / "build"))
    require(not any(name == root or name.startswith(root + "/") for root in roots for name in tracked),
            "Frozen live output root overlaps tracked files")
    return sorted(roots)


def job_context(job):
    steps = job["steps"]
    names = [step["name"] for step in steps]
    require(len(names) == len(set(names)), "Duplicate API job step name")
    selected = {}
    for name in ("Check out source", "Claim fresh verification output ownership"):
        require(names.count(name) == 1, "Missing exact checkout/ownership step")
        selected[name] = next(step for step in steps if step["name"] == name)
    return dict(id=job["id"], name=job["name"], conclusion=job["conclusion"],
                steps=steps, live_path_extraction_admitted=all(
                    step["status"] == "completed" and step["conclusion"] == "success"
                    for step in selected.values()))


def windows_job_contexts(jobs):
    require(len(jobs) == len(JOB_NAMES) and {job["name"] for job in jobs} == set(JOB_NAMES.values()),
            "Exactly one Windows job and four known unrelated jobs required")
    contexts = {}
    for key, name in JOB_NAMES.items():
        job = next(job for job in jobs if job["name"] == name)
        if key == WINDOWS_JOB:
            require(isinstance(job["conclusion"], str) and job["conclusion"] and job["conclusion"] != "skipped",
                    "The Windows job must not be skipped")
            contexts[key] = job_context(job)
        else:
            require(job["conclusion"] == "skipped", "A non-Windows job was selected")
            steps = job.get("steps", [])
            require(isinstance(steps, list) and all(step["status"] == "completed" and step["conclusion"] == "skipped" for step in steps),
                    "A skipped unrelated job contains a non-skipped step")
            contexts[key] = dict(id=job["id"], name=name, conclusion="skipped",
                                 steps=steps, live_path_extraction_admitted=False)
    return contexts


def classify_member(name, artifact, run_id, job_id, context, tracked, roots):
    """Exact root/path classification, not a freshness or execution validator."""
    prefix = f"parlor-verification-{run_id}-1-{job_id}-"
    if artifact.startswith("verification-cleanup-"):
        return ("CURRENT_NAMED_RECEIPT_CONTENT_UNREVIEWED", True) if name == prefix + "cleanup.json" else ("UNKNOWN_ARCHIVE_ONLY", False)
    if re.fullmatch(re.escape("_temp/" + prefix) + r"(?:ownership|stop-[a-z0-9-]+|apple-(?:aggregate|ui|wrapper)-ownership|apple-ui-simulator-[a-z0-9-]+)\.json", name):
        return "CURRENT_NAMED_RECEIPT_CONTENT_UNREVIEWED", True
    if not name.startswith(WORKSPACE_PREFIX):
        return "UNKNOWN_ARCHIVE_ONLY", False
    relative = name[len(WORKSPACE_PREFIX):]
    if relative in tracked:
        return "FROZEN_TRACKED_FILE_ARCHIVE_ONLY", False
    if relative.split("/", 1)[0] in {"remediation-runs", "audit-runs", "handoffs"}:
        return "HISTORICAL_SCOPE_ARCHIVE_ONLY", False
    root = next((root for root in roots if relative.startswith(root + "/")), None)
    if root is None:
        return "UNKNOWN_ARCHIVE_ONLY", False
    if not context["live_path_extraction_admitted"]:
        return "UNATTESTED_LIVE_PATH_ARCHIVE_ONLY", False
    tail = relative[len(root) + 1:]
    report = (tail.startswith("test-results/") and tail.endswith(".xml") or
              tail.startswith(("reports/tests/", "reports/detekt/", "reports/androidTests/managedDevice/")) or
              tail.startswith("outputs/androidTest-results/managedDevice/") and tail.endswith(".xml") or
              tail.startswith("reports/lint-results-"))
    descriptor = (root == "build" and tail.startswith("ci-evidence/") and
                  len(PurePosixPath(tail).parts) == 2 and
                  PurePosixPath(tail).suffix in {".json", ".txt", ".log", ".xml", ".sha256", ".plist"})
    if report or descriptor:
        return "CURRENT_PATH_CANDIDATE_NOT_EXECUTION", True
    # dSYM, xcresult, AAB, mappings etc. retain their exact original ZIP bytes.
    return "LIVE_SCOPE_COMPRESSED_ONLY_NOT_EXECUTION", False


def archive_members(path, classifier=None, max_members=50000, max_total=256 * MIB, max_file=128 * MIB):
    """Preflight the WHOLE table, then stream every entry to EOF for CRC/SHA."""
    rows = []
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        require(len(infos) <= max_members, "Member count limit")
        require(sum(info.file_size for info in infos) <= max_total, "Expanded byte limit")
        kinds = {}
        for info in infos:
            name = info.filename
            path_name = PurePosixPath(name)
            require(name == info.orig_filename and name and path_name.parts and "\\" not in name and "\x00" not in name and
                    not path_name.is_absolute() and all(part not in {".", ".."} and ":" not in part for part in path_name.parts) and
                    str(path_name) == name.rstrip("/") and not name.endswith("//"), "Unsafe/noncanonical member path")
            key = str(path_name)
            require(key not in kinds, "Duplicate file/directory member path")
            require(not info.flag_bits & (1 | 64), "Encrypted member")
            kind = stat.S_IFMT(info.external_attr >> 16)
            require(kind in ({0, stat.S_IFDIR} if info.is_dir() else {0, stat.S_IFREG}), "Nonregular/mismatched member type")
            require(0 <= info.file_size <= max_file and info.compress_size >= 0, "File byte limit")
            require(not info.is_dir() or info.file_size == 0, "Nonempty directory")
            kinds[key] = info.is_dir()
        for name in kinds:
            require(all(str(parent) not in kinds or kinds[str(parent)] for parent in PurePosixPath(name).parents),
                    "File/directory ancestor collision")
        for info in infos:
            digest = hashlib.sha256()
            count = 0
            with archive.open(info) as stream:
                for block in iter(lambda: stream.read(MIB), b""):
                    count += len(block)
                    require(count <= info.file_size, "Stream exceeds declared byte size")
                    digest.update(block)
            require(count == info.file_size, "Stream size mismatch")
            category, selected = classifier(info.filename) if classifier and not info.is_dir() else ("DIRECTORY" if info.is_dir() else "LOG_ARCHIVE_ONLY", False)
            rows.append(dict(path=info.filename, bytes=count, compressed_bytes=info.compress_size,
                             sha256=digest.hexdigest(), crc32=f"{info.CRC:08x}",
                             external_attr=info.external_attr, flag_bits=info.flag_bits,
                             compression=info.compress_type, directory=info.is_dir(),
                             classification=category, selected_for_extraction=selected, extracted=False))
    return rows


def extract_selected(path, output, rows):
    """Called only after the entire archive passed CRC and bounds validation."""
    require(output.parent.resolve() == output.parent.absolute(), "Redirected extraction parent")
    output.mkdir()  # New output only, including for failed-job archives.
    with zipfile.ZipFile(path) as archive:
        for row in rows:
            if not row["selected_for_extraction"]:
                continue
            target = output.joinpath(*PurePosixPath(row["path"]).parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            require(target.parent.resolve() == target.parent.absolute(), "Redirected extraction path")
            digest = hashlib.sha256()
            count = 0
            with archive.open(row["path"]) as incoming, target.open("xb") as outgoing:
                for block in iter(lambda: incoming.read(MIB), b""):
                    count += len(block)
                    require(count <= row["bytes"], "Extraction exceeds verified byte size")
                    outgoing.write(block)
                    digest.update(block)
            require(count == row["bytes"] and digest.hexdigest() == row["sha256"], "Extraction changed verified member")
            row["extracted"] = True


def main(argv):
    require(len(argv) == 4 and re.fullmatch(r"[1-9][0-9]*", argv[1]) and re.fullmatch(r"[0-9a-f]{40}", argv[2]) and
            argv[3] == EXPECTED_SCOPE, "Usage: collect_windows.py RUN_ID SOURCE_SHA windows-only")
    run_id, source, attempt = int(argv[1]), argv[2], 1
    dest = Path("remediation-runs/2026-09-08-continuation/actions") / str(run_id)
    require(dest.parent.resolve() == dest.parent.absolute(), "Redirected collection parent")
    dest.mkdir()  # Preserve all previous successful/failed collection destinations.
    receipt = dict(schema_version=2, kind="BOUNDED_ACTIONS_ARCHIVE_COLLECTION_NOT_RUNTIME_REVIEW",
                   coordinator="/root", started_at=now(), run_id=run_id, run_attempt=attempt,
                   source_commit=source, collector_sha256=sha_file(Path(__file__)), commands=[], archives=[], status="RUNNING",
                   verification_scope=EXPECTED_SCOPE,
                   scope_binding="Coordinator intended scope plus exact API job shape; request/association and raw dispatch inputs require separate review.",
                   current_test_execution_counts="NOT_COMPUTED_REQUIRES_SEPARATE_RUNTIME_REVIEW",
                   extraction_policy="Exact frozen live roots, checkout+ownership step success; candidates only. Historical/unknown and opaque outputs remain in retained original ZIPs. Receipt contents require separate review.",
                   bounds=dict(artifact_compressed=64 * MIB, artifacts_compressed_total=512 * MIB,
                               members_per_artifact=50000, expanded_per_archive=256 * MIB, file_expanded=128 * MIB))

    def download(endpoint, name, limit):
        args = ["gh", "api", endpoint]
        path = dest / name
        row = dict(command=args, started_at=now(), stdout_file=name)
        receipt["commands"].append(row)
        try:
            with path.open("xb") as output:
                proc = subprocess.run(args, stdout=output, stderr=subprocess.PIPE, timeout=180)
            row.update(exit_code=proc.returncode, stderr=proc.stderr.decode("utf-8", "replace"))
            require(proc.returncode == 0 and path.stat().st_size <= limit, "Download failed/size limit: " + name)
        finally:
            row["finished_at"] = now()
            if path.is_file():
                row.update(bytes=path.stat().st_size, sha256=sha_file(path))
        return path

    def metadata(endpoint, name):
        return json.loads(download(endpoint, name, 4 * MIB).read_bytes())

    try:
        tracked_raw = subprocess.check_output(["git", "ls-tree", "-r", "--name-only", "-z", source], timeout=30)
        require(len(tracked_raw) <= 32 * MIB and tracked_raw.endswith(b"\0"), "Frozen tree path table limit/format")
        tracked = set(tracked_raw.decode("utf-8").rstrip("\0").split("\0"))
        roots = live_build_roots(tracked)
        tree = subprocess.check_output(["git", "rev-parse", source + "^{tree}"], text=True, timeout=30).strip()
        require(re.fullmatch(r"[0-9a-f]{40}", tree), "Frozen tree identity")
        with (dest / "frozen-tree-paths.z").open("xb") as stream:
            stream.write(tracked_raw)
        receipt["frozen_scope"] = dict(tree=tree, tracked_count=len(tracked), live_build_roots=roots,
                                      workspace_prefix=WORKSPACE_PREFIX, manifest="frozen-tree-paths.z",
                                      manifest_sha256=hashlib.sha256(tracked_raw).hexdigest())
        base = f"repos/{REPO}/actions"
        run = metadata(f"{base}/runs/{run_id}", "run.json")
        att = metadata(f"{base}/runs/{run_id}/attempts/{attempt}", "attempt.json")
        jobs = metadata(f"{base}/runs/{run_id}/attempts/{attempt}/jobs?per_page=100", "jobs.json")
        arts = metadata(f"{base}/runs/{run_id}/artifacts?per_page=100", "artifacts.json")
        for value in (run, att):
            require(value["id"] == run_id and value["run_attempt"] == attempt and value["head_sha"] == source and value["head_branch"] == BRANCH and
                    value["event"] == "workflow_dispatch" and value["status"] == "completed" and value["repository"]["full_name"] == REPO and
                    value["path"] == ".github/workflows/production-verification.yml", "Run/source/workflow binding")
        require(jobs["total_count"] == len(jobs["jobs"]) == len(JOB_NAMES), "Complete jobs page required")
        require({job["name"] for job in jobs["jobs"]} == set(JOB_NAMES.values()), "Exact job identities required")
        require(all(job["run_id"] == run_id and job["run_attempt"] == attempt and job["head_sha"] == source and job["status"] == "completed" for job in jobs["jobs"]), "Job/source/attempt binding")
        contexts = windows_job_contexts(jobs["jobs"])
        receipt.update(run_conclusion=run["conclusion"], run_url=run["html_url"], jobs=contexts)
        require(arts["total_count"] == len(arts["artifacts"]) <= 100, "Complete artifact page required")
        require(len({art["name"] for art in arts["artifacts"]}) == len(arts["artifacts"]), "Duplicate artifact names")
        receipt["missing_expected_artifacts"] = sorted(set(ARTIFACT_JOBS) - {art["name"] for art in arts["artifacts"]})
        receipt["unexpected_artifacts"] = [dict(id=art["id"], name=art["name"]) for art in arts["artifacts"] if art["name"] not in ARTIFACT_JOBS]
        selected = [art for art in arts["artifacts"] if art["name"] in ARTIFACT_JOBS]
        require(all(type(art["size_in_bytes"]) is int and 0 <= art["size_in_bytes"] <= 64 * MIB for art in selected) and
                sum(art["size_in_bytes"] for art in selected) <= 512 * MIB, "Compressed artifact size limit")
        for art in selected:
            require(not art["expired"] and art["workflow_run"]["id"] == run_id and art["workflow_run"]["head_sha"] == source, "Artifact/source binding")
            label = art["name"]
            job_id = ARTIFACT_JOBS[label]
            new_json(dest / f"artifact-{art['id']}.json", art)
            path = download(f"{base}/artifacts/{art['id']}/zip", label + ".zip", 64 * MIB)
            digest = sha_file(path)
            require(path.stat().st_size == art["size_in_bytes"] and art["digest"] == "sha256:" + digest, "Artifact API size/digest mismatch")
            rows = archive_members(path, lambda name: classify_member(name, label, run_id, job_id, contexts[job_id], tracked, roots))
            archive = dict(artifact_id=art["id"], artifact_name=label, job_id=job_id, archive=path.name,
                           bytes=path.stat().st_size, sha256=digest, api_digest_matches=True, members=rows,
                           crc_and_bounds_checked=True, classification_counts=dict(collections.Counter(row["classification"] for row in rows)),
                           original_zip_retained=True, extraction_complete=False)
            receipt["archives"].append(archive)
            extract_selected(path, dest / label, rows)
            archive["extraction_complete"] = True
        path = download(f"{base}/runs/{run_id}/attempts/{attempt}/logs", "run-attempt-1-logs.zip", 128 * MIB)
        rows = archive_members(path, max_members=1500, max_file=64 * MIB)
        receipt.update(log_archive=dict(path=path.name, sha256=sha_file(path), bytes=path.stat().st_size,
                                       members=rows, crc_and_bounds_checked=True, extracted=False, original_zip_retained=True),
                       status="PARTIAL_COLLECTION_NOT_RUNTIME_REVIEW" if receipt["missing_expected_artifacts"] or receipt["unexpected_artifacts"] else "COLLECTED_NOT_RUNTIME_REVIEW")
    except BaseException as error:
        receipt.update(status="COLLECTION_FAILED", error_type=type(error).__name__, error=str(error))
        raise
    finally:
        receipt["finished_at"] = now()
        new_json(dest / "download-receipt.json", receipt)
    print(json.dumps(dict(run_id=run_id, conclusion=receipt.get("run_conclusion"), status=receipt["status"],
                          receipt=str(dest / "download-receipt.json"), receipt_sha256=sha_file(dest / "download-receipt.json"))))


if __name__ == "__main__":
    main(sys.argv)
