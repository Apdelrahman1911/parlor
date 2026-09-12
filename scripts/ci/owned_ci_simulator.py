"""Journal-first ownership for a fresh simulator, never a pre-existing profile.

All subprocesses are invoked only by the explicit CI entry point. Tests inject a
synthetic backend; importing this module does not contact CoreSimulator.
"""
from __future__ import annotations

import json
import re
import subprocess
import time
from pathlib import Path

from scripts.ci.verification_hygiene import timestamp, write_new

UUID = re.compile(r"[0-9A-Fa-f]{8}(?:-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}")
MAX_DEVICES = 256
MAX_RECORD = 1024 * 1024


def read_record(path: Path) -> dict:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_RECORD:
        raise RuntimeError("Missing, redirected, or oversized ownership record")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise RuntimeError("Invalid ownership record")
    return value


def checked_uuid(value: object) -> str:
    if not isinstance(value, str) or not UUID.fullmatch(value):
        raise RuntimeError("Invalid simulator UUID")
    return value.upper()


def devices(inventory: dict) -> list[dict]:
    groups = inventory.get("devices")
    if not isinstance(groups, dict):
        raise RuntimeError("Invalid simulator inventory")
    result, seen = [], set()
    for runtime, entries in groups.items():
        if not isinstance(runtime, str) or not isinstance(entries, list):
            raise RuntimeError("Invalid simulator runtime inventory")
        for item in entries:
            if not isinstance(item, dict):
                raise RuntimeError("Invalid simulator device record")
            udid = checked_uuid(item.get("udid"))
            if udid in seen or len(seen) >= MAX_DEVICES:
                raise RuntimeError("Duplicate or excessive simulator inventory")
            seen.add(udid)
            result.append({**item, "udid": udid, "runtime": runtime})
    return result


class SimctlBackend:
    def run(self, *args: str) -> tuple[int, str]:
        # Fixed, trusted executable/arguments only. The child is waited/reaped on
        # both normal completion and timeout; no fire-and-forget subprocess.
        result = subprocess.run(["/usr/bin/xcrun", "simctl", *args], text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                timeout=90, check=False)
        if len(result.stdout) > MAX_RECORD or len(result.stderr) > MAX_RECORD:
            raise RuntimeError("Oversized simctl response")
        return result.returncode, result.stdout

    def inventory(self) -> dict:
        code, output = self.run("list", "devices", "--json")
        if code != 0:
            raise RuntimeError("simctl inventory failed")
        value = json.loads(output)
        if not isinstance(value, dict):
            raise RuntimeError("Invalid simctl inventory")
        return value


def paths(prefix: Path) -> tuple[Path, Path]:
    return Path(str(prefix) + "-simulator-plan.json"), Path(str(prefix) + "-simulator-create.json")


def adoption_path(prefix: Path) -> Path:
    return Path(str(prefix) + "-simulator-adopted.json")


def create(prefix: Path, claim: dict, backend) -> str:
    plan_path, result_path = paths(prefix)
    if any(path.exists() or path.is_symlink() for path in (*paths(prefix), adoption_path(prefix))):
        raise RuntimeError("Simulator creation cannot reuse a historical journal")
    inventory = devices(backend.inventory())
    # Preserve the previous runtime/device-type selection, not its user profile.
    template = next((item for item in inventory if item.get("isAvailable") is True and
                     str(item.get("name", "")).startswith("iPhone") and
                     item["runtime"].startswith("com.apple.CoreSimulator.SimRuntime.iOS-") and
                     str(item.get("deviceTypeIdentifier", "")).startswith(
                         "com.apple.CoreSimulator.SimDeviceType.iPhone-")), None)
    if template is None:
        raise RuntimeError("No available compatible iPhone runtime/device type")
    name = "Parlor-ci-" + claim["nonce"]
    if any(item.get("name") == name for item in inventory):
        raise RuntimeError("Refusing an existing simulator name")
    plan = {"task": claim["task"], "cycle": claim["cycle"], "nonce": claim["nonce"],
            "name": name, "runtime": template["runtime"],
            "device_type": template["deviceTypeIdentifier"],
            "baseline": sorted(item["udid"] for item in inventory), "planned_at": timestamp()}
    # Exclusive, fsynced intent precedes the first resource-creating command.
    write_new(plan_path, plan)
    code, output = backend.run("create", name, plan["device_type"], plan["runtime"])
    result = {"task": claim["task"], "nonce": claim["nonce"], "exit_code": code,
              "finished_at": timestamp(), "udid": None}
    if code == 0:
        try:
            result["udid"] = checked_uuid(output.strip())
        except RuntimeError:
            pass  # Keep an explicit failed receipt; finalizer can inspect the intent.
    write_new(result_path, result)
    if code != 0 or result["udid"] is None:
        raise RuntimeError("Simulator create did not return a valid successful receipt")
    device = owned_device(plan, result, devices(backend.inventory()))
    if device is None:
        raise RuntimeError("Created simulator is absent from the authoritative inventory")
    return device["udid"]


def owned_device(plan: dict, result: dict | None, inventory: list[dict]) -> dict | None:
    baseline = plan.get("baseline")
    if (not isinstance(baseline, list) or len(baseline) > MAX_DEVICES or
            len(baseline) != len(set(checked_uuid(value) for value in baseline))):
        raise RuntimeError("Invalid pre-create simulator baseline")
    expected = result.get("udid") if result is not None else None
    if expected is not None:
        expected = checked_uuid(expected)
        if expected in baseline:
            raise RuntimeError("Returned simulator UUID predates this task")
    matches = [item for item in inventory if item.get("name") == plan.get("name")]
    if len(matches) > 1:
        raise RuntimeError("Ambiguous journaled simulator name")
    if expected is not None and any(item["udid"] == expected for item in inventory) and not matches:
        raise RuntimeError("Owned simulator UUID no longer has its journaled name")
    if not matches:
        if expected is None:
            raise RuntimeError("Unsettled create outcome: no owned simulator UUID attested")
        return None  # A fully identified owned device already removed is idempotent.
    device = matches[0]
    if (device["udid"] in baseline or device["runtime"] != plan.get("runtime") or
            device.get("deviceTypeIdentifier") != plan.get("device_type") or
            expected is not None and expected != device["udid"]):
        raise RuntimeError("Simulator identity disagrees with pre-create journal")
    return device


def cleanup(prefix: Path, claim: dict, backend, sleep=time.sleep) -> dict:
    plan_path, result_path = paths(prefix)
    adopted_path = adoption_path(prefix)
    if not plan_path.exists() and not plan_path.is_symlink():
        if any(path.exists() or path.is_symlink() for path in (result_path, adopted_path)):
            raise RuntimeError("Creation receipt without pre-create journal")
        return {"result": "NOT_CREATED", "reason": "No create intent was written"}
    plan = read_record(plan_path)
    if any(plan.get(key) != claim[key] for key in ("task", "cycle", "nonce")):
        raise RuntimeError("Simulator journal belongs to another cycle")
    if plan.get("name") != "Parlor-ci-" + claim["nonce"]:
        raise RuntimeError("Simulator journal name is not task-owned")
    result = read_record(result_path) if result_path.exists() or result_path.is_symlink() else None
    if result is not None and any(result.get(key) != claim[key] for key in ("task", "nonce")):
        raise RuntimeError("Simulator creation receipt belongs to another cycle")
    if result is not None and (type(result.get("exit_code")) is not int or
                               result.get("udid") is not None and result["exit_code"] != 0):
        raise RuntimeError("Invalid simulator creation outcome receipt")
    adopted = read_record(adopted_path) if adopted_path.exists() or adopted_path.is_symlink() else None
    if adopted is not None:
        checked_uuid(adopted.get("udid"))
        if (any(adopted.get(key) != claim[key] for key in ("task", "cycle", "nonce")) or
                result is not None and result.get("udid") not in (None, adopted.get("udid"))):
            raise RuntimeError("Adopted simulator identity conflicts with its creation journal")
    expected = adopted if adopted is not None else result
    inventory = devices(backend.inventory())
    # Parallel Xcode cloning is disabled. An unexpected clone is not authorized
    # by a name heuristic: preserve evidence and require investigation instead.
    if any(item["udid"] not in plan["baseline"] and item.get("name") != plan["name"] and
           plan["name"] in str(item.get("name", "")) for item in inventory):
        raise RuntimeError("Unexpected unjournaled simulator clone; no deletion authorized")
    device = owned_device(plan, expected, inventory)
    if device is None:
        return {"result": "PASS", "udid": expected["udid"], "already_absent": True}
    udid, actions = device["udid"], []
    if adopted is None:
        # Persist independently verified UUID before the first shutdown/delete.
        # This is not a fabricated successful create exit code: an interrupted
        # create can be adopted by its unique pre-create intent and inventory.
        write_new(adopted_path, {"task": claim["task"], "cycle": claim["cycle"],
                                "nonce": claim["nonce"], "udid": udid, "adopted_at": timestamp()})
    if device.get("state") != "Shutdown":
        code, _ = backend.run("shutdown", udid)
        actions.append({"command": "shutdown", "exit_code": code})
        for _ in range(20):
            device = owned_device(plan, {"udid": udid}, devices(backend.inventory()))
            if device is None or device.get("state") == "Shutdown":
                break
            sleep(0.25)
        else:
            raise RuntimeError("Owned simulator did not reach Shutdown")
        # A shutdown racing an already completed shutdown may return nonzero;
        # only the exact current state, never that error alone, authorizes delete.
    if device is not None:
        if device.get("state") != "Shutdown":
            raise RuntimeError("Refusing deletion of an active simulator")
        code, _ = backend.run("delete", udid)
        actions.append({"command": "delete", "exit_code": code})
        if owned_device(plan, {"udid": udid}, devices(backend.inventory())) is not None:
            raise RuntimeError("Owned simulator remains after delete")
        if code != 0:
            raise RuntimeError("Simulator deletion returned failure; inspect retained journal")
    return {"result": "PASS", "udid": udid, "actions": actions,
            "recovered_from_intent": result is None or result.get("udid") is None}
