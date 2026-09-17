# GitHub distribution: Android and Desktop

This channel targets <https://github.com/Apdelrahman1911/parlor/releases>.
It is separate from Play/App Store delivery. **All three Store workflows stay
disabled.** A successful unsigned rehearsal is not a production release.

## Artifacts and native verification

Version name/build number remain in `config/parlor-version.xcconfig`. No
pre-existing GitHub release/tag convention was found; this channel uses
`github-v<version>-b<build>` (for example `github-v1.0.0-b1`), without changing
the existing Store candidate conventions.

New public releases must increase **both** the marketing version and build
number; a build-only increment cannot upgrade an existing Windows MSI. The
preflight/publisher check prior releases (including reserved drafts) and fail
closed on conflicts. MSI versions use three canonical numeric components within
Windows limits (255.255.65535 maximum). macOS's `CFBundleVersion` uses the shared
build number, with minimum macOS 11.0 for the bundled JDK/Skiko. Debian versions
are `<version>-<build>`. Native installer inspection verifies these values and
the fixed Windows upgrade identity, not only the output filename.

| Target | Artifact | Candidate requirements |
|---|---|---|
| Android | Release APK, `me.parlor.android` | Existing real publisher key, pinned certificate, v2/v3 signatures, zip alignment, non-debug manifest/privacy/notice checks |
| macOS arm64 and x64 | Separate self-contained DMGs, `me.parlor.desktop` | Developer ID on native hierarchy, outer app and image; hardened JVM; Accepted notarization for app and DMG; staples; mounted app and Gatekeeper checks |
| Windows x64 | Per-user MSI | Publisher-owned launcher/Skiko signed before installer; valid vendor signatures preserved; trusted Code Signing certificate and timestamp; extracted payload verification |
| Linux x64 | DEB, package `parlor` | Native package identity/architecture, extracted payload/runtime probe, SHA-256 and attested provenance |

Desktop builds use Temurin **JDK 21**, `./gradlew productionDesktopCheck
:composeApp:createDistributable --dependency-verification=strict`, and bundled
runtime modules. Homebrew's JDK is suitable for tests but is not accepted by
Compose's native packaging vendor check. Do not bypass that check.

Linux `jpackage` dereferences JDK legal-file aliases while copying an app image.
Preparation materializes only bounded aliases within `runtime/legal`, preserving
their exact bytes before hashing. The installed-image comparison still requires
every file to match; it never ignores native code or silently accepts a mismatch.
Temporary DMG staging (including the `/Applications` link) is retired on success
and failure, so later repository scans cannot traverse installed applications.

`desktop_package.py` probes the **packaged** launcher with
`--verify-distribution`: JDK version, AES-GCM, the EC provider and native Skia.
It returns before profile/storage/DI/LAN initialization and uses an isolated
home. Installers are mounted/extracted; the entire installed image, not merely
the launcher, must match. Bundled notices remain byte-bound. Runtime `legal/`
notices are retained. macOS includes EN/AR native LAN permission rationale.

Compose/JDK signing changes extracted Skiko bytes without updating the upstream
sibling checksum. `distribution_image.py` does **not** simply rehash those bytes.
It verifies the exact Maven-pinned original and the generated signature, then
compares the **complete bytes** of disposable copies after the same ad-hoc signing
roundtrip. This canonicalizes signature data and its `__LINKEDIT` allocation;
no executable section or other payload is excluded from comparison. Only a
proven generated ad-hoc/Intel-unsigned copy is retained with a refreshed hash.
An exact vendor-signed original is preserved, not stripped or replaced; a foreign
signature still fails the candidate's same-team/library-validation policy.
Non-native vendor resources are preserved; unused embedded architectures are
omitted. The candidate signer updates the hash only after independently verifying
the new signature. Gradle's cache and original vendor signatures remain untouched.

Compose 1.10.3 also extracts Windows `icudtl.dat` beside Skiko; its bytes are
checked against that same pinned JAR and retained in the verified image. All
other non-native JAR resources must remain identical. On Intel macOS,
Compose's `NoCertificateSigner` deliberately leaves a fresh app unsigned (only
arm64 is automatically ad-hoc signed). Preparation accepts that specific fresh
unsigned Intel input, seals the prepared rehearsal app ad-hoc and verifies it.
Invalid existing seals and production signatures are never repaired/replaced.
Candidate signing still requires the complete real Developer ID policy.

### Reviewed build-only helper

Native packaging first exercised
`org.jetbrains.compose:gradle-plugin-internal-jdk-version-probe:1.10.3`.
On 2026-09-17 its Maven Central JAR, POM/module and published hashes matched
the downloaded bytes. The source is a small Java vendor/version probe,
Apache-2.0, with no runtime dependencies. Only this explicitly reviewed
component was added manually to verification metadata; no graph or version
was relaxed. It is a build tool, not an application dependency.

| Artifact | SHA-256 |
|---|---|
| JAR | `93e712459cb6e7ea28759c50745b96b9f0a9cbd36e102a6451ba6a3202c34643` |
| Gradle module | `3127fcb0980c77aeff0959fc28aa5e5484e2d41035ca3b9e229420ba82c22e16` |
| POM | `a2cbf1d985356a91018b6d4617f42b361b35434d67ebbe47dc302b59b12df340` |
| Reviewed source JAR | `6674d97b0c98e5482762444c16ddd2fcd612c38498bdd4d7c3ebdfa115c15c71` |

Base URL: `https://repo.maven.apache.org/maven2/org/jetbrains/compose/gradle-plugin-internal-jdk-version-probe/1.10.3/`.
Re-run strict transport/provenance tests after editing these inputs.

## Workflow and immutable custody

`.github/workflows/github-distribution.yml` has four jobs: preflight, a native
five-variant matrix, seal, and publication. Actions use the repository's reviewed
immutable pins. A single non-cancelling concurrency group serializes this channel.

1. **Rehearsal** is the default dispatch and runs on `feat/last-light` pushes.
   It builds/tests/inspects unsigned artifacts and uploads them to the Actions
   run, with canonical descriptors and attestations. It has no release-write or
   signing authority. These downloads must never be promoted as production.
2. Run full **Production verification** on the same clean SHA. All six mandatory
   jobs must pass in one run; focused runs or another SHA cannot substitute.
3. **Candidate** is an explicit dispatch on `main` or `feat/last-light`, after
   protected approval. Build once, sign/notarize, re-inspect, freeze and attest.
   Manifests bind commit, Git tree, version/build, platform, run/attempt, byte
   sizes, SHA-256, public signer identity and exact-source owner acceptance.
4. Push the exact `github-v<version>-b<build>` tag to trigger publication, or
   dispatch `mode=publish` on that tag with the candidate run ID. The publisher
   downloads the frozen bundle and verifies workflow/source attestations, all
   bytes, independent protected certificate pins and current qualification.
   **It never compiles, signs, uploads to a Store or rebuilds missing artifacts.**
5. Create a draft Release, upload only the five installers, manifest and
   `SHA256SUMS`, download/verify every asset, then expose the Release and read
   it back. No IPA/AAB, signing material, raw logs or player data is published.

Partial uploads stay draft. A retry accepts only identical existing bytes and
fills missing assets; it never overwrites conflicts or moves tags. Rerun **failed
jobs only** after partial candidate success. Already-frozen platforms refuse
rebuilding. Missing/expired artifacts fail publication: allocate a new reviewed
build number rather than substituting rebuilt bytes. Artifact retention is 90 days.

## Protected setup: external prerequisites

Create `github-sign-android`, `github-sign-macos`, `github-sign-windows`,
`github-sign-linux` and `github-publish`. Require named reviewers, prevent
self-review, and disable administrator bypass. Signing environments allow only
explicit branches `main` and/or `feat/last-light`; publication allows **tags**
`github-v*` only. The workflow reads these protections back and fails closed.
The automatically created `github-rehearsal` environment must hold no secrets.

Each signing environment needs protected variables `GH_DIST_APPROVED_SHA`,
`GH_DIST_ACCEPTANCE_SHA`, and `GH_DIST_ACCEPTANCE_REFERENCE` (a repository issue
or issue-comment URL). Acceptance must cover the exact source, physical mixed-LAN
and lifecycle/accessibility behavior, native install/run/close, public identity,
redistribution rights and the Desktop limitations below. Do not populate these
values from a green CI run or manufacture a physical acceptance receipt.

| Protected environment | Secrets (names, never values) | Public variables |
|---|---|---|
| `github-sign-android` | `GH_DIST_ANDROID_KEYSTORE_BASE64`, `GH_DIST_ANDROID_KEYSTORE_PASSWORD`, `GH_DIST_ANDROID_KEY_ALIAS`, `GH_DIST_ANDROID_KEY_PASSWORD` | `GH_DIST_CERT_SHA256`: existing publisher certificate, lowercase hex without colons |
| `github-sign-macos` | `GH_DIST_MACOS_CERTIFICATE_BASE64` (Developer ID P12), `GH_DIST_MACOS_CERTIFICATE_PASSWORD`, `GH_DIST_APPLE_API_KEY_BASE64` (notary-capable P8), `GH_DIST_APPLE_API_KEY_ID`, `GH_DIST_APPLE_API_ISSUER_ID` | `GH_DIST_CERT_SHA256`, `GH_DIST_MACOS_TEAM_ID` |
| `github-sign-windows` | `GH_DIST_WINDOWS_PFX_BASE64`, `GH_DIST_WINDOWS_PFX_PASSWORD` | `GH_DIST_CERT_SHA256`, `GH_DIST_WINDOWS_PFX_APPROVED_SHA` (explicit owner authorization for an exportable key at this SHA) |
| `github-sign-linux` | None | Source/acceptance variables only |
| `github-publish` | None; scoped `GITHUB_TOKEN` only | Source/acceptance variables, `GH_DIST_ANDROID_CERT_SHA256`, `GH_DIST_MACOS_CERT_SHA256`, `GH_DIST_MACOS_TEAM_ID`, `GH_DIST_WINDOWS_CERT_SHA256` |

Obtain these from the existing Android signing owner, Apple Developer team and
public-trust Windows certificate provider respectively. Do not create replacement
Android identities, self-signed Desktop production certificates, Debug-key
fallbacks or undocumented PFX exports. Prefer an HSM/provider if no exportable
key is approved; that provider integration needs a separate reviewed adapter.

Configure credentials privately through GitHub's secret UI or pipe an existing
file without printing it, for example:

```bash
base64 < /private/path/publisher.p12 | tr -d '\n' | \
  gh secret set GH_DIST_MACOS_CERTIFICATE_BASE64 --env github-sign-macos
```

The caller must separately approve the exact public certificate fingerprint.
Secret rotation requires updating the relevant protected pins and a **new**
candidate/build number; a frozen candidate is not re-signed. Only signing steps
receive private values. Tools suppress private arguments/errors; scoped temporary
files and keychains are retired on success, exceptions and handled termination.
Windows private keys use ephemeral memory, not a persistent certificate store.

## Scope limits and present status

Desktop still uses owner-scoped AES-GCM snapshot files with a local key, **not**
Android Keystore/iOS Keychain parity. Same-user processes can access that key.
Desktop rejoin credentials are process-memory-only: reconnect works while the
original processes live, but cold-start credential recovery is not promised.
The existing same-LAN-only/no-host-migration policy is unchanged. Publishing
Desktop requires explicit acceptance of these limits, not silently relaxed tests.

The repository's existing **A37: 0 PASS / 26 FAIL** iOS Strict Complete Protection
finding remains unresolved; no iOS artifact is published by this workflow and
nothing here waives that finding or authorizes Store delivery. Automated native
probes do not establish real cross-device play, accessibility or legal approval.

At implementation time no repository signing secrets or new protected signing
environments were configured, and no GitHub Release existed. Local/CI rehearsal
results must be matched to the exact source/run in the final implementation report.
Real credentials and exact-source physical/owner approvals are external blockers
to public signed distribution, not optional fallbacks.
