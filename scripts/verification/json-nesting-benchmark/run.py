#!/usr/bin/env python3
"""Offline, bounded JVM microbenchmark. Does not run Gradle or alter dependency trust."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import tempfile
import tomllib


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--kotlin-version", help="Explicit diagnostic override; defaults to catalog compiler/stdlib")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    repository = args.repository.resolve()
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
    catalog = tomllib.loads((repository / "gradle/libs.versions.toml").read_text())
    serialization = catalog["versions"]["kotlinx-serialization"]
    artifacts = []

    def jar(group, name, version):
        matches = list((cache / group / name / version).glob(f"*/{name}-{version}.jar"))
        if len(matches) != 1:
            raise RuntimeError(f"Require exactly one already-cached {group}:{name}:{version}; found {len(matches)}")
        path = matches[0]
        artifacts.append({"coordinate": f"{group}:{name}:{version}", "sha256": sha256(path)})
        return str(path)

    kotlin = args.kotlin_version or catalog["versions"]["kotlin"]
    stdlib = jar("org.jetbrains.kotlin", "kotlin-stdlib", kotlin)
    runtime = [stdlib, jar("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm", serialization),
               jar("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm", serialization)]
    compiler = [jar("org.jetbrains.kotlin", name, kotlin) for name in (
        "kotlin-compiler-embeddable", "kotlin-build-tools-api", "kotlin-script-runtime", "kotlin-daemon-embeddable")]
    # Compiler dependencies below match the reviewed Kotlin 2.4.0 and 2.4.10 POMs.
    # Different compiler lines must review/update this diagnostic driver explicitly.
    if kotlin not in ("2.4.0", "2.4.10"):
        raise RuntimeError("Only reviewed compilers 2.4.0/2.4.10 are supported; no implicit fallback")
    compiler += [stdlib, jar("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"),
                 jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"),
                 jar("org.jetbrains", "annotations", "13.0")]
    guard = repository / "shared/core/src/commonMain/kotlin/com/parlor/core/serialization/JsonNestingLimit.kt"
    harness = Path(__file__).with_name("JsonNestingBenchmark.java").resolve()
    if not guard.is_file():
        raise RuntimeError("Production nesting guard source is required; choose integrated checkout with --repository")

    def run(command, timeout=40):
        return subprocess.run(command, check=True, capture_output=True, text=True, timeout=timeout).stdout.strip()

    evidence = {
        "kind": "synthetic JVM JSON structural parser microbenchmark; NOT RPC/mobile/gameplay qualification",
        "source_commit": run(["git", "-C", str(repository), "rev-parse", "HEAD"]),
        "production_guard_sha256": sha256(guard), "harness_sha256": sha256(harness),
        "runner_sha256": sha256(Path(__file__)),
        "catalog_kotlin_version": catalog["versions"]["kotlin"],
        "diagnostic_compiler_stdlib_version": kotlin,
        "serialization_version_from_catalog": serialization,
        "java": run(["java", "-XshowSettings:properties", "-version"]) or
                subprocess.run(["java", "-version"], capture_output=True, text=True, check=True).stderr.strip(),
        "os": platform.system(), "architecture": platform.machine(),
        "logical_cpus": os.cpu_count(), "jvm_flags": ["-Xms64m", "-Xmx256m", "-Xss1m"],
        "artifacts": artifacts, "results": [],
        "limitations": ["One fork per case/mode, 3000 warmup/10000 samples; deep unguarded case 2/10",
                        "Concurrent system load and tiered JIT/GC affect results; not a calibrated JMH study",
                        "Parser-only synthetic shapes, no schema decoder, encryption, network or UI",
                        "Thread allocated bytes exclude allocations by other JVM threads; RSS is endpoint-only",
                        "Native thread endpoints are not lifetime peaks; no battery/thermal or mobile inference",
                        "Isolated compiler invocation is not a Gradle production build or release gate"]}
    with tempfile.TemporaryDirectory(prefix="parlor-json-benchmark-") as directory:
        classes = Path(directory) / "classes"
        classes.mkdir()
        run(["java", "-Xmx384m", "-cp", os.pathsep.join(compiler), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
             "-no-stdlib", "-no-reflect", "-classpath", stdlib, "-jvm-target", "17", "-d", str(classes), str(guard)])
        evidence["compiled_guard_sha256"] = sha256(classes / "com/parlor/core/serialization/JsonNestingLimitKt.class")
        cp = os.pathsep.join([str(classes), *runtime])
        run(["javac", "-cp", cp, "-d", str(classes), str(harness)])
        for fixture in ("command", "snapshot1k", "snapshot8k", "snapshot32k", "depth64", "depth65", "depth4096"):
            for mode in ("parse", "guard", "guardedParse"):
                output = run(["java", *evidence["jvm_flags"], "-cp", cp, "JsonNestingBenchmark", fixture, mode], timeout=25)
                evidence["results"].append(json.loads(output))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(evidence, indent=2) + "\n")
    print(f"Recorded {len(evidence['results'])} isolated case/mode executions to {args.output}")


if __name__ == "__main__":
    main()
