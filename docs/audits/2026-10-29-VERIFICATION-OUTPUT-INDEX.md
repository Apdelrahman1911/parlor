# Indexed verification-output ownership checks

## Change and safeguards

`scripts/ci/verification_hygiene.py` previously scanned every tracked path for
each generated-output root. It now sorts the manifest once and uses two binary
searches per root: exact filename and slash-delimited descendant. Searching the
root alone is unsafe: a sibling such as `build-old` can precede `build/source.kt`.

Output discovery, tracked-source rejection, symlink checks, source/task identity,
fresh ownership, worker retirement, successful artifact custody and cleanup
preflight are unchanged. No additional outputs are admitted or removed. The
optimization adds no dependency, cache, paid runner or workflow change.

## Actual local comparison

Against unchanged `7c87a872` source, nine alternating same-host observations use
the same **40,525 tracked paths and 195 output roots**, including first samples.
Both implementations return identical roots. Median function wall time is
**0.942s → 0.153s**, approximately 84% less for this function, saving 0.790s per
call in this observation. Separate Python `tracemalloc` executions report peak
allocations of 36,326 → 360,743 bytes; this is not process RSS.

This is a small source-scanning improvement, **not an overall hosted-build
benchmark or an explanation/fix for hour-long native compilation**. Standard
free runners, all architectures and Release optimization remain required.

## Regression evidence and limits

Six new methods cover exact roots, misleading lexical siblings, hidden
descendants, input ordering/duplicates, Unicode/newline paths, and 400 seeded
manifests compared with the original linear algorithm. The existing 39 hygiene
controls remain; all 45 pass locally. In-memory negative controls remove exact
matching, remove the slash, or search from the root: targeted controls reject
each, respectively with four assertion failures, one expected unit error, and
three assertion failures. Production source is never mutated by these controls.

An initial private benchmark driver did not finalize its timing receipt because
it incorrectly required assertion failures rather than the valid-path rejection
unit error. Its retained failure is not relabeled; the corrected observation
persists each stage separately.

Fresh committed-source aggregate checks and any later protected integration /
automatic actual-main qualification remain mandatory. Main's original native
input failures, #267/#276, physical-device and Store gates are not cleared.
