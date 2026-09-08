"""Synthetic consumer controls; root alone executes. No Gradle/network/device proof."""
import copy
import importlib.util
import io
import json
from contextlib import redirect_stdout
from pathlib import Path
import socket
import subprocess
import tempfile
import unittest
from unittest import mock

import candidate_consumer as subject


HERE = Path(__file__).resolve().parent
REPOSITORY = HERE.parents[3]
LIBRARY = "org.example:library:1.0"
SOURCE = {"commit": "a" * 40, "tree": "b" * 40, "diff_sha256": subject.digest(b"")}
POM = b'''<project><groupId>org.example</groupId><artifactId>library</artifactId><version>1.0</version>
<licenses><license><name>Synthetic License</name><url>https://example.org/license</url></license></licenses></project>'''


class CandidateConsumerTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="parlor-candidate-consumer-control-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        source_files = [subject.EXPORTER, subject.RENDERER, subject.NOTICES, "config/third-party-notices.json",
                        "gradle/libs.versions.toml", "composeApp/build.gradle.kts"]
        manifest = json.loads((REPOSITORY / "config/third-party-notices.json").read_text())
        source_files += [manifest["resource_directory"] + "/" + row["name"] for row in manifest["files"]]
        for name in source_files:
            self.write(name, (REPOSITORY / name).read_bytes())
        self.write(subject.VERIFICATION, (
            '<verification-metadata xmlns="https://schema.gradle.org/dependency-verification"><components>'
            '<component group="org.example" name="library" version="1.0"><artifact name="library-1.0.jar">'
            '<sha256 value="' + "d" * 64 + '"/></artifact></component></components></verification-metadata>').encode())
        source_files.append(subject.VERIFICATION)
        self.write("controls/lane.py", b"# Synthetic receipt identity, never executed.\n")
        self.write("controls/candidate_consumer.py", (HERE / "candidate_consumer.py").read_bytes())
        for name in subject.SCHEMAS:
            self.write("schemas/" + name, (HERE / "schemas" / name).read_bytes())
        self.graphs = "export/raw-graphs"
        self.report = "research/report"
        self.pom_sha = subject.digest(POM)
        self.declaration = {
            "component": LIBRARY, "status": "PUBLISHER_DECLARED",
            "licenses": [{"name": "Synthetic License", "url": "https://example.org/license"}],
            "url": "https://repo.maven.apache.org/maven2/org/example/library/1.0/library-1.0.pom",
            "sha256": self.pom_sha, "file": self.pom_sha + ".pom", "accessed_at": "2026-09-08T00:00:06+00:00"}
        self.write(self.report + "/metadata/" + self.pom_sha + ".pom", POM)
        hashes = {}
        for name in subject.GRAPHS:
            config = "releaseRuntimeClasspath" if name == "androidRelease" else name + "CompileKlibraries"
            graph = {"schema_version": 1, "graph": name, "configuration": config, "source": SOURCE,
                     "strict_dependency_verification": True, "root_component": "project::composeApp",
                     "components": [{"id": LIBRARY, "kind": "maven", "dependencies": []},
                                    {"id": "project::composeApp", "kind": "project", "dependencies": [
                                        {"selected": LIBRARY, "constraint": False}]}],
                     "artifacts": [{"component": LIBRARY, "name": "library-1.0.jar", "bytes": 100,
                                    "sha256": "d" * 64, "variants": ["synthetic-reviewed-variant"]}]}
            self.write_json(self.graphs + "/" + name + ".json", graph)
            hashes[name] = subject.digest((self.root / self.graphs / (name + ".json")).read_bytes())
            # Hand-authored tiny BOM: fixture construction does not call make_bom.
            bom = {"bomFormat": "CycloneDX", "specVersion": "1.6", "version": 1,
                   "metadata": {"component": {"type": "application", "name": "Parlor", "version": SOURCE["commit"]},
                                "properties": [{"name": "parlor:" + key, "value": value} for key, value in
                                    {**SOURCE, "graph": name, "configuration": config,
                                     "scope": "resolved-inputs; not post-shrinker or binary-internal completeness; declared licenses, not legal approval"}.items()]},
                   "components": [
                       {"type": "library", "bom-ref": "pkg:maven/org.example/library@1.0", "group": "org.example",
                        "name": "library", "version": "1.0", "purl": "pkg:maven/org.example/library@1.0",
                        "properties": [{"name": "parlor:license-status", "value": "PUBLISHER_DECLARED"}],
                        "licenses": [{"license": {"name": "Synthetic License", "url": "https://example.org/license"}}],
                        "components": [{"type": "file", "name": "library-1.0.jar",
                                        "hashes": [{"alg": "SHA-256", "content": "d" * 64}],
                                        "properties": [{"name": "parlor:input-bytes", "value": "100"}]}]},
                       {"type": "library", "bom-ref": "project::composeApp", "name": "project::composeApp",
                        "properties": [{"name": "parlor:license-status", "value": "OWNER_DECISION_REQUIRED"}]}],
                   "dependencies": [{"ref": "pkg:maven/org.example/library@1.0", "dependsOn": []},
                                    {"ref": "project::composeApp", "dependsOn": ["pkg:maven/org.example/library@1.0"]}]}
            self.write_json(self.report + "/" + name + ".cdx.json", bom)
        self.write_json(self.graphs + "/resolution-complete.json", {
            "schema_version": 1, "status": "COMPLETE", "source": SOURCE, "graph_sha256": hashes})
        self.write_json(self.report + "/license-evidence.json", {
            "schema_version": 1, "source": SOURCE, "graph_sha256": hashes,
            "declarations": {LIBRARY: self.declaration}, "unresolved": [], "limitations": ["Synthetic controls only"]})
        self.write(self.report + "/THIRD_PARTY_DECLARATIONS_DRAFT.md", (
            "# Third-party declaration draft\n\nGenerated from exact resolved inputs. NOT final legal approval or a complete redistribution notice.\n\n"
            "## org.example:library:1.0\n\nStatus: PUBLISHER_DECLARED\n- Synthetic License — https://example.org/license\n"
            "- Publisher metadata: " + self.declaration["url"] + "\n").encode())
        rows = [[name, subject.digest((self.root / name).read_bytes())] for name in sorted(source_files)]
        snapshot = {**SOURCE, "branch": "synthetic-control", "tracked_status": "", "source_manifest": rows,
                    "source_manifest_sha256": subject.digest(json.dumps(rows, separators=(",", ":")).encode())}
        lane = [["controls/lane.py", subject.digest((self.root / "controls/lane.py").read_bytes())]]
        runner = {"manifest": lane, "manifest_sha256": subject.digest(json.dumps(lane, separators=(",", ":")).encode())}
        common = {"status": "PASS", "exit_code": 0, "stop_exit_code": 0, "cleanup_errors": [],
                  "deferred_signals": [], "outputs_before": [], "remaining_outputs": [], "retained_outputs": [],
                  "workers": {"terminated_owned_workers": [], "remaining_owned_workers": []},
                  "artifact_workers": {"terminated_owned_workers": [], "remaining_owned_workers": []},
                  "source_changed_during_cycle": False, "runner_changed_during_cycle": False,
                  "source_before": snapshot, "source_after": snapshot, "runner_before": runner, "runner_after": runner}
        self.write_json("export/receipt.json", {**common,
            "started_at": "2026-09-08T00:00:00+00:00", "finished_at": "2026-09-08T00:00:02+00:00",
            "stopped_at": "2026-09-08T00:00:03+00:00", "cleanup_completed_at": "2026-09-08T00:00:04+00:00",
            "command": ["./gradlew", "writeResolvedDependencyInventory", "-I", subject.EXPORTER,
                        "-Pparlor.dependencyInventoryDir=" + str(self.root / self.graphs), "--dependency-verification=strict"]})
        self.write_json("research/receipt.json", {**common,
            "started_at": "2026-09-08T00:00:05+00:00", "finished_at": "2026-09-08T00:00:07+00:00",
            "stopped_at": "2026-09-08T00:00:08+00:00", "cleanup_completed_at": "2026-09-08T00:00:09+00:00",
            "command": ["/usr/bin/python3", "-B", subject.RENDERER, self.graphs, str(self.root / self.report)]})
        self.binding = {"schema_version": 1, "source": {**SOURCE, "source_manifest_sha256": snapshot["source_manifest_sha256"]},
                        "export_receipt": self.reference("export/receipt.json"),
                        "render_receipt": self.reference("research/receipt.json"),
                        "graphs": self.graphs, "report": self.report, "schemas": "schemas",
                        "lane": self.reference("controls/lane.py"), "consumer": self.reference("controls/candidate_consumer.py")}
        self.seal()

    def write(self, name, raw):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(raw)

    def write_json(self, name, value):
        self.write(name, (json.dumps(value, indent=2) + "\n").encode())

    def read_json(self, name):
        return json.loads((self.root / name).read_text())

    def reference(self, name):
        return {"path": name, "sha256": subject.digest((self.root / name).read_bytes())}

    def seal(self):
        self.binding["export_receipt"] = self.reference("export/receipt.json")
        self.binding["render_receipt"] = self.reference("research/receipt.json")
        self.write_json("binding.json", self.binding)
        self.binding_sha = subject.digest((self.root / "binding.json").read_bytes())

    def consume(self):
        return subject.consume(self.root, "binding.json", self.binding_sha)

    def rehash_graphs(self):
        hashes = {name: subject.digest((self.root / self.graphs / (name + ".json")).read_bytes())
                  for name in subject.GRAPHS}
        for path in (self.graphs + "/resolution-complete.json", self.report + "/license-evidence.json"):
            value = self.read_json(path)
            value["graph_sha256"] = hashes
            self.write_json(path, value)

    def test_complete_synthetic_candidate_uses_real_bound_apis_and_offline_schema(self):
        with mock.patch.object(subject, "load_reviewed", wraps=subject.load_reviewed) as loaded, \
                mock.patch.object(socket, "create_connection", side_effect=AssertionError("Network forbidden")), \
                mock.patch.object(subprocess, "Popen", side_effect=AssertionError("Commands forbidden")):
            result = self.consume()
        self.assertEqual(result["status"], "PASS_SCOPED_CANDIDATE_INPUTS")
        self.assertEqual([row["graph"] for row in result["graphs"]], list(subject.GRAPHS))
        self.assertEqual((result["maven_components"], result["publisher_poms"], result["unresolved"]), (1, 1, []))
        self.assertEqual([call.args[1] for call in loaded.call_args_list], [subject.RENDERER, subject.NOTICES])
        self.assertEqual(result["notice_source"]["status"], "PASS")

    def test_import_has_no_evidence_reads_writes_network_or_commands(self):
        spec = importlib.util.spec_from_file_location("consumer_import_control", HERE / "candidate_consumer.py")
        module = importlib.util.module_from_spec(spec)
        with mock.patch.object(Path, "read_bytes", side_effect=AssertionError("Import read")), \
                mock.patch.object(Path, "mkdir", side_effect=AssertionError("Import write")), \
                mock.patch.object(socket, "create_connection", side_effect=AssertionError("Import network")), \
                mock.patch.object(subprocess, "Popen", side_effect=AssertionError("Import process")):
            spec.loader.exec_module(module)
        self.assertTrue(callable(module.consume))

    def test_binding_hash_unknown_fields_and_executing_consumer_are_required(self):
        with self.assertRaisesRegex(ValueError, "Bound input hash"):
            subject.consume(self.root, "binding.json", "0" * 64)
        self.binding["unapproved"] = True
        self.seal()
        with self.assertRaisesRegex(ValueError, "Unexpected object fields"):
            self.consume()
        del self.binding["unapproved"]
        self.write("controls/candidate_consumer.py", b"# Different consumer\n")
        self.binding["consumer"] = self.reference("controls/candidate_consumer.py")
        self.seal()
        with self.assertRaisesRegex(ValueError, "Executing consumer"):
            self.consume()

    def test_receipt_rehash_does_not_waive_failures_cleanup_or_source_drift(self):
        original = self.read_json("export/receipt.json")
        mutations = [lambda r: r.update(status="FAIL"), lambda r: r.update(exit_code=True),
                     lambda r: r.update(stop_exit_code=1), lambda r: r.update(cleanup_errors=["error"]),
                     lambda r: r["workers"].update(remaining_owned_workers=[1]),
                     lambda r: r.update(source_changed_during_cycle=True),
                     lambda r: r["source_after"].update(commit="e" * 40),
                     lambda r: r["source_before"].update(source_manifest=[])]
        for mutate in mutations:
            with self.subTest(mutation=mutate):
                changed = copy.deepcopy(original)
                mutate(changed)
                self.write_json("export/receipt.json", changed)
                self.seal()
                with self.assertRaises(ValueError):
                    self.consume()

    def test_candidate_identity_and_wrong_cycle_commands_do_not_pass(self):
        self.binding["source"]["commit"] = "e" * 40
        self.seal()
        with self.assertRaisesRegex(ValueError, "frozen candidate"):
            self.consume()
        self.binding["source"]["commit"] = SOURCE["commit"]
        receipt = self.read_json("research/receipt.json")
        receipt["command"][-1] += "-stale"
        self.write_json("research/receipt.json", receipt)
        self.seal()
        with self.assertRaisesRegex(ValueError, "Renderer command"):
            self.consume()

    def test_missing_extra_graph_and_completion_bytes_fail_closed(self):
        extra = self.root / self.graphs / "old.json"
        extra.write_text("{}")
        with self.assertRaisesRegex(ValueError, "exactly four"):
            self.consume()
        extra.unlink()
        path = self.root / self.graphs / "androidRelease.json"
        path.write_text(path.read_text() + "\n")
        with self.assertRaisesRegex(ValueError, "Graph bytes differ"):
            self.consume()
        (self.root / self.graphs / "resolution-complete.json").unlink()
        with self.assertRaisesRegex(ValueError, "exactly four"):
            self.consume()

    def test_symlink_traversal_duplicate_json_and_size_controls(self):
        inputs = subject.Inputs(self.root)
        for value in ("../binding.json", "/binding.json", "x/../binding.json", "local.properties"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                inputs.read(value)
        (self.root / "alias").symlink_to(self.root / "binding.json")
        with self.assertRaisesRegex(ValueError, "Symlink"):
            inputs.read("alias")
        with self.assertRaisesRegex(ValueError, "Duplicate JSON"):
            subject.unique_json(b'{"a":1,"a":2}')
        with self.assertRaises(ValueError):
            inputs.read("binding.json", 1)

    def test_bound_renderer_and_notice_edits_fail_before_execution(self):
        for path in (subject.RENDERER, subject.NOTICES):
            original = (self.root / path).read_bytes()
            self.write(path, original + b"\n# changed\n")
            with self.subTest(path=path), self.assertRaisesRegex(ValueError, "Bound input hash"):
                self.consume()
            self.write(path, original)

    def test_strict_pins_and_variants_survive_consistent_evidence_rehash(self):
        path = self.graphs + "/androidRelease.json"
        original = self.read_json(path)
        changed = copy.deepcopy(original)
        changed["artifacts"][0]["sha256"] = "e" * 64
        self.write_json(path, changed)
        self.rehash_graphs()
        with self.assertRaisesRegex(ValueError, "not pinned"):
            self.consume()
        changed = copy.deepcopy(original)
        changed["artifacts"][0]["variants"] *= 2
        self.write_json(path, changed)
        self.rehash_graphs()
        with self.assertRaisesRegex(ValueError, "artifact variants"):
            self.consume()

    def test_metadata_tamper_timestamp_and_extra_files_are_not_declared_success(self):
        path = self.report + "/license-evidence.json"
        original = self.read_json(path)
        altered = copy.deepcopy(original)
        altered["declarations"][LIBRARY]["accessed_at"] = "2025-01-01T00:00:00+00:00"
        self.write_json(path, altered)
        with self.assertRaisesRegex(ValueError, "POM origin/time"):
            self.consume()
        self.write_json(path, original)
        self.write(self.report + "/metadata/unreferenced.pom", b"<project/>")
        with self.assertRaisesRegex(ValueError, "Metadata file set"):
            self.consume()
        (self.root / self.report / "metadata/unreferenced.pom").unlink()
        self.write(self.report + "/metadata/" + self.pom_sha + ".pom", POM + b"\n")
        with self.assertRaisesRegex(ValueError, "metadata bytes"):
            self.consume()

    def test_schema_integrity_and_actual_official_validation_remain_required(self):
        inputs = subject.Inputs(self.root)
        validator = subject.schema_validator(inputs, "schemas")
        value = self.read_json(self.report + "/androidRelease.cdx.json")
        self.assertEqual(subject.validate_schema(validator, value)["missing"], [])
        # Official 1.6 schema requires a string, not a specific string value.
        # The consumer independently enforces the exact rendered version below.
        value["specVersion"] = 0
        with self.assertRaises(subject.SchemaCheckFailure):
            subject.validate_schema(validator, value)
        value["specVersion"] = "0"
        self.write_json(self.report + "/androidRelease.cdx.json", value)
        with self.assertRaisesRegex(ValueError, "SBOM differs"):
            self.consume()
        self.write("schemas/spdx.schema.json", b"{}")
        with self.assertRaisesRegex(ValueError, "Bound input hash"):
            self.consume()

    def test_missing_optional_format_checker_is_not_silent_schema_success(self):
        import jsonschema
        checkers = {name: value for name, value in jsonschema.FormatChecker.checkers.items() if name != "iri-reference"}
        with mock.patch.object(jsonschema.FormatChecker, "checkers", checkers):
            validator = subject.schema_validator(subject.Inputs(self.root), "schemas")
        value = self.read_json(self.report + "/androidRelease.cdx.json")
        with self.assertRaises(subject.SchemaPrerequisiteUnavailable) as failure:
            subject.validate_schema(validator, value)
        self.assertIn("iri-reference", failure.exception.formats["encountered"])
        self.assertIn("iri-reference", failure.exception.formats["missing"])
        self.assertNotIn("iri-reference", failure.exception.formats["registered"])

    def test_recording_checker_preserves_registered_library_rejection(self):
        validator = subject.schema_validator(subject.Inputs(self.root), "schemas")
        value = self.read_json(self.report + "/androidRelease.cdx.json")
        value["components"][0]["licenses"][0]["license"]["url"] = "https://example.org/\x00"
        with self.assertRaises(subject.SchemaCheckFailure) as failure:
            subject.validate_schema(validator, value)
        self.assertIs(type(failure.exception), subject.SchemaCheckFailure)
        self.assertIn("iri-reference", failure.exception.formats["registered"])
        self.assertIn("iri-reference", failure.exception.formats["encountered"])

    def test_wrong_sbom_edges_and_notice_bytes_cannot_pass(self):
        name = self.report + "/androidRelease.cdx.json"
        original = self.read_json(name)
        altered = copy.deepcopy(original)
        altered["dependencies"][1]["dependsOn"] = []
        self.write_json(name, altered)
        with self.assertRaisesRegex(ValueError, "SBOM differs"):
            self.consume()
        self.write_json(name, original)
        notice = "composeApp/src/commonMain/composeResources/files/legal/INDEX.txt"
        self.write(notice, (self.root / notice).read_bytes() + b"\n")
        with self.assertRaises(ValueError):
            self.consume()

    def test_final_recheck_catches_late_file_and_directory_changes(self):
        inputs = subject.Inputs(self.root)
        inputs.read("binding.json")
        self.write("binding.json", b"{}")
        with self.assertRaisesRegex(ValueError, "Input bytes changed"):
            inputs.recheck()
        inputs = subject.Inputs(self.root)
        inputs.names(self.graphs)
        self.write(self.graphs + "/extra.json", b"{}")
        with self.assertRaisesRegex(ValueError, "directory changed"):
            inputs.recheck()

    def test_main_failure_is_nonzero_sanitized_and_never_overwrites_evidence(self):
        args = ["--root", str(self.root), "--binding", "binding.json", "--binding-sha256", "0" * 64,
                "--output", "result.json"]
        with redirect_stdout(io.StringIO()):
            self.assertEqual(subject.main(args), 1)
        raw = (self.root / "result.json").read_bytes()
        self.assertEqual(json.loads(raw)["status"], "FAIL")
        self.assertNotIn(str(self.root).encode(), raw)
        with self.assertRaisesRegex(ValueError, "overwrite"):
            subject.main(args)
        self.assertEqual((self.root / "result.json").read_bytes(), raw)


if __name__ == "__main__":
    unittest.main()
