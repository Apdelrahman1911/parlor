# High/Critical findings — lead re-verification

The dedicated High verifier returned empty (same Task empty-stop as before).
Lead re-read the cited source on 2026-09-01.

| ID | Verdict | Evidence |
|---|---|---|
| F-001 TB-001 TB-013 | REAL | `applicationId` + `verifyApplicationIdentities` pin `com.parlor.app`; `release-policy.json` `blocked`/`public_store_collision`; `release_tool.py:181` hard-fails that ID. |
| F-002 TB-002 | REAL | `testing-candidate.yml:3-14` live `workflow_dispatch` + `publish`. Fail-closed today only because of F-001. |
| F-003 GR-001 | REAL | `openVote` Elimination guard `:620-622` has no clue check. Validator Collecting requires `clueCount == phase.index`. |
| F-007 SN-003 TB-006 | REAL | `@Ignore` on join/broadcast still present (`P2pKitRoomTransportLoopbackTest.kt:126`). **Covered by open #6** (and closed #139). |
| F-008 TB-005 | REAL | 0 instrumented; empty Testables. **Covered by closed #7 and #8.** |
| TB-004 | INTENTIONAL | Split Linux/Apple jobs is documented in workflow + `workflow_contract.py` forbids Apple `allTests`. Naming, not a missing gate. |
| F-004 GR-003 | REAL | `markDisconnected` only adds the id (`MafiaReducer.kt:838-841`). No pause flag. Kdoc still claims pause. |
| F-006 SN-001 SN-007 | REAL | `HOST_MAILBOX_CAPACITY = 8`; inbound `mailbox.send` with no timeout (`AuthoritativeSessionCoordinator.kt:173,212-216`). Stall **Needs runtime**. |
| F-019 GR-002 | REAL (latent) | `killerWins` always `Resolved(killerId, true)` (`:946`). Validator requires `!wasKiller` for SurvivedToFinalTwo. |
| F-005 SP-001 | REAL | `SessionId("local-${seed.toString(16)}")` (`WhodunitGameFlow.kt:770`). |

Issue policy: do not file F-007 (#6), F-008 (#7/#8), TB-004.
