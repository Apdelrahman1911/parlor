# P2P remediation implementation and evidence status

Document status: current implementation ledger as of 2026-08-09.

This file records what is implemented on
`codex/p2p-production-remediation`, what automated evidence exists, and what
still requires release or physical-device evidence. It is deliberately
stricter than a feature checklist: **implemented does not mean production
verified**.

The recoverable pre-remediation baseline is commit
`8186f7d70786057b791bd5c1aa80ca868835ec37`. Every remediation commit is on
top of that checkpoint; no baseline change was reset, cleaned, or rewritten.

## Executive status

- The P2P architecture now has explicit host authority, bounded traffic,
  transactional admission/resume, deterministic lifecycle and discovery
  state, truthful Local Network UX, and privacy-safe diagnostics.
- At committed remediation input `aab673f`, the strict checksum-verified
  `productionCheck`, `productionAppleCheck`, and repository-wide `allTests`
  matrix passed together: 1,150 actionable tasks, 27 executed and 1,123
  up-to-date. Android release compilation, R8, lint, unsigned AAB packaging,
  all three iOS release framework architectures, Desktop/common tests,
  Android unit variants, and executable Apple-simulator tests were included.
- No physical-device row in `P2P_MANUAL_TEST.md` is implied by automated
  evidence. Cross-platform, hotspot, process-death, permission-recovery,
  sustained-session, and signed-artifact behavior remain **UNVERIFIED** until
  dated receipts exist for the exact release SHA.
- Raw-IP/manual endpoint connection is intentionally unsupported for the first
  release under accepted ADR-0002. `MAN-00` is **N/A**, not PASS.
- Therefore the current production-readiness verdict remains **NOT READY FOR
  RELEASE** even though the known code-level defects have remediations. The
  signed artifacts, physical matrix, accessibility, privacy/store, complete
  SBOM/license review, publisher-key trust, and legal gates must still close.

## Recoverable implementation chain

| Commit | Remediation boundary |
|---|---|
| `8186f7d` | Approved pre-remediation checkpoint. |
| `4443b54` | Bounded host-authoritative command/result/snapshot contract. |
| `70057bc` | Dispatcher-isolated P2pKit initialization and cancellation propagation. |
| `74203d2` | Deterministic logical-room lifecycle across Android and iOS. |
| `3416917` | Atomic capacity reservation and transactional admission base. |
| `b75325e` | Platform-protected storage for resumable credentials. |
| `b49a7c4` | Serialized credential staging, promotion, rotation, and deletion. |
| `284c594` | Protocol 3 transactional admission/resume readiness barriers. |
| `4170266` | Pinned, rotating, process-resumable admission and rejoin implementation. |
| `afe9936` | Bounded queues, payloads, rates, attempts, and admission bookkeeping. |
| `bc05b34` | Deadline-owned multi-candidate discovery scheduler and retry policy. |
| `ad203da` | Truthful iOS Local Network state and Settings recovery UX. |
| `8d25e50` | Bounded, fixed-vocabulary, privacy-safe production diagnostics. |
| `8b290aa` | Explicit unsupported manual-endpoint capability and ADR-0002. |
| `14ebb1e` | Runtime-aligned canonical documentation and historical banners. |
| `72182c7` | Least-privilege Android LAN manifest contract. |
| `8815ddb` | Serialized three-architecture Apple release linkage. |
| `738c6db` | Strict artifact checksums, Maven provenance receipt, and regression guard. |
| `aab673f` | Repository-contract input tracking and final automated evidence ledger. |

Every entry is a separate checkpoint above the approved baseline. Do not
squash any remediation into `8186f7d` when reviewing or rolling back. A later
ledger-only commit may record these results without becoming a new executable
remediation boundary.

## Additional verification findings closed

- **VER-01 — stale repository-contract test result (Medium, confirmed):** the
  Desktop tests read runbooks, manifests, plist, coordinates, and checksum
  metadata outside their source set, but `desktopTest` did not declare those
  files as inputs. Gradle could therefore reuse a prior PASS after one of those
  inputs changed. `shared/transport-p2p/build.gradle.kts` now declares the
  repository contract files with relative path sensitivity. A documentation
  edit must cause `desktopTest` to execute rather than remain `UP-TO-DATE`.
- **REL-02 — same-coordinate cache history (provenance warning):** the local
  content-addressed Gradle cache retained unselected 0.7.0-rc2 platform files
  whose hashes differ from current module metadata. The cache cannot prove the
  old repository origin, so this is not labelled a confirmed Maven Central
  republish. Strict checked-in checksums and the independent hard-coded P2pKit
  contract now prevent silent byte substitution; future upstream changes must
  use a new version.

## Original finding disposition

| Finding | Code disposition | Automated evidence required/present | Remaining external evidence |
|---|---|---|---|
| P2P-01 lifecycle | Implemented: platform callbacks feed one ordered room lifecycle; background suspends commands, short foreground recovery resumes, the original 120-second deadline cannot be extended, expiry is terminal, and host loss has no migration. | Lifecycle, idempotency, deadline, coordinator rejection, timer freeze, cleanup, platform compilation, and strict aggregate tests pass. | Host/peer background, lock, long interruption, OS termination, and foreground recovery on both OS families and signed artifacts. |
| P2P-02 rejoin | Implemented: an opaque 256-bit credential is device-protected, host/fingerprint/player/room/game/generation/expiry bound, host stores only its digest, and transactional rotation preserves the last committed generation until acknowledgement. Explicit Leave deletes it; transient loss/background/process death preserve it; host death cannot be resumed. | Credential transaction, malformed storage, concurrent access, admission/resume handshake, pinned identity, rotation, duplicate connection, process-recreation adapter, and cleanup tests are present. | Physical force-stop/termination and relaunch inside/outside seat grace on Android and iOS; protected-storage review on release artifacts. |
| P2P-03 commands | Implemented: one mutation in flight per peer; host-bound actor; command ID plus client sequence and expected revision; explicit applied/duplicate/rejected result; bounded dedupe ledger; monotonic player snapshot; stale/gap rejection triggers revalidation without blind replay. | Protocol validation/CBOR round-trip, duplicate, stale, sequence-gap, simultaneous command, authorization, snapshot ordering, payload, and coordinator tests are present. | Coordinated physical simultaneous actions in both games and UI feedback evidence on each OS. |
| P2P-04 abuse/backpressure | Implemented: 40 KiB peer and 272 KiB host frame ceilings, 16/8-frame and byte-bounded per-room queues, 32-frame burst, 16 frames/second sustained limit, three-strike isolation, 17 pending admissions, 21 tracked sessions, and 128 tracked identities. | Deterministic token-bucket, oversize/malformed frame, sustained flood, queue saturation, peer isolation, admission flood, and 10,000-event diagnostics tests are present. | Sustained and repeated physical sessions; memory, thermal, battery, and release-log observation. |
| P2P-05 discovery | Implemented: candidates may appear/disappear/reappear; endpoint incarnations reset safely; attempts are deduplicated and fairly selected; transient failures use bounded backoff; wrong rooms are candidate-local; total join, per-attempt, first-response, approval, cancellation, and final-error ownership are distinct. | Pure scheduler tests plus late correct candidate, wrong room, transient retry, timeout, approval-window, stale candidate, cancellation, and cleanup adapter tests are present. | Multiple-room/late-candidate and network-switch tests on real LANs/hotspots. |
| P2P-06 main-thread initialization | Implemented: P2pKit/identity creation is suspendable and Android executes disk-backed initialization on the injected IO dispatcher; UI owns neither the work nor its scope. | Factory dispatcher and cancellation tests plus Android compilation are present. | Android StrictMode startup receipt on representative release devices. |
| P2P-07 cancellation | Implemented: broad P2P/storage wrappers rethrow `CancellationException`; cancellation during create/start/discovery/send/resume cleans owned resources and is not mapped to a normal transport error. | Focused cancellation tests cover factory, start, discovery, send, process resume, and secure storage; the source audit and strict aggregate pass. | Lifecycle cancellation under physical app termination is covered by device rows, without treating termination as an exception-propagation test. |
| P2P-08 capacity | Implemented with P2P-09: one mutex-owned admission transaction reserves capacity before approval/offer and counts committed plus reserved seats atomically. | Concurrent last-seat and capacity-bound tests are present. | Three-device/full-room approval races on physical devices. |
| P2P-09 admission rollback | Implemented with P2P-08: pending, offered, confirmed, committed, ready, and acknowledged stages have explicit ownership; disconnect/send/cancel/timeout failure rolls back reservations and uncommitted membership without a ghost member. | Disconnect/failure at admission stages, duplicate request, rollback, leave, and repeated cleanup tests are present. | Physical disconnect-during-approval and repeated-room evidence. |
| P2P-10 direct connect | Resolved product decision: unsupported. Capability reports false; room-code entry remains discovery based; optional P2pKit provisioning/manual-IP sidecars are not packaged. | Capability adapter test and ADR-0002 are present. | `MAN-00` remains N/A. Any future requirement must reopen the ADR and implement fingerprint-pinned parity rather than using unauthenticated raw IP. |
| P2P-11 iOS permission | Implemented: no invented preflight grant; states distinguish Unknown, Attempting, Operational, typed actionable denial, and unclassified failure; Operational requires real advertise/authenticated-connect evidence; Settings opens the app page and return resets only for a real retry. | State-mapping, typed failure, recovery, Bonjour plist, Android no-runtime-permission, and platform compilation tests are present. | Fresh-install denial, Settings recovery, and unrelated LAN failure differentiation on physical iOS release builds. |

## Cross-cutting production gates

| Gate | Current disposition | Final acceptance evidence |
|---|---|---|
| Maven Central provenance | PASS for checksum enforcement: exact 0.7.0-rc2 Android/JVM/iOS artifacts match current Maven Central SHA-256 values and are pinned in strict Gradle verification; no sibling build, `mavenLocal()`, or provisioning sidecar resolves. POMs declare Apache-2.0 and GitHub SCM; two AAR signatures validate against fingerprint `273D 83EA EDCC 24BA 90CA 4E78 6FD7 A2F6 DE03 19E7`. | `P2PKIT_MAVEN_PROVENANCE.md`, dependency graphs, strict build, and checksum contract are present. Publisher fingerprint approval, final SBOM/transitive license review, and third-party notices remain external gates. |
| Android/JVM/common compilation | PASS at `aab673f`: strict aggregate includes release Kotlin compilation, lint, R8/AAB packaging, Desktop/common, Android debug/release unit variants, and P2P adapter tests. | Repeat on the exact final release SHA and archive full logs/reports. |
| Apple compilation/linkage | PASS at `aab673f`: release frameworks link serially for `iosArm64`, `iosSimulatorArm64`, and `iosX64`; executable simulator tests pass where tests exist. x64 test execution is skipped by the existing Apple-Silicon host policy, not counted as a runtime pass. | Repeat linkage on the final SHA; physical arm64 runtime remains a device gate. |
| Manifest/plist | PASS for source and unsigned merged output: Android min 26/target 36 with only four base LAN permissions, cleartext and backup disabled; `plutil` accepts the plist with Local Network usage and `_p2pkit2._tcp`, without Bluetooth wording. | Archive the signed merged manifest/app plist and repeat platform inspection. |
| Encryption/authenticated identity | Parlor relies on P2pKit authenticated-v2 encrypted sessions and pins the authenticated host fingerprint for resume. It does not claim account identity or an internet trust anchor. | P2pKit artifact/API provenance, adapter tests, and physical authenticated connection/rejoin receipts; residual first-contact threat documented. |
| Host authority/actor binding | Implemented at transport and coordinator boundaries. | Modified-client actor-spoof tests, both game authority tests, and physical private-state/command results. |
| Version/order/duplicate/snapshot | Strict protocol 3.1 and monotonic authoritative revisions implemented. | Codec compatibility fixtures, fault injection, coordinator/adapter tests, and physical simultaneous-action evidence. |
| Admission/capacity/backpressure | Implemented and bounded in code. | Deterministic race/flood suites plus physical last-seat, disconnect, sustained, and repeated-session rows. |
| Direct-connect decision | Unsupported under ADR-0002. | Capability remains false, docs remain aligned, and manual row remains N/A. |
| Android-to-Android | UNVERIFIED for the release SHA. | PHY-01 and applicable recovery/hotspot/signed rows. |
| iOS-to-iOS | UNVERIFIED for the release SHA. | PHY-02, PHY-20, and applicable recovery/hotspot/signed rows. |
| Android-to-iOS | UNVERIFIED for the release SHA. | PHY-03 and PHY-04 plus recovery, three-device, hotspot, and signed rows. |
| Three-device/hotspot/repeated sessions | UNVERIFIED for the release SHA. | PHY-05 and HOT-A/HOT-I rows, plus PHY-18 and PHY-19 with exact device/network records. |
| Signed store artifacts | UNVERIFIED. | Protected Android signing gate and Play internal receipt; Xcode Release archive/TestFlight receipt; required device rows repeated from those artifacts. |
| Accessibility/store/legal/operations | Open external release gates. | Receipts enumerated in `RELEASE_GATES.md`; no simulator or source inspection substitutes for them. |

## Automated evidence inventory

Root-cause regression coverage lives primarily in:

- `shared/networking/src/commonTest`: strict protocol validation and CBOR
  compatibility;
- `shared/session/src/commonTest`: authoritative command acknowledgement,
  stale/order/duplicate/concurrency, lifecycle, and snapshot behavior;
- `shared/transport-p2p/src/commonTest`: credential transactions, scheduler,
  traffic policy, diagnostics, and capability contracts;
- `shared/transport-p2p/src/desktopTest`: two-ended loopback plus deterministic
  admission, disconnect, retry, lifecycle, cancellation, permission, and abuse
  fault injection;
- `shared/storage/src/commonTest`: protected-store adapter semantics and
  cancellation;
- both shipping game modules' common tests: rules, authority, lifecycle, full
  game, projection privacy, and serialization; and
- platform compilation plus manifest/plist contract tests.

## 2026-08-09 automated receipt

The following evidence was collected locally on macOS. It is automated build
evidence, not physical-device, signing, or store evidence.

| Command/check | Result |
|---|---|
| `./gradlew productionCheck productionAppleCheck allTests --dependency-verification=strict --no-daemon --stacktrace --console=plain` | PASS at `aab673f` in 28 seconds; 1,150 actionable tasks, 27 executed, 1,123 up-to-date. |
| `./gradlew :shared:transport-p2p:desktopTest --dependency-verification=strict --no-daemon --stacktrace --console=plain` after a ledger edit | PASS in 14 seconds; 20 actionable tasks, 7 executed. This proves repository-file input changes invalidate the result. Includes two-ended loopback, fault injection, docs, manifest/plist, and Maven provenance contracts. |
| Android/JVM/iOS `dependencyInsight` for `p2p` | PASS: only exact `io.github.apdelrahman1911` 0.7.0-rc2 core and LAN variants resolve; no local source/provisioning artifact. |
| Maven Central checksum comparison | PASS for all 15 selected root/platform runtime artifacts; values are in `P2PKIT_MAVEN_PROVENANCE.md` and the strict allowlist. |
| Detached signature verification | PASS cryptographically for the two Android P2pKit AARs; publisher-key ownership remains UNVERIFIED out of band. |
| `plutil -lint iosApp/iosApp/Info.plist` and `xmllint --noout` on the merged release manifest | PASS. |
| `./gradlew productionAndroidSigningCheck --no-configuration-cache` | Expected external-gate FAIL: all four protected signing inputs are absent. The unsigned build was not relabelled as signed. |

Produced unsigned/local artifacts at that receipt point:

| Artifact | Size | SHA-256 |
|---|---:|---|
| Android release AAB (confirmed unsigned with `jarsigner`) | 8,094,485 bytes | `22f143535e647142fcabc67470b9a62fc956ce527cbd33a3d22a9e39dd1f1d3e` |
| R8 mapping | 51,293,267 bytes | `0bd000c67da12de898566138080ee92d2e0bb09adaf6f5aad62ffcc52d45b5e7` |
| iOS arm64 framework executable | 136,939,224 bytes | `d64f0a3a0a1b7fc5d9520d56ffba8472d2bcb2092c5c66c5a341186e58da06d8` |
| iOS simulator arm64 framework executable | 136,680,184 bytes | `1edb4dd5b4a6a34dcbee74a77581b2e10e5b58cff4fefc9a192a7eb14d7ee8e6` |
| iOS x64 framework executable | 134,636,888 bytes | `9e910597132b427a14350e321579f72d7b8c682edbd63401e3618e363a8bb83f` |

The x64 Kotlin/Native test binaries link but `iosX64Test` is skipped on this
Apple-Silicon host. The x64 release framework linkage is PASS; x64 runtime is
not claimed. Lint has no blocking errors. Non-blocking advisories remain for
available dependency/SDK updates, Android data-extraction guidance and icon
shape, Kotlin test deprecations, and Compose Gradle accessor deprecations; no
dependency was blindly upgraded as part of this remediation.

Focused implementation commands also passed throughout the phase:

```bash
./gradlew :shared:transport-p2p:allTests
./gradlew :shared:networking:allTests :shared:session:allTests :shared:transport-p2p:allTests
```

The exact store release SHA must repeat the commands below, and CI/release
operations must archive their full logs and artifact hashes:

```bash
./gradlew productionCheck --no-daemon --stacktrace --console=plain
./gradlew productionAppleCheck --no-daemon --stacktrace --console=plain
./gradlew productionAndroidCheck --no-daemon --stacktrace --console=plain
./gradlew :composeApp:compileReleaseKotlinAndroid --no-daemon --stacktrace --console=plain
./gradlew :composeApp:lintRelease :composeApp:bundleRelease --no-daemon --stacktrace --console=plain
./gradlew productionAndroidSigningCheck --no-configuration-cache
```

The signing command is expected to remain UNVERIFIED on an uncredentialed
developer machine; it must never be bypassed with a debug or invented key.

## Remaining release work

1. Repeat the already-passing strict automated matrix on the exact candidate
   promoted for signing and archive reports, graphs, manifests, plist, AAB,
   shrinking output, checksums, and cleanup-related test logs.
2. Run every applicable row in `P2P_MANUAL_TEST.md` using recorded physical
   devices, OS builds, roles, topology, exact artifacts, diagnostics, and at
   least the required repetitions. A prior informal successful connection is
   useful context but does not replace a row receipt.
3. Build signed Play-internal and TestFlight candidates and repeat the required
   physical subset from those store-delivered artifacts.
4. Complete accessibility, localization/RTL, privacy/store forms, observability
   provider, legal/license/notices, SBOM, and operational receipts in
   `RELEASE_GATES.md`.
5. Reassess every FAIL or environment-dependent hotspot result. Document a
   model/OS/setting limitation rather than generalizing one successful device
   pair into universal support.

## Release decision rule

An issue may be marked **code resolved** when its root-cause regression tests
pass. It may be marked **production verified** only when all applicable
automated, physical, signed-artifact, privacy, security, and operational
evidence in this ledger and `RELEASE_GATES.md` exists for the same release SHA.
No Blocker, Critical, or High failure may be waived silently; owner risk
acceptance must name the finding, scope, expiry, mitigation, and evidence gap.
