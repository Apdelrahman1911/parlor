# Mafia / core / engine source audit notes

Reviewer: `/root/audit_mafia`. Checkout independently verified `main` at `3625d0663ba6eb51338cbd5f9dc45f859ec18846`. All source receipts refer to immutable fresh inventory hashes. No build/test processes started.

## Contracts

Complete reads: all core and generic engine source/tests/build files; engine-testing fixture source/tests/build; Mafia module build/DI/definition/IDs.

- Generic engine exposes opaque state/action/phase/reducer, `SessionConfig`, runtime redaction projections (not a type-level guarantee), and byte payload codecs. No game imports or I/O found in engine implementation. Registry copies supplied list and refuses duplicate IDs before constructing lookup.
- Core typed IDs validate only nonblank; stronger transport/persistence restrictions must be verified at admission/codec boundaries. `RandomSource` is deterministic mutable Kotlin RNG; Mafia assignment use and replay salt still to be traced. `SessionSeedSource` is only interface; entropy provider must be verified in platform root. Result combinators propagate exceptions/cancellation; no broad catch. SemVer compares numerically and rejects malformed negative/excess components on parse; direct data-class construction has no init guard but no demonstrated untrusted construction bypass. `Clock` uses Kotlin time; build comment incorrectly says kotlinx.datetime but dependency removal not proposed absent graph/reachability review.
- Engine architecture tests inspect declarations/imports (production-filtered Konsist); they are not semantic purity proof. Projection test pins explanatory KDoc strings and line-ending normalization, not runtime security.
- Engine-testing RoundRobin codec is strict about unknown JSON fields but fixture semantics intentionally simpler than production. Its 1..16 supported count is metadata; create/reducer do not validate config; this is test-only fixture, not a shipping-game defect. Full fixture tests checked for actual assertions: wrong turn unchanged; every seat + terminal event; roundtrip preserves data; malformed payload throws; registry duplicates fail.
- Mafia build commonMain imports shared session/content/storage/networking/UI, no platform source files in assignment. commonTest alone imports networking-testing. Definition delegates config validation, selects count preset, starts with empty private/roles and host-only supplied seed in Setup. Mode `classic`, game `mafia`. Display-facing literal metadata is a lead only: actual shell binding may localize separately, so no untranslated-UI claim without callers.

No confirmed findings in this group; no tests executed. Cross-module behavior remains subject to subsequent source traces and root build graph review.
