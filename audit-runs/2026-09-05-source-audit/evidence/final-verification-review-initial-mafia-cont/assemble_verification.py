#!/usr/bin/env python3
"""Index executed evidence and explicit unexecuted gates; never infer a test pass."""
import collections
import datetime
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
PREFIX = HERE.relative_to(ROOT).as_posix() + "/"
baseline = json.loads((HERE / "baseline.json").read_text())
coverage = json.loads((HERE / "coverage/summary.json").read_text())
register = json.loads((HERE / "canonical-register.json").read_text())
gates = []

def gate(id, status, reason, refs=(), owner=None, method="source-and-evidence-review"):
    item = dict(id=id, status=status, reason=reason, method=method,
                evidence=[dict(kind="path", ref=PREFIX + p) for p in refs])
    if owner:
        item["owner"] = owner
    gates.append(item)

gate("source-preservation", "PASS", "Tracked SHA/tree, all fingerprinted inputs, refs, stash and non-audit untracked inventory preserved; not a claim of an empty working tree.", ["baseline.json", "evidence/final-state.json"])
gate("fresh-inventory-and-recorded-reading", "PASS" if not coverage["errors"] else "FAIL", f"{coverage['complete_verbatim_text_file_count']}/628 text files and {coverage['verbatim_read_line_count']}/139888 raw lines have reading receipts; six binaries inspected, protected/generated/prior exclusions explicit. Complete reading is not complete behavior verification. Assembly errors: {coverage['errors']}", ["coverage/inventory.jsonl", "coverage/FINAL_COVERAGE.jsonl", "coverage/summary.json"])
gate("observed-build-graph", "PASS", "Fresh Gradle observation records thirteen KMP modules and actual source-set/task wiring; test fixtures/prototype/Desktop shipping distinctions independently reviewed.", ["coverage/observed-build-graph.txt", "evidence/focused-storage-01/receipt.json", "reviews/root-build-notes.md"], method="gradle-configuration-plus-source-review")
gate("focused-storage-desktop", "PASS", "Second focused storage run executed 31 passing cases. First run command passed but its XML collection was incomplete, so its full count is not inferred.", ["evidence/focused-storage-02/receipt.json", "evidence/focused-storage-02/test-receipts.json"] , method="executed-tests")
gate("production-desktop-check", "PASS", "productionDesktopCheck executed 1108 descriptors:1107 passed/1 intentional physical skip. ROOT-T3's undiscovered tests are a separate failed registration gate.", ["evidence/baseline-desktop-static-01/receipt.json", "evidence/baseline-desktop-static-01/test-receipts.json"], method="executed-tests-and-compile")
gate("static-analysis", "PASS", "Repository Detekt and shell dispatch selected in broad checks passed; retained Detekt reports have zero error entries. This is not a proof of functional correctness.", ["evidence/baseline-desktop-static-01/receipt.json", "evidence/production-check-01/receipt.json", "reviews/verification-cleanup-reconciliation-mafia-cont.md"], method="executed-gradle-gates")
gate("production-check", "PASS", "Host-independent productionCheck passed: Desktop1108/1skip; app Android unit76 per variant; release Python130; validators/lint/R8/unsigned AAB. Does not include managed release runtime, real signing, Apple or physical LAN.", ["evidence/production-check-01/receipt.json", "evidence/production-check-01/test-receipts.json", "reviews/verification-cleanup-reconciliation-mafia-cont.md"], method="executed-gradle-aggregate")
gate("android-lint-policy", "PASS", "Release lint passes the checked-in warning policy with 32 accepted warnings:OldTargetApi1,AGP-version4,GradleDependency3,NewerVersionAvailable24. Not zero warnings or blanket future compatibility.", ["evidence/production-check-01/receipt.json", "reviews/verification-cleanup-reconciliation-mafia-cont.md"], method="executed-lint-and-warning-validator")
gate("android-unsigned-release-build", "PASS", "R8/shrinking/unsigned AAB and configured manifest/identity validators ran. Receipts retained, generated AAB cleaned; no signed/device runtime claim.", ["evidence/production-check-01/receipt.json", "evidence/production-check-01/artifact-receipts.json"], method="executed-release-build-without-signing")
gate("strict-dependency-verification", "PASS", "All selected Gradle cycles retained strict verification; metadata/selected published artifact checks reviewed. Unconsumed cross-host artifacts are not certified by a host build.", ["evidence/production-check-01/receipt.json", "evidence/native-alltests-01/receipt.json", "reviews/metadata-whodunit_cont-notes.md", "reviews/verification-metadata-mafia-cont.md"], method="executed-strict-builds-plus-metadata-review")
gate("alltests-executed-matrix", "PASS", "Combined allTests/ARM64 simulator cycle:2285 descriptors,2284 passing,1 skip; Desktop1108,Android392 each variant,iOS393. The explicitly nonexecuted task matrix remains separate.", ["evidence/native-alltests-01/receipt.json", "evidence/native-alltests-01/test-receipts.json", "reviews/verification-cleanup-reconciliation-mafia-cont.md"], method="executed-test-aggregate")
gate("apple-release-framework-linkage", "PASS", "productionAppleCheck at the repository6g heap linked device arm64/simulator arm64/x64 Release frameworks on Xcode26.5. Earlier audit3g OOM was an environment/harness limit, not an app crash. Linkage is not runtime/signing.", ["evidence/apple-check-02/receipt.json", "evidence/apple-check-01/receipt.json"], method="executed-compile-and-link")
gate("ios-arm64-selected-runtime", "PASS", "393 selected common/native tests ran in an owned ARM64 simulator. Mafia/engine desktop-only bodies did not run; Whodunit native has9,not288 Desktop cases.", ["evidence/native-alltests-01/receipt.json", "evidence/native-alltests-01-simulator/receipt.json", "evidence/native-alltests-01/test-receipts.json"], method="executed-simulator-tests")
gate("ios-wrapper-launch-smoke", "PASS", "One checked-in English XCTest passed foreground/Home/no-native-alert assertions; EN/AR screenshots are supplemental. Does not assert recovery health, gestures, a full game or repeated-launch stability.", ["evidence/xcode-ui-01/receipt.json", "evidence/xcode-ui-01/xcresult-summary.json", "evidence/xcode-ui-01/xcresult-tests.json"], method="executed-unsigned-simulator-xctest")
gate("binary-format-and-wrapper-inspection", "PASS", "Six binary inputs inspected with format/metadata/image tools; exact wrapper JAR and configured distribution checksum compared with official8.13 endpoints. No copyright/device rendering or cached distribution attestation inferred.", ["reviews/binary-assets-wrapper-mafia-cont.md", "evidence/design-font-metadata.json"], method="executed-binary-inspection")

# Each FAIL here is an actually executed requirement review or an explicit
# failing reproducer; it is never an invented unexecuted test result.
gate("ios-legacy-snapshot-quarantine", "FAIL", "ST-C1: actual K/N production filesystem leaves malformed legacy plaintext backup-eligible;2 witnesses pass and desired-safety assertion fails. No actual backup performed.", ["validations/ST-C1-root.md", "evidence/storage-native-01/receipt.json", "evidence/storage-native-01/test-receipts.json"], method="native-reproducer-plus-independent-source-proof")
gate("accepted-start-commit-durability", "FAIL", "SN-C1: valid near-deadline commit returns failure if best-effort ACK consumes the remaining receive timeout.", ["validations/SN-C1-root.md", "evidence/repro-session-01/receipt.json"], method="deterministic-session-reproducer")
gate("host-opening-cancellation-ownership", "FAIL", "SN-C2: lifecycle-registration cancellation after kit creation leaves stopCalls0 instead of1 in production transport with fake kit.", ["validations/SN-C2-root.md", "evidence/repro-ui-transport-02/receipt.json"], method="production-boundary-fake-transport-reproducer")
gate("whodunit-peer-readiness-idempotence", "FAIL", "WD-C1: reachable remount schedule automatically submits fresh duplicate ACKs. Source/effect proof, not an executed device/UI test.", ["candidates/WD-C1-independent-session_cont.md"], method="independent-reachable-source-proof")
gate("current-snapshot-reachability", "FAIL", "M-C01 and WD-C3: accepted malformed authenticated snapshots violate Doctor-history or active-final-two rules. Normal producers/authentication bypass not demonstrated.", ["validations/MF-C1-whodunit_cont.md", "validations/WD-C3-root.md", "evidence/repro-mafia-01/receipt.json", "evidence/repro-pending-01/receipt.json"], method="deterministic-codec-and-resume-reproducers")
gate("toast-and-result-layout", "FAIL", "DS-C02 missing replacement toast and M-C03 zero-width final role reproduced in unchanged Desktop production composables. Other mobile geometry remains unexecuted.", ["validations/DS-C02-root.md", "validations/M-C03-root.md", "evidence/repro-ui-03/receipt.json", "evidence/repro-pending-01/receipt.json"], method="production-component-tests")
gate("ios-follow-system-ownership", "FAIL", "DS-C01: source plus native-equivalent two-process preference witness confirms stale owned override. Not an iOS app/device runtime measurement.", ["validations/DS-C01-root.md", "evidence/native-preference-01/receipt.json"], method="source-and-native-preference-witness")
gate("recovery-action-contrast", "FAIL", "DS-C03: reachable Light Ghost-on-black text pair has2.70034:1 contrast. Source/color calculation, not a physical screen test.", ["validations/DS-C03-whodunit_cont.md", "research/DS-C03-w3c-whodunit_cont.json"], method="independent-color-and-source-proof")
gate("bundled-narrative-consistency", "FAIL", "WD-C2: four independently traced contradictory chronologies; structural content tests do not prove editorial consistency. Ambiguous age/duration leads are excluded.", ["validations/WD-C2-mafia_cont.md"], method="independent-authored-data-to-ui-proof")
gate("xcode-framework-failure-propagation", "FAIL", "IOS-B1: native synthetic Xcode phase exits0 after wrapper42 with stale placeholder. No actual crash/archive/signature inferred.", ["validations/IOS-B1-session_cont.md", "evidence/native-xcode-phase-01/receipt.json"], method="native-shell-phase-witness")
gate("transport-jupiter-test-registration", "FAIL", "ROOT-T3:10nonvoid annotated methods absent from normal discovery. Eight enabled bodies later passed isolated wrappers; registration remains unfixed, physical ignores remain.", ["validations/ROOT-T3-session_cont.md", "evidence/inspect-transport-tests-02/compiled-test-methods.json", "evidence/repro-pending-01/test-receipts.json"], method="compiled-metadata-and-execution-reconciliation")
gate("latent-release-validator-policy", "FAIL", "RL-C1 GNU stat,RL-C2 upload-certificate trust and RL-C3 edit-snapshot TOCTOU independently confirmed. First2 source proofs; third synthetic HTTP test. Current identity/workflow disablement remains effective.", ["validations/RL-C1-session_cont.md", "validations/RL-C2-session_cont.md", "validations/RL-C3-session_cont.md", "evidence/repro-release-race-01/receipt.json"], method="independent-source-research-and-synthetic-reproducer")
gate("documentation-and-evidence-fidelity", "FAIL", f"{register['counts']['by_category']['documentation']} documentation mismatches and5 independently validated test/evidence gaps remain. They are separate from the16code/content/tooling defects.", ["canonical-register.json", "OTHER_CANDIDATES.md"], method="independent-source-and-assertion-review")

owner = "project owner and authorized verification agent"
gate("physical-lan-and-lifecycle", "BLOCKED", "No physical multi-device same-LAN proof. Pairwise transport is insufficient for a complete game:current Whodunit needs6 devices,Mafia5–16. Ignored physical tests also need fixture repair without weakening admission.", ["validations/SN-T1-root.md", "validations/SN-D1-root.md"], owner)
gate("android-managed-release-runtime", "BLOCKED", "Locally feasible but unexecuted productionAndroidRuntimeCheck/Pixel2 API35 Release instrumentation needs a disposable-device/install-ready test-signing setup not used here. Not inherently a physical-device-only gate.", ["reviews/verification-cleanup-reconciliation-mafia-cont.md"], owner)
gate("qualified-apple-toolchain-and-real-signing", "BLOCKED", "Installed Xcode26.5/17F42 differs from qualified26.3/17C529; no credential access/real signing/archive or device packaging verification authorized or executed.", ["reviews/root-build-notes.md", "evidence/apple-check-02/receipt.json"], "release owner")
gate("ios-recovery-warning-attribution", "BLOCKED", "IOS-R1 warning is observed; original KMP local-vs-credential error not captured. Separate native probe -34018 supports environment hypothesis but does not establish actual-app cause.", ["validations/IOS-R1-session_cont.md", "evidence/iosr1-native-02/receipt.json"], owner)
gate("navigation-accessibility-and-complete-ui-journeys", "BLOCKED", "Source reviewed, but full Android Back/predictive Back, iOS LTR/RTL gestures, keyboard/large-text/landscape/screen-reader/reduced-motion and complete live-game UI matrix was not executed. Narrow simulator smoke does not satisfy it.", ["reviews/root-platform-shell-notes.md", "reviews/whodunit-ui-followup-whodunit_cont.md", "evidence/xcode-ui-01/receipt.json"], owner)
gate("native-cross-host-test-equivalence", "BLOCKED", "13iosX64Test tasks host-disabled; Mafia/engine tests desktop-only (Native skipped, Android NO-SOURCE). Linux/Windows/other-architecture workflow execution at this tree not evidenced locally.", ["reviews/verification-cleanup-reconciliation-mafia-cont.md"], "CI/platform verification owner")
gate("device-storage-and-app-switcher-privacy", "BLOCKED", "No real-device lock/background/rejoin/OS-backup or app-switcher evidence. Post-open NSFileHandle fault remains unconfirmed; no simulator/native mock can establish all device protection behaviors.", ["validations/storage-residual-root.md", "validations/ST-C1-root.md", "reviews/root-storage-shell-notes.md"], owner)
gate("whodunit-modal-clock-policy", "BLOCKED", "WD-C4 actual cancellation/freeze is corroborated, but elapsed-time policy during Leave confirmation is unspecified. Obtain product decision before calling it a bug or changing clocks.", ["validations/WD-C4-root.md"], "game/product owner")
gate("store-identity-and-live-governance", "BLOCKED", "Tracked policy blocks canonical identity ownership and candidate/promotion jobs. No live owner/Store/protection evidence obtained; no replacements, uploads or publishing activation authorized.", ["reviews/root-build-notes.md", "validations/DOC-C4-session_cont.md", "reviews/release-whodunit_cont-notes.md"], "application and release owner")
gate("content-rights-and-store-declarations", "BLOCKED", "Story/icon/font rights, owner privacy/accessibility declarations and Store acceptance require owner/external evidence. Source, notices and metadata inspection do not establish legal rights or review approval.", ["reviews/binary-assets-wrapper-mafia-cont.md", "reviews/product-docs-mafia-cont.md", "reviews/release-docs-whodunit_cont-notes.md"], "content/legal and release owner")

gate("database-migrations", "NOT_APPLICABLE", "No Room/SQLite database schema or migrations in the observed app graph/first-party persistence. Snapshot/credential/settings migrations are applicable and separately reviewed.", ["reviews/root-build-notes.md", "reviews/root-storage-shell-notes.md"])
gate("cloud-accounts-and-internet-matchmaking", "NOT_APPLICABLE", "Current application uses offline bundled content and same-LAN rooms, with no account backend/internet matchmaking/host migration/raw-IP joining. Release Store API scripts are not an application backend.", ["reviews/session-cont-notes.md", "reviews/whodunit-content-notes.md", "reviews/root-build-notes.md"])
gate("timed-mafia-rounds", "NOT_APPLICABLE", "MafiaSettings.validate rejects all nonnull timer compatibility fields as TimersNotSupported. Timed Mafia gameplay is intentionally outside the current product, not unfinished implementation.", ["reviews/mafia-engine-notes.md"])
gate("desktop-store-packaging", "NOT_APPLICABLE", "Desktop is the declared run/deterministic-test target, not a shipping Store package. Desktop lifecycle/dependency code is still reviewed; no unused package/signing workflow is invented.", ["reviews/root-build-notes.md", "reviews/root-platform-shell-notes.md"])
gate("cycle-cleanup", "PASS", "All16Gradle cycles have immediate stop0 and exact task-output cleanup; native/Xcode companions separately record owned simulator/temp removal. Other task/version daemons and global caches preserved.", ["CLEANUP_LEDGER.json", "reviews/verification-cleanup-reconciliation-mafia-cont.md", "reviews/verification-cleanup-reconciliation-mafia-cont-supplement-01.md"], method="per-cycle-receipts-and-independent-reconciliation")

ledger = dict(schema_version=1, generated_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
              candidate=dict(commit=baseline["commit"], tree=baseline["tree"], clean=False,
                             clean_qualification="Tracked sources unchanged; pre-existing untracked work and the new isolated audit directory intentionally remain. Full inventory/hash binding in baseline/coverage ledgers."),
              verdict="NOT_READY", gates=gates, artifacts=[],
              artifact_qualification="No release candidate retained/approved; inspected generated bytes were hashed in cycle receipts then removed per mandatory cleanup. Unsigned/test artifacts are not Store evidence.",
              manual_actions=[dict(id=g["id"], owner=g["owner"], action=g["reason"], blocking=True) for g in gates if g["status"] == "BLOCKED"])
(HERE / "verification-ledger.json").write_text(json.dumps(ledger, indent=2) + "\n")

primary = []
native = []
for file in sorted((HERE / "evidence").glob("*/receipt.json")):
    value = json.loads(file.read_text())
    if not isinstance(value, dict):
        continue
    ref = file.relative_to(HERE).as_posix()
    item = dict(receipt=ref, sha256=hashlib.sha256(file.read_bytes()).hexdigest(),
                raw_status=value.get("status"), started_at=value.get("started_at", value.get("timestamp")),
                finished_at=value.get("finished_at"), command=value.get("command", value.get("commands")),
                exit_code=value.get("exit_code"),
                qualification="Raw command result; see VERIFICATION.md/reconciliations for intended failures, harness failures and witness limits.")
    if "cleanup_method" in value and value.get("command", [""])[0] == "./gradlew":
        item.update({k: value.get(k) for k in ("stop_exit_code", "stopped_at", "removed_outputs", "cleanup_errors", "remaining_outputs", "cleanup_completed_at", "cleanup_method", "source_before", "source_after", "report_collection_error")})
        item["cleanup_status"] = "PASS" if value.get("stop_exit_code") == 0 and value.get("cleanup_errors") == [] and value.get("remaining_outputs") == [] else "FAIL"
        primary.append(item)
    else:
        item["cleanup_fields"] = {k: v for k, v in value.items() if any(x in k for x in ("cleanup", "stop", "removed", "remaining", "absent", "shutdown", "delete", "owned_temp"))}
        native.append(item)
assert len(primary) == 16, len(primary)
cleanup = dict(schema_version=1, primary_gradle_cycles=primary, other_native_probe_or_companion_receipts=native,
               primary_cleanup_counts=dict(collections.Counter(x["cleanup_status"] for x in primary)),
               independent_reconciliation=["evidence/verification-cleanup-reconciliation-mafia-cont.json", "evidence/verification-cleanup-reconciliation-mafia-cont-supplement-01.json"],
               limitations=["Other/native records have heterogeneous schemas; their results are not guessed from a missing field. Refer to full receipts and independent reconciliation.", "No parent/start-time provenance was captured for every pre-existing unrelated daemon; no claim to have terminated them.", "Reports under evidence/.../reports/.../build are compact retained evidence, not live build intermediates.", "Final source/output/process comparison is separately recorded in evidence/final-preservation-and-hygiene.json."])
(HERE / "CLEANUP_LEDGER.json").write_text(json.dumps(cleanup, indent=2) + "\n")

text = ["# Verification ledger", "", "**Verdict: NOT READY.** PASS applies only to the named executed gate, not the whole feature or platform.",
        "Statuses: PASS / FAIL / BLOCKED / NOT_APPLICABLE. No unexecuted check is PASS; scope and reason accompany every row.",
        "The machine-readable [ledger](verification-ledger.json) follows the readiness skill's schema. All commands, UTC timestamps, exit codes, source identities and cleanup are in the linked original cycle receipts.", "",
        "## Gates", "", "| Gate | Result | Scope/reason | Evidence |", "|---|---|---|---|"]
for g in gates:
    refs = "; ".join(f"[{Path(x['ref']).name}]({x['ref'][len(PREFIX):]})" for x in g["evidence"])
    text.append(f"| {g['id']} | **{g['status']}** | {g['reason']} | {refs} |")
text += ["", "## Exact execution and skipped-test reconciliation", "",
         "- [Full ordinary/reproducer/harness matrix and module test counts](reviews/verification-cleanup-reconciliation-mafia-cont.md).",
         "- [New native storage cycle supplement](reviews/verification-cleanup-reconciliation-mafia-cont-supplement-01.md).",
         "- [Machine cleanup/command ledger](CLEANUP_LEDGER.json).",
         "", "Do not add counts across retries or duplicated platform executions as unique tests. The main combined test run had 2,285 descriptors: 2,284 passing and one skipped; omitted/NO-SOURCE/host-disabled cases remain explicit.",
         "Intentional failing reproducers are outside normal checked-in suites. A witness passing proves observed behavior, not a fix. Earlier audit-init, selector-hook, UI-fixture compilation, undersized-heap and native-probe launch failures are preserved, not reclassified as app crashes.",
         "", "No known defect was remediated; no release artifact, signing, real-device LAN or Store proof was generated."]
(HERE / "VERIFICATION.md").write_text("\n".join(text) + "\n")
print(json.dumps(dict(gate_counts=dict(collections.Counter(x["status"] for x in gates)), primary_cleanup=cleanup["primary_cleanup_counts"]), indent=2))
