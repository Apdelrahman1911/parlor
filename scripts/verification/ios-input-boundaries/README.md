# PIN01 — copy-only public input boundaries

Investigation tooling for [#267](https://github.com/Apdelrahman1911/parlor/issues/267).
**Not a shipping fix, native neutrality certification, or standalone device runner.**
The original lost Host event occurred on iOS26.2; later iOS18.5 passes do not
establish its cause. Keep the original failed source/event/recording evidence.

## What is implemented

`pin01_copy.render` validates four exact source digests and unambiguous anchors,
then returns an additive transformation plan. It performs **no filesystem writes,
build, launch, device allocation, permission action or process cleanup**. Use only
with a separately manifest-owned source copy; never apply the plan to shipping
source or reuse an old diagnostic approval. Both native arms are explicit:

- Compose-only: unchanged clickable/semantics/scroll behavior, read-only Initial
  and Final pointer passes, actual Host/Join callback receipt and public shell
  composition stages. No consumption, synthesized action or automatic retry.
- Compose + native: additionally attach a public UIKit gesture recognizer to the
  original Compose child. It cannot prevent/be prevented, delays/cancels no
  touches, permits simultaneous recognition and only enters `.failed`.
  **Those settings do not prove arbitration/timing neutrality.**

The process-local preallocated512-row numeric buffer never evicts old evidence.
Overflow/invalid samples reject qualification. JSON is created only on an explicit
post-action app-host capture (`PIN01InputSnapshot`), not in event callbacks,
accessibility labels or production logs. No names, roles, game state, room/player
identities, error text, view hierarchy, keychain/network values or touch identities
are recorded. Touch capture is armed only on the public picker and closes
before permission/name input or gameplay; later public transitions record enums
only. Native positions are window points; Compose positions are local.
Do not subtract them or infer matching input from a coincident coordinate.

## Controls and use

```bash
python3 -B -m unittest discover -s scripts/verification/ios-input-boundaries -v
python3 -B -m unittest discover -s scripts/verification/ios-readiness -v
```

Routine readiness discovery reuses these controls; on macOS the standalone Swift
observer also typechecks against the actual Simulator SDK. This is compilation,
not app linkage/input delivery. Linux cannot run that Apple-only control.

Before native investigation, independently bind the actual complete source,
templates, copied diff, original driver, built product, runtime/device, single
synthesized event, result/log artifacts and owned cleanup. Execute original,
Compose-only and Compose+native arms using the **same**60 declared EN/AR,
Host/Join, three-game short/cancel/scroll/repeat/recreated-launch journeys.
Preserve original event duration, selectors, deadlines, permission behavior,
network tripwire and typed-name equality predicates. Do not slow/replay input,
retry a failure automatically, grant permissions or start a room to get green.

`pin01_receipts.decode` rejects unknown/private/duplicate fields, nonnumeric or
nonfinite rows, invalid enums/geometry/time, clock reversal and incomplete buffers.
`public_boundaries` reports presence/count only: missing evidence does **not**
identify the lost boundary. `compare_neutrality` requires the complete three-arm
public outcome matrix, exact frames/runtime and unchanged outcomes. Its synthetic
controls and equal outcomes cannot certify native timing, source custody or the
original iOS26.2 cause. An external app-host capture adapter and real native
three-arm execution remain required; the normal production UITest driver is
intentionally unchanged.

## Official API basis

[Apple’s UIGestureRecognizer documentation](https://developer.apple.com/documentation/uikit/uigesturerecognizer)
describes hit-tested view/subview association, early recognizer delivery and the
possible→failed state machine. Its `cancelsTouchesInView`, `delaysTouchesBegan`,
`delaysTouchesEnded`, `canPrevent` and `canBePrevented` contracts motivate the
explicit policies. These APIs support investigation, not proof that adding a
recognizer leaves Compose/native scheduling unaffected. No window-event
swizzling, private recognizer introspection or unrelated background mode is used.
