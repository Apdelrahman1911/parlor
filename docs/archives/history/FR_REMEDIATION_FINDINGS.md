# Production-review remediation register

Document status: historical remediation ledger for the review that began at
`37a249676fd8d6de109800cd136352bdd55e32ee`. It preserves traceability for that
branch, but its CLOSED rows and readiness language are not current-HEAD
evidence. The independent review must derive its verdict from source inspection,
the mechanically complete inventory, and exact-final-HEAD verification.

The protected remediation baseline is `37a249676fd8d6de109800cd136352bdd55e32ee`.
Work is isolated on `codex/parlor-fr-remediation`; the pre-remediation
checkpoints `8186f7d70786057b791bd5c1aa80ca868835ec37` and
`dff3fcb317fdb89e310db70eb2c44643672c8c6b` remain reachable. This register is
maintained from code, tests, and commits. A status word is not closure evidence:
each finding is closed only after its dedicated regression test, broader
verification, exact fix commit, and final exact-HEAD gate are recorded.

## Current register

| ID | Severity | Root cause | Fix commit | Dedicated evidence | Status after exact final gate |
|---|---|---|---|---|---|
| FR-01 | High | The shell catalog was registry-shaped, but app/local/multiplayer/resume dispatch still contained game-specific branches. | `e2109cd`, `325b302`, `6a9656b` | `GameShellRegistryExtensibilityTest`, `verifyGameShellDispatch`, `:composeApp:desktopTest` | CLOSED |
| FR-02 | Medium | Recovery checks did not encode the full phase/roster/terminal invariants for Mafia and Whodunit. | `cbd1663` | `MafiaSnapshotRecoveryTest`, `WhodunitSnapshotValidationTest`, reducer-trace fixtures | CLOSED |
| FR-03 | Medium | Peer installation checked privacy redaction but not complete game-specific reachability and phase shape. | `cbd1663` | `MafiaPeerPrivateTargetGateTest`, `WhodunitPeerProjectionBoundaryTest`, full shape tests | CLOSED |
| FR-04 | Medium | Detekt was versioned but not applied to every production subproject or enforced by the release umbrella. | `5efca1a`, `83b6a79`, `681f594` | `staticAnalysis`, XML reports, CI contract test, catch-site review enforcement | CLOSED |
| FR-05 | Medium | CI did not enforce the same Android, Apple, static, artifact, provenance, and contract matrix as local release verification. | `f76c285` | `ProductionVerificationWorkflowContractTest`, workflow jobs, root task graph | CLOSED |
| FR-06 | Medium | Operational docs and the contract test still described protocol 3.1 while runtime headers were 4.0. | `d8c453b` | `MultiplayerDocumentationContractTest`, protocol/start-handshake tests | CLOSED |
| FR-07 | Medium | Whodunit `SubmitStructuredAction` was serialized and authorized but its reducer branch silently returned unchanged state. | `fd46a6e` | action codec rejection, authority negative tests, no-production-reference scan | CLOSED |
| FR-08 | Low | Release lint was not triaged as a gate and concrete icon/resource warnings were left unresolved. | `ba1ff74`, `9384006` | `AndroidReleaseLintContractTest`, forced exact-inventory `verifyReleaseLintWarnings` | CLOSED |
| FR-09 | Low | Unreachable spectator resources and obsolete Phase 0/version-catalog commentary remained in the active shell/resources. | `ba1ff74` | resource reachability/locale parity contract and diff audit | CLOSED |
| FR-10 | Low | Optional case payloads contained placeholder author metadata without verified attribution. | `30bdf7b` | content payload hardening test and case validation | CLOSED |
| FR-11 | High | Neutral shell/session helper files imported Whodunit types and embedded Whodunit route/start semantics, so a second game could not own the contract. | `325b302` | fixture registration, shell dispatch scan, route/version assertions | CLOSED |
| FR-12 | Medium | Case-list summaries and content IDs were treated as trusted after decoding; malformed manifests could reach picker/cache and unsafe IDs were interpolated into URLs. | `bf71c10`, `83b6a79` | `CaseSummaryValidatorTest`, `OfflineRemoteCaseDataSourceTest`, `CasePickerDiscoveryTest`, strict Detekt | CLOSED |
| FR-13 | Medium | `BoundedPeerOutbox` cleared its terminal transaction as soon as the worker dequeued it, allowing concurrent shutdown callers to enqueue duplicate terminal frames; later review also found a close-versus-terminal-publication race. | `d281140`, `90a3014` | terminal idempotency tests, deterministic close-before-publication race, bounded queue/close tests | CLOSED |
| FR-14 | Medium | Mafia's snapshot codec accepted permissive JSON and structurally impossible public states, unlike the Whodunit codec. | `31783c1` | `MafiaSnapshotCodecTest` valid round trips and encode/decode rejection fixtures | CLOSED |
| FR-15 | High | `DefaultCaseRepository` cached a remote envelope before typed validation, returned corrupt legacy cache entries without fallback, and later still trusted fetched envelope identity plus shape-only refresh validation. | `57c9475`, `ac9da45`, `0c1ac6e`, `336f69a` | cache trust/fallback tests, exact requested-identity tests, typed refresh validation, direct-envelope boundary tests | CLOSED |
| FR-16 | High | The registry UI collapsed host and join into one capability and its fixture exercised factory construction without proving registered content could actually compose. | `6a9656b`, `70bac8e` | independent play-mode availability tests and Desktop-runtime fixture binding composition test | CLOSED |
| FR-17 | High | A remote/cache source could return a valid envelope for a different requested case, and refresh used common shape checks rather than the game-owned payload validator. | `336f69a` | substitution, corrupt-remote fallback, typed-refresh, manifest/fetch identity, and unsafe-envelope tests | CLOSED |
| FR-18 | Medium | `BoundedPeerOutbox.close()` could win between terminal dequeue and terminal-state publication, leaving a late terminal caller attached to work after closure. | `90a3014` | deterministic close-before-terminal-publication regression plus full session tests | CLOSED |
| FR-19 | Medium | Repository-wide Detekt executed, but `TooGenericExceptionCaught` was globally disabled, so new broad exception boundaries could bypass the required explicit review. | `681f594` | forced `staticAnalysis` across every production module and zero unreviewed generic catches | CLOSED |
| FR-20 | Medium | The Android lint gate allowed any warning carrying one of four IDs, so a new coordinate, source, current version, or duplicate warning could be silently accepted; its documented count had already drifted. | `9384006` | exact 42-entry multiset gate, configuration-cache run, and `AndroidReleaseLintContractTest` | CLOSED |
| FR-21 | Medium | The first real Compose fixture was placed in `commonTest`; Android local unit tests use the Android Compose runtime and failed on the unmocked `android.os.Trace` cleanup path even though Desktop passed. | `70bac8e` | `composeApp` Desktop, Android debug, and Android release shell-test variants | CLOSED |

## Finding details and acceptance matrix

### FR-01 / FR-11 — registry and app-shell extensibility

Root cause: `App.kt`, home/local-resume, and shared multiplayer helpers were
initially the effective router and contained Whodunit/Mafia branches even though
`GameRegistry` existed. The first registry change still left Whodunit-specific
host/peer/case-picker files in neutral shell packages.

Target behavior: the shell resolves a `GameId` to a binding and delegates catalog,
local, host, peer, resume, content, player bounds, and navigation to that
binding. Shared networking/session code receives only transport-independent
contracts. A fixture game can be registered and exercised without editing a
central game switch.

Affected areas: `composeApp/src/commonMain/kotlin/com/parlor/app/App.kt`,
`composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/*`, the Whodunit
binding files under `composeApp/.../shell/game/whodunit`,
`shared/engine-testing/.../RoundRobinAnnounceGame.kt`, and
`shared/engine-testing/.../GameShellRegistryExtensibilityTest.kt`.

Compatibility: game IDs, game versions, persisted snapshot identifiers, and the
P2P protocol are unchanged. The binding contract is internal; existing game
modules keep their public definitions. A future game adds a module and binding,
not a shared router branch.

Failure handling: duplicate IDs, blank IDs, unsupported versions, unsupported
player counts, missing multiplayer contracts, and invalid resume routes fail
closed. A missing fixture binding returns no route rather than another game's
screen.

Acceptance: the fixture registers, appears in the catalog model, exposes entry
modes and bounds, starts locally, round-trips a local snapshot, resolves local
resume, restores an owned host/peer route, and passes without a new game token in
neutral shell or shared multiplayer files.

### FR-02 / FR-03 — recovery and peer snapshot invariants

Root cause: privacy-safe decoded data was not enough to prove that phase,
roster, votes, roles, terminality, and private slices could have been produced
by the reducer.

Target behavior: every local recovery and incoming peer snapshot is decoded with
strict schema rules, checked against the game-specific reachable-state
invariants, and installed atomically only after the complete public/private
projection validates. Rejection leaves the prior state and revision untouched.

Affected areas: Mafia/Whodunit state validators, snapshot codecs, recovery
loaders, peer room bridges, and their desktop fixtures.

Compatibility: existing valid legacy Whodunit snapshot migrations remain
supported; Mafia log trimming remains bounded. Invalid or cross-game records are
intentionally rejected as corrupted data rather than repaired silently.

Acceptance: legal reducer traces for every phase and terminal state are
accepted; one-invariant mutations, wrong game/session, stale revisions,
malformed payloads, privacy-safe but impossible projections, and partial
terminal progress are rejected; failed installation cannot advance the peer.

### FR-04 — enforced static analysis

Root cause: Detekt was declared but modules could omit application and CI had no
enforced repository-wide task.

Target behavior: the root applies `parlor.detekt` to every subproject, scans all
Kotlin source sets, emits reports, and `productionCheck` depends on the aggregate.
No blanket baseline suppresses an actionable finding.

Acceptance: `./gradlew staticAnalysis --dependency-verification=strict --rerun-tasks`
executes every production module and exits zero; CI uploads the reports and a
protected-branch workflow cannot omit the task.

### FR-05 — CI/release truth

Root cause: the workflow could be green without the complete local release
matrix or artifact/manifest evidence.

Target behavior: Linux enforces strict dependency verification, all module tests,
production checks, Detekt, Android debug/release tests, release Kotlin, lint,
R8, AAB inspection, merged manifest, and hashes. macOS runs executable simulator
tests where supported, all three Apple release link targets, plist/privacy
validation, and the unsigned Swift Release wrapper. Linkage is never described
as runtime testing.

Acceptance: `ProductionVerificationWorkflowContractTest` passes and the normal
workflow jobs fail when any required task or artifact inspection is removed.

### FR-06 — protocol 4.0 documentation contract

Root cause: operational docs and manual instructions drifted from runtime
constants and the reliable start barrier.

Target behavior: current docs derive the version claim from
`PARLOR_PROTOCOL_MAJOR/MINOR`, describe `SessionStarting` → `SessionStartReady`
→ `SessionStartCommitted` → `SessionStartCommitAck`, snapshot gating, command
acknowledgements, deadlines, stale revisions, and unsupported legacy behavior.
Historical documents are visibly historical before old details.

Acceptance: `MultiplayerDocumentationContractTest` passes; repository search has
no obsolete 3.1 positive claim in current documents; a future runtime version
change breaks the contract test until docs are updated.

### FR-07 — structured-action contract

Root cause: an active wire action had no reducer semantics and silently returned
the same state.

Target behavior: structured actions are not part of the current Whodunit shipping
contract. New codecs and production references reject them explicitly; only
narrowly versioned legacy decoding remains where needed, and legacy input cannot
mutate state.

Acceptance: encode/decode negative tests, authority tests, and a production
reference scan pass; no authorized serialized action can be an unreported no-op.

### FR-08 / FR-09 — lint, assets, and stale resources

Root cause: concrete adaptive-icon, monochrome, duplicate-resource, obsolete
attribute, SDK qualifier, and catalog warnings were mixed with advisory update
warnings; stale spectator strings were not proven reachable.

Target behavior: all correctness/packaging warnings are fixed; the verifier
allows only the reviewed dependency/SDK advisories and records each reason.
Adaptive icons are declared for normal and round launchers with a monochrome
layer; locale keys match and no unreachable production strings/spectator assets
remain.

Acceptance: forced `:composeApp:verifyReleaseLintWarnings` passes, the generated
XML contains only the documented advisory IDs, and `AndroidReleaseLintContractTest`
passes.

### FR-10 — optional attribution metadata

Root cause: bundled Whodunit JSON contained placeholder author values without a
verified attribution requirement.

Target behavior: optional author metadata is absent until a verified attribution
source exists; content validation and runtime payloads do not invent identities.

Acceptance: all shipping case payloads pass the content validator and contain no
placeholder author fields; a future attribution addition requires a source and
content review.

### FR-12 — remote manifest and URL trust boundary

Root cause: a decoded summary list was passed directly to UI/cache and IDs were
used in URL paths without an allowlist.

Target behavior: list responses are byte-bounded, registry/game/mode/player/
language/version/length/duplicate validated, and unsafe IDs are rejected before
request construction. Invalid remote data falls back safely and never poisons
the cache.

Acceptance: malformed list, wrong-game, duplicate, path-traversal, oversized,
unsupported-mode, and app-version fixtures fail closed; valid lists continue to
drive the picker.

### FR-13 — terminal outbox idempotency

Root cause: the queue marker and terminal transaction shared one nullable field;
dequeueing erased the transaction before the send completed.

Target behavior: one terminal transaction is retained for all duplicate callers,
only one bounded send occurs, and close cancels a queued/in-flight completion so
no caller waits forever.

Acceptance: deterministic blocked-send concurrency test observes one attempt and
identical outcomes; existing bounded queue, timeout, parallel-peer, and sanitized
failure tests remain green.

### FR-14 — Mafia snapshot codec trust boundary

Root cause: Mafia used caller-provided permissive JSON and did not validate the
decoded public state, unlike Whodunit.

Target behavior: strict JSON is used for the current codec; bounded logs are
normalized and observable phase/roster/vote/terminal invariants are enforced on
both encode and decode.

Acceptance: valid setup/night/voting/terminal/history round trips pass and
impossible encode/decode fixtures fail with no state returned.

### FR-15 — content cache trust and fallback

Root cause: remote data was written to cache before game payload validation, and
a corrupt legacy cache entry short-circuited remote/bundled fallback.

Target behavior: typed validation precedes cache writes. Refresh validates the
common envelope and exact list identity before warming the cache; opening a
corrupt cached record invalidates it and tries remote, then bundled content.

Acceptance: invalid remote payloads produce no cache write, invalid refresh
envelopes produce no warmed record, valid remote content is cached once, and a
corrupt cache record is invalidated before a successful remote fallback.

### FR-16 — independent registry entry capabilities

Root cause: the shell binding exposed independent host and join capabilities,
but `PlayModePickerScreen` reduced them to one `multiplayerEnabled` boolean.
The non-shipping fixture test also stopped at creating a content lambda, so it
could pass even if registered fixture content failed during composition.

Target behavior: Solo, Pass & Play, Host, and Join are resolved independently
from the selected game binding. The fixture binding is actually composed under
a deterministic test composition, not merely returned as a function object.

Acceptance: every capability combination produces the corresponding enabled
entries; a third fixture game renders its registered content; neither central
shell dispatch nor networking gains a fixture/game-specific branch.

### FR-17 — fetched case identity and typed refresh validation

Root cause: repository fallbacks validated envelope shape and typed payload but
did not bind a fetched/cached envelope to the exact `CaseId` requested by the
caller. Refresh accepted the common envelope shape without invoking the
game-owned typed payload validator and could warm a substituted record.

Target behavior: every source result must match the requested registry, game,
case, and version identity before it can be returned or cached. Both `getCase`
and `refresh` use the same game-owned payload validator. A rejected remote
record falls through to the next authoritative source and cannot poison cache.

Acceptance: cross-case substitution, manifest/fetch mismatch, malformed typed
payloads, unsafe direct envelopes, and advertised-but-unavailable records fail
closed; valid cache, remote, and bundled flows continue to work.

### FR-18 — atomic outbox closure

Root cause: terminal lifecycle ownership was published separately from queue
dequeue. `close()` could observe no terminal state after the worker had taken
the item but before it published the in-flight transaction, allowing callers to
attach to work owned by an already closed outbox.

Target behavior: one atomic lifecycle state owns Open, Ending, and Closed.
Closure atomically prevents new enqueues and cancels the exact queued/in-flight
terminal completion. Every caller completes with one bounded outcome.

Acceptance: a deterministic hook pauses immediately before terminal-state
publication; closing at that point makes the terminal caller return
`NotConnected`, sends no terminal frame, and leaves no suspended completion.
All prior idempotency, timeout, byte-bound, and peer-isolation tests remain green.

### FR-19 — broad-catch static-analysis enforcement

Root cause: Detekt was applied and executed, but the rule governing generic
`Exception`/`Error` catches was disabled globally. A new broad catch therefore
received no review signal even though cancellation and boundary translation are
security/lifecycle relevant.

Target behavior: `TooGenericExceptionCaught` is active, including for unnamed
catch parameters. Each intentional translation/cleanup boundary is approved at
the catch parameter, and suspending boundaries retain explicit cancellation
propagation. There is no module-, file-, or global baseline waiver.

Acceptance: forced `staticAnalysis` executes every production module and passes;
repository search finds no generic production catch without its exact-site
review; introducing a new unreviewed generic catch fails Detekt.

### FR-20 — exact Android lint inventory

Root cause: the release task compared only warning IDs. Once an ID was allowed,
new instances for another dependency, source, current version, or duplicate
count were accepted automatically. The documented 39-warning total had already
diverged from the generated 42-warning report.

Target behavior: the executable inventory records a multiset of warning ID,
repository-relative location, stable message prefix, and count. Only the
volatile latest-available-version suffix is ignored. XML external entities are
disabled and configuration-cache compatibility remains enforced.

Acceptance: a forced lint run produces exactly the 42 reviewed advisories and
passes with configuration cache enabled; any added, removed, relocated, or
current-version-changed warning fails; the repository contract test validates
the inventory categories and count.

### FR-21 — platform-correct Compose fixture execution

Root cause: a test that executes the Compose runtime was placed in
`commonTest`. Android local JVM tests then selected the Android runtime, whose
`android.os.Trace` implementation is not available without an Android runtime;
the test failed during deterministic composition disposal. Enabling global
default Android stub values would have hidden other accidental framework calls.

Target behavior: pure registry/capability/route contracts remain in
`commonTest` and run for all configured targets. The actual runtime composition
proof lives in `desktopTest`, where a real host runtime is available, and still
resolves the binding through the production registry/router before rendering.

Acceptance: the shell package tests pass in Desktop, Android debug, and Android
release variants without Robolectric, mocked Android framework defaults, leaked
recomposer jobs, or a weaker factory-only assertion.

## Verification and closure rules

Focused phase commands are recorded in commit messages and the final report.
The final exact-HEAD run must include, at minimum:

```text
./gradlew productionCheck productionAppleCheck allTests --dependency-verification=strict --rerun-tasks --no-daemon --console=plain
./gradlew :composeApp:verifyReleaseLintWarnings --dependency-verification=strict --rerun-tasks --no-daemon --console=plain
./gradlew :shared:content:desktopTest :shared:session:desktopTest --dependency-verification=strict --rerun-tasks --no-daemon --console=plain
./gradlew :game-modes:whodunit:desktopTest :game-modes:mafia:desktopTest --dependency-verification=strict --rerun-tasks --no-daemon --console=plain
git diff --check
git status --short --branch
```

Apple simulator execution is reported only where the host can execute it;
`iosX64` linkage is not a runtime pass on Apple Silicon. Signed artifacts,
physical LAN/hotspot behavior, app-store review, accessibility, privacy, legal,
and compliance remain external gates. The historical verdict below must not be
used as the current independent-review verdict.
