# Reopened local evidence/documentation gaps — source proof 01

Reviewer: `/root/release_fix_review`; source `1f809b87c15a4deb079809bc14718a58cf0fe451`, tree `6f6912d8612f71e07d2c338d860e906333ce8aad`; 2026-09-07. Absolute prefix for every repository path below: `/Users/abdelrahman/Projects/parlor/`. Source hashes and exact read ranges are in `source-coverage-01.json`.

This is an additive current-source follow-up to three original audit IDs. Original audit receipts remain untouched. No production edits or tests were performed for these items by this reviewer. The root reviewer is independently reopening the paths before deciding scoped remediation. These items are **not external-device or Store gates** and are **not three newly confirmed application defects**.

## DOC-C4 — stale required-check count

- Current `docs/RELEASE_AUTOMATION.md:342–352` tells an operator at line347 to require the **two** Production verification jobs.
- Current `.github/workflows/production-verification.yml` declares five distinct jobs at24,113,145,177,219, with display names at25,114,146,178,220.
- The instruction is a reachable operator recipe for configuring `main`, `testing` and `release` (line344). An operator using the count literally can omit required platform checks. Earlier prose at314–319 does not make the later contradictory instruction correct.
- This does **not** establish that live GitHub branch protection is misconfigured; no live ruleset was read during this follow-up.
- Minimal correction: require every current workflow check by its actual name rather than freezing an easily stale numeric count; keep protection/approval policy unchanged. A source contract can compare names without any GitHub mutation.
- Historical classification: **DOCUMENTATION MISMATCH**, independently validated in `audit-runs/2026-09-05-source-audit/validations/DOC-C4-session_cont.md` (used here for identity mapping only, not current-source proof).

## INV-C01 — mechanical rows masquerade as reviewed-source attestations

Complete generator and its125line tests were reopened.

- `scripts/generate_review_inventory.py:316–319` obtains tracked filenames from `git ls-files --cached` and adds the output filename. It reads no file content, hashes, reviewed ranges, reviewer identities or approval attestations.
- `255–276` reads Git commit/name history, not review evidence. `307–313` infers "None identified in the independent review" or "latest review remediation" merely from that history.
- `322–359` renders all rows with hard-coded `REVIEWED` at354. `237–252` also infers dispositions; e.g. historical banner "verified" at239 without checking the banner.
- `362–369` composes these helpers directly. `388–396` only compares the mechanically rendered CSV with disk, then reports "Verified ... reviewed rows". `398–403` writes the same unsupported claim.
- `docs/review/README.md:12–19` incorrectly says the generator intentionally reviews every tracked item. `33–38` describes CSV freshness, not independent source approval.
- Actual caller: `scripts/release/validate_release_system.sh:28` invokes `--check`; root `build.gradle.kts:65–70` registers that script in the release automation gate; `139–148` includes it in `productionCheck`.
- Tests in `scripts/release/tests/test_review_inventory.py` verify deterministic rendering/untracked exclusion14–52, full-SHA overrides54–75 and merge-history stability77–110. None supplies or checks source review attestations. Those existing assertions remain useful and should not be weakened.

Reachable consequence: adding a tracked file and regenerating yields `REVIEWED` without anyone reading it. A green freshness check can therefore be mistaken for a line-by-line review attestation. This is **TEST/EVIDENCE GAP / wording**, not an application bug, proof of incomplete original human reading, or release-authorization bypass.

Suggested direction: clearly separate mechanical, heuristic inventory from independent source-bound coverage. Never label generated rows as approved reviews, verified banners or zero-findings statements. Preserve historical receipts; if a new current inventory replaces the old generated-output contract, make that transition explicit rather than silently rewriting original audit evidence. A regression should demonstrate that a new/unreviewed file cannot acquire an attested review merely from Git membership. Root owns final design and authorization.

## MT-T1 — checksum belongs to a sibling artifact

Both complete current test files were reopened:

1. `shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/P2pKitMavenProvenanceContractTest.kt:37–64`: component slicing46–53, artifact membership55–58, checksum membership anywhere in component59–62.
2. `shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/DesktopDependencyVerificationContractTest.kt:136–155`: component slicing143–147, artifact membership148–151, independent component-wide checksum membership152–155. Annotated tests26,90,116 call the helper at65/72/79,105,126.

Deterministic source-level counterexample for either predicate:

```xml
<component group="g" name="n" version="v">
  <artifact name="wanted.jar"><sha256 value="WRONG"/></artifact>
  <artifact name="other.jar"><sha256 value="EXPECTED"/></artifact>
</component>
```

A query for `wanted.jar` + `EXPECTED` finds both independent substrings and passes, although the checksum belongs to `other.jar`. The production metadata includes genuinely multi-artifact components, including the P2p Native main/cinterop siblings, Kotlin Native archives and aapt2 host variants; the predicate is not restricted to one-artifact components.

Reachability: both are registered Desktop test source files, `shared/transport-p2p/build.gradle.kts:35–95` registers file inputs for desktopTest and root `build.gradle.kts:200–202` adds the module test task to the aggregate. There is no test-level ignore protecting this predicate from execution.

Consequence: a false-green **repository contract test** when metadata hashes are assigned to wrong sibling artifacts. This does not allege that current pinned hashes are wrong or that Gradle's independent strict dependency verification accepts a wrong download.

Suggested test-only correction: structurally bind exact component coordinates → named artifact → that artifact's SHA256, using hardened local XML parsing or equally precise scoping. Regressions should reject a checksum found under a sibling artifact or another component, reject missing/malformed entries, and accept the exact binding. Do not change production dependencies or verification metadata to make it pass.

## Pending independent action and cleanup

Root has the exact source proof and froze application/test/config/script edits for its single-lane story regression cycle. Await root's independent verdict and explicit edit instructions. This reviewer started no Gradle/Xcode/app/test/emulator workers and created no generated build outputs. Only these compact additive evidence files were created.
