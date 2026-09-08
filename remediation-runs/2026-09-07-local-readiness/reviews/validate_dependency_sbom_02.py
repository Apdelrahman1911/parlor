from pathlib import Path
import datetime, hashlib, importlib.metadata, json, urllib.request
import jsonschema
ROOT = Path(__file__).resolve().parents[3]
BASE = ROOT / "remediation-runs/2026-09-07-local-readiness/evidence"
OUT = BASE / "dependency-sbom-schema-02"
OUT.mkdir()
refs = []
schemas = {}
original = BASE / "dependency-inventory-preparation-01/bom-1.6.schema.json"
raw = original.read_bytes()
assert hashlib.sha256(raw).hexdigest() == "3e92dddbc30cf7f6a02b80f0942b1a4cfd4fb1c26f1dfc4310afa9d613cafb93"
schemas["bom-1.6.schema.json"] = json.loads(raw)
refs.append({"file": str(original.relative_to(ROOT)), "sha256": hashlib.sha256(raw).hexdigest()})
for name in ("spdx.schema.json", "jsf-0.82.schema.json"):
    url = "https://raw.githubusercontent.com/CycloneDX/specification/1.6/schema/" + name
    with urllib.request.urlopen(url, timeout=30) as response:
        assert response.geturl() == url
        data = response.read(1024 * 1024 + 1)
    assert len(data) <= 1024 * 1024
    (OUT / name).write_bytes(data)
    schemas[name] = json.loads(data)
    refs.append({"url": url, "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data),
                 "accessed_at": datetime.datetime.now(datetime.timezone.utc).isoformat()})
store = {"http://cyclonedx.org/schema/" + name: value for name, value in schemas.items()}
store.update({"https://cyclonedx.org/schema/" + name: value for name, value in schemas.items()})
def denied(uri):
    raise ValueError("Unresolved non-local schema reference: " + uri)
resolver = jsonschema.RefResolver.from_schema(schemas["bom-1.6.schema.json"], store=store,
            handlers={"http": denied, "https": denied, "file": denied})
validator_type = jsonschema.validators.validator_for(schemas["bom-1.6.schema.json"])
validator_type.check_schema(schemas["bom-1.6.schema.json"])
validator = validator_type(schemas["bom-1.6.schema.json"], resolver=resolver,
                          format_checker=jsonschema.FormatChecker())
results = []
for file in sorted((BASE / "dependency-license-research-03/report").glob("*.cdx.json")):
    data = file.read_bytes()
    errors = list(validator.iter_errors(json.loads(data)))
    results.append({"file": str(file.relative_to(ROOT)), "sha256": hashlib.sha256(data).hexdigest(),
                    "errors": [{"path": list(e.path), "message": e.message} for e in errors]})
assert len(results) == 4
report = {"checked_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
          "result": "FAIL" if any(r["errors"] for r in results) else "PASS",
          "jsonschema_version": importlib.metadata.version("jsonschema"), "schemas": refs,
          "results": results, "scope": "Official1.6 schema shape/format only; no legal or final-binary completeness approval"}
(OUT / "schema-validation.json").write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
raise SystemExit(0 if report["result"] == "PASS" else 1)
