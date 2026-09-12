# Root disposition of remaining storage leads

Source: `main` at `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree
`db7f3d2afe73a13628296daee2cce71165eebc8d`. Finder `/root/session_cont`;
separate reviewer `/root`. These are unallocated residuals, not new numbered
confirmed defects. ST-C1 has its own independent native validation.

## Android backup-leak extrapolation — FALSE POSITIVE for the stated path

Root reopened
`/Users/abdelrahman/Projects/parlor/composeApp/src/androidMain/kotlin/com/parlor/app/storage/AndroidSnapshotFileSystem.kt:1–184`.
The `.also` cleanup at121–123 does run only after successful decrypt. Old
`filesDir/snapshots` bytes can be retained, and failed current bytes do not fall
back to that old save. Explicit deletion at75–83 removes both copies.

The independent counter-evidence is concrete configuration, not faith in an
OS default: `composeApp/src/androidMain/AndroidManifest.xml:9–12` disables
backup and points to both rule files. Complete root reads of
`res/xml/backup_rules.xml:1–17` and `res/xml/data_extraction_rules.xml:1–30`
show root/file domain exclusions, including cloud and device-transfer modes.
These are under the same absolute Android source root. The iOS Documents
backup-eligibility inference therefore does not carry across this platform.

This is not a runtime certification of every OEM transfer mechanism. Retained
application plaintext could warrant a stricter quarantine/purge policy, but no
cross-app, network, backup or replacement-device access bypass was established.
Do not silently delete the last save or claim Android encryption itself leaks.

## iOS post-open NSFileHandle fault — UNCONFIRMED / BLOCKED

Root read the complete current iOS filesystem, including
`composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSnapshotFileSystem.kt:403–414`,
and the common store's mutex/error paths at
`shared/storage/src/commonMain/kotlin/com/parlor/storage/snapshot/FileBackedSnapshotStore.kt:33,59–149`.
Nil open is already rejected, the handle is private, and there is no suspension
between synchronous open/read/close. Known legacy native-exception semantics
do not independently establish a reachable post-open fault in this app.

The separate ROOT-C1 directory probe returned no handle on macOS and never
reached read/close. It is counter-evidence only for that proposed witness, not
proof that all iOS I/O or Objective-C exception paths are safe. No actual
post-open error, app crash or data-loss reproduction exists in this audit.
The source and official API investigation is retained in
`reviews/storage-residual-session_cont.md` and its research ledger; no new
platform behavior is invented here.

Remaining work: isolate a synthetic regular protected file in an app-hosted
native test, establish successful open and a genuine subsequent I/O failure,
and distinguish bridge fault injection from production reachability. Device
lock/unlock timing and signing-dependent storage remain separate gates. Do not
add blanket catches or change native storage under this audit-only assignment.

Root reopening receipts are appended to `coverage/reviews-root-closeout.jsonl`.
No new build, device, key, application change or background process was created
for this source disposition.
