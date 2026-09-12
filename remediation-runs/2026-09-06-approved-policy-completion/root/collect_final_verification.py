#!/usr/bin/env python3
"""Materialize additive verification ledgers from receipts, not assumed outcomes."""
from collections import Counter, defaultdict
import datetime
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

RUN = Path(__file__).resolve().parents[1]
ROOT = RUN.parents[1]
DEST = RUN / "final"
SELECTED = (
    "combined-desktop-static-04", "combined-production-04",
    "combined-native-alltests-02", "combined-apple-02", "resources-and-diff-02",
)


def ref(path):
    return {"path": str(path.relative_to(ROOT)),
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}


def write_new(name, value):
    with (DEST / name).open("x") as output:
        json.dump(value, output, indent=2, ensure_ascii=False)
        output.write("\n")


def test_totals(directory):
    total = Counter(tests=0, failures=0, errors=0, skipped=0)
    by_task = defaultdict(Counter)
    modules = defaultdict(Counter)
    skipped, reports, descriptors = [], [], set()
    for file in sorted((directory / "reports").glob("**/test-results/**/TEST-*.xml")):
        document = ET.parse(file).getroot()
        counts = {key: int(document.get(key, "0")) for key in total}
        tests = document.findall("testcase")
        if counts["tests"] != len(tests):
            raise RuntimeError("XML descriptor count disagrees: " + str(file))
        failed = sum(test.find("failure") is not None for test in tests)
        errors = sum(test.find("error") is not None for test in tests)
        skips = sum(test.find("skipped") is not None for test in tests)
        if (failed, errors, skips) != (counts["failures"], counts["errors"], counts["skipped"]):
            raise RuntimeError("XML child/header counts disagree: " + str(file))
        relative = file.relative_to(directory / "reports")
        task = relative.parts[relative.parts.index("test-results") + 1]
        module = "/".join(relative.parts[:relative.parts.index("build")])
        for test in tests:
            descriptor = (module, task, test.get("classname"), test.get("name"))
            if descriptor in descriptors:
                raise RuntimeError("Duplicate XML descriptor: " + str(descriptor))
            descriptors.add(descriptor)
            if test.find("skipped") is not None:
                skipped.append({"module": module, "task": task,
                                "suite": test.get("classname"), "name": test.get("name")})
        total.update(counts)
        by_task[task].update(counts)
        modules[module + ":" + task].update(counts)
        reports.append({**ref(file), "suite": document.get("name"), "counts": counts})
    return {"suite_count": len(reports), "totals": dict(total),
            "passed": total["tests"] - total["failures"] - total["errors"] - total["skipped"],
            "by_task": dict(by_task), "by_module_and_task": dict(modules),
            "skipped_descriptors": skipped, "reports": reports}


def main():
    frozen = json.loads((RUN / "source-freeze-02.json").read_text())["source"]
    at = datetime.datetime.now(datetime.timezone.utc).isoformat()
    combined = []
    for name in SELECTED:
        directory = RUN / "evidence" / name
        receipt = json.loads((directory / "receipt.json").read_text())
        if (receipt["source_before"] != frozen or receipt["source_after"] != frozen or
                receipt["status"] != "PASS" or receipt["exit_code"] != 0):
            raise RuntimeError("Selected gate did not pass on final source: " + name)
        entry = {"cycle": name, "receipt": ref(directory / "receipt.json"),
                 "log": ref(directory / "gradle.log"), "status": receipt["status"],
                 "command": receipt["command"], "started_at": receipt["started_at"],
                 "finished_at": receipt["finished_at"], "exit_code": receipt["exit_code"],
                 "source_manifest_sha256": frozen["source_manifest_sha256"],
                 "cleanup": {key: receipt.get(key) for key in (
                     "stop_exit_code", "stopped_at", "workers", "removed_outputs",
                     "retained_outputs", "remaining_outputs", "cleanup_errors", "cleanup_completed_at")},
                 "tests": test_totals(directory)}
        if name == "combined-production-04":
            log = (directory / "gradle.log").read_text()
            matches = re.findall(r"^Ran (\d+) tests? in [\d.]+s$", log, re.M)
            if matches != ["172"] or "\nOK\n" not in log:
                raise RuntimeError("Release Python suite completion not established")
            lint_files = list((directory / "reports").glob("**/reports/lint-results-release.xml"))
            lint = [issue for path in lint_files for issue in ET.parse(path).getroot().findall("issue")]
            if len(lint_files) != 1 or len(lint) != 32 or any(issue.get("severity") != "Warning" for issue in lint):
                raise RuntimeError("Current accepted lint inventory differs from independently reviewed 32-warning result")
            entry["release_python_suite"] = {"tests": 172, "status": "PASS", "evidence": ref(directory / "gradle.log")}
            entry["lint"] = {"reports": [ref(path) for path in lint_files], "issues": len(lint),
                             "by_severity": dict(Counter(issue.get("severity") for issue in lint)),
                             "by_id": dict(Counter(issue.get("id") for issue in lint)),
                             "qualification": "Accepted warnings are retained. Earlier inventory-gate failures remain FAIL in history."}
        entry["artifacts"] = json.loads((directory / "artifact-receipts.json").read_text())
        combined.append(entry)
    write_new("verification-summary.json", {"recorded_at": at,
              "source_manifest_sha256": frozen["source_manifest_sha256"], "combined": combined,
              "qualification": "Counts are executions per cycle, not disjoint totals to add across overlapping gates. iOS ARM64 simulator runtime is not physical LAN; Apple framework linkage is not Swift app/runtime verification. Native app-host evidence is separate in issues/gates."})
    history, still_running = [], []
    for path in sorted((RUN / "evidence").glob("*/receipt.json")):
        receipt = json.loads(path.read_text())
        if receipt.get("status") == "RUNNING":
            still_running.append({"cycle": path.parent.name,
                                  "reason": "Receipt was unfinished during collection; no outcome claimed. See final observation after cleanup."})
            continue
        stops = receipt.get("gradle_stops", [{"exit_code": receipt.get("stop_exit_code"), "at": receipt.get("stopped_at")}])
        cleanup = receipt.get("cleanup_status")
        if cleanup is None:
            workers = receipt.get("workers")
            cleanup = "PASS" if (receipt.get("stop_exit_code") == 0 and workers is not None and
                not workers.get("remaining_owned_workers") and not receipt.get("remaining_outputs") and
                not receipt.get("retained_outputs") and not receipt.get("cleanup_errors")) else "FAIL"
        history.append({"cycle": path.parent.name, "receipt": ref(path), "status": receipt.get("status"),
            "runtime_evidence_status": receipt.get("runtime_evidence_status"),
            "started_at": receipt.get("started_at"), "finished_at": receipt.get("finished_at"),
            "exit_code": receipt.get("exit_code", receipt.get("xcodebuild_exit_code")),
            "source_manifest_sha256": receipt.get("source_before", {}).get("source_manifest_sha256"),
            "error": receipt.get("error"), "stops": stops, "cleanup_status": cleanup,
            "cleanup_errors": receipt.get("cleanup_errors", []), "remaining_outputs": receipt.get("remaining_outputs", []),
            "source_changed": receipt.get("source_changed_during_cycle", not receipt.get("source_unchanged", True)),
            "note": "Recorded historical outcome retained; a later green run/diagnosis never overwrites this receipt."})
    write_new("cycle-history.json", {"recorded_at": at, "cycles": history,
              "unfinished_at_collection": still_running,
              "prior_campaign_history": ref(ROOT / "remediation-runs/2026-09-05-confirmed-fixes/final/cycle-history.json"),
              "history_qualification": "Failed compilation, selectors, red witnesses, runtime inconsistency and cleanup attestation are different outcomes. Exact causes are in linked diagnosis/review records. This snapshot excludes later final-ledger validation cycles; final observation records those."})
    print(json.dumps({"combined_gates": len(combined), "cycles_recorded": len(history),
                      "source_manifest_sha256": frozen["source_manifest_sha256"]}))


if __name__ == "__main__":
    main()
