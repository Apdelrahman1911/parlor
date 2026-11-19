# iOS snapshot-key read errors are not ciphertext corruption

Related: [#258](https://github.com/Apdelrahman1911/parlor/issues/258) and
[#253](https://github.com/Apdelrahman1911/parlor/issues/253).

## Confirmed source boundary

At main `fca088d1dc2cab8e487e8befd2b7b60db58149cd`, a failed
`IosSnapshotKeychain.readExisting()` lookup throws `IllegalStateException`.
`IosSnapshotFileSystem.decrypt()` wraps that exception in
`SnapshotProtectionException`, which `FileBackedSnapshotStore` classifies as
`CorruptedData`. Authentication has not run, so a failed lookup does not establish
ciphertext corruption. It can incorrectly describe a recoverable storage error
as damaged data. Both saved copies remain intact under the existing policy.

This is an executable source-path finding, not a reproduced physical lock-state
failure or an attribution of the six original Complete Protection failures.
An absent key, invalid key material and failed authentication are separate cases.

## Narrow correction and reuse

Reuse the prepared `8b7e3ed6` candidate's unavailable-protection exception,
opaque-IO store mapping and two common regression methods byte-identically.
Isolate only key acquisition in the iOS decryption path: ordinary lookup errors
become `SnapshotProtectionUnavailableException` and map to
`IoError("snapshot_io")`. Never expose the exception text to the UI or logs.

Cancellation and existing protection/authenticity exceptions propagate unchanged.
A genuinely missing key remains a failed protected read; no replacement key is
created, legacy fallback is not permitted, and no input/action is retried.
Encryption, HMAC verification, directory/file classes, backup exclusion,
Keychain accessibility and Complete readback predicates are not relaxed.

## Regression and verification requirements

`IosLegacySnapshotBackupTest.keyReadFailureReportsOpaqueIoAndRecoversWithoutDiscardingCopies`
uses real Foundation files and real encryption/authentication with synthetic
UUID-named records and a fault-injected key reader, never real player data.
It verifies the original cause, opaque errors on both load paths, exactly one
lookup per operation, preservation of both copies, and successful authenticated
recovery after the reader becomes available. Only successful decryption removes
the stale legacy copy; the current ciphertext is unchanged.

The existing missing-key, authentication-failure, cancellation and backup safety
tests remain mandatory. Common tests cover opaque IO for both read paths,
inventory and mutations. Fresh native/full protected-PR and distinct main
qualification are required before declaring the correction verified. Fault
injection is not real Keychain-lock or physical-device enforcement evidence.

## Outstanding security acceptance

#258 remains open. The full Complete/volume/readback candidate remains held;
supported-volume positive metadata, authenticated save/load, migration and
recovery acceptance are still required. Physical lock/keybag/access/unlock
verification remains the owner's separately reserved Phase 4 campaign.
This classification correction does not certify protection or resolve the
original native-input platform causes in #267/#276.
