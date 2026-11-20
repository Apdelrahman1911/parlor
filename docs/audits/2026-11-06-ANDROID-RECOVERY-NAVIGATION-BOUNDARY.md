# Android recovery fixture: acknowledge navigation before staging storage

## Original failure and scope

PR330's distinct [main42208844307](https://github.com/Apdelrahman1911/parlor/actions/runs/42208844307)
failed on `081a6f681d5ad1ded4b90f53634bd20f7636b032`. Seven producers and the
optimized Apple Release join passed, but the mandatory Common/Android process
matrix did not. Eleven cases/22 phases passed. The final **Whodunit / Arabic /
Multiplayer / prepare** errored at `AndroidResumeProcessPhases.kt:83`, awaiting:

```text
حذف: استئناف لعبة من القاتل المحلية، وهي اللعبة المحفوظة 1 من 1.
AndroidRecoveryObservation(rootPresent=true, targetNodes=40,
  exactLabels=0, sameNodeActions=0, resolvedNodes=0)
Tests run: 1, Failures: 0, Errors: 1
```

This is **before deletion, force-stop or verification**, not evidence that a
removed session returned. The parser correctly rejects the JUnit error;
`INSTRUMENTATION_CODE: -1` alone is not success. Original artifact13501251820,
`desktop-android-verification`, is retained with independently checked SHA256
`ffb3d9fb0c7f4299d423088dfee00dee69ef042a608fae740ee0bd77e45dc549`.
The emulator booted with `swiftshader` and completed11 cases: no GPU/startup
failure was established. Failed evidence and owned cleanup remain retained.

## Confirmed ordering defect; historical causality limit

`AndroidPublicRecoveryUi.clickDescription` dispatches one native `ACTION_CLICK`.
Its successful return does not acknowledge a Compose frame. Both bottom tabs
exist on Home and Settings. The fixture clicked Settings, wrote external
synthetic saves/membership, then clicked Library without observing Settings.

`AppNavigator.selectTopLevel` changes snapshot state. `rememberAppHomeRecovery`
keys its storage effect by the composed route, refresh key and app visibility.
Settings→Home changes can coalesce before composition: the final key is still
Home, so its already-read empty inventory need not be scanned again. External
fixture writes do not send the application's save/exit refresh event.

The new `StartupMultiplayerRecoveryJourneyTest` cases execute the **actual App**
and its actual public semantic callbacks in English and Arabic. One UI turn
deterministically reproduces no intermediate Settings heading, one inventory
read and no new row despite a successfully staged legal test-game snapshot.
A separately committed Settings visit exposes it; staging only after the
heading also exposes both local and multiplayer recovery, without automatic
startup routing or LAN operations. In-memory fixtures isolate scheduling here;
they do not certify native encryption or process death.

The original native failure lacks a screenshot/role-qualified screen/storage
readback. Its exact frame sequence is **not proven** by this reproduction.
Wrong viewport/locale or another boundary was not conclusively excluded in
that run. Do not describe this as a proven production persistence/locale defect
or reopen #329 on the basis of a missing Arabic label alone.

## Correction and regression safeguards

- After the single Settings action, require its unique visible, enabled,
  target-package **heading with exact localized text**, before any fixture
  write. A navigation caption or matching description is not a heading.
- Keep the single Library action and the original exact saved-row assertion.
  The native observer retains its10s deadline/100ms read interval: no sleep,
  action replay, larger timeout, skipped assertion or production refresh hack.
- Four native role/text controls cover EN/AR headings, a clickable non-heading
  caption, wrong-language/partial text and description-only matches. Actual
  destination composition is required in all12 process cases.
- A portable source-order guard requires acknowledgement before each staged
  snapshot/credential write and binds the new controls into source receipts.
- Only if the original post-Library assertion fails, read fixed EN/AR public
  heading/row alternatives and preference/sibling-integrity booleans. Rethrow
  the failure, preserving cancellation. No arbitrary tree, screenshot, player,
  snapshot payload or credential is exported.

No shipping application, storage, locale, protocol, dependency, workflow,
Release optimization or assertion-parser implementation changes are made.

## Verification and acceptance

```bash
./gradlew :composeApp:desktopTest --tests '*StartupMultiplayerRecoveryJourneyTest*' \
  :composeApp:compileDebugAndroidTestKotlinAndroid :composeApp:detekt \
  -Pparlor.androidRuntimeTestVariant=debug --dependency-verification=strict
python3 -B -m unittest scripts.release.tests.test_android_resume_process -v
```

The final seven-method Desktop suite,34 portable controls, native test
compilation and application Detekt pass with JDK21/wrapper/strict verification.
Native compilation is not execution. Complete fresh hosted qualification
remains required. The full workflow must run31 API34
methods, the unchanged3 optimized API35 methods, all12 process cases/24 original
phases and every other required producer/Release join. Then review, normal
protected merge and distinct actual-main acceptance are required. A passing
subset or PR run does not replace a failed main run.

Related [#253](https://github.com/Apdelrahman1911/parlor/issues/253) and
[#259](https://github.com/Apdelrahman1911/parlor/issues/259) remain open. Latest
independently accepted whole-main remains `6028009f` /
[42193749434](https://github.com/Apdelrahman1911/parlor/actions/runs/42193749434).
Original iOS input causes, supported-volume Complete Protection, authenticated
LAN admission/rejoin and owner-reserved physical acceptance are separate gates.
