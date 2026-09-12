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
requests; an appended fourth contrasts **Complete without Atomic**. A directory
requests Complete too. The original14 event positions are unchanged; the
non-atomic write/sample and a current-directory sample append three events.
Each sample opens a read-only,
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

Before cleanup, one additional standalone **macOS** reader observes the exact
still-owned final directory and four file inodes, in this fixed order:

| Simulator witness | Owned relative path |
| --- | --- |
| `directory-final` | `created` |
| `complete-after-replace` | `created/complete.bin` |
| `none-after-url-set` | `created/none.bin` |
| `default-baseline` | `created/default.bin` |
| `nonatomic-complete-baseline` | `created/nonatomic-complete.bin` |

All seven stat fields must match, including the **final** directory size. No
stale pre-write directory witness or replaced-away Complete inode is reused.
The host takes no path arguments: validated simulator identities and the owned
root are compiled into its context. Canonical root/parent/target custody brackets
every sample; the sampler still brackets every API read with descriptor/path
equality. Host reads never write/set protection or read file contents.

Only **after those unchanged five host samples and their read-only report have
been saved and validated**, a separate no-argument macOS executable requests
Complete once with public `NSFileManager.setAttributes:ofItemAtPath:error:`.
Its sole target is the existing `created/none.bin`, index2 of the same compiled
five-identity context. It cannot select another path, create/replace a file, or
retry the API. Fresh sampler observations bracket that single setter, with the
same full inode, root/parent custody, descriptor-close and method-image guards.
The public BOOL, bounded NSError domain/code and actual setter IMP before/after
are retained even when the setter fails or the resulting class is unchanged.
The original simulator and five-target host baselines are not rewritten.

The simulator sampler remains byte-identical, SHA256
`3f4f49af81d396a07beabeed45be649314495fd286327baa931aa5eb2dcca137`.
`host_sampler.py` creates a separately pinned host-only copy. H01's actual
Foundation/CoreFoundation method images had ordered platforms `[1,6]`; only
that platform predicate changes, retaining every other image guard. The pure
application sample validator admits that pair only for FileManager/Foundation
and NSURL/CoreFoundation method roles. Host main must remain `[1]`, and every
simulator image must remain `[7]`. This is not a general multi-platform bypass.

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
All20 packet controls (including both host templates/transform/tests and the pure
application receipt validator), imported helper transitives, workflow, wrapper and
shipping write source are hashed by `controls`. Commit reviewed changes, then
the mechanical inventory, before generating the approved digest for the freeze.

Native execution is closed to `production-verification.yml` job/scope
`ios-protection-probe`, branch `fix/local-readiness-2026-09-07`, matching exact
`frozen_source_sha` / actual checkout / workflow SHA, clean tracked full history,
and `approved_probe_control_sha256`. It has no remote preflight or broad native
continuation scope. Standard honest GitHub context and public JDK21 are required;
JDK21 is used only for mandatory `./gradlew --stop`. Arbitrary loader, token,
Java/Gradle option and simulator-identity overrides are not passed to tools.

All source guards retain the full tracked/staged status check at60s, with only
the per-command `-c core.preloadIndex=false` override; the other five Git commands
remain unchanged at20s. This disables Git's optional parallel index/stat preload,
not tracked paths or dirty-state checks. It is a scheduling hypothesis for PD07's
post-boot timeout, not an established cause or measured performance improvement.

The workflow sequence is `run` → upload evidence → `cleanup` → upload cleanup →
`assert-result`. Both immutable artifact IDs/digests and successful upload/step
outcomes are required. Existing `PARLOR_PROTECTION_*` environment names are
declared explicitly in the reviewed workflow. Output directories are:

`$RUNNER_TEMP/parlor-protection-probe-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT/{evidence,cleanup}`

## Results and retirement

`CAPTURED_SYNTHETIC_METADATA_NOT_APP_QUALIFICATION` means collection completed.
The **separate** five-comparison `strict_synthetic_complete` retains the original
four required sample IDs unchanged, adding only `nonatomic-complete-baseline`.
It remains FAIL if any required FileManager value is missing or not Complete. A zero
exit for collection **does not** pass that strict result, historical A37's26
checks, Parlor functional storage, native app provenance or readiness. Unsupported
APIs, missing returned bits, native errno and failed setters remain explicit
observations, not successful class evidence. Inspect before another native run.

`host-report.json` separately records same-inode simulator/host Foundation values
and raw syscall/filesystem observations. Its
`CAPTURED_SAME_INODE_HOST_METADATA_NOT_PROTECTION_PASS` cannot replace the
simulator's strict result, prove implementation causality, or promote A37.
Both raw stdout/stderr streams are saved before exit/schema checks. Retained
generated headers, fixed identity map, source-copy hash/stat witnesses and a
compact host-sampler diff bind the extra build. Source inputs and the host
binary are re-attested after the owned host child is reaped; `assert-result`
revalidates both raw reports and their source/context/image/identity bindings.

The additive setter has distinct `host-setter.stdout.json`,
`host-setter.stderr.txt`, `host-setter-bindings.json` and
`host-setter-report.json` receipts. Its
`CAPTURED_SAME_INODE_HOST_SETTER_NOT_PROTECTION_PASS` means only that the
one-call observation and same-inode witnesses were captured, regardless of the
BOOL/error/class result. This is **not** a new strict protection PASS. The
original `strict_synthetic_complete`, historical A37, app qualification and
causality claims remain unchanged. The bindings retain the preceding read-only
raw/report hashes and command position, the same header/sampler source identities
and a separate setter main UUID/file hash. Sources and binary are re-attested
after that direct child is reaped and again before owned scratch retirement;
`assert-result` checks the saved command order, raw result and exact retired
entries without executing another reader or setter.

The run budget is480s plus90s bounded finalization; cleanup is300s plus a final
bounded stop reserve. Each native self-alarm bounds its executable to25s; the
reader and setter host children each have a30s driver bound. The setter adds
exactly five command rows: compile90s, ad-hoc sign30s, verify30s, UUID30s and
observation30s. It adds no SDK requery, timeout sample, fallback or retry. Static
maximum paths remain71 run rows and44 cleanup rows (including the existing
20-poll shutdown bound and final stops), below each instance's unchanged96-row
cap. These are row ceilings, not a claim that native execution fits its time
budget; insufficient remaining time still refuses launch. Simulator and host
phases are one bounded native
observation/build cycle, followed immediately by the existing Gradle stop.
No app rebuild or new workflow is involved. Existing
`native_process_probe.Commands` retains/reaps only owned Popen handles; existing
`owned_ci_simulator` journal semantics authorize only the newly created exact
simulator. The small create wrapper merely chooses the explicit runtime/type
instead of the generic helper's first available iPhone. It does not call, fake,
or relax AppHost, full-verification or native-continuation cleanup guards.

The exact `/usr/bin/xcrun simctl list runtimes --json` at its unchanged90s bound,
and the separately admitted exact owned `ProtectionProbe` `simctl spawn` at40s,
opt into one4s symbols-only `sample` of that same direct, unreaped command child
after TIMEOUT is latched. Spawn admission is single-use and binds the owned UUID,
resource custody and compiled image identity/hash, not simulator descendants.
The existing12s sample/retirement reserve must fit the
unchanged run budget. There is no retry or service/PID adoption; raw sample
headers/paths and stderr are discarded. The original ps15 contract is unchanged.
A stack observation cannot turn the timeout into success or establish its cause.

The host SDK version/path are qualified once during pre-boot platform binding,
at the unchanged30s bounds. The retained source/control/nonce-bound attestation
and canonical SDK path are revalidated before host compilation, without repeating
those queries after boot. This scheduling mitigation responds to PD05's lookup
timeout; it neither proves the cause nor changes the host read or strict oracle.

After JDK21 and current source/control validation, the pinned wrapper first
performs its required60s `./gradlew --stop` before simulator allocation, within
the unchanged run budget. This moves wrapper bootstrap earlier after PD06's
download-banner timeout; all later required stops remain unchanged.

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
