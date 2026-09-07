#!/usr/bin/env python3
"""Create resolved-input CycloneDX inventories and publisher-license evidence.

No dependency resolution, cache modification, signing, or legal conclusions.
Public POMs are evidence only: they cannot change Gradle's strictly verified graph.
"""
from __future__ import annotations

import argparse
import datetime
import hashlib
import http.client
import json
from pathlib import Path
import re
import socket
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET


MAVEN_BASES = ("https://repo.maven.apache.org/maven2/", "https://dl.google.com/dl/android/maven2/")
GRAPHS = ("androidRelease", "iosArm64", "iosSimulatorArm64", "iosX64")
MAX_POM_BYTES = 1024 * 1024
MAX_GRAPH_BYTES = 4 * 1024 * 1024


def coordinates(identifier: str) -> tuple[str, str, str]:
    pieces = identifier.split(":")
    if len(pieces) != 3 or any(not re.fullmatch(r"[A-Za-z0-9_.-]+", p) for p in pieces):
        raise ValueError("Invalid Maven coordinates")
    if any(part in ("", ".", "..") for p in pieces for part in p.split(".")):
        raise ValueError("Unsafe Maven coordinates")
    return tuple(pieces)


def purl(identifier: str) -> str:
    group, name, version = coordinates(identifier)
    return f"pkg:maven/{urllib.parse.quote(group, safe='')}/{urllib.parse.quote(name, safe='')}@{urllib.parse.quote(version, safe='')}"


def validate_graph(graph: dict) -> dict:
    if graph.get("schema_version") != 1 or graph.get("graph") not in GRAPHS:
        raise ValueError("Unknown resolved graph")
    if graph.get("strict_dependency_verification") is not True:
        raise ValueError("Strict dependency verification evidence is required")
    source = graph["source"]
    for key, length in (("commit", 40), ("tree", 40), ("diff_sha256", 64)):
        if not re.fullmatch("[a-f0-9]{" + str(length) + "}", source[key]):
            raise ValueError("Missing or invalid graph source identity")
    ids = set()
    for component in graph["components"]:
        identifier = component["id"]
        if identifier in ids:
            raise ValueError("Duplicate component")
        ids.add(identifier)
        if component["kind"] == "maven":
            coordinates(identifier)
        elif component["kind"] != "project" or not identifier.startswith("project:"):
            raise ValueError("Unknown component kind")
    if graph["root_component"] not in ids:
        raise ValueError("Missing root component")
    for component in graph["components"]:
        for dependency in component["dependencies"]:
            if dependency["selected"] not in ids or type(dependency["constraint"]) is not bool:
                raise ValueError("Dangling or invalid dependency edge")
    artifacts = set()
    for artifact in graph["artifacts"]:
        coordinates(artifact["component"])
        key = (artifact["component"], artifact["name"])
        if artifact["component"] not in ids or key in artifacts:
            raise ValueError("Missing or duplicate artifact owner")
        artifacts.add(key)
        if (not re.fullmatch(r"[A-Za-z0-9_.+-]+", artifact["name"]) or
                not re.fullmatch(r"[a-f0-9]{64}", artifact["sha256"]) or
                type(artifact["bytes"]) is not int or artifact["bytes"] <= 0):
            raise ValueError("Invalid artifact identity")
    return graph


def parse_pom(data: bytes, expected: str) -> dict:
    text = data.decode("utf-8-sig")
    if (len(data) > MAX_POM_BYTES or "\0" in text or "<!DOCTYPE" in text.upper() or "<!ENTITY" in text.upper() or
            re.search(r"encoding\s*=\s*['\"](?!utf-?8['\"])", text[:200], re.IGNORECASE)):
        raise ValueError("Unsafe or oversized POM")
    element = ET.fromstring(text)
    if element.tag not in ("project", "{http://maven.apache.org/POM/4.0.0}project"):
        raise ValueError("Unexpected POM root")
    prefix = "{http://maven.apache.org/POM/4.0.0}" if element.tag.startswith("{") else ""

    def value(path):
        found = element.find("/".join(prefix + part for part in path.split("/")))
        return found.text.strip() if found is not None and found.text else ""

    declared = ":".join((value("groupId") or value("parent/groupId"), value("artifactId"),
                         value("version") or value("parent/version")))
    if declared != expected:
        raise ValueError("POM identity does not match resolved component")
    licenses = []
    for item in element.findall(f"{prefix}licenses/{prefix}license"):
        name = (item.findtext(f"{prefix}name") or "").strip()
        url = (item.findtext(f"{prefix}url") or "").strip()
        if (not name or len(name) > 500 or "${" in name or "${" in url or
                any(ord(char) < 32 or ord(char) == 127 for char in name + url)):
            raise ValueError("Unresolved license declaration")
        if url and (urllib.parse.urlsplit(url).scheme not in ("http", "https") or
                    not urllib.parse.urlsplit(url).netloc or len(url) > 2000):
            raise ValueError("Invalid license URL")
        licenses.append({"name": name, "url": url})
    parent = None
    if value("parent/artifactId"):
        parent = ":".join(value("parent/" + key) for key in ("groupId", "artifactId", "version"))
        coordinates(parent)
    return {"licenses": licenses, "parent": parent}


class RejectRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        raise urllib.error.URLError("Metadata redirects are not permitted")


def open_public_metadata(request):
    return urllib.request.build_opener(RejectRedirect()).open(request, timeout=30)


class LicenseEvidence:
    def __init__(self, directory: Path):
        self.directory = directory
        self.results = {}

    def get(self, identifier: str, ancestors=()) -> dict:
        if identifier in ancestors or len(ancestors) >= 5:
            raise ValueError("Cyclic or excessive POM license inheritance")
        if identifier in self.results:
            return self.results[identifier]
        group, name, version = coordinates(identifier)
        suffix = f"{group.replace('.', '/')}/{name}/{version}/{name}-{version}.pom"
        bases = tuple(reversed(MAVEN_BASES)) if group.startswith(("androidx.", "com.android.")) else MAVEN_BASES
        failures = []
        for base in bases:
            url = base + suffix
            try:
                request = urllib.request.Request(url, headers={"User-Agent": "Parlor-dependency-evidence/1.0"})
                with open_public_metadata(request) as response:
                    if response.geturl() != url:
                        raise ValueError("Unexpected metadata redirect")
                    data = response.read(MAX_POM_BYTES + 1)
                parsed = parse_pom(data, identifier)
                digest = hashlib.sha256(data).hexdigest()
                filename = digest + ".pom"
                path = self.directory / filename
                if not path.exists():
                    with path.open("xb") as stream:
                        stream.write(data)
                result = dict(component=identifier, status="PUBLISHER_DECLARED", licenses=parsed["licenses"],
                              url=url, sha256=digest, file=filename,
                              accessed_at=datetime.datetime.now(datetime.timezone.utc).isoformat())
                if not result["licenses"]:
                    if not parsed["parent"]:
                        raise ValueError("No declared or inherited license")
                    parent = self.get(parsed["parent"], (*ancestors, identifier))
                    if parent["status"] != "PUBLISHER_DECLARED":
                        raise ValueError("Parent license remains unresolved")
                    result.update(licenses=parent["licenses"], inherited_from=parsed["parent"])
                self.results[identifier] = result
                return result
            # Response reads can raise directly (rather than urllib's URLError).
            # socket.timeout is distinct from TimeoutError on supported Python 3.9.
            # Do not catch arbitrary OSError: disk/evidence write failures must abort.
            except (urllib.error.URLError, ValueError, ET.ParseError, TimeoutError,
                    socket.timeout, http.client.HTTPException, ConnectionError) as error:
                failures.append({"url": url, "error": type(error).__name__, "reason": str(error)[:300]})
        result = dict(component=identifier, status="UNRESOLVED", licenses=[], attempts=failures)
        self.results[identifier] = result
        return result


def make_bom(graph: dict, licenses: dict, source: dict) -> dict:
    validate_graph(graph)
    refs = {c["id"]: purl(c["id"]) if c["kind"] == "maven" else c["id"] for c in graph["components"]}
    components = []
    for row in graph["components"]:
        identifier = row["id"]
        component = {"type": "library", "bom-ref": refs[identifier], "name": identifier}
        if row["kind"] == "maven":
            group, name, version = coordinates(identifier)
            component.update(group=group, name=name, version=version, purl=refs[identifier])
            declared = licenses[identifier]
            component["properties"] = [{"name": "parlor:license-status", "value": declared["status"]}]
            component["licenses"] = [{"license": {key: value for key, value in license.items() if value}}
                                     for license in declared["licenses"]]
            component["components"] = [
                {"type": "file", "name": a["name"], "hashes": [{"alg": "SHA-256", "content": a["sha256"]}],
                 "properties": [{"name": "parlor:input-bytes", "value": str(a["bytes"])}]}
                for a in graph["artifacts"] if a["component"] == identifier]
        else:
            component["properties"] = [{"name": "parlor:license-status", "value": "OWNER_DECISION_REQUIRED"}]
        components.append(component)
    return {"bomFormat": "CycloneDX", "specVersion": "1.6", "version": 1,
            "metadata": {"component": {"type": "application", "name": "Parlor", "version": source["commit"]},
                         "properties": [{"name": "parlor:" + key, "value": str(value)} for key, value in
                                        {**source, "graph": graph["graph"], "configuration": graph["configuration"],
                                         "scope": "resolved-inputs; not post-shrinker or binary-internal completeness; declared licenses, not legal approval"}.items()]},
            "components": components,
            "dependencies": [{"ref": refs[c["id"]], "dependsOn": sorted({refs[d["selected"]] for d in c["dependencies"] if not d["constraint"]})}
                             for c in graph["components"]]}


def read_graphs(directory: Path):
    def bounded(path, limit):
        with path.open("rb") as stream:
            raw = stream.read(limit + 1)
        if len(raw) > limit:
            raise ValueError("Oversized resolved-graph input")
        return raw

    manifest = json.loads(bounded(directory / "resolution-complete.json", 10000))
    if manifest.get("schema_version") != 1 or manifest.get("status") != "COMPLETE":
        raise ValueError("A successful source-bound resolution receipt is required")
    graphs, hashes = [], {}
    for name in GRAPHS:
        raw = bounded(directory / (name + ".json"), MAX_GRAPH_BYTES)
        hashes[name] = hashlib.sha256(raw).hexdigest()
        graph = validate_graph(json.loads(raw))
        if graph["graph"] != name or graph["source"] != manifest["source"]:
            raise ValueError("Graph identity differs from completion manifest")
        graphs.append(graph)
    if hashes != manifest["graph_sha256"]:
        raise ValueError("Graph bytes differ from completed resolution")
    return graphs, hashes, manifest["source"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("graph_directory", type=Path)
    parser.add_argument("output_directory", type=Path)
    args = parser.parse_args()
    graphs, graph_hashes, source = read_graphs(args.graph_directory)
    args.output_directory.mkdir(exist_ok=False)
    metadata = args.output_directory / "metadata"
    metadata.mkdir()
    evidence = LicenseEvidence(metadata)
    ids = sorted({c["id"] for g in graphs for c in g["components"] if c["kind"] == "maven"})
    declared = {identifier: evidence.get(identifier) for identifier in ids}
    if read_graphs(args.graph_directory) != (graphs, graph_hashes, source):
        raise ValueError("Graph evidence changed during metadata research")
    report = dict(schema_version=1, source=source, graph_sha256=graph_hashes, declarations=evidence.results,
                  unresolved=[identifier for identifier in ids if declared[identifier]["status"] == "UNRESOLVED"],
                  limitations=["Metadata describes publisher declarations, not legal conclusions or approved notices.",
                               "Resolved inputs are a superset of R8 output; compiler/platform runtimes, bundled assets and code embedded inside third-party binaries need separate inspection.",
                               "POMs are public research evidence outside Gradle resolution, never verification-metadata replacements."])
    (args.output_directory / "license-evidence.json").write_text(json.dumps(report, indent=2) + "\n")
    for graph in graphs:
        bom = make_bom(graph, declared, source)
        (args.output_directory / (graph["graph"] + ".cdx.json")).write_text(json.dumps(bom, indent=2) + "\n")
    notice = ["# Third-party declaration draft", "", "Generated from exact resolved inputs. NOT final legal approval or a complete redistribution notice.", ""]
    for identifier in ids:
        row = declared[identifier]
        notice += ["## " + identifier, "", "Status: " + row["status"]]
        notice += ["- " + item["name"] + (" — " + item["url"] if item["url"] else "") for item in row["licenses"]]
        if row.get("url"):
            notice += ["- Publisher metadata: " + row["url"]]
        notice += [""]
    (args.output_directory / "THIRD_PARTY_DECLARATIONS_DRAFT.md").write_text("\n".join(notice))
    print(json.dumps(dict(graphs=len(graphs), components=len(ids), unresolved=report["unresolved"])))
    return 1 if report["unresolved"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
