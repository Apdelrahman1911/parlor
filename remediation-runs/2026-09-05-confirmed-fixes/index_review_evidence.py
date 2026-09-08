#!/usr/bin/env python3
"""Index preserved independent review/research records without changing them."""
import datetime
import hashlib
import json
from pathlib import Path

RUN = Path(__file__).resolve().parent
ROOT = RUN.parents[1]
FINAL = RUN / "final"


def ref(path):
    return {"path": str(path.relative_to(ROOT)), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}


def json_read(path):
    return json.loads(path.read_text())


def urls(value):
    """Only locate already-recorded references; do not invent research dates/claims."""
    found = set()
    if isinstance(value, dict):
        for key, child in value.items():
            if key in {"url", "final_url", "effective_url", "source_url"} and isinstance(child, str):
                if child.startswith("https://"):
                    found.add(child)
            found.update(urls(child))
    elif isinstance(value, list):
        for child in value:
            found.update(urls(child))
    return found


def main():
    issues = json_read(FINAL / "issues.json")
    reviewed = {item["path"] for issue in issues["issues"] for item in issue["independent_evidence"]}
    supplemental = [
        "native_fix_review/issue-status-evidence-check.json",
        "native_fix_review/issue-status-evidence-addendum-01.json",
        "native_fix_review/documentation-review-02-approval.json",
        "native_fix_review/native-final-verification-01.json",
        "factory_review/final-cross-game-review-01.json",
        "factory_review/final-session-cross-check-01.json",
        "release_fix_review/combined-production-evidence-04.json",
        "release_fix_review/combined-apple-evidence-01.json",
        "release_fix_review/final-report-independent-checkpoint-01.json",
        "mafia/run-gradle-cycle-independent-review-02.json",
        "mafia/native-wrapper-independent-review-01.json",
    ]
    reviewed.update(str((RUN / item).relative_to(ROOT)) for item in supplemental)
    research = []
    for directory in ("factory_review", "native_fix_review", "release_fix_review", "whodunit", "mafia"):
        for path in sorted((RUN / directory).glob("*.json")):
            if "research" not in path.name.lower() and "reference" not in path.name.lower():
                continue
            value = json_read(path)
            references = sorted(urls(value))
            if references:
                research.append({**ref(path), "urls": references,
                                 "note": "Access dates, exact-version applicability, claims, response/extraction hashes "
                                         "and limitations are preserved in this original record."})
    result = {
        "recorded_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "source_manifest_sha256": issues["source_manifest_sha256"],
        "scope": "Remediation evidence index, not a claim that root independently reread the entire repository or all cited upstream source.",
        "original_audit_register": ref(ROOT / "audit-runs/2026-09-05-source-audit/canonical-register.json"),
        "issue_dispositions": ref(FINAL / "issues.json"),
        "independent_reviews": [ref(ROOT / path) for path in sorted(reviewed)],
        "research_records": research,
        "execution_controls": [ref(RUN / name) for name in (
            "run_gradle_cycle.py", "owned_ios_simulator.py", "owned_ios_simulator.init.gradle",
            "assemble_final_evidence.py", "complete_final_indexes.py", "index_review_evidence.py")],
        "continuation_note": "Report checkpoint is not final approval. The new DS-C01 evidence-only actual-app harness "
                             "and later runtime/report-validation receipts will be indexed in an explicit addendum, "
                             "not silently backfilled into original build/audit receipts.",
    }
    path = FINAL / "review-and-research-index.json"
    with path.open("x") as out:
        json.dump(result, out, ensure_ascii=False, indent=2)
        out.write("\n")
    print(json.dumps({"status": "PASS", "independent_review_records": len(reviewed),
                      "research_records": len(research), "path": str(path.relative_to(ROOT))}))


if __name__ == "__main__":
    main()
