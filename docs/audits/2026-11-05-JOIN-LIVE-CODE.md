# Room-code confirmation must validate the live draft

Related: [#182](https://github.com/Apdelrahman1911/parlor/issues/182),
[#276](https://github.com/Apdelrahman1911/parlor/issues/276).
This is a reproduced shared action-validation defect, not native input causality.

## Actual failure boundary

Frozen PR322 source `6febf582` plus fixture `a9c3c9f5` executes two real Compose
Desktop methods: **one exact assertion failure, one pass, no errors/skips**.
Enter a valid code and retain the shipping enabled button's semantic `OnClick`.
In one UI turn, clear the real field through `SetText` and invoke that callback:
`onConfirm` receives an empty code instead of being withheld. The shell's
confirmation routes then leave code entry despite its invalid current input.
The original XML/source/logs remain preserved; this is not an observed physical
or XCTest gesture.

Unlike the earlier Name defect, Join already reads its mutable draft, not an old
normalized string. Its defect is relying only on composition-time button
`enabled` state without validating at activation. The passing second baseline
method confirms that changing to another valid code submits the current value.

## Correction and regression coverage

Read `code` once inside `onClick`; require
`RoomInputPolicy.isValidRoomCode(currentCode)` before `onConfirm(currentCode)`.
Keep existing normalization, excluded alphabet, ASCII/non-autocorrecting keyboard,
Done/focus behavior, navigation, admission/authorization and transport unchanged.
Invalid activation is ignored without consuming a later valid user choice.

Corrected `589d3b93` passes **six affected Desktop methods**, zero failures,
errors/skips, plus app Detekt. Two new methods cover English/Arabic/System,
empty/short current codes, subsequent valid confirmation and live valid edits.
Four existing methods preserve EN/AR code editing after Done and native keyboard
option requests at the shared API boundary; these are not UIKit execution.

```sh
xvfb-run -a ./gradlew :composeApp:desktopTest \
  --tests '*JoinInputLiveCodeTest*' \
  --tests '*PublicInputReopenTest.*room_code*' \
  --tests '*PublicInputKeyboardOptionsTest.*room_code*' \
  :composeApp:detekt --dependency-verification=strict
```

The first corrected local attempt is **FAILED**, not accepted: the kernel
OOM-kills its exact Gradle daemon14752. Single-worker/no-parallel resource limits
were already active. Reducing only the command-local JVM heap1536→1024MiB and
metaspace640→512MiB lets the unchanged corrected source finish successfully.
No unrelated process/data or repository/Release configuration is changed.

The failed PR322 source stays frozen while its producers finish. Fold this
reviewed correction into the next PR revision with all identified CI fixes;
do not publish an overlapping replacement PR. Fresh full
protected/source-specific checks, native journeys, all optimized architectures
and distinct resulting-main verification remain required. Original #267/#276,
Strict Complete Protection and owner-reserved physical acceptance remain open;
no retrying input, longer assertion, bypass, release or device claim.
