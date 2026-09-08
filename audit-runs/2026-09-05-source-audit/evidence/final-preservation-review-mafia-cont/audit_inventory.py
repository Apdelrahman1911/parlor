#!/usr/bin/env python3
"""Task-local inventory/evidence tooling; never mutates application files."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
PRUNED = {".git", ".gradle", ".kotlin", ".idea", "build", "DerivedData", "Pods", "node_modules", "__pycache__", "xcuserdata"}
PROTECTED_PREFIXES = ("release/private", ".mobile-release")
PROTECTED_SUFFIXES = (".keystore", ".jks", ".p12", ".p8", ".mobileprovision", ".pem", ".key")

def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT).decode()

def stamp():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()

def protected(path):
    return path == "local.properties" or path.startswith(PROTECTED_PREFIXES) or path.endswith(PROTECTED_SUFFIXES) or Path(path).name.startswith(".env")

def relevance(path):
    if protected(path): return "protected-local-input"
    if path.startswith("project-code-audit/") or path == "docs/PARLOR_PROJECT_HANDOFF.md" or path.startswith("docs/review/"):
        return "pre-existing-review-material-not-evidence"
    if path.startswith("design/"): return "untracked-web-prototype-build-reachability-pending"
    if "/src/" in path:
        source_set = path.split("/src/", 1)[1].split("/", 1)[0]
        if path.startswith(("shared/engine-testing/", "shared/networking-testing/")): return "test-fixture"
        if "test" in source_set.lower(): return "test"
        if "composeResources/" in path or "/res/" in path: return "resource"
        return "production-source-graph-verification-pending"
    if path.startswith("iosApp/"): return "apple-wrapper-resource-or-configuration"
    if path.startswith((".github/", "scripts/", "build-logic/", "gradle/", "config/", "release/", ".run/")) or path.endswith((".gradle.kts", ".gradle", ".properties")) or path in ("gradlew", "gradlew.bat", ".gitignore"):
        return "build-verification-release-or-tooling"
    if path.endswith(".md"): return "documentation-claim-not-proof"
    return "asset-or-other-applicability-pending"

def scan():
    tracked = set(git("ls-files", "-z").rstrip("\0").split("\0"))
    untracked = set(git("ls-files", "--others", "--exclude-standard", "-z").rstrip("\0").split("\0"))
    paths = set(tracked) | set(untracked)
    exclusions = []
    for base, dirs, files in os.walk(ROOT, followlinks=False):
        rel = Path(base).relative_to(ROOT).as_posix()
        for d in list(dirs):
            p = d if rel == "." else rel + "/" + d
            if d in PRUNED or p.startswith(PROTECTED_PREFIXES) or p.startswith("audit-runs/"):
                dirs.remove(d)
                exclusions.append({"path": p, "reason": "protected or existing generated/local state; not opened"})
        for f in files:
            p = f if rel == "." else rel + "/" + f
            if not p.startswith("audit-runs/"): paths.add(p)
    rows = []
    for p in sorted(paths):
        if not p or p.startswith("audit-runs/"): continue
        file = ROOT / p
        row = {"path": p, "absolute_path": str(file), "git_kind": "tracked" if p in tracked else "untracked" if p in untracked else "ignored", "relevance": relevance(p), "reviewed_ranges": [], "reviewers": [], "cross_file_paths": [], "evidence": [], "uncertainty": "Not yet reviewed in this audit", "status": "NOT_REVIEWED"}
        if protected(p):
            row.update(status="PROTECTED_EXCLUSION", uncertainty="Contents not inspected; audit handling through code/templates", kind="protected", sha256=None, lines=None)
        elif file.is_symlink():
            row.update(kind="symlink", target=os.readlink(file), sha256=None, lines=None)
        elif file.is_file():
            if p.startswith("project-code-audit/"):
                row.update(kind="prior-audit-material", sha256=None, lines=None, status="EXCLUDED_PRIOR_EVIDENCE", uncertainty="Preserved; not reused for review coverage or correctness")
            elif file.name == ".DS_Store" or p.endswith((".pyc", ".klib")):
                row.update(kind="generated-local", sha256=None, lines=None, status="EXCLUDED_LOCAL_STATE", uncertainty="Preserved; not source")
            else:
                data = file.read_bytes()
                try:
                    text = data.decode("utf-8")
                    is_text = "\0" not in text
                except UnicodeDecodeError:
                    is_text = False
                row.update(kind="text" if is_text else "binary", sha256=hashlib.sha256(data).hexdigest(), bytes=len(data), lines=len(data.splitlines()) if is_text else None)
        else:
            row.update(kind="missing", sha256=None, lines=None)
        rows.append(row)
    return rows, exclusions

def initialize():
    for name in ("coverage", "evidence", "candidates", "reviews", "reproducers", "assignments"):
        (OUT/name).mkdir(exist_ok=True)
    if (OUT/"baseline.json").exists(): raise SystemExit("Refusing to replace initial baseline")
    rows, exclusions = scan()
    baseline = {"recorded_at": stamp(), "root": str(ROOT), "branch": git("branch", "--show-current").strip(), "commit": git("rev-parse", "HEAD").strip(), "tree": git("rev-parse", "HEAD^{tree}").strip(), "status_including_audit_path": git("status", "--porcelain=v1", "--untracked-files=all"), "refs": git("for-each-ref", "--format=%(refname) %(objectname)"), "stashes": git("stash", "list", "--format=%gd %H"), "exclusions": exclusions, "source_identity": "Commit/tree PLUS immutable inventory hashes for tracked/untracked files; protected local inputs not fingerprinted"}
    (OUT/"baseline.json").write_text(json.dumps(baseline, indent=2)+"\n")
    (OUT/"coverage/inventory.jsonl").write_text("".join(json.dumps(r)+"\n" for r in rows))
    assignments = {"whodunit_content": [], "mafia_engine": [], "session_network": [], "root": []}
    for row in rows:
        p = row["path"]
        if p.startswith(("game-modes/whodunit/", "shared/content/")): group="whodunit_content"
        elif p.startswith(("game-modes/mafia/", "shared/core/", "shared/engine/", "shared/engine-testing/")): group="mafia_engine"
        elif p.startswith(("shared/session/", "shared/networking/", "shared/networking-testing/", "shared/transport-p2p/")): group="session_network"
        else: group="root"
        assignments[group].append(p)
    for group, paths in assignments.items():
        (OUT/f"assignments/{group}.txt").write_text("\n".join(paths)+"\n")
    print(json.dumps({"files": len(rows), "text_lines": sum(r.get("lines") or 0 for r in rows), "assignments": {k:len(v) for k,v in assignments.items()}, "out": str(OUT)}, indent=2))

def record():
    # Input must describe lines actually read by the named reviewer, not a scan.
    entry = json.load(sys.stdin)
    row = next(r for r in map(json.loads, (OUT/"coverage/inventory.jsonl").read_text().splitlines()) if r["path"] == entry["path"])
    current = hashlib.sha256((ROOT/entry["path"]).read_bytes()).hexdigest()
    if current != row["sha256"]: raise SystemExit("Source differs from baseline; record drift first")
    entry.update(sha256=current, timestamp=stamp())
    for first,last in entry.get("reviewed_ranges", []):
        if not 1 <= first <= last <= row["lines"]: raise SystemExit("Invalid line range")
    dest=OUT/"coverage"/("reviews-"+entry["reviewer"].replace("/", "_")+".jsonl")
    with dest.open("a") as stream: stream.write(json.dumps(entry)+"\n")
    print(f"Recorded {entry['path']}: {entry.get('reviewed_ranges', [])}")

if __name__ == "__main__":
    if len(sys.argv) != 2: raise SystemExit("initialize | record")
    if sys.argv[1] == "initialize": initialize()
    elif sys.argv[1] == "record": record()
    else: raise SystemExit("Unknown command")
