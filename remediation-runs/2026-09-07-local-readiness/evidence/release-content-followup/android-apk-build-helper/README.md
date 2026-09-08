# Disposable-signed Android APK build/retention

Campaign-only helper, **not yet executed**. Root coordinates the only build lane;
independent review and the real owned-child process probe precede execution.
This changes no source, identity, Gradle configuration, verification metadata,
release workflow, public trust store, or Store state.

## Root invocation

Use absolute helper/stage paths. The stage must be a fresh direct sibling of
this helper directory. `--cycle` must equal the outer cycle name.

```bash
campaign="$PWD/remediation-runs/2026-09-07-local-readiness"
followup="$campaign/evidence/release-content-followup"
/usr/bin/python3 -B "$campaign/run_gradle_cycle.py" android-arm64-apks-01 \
  --command /usr/bin/python3 -B \
  "$followup/android-apk-build-helper/build_and_retain_apks.py" \
  --cycle android-arm64-apks-01 \
  --artifact-dir "$followup/android-apk-stage-01"
```

The helper refuses standalone operation, a mismatched outer receipt/lock/TMPDIR,
an existing stage, or existing app build output. The outer runner already owns
the complete dirty-source manifest and every initially absent build root.

## Build and verification contract

- Read the actual `composeApp/build.gradle.kts` release-signing environment
  precedence and `testBuildType = "release"`. Reuse AGP's existing managed-device
  signing injection from `scripts/android/run_release_managed_device_smoke.sh`.
- Create a new two-day RSA/PKCS12 fixture with JDK21 keytool. Passwords are fresh,
  env-only (`-storepass:env`/`-keypass:env`), never command arguments. The same
  disposable key is supplied to the app config and AGP injection through
  `ORG_GRADLE_PROJECT_android.injected.signing.*` environment variables.
- Build **only** `:composeApp:assembleRelease` and
  `:composeApp:assembleReleaseAndroidTest`: strict verification, one worker,
  no parallel/configuration/build cache or persistent daemon, in-process Kotlin.
  Heap defaults to3GiB; the existing root lane also permits an explicit6GiB cap.
- In `finally`, immediately `./gradlew --stop`; an interrupted active build is
  first stopped using only its attested worker tokens. Delete the disposable
  private key as soon as the stopped build no longer needs it.
- Discover exactly two single APKs from real AGP `output-metadata.json` records.
  Reject unexpected variants/IDs, splits, traversal, symlinks, extra or empty
  APKs. Do not guess output filenames. Unknown metadata format fails closed.
- Run installed build-tools36 `apksigner verify --verbose --print-certs` for
  each APK, require real exit0, exactly one signer, and the same SHA-256 as the
  exported disposable certificate. Stream/hash the two APKs into the stage.

## Evidence and cleanup

The stage contains **only `release.apk`, `release-androidTest.apk`, and
`receipt.json`** on success. Compact command logs live in the outer cycle's
`android-apk-helper-logs/`. No key, certificate, dependency cache, or build
intermediate is retained in the stage. The receipt binds source/diff manifest,
outer cycle, controls, tools, hashes, signer, metadata, and owned cleanup.

The helper removes only its UID/inode/nonce-attested signing scratch and stops
only its worker registry. The outer runner then stops Gradle again, preserves
reports, and removes its exact module/build-logic build roots. Ownership or
shutdown uncertainty is a recorded failure, not permission for broad deletion.
Do not consume staged APKs unless **both helper and outer receipts PASS** and
the full source manifest remained unchanged. Remove the stage's APKs only after
the following fresh-owned ARM64 AVD test and final inspection have finished.

These are synthetic-installation artifacts with the unchanged Release identity;
never install them onto a personal phone/profile. They prove neither Store
signing nor runtime success. The separate ARM64 runner must attest a newly
created emulator before its destructive settings fixtures. Linux/KVM,
physical-device LAN, accessibility, legal and Store-owner gates stay separate.

## Guard tests

```bash
/usr/bin/python3 -B -m unittest discover \
  -s "$followup/android-apk-build-helper" -p 'test_*.py' -v
```

The17 pure tests exercise metadata and certificate-output guards with temporary
synthetic bytes; they do not create a key, run Java/Gradle/apksigner, or start a
device/server. Root must record their actual execution separately. AST parsing
is not a test PASS. Native ownership was reviewed separately and still requires
the existing actual owned-child probe before this helper runs.
