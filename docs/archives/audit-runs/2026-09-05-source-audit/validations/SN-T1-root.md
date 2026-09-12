# SN-T1 — independent root validation

**TEST/EVIDENCE GAP — Low; not a shipping defect.** Finder `/root/session_cont`; validator `/root`.
Source main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree
`db7f3d2afe73a13628296daee2cce71165eebc8d`; absolute root
`/Users/abdelrahman/Projects/parlor/`. Hashes: `pending-root-source-hashes.json` and coverage receipts.

Independently read the complete291-line
`shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/P2pKitRoomTransportLoopbackTest.kt`.
The peer tests125–249 are ignored and were **not unignored or executed**. They all await join without
any admission approval owner, omit secureStorage, and share the same identity store/factory/AppId.

Reopened production `P2pKitRoomTransport.kt:126–180,916–1024,1482–1502,2236–2308,2681–2704`.
The constructor defaults to fail-closed UnavailableSecureStorage; host handling creates only pending
admission; the explicit approveAdmission call is what admits. The peer waits for approval before
durably staging a credential. Thus the unmodified fixtures cannot reach their membership/delivery
assertions merely by gaining a usable multicast interface. Their30-second outer join deadline is
shorter than the60-second host-approval wait, with nobody able to approve in the fixture.

Also reopened exact published rc3 JVM DSL, storage, namespace and PeerId derivation excerpts
(`evidence/p2pkit-0.7.0-rc3-loopback-identity-excerpts.txt`; official Central URL/hash in candidate).
The shared store+AppId resolves one namespace/keypair/fingerprint/PeerId, not distinct synthetic
devices. This is a test-model error, **not** evidence of shared production mobile identities.

Counter-evidence: the enabled100–123 test starts only one real kit, advertises and checks room fields;
these missing peer prerequisites do not invalidate that narrow check. Deterministic lifecycle fixtures
provide explicit approvals and synthetic storage; they are separate from physical LAN proof. The
comments' historical manual-validation claim has not been adopted as current device evidence.

Recommendation: separate retained synthetic identity and credential backings per device, explicit
approval, identity-distinctness assertions and cleanup. Keep unsupported real-LAN tests gated;
validate the fixture contract independently, then run only in a genuinely supported environment.
No need to weaken production admission/storage. No network/app/build process was started for this
source validation. A local source proof of stale fixtures does not establish actual LAN success/failure.
