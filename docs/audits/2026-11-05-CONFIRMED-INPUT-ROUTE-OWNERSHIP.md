# Confirmed input must belong to its originating route

Related: [#182](https://github.com/Apdelrahman1911/parlor/issues/182).
This correction does not explain the original Simulator HID cancellation or
keyboard-service startup delay tracked by #267/#276.

## Application boundary

At merged main `40b02d74`, a retained Name or room-code button callback can run
after its first successful activation changes the shell's phase. A late editor
callback can still update the outgoing field's draft. The shared input components
correctly validate the *live* draft, but several shell callbacks accept it again
without checking which route owns the activation.

Actual Desktop regressions invoke the shipping field/button semantics in one UI
turn, then observe the real session owner's transport handoff. On the initial
executable baseline, five room-code assertions receive the replacement code
instead of the first confirmed code; Mafia and Last Light also hand off the
replacement peer name. Three generic peer-name controls already pass because
their existing Name phase guard rejects the second activation.

The remaining two Whodunit and three newly added host methods execute on a
standard free Ubuntu runner in
[42072796151](https://github.com/Apdelrahman1911/parlor/actions/runs/42072796151),
frozen baseline `408f3b6e`. Their terminal results/readback are pending. Earlier
incomplete Whodunit DI fixtures and exact-PID local kernel OOMs are preserved;
they are not application assertion failures or passing tests.

## Correction

[`d5c43a3d`](https://github.com/Apdelrahman1911/parlor/commit/d5c43a3d726e8b0d6faadd4ce6a2028f2fac7ef7)
guards Host/Peer Name confirmation in Whodunit, Mafia and Last Light, and guards
room-code confirmation in all six games. Inputs and destinations change only
while the originating Name/JoinCode phase is still current. Generic Name guards
remain unchanged.

This is an ownership check, not a timer, global one-shot latch, debounce, retry,
or input replacement. Before the first accepted activation, the latest valid
draft remains authoritative, preserving PR322's action-time validation. No
transport, permission, protocol, dependency, native input, timeout or security
policy changes.

## Verification and limits

```sh
xvfb-run -a ./gradlew :composeApp:desktopTest \
  --tests '*RetainedNameConfirmationIdentityTest*' \
  --dependency-verification=strict
```

Fifteen permanent methods cover EN/AR/System, all six peer/code paths and the
three bespoke host paths, including Whodunit's real case/mode setup. Fixtures use
synthetic public inputs, never open LAN rooms, and cancel/join their owned scopes
and close dispatchers. Eleven inventory controls pass locally. Corrected-source
runtime/Detekt, all protected PR checks and independent resulting-main acceptance
remain required and pending. Neither shared callback execution nor green CI
would establish physical VoiceOver behavior or the original native causes.
