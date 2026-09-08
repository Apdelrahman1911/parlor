# Authentic emulator-package completion — unexecuted review draft

Author: `/root/factory_review`, 2026-09-07. **No active adapter, original shared
runner, application source, SDK, or build controls were changed. No tests, Java,
CLI, clone, emulator, or Gradle command were executed by this author.**

## Independently established failure

Runtime03's `002-avd-create.log` reports `"emulator" package must be installed!`;
`run.json` has exit1, zero instrumentation tests, unchanged global metadata and
removed owned scratch. The real tiny native clone passed; this was a later CLI
package requirement, not an app failure or an unsuccessful clone.

The installed CLI20 `defaultHardwareConfig()` (parent
`avdmanager20-exact-bytecode-01.txt:2280–2338`) always runs for `--device pixel_2`
(`:1758–1798`). Actual `EmulatorPackages.getEmulatorPackage` looks up the local
`emulator` package, then `EmulatorPackage.getHardwareProperties` reads its real
`lib/hardware-properties.ini`. The original image-only SDK therefore cannot
satisfy this path. A `--sdcard` value additionally reaches the package's
`mksdcard` executable; no such argument is passed in this frozen command.

`installed-emulator-package-classfile-01.json` records exact class bytes from the
actual CLI20 `sdklib.core.jar`, parsed without a JVM. `read_classfile.py` is a
read-only evidence decoder, not a build/runtime control. Its57 parsed instruction
positions/mnemonics for `defaultHardwareConfig` were compared exactly with the
previous independently captured javap output. The older downloaded Java source
is not substituted for the installed Kotlin implementation.

## Proposed correction

Root selected the complete-package approach rather than a metadata-only facade:

* Clone **all** files/directories of the installed API35/Google APIs/ARM64 rev9
  image **and** complete genuine Emulator36.6.11/build15507667 package into the
  fresh attested owned SDK. Never construct replacement package metadata, use
  links to the original SDK, call the scanner on the original SDK, or fall back
  to copying when CoW is unsupported.
* Both selected roots share512 entries and6GiB logical-size ceilings. Current
  read-only inventory:447 entries and5,254,594,975logical bytes. The emulator
  alone has401 entries/1,196,556,349bytes, with no links/specials/hardlinks. Keep
  the8GiB free-headroom guard; it is not a reservation.
* Pin the real emulator metadata, hardware definitions, `emulator`, and
  `mksdcard` bytes. Require regular files and owner-executable tools. Preserve
  safe read/execute permissions plus owner rw, stripping setid/sticky and
  group/other write bits. Package bytes, provenance, notices and other payloads
  remain unchanged. All directories remain within the owned0700 root.
* Keep source descriptors read-only, stream hashes at1MiB, retain per-package
  manifests and aggregate counts, and recheck **both** source inventories after
  the second package. Failure/cancellation leaves only attested owned partial
  outputs for the existing finalizer.
* Extend original-SDK metadata observation to the emulator package metadata and
  hardware definitions. Keep absolute installed adb/emulator commands, private
  ADB/profile paths, three actual instrumentation descriptors and native token
  ownership unchanged. No production APK rebuild is required.

## Draft files and promotion

`owned_sdk_image.py` and `test_owned_sdk_image.py` here are isolated proposals,
not the imported active adapter. `adapter-proposed-01.diff` and
`tests-proposed-01.diff` compare them to the preserved active files.
`integration-proposed-01.diff` contains only required runner metadata observation,
existing cancellation-fixture completion, and the source-discovery count update.
The `.proposed.py.txt` files are read-only complete text for root review; do not
execute them at this nested path.

After independent root review, root may apply the approved deltas to active
campaign controls and freeze new hashes. Current draft source declares30 adapter
tests (original14 unchanged assertions plus16 package regressions) and8 existing
root tests, so the bound driver must require38 actual methods, no skips.
Run only in the coordinated root lane with before/after complete control hashes,
AST/discovery/import/raw execution equality, then inspect all results. The draft
has only been AST-parsed; this is **not** an executable test result.

Regressions cover complete actual-shape payloads (synthetic bytes), every pin and
required file, non-regular metadata, executable bits and permission stripping,
shared size/entry ceilings, links/specials, missing package, second-package
failure/cancellation, first-package drift during the second clone, and unchanged
source bytes under owned scanner-like writes. Tests use synthetic TemporaryDirectory
fixtures; no private data or existing SDK contents are modified.

Finally root can retry the retained source-bound APK03 runtime after refreshing
approval. A CLI/create/runtime failure remains a failure. Capture inner/outer
cleanup and source/global metadata before/after. No physical-device, Store,
Linux/KVM or application success follows from this draft or the old actual22.
