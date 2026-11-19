# iOS input: distinct failure boundaries — November 3, 2026

Issues [#267](https://github.com/Apdelrahman1911/parlor/issues/267) and
[#276](https://github.com/Apdelrahman1911/parlor/issues/276) remain **open**.
Do not describe all retained failures as lost text, a broken button, or a
Compose focus defect. The observed boundaries differ.

## Confirmed and corrected application/observer defects

- Original **41180128325** fails English Word Impostor settlement **before
  its opening tap**. Separate XCTest property queries re-resolve the remote
  element; an interrupted query is not evidence of native disabled state.
  PR309 samples frame/enabled from one fresh snapshot, retaining exact
  adjacency, native hittability, the original ten-second deadline and one tap.
- **#182** reproduces a different, post-Name application defect: invoking
  the retained Host confirmation twice before recomposition changes
  `HostName → Host → JoinCode`. PR311 captures the originating Name phase and
  rejects stale activations. EN/AR raw-pointer and duplicate Host/Join
  semantic controls pass across the three generic multiplayer bindings.
  This does not explain the earlier pre-Name Host cancellation.
- PR311 also corrects the regression fixture's **Opening/callback-entry
  observer race**. Opening is published before background transport entry;
  an explicit checkpoint after its atomic counter establishes the assertion's
  real boundary without a new deadline, gesture, or shipping behavior.

[Full PR41726503501](https://github.com/Apdelrahman1911/parlor/actions/runs/41726503501)
independently passes all eight producers and the optimized Apple Release join:
29 Swift,763 KMP Simulator and454 readiness methods, zero native/readiness
skips. Complete receipt SHA256
`c012804770c5da031a6faa077e59146ea871d43ba036dc4f0ec7de8007bb98c2`.
Protected PR311 merged as `bda97cde2aaa923b0ecf1adf2e3316c05c059baf`,
qualified tree `25ecb66f1a147516785790f32bf548222a53209f`.
[Distinct actual-main41737820087](https://github.com/Apdelrahman1911/parlor/actions/runs/41737820087)
subsequently reaches terminal SUCCESS with independently intaken eight
producers and the optimized Release join at that exact source/tree.
Complete actual-main receipt SHA256
`a90f3a88c6da443cfa9b2fffab883126996f6cb93a5062f78d7513b3e492163e`.
Its29 Swift/763 KMP Simulator/454 readiness passes have no native/readiness
skips. This fresh acceptance closes scoped CI #308/#310, **not the original
Host/typing failures or Phase1**; it does not prove adapter neutrality or
physical-device keyboard/VoiceOver acceptance.

## Original Host: confirmed native HID cancellation

Apple-native archive-only
[41736034650](https://github.com/Apdelrahman1911/parlor/actions/runs/41736034650)
confirms original41135053853/artifact13018521901 and all336 logarchive files.
Original ZIP SHA256
`3102267620e331fbaf0f0bde39b6a4943cde2550e0b5f2a010d87b7ae39998ad`.

The successful preceding catalog tap initializes one virtual-digitizer state,
releases its contact normally, and has no discarded events/soft cancellation.
The failed Host tap initializes two states for the same virtual digitizer,
discards seven path events, records no contact release, then cancels the
still-touching path. Both original UIWindow deliveries remain established.
XCTest `TouchEventsCompleted` and an `up:1` statistic do **not** prove a normal
UIKit release or application activation. The qualified existing cancelled-press
regression correctly retains Setup and performs no networking.

Independent receipt SHA256
`abb605ef4416368fa9c242a4b969b63948e8a8d568028bab73264c83660c974d`.
Exact UIKit/Compose callback state, internal registration synchronization and
original gray shading remain unproven. Never activate cancelled gestures.

## Original Arabic typing: delayed, not permanently lost

Apple-native41704552868 confirms both original iOS18.5/26.2 failures received
all10 synthetic events and ultimately inserted all10 characters, after
28.106s/16.029s keyboard-service startup respectively. Independently viewed
final original recordings show the complete text and enabled Compose
confirmation without retyping. Both original ten-second assertions still fail.

UIKit keyboard `didShow` precedes service readiness in retained26.2 evidence;
waiting for visibility alone cannot establish processing readiness. The
internal startup stall and late accessibility-value resolution are unresolved.
The late Apple tutorial is not proof it caused the preceding stall. Source
review does not establish a sanitizer, draft-state, or focus fault.

## Decisions and remaining acceptance

No further observer expansion, warm-up/repair input, blind rerun, longer
assertion, dependency override or speculative gesture/editor replacement is
selected from these findings. Fresh fixed-source native qualification remains
mandatory; PR passes are never substituted for independent actual-main results.
Original archives, unsuccessful readers and their corrections stay preserved.
Full adapter/60-journey neutrality and causal acceptance remain outstanding.
Physical keyboard/VoiceOver and device gameplay acceptance remain exclusively
in the owner's Phase4 campaign; no physical, security or Store claim is made.
