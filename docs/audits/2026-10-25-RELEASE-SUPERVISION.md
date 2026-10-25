# Distribution subprocess ownership and bounded diagnostics

## Scope

Issue [#257](https://github.com/Apdelrahman1911/parlor/issues/257). Release-only
follow-up to PR #256, based on `afe783838461afae1029f0369280a1bafadc395f`.
Changes affect external-tool supervision, standalone wrapper builds, diagnostics
and their tests. They do not change gameplay, transport, signing trust, publishing
authorization, required checks, dependency pins or Store workflow enablement.

## Confirmed behavior and corrections

1. **Ordinary descendants survived direct-process cleanup.** An isolated baseline
   Python parent timed out after0.2s; its child still wrote a marker after0.5s.
   The supervisor now creates an isolated POSIX session and retires its ordinary
   process group even after successful early root exit. Windows uses a private
   supervisor assigned to a kill-on-close Job Object **before** spawning tools;
   setup failure launches nothing. No global process-name/PID sweeping is used.
2. **Build diagnostics were unbounded in memory.** `desktop_package.gradle`
   wrote an unbounded file then called `read_text()[-18000:]`, allocating all
   content to show a tail. It now uses shared supervision, retains logs on disk,
   monitors a generous256MiB build-log budget, and seeks/reads at most18,000bytes
   for diagnostics. Ordinary captured-tool output has a16MiB limit and an explicit
   bounded final read. Output is checked after descendant retirement as well as
   during execution. The monitored disk threshold is not a filesystem quota:
   a fast writer can overshoot between50ms polls, then the operation fails.
3. **Gradle single-use daemons detach.** Pinned Gradle8.13 bytecode confirms
   `DaemonMain.doAction` calls `ProcessEnvironment.maybeDetachProcess`; thus
   `--no-daemon` alone does not imply wrapper process-group ownership. An actual
   standalone JDK21/Gradle8.13 task left its daemon alive with PID=SID=PGID and
   PPID1 immediately after wrapper timeout. A repeated control observed automatic
   exit by+1s: **no persistent leak beyond that interval was demonstrated**.
   Builds now allocate a fresh private daemon registry and pass the exact same
   absolute `org.gradle.daemon.registry.base` to the build and a bounded final
   `--stop`. This is invocation-scoped, not the prohibited global stop. The
   property is verified against8.13 `DaemonBuildOptions.BaseDirOption`; changing
   Gradle requires requalification. Explicit in-process Kotlin compilation avoids
   a separate compiler-daemon policy on local packaging calls as well as CI.

Shared artifact/wrapper caches remain unchanged. Strict dependency verification
and `--no-daemon` remain present. Production build and private registry/stop logs
are retained, including cleanup failures; later existing owned-build-output
cleanup can retire them. They never enter frozen/public distribution bundles.
A failed/expired scoped stop makes the operation fail rather than silently
claiming cleanup. Deliberately session-escaping non-Gradle tools still need their
own reviewed lifecycle; generic process groups are not a sandbox.

## Verification

Fresh release-only source: `18ec20f2` (before report/inventory-only changes).
The following exact command ran on Linux x86_64, Python3.12, JDK21 and the pinned
Gradle8.13 wrapper:

```bash
python3 -m unittest \
  scripts.release.tests.test_distribution_process_ownership \
  scripts.release.tests.test_gradle_daemon_registry \
  scripts.release.tests.test_github_distribution \
  scripts.release.tests.test_github_test_release \
  scripts.release.tests.test_workflow_contract \
  scripts.release.tests.test_android_test_package \
  scripts.release.tests.test_distribution_image -q
```

**166 tests PASS in60.684s**, zero failures. `workflow_contract.py` also passed.
The negative duplicate-ZIP fixture emits its expected duplicate-entry warning.

Coverage includes timeout, excessive output, Python cancellation, successful
root exit, TERM-resistant descendants inheriting output, unrelated survivors,
retained failed logs, bounded sparse-file tail reads, output written during
finalization, and cleanup failures. Mutation contracts forbid skipping the
mandatory Windows native process step or treating its failure as success.

The real wrapper test uses two/three exclusively owned standalone fixture
projects, no application build/plugins/downloaded dependencies, offline
resolution and256MiB test heaps. It verifies:

- a real single-use daemon executing a task is retired after the build timeout;
- a positive control sends scoped Stop to a **busy daemon with its client still
  connected**, and observes process termination within5s;
- another separately registered idle Gradle daemon remains alive through both;
- final fixture cleanup stops only those private registries, checks actual process
  absence/non-running state, then removes only its exclusive temporary directory.

Independent review checked source/test ownership scope and corrected a Windows
inspection ambiguity: access denied is an inspection failure, not proof of exit.
No raw PID signaling is authorized by these observations.

## Remaining acceptance

- Run the new mandatory Windows native Job Object suite at the release-only PR's
  exact source; local mocked Win32 contracts do not count as native execution.
- Run existing required full CI/platform gates and review their actual outcomes.
- A synthetic standalone wrapper test is not a complete packaging/signing runtime
  rehearsal, macOS device result, or production distribution authorization.
- No Store release, protected signing operation, environment mutation or bypass
  was performed for this workstream. Keep #257 open until integrated qualification.
