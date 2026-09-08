# Hosted macOS `ps` source investigation

Research author: `/root/ci_workflow`, 2026-09-08. This is **source research**,
not an executed process-observer control, native probe, or application result.
No tracked source/control file was edited; no test, build, native command,
workflow dispatch, or process query was run for this investigation.

## Observed failure and limits

Both native receipts from Actions run `34216788568`, `ios-readiness-17` and
`ios-readiness-18`, fail with `TimeoutExpired` for
`ps -axo pid=,ppid=,pgid=,lstart=,command=` at the existing 15-second limit.
Both retain `cleanup_status: PASS`. Their source/preflight binding is commit
`25b1c5551aee7630ac22d4b34668dd4b06c71b17`, tree
`629a5d2b699cddb62d61849de3bd185fd4c01378`, qualified Xcode 26.3 / 17C529,
ARM64 iOS 26.2 simulator runtime 23C54. See the retained receipts and the
separate `/root/native_ci` diagnosis for command timing and original-error
masking analysis; cleanup PASS does not change either native FAIL.

These receipts do **not** identify the timed-out `ps` stack/syscall, resolved
`ps` executable/version, or why it exceeded 15 seconds. The pinned official
Apple sources below explain candidate acquisition paths, but are not proven
to be the exact source/build of that runner's executable. An actool log tail,
empty Xcode log, or runner-size guess does not establish argv, Mach, memory
pressure, or simulator work as the cause. No retry or timeout increase is
justified by this research alone.

## Pinned official references

`manifest.json` records each complete downloaded file, exact URL, byte count,
and SHA256. These were individual public reference downloads, not clones.

- Apple `adv_cmds`: `6bed8737a34dbb54782a18f47dccf933a9967a12`.
- Apple `xnu`: `f6217f891ac0bb64f3d375211650a4c1ff8ca1ea`.

Line numbers below refer to the retained, unchanged files, not generated
snippets. Upstream source and the manpage differ in some unavailable-argument
formatting details; the actual code path, not an assumed bracket spelling,
is used here.

## Findings supported by those sources

1. **`comm` is not a metadata-only replacement for `command`.**
   `apple-ps-keyword.c:96,101-102` gives both `comm` and `command` the
   `COMM|USER|DSIZ` flags. `comm` routes through `just_command` and
   `s_just_command`; `apple-ps-print.c:398-411` routes those to the same
   command acquisition as `command`, with argument display disabled.
   `get_command_and_or_args` calls `getproclline` (`print.c:306-345`), whose
   non-zombie path calls `KERN_ARGMAX` and then **per-PID `KERN_PROCARGS2`**
   (`print.c:124-195`). `command` with `-c` also retains this acquisition.
   Removing displayed arguments does not remove the argv-space read.

2. **`ucomm` is the accounting-name field, not full argv.**
   `keyword.c:217` has no COMM/USER/DSIZ flag, and `print.c:415-423` prints
   the existing `kinfo_proc.p_comm` field directly. `ps.1:411-421,439-442,
   555-556` distinguishes mutable/possibly unavailable arguments from the
   accounting name. `apple-kern_sysctl.c:1196-1246` copies the process start
   time and bounded `p_comm` into the process metadata. Thus `ucomm` avoids
   **this argv acquisition path**, but it cannot replace the exact command
   predicates or be used alone to authorize ownership.

3. **The existing `lstart` field does not itself fetch argv.**
   `keyword.c:126` marks USER, but `print.c:681-692` formats
   `p_starttime.tv_sec` with `%c`. The Apple non-FIXME `saveuser` branch
   (`ps.c:1209-1267`) copies the metadata command name, not target argv.
   `ps.1:292-296` documents the format. Keep `LC_ALL=C` and the existing
   identity semantics; this is a seconds-resolution token, not a claim of
   microsecond identity or an authorization to change the identity schema.

4. **`command` triggers acquisition during both dynamic sizing and output.**
   `ps.c:734-748,1159-1174` calls the DSIZ sizing callback for every kept
   process. `print.c:348-355,390-403` acquires the command for sizing, and
   `print.c:358-387` acquires it again for printing. The argument rendering
   also flattens NUL-separated argv into spaces (`print.c:224-253`) and
   applies `strvis` (`338-341`); it is not a lossless argv-vector API.
   Do not promote an alternative parser's apparent match into stronger
   executable identity than the existing reviewed guard provides.

5. **There are other potentially slow paths even without `command`.**
   `ps.c:600-648` gets `KERN_PROC` metadata and has transient-error retry /
   one-second-backoff logic. `ps.c:737-744` calls `get_task_info` for each
   kept process when `__APPLE__ && !PS_ENTITLEMENT_ENFORCED`, regardless of
   these requested fields. `apple-ps-tasks.c:90-250` includes
   `task_read_for_pid`, several `task_info` calls, `mach_vm_region`,
   `task_threads`, and per-thread reads. This is a conditional source path,
   **not proof those calls ran in the failed process**.

6. **The actual installed `ps` variant matters.**
   `apple-ps-ps.h:48-52` sets `PS_ENTITLEMENT_ENFORCED` for macOS only when
   `PS_ENTITLED` is absent. `apple-adv_cmds-project.pbxproj:3342-3351`
   defines `ps_lowpriv` without that macro, while `3564-3573` defines `ps`
   with `-DPS_ENTITLED`. An installation phase (`2444-2454`) maps
   `ps_lowpriv` to `/bin/ps` and `ps` to `/usr/local/bin/ps`; another phase
   (`2415-2434`) installs `ps` to `/bin/ps`. The install script preserves
   the listed input/output ordering (`apple-ps-install-ps.sh:8-19`). No
   phase/variant or PATH resolution for the actual runner is established
   here. Do not switch binaries or assume the entitlement branch to obtain
   a green result.

7. **`KERN_PROCARGS2` can enter target VM acquisition.**
   `apple-kern_sysctl.c:1300-1326` dispatches to `sysctl_procargsx`.
   `1380-1385` looks up the target PID; `1423-1426` saves its argument and
   stack metadata; `1446-1454` enforces credential checks. The explicit
   comment at `1456-1464` says VM code can block. `1472-1509` acquires a
   task/map reference and performs `vm_map_copyin` of the argument region;
   `1473-1477` explicitly notes that exec can leave stale information.
   This supports an acquisition-path hypothesis, not a timed-out-stack
   diagnosis, VM-deadlock claim, or reason to relax timeout/ownership.

8. **Targeted `-p` can restrict argv acquisition to selected rows.**
   A single selector with a single PID uses `KERN_PROC_PID`
   (`ps.c:548-588`). Multi-PID lists can still collect global metadata,
   but non-selected processes are filtered before `keepit`, dynamic
   sizing, and output (`ps.c:666-748`). `ps.1:195-196` documents `-p`;
   avoid combining it with inclusive `-a`/`-A` selectors. The manpage
   `224-233` and source `ps.c:453-458,484-487` document unbounded width for
   repeated `-w` or nonterminal stdout; `command` must remain the final
   field if full displayed text is required (`print.c:365-385`). None of
   this makes a two-stage result atomic or an argv string lossless.

## Current callers that any proposal must preserve

Reopened `scripts/verification/ios-readiness/{owned_lane.py,
run_ios_readiness.py,secondary_fifo.py}` and the corresponding L08 companion
files. This is not a review authorization to edit the frozen clones.

- `Ownership` excludes every baseline PID/start identity, retains actual
  Popen handles, recovers unreaped live owned children, follows current
  identity-checked ancestors and groups, and rechecks PID/start before a
  signal (`owned_lane.py:94-191`). A global metadata table is still needed;
  a UID-only or owned-PID-only table cannot replace it.
- Detached JVM adoption uses successfully parsed **full displayed command**,
  executable basename exactly `java`, and exactly one
  `-Djava.io.tmpdir=<owned>/tmp` argument (`owned_lane.py:82-91,148-155`).
  Prefixes, duplicates, echo strings, or an accounting name are not proof.
- The baseline refuses any command containing the Gradle 8.13 daemon token,
  or whose first displayed token ends in `/xcodebuild`
  (`run_ios_readiness.py:480-483`; companion `497-500`). A whitelist of
  accounting names does not prove equivalence to those predicates.
- `AppHostOwnership` supplies live snapshot rows to `SecondaryFifoLedger`,
  and refuses unknown live file holders (`run_ios_readiness.py:278-315`).
  Global rows may not be silently dropped to make holders disappear.
- FIFO observation parses **every live owned member's current command**, not
  just a fixed list of Apple binary names. An observed FIFO child requires
  its owned parent, current parent argv basename `ibtoold`, exact arguments,
  PID/start/PPID, same UID, and subsequent **fresh** child/parent full-command
  equality checks during UID and inode attestation
  (`secondary_fifo.py:67-73,96-144,186-187`). Cached argv or substituting
  `ucomm` in `command` could silently bypass discovery or invalidate those
  guards. Cleanup also requires the complete global PID set (`201-211`).

## Candidate design assessment -- not approved implementation

A two-stage observer can, in principle, separate complete metadata collection
from deliberately scoped full-command acquisition. A direct change from
`command` to `comm` is ineffective; a direct change to `ucomm` is unsafe.
Restricting argv reads to `ucomm in {java,xcodebuild,ibtoold,...}` is **not
proven complete**: kernel accounting name and argv[0] are different data,
the baseline token predicate is broader, and FIFO discovery is not currently
binary-name-restricted.

A more conservative candidate would collect a complete initial baseline with
the original full-command refusal, then collect complete PID/PPID/PGID/start
metadata at each refresh and fresh full commands for **all non-baseline
identities**, all live owned members and any required owned parent. This
removes repeated argv reads for unrelated baseline identities without pruning
any possible new detached JVM by accounting name. It still queries unrelated
new processes. In this runner the baseline precedes fresh simulator boot, so
many long-lived simulator processes are non-baseline: the benefit is limited
and not demonstrated. Metadata `ps` still retains the other conditional paths
described above. A native metadata API would be a separate, nontrivial
adapter -- the existing UID-only libproc helper lacks this API's complete
PPID/PGID/argv behavior and is not a drop-in.

Any such design must explicitly distinguish metadata-only from actual fresh
argv rows (never fake an empty/current command), retain the baseline refusal,
make every required consumer request fresh argv, and recheck PID/start and
relationships across queries. Process exit, exec, PID reuse, malformed /
partial output, unavailable required argv, nonzero exit, and timeout must
not join mismatched generations, authorize a signal, become an empty clean
snapshot, or hide a file holder. A disappeared ephemeral observation process
is not permission to discard a still-live candidate; disappearance itself
needs an identity-aware observation policy. Identity-only signaling can use
a separately reviewed metadata query without acquiring unrelated argv, but
that is not equivalent to changing the broad snapshot function wholesale.

These are design constraints, not a correctness proof or source correction.
The canonical and companion `owned_lane.py` are frozen clone-hash inputs;
do not edit them or relax their hash guard. Any proposed adapter/composition
requires explicit ownership, independent review, fresh source/control
bindings, focused synthetic regression coverage, and real native evidence.

## Recommendation to the coordinator

Prefer one bounded, source-bound discriminating observation over an unproven
observer rewrite or blind retry. First bind the actual resolved `ps`
executable and host build. A deliberately reviewed observation can distinguish
metadata-only acquisition from full-command acquisition under the relevant
owned workload, retain exact command/elapsed/exit/timeout/scope receipts, and
retain an owned timed-out observer's stack if available without privileges.
Only explicitly owned live subprocess handles may authorize observation or
termination of such a probe. Avoid retaining unrelated full argv or secrets;
use bounded selected metadata/aggregate receipts. A probe failure remains a
failure, not a fallback success or application-runtime proof.

No recommendation here changes the 15-second limit, enables a fallback,
weakens a refusal or cleanup guard, or declares any A/B application result.
Root remains the only build/test/Actions coordinator. Public reference files
and this research evidence are the only task-created outputs; no generated
build directory, simulator, daemon, or temporary worker requires cleanup for
this research-only work.
