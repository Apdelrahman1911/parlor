# Guarded Darwin host ABI and native capability control

## Confirmed integration failure — #270

Intel comparison [40545185630](https://github.com/Apdelrahman1911/parlor/actions/runs/40545185630)
fails all three Release ownership preparations at `0cd9a9dc` before Gradle
compilation. `DarwinWorkerBackend.__init__` admits only `Darwin/arm64`, although
the benchmark runner selector permits standard `macos-15-intel`. The fail-closed
cleanup/join behaves correctly. This is not an Intel build-speed measurement.

## Reviewed implementation

The backend now explicitly admits only `Darwin/arm64` and `Darwin/x86_64`.
Before loading either native library it validates LP64 scalar sizes, the full
136-byte/eight-byte-aligned `proc_bsdinfo`, every field offset, four-byte argc,
and eight-word/32-byte audit token. An unsupported host or layout fails closed.
The existing **actual self-identity/audit-token capability** remains required;
layout validation alone cannot establish permissions or authorize cleanup.

Signals still re-read complete stable process identity and use
`proc_signal_with_audittoken`. UID, lifetime, executable, audit generation,
baseline exclusions, exact runner marker, resource ceilings and output custody
are unchanged. There is no arbitrary PID/PGID signal fallback or global kill.

Apple-owned XNU headers retrieved October27,2026:

| Header | SHA256 |
| --- | --- |
| [proc_info.h](https://github.com/apple-oss-distributions/xnu/blob/main/bsd/sys/proc_info.h) | `f898ba1f6acecf128f6bb960bf07c7029fdd936620e03b6206419834cf2941fa` |
| [task_info.h](https://github.com/apple-oss-distributions/xnu/blob/main/osfmk/mach/task_info.h) | `1c09d3ff0bb37b4c17da286f011c8ceb61962f610bd1304688bec4b90c556b81` |
| [message.h](https://github.com/apple-oss-distributions/xnu/blob/main/osfmk/mach/message.h) | `ad64b7b413b42e196dc43a54a5978aa049b9133db1fe9369893f12ad4718c615` |

Public fixed-width headers support the expected layout; they do **not**
substitute for actual hosted ABI/permission execution or a Release build.

## Supplemental real-host control

Explicit `verification_scope=darwin-host-capability` runs only two bounded
matrix jobs: standard `macos-15` and `macos-15-intel`. It is not a required
build gate and cannot replace, disable or report success for any full lane.
Default full qualification and the original required checks are unchanged.

```bash
gh workflow run production-verification.yml --repo Apdelrahman1911/parlor \
  --ref <frozen-branch> -f verification_scope=darwin-host-capability \
  -f frozen_source_sha=<exact-40-character-HEAD>
```

The driver binds the actual hosted run/attempt, repository, workflow SHA,
clean source/tree, fixed architecture and fresh external receipt. It constructs
the actual native backend, then launches exactly one30-second direct `/bin/sleep`
child with the **existing** inherited runner marker and no credentials/hooks.
Success requires stable UID/lifetime/executable/audit-token identity, exact
tracking attestation, token-bound SIGTERM and reaping with exit `-SIGTERM`.

Cancellation and native failures remain failures. Defensive cleanup owns only
the actual unreaped `Popen` handle, uses bounded TERM/KILL/wait, and cannot
convert failed native evidence into success. Receipts contain only public
ABI/source/run/status metadata and error types: no environments, argv,
tracking values or private process identities. Failed receipts are retained;
uploads must succeed. No simulator, Xcode build, Gradle task or app is started.

## Verification and next gates

Local synthetic controls cover both layouts, every offset, unsupported hosts,
library-load ordering, mandatory self-capability, identity/marker mutations,
signal denial, spawn-window cancellation, reaping deadlines, redaction, source
binding and stale receipts. Windows tests explicitly emulate Darwin LP64;
Windows host layout is never accepted as native evidence. Before-fix evidence
retains8 failures/28 errors across five new/expanded methods. After-fix local
controls pass; macOS execution remains pending at implementation time.

Required next steps: inspect both real-host receipts, then freeze and run full
ARM64 and Intel comparisons at identical new source. Preserve all optimized
architectures, signing restrictions, strict dependency verification, runtime,
artifact/native/output cleanup and original aggregate check. Retain ARM64 if
Intel cannot qualify securely. No physical-device, protection, signing, battery,
thermal, Store-release or fastest-configuration certification is claimed here.
