#!/usr/bin/env python3
"""Merge actual audit read receipts; never manufacture a source-review attestation."""
import collections
import datetime
import hashlib
import json
from pathlib import Path
import runpy
import subprocess

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[1]
INVENTORY = OUT / "coverage/inventory.jsonl"


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def merged(ranges):
    result = []
    for first, last in sorted(ranges):
        if result and first <= result[-1][1] + 1:
            result[-1][1] = max(last, result[-1][1])
        else:
            result.append([first, last])
    return result


def missing(ranges, count):
    cursor = 1
    result = []
    for first, last in ranges:
        if first > cursor:
            result.append([cursor, first - 1])
        cursor = max(cursor, last + 1)
    if cursor <= count:
        result.append([cursor, count])
    return result


def applicability(row):
    path = row["path"]
    basis = ["evidence/focused-storage-01/gradle.log", "reviews/root-build-notes.md"]
    if row["kind"] in {"protected", "prior-audit-material", "generated-local"}:
        return {"role": row["kind"], "shipping": False, "basis": ["baseline.json"],
                "limitation": "Contents deliberately excluded; handling code/templates remain in scope."}
    if path.startswith("design/"):
        return {"role": "untracked-web-prototype", "shipping": False, "basis": basis,
                "limitation": "Not in Gradle/Xcode inputs; reviewed as prototype, not runtime evidence."}
    if path.startswith(("shared/engine-testing/", "shared/networking-testing/")):
        return {"role": "test-fixture-module", "shipping": False, "basis": basis}
    if path.startswith("build-logic/"):
        return {"role": "build-logic-source" if "/src/" in path else "build-logic-configuration",
                "shipping": False,
                "basis": ["build-logic/settings.gradle.kts", "build-logic/convention/build.gradle.kts"],
                "limitation": "Included Gradle convention-plugin build; executes during builds, not inside the application."}
    if path.startswith("scripts/release/tests/"):
        return {"role": "release-tooling-test", "shipping": False,
                "basis": ["scripts/release/validate_release_system.sh", "reviews/release-whodunit_cont-notes.md"],
                "limitation": "Python tests for release tooling, not application sources or Store execution evidence."}
    if "/src/" in path:
        source_set = path.split("/src/", 1)[1].split("/", 1)[0]
        is_test = "test" in source_set.lower()
        is_desktop = source_set.startswith("desktop")
        resource = "/composeResources/" in path or "/res/" in path or "/resources/" in path
        role = "test-source-or-fixture" if is_test else "resource" if resource else "production-source"
        if is_desktop and not is_test:
            role = "desktop-development-" + role
        return {"role": role, "source_set": source_set, "shipping": not (is_test or is_desktop),
                "basis": basis,
                "limitation": "Build membership is not runtime execution. Desktop is development/test only; see verification matrix."}
    if path.startswith("iosApp/"):
        if "/project.xcworkspace/" in path:
            return {"role": "xcode-workspace-metadata", "shipping": False,
                    "basis": ["iosApp/iosApp.xcodeproj/project.pbxproj"],
                    "limitation": "IDE workspace input, not an app resource or executable source."}
        if path.startswith("iosApp/iosApp/Preview Content/"):
            return {"role": "ios-debug-preview-asset", "shipping": False,
                    "basis": ["iosApp/iosApp.xcodeproj/project.pbxproj:9-16,206-226,398-458"],
                    "limitation": "Debug DEVELOPMENT_ASSET_PATHS only; absent from the app Resources phase and Release configuration."}
        is_test = path.startswith("iosApp/iosAppUITests/")
        config = path.endswith((".pbxproj", ".xcconfig", ".xcscheme", ".xcworkspacedata"))
        return {"role": "ios-ui-test" if is_test else "ios-build-configuration" if config else "ios-wrapper-or-resource",
                "shipping": not is_test and not config,
                "basis": ["iosApp/iosApp.xcodeproj/project.pbxproj", "evidence/xcode-ui-01/receipt.json"],
                "limitation": "Configuration contributes to builds but is not itself app code; test target excluded from Archive."}
    if path.startswith("docs/review/") or path == "docs/PARLOR_PROJECT_HANDOFF.md":
        return {"role": "pre-existing-review-claims", "shipping": False,
                "basis": ["reviews/review-inventory-mafia-cont.md"],
                "limitation": "Claims inspected/reconciled, never inherited as correctness or coverage evidence."}
    if path.endswith(".md"):
        return {"role": "repository-instructions" if path in {"AGENTS.md", "CLAUDE.md"} else "documentation",
                "shipping": False, "basis": ["coverage/reviews-root.jsonl"],
                "limitation": "Instructions followed; implementation claims checked against source, not presumed true."}
    if path.startswith("assets/"):
        return {"role": "design-master-asset", "shipping": False,
                "basis": ["reviews/binary-assets-wrapper-mafia-cont.md"],
                "limitation": "Packaged Android/iOS icon copies have separate ledger entries."}
    return {"role": "build-release-verification-or-repository-tooling", "shipping": False,
            "basis": basis, "limitation": "Build/release reachability only; disabled publication paths remain disabled."}


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def main():
    baseline = json.loads((OUT / "baseline.json").read_text())
    inventory = [json.loads(line) for line in INVENTORY.read_text().splitlines()]
    by_path = {row["path"]: row for row in inventory}
    receipts = collections.defaultdict(list)
    errors = []
    input_receipts = []
    audit_material_receipts = []
    # Publish this invocation's source-comparison receipt before resolving
    # audit-material versions: final-state itself has historical read receipts.
    # Resolving them first would bind a digest that this invocation overwrites.
    current_rows, current_exclusions = runpy.run_path(str(OUT / "audit_inventory.py"))["scan"]()
    current = {row["path"]: row for row in current_rows}
    changed = [path for path, row in by_path.items() if row.get("sha256") is not None and
               (path not in current or current[path].get("sha256") != row["sha256"])]
    added = sorted(set(current) - set(by_path))
    removed = sorted(set(by_path) - set(current))
    def outside_audit(status):
        return sorted(line for line in status.splitlines() if "audit-runs/" not in line)
    final_state = {"recorded_at": now(), "branch": git("branch", "--show-current"),
                   "commit": git("rev-parse", "HEAD"), "tree": git("rev-parse", "HEAD^{tree}"),
                   "tracked_status": git("status", "--porcelain=v1", "--untracked-files=no"),
                   "status_outside_audit": outside_audit(git("status", "--porcelain=v1", "--untracked-files=all")),
                   "source_hashes_compared": sum(row.get("sha256") is not None for row in inventory),
                   "changed_hashes": changed, "new_non_audit_inventory_paths": added,
                   "removed_non_audit_inventory_paths": removed,
                   "refs_unchanged": git("for-each-ref", "--format=%(refname) %(objectname)") == baseline["refs"].strip(),
                   "stashes_unchanged": git("stash", "list", "--format=%gd %H") == baseline["stashes"].strip(),
                   "baseline_untracked_listing_unchanged": outside_audit(baseline["status_including_audit_path"]) == outside_audit(git("status", "--porcelain=v1", "--untracked-files=all")),
                   "excluded_unfingerprinted_paths": [row["path"] for row in inventory if row.get("sha256") is None],
                   "protected_and_prior_material_limitation": "No initial byte hashes for deliberately excluded protected/prior-audit/local-state inputs; preserved without reading/modifying, not claimed byte-attested.",
                   "final_pruned_exclusions": current_exclusions,
                   "diff_check_exit_code": subprocess.run(["git", "diff", "--check"], cwd=ROOT, capture_output=True).returncode}
    for field in ("branch", "commit", "tree"):
        if final_state[field] != baseline[field]:
            errors.append("Changed " + field)
    if changed or added or removed or final_state["tracked_status"] or not final_state["refs_unchanged"] or not final_state["stashes_unchanged"] or not final_state["baseline_untracked_listing_unchanged"]:
        errors.append("Working-tree/inventory preservation mismatch; inspect final-state receipt")
    (OUT / "evidence/final-state.json").write_text(json.dumps(final_state, indent=2) + "\n")
    for file in sorted((OUT / "coverage").glob("reviews-*.jsonl")):
        input_receipts.append({"path": str(file.relative_to(OUT)), "sha256": digest(file)})
        for number, line in enumerate(file.read_text().splitlines(), 1):
            item = json.loads(line)
            ref = str(file.relative_to(OUT)) + ":" + str(number)
            path = item["path"]
            if path not in by_path:
                if path.startswith(str(OUT.relative_to(ROOT)) + "/"):
                    audit_file = ROOT / path
                    if OUT not in audit_file.resolve().parents or not audit_file.is_file():
                        errors.append("Invalid task-evidence receipt path: " + ref)
                    else:
                        current_digest = digest(audit_file)
                        reviewed_file = audit_file
                        if current_digest != item.get("sha256"):
                            # Mutable audit indexes can have historical read receipts.
                            # Accept only a retained byte-identical version, never infer
                            # that the prior reading attests the current index. This
                            # exception is deliberately outside baseline source logic.
                            snapshot_ref = item.get("version_snapshot_ref")
                            if not isinstance(snapshot_ref, str):
                                errors.append("Changed task-evidence read receipt: " + ref)
                                continue
                            reviewed_file = ROOT / snapshot_ref
                            if (OUT not in reviewed_file.resolve().parents or
                                    not reviewed_file.is_file() or
                                    digest(reviewed_file) != item.get("sha256")):
                                errors.append("Invalid historical task-evidence snapshot: " + ref)
                                continue
                        if item.get("status") in {"BINARY_INSPECTED", "BINARY_VISUAL_INSPECTION"}:
                            # Audit screenshots have format/visual evidence, not
                            # UTF-8 lines. They never increase source coverage.
                            method = item.get("read_method")
                            if (item.get("reviewed_ranges") or item.get("total_lines") is not None or
                                    item.get("reviewed_line_count") not in (None, 0) or
                                    not isinstance(method, str) or not method.strip()):
                                errors.append("Invalid binary task-evidence receipt: " + ref)
                        else:
                            line_count = len(reviewed_file.read_text().splitlines())
                            for first, last in item.get("reviewed_ranges", []):
                                if not 1 <= first <= last <= line_count:
                                    errors.append("Invalid task-evidence source range: " + ref)
                        audit_material_receipts.append(dict(
                            item, receipt_ref=ref,
                            verified_review_version_path=str(reviewed_file.relative_to(ROOT)),
                            current_path_sha256=current_digest,
                            attests_current_version=current_digest == item.get("sha256")))
                    continue
                errors.append("Unknown read-receipt path: " + ref)
                continue
            original = by_path[path]
            if original.get("sha256") is not None and item.get("sha256") != original["sha256"]:
                errors.append("Read receipt hash mismatch: " + ref)
                continue
            for first, last in item.get("reviewed_ranges", []):
                if original["kind"] == "text" and not 1 <= first <= last <= original["lines"]:
                    errors.append("Invalid source range: " + ref)
            item = dict(item, receipt_ref=ref)
            receipts[path].append(item)
    register = json.loads((OUT / "canonical-register.json").read_text())
    locations = collections.defaultdict(list)
    for finding in register["findings"] + register.get("unconfirmed_candidates", []):
        for location in finding["primary_source_locations"]:
            locations[location["repository_relative_path"]].append({
                "id": finding["id"], "classification": finding["classification"],
                "ranges": location["one_based_line_ranges"],
                "validation_refs": finding["validation_refs"],
            })
    output = []
    for row in inventory:
        matches = receipts[row["path"]]
        direct = []
        structured = []
        for item in matches:
            if item.get("status") == "INSPECTED_GENERATED_CSV":
                direct += item.get("raw_text_ranges_read", [])
                structured += item.get("reviewed_ranges", [])
            else:
                direct += item.get("reviewed_ranges", [])
        direct = merged(direct)
        structured = merged(structured)
        kind = row["kind"]
        absent = missing(direct, row["lines"]) if kind == "text" else []
        absent_all = missing(merged(direct + structured), row["lines"]) if kind == "text" else []
        if kind == "text":
            status = "TEXT_READ_COMPLETE_WITH_LIMITS" if not absent else (
                "GENERATED_TABLE_FULLY_STRUCTURALLY_INSPECTED" if structured and not absent_all else "REVIEW_INCOMPLETE")
        elif kind == "binary":
            status = "BINARY_INSPECTED_WITH_LIMITS" if any(x.get("status") == "BINARY_INSPECTED" for x in matches) else "REVIEW_INCOMPLETE"
        else:
            status = row["status"]
        if status == "REVIEW_INCOMPLETE":
            errors.append("Unreviewed applicable input: " + row["path"])
        evidence = sorted(set(e for item in matches for e in item.get("evidence", [])))
        cross = sorted(set(e for item in matches for e in item.get("cross_file_paths", [])))
        uncertainties = sorted(set(item["uncertainty"] for item in matches if item.get("uncertainty")))
        out = dict(row)
        out.update(status=status, applicability=applicability(row), file_extension=Path(row["path"]).suffix,
                   reviewed_ranges=direct, structured_inspection_ranges=structured,
                   unread_verbatim_ranges=absent, uninspected_in_scope_ranges=absent_all,
                   reviewers=sorted(set(item.get("reviewer", "UNSPECIFIED") for item in matches)),
                   receipt_refs=[item["receipt_ref"] for item in matches],
                   cross_file_paths=cross, evidence=evidence, findings=locations[row["path"]],
                   historical_receipt_uncertainties=uncertainties,
                   uncertainty="See per-receipt historical uncertainties and final verification/candidate ledgers. Complete text reading is not complete behavioral/platform proof.",
                   behavioral_verification_complete=False)
        output.append(out)
    (OUT / "coverage/FINAL_COVERAGE.jsonl").write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in output))
    (OUT / "coverage/AUDIT_MATERIAL_REVIEW_RECEIPTS.jsonl").write_text(
        "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in audit_material_receipts))
    graph_log = OUT / "evidence/focused-storage-01/gradle.log"
    graph = [line for line in graph_log.read_text().splitlines() if line.startswith("AUDIT_")]
    (OUT / "coverage/observed-build-graph.txt").write_text("\n".join(graph) + "\n")
    summary = {"assembled_at": now(), "source_commit": baseline["commit"], "source_tree": baseline["tree"],
               "inventory_sha256": digest(INVENTORY), "ledger_sha256": digest(OUT / "coverage/FINAL_COVERAGE.jsonl"),
               "read_receipt_inputs": input_receipts,
               "audit_material_read_receipts_excluded_from_source_counts": len(audit_material_receipts),
               "inventory_entries": len(output), "kind_counts": dict(collections.Counter(row["kind"] for row in output)),
               "disposition_counts": dict(collections.Counter(row["status"] for row in output)),
               "text_lines": sum(row.get("lines") or 0 for row in output),
               "complete_verbatim_text_file_count": sum(row["status"] == "TEXT_READ_COMPLETE_WITH_LIMITS" for row in output),
               "verbatim_read_line_count": sum(last - first + 1 for row in output if row["kind"] == "text" for first, last in row["reviewed_ranges"]),
               "structured_generated_table_line_count": sum(last - first + 1 for row in output for first, last in row["structured_inspection_ranges"]),
               "uninspected_applicable_ranges": [{"path": row["path"], "ranges": row["uninspected_in_scope_ranges"]} for row in output if row["uninspected_in_scope_ranges"]],
               "errors": errors,
               "meaning": "Finite file/range disposition only. Generated-table structural and verbatim receipts are distinct; only recorded raw ranges contribute to verbatim coverage. No behavioral/readiness percentage inferred."}
    (OUT / "coverage/summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps({k: v for k, v in summary.items() if k != "read_receipt_inputs"}, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
