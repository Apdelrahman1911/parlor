# M-C02 — Rejected connected-seat retirement refusal

Administrative mirror created by `/root/whodunit_cont` at `/root` request. **No new source approval or application defect is asserted here.** Finder `/root/mafia_cont`; independent validator `/root/session_cont`.

- Classification: **FALSE POSITIVE** for the supposed refusal to retire an already-connected/rejoining seat. No defect severity assigned.
- Baseline: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`.
- Original candidate/rejection note: `reviews/mafia-cont-notes.md:28`.
- Full separate validation, exact source paths/ranges and counter-evidence: `validations/M-C02-session_cont.md:1–36`; source identity manifest `validations/M-C02-session_cont.sources.json`.

## Hypothesis and independent rejection

The suspected sequence was a required Mafia seat reconnecting physically but not completing its game handshake before the one-shot grace deadline. The hypothesis assumed transport retirement refuses a connected seat, leaving the canonical disconnect marker stalled without another expiry.

The separate validator reopened production DI, frozen admission, runtime, bridge, rejoin and retirement paths. Actual `/Users/abdelrahman/Projects/parlor/shared/transport-p2p/src/commonMain/kotlin/com/parlor/transport/p2p/P2pKitRoomTransport.kt:2781–2851` has **no connected-state rejection**. Its failure guards are host identity, already-left room, open admissions, or never-admitted identity. The frozen live remote seat does not satisfy these. Retirement revokes sessions, pending transactions/barriers, reservations and credentials under the state mutex, then closes noncancellably. Pending resume callbacks lose their identity guards.

Mafia bridge retirement/expiry ownership and reducer terminal handling are detailed in the validator report. A fake room can inject arbitrary TransportFailure, but that does not establish the nonexistent production predicate. The default interface Unauthorized result is not the production override.

Keep this record in the rejected register, not the confirmed issue count. Real LAN/socket closure and every possible lifecycle failure remain outside this narrow rejection. No remediation is recommended for a nonexistent connected-state guard. Source/tests were inspected by the independent validator without a new runtime run; no physical evidence is claimed.

No build/test/app process, source edit, Git operation or new independent approval was performed while creating this mirror.
