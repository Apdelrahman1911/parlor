#!/usr/bin/env python3
"""Read-only source/receipt reconciliation; write NEW remediation deliverables only."""
import collections
import datetime
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
RUN = Path(__file__).resolve().parent
FINAL = RUN / "final"
FROZEN = "e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7"
RUNNER_SHA = "d2d18c2311d59554db88be3f92e609ec5ca46fc8af4c79702836f9e4c130240f"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path):
    return json.loads(path.read_text())


def reference(path):
    path = Path(path)
    if not path.is_absolute():
        path = ROOT / path
    return {"path": str(path.relative_to(ROOT)), "sha256": digest(path)}


def write(name, data):
    with (FINAL / name).open("x") as output:
        json.dump(data, output, indent=2, ensure_ascii=False)
        output.write("\n")


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def main():
    runner = RUN / "run_gradle_cycle.py"
    if digest(runner) != RUNNER_SHA:
        raise RuntimeError("Build-lane control changed; review before using its identity function")
    spec = importlib.util.spec_from_file_location("audited_build_lane", runner)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    identity = module.identity()
    if identity["source_manifest_sha256"] != FROZEN:
        raise RuntimeError("Application source drifted from final executed/reviewed tree")
    FINAL.mkdir(exist_ok=False)
    timestamp = datetime.datetime.now(datetime.timezone.utc).isoformat()
    baseline = load(RUN / "baseline.json")
    previous = {item["path"]: item for item in baseline["files"]}
    current = dict(identity["source_manifest"])
    changes, missing, unchanged = [], [], 0
    for name, old in previous.items():
        path = ROOT / name
        if not path.is_file() or path.is_symlink():
            missing.append(name)
        elif digest(path) != old["sha256"]:
            changes.append({"path": name, "original_kind": old["kind"],
                            "before_sha256": old["sha256"], "after_sha256": digest(path)})
        else:
            unchanged += 1
    new_sources = sorted(set(current) - set(previous))
    preservation = {
        "recorded_at": timestamp, "baseline": reference(RUN / "baseline.json"),
        "baseline_files": len(previous), "unchanged_original_files": unchanged,
        "modified_original_files": changes, "missing_original_files": missing,
        "preexisting_untracked_changed": [x for x in changes if x["original_kind"] != "tracked"],
        "new_source_files": new_sources,
        "head_unchanged": identity["commit"] == baseline["commit"],
        "branch_unchanged": identity["branch"] == baseline["branch"],
        "refs_unchanged": git("show-ref").decode().strip() == baseline["refs"].strip(),
        "stashes_unchanged": git("stash", "list").decode().strip() == baseline["stashes"].strip(),
        "index_is_empty": not git("diff", "--cached", "--name-only").strip(),
        "source_manifest_sha256": FROZEN,
        "note": "Uncommitted authorized remediation, NOT a clean immutable release candidate. "
                "Private ignored material was not opened. Only originally inventoried first-party files "
                "and current non-private source hashes are compared. Process/output finalization belongs "
                "to the enclosing cycle receipt and final post-cycle observation.",
    }
    if (missing or preservation["preexisting_untracked_changed"] or
            not all(preservation[k] for k in ("head_unchanged", "branch_unchanged",
                                              "refs_unchanged", "stashes_unchanged", "index_is_empty"))):
        raise RuntimeError("Preservation comparison failed; do not publish a success report")
    write("preservation.json", preservation)
    write("source-identity.json", {"recorded_at": timestamp, **identity})

    patch = git("diff", "--binary", "HEAD", "--")
    if hashlib.sha256(patch).hexdigest() != identity["diff_sha256"]:
        raise RuntimeError("Tracked diff changed during capture")
    for name in new_sources:
        result = subprocess.run(["git", "diff", "--no-index", "--binary", "--", "/dev/null", name],
                                cwd=ROOT, capture_output=True)
        if result.returncode != 1 or result.stderr:
            raise RuntimeError("Could not capture a new first-party source file")
        patch += result.stdout
    (FINAL / "remediation.patch").write_bytes(patch)
    patch_ref = reference(FINAL / "remediation.patch")
    changed_files = []
    for name in sorted({x["path"] for x in changes} | set(new_sources)):
        path = ROOT / name
        added = name in new_sources
        if added:
            ranges = [[1, len(path.read_text().splitlines())]]
        else:
            difference = git("diff", "--unified=0", "HEAD", "--", name).decode()
            ranges = []
            for start, count in re.findall(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@", difference, re.M):
                length = int(count or "1")
                ranges.append({"start": int(start), "length": length})
        changed_files.append({"path": name, "absolute_path": str(path), "sha256": digest(path),
                              "type": "new" if added else "modified", "changed_line_ranges": ranges,
                              "note": "Changed ranges, not a new full-file audit coverage claim."})
    write("diff-identity.json", {"source_manifest_sha256": FROZEN,
                                  "tracked_diff_sha256": identity["diff_sha256"],
                                  "complete_patch": patch_ref,
                                  "modified_original_files": len(changes),
                                  "new_source_files": len(new_sources),
                                  "files": changed_files})

    cycles = ("combined-production-04", "native-alltests-01", "combined-apple-01", "final-resource-checks-01")
    summaries = []
    for cycle in cycles:
        directory = RUN / "evidence" / cycle
        receipt = load(directory / "receipt.json")
        if receipt["status"] != "PASS" or receipt["source_after"]["source_manifest_sha256"] != FROZEN:
            raise RuntimeError("Final evidence does not identify a successful frozen-source cycle")
        suites = load(directory / "test-receipts.json")
        groups = collections.defaultdict(lambda: collections.Counter())
        for suite in suites:
            if "parse_error" in suite:
                raise RuntimeError("Cannot count an unparsed test report")
            module_name, task_path = suite["file"].split("/build/test-results/")
            task = task_path.split("/")[0]
            group = groups[(module_name, task)]
            group["suites"] += 1
            for key in ("tests", "failures", "errors", "skipped"):
                group[key] += int(suite[key])
        summaries.append({"cycle": cycle, "receipt": reference(directory / "receipt.json"),
                          "log": reference(directory / "gradle.log"),
                          "command": receipt["command"], "status": receipt["status"],
                          "started_at": receipt["started_at"], "finished_at": receipt["finished_at"],
                          "source_manifest_sha256": FROZEN,
                          "test_groups": [{"module": m, "task": t, **dict(counts),
                                           "passed": counts["tests"] - counts["skipped"] -
                                                     counts["failures"] - counts["errors"]}
                                          for (m, t), counts in sorted(groups.items())],
                          "artifacts": reference(directory / "artifact-receipts.json"),
                          "stop_exit_code": receipt["stop_exit_code"],
                          "cleanup_completed_at": receipt["cleanup_completed_at"],
                          "cleanup_errors": receipt["cleanup_errors"],
                          "remaining_outputs": receipt["remaining_outputs"],
                          "workers": receipt["workers"]})
    write("verification-summary.json", {"recorded_at": timestamp,
                                         "counts_are_executions_not_distinct_tests_across_cycles": True,
                                         "cycles": summaries})

    history = []
    for path in sorted((RUN / "evidence").glob("*/receipt.json")):
        receipt = load(path)
        if receipt.get("status") == "RUNNING":
            continue  # Enclosing materialization cycle finalizes after this command returns.
        history.append({"cycle": path.parent.name, "receipt": reference(path),
                        **{key: receipt.get(key) for key in (
                            "status", "command", "exit_code", "started_at", "finished_at",
                            "stop_exit_code", "cleanup_completed_at", "cleanup_errors",
                            "retained_outputs", "remaining_outputs", "workers")},
                        "purpose_qualification": "Original-defect negative witness; NOT proof of repair"
                        if "-red" in path.parent.name else "Use actual task/suite outcomes; a failed aggregate is not PASS"})
    write("cycle-history.json", {"recorded_at": timestamp, "cycles": history,
                                 "note": "Original receipts remain intact. Missing historical fields stay null; "
                                         "later cleanup claims must not be backfilled into older receipts."})

    index = load(RUN / "native_fix_review/issue-status-evidence-check.json")
    final_issues = []
    for issue in index["issues"]:
        status = "PARTIALLY VERIFIED" if issue["id"] == "DS-C01" else (
            "BLOCKED" if issue["id"] == "WD-C2" else "FIXED AND VERIFIED")
        refs = list(issue["independent_refs"])
        followups = {
            "MF-C1": ["factory_review/mafia-detekt-review-01/independent-followup-approval-01.json"],
            "WD-C3": ["factory_review/whodunit-detekt-review-01/independent-followup-approval-01.json"],
            "DS-C02": ["native_fix_review/DS-C02-dispatch-review-01/independent-followup-approval-01.json"],
        }
        refs += [reference(RUN / rel) for rel in followups.get(issue["id"], [])]
        fresh_tests = []
        expected_suites = {Path(item["path"]).name for item in issue.get("focused_xml", [])}
        if issue["id"] == "DS-C01":
            expected_suites.add("TEST-com.parlor.app.ProductionUiAccessibilityContractTest.xml")
        for cycle in cycles[:2]:
            for path in sorted((RUN / "evidence" / cycle / "reports").rglob("TEST-*.xml")):
                if path.name in expected_suites:
                    import xml.etree.ElementTree as ET
                    suite = ET.parse(path).getroot()
                    fresh_tests.append({**reference(path), "suite": suite.get("name"),
                                        "counts": {key: int(suite.get(key, "0")) for key in
                                                   ("tests", "failures", "errors", "skipped")}})
        final_issues.append({"id": issue["id"], "canonical_id": issue["canonical_id"],
                             "aliases": issue["aliases"], "severity": issue["original_severity"],
                             "title": issue["original_title"], "status": status,
                             "correction": issue["correction"],
                             "fix_author": issue["fix_author"],
                             "independent_reviewer": issue["remediation_independent_reviewer"],
                             "independent_conclusion": "Approved bounded correction with executed regressions"
                             if status == "FIXED AND VERIFIED" else (
                                 "Ownership correction approved; legacy provenance and real-app lifecycle matrix incomplete"
                                 if status == "PARTIALLY VERIFIED" else "Original defect confirmed; no safe authorial correction chosen"),
                             "original_evidence": issue["original_refs"],
                             "independent_evidence": refs,
                             "evidence_limits": issue["evidence_limits"], "compatibility": issue["compatibility"],
                             "fresh_regression_xml": fresh_tests,
                             "release_python_tests": issue.get("focused_python_tests"),
                             "release_python_evidence": reference(RUN / "evidence/combined-production-04/gradle.log")
                             if issue.get("focused_python_tests") else None,
                             "detailed_focused_proof_index": reference(RUN / "native_fix_review/issue-status-evidence-check.json"),
                             "source_manifest_sha256": FROZEN})
    counts = collections.Counter(item["status"] for item in final_issues)
    if counts != {"FIXED AND VERIFIED": 14, "PARTIALLY VERIFIED": 1, "BLOCKED": 1}:
        raise RuntimeError("Unexpected issue classification/count")
    write("issues.json", {"recorded_at": timestamp, "source_manifest_sha256": FROZEN,
                          "scope": "16 authorized independently confirmed defects, not all project/audit/Store work",
                          "completion": {"numerator": 14, "denominator": 16, "percentage": 87.5,
                                         "counts": dict(counts), "not_a_readiness_or_github_closure_percentage": True},
                          "issues": final_issues})
    print(json.dumps({"status": "PASS", "source_manifest_sha256": FROZEN,
                      "complete_patch": patch_ref, "preserved_original_files": len(previous),
                      "modified_original_files": len(changes), "new_source_files": len(new_sources),
                      "completion": "14/16 = 87.5% scoped fixes only"}, indent=2))


if __name__ == "__main__":
    main()
