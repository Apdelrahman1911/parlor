# DOC-C3 — Xcode Team-selection instructions promise the wrong change scope

Status: independently adjudicated DOCUMENTATION MISMATCH (Low). Finder: `/root/mafia_cont`; independent validator `/root/session_cont`. Full validation record `validations/DOC-C3-session_cont.md` reopened by finder. This is not an application signing/privacy exploit.

## Source identity and affected instructions

Branch main, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`, no tracked modifications. Absolute repository prefix `/Users/abdelrahman/Projects/parlor/`.

- `docs/IOS_SETUP.md:98–100`: says choosing a team in Xcode Signing & Capabilities writes per-user `xcuserdata/` and is gitignored.
- `iosApp/Configuration/Config.xcconfig:3–6`: repeats the same assurance.
- `iosApp/iosApp.xcodeproj/project.pbxproj:398–438`: Debug and Release target build settings store `DEVELOPMENT_TEAM` in the tracked project and resolve through checked-in configuration variables. Test-target equivalent `460–483`.
- `.gitignore:13–18`: ignores actual user-data locations, not the tracked project/config files.

`git ls-files` confirms both configuration/project paths and both documentation surfaces are tracked.

## Expected / actual and consequence

The instructions promise a change isolated to ignored local user data. Xcode's Team control assigns the selected target/project signing build setting; it is not a per-user override mechanism. Choosing an explicit Team can replace the variable-based target setting in the version-controlled project. A contributor following the assurance can unknowingly include local team configuration in a commit or override the shared CI indirection. Merely following the alternative edit-Config instruction also edits a tracked file; its instruction does not promise that route is ignored.

No private key, certificate, actual account/team, or signing material was inspected. Team identifiers themselves are not asserted to be signing secrets; impact is unexpected tracked configuration drift. No app build/signing or Xcode UI mutation was performed.

## Evidence and counter-evidence

Apple current official Xcode documentation (accessed 2026-09-05; sanitized extracts and retrieval hashes in `evidence/ios-setup-api/`):

1. https://developer.apple.com/documentation/xcode/preparing-your-app-for-distribution — Assign the project to a team: in project editor, Signing & Capabilities pane, choose Team.
2. https://developer.apple.com/documentation/xcode/configuring-the-build-settings-of-a-target — changes live at target/project/build-configuration levels; target-level settings override configuration settings; command-line settings have highest precedence.
3. https://developer.apple.com/documentation/xcode/adding-a-build-configuration-file-to-your-project — configuration files are plain-text project/target settings, layered below target Build Settings.

Current tracked project proves ownership/indirection of the setting for this checkout. Official docs establish the control's project scope; they do not supply a byte-level trace of clicking that control in Xcode26.3. Independent validator should distinguish sufficient source/documentation proof from an unexecuted UI reproduction. Actual `xcuserdata/` is ignored, but that does not make project build-setting mutations ignored. Existing empty TEAM_ID and publication identity blockers prevent this candidate from proving successful unapproved signing/upload.

## Suggested remedy / regression coverage

Correct both instructional surfaces: warn that Xcode Team selection changes tracked project settings and require reviewing the diff before commit. Describe a supported local-only override without automatically changing release IDs or production signing configuration; a documented command-line `TEAM_ID=...` override or an intentionally designed ignored local xcconfig may be appropriate only after separate implementation authorization. Add a documentation/configuration contract where feasible. Do not hide tracked project changes with broad ignore/stash/reset advice.

## Reproduction (not executed)

In an isolated disposable copy of a synthetic project (not this repository), capture project bytes/status, select a nonprivate placeholder/test team through Xcode Signing & Capabilities, save, and inspect resulting project-file diff. Do not perform on the audit checkout or use real signing assets to establish this documentation point.
