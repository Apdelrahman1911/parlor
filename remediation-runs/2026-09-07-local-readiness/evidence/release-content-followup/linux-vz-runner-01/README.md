# Owned Linux ARM64 verification controls — review package

Author: `/root/release_fix_review`. These campaign-only files are excluded from
shipping/build source inventories. **They have not been executed, compiled,
syntax-checked, independently approved, or used to boot a VM at this checkpoint.**
The accompanying freeze identifies a review candidate, not runtime authorization.
Root owns the single build/native-runtime lane and all execution and cleanup.

## Intended evidence and limits

- A new Ubuntu 24.04 ARM64 guest runs the repository's real release Python tests,
  then attempts the exact `productionDesktopCheck` Gradle aggregate with strict
  dependency verification, JDK 21, one worker, and no parallel/build cache.
- Host validation binds the run token, source manifest/archive, control hashes,
  actual commands, raw logs, Python discovered/executed IDs, and raw Gradle XML.
  A subsequent Desktop failure or pre-build resource block does not discard a
  separately valid Linux Python PASS; the overall run remains FAIL.
- A Desktop PASS requires every source-manifest module with applicable test
  sources, an executed Jupiter discovery guard, and exactly the three existing
  disabled physical P2pKit fixtures in their original module. Their skips remain
  explicit; nothing is un-ignored or claimed as physical LAN evidence.
- This is not Linux x86/KVM managed-device, Android/iOS rendering, physical LAN,
  real signing, Store approval, or whole-project readiness evidence. A source tar
  has no authoritative Git history: it cannot establish the full review-inventory
  release aggregate. The release Python inventory tests use their own synthetic
  Git repositories; they do not pretend that the staged source has history.

## Isolation, budgets, and ownership

Pinned proposal: `../linux-vz-research/pinned-no-mount-proposal.yaml`, Lima 2.1.4
VZ, 2 CPUs, 3 GiB RAM, 4 GiB raw guest disk. The dated Ubuntu image and Temurin JDK
URL, digest, and download sizes remain those in `OWNED-LINUX-PROPOSAL.md`.
The host allows at most 6 GiB allocated task footprint and requires 8 GiB free.
The guest has a 1,800-second foreground budget; no resource expansion or blind
build retry is implemented. Full Desktop fit and SDK independence are unknown.

The approved shared ownership helper creates a new short, inode/UID/token-owned
temporary root (its historical `parlor-arm64-` prefix does not mean VM reuse).
Fresh HOME, LIMA_HOME, TMPDIR, cache and SSH/scp wrappers exclude personal config,
agents and credentials. There are no host mounts, existing instances, template
overrides, Store keys, Docker/Colima contexts, or global cache deletion.
Source and control reads reject symlinks, unsafe paths, size/count excesses,
changed bytes, and source/mode/inventory drift. Protected files are not staged.

After a Gradle attempt, the guest immediately invokes `./gradlew --stop`, retains
bounded XML/logs, precisely removes its generated build directories after Java
workers are gone, and invokes `--stop` again. Failure/cancellation paths repeat
necessary cleanup and keep failures explicit. At host finalization an attested
foreground SIGINT precedes audit-token-owned escalation; **never `limactl stop`,
`stop -f`, or historical numeric PIDfile signals**. Final source/holder probes have
their own cleanup. Any holder/ownership failure preserves the temporary tree;
it does not authorize signaling an unrelated OS/XPC helper. Guest/root source and
control identities are checked again. Verified duplicate evidence archives are
removed; compact raw evidence is retained.

## Independent review and root-only commands

1. Reopen all three controls and their shared hash-bound dependencies. Inspect
   `draft-freeze-01.json` and the author checkpoint; neither is an approval.
2. Run only pure control tests first, retaining command/exit/descriptors/hashes:

   ```sh
   /usr/bin/python3 -B -m unittest discover \
     -s remediation-runs/2026-09-07-local-readiness/evidence/release-content-followup/linux-vz-runner-01 \
     -p test_linux_vz_controls.py -v
   ```

   Tests deny subprocesses/signals/network unless an in-memory fake replaces
   them, and scope synthetic files with `TemporaryDirectory`. A pure PASS does
   not prove VZ, Linux, builds, or native shutdown. Root must confirm fixture
   cleanup and not mistake nested synthetic test IDs for repository tests.
3. Only after independent approval, select an unchanged-source completed root
   cycle receipt for the **current** checkout. Put the independently selected
   control approval JSON below this control directory. It must bind the exact
   host, guest, tests, and both shared controls using schema 1.
4. With the lane idle and root approval recorded, the proposed invocation is:

   ```sh
   /usr/bin/python3 -B "$CONTROL_DIR/owned_linux_vz.py" \
     --evidence-dir "$FRESH_CAMPAIGN_EVIDENCE_DIR" \
     --source-receipt "$CURRENT_COMPLETED_CYCLE_RECEIPT" \
     --source-receipt-sha256 "$INDEPENDENTLY_SELECTED_SOURCE_RECEIPT_SHA256" \
     --approval-file "$ROOT_APPROVAL_JSON" \
     --approval-sha256 "$INDEPENDENTLY_SELECTED_APPROVAL_SHA256" \
     --limactl /opt/homebrew/Cellar/lima/2.1.4/bin/limactl
   ```

   Do not invent values, reuse an evidence directory, or launch an unreviewed
   draft. Inspect every per-gate status and cleanup failure even when exit is 0.

## Research and still-unverified paths

`lima-control-source-0*.json` preserve exact-version public source excerpts,
URLs/access dates and failed lookups. `openssh-installed-reference-01.json`
records the installed manual's first-option-wins and persistence semantics.
Root's `../../../reviews/openssh-effective-disabled-multiplexing-01.json`
and raw output independently establish this installed SSH's disabled rendering:
ControlPath and UseKeychain are omitted, not printed as `none`/`no`.
The parser still rejects `ControlPersist=0` (indefinite persistence), enabled
values, duplicate fields, and missing required emitted fields.

Outstanding: independent review and pure execution; real YAML validation, VZ
availability/boot/cloud-init/SSH/copy behavior; download/SDK/dependency/build fit;
real child/token attestation and XPC holder shutdown; guest interruption and
evidence-copy failure under runtime conditions. Root must classify actual
failures precisely rather than treating these local gaps as external Store work.
