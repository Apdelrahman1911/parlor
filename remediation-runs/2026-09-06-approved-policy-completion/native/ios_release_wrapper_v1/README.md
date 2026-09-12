# Unsigned Swift Release wrapper — source-identical, build-only

Author: `/root/native_fix_review`; independent reviewer: `/root/factory_review`.
This is task-owned verification infrastructure, not a production change. Root
owns the only execution/build/cleanup lane. Draft/control review is not evidence
that a build or any tests executed.

## Scope

Build the **Release** Swift wrapper with `xcodebuild build`, generic iOS Simulator,
`ARCHS=arm64`, `ONLY_ACTIVE_ARCH=YES`, and code signing disabled. This is the
separate local gate in `docs/RELEASE_GATES.md:20`, not `productionAppleCheck`
framework linkage or the already-executed Debug smoke. No simulator is created,
booted or installed to; no application, XCTest, archive, export, signing,
credential, provisioning, Store or publication operation is requested.

The canonical `com.parlor.app` identity is only compiled, never installed. Its
known Store collision and disabled publication safeguards remain unchanged.
Installed Xcode26.5 evidence is not qualified Xcode26.3/17C529 Store evidence.

## Source and cleanup

The 669-input freeze02 source manifest is
`9ac2be536979f2216b39c10a6d148125a12380987d4efd483bbcbaeb1733318e`, tracked diff
`148fb4583d8fa8341b3cbc48a7fc0bc7564bf4df5c27e9d80e32c6bfdc57b784`, HEAD
`3625d0663ba6eb51338cbd5f9dc45f859ec18846`. Root's fresh binding copies613 build
inputs into a new owned directory; no existing source, Git/user data, private
`local.properties`, signing material or generated output is copied.

Every application/test/resource byte is unchanged. The only copied source delta
is the already-reviewed Gradle phase template: strict embedding, immediate
isolated `--stop` with both exit statuses, and successful-only normalization.
Unchanged source-copy, PID/start ownership, signal finalizer and secondary-FIFO
cleanup helpers come from `ios_wrapper_smoke_v1`. Only simulator/XCTest blocks
are removed from the derived runner; no ownership redesign is introduced.

After Xcode (including failure), immediately stop the isolated Gradle registry.
After inspection, stop again, attest all task workers/FIFOs, compare copied and
original source/control hashes, unlink shared-cache links only, and remove the
owned copy/DerivedData/home/temp. Unknown holders fail cleanup. No original
worktree output, global Gradle cache, device, user app or other task is removed.

## Artifact proof

Require Xcode exit0 and exact nested Gradle build0/stop0 receipt **before** any
artifact can qualify; stale files cannot mask failure. Inspect the fresh Release
`.app` and require main executable plus embedded ComposeApp framework. Stream
hash every regular file within explicit count/size bounds; discover and inspect
every Mach-O (including unexpected debug/helper dylibs) using `file`, `lipo` and
`vtool`. Require arm64/iOS Simulator and binary minimum OS no greater than16.

App plist ID, version/build, minimum16.0, platform, EN/AR and network declarations
must match source. Framework versions/minimums are separate; do not force the
app's1.0.0/16.0 values on the KGP framework. Compare shipped privacy manifest
structurally; bind raw shipped cases/fonts to copied source hashes and inventory
compiled assets rather than pretending compiled bytes equal source XML/PNG.

`release-artifact-inventory.json` is compact evidence, not a signed candidate.
Typed receipt fields include `build_evidence_status`, `artifact_inspection_status`,
`build_configuration=Release`, `build_platform=iOS Simulator`, `architectures`,
and the inventory SHA. No runtime or signature-integrity claim is made.

## Root-only sequence

After source binding and independent control approval:

```sh
/usr/bin/python3 -B -m unittest discover \
  -s remediation-runs/2026-09-06-approved-policy-completion/native/ios_release_wrapper_v1 \
  -p 'test_*.py' -v
```

The fifteen new synthetic artifact/source contracts and unchanged FIFO controls
must pass under the root cycle finalizer. Then obtain separate native-build GO:

```sh
/usr/bin/python3 -B \
  remediation-runs/2026-09-06-approved-policy-completion/native/ios_release_wrapper_v1/run_ios_release_wrapper_cycle.py \
  ios-wrapper-release-01 INDEPENDENTLY_REVIEWED_CONTROL_SHA256
```

Only a completed build, artifact inspection, exact source/control/copy binding
and cleanup can yield PASS. Compilation is not app runtime, physical LAN,
signed-delivery, accessibility, legal or Store qualification.
