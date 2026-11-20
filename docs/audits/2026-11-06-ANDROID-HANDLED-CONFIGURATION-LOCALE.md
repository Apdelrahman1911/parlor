# Android locale ownership across handled configuration changes

## Subsequent recurrence — callback-only correction is insufficient

The original Arabic rotation method fails again in PR336 run42314073678.
[Updated evidence and resource-authority correction](2026-11-07-ANDROID-LOCALE-RESOURCE-AUTHORITY.md)
supersede any inference of a permanent fix from the passing samples below.
#332 remains open; the retained historical outcomes are not relabeled.

## Earlier qualification — Android scope passes, whole main does not

PR333 passed all required producers and the optimized Apple Release join in
[42253730217](https://github.com/Apdelrahman1911/parlor/actions/runs/42253730217)
before normal protected merge as `3fde30d5`. Its distinct
[main42261837661](https://github.com/Apdelrahman1911/parlor/actions/runs/42261837661)
independently passes the actual EN/AR rotation journeys among33 API34 methods,
3 optimized API35 methods,12 process cases/24 native phases and the common
locale regressions on every Desktop host. PR receipt SHA256:
`67761e9862cb73e6c95e3dc8f270348f6058be06f6cf88fbc340b003f065b054`.

Main nevertheless fails the separate iOS Ghamza permission pre-tap observer;
see the [original native boundary](2026-11-06-IOS-PUBLIC-BUTTON-SNAPSHOT.md).
#332 stays open for its complete-main gate. The source/failure statements
below describe preparation and the original reproductions, not current success
for unrelated iOS, physical-device or earlier unproven timing causes.

## Confirmed defect and original evidence

[#332](https://github.com/Apdelrahman1911/parlor/issues/332) is a distinct live
configuration defect, not the late-Activity disposal fixed in #329. On standard
free Ubuntu/API34, [42246135518](https://github.com/Apdelrahman1911/parlor/actions/runs/42246135518)
at `c806de6b5a73a3b34a5492c2f7f2bc7c2fc0f28c`, tree
`524fc4bc8c687cf838cbab8fac6fde2f3d3a0166`, reproduces:

1. Persist Arabic, launch the actual shipping Activity/Koin graph, open Settings
   once and observe its exact Arabic heading.
2. Request one real opposite orientation. Assert it changed, the same Activity
   was not destroyed, and the persisted preference remains Arabic.
3. The unchanged exact Arabic-heading assertion fails:

```text
Handled configuration: english=true, arabic=false,
processLanguageMatches=false, activityDestroyed=false
```

Compose before, cached native, freshly cleared native and Compose after each
contain English heading1 / Arabic0, with one root and no pending measurement.
Process/Compose/Activity locales are English; the Activity is focused and alive.
The sequential failure-only capture takes53ms. These are actual wrongly
rendered labels, not merely a stale accessibility observation. Clearing that
observer cache does not correct them. The English method passes; the original
native result is **1 PASS / 1 ERROR**. Instrumentation shell exit0 does not
override its JUnit failure; the strict parser correctly rejects it.

Independent retained-log/Git-blob/artifact readback receipt SHA256:
`b0f7ef371f86fe7dbf3252311efd2e1b74add0a28557acd348d91bde98fb82d2`.
Original log: `94d2d12390c63cdec67d284f6f5e59b39624a1279484012ca8c97f322a5bc97f`.
Diagnostic artifact13517758515:
`c6411da8a776c1c205d4882a1bdc7ed2d5f20202e51510087646e1d6cedfaad7`;
cleanup13518322802:
`a81878c1cae62901fcc3bdfd1f7dee10295468cb7fe1a87e97e55054e5c7ed41`.
Owned descendants and ordinary output cleanup pass. Failed-runtime AVD is
**RETAINED**, not certified successfully retired. APK hashes in the result
are driver attestations, not independent binary readback.

## Causal boundary and correction

Android14's [ConfigurationController](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/core/java/android/app/ConfigurationController.java)
updates process locale defaults from app resources before dispatching component
configuration callbacks. Parlor's existing `PlatformAppLocale` effect acquired
its override only when requested defaults changed. Rotation leaves requested
Arabic equal, so the still-live effect did not reinstate it after Android's
reset. Compose resources read those process defaults. The framework source is
supporting reference evidence, not a trace of every native internal call.

The correction:

- Registers an application configuration callback for the exact locale-effect
  lifetime, unregistering before release. A real callback refreshes only the
  newest live scope; stale/disposed/equal-language predecessors cannot write.
- Explicit language reinstates its requested list. System mode follows the
  complete new platform list. Effect identity includes language **mode** because
  System and an explicit language can have equal current defaults but must
  react differently to the next system-language change.
- Preserves live predecessors and the new platform restoration baseline,
  including a system change to the same value as the installed override.
  Already-correct defaults receive no redundant write; read/write errors
  propagate before claiming refreshed ownership.

No shared Resources mutation, content remount/navigation reset, focus change,
polling, longer timeout, extra input, dependency override or unconditional
accessibility-cache clearing is introduced. The callback is UI-thread scoped,
with no background worker/service.

## Regressions and integration

- Retain seven existing exact-ownership regressions; add ten configuration
  reset, System/full-list, stale/disposed-owner, predecessor-restoration,
  no-redundant-write and error-propagation methods.
- Reuse the executed EN/AR single-rotation reproducer in the mandatory API34
  native suite. Keep the same heading assertions and10s/100ms bounds, now named
  constants. All31 preexisting methods remain; expected native total is33.
- Keep bounded failure-only public Compose/native counters for the process
  matrix. Cache clearing occurs **after failure**, never on a successful path
  or as a passing substitute. No arbitrary tree, player label, snapshot,
  credential or screenshot is exported; cancellation is preserved.
- Bind the actual production locale adapter/owner and new test/evidence files
  into process receipts. Portable guards preserve deadlines, privacy, actual
  Activity/one-rotation behavior and unchanged strict result parsing.

The earlier diagnostic workflow is **not** integrated into production. Local
review found its unnamed bounds and overly broad evidence catch; the integrated
version names the same bounds and catches the specific stale-tree/observer
exceptions without weakening Detekt.

```bash
./gradlew :shared:design-system:desktopTest --tests '*ScopedLocale*Test*' \
  :shared:design-system:compileDebugKotlinAndroid :shared:design-system:detekt \
  :composeApp:desktopTest --tests '*ProductionUiAccessibilityContractTest*' \
  :composeApp:compileDebugAndroidTestKotlinAndroid :composeApp:detekt \
  -Pparlor.androidRuntimeTestVariant=debug --dependency-verification=strict
python3 -B -m unittest scripts.release.tests.test_android_resume_process -v
```

## Qualification status and limits

The17 locale and16 app source/accessibility methods pass locally, as do39
portable controls. Android source/test frontend and focused Detekt qualification
are recorded separately from actual native execution. Fresh full protected-PR
and distinct resulting-main verification remain required before #332 closes:
API34/native33, unchanged optimized API35/native3, all12 process cases/24 original
phases, every other required producer/architecture and optimized Apple join.

Current main `d7d6f415`/[42227976735](https://github.com/Apdelrahman1911/parlor/actions/runs/42227976735)
is **not independently qualified**. Its Mafia/ar/Multiplayer verification failed
after15 successful native phases. The separate English preparation failure
also lacks a full original frame trace. This rotation reproduction does **not**
establish either historical cold-start sequence; they must not be relabeled
causally resolved simply because a later matrix passes.

The source-bound diagnostic process matrix
[42239226843 attempt2](https://github.com/Apdelrahman1911/parlor/actions/runs/42239226843/attempts/2)
already passed12 cases/24 original phases (receipt
`4813a5a01c2fe97a38f9794b0f23432d85892e23f5d1aa5731084aa37be6869b`).
It is retained, not unnecessarily replayed or described as whole-main success;
its subsequent configuration step never started because of a port preflight
failure. The separate executed rotation control above supplies the new finding.

Broader #253/#259, upstream iOS input, #258 supported-volume/security, native LAN
and owner-reserved physical acceptance stay open. No Store release is authorized.
