# Root platform/shell read notes (audit-only)

Read full files in the accompanying hash/range receipts; reading is not device/runtime verification.

## Platform paths
- Android Application starts Koin once, registers process-visibility callbacks; MainActivity installs Compose synchronously and resolves initial persistent SettingsStore on IO. App has an explicit loading surface. Dispatch failures beyond storage contracts still need end-to-end verification.
- Android uses recents-thumbnail disabling from API33 and FLAG_SECURE below; intentional screenshots on newer releases are not automatically a privacy defect. Actual app-switcher/assistive behavior remains a physical gate.
- iOS owns one ComposeUIViewController via SwiftUI representable; all visible production Koin-start/lifecycle callers are UI-thread owned. A volatile non-atomic guard alone does not demonstrate a race without an off-main production caller. SwiftUI covers inactive/background with black and hides accessibility; real snapshot timing not yet tested.
- iOS scene manifest disables multiple scenes; orientation supports phones/tablets. Bonjour service is _p2pkit2._tcp; permission message is localized. Privacy declarations and dependency APIs still require artifact/source reconciliation, not assumed from plist.
- Desktop shutdown performs bounded leave then always cancels session/transport scopes and closes Koin. Session dispatcher is Default, avoiding the hypothesized UI-dispatcher runBlocking self-deadlock. Native cleanup after cancellation still needs dependency/lifecycle tests.

## Navigation and shell paths
- Android/Desktop use library transitions; iOS explicitly mirrors regular/predictive directions and uses500ms base/linear predictive seeking. That duration is not a defect based on a skill's different-version example. Gesture settlement/direction and native-language propagation remain runtime/version-specific verification.
- Bindings, not generic shell, own game-specific setup and playmode/case/lobby routes. Duplicate registry IDs, missing multiplayer contracts, wrong game IDs, and unsupported contract bounds fail at composition setup. Live in-process route restore and cold-credential resume are separate flows.
- Whodunit shipped shell advertises6..6, independent of larger theoretical engine range; Mafia takes engine support. Local and host case selection routes preserve selected summary/mode metadata. Resume bypasses arbitrary default case via resume session loader (cross-file earlier storage read).
- Android/Desktop LAN gate is NotRequired (NSD/TCP, no provisioning). iOS uses transport evidence, never claims denial from an empty discovery list; Continue permits a fresh real attempt after Settings. No app-created system permission preflight.
- Home metadata cards expose game title/position only, not full save payload/players/roles. Catalog is data-driven and bounded in present shipping games; linear indexOf here is not a proved performance issue. Buttons/card arrows have semantic roles; complete a11y/layout verification pending.

## Leads/uncertainty, not confirmed findings
- Localized iOS CFBundleDisplayName may override Debug APP_NAME; inspect actual Xcode resource wiring, native precedence, and documented expectation before classification.
- SwiftUI app-switcher cover timing cannot be proven from source or JVM tests.
- Back policy tests, layout resources, native locale handling, release artifacts and unsupported-platform gates not yet fully reviewed here.
