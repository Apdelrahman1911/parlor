# Uninstrumented repeated-cold-launch proposal

**Status: researched proposal, not implemented or executed.** Root owns approval,
build lane and any future source-copy cycle. This is separate from instrumented
`ios-readiness-01`; its failed aggregate and original receipts remain unchanged.

## Source and supported API basis

The production `IOSAppLaunchUITests.swift` currently performs one English launch,
checks foreground, the real `parlor-home-brand` Compose marker, no native alert,
and continued foreground. It does not provide eight-launch evidence.

The installed Xcode26.5 SDK's complete public `XCUIApplication.h` and Apple
`launch()`, `terminate()`, `wait(for:timeout:)` documentation were independently
read (URLs/dates/hashes in `reference-receipts.json`). `launch()` is synchronous,
terminates an already running instance and halts the test on launch failure.
Successful `terminate()` yields `.notRunning`; general state observations are
asynchronous, so wait explicitly rather than assuming instant notification.
The public header exposes no process-ID property. Do not use KVC/private
`processID`/`processIdentifier`, nor invent eight different PID/boot-UUID receipts.
The attempted plain `state.json` URL returned404; installed SDK declarations
supply the actual applicable state contract, not an inferred webpage.

## Minimal execution design

1. Freeze/bind a fresh actual source manifest, copied wrapper and controls.
   Create one fresh owned simulator/DerivedData/temp allocation. No existing
   simulator/phone, player data or preference fixture is touched.
2. Copy **all app Kotlin/Swift/resources unchanged**: no `instrument_kotlin`,
   injected MainViewController bridge, storage observer, Swift overlay or
   replacement controller. Only the copied UI-test file and reviewed owned
   Gradle/stop/signing preflight shell phase may differ. Verify this exact
   allowlist before build and again after workers stop.
3. Replace the copied UI test with one eight-launch method (four English and
   four Arabic launch-argument contexts, or explicitly retain English-only if
   chosen). Keep the four production container tests unchanged. No dynamic
   language setter, Settings seed or synthetic storage write is required.
4. Each iteration explicitly terminates, waits for `.notRunning`, invokes
   `launch()` once, requires foreground and the actual Home marker, then takes
   six foreground/no-alert/Home-present observations two seconds apart with
   measured monotonic elapsed times spanning at least10seconds. No activation
   fallback, launch retry, selector relaxation or ignored first failure.
5. XCTest stdout emits a bounded run-token/ordinal/locale/state/timing receipt;
   it may truthfully say `cold_start_basis=public-XCUIApplication-launch-and-
   observed-notRunning`, **not** that distinct process IDs were measured.
   Require exactly8launch receipts/48samples and expected XCTest method counts,
   zero skipped/failed/retried attempts. A `defer` terminates the app; root's
   finalizer always deletes the owned simulator after failure too.
6. Preserve compact XCTest summary/case results, fixture+source hashes, copied
   diff, exact actual signing-mode/effective-phase and built/installed bundle
   inventory. Immediately stop Gradle, clean only task-owned outputs/copy/
   DerivedData, verify owned workers ended. Do not retain whole xcresult or
   screenshots unless needed to diagnose a failed assertion.

## Honest limits and optional follow-up

- This is app-source-uninstrumented **XCTest-controlled** startup, not a manual
  tap, debugger-free production launch, physical iPhone test or Store build.
- A public lifecycle contract establishes clean process starts; no raw-PID
  proof is claimed. If raw PID evidence is specifically required, a separate
  reviewed host-owned `simctl launch`/exact-owned-container PID/start observer
  is needed; it is not silently substituted for the Home-visible XCTest path.
- With no app dyld observer, do not reuse the instrumented framework-origin/
  runtime-SHA/LC_UUID claim. Exact compiled/installed artifacts and Home
  execution are evidence, but loaded mapped-image provenance is narrower.
- No native storage observer means these launches cannot resolve IOS-R1 or
  prove snapshot/rejoin durability. A visible Home with a truthful unavailable
  storage banner can still pass this **launch-only** gate.
- The historical user's first-four launch crashes remain unexplained without
  corresponding app/OS termination receipts. Successful new launches do not
  establish their original cause. Xcode26.5 is not Store-qualified26.3.
- Follow with separate authorized physical/LAN/Store/assistive-technology gates;
  do not add entitlements, hide warnings or alter production session ownership
  to make startup observations green.
