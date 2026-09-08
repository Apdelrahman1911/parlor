# WD-C2 save-preservation wording — independent proposed qualifier

Reviewer: `/root/release_fix_review`; 2026-09-06. **Proposal only: no application/documentation source changed.** Existing frozen verification remains intact.

## Classification and basis

The content-incompatibility correction preserves supported pass-and-play saves. It introduces no deletion: `WhodunitGameFlow.kt:476–492` rejects missing/mismatched identities, and `:290–304` deletes those records only from the explicit Discard action. The earlier retired-Solo cleanup is separate: `:436–442` deletes an envelope explicitly tagged `playMode=Solo` before payload decoding. That block is unchanged from HEAD `3625d0663ba6eb51338cbd5f9dc45f859ec18846`; existing `WhodunitResumeReconstructionTest.kt:333–387` requires deletion and cancellation propagation. The block predates this campaign (`0631951d`/`0e1baa40`, 2026-08-09).

This is an over-broad new documentation claim, not evidence of a new WD-C2 deletion regression. Do not silently change the old retirement policy or claim universal historic-save retention. The owner’s pre-release content instruction is being applied to supported pass-and-play content recovery; whether to replace the older Solo retirement policy would need a specific decision if universal preservation is intended.

## Minimal text proposals

### `docs/PRE_RELEASE_COMPATIBILITY.md` — final content paragraph

Replace the paragraph beginning “Old internal test saves have” with:

> Old internal pass-and-play test saves have **no backward migration requirement**. If content is incompatible or its identity is absent, recovery explains the failure and allows an explicit discard/restart; it must not rewrite or automatically delete that save, or pretend old and new content have the same identity. This preservation rule covers supported pass-and-play recovery, including older records without a `playMode` field. It does not change the pre-existing retirement path: snapshots explicitly marked `playMode=Solo` are deleted during loading before private payload decoding. Players in a room must use matching content. Protocol compatibility remains exact 4.2.

### `docs/WHODUNIT_TEST_CONTENT.md` — older-save paragraph

Replace “Older saves without either identity field” with “Older pass-and-play saves without either identity field”. After that paragraph, add:

> This content-preservation behavior does not cover the retired `Solo` mode. Its unchanged loader deletes snapshots explicitly tagged `playMode=Solo` before payload decoding; it does not migrate them to pass-and-play.

### `docs/PRODUCTION_ARCHITECTURE.md` — content-preservation sentence

Replace “Incompatible pre-release saves stay available” with “Incompatible pre-release pass-and-play saves stay available”. Before “LAN start likewise”, add:

> The separate retired-Solo loader still deletes explicitly `playMode=Solo` records before payload decoding; this content policy does not change that earlier behavior.

## Verification after any authorized application

Root should preserve the current freeze/receipts, record a new source identity for the documentation-only delta, review the exact diff, and rerun applicable documentation-bound verification (including `:shared:transport-p2p:desktopTest`). The application byte identity remains unchanged only if demonstrated by the before/after manifests. A new document hash is not grounds to relabel an earlier failed aggregate PASS.

No build/test/process/Store/Git operation was performed by this reviewer. Only this proposal was written.
