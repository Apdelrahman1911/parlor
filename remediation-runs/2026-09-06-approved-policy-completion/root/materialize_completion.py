#!/usr/bin/env python3
"""Additive report materialization, only after selected outcomes are reviewed.

This does not approve a fix. Root supplies separately reviewed, hash-bound
outcomes, and an independent reviewer must inspect the resulting documents.
No application, Git history, process, simulator, or Store operation is performed.
"""
import copy
import datetime
import hashlib
import json
from pathlib import Path

RUN = Path(__file__).resolve().parents[1]
ROOT = RUN.parents[1]
DEST = RUN / "final"
IDS = ("ST-C1", "SN-C1", "SN-C2", "WD-C1", "IOS-B1", "ROOT-T3", "MF-C1", "WD-C3",
       "M-C03", "DS-C01", "DS-C02", "DS-C03", "WD-C2", "RL-C1", "RL-C2", "RL-C3")


def sha(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def reference(path):
    return {"path": str(path.relative_to(ROOT)), "sha256": sha(path)}


def read(path):
    return json.loads(path.read_text())


def checked_reference(value):
    relative = Path(value["path"])
    if relative.is_absolute() or ".." in relative.parts:
        raise ValueError("Evidence reference must stay in the repository")
    path = ROOT / relative
    if path.is_symlink() or sha(path) != value["sha256"]:
        raise ValueError("Selected evidence changed: " + str(relative))
    return path


def new_json(name, value):
    with (DEST / name).open("x") as output:
        json.dump(value, output, indent=2, ensure_ascii=False)
        output.write("\n")


def gate(identifier, status, reason, references, owner=None):
    value = {"id": identifier, "status": status, "reason": reason,
             "evidence": [{"kind": "path", "ref": item["path"], "sha256": item["sha256"]}
                          for item in references]}
    if status == "BLOCKED":
        if not owner:
            raise ValueError("A blocked gate requires an owner")
        value["owner"] = owner
    return value


def selected_native(selection, key, frozen):
    outcome = selection[key]
    receipt = read(checked_reference(outcome["receipt"]))
    checked_reference(outcome["independent_review"])
    if (receipt.get("status") != "PASS" or receipt.get("xcodebuild_exit_code") != 0 or
            receipt.get("cleanup_status") != "PASS" or receipt.get("cleanup_errors") or
            receipt.get("remaining_outputs") or receipt.get("owned_processes_remaining") or
            receipt.get("source_before") != frozen or receipt.get("source_after") != frozen or
            receipt.get("source_unchanged") is not True or receipt.get("controls_unchanged") is not True or
            receipt.get("copied_sources_unchanged") is not True or
            not receipt.get("gradle_stops") or
            any(stop.get("exit_code") != 0 for stop in receipt["gradle_stops"])):
        raise ValueError("Selected native cycle is not cleanly passing on the frozen source: " + key)
    if key == "native_language":
        if (receipt.get("execution_kind") !=
                "manifest-owned-copy-unsigned-ios-dsc01-observation-invocation-matrix" or
                receipt.get("runtime_evidence_status") != "PASS" or
                receipt.get("embedded_gradle_stop_status") != "PASS" or
                any(receipt.get("xctest", {}).get(field) != expected for field, expected in
                    {"result": "Passed", "total": 5, "skipped": 0, "failures": 0}.items()) or
                receipt.get("os_settings_gate", {}).get("status") != "PASS" or
                receipt.get("dsc01_matrix", {}).get("actual_settings_and_local_sessions_gate") != "PASS" or
                any(receipt.get("dsc01_matrix", {}).get("local_games", {}).get(game, {}).get("status") != "PASS"
                    for game in ("whodunit", "mafia"))):
            raise ValueError("Language slot needs the actual complete Settings/OS/local-game matrix, not smoke or linkage")
    elif key == "release_wrapper":
        if (receipt.get("execution_kind") !=
                "manifest-owned-copy-unsigned-ios-source-identical-release-wrapper-build" or
                receipt.get("build_evidence_status") != "PASS" or
                receipt.get("artifact_inspection_status") != "PASS" or
                receipt.get("embedded_gradle_stop_status") != "PASS" or
                receipt.get("build_configuration") != "Release" or
                receipt.get("build_platform") != "iOS Simulator" or
                receipt.get("architectures") != ["arm64"]):
            raise ValueError("Release slot needs the unsigned arm64 Release wrapper build and artifact inspection")
        inventory_path = checked_reference(outcome["artifact_inventory"])
        if receipt.get("artifact_inventory_sha256") != sha(inventory_path):
            raise ValueError("Release artifact inventory is not bound to its actual build receipt")
    else:
        raise ValueError("Unknown native outcome slot: " + key)
    return receipt


def adopt_issue_review_chains(index, issues):
    """Keep old draft outcomes historical; expose every current reviewed chain."""
    index["historical_issue_review_chains"] = index.pop("issue_review_chains")
    index["issue_review_chains"] = [
        {"finding_id": issue["finding_id"], "status": issue["status"],
         "independent_review_evidence": copy.deepcopy(issue["independent_review_evidence"]),
         "conclusion": issue["independent_conclusion"]}
        for issue in issues
    ]


def main():
    selection_path = RUN / "root/completion-selection.json"
    selection = read(selection_path)
    frozen = read(RUN / "source-freeze-02.json")["source"]
    support_path = RUN / "reviews/final-matrix-support-01.json"
    support = read(support_path)
    if support["source"]["source_manifest_sha256"] != frozen["source_manifest_sha256"]:
        raise ValueError("Support ledger is not bound to the final source")
    native = selected_native(selection, "native_language", frozen)
    release = selected_native(selection, "release_wrapper", frozen)
    if native.get("runtime_evidence_status") != "PASS":
        raise ValueError("Actual language matrix is not a runtime PASS")
    preservation_path = checked_reference(selection["preservation_observation"])
    preservation = read(preservation_path)
    if (preservation.get("status") != "PASS_CURRENT_BOUNDED_OBSERVATION" or
            preservation.get("source_matches_freeze02") is not True):
        raise ValueError("Preservation has not passed at the source being reported")
    now = datetime.datetime.now(datetime.timezone.utc).isoformat()
    issues = copy.deepcopy(support["issues"])
    if tuple(issue["finding_id"] for issue in issues) != IDS:
        raise ValueError("Unexpected issue inventory/order")
    ds = next(issue for issue in issues if issue["finding_id"] == "DS-C01")
    # Original partial receipts remain preserved as historical evidence, but are
    # never left masquerading as the current result or silently changed to PASS.
    ds["historical_native_apphost04"] = ds.pop("native_apphost04")
    ds["historical_completion_conditions"] = ds.pop("remaining_completion_conditions")
    for key in ("implemented_correction", "independent_conclusion", "regression_summary", "scope_limits"):
        ds[key] = selection["native_language"][key]
    ds["status"] = "FIXED AND VERIFIED"
    ds["independent_review_evidence"].append(selection["native_language"]["independent_review"])
    ds["current_native_verification"] = selection["native_language"]
    ds["remaining_completion_conditions"] = []
    if any(issue["status"] != "FIXED AND VERIFIED" for issue in issues):
        raise ValueError("This completion materializer requires every scoped fix to be independently verified")
    for issue in issues:
        for entry in issue["independent_review_evidence"]:
            checked_reference(entry)
        for entry in issue["regression_xml"]:
            checked_reference(entry)
    percentage = {"denominator": 16, "fixed_and_verified": 16, "partially_verified": 0, "blocked": 0,
                  "percentage_fixed_and_verified": 100.0,
                  "meaning": "Sixteen authorized repairs and their explicitly stated local verification only.",
                  "not_claimed": ["100% project review", "absence of other defects", "GitHub closure", "Store readiness"]}
    new_json("issues.json", {"schema_version": 1, "recorded_at": now,
        "source": support["source"], "source_identity": reference(DEST / "source-identity.json"),
        "complete_patch": reference(DEST / "remediation.patch"), "percentage": percentage,
        "basis": reference(support_path), "outcome_selection": reference(selection_path),
        "issues": issues, "doctor_requirement_confirmation": support["doctor_requirement_confirmation"],
        "qualification": "Historical authorship/review chains and narrower test meanings remain intact. This is not a new exhaustive source audit.",
        "remaining_audit_items": selection["remaining_audit_items"]})

    verification = read(DEST / "verification-summary.json")
    gates = [gate("remediation-" + issue["finding_id"].lower(), "PASS",
        issue["implemented_correction"] + " Limits: " + issue["scope_limits"],
        [reference(DEST / "issues.json"), *issue["independent_review_evidence"]]) for issue in issues]
    names = {"combined-desktop-static-04": "desktop-and-static-analysis",
             "combined-production-04": "production-check",
             "combined-native-alltests-02": "configured-alltests-and-native-runtime",
             "combined-apple-02": "apple-release-frameworks-and-analysis",
             "resources-and-diff-02": "resource-parity-and-tracked-whitespace"}
    for entry in verification["combined"]:
        gates.append(gate(names[entry["cycle"]], "PASS", selection["combined_gate_reasons"][entry["cycle"]],
                          [entry["receipt"], entry["log"], reference(DEST / "verification-summary.json")]))
    for key, identifier in (("native_language", "ios-actual-language-direction-and-continuity"),
                            ("release_wrapper", "unsigned-swift-release-wrapper")):
        outcome = selection[key]
        gates.append(gate(identifier, "PASS", outcome["gate_scope"],
                          [outcome["receipt"], outcome["independent_review"]]))
    smoke = selection["uninstrumented_smoke"]
    for entry in (smoke["receipt"], smoke["independent_review"]):
        checked_reference(entry)
    gates.append(gate("uninstrumented-ios-app-launch", "PASS", smoke["gate_scope"],
                      [smoke["receipt"], smoke["independent_review"]]))
    gates.append(gate("frozen-source-and-preservation", "PASS",
        "Intentionally dirty 669-input source is frozen; all 2,180 baseline files retained with exactly 13 approved changes. Refs/stash/logical staged diff and original audits/design/AGENTS preserved. Not a clean release candidate.",
        [reference(DEST / "source-identity.json"), reference(DEST / "diff-identity.json"), reference(preservation_path)]))
    gates.append(gate("current-owned-resource-cleanup", "PASS",
        "Selected cycles' immediate Gradle stops and precise owned cleanup passed. The bounded observation found recorded owned paths/workers absent; historical apphost02 attestation remains FAIL, never retroactively PASS. Later final observation is separate.",
        [reference(preservation_path), reference(DEST / "cycle-history.json"),
         selection["native_language"]["receipt"], selection["release_wrapper"]["receipt"]]))
    for value in selection["remaining_gates"]:
        for entry in value["references"]:
            checked_reference(entry)
        gates.append(gate(value["id"], value["status"], value["reason"], value["references"], value.get("owner")))
    candidate = {key: frozen[key] for key in ("commit", "tree", "branch", "source_manifest_sha256")}
    candidate.update(clean=False, not_a_release_candidate=True,
                     tracked_diff_sha256=frozen["diff_sha256"], complete_patch_sha256=sha(DEST / "remediation.patch"))
    new_json("gates.json", {"schema_version": 1, "recorded_at": now, "candidate": candidate,
        "verdict": "NOT_READY", "scope": "Authorized sixteen-defect remediation and explicitly distinguished verification/release gates.",
        "gates": gates, "artifacts": selection["artifacts"], "manual_actions": selection["manual_actions"]})
    new_json("preservation.json", {"recorded_at": now, "observation": reference(preservation_path),
        "source": preservation["source"], "source_input_count": preservation["source_input_count"],
        "source_matches_freeze02": preservation["source_matches_freeze02"],
        "baseline_preservation": preservation["baseline_preservation"],
        "git_preservation": preservation["git_preservation"],
        "historical_cleanup_qualification": preservation["historical_cleanup_qualification"],
        "qualification": "Time-bound bounded observation before report materialization. final-observation.json records the subsequent last observation and ledger-validation cleanup."})
    draft_index = RUN / "reviews/final-review-research-index-draft-01.json"
    index = read(draft_index)
    index["historical_status_snapshot"] = index.pop("status_snapshot")
    index["historical_pending_finalization"] = index.pop("pending_finalization")
    adopt_issue_review_chains(index, issues)
    index.update(recorded_at=now, integrator="/root", status="FINAL_SCOPED_INDEX_ROOT_ADOPTED",
                 original_draft=reference(draft_index), status_snapshot=percentage,
                 final_scope_evidence=selection["final_scope_evidence"],
                 adoption_qualification="Original access dates, failed research attempts and prior review identities remain unchanged. New outcomes are separately reviewed evidence, not approvals by the original index author.")
    new_json("review-and-research-index.json", index)
    report = (RUN / "root/REPORT-DRAFT.md").read_text()
    report = report.replace("**DRAFT — native-language verification and final evidence reconciliation are still in progress.**",
        "**Scoped remediation complete: 16/16 independently reviewed and locally verified (100%). Store readiness is NOT established.**")
    report = report.replace("<!-- FINAL_SCOPE_COUNTS -->",
        "**16 FIXED AND VERIFIED / 16 authorized repairs = 100%.** No scoped repair remains partially verified or blocked. This denominator excludes the remaining product decisions, test-evidence gaps and external release gates below.")
    report = report.replace("**PENDING FINAL NATIVE REVIEW**", "**FIXED AND VERIFIED**")
    report = report.replace("<!-- FINAL_NATIVE_RESULT -->", selection["native_report_markdown"])
    report = report.replace("<!-- FINAL_PRESERVATION -->", selection["preservation_report_markdown"])
    report = report.replace("Counts are **executions per cycle**;", selection["release_report_markdown"] + "\n\nCounts are **executions per cycle**;")
    report = report.replace("- **IOS-R1:**", selection["additional_remaining_markdown"] + "\n- **IOS-R1:**")
    if "<!-- FINAL_" in report or "PENDING FINAL NATIVE" in report or "**DRAFT" in report:
        raise ValueError("Unfinished report marker")
    with (DEST / "REPORT.md").open("x") as output:
        output.write(report)
    print(json.dumps({"status": "MATERIALIZED_REQUIRES_INDEPENDENT_FINAL_REVIEW",
                      "issues": 16, "gates": len(gates), "source_manifest_sha256": frozen["source_manifest_sha256"]}))


if __name__ == "__main__":
    main()
