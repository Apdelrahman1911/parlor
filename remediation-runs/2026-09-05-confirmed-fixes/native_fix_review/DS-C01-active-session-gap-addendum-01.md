# DS-C01 — active-session verification boundary

**TEST/EVIDENCE GAP, not an application defect.** `/root/native_fix_review`; no runtime test or source change performed for this addendum. Exact source hashes/read ranges are in the sibling JSON.

- `App.kt:325–333` displays Settings navigation only for top-level routes; `AppNavigation.kt:104–108` rejects selection during an active game flow. Changing language from Settings while staying inside a game is **not a current UI journey**. Do not add a navigation bypass to manufacture one.
- `PersistentSettingsStore.kt:34–61` reads backing once and publishes through its setter. Post-startup writes to `UserDefaults` alone would not exercise the live production settings flow.
- Local controllers are private remembered values (`MafiaGameFlow.kt:247–265`, `WhodunitGameFlow.kt:787–810`), not Koin-owned session singletons. Keeping the Swift root controller alive does not independently prove that those controllers/canonical states survived.

The narrowed Swift-only apphost matrix must remain limited to actual Settings actions, strings, UIKit semantic direction and persisted ownership across real process restarts. It is not active-session or direct Compose-layout-direction evidence.

A future invocation-only additive Kotlin bridge could call the **actual** Koin SettingsStore while a UI-created game remains mounted, with no shipping hook or navigation change. That is artificial test-controlled store mutation, distinct from normal UI reachability. UI phase/progression assertions can be added, but reference-level local controller/canonical continuity still needs a separately reviewed copy-only observation seam rather than fake tokens, source-string assertions, snapshots or private reflection. Generic retained host runtime identity is observable through `ProcessMultiplayerSession.runtime`; synthetic host fixtures are not physical LAN proof.

Root explicitly deferred any new bridge/seam. Keep this gap open and distinguish same-process retention from process-death recovery. The legacy unmarked AppleLanguages provenance decision also remains separate.
