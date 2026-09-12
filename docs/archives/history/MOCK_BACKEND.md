# Historical mock-backend decision — superseded

Document status: historical filename retained for old links. This is not a
current implementation or release contract.

The original plan proposed serving bundled case JSON through Ktor `MockEngine`
in development and swapping in a live HTTP backend for production. Parlor does
not ship that architecture.

## Current implementation

- Production composition binds `RemoteCaseDataSource` to
  `OfflineRemoteCaseDataSource`.
- Whodunit content ships as Compose resources under
  `game-modes/whodunit/src/commonMain/composeResources/files/cases/`.
- `BundledWhodunitCases` exposes those resources through the same
  `DefaultCaseRepository`, `DefaultCaseValidator`, and
  `WhodunitPayloadValidator` boundaries used by an optional future remote
  source.
- `KtorRemoteCaseDataSource` remains a bounded, strict adapter with automated
  tests. It is not bound into a shipping app and `MockEngine` appears only in
  tests.
- The process-lifetime in-memory cache is an optimization, not an authoritative
  content store.

Adding live content delivery is a future product/security change. It requires a
reviewed HTTPS endpoint and trust model, pinned content integrity policy,
privacy and availability decisions, cache migration, failure UX, and the full
release matrix. Rebinding an interface alone is not release evidence.

The executable sources are:

- `composeApp/.../di/ContentModule.kt`;
- `shared/content/.../repository/DefaultCaseRepository.kt`;
- `shared/content/.../validation/DefaultCaseValidator.kt`; and
- `game-modes/whodunit/.../content/BundledWhodunitCases.kt`.
