# Dependency chain: Linux continuation preparation

Author: `/root/dependency_chain`, 2026-09-08. This is source/preparation evidence,
not a completed export, candidate consumer, application-runtime result, or legal
approval. Coordinator owns every build/test/CI invocation and the source freeze.

## Reopened authority and actual inputs

Read `AGENTS.md`, `CONTINUE_HERE.md`, `docs/AGENT_CONTINUATION_2026-09-08.md`,
the transfer manifest, current architecture/release/game contracts and both
accepted ADRs. Reopened the complete exporter, renderer, candidate consumer,
approved adapter/helper, ordinary lane, consumer README, packet B in
`final-current-sha-verification-preparation-02.md`, adapter independent review,
schema-helper/optional-wheel research and reviews, and historical export02
integrity review plus actual four graphs/completion file.

The handoff manifest remains immutable transfer evidence. Nothing here replaces
P2pKit's pinned Maven Central dependency or changes application/protocol/privacy
behavior. All four export configurations exist unconditionally in current KMP
source. The exporter filters out first-party artifacts and performs resolution,
not Apple compilation. Linux is therefore an actionable export path; actual
four-graph resolution still has to succeed before this becomes platform evidence.
Apple linkage/runtime remains separate, justified CI work.

## Portability correction and its bounded result

`C=remediation-runs/2026-09-07-local-readiness` below.

The original ordinary lane unconditionally executed `/usr/libexec/java_home`
after allocating its evidence directory but before entering its finalizer.
That path is absent on this Linux host. The minimal reviewed correction:

- Retains Darwin's `java_home -v 21` selection, with a bounded selector timeout.
- Requires an explicit absolute Linux `JAVA_HOME`, canonicalizes it, checks a
  unique major-21 release declaration and executable Java/Javac before output
  allocation. There is no PATH/system-Gradle fallback.
- Executes actual Java specification/home and Javac major-version probes through
  the existing owned invocation **inside** its finalizer. Probe failures remain
  failures, with Gradle stop, owned-worker retirement and exact cleanup.
- Resolves Android `dexdump` only when package inspection needs it, from agreeing
  explicit SDK roots (or the historical Darwin-only SDK location), still fixed
  to build-tools `36.0.0`; no alternative-version fallback.
- Leaves existing strict flags, signer removal, source/runner identity,
  process-lifetime checks and output cleanup logic unchanged.

Lane SHA256: `1b9e882514bfe5386785d5a71ae32fdc2bc3aa4cc1dd24532709899fbf4f41bc`.
Portability-test SHA256:
`27283e41eb10bfdd9f9fdab1908d45575da75ce9322936d19ced11cd3de3eb33`.

Coordinator's actual first Linux cycle was reopened directly:
`C/evidence/continuation-linux-lane-controls-01/receipt.json`, SHA256
`9538e853b2a868e1f3fac43f37b8363901f095372ec27c1676fa2c5da864c76b`.
Its raw log contains **21 executed tests, OK**. Java specification 21 and actual
Javac `21.0.12` were observed. Cycle/exit/stop passed; source/control snapshots
equal; no remaining workers, outputs, retained outputs or cleanup errors.
This is lane/control evidence, not a dependency export or application launch.
The first cycle does not itself capture the test file as a separate control
manifest: preserve the independent review and coordinator's later exact map.

Pre-existing preflight limits: malformed heap selection or missing command can
still raise after scratch allocation but before the main try/finally; this patch
does not refactor those unrelated paths. Neither path starts the requested
builder. Valid approved invocations below do not exercise them. Treat such a
failure as needing explicit owned-output inspection/cleanup, not PASS.

## Missing schema prerequisites and additive bootstrap

Read-only distribution metadata found **none** of jsonschema, referencing,
attrs, jsonschema-specifications, rpds-py or optional parsers installed for
`/usr/bin/python3` (CPython 3.12.3). The unchanged approved helper provisions
only rfc3987-syntax/lark and correctly refuses absent base engines. No global
installation was performed.

New `controls/run_candidate_with_schema.py` supplies six explicitly pinned
public wheels in a new cycle-owned scratch child: jsonschema `4.25.1`,
referencing `0.36.2`, attrs `25.3.0`, jsonschema-specifications `2025.4.1`,
rpds-py `0.27.1`, typing-extensions `4.15.0`. The ordinary required dependency
closure was checked against official PyPI and wheel METADATA; extras are not
silently enabled. This bootstrap is deliberately Linux x86_64 CPython 3.12 /
glibc >=2.17 only. Its published rpds wheel contains native code; no source
build, ABI guess, or claim of a pure-Python environment is made.

`schema-base-prerequisites-research-01.json` retains official metadata URLs,
timestamps/digests, exact wheel origins/sizes/hashes and all member identities.
Those bytes were fetched and inspected **in memory**, not extracted or imported
during research. SHA256:
`54c9f96db0ead50008cab5c8c960c0540351922f949757e03dc91b8c5328b13e`.

The additive bootstrap leaves approved adapter/helper/consumer/test bytes
unchanged. It has no arbitrary script/command option: either binding+digest
invokes the fixed approved adapter, or `--controls` invokes the existing fixed
16-test driver to qualify the new Python environment. Downloads refuse redirects
and enforce exact byte identities; extraction rejects traversal, symlinks,
foreign namespaces, `.pth`, duplicates, and excessive expansion. Version,
module-resolution, actual loaded-module origin (including native rpds), final
package/control integrity, owned inode cleanup and coupled receipts are required.
No pip, global caches, package setup, or format-checker waiver is used.

This source and its **26 new synthetic test methods** require independent review
and coordinator execution. Their later review/result receipts, not this initial
preparation wording, are authoritative for execution status.

## Exact execution sequence after reviewed source + inventory freeze

The following names were unused at preparation time; coordinator must recheck
before use. Do not overlap with another lane. Native A/B are never nested in the
ordinary lane. Preserve a failed cycle under its original name.

```sh
C=remediation-runs/2026-09-07-local-readiness
R="$C/reviews"
N=remediation-runs/2026-09-08-continuation
ROOT=$(pwd -P)

# Only after independent bootstrap approval; focused controls, not candidate evidence:
/usr/bin/python3 -B "$C/run_gradle_cycle.py" schema-base-bootstrap-controls-01 \
  --command /usr/bin/python3 -B "$N/controls/test_schema_base_bootstrap.py"
/usr/bin/python3 -B "$C/run_gradle_cycle.py" candidate-schema-linux-controls-01 \
  --command /usr/bin/python3 -B "$N/controls/run_candidate_with_schema.py" --controls

# Then commit reviewed source/controls; regenerate and commit inventory only;
# check it, push/freeze the SHA/tree, and obtain fresh independent bindings.
/usr/bin/python3 -B "$C/run_gradle_cycle.py" final-dependency-export-linux-01 \
  writeResolvedDependencyInventory -I scripts/verification/resolved_dependencies.init.gradle \
  -Pparlor.dependencyInventoryDir="$ROOT/$C/evidence/final-dependency-export-linux-01/raw-graphs"
/usr/bin/python3 -B "$C/run_gradle_cycle.py" final-dependency-render-linux-01 \
  --command /usr/bin/python3 -B scripts/verification/dependency_inventory.py \
  "$C/evidence/final-dependency-export-linux-01/raw-graphs" \
  "$ROOT/$C/evidence/final-dependency-render-linux-01/report"
```

Do not change the renderer interpreter string or relative graph argument: the
approved consumer validates that exact producer command. Export/render outputs
must be new direct children of their own cycle, outside scratch. Require both
outer receipts PASS/exit0/stop0, equal before/after full source and runner
snapshots, no deferred signals/cleanup errors/remaining or retained outputs, and
serial timestamps. Require the manifest to say COMPLETE with exactly the four
graphs and exact graph hashes, matching final source/empty diff.

Inspect actual component/edge/variant/artifact changes rather than asserting
historical counts. Actual old export02 inputs were 239/129 components/artifacts
for Android and 167/81 for each iOS graph; their retained completion hashes match
their old bytes. Those are comparison data, **not required counts** or current
source evidence. The renderer must return no unresolved metadata and preserve
all four SBOMs, exact publisher POM bytes/declarations and notice draft.

### Fresh binding procedure

Create a **new repository-relative** JSON after successful export/render. Use
the exact keys in the approved consumer README: `schema_version`, `source`,
`export_receipt`, `render_receipt`, `graphs`, `report`, `schemas`, `lane`,
`consumer`. `source` contains final full Git `commit`/`tree`, empty-diff SHA256
and the current lane source-manifest SHA256. Both producer source snapshots must
equal a fresh `run_gradle_cycle.identity()` observation (normalize through JSON
when comparing Python tuples with receipt arrays). Its tracked status must be
empty; manifests must match their recorded hashes. Producer runner manifests
must match the current lane's `runner_identity(False)`.

Hash the exact export/render receipt files, lane, and unchanged consumer into
their `{path, sha256}` references. Graph/report paths are those above; schema
directory remains `R/dependency-candidate-consumer-03/schemas`. Do not add keys
to the strict consumer binding to carry bootstrap metadata; independently pin
the new bootstrap, research/test files and old adapter/helper alongside it.
Have a different agent inspect and approve the resulting binding bytes and
hash, then run:

```sh
# BINDING is the new explicit repository-relative JSON; REVIEWED_SHA is its
# independently approved SHA256. These are intentionally not invented values.
/usr/bin/python3 -B "$C/run_gradle_cycle.py" final-candidate-input-linux-01 \
  --command /usr/bin/python3 -B "$N/controls/run_candidate_with_schema.py" \
  "$BINDING" "$REVIEWED_SHA"
```

Accept only the conjunction of all **four** complete receipts:

1. Consumer `PASS_SCOPED_CANDIDATE_INPUTS` for all four bound SBOMs, no unresolved
   metadata/missing encountered formats and exact source-notice proof.
2. Unchanged adapter `PASS_SCOPED_CANDIDATE_INPUT_EXECUTION`, consumer exit0,
   no error field, optional parser cleanup true.
3. Bootstrap `PASS_OWNED_SCHEMA_PREREQUISITES`, inner exit0, no error field,
   exact package/import/control integrity and base cleanup true.
4. Ordinary outer lane PASS/exit0/stop0, current source equal to the independently
   frozen binding and before/after source/control equality, complete worker and
   output/scratch cleanup. Its success label alone does not compare a possibly
   stale candidate binding with current Git; coordinator must reconcile them.

The source notices plus resolved-input SBOMs are not final-binary completeness,
Store, physical-device, or legal certification. Package delivery evidence is
still supplied by final qualification at the frozen source.

## Cleanup/ownership

This agent ran no builders, tests, native/CI jobs, package imports/installation,
or Git mutation. New source/review files are intentional deliverables. Public
wheel research allocated only process memory; no extracted or cached wheel tree
exists from research. Coordinator-run ordinary lanes preserve reports then stop
Gradle and remove only declared live module outputs/scratch; archived evidence
`build/` segments and all failed receipts remain untouched. Base/optional package
trees must both be gone before candidate acceptance, then the outer process must
exit. Global caches, unrelated work, signing material and source are never
cleanup targets.
