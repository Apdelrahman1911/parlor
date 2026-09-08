#!/usr/bin/env python3
"""Add final continuation deliverables; never replace prior milestones/receipts."""
import copy
import datetime
import hashlib
import json
from pathlib import Path
import sys

RUN = Path(__file__).resolve().parent
ROOT = RUN.parents[1]
FINAL = RUN / "final"
sys.path.insert(0, str(RUN))
import record_final_observation_v2 as observer
import run_gradle_cycle as lane

FROZEN = observer.FROZEN
RUNTIME_REVIEW = RUN / "factory_review/dsc01-apphost-02-independent-result-review.json"


def read(path):
    return json.loads(observer.safe_path(path).read_text())


def ref(path):
    return observer.ref(path)


def gate_ref(path):
    value = ref(path)
    return {"kind": "path", "ref": value["path"], "sha256": value["sha256"]}


def write_new(name, value):
    with (FINAL / name).open("x") as stream:
        stream.write(value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def main():
    outputs = ["REPORT-CONTINUATION.md", "issues-continued.json", "gates-continued.json", "continuation-evidence.json"]
    if any((FINAL / name).exists() or (FINAL / name).is_symlink() for name in outputs):
        raise RuntimeError("Refuse to overwrite an existing continuation deliverable")
    source = observer.validated_source_identity(lane)
    if source["source_manifest_sha256"] != FROZEN:
        raise RuntimeError("Frozen source changed")
    prior_names = ["REPORT.md", "issues.json", "gates.json", "source-identity.json", "diff-identity.json",
                   "remediation.patch", "preservation.json", "verification-summary.json", "cycle-history.json",
                   "issue-source-map.json", "review-and-research-index.json"]
    prior = {name: ref(FINAL / name) for name in prior_names}
    proof_dir = RUN / "evidence/dsc01-apphost-02"
    runtime = read(proof_dir / "receipt.json")
    summary = read(proof_dir / "xcresult-summary.json")
    probe = read(proof_dir / "probe-result.json")
    if (runtime["status"] != "PASS" or runtime["runtime_evidence_status"] != "PASS" or
            runtime["cleanup_status"] != "PASS" or runtime["source_before"]["source_manifest_sha256"] != FROZEN or
            not runtime["source_unchanged"] or not runtime["controls_unchanged"] or
            {k: summary[k] for k in ("totalTestCount", "passedTests", "failedTests", "skippedTests")} !=
            dict(totalTestCount=1, passedTests=1, failedTests=0, skippedTests=0) or
            not probe["completed"] or len(probe["observations"]) != 14):
        raise RuntimeError("No exact complete runtime/cleanup evidence")
    # The immutable independent dossier is included in full by reference and
    # must already exist; root inspects its conclusion before this materializer.
    independent = ref(RUNTIME_REVIEW)
    if independent["sha256"] != "11137e5e68aa794a056c5c2d0eaaca5ea1843a609063a27245c25c5cbbed8795":
        raise RuntimeError("Independent runtime approval differs from reviewed dossier")
    stamp = datetime.datetime.now(datetime.timezone.utc).isoformat()

    issues = copy.deepcopy(read(FINAL / "issues.json"))
    issues.update(recorded_at=stamp, supersedes=prior["issues.json"],
                  continuation_note="No application source changed. Only DS-C01 verification and its remaining limits are updated.")
    item = next(x for x in issues["issues"] if x["id"] == "DS-C01")
    item["independent_reviewer"] = "native_fix_review (source fix); factory_review (actual app-host result)"
    item["independent_conclusion"] = "Ownership fix and exact four-boot app-host restart/System matrix approved within scope; legacy provenance and active-session/direct Compose/OS Settings limits remain."
    item["independent_evidence"].append(independent)
    item["actual_apphost_evidence"] = [ref(proof_dir / n) for n in (
        "receipt.json", "xcresult-summary.json", "xcresult-tests.json", "probe-result.json", "input-manifest.json",
        "copied-wrapper.diff", "secondary-fifo-ownership-final.json")]
    item["evidence_limits"] = (
        "11 native owner tests, 13 source-contract tests and 1 actual app-host XCTest pass. Four distinct processes/14 events "
        "prove persisted AR/EN ownership before App initializes and System restoration of absence/exact prior [ar-EG,en], "
        "localized UI and native semantic direction. Observer modifies temporary Swift wrapper only. Legacy unmarked "
        "AppleLanguages provenance, identical external writes and asynchronous durability remain limited; no direct Compose "
        "direction, actual iOS Settings application, active-game identity or physical-device proof."
    )
    assert item["status"] == "PARTIALLY VERIFIED" and issues["completion"]["percentage"] == 87.5

    gates = copy.deepcopy(read(FINAL / "gates.json"))
    gates.update(recorded_at=stamp, supersedes=prior["gates.json"])
    by_id = {x["id"]: x for x in gates["gates"]}
    by_id["remediation-ds-c01"]["reason"] = item["independent_conclusion"] + " " + item["evidence_limits"]
    by_id["remediation-ds-c01"]["evidence"] += [gate_ref(RUNTIME_REVIEW), gate_ref(proof_dir / "receipt.json")]
    by_id["ios-real-app-language-lifecycle"]["reason"] = (
        "Actual four-boot Settings/restart matrix now passes with native direction and localized resources. Broader gate remains "
        "incomplete: legacy unmarked-preference policy, direct Compose direction, OS Settings interaction and active-game continuity. "
        "In-game navigation intentionally does not expose Settings; synthetic bridge mutation is not an existing UI journey."
    )
    by_id["ios-swift-apphost-and-wrapper"]["reason"] = (
        "Unsigned Debug app-host with temporary Swift observer now compiled and executed successfully; original Kotlin App/Settings "
        "unchanged. This is not the unmodified shipping-wrapper smoke test or an unsigned Swift Release wrapper/archive run. "
        "The archived runner remains untouched; its secondary FIFO cleanup limitation was addressed in independently reviewed "
        "new harness controls and actually verified on both new cycles."
    )
    action = next(x for x in gates["manual_actions"] if x["id"] == "local-ios-apphost-verification")
    action["id"] = "remaining-ios-language-lifecycle"
    action["action"] = ("The owned-app restart/System and safe-cleanup prerequisite now pass. Remaining: "
                        "direct Compose direction, actual OS Settings interaction and active-game continuity "
                        "evidence without inventing an in-game Settings route or treating root identity as session proof.")
    gates["gates"].append({"id": "ios-owned-app-language-restart-matrix", "status": "PASS",
        "reason": "One real unskipped XCTest, four distinct process boots, 14 bounded observations: actual AR/EN selection, "
                  "persisted before-App ownership, System absence/prior-array restoration, localized controls and native direction. "
                  "Not active-game, direct Compose direction, OS Settings, physical, release or Store proof.",
        "evidence": [gate_ref(RUNTIME_REVIEW)] + [gate_ref(proof_dir / n) for n in (
            "receipt.json", "xcresult-summary.json", "xcresult-tests.json", "probe-result.json")]})

    old_cycles = {x["cycle"] for x in read(FINAL / "cycle-history.json")["cycles"]}
    new_cycles = []
    for path in sorted((RUN / "evidence").glob("*/receipt.json")):
        d = read(path)
        if d["cycle"] in old_cycles:
            continue
        if d["status"] == "RUNNING":
            # This materialization cycle is bound by the later final observation,
            # after its mandatory stop/cleanup. Never relabel it PASS prematurely.
            new_cycles.append({"cycle": d["cycle"], "status_at_materialization": "RUNNING",
                               "final_receipt_locator": str(path.relative_to(ROOT)),
                               "note": "Later final-observation.json binds its finished receipt."})
            continue
        new_cycles.append(observer.cycle_observation(path))

    reviews = ["factory_review/dsc01-harness-execution-review-01.json",
               "factory_review/dsc01-apphost-01-independent-result-review.json",
               "factory_review/dsc01-accessibility-independent-review-01.json",
               "factory_review/dsc01-harness-v2-execution-review.json",
               "factory_review/dsc01-apphost-02-independent-result-review.json",
               "release_fix_review/final-observation-control-review-01.json",
               "release_fix_review/final-observation-control-review-02.json",
               "release_fix_review/final-observation-control-approval-03.json",
               "native_fix_review/DS-C01-remaining-gap-adjudication-01.json",
               "native_fix_review/DS-C01-active-session-gap-addendum-01.json"]
    research = ["native_fix_review/DS-C01-gap-research-01.json",
                "factory_review/dsc01-cmp-source-01/receipt.json",
                "factory_review/dsc01-accessibility-counterevidence-01/receipt.json",
                "native_fix_review/dsc01-accessibility-source-01/receipt.json",
                "native_fix_review/dsc01-accessibility-source-02/receipt.json"]
    controls = ["native_fix_review/dsc01_apphost/author-receipt-01.json",
                "native_fix_review/dsc01_apphost_v2/author-receipt-01.json",
                "observation-controls-contracts-01/manifest.json", "record_final_observation.py",
                "record_final_observation_v2.py", "test_final_observation_v2.py", Path(__file__).name]
    evidence = {"recorded_at": stamp, "scope": "Additive continuation of scoped 16-defect remediation, not a new exhaustive audit",
        "source_manifest_sha256": FROZEN, "source_changed": False, "previous_deliverables_unchanged": prior,
        "completion": issues["completion"], "new_cycle_records": new_cycles,
        "additional_independent_reviews_and_gap_dossiers": [ref(RUN / p) for p in reviews],
        "additional_research_records": [ref(RUN / p) for p in research],
        "evidence_controls": [ref(RUN / p) for p in controls],
        "runtime_result": {"cycle": runtime["cycle"], "independent_review": independent,
                           "receipt": ref(proof_dir / "receipt.json"), "test_count": 1, "passed": 1,
                           "failed": 0, "skipped": 0, "actual_process_boots": 4, "probe_observations": 14},
        "preserved_failure": {"cycle": "dsc01-apphost-01", "actual_result": "FAIL_EXECUTED",
            "classification": "TEST/HARNESS SELECTOR FAILURE; no application crash or original-defect conclusion",
            "tests": 1, "passed": 0, "failed": 1, "skipped": 0,
            "note": "Immutable v1 runtime_evidence_status=NOT_RUN is incorrect bookkeeping. Raw XCTest executed and failed before language mutation. V2 repairs only the harness selector/status, preserving assertions.",
            "independent_review": ref(RUN / "factory_review/dsc01-apphost-01-independent-result-review.json")},
        "historical_control_locator": {"reason": "Exact pre-follow-up recorder/test bytes from contracts01 remain archived; old review hashes identify these historical versions, not current v2.",
            "manifest": ref(RUN / "observation-controls-contracts-01/manifest.json")},
        "cleanup_note": "Final observation evaluates all finished modern receipts and owned simulator/FIFO cleanup. ios-b1-red retains an explicit missing historical PID-ledger limitation; no historical ownership is invented.",
        "final_observation_locator": "remediation-runs/2026-09-05-confirmed-fixes/final/final-observation.json"}

    report = (FINAL / "REPORT.md").read_text()
    report = report.replace("# Parlor — Controlled Remediation Report", "# Parlor — Controlled Remediation Report (Continuation)", 1)
    report = report.replace("**Date:** 6 September 2026 (Africa/Cairo).", "**Date:** 6 September 2026 (Africa/Cairo).  \n**Current report:** this additive continuation supersedes status statements in [the preserved prior milestone](REPORT.md).", 1)
    report = report.replace("The final continuation completed the remaining combined native and Apple checks, structural English/Arabic resource validation, independent evidence review, and the preservation/diff record. It did not introduce further application behavior changes.",
        "The earlier milestone completed combined native/Apple checks and resource validation. This continuation added a real iOS app-host language/restart matrix, independently reviewed cleanup controls, and final evidence reconciliation. It changed no application source and preserved every prior receipt, including the first failed selector test.")
    report = report.replace("[issues.json](issues.json)", "[issues-continued.json](issues-continued.json)")
    report = report.replace("**11 native ownership tests**, plus 13 structural source-contract checks.", "**11 native ownership tests**, 13 structural source-contract checks, and **one actual app-host XCTest with four distinct process launches**.")
    report = report.replace("`native_fix_review`: approves the ownership correction, **not complete end-to-end or legacy-upgrade coverage**.", "`native_fix_review`: source fix approved; `factory_review`: actual four-boot restart/System matrix approved. **Legacy-upgrade and broader lifecycle limits remain**.")
    anchor = "Counts are **executions**, not distinct tests summed across overlapping cycles."
    extra = """### Additional actual iOS runtime verification

`dsc01-apphost-02` **PASS**: one exact XCTest, zero failures/skips, four distinct app process launches and 14 ordered synthetic observations. Through the actual Settings UI, Arabic survives a restart then System restores prior absence/English fallback; English survives a restart then System restores the exact synthetic prior `[ar-EG, en]` preference. Ownership was already present **before original App initialization** on both restarts. Real localized controls and the original UIKit root's forced LTR/RTL direction are checked; exactly one root controller was created per process.

The test uses unchanged Kotlin App/Settings and an **isolated copied Swift wrapper** containing a bounded observer. It is not an unmodified shipping-wrapper smoke test, direct Compose-direction probe, active-game identity test, actual iOS Settings app interaction, physical-device or signed-release proof. `factory_review/dsc01-apphost-02-independent-result-review.{md,json}` independently verifies the raw results, copy/source binding and cleanup.

The first attempt, `dsc01-apphost-01`, genuinely executed **one failed XCTest**: its exact Settings label omitted the child text that pinned CMP 1.10.3 merges into the accessibility label. Home launch/observation succeeded; no language mutation occurred. This was not an inferred app crash. Its incorrect `NOT_RUN` receipt field remains untouched and is explicitly qualified by raw failure evidence. The versioned harness fixes the selector and status bookkeeping—not application code—and preserves every language/restart assertion. Both successful and failed cycles cleaned their outputs, owned simulators, workers and attested secondary FIFOs.

**36** current harness safety/receipt/shell/selector contracts and **17** final-observation contracts pass. These are evidence-infrastructure tests, not additional app-runtime cases. Hash-bound before/after controls and Gradle-stop receipts are retained; earlier overlapping executions are not summed as distinct tests.

"""
    report = report.replace(anchor, extra + anchor, 1)
    start = report.index("Native tests exercise real Foundation preferences")
    end = report.index("### Other unresolved or external gates", start)
    report = report[:start] + """The actual four-process Settings/restart matrix described above now passes. Remaining verification is **direct Compose direction, actual iOS Settings interaction and active-game/controller continuity**. Current in-game navigation deliberately hides Settings; changing the persistent domain behind the running store would not test its real live mutation path. No artificial navigation path or production observation hook was added.

An old unmarked `AppleLanguages` value may be an earlier Parlor override or a legitimate OS-managed preference. Current code safely preserves it. **An explicit legacy handling policy remains necessary**; the test does not manufacture missing provenance. Same-value external writes and asynchronous/nontransactional UserDefaults durability remain bounded guarantees. DS-C01 therefore stays **PARTIALLY VERIFIED**, despite the now-successful restart matrix.

The archived Apple runner remains untouched. New independently reviewed versioned harnesses address its secondary-worker cleanup limitation: live PID/start ancestry, UID/inode/birth-time and exact FIFO-pair ownership are attested; only those FIFOs and empty parents are removed after workers exit. **Both actual cycles verified this cleanup.** No broad `/var/folders` deletion, global-cache deletion or unrelated process termination occurred.

""" + report[end:]
    report = report.replace("The actual Swift/Compose app-launch test and unsigned Swift Release wrapper were **not rerun in this remediation**. Kotlin-native tests and framework linkage are not substitutes.",
        "The copied unsigned Debug Swift/Compose app-host now ran successfully. The **unmodified shipping-wrapper smoke test and unsigned Swift Release wrapper** were not rerun; the observer matrix does not replace them.")
    report = report.replace("Final cycles report no cleanup errors, remaining owned workers or retained build outputs.",
        "Completed modern cycles report no cleanup errors, remaining owned workers or retained build outputs. The original `ios-b1-red` receipt predates PID/start ownership logging; its empty scan is retained as a historical evidence limitation, not fabricated proof of historical worker absence.")
    report = report.replace("[Issue ledger](issues.json), [gate ledger](gates.json)", "[Current issue ledger](issues-continued.json), [current gate ledger](gates-continued.json)")
    report = report.replace("[Independent review and research index](review-and-research-index.json).", "[Original review/research index](review-and-research-index.json), plus [the additive continuation evidence](continuation-evidence.json).")
    report = report.replace("In parallel, complete the DS-C01 app-host verification prerequisite and lifecycle matrix.",
        "Resolve the DS-C01 legacy-language policy and remaining broader lifecycle evidence; the bounded app-host restart matrix is now complete.")
    report += "\nThe latest [final observation](final-observation.json) binds the finished post-materialization cycles, preservation checks, cleanup evaluation, observed workers and free disk. It deliberately records incomplete historical PID evidence rather than claiming perfect historical cleanup provenance.\n"
    write_new("issues-continued.json", issues)
    write_new("gates-continued.json", gates)
    write_new("continuation-evidence.json", evidence)
    write_new("REPORT-CONTINUATION.md", report)
    for name, previous in prior.items():
        if ref(FINAL / name) != previous:
            raise RuntimeError("Original milestone changed during materialization")
    print(json.dumps({"status": "PASS", "source_manifest_sha256": FROZEN,
                      "completion": issues["completion"], "created": [ref(FINAL / n) for n in outputs]}, indent=2))


if __name__ == "__main__":
    main()
