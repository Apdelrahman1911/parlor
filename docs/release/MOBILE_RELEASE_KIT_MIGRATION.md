# Mobile Release Kit migration

Parlor now has a non-publishing shadow configuration for Mobile Release Kit. It lets the shared
tool diagnose the real project contract without replacing or enabling any existing workflow. Both
platforms deliberately remain blocked from candidate creation.

## Current shadow contract

- `config/parlor-version.xcconfig` remains the single source of truth for Android and iOS, using
  `PARLOR_VERSION_NAME` and `PARLOR_BUILD_NUMBER`. CI must consume those committed values and must
  not allocate substitutes.
- Android uses application module `:composeApp`, variant `release`, Store package
  `com.parlor.app`, and isolated Debug package `com.parlor.app.debug`.
- iOS archives shared scheme `iosApp` with configuration `Release`, Store bundle
  `com.parlor.app`, and isolated Debug bundle `com.parlor.app.debug`. Distribution symbols are
  retained.
- Firebase is not part of either mobile release path. The migration does not introduce an
  identifier-bound service or copy a service credential into the shared repository.
- The existing credential-free release unit and workflow-contract tests run as bounded project
  preflight checks. Existing artifact validators remain application-owned because their interfaces
  also require legacy candidate evidence and pinned tool inputs.

The Android build accepts generic `MOBILE_RELEASE_ANDROID_*` signing names after the existing
`PARLOR_ANDROID_*` names and before the existing Gradle-property fallback. Setting
`MOBILE_RELEASE_REQUIRE_SIGNING=true` makes an incomplete shared signing request fail during Gradle
configuration. No signing value is stored in this file or in `release/mobile-release.json`.
The iOS Release target likewise maps four user-defined `MOBILE_RELEASE_IOS_*` values to the standard
signing settings. This keeps shared overrides app-target-only instead of leaking them into dependency
targets; local defaults still come from `Config.xcconfig`.

## Exact blockers before candidate mode

1. The canonical Android package and iOS bundle currently collide with public Store identities that
   have not been established as publisher-owned Parlor records. Keep both `identityStatus` values
   `blocked`; do not treat identifier syntax, a successful local build, or possession of signing
   material as ownership proof.
2. Verify the intended Google Play application and Apple App Store Connect application through
   authenticated read-only Store access. Select and commit the external Play track name plus its
   closed/open kind (the legacy path reads those values from protected environment configuration,
   but they are non-secret release policy). Then record the approved Play upload-certificate
   SHA-256, Apple numeric app and Team IDs, TestFlight external group, and Apple Distribution
   certificate SHA-256 as public policy. Do not guess them or move values out of
   `release/private/`.
3. Populate reviewed public metadata and approved fictional screenshots under `release/store/`.
   `en-US` is only the initial required locale; the owner must explicitly approve any additional
   Store locale policy. Privacy, content, export, pricing, availability, tester, review-contact, and
   final visual decisions remain owner/Store responsibilities.
4. At the migration audit, the locally fetched `origin/testing` and `origin/release` refs still
   pointed to `bc6043c`, while the later Store-identity collision guard existed only on `main`.
   Re-fetch and inspect the exact protected branch trees before enabling any legacy or shared
   candidate. A passing contract test on the `main` worktree is not proof about those branch copies.
5. Publish a reviewed Mobile Release Kit release, pin its full immutable commit SHA in thin caller
   workflows, and add only the environment-scoped credentials reported by its inventory. No caller
   is added while that public immutable revision is unavailable.
6. Prove one no-publication rehearsal, then one internal candidate and exact-receipt promotion cycle
   per enabled platform. Compare identities, versions, final signer fingerprints, permissions or
   entitlements, symbols, hashes, and Store readback with Parlor's existing evidence before retiring
   anything.

## Existing path preservation

The workflows under `.github/workflows/`, scripts under `scripts/release/`, release policy, ignored
private handoff, branch protections, environments, and credential names remain unchanged and
authoritative. Do not re-enable a stale branch workflow merely because this shadow configuration
exists.

When a pinned shared release is available, add its callers beside the legacy path with Store
mutation disabled first. Remove superseded release logic only in a later reviewed change after the
replacement has produced accepted signed-artifact evidence and exact Store receipts. The shared
system never performs the final public rollout; that remains an explicit Store-console decision.
