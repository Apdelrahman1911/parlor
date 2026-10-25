# RPC and mobile lifecycle assessment — 2026-10-25

## Scope and decision

Source inspected: Parlor `39aacd31` (PR #256), including app lifecycle bridges,
room retention/reconnect, foreground probes and authoritative session ownership.
This assessment does not certify the full application or newer commits.

**Retain Parlor's current protocol and dependency pins.** P2pKit RPC is genuinely
published, but does not fit as a small add-on to the existing game connection.
No evidence presently demonstrates that a second RPC connection or a complete
transport migration improves Parlor. Do not confuse this decision with a claim
that RPC is slower: no end-to-end comparative benchmark has been executed.

## Upstream facts, independently checked

- [PR #524](https://github.com/p2pKit/P2pKit/pull/524) is merged at
  `379670d5e04a81c63f8a91aa9db09defe29a1bb5`. It adds shared socket readiness
  and opt-in `host256()` capacity; it is **not** the introduction of RPC.
- Upstream explicitly states #524 functionality is **not present** in published
  `0.8.0-rc7`. Do not use source-head performance claims to qualify that artifact.
- [Maven Central metadata](https://repo.maven.apache.org/maven2/io/github/apdelrahman1911/p2p-rpc/maven-metadata.xml)
  returned HTTP 200, latest/release `0.8.0-rc7`, updated `20261023063934`.
  Published RPC versions start at `0.8.0-rc2`; Parlor's `0.7.0-rc3` has no RPC.
- The published rc7 Gradle module was fetched, not merely inferred from README.
  SHA-256: `28587dd7d12f752451280cd864085acad03de4a7296c746ae642980693385298`.
  It declares Android, JVM, iosArm64, iosSimulatorArm64 and iosX64 variants.
  This is publication/metadata evidence, not successful Parlor linkage/runtime.
- [RPC documentation](https://github.com/p2pKit/P2pKit/blob/379670d5e04a81c63f8a91aa9db09defe29a1bb5/docs/rpc/README.md)
  documents JVM 17, Android API 24+, iOS 14+, explicit typed serializers and
  `RpcHost.create`, `RpcClient.create`, `RpcProcedure`, `RpcReply`, `RpcFailure`,
  `RpcNotification` and authenticated `RpcCallContext.peer`.
- The facade owns a **dedicated kit**; arbitrary existing-kit injection is not
  supported. Core, LAN and RPC must use the same reviewed published version.
- [Strict organization-LAN policy](https://github.com/p2pKit/P2pKit/blob/379670d5e04a81c63f8a91aa9db09defe29a1bb5/docs/rpc/security-and-deployment.md)
  requires explicit interface/address/subnet selection and durable local trust.
  Android rejects hotspot/null-Network fallback and observed VPNs. Strict JVM
  rejects multiple active interfaces. iOS excludes cellular/AWDL and requires a
  verifiable selected interface. These restrictions are not interchangeable
  with Parlor's ordinary discovery/admission contract.

## Operations and integration cost

| Operation | RPC benefit | Parlor decision |
|---|---|---|
| Turn actions, voting, settings | Typed response, bounded correlation and infrastructure errors | Existing host reducer, command IDs and outcomes already supply game authority. Keep single connection; do not wrap with another retry layer. |
| Create/join/admission | Typed control requests after authenticated setup | RPC needs its own enrollment/trust before calls. It does not replace room-code admission or game-version validation. |
| State/probe requests | Natural request/response shape | Potential future candidate only after transport/version qualification; retain current fresh seat-bound probe and revision checks. |
| Rejoin and recovery | Bounded status recovery within a host incarnation | Not durable recovery. Keep saved identity/seat credentials and game revision reconciliation. |
| Snapshots, results, roster broadcasts | Typed notifications available | Keep event-driven, recipient-filtered delivery. RPC notifications are best effort and not replayed. |
| Hidden roles/cards | No automatic business isolation | Always authorize authenticated caller and construct only its public/private view. Never return host state. |

The [reliability contract](https://github.com/p2pKit/P2pKit/blob/379670d5e04a81c63f8a91aa9db09defe29a1bb5/docs/rpc/reliability.md)
uses one monotonic call deadline (10 seconds default, 30 maximum), bounded calls,
volatile deduplication and status recovery. Cancellation is **not rollback**;
`MayHaveExecuted` must not cause a new non-idempotent gameplay command. Host
restart changes incarnation and loses request records. Concurrent handlers can
finish out of order, so a Parlor reducer still requires serialized authority.
RPC does not solve host migration, process-death restoration or iOS suspension.

A safe future prototype must live outside shipping dependencies, pin the complete
published graph, review checksums/provenance and maintain application command IDs.
Do not add `mavenLocal`, a sibling build, an unpublished source override, alternate
crypto, per-client blocking threads or a second automatic-retry policy.

## Benchmark status and reproducible acceptance plan

**Not measured:** comparative latency, allocation, CPU, RSS, thread count, wire
bytes, battery, thermals or physical recovery. A Python/serializer-only loop
would not establish real encrypted-session or mobile-host performance.

Upstream #524 reports Linux virtual-link 128/256-client measurements, including
post-load RSS above 500 MiB; these are upstream, unpublished-source observations,
not measurements performed here and not a Parlor/ART/iOS comparison. Their scale
is unlike a six-game party room and cannot justify migration.

Before reconsidering, compare the exact same 4/8/16-player workloads on both
implementations: 1 KiB actions at 1 and 10 Hz, recipient-specific 4/32 KiB
snapshots, simultaneous votes, 30-minute idle/run phases, response loss, duplicate
commands and host/peer reconnect. Record hardware, OS, source/artifact hashes,
JIT warmup, p50/p95/p99 acknowledgement time, wire bytes, CPU time, peak/steady
RSS, allocation rate, process threads, battery/thermal measurements and final
resource cleanup. Require identical reducers, privacy and accepted-action counts.
A host restart or lost reply must never produce a second side effect. Compare
Android and iOS in both roles on physical LAN, not only JVM loopback.

## Current lifecycle behavior

- Android process visibility tracks started Activity owners and avoids a false
  network restart during configuration replacement. Window focus and resume
  state separately conceal private content.
- iOS `.inactive` conceals content without immediately stopping LAN; true
  `.background` invokes the shared room policy. SwiftUI also covers app-switcher
  content and hides it from accessibility.
- Established, validator-ready connections receive **at most 15 seconds of
  best-effort socket retention**, not an OS execution entitlement. Host
  admissions stop; local game commands remain blocked. A fresh bounded
  round-trip validates a return, rather than trusting cached `Connected`.
- Known connection loss or elapsed grace causes hard suspension. The original
  120-second logical recovery deadline is preserved; delayed timers must not
  extend it. Rejoin is seat/credential based, not a new anonymous seat.
- New/replacement rooms created while already backgrounded receive no fresh
  retention grace. Explicit leave and stale generations cannot revive a room.

No foreground service, wake lock, background-task entitlement or host migration
was added by this assessment. Neither continuous background availability nor
complete process-death recovery is claimed.

## Official platform guidance and implementation choices

### Android

[Foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
permit `connectedDevice` for interaction with external devices over a network,
subject to `FOREGROUND_SERVICE_CONNECTED_DEVICE`, general service requirements
and a qualifying permission. Existing `CHANGE_WIFI_MULTICAST_STATE` is one listed
prerequisite; that alone does not establish Play-policy approval or robust runtime.

[Android 12+ background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
mean a service cannot simply be started after every background callback. An
optional, explicitly user-started **host-only** connected-device service is a
reasonable future experiment: start while visible, display ongoing host status,
provide Stop, stop on final leave/expiry, handle start refusal and notification
permissions, and avoid restart-on-boot or silent sticky resurrection. Clients
normally need recovery, not a permanent service. Platform/Play declaration and
manufacturer-specific testing remain necessary before enabling this policy.

[Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
can suspend network access, ignore wake locks and defer jobs. WorkManager uses
JobScheduler and is not a live LAN server or heartbeat facility. A foreground
service is not a guarantee against all network restrictions, process termination,
user Stop or OEM battery policy. Never request blanket battery exemption just
to mask recovery defects. Persist essential state incrementally, not only at
`onStop`; Android may kill a process without a final cleanup callback.

Target 36 is pinned currently. Recheck newer-target local-network permission
requirements before raising target SDK; do not inherit upstream future-platform
statements without matching the target/runtime and current official guidance.

### iOS

[UIKit background execution](https://developer.apple.com/documentation/uikit/extending-your-app-s-background-execution-time)
provides about five seconds for background transition work before suspension in
the ordinary case. A bounded `beginBackgroundTask` may finish critical saving or
cleanup; start before that work, end on completion, handle expiration and cancel
outstanding work. It must not become a 15-second keepalive timer or indefinite
LAN-server workaround. The current 15-second retention must remain explicitly
best effort; it does not guarantee the app runs for that interval.

[Background strategies](https://developer.apple.com/documentation/backgroundtasks/choosing-background-strategies-for-your-app)
make BGTaskScheduler system-scheduled maintenance, not immediate multiplayer
execution. Background URLSession is file transfer, not arbitrary LAN sockets.
Do not declare audio, location, VoIP or unrelated background modes to keep games
alive. Bonjour/Network.framework do not grant indefinite execution.

[`BGContinuedProcessingTask`](https://developer.apple.com/documentation/backgroundtasks/bgcontinuedprocessingtask)
is for user-started foreground work continued with visible progress/cancellation;
resource pressure can terminate it. An idle open game has no bounded completion
progress, so this is not a legitimate replacement for LAN host recovery. Also
retain fallbacks for Parlor's iOS 16 deployment floor.

Host suspension can affect every seat; client suspension affects only that
client. Preserve different user messaging and recovery paths. Host migration
remains out of scope: distributing authoritative secrets and transferring a
trusted identity requires a new security protocol, not choosing another socket.

## New executable coverage and outstanding evidence

`AppLifecycleFaultInjectionTest` adds deterministic injected retention/validation
exceptions, 100 duplicate background signals, clock rollback and 100 rapid
background/foreground cycles followed by timer expiry. Assertions check one
reported failure, preserved original suspension time and absence of stale expiry
jobs. These tests extend exception/stress coverage; they are not hardware tests.

Run focused checks with the repository JDK 21/wrapper/strict verification:

```sh
./gradlew :shared:transport-p2p:desktopTest \
  --tests '*AppLifecycle*Test' --tests '*BackgroundConnectionRetentionTest' \
  --dependency-verification=strict
```

Execution status is recorded by the integrating PR/test report; merely adding
this command is not a passing receipt. Retention, retention timeout, leave races,
failed validator and delayed scheduler cases already have independent existing
coverage. No confirmed physical lifecycle bug is asserted from source review.

Environment inspection: Linux x86_64, no `xcodebuild`; `adb devices -l` returned
an empty list. Incoming calls, lock, Wi-Fi changes, low-power mode, OS suspension,
memory pressure, force-quit, simultaneous reconnections, battery and thermal
qualification are **unexecuted**. Existing #252 (background/reconnect), #6 (real
LAN) should retain these acceptance gates; do not close them because
deterministic tests pass. #230 separately tracks Store identity/ownership. Strict iOS protection finding A37
also remains unresolved and must not be weakened for background availability.
