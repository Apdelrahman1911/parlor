# Store release automation and private configuration

This document is the operational contract for Parlor mobile releases. Source,
workflow, artifact, and Store evidence must agree; a successful upload is not a
release. Android and iOS are shipping targets. Desktop remains a development
and deterministic-test target and has no publishing workflow.

## Repository facts discovered before implementation

| Item | Repository truth | External confirmation still required |
|---|---|---|
| Android release build identity | `com.parlor.app` | **Blocked:** Google Play publicly assigns this package to an unrelated beauty-salon app. It is not an approved Parlor Store identity. |
| Android Debug identity | `com.parlor.app.debug` | Provisional; derive the final Debug identity from the owner-controlled Store identity during the identity migration. |
| iOS release build identity | `com.parlor.app` | **Blocked:** Apple publicly assigns this Bundle ID to “The Parlor Kuwait,” seller Moiz Bohra. It is not an approved Parlor Store identity. |
| iOS Debug identity | `com.parlor.app.debug` | Provisional; derive the final Debug identity from the owner-controlled Store identity during the identity migration. |
| Version source | `config/parlor-version.xcconfig` | Version code/build number must be unused in both Stores. |
| Current version | marketing `1.0.0`, Android code `1`, iOS build `1` | These initial numeric values must be deliberately approved as unused, or reviewed and bumped, before the first candidate. |
| Android signing | External keystore supported; no key is committed | Play App Signing enrollment and the upload-key fingerprint must be confirmed. |
| Apple signing | Team is intentionally unset; no certificate/profile is committed | Team, certificate, profile, and app record must be supplied privately. |
| Apple Store toolchain | Candidate/CI policy pins Xcode `26.3` build `17C529`, physical iOS SDK major `26` or newer, and deployment target `16.0` | Confirm the hosted `macos-15` image still contains that exact path/build before each candidate; a toolchain change requires review. |
| Existing Store automation | None existed before this release system | Store API access and environments must be configured. |
| Google external track | Not discoverable from the repository | Set the exact existing closed or open track; do not invent one. |
| GitHub protection | The repository is public; branch-scoped environments, artifact attestations, secret scanning, push protection, and an active ruleset are configured. The ruleset requires pull requests, every required production-verification check, resolved conversations, and blocks deletion/force-push. | Select and add a second trusted reviewer, then raise the active ruleset from zero to one approval with last-pusher separation before enabling Store workflows. |

No Firebase, Google Services, APNs, OAuth callback, associated-domain, app
group, URL-scheme, or deep-link production configuration was found. The iOS
signed-artifact validator therefore rejects unreviewed entitlements. Adding any
of those services reopens identity, signing, privacy, and release review.

### Blocking Store-identity collision

On 2026-08-16, repository-independent public Store readback proved that the
provisional `com.parlor.app` identifier is already used by another developer on
both platforms:

- Google Play: `https://play.google.com/store/apps/details?id=com.parlor.app`
- Apple public lookup: `https://itunes.apple.com/lookup?bundleId=com.parlor.app&country=us`

The affected GitHub environment variables were removed immediately. Every
candidate or promotion workflow, and every direct Store API execution command,
now calls `assert-store-identity-approved` before signing or Store access. The
checked-in release policy marks both identities `blocked`; changing only that
flag is rejected while the known-colliding identifier remains configured.
As an independent live control, the candidate, external-testing promotion, and
production-promotion workflows are manually disabled in GitHub; only production
verification remains active. Re-enable those workflows only after the reviewed
identity migration, authenticated Store readback, protected-variable setup,
environment approval support, and branch-rule activation are all complete.

Before any signed candidate can exist, authenticated Store readback must either
locate existing Parlor app records controlled by the owner or the owner must
approve and register new canonical identifiers. The resulting reviewed identity
migration must update Gradle/Xcode configuration, Debug derivations, release
policy, manifest schema, validators, workflow guards, signing/services, and
tests together. Only then may API readback evidence set the policy status to
`verified` and repopulate protected GitHub variables. Never upload under
`com.parlor.app`, and never guess a replacement reverse-DNS identifier.

## Branch and workflow lifecycle

| Branch | Workflow | Trigger | Store credentials | Store mutation |
|---|---|---|---|---|
| `main` | `production-verification.yml` | pull request, push, manual | Never | Never |
| `testing` | `testing-candidate.yml` | manual, exact 40-character branch-tip SHA | Secretless protected `testing-candidate` control gate, then protected `testing-android` / `testing-ios` | Optional; `publish=false` is validation-only, `publish=true` uploads once to Play internal and TestFlight internal |
| `testing` | `testing-external-promotion.yml` | separate manual action selecting an exact candidate run/attempt | Protected external-testing environments | Optional; promotes recorded Store identities without binaries |
| `release` | `production-promotion.yml` | separate manual action selecting exact candidate and external-test receipts | Protected production environments | Optional; promotes/attaches the existing Store builds and never compiles |

A `publish=false` candidate dispatch is a disposable, non-promotable rehearsal:
it may exercise signing and artifact validators but creates no canonical
candidate manifest and makes no Store request. It is not the candidate later
received by testers. The separately approved `publish=true` dispatch creates
the actual immutable candidate; within that dispatch each final AAB/IPA is
built and signed once, validated before upload, and reused thereafter. Never
describe artifacts from two dispatches as the same candidate merely because
their source SHA and version are equal.

The workflows must exist on GitHub's default branch before GitHub exposes their
manual dispatch controls. In the Actions UI, select `testing` for candidate and
external-testing dispatches and `release` for production dispatches.

Production accepts either the exact candidate commit or a commit in the same
repository history whose complete Git tree SHA equals the candidate tree SHA.
This supports protected merge/rebase metadata without relying on ancestry
alone. A divergent tree, partial diff, version match, branch name, commit
message, or unrelated same-tree history is rejected.

Android and iOS mutations use separate, shared cross-workflow concurrency
groups. A running Store mutation is never cancelled by a newer run. Pending
runs have not received a Store mutation and may be superseded by GitHub's
concurrency scheduler. Android and iOS can proceed or resume independently.

## Build-once and rerun contract

Candidate creation produces exactly one durable signed AAB and one durable
signed IPA for a candidate:

1. Reserve the reviewed shared version code/build number with a protected
   candidate-claim artifact before either platform build starts.
2. Query each Store and reject an already-used version/build before compiling
   or signing a new candidate.
3. Build, sign, and deeply validate the binary.
4. Upload the immutable binary artifact and create GitHub artifact provenance.
5. Persist its descriptor before any Store mutation.
6. Persist a byte-bound mutation-intent marker before an upload can begin.
7. Upload to the internal Store destination when explicitly authorized.
8. Read the Store state back and persist a platform receipt.
9. Seal both platform receipts into the canonical candidate manifest.

The Apple receipt is not sealed merely because processing returned `VALID`.
The API readback must also say the build is non-expired,
`APP_STORE_ELIGIBLE` (not `INTERNAL_ONLY`), has the expected minimum OS, and
contains an explicit Boolean export-compliance result. External and production
promotion re-read those attributes so an expired or no-longer-eligible build
cannot advance on stale evidence. Parlor does not choose the export answer:
the owner/export reviewer must classify the actual cryptography and configure
Apple accordingly before the candidate can become promotable.

Android validation also requires the signed AAB's dependency metadata, R8
metadata, and obfuscation map, and retains a SHA-256-bound
`releaseRuntimeClasspath` report with the deep-validation record. This is
evidence for the exact signed candidate job; it does not replace strict Gradle
dependency verification or the repository's provenance checks. Each external
or production promotion re-reads Play's app-bundle inventory and refuses to
mutate a track unless the selected version code still resolves to the exact
candidate AAB SHA-256.

The candidate concurrency group serializes every candidate-creation dispatch,
not only identical SHAs. That makes the claim lookup and durable artifact
creation one repository-wide transaction: two different commits carrying the
same reviewed Store build number cannot both pass the empty-claim check. The
claim also prevents a later protected workflow run from signing a second
artifact for that build number; only a rerun of the original GitHub run is
allowed to recover its checkpoints. An abandoned `publish=true` claim consumes
that version/build number. Review and commit a new number rather than deleting
the claim or trying a second dispatch.

On a rerun, the workflow searches the same workflow run for the latest trusted
platform state. If only the artifact descriptor exists, it downloads the exact
recorded artifact ID, verifies the GitHub archive digest, extracted byte size
and SHA-256, and verifies the GitHub attestation before continuing. For Apple,
an accepted `altool` upload transport receipt is checkpointed before waiting
for App Store processing, so a processing timeout does not upload another IPA.

If a binary exists without a trusted descriptor, the workflow refuses to
rebuild. If Play committed the internal release before GitHub retained the
receipt, recovery is allowed only for the same workflow run after its exact
candidate/bytes mutation-intent checkpoint was retained, and only when the API
returns exactly one bundle for the version code, its Store-reported SHA-256
equals the immutable AAB SHA-256, and that version appears exactly once on the
completed internal track. A new candidate run may never adopt an existing Play
version, even if its bytes happen to match. The receipt records
`store_sha256_readback` and no invented edit ID. A digest, track, or
multiplicity mismatch fails closed. App Store Connect does not expose
an equivalent IPA byte digest; Apple recovery therefore requires the previously
checkpointed accepted upload-request receipt. If that evidence is unavailable,
use a new reviewed build number/candidate rather than guessing. No mutating HTTP
request is blindly retried. Safe GET/readback requests use a finite retry
schedule only.

The mutation-intent marker closes the Store-visibility race: once it is found
on a rerun, Google may perform exact digest readback but cannot call bundle
upload again. Apple may continue only from its exact accepted-upload request;
without that receipt it stops before loading the API key or sending the IPA.
This can conservatively abandon a candidate when GitHub saved the intent but
the Store call never started. That is intentional: create a newly reviewed
build number rather than risk uploading one Store build twice.

Candidate manifests, external-promotion receipts, the canonical successful
external-evidence record, and executed production receipts are attested.
Validation-only receipts are deliberately
not represented as Store provenance because they record no Store mutation.
Production verifies the candidate attestation against the candidate dispatch
SHA. It obtains the selected external workflow run through the GitHub API,
requires the exact successful attempt, protected `testing` branch, workflow
path, repository name/immutable repository ID, and shared repository history,
then verifies one canonical external-evidence attestation against that external
dispatch's own source SHA. That record binds the candidate-manifest digest and
valid Android/iOS receipts. Its finalizer can safely combine an Android receipt
from an earlier successful job attempt with a later approved Apple receipt,
which makes `both` resilient to Beta App Review and rerun timing. This remains
correct when `testing` advances after candidate creation.

The unavoidable hard-failure boundary is the interval between an external
Store accepting a request and GitHub durably saving its receipt. Neither Store
offers a cross-system transaction with GitHub. Google can be recovered only by
the exact Store bundle digest rule above. Apple and any other state without an
exact trusted binding are refused rather than claimed as the candidate.

Production App Store submission also resumes transactionally: if Apple created
an empty `READY_FOR_REVIEW` submission before a network failure, the next
authorized run reuses exactly that one empty draft. It refuses multiple empty
drafts or any unrelated active submission instead of creating a duplicate or
silently combining releases.

Candidate control and binary artifacts are retained for 90 days. Expired
artifacts still count as evidence that the candidate was built, so the rerun
guard refuses to rebuild them. If the candidate manifest or its recovery
evidence expires before promotion, review a new build number and create a new
candidate. Do not reconstruct a manifest from memory or version numbers.

## Canonical candidate manifest

`config/candidate-manifest.schema.json` documents the public format;
`scripts/release/release_tool.py` enforces it fail-closed. It includes:

- schema version, repository full name and immutable GitHub repository ID;
- full candidate commit SHA and complete Git tree SHA;
- marketing version, Android version code, and iOS build number;
- candidate workflow run ID, attempt, and UTC creation time;
- canonical Android Application ID and iOS Bundle ID;
- each artifact filename, byte size, SHA-256, and signing-certificate SHA-256;
- exact GitHub artifact ID/name/archive digest and artifact-attestation ID/URL;
- Google Play internal-track version/release plus either committed-edit evidence
  or exact Store bundle-SHA-256 recovery evidence; and
- App Store Connect processed build ID, upload request, and internal-group readback.

Receipts use strict allowlists so credentials or arbitrary private fields
cannot enter the manifest. The manifest contains no keystore, certificate
private key, provisioning profile, service-account JSON, `.p8` key, tester
email, gameplay payload, room secret, or private signed binary.

## Workflow operator sequence

1. Review and commit a version bump in `config/parlor-version.xcconfig`. Android
   version code and iOS build number intentionally share the same positive
   integer. Never let a workflow choose a number automatically.
2. Land the complete reviewed tree on protected `testing` and wait for every
   normal production-verification job.
3. Optionally dispatch **Create immutable Store candidate** on `testing` with
   the exact branch-tip SHA and `publish=false`. This is only a non-promotable
   signing/validation rehearsal and cannot call a Store API.
4. After reviewing rehearsal evidence and protected-environment approvals,
   dispatch the workflow with `publish=true`. This separate dispatch builds
   and signs the one actual Store candidate, validates those exact bytes, then
   uploads them once. Its first durable action reserves the version/build for
   that run; do not start a second publish dispatch for the same number. Record
   this run ID and successful run attempt; do not use artifacts or IDs from the
   rehearsal run.
   The Apple job must report Xcode `26.3` build `17C529`, a physical iOS SDK
   major of at least `26`, and deployment target `16.0`; any mismatch fails
   before archive creation or upload.
5. Verify Play says the version is on `internal`, App Store Connect says the
   build is processed, and the intended internal TestFlight group has it.
   Upload success alone is insufficient.
6. Run the signed internal-build device matrix.
7. Dispatch **Promote immutable candidate to external testing** on `testing`,
   selecting the exact candidate run/attempt and `android`, `ios`, or `both`.
   Rehearse with `publish=false`, then explicitly authorize `publish=true`.
8. For Apple, the workflow retains an attested pending/rejected receipt but
   remains failed until Beta App Review readback is `APPROVED` and the receipt
   records `available_to_external_testers`; rerun that exact action after the
   review state changes. The successful attempt seals one attested external
   evidence record, even when platform receipts originated in different rerun
   attempts. For Google, confirm actual
   tester availability in Console/device evidence; an API track commit is not
   tester-install evidence.
9. Complete external testing. Move the complete approved candidate tree to
   protected `release`; a merge commit is allowed only if its complete tree is
   byte-for-byte the candidate tree.
10. Dispatch **Promote tested Store candidate to production** on `release`.
    Supply the exact candidate run/attempt, exact external-promotion
    run/attempt, explicit platform, and—for iOS—the exact App Store Connect
    version resource ID. Use `attach` first when review metadata is incomplete;
    use `submit` only when submission is authorized. Rehearse with
    `publish=false` before the protected production approval.

The production workflow contains no Gradle, Xcode, signing, AAB, IPA, archive,
or compilation command. Google receives the recorded version code already in
the testing track. Apple receives the recorded App Store Connect build ID
already used by TestFlight.

## Required GitHub configuration

Repository settings are external state and are not established by this commit.
The live repository configuration was inspected and hardened on 2026-08-16:

- workflow tokens default to read-only and cannot approve pull requests;
- only GitHub-owned Actions are permitted and every Action is required by
  repository policy to use a full-length commit SHA;
- dependency vulnerability alerts and automated security updates are enabled;
- secret scanning and push protection are enabled and were read back through
  the repository API;
- `testing-candidate`, `testing-android`, `testing-ios`,
  `external-testing-android`, and `external-testing-ios` accept deployments
  from `testing` only;
- `production-android` and `production-ios` accept deployments from `release`
  only; and
- active safety ruleset `20910226` requires pull requests, resolved
  conversations, every required GitHub-Actions-bound production-verification check, and
  deletion/force-push protection without a bypass. Its approval count is
  temporarily zero because the repository has no second trusted collaborator;
  before any candidate dispatch, add that reviewer, require one approval plus
  last-pusher separation, and read the final ruleset back through the API.

The repository is public, so GitHub makes required environment reviewers and
artifact attestations available on the current plan. It currently has only one
collaborator, however, and the person who triggers a Store deployment must not
approve their own deployment. No Store workflow may be treated as fully
authorized until a second trusted reviewer with repository read access is
selected, configured on every Store environment, and independently verified.
See GitHub's current
[environment-reviewer policy](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)
and
[artifact-attestation availability](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations).

The repository and its Actions artifacts are part of a public project. Treat
signed candidate binaries as publicly retrievable release inputs, never as
secret material. If that exposure becomes unacceptable, migrate the repository
or immutable binaries only through a separately reviewed design that preserves
approval and attestation guarantees. A visibility change invalidates this
GitHub capability review. The checked-in 90-day retention is the
public-repository maximum; if qualification cannot finish in that window,
create and retest a new candidate or approve a durable evidence design before
the old evidence expires.

### Protected branches

Configure `main`, `testing`, and `release` independently:

- require pull requests and at least the organization's approved reviewer count;
- require the two **Production verification** jobs and require branches to be up to date;
- require conversation resolution and disallow force pushes and deletion;
- restrict direct pushes to the approved release maintainers/bot;
- do not permit bypass for Store promotion operators unless explicitly governed;
- enable signed commits if this is an organization policy; and
- ensure fork pull requests never receive environment or Store secrets.

Create `testing` and `release` from reviewed repository history if they do not
exist. Do not create them from copied files or an unrelated repository.

### Protected environments

| Environment | Allowed branch | Required approval | Secrets/variables |
|---|---|---|---|
| `testing-candidate` | `testing` only | Candidate owner/release reviewer | None. This approval guards the global version/build claim and exact-source preflight; do not place Store or signing secrets here. |
| `testing-android` | `testing` only | Testing release reviewer | Android signing plus internal Play access |
| `testing-ios` | `testing` only | Testing release reviewer | Apple signing plus internal TestFlight access |
| `external-testing-android` | `testing` only | External-testing reviewer | Existing external Play track access |
| `external-testing-ios` | `testing` only | External-testing reviewer | External TestFlight/Beta Review access |
| `production-android` | `release` only | Production approver, no self-approval | Play production access only |
| `production-ios` | `release` only | Production approver, no self-approval | App Store attachment/review-submission access only |

Disable administrator bypass where the plan supports it. Reviewers must inspect
the exact SHA/tree and workflow inputs, not only the branch name or version.

## Private credential and variable handoff

Use separate least-privilege Store principals per environment when the Store
supports it. The same secret name may contain a different principal in each
environment. Do not use repository-wide Store secrets.

| Name | GitHub location | Consumed by | Content/format | Sensitive | Obtain and verify | Rotation |
|---|---|---|---|---|---|---|
| `PARLOR_ANDROID_KEYSTORE_B64` | secret, `testing-android` | candidate Android signing | Base64 of the existing JKS/PKCS12 upload keystore | Yes | Obtain from the current Play upload-key custodian; decode to a temp file and run `keytool -list` without printing passwords | Rotate only through Play's upload-key reset process; a changed key creates a new candidate |
| `PARLOR_ANDROID_KEYSTORE_PASSWORD` | secret, `testing-android` | Gradle signing | Exact keystore password | Yes | Verify by opening the keystore with `keytool` | Rotate with the protected keystore copy |
| `PARLOR_ANDROID_KEY_ALIAS` | secret, `testing-android` | Gradle signing | Existing key alias | Yes | `keytool -list -keystore <file>`; avoid logging unrelated aliases | Rotate only with the key |
| `PARLOR_ANDROID_KEY_PASSWORD` | secret, `testing-android` | Gradle signing | Exact private-key password | Yes | Verify through a protected local signing rehearsal | Rotate with the key |
| `PARLOR_ANDROID_UPLOAD_CERT_SHA256` | variable, `testing-android` | AAB validator | 64 lowercase hex characters, no colons | No | Compare `keytool` output with Play Console's registered upload certificate | Reapprove whenever Play resets the upload key |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_B64` | environment secret in each Android Store environment | Store API client | Base64 of one Google service-account JSON file | Yes | Create/link in the Google Cloud project used by Play Console; enable Android Publisher API; validate its JSON shape privately with `jq -e`, then prove app-level least privilege only in an explicitly authorized protected Store run (validation-only mode never loads it) | Rotate JSON keys regularly and immediately on personnel/security changes |
| `GOOGLE_PLAY_PACKAGE_NAME` | environment variable in each Android Store environment | validation and Store API | Owner-controlled package verified by authenticated Play API readback | No | Must equal the reviewed release policy and an app record administered by the supplied principal; currently deliberately unset | Immutable after Play registration |
| `GOOGLE_PLAY_EXTERNAL_TRACK` | variable, `external-testing-android` and `production-android` | Play promotion | Exact existing API track name for the repository's closed or open test | No | Read from Play Console/API; do not guess `alpha`, `beta`, or a display label | Review if track configuration changes |
| `GOOGLE_PLAY_EXTERNAL_TRACK_TYPE` | variable, `external-testing-android` | external-track guard | `closed` or `open` | No | Match the existing track selected above | Review with track changes |
| `PARLOR_APPLE_DISTRIBUTION_CERTIFICATE_P12_B64` | secret, `testing-ios` | archive/sign/export | Base64 PKCS#12 containing the Apple Distribution certificate and private key | Yes | Export from Keychain/managed signing custody; validator binds its leaf SHA-256 to the profile | Replace before expiry/revocation; new signing inputs require a new candidate |
| `PARLOR_APPLE_DISTRIBUTION_CERTIFICATE_PASSWORD` | secret, `testing-ios` | ephemeral keychain import | PKCS#12 password | Yes | Verify with `openssl pkcs12` in a protected terminal | Rotate with the PKCS#12 |
| `PARLOR_APPLE_APP_STORE_PROFILE_B64` | secret, `testing-ios` | archive/export | Base64 App Store provisioning profile for the verified owner-controlled Bundle ID | Yes | Download from Apple Developer; validator rejects development, ad-hoc, enterprise, expired, wrong-team, wrong-ID, or wrong-certificate profiles | Renew before expiry; new profile means a new candidate |
| `PARLOR_APPLE_TEAM_ID` | variable, `testing-ios` | Xcode and validation | 10 uppercase alphanumeric characters | No | Apple Developer membership details; compare signed entitlement/profile | Normally stable |
| `PARLOR_APPLE_BUNDLE_ID` | variable in all Apple Store environments | validation/API | Owner-controlled Bundle ID verified by authenticated Apple API readback | No | Must equal the reviewed release policy, provisioning profile, Apple identifier, and App Store Connect app; currently deliberately unset | Immutable after registration |
| `PARLOR_APPLE_DISTRIBUTION_CERT_SHA256` | variable, `testing-ios` | archive/IPA validator | 64 lowercase hex characters, no colons | No | Derive from the `.p12` leaf certificate and compare with Apple Developer | Reapprove on certificate rotation |
| `APP_STORE_CONNECT_API_KEY_P8_B64` | environment secret in each Apple Store environment | upload/TestFlight/App Store API | Base64 of the `.p8` private key | Yes | Create a least-privilege App Store Connect API key; the file can be downloaded only once | Revoke/replace before personnel or security changes; keys do not expose an expiry date |
| `APP_STORE_CONNECT_KEY_ID` | variable in each Apple Store environment | Apple JWT/upload | 10 uppercase alphanumeric characters | No | App Store Connect Users and Access → Integrations | Update with the `.p8` key |
| `APP_STORE_CONNECT_ISSUER_ID` | variable in each Apple Store environment | Apple JWT/upload | Issuer UUID | No | App Store Connect Users and Access → Integrations | Normally stable for the issuer |
| `APP_STORE_CONNECT_APP_ID` | variable in each Apple Store environment | build lookup/group/review | App Store Connect app resource/numeric ID | No | Retrieve through the authenticated API and verify it resolves to the approved Bundle ID | Stable for the app |
| `TESTFLIGHT_INTERNAL_GROUP_ID` | variable, `testing-ios` | internal distribution | Internal beta-group resource ID, not display name | No | App Store Connect API/URL for the intended internal group; workflow verifies it is internal | Review when groups change |
| `TESTFLIGHT_EXTERNAL_GROUP_ID` | variable, `external-testing-ios` | external distribution | External beta-group resource ID, not display name | No | App Store Connect API/URL; workflow verifies it is external | Review when groups change |
| App Store version resource ID | protected production workflow input `app_store_version_id` | production iOS job | Exact version record ID whose marketing version equals the candidate | No | Create/select the existing version record in App Store Connect and copy its API resource ID | Supply per marketing version; never reuse blindly |

No tester-email file is consumed by these workflows. Google and Apple tester
membership is configured in the existing Console groups represented by the
track/group IDs above. Validating a private tester file elsewhere does not
prove those users are members or can install the candidate.

Exact placement/consumer matrix:

| Environment | Workflow job | Required secrets | Required variables/inputs |
|---|---|---|---|
| `testing-candidate` | `testing-candidate.yml` → `preflight` | None | Exact branch-tip `candidate_sha` and explicit `publish` dispatch choice; keep this environment secretless |
| `testing-android` | `testing-candidate.yml` → `android` | `PARLOR_ANDROID_KEYSTORE_B64`, `PARLOR_ANDROID_KEYSTORE_PASSWORD`, `PARLOR_ANDROID_KEY_ALIAS`, `PARLOR_ANDROID_KEY_PASSWORD`; plus `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_B64` only for `publish=true` | `PARLOR_ANDROID_UPLOAD_CERT_SHA256`, `GOOGLE_PLAY_PACKAGE_NAME` |
| `testing-ios` | `testing-candidate.yml` → `ios` | `PARLOR_APPLE_DISTRIBUTION_CERTIFICATE_P12_B64`, `PARLOR_APPLE_DISTRIBUTION_CERTIFICATE_PASSWORD`, `PARLOR_APPLE_APP_STORE_PROFILE_B64`; plus `APP_STORE_CONNECT_API_KEY_P8_B64` only for `publish=true` | `PARLOR_APPLE_TEAM_ID`, `PARLOR_APPLE_BUNDLE_ID`, `PARLOR_APPLE_DISTRIBUTION_CERT_SHA256`, `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID`, `APP_STORE_CONNECT_APP_ID`, `TESTFLIGHT_INTERNAL_GROUP_ID` |
| `external-testing-android` | `testing-external-promotion.yml` → `android` | `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_B64` only for `publish=true` | `GOOGLE_PLAY_PACKAGE_NAME`, `GOOGLE_PLAY_EXTERNAL_TRACK`, `GOOGLE_PLAY_EXTERNAL_TRACK_TYPE` |
| `external-testing-ios` | `testing-external-promotion.yml` → `ios` | `APP_STORE_CONNECT_API_KEY_P8_B64` only for `publish=true` | `PARLOR_APPLE_BUNDLE_ID`, `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID`, `APP_STORE_CONNECT_APP_ID`, `TESTFLIGHT_EXTERNAL_GROUP_ID` |
| `production-android` | `production-promotion.yml` → `android` | `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_B64` only for `publish=true` | `GOOGLE_PLAY_PACKAGE_NAME`, `GOOGLE_PLAY_EXTERNAL_TRACK` |
| `production-ios` | `production-promotion.yml` → `ios` | `APP_STORE_CONNECT_API_KEY_P8_B64` only for `publish=true` | `PARLOR_APPLE_BUNDLE_ID`, `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID`, `APP_STORE_CONNECT_APP_ID`; exact `app_store_version_id` is a protected dispatch input |

`main`/pull-request validation receives none of these values. The no-publication
branches in Store jobs intentionally pass empty Store credential variables to
the API client and the regression suite asserts that no Store client can be
constructed in that mode. Signing secrets are still required by a candidate
artifact rehearsal because it validates a real signed binary.

The production Android service account should have production release
permission only in `production-android`; testing accounts should not inherit
production permission. The Apple API key role must be the least role that can
perform the relevant beta/build/review operation for the app. Do not default to
Account Holder/Admin merely to avoid permission design.

### Safe file encoding and fingerprint commands

These commands stream encoded files directly to the target environment secret
without creating a plaintext encoded copy. Substitute protected absolute paths.

```bash
openssl base64 -A -in /secure/parlor-upload.jks \
  | gh secret set PARLOR_ANDROID_KEYSTORE_B64 --env testing-android

openssl base64 -A -in /secure/google-play-service-account.json \
  | gh secret set GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_B64 --env testing-android
openssl base64 -A -in /secure/google-play-service-account.json \
  | gh secret set GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_B64 --env external-testing-android
openssl base64 -A -in /secure/google-play-production-service-account.json \
  | gh secret set GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_B64 --env production-android

openssl base64 -A -in /secure/Parlor-Distribution.p12 \
  | gh secret set PARLOR_APPLE_DISTRIBUTION_CERTIFICATE_P12_B64 --env testing-ios
openssl base64 -A -in /secure/Parlor-AppStore.mobileprovision \
  | gh secret set PARLOR_APPLE_APP_STORE_PROFILE_B64 --env testing-ios

openssl base64 -A -in /secure/AuthKey_TESTING.p8 \
  | gh secret set APP_STORE_CONNECT_API_KEY_P8_B64 --env testing-ios
openssl base64 -A -in /secure/AuthKey_EXTERNAL.p8 \
  | gh secret set APP_STORE_CONNECT_API_KEY_P8_B64 --env external-testing-ios
openssl base64 -A -in /secure/AuthKey_PRODUCTION.p8 \
  | gh secret set APP_STORE_CONNECT_API_KEY_P8_B64 --env production-ios
```

The commands above mutate GitHub configuration; execute them only from a
trusted machine after checking `gh auth status` and the selected repository.
They should produce no secret output. If the web UI is used, run
`openssl base64 -A -in <file>` only in a private terminal that is not recorded.

Verify non-secret fingerprints without putting passwords on the command line:

```bash
keytool -list -v -keystore /secure/parlor-upload.jks -alias '<existing-alias>' \
  | sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]'

read -r -s PARLOR_P12_PASSWORD
export PARLOR_P12_PASSWORD
openssl pkcs12 -in /secure/Parlor-Distribution.p12 -clcerts -nokeys \
  -passin env:PARLOR_P12_PASSWORD 2>/dev/null \
  | openssl x509 -noout -fingerprint -sha256 \
  | cut -d= -f2 | tr -d ':' | tr '[:upper:]' '[:lower:]'
unset PARLOR_P12_PASSWORD

security cms -D -i /secure/Parlor-AppStore.mobileprovision \
  | plutil -p -
```

Do not paste passwords, service-account JSON, private keys, tester emails, or
profiles into workflow inputs, issues, logs, artifacts, or this repository.

## Google Play Console handoff

The workflow can upload one AAB to `internal`, promote its version code to the
configured external track, and promote that version code to `production`. It
cannot truthfully complete product/legal/account configuration. An owner must
inspect and record:

Promotion uses the Play `tracks.update` replacement operation. Automation
therefore refuses a destination that contains an active draft, staged or
halted rollout, multiple releases, or a multi-version release. Resolve such a
track deliberately in Play Console and capture the decision; the workflow must
not silently cancel or replace it.

- owner-controlled app record and canonical package matching the reviewed
  release policy; `com.parlor.app` is expressly forbidden by the known public
  collision;
- Play App Signing enrollment, app-signing certificate, and registered upload
  certificate matching `PARLOR_ANDROID_UPLOAD_CERT_SHA256`;
- the workflow verifies the upload-signed AAB; because Google Play generates
  and re-signs delivered APKs with the separate app-signing key, record the
  Play-delivered APK certificate/package/version from an internal-track device
  or Play artifact inspection as a post-upload external gate;
- linked Google Cloud project, Android Publisher API, separate service
  accounts, app-level least-privilege permissions, and key rotation owner;
- internal tester group plus the exact existing closed or open external track,
  tester groups/lists, countries, and actual tester membership;
- production eligibility and any account-specific required testing duration;
- countries/regions, free/paid status and pricing decisions;
- privacy-policy URL, app access/sign-in instructions, ads declaration,
  content rating, target audience, and Data Safety answers;
- government-app, financial-features, and health declarations;
- category, developer contact details, Store listing, localized copy,
  screenshots, icon, and feature graphic;
- account-deletion requirements (the source currently has no account system,
  but the owner must confirm the Store answer);
- outstanding policy declarations, Publishing Overview, managed-publishing
  choice, review submission, review state, rejection, and live availability.

Do not treat a temporary Console title, syntactically valid tester file, API
track commit, or successful upload as evidence that testers were added or the
release is live.

## App Store Connect handoff

The workflow can upload one IPA, associate its processed build with an internal
group, associate the same build with an external group and Beta App Review,
attach the same build to an App Store version, and optionally submit that
version's review submission. An owner must inspect and record:

- owner-controlled app record, Bundle ID matching the reviewed release policy,
  SKU, Team ID, and app ID; `com.parlor.app` is expressly forbidden by the
  known public collision;
- Apple Developer certificate/profile validity and App Store Connect API-key
  access/role;
- agreements, tax, and banking state where applicable;
- TestFlight internal and external groups, actual tester membership, beta
  description, feedback contact, Beta App Review information, and review state;
- export-compliance answer for the app's local-network transport and protected
  storage; the repository uses authenticated encryption and currently does not
  declare `ITSAppUsesNonExemptEncryption`, so the owner/export reviewer must
  determine the truthful answer before the first upload and either add a
  reviewed Info.plist declaration or complete the Store compliance flow. Do
  not infer the legal answer from the absence of a backend;
- App Privacy questionnaire and privacy-policy URL;
- age rating, category, pricing, availability, and version release mode;
- App Review contact, reviewer notes, and demo/sign-in account only if a future
  feature introduces authentication (the current source has no account login);
- screenshots for every required device class, description, keywords,
  subtitle, support URL, optional marketing URL, and release notes; and
- attached build ID, review submission state, rejection/resolution, approval,
  pending developer release, and actual Store availability.

TestFlight internal testing, TestFlight external testing, and App Store release
all use the same Bundle ID and processed build. They are distribution stages,
not separate app identities. Upload/processing, tester availability, Beta App
Review, App Review, approval, and publication are distinct states.

## Declarations that require owner/legal answers

Repository evidence currently shows local-network peer traffic, encrypted
session/storage behavior, no account backend, and no analytics/remote crash
provider. That evidence informs—but does not replace—owner/legal decisions for:

- encryption/export classification and any documentation/filing;
- Apple privacy and Google Data Safety definitions;
- child/target-audience and age-rating answers;
- murder/deception content disclosures and content rights;
- government, health, financial, ads, and tracking declarations;
- privacy-policy/support URLs, data-subject contact, and retention promises;
- license/trademark/third-party notices; and
- account-deletion applicability.

No workflow should guess these answers or change Store metadata automatically.

## State reporting and rollback

Receipts deliberately distinguish `internal_track_committed`, processed and
available to internal TestFlight, submitted for external testing, Beta App
Review, available to external testers, production-track committed, attached to
an App Store version, and submitted for production review. Console/manual
evidence must distinguish review, approval, pending developer release, and
published/live. Never report “released” from an upload receipt.

Because Google and Apple review asynchronously, a safe success on one platform
does not authorize or roll back the other. Stop a rollout by changing Store
state through an explicitly approved Console/API operation. A code/content fix
requires a new version/build and a new immutable candidate; never replace the
artifact or move an immutable release tag.

## Current external blockers

Until evidence is supplied, release automation is **NOT READY TO PUBLISH**:

- both provisional production identifiers are assigned to another developer in
  the public Stores; identity variables were removed from GitHub and all Store
  workflows are both manually disabled and fail-closed in code until an
  owner-controlled identity migration is reviewed and authenticated API
  readback is recorded;
- the public repository now has secret scanning and push protection enabled and
  supports environment reviewers and artifact attestations, but an independent
  release reviewer has not been selected or added; the active branch ruleset
  therefore enforces PR/check/conversation/immutability controls but cannot yet
  enforce an independent approval or last-pusher separation;
- no signing or Store secrets are configured; the ignored local handoff at
  `release/private/REQUIRED_FROM_USER.md` lists only the unavailable private
  inputs;
- Google Play app/package, signing enrollment, external track, service-account
  permissions, tester membership, and policy state are unverified;
- App Store Connect app/team/build/groups/API access, certificate/profile, and
  metadata/compliance state are unverified;
- signed AAB/IPA validation and validation-only GitHub rehearsals have not run
  on protected hosted runners; and
- physical-device, signed Play Internal/TestFlight, accessibility, privacy,
  legal, and Store-review gates remain external.

Official platform references:

- GitHub environments and deployment protection:
  <https://docs.github.com/actions/deployment/targeting-different-environments/using-environments-for-deployment>
- GitHub artifact attestations:
  <https://docs.github.com/actions/security-for-github-actions/using-artifact-attestations>
- Google Play Android Publisher API:
  <https://developers.google.com/android-publisher>
- App Store Connect API:
  <https://developer.apple.com/documentation/appstoreconnectapi>
- Xcode distribution:
  <https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases>
- Apple upload toolchain requirements:
  <https://developer.apple.com/news/upcoming-requirements/>
- GitHub-hosted macOS runner software inventory:
  <https://github.com/actions/runner-images/tree/main/images/macos>
