# Home Resume membership ownership — October 28, 2026

## Finding and scope

The game/version safeguard does not identify a particular saved membership.
A controlled real-adapter/fake-kit experiment reads Home metadata, commits a
replacement with the **same game, version, name and host** but a different
membership, then invokes the former game-only Resume. The kit is created once
instead of the expected zero. The original failed method/log remain retained.
This confirms missing caller context at this API boundary, **not a reproduced
user-reachable mobile race, unauthenticated admission or hidden-data exploit**.
Existing [#251](https://github.com/Apdelrahman1911/parlor/issues/251) owns this
follow-up; do not create a duplicate or close its physical acceptance.

## Implementation

Home forwards its existing opaque `deletionSelection` through the registered
launch and all six actual game bindings. Unsupported or missing selections fail
closed. The transport compares logical ownership against the **same credential
loaded for authentication**, under its credential-store lock and before kit/LAN
creation. No stale-summary fallback to another stored membership is attempted.

A matching logical membership may rotate its credential: the handle is not a
secret, serialized identity or persistent navigation key. Existing pending/active
candidate preference, exact game/version, authentication, host authority, privacy,
wire4.2, deadlines and query-only gameplay recovery remain unchanged.

Cold failed Resume also needs safe Leave. Its process-owned route retains the
handle and uses a selected logical-membership discard, never global discard.
Already-absent ownership succeeds, unrelated replacements survive, and selected
rotations are revoked. Home's separate destructive deletion retains its stricter
exact observed-generation/record check; this is **not** a relaxation of that gate.
Existing retained physical rooms still own their original final-Leave transaction.

## Regression evidence

- Six Desktop methods exercise **24 actual binding/Home EN/AR journeys** at
  320×740dp/200% text. The exact handle and game/version reach the transport;
  unbound/game-only alternatives and host/join operations remain idle.
- Four common storage methods cover matching pending/committed rotations,
  five independent ownership replacements, no unrelated-active fallback, scoped
  final Leave and idempotence. Three common owner methods cover selected cold
  Leave, fail-closed unsupported discard and distinct/redacted route identities.
- Common transport defaults cannot fall back to game-only/global operations;
  the router rejects missing ownership. Adapter methods cover same-game
  replacement, foreign/absent handles, matching authenticated rotation, and
  all existing cancellation/handoff/registration cases through the selected API.

Focused tests and Detekt pass after extracting error mapping from the existing
large Resume transaction rather than raising its complexity threshold. The first
full check at `0dcc607d` remains **FAIL**: three mechanically generated inventory
rows still name the predecessor commits. All1,851Desktop/662Android perDebug and
Release/113GitHub-test cases pass, but that does not qualify the failed gate.
The inventory is regenerated only after source commits. Fresh full exact-head
verification and opt-in separate-process real-LAN selected-Resume execution are
pending; the earlier pipeline aborts before LAN because this gate failed.

## Remaining qualification

This is an unpublished follow-up stacked on the still-pending Resume branch,
not accepted main. Complete strict checks, sensitivity controls, independent
runtime/source/cleanup custody, accepted-base integration, fresh free-hosted
architectures/optimized Release lanes, protected merge and automatic actual-main
verification remain required. No workflow/dependency/Store-policy change.

The original iOS26.2 input cause, automatic cold-peer startup, native protected
storage, process death, physical host/client/gameplay/accessibility and measured
mobile battery/thermal matrices remain separate unfinished work. Existing
#251/#252/#258/#267 and Store/signing gates remain open. AGENTS is byte-identical.
