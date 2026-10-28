# Catalog restoration and failed Simulator query — October 27, 2026

## Executed failure, not an Intel compiler failure

[Full run 40563194924](https://github.com/Apdelrahman1911/parlor/actions/runs/40563194924)
executes frozen `711cc1ab`. Seven of eight producer lanes and the original
required Apple Release join pass. All three standard Intel Release jobs pass:
simulator arm64/Swift 43.63 minutes, x64 35.53, device arm64 34.22. The failed
whole-workflow elapsed time of 47.12 minutes is **not a qualified Intel winner**.
ARM64 remains the default; no paid infrastructure or protection change is used.

The separate ARM64 `macos-15` Simulator runtime fails one of 16 Swift tests:
`testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish`. After successful
Ghamza Host/Continue/Back navigation, the reopened catalog control cannot obtain
two adjacent valid equal snapshots within the unchanged ten-second deadline.
The first enabled frame is `(24,703,354,67.667)`, query time 6.656 seconds;
the second query takes 2.265 seconds and returns nil. **No tap is synthesized
on this failing control.** This is different from #267's original synthesized
Host tap that did not navigate; neither root cause is established by nil alone.

The source-bound runtime ZIP (artifact 12765137244) has SHA256
`417aa1a165b05994f6a8228d212f9adec1759b434dd0b0fe47e230ad6ef86d87`.
Read-only bounded decoding retains the public screenshot and recording (decoded
MP4 SHA256 recorded in local custody). Only Ghamza's title is visible above the
bottom navigation after Back; the preceding card is now the scroll anchor.
Recording offsets are not assumed equal to XCTest wall-clock offsets.

## Confirmed shared application defect — #273

The shipping Home composable plus a public-only saved-entry regression reproduces
an independent wrong-position invariant at base `97500656`: Ghamza changes from
`(24,132)-(378,400)` to `(24,428)-(378,696)` after returning while recovery loads.
The 296-point displacement matches one catalog card. The initial fixture lookup
error is retained separately and is not used as reproduction evidence.

[Compose 1.10.3 LazyListState source](https://github.com/JetBrains/compose-multiplatform-core/blob/v1.10.3/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/LazyListState.kt)
shows that its Saver retains index/offset, not the item key. Nav3 disposes Home's
live state; App inserts a transient recovery-loading item before games on return.
Restoring an index against that temporary list selects the preceding card, then
live key anchoring preserves the wrong item when loading disappears.

The fix hoists `rememberLazyListState()` into App above the navigation renderer
and passes it to Home's LazyColumn. Its live key anchor survives entry disposal;
loading, recovery, filters, layout, labels, gestures and game behavior are not
removed or changed. Two Desktop regressions verify exact unchanged bounds in
English and Arabic at 200% text, twice per journey, and one selection per return.
Focused execution passes both methods; a source contract guards the actual
App-to-Home wiring. Final strict/full hosted qualification remains required.

## Safe diagnostics and acceptance boundary

The public XCTest sampler now catches snapshot errors and retains only their
numeric NSError code in the existing bounded failure ledger. It never retains
error descriptions, domains, userInfo, arbitrary hierarchy or player data.
The same one snapshot/sample, exact adjacency, reset rules, native hittability,
ten-second deadline and one ordinary tap remain enforced. No selector, swipe,
retry, simulator version or verification gate is relaxed.

This fixes a reproduced shared scroll bug, **not a proven XCTest nil or original
Host-input cause**. Track #273 separately from #267, #270 and broad native UX #259.
Keep failed artifacts and observer failures. Fresh current-source checks and
protected/post-merge acceptance are necessary before closure. Cold process
recreation (an index-only Saver), real-device gestures/assistive technology,
LAN/background/power/thermal, strict protection, signing and Store readiness
remain separately unverified or blocked; this work claims none of them.
