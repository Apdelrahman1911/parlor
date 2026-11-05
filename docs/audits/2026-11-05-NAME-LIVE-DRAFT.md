# Name confirmation must read the live draft

Related: [#182](https://github.com/Apdelrahman1911/parlor/issues/182) and
[#276](https://github.com/Apdelrahman1911/parlor/issues/276).
This is a confirmed shared UI defect, not the original native keyboard/HID cause.

## Actual regression and root cause

Shipping baseline `dad2959c` plus corrected fixture `c8a75d7b` executes two real
Compose Desktop methods: **2 assertion failures, 0 errors/skips**. Retain the
shipping Name field's `SemanticsActions.SetText` and enabled button's `OnClick`;
edit and activate in one UI turn, without intervening recomposition:

- Change `Table Before` to `Table After`: confirmation incorrectly submits
  `Table Before`.
- Clear the name: the old enabled callback still submits `Table Before`, rather
  than rejecting the invalid current draft without navigation.

The button captures `sanitized`, an immutable value computed during composition.
Field state can already contain the newer input when that callback executes.
Button enabled state is likewise not an authoritative action-time validator.
The original failed XML, source and logs remain in agent custody. An earlier
fixture import error prevented compilation; it was corrected before the actual
assertion reproduction and is not an application failure.

## Correction and pre-PR verification

Read `normalizedMultiplayerDisplayName(name)` inside the activation handler and
require `RoomInputPolicy.isValidDisplayName` before calling `onConfirm`. Reuse
the existing normalization/validation policy; do not change the draft, focus,
keyboard, navigation phase, input event stream or transport. Invalid current
input does not invoke the callback or prevent a subsequent valid user choice.
Existing shell phase guards still handle duplicate confirmed navigation.

At correction `ed062ddb`, both new methods and six affected existing methods
pass: **8 PASS, 0 errors/failures/skips**, app Detekt PASS. New regressions cover
English/Arabic/System and Host/Peer; existing confirmation tests cover all six
games, and English/Arabic post-Done name editing remains intact.

```sh
xvfb-run -a ./gradlew :composeApp:desktopTest \
  --tests '*NameInputLiveDraftTest*' \
  --tests '*MultiplayerNameConfirmationPointerTest*' \
  --dependency-verification=strict
```

Full fresh protected checks, native English/Arabic journeys, optimized Release
architectures and independent resulting-main admission are required after this
pre-PR evidence. No timeout increase, repeated input, dependency override,
assertion weakening or physical-device claim. The original #267 cancelled
native touch and #276 delayed native keyboard-service startup remain separate.
