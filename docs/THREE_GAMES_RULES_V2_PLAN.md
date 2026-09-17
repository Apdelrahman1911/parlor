# Three-game rules revision and GitHub distribution

## Scope and safety

Implement the owner's September 17 rules revision on `feat/last-light` without
changing shared P2P/session authority, weakening strict dependency verification,
overwriting local evidence, or enabling Store candidate/promotion workflows.
Existing `AGENTS.md` is preserved. GitHub distribution is a new, explicitly
requested channel; it does not certify Play/App Store readiness.

## Implementation sequence

1. Inspect game reducers, projections, replay snapshots, shell bindings, UI,
   existing regression tests and release/signing conventions.
2. Dominoes: Default/Draw/Block modes; targets 51/101/151; 151-mode shutout at
   **at least** 101 against zero; Default with two players draws seven tiles,
   three players receive nine after removing double-blank (0–0), and four
   players receive seven with optional opposite-seat partnerships.
3. Use one canonical score per side. In partnerships, opposite seats share a
   score. Going out scores opponents' pips only; a blocked hand compares the
   two combined team pip totals and awards their difference. A tied blocked
   hand awards nothing. The shutout rule applies to sides, including teams.
   Preserve physical tiles, measured flights, orientation and privacy covers;
   add accessible team labels and shared-score presentation.
4. Ghamza: remove score fields/calculations/projections/resources/UI entirely.
   Keep 1/3/5 rounds, 1/2/3 lives, per-round eliminations and an explicit losing
   final participant. Reset lives and randomly reassign roles each round.
5. Word Impostor: each ordinary player earns one point when their own single
   vote targets any impostor. Each impostor independently earns one point for
   the correct word. Majority/tie outcomes never change those personal awards.
   Keep scores unrevealed until the result so scoring cannot reveal roles or
   confirm the word during the guessing phase.
6. Bump all three game contract and snapshot/payload versions. Reject old
   payloads/retained game versions explicitly; do not reinterpret old scores
   or silently migrate an active round. Keep protocol 4.2 and transport pins.
7. Add deterministic rules, malformed/duplicate-action, team/deal/threshold,
   privacy, replay, rematch and multiplayer recovery tests; update EN/AR UI
   and setup/result regressions. Run focused tests before broad gates.
8. Add a separate source-bound GitHub distribution workflow: verify first,
   build Android APK and native macOS/Windows/Linux packages, enforce signing
   and artifact checks, then publish only the verified immutable bytes.
   Preserve Store stops, use protected credentials/permissions and immutable
   Actions, reject conflicting tags/assets and support validation-only runs.
9. Run strict repository/Apple/platform checks, validate workflow contracts
   and no-publication tests, freeze and push reviewed source, and exercise CI.
   Publication requires real configured signing identities and release gates;
   never substitute Debug keys, unsigned production fallbacks or fake receipts.

## Evidence and external gates

Record exact source/tree, command exits, artifact hashes, CI run and scoped
cleanup receipts outside the tracked source freeze. Previous qualification is
historical after this revision. Do not mark missing physical acceptance,
signing credentials, editorial approval, Store ownership or the existing A37
strict-protection failure as passing. Report actual publication/readback or
the precise external prerequisite preventing it.
