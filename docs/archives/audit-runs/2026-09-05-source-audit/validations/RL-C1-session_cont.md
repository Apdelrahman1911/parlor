# RL-C1 — Independent validation: GNU stat fallback corrupts byte count

- **Classification: CONFIRMED DEFECT (latent release-validator code), Medium.** Blocks otherwise valid Android candidate artifact validation on the declared Linux runner. Not an application-runtime/game defect; no Store upload failure is claimed.
- Finder: `/root/whodunit_cont`. Independent validator: `/root/session_cont` (2026-09-05).
- Baseline: `main`, HEAD `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; original user untracked files preserved.
- Exact affected source: `/Users/abdelrahman/Projects/parlor/scripts/release/validate_android_artifact.sh:20–28` (same root cause at 21 and 28). Caller: `/Users/abdelrahman/Projects/parlor/.github/workflows/testing-candidate.yml:177–185,389–432`.

## Reachability and proof

The CLI requires a regular, non-symlink AAB and immediately evaluates `$(stat -f %z "$aab" 2>/dev/null || stat -c %s "$aab")` as one arithmetic operand. The declared Android job uses `ubuntu-24.04`, whose native stat is GNU rather than BSD. GNU coreutils 9.4 upstream source was independently inspected:

1. `src/stat.c:1899,1919–1921` defines `-f` as a flag (filesystem statistics), **not** a format argument. `%z` is therefore the first filename and the actual AAB is the second filename.
2. `1259–1278` reports failure for the nonexistent `%z` operand, to stderr (which the script suppresses).
3. `1971–1976` still evaluates the actual AAB because `ok &= do_statfs(...)` does not short-circuit; the existing file emits default filesystem details (`1672–1676`, beginning `File:`), and the combined command exits nonzero.
4. The shell executes fallback `stat -c %s`, appending the correct number **after** the already-emitted filesystem text in the same command substitution.
5. The `[[ ... -le 536870912 ]]` operand is not a number. Validation stops at the bound guard for an in-bound AAB, before bundletool/signature validation. After fixing this occurrence, the identical dependency-report guard would otherwise fail too.

A suitable scenario is any small regular AAB on GNU/Linux while no literal `%z` file exists in cwd. An existing `%z` does not rescue the algorithm; the first command then succeeds with filesystem output and skips the numeric fallback. The script's intended byte-bound check and selected Ubuntu runner establish expected behavior without an invented product requirement.

## Counter-evidence and boundaries

- BSD/macOS `stat -f %z` is valid: the defect is Linux-specific, not proof that the current macOS iOS artifact guard fails.
- `2>/dev/null` removes only stderr, not the successful second operand's stdout.
- Workflow Android job has `if: ${{ always() && false }}` at178. Identity ownership is also blocked in `config/release-policy.json:9–15`. Therefore **no presently enabled Store workflow is asserted to reach this path**. The CLI and future enabled candidate implementation are affected; do not remove those guards to reproduce it.
- Existing workflow-contract tests assert cleanup tokens, not GNU stat execution. No existing passing suite proves this size check.
- Local host is Darwin with BSD `/usr/bin/stat`; no GNU `gstat` was found. **Source-level proof only; no genuine GNU runtime or artifact validation was executed.** Ubuntu manual retrieval timed out and is recorded, not treated as successful research. The GNU option semantics are established from upstream implementation; an exact future GitHub runner image/package version remains external evidence.

## Recommendation and regression coverage

Use an explicitly selected platform form or the already-required Python runtime's regular-file `stat().st_size`; avoid a fallback sharing partially successful stdout. Preserve the regular-file/no-symlink constraints and byte bounds. Add a tiny unsigned synthetic regular-file test of the preflight size helper on GNU/Linux and BSD/macOS, including spaces, at-limit/over-limit input and missing files. No key, AAB build, Store call, or disabled workflow activation is necessary to verify byte counting.

## Evidence and hygiene

Official source: <https://raw.githubusercontent.com/coreutils/coreutils/v9.4/src/stat.c>, accessed2026-09-05; SHA256 `c4b3f74e069e9f45e6110a1ba021e2367c70661aae2575869816794e216d748e`. Retained source/research receipts: `evidence/release-independent-session_cont/gnu-stat-9.4-source.txt` and `research.jsonl`. File hashes: `validations/RL-source-hashes-session_cont.json`. Coverage ranges appended to `coverage/reviews-session_cont.jsonl`.

No application/configuration/Git changes, signing material inspection, key generation, Store operation, Gradle/Xcode/app/server execution. Only audit text was created; no build artifacts or daemon were created by this validator. Root owns the shared verification/cleanup lane.
