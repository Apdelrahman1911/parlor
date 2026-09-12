# SN-T1 — Ignored peer loopback fixtures no longer model the current admission contract

> **Final independent disposition (2026-09-05): TEST/EVIDENCE GAP — Low; not a shipping application defect.** Validator `/root`; see `validations/SN-T1-root.md`. The original candidate text below is preserved as the pre-validation record; its pending language is superseded by this disposition.

- Finder: `/root/session_cont`.
- Independent validator: **pending**; proposed classification **TEST/EVIDENCE GAP**, not a shipping application defect.
- Suggested severity: Low (test infrastructure); real LAN readiness remains an external gate independently of this test weakness.
- Source: `main`, HEAD `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Exact file hashes/ranges are in `coverage/reviews-session_cont.jsonl`.
- Absolute test path: `/Users/abdelrahman/Projects/parlor/shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/P2pKitRoomTransportLoopbackTest.kt`.

## Expected versus actual

The three ignored tests claim to exercise peer join, bidirectional delivery, and host broadcast when a suitable physical/multicast environment is available (lines 125–249). They are not run automatically and were **not unignored or executed in this audit**. Their bodies also omit prerequisites of the current implementation, so network availability alone cannot make the authored paths successful:

1. They call `peerTransport.join` and immediately await its return, but never collect `pendingAdmissions` or invoke `hostRoom.approveAdmission`. The production host stages a request and emits `AdmissionPending`, then waits for the explicit approval owner. No automatic admission owner exists in these fixtures.
2. Every peer `P2pKitRoomTransport` omits `secureStorage` (lines 132, 164, 214–215). Its public constructor defaults to `UnavailableSecureStorage`, whose `get`, `put`, and `remove` all fail. Even adding host approval would fail durable credential staging before `AdmissionConfirmed` can be sent.
3. All simulated devices share one `testKitFactory` and one `LoopbackIdentityStore` for the same per-test `AppId` (lines 73–87, 129–132, 161–164, 211–215, 278–291). Exact published rc3 JVM implementation derives the storage namespace from AppId alone, returns an existing keypair for that namespace, and derives PeerId from AppId plus the key fingerprint. These kits therefore represent one cryptographic device identity rather than distinct host/peer identities. This is a fixture modeling error, **not evidence that production devices share keys**.

## Complete source proof and counter-evidence

Production file: `/Users/abdelrahman/Projects/parlor/shared/transport-p2p/src/commonMain/kotlin/com/parlor/transport/p2p/P2pKitRoomTransport.kt`:

- Constructor defaults: 129–149, with `secureStorage = UnavailableSecureStorage` at 134; credential-store owner at 175.
- Host request handling: 2240–2295 only stores pending connection and sends `AdmissionPending`; approval entry at 2691–2695 explicitly calls `admit`.
- Peer first response and approval wait: 930–975; `HOST_APPROVAL_TIMEOUT_MS = 60_000` at 1455. These tests have a 30-second outer join timeout (143, 175, 226, 230).
- Durable credential stage: 990–1005; failure returns `NetError.SecureStorageUnavailable`.
- Fail-closed default storage: 1487–1497.

Published source reference, accessed 2026-09-05:
`https://repo.maven.apache.org/maven2/io/github/apdelrahman1911/p2p-core-jvm/0.7.0-rc3/p2p-core-jvm-0.7.0-rc3-sources.jar`.
SHA-256 `bc9f7afa1e96f434aec93331e45ec3dd54f1254a80bc1d2c7ae1e05649463f55`.
Relevant reviewed excerpts are in `evidence/p2pkit-0.7.0-rc3-loopback-identity-excerpts.txt` (JVM DSL adapter, identity storage, namespace derivation, and identity service).

Counter-evidence: the **enabled host-only** test (100–123) legitimately exercises real kit startup/advertising and verifies local room fields; it does not join or verify peer delivery. Host startup does not require Parlor peer credential storage, and only one kit is created there, so these omissions do not invalidate that limited test. Manual physical-device testing remains a separate procedure; no claim is made about whether any historical manual run succeeded. Existing deterministic lifecycle tests use distinct fake peer IDs, explicit approvals, and synthetic protected-storage backings; they are not affected by the omitted setup in these ignored tests.

## Suggested remediation, not implemented

Before ever re-enabling or relying on these fixtures, give each simulated device its own retained secure identity and credential backing, explicitly await and approve synthetic host admission, assert distinct identities, and retain deterministic cleanup. Keep the tests gated by a genuinely supported network environment; do not replace or claim physical-device LAN validation from mocks. Add a network-independent fixture-contract check for those prerequisites. Update comments to distinguish the host startup test, ignored stale fixtures, and external device evidence.

No application/build configuration was changed; no native kit, server, Gradle task, or LAN room was started by this reviewer.
