# Parlor release runbook

Android and iOS are the production targets. Desktop is a development/test
harness and is not shipped by this runbook.

## 1. Open the release candidate

1. Create the release branch from the approved commit. Record the Parlor commit,
   resolved P2pKit 0.7.0-rc3 coordinates/checksums, and upstream release-tag
   provenance. A sibling P2pKit checkout is not the executed dependency.
2. Confirm the worktree is clean and the diff contains no generated caches,
   credentials, signing assets, local repository paths, or debug-only
   transport/logging switches.
3. Set Android `versionCode`/`versionName` and iOS
   `CURRENT_PROJECT_VERSION`/`MARKETING_VERSION`. Record the protocol major,
   game protocol versions, content versions, and P2pKit coordinate.
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

On macOS with the release Xcode toolchain:

```bash
./gradlew productionAppleCheck --no-daemon --stacktrace --console=plain
```

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

## 5. Sign and stage

### Android

1. Build the final AAB from the already-verified commit in the protected
   signing environment.
2. Supply the upload key only through the secret store; never copy a keystore
   or password into the repository or logs.
3. Confirm the artifact is release/minified as configured, signed by the
   expected upload certificate, and byte-identical in inputs to the unsigned
   candidate other than signing.
4. Upload to Play internal testing. Re-run install, startup, LAN multiplayer,
   background/rejoin, and no-provider-traffic inspection from the delivered build.

### iOS

1. Archive the same commit with the protected distribution certificate,
   provisioning profile, bundle ID, and App Store configuration.
2. Validate the archive and privacy manifest in Xcode/Organizer.
3. Upload to TestFlight. Re-run install, startup, LAN multiplayer,
   background/rejoin, and no-provider-traffic inspection from the delivered build.

Promote through internal/TestFlight cohorts before broader rollout. Store
submission is blocked while any Blocker/Critical/High issue is open without
written risk acceptance.

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
- physical-device, accessibility, privacy, and store receipts;
- accepted risks and residual `UNVERIFIED` checks; and
- rollout/rollback owner and decision timestamps.
