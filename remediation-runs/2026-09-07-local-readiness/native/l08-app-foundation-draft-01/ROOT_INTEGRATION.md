# Root-only integration contract

Proposal, not an adopted/executed runner. The six executable/template files are
frozen by `../../reviews/l08-app-foundation-executable-freeze-01.json`.
Compose over **functional companion02**; do not modify companion01/02 or clone
their whole fixture trees. A new thin driver may reuse the frozen companion
imports/templates, but must bind both dependency sets and itself.

## 1. Binding and allocation

Keep the inherited `control_files`/`control_hash` protections. Include every
inherited companion02 control, all files in this draft, the exact control03
`run_control.py` validator and Objective-C reference, the new driver/adapter
hooks, and the fresh source-binding file in the final reviewed control manifest.
Do not substitute the old native/source control hash for this new composition.
Import these adapters from this frozen directory: their root resolution depends
on this location. They have no CLI, process, device, or deletion entry point.

Capture `path/device/inode/uid` custody immediately when `mkdtemp` creates the
new task root, before another call can lose ownership. Preserve it durably under
the inherited deferred-signal allocation boundary. When canonicalizing the path,
require the same inode/device/UID; pass the canonical attested custody unchanged
to the adapter. Do not fabricate custody from a later arbitrary existing folder.
Keep the inherited private `copy/` allocation and finalizer.

## 2. Copy hook

Original companion02 runner lines 567–635 perform all inherited transforms and
the strict `inspect_copied_manifest`. Keep that inspector **before** this adapter.
Then call:

```python
copied_source_manifest, app_foundation_binding = apply_owned_adapter(
    temp / 'copy', copied_source_manifest, creation_attested_custody,
    receipt['source_before'], approved, mode,
    Path.home() / 'Library/Developer/CoreSimulator/Devices')
```

Regenerate `copied-source.diff` **after** this call from original source to the
final copy. Its modification set remains the inherited set (the three changed
files already belong to it); its addition set is inherited `ADDITIONS` plus
`app_foundation_copy.ADDITIONS`. Record the returned manifest/binding and their
hashes before compiling. The existing post-build input inspector accepts these
`iosApp/` additions; pass it the extended manifest, not the inherited one.

The adapter inserts only one explicit Foundation action on existing readiness
boot1, before its health action. No new XCTest method or boot is introduced.
Original five XCTest methods, health8, functional storage13, host3, scene/action
assertions, main/Compose image checks, and strict L08 classification remain.

## 3. Preserve before classifying

After actual `get_app_container` and inherited bounded readiness-result copying
(runner 680–691), validate that `probe-readiness-result.json` has schemaVersion6,
scenario `readiness`, a UUID `runToken`, and bounded observations. Take the
expected token from that existing context, never from the new receipt itself.

Call `preserve_available(container, dest, uuid, context['runToken'])` before any
later storage/host/native validator can abort. Save its returned names. It reads
only the two exact bounded receipt names, never recursively inspects the sandbox,
and never deletes anything. Preservation and collection validation are distinct.

Ensure this preservation stage also runs after an interrupted/timed-out Xcode
cycle when an exact owned container/context is available, **before** inherited
device deletion. Retain a closed stage/error on absence or failure; do not block
the remaining finalizer or invent missing observations. Do not overwrite a result
already preserved on the normal path. A later XCTest failure must not erase an
already available Foundation observation.

## 4. Independent observation classification

After the unchanged built/installed native image inventories and `dwarfdump`
UUID inventory are available, call `bind_collection` with the preserved parsed
record, returned copy binding, both inventories, exact XCTest log, actual UDID,
and the independently established token. Pass the first native health receipt
when available: its actual `process_id`, `process_boot`, signing mode and token
must match; it is not the later aggregate cold-launch log row.

Only `COLLECTION_VALIDATED_NOT_L08_PASS` is possible. Missing/failed observation,
delivery failure, unavailable image, cleanup failure, or unmatched XCTest/image
proof stays separate FAIL/BLOCKED/NOT_RUN evidence. Do not silently skip this
new required discriminator. A completed observation can remain useful if a later
original matrix assertion fails, but cannot change that failure into success.
Compare the observed Foundation UUID and each ordered row with retained native
control02 execution of control03. If UUIDs differ, report that distinction rather
than claiming the same installed Foundation image was compared.

The original overall gates remain necessary. The new driver may additionally
require validated collection; it must not replace or relax `verify_xctest`,
`runtime_complete`, `classify_final_companion`, original strict L08, signature,
source-copy, or cleanup predicates. Keep the root build lane, immediate Gradle
stop, ownership-attested secondary-worker cleanup and final source comparison.

## 5. Tests and limits

`test_app_foundation.py` declares **30** synthetic parser/copy/source controls,
with an executable exact-count guard. They are not yet executed. They neither
compile nor launch Objective-C/Swift. Source guards cannot prove native cleanup
failure behavior, UI hittability, or hardware protection. Root must independently
review the composed hooks/full diff, execute controls in its shared lane, then
bind native results and cleanup separately. No fresh build is authorized by this
document alone.
