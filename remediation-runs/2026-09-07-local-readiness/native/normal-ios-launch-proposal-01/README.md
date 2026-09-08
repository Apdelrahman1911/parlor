# Normal-source iOS launch observation — campaign controls

The failure-only image diagnostics were adopted from the independently reviewed
`normal-image-diagnostics-draft-01/` after its **163 passing controls** in
`evidence/normal-image-diagnostics-draft-controls-01/`. Canonical control execution
and a fresh independently reviewed source/control binding are still required
before a new native cycle. This tooling adoption is not app-runtime evidence.

The original controls had independent source review and **78 passing synthetic
tests** in `evidence/package-controls-01/` (2026-09-07). The subsequent **99** controls
passed in `evidence/normal-after-native10-controls-01/`. The current suite declares
**187 tests**: the original 177 (actually passed in
`evidence/normal-star-vmmap-controls-01/`) plus 10 new explicit-libproc observer
controls. The 10 additions await execution and independent review. Existing
test methods are unchanged. Test-control success is not app-runtime evidence.

`ios-readiness-06` executed eight successful XCTest repetitions but then failed
report parsing, before the separate ninth-launch provenance observation. Its
failure receipt remains unchanged. The parser now distinguishes the observed
one-method summary from eight destination executions, cross-checking eight
identified leaf repetitions in both XCResult queries. A complete native PASS
still requires a fresh binding, independent exact-control approval and execution.
Root owns the single execution lane; application source is not instrumented here.

`ios-readiness-08` subsequently passed all eight repetitions and packaged-notice
checks, but the separate ninth-launch `sample` header did not match the attested
PID/path predicate. Its raw headers were not retained; `vmmap` was not executed.
That overall **FAIL** remains unchanged. New failure-only diagnostics preserve
header counts, equality flags, public redaction markers and expected-path component
indexes; unknown components/process names are hashed. Raw stacks, mappings and
unknown paths still are not retained. Diagnostics cannot satisfy provenance or
relax any existing PID/path, UUID, artifact, mapping or process-lifetime assertion.

`ios-readiness-10` also passed eight repetitions and notices, then failed the
separate provenance observation. Its retained diagnostic established that only the
username component became literal `USER`; every other installed-launcher path
component and PID matched. That original **FAIL** remains unchanged; `vmmap` did
not run. The new explicit policy maps only exact, complete `/Users/USER/...` aliases
for artifacts in the current OS-account home, fresh owned Simulator UUID and exact
installed `Parlor.app` container. It is enabled only after kernel launcher
attestation; environment `HOME` and tool text cannot authorize it. Non-`/Users`
account homes stay exact-only. Build/DerivedData paths get no aliases. Collisions,
malformed owned rows, raw/alias duplicates, wrong UUIDs and mapping ranges still
fail. Other callers keep strict exact matching by default.

`ios-readiness-13` passed eight repetitions, notices and cleanup, but failed the
separate image observation. Its retained diagnostics established a `USER` header
and literal `*` in the username component of three Binary Images paths: launcher,
debug dylib and Compose framework. Each other component and UUID matched its
unique installed artifact. That original **FAIL** remains unchanged. The finite
exact-string policy now additionally recognizes `/Users/*/...` for **sample
Binary Images only**. This is not glob matching: other components must match in
full, headers and `vmmap` still accept exact/`USER` only, and copied-build/DerivedData
origins remain exact-only. Literal-star observations receive a distinct lossy
presentation label. Existing origin, collision, UUID, address and lifetime checks
remain strict.

These presentation maps are deliberately lossy, not independent kernel attestation
of each loaded non-launcher image's full path or mapped-memory bytes. The new
sample-table policy and successful app `vmmap` binding remain unverified until
a fresh approved native cycle exercises them; synthetic fixtures do not prove
actual provenance or tool support.

## Failure-only image diagnostics

`ios-readiness-11` passed eight XCTest repetitions, both notice-package checks
and cleanup. Its separate image observation failed with **“Runtime contains an
unbound application/framework image.”** The offending Binary Images row was not
retained, so neither its path format nor an explanation is established. That
original FAIL is unchanged. These diagnostics grant **no additional path aliases**.

`external_image_diagnostics.py` examines bounded already-acquired text only after
a parser rejection. Its closed `FAILURE_ONLY_EXTERNAL_IMAGE_FORMAT` result always
has `proves_provenance=false`. It retains only Parlor/Compose names, known
artifact/origin/kind/component indexes, exact UUID-equality/index relationships,
interval metadata, closed public redaction markers, and unknown component
SHA-256/lengths. Indexes refer to sorted canonical keys of the existing artifact
inventory, additionally bound by its canonical JSON value hash. They are
comparisons, never newly authorized paths. No unknown paths, raw UUIDs, stacks,
symbols, unrelated mappings or arbitrary exception messages are retained.
Alternate `__TEXT_EXEC` and malformed literal `__TEXT`-prefixed rows can receive
closed diagnostic-kind labels; the unchanged acceptance parser still rejects
missing required `__TEXT` bindings. This is not support for another native format.

Limits remain 16 MiB input, 65,536 scanned lines, 8,192 bytes per line, 32 relevant
rows, 4,096 path bytes, 128 path components, 64 known-component relationships per
component / 2,048 per path, and 256 KiB serialized output. Each appended row is
budget-checked. Missing or ambiguous Binary Images tables are explicitly unscanned,
not normalized. Bound failures remain diagnostic insufficiency, not provenance.

The reviewed control design allows a single guarded sibling
`vmmap -w` attempt after a successful `sample` command, unchanged post-sample
lifetime, and a `RuntimeError` parser rejection. Native execution still requires
the separate fresh binding and approval below. A fresh same-target preflight
must succeed, command time/output limits are unchanged, and postflight is attempted
even if the command fails. Diagnostic interruption prevents further optional
native work. A sample failure provides **no selected-image reference**; this path
never calls `bind_vmmap`, retries, changes a parser, or reaches provenance PASS.
The original sample exception remains primary; sibling command, lifetime and
diagnostic failures are recorded separately by type. Normal successful-sample
`vmmap` diagnostics may compare only to that actual successful sample result.

Failure-only records never substitute for the provenance document. Original raw
file deletion, process ownership, simulator cleanup, copied-input validation,
source/control binding and final PASS predicates remain unchanged.

### Completed nonzero `vmmap` commands

Native13 actually ran `/usr/bin/vmmap -w` against the attested app and exited
**255**. Its raw output was deleted without command diagnostics; the cause is
**unknown**, not retrospectively inferred. The separate harmless-child capability
probe passed, but is not application provenance or mapping evidence.

The existing command boundary now preserves `vmmap-command-failure.json` only
after an actual completed nonzero `vmmap -w PID` command. Both the normal and
sample-rejection paths use this boundary. The owned, single-link, regular temporary
file is checked before/after a no-follow bounded read, then removed by the existing
finalizer. At most eight lines, 512 bytes and 32 tokens per line are considered.
A closed generic-error vocabulary and the `REQUESTED_PID` marker are retained;
unknown text, paths, addresses and numeric codes are hashed with lengths. Input
is bounded to 16 MiB and serialized diagnostics to 64 KiB. Full-output and line
hashes, lengths, truncation flags and the actual exit status are retained.

The result is explicitly failure-only, `proves_provenance=false`, with **no
inferred failure cause**. Empty output stays explicitly empty. Successful commands,
timeouts, interruptions and output-budget failures cannot become completed
nonzero-command evidence. Diagnostic errors preserve the original failure and
record only their type. No command retry, extra native invocation, entitlement
change or provenance waiver is introduced.

## What this runner does

1. Requires a later explicit `bind_source.py` schema-3 binding of the complete
   current tracked/untracked source identity, and an independently reviewed hash
   of the binding plus the executable controls. Refuses stale source, a reused
   evidence directory, concurrent Gradle/Xcode work, or original build outputs.
2. Reuses the current `scripts/verification/ios-readiness` source-copy, process,
   secondary-FIFO, simulator-signing, and artifact helpers **without calling the
   instrumented runner's `main()` or any instrumentation function**.
3. Creates one fresh synthetic iPhone17Pro/iOS26.5 simulator and one owned source
   copy/Gradle registry/DerivedData allocation. Exactly two copied inputs change:
   the UI-test file and the isolated stop/attested-embedding build phase. Every
   Kotlin, production Swift, resource, dependency, and game input must be unchanged.
   The exact two-file diff and complete before/after input hashes are retained.
4. Builds once and runs one selected XCTest method **eight fixed times**, with
   test-process relaunch enabled, no retry-on-failure and no parallel testing.
   The test adds the checked-in English `AppleLanguages`/`AppleLocale` arguments,
   calls public `XCUIApplication.launch()`, waits for foreground/Home, then records
   six observations over at least ten actual post-home seconds. Each observation
   requires foreground, `parlor-home-brand`, and absence of native alerts.
5. Requires eight actual individually passing execution records in XCResult
   (nested `Test Case Run`, or the reviewed Xcode 26.5 leaf-`Repetition` layout)
   and eight complete, serial UUID marker trains. Leaf IDs, order and durations
   must agree across both queries. No skipped/failed/
   expected-failure run is allowed. Forty-eight samples are **not forty-eight
   tests**. Summary method counts alone cannot establish repetitions. Unknown
   XCResult repetition layouts fail closed for evidence-backed parser review.
6. Afterward, performs **one separate ninth launch** using public
   `simctl launch --terminate-running-process` on the same owned installed app.
   The exact returned PID must be new, same-UID, within this launch interval,
   and executable-path/kernel-lifetime/audit-token attested using the approved
   Darwin helper. After a bounded normal startup wait, public `sample` and
   `vmmap -w` must report the app's launcher, debug dylib, and exactly one Compose
   framework, matching current built/installed file hashes, UUIDs and text mapping
   addresses. Owned copied-build framework origins remain distinct from embedded
   origins; other images are not retained. No app code or DYLD probe is injected.

## Evidence limitations

- This is normal **application source**, not an assertion that Apple's XCTest
  machinery performs no instrumentation. The eight repetitions remain XCTest-
  controlled English launches on one fresh install; they do not test Follow
  System, game/session retention, Arabic gestures, full games, LAN or Store builds.
- Periodic public UI checks are not continuous liveness or per-boot PID/image
  observation. Apple documents that `launch()` terminates an already-running app;
  test UUIDs are not represented as kernel app-process identities.
- The ninth launch's external provenance cannot be borrowed as image proof for
  each earlier XCTest repetition. `sample` suspends the process while sampling;
  this separate launch is not passive startup-timing evidence.
- Reported `sample` image UUIDs are matched against artifact UUIDs, not independently
  read from mapped Mach-O headers. Numeric-PID tools cannot atomically reserve a
  generation; pre/post audit-token checks plus tool PID/path/address checks are
  stated precisely, not upgraded into that guarantee. No mapped-page hash claim.
- Built and installed full native inventories must match. This is not proof of
  release signatures, physical devices, launch-crash history, storage health,
  OS language-override ownership or an absence of other bugs.
- All 26 reviewed notice resources must also match the bound source bytes in
  both built and installed apps. Source-only notice success cannot satisfy this
  package gate, and notice delivery does not establish legal approval.
- Xcode26.5/17F42, SDK/runtime26.5 are the currently researched local tools, **not**
  Store-qualified Xcode26.3/17C529. Read-only host observation on2026-09-07 found
  macOS26.2/25C56, Darwin25.2.0. An earlier helper's “Darwin26” prose must not be
  interpreted as the actual kernel release; its approved implementation is reused.
- The actual app `sample` invocation succeeded in native13, but sample-table
  validation failed. Its app `vmmap` invocation exited 255; the cause is unknown
  because raw output was deleted. A denial/unknown format remains a failed or
  blocked evidence gate, not an app
  defect and not authorization to change entitlements, attach by name, or use sudo.

## Cleanup and resource ownership

The copied shell phase stops its isolated Gradle registry immediately after
embedding; the Python lane also stops it immediately after `xcodebuild`, including
failed/timed-out/interrupted commands, and in finalization. Build artifacts remain
only until XCResult/native-file/provenance inspection ends; that dependency is
recorded. Root source/build directories are never deleted.

Finalization continues across failures: exact owned-device shutdown/deletion,
observed task-worker shutdown, reviewed secondary-FIFO attestation/cleanup,
unknown-holder checks, post-build input comparison, then exact allocation/inode-
checked removal of copied module/build-logic outputs, isolated Gradle registry,
DerivedData, XCResult, app stdout/stderr and temporary files. Shared global-cache
symlinks are checked and unlinked; global caches are never removed. Unknown file
holders/changed ownership preserve the directory and fail cleanup rather than
authorizing a broad kill/delete. Temporary full stack/mapping/device-list data are
deleted rather than copied into reports. Xcode logs are bounded and compressed;
only structured results, the two-file diff and compact metadata are retained.

Allocation canonicalizes its parent before `mkdtemp`, then defers parent signals
through exact directory/device/inode/UID capture and cleanup-owner registration.
Receipt persistence and owner-construction failures retain this in-memory identity
before any subprocess can start. A changed/unattestable allocation is not removed
by guesswork; cleanup fails with evidence for targeted follow-up.

## Root review and execution

Do not execute while another lane is active. Independent review must examine all
new source and the referenced approved helper hashes, then run the **187** synthetic
control tests (not Swift compilation or app runtime):

```sh
/usr/bin/python3 -B remediation-runs/2026-09-07-local-readiness/native/normal-ios-launch-proposal-01/run_control_tests.py
```

Use the root's outer receipt/finalization wrapper even for this pure cycle. The
driver records exact imported-module paths, before/after control hashes, static
test IDs, actual discovered IDs, actual execution count, failures and skips.

After the source freeze, use the adopted `bind_source.py` to create a **new** direct
campaign JSON binding. Never silently refresh an older binding. Inspect:

```sh
/usr/bin/python3 -B .../run_normal_ios_launch.py --control-manifest /absolute/campaign/new-source-binding.json
```

Then independently approve that exact printed combined hash. Pick an unused
`ios-readiness-NN` evidence name; this naming is required by the already-reviewed
owned-phase helper, while `execution_kind` explicitly distinguishes this run.
The root-reviewed execution has this shape:

```sh
/usr/bin/python3 -B .../run_normal_ios_launch.py ios-readiness-NN /absolute/campaign/new-source-binding.json REVIEWED_COMBINED_SHA256 --simulator-signing=adhoc
```

Omit the final option for the existing disabled-signing baseline. Ad-hoc mode is
explicit, local, credential-free and must satisfy the actual app-phase signing
preflight; this is not Store signing or publication authorization. No app-source
freeze or native success is implied by this draft's control freeze.

Research receipts/gzipped public sources are adjacent. Apple current launch docs,
the installed Xcode repetition manpage and xcresult0.1.0 schemas, installed
`sample(1)`/`vmmap(1)`, Apple vmmap documentation, installed LLDB help, and current
LLVM command mapping were considered. LLDB was researched but is **not** used as
an unreviewed automatic fallback in this bounded runner.

## Explicit alternate selected-image observer

Native15 passed the eight normal-source repetitions and package/notices checks,
but **failed provenance**: vmmap returned 255 with a DYLD-info acquisition error.
Its installed public diagnostic says “Assuming … minimal corpse”; that is not
proof of a crash, OOM, or permission denial. The original FAIL is unchanged.
Research: `../../reviews/normal-native15-vmmap-libproc-review-01/review.md`.

A separately selected `--image-observer=libproc` uses a host-only C executable
compiled against the installed macOS SDK. It is never injected into Parlor, never
uses private credentials, and never falls back to another observer on failure.
Default vmmap behavior is preserved. Both methods retain the exact same source,
artifact, signature, sample UUID/path, process-lifetime and cleanup checks.

After the strict sample parser selects images, the new method queries **every**
selected start using `proc_pidinfo(PROC_PIDREGIONPATHINFO)`. The kernel can advance
past address holes, so the returned region must start at exactly that address.
It must fit the sampled image range, be readable/executable/nonwritable with zero
offset, and supply the exact canonical path plus matching vnode device, inode and
file size. Partial returns, empty/unbounded paths, changed files/process lifetimes,
unknown JSON fields, rejected observations and helper changes fail closed. A
bounded numeric/boolean receipt retains no unbound path or app-memory contents.
The one task-owned helper and its compile output are removed by existing cleanup.

This is **selected executable-region/backing-file corroboration**, not a second
Mach-O UUID reader or whole-address-space enumeration. UUIDs still come from
sample, bound to file UUIDs/hashes. Unlike full vmmap output, per-address queries
do not enumerate extra mappings absent from sample; sample's rejection of unbound
application images remains necessary. Zero-offset RX is a narrow contract for
this run's thin ARM64 Debug images, not a universal Mach-O rule. Numeric-PID
identity brackets are not atomic reservations; mapped pages are not hashed.

The libproc mode is unexecuted until a separately bound native run succeeds. A
control test, compiler success or harmless-process result cannot establish actual
Parlor provenance, physical-device validation, or Store readiness.

## Explicit qualified hosted profile (2026-09-08 continuation)

The original local default remains Xcode26.5/17F42, SDK/runtime26.5. The only
additional profile is selected explicitly with
`--toolchain=qualified-xcode-26.3`; it also requires the inherited
`DEVELOPER_DIR` to be exactly
`/Applications/Xcode_26.3.app/Contents/Developer`. The retained actual tool outputs
must identify Xcode26.3/17C529, simulator SDK26.2 and arm64. Creation and all owned
simulator metadata checks use `com.apple.CoreSimulator.SimRuntime.iOS-26-2`.
Xcode version26.3 does **not** imply an iOS26.3 SDK. An unknown/duplicate profile,
missing Developer selection, wrong tool output or wrong runtime fails closed.

The pure shared helper `scripts/verification/ios-readiness/toolchain_profiles.py`
is bound by the existing support control manifest. Native code, libproc guards,
eight-repetition/ninth-observation scope, ad-hoc-only signing and exact finalizer
are unchanged. The published official image inventory and immutable source URL
are retained under `../../reviews/hosted-native-portability-01/`; they are not
actual host/runtime evidence. A fresh actual-runner source binding and separately
approved control hash remain mandatory after the source/inventory freeze.

Hosted cache prerequisites are the existing public
`$HOME/Library/Android/sdk` and `$GRADLE_USER_HOME/{caches,wrapper}` (or the existing
`$HOME/.gradle` default). Only dependency/distribution caches are shared into the
fresh owned copy; Gradle build/configuration caching remains disabled. Never
remove global caches or infer runtime execution from cache hits. Each native lane
must finish its immediate Gradle stop, owned simulator/workers/FIFOs and temporary
copy/DerivedData cleanup before the next lane starts.
