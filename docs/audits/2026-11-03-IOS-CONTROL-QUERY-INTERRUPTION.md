# iOS public-control query interruption — November 3, 2026

## Scope and original evidence

Phase 1 / [#276](https://github.com/Apdelrahman1911/parlor/issues/276).
This corrects a confirmed **test observation defect**, not a demonstrated
shipping input defect. The separate Host-event and Arabic typing failures
remain unresolved; do not close #267/#276 on this correction alone.

The original [41180128325](https://github.com/Apdelrahman1911/parlor/actions/runs/41180128325)
failed at application source `5029b3f48c2d012b34dd2748f809dc775276a622`:
English Word Impostor settlement, before its opening tap. Retained artifact
SHA256 `e5031382aee151fc65dbe551a58fa1bc75e1feda3fb0becbab9e0f8ce30aff20`.
The original native logs were recovered **offline**, not by replaying the app:

- Session log SHA256 `8c3bc2433cfd9243355203a794966a981aea9295d0b4c46a810294266b0288a0`.
- Application log SHA256 `272b2c244fcd55b5dacccc5bd83925b87e3fe87afb15f7d22fcb1c9183b17d0c`.

The original bounded result decoder was not weakened. Large native logs were
separately inspected through system zstd with a 256 MiB/file cap. No complete
system logarchive decoding or physical-device verification is claimed.

## Confirmed failure boundary

All times below are original October 31 UTC. App PID61316 / runner46429:

1. The second observation obtains the same positive on-screen frame as the
   first: `(24, 419.6666564941406, 354, 267.9999694824219)`.
2. At **07:48:11.346**, the subsequent `isEnabled` read starts another remote
   first-match query. This is another resolution, not a cached property read.
3. The app receives it at **11.346953**, and its main run loop starts traversing
   at **11.500674**. Traversal continues; no missing root is reported here.
4. At **15.788**, the outer ten-second predicate waiter expires. It interrupts
   the nested query at **15.792–15.793**, after4.446s. The exact native error is
   `com.apple.dt.XCTest.XCTFuture Code=1001: Interrupted by waiter.` XCTest's
   own subsequent retries are immediately interrupted by that expired waiter.
5. The helper records `enabled=false`. No successful enabled observation
   established that value. It must not be interpreted as Parlor disabling
   the card.
6. The original in-flight traversal finds the same matching card at
   **18.795482**, with Button traits8589934593 (no NotEnabled bit), and replies
   without query error at **19.283222**, after the original failure deadline.

This establishes interruption/fallback, not why traversal took7.94s. System
process notifications occur concurrently, but contention, crash reports and
Compose/UIKit attribution are not established causes from these logs.

## Correction and preserved acceptance

Obtain one public `XCUIElement.snapshot()` per settlement sample; extract frame
and enabled state from that same resolved snapshot. Eliminate the three
independent existence/frame/enabled resolutions. Retain only geometry/status
and numeric error code, never snapshot objects or their text/hierarchy.

A thrown/missing resolution breaks frame adjacency; disabled remains distinct
from error. Retain exact adjacent frame equality, geometry validation, native
hittability immediately before admission, the original ten-second waiter,
failure-only screenshot, failure assertion, and exactly one ordinary tap.
There are no repeated taps, longer waits, cached-frame fallback or selector
changes. New regression controls verify single resolution, error gaps and
valid disabled samples; existing motion/geometry/hittability controls remain.

## Separate typing evidence

Offline inspection of retained Arabic failure
[41502134475](https://github.com/Apdelrahman1911/parlor/actions/runs/41502134475)
shows successful typing synthesis completion followed by ten snapshots without
a String value. Its focused-descendant and application displayID0 messages also
occur in the **passing English journey** of that same run. Those differences
are therefore not sufficient causes or justification for a focus/selector
patch. The retained editor observer saw no mutator calls; its main-thread scope
and missing actual responder/liveness results limit that conclusion. No native
typing correction or acceptance is claimed here.

## Verification status

Local controls and fresh hosted runtime/required qualification must be recorded
on the associated PR. A passing run cannot retroactively qualify the original
failure, establish unrelated typing/touch causality, or certify a physical device.
