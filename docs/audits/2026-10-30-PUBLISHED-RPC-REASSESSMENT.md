# Published P2pKit rc8 reassessment — October 30, 2026

## Decision and scope

Keep Parlor's shipping `p2p-core`/`p2p-transport-lan` pins at `0.7.0-rc3`.
The previously absent **post-#524 publication is now available**; publication is
no longer the reason to defer evaluating that code. It does not prove a
selective RPC migration improves Parlor. No shipping graph was changed here.
Parlor's unchanged shipping graph was rechecked at main
`71c3f7cc6b8c792b8d8c061d2e35fbe771f8dfe1`, tree
`a36e15b551fb2b8fa503e8e21c20ac6d75d73087`. Publication/source readbacks below
executed October30, 09:29–10:41 UTC; this is not a fresh session benchmark.
PR298 is independently qualified, but its distinct automatic actual-main CI is
still pending at report preparation. This report does not qualify that run.

## Main-acceptance addendum — 18:51 UTC

The distinct [actual-main41099887333](https://github.com/Apdelrahman1911/parlor/actions/runs/41099887333)
now independently qualifies all eight producers/optimized Apple Release join,
23 Swift /716 KMP Simulator /450 readiness methods and both Android runtime lanes,
with exact source/artifact/native/cleanup custody. Qualification SHA256:
`83469030473de7ef4b3b028669bc735a78e4d659251f01dc8eeb286592758bc7`.
Scoped297 is closed. This does not qualify a newer documentation tree, rc8 graph,
mobile energy behavior or physical devices. The preceding report-preparation
statement and original publication timestamps remain historical, not rewritten
as later network reads. PR294's changed-source full41110715660 is separately pending.

## Independently retained publication evidence

- Maven Central root metadata/POM/Gradle modules for core, LAN and RPC return
  HTTP200 and version `0.8.0-rc8`, updated October30 at09:29 UTC.
- Annotated `v0.8.0-rc8` resolves to publisher source
  `6c85fdc2977386e572830c243f9aa8bb17d84ec3`, tree
  `87d0eac4969dccb978207fa9be85882f1d22339a`. Git compare establishes #524's
  `379670d5` is its ancestor (13 commits ahead, zero behind), not merely a date inference.
- Original [publisher41028814149](https://github.com/p2pKit/P2pKit/actions/runs/41028814149)
  and signed-bundle publication/deployment/verification artifacts were retained.
  The deployment receipt reports PUBLISHED. Independent readback verifies all726 bundle
  members against its SHA256 manifest, including121 signature files; **this is
  not an OpenPGP-signature verification claim**.
- All15 core/LAN/RPC Android, JVM, iosArm64, iosSimulatorArm64 and iosX64 target
  module descriptors and referenced runtime payloads match that bundle;33 Central
  objects total,12,295,025 bytes. Target module components correctly refer back
  to their KMP root coordinates. This is availability/byte parity, not linking.
- Five key published RPC source files match immutable publisher Git bytes:
  host/client/platform/configuration and JVM construction. Metadata declares
  Kotlinstdlib2.4.20; Parlor's compiler remains2.4.10. A real reviewed complete
  transitive graph and all maintained architecture checks are required before
  adoption; no repository verification metadata was regenerated.

## Architectural fit, not an unmeasured speed claim

The published `RpcPlatform` constructor is internal and `RpcHost`/`RpcClient`
create their own kit/profile/scopes. There is still no public borrowed-kit
adapter for Parlor's existing authenticated game connection. Adding RPC solely
for state probes or turn acknowledgements would add another connection/trust
lifecycle, or require a full transport/admission migration. Neither is justified
by demonstrated mobile benefit.

Useful reviewed additions include `registerScoped`, `tryNotifyScoped` with fresh
application-session authorization, and `RpcClientConfiguration.transportReconnect`
for choosing one recovery owner. They do **not** supply Parlor seat ownership,
room-code admission, revision fencing, durable action IDs, host authority,
recipient projection or process-death restoration. Preserve those contracts.

Organization-LAN requires an explicit permitted interface/address/subnet and
application-owned durable trust. Its enrollment/approval semantics differ from
Parlor's same-LAN short-lived room flow; do not weaken them or add raw-IP joins.
RPC's recover-only/status and ambiguous-result semantics still require the
original application action ID and reconciliation, not automatic replay of a
non-idempotent command. Broadcasts/snapshots remain event-driven candidates.
The new shared readiness I/O is a **core/LAN** change, not proof RPC itself wins.

## Verification and next useful experiment

This investigation executed publication/source/target-byte readbacks, **no
comparative RPC session benchmark**. It cannot report mobile latency, memory,
CPU, allocations, threads, battery, thermals or physical reconnection numbers.
Upstream capacity measurements and its sample prerelease are not Parlor evidence;
upstream documentation still retains independent mobile/security qualification holds.

Before any integration, use an isolated non-shipping prototype with a fully
reviewed published graph, identical host reducers/seat filtering and4/8/16-player
workloads. Compare current messaging, rc8 messaging and rc8 selective RPC
separately so readiness I/O cannot be misattributed to RPC. Execute response
loss, duplicate/stale requests, cancellation, simultaneous votes and reconnect;
verify exact accepted-action counts and no second side effect. Measure actual
Android/iOS hosts and clients and sustained idle/play workloads, including
latency distribution, CPU/RSS/threads/allocations/wire bytes and energy/thermals.
Linux/JVM loopback alone cannot satisfy that mobile decision gate. No physical
Android/iPhone or Xcode is available in this environment. Do not add a second
networking layer, unpublished override or relax current verification to prototype.

## Evidence boundaries

Retained bundle and target-readback receipt SHA256 values, respectively:
`38dec1707075bfd18a8261f6e45799302d192195e0975d05cdf515371b4edbed`;
`b93ab9e2530b874aff5ed41f7e5e5d02e812e7f8a23833c5432d206a96ac3bdf`.
They bind completed readbacks, not a new Parlor link/runtime result or verified
OpenPGP signer. The [older assessment](RPC_AND_LIFECYCLE_2026-10-25.md) retains
its dated rc7 observations; do not use them to deny the now-published rc8 graph.
Production dependency verification, current game/privacy/recovery contracts and
#251–253/#258–260 physical/security gates remain unchanged.
