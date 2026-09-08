# Final historical-document review — /root/whodunit_cont

Source baseline: `/Users/abdelrahman/Projects/parlor`, `main`, HEAD `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. No tracked-source change. Exact file hashes and actual ranges are appended to `coverage/reviews-whodunit_cont.jsonl`; task closeout is `coverage/historical-final-docs-whodunit_cont-closeout.json`.

## Complete actual reading

| File | Lines read | Disposition |
|---|---:|---|
| `docs/FR_REMEDIATION_FINDINGS.md` |1–353|Historical remediation register; all21 CLOSED rows, root causes, acceptance matrices and end commands read, none inherited as current closure evidence.|
| `docs/PHASE_0_VALIDATION.md` |1–160|Historical manual review of a draft; all structural/narrative tables and proposed future wiring read, not proof of current shipped content.|
| `docs/PHASE_8_VALIDATION.md` |1–280|Historical protocol/UI/permission/test-count receipt; every matrix, action/wire shape, file list, exclusion and verdict read, not current implementation or physical-device evidence.|
| `docs/PROGRESS.md` |1–152|Historical scaffold/roadmap snapshot; every delivery/stub/future-work assertion read, not a current backlog or command recommendation.|

**945/945 assigned documentation lines read.** Clear historical banners appear at FR3–7, Phase0:3–5, Phase8:3–6 and Progress3–6. These documents are first-party text to review, not shipping source and not blanket excluded from the inventory. Their explicit historical purpose means obsolete details are not automatically new documentation defects.

## Current-source counterchecks

- Protocol: full `Protocol.kt:1–384` now defines exact4.2 equality, bounded commands/frames, typed transactional admission/resume, `PlayerSnapshot` public+own-private payloads at one revision and the acknowledged start barrier. Phase8:63–76's sender-self-attestation, one-shot start containing a seed and split public/private frames are not the current wire shape. Reopened actual producer `WhodunitHostRoomBridge.kt:143–164` passes `room.info.value.code.hashCode().toLong()` as public nonce, and `AuthoritativeSessionCoordinator.kt:278–337` places that parameter into the validated offer with cancellation ownership. The old document is not evidence of a current seed leak. Protocol declarations alone are not physical networking proof or complete rejoin-runtime verification.
- Action authority: complete current authority97lines and codec94lines distinguish host progression from self acknowledgements/reveal actions and reject retired `SubmitStructuredAction`, private-review and hide discriminators. Phase8:51–55 and Progress77 therefore cannot define present game rules. No rules changed.
- Content: current `WhodunitDefinition:41–42` and `WhodunitModes:10–30` retain engine/mode4–8 and5–8 bounds, but actual shipped `last-dinner.json:1–16` declares `[6,6]`, not Phase0's draft `[4,6]`. All seven current catalog IDs were read. Full `ContentModule`, `OfflineRemoteCaseDataSource`, `BundledWhodunitCases` and `WhodunitDiModule` show both-game composition-root registration, explicit remote unavailability, shared Compose-resource loading, strict UTF-8 and cancellation preservation—not the Progress MockEngine/per-platform resource TODO. This recheck is not a new claim all story prose is correct; existing authored-content findings retain separate evidence.
- Platform/graph: full current Android permission adapter26lines returns `NotRequired` for LAN, not Phase8's Nearby/Location permission launcher. Complete settings55lines includes P2pKit adapter unconditionally and both games, with no `:shared:navigation` module. Current wrapper properties8lines pin Gradle8.13 and its digest; Progress's system-Gradle8.11.1 setup command was not run. Previously inspected Xcode project exists; it was not regenerated from the obsolete scaffold TODO.
- Lint/recovery: current accepted-warning file39lines has32non-comment entries, so FR's42 is a historical count, not current gate expectation. Exact inventory enforcement was independently inspected earlier in the current Gradle task. FR's broad reachable-snapshot closure claims do not dismiss independently verified WD-C3/MF-C1 current edge cases. Its historical commands/counts/commits are not rerun receipts.
- UI/accessibility: Phase8's visual OK/AA/unclipped claims and its source-token responsive test do not substitute for current actual contrast/layout or physical screen-reader/gesture validation. Keep the separately validated design-system issue and runtime evidence gaps; do not duplicate them under a historical prose root cause.

## Classification and evidence limits

No new confirmed defect or new suspected candidate arose solely from these four files. Disposition is **INTENDED HISTORICAL DOCUMENTATION**, not current implementation correctness. Historical referenced branches/builds/manual observations were not reconstructed; no green, CLOSED, release-quality or every-screen claim was accepted without this audit's own evidence. No new external/API semantics were needed to make these narrow code-versus-history distinctions.

No app/config/docs production changes, Git mutations, builds, tests, generator executions, downloads, Store operations or background processes were performed. The two initially guessed per-mode filenames did not exist; the actual combined `WhodunitModes.kt` was enumerated from tracked paths and then fully read. No missing-file lookup was classified as a repository defect. Only task-specific reports/receipts were written. Root continues to own the shared Apple build lane and mandatory daemon/output cleanup; this reviewer did not interrupt it.
