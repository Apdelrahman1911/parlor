#!/usr/bin/env python3
"""Read-only consistency checks; execute through the owned finalization lane.

This verifies retained evidence, not new application/device behavior. The final
observation is intentionally created only after this cycle has been cleaned.
"""
from collections import Counter
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess

RUN = Path(__file__).resolve().parent
ROOT = RUN.parents[1]
FINAL = RUN / "final"
# The configured skill alone is a documented symlink outside the repository.
# Pin its exact inspected target; evidence/source links remain prohibited.
OFFICIAL_REQUESTED = Path("/Users/abdelrahman/.agents/skills/production-readiness-audit/scripts/validate_evidence_ledger.py")
OFFICIAL = Path("/Users/abdelrahman/Projects/passvault/engineering-skills/skills/production-readiness-audit/scripts/validate_evidence_ledger.py")
FROZEN = "e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7"
PINS = {
    RUN / "run_gradle_cycle.py": "d2d18c2311d59554db88be3f92e609ec5ca46fc8af4c79702836f9e4c130240f",
    RUN / "record_final_observation_v2.py": "f5dabf6f162f4b4e359e928a5b371a6f42e1e1420c1c357b738707fc1753533d",
    FINAL / "REPORT-CONTINUATION.md": "a3359c488364141d326964a13427e558f5d7fc6e00f190d54effa9e805a34b8f",
    FINAL / "issues-continued.json": "fcdf977fcdf9d1be7693142aab2152ae713ba9cfecc1ee02b1d1cfc832324c10",
    FINAL / "gates-continued.json": "92f64cb19df9e802e996eb22ef4e1a3f6e3f02591f751f92c431fa0b90ea10a9",
    FINAL / "continuation-evidence.json": "c8b8f844b7666e7a159c9c99dc93984310f336f9168dd91b231bc556bc343d2b",
    OFFICIAL: "81d72fc25f9e67ae00740019e52e3d51485dfab782775809c4ceb61776cf268e",
}


def need(condition, reason):
    if not condition:
        raise RuntimeError(reason)


def sha(path):
    need(not any(p.is_symlink() for p in (path, *path.parents)), f"Symlink ancestry: {path}")
    need(path.is_file(), f"Missing regular file: {path}")
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    sha(path)
    return json.loads(path.read_text())


def load_control(name):
    path = RUN / (name + ".py")
    need(sha(path) == PINS[path], f"Unreviewed control: {name}")
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def verify_references(value, references):
    if isinstance(value, dict):
        name = value.get("path") if "path" in value else value.get("ref") if value.get("kind") == "path" else None
        if isinstance(name, str) and isinstance(value.get("sha256"), str):
            path = Path(name)
            need(not path.is_absolute() and ".." not in path.parts, "Unsafe evidence reference")
            need(not any(part in name.lower() for part in (
                ".keystore", ".p12", ".p8", ".mobileprovision", "credentials.json",
                "service-account", "local.properties",
            )), "Protected evidence reference")
            need(sha(ROOT / path) == value["sha256"], f"Evidence drift: {name}")
            references.add(name)
        for child in value.values():
            verify_references(child, references)
    elif isinstance(value, list):
        for child in value:
            verify_references(child, references)


def command(args):
    result = subprocess.run(args, cwd=ROOT, text=True, capture_output=True)
    print(json.dumps({"command": args, "exit_code": result.returncode,
                      "stdout": result.stdout, "stderr": result.stderr}), flush=True)
    need(result.returncode == 0, "Validation command failed")


def main():
    need(OFFICIAL_REQUESTED.resolve(strict=True) == OFFICIAL, "Configured skill target changed")
    print(json.dumps({"official_validator_requested": str(OFFICIAL_REQUESTED),
                      "official_validator_canonical": str(OFFICIAL)}), flush=True)
    before = {str(p): sha(p) for p in PINS}
    before[str(Path(__file__).resolve())] = sha(Path(__file__).resolve())
    print(json.dumps({"controls_and_deliverables_before": before}), flush=True)
    need(all(before[str(p)] == expected for p, expected in PINS.items()), "Input/control pin mismatch")
    try:
        observer = load_control("record_final_observation_v2")
        lane = load_control("run_gradle_cycle")
        current = json.loads(json.dumps(observer.validated_source_identity(lane)))
        frozen = read(FINAL / "source-identity.json")
        need(current["source_manifest_sha256"] == FROZEN, "Application source changed")
        need(all(current[k] == v for k, v in frozen.items() if k != "recorded_at"), "Source/checkout drift")

        issues = read(FINAL / "issues-continued.json")
        gates = read(FINAL / "gates-continued.json")
        continuation = read(FINAL / "continuation-evidence.json")
        references = set()
        for data in (issues, gates, continuation):
            verify_references(data, references)

        issue_map = {item["id"]: item for item in issues["issues"]}
        expected_ids = set("ST-C1 SN-C1 SN-C2 WD-C1 IOS-B1 ROOT-T3 MF-C1 WD-C3 M-C03 DS-C01 DS-C02 DS-C03 WD-C2 RL-C1 RL-C2 RL-C3".split())
        need(len(issues["issues"]) == len(issue_map) == 16 and set(issue_map) == expected_ids, "Incorrect issue scope")
        issue_counts = dict(Counter(x["status"] for x in issue_map.values()))
        need(issue_counts == {"FIXED AND VERIFIED": 14, "PARTIALLY VERIFIED": 1, "BLOCKED": 1}, "Incorrect completion count")
        need(issues["completion"]["counts"] == issue_counts, "Completion metadata mismatch")
        need(issues["completion"]["numerator"] == 14 and issues["completion"]["denominator"] == 16
             and issues["completion"]["percentage"] == 100 * 14 / 16, "Incorrect completion percentage")
        old = {x["id"]: x for x in read(FINAL / "issues.json")["issues"]}
        need(all(item == old[key] for key, item in issue_map.items() if key != "DS-C01"), "Unrelated issue metadata changed")
        need(issue_map["DS-C01"]["status"] == "PARTIALLY VERIFIED" and issue_map["WD-C2"]["status"] == "BLOCKED", "Blocked issue promoted")
        for item in issue_map.values():
            if item["status"] == "FIXED AND VERIFIED":
                need(bool(item["independent_evidence"]), "Missing independent review")
                author = item["fix_author"].split("/")[-1]
                need(author not in re.findall(r"[a-z][a-z0-9_]*", item["independent_reviewer"]), "Author self-approved")

        gate_counts = dict(Counter(g["status"] for g in gates["gates"]))
        need(len(gates["gates"]) == 38 and gate_counts == {"PASS": 23, "BLOCKED": 15}, "Gate count changed")
        need(gates["verdict"] == "NOT_READY" and gates["candidate"]["clean"] is False, "Incorrect readiness")
        by_gate = {g["id"]: g for g in gates["gates"]}
        for key, item in issue_map.items():
            need(by_gate["remediation-" + key.lower()]["status"] ==
                 ("PASS" if item["status"] == "FIXED AND VERIFIED" else "BLOCKED"), "Issue/gate discrepancy")
        command(["/usr/bin/python3", "-B", str(OFFICIAL), str(FINAL / "gates-continued.json"), "--root", str(ROOT)])

        summary_fields = ("totalTestCount", "passedTests", "failedTests", "skippedTests")
        for cycle, expected in (("dsc01-apphost-01", (1, 0, 1, 0)), ("dsc01-apphost-02", (1, 1, 0, 0))):
            summary = read(RUN / "evidence" / cycle / "xcresult-summary.json")
            need(tuple(summary[k] for k in summary_fields) == expected, "Actual XCTest result mismatch")
        probe = read(RUN / "evidence/dsc01-apphost-02/probe-result.json")
        observations = probe["observations"]
        need(probe["completed"] is True and len(observations) == 14, "Incomplete app-host observations")
        need([o["ordinal"] for o in observations] == list(range(1, 15)), "Nonordered app-host observations")
        need(len({o["boot"] for o in observations}) == 4, "Incorrect process-boot count")
        need(continuation["preserved_failure"]["actual_result"] == "FAIL_EXECUTED", "First attempt failure hidden")

        report = (FINAL / "REPORT-CONTINUATION.md").read_text()
        pending_links = []
        for target in re.findall(r"\[[^\]]+\]\(([^)\s]+)\)", report):
            path = (FINAL / target).resolve()
            path.relative_to(ROOT)
            if target == "final-observation.json" and not path.exists():
                pending_links.append(target)
            else:
                need(path.is_file(), f"Broken report link: {target}")
        command(["git", "diff", "--check"])
        command(["git", "apply", "--check", "--reverse", str(FINAL / "remediation.patch")])
        need(json.loads(json.dumps(observer.validated_source_identity(lane))) == current, "Verification modified application source")
        print(json.dumps({"status": "PASS", "source_manifest_sha256": FROZEN,
                          "retained_evidence_references_checked": len(references), "issue_counts": issue_counts,
                          "completion_percentage": 87.5, "gate_counts": gate_counts,
                          "pending_post_cleanup_observation_links": sorted(set(pending_links)),
                          "scope": "Evidence/checkout consistency only; no new runtime/build claim"}, indent=2), flush=True)
    finally:
        need(OFFICIAL_REQUESTED.resolve(strict=True) == OFFICIAL, "Configured skill target changed")
        after = {name: sha(Path(name)) for name in before}
        print(json.dumps({"controls_and_deliverables_after": after, "unchanged": before == after}), flush=True)
        need(before == after, "Evidence/control mutation during validation")


if __name__ == "__main__":
    main()
