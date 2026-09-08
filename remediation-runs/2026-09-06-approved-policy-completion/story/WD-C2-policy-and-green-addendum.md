# WD-C2 — compatibility-policy and focused-green addendum

Author: `/root/factory_review`. Independent review owner:
`/root/release_fix_review`. This records evidence, not the author's approval of
their own fix. No application/test/resource/document changes were made while
the root's focused-green source freeze was active.

## Historical baseline versus final policy

`WD-C2-source-proof-and-decisions.md` is preserved as the original pre-red04
source investigation and proposed plan. Its **legacy exception**, statements
that compatibility guards would stay unchanged, and **nine-test** count
describe that earlier baseline. They are not claims about the final source.

The independent reviewer identified that an identity-less pre-clue save could
silently bind to revised story prose. Under the approved exact-identity
pre-release policy, root changed the loaded-case recovery boundary to reject
missing identity as well as mismatched identity. The raw legacy loader and
codec remain available for inspection; no identity is fabricated, no saved
bytes are automatically rewritten/deleted, and no snapshot/protocol version
change is introduced.

`identityLessPreClueSavesCannotSilentlyBindToInstalledStoryProse` is the tenth
new story test. It proves all eight real corrected-case/mode pre-clue states
are structurally valid with exact identity, then rejects the same states
without it. This avoids an unrelated malformed-fixture rejection making the
test pass. The root-owned recovery UI tests cover English/Arabic explanation,
Back retention, and explicit discard for mismatched and missing identity.

## Executed evidence personally checked

- `story-recovery-red-01`: compilation failed in the new UI fixture; not a
  defect reproduction.
- `story-recovery-red-02`: five chronology and three compatibility tests
  failed as expected on original bundles; the 48-combination repeated game
  trace passed. Its digest witness enumerated all four original bundles,
  verifying the archived original digest literals through production Kotlin.
  The UI fixture error in that cycle was not the intended UI defect witness.
- `legacy-identity-red-04`: one test executed, one expected assertion failure,
  zero errors/skips. Raw XML names all eight case/mode saves incorrectly
  accepted without identity. Source identity was unchanged during execution;
  stop/cleanup succeeded with no remaining owned workers or outputs.
- `story-recovery-green-01`: actual Whodunit execution produced **57 suites,
  323 tests, zero failures/errors/skips**, including all ten new story tests,
  four root-owned recovery UI tests, two existing identity tests, eight
  case-binding tests and eleven resume-reconstruction tests.
- Shared content's five suites/24 tests have clean XML in green01, but the
  task was **FROM-CACHE**, not newly executed. Root will run fresh combined
  verification with cache disabled; this addendum does not claim it ran yet.

`green-01-author-result-review.json` binds the source/diff and raw XML hashes,
test names, commands, timestamps, exit status, semantic resource changes and
cleanup receipts. The root's full source manifest was unchanged before/after:
`de7573ed1f4e1a19969f3063e135d234cd57ec8764c4af784cf4b3eaff5ef9b4`.
The author-scope manifest, including untracked tests/documentation, is:
`6b27c5c9036bbc65c9ccbff920f847aed3efa26febf71eb223033e6d062027ca`.

## Content identity and scope

JSON structural comparison verifies exactly **15 changed scalar paths** across
the four resources, including their four content-version bumps to 1.0.1.
No other object key, array membership/order, character/clue ID, role, motive,
player range, mode or round rule changed. The separate identity test now
computes a genuinely different patch version instead of hardcoding 1.0.1.

The result JSON records all four new literal canonical digests as a **Python
algorithm cross-check**, not emitted Kotlin runtime output. Production Kotlin
green tests independently prove that every identity changed and verify exact
identity save/load and LAN-offer matching. Their XML does not print the new
digest literals; no stronger provenance is claimed.

## Limits and next gate

Independent reviewer approval and fresh combined results are recorded
separately by their owners. Local deterministic tests are not physical LAN,
iOS/Android presentation, storage durability, process-rejoin, signed-release,
content-rights or full editorial proof. Unrelated witness-testimony questions
remain explicit; the rejected cross-variant Daniel toast comparison remains
rejected. These do not silently expand the four authorized repairs.

Root's green01 receipt records stop exit 0, completed task-owned cleanup, no
cleanup errors, no remaining generated outputs and no remaining owned
workers. This author ran no build, simulator, app/test worker, server, Git
history/index/branch operation, signing or Store operation.
