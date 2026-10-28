"""Fail-closed join of isolated free-hosted Apple Release lanes.

Outputs identify current source and retained evidence, not cached build products.
The original required check succeeds only if all three lanes succeed, including
their existing source-bound native retirement and output-cleanup controls.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import sys
from pathlib import Path

from scripts.ci import verification_hygiene as hygiene

LANES = ("ios-release-simulator", "ios-release-device", "ios-release-x64")
FIELDS = ("source_sha", "source_tree", "evidence_id", "evidence_sha256", "cleanup_sha256")


def read_json(path: Path) -> dict:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 1024 * 1024:
        raise RuntimeError("Missing, redirected or excessive Apple receipt")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise RuntimeError("Apple receipt must be an object")
    return value


def lane_identity(root: Path, prefix: Path, task: dict) -> dict:
    if task.get("GITHUB_JOB") not in LANES:
        raise RuntimeError("Unknown Apple Release lane")
    source = hygiene.source_identity(root)
    claim = read_json(Path(str(prefix) + "-ownership.json"))
    cleanup_path = Path(str(prefix) + "-cleanup.json")
    cleanup = read_json(cleanup_path)
    if (claim.get("task") != task or
            any(claim.get(key) != source[key] for key in ("root", "head", "tree", "outputs")) or
            cleanup.get("task") != task or cleanup.get("result") != "PASS" or
            cleanup.get("errors") != [] or cleanup.get("retained") != [] or
            cleanup.get("preparation_outcome") != "success" or
            cleanup.get("gradle_stop", {}).get("exit_code") != 0 or
            hygiene.git(root, "status", "--porcelain").strip()):
        raise RuntimeError("Apple lane source or final cleanup is not qualified")
    cycles = hygiene.apple_cycles(task)
    if set(cleanup.get("apple_cleanup", {})) != set(cycles):
        raise RuntimeError("Apple lane cleanup omitted an assigned cycle")
    for cycle in cycles:
        receipt = read_json(Path(str(prefix) + "-stop-" + cycle + ".json"))
        if (receipt.get("task") != task or receipt.get("result") != "PASS" or
                receipt.get("source") != {key: source[key] for key in ("root", "head", "tree")} or
                receipt.get("prepare_outcome") != "success" or receipt.get("run_outcome") != "success" or
                receipt.get("errors") != [] or
                cleanup["apple_cleanup"][cycle] != {
                    "result": "PASS",
                    "sha256": hashlib.sha256(Path(str(prefix) + "-stop-" + cycle + ".json").read_bytes()).hexdigest(),
                }):
            raise RuntimeError("Apple lane has an unsuccessful native cycle")
    upload = cleanup.get("upload", {})
    if not hygiene.uploaded_evidence_is_retained(upload):
        raise RuntimeError("Apple lane evidence was not retained")
    return dict(source_sha=source["head"], source_tree=source["tree"], evidence_id=upload["artifact_id"],
                evidence_sha256=upload["artifact_digest"], cleanup_sha256=hashlib.sha256(cleanup_path.read_bytes()).hexdigest())


def aggregate(needs: dict, head: str, tree: str) -> dict:
    if not isinstance(needs, dict) or set(needs) != set(LANES):
        raise RuntimeError("Apple Release requires exactly three architectures")
    for lane in LANES:
        value = needs[lane]
        if not isinstance(value, dict) or value.get("result") != "success":
            raise RuntimeError("Apple Release lane failed, skipped or cancelled: " + lane)
        outputs = value.get("outputs")
        if not isinstance(outputs, dict) or set(outputs) != set(FIELDS):
            raise RuntimeError("Incomplete Apple lane custody: " + lane)
        if outputs["source_sha"] != head or outputs["source_tree"] != tree:
            raise RuntimeError("Apple lane source differs from triggering checkout: " + lane)
        for field in ("evidence_sha256", "cleanup_sha256"):
            if not isinstance(outputs[field], str) or not re.fullmatch(r"[0-9a-f]{64}", outputs[field]):
                raise RuntimeError("Invalid Apple lane digest: " + lane)
        if not isinstance(outputs["evidence_id"], str) or not re.fullmatch(r"[1-9][0-9]*", outputs["evidence_id"]):
            raise RuntimeError("Invalid Apple lane artifact ID: " + lane)
    if len({needs[lane]["outputs"]["evidence_id"] for lane in LANES}) != len(LANES):
        raise RuntimeError("Apple lanes cannot reuse another lane's artifact")
    return {"schema": 1, "result": "PASS", "source_sha": head, "source_tree": tree, "lanes": needs}


def main() -> int:
    if sys.argv[1:] == ["lane"]:
        root, prefix, task = hygiene.context()
        identity = lane_identity(root, prefix, task)
        with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
            for key, value in identity.items():
                output.write(key + "=" + value + "\n")
        print(json.dumps(identity))
    elif sys.argv[1:] == ["aggregate"]:
        if os.environ.get("GITHUB_JOB") != "ios-release":
            raise RuntimeError("Aggregate runs only in the original required gate")
        root = Path(os.environ["GITHUB_WORKSPACE"]).resolve(strict=True)
        source = hygiene.source_identity(root)
        if hygiene.git(root, "status", "--porcelain").strip():
            raise RuntimeError("Aggregate source is not clean")
        raw = os.environ["PARLOR_APPLE_RELEASE_NEEDS"]
        if len(raw) > 65536:
            raise RuntimeError("Excessive aggregate input")
        value = aggregate(json.loads(raw), source["head"], source["tree"])
        value.update(run_id=os.environ["GITHUB_RUN_ID"], run_attempt=os.environ["GITHUB_RUN_ATTEMPT"])
        for key in ("run_id", "run_attempt"):
            if not re.fullmatch(r"[1-9][0-9]*", value[key]):
                raise RuntimeError("Invalid aggregate run binding")
        destination = Path(os.environ["RUNNER_TEMP"]) / (
            "parlor-apple-release-" + value["run_id"] + "-" + value["run_attempt"] + ".json")
        hygiene.write_new(destination, value)
        print(json.dumps(value))
    else:
        raise RuntimeError("Expected lane or aggregate")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
