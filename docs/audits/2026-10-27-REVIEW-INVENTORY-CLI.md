# Review-inventory CLI safety — confirmed tooling fix

## Finding and cause

[#272](https://github.com/Apdelrahman1911/parlor/issues/272) tracks a P3 tooling
bug: `python3 scripts/generate_review_inventory.py --help` generated a complete
CSV in a file named `--help`. Mistyped options such as `--chekc` also wrote files.
The manual parser treated every argument except exact `--check` as an output
filename and discovered Git before understanding help/errors.

## Implementation

The standard-library `argparse` parser runs before repository discovery or
file I/O. Abbreviations are disabled; help exits0, invalid/extra arguments
exit2. Default/custom output, both `--check` orderings, existing inside-repository
path validation and stale-check nonmutation remain unchanged. A literal output
beginning with a dash requires the explicit `--` separator.

```bash
python3 scripts/generate_review_inventory.py --help
python3 scripts/generate_review_inventory.py --check
python3 scripts/generate_review_inventory.py custom.csv --check
python3 -B -m unittest scripts.release.tests.test_review_inventory -v
```

## Executed evidence and limits

The original isolated before-fix receipt retains1failure/5errors; all10 inventory
regressions pass after correction. The fix has now been cherry-picked into a
separate711cc1ab-based integration checkout. All10 tests pass there, including
help/no Git lookup, unknown/abbreviated options, extra positionals, generation,
check orderings, stale-check nonmutation and explicit literal filenames.
Actual subprocess invocation from a temporary directory **outside any Git
repository** also confirms help/invalid statuses and zero file creation.

This is CLI behavior evidence, not application/runtime or protected merge
qualification. The frozen ARM64/Intel benchmark checkout and AGENTS.md are
unchanged. Fresh integration-head inventory, strict local gates and protected
PR/post-merge verification remain required; #272 stays open until integrated.


The separate integration at `08a87d6a` subsequently passes the complete strict
local `productionCheck allTests`:3,187 current module report cases, zero
failures/errors (three existing real-LAN skips),749 release Python controls
(one known local-environment skip), Detekt/lint/R8/unsigned AAB and pinned shell
analysis. Its clean source and Gradle stop were verified; valid task-cache
outputs were reused. This later documentation does not attest a newer tree or
replace fresh protected PR/post-merge checks.
