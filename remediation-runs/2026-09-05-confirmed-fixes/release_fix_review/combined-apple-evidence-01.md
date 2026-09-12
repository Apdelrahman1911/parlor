# Apple final gate — independent evidence approval

**APPROVED for local Apple analysis and framework linkage only.** Reviewer `/root/release_fix_review`; implementation/execution owner `/root`. No reviewer builds, source edits, Git mutations, worker stops, signing or Store operations.

## Source and command

`main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, Git tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Dirty 658-file source manifest `e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7` and tracked diff `60b555c0dd323a086e959ffbc5aa3fd7c6629ae1d0ad016021572e7ae272e4dd` agree before/after/current. Policy, workflows, dependency pins and strict verification metadata match remediation baseline.

`productionAppleCheck --no-build-cache --continue` ran with strict dependency verification, no parallelism, one worker, in-process compiler and 6GiB heap. Complete 454-line log inspected; exit0. Root task wiring includes Apple type-aware Detekt and exactly three ComposeApp release framework links, **not Swift/Xcode wrapper or runtime tests**.

## Actual execution

- All three framework links freshly execute, with 11 shipping Kotlin module compiles per architecture. No FROM-CACHE tasks.
- Four fresh `detektMetadataIosMain` reports (`composeApp`, design-system, networking, transport-p2p): **zero findings**.
- Other 113 registered Apple analysis tasks are **NO-SOURCE**. Actual file inventory places 16 Apple production files in those four iosMain groups; no authored leaf/native/apple source. Hierarchical commonTest/iosTest are not type-aware metadata tasks; plain Detekt/native runtime evidence is separate.
- Gradle summary: **143 executed,4 up-to-date actionable tasks**. The four UP-TO-DATE labels are Compose common-resource preparation, not framework links.
- **Zero tests run** in this cycle; test receipt `[]` and absence of JUnit XML agree.

## Three artifact receipts

| Target | CPU / plist platform | Bytes | Recorded SHA-256 |
| --- | --- | ---: | --- |
| `iosArm64` | `arm64` / `iPhoneOS` | 52,067,408 | `70e113c4e73d993eb4545748e62ee1c9b7dd8e1dff1b4c3ffd99374f9ad33632` |
| `iosSimulatorArm64` | `arm64` / `iPhoneSimulator` | 52,265,480 | `b38bef11efdad166858d6b3ef45cae96e1783647d1d61a8f08f2a4b78caa016d` |
| `iosX64` | `x86_64` / `iPhoneSimulator` | 53,549,512 | `dce4720c19d635b26f9f306b11b1f668d24b6f347bbcb6a0501a752613434b9b` |

Retained plist structure is consistent: dynamic framework name/executable `ComposeApp`, type `FMWK`, correct target platform, nested ID `com.parlor.app.ComposeApp`. Framework minimum OS15.0 is below wrapper deployment16.0; it does not change or independently validate the app wrapper's deployment/version/identity. Device plist includes required arm64 capability.

Root streamed binary hashes and recorded `file(1)` architecture before mandatory cleanup. Reviewer inspected retained receipts/plists, **not deleted Mach-O bytes**. Simulator/device distinction additionally uses target/plist; no LC_BUILD_VERSION, install-name/UUID/dSYM/signature/entitlement claim. No app wrapper, Xcode embedding phase, UI launch, app-host runtime, privacy-cover behavior, full framework-package resource inspection, archive/IPA or signing verification occurred here.

## Toolchain qualification and cleanup

Independent post-cycle metadata-only queries return **Xcode26.5 /17F42**, selected at `/Applications/Xcode.app/Contents/Developer`. Exact Store-qualified policy remains **26.3 /17C529**; CI enforces it before release verification. This successful Gradle gate therefore **does not qualify a Store candidate**. The cycle receipt itself lacks embedded DTXcodeBuild metadata; current query corroborates root's local-toolchain statement rather than inventing per-binary toolchain attestation.

Experimental-lint/Gradle-version notices and iosX64 runtime host-architecture warning remain visible. X86_64 cross-linking succeeded; this does not make its runtime executable on this ARM64 host.

Stop exit0 at22:53:15.982UTC; root's exact-output cleanup completed22:53:16.378UTC, no retained outputs/workers/errors. Raw stop log says no Gradle daemons running. Cycle scratch and all three original framework binaries independently absent; compact receipts/plists retained. Reviewed runner is unchanged and stops before evidence collection/removal. Reviewer only wrote this dossier and ran completed metadata queries, leaving root's shared lane untouched.

**Conclusion:** accept combined-apple-01 as local source analysis and three-architecture release-framework linkage evidence. Native tests, Swift wrapper/app-host flows, physical devices, real signing/Store/legal gates, exact release Xcode and unresolved product decisions require separate evidence; no global READY verdict.
