# DS-C01 app-host V9 — idempotent public OS prerequisite

Author: `/root/native_fix_review`. These are task-owned verification controls,
not production code. Root owns the only execution/build/cleanup lane. No author
test, native run, or independent approval is claimed by this document.

## Why V9 exists

Apphost06 on immutable V8 reached the original Settings proof and both complete
local-game observation matrices. Its public Settings → General → Language &
Region receipt already showed unique, ordered, hittable English-primary and
Arabic-preferred rows, then an enabled Add Language row. No keyboard, search,
alert, or covered page was present. V8 nevertheless required Arabic to be absent
and stopped at `initialEnglishPrimary`. This is a harness assumption, not a new
application defect or proof of a crash. Creating a fresh simulator UUID did not
establish a monolingual global default. Neither source nor evidence establishes
where its existing Arabic preference originated; no host preferences are read.

## Narrow changes

- Keep the entire original 662-line XCTest matrix byte-identical, including the
  actual per-app OS English selection, in-app Arabic → System and restart proof.
  All Kotlin/Swift probes, original Settings22-row/four-boot assertions, local
  actual setup, public Start, native identity/full-bounds geometry, 72 passive
  samples per game and finalizers remain unchanged.
- Classify a current exposed, unambiguous, physically ordered public preferred
  list as `already-satisfied`, `requires-arabic`, or blocked. Require exact
  foreground/title/row/keyboard/search/alert guards, finite on-screen frames,
  unique English-primary representation and a genuine Arabic cell.
- For `already-satisfied`, reobserve the full actual preferred-list proof and
  return without tapping Add/search/primary-language choices. Record
  `arabicAddedByThisPrerequisite=false`; never claim to have added the language
  or infer its origin. The original per-app proof still runs next.
- For `requires-arabic`, retain V8's exact actual public add/search/keep-English
  UI path and timeout, then prove the full list. Record `arabic-added` only then.
  Missing, ambiguous, wrongly ordered or covered initial states remain blocked.
- Execute 24 pure Swift decision cases inside the same matrix XCTest. Add 18
  Python parser/source contracts. The V8 addition and Start parsers remain
  unchanged and consumed; all earlier 130 controls remain discoverable.

## Identity and review

The source is freeze02: 669 inputs, source SHA
`9ac2be536979f2216b39c10a6d148125a12380987d4efd483bbcbaeb1733318e`, tracked diff
`148fb4583d8fa8341b3cbc48a7fc0bc7564bf4df5c27e9d80e32c6bfdc57b784`, HEAD
`3625d0663ba6eb51338cbd5f9dc45f859ec18846`. Root's fresh `source-bindings.json`
binds the 613 copied build inputs. Inherited V8 binding is preserved separately,
not silently overwritten. All 23 V8 reference controls consumed by the new
contracts are full-path/hash-bound in addition to the prior V6/V7 controls.
Executed V8 files are untouched.

Root must first bind, then obtain independent control approval, run focused
contracts with its cycle finalizer, and obtain separate native execution GO:

```sh
/usr/bin/python3 -B -m unittest discover \
  -s remediation-runs/2026-09-06-approved-policy-completion/native/dsc01_apphost_v9 \
  -p 'test_*.py' -v
```

Only a completed, source/control-bound native cycle with all five XCTest cases,
all four original scenario proofs, all interaction receipts and cleanup PASS
can support DS-C01 verification. Prerequisite PASS, Python controls, Swift
compilation, or successful local-game observations alone are not that result.
Unknown per-app Settings selectors remain an explicit evidence gap. No physical
LAN, release signing, Store, or comprehensive accessibility claim is implied.
