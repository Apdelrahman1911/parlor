#!/usr/bin/env python3
"""Offline consumer of one explicitly frozen dependency candidate, not a builder.

Imports are read-only. Only main() writes one new receipt. The existing reviewed
renderer and source-notice verifier are loaded after their bytes are bound to
the candidate. No network, Gradle, Git, package inspection, or publication.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import importlib.metadata
import importlib.util
import json
import os
from pathlib import Path
import re
import stat
import xml.etree.ElementTree as ET


GRAPHS = ("androidRelease", "iosArm64", "iosSimulatorArm64", "iosX64")
EXPORTER = "scripts/verification/resolved_dependencies.init.gradle"
RENDERER = "scripts/verification/dependency_inventory.py"
NOTICES = "scripts/verification/third_party_notices.py"
VERIFICATION = "gradle/verification-metadata.xml"
SCHEMAS = {
    "bom-1.6.schema.json": "3e92dddbc30cf7f6a02b80f0942b1a4cfd4fb1c26f1dfc4310afa9d613cafb93",
    "spdx.schema.json": "baa9d3bd1ed57b6751b0887edead6b5063ff53ff7429cf85d476c6c94af0166e",
    "jsf-0.82.schema.json": "8bae002c25e723db7ee1f26afde680ae1a2b1a8f6b4b4b0fd65dc3becb090aae",
}
LIMIT = 4 * 1024 * 1024
MAX_METADATA_BYTES = 32 * 1024 * 1024


class SchemaCheckFailure(ValueError):
    def __init__(self, reason, formats):
        super().__init__(reason)
        self.formats = formats


class SchemaPrerequisiteUnavailable(SchemaCheckFailure):
    """An encountered but missing format checker is not schema success."""


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def exact(value, keys):
    require(type(value) is dict and set(value) == set(keys), "Unexpected object fields")


def unique_json(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, "Duplicate JSON key")
            result[key] = value
        return result
    return json.loads(raw.decode("utf-8"), object_pairs_hook=pairs,
                      parse_constant=lambda _: require(False, "Nonfinite JSON number"))


def relative(value):
    require(type(value) is str and 0 < len(value) <= 1024 and not value.startswith("/")
            and "\\" not in value and ":" not in value
            and all(ord(c) >= 32 and ord(c) != 127 for c in value)
            and all(p not in ("", ".", "..") for p in value.split("/")), "Unsafe relative path")
    require(not any(p in value.lower() for p in
                    (".keystore", ".p12", ".p8", ".mobileprovision", "credentials.json",
                     "service-account", "local.properties")), "Protected input excluded")
    return value


class Inputs:
    """Small hash ledger; paths are explicit, regular, bounded, and rechecked."""
    def __init__(self, root):
        self.root = Path(root).absolute()
        require(self.root.resolve(strict=True) == self.root, "Repository root must be canonical")
        self.files, self.directories = {}, {}

    def path(self, name):
        path = self.root
        for part in relative(name).split("/"):
            path = path / part
            require(not path.is_symlink(), "Symlinked input refused")
        return path

    def read(self, name, limit=LIMIT):
        path = self.path(name)
        before = path.lstat()
        require(stat.S_ISREG(before.st_mode) and 0 < before.st_size <= limit, "Input size/type invalid")
        fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        with os.fdopen(fd, "rb") as stream:
            opened = os.fstat(stream.fileno())
            require(stat.S_ISREG(opened.st_mode) and opened.st_ino == before.st_ino
                    and opened.st_dev == before.st_dev, "Input identity changed before read")
            raw = stream.read(limit + 1)
        after = path.lstat()
        fields = ("st_dev", "st_ino", "st_mode", "st_size", "st_mtime_ns", "st_ctime_ns")
        require(len(raw) == before.st_size and all(getattr(before, key) == getattr(after, key) for key in fields),
                "Input changed during read")
        row = (digest(raw), len(raw), limit)
        require(name not in self.files or self.files[name][:2] == row[:2], "Input bytes changed")
        self.files[name] = row
        return raw

    def reference(self, reference):
        exact(reference, ("path", "sha256"))
        raw = self.read(reference["path"])
        require(digest(raw) == reference["sha256"], "Bound input hash differs")
        return raw

    def names(self, name):
        path = self.path(name)
        require(path.is_dir(), "Expected input directory")
        names = set()
        for entry in path.iterdir():
            require(len(names) < 4096, "Too many evidence files")
            names.add(entry.name)
        require(name not in self.directories or names == self.directories[name], "Input directory changed")
        self.directories[name] = names
        return names

    def recheck(self):
        for name, (_, _, limit) in list(self.files.items()):
            self.read(name, limit)
        for name in list(self.directories):
            self.names(name)


def instant(value):
    parsed = datetime.fromisoformat(value)
    require(parsed.tzinfo is not None, "Receipt time must have a timezone")
    return parsed


def cycle(inputs, reference, expected):
    value = unique_json(inputs.reference(reference))
    require(value["status"] == "PASS" and type(value["exit_code"]) is int and value["exit_code"] == 0
            and type(value["stop_exit_code"]) is int and value["stop_exit_code"] == 0,
            "Build/research cycle or Gradle stop did not pass")
    for field in ("cleanup_errors", "deferred_signals", "outputs_before", "remaining_outputs", "retained_outputs"):
        require(value[field] == [], "Cycle cleanup incomplete")
    for field in ("workers", "artifact_workers"):
        require(value[field]["remaining_owned_workers"] == [], "Cycle workers remain")
    require(value["source_changed_during_cycle"] is False and value["runner_changed_during_cycle"] is False
            and value["source_before"] == value["source_after"]
            and value["runner_before"] == value["runner_after"], "Cycle source/control drift")
    source = value["source_before"]
    require({key: source[key] for key in expected} == expected and source["tracked_status"] == "",
            "Cycle is not the clean frozen candidate")
    rows = source["source_manifest"]
    require(type(rows) is list and 1 <= len(rows) <= 10000
            and all(type(row) is list and len(row) == 2 for row in rows), "Invalid source manifest")
    require(len(dict(rows)) == len(rows) and rows == sorted(rows)
            and digest(json.dumps(rows, separators=(",", ":")).encode()) == expected["source_manifest_sha256"],
            "Candidate source-manifest identity differs")
    for path, sha in rows:
        relative(path)
        require(re.fullmatch(r"[a-f0-9]{64}", sha) is not None, "Invalid source digest")
    require(instant(value["started_at"]) <= instant(value["finished_at"]) <= instant(value["stopped_at"])
            <= instant(value["cleanup_completed_at"]), "Cycle timestamps are out of order")
    return value


def load_reviewed(inputs, path, source_files):
    inputs.reference({"path": path, "sha256": source_files[path]})
    spec = importlib.util.spec_from_file_location("candidate_" + Path(path).stem, inputs.path(path))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    inputs.reference({"path": path, "sha256": source_files[path]})
    return module


def schema_validator(inputs, directory):
    # Pinned retained bytes, not current network/tag contents. No default remote resolver.
    require(inputs.names(directory) == set(SCHEMAS), "Unexpected official schema set")
    schemas = {name: unique_json(inputs.reference({"path": directory + "/" + name, "sha256": sha}))
               for name, sha in SCHEMAS.items()}
    require(importlib.metadata.version("jsonschema") == "4.25.1"
            and importlib.metadata.version("referencing") == "0.36.2", "Unreviewed schema-engine version")
    import jsonschema
    from referencing import Registry, Resource
    # Registry's default retrieve raises NoSuchResource; it does not fetch URLs/files.
    registry = Registry().with_resources(
        (scheme + "cyclonedx.org/schema/" + name, Resource.from_contents(value))
        for name, value in schemas.items() for scheme in ("http://", "https://"))
    cls = jsonschema.validators.validator_for(schemas["bom-1.6.schema.json"])
    cls.check_schema(schemas["bom-1.6.schema.json"])
    encountered = set()

    class RecordingChecker(jsonschema.FormatChecker):
        def check(self, instance, format):
            encountered.add(format)
            return super().check(instance, format)  # Delegate exact pinned library semantics.

    checker = RecordingChecker()
    return (cls(schemas["bom-1.6.schema.json"], registry=registry, format_checker=checker),
            {"registered": sorted(checker.checkers), "encountered": encountered})


def validate_schema(check, bom):
    validator, observed = check
    try:
        error = next(validator.iter_errors(bom), None)
    except Exception as failure:
        error = failure  # Do not expose raw schema/instance contents.
    formats = {"registered": observed["registered"], "encountered": sorted(observed["encountered"]),
               "missing": sorted(observed["encountered"] - set(observed["registered"]))}
    if error is not None:
        raise SchemaCheckFailure("Official schema validation failed", formats)
    if formats["missing"]:
        raise SchemaPrerequisiteUnavailable("An encountered schema format checker is unavailable", formats)
    return formats


def declarations(inputs, directory, report, inventory, selected, window):
    rows = report["declarations"]
    require(type(rows) is dict and 1 <= len(rows) <= 4096 and report["unresolved"] == [],
            "Unresolved or invalid publisher declarations")
    parsed, total = {}, 0
    for identifier, row in rows.items():
        inventory.coordinates(identifier)
        require(set(row) in ({"component", "status", "licenses", "url", "sha256", "file", "accessed_at"},
                            {"component", "status", "licenses", "url", "sha256", "file", "accessed_at", "inherited_from"}),
                "Unexpected declaration fields")
        require(row["component"] == identifier and row["status"] == "PUBLISHER_DECLARED"
                and re.fullmatch(r"[a-f0-9]{64}\.pom", row["file"]) is not None
                and row["file"] == row["sha256"] + ".pom", "Unbound declaration")
        group, name, version = inventory.coordinates(identifier)
        suffix = f"{group.replace('.', '/')}/{name}/{version}/{name}-{version}.pom"
        require(row["url"] in [base + suffix for base in inventory.MAVEN_BASES]
                and window[0] <= instant(row["accessed_at"]) <= window[1], "POM origin/time differs")
        raw = inputs.read(directory + "/metadata/" + row["file"], inventory.MAX_POM_BYTES)
        require(digest(raw) == row["sha256"], "Publisher metadata bytes differ")
        total += len(raw)
        require(total <= MAX_METADATA_BYTES, "Metadata aggregate exceeds bound")
        parsed[identifier] = inventory.parse_pom(raw, identifier)
    needed = set()
    for identifier in selected:
        current, chain = identifier, []
        while True:
            require(current in rows and current not in chain and len(chain) < 5,
                    "Missing, cyclic, or excessive inherited license")
            chain.append(current)
            needed.add(current)
            row, pom = rows[current], parsed[current]
            if pom["licenses"]:
                require("inherited_from" not in row and row["licenses"] == pom["licenses"], "Direct license differs")
                break
            require(row.get("inherited_from") == pom["parent"], "License parent differs")
            current = pom["parent"]
        require(all(rows[node]["licenses"] == pom["licenses"] for node in chain), "Inherited license differs")
    require(needed == set(rows), "Unrelated or missing metadata declarations")
    require(inputs.names(directory + "/metadata") == {row["file"] for row in rows.values()}, "Metadata file set differs")
    return rows, total


def consume(root, binding_name, binding_sha256):
    inputs = Inputs(root)
    binding = unique_json(inputs.reference({"path": binding_name, "sha256": binding_sha256}))
    exact(binding, ("schema_version", "source", "export_receipt", "render_receipt", "graphs", "report", "schemas", "lane", "consumer"))
    require(type(binding["schema_version"]) is int and binding["schema_version"] == 1, "Unknown binding version")
    require(inputs.reference(binding["consumer"]) == Path(__file__).read_bytes(), "Executing consumer is not bound")
    expected = binding["source"]
    exact(expected, ("commit", "tree", "diff_sha256", "source_manifest_sha256"))
    for key, value in expected.items():
        require(type(value) is str and re.fullmatch(r"[a-f0-9]{" + str(40 if key in ("commit", "tree") else 64) + "}", value),
                "Invalid candidate identity")
    require(expected["diff_sha256"] == digest(b""), "Final candidate must be clean; preserve dirty work separately")
    exported = cycle(inputs, binding["export_receipt"], expected)
    rendered = cycle(inputs, binding["render_receipt"], expected)
    require(exported["source_before"] == rendered["source_before"], "Export/render source snapshots differ")
    source_files = dict(exported["source_before"]["source_manifest"])
    inputs.reference(binding["lane"])
    runner = [[binding["lane"]["path"], binding["lane"]["sha256"]]]
    for receipt in (exported, rendered):
        require(receipt["runner_before"]["manifest"] == runner
                and receipt["runner_before"]["manifest_sha256"] == digest(json.dumps(runner, separators=(",", ":")).encode()),
                "Unbound coordinated lane")
    require(instant(exported["cleanup_completed_at"]) <= instant(rendered["started_at"]), "Overlapping or reversed cycles")
    graph_dir, report_dir = relative(binding["graphs"]), relative(binding["report"])
    require(Path(graph_dir).parent == Path(binding["export_receipt"]["path"]).parent
            and Path(report_dir).parent == Path(binding["render_receipt"]["path"]).parent, "Evidence outside its cycle")
    command = exported["command"]
    require(command[:4] == ["./gradlew", "writeResolvedDependencyInventory", "-I", EXPORTER]
            and all(type(arg) is str and arg.startswith("-") for arg in command[4:])
            and command.count("--dependency-verification=strict") == 1
            and command.count("-Pparlor.dependencyInventoryDir=" + str(inputs.path(graph_dir))) == 1
            and not any("write-verification-metadata" in arg or arg == "--dependency-verification=off" for arg in command),
            "Export command is not the requested strict graph task")
    require(rendered["command"] == ["/usr/bin/python3", "-B", RENDERER, graph_dir, str(inputs.path(report_dir))],
            "Renderer command does not bind these input/output paths")
    inputs.reference({"path": EXPORTER, "sha256": source_files[EXPORTER]})
    inventory = load_reviewed(inputs, RENDERER, source_files)
    require(tuple(inventory.GRAPHS) == GRAPHS, "Renderer graph contract changed")
    require(inputs.names(graph_dir) == {name + ".json" for name in GRAPHS} | {"resolution-complete.json"},
            "Expected exactly four completed graphs")
    captured = {name: unique_json(inputs.read(graph_dir + "/" + name + ".json")) for name in GRAPHS}
    completion = unique_json(inputs.read(graph_dir + "/resolution-complete.json", 10000))
    graphs, graph_hashes, source = inventory.read_graphs(inputs.path(graph_dir))
    require(type(completion["schema_version"]) is int and completion["schema_version"] == 1
            and all(type(graph["schema_version"]) is int for graph in graphs)
            and source == {key: expected[key] for key in ("commit", "tree", "diff_sha256")}
            and completion["status"] == "COMPLETE" and completion["graph_sha256"] == graph_hashes
            and graphs == [captured[name] for name in GRAPHS], "Graph completion/source binding differs")
    report = unique_json(inputs.read(report_dir + "/license-evidence.json"))
    require(type(report["schema_version"]) is int and report["schema_version"] == 1
            and report["source"] == source and report["graph_sha256"] == graph_hashes, "Declaration graph/source differs")
    selected = {row["id"] for graph in graphs for row in graph["components"] if row["kind"] == "maven"}
    declared, metadata_bytes = declarations(inputs, report_dir, report, inventory, selected,
        (instant(rendered["started_at"]), instant(rendered["finished_at"])))
    verification = inputs.reference({"path": VERIFICATION, "sha256": source_files[VERIFICATION]})
    require(b"<!DOCTYPE" not in verification.upper() and b"<!ENTITY" not in verification.upper(), "Unsafe verification XML")
    ns = "{https://schema.gradle.org/dependency-verification}"
    approved = {}
    for component in ET.fromstring(verification).findall(ns + "components/" + ns + "component"):
        identifier = ":".join(component.attrib[key] for key in ("group", "name", "version"))
        for artifact in component.findall(ns + "artifact"):
            key = (identifier, artifact.attrib["name"])
            require(key not in approved, "Duplicate strict artifact pin")
            approved[key] = {item.attrib["value"] for item in artifact.findall(ns + "sha256")}
    validator = schema_validator(inputs, relative(binding["schemas"]))
    require(inputs.names(report_dir) == {name + ".cdx.json" for name in GRAPHS}
            | {"license-evidence.json", "THIRD_PARTY_DECLARATIONS_DRAFT.md", "metadata"}, "Report file set differs")
    results = []
    for graph in graphs:
        name = graph["graph"]
        config = "releaseRuntimeClasspath" if name == "androidRelease" else name + "CompileKlibraries"
        require(graph["configuration"] == config and graph["root_component"] == "project::composeApp"
                and not {"project::shared:engine-testing", "project::shared:networking-testing"}
                & {row["id"] for row in graph["components"]}, "Unexpected production graph/fixture")
        for artifact in graph["artifacts"]:
            require(artifact["sha256"] in approved.get((artifact["component"], artifact["name"]), set()),
                    "Artifact is not pinned by strict verification metadata")
            require(type(artifact["variants"]) is list and artifact["variants"]
                    and all(type(value) is str and 0 < len(value) <= 2048 for value in artifact["variants"])
                    and sorted(set(artifact["variants"])) == artifact["variants"], "Missing/duplicate artifact variants")
        raw = inputs.read(report_dir + "/" + name + ".cdx.json")
        bom = unique_json(raw)
        require(bom == inventory.make_bom(graph, declared, source), "SBOM differs from bound graph/declarations")
        try:
            formats = validate_schema(validator, bom)
        except SchemaCheckFailure as failure:
            failure.graph = name
            raise
        results.append({"graph": name, "graph_sha256": graph_hashes[name], "sbom_sha256": digest(raw),
                        "components": len(graph["components"]), "artifacts": len(graph["artifacts"]), "status": "PASS"})
    draft = ["# Third-party declaration draft", "", "Generated from exact resolved inputs. NOT final legal approval or a complete redistribution notice.", ""]
    for identifier in sorted(selected):
        row = declared[identifier]
        draft += ["## " + identifier, "", "Status: " + row["status"]]
        draft += ["- " + item["name"] + (" — " + item["url"] if item["url"] else "") for item in row["licenses"]]
        draft += ["- Publisher metadata: " + row["url"], ""]
    require(inputs.read(report_dir + "/THIRD_PARTY_DECLARATIONS_DRAFT.md").decode("utf-8") == "\n".join(draft),
            "Declaration draft differs from validated metadata")
    notices = load_reviewed(inputs, NOTICES, source_files)
    notice_result, notice_bytes = notices.source_receipt(inputs.root)
    for name in (notices.MANIFEST_PATH, notices.CATALOG_PATH, notices.BUILD_PATH):
        inputs.reference({"path": name, "sha256": source_files[name]})
    inputs.names(notices.RESOURCE_DIRECTORY)
    for name, raw in notice_bytes.items():
        path = notices.RESOURCE_DIRECTORY + "/" + name
        require(inputs.reference({"path": path, "sha256": source_files[path]}) == raw, "Notice source binding differs")
    inputs.recheck()
    rows = sorted([path, sha, size] for path, (sha, size, _) in inputs.files.items())
    return {"schema_version": 1, "status": "PASS_SCOPED_CANDIDATE_INPUTS", "source": expected,
            "binding_sha256": binding_sha256, "consumer": binding["consumer"],
            "graphs": results, "maven_components": len(selected),
            "publisher_poms": len(declared), "metadata_bytes": metadata_bytes, "unresolved": [],
            "notice_source": notice_result, "schemas": SCHEMAS, "jsonschema_version": "4.25.1",
            "referencing_version": "0.36.2", "schema_formats": formats, "consumed_file_count": len(rows),
            "consumed_manifest_sha256": digest(json.dumps(rows, separators=(",", ":")).encode()),
            "limits": ["Root frozen source/receipt attestation is required; this consumer does not recapture Git or rebuild.",
                       "Renderer/parser and source-notice implementation checks reuse their reviewed, source-bound APIs.",
                       "Resolved input and source-notice integrity only; no final-package, legal, physical-device, signing or Store approval."]}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--binding", required=True, help="Repository-relative, externally reviewed binding JSON")
    parser.add_argument("--binding-sha256", required=True)
    parser.add_argument("--output", required=True, help="New repository-relative result JSON; parent must exist")
    args = parser.parse_args(argv)
    owner = Inputs(args.root)
    destination = owner.path(relative(args.output))
    require(not destination.exists(), "Refusing to overwrite evidence")
    try:
        result = consume(args.root, args.binding, args.binding_sha256)
        code = 0
    except Exception as error:
        # Preserve failure without echoing paths, XML text, or raw schema errors.
        result = {"schema_version": 1, "status": "FAIL", "error_type": type(error).__name__}
        if isinstance(error, SchemaCheckFailure):
            result["schema_formats"] = error.formats
            result["schema_graph"] = getattr(error, "graph", None)
        code = 1
    result["checked_at"] = datetime.now(timezone.utc).isoformat()
    with destination.open("x") as stream:
        json.dump(result, stream, indent=2)
        stream.write("\n")
    print(json.dumps({"status": result["status"], "exit_code": code}))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
