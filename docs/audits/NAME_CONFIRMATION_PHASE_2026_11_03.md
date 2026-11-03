# Name confirmation must retain its originating phase

## Confirmed shared application defect

Existing [#182](https://github.com/Apdelrahman1911/parlor/issues/182) is reopened,
not duplicated. At main `d821bf8c`, two activations of the actual Compose Name
confirmation callback before recomposition cause:

1. Host Name → Host on the first activation.
2. The retained callback reads the now-mutated step on the second activation,
   treats it as non-Host, and changes Host → Join Code.
3. The room-code screen appears; Host acquisition never begins.

The original exact screen-absence regression fails. No slower tap, debounce,
waiter relaxation, repeated repair input or transport change is needed.

## Correction and regression scope

Capture the originating Name phase during composition and admit confirmation
only while that phase is still current. Repeated/stale activation is a no-op;
a legitimate current confirmation still advances immediately.

Four Desktop methods cover actual shared Compose controls in English/Arabic
across Egyptian Dominoes, Ghamza and Word Impostor: single/repeated raw pointer
activation, duplicate retained semantic Host activation, and duplicate retained
semantic Join activation. Host acquisition must occur exactly once; Join must
remain at explicit room-code entry without initiating transport. Fixtures use
synthetic names and a failing host transport, never real LAN/private gameplay.
The originally failing method and corrected passing outcomes remain preserved
in `.git/agent-custody/overhaul-2026-11-03/name-confirmation-route-review/`.

## Explicit limits

This is a shared callback/state-machine defect **after** Name confirmation.
It does not establish native VoiceOver reachability, explain #267's earlier
Host/Permission failures, or prove the native typing boundary in #276. Those
issues remain open. Fresh affected source-specific checks, hosted PR checks,
normal merge and resulting-main verification remain required. Physical-device
acceptance stays reserved for the owner's separate campaign.
