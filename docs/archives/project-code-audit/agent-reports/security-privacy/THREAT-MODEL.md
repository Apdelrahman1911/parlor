# Threat model — security / privacy

Scope: shipping Android/iOS + the desktop harness. Attacker is not assumed to
break P2pKit's transport crypto; application-layer mistakes are in scope.

## Actors

| Actor | Position | Goal |
|---|---|---|
| Malicious LAN peer | Another Parlor (or same-AppId) process on the Wi-Fi | Join without code, impersonate a seat, read other players' private slices, replay commands/credentials, DoS the host |
| Malformed / modified client | Authenticated same-app session | Oversized/malformed CBOR, forged `actor`, inject control chars into ids/names, leak via logs |
| Local snapshot tamper | Filesystem access without Keystore/Keychain | Swap/truncate snapshots, rename ciphertext, recover roles from plaintext or filenames |
| Backup / device-transfer | Cloud backup, USB, Quick Start, iCloud Keychain | Copy host-only state or rejoin secrets onto another device |
| A11y observer | TalkBack/VoiceOver, or a passer-by during pass-and-play | Hear hidden role / night target / room secret |
| Impersonator | New physical device claiming an old seat | Resume without the credential + fingerprint pin |

Out of scope by product: WAN play, spectators, host migration, manual IP join.

## Assets

- Host-only: role map, killer id, random seed, night/vote logs, red herrings
- Per-player private: own role, teammates, detective result, night pick
- Rejoin secret (256-bit) and host fingerprint pin
- Room code (6 chars / 32-symbol alphabet ≈ 30 bits)
- Local P&P snapshots (canonical state including host-only)
- Display names (not identities; `PlayerId` is)

## Controls (code)

| Threat | Control | Residual |
|---|---|---|
| Read another seat's private | `toPlayer` + host encodes only own slice; peer rejects non-fixed-point public payloads | Mafia living `revealedRole` is reducer-trusted (SP-003). Physical LAN broadcast unproven (SN-003) |
| Impersonate actor | Transport overwrites every `actor` with authenticated peer id | Real-radio overwrite is `@Ignore` |
| Replay command | `commandId` ledger + `clientSequence` + `expectedRevision` (session workstream) | Not re-audited here |
| Replay / steal rejoin | Host digest + constant-time compare + generation rotation + fingerprint + expiry; peer stages before commit | Unlocked-device extraction (SP-010) |
| Guess room code | Not in mDNS; host approval; admission limiter | `WrongCode` oracle (SP-007); 30-bit code |
| Same-app LAN handshake | `AcceptAnyAuthenticatedSameApp` then app admission | Any Parlor install can open a session and probe |
| Snapshot tamper | AEAD + filename AAD; fail-closed `SnapshotProtectionException`; safe names | Seed in P&P filename (SP-001); rooted/jailbroken extract |
| Backup exposure | `allowBackup=false` + exclude-all rules + `noBackupFilesDir`; iOS exclude-from-backup + ThisDeviceOnly Keychain | OEM backup bugs; adb on older images — Needs runtime |
| A11y leak of hidden role | Handoff/gate strings omit role; WaxSeal generic a11y; host-lost `clearAndSetSemantics` | TalkBack after reveal; pass-and-play leftover (SP-009) |
| Remote content | DI binds `OfflineRemoteCaseDataSource`; no composeApp `HttpClient` | Public `KtorRemoteCaseDataSource` on classpath (SP-002) |
| Weak RNG | Platform CSPRNG for ids, tokens, seeds, room codes | None in code |
| Log leak | Diagnostics closed vocabulary; `rejoinToken` never on `LocalRoom` | INFO/NSLog activity fingerprint (SP-006) |
| Id injection | Wire ids `[A-Za-z0-9_-]`; display names drop controls/format chars | — |
| Deprecation trap | `AdmissionAccepted` rejected as incompatible | — |

## Trust boundaries

```
LAN peer
  -- P2pKit encrypted session (any same AppId) -->
    admission (code + host tap + credential)
      -- actor-stamped PeerMessage -->
        host reducer
          -- PlayerSnapshot(public=toPublic, private=own slice) -->
            peer validator (fixed-point + game validator)
              -- ShadowSessionController -->
                UI (must not read hostOnly)

Local disk
  -- AEAD (mobile) / file key (desktop) -->
    FileBackedSnapshotStore
      -- GameSnapshot envelope + canonical payload (P&P only) -->
        resume (fail-closed; no auto-delete)
```

## What “Needs runtime” means here

Physical two-device join, TalkBack/VoiceOver during handoff, backup/restore
onto a second device, and rooted/jailbroken extraction cannot be closed from
source. Those are called out as Needs runtime, not Confirmed defects.
