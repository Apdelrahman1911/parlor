# DS-C01 — task-owned app-host verification harness v2

**Author:** `/root/native_fix_review`. **Status:** authored, not executed; independent review and root's sole build lane are required. This is evidence infrastructure, not a shipping application change. Frozen v1 controls, its failed-runtime receipts, original audit receipts, and archived runner are unchanged.

## Scope

The new runner derives from `audit-runs/2026-09-05-source-audit/run_iosr1_apphost_cycle.py`, but binds the **current dirty source including build-consumed untracked source**, uses a fresh cycle ID, and tracks external Apple `ibtoold` FIFO paths. It copies only allowlisted Xcode wrapper/assets/configuration inputs into a new owned temporary root. It changes only copied:

- `iOSApp.swift`: prepare the synthetic fixture before original App/Koin initialization, including scene callbacks.
- `ContentView.swift`: retain the unchanged original `MainViewController()` and scene/privacy behavior, keep a weak reference to its actual UIKit controller, and add a small copy-only observation strip below the status area. The strip changes neither Kotlin composition nor the original content frame; it is not layout-validation evidence.
- `IOSAppLaunchUITests.swift`: replace the one copied smoke method with a bounded real-Settings language/restart matrix.
- `project.pbxproj`: point framework search/build to the original current repository and insert immediate Gradle-stop/failure propagation in the copied phase.

**No Kotlin source addition, Gradle init source overlay, production hook, dependency change, signing, entitlement, identity change, source commit, or real-device operation.**

## Actual runtime matrix, once executed

1. Fresh app-domain absence → actual Compose Settings chooses Arabic → terminate/relaunch → prove original persisted owner was present **before** unchanged App initialization → actual Settings chooses System → original fallback/absence restored.
2. In the same fresh owned sandbox, start a new process with an explicit **synthetic** previous per-app preference `[ar-EG, en]` (only when the three scoped production keys are absent after System). Actual Settings chooses English → terminate/relaunch → assert persisted owner/original preference before App → actual Settings chooses System → restore the exact previous value and Arabic resources.
3. Assert actual translated Compose language-section/System labels and the **actual** original root UIView's forced semantic direction; four distinct process boot UUIDs, one original controller creation per process, and two no-disposal restart preconditions.

No `-AppleLanguages`/`-AppleLocale` launch arguments are allowed. The copied probe only reads the three scoped app-domain keys and collapsed supported-language status, never arbitrary defaults, player data, stores, roles, seeds or credentials. It does not read/write preference backing files. Synthetic seeding adds only `AppleLanguages` inside the new owned app sandbox, never global/system preferences. All normal language mutations use the production Settings UI/store.

Do not infer active-game/session retention, direct Compose-layout-direction measurement, actual OS Settings interaction, gesture/accessibility behavior, physical LAN, signed Keychain, power-loss durability or Store readiness. An absent pre-App persisted ownership marker is **missing reproduction precondition**, not proof of a repair or a new app defect. UIKit direction is inspected; Compose direction is not directly instrumented. The old unmarked-override ownership policy remains unresolved.

## Execution, root only after independent review

Use `/usr/bin/python3 -B` (ambient Python's XML extension is broken):

```sh
/usr/bin/python3 -B -m unittest discover \
  -s remediation-runs/2026-09-05-confirmed-fixes/native_fix_review/dsc01_apphost_v2 \
  -p 'test_*.py' -v
```

These are 19 synthetic FIFO-safety cases and 17 receipt/status/selector/copied-shell-phase cases (36 total), **not iOS runtime tests**. New status tests replay the actual runner exception statements using synthetic failed evidence; source contracts verify status persistence before the guarded Xcode call and exact source-whitelisted selectors. They do not claim XCTest execution. Shell tests use a disposable fake `gradlew`, not the repository wrapper. Every fixture is a `TemporaryDirectory`; no device, preference, process signal or heavyweight build is involved. Tests have not been run by the author.

Then root may invoke `run_dsc01_apphost_cycle.py dsc01-apphost-02 REVIEWED_CONTROL_SHA256` after the independent reviewer supplies approval for the exact control manifest. The CLI hash pins reviewed content; it is not independent authorization by itself. Refuse reused cycle IDs and changed controls/source. Runtime is pinned to an available iOS 26.5 iPhone 17 Pro simulator; if unavailable, stop and review explicit adaptation rather than silently selecting an existing device. Only the newly created UUID is booted/deleted.

## Cleanup and safety

- One shared remediation `build-lane.lock`; refuse pre-existing module outputs and detected Gradle/Xcode workers.
- Isolated Gradle daemon/home with **symlinks to existing global dependency/distribution caches only**; signing environment scrubbed. Do not remove global caches.
- Nested phase stops Gradle immediately after embedding, including failure/interruption. Root runner stops again immediately after Xcode and during finalization. Required frameworks remain only through current Swift link/runtime inspection.
- Process ownership comes from new Popen PID/start/observed ancestry/process groups and a unique task JVM temp argument. File holders or an Apple process/directory name never independently establish process ownership.
- Exact FIFO arguments are accepted only from already-attested live child/`ibtoold` parent identities with matching UID. Canonical path shape, symlink-free components, inode/device/type/UID/birthtime within the cycle, and exact pair/parent binding are recorded before exit.
- After owned workers stop: reject PID reuse, unrecognized contents, changed metadata, unattested pending paths, or any file holders/query error. Unlink only recorded FIFO files; remove only empty recorded parents. Never recursively delete external temporary roots.
- Attestation errors are retained as failure without preventing PID-owned worker shutdown. Unknown/unattested paths remain explicit cleanup failures for targeted review, never a false PASS.
- Preserve bounded XCTest summary/test tree, synthetic JSON (≤256 KiB), hashes, copied diff, commands/exit status, source/control identities, and cleanup ledger. No bulk screenshot attachments. Shutdown/delete only the new simulator, remove current cycle's module outputs and owned copy/DerivedData/temp tree, verify source equality and worker absence.

SIGKILL/host death cannot execute Python finalizers; incomplete receipts provide the owned UUID/path/identity checkpoint for independently reviewed recovery.

## Review notes

V1 (`evidence/dsc01-apphost-01`) genuinely ran one XCTest and failed at its first Settings tap. Its initial Home/observer succeeded; two probe events show one English/System boot and no language mutation. This is **failed selector/evidence**, not an app crash or proof about DS-C01. V1's receipt incorrectly retained `runtime_evidence_status=NOT_RUN` when the strict XCTest verifier threw; raw XCTest failure evidence remains intact.

V2 changes only the source-informed tab selector, failure diagnostics, and runtime-status bookkeeping; the original probe, process/restart matrix, all assertions, source bindings, copied build phase, ownership, and cleanup remain unchanged. The exact tab label is `Open settings., SETTINGS` / `افتح الإعدادات., الإعدادات`. Pinned CMP UI 1.10.3 `Accessibility.uikit.kt:1882–1940` collects the selectable parent's content description then child text, joined with comma-space; `OnClick.label`, not the description, maps to the hint. Common fake role/description nodes do not duplicate it: they have unattached virtual LayoutNodes and are rejected by UIKit's `isValid` gate. See `../dsc01-accessibility-source-{01,02}/receipt.json` and the independent factory counter-evidence.

Selectors remain exact equality. A miss never chooses a looser fallback. Bounded diagnostic output queries only 12 EN/AR source-whitelisted literal labels, caps counts at 32, inspects at most four matching elements per label, and emits at most 8 KiB. It exports no arbitrary label/value/hierarchy and performs no mutation. The existing `LANGUAGE`/`اللغة` section and option labels remain unchanged.

The runner now persists `RUNNING` plus an attempt timestamp **before** Xcode and marks attempted evidence `FAIL` when verification throws. A preflight failure remains `NOT_RUN`. `FAIL` describes the verification attempt, not an inferred application defect. Passing still requires the exact unskipped XCTest, complete bounded probe matrix, successful stops/cleanup, and unchanged source/controls.

The helper tests include process UID versus inode UID separately, old parent creation time, holder-query failure, PID reuse, symlinks, unknown contents, substituted inode, pending late-created paths, and synthetic cancellation/timeout finalization. The copied phase tests exercise stale artifacts with failed Gradle, failed `cd`, failed stop, failed normalizer, and correct stop-before-normalize ordering.

See `../DS-C01-remaining-gap-adjudication-01.{md,json}`, `../DS-C01-gap-research-01.json`, and original DS-C01 dossiers for the remaining product question and official Apple references. This harness does not answer the legacy unmarked-preference provenance policy.
