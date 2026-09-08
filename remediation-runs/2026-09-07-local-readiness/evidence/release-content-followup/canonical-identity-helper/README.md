# Production canonical identity corroboration (campaign only)

Run **only after independent review, inside the root's exclusive build lane**:

```sh
/usr/bin/python3 remediation-runs/2026-09-07-local-readiness/run_gradle_cycle.py STORY-CYCLE --command \
  /usr/bin/python3 remediation-runs/2026-09-07-local-readiness/evidence/release-content-followup/canonical-identity-helper/run_with_identity.py \
  --evidence-dir /Users/abdelrahman/Projects/parlor/remediation-runs/2026-09-07-local-readiness/evidence/STORY-CYCLE/identity \
  -- :game-modes:whodunit:desktopTest --tests '*TestingStory*'
```

Root may append focused Gradle checks. The read-only init script captures the
executing `desktopTest` task's real classpath; it does not change repositories,
dependencies, verification, test signatures, selection, or assertions. An
up-to-date/cache-restored test that never executes the action will fail capture.

The wrapper immediately stops Gradle on success/failure/interruption. On success,
JDK21 source-file launch calls the compiled production `CaseEnvelope.serializer`
and `contentIdentity`, with no duplicate canonicalization algorithm. It verifies
all 11 historical digest pins (four originals + seven preceding versions) and
records seven corrected identities. Older bytes are obtained by read-only
`git show`, never checkout. Original files and the build graph are unchanged.

The receipt binds helper/init/wrapper/validation/test hashes, commands, source byte hashes,
timestamps, statuses, and exact classpath. Temporary story copies are deleted;
compact manifests, identities, and logs remain. **The outer lane must then stop
again, collect test XML, and remove compiled outputs.** No helper result is a
substitute for focused tests or platform-runtime checks.

## Observed verification-control failure and correction

`focused-story-followup-05` executed all72 selected tests and both Detekt gates
successfully. Its Java corroboration did **not** execute: the original wrapper
rejected two nonexistent Gradle classpath outputs even though both matching
Java compile tasks had actually reported `NO-SOURCE`. The original failure and
cleanup receipt remain intact; this was not an application/test failure.

The revised read-only init captures existence/type for every runtime entry plus
both exact Java compile tasks' destination, executed/skip/failure/source states.
Only `game-modes/whodunit/build/classes/java/{desktopMain,desktopTest}` may be
missing, and only with complete matching `NO-SOURCE` evidence. All Kotlin/
resource directories and JAR inputs must remain present with unchanged types.
Unknown/missing inputs, symlinks, changed captures, and generic skipped tasks
still fail. The **original complete classpath string** is passed to Java; no
dependencies or empty-output entries are filtered out.

```sh
/usr/bin/python3 -B -m unittest discover \
  -s remediation-runs/2026-09-07-local-readiness/evidence/release-content-followup/canonical-identity-helper \
  -p 'test_classpath_validation.py' -v
```

The16 synthetic tests exercise validation only, not Gradle or Java execution.
The revised controls require root independent review and a real rerun. Public
`TaskState` accessors were inspected read-only in Gradle8.13's checked-in-wrapper
distribution (`getExecuted/getNoSource/getSkipped/getSkipMessage/getFailure`).
