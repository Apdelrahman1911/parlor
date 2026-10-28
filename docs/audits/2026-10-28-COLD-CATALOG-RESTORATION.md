# Cold catalog restoration — confirmed defect and correction

## Root cause and reproduction (#273)

PR #266 preserves a live Home `LazyListState` across navigation disposal, but
its standard saver persists only index/offset. Disposing the **entire saveable
root** and restoring while recovery loading changes the rows before the games.
The old index anchors a different card once recovery becomes ready.

Executed against merged `f4a91ef3`, the new Desktop fixture saves
`SaveableStateRegistry.performSave()`, disposes the root, recreates its registry
and renders loading → ready. English Ghamza shifts from
`(24,132)-(378,400)` to `(24,428)-(378,696)` (296 points); Arabic/200% Ghamza
leaves the rendered tree. Both original regressions fail. XML SHA256:
`3e31cdf6bd7c0fa5aba04de6cca56bc96bfa60ce4fd10a55d8b7f749643ea1f8`.
This is shared saved-composition execution, **not** actual Activity/process
termination, native iOS restoration or #267's lost native tap.

## Implementation and privacy

`HomeCatalogScrollState` remains root-owned. A custom saver persists a public
catalog/chrome key plus pixel offset, not a transient index. Keys are validated
and bounded to128 characters; recovery/session IDs never enter this saved
anchor. Invalid values restore safely; removed/filtered games return to the
catalog heading rather than reuse an unrelated old offset.

Restore once against the ready keys. Preserve the original pending anchor if
the platform saves again during loading. User scrolling, game selection and
filter changes supersede pending restoration. No polling, new transport,
background entitlement, authority change or private-state serialization.

## Executed verification

Focused strict `:composeApp:desktopTest --tests '*HomeCatalog*Test*'
:composeApp:detekt` passes18 cases and Detekt: six cold-root journeys, five
saver/key boundary cases, the two existing live-navigation cases and five other
catalog cases. English/Arabic restored bounds match exactly, including added
and removed recovery rows, a second loading-time save and user-scroll priority.

A one-shot negative control restores the original index-only App wiring:
**all six cold-root tests fail**. XML SHA256:
`7fc23ac2c86c2a4caa7111c56abcf4f21e63e3baf6e7e8bcd17d39152d21b261`.
Mutation removal and owned Gradle stop0 are verified. The restored source then
passes all18 tests again (28s, valid caches reused). Original failing evidence
and control receipts remain untracked under `.git/agent-custody/`.

The first cold-build attempt failed before compilation on Maven Central TLS;
subsequent default-JDK HTTPS controls returned200 and the strict build downloaded
normally. No repository override, certificate bypass or metadata regeneration.

## Remaining acceptance

See [#273](https://github.com/Apdelrahman1911/parlor/issues/273).
Committed-source full strict checks, fresh hosted native/architecture checks,
protected integration and fresh main acceptance are still required. Actual
Android process death/iOS native restoration and physical-device journeys remain
unverified; Desktop registry recreation does not certify them.

## Full-gate regression correction

At frozen4294876f the complete local aggregate and fresh hosted Linux ARM64
run40623162752 both fail exactly one Desktop source-contract assertion:
`ProductionUiAccessibilityContractTest:121` still requires `items = games`.
The refactor now shares `filteredGames` between restoration keys, count and
rendering; it remains derived solely from the registered `games` argument.
The updated guard verifies the exact capability-filter derivation **and**
`items = filteredGames`; original ID/placeholder/localization exclusions remain.
No rendering/privacy assertion or runtime test is removed.

Local full execution has1,817 Desktop cases (one failure, three existing
LAN skips),651 Android unit cases per Debug/Release variant and112 GitHub-test
variant cases, zero errors. Lint/R8/unsigned AAB and749 release controls run;
the full command is failed, not qualified. Original failing XML SHA256:
`0eba566966bdc9212ba8f51bb390282132bf2cf98ea891e13ea3aa4761352e3f`.
The429 checkout/observer remains frozen. Final guard correction needs new
committed-source complete checks and fresh hosted qualification.
