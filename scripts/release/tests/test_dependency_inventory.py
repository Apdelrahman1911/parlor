from __future__ import annotations

import copy
import importlib.util
import hashlib
import http.client
import json
from pathlib import Path
import socket
import tempfile
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("dependency_inventory", ROOT / "scripts/verification/dependency_inventory.py")
inventory = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(inventory)


def graph():
    return {"schema_version": 1, "graph": "androidRelease", "configuration": "releaseRuntimeClasspath",
            "source": {"commit": "a" * 40, "tree": "b" * 40, "diff_sha256": "c" * 64},
            "strict_dependency_verification": True, "root_component": "project::composeApp",
            "components": [
                {"id": "project::composeApp", "kind": "project", "dependencies": [{"selected": "org.example:library:1.0", "constraint": False}]},
                {"id": "org.example:library:1.0", "kind": "maven", "dependencies": []}],
            "artifacts": [{"component": "org.example:library:1.0", "name": "library-1.0.aar", "bytes": 100, "sha256": "d" * 64}]}


def pom(extra="", artifact="library", version="1.0"):
    return (f'<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>org.example</groupId>'
            f'<artifactId>{artifact}</artifactId><version>{version}</version>{extra}</project>').encode()


LICENSE = "<licenses><license><name>Declared License</name><url>https://example.org/license</url></license></licenses>"


class DependencyInventoryTest(unittest.TestCase):
    def test_resolved_graph_and_cyclonedx_keep_edges_bytes_and_declared_status(self):
        value = graph()
        license_rows = {"org.example:library:1.0": {"status": "PUBLISHER_DECLARED", "licenses": [{"name": "Declared License", "url": "https://example.org/license"}]}}
        bom = inventory.make_bom(value, license_rows, value["source"])
        self.assertEqual(bom["specVersion"], "1.6")
        self.assertEqual(bom["dependencies"][0]["dependsOn"], ["pkg:maven/org.example/library@1.0"])
        library = bom["components"][1]
        self.assertEqual(library["licenses"][0]["license"]["name"], "Declared License")
        self.assertEqual(library["components"][0]["hashes"][0]["content"], "d" * 64)
        self.assertEqual(library["properties"][0]["value"], "PUBLISHER_DECLARED")

    def test_constraints_are_not_misrepresented_as_runtime_dependency_edges(self):
        value = graph()
        value["components"][0]["dependencies"][0]["constraint"] = True
        bom = inventory.make_bom(value, {"org.example:library:1.0": {"status": "UNRESOLVED", "licenses": []}}, value["source"])
        self.assertEqual(bom["dependencies"][0]["dependsOn"], [])

    def test_graph_rejects_unverified_duplicate_dangling_and_forged_inputs(self):
        mutations = [lambda g: g.update(strict_dependency_verification=False),
                     lambda g: g["components"].append(copy.deepcopy(g["components"][1])),
                     lambda g: g["components"][0]["dependencies"][0].update(selected="missing"),
                     lambda g: g.update(root_component="missing"),
                     lambda g: g["source"].update(commit="main"),
                     lambda g: g["artifacts"][0].update(sha256="wrong"),
                     lambda g: g["artifacts"][0].update(bytes=True),
                     lambda g: g["artifacts"][0].update(name="../outside"),
                     lambda g: g["artifacts"].append(copy.deepcopy(g["artifacts"][0]))]
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                value = graph()
                mutation(value)
                with self.assertRaises(ValueError):
                    inventory.validate_graph(value)

    def test_coordinates_cannot_escape_metadata_repository(self):
        for identifier in ("../other:lib:1", "org.example:../../key:1", "https://host:lib:1", "org..example:lib:1"):
            with self.subTest(identifier=identifier), self.assertRaises(ValueError):
                inventory.coordinates(identifier)

    def test_direct_pom_license_identity_and_parent_are_explicit(self):
        parsed = inventory.parse_pom(pom(LICENSE), "org.example:library:1.0")
        self.assertEqual(parsed["licenses"], [{"name": "Declared License", "url": "https://example.org/license"}])
        self.assertIsNone(parsed["parent"])
        with self.assertRaises(ValueError):
            inventory.parse_pom(pom(LICENSE), "org.example:library:2.0")

    def test_pom_inherited_group_and_version_do_not_erase_identity(self):
        data = b"<project><parent><groupId>org.example</groupId><artifactId>parent</artifactId><version>1.0</version></parent><artifactId>library</artifactId></project>"
        parsed = inventory.parse_pom(data, "org.example:library:1.0")
        self.assertEqual(parsed["parent"], "org.example:parent:1.0")
        self.assertEqual(parsed["licenses"], [])

    def test_unsafe_or_unresolved_pom_metadata_is_not_accepted(self):
        for data in (b"<!DOCTYPE project><project/>", b"<!ENTITY secret SYSTEM 'file:///secret'><project/>",
                     b"<other/>", b"x" * (inventory.MAX_POM_BYTES + 1),
                     '<!DOCTYPE project [<!ENTITY x "private">]><project/>'.encode("utf-16"),
                     '<!DOCTYPE project [<!ENTITY x "private">]><project/>'.encode("utf-16-le"),
                     pom(LICENSE.replace("Declared License", "${license.name}")),
                     pom(LICENSE.replace("https://example.org/license", "file:///secret")),
                     pom(LICENSE.replace("Declared License", "Title&#10;# Forged"))):
            with self.subTest(data=data[:100]), self.assertRaises(ValueError):
                inventory.parse_pom(data, "org.example:library:1.0")

    def test_missing_license_is_reported_as_unresolved_never_a_default(self):
        with tempfile.TemporaryDirectory() as temp:
            evidence = inventory.LicenseEvidence(Path(temp))
            response = mock.MagicMock()
            response.__enter__.return_value = response
            response.read.return_value = pom()
            response.geturl.side_effect = [base + "org/example/library/1.0/library-1.0.pom" for base in inventory.MAVEN_BASES]
            with mock.patch.object(inventory, "open_public_metadata", return_value=response):
                result = evidence.get("org.example:library:1.0")
            self.assertEqual(result["status"], "UNRESOLVED")
            self.assertEqual(result["licenses"], [])
            self.assertEqual(len(result["attempts"]), 2)

    def test_network_read_failures_remain_unresolved_without_losing_the_inventory(self):
        # Xcode's Python 3.9 has a distinct socket.timeout type (aliased only in 3.10+).
        for error in (socket.timeout("read timed out"), TimeoutError("read timed out"),
                      http.client.IncompleteRead(b"partial", 100), ConnectionResetError("reset")):
            with self.subTest(error=type(error).__name__), tempfile.TemporaryDirectory() as temp:
                evidence = inventory.LicenseEvidence(Path(temp))
                def fetch(request):
                    response = mock.MagicMock()
                    response.__enter__.return_value = response
                    response.geturl.return_value = request.full_url
                    response.read.side_effect = error
                    return response
                with mock.patch.object(inventory, "open_public_metadata", side_effect=fetch) as opened:
                    result = evidence.get("org.example:library:1.0")
                self.assertEqual(result["status"], "UNRESOLVED")
                self.assertEqual(result["licenses"], [])
                self.assertEqual(opened.call_count, len(inventory.MAVEN_BASES))
                self.assertEqual([a["error"] for a in result["attempts"]],
                                 [type(error).__name__] * len(inventory.MAVEN_BASES))
                self.assertEqual(list(Path(temp).iterdir()), [])

    def test_evidence_write_errors_are_not_misclassified_as_missing_network_metadata(self):
        with tempfile.TemporaryDirectory() as temp:
            evidence = inventory.LicenseEvidence(Path(temp))
            def fetch(request):
                response = mock.MagicMock()
                response.__enter__.return_value = response
                response.geturl.return_value = request.full_url
                response.read.return_value = pom(LICENSE)
                return response
            with (mock.patch.object(inventory, "open_public_metadata", side_effect=fetch),
                  mock.patch.object(Path, "open", side_effect=PermissionError("read-only evidence directory"))):
                with self.assertRaises(PermissionError):
                    evidence.get("org.example:library:1.0")

    def test_parent_license_is_fetched_bound_and_attributed(self):
        parent = "<parent><groupId>org.example</groupId><artifactId>parent</artifactId><version>1.0</version></parent>"
        with tempfile.TemporaryDirectory() as temp:
            evidence = inventory.LicenseEvidence(Path(temp))
            def fetch(request):
                response = mock.MagicMock()
                response.__enter__.return_value = response
                response.geturl.return_value = request.full_url
                response.read.return_value = pom(LICENSE, artifact="parent") if "/parent/" in request.full_url else pom(parent)
                return response
            with mock.patch.object(inventory, "open_public_metadata", side_effect=fetch):
                result = evidence.get("org.example:library:1.0")
            self.assertEqual(result["status"], "PUBLISHER_DECLARED")
            self.assertEqual(result["inherited_from"], "org.example:parent:1.0")
            self.assertEqual(len(list(Path(temp).glob("*.pom"))), 2)
            self.assertEqual(result["licenses"], evidence.results["org.example:parent:1.0"]["licenses"])

    def test_cycle_and_excessive_inheritance_fail_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            evidence = inventory.LicenseEvidence(Path(temp))
            with self.assertRaises(ValueError):
                evidence.get("org.example:library:1.0", ("org.example:library:1.0",))
            with self.assertRaises(ValueError):
                evidence.get("org.example:library:1.0", ("x",) * 5)

    def test_redirect_handler_refuses_before_following_another_origin(self):
        with self.assertRaises(inventory.urllib.error.URLError):
            inventory.RejectRedirect().redirect_request(None, None, 302, "redirect", {}, "http://127.0.0.1/private")

    def test_graph_receipt_binds_success_source_and_exact_consumed_bytes(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            hashes = {}
            for name in inventory.GRAPHS:
                value = graph()
                value["graph"] = name
                raw = json.dumps(value).encode()
                (directory / (name + ".json")).write_bytes(raw)
                hashes[name] = hashlib.sha256(raw).hexdigest()
            with self.assertRaises(FileNotFoundError):
                inventory.read_graphs(directory)
            receipt = {"schema_version": 1, "status": "COMPLETE", "source": graph()["source"], "graph_sha256": hashes}
            (directory / "resolution-complete.json").write_text(json.dumps(receipt))
            loaded, captured, source = inventory.read_graphs(directory)
            self.assertEqual(len(loaded), 4)
            self.assertEqual(captured, hashes)
            self.assertEqual(source, graph()["source"])
            with (directory / "androidRelease.json").open("a") as stream:
                stream.write("\n")
            with self.assertRaisesRegex(ValueError, "Graph bytes differ"):
                inventory.read_graphs(directory)

    def test_gradle_exporter_never_adds_repositories_or_weakens_verification(self):
        source = (ROOT / "scripts/verification/resolved_dependencies.init.gradle").read_text()
        self.assertIn("dependencyVerificationMode.name() != 'STRICT'", source)
        self.assertIn("componentFilter { id -> id instanceof ModuleComponentIdentifier }", source)
        self.assertIn("compilation.compileDependencyConfigurationName", source)
        self.assertIn("Source changed during graph resolution", source)
        self.assertIn("row.subMap(['component', 'name', 'bytes', 'sha256'])", source)
        self.assertIn("variants: rows.collect { it.variant }.unique().sort()", source)
        for forbidden in ("mavenLocal", "verification-metadata.xml", "repositories {", "lenient = true"):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
