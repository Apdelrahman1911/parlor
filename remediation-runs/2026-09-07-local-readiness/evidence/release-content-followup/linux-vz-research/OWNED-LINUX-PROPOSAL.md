# Bounded Linux ARM64 alternative — research, not execution

Reviewer: `/root/release_fix_review`. Root owns execution/independent approval.
No VM was listed, created, started, stopped, or reused. No full image/JDK archive
was downloaded. Only public help/version calls and authoritative source/metadata
requests ran. One HTTP Range request read exactly104 image-header bytes.

## Available target and honest scope

Installed Lima2.1.4 exposes VZ and `start --foreground`. This repository's
`.github/workflows/production-verification.yml` has an actual `ubuntu-24.04-arm`
`productionDesktopCheck` job; LinuxARM64 Skiko0.9.37.4 and ComposeDesktop1.10.3
artifacts are in verification metadata. This can exercise that host target, not
Linuxx86/KVM Android managed devices, native Intel hardware, physical LAN, iOS,
or Store signing. Availability is not a successful guest/build receipt.

## Pinned image and resource envelope

The accompanying standalone YAML does not import a template or latest fallback.
URL: `https://cloud-images.ubuntu.com/releases/noble/release-20260615/ubuntu-24.04-server-cloudimg-arm64.img`

SHA256: `cafa1a965b591b7c4184b484ffd8e625981a79d48f9b4ae8a4adf7b4c5ade927`.
Both installed Lima's image template and Ubuntu's dated SHA256SUMS agree.
HEAD reports614,949,888bytes;206 Range header confirms qcow2 virtual
3,758,096,384bytes (3.5GiB). Proposed guest:2CPUs/3GiB RAM/4GiB raw sparse disk.
Lima's native qcow2reader can convert to raw; missing `qemu-img` is not alone a
blocker. No ASIF device attachment, Rosetta share, GUI, containerd, or nested VM.

Root's total6GiB task budget must include image cache, source/JDK archives, logs,
and the disk. FourGiB is a **guest disk ceiling, not a measured build fit**.
Monitor allocated host blocks and guest free space before each stage; stop before
6GiB aggregate or unacceptable host free space. Delete only owned cached image
copies after conversion is attested; retain original compact SHA receipt. The
fetched JDK proposal is205,641,175bytes compressed; no archive fetched. If full
Desktop dependencies cannot fit, record that gate BLOCKED rather than expanding
silently. Linux release Python tests can still be a narrower real-host result.

## Host isolation

Create a fresh short `mkdtemp` root, e.g. `/private/tmp/pl-vz-<random>`, chmod0700,
and record creatorUID/inode/random ownership marker before any launch. A short
path matters: Darwin Unix socket paths have a104-byte limit. Do not put Lima
sockets below the long repository evidence path.

All processes receive a constructed environment, not `os.environ.copy()`:
`HOME=<owned>/home`, `LIMA_HOME=<owned>/lima`, `TMPDIR=<owned>/tmp`,
`XDG_CACHE_HOME=<owned>/cache`, a reviewed public-tool PATH, and a fixed locale.
No SSH agent, proxy, cloud, Gradle, signing, or personal environment is inherited.
On macOS Lima's downloader uses Go `os.UserCacheDir()`, normally
`$HOME/Library/Caches/lima`; **LIMA_HOME or XDG_CACHE_HOME alone is insufficient**.
Private HOME owns that cache and no personal cache needs reading or deleting.

The YAML enables `plain`, which v2.1.4 explicitly clears mounts/containerd from,
and independently sets no mounts/base templates/port forwards/copy-to-host.
SSH public-key loading, agent/X11 forwarding and proxy propagation are false.
Lima-generated SSH key/config files stay in fresh LIMA_HOME. Its public source
uses `-F /dev/null`, a private IdentityFile and `/dev/null` known-host output;
no personal SSH config/key/known-host file is needed. Do not use an existing VM,
Colima/Docker context, default `~/.lima`, shared mount, or host source directory.

## Owned lifecycle — required before approval

`limactl start --foreground --tty=false <fresh-name>` is the supported route to
keep the hostagent lifetime attached to an owned launch. Reopen exact foreground
exec implementation and complete the native audit-token child probe before use.
Attest the direct child plus its driver/SSH helpers by native birth/UID/audit token;
keep waitable ownership until final descendant capture. Never use historical PID
files as independent authority.

**Do not blindly call `limactl stop` or `stop -f`.** Exact v2.1.4 `instance/stop.go`
uses numeric PID signaling, including stale PIDfile scans in force-stop. Prefer
an attested foreground hostagent SIGINT (the same graceful request), bounded wait,
then only audit-token-attested TERM/KILL as needed. Close any owned SSH control
master via its inode/UID-attested private control socket; verify descendants and
socket endpoints before removing the owned tree. Lack of attestation is a cleanup
failure, not permission to signal an arbitrary process. No launchd/autostart.

## Staging and checks

1. Root freezes the exact source manifest, including build-consumed untracked
   files. Stage only the reviewed public allowlist; reject symlinks/escapes. Do
   not use `git archive HEAD` as a substitute for a dirty-source freeze, or copy
   private local.properties/credentials/user saves. Keep original checkout intact.
2. Create from standalone pinned YAML; use owned `limactl cp --backend=scp` for
   source/JDK archives, never mounts or shell `--sync`. Shell uses explicit guest
   workdir and `--preserve-env=false`. Capture guest uname/os-release/tool versions
   and staged-file hashes; compare against root source manifest before execution.
3. Start with JDK21/GNU coreutils/Python availability and the release Python tests.
   Then try `./gradlew productionDesktopCheck --dependency-verification=strict
   --no-daemon --max-workers=1` with a recorded modest Gradle heap and in-process
   Kotlin compiler; do not alter source/dependency verification to make it fit.
4. Every guest Gradle cycle: capture exit/XML/sanitized log, `./gradlew --stop`,
   precise owned outputs cleanup or `clean --no-daemon`, then `--stop` again.
   Downloaded dependency caches are in the disposable guest, not host global cache.
5. Collect only compact evidence before VM stop; remove source/JDK archives and
   full guest disk/cache after their final use. Verify owned workers are gone.

## Still unresolved / not claims of PASS

- The YAML has not been run through Lima validation or a boot. VZ availability,
  image conversion, first-boot SSH/cloud-init, network, and safe shutdown need
  real owned-runtime evidence.
- The3GiB/4GiB resource choice may be too small for all Desktop checks. JDK/Gradle
  first-download size and headless libraries must be measured, not assumed.
- The CI ARM job has no explicit SDK install, but hosted runners may already have
  ANDROID_HOME. Android plugin configuration might require a public SDK even for
  Desktop-only tasks; actual configuration will decide. Do not claim the SDK is
  unnecessary merely from the job YAML.
- Full `validate_release_system.sh` also calls the review-inventory validator,
  requiring real Git history/baseline9cd4040a81c4f2f8fe6f5f161dabcd5351682c02.
  A plain source tar cannot establish that aggregate PASS; narrower release tests
  are distinct evidence. Never invent history or copy private Git configuration.
- This proposal is not an independently approved native lifecycle runner. Root
  decides whether the bounded alternate-host run is worth the remaining budget.

Authoritative exact-version excerpts and access dates are in
`official-*-references-0[2-5].json` plus the first image reference receipt. All
404 research attempts are retained and are not evidence for a successful claim.
