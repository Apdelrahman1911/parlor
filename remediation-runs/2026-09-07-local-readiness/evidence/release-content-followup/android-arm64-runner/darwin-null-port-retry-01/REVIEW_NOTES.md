# Successful NULL task-name-port handling — draft 01

Author: `/root/release_fix_review`. This is an excluded campaign-control draft,
not an application change. No tests or native workers were executed by its author.
Root owns execution, artifact retention, and cleanup. Original controls remain
unchanged and are hash-bound in `draft-freeze-01.json`.

## Established source contract, not a guessed runtime cause

The host reports macOS 26.2 / 25C56, Darwin 25.2.0,
`root:xnu-12377.61.12~1/RELEASE_ARM64_T8132`. Apple source at that exact XNU tag:

- `bsd/kern/kern_proc.c:5938–6023`: `task_name_for_pid` can return
  `KERN_SUCCESS` and `MACH_PORT_NULL` when an authorized, non-zombie BSD proc
  has no task. Its ordinary denial path instead returns `KERN_FAILURE`.
- `osfmk/kern/ipc_tt.c:3577–3648,3687–3692`: the name-flavor conversion starts
  with `IP_NULL` and retains that result when `task->ipc_active` is false.
- `osfmk/ipc/ipc_port.c:2940–2974`: copyout preserves an invalid null port;
  a failed copyout can also yield `MACH_PORT_NULL` rather than a task port.

URLs, timestamps, exact-byte hashes and excerpts are retained in
`exact-xnu-references-01.json` and `exact-xnu-copyout-supplement-01.json`.
These establish that success/null is **not equivalent to permission denial**.
They do **not** establish the precise kernel event behind the first run, prove
that an observable process exited, or guarantee a retry will succeed.

The first Android runner failed during `emulator -version`, before AVD creation
or Android launch; its original failure receipt must remain a failure. Root's
separate cleanup supplement attests the later absence/removal of that run's
owned workers and files. This was not a Parlor crash or an Android test result.

## Correction boundary

Only `_token` changes: retry a successful NULL result at most three times, with
two interruptible 10 ms sleeps. After each unusable task-name result, `_basic`
must independently establish gone/zombie before returning `None`. A live proc
that remains unattestable still raises; nonzero kernel errors still raise
immediately when the proc is present. Native/syscall and scheduler latency have
no asserted hard real-time bound.

No permission-error, `task_info`-error, or dead-port retry is introduced. No
ownership scan, lifetime rule, ledger bound, wait/reap behavior, cancellation
handler, or signal path changes. Positive ports still pass through the original
`finally` deallocation. `read` still compares the BSD lifetime/command fields and
two independently acquired complete audit tokens before publishing an identity.
There is no numeric PID/PGID signal or fallback and no "ignore this process"
branch for persistent NULL.

## Proposed independent verification (not executed here)

From the repository root, let `BASE` name the parent `android-arm64-runner`
directory and `DRAFT="$BASE/darwin-null-port-retry-01"`.

1. Pin actual imported module paths and all six file hashes to the freeze.
2. Original failure witness (must fail on the original helper):
   `PYTHONPATH="$BASE:$DRAFT" /usr/bin/python3 -B -m unittest -v test_task_name_null_retry.NullTaskNamePortTest.test_successful_null_port_then_valid_token_is_retried_not_denied`.
3. Corrected pure control suite, **61 expected test methods**, no skips:
   `PYTHONPATH="$DRAFT:$BASE" /usr/bin/python3 -B -m unittest -v test_darwin_owned_processes test_owned_arm64_smoke test_task_name_null_retry`.
4. Reopen the diff, existing 20 process tests, existing 21 runner tests, and all
   20 new tests. Check positive port release, persistent NULL failure, genuine
   denial, NULL-to-exit, NULL-to-valid, cancellation, malformed token rejection,
   process-lifetime crossing and mismatched token generations.
5. Preserve exact exit codes, actual discovered test IDs and module paths.
   Pure fixtures use self-cleaning generated temporary files only; verify no
   leftover task directories or bytecode outputs. No Gradle run is needed.
6. Only after independent approval may root apply the exact control patch and
   retry its owned runtime lane. Keep staged APKs until that separate inspection
   is complete, and do not overwrite the original failed run.

The existing 41 tests are reused unchanged rather than copied or weakened. The
new tests mock all native calls; they validate Python control behavior, not the
kernel, emulator, Android UI, signing integrity, or physical LAN operation.
