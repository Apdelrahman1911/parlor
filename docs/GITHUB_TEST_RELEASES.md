# Public GitHub testing releases

The owner explicitly requested **downloadable experimental unsigned apps** on
September 18, 2026. This is a separate, opt-in **prerelease** channel, not an
unsigned fallback for [signed production distribution](GITHUB_DISTRIBUTION.md).
Store workflows and production signing/acceptance gates remain unchanged.

## What testers receive

| Platform | Download | Signing and installation |
|---|---|---|
| Android 8+ | `Parlor-Test-<version>-b<build>-android.apk` | Release-hardened, non-debuggable `me.parlor.android.test`, named **Parlor Test**; disposable test-key signature because Android cannot install an unsigned APK |
| macOS 11+, Apple Silicon | `…-macos-arm64.dmg` | Ad-hoc signed app; **no Developer ID or notarization** |
| macOS 11+, Intel | `…-macos-x64.dmg` | Ad-hoc signed app; **no Developer ID or notarization** |
| Windows x64 | `…-windows-x64.msi` | Unsigned installer; unknown-publisher/SmartScreen warnings are expected |
| Linux x64, Debian/Ubuntu | `…-linux-x64.deb` | No publisher signature; checksum and GitHub provenance verified |

All builds include their runtime. No separate Java installation is required.
Every release includes `SHA256SUMS` and `test-release-manifest.json` with the
exact source/tree, version/build, build run, platform hashes and inspection
results. Attestations prove build provenance, **not trusted publisher signing**.

### Installation and limits

1. Download from the repository's [Releases page](https://github.com/Apdelrahman1911/parlor/releases).
   Check `SHA256SUMS` before installation: `shasum -a 256 <file>` on macOS,
   `sha256sum <file>` on Linux, or `Get-FileHash <file> -Algorithm SHA256` in
   PowerShell. Compare the exact filename and hash, not merely the version label.
2. Android: open the APK and allow installation from the browser if prompted.
   The test app coexists with Store and local Debug. Each build has a different
   disposable key: **uninstall an older Parlor Test before changing test builds**.
   This removes that test app's saved sessions; it does not remove Store/Debug data.
3. macOS: choose the correct CPU download, open the DMG and copy Parlor to
   Applications. If blocked, only after checking provenance/hash, use
   **System Settings → Privacy & Security → Open Anyway** for this app. Do not
   disable Gatekeeper globally. Managed devices may disallow unsigned software.
4. Windows: run the MSI only if you trust this source and its verified hash.
   Security warnings are expected; do not disable Windows security globally.
5. Debian/Ubuntu: `sudo apt install ./Parlor-Test-*-linux-x64.deb` from a directory
   containing only the selected build. Other Linux package formats are not supplied.

Desktop test installers retain the **normal Parlor application and storage
locations**, including the MSI upgrade identity and Debian package `parlor`.
Do not overwrite an important existing installation. Use a separate OS account
for isolated test data; uninstall a prior same-version Desktop test package
before changing builds. Desktop snapshot keys are accessible to same-user
processes; cold-start rejoin credential recovery is not supported.

Use test data. Same-LAN play only, no Internet matchmaking or host migration.
These builds do not claim physical multiplayer, accessibility, legal/editorial,
or production-readiness acceptance. No iOS binary is included. The pre-existing
iOS **A37: 0 PASS / 26 FAIL** remains unresolved and is not waived by this channel.

## Build and publication workflow

`.github/workflows/github-test-release.yml` accepts maintainer dispatches on
`feat/last-light` or `main` only. It does not publish on pushes, PRs, untrusted
workflow events or automatically after a signed-candidate failure.
Changes to this workflow on `feat/last-light` run a registration-only job so
GitHub can discover the workflow before it exists on the default branch. That
job has no repository permissions or checkout and cannot build or publish;
the entire operational pipeline is skipped on pushes.

1. Dispatch `mode=build`. All five native jobs build/test/inspect, freeze and
   attest their outputs. Android uses `githubTest`, inheriting Release shrinking
   and privacy but **no Release/Debug signing config**. A private temporary key
   is generated, the final APK's identity/permissions/label/signature are checked,
   and it is installed **without `adb -t`** and launched on a disposable emulator.
   Key material is retired on success/failure/handled termination and never
   uploaded, printed or committed. Desktop reuses the existing complete native
   image/installer/runtime/notices verification, not a weaker alternate packager.
2. The seal job downloads every platform, verifies exact-source attestations and
   hashes, then freezes and attests the complete seven-file test bundle.
3. Run **full Production verification** at the same SHA. All six mandatory jobs
   must pass in one run; focused/historical results cannot substitute.
4. Dispatch `mode=publish`, the successful test `test_run`, and
   `acknowledge_test_limits=true` on that **same source**. Only this job has
   `contents: write`; no production signing secrets are used anywhere in this
   workflow. Explicit maintainer dispatch is testing publication authorization,
   not a fabricated production environment/physical acceptance receipt.
5. Publication validates the build run, all six verification jobs, every byte
   and all seven attestations. It creates an immutable
   `github-test-v<version>-b<build>-r<build-run>` tag at the exact source, uploads
   a draft, reads every asset back, then exposes a **prerelease, never Latest**.
   It does not rebuild, re-sign, accept a rehearsal/production bundle, move a
   conflicting tag, replace assets or silently change an existing visible release.

Example (replace the run ID with the actual successful build):

```bash
gh workflow run github-test-release.yml --ref feat/last-light -f mode=build
gh workflow run production-verification.yml --ref feat/last-light -f verification_scope=full
gh workflow run github-test-release.yml --ref feat/last-light \
  -f mode=publish -f test_run=BUILD_RUN_ID -f acknowledge_test_limits=true
```

Both GitHub channels share non-cancelling serialization. On partial build
failure, rerun **failed jobs only**; frozen platforms must not rebuild. Partial
uploads remain draft; publication retries accept identical existing bytes only.
Expired artifacts require a new test build/run, never replacement bytes under
the old tag. A new run allows repeated tests at the same app version without
claiming seamless signed upgrades. Marketing/build versions still come solely
from `config/parlor-version.xcconfig`.

## Verification and production separation

Adversarial tests cover private-field/identity/signature/checksum corruption,
missing/skipped jobs, wrong workflow/source, partial publication, tag conflicts,
stable-release masquerading, immutable retries and private-key cleanup. Static
workflow contracts reject implicit publication, broad permissions, secrets,
unreviewed runners, mutable actions and rebuilds in the publisher.

`github_distribution.validate_bundle()` still rejects test bundles. Production
requires its existing real publisher identities, protected approvals and signed
immutable candidates. Public testing is not evidence those gates passed.
Consult the exact-source final report and live Release readback for actual
publication status; an Actions artifact or green build alone is not a Release.
