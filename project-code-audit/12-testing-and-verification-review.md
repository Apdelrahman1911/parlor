# Testing and verification

## What actually runs

| Gate | Command | Proves | Does not prove |
|---|---|---|---|
| Module desktopTest | `:mod:desktopTest` | JVM tests in that module | Android/iOS runtime |
| productionDesktopCheck | every `desktopTest` | same + **not** desktop app compile (TB-003) | device |
| productionCheck | desktop + Android unit + Detekt + unsigned AAB + lint allowlist + shell grep + release scripts | host-independent unsigned quality | Apple, signing, LAN |
| allTests | explicit root aggregate of module `allTests` | Linux: common/desktop/android unit | iOS (Apple job forbids `allTests`) |
| productionAppleCheck | type-aware Detekt + linkReleaseFramework* | linkage | runtime |
| productionIosSimulatorRuntimeTests | iosSimulatorArm64Test | 1 iosTest + commonTest-on-sim | physical device |
| productionAndroidSigningCheck | signed AAB | needs credentials | not in PR CI |

## Matrix holes

- 0 Android instrumented tests; Xcode Testables empty (TB-005).
- 3 `@Ignore` real P2pKit join/broadcast tests (TB-006).
- Mafia and engine have no `commonTest` files (TB-012); Mafia rules never
  run as Native tests.
- Doc-parsing “tests” can fail on prose or pass while runtime diverges (TB-007).
- GR-010: several rule tests use validator-illegal fixtures.
- `InMemoryRoomBus` ≠ P2pKit (SN-009).

## This session’s command

```
./gradlew :shared:core:desktopTest :shared:engine:desktopTest --offline
```

**FAILED** in ~2s. Cause: `--offline` + configuration cache could not resolve
`assertk:0.28.1` / `opentest4j:1.3.0` from cache. **Environment/cache, not
product test failure.** Also logged: Gradle 8.13 deprecated for Kotlin 2.5;
`iosX64Test` disabled on arm64 host.

Did not rerun `productionCheck` (expensive; would not add device/LAN proof).

## False confidence

Green Linux CI ≠ releasable app. It does not run signed artifacts, physical
LAN, TalkBack/VoiceOver, or iOS simulator tests (those are the other job).
