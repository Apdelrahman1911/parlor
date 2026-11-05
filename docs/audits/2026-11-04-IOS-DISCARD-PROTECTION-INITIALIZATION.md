# iOS Discard must not initialize snapshot protection

## Scope and source evidence

Related: [#258](https://github.com/Apdelrahman1911/parlor/issues/258).
This is a narrow recovery correction, **not Strict Complete Protection acceptance**.

At independently qualified main `811dfde660fb0dfd7c6e0d7540989f5001d13e54`,
`IosSnapshotFileSystem.delete()` resolves `filePath(name)`, which evaluates the
lazy `basePath`. That initializer creates the directory, obtains protection
constants and applies backup exclusion before returning its path. If any of
those operations fails, deletion never reaches either saved copy. Thus the same
initialization error that prevents Load/Retry can also prevent explicit Discard.
This follows the executable call path; no new original-main native reproduction
or physical-device result is claimed here.

## Reused correction

Reuse the already prepared `8b7e3ed6` candidate's location/initialization split
and deletion implementation, rather than rebuilding the security correction:

- `snapshotDirectoryPath` resolves the application's sandbox namespace only.
- Reads, writes and inventory retain the existing protected `basePath`.
- Discard validates the existing safe filename and removes current and legacy
  copies without reading/decrypting them or initializing snapshot protection.
- Normal OS filesystem permissions still apply. This does not promise deletion
  while locked or permit weaker protection on new/readable data.

No encryption, Keychain accessibility, backup policy, file-protection constants,
protocol, dependency, XCTest input action, assertion budget or release gate is
changed. Complete metadata/volume enforcement remains on the separate, held
security candidate.

## Regression evidence and fresh qualification

The three native methods are reused byte-identically from the prepared
`IosStorageSafetyTest`:

1. `explicitDiscardRemovesBothCopiesWithoutInitializingProtection` — deletion
   must not invoke an initializer that is configured to fail.
2. `currentDirectoryBackupExclusionFailurePrecedesProtectionAndDoesNotReadSecrets`
   — retain the read failure, avoid key reads, preserve bytes, then permit Discard
   without retrying the failed exclusion.
3. `rejectedCurrentReadStillExcludesItsDirectoryAndPreservesItsLastCopy` — rejected
   reads must retain backup exclusion and the original synthetic record.

Scoped prior native evidence: [41787455931](https://github.com/Apdelrahman1911/parlor/actions/runs/41787455931)
and [41820290844](https://github.com/Apdelrahman1911/parlor/actions/runs/41820290844).
Those runs test the larger unmerged candidate, not this subset's qualification.
The subset requires fresh protected-PR checks and distinct resulting-main
verification; execution evidence will be recorded on #258 and the PR.

## Remaining gates

#258 stays open. Supported-volume healthy Complete metadata, authenticated
save/load, migration and recovery remain unqualified. Physical lock/keybag,
locked storage access and unlock recovery remain reserved for the owner's
separate Phase 4 campaign. General CI, deletion tests and unsupported-volume
negative observations cannot replace those security results.
