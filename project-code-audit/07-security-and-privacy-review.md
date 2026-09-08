# Security and privacy review

## Trust model (from code)

- Same-app LAN + 6-char CSPRNG room code + host tap. Not an account/PKI identity.
- P2pKit authenticated-v2 session; transport overwrites `actor`.
- Residual first-contact LAN attacker is accepted by the architecture
  (no out-of-band fingerprint UX).

## Confirmed

| ID | Issue | Sev |
|---|---|---|
| F-005 / SP-001 | PaP snapshot **filename** embeds gameplay seed (`local-${seed.toString(16)}`) | Medium |
| SP-002 / AR-003 | Public Ktor remote client on shipping classpath | Medium |
| SP-006 | `WrongCode` oracle + non-constant-time compare; limiter before code | Low |
| SP-004 | Desktop snapshot key is same-uid file | Low (non-shipping) |
| SP-005 | Diagnostics always log (closed vocab, no secrets) | Low |
| SP-007 / TB-008 | Dependency verification checksums only, not signatures | Low |
| SN-006 | Admission budget burn before room-code check | Low |

## Confirmed non-leaks (source)

- Multiplayer snapshot = `toPublic` + recipient private only.
- Host-only role maps / killer id not on the wire (sessionNonce ≠ seed).
- Peer install validators fail-closed on living `revealedRole` (Mafia) and
  vote-target redaction (Whodunit collecting).
- Diagnostics export has no names, codes, fingerprints, payloads.
- `LocalRoom.rejoinToken` getter is hard-null; secret stays in SecureStorage.
- Android `allowBackup=false` + exclude-all backup/extraction XML;
  snapshots in `noBackupFilesDir`.
- iOS Keychain `AfterFirstUnlockThisDeviceOnly`, non-sync;
  snapshot dir excluded from backup.
- Manifest: no Nearby/Location. INTERNET + wifi multicast only.
- `usesCleartextTraffic=false`.
- Production seed: `SecureIds.randomLong()`.

## Needs runtime / pentest

- Physical LAN actor-stamp and isolation (SP-008 / SN-003) — `@Ignore`.
- TalkBack announcing role after “I’m alone” gate (SP-009) — intended but
  unproven focus-order.
- OEM honor of `allowBackup=false` / iOS unencrypted backup (SP-010).
- Room-code 30-bit space + host tap: online guessing needs LAN presence.

**Posture:** stronger than typical indie party apps on the wire path; local
filename seed and unproven radio are the real gaps. Store identity collision
is a release/security-process issue (F-001), not a runtime exploit.
