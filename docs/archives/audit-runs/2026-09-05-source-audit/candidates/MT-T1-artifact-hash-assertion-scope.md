# MT-T1 — Checksum contract assertions do not bind hashes to artifact elements

**INDEPENDENTLY VALIDATED — TEST/EVIDENCE GAP, Low.** Finder `/root/whodunit_cont`; independent validator `/root/session_cont` (`validations/MT-T1-session_cont.md`, complete source-level counterexample; report reread by finder). Not a claimed wrong current dependency checksum, Gradle verification bypass, malicious dependency, or application defect.

Baseline main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Absolute repository root `/Users/abdelrahman/Projects/parlor/`.

## Source path and expected boundary

- `shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/P2pKitMavenProvenanceContractTest.kt:45–62` locates a component block and separately tests whether that block contains the expected artifact marker and the expected SHA256. It never restricts the hash search to the named artifact's child element. The test title37 and failure message61 claim an artifact-specific checksum contract; all15 entries88–163 are intended to pin named artifact bytes.
- `shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/DesktopDependencyVerificationContractTest.kt:136–155` repeats the same component-wide marker/hash logic for the host runtime/toolchain artifacts. This is one shared assertion-boundary weakness, not two defect counts.
- `shared/transport-p2p/build.gradle.kts:31–95` attaches the actual metadata as desktopTest inputs. These are real registered first-party tests, not orphan examples. Full tests166/182lines and build95lines were read.

## Complete synthetic counterexample

Current metadata is correct for the selected example: `gradle/verification-metadata.xml:4789–4796` has `p2p-core-0.7.0-rc3.module` with SHA256 `8dfc573fe79bde06286c1183794ae72f622f6001610f97bb11871cace66ba463` and a distinct metadata JAR checksum.

In an **isolated in-memory copy only**, change the module artifact's hash to64zeros and move its expected hash to the sibling metadata JAR. Both artifacts remain present and the expected module hash remains somewhere in the component, so every existing P2pKit contract assertion still evaluates true. An artifact-aware parse sees the actual module hash is wrong. No module/version change or test modification is necessary for this false-green behavior.

`reproducers/MT_T1ChecksumContractWitness.py` expresses this source-level counterexample, checks all existing15 expected-artifact pairs using the actual test's component-wide algorithm, and deliberately fails the safer expectation that the mutation must be rejected. It was **not executed by the finder**; root owns any separate execution receipt. It writes no repository file and uses no network, build, private input or global cache.

## Counter-evidence and limits

Gradle itself binds SHA checksums to artifact elements. A real build consuming the deliberately corrupted copy would fail strict verification for the real module/JAR; this test weakness does not disable that safeguard. The actual metadata parsed during this audit has no such swap and matches the displayed expected hashes. The separate test also checks exact P2pKit catalog coordinates and source repositories, which this witness leaves unchanged. Repeated allowed versions and scoped alternate hashes elsewhere in the XML are not proof of this problem or a malicious artifact.

This is therefore a limited test-quality finding: a named provenance regression test can miss an artifact-specific checksum mutation and overstates what its assertions prove. It does not justify weakening metadata, changing dependencies, or claiming current published bytes are untrusted.

## Suggested remediation

Parse namespace-aware XML or isolate the exact `<artifact>` subtree before checking its checksum. Assert the expected hash belongs to that artifact and define whether extra accepted hashes are permitted. Add in-memory mutation cases for moved/swapped sibling hashes, wrong artifact name, unexpected alternates and missing component. Keep strict Gradle verification and legitimate multi-platform checks unchanged.
