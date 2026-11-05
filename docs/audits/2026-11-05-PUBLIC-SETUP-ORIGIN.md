# Public setup callbacks must belong to their originating phase

Related: [#182](https://github.com/Apdelrahman1911/parlor/issues/182),
[#267](https://github.com/Apdelrahman1911/parlor/issues/267), and
[#276](https://github.com/Apdelrahman1911/parlor/issues/276).

## Reproduced application defect

At independently verified main `0a2cbd6b3965ad5b18d6e99948f63f50a8e6f871`,
three new actual Compose Desktop regressions fail, without compilation errors
or skips. They retain the shipping picker's `SemanticsActions.OnClick` callbacks:

1. Host followed by the previously captured Join callback selects Join instead.
2. Join followed by the previously captured Host callback selects Host instead.
3. After entering a name, invoking the old picker callbacks removes the Name
   composition and recreates it with an empty draft.

The first two failures are exact destination assertions; the third observes
empty `EditableText` where the complete synthetic `Table Test` was required.
This is retained-callback execution, **not** a reproduced physical double tap,
VoiceOver sequence, original XCTest failure, or permanent native character loss.
The original source, failed XML, and logs remain in agent custody.

## Root cause and correction

Picker callbacks assign the next screen unconditionally. A callback can outlive
its originating Setup composition, so a later activation can overwrite an
already selected route. The Name field's composition-owned draft is then lost.
PR311 already guards the generic Name confirmation; that guard does not protect
these earlier picker callbacks.

Only admit a picker transition while its originating Setup phase is current.
Apply the same rule to Host/Join in the generic multiplayer shell and the
Whodunit, Mafia, and Last Light bindings; local-mode choices in those three
bindings have the same origin check. The first activation wins immediately,
including before recomposition. No debounce, delay, gesture change, text
normalization, extra network call, or new navigation layer is introduced.

New regressions cover all six games under English, Arabic, and System language,
both first-selection orders, and retained callbacks after exact name input.
Available local-mode callbacks are also rejected after multiplayer selection.
Transport and case-content tripwires prevent accidental networking/game entry.
Existing short, cancelled, scrolling, repeated, recreated-launch, full-input,
and Name-confirmation checks remain unchanged.

## Adapter integration correction

The first PR321 workflow [41978547098](https://github.com/Apdelrahman1911/parlor/actions/runs/41978547098)
fails before KMP/native execution: portable readiness discovers 454 methods,
with two errors in `CopyControls`. `pin01_copy.py` still requires the previous
unguarded Host/Join anchors, so it correctly rejects the changed source. This
is a diagnostic-copy integration error, not a new native input failure.

The renderer now admits exactly one complete known callback revision: legacy
unguarded or current guarded, never mixed, missing, duplicated or ambiguous
known anchors. Instrumented guarded callbacks keep observation **inside** the
origin check. Source digest checks, once-only transformations, the observer,
privacy boundaries and original failure assertions are unchanged. Three new
controls cover reversible guarded transformation, legacy assignments, and
rejected invalid revisions. Both legacy renderings are byte-identical to the
renderer on the independently verified baseline; this is transformation
evidence, not native adapter-neutrality acceptance.

Final corrected portable readiness: **457 methods, no failures/errors**, with
the six established Apple-only skips on Linux. The macOS qualification must
execute those controls without skips. The failed run cannot qualify the PR;
a fresh corrected-source workflow and resulting-main checks remain required.

## Verification boundaries

Run focused verification with JDK21 and the repository wrapper:

```sh
xvfb-run -a ./gradlew :composeApp:desktopTest \
  --tests '*MultiplayerSetupPointerTest*' \
  --tests '*MultiplayerNameConfirmationPointerTest*' \
  --dependency-verification=strict
```

Final-source affected analysis and full protected PR checks are mandatory,
including unchanged native English/Arabic journeys and optimized Apple Release
architectures. A PR pass does not substitute for independent resulting-main
verification. Native input/device acceptance is not certified by Desktop tests.

The original #267 touch was cancelled by native HID delivery. Original #276
Arabic text eventually arrived after native keyboard-service startup exceeded
the unchanged assertion deadline. **This correction does not resolve either
original native boundary.** Those Issues and physical verification remain open;
no private-framework workaround, replay, longer timeout, or protection waiver.
