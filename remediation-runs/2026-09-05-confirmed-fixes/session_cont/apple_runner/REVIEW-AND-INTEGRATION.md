# Apple secondary-worker cleanup — independent review and bounded proposal

Reviewer: `/root/session_cont`. Execution owner: `/root`. Branch `main`, HEAD
`3625d0663ba6eb51338cbd5f9dc45f859ec18846`. This is remediation-owned harness
material only. No Apple, Gradle, simulator, signing, process-control, or test
command was executed by this reviewer. No historical audit material was changed.

## Historical adjudication

**The additive cycle02 cleanup is ownership-supported. The original runner's
cleanup PASS alone was insufficient.** This is a harness/evidence gap, not a new
Parlor application defect. The old finding count must not increase.

I reopened the full 622-line archived app-host runner, its actual ownership
implementation (`run_android_managed_cycle.py:62–92,143–271`), the actual cycle02
process receipt, and the independent omission and additive-correction receipts.
The original runner uses a fresh task-owned temporary root and TMPDIR, but an
Apple child used a different native temporary parent. It remained outside the
runner's `temporary` and `outputs` path lists.

Actual ownership evidence is specific, not a generic process-name match:

- `ibtoold` PID67921, PPID67919, PGID67921, start `Sat Sep 5 17:14:45 2026`,
  observed as a child/group of an identity-checked task process;
- `AssetCatalogSimulatorAgent` PID68144, PPID67921, PGID67921, start
  `Sat Sep 5 17:15:08 2026`, with both exact FIFO arguments;
- both FIFO basenames share UUID `613ED9B4-D4E6-4CDA-ACB8-AA81DFFB0280`;
- their directory is exactly the native temp parent's `ibtoold-67921/IB`,
  not an arbitrary path inferred from a build log;
- recorded lstat metadata identifies UID501, exact device/inodes, directory/FIFO
  types, zero-byte FIFO entries, and birth times during cycle02;
- targeted PID and file-holder checks found no live owner/holder before removal;
- the correction unlinked only the two FIFOs, then removed only empty `IB` and
  `ibtoold-67921`; it performed no process termination or global/temp traversal.

`AppHostOwnership.refresh:169–186` deliberately does not adopt a viewer or shared
service merely because it reads an artifact. Keep that restriction. The worker
was already correctly tracked; this incident was missing **path inventory**,
not evidence authorizing broader process killing. Original finalization
`569–603` checks/removes only primary owned roots and therefore cannot support a
global temporary-file absence claim. The later independent post-check covers
the exact known secondary root, not all Apple caches or unknown global paths.

## Added bounded planner (not yet approved or executed)

`apple_fifo_plan.py` has no subprocess, filesystem, process-control, or import
side effects. It consumes metadata and returns only an identity-guarded exact
four-step plan. It does **not** authorize a shell `rm -rf`, PID-pattern kill, or
later deletion after metadata changes.

`attest_pair` requires:

1. Host and agent were already admitted by this cycle's ownership tracker, which
   excludes baseline processes; both match the current PID/start/command/lineage
   snapshot. No process is adopted by a pathname or lsof access.
2. Explicit selected-Xcode executable allowlists, the observed exact host
   invocation shape, and exactly one of each known FIFO option. Unknown versions,
   options, ambiguous commands or absent short-lived parents fail closed.
3. An exact native temp parent supplied independently by the runner, a directory
   named for that host PID, matching UUID/direction pairs, no traversal/control
   characters, and only the explicit `/var` to `/private/var` platform alias.

`plan_cleanup` additionally requires:

- successful complete before/after no-follow observations for all four paths;
- matching UID, native birth within the observation window, device/inode/type/
  mode/birth identity, zero-byte single-link FIFOs, no symlink ancestors;
- no live or **reused** PID at either recorded worker PID (a reused PID is never
  ours to terminate), and no file holder of any kind;
- exact directory entry sets, with no unknown sibling file or extra FIFO pair.

The 12 test methods in `test_apple_fifo_plan.py` are pure synthetic inputs.
They cover positive exact plans and negative lineage, changed generation/command,
path/UUID/argument binding, inode/UID/type/birth/links, live/reused PID, holders,
unknown children, incomplete metadata, and symlink ancestors. They have **not
been run**. This is proposed hardening, not actual post-build cleanup evidence.

## Integration required before a future Xcode cycle

1. Keep the frozen archived runner unchanged. Create a new remediation-owned
   runner that uses current dirty-tree source identity/hashes; old IOS-R1 source
   bindings intentionally require the old clean tree and are not reusable as-is.
2. Take the shared build lane before allocating a simulator or starting tools.
   Establish an independent canonical macOS user temp parent before overriding
   child TMPDIR; never scan the whole parent or unrelated user directories.
3. After each bounded ownership refresh, discover only already-attested matching
   workers. Record pair process/path evidence durably and capture no-follow
   metadata while the workers are still observable. Bound the number of pairs
   (for example32). Save partial evidence even if later observation fails.
4. After the required immediate `./gradlew --stop`, shut down/delete the exact
   owned simulator and stop only the existing PID/start-attested task workers.
   Keep those shutdown stages independent and bounded even after an earlier
   stage fails. Retain unknown-holder outputs rather than killing the holder.
5. Refresh process identity, no-follow metadata and exact directory children;
   run a bounded, fail-closed lsof query of the exact known root. An unreadable
   scan is not an empty scan. Do not open FIFO contents (that can block).
6. Run the planner. Execute only its exact unlink/rmdir steps with no-follow
   directory handles and a fresh device/inode/type/UID/birth comparison directly
   before each operation. Recheck canonical directory-handle identity too.
   Never recurse through symlinks or remove an unknown child. Record every
   operation; partial cleanup remains a recovery case, not blanket permission.
7. Verify exact primary/secondary paths absent, simulator absent, all attested
   processes gone, global caches unchanged and source stable. Return FAIL if any
   stage or identity/absence check is incomplete. Preserve only compact receipts.
8. Obtain an independent review and execute synthetic runner tests before the
   native attempt. After that attempt, independently reconcile actual receipts.

POSIX path-name deletion is not an atomic compare-inode-and-unlink primitive.
The runner must hold the exclusive task lane, use no-follow directory handles,
recheck just before deletion and assume no unrelated local actor intentionally
replaces task-owned entries concurrently. If replacement/ownership ambiguity is
observed, retain rather than widening permission. SIGKILL/host death cannot run
Python finalization; durable partial receipts enable exact manual recovery.

## Native Gradle test wrapper is a separate, smaller path

The archived `run_storage_native_cycle.py:1–118` and
`run_owned_simulator_cycle.py:1–117` are historical evidence, not approved current
runner implementations. Their finalizers are not staged/deferred; one exception
can prevent subsequent shutdown/delete, a created simulator can outlive missing
stdout, and returned success does not depend on cleanup success. Do not copy
those shortcomings merely because earlier invocations passed.

For ST-C1 `:composeApp:iosSimulatorArm64Test`, the new wrapper should use the
current root-owned Gradle lane, a unique task name/new UUID, recovery by exact
unique name if creation is interrupted before UUID receipt, bounded stages and
deferred parent interruptions during finalization, and final FAIL if cleanup
fails. Never let a create/cleanup command select an existing user's simulator.
No evidence here establishes that Kotlin native test linking invokes ibtool;
keep the Apple FIFO planner conditional on an actually observed worker instead
of inventing an Xcode build requirement for those focused tests.

## Evidence and limitations

All paths below are relative to `audit-runs/2026-09-05-source-audit/`:

- `run_iosr1_apphost_cycle.py` and `run_android_managed_cycle.py`;
- `evidence/iosr1-apphost-02/receipt.json`;
- `evidence/iosr1-apphost-02-cleanup-independent-whodunit-cont.json`;
- `evidence/iosr1-apphost-02-secondary-cleanup/receipt.json`;
- `evidence/iosr1-apphost-02-post-secondary-cleanup-independent-whodunit-cont.json`.

`source-receipt.json` binds reopened source/evidence and newly authored helper
files. The archived successful correction remains historical, not a successful
run of the new helper. Root owns all future native/build/process execution.
No claim of physical-device, signing, Keychain durability, release, or complete
temporary-file coverage follows from this review.
