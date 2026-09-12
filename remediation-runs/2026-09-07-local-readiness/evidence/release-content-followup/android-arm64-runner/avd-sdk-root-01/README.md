# CLI20 SDK-root correction — review draft

Author: `/root/release_fix_review`, 2026-09-07. This directory is campaign
evidence/control work, not shipping application code. **Draft tests and native
cloning have not been executed by the author.** The coordinator must independently
review, execute the pure tests, and own subsequent native/runtime verification.
Existing shared controls, frozen approvals, and stage02 APKs remain untouched.

## Original failure and exact-version proof

Android runtime02 failed before any emulator/app launch: `Package path is not
valid. Valid system image paths are: null`. The installed `avdmanager` wrapper
resolves beneath Homebrew, not the SDK containing the selected image.

* CLI20 wrapper line31 sets `com.android.sdkmanager.toolsdir=$APP_HOME`.
  Installed `AvdManagerCli.init()` bytecode at parent
  `avdmanager20-exact-bytecode-01.txt:141–220` reads that JVM property, canonicalizes
  it, and derives SDK root from its grandparent. Setting `ANDROID_HOME` and
  `ANDROID_SDK_ROOT` alone does not override this path. The property is **not**
  `com.android.sdklib.toolsdir`.
* Installed `LocalRepoLoaderImpl.getPackages()` at
  `repo-local-loader-cli20-bytecode-01.txt:33–70` writes the selected root's
  `.knownPackages` (`:525–556,633–652`); its fallback can replace package.xml
  (`:99–155,355–437`). Therefore pointing it directly at the user's real SDK is
  not a read-only correction, even with valid package metadata.
* Installed `AvdManager` is Kotlin, unlike the separately downloaded older Java
  source. Exact installed bytecode is authoritative: `getImageRelativePath()` at
  `avd-manager-cli20-bytecode-01.txt:2119–2280` derives an SDK-relative path and
  `setImagePathProperties()` at `:3486–3501` writes `image.sysdir.1`, removing
  `image.sysdir.2`. Its userdata path at `:3543–3618` accepts the installed public
  image's `data/` directory instead of requiring userdata.img.

The two bounded JDK21 `javap -J-Xmx64m -c -p` inspections exited0; original jars,
commands, logs, timestamps, and SHA256 bindings are in
`additional-installed-bytecode-receipt-01.json`. No target class was initialized.

## Proposed minimal correction

`owned_sdk_image.py` contains a small independent adapter:

1. Inventory only the selected installed API35/Google APIs/ARM64 rev9 image.
   Reject links/special files, excessive depth/count/size, metadata drift, and
   insufficient host headroom. Observed installed tree:45 descendants,
   4,058,038,626 logical bytes, no symlinks/specials.
2. Create `owned.path/sdk`, never reusing an existing destination. Clone regular
   files through **fclonefileat** from read-only source descriptors into owned
   directory descriptors. Each source/clone pair is streamed through SHA256.
   Recheck source identities and the complete inventory; record the full image
   hash manifest. There is no full-copy fallback. Partial outputs belong to the
   existing runner finalizer, never to the installed SDK.
3. Invoke the existing public CLI20 classpath directly through the attested JDK21
   executable. Supply exactly one `-Dcom.android.sdkmanager.toolsdir` pointing
   below the owned SDK; contain JVM home/tmp/error logs; discard inherited JVM
   option injection. Both Android SDK environment variables select the owned SDK.
4. Require the generated `image.sysdir.1` to equal the expected relative image
   path. Launch the existing absolute emulator executable with explicit
   `-sysdir <owned absolute image>` as well; never silently rewrite an unexpected
   image path. All user-writable AVD/home/tmp paths and ADB/process ownership
   controls remain as already reviewed.
5. Stop attested workers before the existing owned-root finalizer removes the
   AVD **and** the cloned image. Preserve APKs/compact evidence needed later.

Do not use `cp -c`: the installed Apple cp(1) manpage lines136–145 explicitly
documents a full-copy fallback. `fclonefileat` returns an error for unsupported
or cross-filesystem cloning. Apple clonefile(2) discourages directory cloning;
the adapter uses one regular file per call and the SDK header's exact ABI/flags.

## Runner integration points (coordinator only)

* After creating/attesting `OwnedDirectory`, call `clone_installed_image(sdk,
  owned, checkpoint)`; checkpoint raises on the runner's existing cancellation
  flag. Retain its result in `run.json`.
* Replace only the `avd-create` command with `cli20_invocation(...)`, preserving
  the original `create avd`, package, name, owned path, device, input, timeout,
  command registry, and failure finalizer. Set `commands.env` to the returned
  isolated environment. Caller attests the exact JDK21 executable separately.
* After the existing config ownership check, call `validate_created_image_path`.
  Add `-sysdir` and its returned owned absolute path to the existing emulator
  argv. Keep absolute installed `adb`/`emulator` paths and all transport/device
  attestation unchanged.
* Capture before/after identities/hashes of original image metadata and the
  original SDK `.knownPackages` (existence included). Native verification should
  prove the real scanner used the owned root and left original metadata intact.
* Before retrying stage02 APKs, bind the modified shared runner/new adapter to a
  **new** approval/freeze; do not edit old approvals. Updating the shared runner
  also invalidates the Linux draft's prior shared-runner hash.

## Proposed verification, not claimed results

Pure command, coordinator lane:

```sh
/usr/bin/python3 -B -m unittest discover -s <this-directory> -p test_owned_sdk_image.py -v
```

The14 authored tests cover independent destinations, scanner-like owned writes,
no-copy-on-error, existing-output preservation, symlinks/hardlinks/specials,
bounds, metadata drift/missing metadata, wrong clone bytes, source mutation,
cancellation/ownership loss, low disk, ABI/errno, explicit argv/environment,
and unexpected generated image paths. Fake cloning in these tests is **not**
APFS, installed CLI, emulator, or application execution evidence.

After independent review/pure execution: coordinator performs an owned tiny
native CoW fixture (including clone mutation leaving source intact), then an
owned image/CLI create preflight, then the retained-APK runtime retry if clean.
No global SDK installation, mutation, existing AVD, signing, Store operation,
or Gradle rebuild is required for this root-selection correction.

## Research and limitations

Official public references, accessed2026-09-07, are bound in the numbered
`official-reference-receipt-*.json` files:

* Google SDK source/repository/AvdManager sources are explanatory leads only;
  the installed CLI20 bytecode establishes applicability.
* `https://developer.android.com/tools/avdmanager`
* `https://developer.android.com/studio/run/emulator-commandline`
  (`emulator-commandline.html:9274–9278,10479–10487`) documents `-sysdir` as an
  absolute alternate system directory. Installed emulator metadata reports
  36.6.11/build15507667; actual launch/flag behavior remains runtime verification.
* `https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html`
* Apple SDK `sys/clonefile.h`, installed clonefile(2)/cp(1) pages, and official
  Apple source counterparts establish exact signatures, flags, and fallback
  semantics. Local copies/hashes are retained in `local-reference-receipt-01.json`.

Known limits: native clone support, owned-only SDK sufficiency for actual CLI
creation, emulator startup, and runtime instrumentation have **not** been
verified by this draft. A failure must remain explicit rather than enabling a
full-copy fallback, pointing scans at the original SDK, or claiming app success.
The author started no Gradle, emulator, VM, application, or persistent worker;
both javap inspections and public GET subprocesses completed normally.
