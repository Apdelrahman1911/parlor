# DS-C01 — remaining-gap adjudication

**PARTIALLY VERIFIED.** Independent reviewer `/root/native_fix_review`.

The 11 native helper tests pass, but create new owners inside one process/suite. They do not prove process restart, UIKit/Compose resource refresh or real active-session retention. Those are **locally executable verification gaps**, not inherently physical-device/external blockers. A fresh owned simulator with an additive, reviewed copied-wrapper fixture can execute the real Settings process-restart path without changing shipping source. No new runtime test ran for this dossier.

## Exact product decision

Old installs may contain unmarked `AppleLanguages=[en]` written by old Parlor or by iOS per-app Settings. The representation has no provenance. Current code safely refuses to claim/delete an identical unmarked value; selecting System therefore retains that value. Should Parlor preserve it and direct the user to iOS Settings, or offer an explicit user-consented reset of only its app-domain language override? Silent deletion cannot safely choose. Same-value external writes while a marker exists are likewise indistinguishable; not a newly confirmed defect.

## Safe executable continuation

Use the actual `.debug` app `standardUserDefaults` only inside a new task-owned simulator sandbox, with synthetic fixtures and no language launch arguments, backing-plist edits, entitlements or real devices. Keep original Kotlin entry/composition and actual Settings actions; observe startup before App and after UI changes. Assert a preceding ownership marker really survived termination before claiming the no-disposal precondition. A Swift-only temporary wrapper observer can cover preference ownership, process identity, text and native semantic direction; actual Compose-direction and active-session assertions need distinct observation and cannot be inferred. Do not add Settings navigation during guarded game flows.

The archived IOS-R1 runner cannot be reused unchanged: it binds a clean old source, fixed used cycle, old copied phase, unrelated probe schema and omits external ibtoold FIFO paths from cleanup. A new runner must attest exact live-owned worker FIFO arguments/UID/inode/birthtime, refuse symlinks/unknown contents/holders, and unlink only attested files then empty parents after owned workers stop. See JSON for file hashes, read ranges and authoritative references. Original evidence remains intact.
