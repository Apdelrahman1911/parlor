# ADR-0002: Manual endpoint connection is outside the first release

- Status: Accepted
- Date: 2026-08-09
- Decision owners: Parlor product and multiplayer architecture
- Finding: P2P-10

## Context

Parlor supports entering the six-character code shown by a nearby host. That
is an admission action, not a direct network endpoint. The current
`P2pKitRoomTransport.join(code, displayName)` still discovers generic Parlor
LAN advertisements, connects through P2pKit, and then proves the code inside
the authenticated encrypted session.

P2pKit 0.7.0-rc2 has an experimental, fingerprint-pinned manual-peer API, but
it is available only when an appropriate provisioning implementation is
registered. Parlor depends on `p2p-core` and `p2p-transport-lan`; it does not
depend on the Android or Desktop provisioning sidecars, and its Apple factory
does not enable `iosManualIp()`. Parlor also has no transport-independent
endpoint model, authenticated-fingerprint provisioning UX, QR/deep-link
format, or parity tests for that path.

Adding an IP text field alone would create a weaker and platform-inconsistent
join path. It would also be misleading on networks that isolate clients or
require public-internet NAT traversal, neither of which a local endpoint can
solve.

## Decision

Manual endpoint connection is unsupported on Android, iOS, and Desktop for
the first production release. `TransportCapability` reports
`supportsManualEndpointConnection = false`. UI and test instructions may say
"enter the room code", but must not call that direct connection or promise a
fallback when Bonjour/mDNS discovery is unavailable.

The supported topology is a reachable local IP network on which P2pKit LAN
discovery and peer TCP traffic both work. Public-internet play, NAT traversal,
rendezvous, and relay are separate unsupported capabilities.

This decision makes direct/manual physical-test rows **not applicable**, not
passed. Failure of automatic discovery remains a failed session for this
release and must produce truthful recovery guidance.

## Invariants

- Room codes remain encrypted admission secrets and are never advertised.
- Every connection uses P2pKit AuthenticatedV2 and host approval.
- No raw-IP scan, unauthenticated endpoint, or identity-pin bypass is allowed.
- No platform silently enables a path that the shared capability reports as
  unsupported.
- Documentation distinguishes room-code entry from endpoint provisioning.

## Conditions for a future supported capability

A future ADR may reverse this decision only after all of the following exist:

1. A transport-independent endpoint provision containing a validated host,
   port, and full expected P2pKit fingerprint obtained out of band.
2. Platform adapters that explicitly register the correct P2pKit provisioning
   implementation and fail closed when it is unavailable.
3. One shared post-connect path for protocol negotiation, actor binding, room
   admission, capacity/rate limits, resumable credentials, and cleanup.
4. Parser, wrong-fingerprint, stale/unreachable endpoint, cancellation,
   network-switch, rejoin, and malicious-input tests.
5. Android-to-Android, iOS-to-iOS, and both cross-platform hosting directions
   passing on signed physical-device builds.

## Consequences and regression proof

Players cannot bypass blocked multicast discovery by typing an IP address in
this release. Existing room-code sessions are unchanged because no join API or
wire message changes. Automated tests assert the P2pKit adapter does not claim
the capability; release documentation and the physical matrix must mark the
direct-endpoint row unsupported/N/A rather than PASS.
