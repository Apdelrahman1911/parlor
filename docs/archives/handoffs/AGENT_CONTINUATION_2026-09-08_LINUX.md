# Parlor — Linux / GitHub Actions continuation

**In progress; NOT READY.** This supplements, rather than replaces or rewrites,
[the transferred handoff](AGENT_CONTINUATION_2026-09-08.md). Read `AGENTS.md`,
that handoff's architecture/release contracts and referenced reviews first.
For later commits, dispatches and evidence reconciliation, read the
[continuation execution ledger](../remediation-runs/2026-09-08-continuation/CONTINUE_LATEST.md).
The original 16 repairs and Whodunit Leave Confirmation timer policy are already
committed. Do not restart them or reinterpret harness success as app-runtime proof.

## Checkout and evidence

- Repository: `https://github.com/Apdelrahman1911/parlor.git`.
- Branch: `fix/local-readiness-2026-09-07`.
- Full-history Linux checkout: `/root/projects/Parlor/parlor`.
- Verified delivered commit: `ec0de52a2c7ff9077482a291dc4fd2591fdea01f`;
  tree: `48b099f3cd002a3253f21d2b06a4ece5787e62cb`.
- Initial checkout was clean and remote branch SHA matched. Continuation changes
  described below are **not yet committed/pushed or finally qualified** at this
  checkpoint. Inspect newer commits rather than resetting to this baseline.

Paths below use these aliases; they are not separate repositories:

```sh
C=remediation-runs/2026-09-07-local-readiness
N=remediation-runs/2026-09-08-continuation
```

New controls, reviews and the execution ledger live under `$N/`. Ordinary Linux
lane cycles write under `$C/evidence/`; the hosted native adapter writes A to
`$N/evidence/ios-readiness-17/` and B to `$N/evidence/ios-readiness-18/` before
artifact preservation. Preserve historical failures, original bindings and
archived evidence `build/` directories.

Independent baseline audit: `$N/reviews/baseline-evidence-audit-01.json`
(SHA-256 `6c82cd58ce41377f9ca1f20b09ff46fb49da6f0fa8a62595b53a5fd454d0960d`).
All 14,710 delivery payload files / 251,055,182 bytes and Git modes matched;
248 selected referenced hash records matched. Mechanical inventory is explicitly
outside the payload list, as are self-referential handoff records; neither is a
missing-file exception or proof of exhaustive source review.

## Actually executed in this continuation

All five local cycles below retain raw logs and `receipt.json` under
`$C/evidence/`. They ran against the baseline commit plus recorded working-tree
changes, **not the forthcoming frozen commit**. Counts are per cycle, not additive
unique coverage.

| Cycle | Actual outcome |
|---|---|
| `continuation-linux-lane-controls-01` | 21 Python controls PASS; real bounded Java/Javac21 probes. |
| `continuation-ci-controls-01` | 79 workflow/native-orchestration controls PASS; no failures/errors/skips. |
| `continuation-transport-contracts-01` | Strict `:shared:transport-p2p:desktopTest`: 274 PASS, three existing single-JVM loopback skips, zero failures/errors (277 cases / 22 XML suites). |
| `continuation-focused-controls-01` | **FAIL retained:** 358 tests, 356 PASS, one failure and one error; all nine commands executed. |
| `continuation-l08-control-recheck-01` | Corrected single-tap14 and original functional-copy12: **26 PASS**, no failures/errors/skips. |

The focused cycle passed packet16, lane21, schema-bootstrap26, toolchain12,
composition55, normal187 and schema-closure16. Its single-tap13 scanner mistook
Swift's `wait(for:)` label for an activation loop; its functional-copy12 correctly
rejected a changed immutable README. Independent corrections preserved the actual
Swift fixture, narrowed only the scanner's colon-label handling, added baseline/
loop witnesses, and restored README bytes while relocating the new usage section.
The failed cycle remains FAIL; its earlier mutation labels are not reused as
valid witnesses. The corrected26 recheck has its own passing evidence and review.

Both consolidated/recheck cycles captured identical before/after **204-input**
control manifests. The first21 cycle's missing separate test-byte binding remains
a historical limitation; the later bound lane21 execution closes current scope
without retroactively modifying that receipt. All five outer receipts record
stable source/runner manifests, stop0, no remaining owned workers/live outputs,
and no cleanup errors. See the ledger for exact independent review paths.

No new L08 simulator execution, normal Parlor libproc query, final dependency
candidate execution or final five-job qualification is established here.

## Implemented and independently source-reviewed; platform execution pending

- Copy-only L08 single-tap fixture: bounded stable, visible, uncovered exact-card
  geometry plus existing zero-before/one-after callback observation around one
  actual tap. No retry activation, direct navigation/reducer call, counter
  relaxation, snapshot change or production gameplay edit is authorized.
- Independently source-reviewed Linux portability for the ordinary lane: explicit
  JDK21/runtime/compiler validation and exact Android SDK tool selection; existing
  locking, strict verification, ownership and cleanup guards retained.
- Explicit hosted Apple profile and relocated composition binding/freezes:
  qualified Xcode26.3/17C529 with its actual iOS26.2 simulator SDK/runtime. The
  existing local26.5 default remains distinct; no silent toolchain fallback.
- Focused modes in the existing `production-verification.yml`:
  `native-preflight` and `native-evidence`, with `full` remaining the combined
  qualification mode. Their 79 focused controls executed successfully on Linux.
- Owned, pinned schema-prerequisite bootstrap for the actual candidate chain;
  no global installation or waived schema formats.

Source reviews and focused execution do **not** establish native application
behavior, candidate-consumer success or final qualification. Consult the ledger
and exact bound reviews. Only the coordinator owns dispatches and build lanes.

## Remaining execution — A through D

1. Review this documentation checkpoint, reconcile affected source/control hashes,
   then freeze. The focused Linux controls and corrected26 recheck above have
   executed and been independently reconciled; do not repeat unchanged successes
   without a reason. Later relevant docs/manifests/workflow edits still require
   the affected transport contracts; older runs are not newer-source evidence.
2. **Freeze source/control commits first; regenerate inventory second; make an
   inventory-only commit last.** Check inventory at that final SHA, push normally,
   verify remote equality, then create fresh source/control bindings. No old
   source15 or control521a binding is current. Later changes require explicit
   affected-scope requalification and new bindings, not relabelled evidence.
3. **A — L08:** use reviewed focused hosted preflight, independently inspect its
   exact source/run/attempt/artifact/control bindings, then approve actual native
   execution. Complete all 13 functional boots / 20 operations / 26 protection
   comparisons and three retained host fixtures / 42 operations, plus required
   Settings/OS/lifecycle/image paths.
   Native16 remains FAIL: 14/15 functional receipts passed; boot10 Home callback
   stayed zero before Mafia continuation; boots11–13 and host paths were not run.
   All 24 observed strict Complete comparisons failed. Hosted SDK26.2 is a new
   platform comparison, not an advance waiver of that mismatch. Retain strict
   validation and failures; the functional companion cannot establish original
   strict L08 PASS. Report functional and protection-metadata outcomes separately.
4. **B — normal Debug provenance:** actual eight normal-source XCTest repetitions
   plus a separate ninth explicit-libproc image observation, native structure/
   return-size evidence, artifact/signature/notices bindings and cleanup. Historical
   187 controls/compile/mutation evidence is not a Parlor query. Reuse unchanged
   scope only; profile/control changes need affected controls and fresh approval.
   Native15's eight repetitions passed but ninth vmmap provenance failed; its
   historical launch-failure cause remains unknown.
5. **C — dependency chain:** at the frozen source, strict four-graph export using
   the existing init script/task, production rendering, reviewed candidate binding
   and real candidate consumer through owned prerequisites. Require **four**
   coupled receipts: candidate verification, candidate prerequisites, schema-base
   prerequisites and the outer lane, including exact frozen-source/control
   equality and cleanup. The ledger names their acceptance statuses. Successful
   16 synthetic consumer controls/owned bootstrap are not candidate execution.
6. **D — combined qualification:** run `full` in the existing verification workflow
   at the same frozen SHA, collecting all five jobs and five main/five cleanup
   artifacts. Inspect raw tests/tasks/cache/skips, Apple linkage versus runtime,
   package/notices and artifact digests. Independently reconcile A–D and the
   original16/timer evidence, update the execution ledger, commit/push completed work and
   verify the remote. Evidence-only later commits still need an explicit scope
   reconciliation; do not call earlier CI execution a run at a newer commit.

The existing workflow concurrency cancels earlier same-ref runs. Never overlap
focused and full runs or blindly retry failed native work. No Store workflow may
be enabled or dispatched. Do not use compilation or cached tests as fresh runtime.

## Safe operating reminders

Reobserve identity without discarding work:

```sh
git status --short
git rev-parse HEAD HEAD^{tree}
git ls-remote origin refs/heads/fix/local-readiness-2026-09-07
```

Inventory commands, **only in the freeze order above**:

```sh
/usr/bin/python3 -B scripts/generate_review_inventory.py
/usr/bin/python3 -B scripts/generate_review_inventory.py --check
```

Exact dispatch/binding instructions are in `scripts/ci/NATIVE_CONTINUATION.md`;
they require actual fresh identities, not placeholder approval. Native runners own their lock
and finalizer: never nest them inside the ordinary lane. Use one coordinated lane
per environment; do not edit frozen sources/controls while it executes.

After every successful, failed, interrupted or timed-out cycle: preserve evidence,
immediately `./gradlew --stop`, clean only attested task-owned outputs/DerivedData/
simulators/workers, stop Gradle again if cleanup started it, and record verified
cleanup or its failure. Use `./gradlew clean --no-daemon` only when safe. Never
delete global caches, user work/stashes, source/configuration/signing files, or
retained reports merely because their path contains `build/`.

## Unchanged boundaries and readiness limits

Preserve host-only multiplayer reducers, exact protocol4.2, deterministic seed,
public/own-private/host-only isolation, strict snapshots/content, Doctor ON/OFF
semantics, and excluded Leave Confirmation Discussion time. P2pKit0.7.0-rc3 stays
on Maven Central; no mavenLocal/composite/sibling dependency replacement.

Physical LAN/device lifecycle/accessibility/privacy, actual lock/keybag/backup/
restore/power-loss tests remain unperformed here. The observable strict protection
metadata failure is **not** reclassified as physical-only. Private signing,
Store publication/promotion/submission and physical-device work remain excluded.
Known `com.parlor.app` Store collision and disabled publication safeguards remain;
do not choose replacement identities. Owner legal/license/content-rights approval,
final editorial approval, real privacy URLs/questionnaires and Store declarations
are separate requirements. No merge, force-push or issue mutation is authorized.

**Verdict at this checkpoint: NOT READY; execution and independent final
qualification remain unfinished.**
