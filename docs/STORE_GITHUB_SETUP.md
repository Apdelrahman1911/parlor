# Store identities and GitHub release setup

Prepared on **2026-09-14** for `Apdelrahman1911/parlor`. This guide explains
every owner-supplied secret, variable, and dispatch input consumed by the three
Store workflows. Follow [RELEASE_AUTOMATION.md](RELEASE_AUTOMATION.md) for the
release contract and [RELEASE_GATES.md](RELEASE_GATES.md) for acceptance criteria.

## 1. Selected identifiers and availability check

| Platform | Configured release identifier | Configured Debug identifier |
|---|---|---|
| Android / Google Play | `me.parlor.android` | `me.parlor.android.debug` |
| iOS / App Store / TestFlight | `me.parlor.ios` | `me.parlor.ios.debug` |

The app's displayed name can remain **Parlor**. Different Android and iOS
identifiers are supported. Internal testing, external testing, and production
use the same release identifier on each platform; do not register separate
testing identities for these workflows.

Public checks performed on 2026-09-14:

| Identifier | Google Play US listing | Apple US Bundle ID lookup |
|---|---|---|
| `me.parlor.android` | HTTP 404 | HTTP 200, `resultCount: 0` |
| `me.parlor.ios` | HTTP 404 | HTTP 200, `resultCount: 0` |

Sources: [Android Play lookup](https://play.google.com/store/apps/details?id=me.parlor.android&hl=en&gl=US),
[iOS Play lookup](https://play.google.com/store/apps/details?id=me.parlor.ios&hl=en&gl=US),
[Android Apple lookup](https://itunes.apple.com/lookup?bundleId=me.parlor.android&country=us),
and [iOS Apple lookup](https://itunes.apple.com/lookup?bundleId=me.parlor.ios&country=us).

**No public listing was found; availability and ownership are not verified.**
Unpublished or region-restricted records may not appear. Confirm acceptance in
your developer accounts. The reverse-domain prefix `me.parlor` corresponds to
`parlor.me`; these checks establish neither domain ownership nor naming rights.

The owner-selected IDs above replace `com.parlor.app` in Gradle, Xcode, Debug
configuration, release policy, schema, artifact validators, and workflow
assertions. Kotlin/Java package and resource namespaces remain `com.parlor.app`;
these are code namespaces, not Store application identities. The shared LAN
application discriminator also stays unchanged so Android and iOS discover
each other; it is not an Android package or Apple Bundle ID. Store ownership
remains blocked as `store_ownership_unverified`, with no verification evidence.
Publication remains disabled. Registering identifiers and creating Store
records do not by themselves pass release qualification.

## 2. Prepare accounts and GitHub environments

You need an active Google Play developer account, Apple Developer membership,
App Store Connect access, GitHub repository administration, and a Mac for the
Apple signing setup. Complete the account verification and agreements shown by
each provider. UI labels can vary by account permissions and provider updates.

In GitHub, open **Settings → Environments**, then create or inspect these exact
environment names:

| Environment | Allowed deployment branch | Purpose |
|---|---|---|
| `testing-candidate` | `testing` | Approval before reserving the candidate version/build; keep secretless |
| `testing-android` | `testing` | Sign Android and upload to Play internal testing |
| `testing-ios` | `testing` | Sign iOS and upload to internal TestFlight |
| `external-testing-android` | `testing` | Promote to the selected external Play track |
| `external-testing-ios` | `testing` | External TestFlight and Beta App Review |
| `production-android` | `release` | Promote the tested Play version to production |
| `production-ios` | `release` | Attach the tested Apple build and optionally submit for review |

Inside each environment, use **Environment secrets → Add secret** for secrets
and **Environment variables → Add variable** for variables. Names are exact.
Do not use repository-wide Store secrets. `main` and pull-request verification
need none of these credentials. GitHub supplies `GITHUB_TOKEN` automatically;
these workflows do not require an owner-created PAT secret.

Configure an independent required reviewer and prevent self-review. Apply the
branch restrictions above and disable administrator bypass where supported.
The repository contract also requires protected `main`, `testing`, and `release`
branches, the six mandatory verification checks, resolved PR conversations,
at least one approval with last-pusher separation, and no force pushes/deletion.
See the automation contract for the exact check names. Verify the current
settings; historical setup records do not prove today's configuration.

## 3. Google Play: create the app and prepare access

### 3.1 Establish the Android app record

1. In [Play Console](https://play.google.com/console/), select your developer
   account and create the Parlor app. Choose the real default language, app/game
   category, and free/paid setting, and complete required declarations.
2. Confirm the final package will be `me.parlor.android`. A Console app title
   does not reserve this package; its identity is established through the
   accepted Android artifact. Package names cannot be changed for an existing
   published app.
3. Complete Play App Signing setup and identify the upload certificate registered
   for this app. Google may use a separate app-signing key for delivered APKs.
4. Confirm app access through the authorized Play principal before marking the
   repository's identity policy verified.

**First-app bootstrap:** the existing workflow expects an app already accessible
through the Publishing API. A brand-new app can require its first artifact to
be uploaded through Play Console before API operations work. Treat that as a
separate reviewed setup step after the identity migration and artifact validation;
do not bypass the workflow's identity guard. Record any version code consumed
by bootstrap so the later immutable candidate uses an unused code. Creating
this guide authorizes no upload.

### 3.2 Obtain or create the Android upload key

For an existing Play app, obtain the registered upload keystore from its
custodian. Do not replace it with a random new key. For a new app with no
established upload key, create one on a trusted machine with JDK `keytool`:

```bash
keytool -genkeypair -v \
  -keystore /secure/parlor-upload.jks \
  -storetype JKS \
  -alias parlor-upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Replace `/secure/` with a private directory outside the checkout. Answer the
identity prompts accurately, choose strong passwords, and back up the keystore
and passwords in protected custody. If you press Enter to reuse the keystore
password for the key, both password secrets will contain that same value.
`parlor-upload` is an example alias for a new key; an existing key keeps its alias.

Inspect the upload certificate without putting passwords on the command line:

```bash
keytool -list -v -keystore /secure/parlor-upload.jks -alias parlor-upload
```

Copy its SHA-256 fingerprint, remove all colons, and lowercase A–F. The result
must contain exactly 64 hexadecimal characters. Compare it with the **upload
key certificate** in Play Console's App integrity/App signing page. Do not copy
Google's separate app-signing certificate fingerprint into the upload variable.

### 3.3 Create Google API credentials

1. In [Google Cloud Console](https://console.cloud.google.com/), choose a project
   you control. Enable **Google Play Android Developer API** (`androidpublisher`).
   Google's current setup documentation says linking a Play developer account
   to the Cloud project is no longer required.
2. Open **IAM & Admin → Service Accounts**, create a service account, and record
   its email. Do not grant Cloud Owner/Editor merely to permit Play publishing;
   Play permissions are configured in Play Console.
3. In **Play Console → Users and permissions**, invite that service-account email
   and grant access to this app. Give testing credentials the app visibility and
   testing-release permissions they need. Production credentials additionally
   need the production release permission required for promotion. Check the
   current Console permission labels and avoid unrelated account-wide access.
4. In the Cloud service account's **Keys** tab, create a JSON key if your
   organization's policy permits it. Download it once to protected storage.
   This workflow specifically consumes a JSON private key; it does not implement
   keyless federation. If key creation is prohibited, arrange an approved
   authentication change rather than weakening your organization's policy.
5. Base64-encode the complete JSON file into the GitHub secret. Do not paste
   just `private_key`, the service-account email, or an OAuth access token.
6. Prefer distinct credentials for internal testing, external testing, and
   production, with the same secret name scoped to each environment. Validation
   with `publish=false` does not prove these credentials can access Play.

### 3.4 Create tester access and identify the external track

Configure your internal tester list in Play Console. Create/select the closed
or open external test track, add its actual testers, and complete any required
country/availability setup. Account-specific production testing requirements
must also be met.

`GOOGLE_PLAY_EXTERNAL_TRACK` is the exact API `track` value for that existing
track. Do not infer it from a friendly display name or assume `alpha`/`beta`.
Obtain it from the Console where exposed or an authorized setup operation using
`edits.tracks.list`; that API requires an edit ID, so creating an edit is a
Store operation, not a purely local lookup. Keep setup edits uncommitted unless
their changes are separately approved, and retire disposable edits afterward.

Set `GOOGLE_PLAY_EXTERNAL_TRACK_TYPE` to `closed` or `open`, matching the track.
Internal and production track names are already fixed by repository policy;
you do not supply GitHub variables for them.

### 3.5 Every Android secret and variable

| Name | GitHub type | Value / source | Environments |
|---|---|---|---|
| `PARLOR_ANDROID_KEYSTORE_B64` | Secret | Base64 of the upload `.jks`/PKCS12 file from §3.2 | `testing-android` |
| `PARLOR_ANDROID_KEYSTORE_PASSWORD` | Secret | Exact password opening that keystore | `testing-android` |
| `PARLOR_ANDROID_KEY_ALIAS` | Secret | Alias containing the upload private key; e.g. `parlor-upload` for the example new key | `testing-android` |
| `PARLOR_ANDROID_KEY_PASSWORD` | Secret | Exact private-key password for that alias | `testing-android` |
| `PARLOR_ANDROID_UPLOAD_CERT_SHA256` | Variable | Registered upload certificate SHA-256, 64 lowercase hex characters, no colons | `testing-android` |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_B64` | Secret | Base64 of the complete environment-appropriate JSON key from §3.3 | `testing-android`, `external-testing-android`, `production-android` |
| `GOOGLE_PLAY_PACKAGE_NAME` | Variable | `me.parlor.android` **after** registration/ownership confirmation and reviewed migration | `testing-android`, `external-testing-android`, `production-android` |
| `GOOGLE_PLAY_EXTERNAL_TRACK` | Variable | Actual existing external track's API identifier from §3.4 | `external-testing-android`, `production-android` |
| `GOOGLE_PLAY_EXTERNAL_TRACK_TYPE` | Variable | `closed` or `open` | `external-testing-android` |

## 4. Apple: register the app and prepare signing

### 4.1 Register the Bundle ID and App Store Connect app

1. Sign in to [Apple Developer](https://developer.apple.com/account/) with the
   intended team. In membership details, record the **Team ID**: ten uppercase
   letters/digits. This is not the API Key ID, Issuer ID, or numeric Apple app ID.
2. Open **Certificates, Identifiers & Profiles → Identifiers → + → App IDs →
   App**. Register an **explicit** Bundle ID, `me.parlor.ios`, with a meaningful
   description. Apple's acceptance is required; the public lookup cannot reserve it.
3. Enable only capabilities the app actually needs. Do not add push, associated
   domains, app groups, or other entitlements merely to get through setup.
4. In [App Store Connect](https://appstoreconnect.apple.com/), open **Apps → + →
   New App**. Select iOS, the intended Bundle ID, the real language/name, and a
   unique internal SKU. The SKU is an owner-chosen inventory label, not a GitHub
   secret or Bundle ID.
5. Under the app's **App Information**, copy its numeric **Apple ID** into
   `APP_STORE_CONNECT_APP_ID`. Verify the app record shows `me.parlor.ios`.
   Confirm that association with authenticated API readback during setup.

### 4.2 Obtain an Apple Distribution certificate with its private key

1. Reuse a valid distribution identity controlled by your team when appropriate.
   A downloaded `.cer` alone is insufficient: signing also needs its private key.
2. For a new identity, on your Mac open **Keychain Access → Certificate Assistant
   → Request a Certificate From a Certificate Authority**. Save a CSR to disk;
   the associated private key stays in your keychain.
3. In the Apple Developer certificates page, create an **Apple Distribution**
   certificate using that CSR, download the certificate, and import it on the
   same Mac. In Keychain Access, confirm the certificate expands to show its
   private key. If it does not, locate the original private key instead of
   exporting a certificate-only file.
4. Export the certificate together with its private key as
   `Parlor-Distribution.p12`, protected by a strong export password.
5. Store Base64 of the `.p12` as
   `PARLOR_APPLE_DISTRIBUTION_CERTIFICATE_P12_B64`. Store the export password as
   `PARLOR_APPLE_DISTRIBUTION_CERTIFICATE_PASSWORD`.
6. Obtain the SHA-256 fingerprint of that exact distribution certificate and
   normalize it to 64 lowercase hex characters without colons. If the downloaded
   `.cer` is DER encoded, this prints its public fingerprint:

```bash
openssl x509 -inform DER -in /secure/Parlor-Distribution.cer \
  -noout -fingerprint -sha256
```

Ensure this certificate is the one exported in the `.p12`. Enter the normalized
value in `PARLOR_APPLE_DISTRIBUTION_CERT_SHA256`.

### 4.3 Create the App Store provisioning profile

1. In **Apple Developer → Certificates, Identifiers & Profiles → Profiles → +**,
   choose the App Store Connect/App Store distribution profile type for iOS.
2. Select the explicit `me.parlor.ios` App ID and the distribution certificate
   from §4.2. Use the intended team.
3. Generate and download the `.mobileprovision` file. A development, ad hoc,
   enterprise, or wildcard profile will not satisfy this workflow.
4. Store Base64 of the complete file as `PARLOR_APPLE_APP_STORE_PROFILE_B64`.
   Keep track of profile and certificate expiry. Renewed signing inputs require
   a newly reviewed candidate; do not replace an already-tested artifact.

### 4.4 Obtain an App Store Connect team API key

1. Open **App Store Connect → Users and Access → Integrations → App Store Connect
   API → Team Keys**. The Account Holder may need to request API access first;
   an Account Holder/Admin creates team keys.
2. Generate a key for the required workflow role. This implementation requires
   an **Issuer ID** and uses team-key authentication; do not substitute an
   individual API key that lacks this issuer configuration.
3. Give each key the least role that can perform its upload, TestFlight, or
   App Review operation. Select the production role against Apple's current
   endpoint permission table; do not use Admin merely to avoid investigating a
   permission error. Team keys can have access across the team's apps, so do
   not assume they are restricted to Parlor merely because this workflow is.
4. Download the `.p8` private key immediately; Apple permits downloading it only
   once. Save it in protected custody and encode it as
   `APP_STORE_CONNECT_API_KEY_P8_B64`.
5. Copy the matching ten-character **Key ID** to `APP_STORE_CONNECT_KEY_ID` and
   the **Issuer ID** UUID to `APP_STORE_CONNECT_ISSUER_ID`. These two identifiers
   are variables; the `.p8` is a secret.
6. Prefer separate keys for testing and production operations. Each environment's
   private key, Key ID, and Issuer ID must form a matching set.

### 4.5 Create TestFlight groups and obtain their IDs

1. In the app's **TestFlight** tab, create/select the internal group and add the
   eligible App Store Connect users who should test internally.
2. Create/select an external group, add its testers, and complete the beta
   description, feedback contact, and Beta App Review details. External testing
   may require Beta App Review before testers can install.
3. Obtain the groups' resource IDs through an authorized App Store Connect API
   client. Request `GET /v1/apps/{APP_ID}/betaGroups` and follow pagination.
   Match each record by `attributes.name` and `attributes.isInternalGroup`;
   copy its `data[].id`. Do not copy the group name or tester email.
4. Set the internal ID in `TESTFLIGHT_INTERNAL_GROUP_ID`; set the external ID in
   `TESTFLIGHT_EXTERNAL_GROUP_ID`. Group creation does not add testers by itself.

API paths here are relative to `https://api.appstoreconnect.apple.com` and need
a short-lived JWT signed using the appropriate `.p8`, Key ID, and Issuer ID.
Use a trusted API client or an approved protected setup run; keep private keys
and JWTs out of command history, shared logs, and screenshots.

### 4.6 Every Apple secret and variable

| Name | GitHub type | Value / source | Environments |
|---|---|---|---|
| `PARLOR_APPLE_DISTRIBUTION_CERTIFICATE_P12_B64` | Secret | Base64 of distribution certificate **and private key** exported as `.p12`, §4.2 | `testing-ios` |
| `PARLOR_APPLE_DISTRIBUTION_CERTIFICATE_PASSWORD` | Secret | Exact `.p12` export password | `testing-ios` |
| `PARLOR_APPLE_APP_STORE_PROFILE_B64` | Secret | Base64 of the matching App Store `.mobileprovision`, §4.3 | `testing-ios` |
| `PARLOR_APPLE_TEAM_ID` | Variable | Ten-character Developer Team ID, §4.1 | `testing-ios` |
| `PARLOR_APPLE_DISTRIBUTION_CERT_SHA256` | Variable | Exact distribution certificate SHA-256, 64 lowercase hex characters without colons | `testing-ios` |
| `PARLOR_APPLE_BUNDLE_ID` | Variable | `me.parlor.ios` **after** ownership confirmation and reviewed migration | `testing-ios`, `external-testing-ios`, `production-ios` |
| `APP_STORE_CONNECT_API_KEY_P8_B64` | Secret | Base64 of the environment-appropriate team API `.p8`, §4.4 | `testing-ios`, `external-testing-ios`, `production-ios` |
| `APP_STORE_CONNECT_KEY_ID` | Variable | Matching API Key ID, ten uppercase letters/digits | `testing-ios`, `external-testing-ios`, `production-ios` |
| `APP_STORE_CONNECT_ISSUER_ID` | Variable | Team API issuer UUID | `testing-ios`, `external-testing-ios`, `production-ios` |
| `APP_STORE_CONNECT_APP_ID` | Variable | Numeric Apple app ID from App Information, §4.1 | `testing-ios`, `external-testing-ios`, `production-ios` |
| `TESTFLIGHT_INTERNAL_GROUP_ID` | Variable | Internal beta-group resource ID, §4.5 | `testing-ios` |
| `TESTFLIGHT_EXTERNAL_GROUP_ID` | Variable | External beta-group resource ID, §4.5 | `external-testing-ios` |

## 5. Encode and enter values safely

`_B64` means Base64 of the **entire file**, not the filename. Base64 is encoding,
not encryption. Keep credentials outside the repository and never paste real
values into this document, a PR, chat, workflow inputs, or an artifact.

From a trusted machine with GitHub CLI installed, authenticate and confirm the
repository. The commands below modify GitHub secrets when you run them; replace
the example file paths with your protected local paths.

```bash
gh auth status
gh repo view Apdelrahman1911/parlor --json nameWithOwner

openssl base64 -A -in /secure/parlor-upload.jks \
  | gh secret set PARLOR_ANDROID_KEYSTORE_B64 \
      --repo Apdelrahman1911/parlor --env testing-android

openssl base64 -A -in /secure/Parlor-Distribution.p12 \
  | gh secret set PARLOR_APPLE_DISTRIBUTION_CERTIFICATE_P12_B64 \
      --repo Apdelrahman1911/parlor --env testing-ios

openssl base64 -A -in /secure/Parlor-AppStore.mobileprovision \
  | gh secret set PARLOR_APPLE_APP_STORE_PROFILE_B64 \
      --repo Apdelrahman1911/parlor --env testing-ios

openssl base64 -A -in /secure/google-play-testing.json \
  | gh secret set GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_B64 \
      --repo Apdelrahman1911/parlor --env testing-android

openssl base64 -A -in /secure/AuthKey_TESTING.p8 \
  | gh secret set APP_STORE_CONNECT_API_KEY_P8_B64 \
      --repo Apdelrahman1911/parlor --env testing-ios
```

Repeat the Google JSON command for `external-testing-android` and
`production-android`, selecting each environment's proper credential file.
Repeat the Apple `.p8` command for `external-testing-ios` and `production-ios`,
again selecting the appropriate key. Do not copy signing keystores, `.p12`
files, or provisioning profiles into promotion environments: promotions reuse
existing Store artifacts.

Use GitHub's environment secret form or the CLI's interactive prompt for
passwords and the Android alias; do not put passwords in command arguments:

```bash
gh secret set PARLOR_ANDROID_KEYSTORE_PASSWORD --repo Apdelrahman1911/parlor --env testing-android
gh secret set PARLOR_ANDROID_KEY_ALIAS --repo Apdelrahman1911/parlor --env testing-android
gh secret set PARLOR_ANDROID_KEY_PASSWORD --repo Apdelrahman1911/parlor --env testing-android
gh secret set PARLOR_APPLE_DISTRIBUTION_CERTIFICATE_PASSWORD --repo Apdelrahman1911/parlor --env testing-ios
```

Variables contain ordinary text, not Base64. Example **after identity approval**:

```bash
gh variable set GOOGLE_PLAY_PACKAGE_NAME --body 'me.parlor.android' \
  --repo Apdelrahman1911/parlor --env testing-android
gh variable set PARLOR_APPLE_BUNDLE_ID --body 'me.parlor.ios' \
  --repo Apdelrahman1911/parlor --env testing-ios
```

Repeat variables in every environment listed in §3.5 and §4.6. Environment
values are not inherited from another environment. Do not include surrounding
quotation marks or accidental whitespace in values entered through the web UI.

List configured names without retrieving secret contents:

```bash
gh secret list --repo Apdelrahman1911/parlor --env testing-android
gh variable list --repo Apdelrahman1911/parlor --env testing-android
```

Repeat for the other six environments. Matching names prove configuration
presence, not credential validity or permission to publish.

## 6. Workflow inputs: where each value comes from

These values are supplied in **Actions → workflow → Run workflow**, not as
GitHub secrets. The workflows currently remain disabled. The sequence below
applies only after the reviewed identity and release-control migration.

### 6.1 Create immutable Store candidate — branch `testing`

Source: [testing-candidate.yml](../.github/workflows/testing-candidate.yml).

| Input | How to obtain / choose it |
|---|---|
| `candidate_sha` | Full 40-character commit SHA at the tip of protected `testing`, after its complete reviewed source is integrated. Copy the full SHA from GitHub; do not use the short hash or a branch name. |
| `publish` | `false` for a disposable signing/validation rehearsal; `true` for a separately authorized real upload to Play internal and internal TestFlight. |

Signing secrets are needed even for the candidate rehearsal. Store API secrets
are consumed only for publishing. A rehearsal does not create a promotable
candidate, validate Store access, or count as the later tested artifact.

### 6.2 Promote immutable candidate to external testing — branch `testing`

Source: [testing-external-promotion.yml](../.github/workflows/testing-external-promotion.yml).

| Input | How to obtain / choose it |
|---|---|
| `candidate_run_id` | Numeric ID from the successful publishing candidate's Actions URL: `/actions/runs/<ID>`. Use a run with a sealed candidate manifest for both platforms. |
| `candidate_run_attempt` | Exact successful attempt number for that candidate run, visible in Actions/API. A rerun may be attempt 2 or later; do not assume 1. |
| `platform` | `android`, `ios`, or `both`, matching the promotion you authorize. |
| `publish` | `false` validates local evidence without Store mutation; `true` performs the authorized external-testing promotion. |

### 6.3 Promote tested Store candidate to production — branch `release`

Source: [production-promotion.yml](../.github/workflows/production-promotion.yml).

| Input | How to obtain / choose it |
|---|---|
| `candidate_run_id` | Same immutable candidate run ID used for the completed external qualification. |
| `candidate_run_attempt` | Exact successful candidate attempt associated with its manifest. |
| `external_run_id` | External promotion run ID with the required successful external evidence. An Apple review-pending receipt is not successful tester availability. |
| `external_run_attempt` | Exact external run attempt associated with the successful attested evidence. |
| `platform` | `android`, `ios`, or `both`. |
| `apple_operation` | `attach` attaches the tested build to the selected version; `submit` also requests App Review submission. This is an explicit operational choice, not a credential. |
| `app_store_version_id` | Required for `ios`/`both`: the version **resource ID**, obtained as described below. It is not the app ID, build number, or version string. |
| `publish` | `false` validates without changing the Stores; `true` performs the selected authorized production operation. |

For `app_store_version_id`, create/select the intended iOS version in App Store
Connect, then query `GET /v1/apps/{APP_ID}/appStoreVersions` with an authorized
client. Follow pagination, select the record whose `attributes.platform` is
`IOS` and `attributes.versionString` equals the candidate's marketing version,
and copy its `data[].id`. Confirm the existing record is suitable for the intended
submission; do not create duplicate versions to resolve an ambiguous lookup.

For run attempt numbers, the authenticated GitHub API response from
`GET /repos/Apdelrahman1911/parlor/actions/runs/{RUN_ID}` includes `run_attempt`.
If using an earlier attempt's receipt, inspect that exact attempt's Actions page
and evidence instead of taking the latest number blindly.

The complete `release` Git tree must equal the tested candidate tree. Production
promotion does not rebuild or sign. Store upload, processing, tester access,
review submission, approval, and public availability are separate outcomes.

## 7. Other required values that are not GitHub secrets

| Value / decision | Where to set or obtain it |
|---|---|
| `PARLOR_VERSION_NAME` | `config/parlor-version.xcconfig`; currently `1.0.0`. Review the intended three-part marketing version. |
| `PARLOR_BUILD_NUMBER` | Same file; currently `1`. Must be an unused positive integer for both Stores. Bootstrap uploads can consume a number. Workflows do not auto-increment. |
| Android internal/production track | Already `internal` / `production` in `config/release-policy.json`; no extra GitHub variables. |
| Apple SKU | Choose when creating the App Store Connect app; no workflow input or secret. |
| Tester membership | Play Console lists/tracks and TestFlight groups; these workflows consume no tester-email file. |
| Privacy policy and support URLs | Publish real pages and enter them in the Store listings. Draft requirements are in `STORE_METADATA.md`. |
| Ratings, privacy, export answers, rights, countries, pricing, reviewer contact | Complete in the Stores using the actual app behavior and owner decisions; credentials cannot fill these declarations. |
| Apple build encryption status | Resolve the real export-compliance classification before candidate upload/processing. The current source lacks an `ITSAppUsesNonExemptEncryption` declaration; use a reviewed declaration or the Store compliance flow, not a guessed value. |
| Apple toolchain | Repository policy pins Xcode `26.3` / `17C529`, physical iOS SDK major at least `26`, deployment target `16.0`; confirm the runner and current Store requirements before release. |

## 8. Configuration completion and remaining release stops

Before requesting workflow enablement, confirm:

- The proposed identifiers are accepted and demonstrably controlled by the
  intended accounts, with any first-app bootstrap explicitly handled.
- The complete repository identity migration and fresh checks have passed.
- All seven environments have the exact placement above and the required
  independent approvals and deployment branch restrictions.
- Signing fingerprints match the selected upload/distribution certificates;
  Apple profile, certificate, Bundle ID, and Team ID agree.
- Store principals, app records, tracks/groups, and tester membership have been
  verified in the intended accounts. `publish=false` cannot establish this.
- Version/build values are unused; pending Store drafts or rollouts have been
  deliberately reviewed before promotion.

All three Store workflows still have checked-in `if: ${{ always() && false }}`
guards, and the release policy still marks identity ownership blocked. The
documented remote workflow disables must also be checked. Do not remove guards
or merely flip policy to `verified` after entering secrets: enablement requires
the reviewed identity, authenticated ownership, and release-control evidence.

The Last Light integration still needs fresh qualification. The recorded iOS
Strict Complete Protection failure, physical-device LAN/accessibility testing,
signed-artifact testing, and owner/legal/privacy gates remain separate work;
this configuration guide does not resolve them. See
[PROJECT_STATUS.md](PROJECT_STATUS.md) and [RELEASE_RUNBOOK.md](RELEASE_RUNBOOK.md).

## 9. Provider references

- [Google Play Developer API setup](https://developers.google.com/android-publisher/getting_started)
- [Android app signing and upload keys](https://developer.android.com/studio/publish/app-signing)
- [Google Play edit workflow](https://developers.google.com/android-publisher/edits)
- [Google Play track listing API](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.tracks/list)
- [Register an Apple App ID](https://developer.apple.com/help/account/identifiers/register-an-app-id/)
- [Create an App Store provisioning profile](https://developer.apple.com/help/account/provisioning-profiles/create-an-app-store-provisioning-profile/)
- [App Store Connect API keys](https://developer.apple.com/help/app-store-connect/get-started/app-store-connect-api/)
- [App Store Connect API reference](https://developer.apple.com/documentation/appstoreconnectapi)
- [GitHub deployment environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)

Public identifier lookups and the Google API setup / Apple App ID, profile, and
API-key help pages were checked when preparing this guide. Authenticated account
state, registrations, credential permissions, and Store eligibility were not
verified. Keep actual credentials in protected custody, not in this file.
