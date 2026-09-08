# L08 native Foundation control — unexecuted draft

**Author:** `/root/native_fix_review`. **Status:** draft; no build, test, native
launch, Simulator operation or fixture cleanup has been performed by the author.
Independent review and root's exclusive build lane are required before execution.

**Revision03:** preserves frozen01, frozen02, their reviews and actual native01
failure without rewriting any evidence. Only three C-comparison boxes in the
producer now select explicit `@YES`/`@NO` objects; runner/parser/native operations,
ownership and cleanup remain identical to02. Native01 compiled and exited0 but
correctly failed the strict parser (`native-error-types`): Objective-C C
comparisons boxed as JSON0/1, not Boolean. It remains `CONTROL_FAILED`.
Authoritative language/SDK reasoning and exact actual receipt hashes:
`../l08-foundation-wire-types-research-01.json`.

The original33 synthetic tests are preserved; three new tests replay the immutable
failed native01 wire receipt, reject numeric0/1 at every one of144 Boolean slots,
and guard all three actual producer expressions (including single-site reversions).
These are wire/parser/source controls, **not compiled-producer or native proof**.
This03 revision requires fresh independent review, all36 controls executed, then
strict validation of a fresh actual native output before collection success.

**Prior Revision02:** preserved rejected frozen01 and its review. Addresses the three
independently reported control-safety roots: warning suppression, repeated-signal
finalization, and private-device-set custody/empty proof. Review receipt:
`../l08-native-foundation-control01-independent-review-01.json`.

## Question and evidence boundary

Native09 failed `metadata-directory-protection`: NSFileManager returned a
dictionary without `NSFileProtectionKey`, with no NSError. The file query was
diagnostic-only and also omitted the key; both volume capability queries returned
`supported`. This is not an app-crash diagnosis or a passed L08 storage scenario.

The official Swift Foundation implementation at commit
`60bd7a1d8a730ebdeab2cb037b0519ef3b010ed3` excludes Simulator from the FileManager
protection getter/setter paths. That source has **not** been attested as the exact
installed Foundation binary. Private NSData write/relocation helpers also remain
unexplained. Full research, authoritative URLs and native09 source/receipt hashes:
`../l08-native09-file-protection-research-01.json`.

This control asks whether the **installed** iOS26.5/23F77 Simulator exhibits the
same omission when direct Objective-C Foundation calls remove Kotlin map/string,
Koin, session and application-storage orchestration from the path.

## Deliberately narrow design

- Tiny native command-line executable, target `arm64-apple-ios16.0-simulator`;
  only Foundation and public dyld/Objective-C runtime metadata APIs.
- No app installation, bundle-ID selection, defaults, Keychain, app container,
  application build, XCTest, archived runner, connected phone or existing profile.
- Fresh private Simulator **device set** under a canonical mode0700,
  inode/device/UID-attested `/private/tmp/parlor-foundation-control-*` root.
  The `devices/` child inode is separately pinned/re-attested on every simctl call;
  replacing that child or redirecting it with a symlink blocks all mutation.
- Xcode26.5/17F42, SDK26.5, CoreSimulator1051.54, iOS26.5/23F77, iPhone17 Pro;
  no fallback or implicit runtime download. This is not the repository's
  Store-qualified Xcode26.3/17C529 gate.
- Exact underlying simctl executable is used, not the Xcode shell wrapper that
  can run `xcodebuild -runFirstLaunch`. Static installed-tool evidence is retained
  in `public-tool-evidence.json`; no simctl command ran during drafting.
- Fixed public payload, one new directory, three files <=256 bytes,16 ordered
  rows. No logs include payload, arbitrary attribute dictionaries or native
  exception/error descriptions. NSError presence/domain category/code stay distinct.
  The control observes metadata only: it does not read file contents back or claim
  a plaintext, encrypted-payload, save/load or recovery round trip.

### Native observation order

1. Create a new directory with `NSFileProtectionKey: NSFileProtectionComplete`;
   observe immediately, apply backup exclusion, then observe again.
2. `NSDataWritingAtomic | NSDataWritingFileProtectionComplete`; observe the new
   regular file, apply backup exclusion and observe again. These steps precede
   any explicit directory protection setter, so that setter cannot mask baseline
   create/write behavior.
3. Explicitly set Complete on the directory, observe; write a separate non-atomic
   Complete file, observe; write a third default-atomic file, observe, explicitly
   set Complete, observe.

Each observation includes fresh native FileManager protection, URL volume
capability and URL backup-exclusion queries. **Only regular files** receive a
URL protection-key query. Missing key, unavailable dictionary, Objective-C
exception, nonmatching value and NSError are not normalized into one another.

The executable rejects the wrong Simulator UUID/token/runtime, replaced directory,
noncanonical root, symlinked paths and an unexpected `NSTemporaryDirectory()`.
Root and temporary-directory inodes are compiled into its owned config. A20-second
alarm bounds even a stuck native call. Main-image UUID/platform is bound to the
exact compiled image; loaded NSFileManager image UUID/platform is retained. This
is runtime-image identity, not source provenance for private Foundation internals.

## Execution and ownership

`run_control.py` has no launch at import. Before any action it requires:

1. explicit `--execute`, a fresh cycle name and JDK21 location;
2. a manifest hash matching every draft file;
3. a separate review JSON under campaign `reviews/`, with a reviewer other than
   `/root/native_fix_review`, the same `manifest_sha256`, and decision
   `APPROVED_FOR_ISOLATED_CONTROL`;
4. nonblocking exclusive ownership of campaign `build-lane.lock`.

An approval file is an audit receipt, not a cryptographic authorization system.
Root must verify its author/decision rather than generate a self-approval.

After independent approval, root may first run only the synthetic suite:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s remediation-runs/2026-09-07-local-readiness/reviews/l08-native-foundation-control-03 \
  -p test_control.py -v
```

It exercises parser/identity/command contracts and task-owned temporary fixtures;
Popen/process signalling are mocked. **These tests do not compile or execute the
Objective-C observer, prove CoreSimulator semantics, or count as L08 evidence.**
Capture the suite result and cleanup receipts in a fresh evidence cycle using the
coordinated lane; do not run it concurrently with builds.

The native runner's CLI documents required arguments via `--help`. Do not execute
it from an old review hash. It compiles and ad-hoc-signs only the disposable native
image; it never accesses a signing identity or Store credentials. An explicit
command receipt binds tool hashes, branch/commit/tree, relevant source hashes,
config/image hashes, image UUID, device UUID, command status and bounded output.

### Finalization

Every command is bounded; output is capped. Parent signal handlers only record a
bounded cancellation request; they never throw through a worker finalizer. Explicit
checkpoints stop new work and poll waits at most0.2 seconds apart. Ownership
registration and kill/reap/pipe-close/log steps also mask interruption until safe.
The first and repeated signal identities remain in the final receipt; cancellation
cannot be turned into a successful collection. Cleanup and immediate Gradle-stop
commands remain shielded. Timeouts/interruption terminate only a new Popen group
while its direct child remains unreaped; no ambient PID matching grants kill
authority. Cleanup children restore ordinary signal masks.

- Run `./gradlew --stop` immediately after compilation, including failure. The
  image/cache/config remain only until the following native inspection needs them.
- Never stop a foreign Gradle daemon: this control creates none, so discovering one
  blocks the stop action and is reported rather than harming another task.
- Finalize through another Gradle stop, then shutdown/delete only the exact
  token/type/runtime/UUID device in the private set. Partial-create recovery is
  permitted only for that uniquely matching device after an actual create attempt;
  ambiguous sets are retained. An unexpected pre-create entry is permanently
  quarantined for this run, not "empty" merely because creation did not happen.
- After any CoreSimulator contact, require a positively observed empty private
  set (or exact owned-device cleanup followed by that observation); absence of a
  create attempt is not sufficient. Before any contact, the unchanged fresh-owned
  root may be cleaned without starting CoreSimulator merely for cleanup.
- Require zero `lsof -nP -t +w +D` holders and no lsof diagnostic before
  fd-relative, no-symlink-following removal of the exact scratch inode. Unknown
  owners/mounts/types/remaining holders fail closed. Do not terminate shared
  CoreSimulator services or delete a stale path by basename.
- Preserve compact evidence outside scratch. No repository `build/` directories,
  global caches, previous audit material or user Simulator data are deleted.

Any failure retains exact cleanup status/owned path. A retained path requires
separate ownership-attested follow-up, not a broad rm or an undocumented waiver.
No Kotlin, Xcode project/DerivedData or archived secondary-worker paths are used.

## Interpretation and remaining limitations

`COLLECTION_VALIDATED_NOT_L08_PASS` means only that bounded, source/image-bound
observations were collected and shaped correctly. Operation failure or missing
metadata remains visible in those observations. Zero exit alone is not a storage
test, application pass, repair, physical lock/unlock proof or Store readiness.

- A matching native omission removes Kotlin/application orchestration as necessary
  causes **in this control** and is consistent with the identified upstream guard.
  It does not prove universal Simulator incapability or absent encryption.
- Native Complete versus application omission calls for further comparison of the
  app-container path, directory timing and bindings; do not change production first.
- This CLI uses an owned host-root temporary path, not the Parlor app sandbox.
  Atomic relocation, container-specific behavior, entitlements and actual hardware
  protection are not established. No Foundation private implementation was inspected.
- L08 remains failed/unresolved, including unexecuted later storage boots. The
  original equality assertions, production code and existing receipts are untouched.
- New private device-set support, app-less launch, temp-directory forwarding,
  executable adhoc signing, loaded Foundation identity and cleanup lsof behavior
  are not yet runtime-verified. The runner fails rather than fabricating those facts.

## Files and continuation checkpoint

- `FoundationProtectionControl.m`: native observer; uncompiled.
- `run_control.py`: guarded runner/parser/owner-scoped cleanup; unexecuted.
- `test_control.py`: synthetic regressions; unexecuted.
- `public-tool-evidence.json`: installed public header/tool metadata; read-only.
- `manifest.json`: immutable draft-file hashes for independent review.

Next: independently review the full draft; fix review defects; freeze a new
manifest if any source changes; root runs synthetic checks; only then consider
one controlled native cycle. Keep native09's failed result and cleanup evidence
separate. No production change or L08 policy change is authorized by this draft.
