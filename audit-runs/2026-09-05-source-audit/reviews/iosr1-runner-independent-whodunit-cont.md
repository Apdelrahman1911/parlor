# Independent IOS-R1 app-host runner safety review

Reviewer: `/root/whodunit_cont` (not the runner author). Repository: `/Users/abdelrahman/Projects/parlor`, `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Tracked working-tree status was empty at the final review.

## Verdict

**SAFE TO ATTEMPT — not runtime PASS**, for the exact 609-line audit-only runner:

- Path: `audit-runs/2026-09-05-source-audit/run_iosr1_apphost_cycle.py`
- SHA-256: `6922206db73a2b5e9d01f6fab5d5b63a2983f44a0bf54d5beb2e2d06ee3f7262`
- Immutable reviewed bytes: `evidence/iosr1-runner-independent-whodunit-cont/draft-6922206db73a.py`
- Imported Android ownership helper: SHA-256 `b34ad4662ac906bff0a2f4e8ebe25c0757229098361e8e34c29b7c06681730c0`
- Imported identity/output helper: SHA-256 `a0500421159c6fee5ebca996b86f84dd4c877cfd30519f0e2f873fa76b9914bb`

Root alone owns execution. No Gradle, Xcode, simulator/device, app, signing, or native-runtime command was executed by this reviewer. This approval does not classify IOS-R1 as an application defect or establish an actual header, working Swift binding, successful test, healthy recovery, or successful runtime cleanup.

## Source coverage and review lineage

I read the complete initial 393-line `71966f3f0c81…` draft; the complete 575-line `876344e0b9f4…` revision; every change to the 609-line `b40a5bec2d66…` revision; and every final change to `6922206db73a…`, with surrounding execution paths. Each revision is archived under `evidence/iosr1-runner-independent-whodunit-cont/` before review. No earlier version is silently retargeted to the final hash.

I reopened the imported ownership implementation, identity/output-list helper, all eight fixture/template/manifest files, the invocation-only init script, the original Swift host and XCTest, the full 529-line Xcode project, scheme/config/version files, normalization script, and full 615-line `composeApp/build.gradle.kts`. The file/range/hash records are in:

- `coverage/reviews-iosr1-runner-initial-whodunit_cont.jsonl`
- `coverage/reviews-iosr1-runner-final-whodunit_cont.jsonl`

Audit-only files and helper tests do not inflate application-source coverage. The source-binding manifest's other original-file hashes were independently recomputed, not claimed as newly line-reviewed by this bounded task.

## Safety boundaries verified

1. **Single lane and preservation.** Exclusive build-lane locking and a new evidence directory prevent competing agent builds and receipt overwrite. Pre-existing generated output directories refuse execution. Original application/build files are read, not patched. Only an 18-file tracked wrapper/config allowlist is copied; no xcuserdata, signing material, preferences, existing simulator data, or broad source-tree copy occurs.
2. **Invocation isolation.** The two additive Kotlin sources join `iosMain` only through the explicit init script, with original/input hash checks, Debug ARM64-simulator task guards, unsigned context, strict dependency verification, no repository override, and no global init installation. Copied Swift changes retain original Compose creation, lifecycle callbacks, and privacy-cover ordering.
3. **Real source attribution, not substitution.** The fixture uses the existing Koin graph and delegates the actual empty-list/rejoin read results unchanged. It does not host, join, resume, save, discard, or execute a multiplayer reducer. The late empty-result guards alone would not protect old player data; therefore use of a newly created task-owned simulator is a required privacy boundary, not optional convenience.
4. **Header and test evidence.** The actual generated header is preserved before parsing. The current Swift template activates only after class/member and void-block checks; an unfamiliar export fails closed. Actual XCTest JSON must identify the exact one audited method, no failures/skips, and the owned ARM64 simulator. The copied nested Gradle phase must provide its successful immediate-stop receipt. The bounded result JSON is separately validated against the source-dependent schema, including the optional native branch guard.
5. **Process ownership.** The final iOS subclass intentionally bypasses the imported Android helper's open-file adoption. It accepts observed Popen descendants/process groups and the explicit task-only JVM temporary-directory argument. Unattributed file holders block deletion; a new Apple service, Java reader, Swift compiler, or viewer is not adopted merely because it opens or names an artifact. No global process-name kill is used.
6. **Finalization.** Parent-only signal deferral protects allocation and Popen registration without leaking a blocked mask into children. The raw temporary allocation is recorded before canonicalization. Every generating Gradle stage immediately stops its isolated daemon registry; framework/compiler outputs remain only for the directly following copied Swift link/runtime inspection. Finalization independently attempts stop, owned-UUID shutdown/deletion, worker verification, exact generated-directory removal, cache-symlink unlinking, and temporary copy/DerivedData removal. A unique synthetic device name allows scoped recovery if creation finished before printing its UUID. Nonzero cleanup outcomes and source drift cannot become overall PASS.

## Audit-harness corrections reviewed before authorization

These are **not Parlor application findings** and must not enter the confirmed application issue count.

| Earlier weakness | Independent evidence/counter-evidence | Final disposition |
| --- | --- | --- |
| Any new Apple developer executable with an output FD could be killed as owned | A shared Xcode/CoreSimulator service is not task-owned merely because it accesses DerivedData | Broad adoption removed |
| Replacing that heuristic with any exact-temp argv still adopted unrelated viewers; inherited generic Java FD adoption remained | Pure synthetic `tail` case adopted PID9999 in `b40a…`; `synthetic-unrelated-viewer-b40a.json` preserves proof | iOS implementation now disables all open-file adoption; unknowns block cleanup |
| SIGINT/SIGTERM were blocked across Popen | POSIX preserves the calling thread's mask across exec; CPython 3.9.6 `restore_signals` changes dispositions, not that mask | Parent-only caught-handler deferral; root's separate tiny child test also observed empty mask and cooperative TERM exit |
| Temporary allocation could be lost if canonicalization failed | `temp = Path(mkdtemp(...)).resolve()` assigned only after resolution | Raw path and receipt precede resolution |
| Exit codes/minimal result markers could overstate evidence | Successful xcodebuild alone is not exact test execution or valid production attribution; native corroboration has a specific source-result prerequisite | Exact XCTest, native-applicability, embedded-stop, cleanup-status, and final source-equality checks added |
| Imported helpers were absent from input hashes | Actual ownership and output-selection code affect execution | Both imported source files are now bound in input manifest |

## Independent synthetic verification

- **88 checks PASS:** whole-runner Python syntax without execution; actual copied-wrapper patch/context rejection; original lifecycle/privacy suffix preservation; exact XCTest method/count/device checks and malformed/skip/duplicate rejection; successful and failed production-source observations; privacy/schema/native-guard rejection cases; fake parent signal deferral/restoration; all 45 source/addition hash bindings; exact 18-file copy allowlist.
- **9 checks PASS:** fake process-table tests reject unrelated tail/Java/shared-Xcode/Swift FD holders; preserve pre-existing processes; attribute exact task JVM/observed ancestry; reject PID-reuse confusion and symlink scan roots.
- Evidence: `pure-synthetic-6922206db73a.json`, `ownership-synthetic-6922206db73a.json`, and their runnable pure test sources under the independent evidence directory.
- One earlier synthetic assertion was wrong: the XCTest patch does not touch import line1, so changing it should not fail a hunk-context check. That failed test expectation and original script are retained in `pure-synthetic-attempt-01.json` / `verify_runner_purely.attempt-01.py`; the corrected fixture changes a line actually inside its hunk. This was not a runner/application defect.

Root's separately produced `evidence/iosr1-runner-signal-root.json` was reopened: it records an empty child signal mask, exit `-15` on cooperative SIGTERM, and child termination. That is root-run harness evidence against `b40a…`; the signal implementation is unchanged in the approved final revision. It is not app/device evidence and is not included in this reviewer's 97 pure checks.

## Research, cleanup, and remaining limits

Versioned CPython and normative POSIX excerpts, URLs, access dates, and hashes are under `research/iosr1-runner-signal-whodunit-cont/`. The normative counter-evidence distinguishes masks (preserved) from caught handlers (reset). No full third-party source download was retained; only compact excerpts/hashes were written. Two unsuccessful symbol-location lookups are explicitly recorded before locating `_Py_RestoreSignals` in the correct versioned file.

The final review created only task-owned audit source/evidence. No build output, simulator, task server, daemon, or native process was created by this reviewer, so invoking Gradle stop/clean here would be unnecessary and could interfere with root's single lane. Necessary runner snapshots and failed-candidate evidence are retained. No source/private material/global cache was deleted.

Actual compiler/header behavior, XCResult schema compatibility, the new simulator's runtime, native status, and all real cleanup outcomes still require root-run verification. A PASS for receipt delivery may legitimately contain `SecureStorageUnavailable`; it is not a healthy-storage assertion. The subsequent equivalent native read is never the original production query's intercepted status. First Home versus rerun differences must remain explicit. Unknown/shared file holders may cause a deliberate cleanup failure requiring targeted follow-up rather than unauthorized termination.

This attempt cannot establish physical-device LAN behavior, signed Keychain behavior, Store signing/ownership/readiness, complete gameplay, full UI/layout/a11y, restart durability, or a repaired IOS-R1. No production fixes are authorized by this review.
