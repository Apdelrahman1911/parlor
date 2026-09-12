# L08 file-protection research — no root cause inferred

**Status:** documentation/header research complete; exact iOS 26.5 Simulator
readback remains unresolved until actual stage-bound observations exist.

Source baseline: `bca3e2d9fce4b8b47bf17ddd4bb9fdceb7b40d6e`, tree
`93fa7690655135a6ccc1e261c345d00847e832b8`. Selected local SDK: Xcode **26.5 /
17F42**, `iPhoneSimulator26.5.sdk`—not the Store-qualified Xcode gate.

## Established source/API contracts

- Production `IosSnapshotFileSystem.kt:76–106` requests complete protection for
  newly created snapshots directories. Lines 362–374 write every protected file
  with atomic replacement plus `NSDataWritingFileProtectionComplete`.
- Copied observer `L08StorageProbe.kt.in:464–488` checks the actual owned
  directory/file attributes. The two comparisons currently cannot distinguish
  a null dictionary, missing key, or different protection value.
- Apple documents `NSFileProtectionKey` as NSString-valued and Objective-C
  `attributesOfItemAtPath` as returning nil on error. The selected SDK declares
  these constants without a simulator-specific exclusion. Declarations alone
  do not promise a returned value on every host filesystem.
- Complete protection describes locked-device file inaccessibility. A simulator
  returning that metadata is useful evidence of the actual Foundation/volume
  response—not proof of iOS hardware encryption, passcode/class-key behavior,
  file denial after lock, or actual backup exclusion.

## Counter-evidence and limits

Do **not** assume “Simulator runs on macOS, therefore protection metadata is
unsupported.” Apple Platform Security, published August 3, 2026, explicitly
supports Data Protection on Apple-silicon Macs and opt-in higher classes.
Conversely, that does not prove the iOS26.5 simulator exposes those classes.
The current Xcode guide requires physical devices for hardware-specific
features; it does not specify protection-key readback behavior.

No inspected official reference guarantees or forbids complete-protection
readback for these simulator paths. No native probe was run. SDK headers are
not the Foundation/CoreSimulator implementation. Archived iOS8-era guidance
and non-shipping swift-corelibs implementations are not current runtime proof.

## Useful diagnostic discriminator, if metadata actually fails

`NSURLVolumeSupportsFileProtectionKey` is a documented **read-only NSNumber**
capability (iOS14+, SDK NSURL.h:353). A future ownership-attested observation
could distinguish unsupported host-volume capability from a failed attribute
lookup or different class. It has **not** been queried here and must not become
a bypass or substitute for actual protection tests. Also avoid substituting
`NSURLFileProtectionKey` for directory attributes: the SDK groups that URL key
under regular-file-only resource keys, a different contract from NSFileManager.

Keep four independent claims separate: backup-exclusion metadata, encrypted
store roundtrip/tamper behavior, OS protection metadata, and physical lock/backup
behavior. Wait for the new precise failure stage; preserve every existing
assertion and fail-closed classification until the cause is demonstrated.

## Authoritative references (accessed September 7, 2026)

- [Complete protection](https://developer.apple.com/documentation/foundation/fileprotectiontype/complete)
- [NSData complete-file protection](https://developer.apple.com/documentation/foundation/nsdata/writingoptions/completefileprotection)
- [Protection attribute key](https://developer.apple.com/documentation/foundation/fileattributekey/protectionkey)
- [FileManager attributes](https://developer.apple.com/documentation/foundation/filemanager/attributesofitem(atpath:))
- [Directory creation](https://developer.apple.com/documentation/foundation/filemanager/createdirectory(atpath:withintermediatedirectories:attributes:))
- [Encrypting app files](https://developer.apple.com/documentation/uikit/encrypting-your-app-s-files)
- [Volume protection capability](https://developer.apple.com/documentation/foundation/urlresourcekey/volumesupportsfileprotectionkey)
- [Simulator/physical-device scope](https://developer.apple.com/documentation/xcode/running-your-app-on-simulated-or-physical-devices)
- [Apple Platform Security](https://support.apple.com/guide/security/data-protection-overview-secf6276da8a/web)

Exact source/header hashes, public response hashes/access dates, failed URL
attempts, applicability caveats and cleanup are in the adjacent JSON report and
`evidence/l08-file-protection-research-01/`. No application/control edits, native
probes, tests, builds, signing operations, or retained background workers.
