#!/usr/bin/env python3
"""Complete task-owned report indexes, never modify application source or audit receipts."""
import datetime
import hashlib
import importlib.util
import json
from pathlib import Path

RUN = Path(__file__).resolve().parent
ROOT = RUN.parents[1]
FINAL = RUN / "final"
FROZEN = "e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7"
RUNNER_SHA = "d2d18c2311d59554db88be3f92e609ec5ca46fc8af4c79702836f9e4c130240f"


def read(path):
    return json.loads(path.read_text())


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def ref(path):
    path = Path(path)
    if not path.is_absolute():
        path = ROOT / path
    return {"path": str(path.relative_to(ROOT)), "sha256": sha(path)}


def write_new(name, value):
    with (FINAL / name).open("x") as out:
        json.dump(value, out, ensure_ascii=False, indent=2)
        out.write("\n")


def issue_ids(path):
    """Explicit remediation attribution, not inferred full-file audit coverage."""
    exact = {
        "composeApp/build.gradle.kts": ["DS-C01"],
        "docs/MAFIA_RULES.md": ["MF-C1"],
        "docs/PRODUCTION_ARCHITECTURE.md": ["ST-C1", "DS-C01"],
        "docs/RELEASE_AUTOMATION.md": ["RL-C1", "RL-C2", "RL-C3"],
        "game-modes/mafia/build.gradle.kts": ["MF-C1", "M-C03"],
        "scripts/release/validate_android_artifact.sh": ["RL-C1", "RL-C2"],
        "scripts/release/store_api.py": ["RL-C3"],
        "scripts/release/tests/test_store_api.py": ["RL-C3"],
        "iosApp/iosApp.xcodeproj/project.pbxproj": ["IOS-B1"],
    }
    if path in exact:
        return exact[path]
    names = {
        "ProductionUiAccessibilityContractTest.kt": ["DS-C01"],
        "IosSnapshotFileSystem.kt": ["ST-C1"],
        "IosLegacySnapshotBackupTest.kt": ["ST-C1"],
        "MafiaSnapshotRecovery.kt": ["MF-C1"],
        "MafiaMultiDevicePhaseRouter.kt": ["MF-C1"],
        "MafiaPassAndPlayPhaseRouter.kt": ["MF-C1"],
        "MafiaSetupScreen.kt": ["MF-C1"],
        "PostGameScreen.kt": ["M-C03"],
        "PostGameScreenLayoutTest.kt": ["M-C03"],
        "WhodunitStateValidator.kt": ["WD-C3"],
        "WhodunitRulesInvariantTest.kt": ["WD-C3"],
        "WhodunitFinalTwoRecoveryTest.kt": ["WD-C3"],
        "WhodunitPhaseRouter.kt": ["WD-C1"],
        "PeerReadinessRemountTest.kt": ["WD-C1"],
        "SessionStartHandshake.kt": ["SN-C1"],
        "SessionStartCommitDeadlineTest.kt": ["SN-C1"],
        "PrepareAndroidUploadTrust.java": ["RL-C2"],
        "test_android_upload_signature.py": ["RL-C2"],
        "test_android_artifact_size.py": ["RL-C1"],
        "test_google_promotion_edit.py": ["RL-C3"],
        "test_xcode_framework_phase.py": ["IOS-B1"],
        "HostDisconnectedOverlay.kt": ["DS-C03"],
        "ReconnectingOverlay.kt": ["DS-C03"],
        "ParlorButton.kt": ["DS-C03"],
        "RecoveryActionContrastTest.kt": ["DS-C03"],
        "IosLanguageOverrideOwner.kt": ["DS-C01"],
        "IosLanguageOverrideOwnerTest.kt": ["DS-C01"],
        "LocalAppLocale.ios.kt": ["DS-C01"],
        "JupiterDiscoveryContractTest.kt": ["ROOT-T3"],
        "P2pKitRoomTransportLoopbackTest.kt": ["ROOT-T3"],
        "P2pKitRoomTransportLifecycleTest.kt": ["SN-C2", "ROOT-T3"],
    }
    name = Path(path).name
    if name in names:
        return names[name]
    if "/mafia/" in path and ("Doctor" in name or path.endswith("values-ar/strings.xml")):
        return ["MF-C1"]
    if name.startswith("ParlorToast"):
        return ["DS-C02"]
    if path.startswith("shared/transport-p2p/"):
        return ["SN-C2"]
    raise ValueError("Unattributed changed file: " + path)


def relevance(path):
    if "/src/" in path:
        source_set = path.split("/src/", 1)[1].split("/", 1)[0]
        if "Test" in source_set:
            return "test-only"
        if "Resources/" in path:
            return "shipping-resource"
        return "production-source"
    if path.startswith("docs/"):
        return "documentation"
    if "/tests/" in path:
        return "release-tooling-test"
    if path.endswith(".gradle.kts") or path.endswith(".pbxproj"):
        return "build-configuration"
    return "release-tooling"


def path_evidence(path, note=None):
    item = ref(path)
    result = {"kind": "path", "ref": item["path"], "sha256": item["sha256"]}
    if note:
        result["note"] = note
    return result


def main():
    runner = RUN / "run_gradle_cycle.py"
    if sha(runner) != RUNNER_SHA:
        raise RuntimeError("Reviewed build lane has changed")
    spec = importlib.util.spec_from_file_location("lane", runner)
    lane = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(lane)
    current = lane.identity()
    if current["source_manifest_sha256"] != FROZEN:
        raise RuntimeError("Frozen source changed; indexes would be stale")
    stamp = datetime.datetime.now(datetime.timezone.utc).isoformat()
    issues = read(FINAL / "issues.json")
    diff = read(FINAL / "diff-identity.json")
    mapped = {issue["id"]: [] for issue in issues["issues"]}
    for file in diff["files"]:
        if sha(ROOT / file["path"]) != file["sha256"]:
            raise RuntimeError("Changed file drift: " + file["path"])
        count = len((ROOT / file["path"]).read_text().splitlines())
        ranges = []
        for span in file["changed_line_ranges"]:
            if isinstance(span, list):
                ranges.append({"start": span[0], "end": span[1], "kind": "new-file"})
            else:
                ranges.append({"start": span["start"], "end": span["start"] + max(1, span["length"]) - 1,
                               "kind": "changed-lines" if span["length"] else "deletion-boundary"})
        if any(span["start"] < 1 or span["end"] > count for span in ranges):
            raise RuntimeError("Out-of-bounds location: " + file["path"])
        ids = issue_ids(file["path"])
        entry = {"path": file["path"], "absolute_path": file["absolute_path"], "sha256": file["sha256"],
                 "line_count": count, "relevance": relevance(file["path"]), "change": file["type"],
                 "location_ranges": ranges, "shared_with_issues": ids if len(ids) > 1 else []}
        for issue_id in ids:
            mapped[issue_id].append(entry)
    for file in read(RUN / "whodunit/wd-c2-unmodified-resources.json")["files"]:
        path = Path(file["path"])
        if sha(path) != file["sha256"]:
            raise RuntimeError("Blocked story was edited")
        mapped["WD-C2"].append({"path": str(path.relative_to(ROOT)), "absolute_path": str(path),
                                "sha256": file["sha256"], "line_count": file["lines"],
                                "relevance": "shipping-resource", "change": "unchanged-blocked",
                                "location_ranges": [{"start": a, "end": b, "kind": "confirmed-manifestation"}
                                                    for a, b in file["reviewed_ranges"]]})
    write_new("issue-source-map.json", {
        "recorded_at": stamp, "source_manifest_sha256": FROZEN,
        "tracked_diff_sha256": current["diff_sha256"], "complete_patch": diff["complete_patch"],
        "note": "One-based locations, not a new line-by-line review claim. Shared files show all modified context; "
                "causal paths, exact reviewed ranges and reviewers are in each linked independent dossier. "
                "Raw resource hashes are not canonical content-identity digests.",
        "changed_files_attributed": len(diff["files"]),
        "issues": [{"id": issue["id"], "status": issue["status"], "files": mapped[issue["id"]],
                    "author": "/root" if issue["fix_author"] == "/root/root" else issue["fix_author"],
                    "independent_reviewer": issue["independent_reviewer"],
                    "independent_evidence": issue["independent_evidence"]} for issue in issues["issues"]],
    })

    gates = []
    def gate(id_, status, reason, evidence, owner=None):
        value = {"id": id_, "status": status, "reason": reason,
                 "evidence": [path_evidence(x) for x in evidence]}
        if owner:
            value["owner"] = owner
        gates.append(value)

    for issue in issues["issues"]:
        passed = issue["status"] == "FIXED AND VERIFIED"
        refs = [x["path"] for x in issue["independent_evidence"]]
        refs += [x["path"] for x in issue["fresh_regression_xml"]]
        if issue["release_python_evidence"]:
            refs.append(issue["release_python_evidence"]["path"])
        gate("remediation-" + issue["id"].lower(), "PASS" if passed else "BLOCKED",
             issue["independent_conclusion"] + ". Limits: " + issue["evidence_limits"], refs,
             None if passed else ("content author / repository owner" if issue["id"] == "WD-C2"
                                 else "implementation agent (local app-host verification); owner (legacy preference policy)"))
    prod = RUN / "evidence/combined-production-04"
    native = RUN / "evidence/native-alltests-01"
    apple = RUN / "evidence/combined-apple-01"
    gate("production-check", "PASS", "Configured host-independent aggregate actually executed: 1372 JVM/Android passes, "
         "3 physical skips; 172 release Python tests; 43 Detekt XML reports clear. 32 policy-accepted lint warnings remain.",
         [prod / "receipt.json", RUN / "release_fix_review/combined-production-evidence-04.json"])
    gate("configured-alltests-and-native-runtime", "PASS", "2458 passes + 3 physical skips: 1220 Desktop, 426 actual iOS "
         "simulator, 406 Android debug-unit, 406 Android release-unit. 11 native modules, 13 host-disabled x64 tasks. "
         "Mafia/engine native tests are NO-SOURCE. Not all platforms/devices.",
         [native / "receipt.json", RUN / "native_fix_review/native-final-verification-01.json"])
    gate("apple-release-frameworks-and-analysis", "PASS", "Three fresh Release framework links, 4 actual iOS-main "
         "Detekt reports clear; 113 NO-SOURCE analysis tasks; zero runtime tests in this cycle. Xcode not Store-qualified.",
         [apple / "receipt.json", RUN / "release_fix_review/combined-apple-evidence-01.json"])
    gate("en-ar-structural-resource-parity", "PASS", "Keys/types/empty values/placeholders: 139 shell, 323 Whodunit, "
         "269 Mafia and 16 design-system per locale. Not editorial or accessibility proof.",
         [RUN / "evidence/final-resource-checks-01/receipt.json", RUN / "evidence/final-resource-checks-01/gradle.log"])
    gate("tracked-diff-whitespace", "PASS", "git diff --check passed for tracked changes. New source additions are "
         "separately hashed and included in the complete patch.", [RUN / "evidence/final-resource-checks-01/gradle.log"])
    gate("frozen-source-and-original-work-preservation", "PASS", "658-file source manifest and complete62-file patch "
         "identify dirty checkout; 1618 original files retained, no pre-existing untracked changes, refs/stash/index preserved.",
         [FINAL / "source-identity.json", FINAL / "diff-identity.json", FINAL / "preservation.json"])
    gate("scoped-cross-game-authority-and-compatibility", "PASS", "Additional independent bounded cross-game/session "
         "reviews found no new scoped blocker. Pure host authority, own-private projection and exact4.2 rules retained; "
         "this is not a new exhaustive whole-repository audit.",
         [RUN / "factory_review/final-cross-game-review-01.json", RUN / "factory_review/final-session-cross-check-01.json"])
    gate("combined-cycle-resource-cleanup", "PASS", "Latest completed Gradle cycles stop daemons immediately, collect "
         "compact evidence, delete owned build/scratch outputs, remove owned simulator and report no survivors. "
         "Post-index final observation is recorded separately.", [prod / "receipt.json", native / "receipt.json", apple / "receipt.json"])

    release_gates = ROOT / "docs/RELEASE_GATES.md"
    root_build = ROOT / "build.gradle.kts"
    blocked = [
        ("ios-real-app-language-lifecycle", "Actual killed/relaunched app EN/AR/System matrix, live UIKit/Compose direction "
         "and session continuity not yet executed. New evidence-only owned-simulator harness under construction; not inherently external.",
         "implementation agent", [RUN / "native_fix_review/native-final-verification-01.json"]),
        ("ios-swift-apphost-and-wrapper", "Actual Swift app-launch and unsigned Swift Release wrapper not rerun this remediation. "
         "Archived apphost runner must first have secondary-worker temporary-path ownership/cleanup repaired and reviewed.",
         "implementation agent", [release_gates, RUN / "native_fix_review/native-final-verification-01.json"]),
        ("android-managed-runtime", "Pinned Linux/KVM/x86_64 managed-device runtime not available on current Apple Silicon host. "
         "Desktop and Android unit tests are not the R8-shrunk Android runtime gate.", "CI/repository owner", [release_gates, root_build]),
        ("other-desktop-host-graphs", "Linux x64/arm64, macOS x64 and Windows x64 dependency/runtime graphs not executed by this "
         "macOS arm64 run.", "CI/repository owner", [release_gates]),
        ("physical-p2pkit-lan", "Three physical tests remain skipped and have historical admission assumptions. No two-device "
         "host/peer, denial/rejoin, radio or lifecycle evidence provided by in-memory tests.", "device validation owner", [release_gates,
         RUN / "native_fix_review/native-final-verification-01.json"]),
        ("native-accessibility-layout-and-privacy", "Native VoiceOver/TalkBack, compact/large-text RTL, app-switcher and physical "
         "lifecycle matrix not executed. Desktop composable tests only cover their declared cases.", "device/accessibility owner", [release_gates]),
        ("physical-ios-backup-and-keychain", "Backup resource-flag tests are not real backup/restore or app-host Keychain migration "
         "evidence. IOS-R1 remains an evidence gap, not a new confirmed defect.", "device validation owner", [release_gates,
         RUN / "native_fix_review/independent-review.json"]),
        ("qualified-apple-toolchain", "Installed Xcode26.5/17F42 differs from pinned Store-qualified26.3/17C529.", "release owner",
         [ROOT / "config/release-policy.json", RUN / "release_fix_review/combined-apple-evidence-01.json"]),
        ("real-store-signing-and-submission", "No authority to read/use private Store keys, sign Store candidates, upload, promote "
         "or publish. Local synthetic certificate tests do not satisfy this gate.", "release owner", [release_gates]),
        ("store-identity-ownership", "Known com.parlor.app identity collision unresolved; disabled publication and identity checks "
         "are deliberately preserved.", "repository/release owner", [ROOT / "config/release-policy.json",
         ROOT / ".github/workflows/testing-candidate.yml", ROOT / ".github/workflows/production-promotion.yml"]),
        ("legal-and-store-declarations", "Owner approval of content rights, licenses, privacy declarations, metadata and Store setup "
         "not independently established by tests.", "product/legal/release owner", [release_gates]),
        ("immutable-integrated-release-candidate", "Authorized changes are intentionally uncommitted. No commit/merge authorization; "
         "base SHA/tree alone cannot identify a clean reviewed release candidate.", "repository owner", [FINAL / "preservation.json"]),
        ("wd-c4-modal-timer-policy", "Outside16 repairs: whether Leave-confirmation modal time counts as discussion time is unresolved; "
         "no policy or code change invented.", "product owner", [ROOT / "audit-runs/2026-09-05-source-audit/canonical-register.json"]),
    ]
    for id_, reason, owner, evidence in blocked:
        gate(id_, "BLOCKED", reason, evidence, owner)
    artifacts = []
    for directory in (prod, apple):
        for artifact in read(directory / "artifact-receipts.json"):
            artifacts.append({"name": artifact["path"], "sha256": artifact["sha256"], "bytes": artifact["bytes"],
                              "source_commit": current["commit"], "source_tree": current["tree"],
                              "source_manifest_sha256": FROZEN, "tracked_diff_sha256": current["diff_sha256"],
                              "complete_patch_sha256": diff["complete_patch"]["sha256"],
                              "evidence": ref(directory / "artifact-receipts.json"),
                              "disposition": "Unsigned/linkage inspection only; generated binary deleted after inspection; "
                                             "not an approved Store candidate or runtime proof."})
    write_new("gates.json", {
        "schema_version": 1, "recorded_at": stamp, "scope": "Scoped remediation plus applicable remaining release/evidence gates",
        "candidate": {"commit": current["commit"], "tree": current["tree"], "clean": False, "branch": current["branch"],
                      "source_manifest_sha256": FROZEN, "tracked_diff_sha256": current["diff_sha256"],
                      "complete_patch_sha256": diff["complete_patch"]["sha256"], "not_a_release_candidate": True},
        "verdict": "NOT_READY", "gates": gates, "artifacts": artifacts,
        "manual_actions": [
            {"id": "chronology-and-compatibility", "owner": "content author/repository owner", "blocking": True,
             "action": "Answer the four WD-C2 chronology questions and choose an explicit content-version/save compatibility policy."},
            {"id": "legacy-ios-language-provenance", "owner": "product owner", "blocking": True,
             "action": "Choose user-directed recovery policy for pre-marker AppleLanguages values indistinguishable from OS preferences; never clear silently."},
            {"id": "local-ios-apphost-verification", "owner": "implementation agent", "blocking": True,
             "action": "Complete separately reviewed evidence-only harness cleanup prerequisite and actual-app restart/language checks; report exact remaining limits."},
            {"id": "physical-and-external-gates", "owner": "repository/device/release/legal owners", "blocking": True,
             "action": "Complete the explicit physical, alternate-host, signing/identity/Store/legal gates without bypassing publication disablement."},
        ],
    })
    print(json.dumps({"created": ["issue-source-map.json", "gates.json"], "source_manifest_sha256": FROZEN,
                      "mapped_changed_files": len(diff["files"]), "gate_count": len(gates), "verdict": "NOT_READY"}, indent=2))


if __name__ == "__main__":
    main()
