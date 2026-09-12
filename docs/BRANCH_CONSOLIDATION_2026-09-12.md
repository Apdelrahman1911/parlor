# Branch consolidation decision — 2026-09-12

The owner authorized merging important branches into `main` and deleting
unneeded branches. This supersedes the earlier handoff's no-merge restriction,
not its prohibitions on force-push, Store operations, or destruction of evidence.
This document records branch disposition and safeguards, not a new readiness
certificate or a claim that a pending PR has already merged.

## Reviewed integration

- Starting `main`: `3625d0663ba6eb51338cbd5f9dc45f859ec18846`.
- Reviewed continuation: `b9ce9323a8fae26ac93649d48948b9b589541536`.
- Main is an ancestor of that continuation, with 135 additional commits and no
  main-only commits. Integrate through a protected PR using a merge commit,
  preserving source/evidence history rather than squashing or rebasing it.
- These handoff changes and their mechanical inventory add no application,
  dependency, or verification-control changes.
- D10 remains evidence for `25cd7f57b031bebdcfb4931615782b906610703f`, not a run
  at the integration or delivery SHA. Strict Complete protection remains failed
  under its original oracle. Internal QA is not Store/public-release approval.

## Every remote branch reviewed

| Branch | Reviewed tip | Disposition and reason |
|---|---|---|
| `main` | `3625d0663ba6eb51338cbd5f9dc45f859ec18846` | Keep as the development/default branch; receive reviewed continuation through PR. |
| `fix/local-readiness-2026-09-07` | `b9ce9323a8fae26ac93649d48948b9b589541536` | Merge into main, but retain the ref: focused native/Linux diagnostic controls require this exact branch. |
| `codex/parlor-fr-remediation` | `9cd4040a81c4f2f8fe6f5f161dabcd5351682c02` | Delete stale ref after rechecking its tip; all commits are already in starting main. |
| `codex/parlor-independent-review-9cd4040` | `745fa8218b6b5454741722d068e9d392dae2b5b0` | Delete stale ref; all commits are already in starting main. |
| `fix/issue-103` | `135a53c0a356a4a1c142b05afe0fca030de74c2e` | Delete stale ref; all commits are already in starting main. |
| `fix/issue-72` | `2ae51efb0ad05c38e753e0225f03de09cbfda28f` | Delete stale ref; all commits are already in starting main. |
| `fix/issue-73` | `ede37ee8f6046a5b111269d961f301f7bbfe9bc5` | Delete stale ref; all commits are already in starting main. |
| `mafia/module-and-platform-updates` | `abac6bb5c7306eb616835328973f6725509c6de8` | Delete stale ref; all commits are already in starting main. |
| `whodunit/round-config-and-clue-pool` | `631e2fea7aade9d35d7e799e0236ec8e48ad0c2c` | Delete stale ref; all commits are already in starting main. |
| `testing` | `bc6043c9d0acb9e9441e17b2be6bbb2fb094fbe3` | Keep protected candidate branch defined by release policy; no unique work to merge. Do not advance it as part of housekeeping. |
| `release` | `bc6043c9d0acb9e9441e17b2be6bbb2fb094fbe3` | Keep protected production branch defined by release policy; no unique work to merge. Do not advance a production marker as housekeeping. |
| `dependabot/github_actions/ci-actions-6bc1927ba3` | `c22780663a7cca9c42896f3a186858a606f6ed5c` | Keep unmerged PR #2 for a separate dependency update. Its setup-java 6 patch is not applied, is incomplete for the split workflow, and conflicts with current reviewed action-pin contracts. |

All seven deletion candidates are ancestors of both starting main and the
continuation. Deleting their names does not delete their history. No unique
implementation is discarded. No open PR used those seven heads at review time.

## Protection and automation

Ruleset `20910226` required the obsolete combined check
`iOS tests, release frameworks, and Swift wrapper`. The reviewed correction
replaces it with **both** actual checks:

- `iOS simulator runtime and Swift host`
- `iOS release frameworks and Swift wrapper`

The common/desktop/Android check, Actions integration ID `15368`, strict
up-to-date checks, required PR, review-thread resolution, no bypass actors, and
deletion/non-fast-forward restrictions remain unchanged. Merge requires real
successful checks, not synthetic statuses or old-SHA results. The correction
was applied and read back before this document was committed.

PR/push verification runs independently of Store publication. All three Store
workflows remain server-side disabled; no publication or signing is authorized.
The old `testing`/`release` tips predate current source-level Store guards, so
their workflow copies must not be dispatched or assumed safe merely because
current main has those guards. See `release/MOBILE_RELEASE_KIT_MIGRATION.md`.

Focused diagnostic branch constraints in `scripts/ci/native_continuation.py`
and `scripts/ci/linux_process_probe.py` are intentionally unchanged. Development
can continue on main; future diagnostic work needs reviewed source/control
bindings on its retained authorized branch.

## Local preservation and cleanup

At review there were no live Parlor build outputs/workers requiring cleanup.
Retain historical CI artifacts, failed receipts, and archived `build/` reports;
do not run blanket `git clean -xfd`, delete caches globally, or trim Git history.
The inherited untracked build-lane lock and historical `PAUSED_2026-09-11.md`
remain local user/handoff custody, not unfinished task-owned changes.

Small operation receipts, before/after ruleset JSON, and branch-tip snapshots
are retained locally under
`.git/agent-custody/branch-consolidation-2026-09-12/`; these are not versioned
release evidence. GitHub's PR/check history records the actual integration.
