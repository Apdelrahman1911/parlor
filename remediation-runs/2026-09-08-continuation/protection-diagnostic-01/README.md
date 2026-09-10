# PD01: focused native protection discriminator

**Authored controls, not execution evidence. A37 remains 0 PASS / 26 FAIL.**
This additive directory changes no shipping file, fixture oracle, game, app ID,
Keychain behavior, or release gate. Root coordinates review, tests and dispatch.

## Scope

One standalone Objective-C Foundation executable runs once on one freshly
journal-owned iPhone 17 Pro, iOS Simulator **26.2/23C54**, with Xcode
**26.3/17C529** on non-root arm64 macOS15. There is no app installation,
Kotlin/Gradle build, physical device, private API/header, signing identity,
network operation, player state, or production snapshot access. Ad-hoc signing
only permits the owned simulator executable to run.

Three fixed public-data files exercise atomic **Complete**, **None** and default
requests. A directory requests Complete too. Each sample opens a read-only,
no-follow descriptor and brackets each read with path/FD device, inode, type,
UID, links and size equality. The five readers are FileManager, fresh NSURL
(protection only for regular files), public `F_GETPROTECTIONCLASS`, public
`fgetattrlist` with returned-attribute masks, and `fstatfs`/`MNT_CPROTECT`.
Every FD closes before returning. No descriptor is held across replacement:
an immutable old-inode witness and a fresh post-write witness record the
transition without provoking a possible open-inode EBUSY fallback.

After immutable baselines and the real Complete replacement, only the owned
None negative control receives setters. `F_SETPROTECTIONCLASS` runs **only** if
the installed public headers define both that command and `PROTECTION_CLASS_A`;
otherwise it records an explicit unavailability, never invented class `1`.
A fresh NSURL then requests Complete on that same negative-control inode.
Neither setter is used to fix or waive an original observation.

Actual receiver classes/selectors and resolved method IMPs are bracketed before
and after each Foundation operation. `dladdr` plus the loaded Mach-O UUID,
platform, executable-segment membership, relative offset and dylib versions
bind the selected entry point. These bindings do **not** reveal private callees,
prove a public-source branch matches the loaded image, hash mapped pages, or
establish lock/keybag enforcement. Samples are bounded bracketing observations,
not atomic whole-filesystem snapshots. Synthetic scratch is outside an app
sandbox; do not assume every production snapshot has its behavior or volume.

## Invocation and admission

```sh
python3 -B remediation-runs/2026-09-08-continuation/protection-diagnostic-01/run_probe.py controls
python3 -B -m unittest discover -s remediation-runs/2026-09-08-continuation/protection-diagnostic-01 -p 'test_probe.py'
```

The first is read-only; the second executes pure synthetic control tests, not
native operations. Root owns this Linux cycle and its immediate Gradle stop.
All six owned files, imported helper transitives, the workflow, wrapper and
shipping write source are hashed by `controls`. Commit reviewed changes, then
the mechanical inventory, before generating the approved digest for the freeze.

Native execution is closed to `production-verification.yml` job/scope
`ios-protection-probe`, branch `fix/local-readiness-2026-09-07`, matching exact
`frozen_source_sha` / actual checkout / workflow SHA, clean tracked full history,
and `approved_probe_control_sha256`. It has no remote preflight or broad native
continuation scope. Standard honest GitHub context and public JDK21 are required;
JDK21 is used only for mandatory `./gradlew --stop`. Arbitrary loader, token,
Java/Gradle option and simulator-identity overrides are not passed to tools.

The workflow sequence is `run` → upload evidence → `cleanup` → upload cleanup →
`assert-result`. Both immutable artifact IDs/digests and successful upload/step
outcomes are required. Existing `PARLOR_PROTECTION_*` environment names are
declared explicitly in the reviewed workflow. Output directories are:

`$RUNNER_TEMP/parlor-protection-probe-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT/{evidence,cleanup}`

## Results and retirement

`CAPTURED_SYNTHETIC_METADATA_NOT_APP_QUALIFICATION` means collection completed.
The **separate** four-comparison `strict_synthetic_complete` result remains FAIL
if any required original FileManager value is missing or not Complete. A zero
exit for collection **does not** pass that strict result, historical A37's26
checks, Parlor functional storage, native app provenance or readiness. Unsupported
APIs, missing returned bits, native errno and failed setters remain explicit
observations, not successful class evidence. Inspect before another native run.

The run budget is480s plus90s bounded finalization; cleanup is300s plus a final
bounded stop reserve. A native self-alarm bounds the executable to25s. Existing
`native_process_probe.Commands` retains/reaps only owned Popen handles; existing
`owned_ci_simulator` journal semantics authorize only the newly created exact
simulator. The small create wrapper merely chooses the explicit runtime/type
instead of the generic helper's first available iPhone. It does not call, fake,
or relax AppHost, full-verification or native-continuation cleanup guards.

Evidence is saved before the immediate post-cycle stop, then uploaded before
destructive retirement. The exact creation journal is copied into the cleanup
artifact; adoption/retirement records cannot become an unuploaded tail of the
first bundle. Cleanup verifies exact simulator shutdown/deletion and device-dir
absence, then removes only attested private native scratch. A failed upload,
uncertain tool timeout, unresolved ownership or preservation failure is retained
as cleanup FAIL, never bypassed. Stops are attempted even after collection fails.
No Gradle clean/configuration task is appropriate because this lane creates no
project build outputs. No global cache, historical evidence `build/`, unrelated
simulator, arbitrary PID/PGID, source or signing file is deleted or signaled.

The separate `protection-application-01` follow-up may reuse the exact sampler
bytes on real copied-app writer paths after this diagnostic is interpreted.
Only that separate source-bound execution can supply actual production-inode
integration evidence. Physical-device, Store-operation and owner/legal gates
remain independent and unexecuted here.
