# macOS x64 DNS failure and bounded evidence correction — November 11, 2026

## Original terminal result

PR336's authored `2ffa5d6d1d31d292310d0e03fc00c8c0bfc77cdb` passed full
[PR42693043730](https://github.com/Apdelrahman1911/parlor/actions/runs/42693043730),
executed test merge `4c322e5d2549f55e09299798f069bf87773b06c3`, tree
`6e35bea6bae1b676f8d5a0826e19980058d1f66c`. Terminal PR receipt SHA256:
`0f24c8dd66fdd9f68720650224ee784a5f9f0cb5ca9cb8496f37723273122f8c`.
Normal protected merge on November9 at21:37:22UTC produced actual main
`46fdc96dc0695a1b7f03b24dd781767e9fd008c1`, exact same tree and parents
`1e08bab421e366e8e25ddeaa3d1fbdfd28c2aecc` / `2ffa5d6d`.

Its automatic, distinct [push42704248797](https://github.com/Apdelrahman1911/parlor/actions/runs/42704248797)
attempt1 is terminal **FAILURE**, completed November9 at22:31:03UTC.
macOS x64 job129456721858 fails before Gradle or any test starts:

| Original observation | Retained result |
| --- | --- |
| Standard runner | `macos-15-intel`; macOS15.7.9/24G830; image `macos-15/20260824.0482.1`; runner2.338.2; Temurin21.0.12 |
| 22:01:31.475245UTC | Cache restore: `Failed to GetCacheEntryDownloadURL: Unable to make request: ENOTFOUND` |
| 22:01:34.796508UTC | First wrapper download starts at the pinned Gradle8.13 distribution URL |
| 22:02:05.463727UTC | `java.net.UnknownHostException: services.gradle.org`; original step exits1 |
| Original producer artifact13714855806 | Only ownership and Gradle-stop JSON; **zero test XML** |
| Original cleanup artifact13715200548 | PASS, no errors or retained owned outputs |

Original job-log SHA256:
`fd273d67e416b962411ca76e794bb8b68259770277cdb51883a9243fa3691761`.
Original producer ZIP SHA256:
`c3b72e456d082b0b01090c744194aa31876e2b4c046f0cdab88a99dff7f64433`.
Original cleanup ZIP SHA256:
`d5d05526676b83735fb061be828e3331118d8a1f3fcfaf176ba4056a8f744aeb`.
The failure-preservation receipt SHA256 is
`6f88ba23cdbbf322a4149902d41cae57945b5606555692a17136b1aa69096ec3`.
Originals and all eight scoped producer receipts are retained under the local
Git custody directory for this source/run; no failing producer was admitted and
no whole-main success receipt was generated.

This establishes the name-resolution boundary, **not its internal cause**.
The original resolver configuration/query was not captured. Cache and wrapper
errors do not prove a common internal cause. No application/runtime regression,
new iOS input cause or permanent infrastructure correction is inferred.

## Independently admitted scoped main evidence

- Android:34 API34 and3 optimized API35 original methods pass, no skips;
  **12 actual process cases/24 original phases**, all exact-PID instrumentation
  retirement receipts, pass. Unit variants782 each; GitHub-test variant120.
- Three completed Desktop hosts:2,053 methods each, **2,050 PASS/3 established
  single-JVM LAN skips**. The failed macOS x64 host contributes no test result.
- iOS:828 KMP/47 Swift/457 readiness methods pass; optimized Release simulator,
  device and x64 producers and both protected Apple joins pass.
- Release880 and worker80 controls pass with no hosted control skips. Their
  independent receipts are `8f53a395a09054a9c014b721f6b8b8d0272e3258e7c4ad1c32ea4f258f60856b`
  and `09b0953aab8d1ea05115d9a3eb5c62fbccd75c0c3a9865669b806687a2c56ed8`.
- All257 fresh uploaded synthetic PNGs per completed Desktop host decode.
  Exact-byte parity permits reuse of only22 earlier manual observations;
  visual-reuse receipt `1302937601e0282b55ad87da4d6fbad7e5cf566c0f4dc5e24be6a774655637dd`.
  This is not all-image manual review or physical/native UX acceptance.

None of these converts the failed whole main into success. #332/#335 stay OPEN
for complete resulting-main acceptance. #253's Android process evidence does
not satisfy its separate iOS persistence/security and physical acceptance.

## Supported, limited correction

[Issue338](https://github.com/Apdelrahman1911/parlor/issues/338) tracks the
missing failure evidence. No supported deterministic application/DNS change is
established by the retained data. Instead, the existing macOS x64 Gradle command
and all bounds/checks remain intact; a new step runs **only after that exact
original step fails**:

1. Validate the exact hosted repository/lane, failed outcome and numeric
   run/attempt identities. Refuse non-macOS execution or extra arguments.
2. Require clean tracked source, bind actual Git HEAD/tree and the collector/
   workflow SHA256s, and select one exclusive run/attempt/job receipt.
3. Read `/usr/sbin/scutil --dns` once and call Python's system resolver once
   for each of two fixed public hosts: `services.gradle.org` and `github.com`.
   Each subprocess has a5s caller bound; each retained stream has a32KiB bound;
   resolver addresses are deduplicated and limited to16. No shell or user-selected
   command/host is accepted. The entire workflow step is bounded to1minute.
4. Explicitly retain unavailable/nonzero/timeout observations and separately
   timed `OBSERVATION_ONLY_NOT_QUALIFICATION` scope. A later observation never
   claims to capture the original failed lookup or establish its cause.
5. Upload the receipt in the existing source/run/job-bound producer artifact,
   before attested cleanup. No successful-path work, native-input change,
   application warmup, Gradle replay, transport retry, DNS/hosts/proxy override,
   TLS/checksum bypass, pin change or qualification weakening is introduced.

The observation mechanisms are standard read-only macOS DNS reporting and
Python `socket.getaddrinfo`/bounded `subprocess.run`; a currently successful
public DNS lookup or a later green CI cohort is not a fix for the historical
failure. Upstream runner-images15252 concerns the independent original iOS
input/service boundary, not an established cause of this separate DNS failure.

The supported interfaces are documented in Apple's
[`scutil` manual source](https://github.com/apple-oss-distributions/configd/blob/main/scutil.tproj/scutil.8)
and Python's [`socket`](https://docs.python.org/3/library/socket.html#socket.getaddrinfo)
and [`subprocess`](https://docs.python.org/3/library/subprocess.html#subprocess.run)
references. The collector uses the existing system resolver, not a different DNS
server. A caller timeout is retained as an observation failure, not retried.

## Validation and remaining gates

The new original-command/failure-only/upload regression **fails before the
correction**. After correction, all124 focused controls pass, including14 new
controls for exact admission, command scope, bounded output, actual subprocess
timeout, failure preservation, source/task binding, exclusive non-replayed
receipts, workflow ordering and mutation rejection. Controlled resolver tests
make no real DNS request; Linux controls are not claimed as actual macOS
resolver acquisition. Resource-intensive local work uses the shared VPS gate.

The complete local release-control suite passes894 methods:893 PASS/one
established Linux procfs/subreaper-capability skip, with no new skip. The release
workflow contract passes. Strict `:shared:transport-p2p:desktopTest` executes391
methods:388 PASS/three established single-JVM LAN skips. These are local source
feedback, not protected, original macOS DNS or physical acceptance.

Fresh clean-source protected PR and distinct-main qualification remain pending.
The old failed run is not rerun or rewritten. No complete-main, physical,
original input-cause, positive Complete Protection or Store readiness is claimed.
Candidate `c3406ec3` stays held; owner-reserved physical Phase4 and all identity,
account/signing/provisioning/legal/privacy gates remain unchanged. No Issue is
closed by this source-authoring checkpoint.
