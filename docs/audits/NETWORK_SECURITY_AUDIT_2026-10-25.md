# Multiplayer boundary and security audit — 2026-10-25

## Scope and evidence

Reviewed protocol 4.2 envelopes/validation, authenticated admission, session start,
command serialization/outcome reconciliation, snapshot ownership, independent peer
outboxes, terminal commits, foreground validation, retained routes, and the actual
join entrypoints for all six games. This is a focused workstream report, not proof
that every application behavior or physical-device scenario was tested.

Base: `39aacd31` (`origin/feat/last-light`); fixes through `d6a53248` on
`overhaul/network`. Source, not historical prose, determined behavior. Existing
issue inventory (open and closed titles) and full #251/#252 discussions were
reviewed. The report must be combined with the root integration commit/CI record;
branch-local evidence does not qualify a later checkout.

## Confirmed findings

### NET-01 — Selected game not bound during admission (P1, existing #251)

**Cause:** all four shipping join call sites used `join(code, name)`; no selected
game identity reached the adapter. A host already includes game ID/version in its
authenticated credential offer, but the peer did not compare them before staging
credentials and confirming admission. The later SessionStarting barrier rejects
wrong-game starts, but that is too late to prevent confusing/ineligible membership.

**Fix:** `JoinConfig.expectedGameProtocol` is a local API parameter, not a new wire
field. All four entrypoints supply their registry/game binding. The adapter compares
the authenticated offer before credential staging/AdmissionConfirmed, rejects wrong
or missing ID/version, closes the connection, and returns the existing localized
incompatibility result. Null remains available only to game-agnostic callers. Host
approval may occur before the peer receives identity; discovery text remains
untrusted. Rejection precedes membership confirmation, not necessarily the human
approval prompt. No protocol, credential format, host authority or readiness checks
were weakened. Rejoin still uses its protected game-bound credential; the start
barrier remains an independent final compatibility check.

**Regression:** actual adapter+CBOR with fake P2pKit session: different game,
different version, missing game all fail before AdmissionConfirmed/persistence;
matching identity succeeds. Session test independently verifies wrong-game offer
cannot call preparation or send Ready. Android↔iOS device reproduction of the
original report remains outstanding; do not close #251's physical acceptance gate
solely on these synthetic tests.

### SEC-01 — Byte limits do not bound recursive JSON parsing (P1)

**Cause:** Whodunit/Mafia action codecs parse a generic JSON tree to identify retired
actions before applying their finite schema. Serialization 1.11.0 can exhaust the
stack on nesting well within the 32-KiB action limit. The host catches recoverable
exceptions, not JVM Errors; the single-writer mailbox can terminate. Local snapshots
and peer projection decoding also deserve a resource guard. Reachability requires
an authenticated/admitted same-LAN peer; this is not an internet endpoint exploit.

**Evidence:** actual pinned JSON parser throws StackOverflowError for 4,096 levels
and an approximately 8-KiB synthetic input. The production parser microbenchmark
is documented separately in `JSON_NESTING_MICROBENCHMARK_2026-10-25.md`. Malformed
CBOR arrays, maps and tags at 64/4,096/16,000 levels were also probed through the
actual RoomMessageCodec: all 15 cases rejected as CborDecodingException, no stack
error. This is targeted malformed-envelope evidence, not exhaustive CBOR fuzzing.

**Fix:** reusable pure-core byte scanner bounds JSON to 64 containers before
recursive decoding. The schema is finite/shallow; 64 leaves ample envelope margin.
The scanner is iterative/allocation-free on success, ignores braces in quoted and
escaped text, and preserves strict UTF-8/schema/canonical checks. No dependency
upgrade or Error-catching workaround. Guard placements: original-three game codecs,
Whodunit/Mafia public/private peer snapshots, credential store; coordinated companion
changes cover new-three WireFormats and the generic snapshot store.

**Regression:** maximum/over-limit depth, deeply nested below-byte-limit input,
escaped quote/backslash/brackets, Arabic/emoji, invalid closing-depth offset; all
original-three action/snapshot boundaries; credential store remains usable after
corruption. An actual shared host coordinator plus Whodunit decoder test sends the
malformed command then a valid command, requiring InvalidAction then Applied and
one authoritative revision. Public issue publication should follow coordinated fix
qualification, not precede mitigation.

## Architecture and resource observations

- **Identity:** P2pKit adapter stamps every peer actor from its authenticated
  connection; unadmitted traffic never enters gameplay. Session checks pin exact
  game/version/session/epoch. Room codes alone are not authorization.
- **Authority:** one host mailbox serializes reducers, host actions, lifecycle
  mutations and publications. Start offer/Ready/commit/ack is idempotent. Peers
  install host snapshots only; they cannot mutate optimistically.
- **Confidentiality:** snapshots are individually addressed public+own-private
  projections. ShadowSessionController denies other-player private access and
  host viewing. UI must consume its complete private projection, not combine
  independently collected public/private revisions.
- **Duplicates and recovery:** per-actor sequences plus 256 cached outcomes bound
  deduplication memory. Peer has one outstanding action; ambiguous writes query
  outcome rather than replaying non-idempotent actions. Revisions/message IDs reject
  stale snapshots. Seen-host ledger is bounded to 2,048 IDs.
- **Backpressure:** host mailbox 8; transport host queue 16×40 KiB; peer queue
  8×272 KiB. Per-peer outbox holds 32 control frames plus conflated snapshot/heartbeat
  and one in-flight frame (827,392-byte conservative payload bound per lane,
  approximately 13.414 MiB for 17 peers). These are upper-bound accounting values,
  not measured resident RAM. Outboxes use coroutines, not one blocking thread per
  client; slow delivery is isolated. Snapshot conflation is appropriate for state,
  not acknowledgments.
- **Abuse limits:** inbound burst 32/sustained16 frames/sec and three violations;
  tracked sessions21, admission identities128, pending requests17. Game parsing is
  now additionally depth-bounded. These do not prove battery/thermal acceptability.
- **Lifecycle:** foreground liveness requires authenticated probes; known loss,
  retired sockets, explicit Leave and original expiry remain distinct. #252's
  physical/background gates and #6's skipped real-LAN scenarios remain open.
- **Maintenance:** generic new-game binding and old dedicated game flows coexist.
  Both needed the join fix; eventually removing duplicate flow implementations is
  a maintenance improvement, not justification to bypass game-specific tests.

## Verification and limitations

Verified on JDK21/Gradle8.13, strict dependency verification, frozen `aefa85ac`:
core18, networking34, session156, focused transport40: **248 passing, no skips**.
The initial test compilation error referenced a private test constant; fixed using
the existing fixture storage key, without changing production visibility.

The subsequent original-game/app/Detekt invocation was deliberately stopped
before execution completed, while resolving HTTPS dependencies, to correct a new
test fixture's too-short command ID. `d6a53248` fixes the ID and asserts both
actual decoder invocation and the exact recoverable failure class. Its game tests,
app compilation and Detekt still require root integration qualification; do not
infer PASS from their existence or the earlier 248 tests.
No real device, iOS runtime, physical LAN, battery, CPU-temperature, Android Doze,
incoming-call, force-quit, or extended-host test is represented by these counts.
The standalone parser measurements are JVM synthetic microbenchmarks, not mobile
host/RPC benchmarks. Full integration CI, Android build and macOS qualification
remain root-coordinated release requirements.
