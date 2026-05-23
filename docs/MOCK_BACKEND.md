# Mock Backend — Hosting Choice

## Decision

**Static JSON file(s) inside the repository, served via Ktor's `MockEngine` in dev builds.**

This is the simplest path that satisfies the architecture's "production code path is the only code path" rule (`ARCHITECTURE.md` §8.8). No external service to maintain, no CDN account, no network dependency for development, and the bundled fallback case shares its source file with the "remote" mock.

## What this means concretely

- *The Last Dinner* and any future case live as JSON files under `:game-modes:whodunit/src/commonMain/resources/cases/` (or the equivalent KMP resources location).
- `RemoteCaseDataSource` is wired with Ktor's `HttpClient(MockEngine)` in the dev configuration. The mock engine resolves a small set of paths:
  - `GET /cases` — returns a manifest (list of `CaseSummary`).
  - `GET /cases/{id}` — returns the matching `CaseEnvelope` JSON, or 404.
  - `GET /cases/{id}/version` — returns the case version for cache-invalidation probes.
- In a production build, the same `RemoteCaseDataSource` interface is satisfied by a real `HttpClient` pointed at the production backend URL. Swapping is a DI choice; no call-site changes.
- The bundled fallback (`BundledFallbackCaseDataSource`) reads the same JSON file directly from app resources. The same file feeds both the mock "remote" and the bundled fallback, so they never drift in the MVP. (Post-MVP, when the live backend diverges from the bundle, this is revisited.)

## Why not the alternatives

- **A tiny in-process HTTP server** — More moving parts. Adds a port, lifecycle, and platform-specific concerns. The Ktor `MockEngine` covers the same code path with less overhead.
- **A CDN-hosted JSON** — Requires a CDN account and a deploy step. Worth doing for staging/production later; not needed for MVP development.
- **A real backend service** — Out of scope until Post-MVP; the case-management surface is a separate concern.

## What this does NOT decide

- The production backend's URL, hosting, or authentication. That is a Phase 8 / Post-MVP decision.
- The schema for the manifest or version endpoints — drafted as part of Phase 0 Task #1 (Content Schema).
- Whether the mock-engine config lives in `:composeApp` (single dev source) or in `:shared:content` (shared dev source). Lean toward `:composeApp` so production builds don't carry mock code.

## Rollback / future flexibility

Switching the mock to a real HTTP backend is a DI change in `:composeApp`'s content module:

```
single<HttpClient> { HttpClient(/* real engine */) }   // production
single<HttpClient> { HttpClient(MockEngine { ... }) } // dev
```

The architecture absorbs the change without any call-site impact. This is the entire point of the "production code path is the only code path" rule.

## Status

Locked for Phase 0–8. Revisited Post-MVP when a real case-management backend is introduced.
