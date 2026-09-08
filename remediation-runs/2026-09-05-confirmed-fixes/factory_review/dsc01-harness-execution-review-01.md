# DS-C01 harness — independent execution approval

**Reviewer:** `/root/factory_review` (not the production or harness author).
**Decision:** approved for supervised root-only execution of exact controls `82c4efd11d4c9885423066e6318967321c5dc1a7c3afe550cae42c3ff1cdb375`. This is **not** a runtime PASS. Approval was delivered before `dsc01-apphost-01`; this durable record was finalized afterward. Its actual failed result is reviewed separately.

## Binding and coverage

Repository `/Users/abdelrahman/Projects/parlor`; branch `main`; commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`; tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Dirty source manifest `e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7`; tracked diff `60b555c0dd323a086e959ffbc5aa3fd7c6629ae1d0ad016021572e7ae272e4dd`. All 658 source rows, their list digest, tracked diff, and 18 copied-input hashes/non-symlink checks independently match. No Kotlin or Gradle-init source overlay. Exactly four temporary Swift/Xcode copies change; none are copied back.

The companion JSON records exact hashes and honest line ranges: all seven newly authored executable text files (1,555 lines) read completely; relevant imported helpers and app-path reads separately identified. Bulk source-manifest rows are machine binding evidence, **not** 658 new file-reading claims. Source reads carried forward by the same reviewer were rehashed on finalization.

## Safety and reachability conclusions

- The copied wrapper still creates the original `MainViewController` and original App/Koin/Settings path. Probe preparation runs before App initialization; only the owned Debug sandbox's synthetic language keys are observed. No launch-language masking, OS preference-file edit, real-game data, credentials, or user-device operation.
- Intended matrix requires actual Settings Arabic → kill/relaunch → System restoration, then synthetic prior `ar-EG,en` → actual English → kill/relaunch → exact System restoration. Four different process boot UUIDs and before-App ownership markers distinguish restart from remount. These remain assertions to execute, not achieved results.
- At exact Compose UI1.10.3, `ComposeUIViewController` returns the controller provided to `LocalUIViewController`; the root view observed in Swift is the one production locale code updates. Exact tagged-source research and read ranges are in JSON. Native semantics are not direct Compose layout/gesture evidence.
- Single root build lane, owned PID/start/ancestry/group, whitelisted environment, isolated Gradle home/cache symlinks, and new simulator UUID. External `ibtoold` FIFOs require already-attested live process identities and UID/inode/type/birthtime checks; unknown files/holders/PID reuse/errors fail closed. Only attested FIFO leaves and empty parents can be removed.
- Immediate nested Gradle stop follows embedding; failed build/stop prevents normalization. Outer runner stops after Xcode and finalization, preserves compact receipts, deletes only owned output paths/device/temp tree, and fails for cleanup uncertainty.

## Executed lightweight verification

Root's `dsc01-harness-contracts-01`: **30 passed** (19 synthetic FIFO safety, six synthetic receipt validation, five fake-shell phase tests), exit0; 2026-09-06 00:49:11–00:49:14 UTC. Stop exit0 at00:49:15; no remaining outputs/workers/errors; source unchanged. Receipt `e754b3a6b475f154d27216a100d753b7271f50a6a32926459d561adbe7301c92`. Raw log inspected. This command ran no iOS app or physical network and is not DS-C01 runtime verification. Generic lightweight receipt binds root runner/source; this dossier separately binds full controls.

## Remaining limitations

No active-session retention, direct Compose direction, actual OS Settings, physical device, gesture/accessibility, power-loss, Store/signing, or release-readiness conclusion. Unmarked legacy `AppleLanguages` ownership remains unresolved. Original raw receipts are immutable. The companion runtime addendum must classify the actual XCTest result, even when fixture selection fails before a settings mutation.

Reviewer started no builds/tests/native tools/background processes and changed no application/configuration/Git state. Only these compact evidence files are new; root owns cleanup for its run.
