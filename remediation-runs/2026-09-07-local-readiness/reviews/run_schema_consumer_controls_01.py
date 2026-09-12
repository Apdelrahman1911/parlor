#!/usr/bin/env python3
"""Root-lane-only synthetic controls with ephemeral, hash-pinned format parsers.

No pip/global installation, Gradle, Git, device, publishing or candidate export.
The outer reviewed run_gradle_cycle.py owns stop/process/scratch finalization.
"""
import ast
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import importlib
import importlib.metadata
import io
import json
import os
from pathlib import Path, PurePosixPath
import signal
import stat
import sys
import tempfile
import unittest
import urllib.request
import zipfile


HERE = Path(__file__).resolve().parent
CAMPAIGN = HERE.parent
CONSUMER = HERE / "dependency-candidate-consumer-03"
CONTROLS = {
    "candidate_consumer.py": "eb6e2baaac22739b4652bc63c6b57a24374ee57fb81d4711b981b872e8ac1c7f",
    "test_candidate_consumer.py": "92eef687dbf037b310b00c9b678a5718d95cf5e8313094861c398554755a3535",
}
# Official PyPI byte identities recorded in schema-optional-*-research-01.json.
WHEELS = (
    ("rfc3987-syntax", "1.1.0", "rfc3987_syntax",
     "https://files.pythonhosted.org/packages/7e/71/44ce230e1b7fadd372515a97e32a83011f906ddded8d03e3c6aafbdedbb7/rfc3987_syntax-1.1.0-py3-none-any.whl",
     8046, "6c3d97604e4c5ce9f714898e05401a0445a641cfa276432b0a648c80856f6a3f"),
    ("lark", "1.2.2", "lark",
     "https://files.pythonhosted.org/packages/2d/00/d90b10b962b4277f5e64a78b6609968859ff86889f5b898c1a778c06ec00/lark-1.2.2-py3-none-any.whl",
     111036, "c2276486b02f0f1b90be155f2c8ba4a8e194d42775786db622faccd652d8e80c"),
)


def require(ok, reason):
    if not ok:
        raise ValueError(reason)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise ValueError("Public tool redirect refused")


def control_hashes():
    result = {}
    for name, digest in CONTROLS.items():
        path = CONSUMER / name
        require(not path.is_symlink() and path.is_file(), "Control input type")
        result[name] = sha(path.read_bytes())
        require(result[name] == digest, "Control input drift")
    return result


@contextmanager
def optional_checkers(parent, report):
    """Only extracts reviewed pure-Python members into a new outer-lane child."""
    require(not any(name in sys.modules for name in ("jsonschema", "lark", "rfc3987_syntax")),
            "Format libraries must load after owned parser setup")
    temporary = tempfile.TemporaryDirectory(prefix="schema-parsers-", dir=parent)
    owned = Path(temporary.name).resolve(strict=True)
    identity = owned.stat()
    old_path = sys.path[:]
    try:
        packages = []
        opener = urllib.request.build_opener(NoRedirect())
        for distribution, version, package, url, size, digest in WHEELS:
            with opener.open(url, timeout=30) as response:
                require(response.status == 200 and response.geturl() == url, "Public tool origin")
                raw = response.read(size + 1)
            require(len(raw) == size and sha(raw) == digest, "Public tool bytes")
            members, total = [], 0
            with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                require(len(archive.infolist()) <= 100, "Public tool entry bound")
                for item in archive.infolist():
                    name = item.filename
                    parts = name.split("/")
                    require(0 < len(name) <= 240 and not name.startswith("/")
                            and not any(c in name for c in ("\\", ":", "\x00"))
                            and all(p not in ("", ".", "..") for p in parts)
                            and PurePosixPath(name).parts[0] in
                            (package, package + "-" + version + ".dist-info"), "Public tool path")
                    mode = item.external_attr >> 16
                    require(stat.S_IFMT(mode) in (0, stat.S_IFREG) and not item.is_dir()
                            and 0 <= item.file_size <= 1024 * 1024, "Public tool member type/size")
                    total += item.file_size
                    require(total <= 2 * 1024 * 1024, "Public tool expansion bound")
                    data = archive.read(item)
                    require(len(data) == item.file_size, "Public tool member size")
                    path = owned.joinpath(*parts)
                    path.parent.mkdir(parents=True, exist_ok=True)
                    with path.open("xb") as stream:
                        stream.write(data)
                    members.append([name, len(data), sha(data)])
            packages.append({"distribution": distribution, "version": version, "url": url,
                             "bytes": size, "sha256": digest, "uncompressed_bytes": total,
                             "members": members})
        report["ephemeral_packages"] = packages
        sys.path.insert(0, str(owned))
        importlib.invalidate_caches()
        expected = {"jsonschema": "4.25.1", "referencing": "0.36.2",
                    **{name: version for name, version, *_ in WHEELS}}
        report["versions"] = {name: importlib.metadata.version(name) for name in expected}
        require(report["versions"] == expected, "Schema prerequisite version")
        for _, _, name, *_ in WHEELS:
            module = importlib.import_module(name)
            path = Path(module.__file__).resolve(strict=True)
            require(owned in path.parents, "Schema parser not from owned scratch")
        yield
    finally:
        sys.path[:] = old_path
        after = owned.lstat()
        require(stat.S_ISDIR(after.st_mode) and
                (after.st_dev, after.st_ino, after.st_uid) ==
                (identity.st_dev, identity.st_ino, identity.st_uid), "Parser cleanup ownership")
        temporary.cleanup()
        report["ephemeral_parser_cleanup"] = not owned.exists()


def flattened(suite):
    for item in suite:
        if isinstance(item, unittest.TestSuite):
            yield from flattened(item)
        else:
            yield item


def main():
    require(len(sys.argv) == 1, "This control driver accepts no free-form input")
    tmp = Path(os.environ["TMPDIR"]).absolute()
    require(tmp.resolve(strict=True) == tmp and tmp.name == "tmp" and tmp.parent.name == "scratch"
            and tmp.parents[2] == CAMPAIGN / "evidence", "Coordinated owned TMPDIR required")
    destination = tmp.parents[1] / "schema-consumer-controls.json"
    require(not destination.exists(), "Existing control receipt")
    report = {"schema_version": 1, "status": "FAIL", "started_at": datetime.now(timezone.utc).isoformat(),
              "driver_sha256": sha(Path(__file__).read_bytes()), "scope": "Synthetic controls, not candidate evidence"}
    code = 1
    try:
        report["controls_before"] = control_hashes()
        parsed = ast.parse((CONSUMER / "test_candidate_consumer.py").read_text())
        expected = sorted("test_candidate_consumer.CandidateConsumerTests." + n.name
                          for n in ast.walk(parsed) if isinstance(n, ast.FunctionDef) and n.name.startswith("test_"))
        require(len(expected) == 16 and len(set(expected)) == 16, "Control signature count")
        with optional_checkers(tmp, report):
            suite = unittest.defaultTestLoader.discover(str(CONSUMER), pattern="test_*.py")
            report["discovered_ids"] = sorted(test.id() for test in flattened(suite))
            require(report["discovered_ids"] == expected, "Control discovery differs from AST")
            result = unittest.TextTestRunner(verbosity=2).run(suite)
            report.update(tests_run=result.testsRun, failures=[test.id() for test, _ in result.failures],
                          errors=[test.id() for test, _ in result.errors], skipped=[test.id() for test, _ in result.skipped])
            require(result.wasSuccessful() and result.testsRun == 16 and not result.skipped,
                    "Synthetic controls did not all execute successfully")
        report["controls_after"] = control_hashes()
        require(report["ephemeral_parser_cleanup"] is True, "Parser cleanup incomplete")
        report["status"] = "PASS_SYNTHETIC_CONTROLS"
        code = 0
    except BaseException as error:
        report["error_type"] = type(error).__name__
    finally:
        report["finished_at"] = datetime.now(timezone.utc).isoformat()
        with destination.open("x") as stream:
            json.dump(report, stream, indent=2)
            stream.write("\n")
        print(json.dumps({key: report[key] for key in ("status", "error_type", "tests_run", "ephemeral_parser_cleanup")
                          if key in report}), flush=True)
    return code


if __name__ == "__main__":
    def interrupted(signum, frame):
        raise KeyboardInterrupt("Owned schema controls interrupted")
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    raise SystemExit(main())
