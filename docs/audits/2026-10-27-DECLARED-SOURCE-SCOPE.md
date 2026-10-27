# Declared architecture input scope — 2026-10-27

## Confirmed defect

[Issue #269](https://github.com/Apdelrahman1911/parlor/issues/269) is a P2 local
verification defect, not a shipping fixture leak. At clean original commit
`3806921f4902f5d841da7239c70c6c358b374adf`, the five
`TestTransportIsolationContractTest` cases executed with two assertion failures.
Retained ignored checkout copies were incorrectly classified as production
Kotlin sources and duplicate active build consumers by `repositoryRoot.walkTopDown()`.
The failed log SHA256 is
`277ff28cf791680cb6afeeb7cff4dffaf7b42121d8e66340aa96671786c6cf36`.
Source and the expected five test-only dependency consumers were unchanged.

## Repair

`:shared:transport-p2p:exportArchitectureContractInputs` exports the actual
configured project build files and KMP Main Kotlin FileTrees. Compiler
includes/excludes and newly created/untracked source are honored. There is no
Git-only allowlist, root-recursive scan or blanket exclusion of ignored paths:
an explicitly included future project inside build output is still checked.
Generated Main-source providers retain their Gradle task dependencies.

The desktop architecture test consumes this index as a tracked test input.
A bounded, fail-closed reader rejects missing, oversized, malformed, duplicated,
unknown, redirected or trailing data. Index framing is limited to 1 MiB and
20,000 records. Original shipping-import rejection and the exact expected
test-only build consumers remain unchanged. No shipping source, dependencies,
verification policy or repository archives are removed.

## Executed regression evidence

Working-tree prototype: **13 cases pass** (five existing plus eight input-reader
cases). This is not committed-source or native runtime qualification.

Eight integration/fault exercises passed their expected outcomes, with ignored
copies retained, on October 27 12:20–12:24 UTC:

1. Existing ignored checkout/source/build copies: pass (valid unchanged-input reuse).
2. Newly created/untracked real iOS Main fixture import: assertion failure.
3. Remove only the owned leak: pass.
4. Declare a future KMP project inside ignored build output, using a custom
   source root and an excluded fake import: pass.
5. Add a new genuine fixture import to that future project's iOS Main: assertion failure.
6. Add its shipping fixture dependency: assertion failure.
7. Restore only those owned future-module changes: pass.
8. Restore original settings with all ignored copies retained: pass.

The three negative XML receipts confirm architecture assertions failed, not
compiler errors. Exported future-module inputs were preserved. Original settings
were restored exactly; owned private Gradle-registry stop returned 0.
Exercise-receipt SHA256: `557883103a50003668a788a0778629ab1fdda0458b427a778519d5f581fa4315`.
Custody remains beneath
`.git/agent-custody/overhaul-2026-10-27/diagnostic-controls/source-scope-3806921f/`.

An initial extended-exercise command was accidentally run from the frozen
original checkout; its unchanged baseline correctly failed before source/settings
mutation. That administrative failure is preserved, not relabeled successful.
The corrected helper asserts its dedicated worktree before making owned changes.

## Remaining gates

Committed-source strict transport tests, plain/type-aware Detekt, release
controls/inventory and exact-head hosted qualification remain required before
protected integration. Keep #269 open until verified integration. This repairs
the two reproduced isolation scans, not every other repository text scan.
Generated-source execution is not Apple compilation/runtime. No physical device,
mobile performance, privacy acceptance or Store release is certified.
