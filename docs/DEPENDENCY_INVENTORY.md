# Resolved dependency and license evidence

Parlor ships Android and iOS; Desktop is a development/test target. Dependency
catalog entries are requested versions, not a complete resolved dependency list.
Use the opt-in exporter to record the actual Android release runtime graph and
the `main` Kotlin/Native compilation graphs for all three Apple targets:

```sh
./gradlew writeResolvedDependencyInventory \
  -I scripts/verification/resolved_dependencies.init.gradle \
  -Pparlor.dependencyInventoryDir=/absolute/fresh/evidence/graphs \
  --no-configuration-cache --dependency-verification=strict
```

The parent evidence directory must exist; the graph directory must not. The
exporter records project/module edges, selected variants, artifact sizes and
SHA-256 values, and commit/tree/working-diff identity. It refuses unreviewed
untracked build inputs, unresolved dependencies, non-strict verification and
source changes. Only a complete successful export writes
`resolution-complete.json`. Identical artifact bytes selected through multiple
variants are coalesced; conflicting same-name artifacts are not hidden.

Preserve the graph evidence, immediately run `./gradlew --stop`, and remove only
that cycle's generated build directories, including build-logic outputs. Do not
delete global caches or source. If using `./gradlew clean --no-daemon`, stop
Gradle again afterward. Use the repository's coordinated build lane when other
verification work is active.

## Publisher declarations, not legal approval

```sh
python3 -B scripts/verification/dependency_inventory.py \
  /absolute/fresh/evidence/graphs /absolute/fresh/evidence/declarations
```

The renderer verifies the completed graph hashes, fetches bounded public POMs
only from Maven Central/Google Maven, rejects redirects and unsafe XML, and
records exact metadata bytes, identity, URL and access time. License inheritance
is bounded and attributed. Missing/failed metadata stays `UNRESOLVED` and produces
a nonzero exit; network read failures do not erase the rest of the inventory.
Evidence-write failures still abort. Python 3.9 and later are supported.

Outputs are four CycloneDX 1.6 JSON input SBOMs, `license-evidence.json`, saved POM
evidence and `THIRD_PARTY_DECLARATIONS_DRAFT.md`. Validate SBOMs against the
[official 1.6 schemas](https://github.com/CycloneDX/specification/tree/1.6/schema).
POM research is **not** Gradle dependency-verification metadata and never changes
resolution or substitutes for an approved artifact digest.

## Required interpretation

- Resolved inputs are not proof that every input survives R8/native linking.
- POM license labels do not cover every component embedded inside an AAR, JAR,
  KLIB or static archive. Skiko's Apache label, for example, does not replace
  Skia and its embedded third-party notices.
- Compiler runtimes, bundled fonts, authored stories, notices and final packaged
  resources need separate inspection. Do not label compiler-only dependencies
  as shipped, or infer a license obligation from a filename alone.
- Check applicable copyright, NOTICE, custom/patent and binary-redistribution
  terms from the exact upstream revision. Preserve existing embedded notices.
  Record object-code exceptions and rejected candidates rather than inflating
  a missing-notice count.
- The draft is not an approved redistribution notice or a license for Parlor's
  own code/content. Publisher distribution rights, trademarks, content rights
  and Store declarations require explicit owner approval.

Rerun the export after graph/source changes. Keep failing receipts intact and
record new evidence separately; neither a successful export nor a valid SBOM
schema establishes Store readiness.
