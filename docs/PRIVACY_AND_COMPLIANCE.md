# Privacy and compliance release contract

This is the engineering data inventory, not a substitute for legal advice or
the published privacy policy.

## Product posture

- Intended audience: 13+.
- Price: free.
- No advertising or in-app purchases.
- No account or authenticated real-world identity.
- Multiplayer is limited to peers reachable on the same local network.
- No analytics or crash-reporting provider is included in the shipping source.

## Data inventory

| Data | Purpose and recipients | Retention contract |
|---|---|---|
| Player display name | Labels the local session; sent only to admitted room peers as required by gameplay. It is not an authenticated identity. | Memory for the room and, on a peer, inside the protected resumable credential. Cleared with that capability. Never telemetry or release logs. |
| Room code | Human admission secret shown by the host and entered by peers. | Memory for the host room and, on a peer, inside the protected resumable credential. Never advertisements, analytics, crash reports, or release logs. |
| Resumable credential and rejoin secret | Restores the same admitted player/host/game binding after transient disconnect, backgrounding, or peer process death. | Device-protected secure storage on the peer; the host retains only the secret digest in room memory. Rotated on successful resume. Cleared on explicit Leave, terminal invalidation, corruption, or expiry. Cryptographic maximum is 24 hours, but a host reserves a disconnected seat for only 120 seconds. Never UI, telemetry, or logs. |
| Game state | Enables play and optional local resume when a game supplies a snapshot adapter. Includes public, per-player private, and host-only buckets. | Local snapshot only when the game supplies a snapshot adapter; protected by platform storage/file protection and removed at session completion or explicit deletion. Host-only state never leaves the host. |
| P2P cryptographic identity | Authenticates encrypted same-app transport sessions; it is not a Parlor account. | Device-local protected storage until app data is cleared or the app is removed. Never analytics. |
| Local multiplayer diagnostics | Fixed event/result/reason vocabulary plus sequence, elapsed time, role, and coarse count bucket. | In-process ring of at most 256 records; platform console output is rate-limited to ten lines/second with a one-record backlog. No upload by Parlor. OS/device log retention is platform-controlled. |
| Analytics events | Not collected. The shipping dependency graph and application contain no analytics provider or event API. | None. Introducing collection requires a new reviewed allowlist, informed consent where required, retention policy, and store-disclosure update. |
| Uploaded crash diagnostics | Not collected. Local allowlisted P2P console diagnostics are not an upload mechanism. | None. Introducing a crash provider requires redaction, consent/legal review where required, retention policy, and store-disclosure update. |

Absence of a provider and upload API is the current enforcement mechanism; a
no-op placeholder must not be treated as a privacy control.

## Least privilege

Android and Apple declarations must match the actual LAN discovery/TCP
transport. A release reviewer must compare the final manifests/plists against
P2pKit behavior and reject unused nearby, Bluetooth, location, contacts,
camera, microphone, advertising, or tracking access.

The Apple build needs `NSLocalNetworkUsageDescription` and the exact
`_p2pkit2._tcp` Bonjour service declaration used by P2pKit. It does not use
MultipeerConnectivity or Bluetooth and must not declare Bluetooth purpose
strings for this transport.

The Android base LAN transport declares only `INTERNET`,
`ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, and
`CHANGE_WIFI_MULTICAST_STATE`. It uses NSD/JmDNS plus TCP and requests no
Nearby Devices or Location runtime permission. Provisioning-only permissions
must not be added unless Parlor actually ships and reviews the corresponding
P2pKit provisioning feature.

## Store disclosures

Before submission, record evidence for:

- the public privacy-policy and support URLs;
- Google Play Data safety answers;
- Apple privacy answers and the final `PrivacyInfo.xcprivacy` report;
- no tracking/advertising identifier use;
- local-network purpose copy in EN and AR where the OS permits localization;
- the 13+ intended audience and store-generated content rating;
- encryption/export-compliance answers for P2pKit's authenticated transport;
  and
- deletion behavior for unfinished local sessions and settings.

Mark each item `UNVERIFIED` until checked in the signed store artifact. Source
inspection alone is not sufficient.

## Logging and incident response

Release logging is allowlist-only. `ParlorP2p` accepts closed enums and numeric
or coarse-bucket metadata; callers cannot supply arbitrary strings. Do not
interpolate player-supplied text, exception messages, IP addresses,
fingerprints, room codes, player/session/peer IDs, rejoin tokens, private
content, host-only state, or raw packets. The local diagnostic stream is not a
substitute for consented crash reporting and must never be uploaded by a
provider without a separate privacy review.

A suspected privacy leak stops rollout. Preserve only sanitized diagnostic
metadata, rotate any affected backend/provider credentials, invalidate the
release if needed, and document user/store notification decisions with legal
review.
