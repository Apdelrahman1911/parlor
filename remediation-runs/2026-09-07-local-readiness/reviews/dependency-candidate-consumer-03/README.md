# Frozen-candidate dependency consumer — draft 03

**DRAFT: independent review and root-executed controls are required.** Nothing in
this folder has yet verified a new candidate. Archived export/research/schema
receipts remain unchanged and cannot identify a later checkout.

This small offline consumer reuses the current, hash-bound
`dependency_inventory.py` (`read_graphs`, `parse_pom`, `make_bom`) and
`third_party_notices.py` (`source_receipt`). It does not resolve dependencies,
fetch POMs, implement another inventory, run Git/Gradle, install Python packages,
inspect private files, inspect packages, sign, or publish. Module import performs
no evidence reads/writes, network calls, or process execution.

## Root-owned input contract

After freezing the clean candidate, root must run the existing exporter and
renderer in separate coordinated `run_gradle_cycle.py` cycles. Both receipts must
contain the same complete `source_before`/`source_after` snapshot and successful
stop/cleanup. This deliberately rejects old custom metadata-only receipts that
lack current-candidate source/cleanup binding.

Use the existing exporter invocation from the lane recommendation, with fresh
cycle/direct-child graph destinations. The renderer command recorded in its
receipt must be exactly:

```text
/usr/bin/python3 -B scripts/verification/dependency_inventory.py <repository-relative-graphs> <absolute-new-report-directory>
```

The report directory must be a direct child of its renderer cycle directory.
Neither directory basename nor any old export/research path is hardcoded here.

Root then creates and independently reviews a **new** binding JSON with these
exact fields (placeholders below are not valid candidate evidence):

```json
{
  "schema_version": 1,
  "source": {
    "commit": "FULL_FINAL_SHA",
    "tree": "FULL_FINAL_GIT_TREE",
    "diff_sha256": "SHA256_OF_EMPTY_DIFF",
    "source_manifest_sha256": "FINAL_ROOT_SNAPSHOT_MANIFEST_SHA256"
  },
  "export_receipt": {"path": "FRESH_EXPORT/receipt.json", "sha256": "SHA256"},
  "render_receipt": {"path": "FRESH_RESEARCH/receipt.json", "sha256": "SHA256"},
  "graphs": "FRESH_EXPORT/raw-graphs",
  "report": "FRESH_RESEARCH/report",
  "schemas": "CONSUMER_FOLDER/schemas",
  "lane": {"path": "ROOT_LANE/run_gradle_cycle.py", "sha256": "SHA256"},
  "consumer": {"path": "CONSUMER_FOLDER/candidate_consumer.py", "sha256": "SHA256"}
}
```

All paths are explicit, repository-relative, and non-symlinked; the repository
root must be canonical. Root's frozen snapshot is the authority for the complete
source inventory. The consumer validates its manifest hash and matching cycle
snapshots, then verifies the executable producer/consumer controls, strict
metadata, catalog/build notice configuration, and notice source files it uses.
It does **not** claim to recapture all source files or independently authenticate
a forged root receipt. Review and pin the binding SHA outside the input itself.

```sh
/usr/bin/python3 -B CONSUMER_FOLDER/candidate_consumer.py \
  --root /Users/abdelrahman/Projects/parlor \
  --binding NEW_BINDING.json --binding-sha256 REVIEWED_BINDING_SHA256 \
  --output NEW_CYCLE/candidate-input-verification.json
```

The output parent must already exist; existing output is never overwritten.
Only `PASS_SCOPED_CANDIDATE_INPUTS` returns zero; failures return nonzero. A
failure preserves a bounded error
type, not raw POM/schema/OS exception contents. Root still owns normal lane
receipts, immediate Gradle stop, scratch/output cleanup, and final source checks.

## What is reconciled

- Exactly four source-matched graphs and `COMPLETE` digests; strict exporter
  command, no fixture projects, expected target/configuration, variants and
  artifact hashes matching the candidate's strict verification XML.
- Exact retained POM hashes, coordinates, allowed publisher origins, access
  times, direct/inherited declarations and bounded parent chains. No network
  license lookup. Unknown/extra/missing metadata or unresolved licenses fail.
- All four exact SBOMs against the bound renderer output, including components,
  input hashes/sizes, license declarations and dependency edges; exact draft
  notice text; actual local official CycloneDX 1.6 schema validation.
- Existing source-notice validation and hashes tied to the same snapshot.
- Final re-reading of consumed files and directory inventories. This is for an
  owned, frozen workspace, not a replacement for hostile-filesystem isolation.

Only the three hash-verified official schema copies in `schemas/` are used.
`SCHEMA_PROVENANCE.json` records original URLs/access dates and immutable byte
hashes. No fresh download or schema execution occurred during drafting.

## Schema format prerequisites — do not silently waive

The consumer requires the reviewed `jsonschema==4.25.1` and
`referencing==0.36.2`. A recording `FormatChecker.check` delegates **unchanged**
library semantics and records every encountered format keyword. Reports include
exact registered, encountered and missing formats. An encountered format absent
from the registry causes nonzero `SchemaPrerequisiteUnavailable`, never PASS.
Unused schema format definitions alone do not require extra packages.

Current renderer outputs contain nonempty license URLs using `iri-reference`.
Installed jsonschema metadata lists `rfc3987-syntax>=1.1.0` for its non-GPL
optional format support (alternative: `rfc3987`). Author-side metadata lookup
found neither distribution; the actual runtime registry has **not** been run in
this draft. Root must research/provision any needed public checker in an owned
ephemeral environment and preserve package/version/hash receipts. Do not install
it globally or treat a skipped format as passed. See `SOURCE_REASONING.json`.

## Focused controls and limits

After independent approval, root runs these synthetic controls in the shared
lane, with the real offline schema/checker prerequisites available:

```sh
/usr/bin/python3 -B -m unittest discover -s CONSUMER_FOLDER -p 'test_*.py' -v
```

Fixtures contain fabricated receipt/source identities and tiny Maven graphs;
they deliberately do not prove an actual Gradle export. Public source-notice
assets and hash-bound production parser functions are reused, not mocked.
Controls exercise binding, stale/failed/unclean receipts, source changes,
graph sets/completion hashes, symlinks/unsafe paths, metadata tampering, strict
pins/variants, SBOM changes, schema bytes/optional checkers, late mutations and
exclusive failure output. All fixture files belong to `TemporaryDirectory`.

PASS proves resolved-input and source-notice integrity for the explicit binding.
It is not a final-binary SBOM, final-package delivery proof, exhaustive embedded
license audit, legal approval, physical LAN evidence, signing or Store readiness.
