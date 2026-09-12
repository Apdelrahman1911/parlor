# Project status — 2026-09-12

**The readiness continuation is integrated into `main`; this is not Store-release
approval.** Source and executable contracts remain authoritative. Start with
[the documentation index](README.md), not an archived handoff's old task list.

## Completed checkpoint and evidence

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
| Store/account/signing | Application identity ownership/collision, signed artifacts and Store operations remain unresolved external work. All three Store workflows remain disabled; do not dispatch older operational-branch copies. |
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

Develop from `main`. Retain `fix/local-readiness-2026-09-07`: focused diagnostic
ownership guards still require that exact branch. Create fresh source/control
bindings before a new diagnostic execution; never retarget guards just to remove
a branch. Preserve failures, user work and archived evidence `build/` folders.
Stop owned workers and clean disposable outputs after each verification cycle;
do not delete required evidence or global dependency caches.

Use [release gates](RELEASE_GATES.md) for acceptance requirements and
[the archive index](archives/README.md) for historical plans/handoffs. A green
automated run, an approved merge or this documentation cleanup does not resolve
Strict Complete Protection or approve public/Store release.
