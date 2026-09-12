# IOS-R1 app-host cycle01: independent signing-path adjudication

Reviewer: `/root/session_cont`; original suspicion/runner author: `/root`. Date: 2026-09-05.
Scope: read-only review; no builds, signing commands, devices, credentials, or production edits by this reviewer.
Baseline: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`.

## Conclusion

**Confirmed audit-harness environment defect; not a Parlor application defect.**
`iosr1-apphost-01` failed before XCTest/application execution. It supplies no Home recovery API observation and no original or subsequent Keychain status. IOS-R1 remains **UNCONFIRMED — BLOCKED as an application defect** pending valid app-host evidence.

## Exact pinned implementation

The repository pins Kotlin `2.4.10` (`gradle/libs.versions.toml:8`). The cached `kotlin-gradle-plugin-2.4.10-gradle813-sources.jar` was inspected without executing Gradle. All three implementation files were read completely and byte-matched against official JetBrains source at tag `v2.4.10`, commit `5687445832cd835b4509b9fbc264cdf1a8201093`:

- `SetupEmbedAndSignAppleFrameworkTaskSideEffect.kt:14–19`: every Apple framework registers the task with `XcodeEnvironment(target.project)`.
- `XcodeEnvironment.kt:59–60,83–84`: `sign` is the String extra-property override or `System.getenv("EXPANDED_CODE_SIGN_IDENTITY")`; an empty String remains non-null.
- `AppleXcodeTasks.kt:244–342`: registers the framework assemble/embed tasks. Parlor's `composeApp/build.gradle.kts:114–118` selects a dynamic framework (`isStatic=false`), so the embed/sign path is enabled.
- `AppleXcodeTasks.kt:369–420`: captures `environment.sign`; `:395` and `:406` check only `!= null`. `:411–412` invokes `codesign --force --sign <envSign> -- <binary>` even for `envSign=""`.
- No `CODE_SIGNING_ALLOWED` or `CODE_SIGNING_REQUIRED` guard exists in this path. A narrow source-archive search found those signing settings in unrelated CocoaPods generation, not the direct-integration path. No repository `kotlin.envOverride` signing override was found.

Official URLs, access timestamps, cached/public SHA-256 comparisons, and full sources are retained in `research/iosr1-codesign-session_cont/`. The tag resolution is retained in `research/iosr1-binding-session_cont/kotlin-tag.json`.

## Reachable cycle01 failure

1. Executed runner `evidence/iosr1-apphost-01/runner-executed.py:347–353` builds a whitelisted child environment but explicitly assigns `EXPANDED_CODE_SIGN_IDENTITY=''`.
2. Its hash `6922206db73a2b5e9d01f6fab5d5b63a2983f44a0bf54d5beb2e2d06ee3f7262` matches the per-run `input-manifest.json`.
3. The copied shell phase invokes the real `:composeApp:embedAndSignAppleFrameworkForXcode` task with strict dependency verification. The pinned source carries the inherited empty identity into the codesign command.
4. `evidence/iosr1-apphost-01/xcodebuild.log:1088–1119` reports this task failing with `: no identity found`, `codesign` exit1, and `BUILD FAILED`; `:1126–1136` explicitly cancels testing because compilation/build failed.
5. Raw `xcresult-summary.json` has total/passed/failed/skipped counts all0; `xcresult-tests.json` has no test nodes. `embedded-gradle-stop.txt` records `build_exit=1`, `stop_exit=0`.
6. The cycle receipt records standalone framework exit0, Xcode exit65, runtime `NOT_RUN`. Its generic final error about not having one successful XCTest does not supersede the actual upstream build failure.

## Counter-evidence and safe audit-only adjustment

The prior `run_xcode_ui_cycle.py:39–52` uses a whitelist without this environment entry. Its `:103–110` passes the same Xcode command-line `CODE_SIGNING_ALLOWED=NO`, `CODE_SIGNING_REQUIRED=NO`, blank `CODE_SIGN_IDENTITY`, and blank `DEVELOPMENT_TEAM`. Actual `xcode-ui-01/xcodebuild.log:1085–1091` shows embed success; `:1513–1554` shows one executed passing launch test. The prior runner is preserved source, not a newly executed control experiment or a per-run environment dump.

Recommended next attempt: **omit/remove `EXPANDED_CODE_SIGN_IDENTITY` from the runner child environment**, rather than assigning an empty String. Since the runner starts from a small whitelist, removing the explicit entry does not inherit a user identity. Keep the explicit NO signing flags, blank base identity/team, strict dependency verification, real embedding task, invocation-only fixture guards, fresh owned simulator, and finalization. The fixture guard already accepts an absent value and rejects a nonblank identity. If Xcode unexpectedly supplies a value anyway, inspect the copied phase/environment and fail closed; do not substitute a real or `-` ad-hoc identity, relax production checks, or skip embedding to get green.

This recommendation resolves the deterministic empty/non-null mismatch only. It is not evidence that a retry launches, that storage works, or that signing/Store readiness is established. The actual retry requires a new receipt and independent runtime review.

## Cleanup and preservation

Root's cycle receipt reports immediate/final stop exit0, simulator deletion, no remaining owned processes/outputs, unchanged tracked source, and cleanup `PASS`; full ownership/cleanup validation is assigned to `/root/whodunit_cont`. This reviewer performed source/archive reads and bounded public-source HTTP requests only; all commands completed and produced only compact retained audit evidence. No task daemon/device was started or stopped by this reviewer. AGENTS.md and pre-existing untracked material were preserved.

## Independence disclosure

This reviewer authored the additive IOS-R1 diagnostic fixture, independently safety-reviewed by `/root/mafia_cont`; the failing runner environment was authored and suspected by `/root`. This report independently validates that harness cause only and does not self-approve the fixture or a new application finding.
