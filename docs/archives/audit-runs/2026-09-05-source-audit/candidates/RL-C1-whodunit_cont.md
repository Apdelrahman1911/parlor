# RL-C1 — Android artifact size guard mixes BSD/GNU stat output

Status: **CONFIRMED DEFECT — independent source validation**. Finder: `/root/whodunit_cont`; independent validator: `/root/session_cont`, `validations/RL-C1-session_cont.md`. Severity: Medium, latent release-validator defect, not an application-runtime defect. Baseline: main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; no tracked modifications.

Independent conclusion: GNU9.4 source confirms all operands are evaluated and successful filesystem stdout survives into the fallback; Bash receives nonnumeric output. No genuine GNU runtime was available. This is a complete source-level proof, **not an executed Linux/AAB or Store check**. Exact official-source URLs/hashes and counter-evidence are in the validator report and `evidence/release-independent-session_cont/research.jsonl`.

## Source and applicable path

- `/Users/abdelrahman/Projects/parlor/scripts/release/validate_android_artifact.sh:20–28`, SHA-256 `1d0d423a3e149d9db85a17d82bfff9f9998239202d04a741e0779ea8218c7428`.
- `/Users/abdelrahman/Projects/parlor/.github/workflows/testing-candidate.yml:177–186,395–432`: the Android candidate job selects `ubuntu-24.04` and invokes this validator before retaining/uploading the immutable signed candidate.

The guards at21 and28 use `$(stat -f %z "$file" 2>/dev/null || stat -c %s "$file")` as a numeric operand. BSD stat accepts the first spelling. GNU stat instead treats `-f` as filesystem mode, `%z` as a filename, and the actual file as another filename. The suspicion is that it prints filesystem information for the existing file before failing for `%z`; the fallback adds the byte count without discarding prior stdout. Bash then receives a multiline, nonnumeric arithmetic operand rather than just bytes and rejects a valid small file.

## Reproducer/proof requested, not executed by finder

On GNU coreutils/Linux, create a small synthetic regular file. Capture stdout, stderr and status for the exact first command and combined expression, then evaluate the exact guard. Confirm GNU version and show that an ordinary within-bound file fails before any AAB-specific validation. Repeat the report guard, or establish the same shared expression. No actual signing, Store credentials, real AAB or Gradle build is required for this isolated shell behavior.

## Counter-evidence and limits

BSD/macOS stat may accept this correctly; no macOS-only failure is claimed. Every current candidate job has `if: ${{ always() && false }}` and preflight checks Store identity approval; `com.parlor.app` is intentionally blocked. Therefore no current workflow execution or Store failure is claimed. Direct invocation of the first guard is reachable without secrets; isolated runtime reproduction remains unexecuted. No generated outputs or processes created by finder.

## Suggested remediation/coverage

Use one bounded portable file-size reader already available to this script (e.g. Python `Path.stat().st_size`) or select the platform-specific command without combining partial failure stdout. Preserve file/symlink and size limits. Add real Bash/GNU and Bash/BSD synthetic-file tests, including exact bound, over-bound, nonexistent file and symlink cases. Do not disable release guards/workflows to test this.
