# Prototype and design documentation review — /root/mafia_cont

Baseline: main at 3625d0663ba6eb51338cbd5f9dc45f859ec18846; no tracked changes. Pre-existing untracked prototype is preserved. Source-only review; no browser/server/build/test started by this reviewer.

## Reachability and scope

`settings.gradle.kts` includes only KMP application/shared/game modules. `composeApp/build.gradle.kts` source-set/resource declarations do not include `design/web-ui-rework`. Actual graph captured in `evidence/baseline-desktop-static-01/gradle.log` corroborates normal module source roots. Prototype README accurately states that it is an isolated discussion artifact, not game/network implementation. No shipping defect should be inferred solely from prototype shortcuts.

## Read progress

All seven assigned text files completely read. README70, app.js742, index.html763, styles.css2830, DESIGN_TOKENS303, ACCESSIBILITY_AUDIT103, MOTION_DOWNGRADE31 lines (4,842 total). Receipts record hashes and line coverage.

## Prototype behavior inspected

Static templates + local JS state; synthetic roster/code/cases and action confirmations. Query route is allowlisted, user strings rendered via textContent/createElement rather than HTML interpolation. No remote service or durable preference store in app.js. Prototype scenarios intentionally do not implement reducers, protocol, credentials, actual timers, or real projections. UI contains all synthetic phase views then hides irrelevant views, not real peer private data. Browser history replacement is not production Navigation 3. Clipboard error feedback and simulated admission are prototype-only limitations.

## Limits

No live layout/browser, screen-reader, keyboard, actual locale, or physical-device evidence. Production Compose behavior reviewed separately in `reviews/design-system-mafia-cont.md`.

## Complete HTML/CSS review

All templates, SVG symbols, selectors, pseudo-elements, media queries and reduced-motion overrides were read. Fonts resolve by relative URLs to already-inspected bundled font assets when served from the documented repository root. No remote font/CDN/script asset is requested by this prototype. CSS uses synthetic device chrome, scrollable phone content, 1120/760/430px breakpoints, light/dark variable palettes, and an explicit direction toggle; there is no native safe-area integration. Semantic template headings/forms/action labels and hidden subviews were traced to script selectors. Prototype values (fixed role counts, fake private content, fixed timer/code, demo dialogs and acknowledgements) are not production acceptance evidence. Screen-reader radio keyboard behavior, focus movement, low-contrast tiny prototype labels, and actual viewport/rendering remain browser-validation limits, not independently confirmed shipping findings.

## Documentation disposition

- `DESIGN_TOKENS.md`: every line read; header/footer explicitly identify historical visual proposal. Color/typography/effect values, pure-black prohibition, future overlays and sound design are not current contracts. Present implementation differs intentionally: editorial palette, bundled Inter/Mono plus system fallback, opaque black privacy cover, Boolean motion policy. Do not report obsolete proposed sound/tiers/serif as missing code.
- `MOTION_DOWNGRADE.md`: current Boolean policy corroborated in App.kt48–110, ParlorMotion.kt, platform observers, theme72–149, Settings.kt, PersistentSettingsStore.kt and settings UI145–177. App ORs in-app preference with platform request; Android observes animator scale, iOS observes accessibility notification, Desktop returns false as development-only policy. Persisted backing updates precede flow publication; this read does not prove disk durability beyond platform guarantees. No adaptive GPU/FPS feature demonstrated or claimed.
- `ACCESSIBILITY_AUDIT.md`: uncompleted, explicitly external qualification matrix. Source/static tests are prerequisites and are explicitly not screen-reader/device proof. Current DS-C03 contrast defect is separate shipping evidence; this checklist cannot be marked complete based on graph/test success. Requires both games/topologies/platforms/locales, font scales, lifecycle/motion, focus and complete assistive-technology journeys.

No new candidate from this bounded prototype/documentation assignment. Existing design-system candidates and independent validations remain in their dedicated records; no application source changed.
