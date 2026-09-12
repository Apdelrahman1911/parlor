# Scoped remediation plan — SN-C1, SN-C2, ST-C1

Owner: `/root/session_cont`. Baseline main `3625d0663ba6eb51338cbd5f9dc45f859ec18846` / tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Production changes authorized by root on the user's latest remediation request; no commits, branches, integration, GitHub, Store, signing or device work is authorized here. Root owns the single build lane. Existing audits and user work are preserved.

## SN-C1 — receive deadline includes best-effort commit ACK

Reopened the independent dossier and complete `SessionStartHandshake.kt`. The late accepted-commit branch suspends in `sendCommitAck` before exiting the commit timeout. Move bounded best-effort acknowledgment outside that receive timeout after a validated Success; keep cancellation, validation and all failure paths unchanged. New deterministic common tests will cover a commit at99ms of100ms, delayed/failed/stalled ACK, no/invalid commit, and genuine cancellation during ACK. Existing revision-zero/duplicate recovery tests remain required regression checks.

## SN-C2 — host creation ownership transfer before lifecycle registration

Reopened the host, lifecycle coordinator, host background/foreground/leave, process-owner adoption and peer join/resume siblings. Keep host's cleanup region through registration and the final Success decision. Roll back a failed exact lifecycle registration before the transition mutex is released so queued lifecycle work cannot target an unreturned room. Close only this created room/kit, NonCancellable; preserve external caller cancellation and generation guards. New separate transport tests will cover registration cancellation, early cancellation, repeated cleanup, no orphan readvertisement and replacement host generation. Do not edit root-owned existing lifecycle/loopback test methods. Peer credential behavior must remain unchanged.

## ST-C1 — retained malformed legacy plaintext is backup-eligible

Reopened the complete iOS filesystem, source-backed platform findings, actual native witness and existing key-loss/recognized-magic tests. Prefer in-place verified directory exclusion before discovery/direct reads over relocating or deleting the final legacy copy. Do not cache successful protection across directory recreation. If exclusion cannot be verified, fail closed before reading/migrating; explicit Discard remains possible. Preserve current-record precedence, size bounds, corruption rejection, successful migration ordering and legacy-retention semantics. Native tests should inspect actual Foundation exclusion flags and unchanged bytes for both malformed legacy and damaged-current-header witnesses, direct read, oversized data, retry, and exclusion failure. Correctly signed Keychain/device backup evidence is separate.

## Execution discipline

No Gradle, Xcode, compiler, simulator/device or server commands by this agent. Production/session/transport writes are paused for root's initial ROOT-T3 source freeze until root releases it. Tests will be authored with discoverable Unit/TestResult signatures. Root will collect actual test receipts and immediately stop/clean each cycle. This owner cannot independently approve its own changes; final status remains implementation/testing/review pending until separate validation.
