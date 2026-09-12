# MF-C1 independent validation — /root/whodunit_cont

Baseline: `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, 2026-09-05. Finder: `/root/mafia_cont`; validator: `/root/whodunit_cont`. No tests/builds run by validator; deterministic source proof below.

## Classification
CONFIRMED DEFECT in local snapshot semantic validation, **Low / defense-in-depth**. This is not evidence of a currently reachable unmodified-UI game-state corruption, a remote peer exploit, or broken disk authentication. It needs a malformed but successfully authenticated current snapshot (for example an app-side producer defect or synthetic store fixture). The decoder itself is production-reachable on Resume. Legitimate normal gameplay writes the correct field.

## Independently reopened source
All of `MafiaSnapshotRecovery.kt` (1–524), `MafiaSnapshotCodec.kt` (1–55), `MafiaObservableStateValidator.kt` (1–371), `NightResolution.kt` (1–115), `MafiaSettings.kt` (1–133); relevant callers/reducer paths recorded in coverage receipts. Paths are relative to `/Users/abdelrahman/Projects/parlor/`.

## Deterministic proof
1. Valid settings: 5 players, Mafia=1, Doctor=1, Detective=0, 3 civilians, default no-consecutive protection and no-self-heal. Start/ack roles; day 1 Doctor protects another living player X, Mafia skips, civilians skip; ResolveNight. No death or win. Reducer `MafiaReducer.kt:445–458` sets Doctor.previousDoctorProtect to effective target X, and `467–475` records the same X in host nightLog.
2. Ack Night, open Discussion/Voting, everyone abstains, tally, ack vote announcement, advance. `MafiaReducer.kt:775–801` preserves previousDoctorProtect and starts Night(day=2). This is a legal ordinary state with final nightLog.doctorProtect=X.
3. Change only Doctor.previousDoctorProtect to null in a synthetic copy. The public observable validator never reads private slices. Recovery 119–124 allows null; day-1-only guard at187 is not applicable; no pending choices/results exist so 201–250 allows the copy. Coordination/roles/public winner/history are unchanged. History `398–423` validates its own independent local previousDoctorProtect variable but never compares its terminal value with the Doctor private field. Thus this altered state passes all recovery conditions exactly when the original does.
4. `MafiaSnapshotCodec.kt:28–47` accepts it through encode and canonical decode because both use those validators, and `loadMafiaResumedSession:35–64` has no additional binding beyond metadata, phase, and the same check. `MafiaGameFlow.kt:117–177,243–263` installs that decoded state into the local controller. Generic `PassAndPlaySessionController.kt:43–55` uses restoredState directly.
5. Local UI `MafiaPassAndPlayPhaseRouter.kt:388–402` now offers X. `MafiaReducer.kt:279–304` compares only target versus private.previousDoctorProtect; null allows X again. `NightResolution.kt:51–61` also compares only the corrupted private-derived value (reducer389–399), so day2 protection can save X again.
6. The opposite mutation (set a different valid living non-self player Y) similarly wrongly forbids Y and permits X.

## Counter-evidence / limits
- Normal ResolveNight writes the private field and log from exactly the same `resolution.effectiveDoctorTarget`; no present production action found that creates disagreement.
- PostGame clears private progress and recovery144–155 rejects retained previousDoctorProtect, so a fix must exempt terminal cleared state and handle missing/dead doctors and skipped nights correctly.
- The log suffix retains the latest night, even after cap, so latest-night binding can remain bounded; special pre-night/setup paths need null expectations.
- Snapshot files are authenticated below FileBackedSnapshotStore (`save/load`59–86 and SnapshotFileSystem contract); raw disk edits should fail before this validator. Platform crypto was not re-audited by this validator. No alleged authentication bypass.
- Peer validation has no host log by design. Do not transmit that log or another private slice to address this local-save issue.

## Recommendation
Bind the Doctor private previousDoctorProtect to the final retained resolved-night record for assigned, nonterminal local snapshots, with correct null expectations before any resolved night and terminal cleanup exception. Add synthetic current-format encode/decode/resume tests for null/incorrect previous target and prove that normal skipped/dead-doctor/capped-history states continue to pass. No implementation performed.
