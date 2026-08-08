# P2P Manual Test Runbook

Single-JVM loopback can't reliably exercise mDNS multicast on Windows, so the
host↔peer half of `P2pKitRoomTransportLoopbackTest` is `@Ignore`d. This doc
explains how to validate that path end-to-end on real machines.

## Prerequisites

- Maven Central access. Gradle resolves P2pKit `0.7.0-rc2` from
  `io.github.apdelrahman1911`; no sibling checkout or local publication is
  required.
- Two devices on the **same Wi-Fi LAN**. Home Wi-Fi or a phone hotspot —
  corporate / guest / hotel networks often block mDNS multicast.

## Wire format quick reference

| Direction | Parlor message | P2pKit envelope |
|---|---|---|
| Host → Peer (broadcast / direct) | `HostMessage.PublicStateSnapshot`, `PublicStateDelta`, `PrivateStateForPlayer`, `EventBroadcast`, `EventDirect`, `TimerSync`, `EndSession` | `P2pMessage.Binary(json bytes)` |
| Peer → Host | `PeerMessage.JoinRequest`, `ActionSubmit`, `Heartbeat`, `LeaveNotice` | `P2pMessage.Binary(json bytes)` |

`PeerMessage.ActionSubmit.payload` carries a `WhodunitAction` encoded via
`WhodunitActionCodec.encode(action)`; the host decodes with
`WhodunitActionCodec.decode(payload)` and feeds it to the reducer.

## Smoke test (no game UI required)

Until Phase 8 wires the transport into the production DI, you can stand up
the adapter in a small main() to validate two devices can find each other:

```kotlin
// Run on host machine:
val transport = P2pKitRoomTransport(
    appId = AppId("com.parlor.p2p.smoke"),
    deviceName = "host-A",
    scope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
)
val room = (transport.host(HostConfig(roomDisplayName = "Smoke")) as Result.Success).data
println("Room code: ${room.info.value.code}")
// keep process alive; print incoming messages
room.incoming.onEach { println("from peer: $it") }.launchIn(...)
```

```kotlin
// Run on peer machine, using the printed code:
val transport = P2pKitRoomTransport(
    appId = AppId("com.parlor.p2p.smoke"),
    deviceName = "peer-B",
    scope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
)
val room = (transport.join("ABCDEF", "peer-B") as Result.Success).data
room.sendToHost(PeerMessage.JoinRequest("peer-B"))
```

Host should print `from peer: JoinRequest(displayName=peer-B)` within a few
seconds. If it doesn't:

1. Confirm both devices are on the same network.
2. Confirm the host's firewall allows JmDNS (UDP 5353) and the dynamically-
   bound TCP port P2pKit picks.
3. On Android, the app must hold runtime permissions surfaced by
   `P2pKit.permissions.requiredPermissions()` (`NEARBY_WIFI_DEVICES` on
   API 33+, plus the manifest entries listed in P2pKit's README).
4. On iOS, the host app's `Info.plist` must include
   `NSLocalNetworkUsageDescription` and `NSBonjourServices`
   (`_p2pkit2._tcp`).

## Action round-trip (Phase 8 follow-up)

Once Parlor wires `P2pKitRoomTransport` into DI:

1. Host phone starts the session and selects a case (e.g. `layla-halabi`).
2. Peer phone joins by code, sees the same public state.
3. Peer submits an action (e.g. `RefuseToVote(p2)`) — the adapter encodes
   it via `WhodunitActionCodec.encode(...)`, ships it as
   `PeerMessage.ActionSubmit(payload)`, host decodes, reducer applies.
4. Host re-broadcasts the new public state; peer's UI updates.

This is the friends-testing flow that the in-memory shape test pins
end-to-end (`WhodunitMultiDeviceShapeTest`). Phase 8 will replace the in-
memory bus in that contract with the real `P2pKitRoomTransport` for
real-device validation.

## Limitations

- **mDNS only** — no relay / cloud signalling. Both devices must be on the
  same broadcast domain.
- **Authenticated encrypted TCP** — P2pKit 0.7 secure protocol v2 uses
  Noise XX by default. Parlor still owns room admission and player
  authorization; transport authentication alone does not identify a person.
- **No background advertising on iOS** — App Store rules; P2pKit honours
  the platform constraint and ceases advertising when the app backgrounds.
- **iOS Network Provisioning is permanently `Unsupported`** — Apple
  doesn't allow third-party apps to create hotspots or silently join Wi-Fi.
