#!/usr/bin/env python3
"""Additive source/patch evidence; never stage or modify application inputs."""
import datetime
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess

RUN = Path(__file__).resolve().parents[1]
ROOT = RUN.parents[1]
PRIOR = ROOT / "remediation-runs/2026-09-05-confirmed-fixes/final"
DEST = RUN / "final"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_new(name, value):
    path = DEST / name
    with path.open("x") as output:
        json.dump(value, output, indent=2, ensure_ascii=False)
        output.write("\n")


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def file_ref(path):
    return {"path": str(path.relative_to(ROOT)), "sha256": digest(path)}


def relevance(path):
    if path.startswith("docs/"):
        return "documentation"
    if "composeResources/" in path:
        return "shipped-resource"
    if "Test/" in path or "Tests/" in path or "/tests/" in path or "iosTest/" in path:
        return "test"
    if path.endswith("build.gradle.kts") or ".xcodeproj/" in path:
        return "build-configuration"
    if path.startswith("scripts/"):
        return "release-validation-tooling"
    if path.startswith("shared/networking-testing/"):
        return "non-shipping-test-fixture"
    return "production-source"


def main():
    spec = importlib.util.spec_from_file_location("lane", RUN / "run_gradle_cycle.py")
    lane = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(lane)
    source = json.loads(json.dumps(lane.identity()))
    frozen = json.loads((RUN / "source-freeze-02.json").read_text())
    if source != frozen["source"]:
        raise SystemExit("Source drift: refuse to describe another tree as the verified freeze02")
    if any(path.exists() or path.is_symlink() for path in lane.owned_outputs()):
        raise SystemExit("Generated output ownership must be resolved before final materialization")
    current = dict(source["source_manifest"])
    baseline = json.loads((RUN / "baseline.json").read_text())
    previous = json.loads((PRIOR / "diff-identity.json").read_text())
    new_phase_paths = set(current) - dict(baseline["source_identity"]["source_manifest"]).keys()
    tracked_paths = set(git("diff", "--name-only", "HEAD", "--").decode().splitlines())
    paths = {entry["path"] for entry in previous["files"]} | new_phase_paths | tracked_paths
    if paths - current.keys():
        raise SystemExit("A changed path is outside the protected source inventory")
    tracked = set(git("ls-files", "-z").decode().rstrip("\0").split("\0"))
    added_paths = sorted(paths - tracked)
    patch = git("diff", "--no-ext-diff", "--binary", "HEAD", "--", *sorted(paths & tracked))
    files = []
    for rel in sorted(paths):
        path = ROOT / rel
        if path.is_symlink() or not path.is_file() or digest(path) != current[rel]:
            raise SystemExit("Unstable or unsafe source input: " + rel)
        if rel in tracked:
            delta = git("diff", "--no-ext-diff", "--unified=0", "HEAD", "--", rel).decode()
            ranges = [{"start": int(match[0]), "length": int(match[1] or 1)}
                      for match in re.findall(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@", delta, re.M)]
            kind = "modified"
        else:
            result = subprocess.run(["git", "diff", "--no-ext-diff", "--binary", "--no-index",
                                     "--", "/dev/null", rel], cwd=ROOT, capture_output=True)
            if result.returncode != 1 or not result.stdout:
                raise SystemExit("Unable to capture new source patch: " + rel)
            patch += result.stdout
            ranges = [{"start": 1, "length": len(path.read_text().splitlines())}]
            kind = "new"
        files.append({"path": rel, "absolute_path": str(path), "sha256": current[rel],
                      "type": kind, "relevance": relevance(rel),
                      "line_count": len(path.read_text().splitlines()), "changed_line_ranges": ranges,
                      "note": "One-based changed locations, not a new full-file audit-coverage claim."})
    DEST.mkdir(exist_ok=True)
    with (DEST / "remediation.patch").open("xb") as output:
        output.write(patch)
    at = datetime.datetime.now(datetime.timezone.utc).isoformat()
    write_new("source-identity.json", {"recorded_at": at, "repository": str(ROOT), **source,
              "tools": frozen["tools"], "freeze": file_ref(RUN / "source-freeze-02.json"),
              "note": "Dirty and untracked source is intentional. HEAD/tree alone do not identify these repairs."})
    write_new("diff-identity.json", {"recorded_at": at,
              "source_manifest_sha256": source["source_manifest_sha256"],
              "tracked_diff_sha256": source["diff_sha256"],
              "complete_patch": file_ref(DEST / "remediation.patch"),
              "modified_tracked_files": len(paths & tracked), "new_source_files": len(added_paths),
              "scope": "All16 authorized repairs and tests; preserved pre-existing AGENTS/audit/design/handoff are not additions in this patch.",
              "files": files})
    previous_map = json.loads((PRIOR / "issue-source-map.json").read_text())
    mapping = {entry["id"]: {file["path"] for file in entry["files"]}
               for entry in previous_map["issues"]}
    mapping["DS-C01"].update({
        "docs/PRE_RELEASE_COMPATIBILITY.md", "iosApp/iosApp/ContentView.swift",
        "iosApp/iosApp/ComposeContainerViewController.swift",
        "iosApp/iosAppUITests/ComposeContainerViewControllerTests.swift",
        "iosApp/iosApp.xcodeproj/project.pbxproj",
        "shared/networking-testing/src/commonMain/kotlin/com/parlor/networking/testing/ControlledStartRoom.kt",
        "game-modes/mafia/src/desktopTest/kotlin/com/parlor/games/mafia/ui/flow/multidevice/MafiaHostLanguageContinuityTest.kt",
        "game-modes/whodunit/src/desktopTest/kotlin/com/parlor/games/whodunit/ui/flow/multiplayer/WhodunitHostLanguageContinuityTest.kt",
    })
    mapping["WD-C2"].update({
        "docs/PRE_RELEASE_COMPATIBILITY.md", "docs/WHODUNIT_TEST_CONTENT.md", "docs/PRODUCTION_ARCHITECTURE.md",
        "game-modes/whodunit/src/commonMain/composeResources/values/strings.xml",
        "game-modes/whodunit/src/commonMain/composeResources/values-ar/strings.xml",
        "game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitGameFlow.kt",
        *{"game-modes/whodunit/src/desktopTest/kotlin/com/parlor/games/whodunit/" + suffix for suffix in (
            "content/TestingStoryChronologyTest.kt", "content/TestingStoryCompatibilityTest.kt",
            "content/TestingStoryGameTraceTest.kt", "content/WhodunitContentIdentityTest.kt",
            "snapshot/WhodunitCaseBindingTest.kt", "snapshot/WhodunitResumeReconstructionTest.kt",
            "ui/flow/WhodunitRecoveryInteractionTest.kt")},
    })
    unattributed = paths - set().union(*mapping.values())
    if unattributed:
        raise SystemExit("Unattributed patch files: " + str(sorted(unattributed)))
    files_by_path = {entry["path"]: entry for entry in files}
    write_new("issue-source-map.json", {"recorded_at": at,
              "source_manifest_sha256": source["source_manifest_sha256"],
              "tracked_diff_sha256": source["diff_sha256"], "complete_patch": file_ref(DEST / "remediation.patch"),
              "changed_files_attributed": len(paths),
              "note": "Locations identify current changes, not claimed rereading. Causal paths/reviewed ranges remain in independent dossiers. See issues.json for completion.",
              "issues": [{"id": issue, "files": [dict(files_by_path[rel], shared_with_issues=[
                  other for other, values in mapping.items() if other != issue and rel in values])
                  for rel in sorted(values)]} for issue, values in mapping.items()]})
    print(json.dumps({"files": len(paths), "tracked_modified": len(paths & tracked),
                      "new": len(added_paths), "patch": file_ref(DEST / "remediation.patch"),
                      "source_manifest_sha256": source["source_manifest_sha256"]}, indent=2))


if __name__ == "__main__":
    main()
