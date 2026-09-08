#!/usr/bin/env python3
"""Generate the independent-review inventory from Git's tracked file set.

The CSV is intentionally deterministic: review-infrastructure rows do not
embed their own commit SHA, avoiding a self-referential generated-file diff.
"""

from __future__ import annotations

import csv
import io
import re
import subprocess
import sys
from pathlib import Path


BASELINE = "9cd4040a81c4f2f8fe6f5f161dabcd5351682c02"
DEFAULT_OUTPUT = "docs/review/INDEPENDENT_REVIEW_INVENTORY.csv"
FINDING_OVERRIDES = "docs/review/INDEPENDENT_REVIEW_FINDING_OVERRIDES.csv"

HISTORICAL_DOCUMENTS = {
    "ARCHITECTURE.md",
    "PROBLEMS_PARLOR.md",
    "whodunit-game-design.md",
    "docs/APP_PLAN.md",
    "docs/DESIGN_TOKENS.md",
    "docs/FR_REMEDIATION_FINDINGS.md",
    "docs/MOCK_BACKEND.md",
    "docs/MOTION_DOWNGRADE.md",
    "docs/P2P_REMEDIATION_PLAN.md",
    "docs/PARLOR_P2P_SMOKE_TEST.md",
    "docs/PHASE_0_VALIDATION.md",
    "docs/PHASE_8_VALIDATION.md",
    "docs/PROGRESS.md",
}

REVIEW_INFRASTRUCTURE = {
    "scripts/generate_review_inventory.py",
    "docs/review/README.md",
    FINDING_OVERRIDES,
    DEFAULT_OUTPUT,
}

MODULE_CONSUMERS = {
    ":shared:core": "all shared modules, both game modules, and app composition",
    ":shared:engine": "session/content layers, both games, and app registries",
    ":shared:session": "both game runtimes and app-owned multiplayer composition",
    ":shared:networking": "session coordinators, P2pKit adapter, games, and app UI",
    ":shared:storage": "session/game recovery and platform app storage bindings",
    ":shared:transport-p2p": "app composition and transport contract tests only",
    ":shared:design-system": "both game UIs and the app shell",
    ":shared:content": "Whodunit content pipeline, start protocol, and app composition",
    ":shared:engine-testing": "test source sets only",
    ":shared:networking-testing": "test source sets only",
    ":game-modes:whodunit": "registered app-shell binding and Whodunit runtime",
    ":game-modes:mafia": "registered app-shell binding and Mafia runtime",
    ":composeApp": "Android/iOS launchers and Desktop development runtime",
    "included-build:convention": "all Gradle subprojects applying Parlor conventions",
    "iosApp": "Xcode build, Swift wrapper, and iOS packaging",
    "repository": "contributors, CI, release operators, or repository tooling",
}


def run_git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return result.stdout


def repository_root() -> Path:
    return Path(
        subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        ).stdout.strip(),
    )


def module_for(path: str) -> str:
    parts = path.split("/")
    if parts[0] == "composeApp":
        return ":composeApp"
    if len(parts) > 1 and parts[0] == "shared":
        return f":shared:{parts[1]}"
    if len(parts) > 1 and parts[0] == "game-modes":
        return f":game-modes:{parts[1]}"
    if path.startswith("build-logic/convention/"):
        return "included-build:convention"
    if path.startswith("iosApp/"):
        return "iosApp"
    return "repository"


def source_set_for(path: str) -> str:
    match = re.search(r"/src/([^/]+)/", path)
    if match:
        return match.group(1)
    if ".lproj/" in path:
        return "ios-localized-resource"
    if path.startswith("iosApp/") and path.endswith(".swift"):
        return "ios-app"
    if "/Assets.xcassets/" in path:
        return "ios-asset-catalog"
    if path.startswith(".github/"):
        return "ci"
    if path.startswith("docs/") or path.endswith(".md"):
        return "documentation"
    return "repository"


def classification_for(path: str, source_set: str) -> str:
    suffix = Path(path).suffix.lower()
    if path in REVIEW_INFRASTRUCTURE or path.startswith("docs/review/"):
        return "review-evidence"
    if path in HISTORICAL_DOCUMENTS:
        return "historical-document"
    if path.startswith(".github/workflows/"):
        return "ci-workflow"
    if path == ".github/dependabot.yml":
        return "dependency-automation-config"
    if path.startswith(".run/"):
        return "developer-run-config"
    if path.startswith("build-logic/") and suffix == ".kt":
        return "build-logic-source"
    if path.startswith("iosApp/iosApp/Preview Content/"):
        return "ios-preview-resource"
    if path.startswith("iosApp/") and suffix == ".swift":
        return "production-source"
    if source_set.lower().endswith("test"):
        return "test-source" if suffix in {".kt", ".swift"} else "test-resource"
    if source_set.lower().endswith("main") and suffix in {".kt", ".swift"}:
        return "production-source"
    if path.endswith("AndroidManifest.xml"):
        return "android-manifest"
    if path.endswith("Info.plist") or path.endswith("PrivacyInfo.xcprivacy"):
        return "ios-privacy-or-manifest"
    if (
        "/iosApp.xcodeproj/" in path
        or path.endswith("project.pbxproj")
        or suffix in {".xcconfig", ".xcscheme", ".xcworkspacedata"}
    ):
        return "apple-build-config"
    if path.startswith("gradle/verification-metadata") or path.startswith("config/"):
        return "verification-config"
    if path == "gradle/libs.versions.toml":
        return "dependency-catalog"
    if path.startswith("gradle/wrapper/") or path in {"gradlew", "gradlew.bat"}:
        return "gradle-wrapper"
    if path.endswith(".gradle.kts") or path == "gradle.properties":
        return "gradle-build-config"
    if path.startswith("iosApp/") and suffix in {".strings", ".json", ".png", ".plist"}:
        return "production-resource"
    if (
        "/composeResources/" in path
        or "/res/" in path
        or "/Assets.xcassets/" in path
        or ".lproj/" in path
    ):
        return "production-resource"
    if path.startswith("assets/"):
        return "source-artwork"
    if suffix == ".md":
        return "operational-document"
    if path.endswith("proguard-rules.pro"):
        return "android-shrinker-config"
    if path == ".gitignore":
        return "repository-config"
    return "repository-tooling-or-config"


def reachability_for(path: str, source_set: str, classification: str, module: str) -> str:
    if module in {":shared:engine-testing", ":shared:networking-testing"}:
        return "NON-RUNTIME: test fixture module; absent from shipping runtime graphs"
    if classification == "production-source":
        if path.startswith("iosApp/"):
            return "SHIPPING IOS: compiled into the Swift application wrapper"
        if source_set.startswith("android"):
            return "SHIPPING ANDROID: compiled into the Android app"
        if source_set.startswith("ios"):
            return "SHIPPING IOS: compiled into the Kotlin iOS framework"
        if source_set.startswith("desktop"):
            return "DEVELOPMENT: Desktop-only runtime; not a mobile shipping target"
        return "SHIPPING SHARED: compiled for Android and iOS; also used by Desktop tests/dev"
    if classification == "production-resource":
        if source_set.startswith("android"):
            return "SHIPPING ANDROID: packaged Android resource"
        if source_set.startswith("ios") or path.startswith("iosApp/"):
            return "SHIPPING IOS: packaged iOS source/resource"
        return "SHIPPING SHARED: packaged through Compose Multiplatform resources"
    if classification == "android-manifest":
        return "SHIPPING ANDROID: merged into the release manifest"
    if classification == "ios-privacy-or-manifest":
        return "SHIPPING IOS: copied into the app bundle"
    if classification in {"test-source", "test-resource"}:
        return "NON-RUNTIME: executed or loaded only by automated test tasks"
    if classification == "source-artwork":
        return "NON-RUNTIME MASTER: source for derived app/store artwork"
    if classification in {"historical-document", "operational-document", "review-evidence"}:
        return "NON-RUNTIME: review/release/contributor evidence"
    if classification == "developer-run-config":
        return "DEVELOPMENT-ONLY: IDE convenience configuration"
    if classification == "ios-preview-resource":
        return "DEVELOPMENT-ONLY: SwiftUI/Xcode preview resource"
    if classification == "ci-workflow":
        return "CI-ONLY: production qualification workflow"
    return "BUILD-TIME OR REPOSITORY-ONLY: not loaded by the app at runtime"


def consumers_for(path: str, classification: str, module: str) -> str:
    if classification == "android-manifest":
        return "Android manifest merger, lint, R8/AAB packaging, and Play review"
    if classification == "ios-privacy-or-manifest":
        return "Xcode bundle assembly, iOS runtime declarations, and App Store review"
    if classification == "ci-workflow":
        return "GitHub Actions protected-branch production verification"
    if classification in {"historical-document", "operational-document", "review-evidence"}:
        return "reviewers, contributors, release operators, and contract tests"
    if classification in {"test-source", "test-resource"}:
        return f"{module} test tasks and aggregate production checks"
    if classification == "production-resource" and "/cases/" in path:
        return "BundledWhodunitCases, content validators, Whodunit picker/runtime, and packaging"
    if classification == "source-artwork":
        return "launcher/store asset derivation and visual release review"
    return MODULE_CONSUMERS.get(module, MODULE_CONSUMERS["repository"])


def disposition_for(classification: str, source_set: str) -> str:
    if classification == "historical-document":
        return "RETAIN AS HISTORICAL; non-authoritative status banner verified"
    if classification == "review-evidence":
        return "RETAIN AND REGENERATE after every tracked review change"
    if classification in {"test-source", "test-resource"}:
        return "RETAIN as automated regression/release evidence"
    if classification == "developer-run-config":
        return "RETAIN as developer-only tooling; excluded from shipping artifacts"
    if classification == "ios-preview-resource":
        return "RETAIN for previews; exclude from qualified release behavior"
    if classification == "source-artwork":
        return "RETAIN master; derived asset/device/store visual checks remain external"
    if source_set.startswith("desktop"):
        return "RETAIN for development/test parity; exclude from mobile artifacts"
    return "RETAIN in the current production, build, test, or release contract"


def last_changes(
    root: Path,
    baseline: str = BASELINE,
) -> dict[str, tuple[str, str]]:
    log = run_git(
        root,
        "log",
        "--no-merges",
        "--format=@@@%H%x09%s",
        "--name-only",
        f"{baseline}..HEAD",
    )
    current: tuple[str, str] | None = None
    changes: dict[str, tuple[str, str]] = {}
    for raw in log.splitlines():
        line = raw.strip()
        if line.startswith("@@@"):
            commit, subject = line[3:].split("\t", 1)
            current = (commit, subject)
        elif line and current is not None and line not in changes:
            changes[line] = current
    return changes


def finding_overrides(root: Path) -> dict[str, str]:
    path = root / FINDING_OVERRIDES
    with path.open(encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source)
        if reader.fieldnames != ["commit_sha", "finding"]:
            raise ValueError(f"Invalid finding override header: {path}")
        overrides: dict[str, str] = {}
        for row in reader:
            commit = row["commit_sha"].strip()
            finding = row["finding"].strip()
            if not re.fullmatch(r"[0-9a-f]{40}", commit):
                raise ValueError(f"Finding override must use a full commit SHA: {commit}")
            if not finding:
                raise ValueError(f"Finding override must not be empty: {commit}")
            if commit in overrides:
                raise ValueError(f"Duplicate finding override: {commit}")
            overrides[commit] = finding
    return overrides


def finding_for(
    path: str,
    changes: dict[str, tuple[str, str]],
    overrides: dict[str, str],
) -> str:
    if path in REVIEW_INFRASTRUCTURE or path.startswith("docs/review/"):
        return "Review infrastructure; no product finding"
    change = changes.get(path)
    if change is None:
        return "None identified in the independent review"
    commit, subject = change
    override = overrides.get(commit)
    if override is not None:
        return override
    return f"See findings register; latest review remediation {commit[:7]} ({subject})"


def tracked_paths(root: Path, output_relative: str) -> list[str]:
    tracked = set(run_git(root, "ls-files", "--cached").splitlines())
    tracked.add(output_relative)
    return sorted(path for path in tracked if path)


def render_inventory(
    paths: list[str],
    changes: dict[str, tuple[str, str]],
    overrides: dict[str, str],
) -> str:
    rendered = io.StringIO(newline="")
    writer = csv.writer(rendered, lineterminator="\n")
    writer.writerow(
        [
            "path",
            "module",
            "source_set",
            "classification",
            "production_reachability",
            "main_callers_or_consumers",
            "reviewer_status",
            "findings",
            "final_disposition",
        ],
    )
    for path in paths:
        module = module_for(path)
        source_set = source_set_for(path)
        classification = classification_for(path, source_set)
        writer.writerow(
            [
                path,
                module,
                source_set,
                classification,
                reachability_for(path, source_set, classification, module),
                consumers_for(path, classification, module),
                "REVIEWED",
                finding_for(path, changes, overrides),
                disposition_for(classification, source_set),
            ],
        )
    return rendered.getvalue()


def inventory_content(
    root: Path,
    output_relative: str,
    baseline: str = BASELINE,
) -> tuple[list[str], str]:
    paths = tracked_paths(root, output_relative)
    changes = last_changes(root, baseline)
    return paths, render_inventory(paths, changes, finding_overrides(root))


def main() -> int:
    root = repository_root()
    arguments = sys.argv[1:]
    check_only = "--check" in arguments
    positional = [argument for argument in arguments if argument != "--check"]
    if len(positional) > 1:
        raise SystemExit("Usage: generate_review_inventory.py [--check] [output.csv]")
    output_arg = positional[0] if positional else DEFAULT_OUTPUT
    output = (root / output_arg).resolve()
    try:
        output_relative = output.relative_to(root).as_posix()
    except ValueError as error:
        raise SystemExit(f"Output must be inside the repository: {output}") from error

    paths, content = inventory_content(root, output_relative)

    if check_only:
        if not output.is_file() or output.read_text(encoding="utf-8") != content:
            print(
                f"Inventory is stale: run scripts/generate_review_inventory.py "
                f"and review {output_relative}",
                file=sys.stderr,
            )
            return 1
        print(f"Verified {len(paths)} reviewed rows in {output_relative}")
    else:
        output.parent.mkdir(parents=True, exist_ok=True)
        temporary = output.with_suffix(output.suffix + ".tmp")
        with temporary.open("w", encoding="utf-8", newline="") as destination:
            destination.write(content)
        temporary.replace(output)
        print(f"Wrote {len(paths)} reviewed rows to {output_relative}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
