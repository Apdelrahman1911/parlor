# P2pKit RPC reassessment — October 31, 2026

## Decision and verified scope

Retain Parlor's reviewed `p2p-core`/`p2p-transport-lan` **0.7.0-rc3** and existing
host-authoritative messaging. RPC is published, not unavailable: upstream PR524
is merged, and Maven Central exposes `p2p-rpc:0.8.0-rc8`. This is a static source
and publisher-metadata evaluation, **not linkage, runtime or performance proof**.
No production dependency or executable application source was changed for this reassessment.

The newly reviewed 63,328-byte sources JAR matches publisher metadata SHA256
`60c2eecd2c1f943d0732744416121306d39a858c513e8fcb06d80bd35b364918`.
Published metadata advertises Android JVM, JVM and all three required iOS targets.
Previous October 30 target/payload custody already covers their publication;
it must not be replayed or described as application compatibility testing.
The root graph requests core/LAN 0.8.0-rc8, serialization 1.11.0 and Kotlin
stdlib 2.4.20, versus Parlor's reviewed Kotlin 2.4.10. This is graph drift, not
an executed compiler failure. The repository's strict verification remains intact.

Research reviewed Parlor `301ff8f5`, normally merged as `706e0ecb`; both have
the same application/source tree `67cacfa6`. Its actual-main CI qualification
certifies Parlor, **not RPC linkage or this newer report-only checkout**.

## Why it is not a drop-in optimization

`RpcHost.create` and `RpcClient.create` build and own their P2pKit instances.
The configuration accepts an `OrganizationLan`, but not a borrowed kit or
existing session; platform factories are internally constructed.
`RpcClient.attach(P2pSession)` is private; RPC link/engine attachment
is internal. There is no supported public facade seam for attaching RPC to
Parlor's existing `LocalRoom` session. Adding a second facade alongside it would
add discovery, transport and lifecycle ownership instead of removing networking
layers. Replacement requires a deliberate adapter/room-stack design and proof,
not internal API imports or an independently running transport per game.

The host defaults to 128 running/256 queued/eight per-client calls. Limits are
configurable, but concurrency must remain subordinate to Parlor's single
reducer owner and one outstanding action per player. Coroutine-based bounded
RPC admission is useful; its existence does not measure mobile thread, CPU,
allocation or battery costs.

Notifications accept at most 16 KiB encoded bodies. Calls/responses accept at
most 1 MiB; procedure defaults are 64 KiB. Parlor's combined public/private snapshot
limit is 256 KiB raw bytes. Blind notification substitution would reject valid
snapshots, and serializer overhead matters. Do not truncate, enlarge production
bounds, or add an unbounded fragment layer to make the comparison fit.

## Operation-by-operation assessment

| Operation | Current protection | RPC opportunity / decision |
| --- | --- | --- |
| Turn actions, votes, configuration | Stable command ID, sequence/revision checks, per-actor bounded result ledger | Typed request/result API is plausible; no measured benefit over existing correlated acknowledgements. Retain. |
| Action outcome recovery | Query by original ID; never replay an ambiguous non-idempotent action | `NeverReinvoke` and `RecoverOnly` align conceptually, but do not replace Parlor's persisted seat, epoch or revision policy. Retain. |
| Room create/discovery/join | Pinned LAN identity/admission and start barrier | RPC facade overlaps transport ownership. Not an incremental operation-only integration. Retain. |
| State requests | Validated receiving-player projection and monotonically installed revisions | A pull-response pilot could be evaluated after a supported single-connection seam exists. Must retain all filtering/size/stale checks. |
| State updates, host events, termination | Recipient-specific conflated outboxes; broadcasts/events where applicable | Retain event-driven messaging. RPC calls must not turn these into client polling. |

`RpcClientConfiguration.transportReconnect` defaults to true and can be disabled
when an application already owns recovery. Any future adapter must select one
reconnect owner, not nest RPC retries inside Parlor's bounded recovery.
`RpcHostRequests` is an internally constructed local-admin observation, not a
public existing-session attachment. Its identity/procedure metadata must never
be automatically logged, sent to peers or placed in gameplay UI.

RPC authorization scopes and application-session revocation are useful primitives,
but transport identity is not a game seat or permission to inspect hidden roles.
Every handler still requires actor/seat authorization, exact protocol/game
compatibility, start eligibility and reducer serialization. Replies/notifications
must contain public state plus only their receiving player's private slice.
RPC cannot allow indefinite iOS background LAN hosting or securely migrate a host
without a separate designed, reviewed system.

## Required adoption experiment — not executed

First obtain a supported single-connection adapter (or justify a complete
room-stack replacement), resolve/review the reproducible dependency graph,
and compile/test Android plus every iOS architecture without weakening strict
verification. Compare the same frozen gameplay workload and payloads under the
same host authority/filtering and Release optimization settings.

Measure 1/4/8/maximum supported peers, small commands plus representative/full
bounded snapshots, normal turn cadence, synchronized votes and bounded bursts.
Inject dropped acknowledgements, duplicate IDs, cancellation during send, stale
replies, Wi-Fi changes, lock/call/app switching and simultaneous reconnections.
Record command-to-authoritative-result latency p50/p95/p99, wire bytes, CPU time,
allocations/GC, RSS/peak memory, thread count and recovery time/correctness.
On physical Android/iOS hosts also record battery energy, thermal state and
lifecycle behavior. Report device/OS/build/workload/warmup and repeatability;
a JVM/simulator measurement is not phone-energy evidence.

This Linux environment has no Xcode, physical phones or `/dev/kvm`. No RPC
host/client or comparative benchmark has been executed. Performance, mobile
compatibility and recovery remain unverified—not presumed inferior or superior.
Publication and typed APIs alone are insufficient reason to introduce a second
network stack or migrate production.

## Reproducible research references

- [Upstream PR524](https://github.com/p2pKit/P2pKit/pull/524), merged as
  `379670d5e04a81c63f8a91aa9db09defe29a1bb5`. PR merge is not Parlor acceptance.
- [Published source JAR](https://repo.maven.apache.org/maven2/io/github/apdelrahman1911/p2p-rpc/0.8.0-rc8/p2p-rpc-0.8.0-rc8-sources.jar):
  coordinate `io.github.apdelrahman1911:p2p-rpc:0.8.0-rc8`, 63,328 bytes,
  SHA256 `60c2eecd2c1f943d0732744416121306d39a858c513e8fcb06d80bd35b364918`.
- [Publisher module metadata](https://repo.maven.apache.org/maven2/io/github/apdelrahman1911/p2p-rpc/0.8.0-rc8/p2p-rpc-0.8.0-rc8.module):
  SHA256 `c3dff5abf0a2ef17f5f7f1323488e7057d729b78744269f2d4b410eb2a235f29`.
- API/ownership: source-JAR `commonMain/dev/p2pkit/rpc/` files
  `RpcHost.kt`, `RpcClient.kt`, `RpcConfiguration.kt`, `RpcPlatform.kt`,
  `RpcHostRequests.kt`, `RpcPolicy.kt`, `RpcProcedure.kt` and
  `RpcApplicationSession.kt`; wire caps: `internal/RpcWire.kt`.
- Parlor comparisons: `AuthoritativeSessionCoordinator.kt` and
  `BoundedPeerOutbox.kt` under
  `shared/session/src/commonMain/kotlin/com/parlor/session/multidevice/`,
  the accepted production architecture and
  [dated earlier decision](RPC_AND_LIFECYCLE_2026-10-25.md). The previous
  rc7 publication uncertainty is historical, not the current adoption blocker.
