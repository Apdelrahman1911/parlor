# Privacy and compliance release contract

This is the engineering data inventory, not a substitute for legal advice or
the published privacy policy.

## Product posture

- Intended audience: 13+.
- Price: free.
- No advertising or in-app purchases.
- No account or authenticated real-world identity.
- Multiplayer is limited to peers reachable on the same local network.
- Analytics consent and crash-reporting consent are separate, default-off
  choices.

## Data inventory

| Data | Purpose and recipients | Retention contract |
|---|---|---|
| Player display name | Labels the local session; sent only to admitted room peers as required by gameplay. It is not an authenticated identity. | Memory for the session; if present in a resume snapshot, local only and deleted with that unfinished session. Never telemetry or release logs. |
| Room code | Human admission secret shown by the host and entered by peers. | Session only. Never advertisements, analytics, crash reports, or release logs. |
| Rejoin token | Restores the same admitted seat to the same host. | In memory for the documented 120-second grace period, then invalidated. Never UI, analytics, or logs. |
| Game state | Enables play and (currently for Whodunit) optional local resume. Includes public, per-player private, and host-only buckets. | Local snapshot only when the game supplies a snapshot adapter; protected by platform storage/file protection and removed at session completion or explicit deletion. Host-only state never leaves the host. |
| P2P cryptographic identity | Authenticates encrypted same-app transport sessions; it is not a Parlor account. | Device-local protected storage until app data is cleared or the app is removed. Never analytics. |
| Analytics events | Optional aggregate product behavior from an allowlist with no names, peer IDs, room codes, rejoin tokens, content prose, or network payloads. | No collection until separate analytics opt-in. Provider retention must be documented before enabling. |
| Crash diagnostics | Optional stack/build/device diagnostics. Network payloads and identifiers must be removed before provider submission. | No collection until separate crash-reporting opt-in. Provider retention must be documented before enabling. |

The current safe fallback is `NoOpTelemetry`: if a consent-aware provider is
not completely configured, nothing is collected.

## Least privilege

Android and Apple declarations must match the actual LAN discovery/TCP
transport. A release reviewer must compare the final manifests/plists against
P2pKit behavior and reject unused nearby, Bluetooth, location, contacts,
camera, microphone, advertising, or tracking access.

The Apple build needs the local-network usage description and exact Bonjour
service declaration used by P2pKit. Android needs only the network/Wi-Fi and
multicast capabilities required by NSD/TCP on the supported OS versions.

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

Release logging is allowlist-only. Do not interpolate player-supplied text,
exception messages from decoded peer payloads, room codes, session/peer IDs,
rejoin tokens, private content, host-only state, or raw packets.

A suspected privacy leak stops rollout. Preserve only sanitized diagnostic
metadata, rotate any affected backend/provider credentials, invalidate the
release if needed, and document user/store notification decisions with legal
review.
