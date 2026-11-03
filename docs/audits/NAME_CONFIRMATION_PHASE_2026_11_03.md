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

## Hosted regression-fixture correction

[PR311 run41712585948](https://github.com/Apdelrahman1911/parlor/actions/runs/41712585948)
is unqualified: Windows's repeated-pointer method recorded two Host calls;
macOS's same method timed out waiting for the Failed state. The other three
methods passed on both hosts. Original artifact hashes:

- Windows `a7cbf316f2284f889d0f8e6b0fdcc135c52e60a9d3b7f03f919727a115102094`.
- macOS `34bfd725fa426604a633aba22e6224115de889824d94051f828ccdb618884c1d`.

The raw batch allowed rendering between its two presses while the synthetic
transport returned failure immediately. A fixed coordinate is not a retained
Name control: the next screen exposes intentional Retry/Leave actions. The
owner cannot reacquire a Failed opening without an explicit retry; the fixture
was testing an uncontrolled cross-screen schedule as well as Name confirmation.
Local traced runs did not reproduce the hosted event order, so they do not
prove which destination control received the original macOS tap.

The published CMP1.10.3 Skiko dispatcher explicitly advances the test clock
between injected events, even with `autoAdvance=false`. A local extra assertion
proved that attempted clock-only isolation was insufficient (acquisition already
started inside the batch); that unpublished experiment is preserved, not used
as passing evidence. No clock-only change is published.

Instead hold the synthetic host result pending until the original pointer
batch finishes. Assert Opening, no Retry control, and exactly one acquisition;
only then release the fixture failure and retain the exact Failed-state and
single-call assertions. This excludes the unrelated error-screen retry path
without delaying shipping UI or suppressing another Name activation. All four
method identities, both languages, three games, pointer events and five-second
limits stay unchanged. No shipping transport, retry, motion, timeout or
navigation change is added. The duplicate semantic regression still invokes
the original retained callback twice on the UI thread; its earlier red evidence
remains valid and preserved.

## Explicit limits

This is a shared callback/state-machine defect **after** Name confirmation.
It does not establish native VoiceOver reachability, explain #267's earlier
Host/Permission failures, or prove the native typing boundary in #276. Those
issues remain open. Fresh affected source-specific checks, hosted PR checks,
normal merge and resulting-main verification remain required. Physical-device
acceptance stays reserved for the owner's separate campaign.
