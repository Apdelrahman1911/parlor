# Project status — 2026-09-18

**The readiness continuation is integrated into `main`; this is not Store-release
approval.** Source and executable contracts remain authoritative. Start with
[the documentation index](README.md), not an archived handoff's old task list.

## Completed checkpoint and evidence

### Public testing channel

On September 18 the owner explicitly requested public **unsigned test downloads**.
The separate [public testing workflow](GITHUB_TEST_RELEASES.md) builds an isolated
Android `Parlor Test` APK with a disposable installation signature and existing
verified macOS/Windows/Linux test installers. Publication requires exact-source
full CI, frozen hashes/attestations and explicit maintainer acknowledgement;
releases are marked prerelease and never Latest. This does not enable Store
publishing, waive physical/security findings or relax signed-production gates.
Use exact-source execution receipts and live GitHub Release readback for status;
this source document does not pre-certify a build or claim publication.

### Rules revision and GitHub distribution qualification

The September 17 revision on `feat/last-light` changes all three new games to
contract version 2: Dominoes Default/Draw/Block, 51/101/151 targets, 101–0 shutout
and opposite-seat teams; scoreless Ghamza; personal correct-vote and independent
word-guess points in Word Impostor. See [current rules](THREE_GAMES_INTEGRATION.md).
Earlier version-1 game receipts below do not qualify this revision.

The separate [GitHub distribution pipeline](GITHUB_DISTRIBUTION.md) adds Android
APK, macOS arm64/x64 DMG, Windows x64 MSI and Linux x64 DEB preparation, native
runtime/installed-payload verification, protected signing and immutable Release
publication. Store workflows remain disabled. Its unsigned rehearsals are **not**
directly publishable; public testing uses its own separately validated channel
above. No signed production Release is claimed by this documentation.

Focused game/app tests, host authority/recovery cases and a local macOS native
packaging rehearsal passed during implementation. Those were working-tree
checks, not an immutable release qualification. Use exact-SHA workflow runs and
the final execution report for later verification. Real Android/Developer ID/
Windows signing credentials, new protected environment configuration and current
physical/owner acceptance were unavailable at implementation time. A37 and the
physical-device matrix remain open; new packaging does not waive either.

### Last Light integration

- [PR #254](https://github.com/Apdelrahman1911/parlor/pull/254) merged the prior
  Last Light/UI/lifecycle work into `main` as
  `8e439075ff2d47e2b848d83625c91fe6349b2e92` on 2026-09-17.
- [Full qualification 35199246020](https://github.com/Apdelrahman1911/parlor/actions/runs/35199246020)
  passed all six mandatory lanes at source
  `4e60afa866b2799c531d0d169179a2e0d71deb2a`. The merge has the same source tree;
  this is not a run executed at the merge SHA or at the subsequent game work.
  This merge used normal required checks, without a protection exception.
- [Issue #253](https://github.com/Apdelrahman1911/parlor/issues/253) tracks resume
  sessions that cannot be deleted. Filing the issue did not delete user saves
  or implement a deletion policy.

### Three additional multiplayer games

Egyptian Dominoes, Ghamza and Word Impostor are implemented on `feat/last-light`,
not merged into `main`. Their rules, research, code/module map, privacy and
recovery contracts are in [the integration guide](THREE_GAMES_INTEGRATION.md).
They reuse protocol 4.2 and the existing P2pKit/start/rejoin infrastructure.

Local verification checkpoint: **`f8af3236862825874204510c91d2b07ffe12bbbc`**,
tree `ff5d001a5939612aa4f5f62a824671a89157d1c4`. Tracked files were unchanged
through verification; unrelated untracked user evidence was preserved. JDK 21,
the wrapper and strict dependency verification were used throughout.

| Local gate | Result at that checkpoint |
|---|---|
| Focused games/app/design-system/transport tests; application identities; shell dispatch | PASS |
| `productionCheck allTests` | PASS; Detekt/type-aware analysis, lint, R8, Android/desktop tests and release-system validation included |
| `productionAppleCheck` | PASS; all three iOS Release framework architectures and Apple static analysis |
| `productionIosSimulatorRuntimeTests` | PASS; executable ARM64 KMP Simulator tests, not x64 runtime on this ARM Mac |
| Swift host/native Debug tests | **10 PASS / 0 FAIL / 0 SKIP**; all new-game setup paths in EN/AR plus existing keyboard/host regressions |
| Android unsigned Release AAB/package/notices | PASS |
| Unsigned iOS Simulator Release Swift wrapper/package/notices | PASS; linkage/package evidence, not physical or signed Release runtime |

JUnit receipts contain 1,653 Desktop tests (three existing skips), 618 iOS
Simulator tests, and 592 Android unit tests per Debug/Release variant, with
zero failures/errors. These are platform-repeated receipts, not unique behavior
counts; unchanged inputs may reuse Gradle's up-to-date test results. Local Apple
checks used **Xcode 26.5 / 17F42**, not the qualified CI **26.3 / 17C529**.

The earlier [three-game CI run](https://github.com/Apdelrahman1911/parlor/actions/runs/35207811517)
exposed unsupported local-mode UI and native text-observation failures. Local
native failures also exposed an incorrect test assumption that Home resets its
catalog scroll after Back. The corrected tests require the full typed value and
the restored catalog; they do not reset navigation or relax the expected text.
Failed runs and rejected package-inspector invocations remain preserved beside
their corrected receipts. Inspector argument corrections changed no release gate.

Detailed local evidence is retained at
`.git/agent-custody/three-games-2026-09-17/checkpoint-f8af3236/`, which is not part
of a fresh clone. For later source, consult the
[validation-only workflow runs](https://github.com/Apdelrahman1911/parlor/actions/workflows/production-verification.yml?query=branch%3Afeat%2Flast-light)
and match the run's exact source SHA; this checkpoint and the earlier Last Light
run do **not** automatically qualify newer commits. The
[physical-device matrix](THREE_GAMES_INTEGRATION.md#physical-device-acceptance-matrix)
remains unexecuted, not waived.

### Earlier readiness checkpoint

- The original **16 repairs** and Whodunit Leave-confirmation timer policy are
  committed. Do not restart that remediation campaign.
- [Full qualification 34682534423/1](https://github.com/Apdelrahman1911/parlor/actions/runs/34682534423)
  passed **all six mandatory jobs and all six scoped cleanups**. It actually ran
  at commit `45df1ccee00d3562601651de8e6905c1f99c30f2`, tree
  `2b7490d54654711d9b12f9ee5d6bf10d2842c431`.
- [PR #248](https://github.com/Apdelrahman1911/parlor/pull/248) merged that exact
  tree into `main` as `f8e2ec3d6aa6669b2f10650c115ae37c38eb67a1`.
  This is **equal-tree integration, not runtime executed at the merge SHA**.
  Later repository-cleanup commits likewise are not executions of that run.

The six jobs covered Linux common/Desktop/Android release and managed-emulator
tests; Desktop on Linux ARM64, macOS x64 and Windows x64; iOS Simulator KMP and
Swift-host runtime; and iOS Release analysis, three framework architectures and
the unsigned Simulator Swift wrapper/package. Release linkage/package checks
are build evidence, not Release runtime or physical-device evidence. Existing
three multi-device skips on each Desktop host and the unselected strict-protection
diagnostic are **not passes**. Repeated host tests are not unique behavior counts.

GitHub did not expose the successful manual run as the required PR-check rollup.
The owner authorized a **one-time, main-only required-check enforcement
exception**, followed by the merge and exact restoration of the original
configurable policy/effective protections. Independent review verified
restoration and temporary-helper retirement; `testing` and `release` remained
protected. The missing PR checks were not fabricated or relabeled PASS. The PR
records the procedure, qualification, preserved failures and independent reviews.

Detailed merge/qualification receipts are retained locally under
`.git/agent-custody/branch-consolidation-2026-09-12/` (`final-full-01/` and
`config-exception-01/`). That local custody is **not included in a fresh clone**;
the linked run and PR identify the durable remote checkpoint.

## Still open

| Area | Actual status / next requirement |
|---|---|
| iOS Strict Complete Protection | **A37: 0 PASS / 26 FAIL, unresolved.** No shipping storage fix or waiver was made. Identify the rejecting condition or obtain an explicit, evidence-backed security-policy decision; do not infer Simulator-only causality or physical-device success. |
| Physical-device qualification | Real Android/iOS and mixed-platform LAN, lifecycle, permissions, hotspot and accessibility evidence remains separate from automated/simulator coverage. |
| Store/account/signing | Android now selects `me.parlor.android` and iOS `me.parlor.ios`, replacing the known-colliding identifier. Ownership of both replacements, signed artifacts and Store operations remain unverified. All three Store workflows remain disabled; do not dispatch older operational-branch copies. See [setup instructions](STORE_GITHUB_SETUP.md). |
| Owner/legal/privacy/content | Distribution and content rights, privacy declarations, final metadata/editorial approvals and other owner decisions remain required. |

The [A37 independent evidence review](../remediation-runs/2026-09-08-continuation/reviews/native-a37-retrospective-actual-independent-01.json)
and [H03 error-chain review](../remediation-runs/2026-09-08-continuation/reviews/protection-host-error-h03-native-actual-independent-01.json)
preserve the strict failure and later diagnostic limits. H03's host-only setter
reported Cocoa 256 with underlying POSIX 22; it is not a new A37 application
execution, an identified failing syscall, or proof of the exact rejecting cause.
The [historical execution ledger](../remediation-runs/2026-09-08-continuation/CONTINUE_LATEST.md)
contains earlier L08, normal-Debug B30 and dependency-chain C08 receipts, each
valid only for its recorded source, controls and scope. Its premerge statuses
do not override this checkpoint.

## Continuing work safely

Keep the three-game implementation and qualification on `feat/last-light`; do not merge it
without a separate reviewed qualification and authorization. For unrelated work,
branch from `main`. Retain `fix/local-readiness-2026-09-07`: focused diagnostic
ownership guards still require that exact branch. Create fresh source/control
bindings before a new diagnostic execution; never retarget guards just to remove
a branch. Preserve failures, user work and archived evidence `build/` folders.
Stop owned workers and clean disposable outputs after each verification cycle;
do not delete required evidence or global dependency caches.

Use [release gates](RELEASE_GATES.md) for acceptance requirements and
[the archive index](archives/README.md) for historical plans/handoffs. A green
automated run, an approved merge or this documentation cleanup does not resolve
Strict Complete Protection or approve public/Store release.
