# Findings — tests / build / release (TB-001+)

Severity: P0 ship-blocker · P1 likely user-visible / security / false gate ·
P2 latent / matrix hole · P3 hygiene.

---

## TB-001 — Store identity `com.parlor.app` is mandatory and unlistable

**Severity:** P0
**Confidence:** High
**Evidence:**

- `composeApp/build.gradle.kts:190-194` `applicationId = "com.parlor.app"`
- `composeApp/build.gradle.kts:276-278` `verifyApplicationIdentities` fails
  if Store ID changes
- `iosApp/Configuration/Config.xcconfig:11` `BUNDLE_ID = com.parlor.app`
- `.github/workflows/production-verification.yml:162-163` asserts Debug
  `.debug` / Release `com.parlor.app`
- `config/release-policy.json:8-28` both platforms
  `store_identity_ownership.status = "blocked"`, reason
  `public_store_collision`
- `scripts/release/release_tool.py:181-182` hard-fail if identity ==
  `com.parlor.app` even when status is flipped to `verified`
- `scripts/release/workflow_contract.py:347-350` forbids marking that
  ID verified
- `config/candidate-manifest.schema.json:62-63` schema `const` is that ID

**Why it matters:** Every executable identity gate requires the colliding
ID. Every Store-mutation path refuses it. There is no in-repo ID that can
be uploaded. First production ship is blocked until the ID is changed
**and** every pin (Gradle, pbxproj, policy, schema, validate_*.sh,
workflow string compares, CI assertions) is changed together.

---

## TB-002 — Candidate and promotion workflows are live, not disabled

**Severity:** P0 (process / accidental-publish surface)
**Confidence:** High
**Evidence:**

- `.github/workflows/testing-candidate.yml:3-14` `workflow_dispatch` +
  `publish` boolean, default false
- same file `:517-549` `store_api.py google-internal --execute` when
  `PUBLISH=true`
- same file `:1003-1027` IPA upload when `PUBLISH=true`
- `.github/workflows/testing-external-promotion.yml` and
  `production-promotion.yml` likewise `workflow_dispatch` + `--execute`
- no `if: false`; `workflow_contract.py:14-20` requires these four files
- `scripts/release/tests/test_store_api.py:802-827` only proves execute
  mode dies on the collision, not that the workflow is disabled

**Why it matters:** Anyone with environment approval who also changed
identity pins could publish. Today mutation fails closed on TB-001. The
workflows are still the production publish path, not stubs. Rehearsal
still signs binaries when publish=false (candidate Android/iOS jobs).

---

## TB-003 — `productionDesktopCheck` does not compile the desktop app

**Severity:** P2
**Confidence:** High
**Evidence:**

- `build.gradle.kts:27-30` description: “every KMP desktop test plus the
  desktop application compile”
- `build.gradle.kts:193-195` only `dependsOn(tasks.named("desktopTest"))`
- no `compileKotlinDesktop` / `compileDesktopKotlin` / `desktopJar` /
  `packageRelease*` in any `.gradle.kts` aggregate
- `composeApp/build.gradle.kts:533-545` desktop nativeDistributions exist
  but are ungated

**Why it matters:** Linux CI can be green with a desktop `main` that does
not compile. Description overclaims the gate.

---

## TB-004 — Linux `allTests` is not iOS; Apple job is not `allTests`

**Severity:** P1
**Confidence:** High
**Evidence:**

- `.github/workflows/production-verification.yml:60` Linux runs `allTests`
- same file `:139` Apple runs only
  `productionIosSimulatorRuntimeTests productionAppleCheck`
- `scripts/release/workflow_contract.py:90-91` **fails** if the Apple
  step contains `./gradlew allTests`
- `productionAppleCheck` (`build.gradle.kts:121-129`) is Detekt +
  `linkReleaseFramework*` — comment in workflow: “linkage-only”
- only one `src/iosTest` file exists (`IosStorageSafetyTest.kt`)
- Gradle on arm64: `iosX64Test` disabled (observed this session)

**Why it matters:** The name `allTests` on Linux does not mean every
target ran. Native commonTest + the single iosTest run only on macOS.
`iosX64` runtime never runs on current runners.

---

## TB-005 — Zero Android instrumented tests; Xcode Testables empty

**Severity:** P1
**Confidence:** High
**Evidence:**

- glob `**/src/{androidInstrumentedTest,androidTest}/**/*.kt` → 0
- `build.gradle.kts:160-169` Detekt hook for those dirs is dead
- `iosApp/.../iosApp.xcscheme:30-31` `<Testables>` empty
- composeApp androidUnitTest = 3 files (storage + MainActivity startup)
- no `connectedAndroidTest` / Firebase Test Lab / `xcodebuild test` job

**Why it matters:** Permissions, backup rules, LAN multicast, compose
navigation on device, and process death are unproven. CI “Android
release” is JVM unit + unsigned package inspection.

---

## TB-006 — Real P2pKit join/broadcast tests are `@Ignore`

**Severity:** P1
**Confidence:** High
**Evidence:**

- `P2pKitRoomTransportLoopbackTest.kt:126-128`, `:158-159`, `:208-209`
- only live test (`host_*`) asserts advertise + 6-char code
- session tests use `:shared:networking-testing` `InMemoryRoomBus`
- `transport-p2p/build.gradle.kts:15-16` production always depends on
  p2pkit; missing LAN is not a compile failure

**Why it matters:** Host-authoritative multiplayer over P2pKit is a
product invariant. CI cannot see a broken join/send path.

---

## TB-007 — Release “tests” that only parse documentation

**Severity:** P2
**Confidence:** High
**Evidence:**

- `MultiplayerDocumentationContractTest.kt:30-33,84-91,96,124-125,223+`
  reads `docs/*.md`, `ARCHITECTURE.md`, `CLAUDE.md`, `README.md`
- `ProductionVerificationWorkflowContractTest.kt:309-327` requires
  `docs/IOS_SETUP.md` and `docs/RELEASE_RUNBOOK.md` to contain
  `-configuration Release` / `ARCHS=arm64`
- `AndroidReleaseLintContractTest.kt:47-75` requires
  `docs/ANDROID_LINT_TRIAGE.md` phrases `"reported 59 warnings"` /
  `"contains 29"`

**Why it matters:** `productionDesktopCheck` can fail on prose drift or
pass while runtime diverges. These are file-contract tests, not product
tests. They do invalidate cache via `desktopTest` inputs (good), but they
inflate “test” coverage.

---

## TB-008 — Dependency verification does not verify signatures

**Severity:** P2
**Confidence:** High
**Evidence:**

- `gradle/verification-metadata.xml:4-5`
  `<verify-metadata>true</verify-metadata>`
  `<verify-signatures>false</verify-signatures>`
- `settings.gradle.kts:27` extra JetBrains Space Compose repo
- P2pKit provenance test asserts SHA-256, not PGP

**Why it matters:** A colliding checksum substitution on a repo the
resolver trusts is the residual risk. Policy is checksum-only by design.

---

## TB-009 — `productionCheck` is host-independent and therefore not a ship gate

**Severity:** P1
**Confidence:** High
**Evidence:**

- `build.gradle.kts:132-141` = desktopTest + unsigned Android +
  release-automation + static + game-shell grep
- signed Android is `productionAndroidSigningCheck` (separate)
- Apple is `productionAppleCheck` / `productionIosSimulatorRuntimeTests`
  (macOS only)
- Linux CI adds `allTests` + AAB unzip; still no signing, no IPA

**Why it matters:** A green PR on Linux is routinely treated as
“productionCheck passed.” It does not prove Apple linkage, simulator
runtime, or store signing.

---

## TB-010 — Proguard/R8 keep surface is empty; minify is the only proof

**Severity:** P2
**Confidence:** High
**Evidence:**

- `composeApp/proguard-rules.pro:1-9` keepattributes only; comment says
  no blanket retention
- `composeApp/build.gradle.kts:227-232` minify + shrink on release
- CI runs `:composeApp:minifyReleaseWithR8` and bundles unsigned AAB
- no mapping-based keep test, no startup test of the minified APK

**Why it matters:** kotlinx.serialization / Koin / P2pKit reflective
edges rely on consumer rules. Shrink success ≠ runtime on a device.

---

## TB-011 — Dirty working tree changes signing inputs (not HEAD)

**Severity:** P2
**Confidence:** High
**Evidence:**

- `git status`: `composeApp/build.gradle.kts`,
  `iosApp/Configuration/Config.xcconfig`,
  `iosApp/iosApp.xcodeproj/project.pbxproj`,
  `scripts/release/tests/test_workflow_contract.py`
- composeApp diff: `MOBILE_RELEASE_ANDROID_*` aliases +
  `MOBILE_RELEASE_REQUIRE_SIGNING` configuration check
- pbxproj Release: `CODE_SIGN_*` / `DEVELOPMENT_TEAM` /
  `PROVISIONING_PROFILE_SPECIFIER` now `$(MOBILE_RELEASE_IOS_*)`
- HEAD candidate workflow still exports `PARLOR_ANDROID_*` (still first
  in the chain)
- `build_ios_candidate.sh:185-189` still command-line-overrides Manual
  Distribution

**Why it matters:** Local/kit signed builds can diverge from HEAD CI.
`MOBILE_RELEASE_REQUIRE_SIGNING=true` would break unsigned
`productionCheck` if ever exported in CI. Uncommitted iOS Release
mapping is not what origin/main verifies.

---

## TB-012 — Engine and Mafia commonTest source sets are empty

**Severity:** P3
**Confidence:** High
**Evidence:**

- `shared/engine/build.gradle.kts:13-18` declares commonTest deps; no
  `src/commonTest` files
- `game-modes/mafia/build.gradle.kts:26-32` same; 0 commonTest files,
  24 desktopTest files
- only engine tests: two desktop Konsist files

**Why it matters:** Mafia rules never execute as Native tests. Engine
purity is import-scan only. Not a missing gate so much as a lopsided
matrix (see TESTS.md).

---

## TB-013 — `verifyApplicationIdentities` will fight any collision fix

**Severity:** P0 (paired with TB-001)
**Confidence:** High
**Evidence:**

- `composeApp/build.gradle.kts:276-278` equality to literal
  `"com.parlor.app"`
- `composeApp/build.gradle.kts:388-390` merged manifest package check
- candidate YAML `:309`, `:400`, `:774`, `:840` string-compare vars to
  `com.parlor.app`
- `validate_android_artifact.sh:23` rejects any other application ID

**Why it matters:** The “do not fix com.parlor.app” instruction in
contributor prose is also compiled into failing the release gate if
someone *does* fix it. A real identity change is a coordinated
multi-file migration, not a one-line applicationId edit. Until that
migration, Store ship is impossible (TB-001).
