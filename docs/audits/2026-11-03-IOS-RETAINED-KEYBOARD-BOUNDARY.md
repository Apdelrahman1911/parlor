# Retained iOS keyboard delivery boundary — November 3, 2026

## Scope

Phase 1 / [#267](https://github.com/Apdelrahman1911/parlor/issues/267) /
[#276](https://github.com/Apdelrahman1911/parlor/issues/276), still open.
This investigates retained **original failures**, without building the app,
replaying typing, changing focus/selectors or extending an assertion deadline.
Physical-device acceptance remains reserved for the owner.

## Original evidence and newly narrowed boundary

Offline Mandiant macOS-UnifiedLogs v0.7.0 interpretation of the unchanged
tracev3 files finds the following. These are provisional until the independent
Apple decoder confirms the source-bound traces.

| Original run | Runtime | UIKit receives 10 events | UIKit logs 10 insertions | First-event delay |
| --- | --- | --- | --- | --- |
| [41502134475](https://github.com/Apdelrahman1911/parlor/actions/runs/41502134475), Arabic | 26.2 | 18:56:56.643724–56.754387 | 18:57:11.349562–11.404165 | 14.705838 s |
| [40987141694](https://github.com/Apdelrahman1911/parlor/actions/runs/40987141694), Arabic | 18.5 | 02:11:41.333998–41.483612 | 02:12:08.862212–08.905702 | 27.528214 s |

Times are original UTC; the second row is October 30 and the first November 2.
Both show the same main-thread identifier across their events and insertions.
An unrelated haptic callback thread must not be attributed to text insertion.
The passing English journey in41502134475 has10 events/10 insertions and only
0.059903 s between its first event and first insertion.

The newly reviewed launchd lifecycle further narrows the stall:

- In41502134475, keyboard xpcproxy PID34648 spawns at18:56:53.682836;
  launchd reports keyboard-daemon running at18:57:09.711965, about16.029 s later.
- In40987141694, xpcproxy PID51221 spawns at02:11:39.616024;
  daemon running appears at02:12:07.721925, about28.106 s later.
- Both insertion bursts follow that delayed service startup and exceed the
  original ten-second exact-value deadline. Their original failures remain failures.

Original artifact SHA256s:
`f9b1d5967877903d20fc6af27b365cd501ff96352e711ab745382c026622da50`
and `ad0d3d46328aa757767afa238afcdd058d79b9e05adc69d68ed8b1215ac57fd4`.
The parser distribution was independently hash-checked:
`c1fea0f142850ac05e0faf66f3d69aca848793bc121e972238fb1927daf31004`.

## Conclusions and limits

This contradicts attributing these samples solely to absent XCTest synthesis
or application string sanitization: input reaches UIKit before failure. It
also explains why the failure-time native editor capture can contain no
mutator rows without establishing permanent event loss: that capture precedes
the later UIKit insertion logs.

**Still unproven:** why xpcproxy/daemon startup stalls, whether UIKit ultimately
calls the expected Compose responder, the final application string, and whether
this occurs on physical devices. Temporal order alone is not application root
cause or permission for a speculative keyboard warmup, timeout increase or
shipping focus workaround. Host-touch failures remain a separate boundary.

## Bounded native confirmation

The separate `ios-retained-keyboard-review.yml` uses one standard free
`macos-15` runner for at most10 minutes. It verifies immutable original
run/artifact identities and hashes, exports diagnostics with Apple's
`xcresulttool`, matches all276/343 original logarchive files, and decodes only
fixed keyboard-event/insertion/daemon-lifecycle categories with `/usr/bin/log`.
Only numeric/categorical facts and command custody are uploaded. No raw messages,
private input, broad logs, screenshots, app/Simulator execution or compiler work
are published or performed. Native execution remains pending when this report
is authored. This is not another replay of a passing diagnostic journey.

## Native preflight compatibility correction

[41701109207](https://github.com/Apdelrahman1911/parlor/actions/runs/41701109207)
failed before decoding either original: Apple's system Python lacks
`hashlib.file_digest` (introduced in 3.11) and rejects compact `+0000` in
`datetime.fromisoformat`. The receipt upload also failed because preflight
correctly prevented reader execution. No input or original artifact was modified.
The reader now hashes in bounded 1 MiB chunks and normalizes an already validated
UTC offset to `+00:00`. Two regressions preserve exact timestamps, bounded reads
and compatibility with the system interpreter. Fresh native confirmation,
not an unchanged retry, remains required.

The corrected-source [41703399589](https://github.com/Apdelrahman1911/parlor/actions/runs/41703399589)
passes all16 preflight tests; toolchain and `xcresulttool` help succeed. It rejects
Apple `log help show`'s EX_USAGE64/stderr response before archive intake. Its
closed receipt is retained and all three command children are reaped. Explicit
help alone now accepts0/64 plus the complete required usage vocabulary; actual
export and decode commands must still exit0. A regression rejects decode commands,
other exits, missing flags and unreaped/timed-out commands.
