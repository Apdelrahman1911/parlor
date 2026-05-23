# Parlor — Release Runbook

> Operational doc for cutting a release and rolling back a broken case.

## Pre-flight (every release)

1. **Architecture lint** green: `./gradlew :shared:engine:desktopTest` (Konsist purity tests).
2. **All unit tests** green: `./gradlew test`.
3. **Shape test** green: `./gradlew :shared:session:allTests`.
4. **Compose UI tests** green on Android (cover→reveal→hide ceremony, voting flow, reveal stage).
5. **Real-device QA** completed for the device matrix (`docs/MOTION_DOWNGRADE.md` §validation).
6. **Accessibility audit** complete (`docs/ACCESSIBILITY_AUDIT.md`).
7. **Bundled case refreshed** — the in-repo `content/last-dinner.draft.json` matches the latest approved live API version (or, if intentionally lagging, the delta is recorded in the release notes).
8. **Crash-free session rate** ≥ 95th-percentile target over the prior dogfood window.

## Build and ship — Android

1. Bump `versionCode` and `versionName` in `composeApp/build.gradle.kts`.
2. `./gradlew :composeApp:bundleRelease`.
3. Sign with the release keystore (out-of-band; do not commit keystore credentials).
4. Upload `.aab` to Play Console; promote through Internal → Closed → Open → Production per Play's review schedule.

## Build and ship — iOS

1. Bump `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` in the Xcode wrapper project.
2. Build with `./gradlew :composeApp:assembleXCFramework` followed by Xcode archive.
3. Notarize and submit to App Store Connect for TestFlight, then App Store review.

## Build and ship — Desktop

1. Bump `compose.desktop.application.nativeDistributions.packageVersion`.
2. `./gradlew :composeApp:packageDmg` (macOS), `:packageMsi` (Windows), `:packageDeb` (Linux).
3. Sign each installer per platform requirements.
4. Upload to the chosen distribution channel.

## Rolling back a broken case

A case can be broken in two ways: invalid (fails validation for some users) or unbalanced (one killer wins 90% of games, or a clue contains a typo that breaks the mystery).

1. **Identify the broken version.** Look at telemetry for `ValidationError` events tagged with the broken `caseId` + `version`, or for crash reports referencing the case.
2. **Choose the last-known-good version.** Cached cases are keyed on `(caseId, version)`; the most recent approved older version is the rollback target.
3. **Re-publish the older version with a fresh timestamp.** The case-management mock backend (and, post-MVP, the real backend) supports re-publishing an older case as the current version — same content, fresh manifest entry.
4. **Verify cache invalidation.** On next app open, clients fetch the new manifest, see a newer-than-cached `version` for that `caseId`, and refresh.
5. **Confirm** by manually opening the case on a fresh install — should serve the rolled-back content.

**Drill.** Before any production release, rehearse this drill against the mock backend. Time-to-rollback target: under 5 minutes.

## Telemetry to monitor in the first 24 hours

- Crash-free session rate per platform.
- Validation failures by `caseId` × `version`.
- Time-to-completion p50 and p90 (Classic vs Elimination).
- Killer-wins rate per killer variant (target: 30–50% balanced).
- Replay rate after first game.

Filter all telemetry through `:shared:core/logging`'s no-private-leak helper. Private dossier text and host-only fields must never appear in payloads.

## Communication

- File a runbook entry in the team's incident tracker for each rollback, including the `caseId × version` rolled from and to and the reason.
- Notify the content team so they can fix the broken version offline.
