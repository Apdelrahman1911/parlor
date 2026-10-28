# Selected-game resume boundary — October 28, 2026

## Finding and interpretation — existing #251

All six shipping Resume routes call the game-agnostic transport method even
though their initial Join routes supply a selected game/version. Executing the
actual registered routes exposes that missing caller context. Protected recovery
credentials and host offers still authenticate and compare game metadata; no
unauthenticated cross-game admission, accepted wrong-game gameplay or private
information exposure is reproduced here. This is a **confirmed caller-binding
gap / defense-in-depth correction**, not a demonstrated exploit.

A trusted protected-store metadata update between the public Home summary and
Resume is injected in the real adapter fixture. The selected version must be
compared to the credential actually used for authentication, rather than checking
an earlier summary and later calling unbound Resume. Reachability of such a
replacement in ordinary mobile user flows remains unverified. Keep this within
[#251](https://github.com/Apdelrahman1911/parlor/issues/251); no duplicate Issue.

## Implementation

`RoomTransport.resumeLastSession(expectedGameProtocol)` fails closed by default.
There is deliberately no summary-read/unbound-call fallback. The P2pKit adapter
passes both overloads through its existing transactional resume implementation;
the selected overload rejects mismatched or absent credential game metadata
before kit creation, discovery, connection or traffic. It does not erase that
membership or change the local-network evidence. Game-agnostic non-shipping
callers retain the original overload.

Whodunit, Mafia and Last Light supply their existing IDs/host protocol versions;
the generic peer lobby supplies its registered spec for Egyptian Dominoes,
Ghamza and Word Impostor. No game branch enters transport/session/protocol code.
The same loaded credential then follows the existing authentication, rotation,
seat, cancellation and ownership-checked cleanup path. No wire field, protocol
version, dependency, timeout, command retry, lifecycle deadline, optimistic peer
mutation or host authority changes.

## Executed regression scope

- Six actual shipping-binding Desktop methods cover both EN/LTR and AR/RTL at
  320×740dp/200% text. A tripwire receives the exact binding version once and
  never creates a room, loads private state or starts discovery. Each test owns,
  injects and retires its executor; no shipping/per-client thread is added.
- A common test rejects unsupported selected Resume without calling metadata
  preflight, unbound Resume, Host or Join.
- Five new real-adapter/fake-P2pKit methods cover wrong game, wrong version,
  missing metadata, successful same-seat credential rotation and metadata
  replacement after a summary read. Rejection requires zero kit creation,
  connections and frames, with the stored credential unchanged.
- The existing16 opening/cancellation/registration tests also run; their Resume
  scenarios now use the selected overload. Original cleanup/revocation checks
  remain. This is adapter execution with fakes, not real P2pKit/mobile LAN.

A temporary removal of the credential guard fails exactly the four rejection
methods. Temporarily restoring all four shipping calls to unbound Resume fails
all six registered-binding methods. Source bytes are restored in each control;
failed XML/logs and scoped-stop receipts remain under local agent custody.
The initial observer compile/API error and missing test lifecycle binding are
retained separately and are not qualified regression evidence. Final controls
use the corrected actual test fixture, not that earlier failed harness.

```bash
./gradlew :composeApp:desktopTest --tests '*ResumeGameBindingTest*' \
  :shared:transport-p2p:desktopTest --tests '*PeerOpeningCancellationTest*' \
  :shared:networking:desktopTest --tests '*BoundResumeDefaultTest*' \
  --dependency-verification=strict --no-daemon
```

## Required acceptance and remaining work

The restored complete strict `productionCheck allTests` at
`479e4ac5ec084d0ccc76f307e2ddb4086db4f967` (tree
`153feaa8c3a223ded67e12f30fd0c6f9cdff5956`) passes **1,840 Desktop**,
**653 Android Debug +653 Release**,112GitHub-test and750release controls.
Only three existing LAN skips and one known Linux release-control skip remain.
All28 focused methods are contained in those passing current module reports.
Detekt/lint/R8/unsigned AAB, inventory and shell gates pass. Source is unchanged;
private registry stop0. Full log SHA256:
`15e14910154244c8344ea685019c0537d542f5f92e622ebd01ad0efaf28a88eb`.
981tasks execute,553 valid cached outputs and134up-to-date outputs are exposed
in the full log. Linux Native klibrary compilation is not Apple linkage/runtime.

The documentation/inventory follow-up at `09578533` also passes the complete
strict check with the same1,840/653/653/112 module totals,750release controls,
unchanged source and private-registry stop0; valid cache reuse is exposed.
Its full log SHA256 is
`53d1ec1d8ebf381b14ce94e52e18fb25b6cc01f4306c8102ce0305ef6aaac6b5`.

This unpublished Resume branch is now normally integrated with independently
verified catalog [PR#280](https://github.com/Apdelrahman1911/parlor/pull/280)
main `0bb8d5a59d2a4f7b1849d188e312aef178593005`. Fresh
[actual-main40731239367](https://github.com/Apdelrahman1911/parlor/actions/runs/40731239367)
qualifies all eight producers plus the original Apple join,20Swift/678KMP
Simulator cases and the exact source/artifact/native/output custody. That
qualifies catalog integration only; the Resume source still requires its own
full new-head check, fresh hosted architectures/optimized Release lanes, normal
protected merge and independently qualified automatic actual-main run.
AGENTS remains byte-identical; no verification gate, signing bypass, Store
publication or paid infrastructure is introduced.

#251's actual mobile cross-game/recovery matrix remains open. This does not bind
a particular same-game room/credential generation to the public Home selection,
implement automatic cold-start recovery (#252), diagnose the original UIKit
input event (#267), qualify strict protection (#258), or execute physical
phone/lock/call/Wi-Fi/accessibility/battery/thermal acceptance.

The additional [real-LAN selected Resume experiment](2026-10-28-REAL-LAN-SELECTED-RESUME.md)
now passes three distinct synthetic Linux host/peer pairs, including same-seat
rotation/revocation and wrong-game/version rejection. It is not native/mobile
or process-death qualification; full current-source integration remains pending.
