"""Admit this four-case stock baseline, never Parlor qualification."""
import json
import sys
from pathlib import Path

CASES = {
    "StockInputUITests/test01FirstArabic()",
    "StockInputUITests/test02FirstEnglish()",
    "StockInputUITests/test03PostDoneArabic()",
    "StockInputUITests/test04PostDoneEnglish()",
}

def verify(summary, tests, udid, test_exit):
    assert summary["totalTestCount"] == 4
    assert summary["skippedTests"] == summary["expectedFailures"] == 0
    devices = tests["devices"]
    assert len(devices) == len(summary["devicesAndConfigurations"]) == 1
    device = devices[0]
    assert summary["devicesAndConfigurations"][0]["device"] == device
    assert (device["deviceId"], device["architecture"], device["modelName"],
            device["osVersion"], device["osBuildNumber"], device["platform"]) == (
                udid, "arm64", "iPhone 17 Pro", "26.2", "23C54", "iOS Simulator")
    rows = []
    def visit(nodes):
        for node in nodes:
            if node.get("nodeType") == "Test Case":
                rows.append((node["nodeIdentifier"], node["result"]))
            visit(node.get("children", []))
    visit(tests["testNodes"])
    assert len(rows) == 4 and {name for name, _ in rows} == CASES
    assert all(result in ("Passed", "Failed") for _, result in rows)
    passed = sum(result == "Passed" for _, result in rows)
    failed = 4 - passed
    assert (summary["passedTests"], summary["failedTests"]) == (passed, failed)
    assert (summary["result"], test_exit) == (
        ("Passed", 0) if failed == 0 else ("Failed", 65))
    config = summary["devicesAndConfigurations"][0]
    assert (config["passedTests"], config["failedTests"],
            config["skippedTests"], config["expectedFailures"]) == (passed, failed, 0, 0)
    return dict(rows)

if __name__ == "__main__":
    out = Path(sys.argv[1])
    results = verify(json.loads((out / "summary.json").read_text()),
                     json.loads((out / "tests.json").read_text()),
                     sys.argv[2], int((out / "test.exit").read_text()))
    print(json.dumps({"stock_cases": results, "parlor_qualified": False}, sort_keys=True))
