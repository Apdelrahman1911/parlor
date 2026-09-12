# MT-T1 — Independent validation of checksum assertion scope

- Finder: `/root/whodunit_cont`. Independent validator: `/root/session_cont`.
- **Classification: TEST/EVIDENCE GAP; Low.** Concrete false-green assertion boundary, not a shipping application defect, wrong current checksum, supply-chain compromise or Gradle verification bypass.
- Source: `main` at `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, Git tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; tracked files unchanged. Absolute root `/Users/abdelrahman/Projects/parlor`. Fresh source hashes in `evidence/MT-T1-independent-session_cont/source-hashes.json`.

## Reopened source and reachability

Full files independently reopened: `shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/P2pKitMavenProvenanceContractTest.kt:1–166`, `DesktopDependencyVerificationContractTest.kt:1–182`, `shared/transport-p2p/build.gradle.kts:1–95`. The module is included and creates real Desktop JUnit tasks through its convention (already reviewed). Build35–95 marks current dependency metadata/catalog/settings as test inputs, so this is not an orphan sample or stale task-input claim.

The first contract45–62 locates exactly the requested component, but after that independently searches its whole substring for the artifact marker and expected hash. The second contract136–155 has the same ownership boundary despite different substring APIs. Neither checks that the hash is a descendant of the requested artifact. Locate-root helpers, missing-component checks, P2p version/catalog checks, and metadata enablement assertions do not restore that association.

## Independent deterministic proof

The current XML `gradle/verification-metadata.xml:4789–4796` correctly places module hash `8dfc573fe79bde06286c1183794ae72f622f6001610f97bb11871cace66ba463` under `p2p-core-0.7.0-rc3.module` and a different hash under its sibling `p2p-core-metadata-0.7.0-rc3.jar`. This validator read the actual current block.

For an in-memory counterexample, keep both artifact tags in that component, replace the module hash with64zeros, and replace the sibling's hash with the old module hash. The first assertion still finds the module tag; the second still finds its expected hash in the same component, although under the wrong artifact. All other expected components/hashes remain unchanged. There is no expected metadata-JAR entry in the15-entry provenance table that would reject this mutation. Thus every present provenance assertion evaluates true while the selected module's artifact-specific checksum is false. This is complete source-level proof and does not require mutating actual metadata.

The independent validator read the complete isolated `reproducers/MT_T1ChecksumContractWitness.py`. Its original-file check,15-entry table extraction, in-memory-only mutation, one-occurrence guards, artifact-aware XML check and deliberate final failure correctly express the above counterexample. Its component-wide search is equivalent to the inspected Kotlin algorithm for this exact fixture. No test command was executed by this validator; any root execution must be cited separately.

## Counter-evidence and impact limits

- Current selected hashes are correct; this finding cannot establish any currently consumed untrusted bytes. The broader metadata/advisory review belongs to the separate metadata audit.
- Strict Gradle verification uses checksums under each artifact. A real build consuming the artificially wrong selected module checksum would reject the real module; this weakness does not bypass that boundary. Gradle8.13 official dependency-verification documentation was independently consulted2026-09-05: `https://docs.gradle.org/8.13/userguide/dependency_verification.html`. Per-artifact examples, checksums enabled while signatures disabled, and artifact-scoped also-trust entries are retained with retrieval hash in `evidence/MT-T1-independent-session_cont/research.json`.
- Host-selected builds may never consume every cross-platform artifact, which is why a source-level cross-host contract can be useful, but no unexecuted host gate is called PASS.
- This groups two sibling helpers under one root cause. It does not inflate every table row into a separate issue.

## Recommended remediation and tests

After authorization, parse namespace-aware XML and select group/name/version/artifact before reading checksums, or otherwise strictly isolate the exact artifact subtree. Explicitly decide the permitted alternate hashes rather than treating a matching marker anywhere as sufficient. Add synthetic moved/swapped sibling, missing artifact/component, wrong hash and allowed-alternate mutation tests. Preserve strict verification and genuine current hashes; do not regenerate metadata to hide failures.

**Independent conclusion:** Low test-quality gap confirmed by complete source-level counterexample. No Gradle, Xcode, app/server, private-input or Store operation performed by this reviewer; writes limited to audit evidence.
