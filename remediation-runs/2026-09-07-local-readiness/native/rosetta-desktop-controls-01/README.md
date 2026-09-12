# macOS x64 Desktop verification under Rosetta — draft controls

**Root executes only after independent review and the current iOS lane is fully
finalized. No JDK download, native probe, Gradle run, or control test has occurred
merely because these files exist.** This directory is excluded from application
source identity and changes no production source or current iOS controls.

## Fixed inputs and scope

- Public Eclipse Temurin21.0.12.1+1 macOS **x64** JDK archive:194,316,575bytes,
  SHA256`44db0f08196daf19a47f90d13388b0c943b67663cb537f998fe29e836fa842ce`.
  The existing `official-jdk-metadata-alternative.json` is a separate original
  official metadata/checksum receipt; it is preserved and included in binding.
- Existing checked-in Gradle8.13 wrapper; exact `productionDesktopCheck` task.
- This is **macOS x64 JVM execution translated by Rosetta**, not Intel hardware,
  physical LAN, native iOS, Android, Store signing, or a dependency upgrade.
- Keep strict verification. Missing x64 artifacts/checksums are honest failures,
  not permission to change verification metadata or repositories.

## Runtime and toolchain controls

The inner runner sets `JAVA_HOME`/`PATH` to the extracted JDK and passes
`-Dorg.gradle.java.home=...`. It also sets the Gradle properties
`org.gradle.java.installations.paths`, `auto-detect=false`, `auto-download=false`,
and empty `fromEnv`; a conflicting daemon-criteria file fails before execution.
The init script checks the real Gradle JVM home/`os.arch`, selects and checks each
Test/JavaCompile launcher, and verifies Kotlin `IN_PROCESS` at task execution.
The archive's `bin/java` must be x86_64-only Mach-O and the real Java probe must
report x64 JDK21. No architecture is inferred just from an archive filename.

There is one worker and one test fork, a3GiB Gradle heap, no configuration/build
cache reuse, and `--rerun-tasks`. Existing test assertions/discovery/ignored tests
are unchanged. The init script's Groovy integration is **not yet runtime tested**.
Exact Gradle8.13 docs and current official Kotlin strategy docs are retained in
`references/`; the actual pinned plugin's runtime property is checked, not assumed.

## Root-only sequence

1. Independently review every control and original metadata. Run the20pure
   synthetic unittest methods using the shared lane, only after it is free:

   ```sh
   /usr/bin/python3 -B remediation-runs/2026-09-07-local-readiness/run_gradle_cycle.py \
     rosetta-controls-01 --command /usr/bin/python3 -B -m unittest discover \
     -s remediation-runs/2026-09-07-local-readiness/native/rosetta-desktop-controls-01 -v
   ```

2. Freeze `run_rosetta_desktop.control_binding()` (import has no network/process
   side effects), independently approve that digest, then execute once:

   ```sh
   /usr/bin/python3 -B remediation-runs/2026-09-07-local-readiness/run_gradle_cycle.py \
     rosetta-desktop-01 --command /usr/bin/python3 -B \
     remediation-runs/2026-09-07-local-readiness/native/rosetta-desktop-controls-01/run_rosetta_desktop.py \
     --cycle rosetta-desktop-01 --approved-control-sha256 <reviewed-binding>
   ```

3. Review both receipts, raw XML/discovered test cases, actual launcher/Kotlin
   records and strict-resolution outcomes. No test is PASS until it executed.

## Resource ownership and cleanup

One public HTTPS GET is allowed, with at most3HTTPS redirects restricted to
GitHub/public release CDN, no credential/proxy configuration, alternate JDK,
outer retry, or resume. It is length/SHA-bound before extraction; reads check a
600-second deadline with at most a30-second blocking read. Extraction permits
only bounded regular files/directories/in-tree links; links are created last.
At least8GiB free disk is required before downloading. No global cache is removed.

JDK/archive/temp files use fresh0700`/private/tmp/parlor-rosetta-*` scratch with
inode/UID/nonce attestation. Only the independently reviewed Darwin audit-token
process controller can stop its recorded children. Failed/cancelled commands
still run finalizers. Immediately after the build the **x64** wrapper executes
`--stop`, then owned workers and JDK/archive are removed. Root's outer lane
preserves XML and performs its second `--stop` and exact module-output cleanup.
Uncertain scratch/process ownership is preserved with an explicit failure,
never broadly deleted/killed. Retained evidence consists of compact receipts,
logs, original document copies, and test reports. Destructive settings fixtures,
simulators, emulators, Store credentials, and connected phones are not involved.
