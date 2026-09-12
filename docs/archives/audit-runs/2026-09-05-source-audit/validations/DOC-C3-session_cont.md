# DOC-C3 — Independent documentation/configuration validation

Finder `/root/mafia_cont`; independent validator `/root/session_cont`. **DOCUMENTATION MISMATCH, Low.** Not an application defect, key-disclosure finding or signing exploit.

Baseline `main` at commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; tracked files unchanged. Absolute repository root `/Users/abdelrahman/Projects/parlor`. Fresh per-file source hashes and actually read ranges are appended to `coverage/reviews-session_cont.jsonl`.

## Independently reopened implementation and instructions

- Full `docs/IOS_SETUP.md:1–263`; affected98–100 explicitly promises choosing Signing & Capabilities Team writes only ignored per-user `xcuserdata/` and stays out of commits.
- Full `iosApp/Configuration/Config.xcconfig:1–19`; affected3–6 repeats the assurance; actual10,19 define shared TEAM_ID and release-team indirection.
- `iosApp/iosApp.xcodeproj/project.pbxproj:130–205,265–292,380–513`: real app target links its configuration list to Debug/Release entries whose tracked target build settings include `DEVELOPMENT_TEAM = "$(TEAM_ID)"`407 and `DEVELOPMENT_TEAM = "$(MOBILE_RELEASE_IOS_DEVELOPMENT_TEAM)"`438. App and project entries reference the tracked Config file. Test targets also have development-team settings465/483. This is the actual opened `.run/iOS App.run.xml` project, not an orphan sample.
- Full `.gitignore:1–43`;13–18 ignore Xcode user-state directories but not the shared project or configuration. `git ls-files` independently confirms all affected documents/project/config are tracked.
- `scripts/release/tests/test_workflow_contract.py:1–145`:75–123 explicitly asserts the present target-scoped Debug/Release signing indirection. This is important counter-evidence against claiming an unnoticed successfully released override.

## Basis and reasoning

The Apple primary documentation content saved in `evidence/ios-setup-api/{target-settings,xcconfig,distribution}.txt` was independently read, not just the finder's summary. URLs/access date and retrieved hashes are in the adjacent research.json (2026-09-05):

- `https://developer.apple.com/documentation/xcode/preparing-your-app-for-distribution` explicitly labels the operation **Assign the project to a team** and locates Team in the project editor's Signing & Capabilities pane.
- `https://developer.apple.com/documentation/xcode/configuring-the-build-settings-of-a-target` describes project/target configuration, per-configuration edits, target values outranking configuration-file values and command-line overrides.
- `https://developer.apple.com/documentation/xcode/adding-a-build-configuration-file-to-your-project` describes tracked text-based configuration and inheritance below target Build Settings.

These sources and this checkout's actual target-owned DEVELOPMENT_TEAM settings refute the blanket assurance that the target Team control is an ignored per-user override. A contributor choosing an explicit different Team can replace shared variable-based project signing settings, creating tracked changes. The ignore rule only applies if data is actually written under the ignored user-data paths; it cannot shield tracked build-setting changes. The expected behavior is accurate documentation of scope and required diff review, not a new signing architecture.

## Counter-evidence, limitation and conclusion

The exact byte-level output of clicking Team in Xcode26.3 was **not reproduced**. No Xcode build, signing UI account interaction, actual Team ID, credential, certificate or private file was accessed. Do not present a particular generated project diff as observed or assume every version writes exactly the same keys. Documentation classification rests on official project/target ownership plus actual tracked configuration, not a fabricated UI trace.

The docs separately warn never to commit a team identifier; choosing an already-selected team may do nothing; editing Config is obviously a tracked edit. Neither negates the explicit false claim about the alternative UI route. Team IDs are not alleged to be private cryptographic secrets. The executable workflow contract can reject altered target indirection, and current Store-identity blockers remain; no successful unauthorized signing or Store upload is inferred.

**Independent conclusion:** sufficient evidence for a Low documentation mismatch about change scope, with UI mutation reproduction explicitly unexecuted. Recommended authorized fix: correct both instructional surfaces, warn users to inspect shared project diffs, and document a deliberate local-only override if supported. Do not ignore tracked project files, weaken identity contracts, reset/stash user work or invent release identifiers. A regression check may assert the corrected guidance; actual Xcode interaction would remain a separately controlled synthetic test.

No build/test/app/server processes were started by this validator; only isolated audit evidence was written.
