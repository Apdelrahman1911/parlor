# Harmless-child `vmmap` capability probe — isolated proposal

Root must independently review these controls and run the **40 pure tests**
through the existing single-lane outer wrapper before authorizing native use.
The author has not run either the tests or the probe. This is not app evidence.

## Scope

The probe creates exactly one `/bin/sleep 60` child, then invokes exactly one
`/usr/bin/vmmap -w <that numeric PID>`. It never accepts an existing PID, uses
`sample`, examines Parlor or another app, changes entitlements, escalates privilege,
or inherits environment secrets into either child. The fixed child environment
contains only `PATH=/usr/bin:/bin` and `LC_ALL=C`. There are no retries.

The mapper has a 25-second deadline and a 256-KiB parent-drained output bound.
The sole raw file is exclusively created in the root outer cycle's canonical,
same-owner, non-group/world-writable `evidence/<cycle>/scratch/tmp`. The file's
device/inode/UID/type and its parent identity must still match before deletion.
No source/build/global cache is deleted. No new Gradle/Xcode process is started
by this probe; the root wrapper owns its normal stop/cleanup record.

## Process ownership and interruption

Darwin, one Python thread, and default `SIGCHLD` disposition are required. The
sleep `Popen` stays strongly referenced and is deliberately not polled or waited
while vmmap can use its numeric PID; an unexpectedly dead child remains unreaped
until the mapper finishes. Both spawn/ownership assignments and finalization
defer SIGINT/SIGTERM, while the capture loop checks cancellation at most every
0.2 seconds. Cancellation stays nonzero, including a signal during finalization.

Cleanup reaps the mapper before retiring sleep, using only the retained `Popen`
objects, with bounded terminate/wait and kill/wait. A failed mapper reap is an
explicit cleanup failure: sleep is deliberately not reaped there, and the root
outer wrapper must complete cleanup of the task-owned process family. Such an
exceptional run cannot pass. SIGKILL, an OS failure, or abrupt interpreter death
cannot be handled by Python finalizers; the independent outer owner is required.

## Evidence and limitations

Only whole-output lengths/hashes and closed error-vocabulary tokens survive.
Unknown words, paths, UUIDs, addresses and error codes are replaced with hashes.
Diagnostics have at most 16 lines, 1,024 examined bytes and 64 tokens per line.
Unknown formats remain unknown; exit 255 alone never implies permission denial.
Raw output is deleted even after an ordinary nonzero exit, timeout or cancellation.
If ownership changes, deletion fails closed and the retained basename is reported.

Exit zero means only that this fixed harmless-child command completed successfully.
The probe does not validate mappings, application identity, UUIDs, loaded bytes,
Parlor provenance, Simulator permissions, launch health, or Store readiness.
Success here does not explain the earlier app-targeted vmmap failure. A negative
result is useful diagnostic evidence, but is still reported as a failed command.

## Root-only execution

First run `run_control_tests.py` through `run_gradle_cycle.py <unused-name>
--command /usr/bin/python3 -B <absolute-driver-path>`. The driver requires all
40 AST declarations, discovered IDs and actual successful IDs to agree, with no
skips; it records exact imported paths and all four control files before/after.
These mocks never launch a process, write a raw file, or send a real signal.

Freeze the four `.py`/`.md` hashes after review. `run_vmmap_capability.py --manifest`
prints the same sorted-file manifest and combined SHA-256 without native work.
Only then may root invoke the native script through the same outer wrapper,
passing that independently approved combined hash as its **only** argument.
The wrapper must use a fresh cycle directory and preserve the resulting compact
JSON plus stop/cleanup receipts. Do not use shell sequencing that continues after
a failed preflight. No canonical runner or application patch is proposed here.

The retained-Popen, timeout and signal design uses Python's public `subprocess`,
`signal` and `selectors` interfaces; native capability remains unexecuted until
root collects an actual receipt. Official references (applicability: system
Python 3 on this macOS host; no undocumented vmmap format is assumed):
- https://docs.python.org/3/library/subprocess.html#subprocess.Popen.wait
- https://docs.python.org/3/library/subprocess.html#subprocess.Popen.send_signal
- https://docs.python.org/3/library/signal.html#execution-of-python-signal-handlers
- https://docs.python.org/3/library/selectors.html#selectors.BaseSelector.select

These reference URLs are research leads, not a claim of a fresh network fetch.
