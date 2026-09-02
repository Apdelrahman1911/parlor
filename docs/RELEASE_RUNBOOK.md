# Parlor release runbook

Android and iOS are the production targets. Desktop is a development/test
harness and is not shipped by this runbook.

The protected branch, build-once, credential, and Store-promotion contract is
canonical in [`RELEASE_AUTOMATION.md`](RELEASE_AUTOMATION.md). This runbook
covers qualification around that automation; it must not be used to build a
second production artifact after testing.

## 1. Open the release candidate

1. Review the candidate on `main`, then move its complete tree through protected
   `testing`. Record the exact full commit and tree SHA, resolved P2pKit
   0.7.0-rc3 coordinates/checksums, and upstream release-tag provenance. A
   sibling P2pKit checkout is not the executed dependency.
2. Confirm the worktree is clean and the diff contains no generated caches,
   credentials, signing assets, local repository paths, or debug-only
   transport/logging switches.
3. Review the one version change in `config/parlor-version.xcconfig`. Android
   `versionCode` and iOS build number use the same positive integer; Android
   `versionName` and iOS marketing version use the same three-part version.
   Workflows never auto-increment it. Record protocol, game/content versions,
   and the P2pKit coordinate.
4. Resolve `io.github.apdelrahman1911:p2p-core:0.7.0-rc3` and
   `io.github.apdelrahman1911:p2p-transport-lan:0.7.0-rc3` from Maven Central.
   Do not use `mavenLocal()`, a sibling checkout, or a repository override.
5. Confirm strict dependency verification succeeds without
   `--write-verification-metadata`. Compare P2pKit against
   `P2PKIT_MAVEN_PROVENANCE.md`; a checksum change is a blocked dependency
   review, not a cache problem to bypass.
6. Freeze bundled EN/AR content and translations. This release has no remote
   case rollout to use as a substitute for an app release.

## 2. Automated verification

On Linux or macOS with JDK 21 and Android SDK 36:

```bash
./gradlew productionCheck --no-daemon --stacktrace --console=plain
# In the protected signing environment, add:
./gradlew productionAndroidSigningCheck --no-daemon --no-configuration-cache --stacktrace --console=plain
```

On macOS with the release Xcode toolchain pinned in
`config/release-policy.json` (currently Xcode `26.3` build `17C529`, with a
physical iOS SDK major of at least `26`; deployment remains iOS `16.0`):

```bash
./gradlew productionAppleCheck --no-daemon --stacktrace --console=plain

xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Release \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/xcode-derived-data \
  ARCHS=arm64 \
  ONLY_ACTIVE_ARCH=YES \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  build
```

The unsigned Swift wrapper is qualified as a deterministic arm64 simulator
artifact. `productionAppleCheck` independently links `iosSimulatorArm64`,
`iosX64`, and device `iosArm64`; linkage is not runtime validation.

Archive:

- Gradle and Xcode versions;
- full command logs;
- XML/HTML test and lint reports;
- dependency graphs and P2pKit provenance;
- unsigned Android AAB checksum; and
- linked iOS framework checksums.

A successful compiler or simulator result does not pass device, signing,
privacy, or store gates. See `docs/RELEASE_GATES.md`.

## 3. Physical multiplayer matrix

Execute every applicable row in [`P2P_MANUAL_TEST.md`](P2P_MANUAL_TEST.md)
against the exact candidate. That runbook is canonical; do not substitute an
older smoke document.

At minimum it requires:

1. Android-to-Android, iOS-to-iOS, and Android-to-iOS in both hosting
   directions, plus a mixed room of at least three devices;
2. normal Wi-Fi and every hotspot topology claimed by the release, including
   hotspot-owner participation and connected-client-to-connected-client host;
3. discovery, multiple rooms, wrong code, authenticated connection, explicit
   host approval, decline, atomic capacity, timeout, and closed admission;
4. both games through completion/rematch, simultaneous actions, and protocol
   fault-injection results;
5. short/long background and screen lock, network switch, transient peer loss,
   process-death Resume, final Leave, host disappearance, and grace expiry;
6. ten repeated room lifecycles plus a sustained session; and
7. iOS Local Network denial/Settings recovery, Android's no-runtime-permission
   path, accessibility/localization, and signed Play/TestFlight artifacts.

Record model, OS version, network topology, build number, result, logs with
sensitive values redacted, and issue links. Anything not run is `UNVERIFIED`.
Raw-IP/manual endpoint connection is `N/A` under ADR-0002, not a passed
fallback. Do not claim universal hotspot support from a single successful run.

## 4. Compliance and observability

Complete `docs/PRIVACY_AND_COMPLIANCE.md` and
`docs/DEPENDENCY_REVIEW.md`.

- Confirm the signed artifact contains no analytics or crash-upload provider
  and emits no provider traffic. Adding either provider requires a separate
  reviewed consent, withdrawal, retention, and disclosure design before it can
  enter a release candidate.
- Inspect captured events/crashes for player names, room/peer/session IDs, IP
  addresses, fingerprints, room codes, tokens, private/host content, exception
  text, and payloads. `ParlorP2p` must retain only its fixed event fields.
- Complete store privacy/data-safety, encryption/export, age-rating, support,
  and content-rights reviews.
- Legal must approve the project license, SBOM, and third-party notices.

## 5. Create and stage the immutable Store candidate

> **Current release stop:** do not execute this section. Every candidate and
> promotion job is repository-disabled with
> `if: ${{ always() && false }}` until the owner-controlled Store identity
> migration and release authorization gates are reviewed. The steps below
> document the future procedure only.

### Android

1. Dispatch `.github/workflows/testing-candidate.yml` on protected `testing`
   with the exact branch-tip SHA. An optional `publish=false` dispatch is a
   disposable, non-promotable rehearsal. Authorize a separate `publish=true`
   dispatch through `testing-android`; only that run creates the candidate
   manifest and the Store-tested artifact.
2. The workflow builds/signs one AAB, validates its exact identity, version,
   certificate, manifest, permissions, SDKs, DEX/native contents, R8/lint, and
   provenance, then uploads that AAB once to Play `internal`.
3. Record the candidate run/attempt, manifest, artifact SHA-256, Play version
   code/edit/track receipt, and Console readback. Re-run install, startup, LAN
   multiplayer, background/rejoin, and provider-traffic inspection from the
   Play-delivered build.
   If a run loses its edit receipt after a successful commit, the automation may
   resume only when Play reports the exact immutable AAB SHA-256 on the single
   completed internal release; the recovery receipt explicitly has no edit ID.

### iOS

1. The same candidate dispatch uses `testing-ios` to create one signed Release
   archive/IPA with an ephemeral keychain and protected profile. It refuses a
   runner whose Xcode build or physical SDK differs from the reviewed policy.
2. It validates the archive and IPA Bundle ID, version/build, Team/certificate,
   profile, entitlements, nested signatures, arm64, privacy manifest, and exact
   bytes before uploading once to App Store Connect.
3. Record the processed App Store Connect build ID and internal TestFlight
   group readback. Re-run install, startup, LAN multiplayer,
   background/rejoin, and provider-traffic inspection from that TestFlight build.

Use `testing-external-promotion.yml` to promote the recorded version/build to
the existing Play external track and external TestFlight group. After testing,
move only the complete candidate tree to `release` and use
`production-promotion.yml`; production promotion never builds or signs. Store
submission is blocked while any Blocker/Critical/High issue is open without
written risk acceptance.

Before a Play promotion, verify the destination track has no staged/draft/
halted rollout or multi-version release. The workflow fails closed for those
states because replacing them automatically could cancel an unrelated rollout.

## 6. Rollout and rollback

Start with the smallest store cohort that provides useful operational evidence.
The current release has no crash/analytics upload provider, so operational
signals are store crash dashboards and user support reports. Do not inspect or
retain gameplay payloads to diagnose a release.

Stop rollout for crashes, privacy/security regressions, incompatible peers,
state corruption, inability to finish either game, or discovery/session leaks.

Rollback means halting store rollout and promoting the last approved signed app
build where the store permits. Because rules and content are bundled, fixing a
broken game/content version requires a new app build. Preserve protocol
compatibility during staged rollout or explicitly reject incompatible peers
with user-readable copy.

## 7. Close the release

Store one release evidence record containing:

- source commits and clean-tree proof;
- version/protocol/content coordinates;
- automated logs and reports;
- dependency/SBOM/license review;
- signed artifact identifiers/checksums;
- canonical candidate manifest, exact Git tree SHA, GitHub artifact IDs and
  attestations, candidate/external/production workflow run IDs and attempts;
- physical-device, accessibility, privacy, and store receipts;
- accepted risks and residual `UNVERIFIED` checks; and
- rollout/rollback owner and decision timestamps.
