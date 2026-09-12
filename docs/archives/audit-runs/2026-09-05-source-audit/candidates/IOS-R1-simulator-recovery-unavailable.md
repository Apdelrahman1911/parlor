# IOS-R1 — Fresh unsigned simulator Home displays recovery-unavailable banner

Administrative pending mirror requested by `/root`; created by `/root/whodunit_cont`. **UNCONFIRMED — independent source investigation pending.** Finder `/root`; assigned independent validator `/root/session_cont`. No defect severity or root cause is approved. Keep separate from the 29 adjudicated IDs / 26 positive adjudications.

## Observation and identity

- Repository `/Users/abdelrahman/Projects/parlor`, `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; tracked source unchanged.
- Root-owned `evidence/xcode-ui-01/receipt.json` records an unsigned Debug build, temporary iPhone17Pro simulator on iOS26.5/Xcode26.5, application ID `com.parlor.app.debug`, and separate English/Arabic `simctl launch` plus screenshots. No real player data, private signing input or Store operation used.
- Both retained screenshots (`evidence/xcode-ui-01/home-en.png`, `home-ar.png`) visibly show the localized recovery-unavailable card. English text says “Some recovery information is unavailable” and that not every saved game or resumable session could safely be read. The register reviewer viewed both images; this confirms the presentation, not its cause.
- The ordinary XCTest was one English-only launch/foreground/home-brand/no-alert assertion (`iosApp/iosAppUITests/IOSAppLaunchUITests.swift:9–30`). `xcresult-summary.json` reports one passing case. It does not assert storage availability, and the two supplemental locale launches are not additional XCTest cases.

## Pending independent questions

Trace actual Home recovery loading, saved-game and resumable-credential stores, native protection/key access, and unsigned-simulator configuration. Determine whether the banner is a code defect, appropriate failure handling under an unsigned environment, a test limitation, or another independently reproducible condition. Do not infer Keychain failure, data loss, launch crash, or shipping-device impact solely from a screenshot.

Exact causal source locations, expected behavior under these unsigned prerequisites, a reproducer/proof, counter-evidence and final separate classification remain pending. No code fix is recommended before that work is complete. `/root` is reopening source and `/root/session_cont` is independently validating; this administrative mirror does not substitute for their review.

## Evidence and cleanup limits

Receipt: `evidence/xcode-ui-01/receipt.json`; test evidence: `xcresult-summary.json`, `xcresult-tests.json`; observations: `home-en.png`, `home-ar.png`. The receipt records immediate and final Gradle stop exit0, simulator shutdown/delete exit0, task-owned build/DerivedData removal, no remaining outputs and no cleanup errors. No additional build, test or process operation was performed to create this mirror. Simulator success and screenshot visibility do not constitute physical-device or signed-release verification.

## Final independent validation — 2026-09-05

The initial pending-investigation wording above is historical. `/root/session_cont` has now independently reopened the Home/DI/native-storage/credential paths, inspected both original screenshots, reviewed Apple API contracts, and checked the complete native probe source and both root-owned execution receipts.

**Current classification: UNCONFIRMED — BLOCKED (application attribution).** Keep outside both confirmed-code and positive evidence-gap counts. Annotation: the unsigned Home observation remains a test/environment evidence gap; it is not an independently proved application defect or a claimed fix.

`evidence/iosr1-native-02/probe-result.json` provides a real numerical counter-test: the separate fresh unsigned UIKit probe's equivalent Keychain query returns **-34018 / errSecMissingEntitlement**, while empty-folder lookup/creation/protection/backup exclusion/listing all succeed. Native01's deliberately unstamped binary did not launch and yielded no API result; that harness failure is not a Parlor crash. Neither probe captures the original KMP app's per-source OSStatus/Foundation error. Empty snapshots and `errSecItemNotFound` are explicitly handled in production. No production change, missing-entitlement “fix,” banner suppression or shipping-storage conclusion is justified.

Full independent reasoning, counter-evidence, exact source ranges/hashes and remaining source-aware verification: `validations/IOS-R1-session_cont.md`, `validations/IOS-R1-session_cont.sources.json`. Authoritative references: `research/IOS-R1-session_cont/research-ledger.json`. Both native receipts record stop0, owned-device shutdown/delete0, no remaining owned processes/outputs and unchanged tracked source. This validator ran no build/device/signing task.
