# Parlor — Independent Source Audit

**Verdict: NOT READY. Audit only; no application fixes implemented.**

This report separates completed reading, independently demonstrated defects,
executed checks, and missing behavioral/release evidence. It is not a claim that
every possible failure has been found, that every UI journey ran, or that GitHub
issues have been closed. See [CONTINUATION.md](CONTINUATION.md) for remaining work.

## 1. Exact reviewed baseline

| Item | Reviewed value |
|---|---|
| Repository | `/Users/abdelrahman/Projects/parlor` |
| Branch | `main` |
| Commit | `3625d0663ba6eb51338cbd5f9dc45f859ec18846` |
| Git tree | `db7f3d2afe73a13628296daee2cce71165eebc8d` |
| Tracked modifications | None at baseline; compare final preservation receipt |
| Existing untracked work | `AGENTS.md`, `design/`, `docs/PARLOR_PROJECT_HANDOFF.md`, `project-code-audit/` |
| New work | This isolated `audit-runs/2026-09-05-source-audit/` directory only |

The commit alone is **not** the source identity: the fresh inventory also
fingerprints applicable untracked input. Existing refs/stash/user assets were
preserved. `AGENTS.md` already existed and was not modified. Protected local
configuration/signing/player data was not opened or hashed; that exclusion is
not a claim that such inputs have no effect on a build.

[Baseline](baseline.json) · [Fresh inventory](coverage/inventory.jsonl) ·
[Final source comparison](evidence/final-state.json) ·
[Final preservation/hygiene](evidence/final-preservation-and-hygiene.json)

## 2. Coverage and its limits

- **735 inventoried entries**, each with an explicit disposition.
- **628 first-party text files / 139,888 lines** have complete recorded verbatim
  reading ranges. This includes implementation, tests, resources, build/release
  code, relevant hidden files, prototypes and documentation—not just recent changes.
- **Six binary inputs** were inspected using suitable format, image/font,
  checksum and wrapper tools. Binary inspection does not prove asset rights or
  rendering/accessibility on all devices.
- Explicit exclusions: **72 previous-audit files**, **28 local/generated-state
  entries**, and **one protected `local.properties`**. Pruned directories such as
  signing/private/IDE/cache state are separately recorded in the baseline.
- There are **no unread verbatim ranges in the applicable text inventory**.
  This is a finite reading statement, **not “100% behavior verified.”** The ledger
  deliberately uses `TEXT_READ_COMPLETE_WITH_LIMITS`, records historical
  uncertainties, and does not mark behavioral verification complete.

The file-level [coverage ledger](coverage/FINAL_COVERAGE.jsonl) includes hashes,
one-based ranges, reviewers, production/test/build relevance, investigated
cross-file paths, evidence and uncertainty. [COVERAGE.md](COVERAGE.md) explains
the denominator. Prior coverage tables were treated as claims and inspected,
not inherited as proof. Historical mutable audit-index receipts are bound to
their exact archived bytes, separately from application-source coverage.

## 3. Verified project model

The observed build graph has **13 KMP modules** (16 root/group/module Gradle
projects, with a separate included convention build). Android and iOS are the
shipping targets; Desktop is for development and deterministic testing.

| Module/area | Actual responsibility |
|---|---|
| `composeApp` | Composition root/Koin, game bindings/catalog, typed Navigation 3 state and Back policy, shell/recovery, platform storage/lifecycle adapters |
| `shared/core` | Small shared identifiers/value/error contracts |
| `shared/engine` | Generic definitions, pure reducers and public/private projection contracts; no shipping-game dependency |
| `shared/session` | Local and multiplayer controllers, canonical mutation, host/peer orchestration, command receipts and start handshakes |
| `shared/networking` | Transport-independent protocol, framing/serialization/semantic validation, exact protocol **4.2** |
| `shared/transport-p2p` | Sole P2pKit integration, discovery/admission/identity/rejoin/lifecycle and bounded transport bookkeeping |
| `shared/storage` | Snapshot envelopes, serialized writer, secure storage/rejoin/settings abstractions |
| `shared/content` | Content acquisition/validation/cache contracts; shipping remote source is offline |
| `shared/design-system` | Shared UI, themes, components, localization and platform locale adapters |
| `game-modes/whodunit` | Whodunit rules, validators/codecs/recovery, authored cases, setup/local/host/peer UI |
| `game-modes/mafia` | Mafia rules, role actions/projections, validators/codecs/recovery and game UI |
| `shared/engine-testing`, `shared/networking-testing` | Non-shipping registration/network fixtures, not production dependencies |
| `iosApp`, `build-logic`, `scripts`, `.github` | Swift wrapper/Xcode resources; build conventions; validation/release tooling; workflows |
| `design/web-ui-rework` | Preserved untracked web prototype; not a shipping app source set |

[Observed graph](coverage/observed-build-graph.txt) and per-file coverage are the
source evidence; the architecture documents are supporting claims, not proof.

### Authority and privacy boundaries

Pass-and-play runs one local canonical controller on one device and uses private
handoffs. LAN uses a retained host runtime: **only the host executes reducers**;
peers send validated seat-bound commands and render received projections, not
optimistic canonical state. Shared infrastructure dispatches registered game
contracts rather than importing either shipping game.

Peer wire state consists of a public projection plus **only that recipient's
private slice**. Host-only seeds, room secrets, secret assignments/role maps and
other players' private state must stay outside peer serialization. Documented
post-game role/assignment reveals are intentional public outcomes, not private
projection sharing. Authored bundled story files are public assets; secret
runtime assignment is a different boundary.
There is no account backend, database, internet matchmaking, raw-IP join,
spectator mode or host migration in this product.

Canonical snapshots, navigation launch objects and rejoin credentials have
separate owners/stores/version domains. Mobile snapshots are authenticated and
encrypted; current-state recovery is game-validated. ST-C1, M-C01 and WD-C3 below
show why encryption and passing round-trip tests alone are insufficient.

### Game behavior traced

- **Whodunit:** seeded assignment; intro/briefing/readiness/private reveal;
  authored clue rounds and final evidence; discussion timer/pause; Classic
  voting or Elimination/revote/termination; final reveal and replay; local save
  and retained-host lifecycle/same-host peer rejoin—not host migration. Engine
  ranges are Classic **4–8**, Elimination **5–8**;
  the **seven bundled cases support exactly six seats**. Wider synthetic engine
  fixtures do not make four-player bundled UI/LAN play supported.
- **Mafia:** validated **5–16 seats**, deterministic role assignment and private
  readiness; legal Mafia/Doctor/Detective actions and explicit skips; resolution,
  private results, dawn/discussion/public voting/revotes, parity/win or early end,
  final roles, replay and recovery. Non-null timed-round compatibility fields
  are deliberately rejected; timers are not an unfinished product feature.
- **Navigation:** Navigation 3 is already in use. App-owned typed stacks and a
  central navigator coordinate feature-owned route adapters; guarded live-game
  entries restrict what Back can expose. No migration or redesign was performed.
  Source/state tests do not prove interactive iOS/Android gesture behavior.

## 4. Independently adjudicated findings

**16 confirmed defects: nine Medium and seven Low.** Every one has a separate
validator, reachable source proof or reproducer, checked counter-evidence and
qualified platform scope. There are also **nine documentation mismatches**,
**six test/evidence gaps**, **three rejected numbered candidates**, and **one
blocked numbered candidate**: **35 allocated IDs**, not a GitHub issue count.
IOS-R1 is now an independently adjudicated evidence gap with no defect severity,
not another confirmed defect; WD-C4 remains blocked on a product decision.

| ID | Severity | Confirmed problem |
|---|---|---|
| ST-C1 | Medium | Failed iOS legacy-save migration can retain backup-eligible application plaintext |
| SN-C1 | Medium | A valid late start commit is lost to the best-effort ACK timeout |
| SN-C2 | Medium | Cancelled host opening can lose ownership of an already-created transport |
| WD-C1 | Medium | Whodunit peer router remount automatically resubmits readiness commands |
| IOS-B1 | Medium | Xcode shell phase can mask a failed framework build when stale output exists |
| ROOT-T3 | Medium | Ten non-void Jupiter methods are undiscovered: eight enabled, two already ignored |
| RL-C1 | Medium | Latent GNU/Linux release size guard mixes BSD/GNU `stat` output |
| RL-C2 | Medium | Latent signed-AAB validation lacks explicit upload-certificate trust |
| RL-C3 | Medium | Latent Play promotion validates one edit but mutates a different snapshot |
| M-C01 | Low | Malformed authenticated Mafia recovery can contradict Doctor protection history |
| WD-C3 | Low | Malformed current Elimination snapshot can reopen an impossible final-two state |
| M-C03 | Low | Legal long name can give the Mafia final role zero layout width |
| DS-C01 | Low | iOS Follow System can retain an app-owned language override across restart |
| DS-C02 | Low | Reused toast lifetime IDs can hide a replacement notification |
| DS-C03 | Low | Light-theme recovery Leave labels have insufficient contrast on the black cover |
| WD-C2 | Low | Four bundled story chronologies contain independently verified contradictions |

Read [FINDINGS.md](FINDINGS.md) for exact absolute paths/line ranges/hashes,
prerequisites, root causes, expectations, reproductions, validator conclusions,
remediation and regression tests. Latent release defects do **not** mean disabled
workflows currently publish. Malformed authenticated snapshot witnesses do
**not** demonstrate an authentication bypass or a normal UI producer.

[OTHER_CANDIDATES.md](OTHER_CANDIDATES.md) preserves rejected/blocked candidates,
documentation drift and test weaknesses separately. IOS-R1's real diagnostic
rerun attributes unavailability to the multiplayer credential pipeline; the
warning correctly preserves that failure. Only a subsequent equivalent native
query returned `-34018`, not an intercepted original Home/production-rerun status.
WD-C4's modal-clock policy is unresolved. The ordered-event extension concern has no demonstrated current
shipping consumer impact. No regression-introduction commit or first-ever
discovery is invented from an audit label.

## 5. Verification: what actually ran

The authoritative changing gate matrix is [VERIFICATION.md](VERIFICATION.md)
and its validated [JSON ledger](verification-ledger.json). Original commands,
UTC timestamps, exits, flags, source identities and test names are retained.

- `productionDesktopCheck`, repository static analysis and `productionCheck`
  passed their executed scopes. Detekt reports contained zero issues; Android
  lint passed with **32 policy-accepted warnings**, not zero warnings.
- Combined `allTests productionIosSimulatorRuntimeTests` reported **2,285 test
  descriptors: 2,284 passing, one skipped**. Do not sum retries/platform copies
  as unique tests. Thirteen Intel iOS tasks were host-disabled; Desktop-only
  engine/Mafia tests were not magically exercised natively. Whodunit native
  ran nine tests, not all 288 Desktop tests.
- `productionAppleCheck` linked three Release frameworks on installed Xcode
  **26.5/17F42**, not the qualified **26.3/17C529**. The earlier audit heap-limit
  failure is not an app crash or a failed game test.
- The actual unsigned iOS wrapper passed **one English launch XCTest**. English
  and Arabic screenshots are supplemental; this did not verify recovery health,
  complete gameplay, gestures, repeated-launch stability or physical devices.
- A separate, source-bound **copied app-host diagnostic XCTest passed once** on
  a fresh iOS 26.5 simulator. The real Koin-bound rerun returned local
  success-empty, multiplayer `SecureStorageUnavailable`, and the correct
  unavailable projection. Its later equivalent Keychain query returned
  `errSecMissingEntitlement` (`-34018`). The initial Home call and rerun did not
  retain their numerical native status. This is **intended fail-closed handling
  plus a platform/test-environment evidence gap**, not healthy-storage proof or
  a confirmed application bug. The earlier attempt executed zero tests because
  the audit harness supplied an empty signing-identity environment entry;
  pinned KGP source independently explains that build failure, not an app crash.
- The checked-in Android disposable-signing harness ran
  `productionAndroidRuntimeCheck`: **three Release instrumentation tests passed,
  none failed or skipped**, on the local API35 ARM64 emulator. This checks launch,
  multicast-lock acquisition and first draw during blocked settings I/O—not
  gameplay, physical LAN, Linux/x86_64 CI qualification or Store signing.
  An earlier ADB-listener setup failure occurred before Gradle or tests and is
  preserved as a harness failure, not an application failure.
- Isolated reproducers include intentional failing safety assertions. A passing
  unsafe-behavior witness is evidence of a defect, **not a fix**. Eight normally
  undiscovered transport test bodies passed wrappers; original registration is
  still broken, and ignored physical tests stayed ignored.
- Four independent resource-parity checks passed **747 string keys per locale**
  for structural/type/placeholder/nonempty parity. No plural resources were
  present, so plural-validation branches were not exercised. This is not linguistic,
  screen-reader, visual or bidirectional runtime certification.

The Android result and its cleanup were independently reconciled against original
XML, logs and input hashes; see
[the execution review](reviews/android-managed-execution-independent-whodunit-cont.md).
Unexecuted cross-host, gameplay and physical-device gates remain separate.
The [iOS source/result adjudication](validations/IOS-R1-apphost02-session_cont.md)
and [independent execution review](reviews/iosr1-apphost-02-execution-independent-whodunit-cont.md)
bind the diagnostic to its inputs, actual header and XCTest. Only launcher and
framework binary hashes were retained: no complete Debug bundle attestation or
signed-release provenance is claimed.

## 6. Recommended order — not authorization to implement

1. Preserve privacy/verification trust first: **ST-C1, IOS-B1, ROOT-T3**.
2. Repair multiplayer ownership/commit/effect behavior: **SN-C1, SN-C2, WD-C1**;
   rerun both games' local/host/peer and cancellation/rejoin regression suites.
3. Tighten game-owned current-snapshot invariants: **M-C01, WD-C3** without
   changing normal rules, exposing host history, or repairing corrupt current data.
4. Correct UI lifetime/locale/contrast/result layout: **DS-C01–03, M-C03**;
   verify actual compact/large-text/LTR/RTL and lifecycle behavior.
5. Resolve authored facts with the content owner (**WD-C2**) and evaluate content
   digest/save compatibility. Do not silently choose new game/story facts.
6. Repair latent release code **RL-C1 → RL-C2**, plus **RL-C3**, while workflows
   remain disabled. No real signing/promotion evidence follows from these fixes.
7. Address documentation/test gaps and execute outstanding platform/physical/
   owner-dependent gates. Revalidate the exact changed tree and all sibling paths.

Each future fix needs separate authorization, focused changes, regression tests
and an independent second review; this audit made none.

## 7. Preservation, cleanup and final assessment

[RESEARCH_AND_CLEANUP.md](RESEARCH_AND_CLEANUP.md) indexes authoritative references,
strict build receipts and cleanup. Required evidence is retained compactly;
module/build-logic outputs, owned native intermediates/DerivedData, test apps and
simulators are not deliverables. Global caches and unrelated workers must remain
untouched. See the final preservation receipt for the actual final process and
output check, rather than assuming cleanup from a green test.

The last iOS runner initially missed one attested external Apple `ibtoold` FIFO
directory. Independent review caught it; root removed only its two dead-process
FIFOs and empty directories after ownership/no-holder checks. A second independent
check passed. Both the initial cleanup failure and additive correction remain
recorded. Known-output cleanup is not a blanket claim about unrecorded global
temporary files or unrelated processes.

The project has meaningful modular boundaries and substantial deterministic
tests, but **known code/content/tooling defects remain**, and local UI/runtime,
cross-host, physical LAN/privacy and owner/Store gates remain distinct. The audit
is complete at the recorded text-reading level, **not complete end-to-end
behavioral verification**. Store readiness is not established. This report
therefore does not declare the repository clean, release-ready, or defect-free.
