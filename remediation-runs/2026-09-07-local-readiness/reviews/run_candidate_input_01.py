#!/usr/bin/env python3
"""Root-lane candidate consumer with the already-reviewed ephemeral checkers.

No export, build, signing, global install, or publication. The outer lane owns
process/Gradle/output cleanup. This adapter adds no schema or receipt waiver.
"""
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import signal
import sys


HERE = Path(__file__).resolve().parent
CAMPAIGN = HERE.parent
ROOT = CAMPAIGN.parents[1]
HELPER = HERE / "run_schema_consumer_controls_01.py"
HELPER_SHA = "78222a79d0d19cdce28077506ce1fd74481cc419fdf9dce9e9e5b6fd8840336f"
CONSUMER = HERE / "dependency-candidate-consumer-03/candidate_consumer.py"
CONSUMER_SHA = "eb6e2baaac22739b4652bc63c6b57a24374ee57fb81d4711b981b872e8ac1c7f"


def checked_module(path, expected, name):
    if (path.is_symlink() or path.resolve(strict=True) != path or not path.is_file()
            or hashlib.sha256(path.read_bytes()).hexdigest() != expected):
        raise ValueError("Unreviewed candidate-execution control")
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    if len(sys.argv) != 3 or re.fullmatch(r"[a-f0-9]{64}", sys.argv[2]) is None:
        raise ValueError("Expected explicit repository-relative binding and reviewed SHA256")
    tmp = Path(os.environ["TMPDIR"]).absolute()
    if (tmp.resolve(strict=True) != tmp or tmp.name != "tmp" or tmp.parent.name != "scratch"
            or tmp.parents[2] != CAMPAIGN / "evidence"):
        raise ValueError("Coordinated owned TMPDIR required")
    destination = tmp.parents[1]
    report_path = destination / "candidate-prerequisites.json"
    output = destination / "candidate-input-verification.json"
    if report_path.exists() or report_path.is_symlink() or output.exists() or output.is_symlink():
        raise ValueError("Refusing to overwrite evidence")
    report = dict(schema_version=1, status="FAIL", started_at=datetime.now(timezone.utc).isoformat(),
                  driver_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  helper_sha256=HELPER_SHA, consumer_sha256=CONSUMER_SHA,
                  scope="Fresh bound candidate input consumer; no final-binary or legal approval")
    code = 1
    try:
        helper = checked_module(HELPER, HELPER_SHA, "parlor_candidate_checkers")
        consumer = checked_module(CONSUMER, CONSUMER_SHA, "parlor_candidate_consumer")
        helper.control_hashes()
        # Reuse the exact hash-pinned, ownership-checked helper. Its main()
        # synthetic-test entry point is deliberately never called here.
        with helper.optional_checkers(tmp, report):
            code = consumer.main(["--root", str(ROOT), "--binding", sys.argv[1],
                                  "--binding-sha256", sys.argv[2], "--output", str(output.relative_to(ROOT))])
        if report.get("ephemeral_parser_cleanup") is not True:
            raise RuntimeError("Owned parser cleanup incomplete")
        helper.control_hashes()
        for path, expected in ((HELPER, HELPER_SHA), (CONSUMER, CONSUMER_SHA)):
            if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
                raise RuntimeError("Candidate-execution control changed")
        report["consumer_exit_code"] = code
        if code == 0:
            report["status"] = "PASS_SCOPED_CANDIDATE_INPUT_EXECUTION"
    except BaseException as error:
        report["error_type"] = type(error).__name__
        code = 1
    finally:
        report["finished_at"] = datetime.now(timezone.utc).isoformat()
        with report_path.open("x") as stream:
            json.dump(report, stream, indent=2)
            stream.write("\n")
        print(json.dumps({key: report[key] for key in ("status", "error_type", "consumer_exit_code",
                                                     "ephemeral_parser_cleanup") if key in report}), flush=True)
    return code


if __name__ == "__main__":
    def interrupted(_signum, _frame):
        raise KeyboardInterrupt("Owned candidate verification interrupted")
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    raise SystemExit(main())
