# Bounded iOS snapshot reads: NSError boundary — November 5, 2026

## Finding and evidence boundary

`IosSnapshotFileSystem.kt` uses legacy `NSFileHandle.readDataOfLength` and
`closeFile`. Apple documents `NSFileHandleOperationException` for legacy read
failures and recommends the NSError-returning replacement. Kotlin/Native
documents that Objective-C exceptions crash at the Kotlin interop boundary by
default; the reviewed Foundation definition does not enable exception wrapping.
A Kotlin `catch (Exception)` in `FileBackedSnapshotStore` cannot repair that
boundary. The **API-contract risk is confirmed**; an original application I/O
crash or physical-lock crash has **not** been reproduced.

This is distinct from [#258](https://github.com/Apdelrahman1911/parlor/issues/258)'s
failed Complete metadata checks, #267's native HID cancellation, #276's delayed
keyboard startup, and PR318's Keychain error classification. It explains none
of those original platform failures.

## Correction

- Keep the existing read-only open, nonnegative limit and single `limit + 1`
  bounded read. No full-file allocation, polling, retry or format change.
- Use `readDataUpToLength(..., error)` and explicitly inspect both NSError and
  the nullable result. Kotlin does not import Swift's `throws` as automatic
  Kotlin exception translation.
- Transfer exclusive ownership to `readOwnedSnapshotHandle`; use
  `closeAndReturnError` exactly once on success or failure. Preserve the primary
  validation/read/cancellation failure; report failed close after successful
  read as an opaque Kotlin error.
- Keep generic I/O mapped to `IoError("snapshot_io")`; no native NSError,
  filename, payload or path enters the domain error. Oversize remains a
  protection failure. No deletion, legacy fallback or key replacement.
- These member APIs are present in the reviewed Kotlin/Native 2.4.21 platform
  metadata. Modern read availability is iOS13.4; Parlor's minimum is16.
  Dependency configuration and platform exception modes remain unchanged.

## Regression and verification

Eight new `IosSnapshotReadErrorTest` methods cover empty/one-byte zero limits,
negative pre-open limits, absent files, successful/oversized/invalid-limit owned
cleanup, and a real NSError read failure. The latter opens a **valid owned
write-only descriptor** to synthetic bytes, verifies both store read paths,
one attempt per operation, closed descriptors and unchanged bytes. It does not
close a global descriptor or imitate physical Data Protection. The two existing
bounded-reader regressions remain unchanged.

Prepared source passes local JDK21/wrapper/strict app Detekt. Linux cannot
execute Foundation or certify the new native methods. Fresh source-specific
hosted Apple/Android/Desktop/optimized Release checks and independent
resulting-main acceptance remain required before merge/qualification.
No Complete-positive, lock/keybag, physical-device or Store acceptance follows
from this correction; Phase4 remains owner-reserved.

## Official references

- [Legacy read](https://developer.apple.com/documentation/foundation/filehandle/readdata(oflength:))
- [NSError read](https://developer.apple.com/documentation/foundation/filehandle/read(uptocount:))
- [NSError close](https://developer.apple.com/documentation/foundation/filehandle/close())
- [Kotlin Objective-C exception boundary](https://kotlinlang.org/docs/native-definition-file.html#handle-objective-c-exceptions)
- [Kotlin/Native Objective-C interop](https://kotlinlang.org/docs/native-objc-interop.html)
