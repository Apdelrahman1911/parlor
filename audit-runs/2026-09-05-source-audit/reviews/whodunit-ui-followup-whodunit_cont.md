# Whodunit UI/timer follow-up — /root/whodunit_cont

Source-only bounded assignment: resolve the earlier UI-owned discussion-ticker concern and disposition loading/connecting geometry, TimerRibbon width, and duplicate-name feedback. No production, configuration, rule, dependency, or Git changes. Baseline remains main3625d0663ba6eb51338cbd5f9dc45f859ec18846 / treedb7f3d2afe73a13628296daee2cce71165eebc8d. Untracked AGENTS.md, audit-runs/, design/, docs/PARLOR_PROJECT_HANDOFF.md, and project-code-audit/ preserved.

Conservative new direct-read coverage is `coverage/whodunit-ui-followup-whodunit_cont.json` (also JSONL). It lists exact absolute paths/hashes/selected ranges and their complements; it does not replace the original complete-module reading ledgers or claim that every line in these large files was reread during this narrow follow-up.

## Dispositions

| Lead | Source-backed result | Disposition / remaining work |
|---|---|---|
| Settings/tab navigation strands a host timer | AppNavigator104–109 refuses top-level changes from Game;139–151 refuses unguarded Game pop; App.kt325–332 hides bottom tabs. Back delegates into game confirmation; NavDisplay receives only the last guarded game entry. | Rejected broader hypothesis; no ordinary Settings/tab path found. Existing AppNavigationTest67–94 asserts these guards. |
| Exit confirmation freezes an unpaused discussion | Host confirmation replaces whole game child; local confirmation replaces router; both remove RoundSegment's sole ticker while canonical state survives. Stay remounts without elapsed-time catch-up or a Pause/Resume action. | New **WD-C4 candidate, not independently approved**; see complete dossier. Source behavior is deterministic, but intended modal policy needs adjudication. Proposed Low only if accepted. No claim that peers display numeric countdown; actual peer route is waiting. |
| Host loading / peer connecting may lose content | HostLoadingState573–608 and PeerConnectingState435–476 are centered non-scrolling Columns containing72dp flame, localized28sp title, action. CallersHostSessionFlow291–296 and PeerSessionFlow365–372 make both production reachable. Error/waiting siblings explicitly scroll. | No measured clipping or unusable control proved here. Do not turn non-scrolling source alone into a confirmed geometry finding. Constrained-size/large-text EN/AR production-composable measurement remains unexecuted. |
| TimerRibbon may crowd at large text | TimerRibbon1–96 uses three unweighted Text children in a clipped, padded Row; actual DiscussionScreen135–188 is vertically scrollable. Status/remaining/total text are localized and expose one merged semantic value. | No demonstrated current-layout clipping. Need text-layout/bounds measurement for actual shipped fonts, narrow widths, and large scaling. Existing semantics tests are not geometry tests. |
| Duplicate local names have no inline explanation | PlayerEntryScreen1–143 sanitizes input, trims names, disables Confirm unless RoomInputPolicy43–46 validates canonical exact uniqueness; caller creates seats from those names. Domain roster/definition repeat validation. NameField115–142 does not set supportingText/isError. | Duplicate acceptance is rejected. Case-sensitive labels are intentional and explicitly asserted by policy and Whodunit tests. Lack of a specific inline message is an optional UX improvement absent an established requirement, not independently confirmed application defect. |

No additional confirmed issue count is authorized by this report. Layout follow-ups are unexecuted validation, not PASS and not fabricated device-only blockers.

## Timer execution path and counter-evidence

The complete narrow proof is in `candidates/WD-C4-whodunit_cont.md` with separate source hashes. `WhodunitGameFlow976–1167` obtains a game-specific process-retained runtime, but its ticker is still under the visual `RoundSegment` in `WhodunitPhaseRouter827–938`. Session scopes therefore survive the opaque exit-confirmation replacement while the ticker scope does not. The loop has no epoch/wall-clock catch-up. Existing ticker tests182–255 exercise its decrement/pause/expiry directly, not confirmation mounting.

Canonical pause is otherwise deliberate: reducer506–610 models timer creation/ticks/expiry;1067–1094 changes public and per-timer paused flags. HostRoomBridge113–136 freezes on transport Suspended/Resuming and489–507 resumes only its own pause after Active. This differs from an exit-confirmation boolean.

Actual platform chain inspected: SwiftContentView14–49 reports scene phase but leaves ComposeView mounted below an opaque inactive/background privacy layer; Kotlin iOSMainViewController35–56 forwards to AppLifecycleCoordinator. AndroidProcessLifecycleCallbacks25–44 uses ProcessVisibilityTracker to avoid treating configuration replacement as true background. Common lifecycle45–68 maps inactive to unchanged transport visibility and background to a background notification. P2pKitRoomTransport180–218 serializes notifications; AppLifecycleRoomCoordinator28–54 applies them to the registered room. Host room3204–3331 emits Suspended/Resuming/Active and preserves its original recovery deadline. This explains why normal lifecycle pauses are not the same as the confirmation path. Physical suspension, power loss, and OS scheduling behavior were not run in this follow-up.

No permanent deadlock, role/seed leakage, peer authority mutation, winner change, or timed-Mafia defect follows from WD-C4. Retaining host timer work must not undo opaque privacy replacement or introduce a shared game-specific clock without architecture review. An explicit modal pause policy may be legitimate; source comments alone do not establish a contrary product rule.

## UI lead details and honest limits

- Loading titles include EN"Setting up your room…", "Connecting to room%1$s…" and corresponding Arabic resource strings. The room code is bounded6ASCII by RoomInputPolicy, not arbitrary long user content. `parlorSafeContentPadding` adds system/cutout safe area plus32dp horizontal/vertical spacing; the action primitive has a52dp minimum and16dp vertical padding. These facts identify stress-test inputs, not measured failures.
- TimerRibbon has24dp horizontal internal padding plus8dp row gaps; DiscussionScreen adds32dp safe-content spacing. Fonts are token-driven (label11sp with1.8sp spacing, timer22sp monospace, total15sp body). Because constrained Text can wrap/reflow differently across font fallback, simple character-count arithmetic is not sufficient evidence of clipping. Test actual TextLayoutResults/semantics bounds, not a manually reconstructed approximation.
- WhodunitAccessibilitySemanticsTest29–81 checks merged normal/urgent descriptions and the exact10-second live-region threshold. RtlFormattingTest38–76 scans source references and80–141 composes resources using UnitApplier; neither measures the ribbon's visible geometry. These limitations do not invalidate their narrower assertions.
- Player-name testsRoomInputPolicyTest55–62 and WhodunitRulesInvariantTest170–209 explicitly reject exact duplicates and accept"Alice" vs"alice". UI headline/description contains no duplicate-specific instruction, so an explanatory helper would be useful but cannot be promoted into a rules/security defect on this evidence.

## Continuation / verification

1. Root independently reopens and adjudicatesWD-C4. If desired, run a production-composable mount/unmount reproducer in the shared build lane, using a legal six-player bundled story or explicitly synthetic content. The source proof does not require pretending a four-player bundled path ships.
2. If geometry needs closure, measure actual HostLoadingState/PeerConnectingState and DiscussionScreen/TimerRibbon under EN/AR, constrained portrait/landscape dimensions, relevant safe areas and font scales. These private composables may need isolated audit-only access/test setup, not production rewrites. Capture bounds/overflow and screenshots. No such run occurred here.
3. Physical-LAN/background behavior and VoiceOver/TalkBack remain platform verification tasks; desktop source/semantics tests cannot substitute for them.

## Research and hygiene

Effect-cancellation provenance was reopened from `evidence/session_cont-research.json` and the exact AndroidXruntime1.10.5 source excerpt linked from pinned CMP1.10.3. URL/hash/access-date details are inWD-C4. No new downloads or third-party data uploads.

No Gradle/Xcode build or test cycle was started by this child, so no child build outputs, daemons, app, simulator, or server required cleanup. No unrelated/root process was stopped. Only the listed audit evidence files were created using Python-B. Tracked-tree comparison is saved with this follow-up closeout receipt; concurrent parent/sibling audit material is intentionally preserved.
