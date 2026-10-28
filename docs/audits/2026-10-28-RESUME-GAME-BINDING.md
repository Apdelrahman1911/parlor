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

Final restored focused/full committed-source checks, all existing hosted
architectures/optimized Release lanes, protected integration and fresh actual
main must qualify this candidate separately. Older main or catalog passes do
not certify it. AGENTS remains byte-identical; no release gate, signing bypass,
Store publication or paid runner is introduced.

#251's actual mobile cross-game/recovery matrix remains open. This does not bind
a particular same-game room/credential generation to the public Home selection,
implement automatic cold-start recovery (#252), diagnose the original UIKit
input event (#267), qualify strict protection (#258), or execute physical
phone/lock/call/Wi-Fi/accessibility/battery/thermal acceptance.
