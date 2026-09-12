# L08 draft handoff — NOT EXECUTED

Author: `/root/native_fix_review`. All work remains in this directory. No
production/control files, original audit receipts, dependencies, or Git history
were changed by this agent. No Gradle/Xcode build, test run, simulator, app,
server, or persistent worker was started. Python authoring/AST parsing and
bounded official Koin 4.0.0 source research are not runtime verification.

## Root review / promotion inputs

Read `L08_README.md`, every template/helper/test, and **`promotion-02.patch`**.
`promotion.patch` / `promotion-inputs.json` are preserved superseded drafts;
do not apply them. `promotion-inputs-02.json` records the exact original and
proposed three control-file hashes for the current patch. Reject or regenerate
a separately numbered patch if those original controls change.

Only after independent review, the proposed promotion copies these files into
`scripts/verification/ios-readiness/` and applies the three-file patch:

- Nine Kotlin templates named by `l08_copy.L08_ADDITIONS` (including the two
  unchanged networking-testing fixtures).
- `L08StorageLaunch.swift.in`, `L08StorageUITests.swift.in`,
  `L08HostLaunch.swift.in`, `L08HostUITests.swift.in`.
- `l08_copy.py`, `l08_receipts.py`, the three `test_l08_*.py` files, and
  `L08_README.md`.

Do **not** promote this authoring README, the patch generator, freeze records,
research records, or superseded patches as application/source dependencies.
The generator only writes exclusive draft files here; it never applies patches.

## Review priorities

1. Native storage: `L08StorageProbe.kt.in`, both snapshot helpers, original
   recovery functions/Home coordinator/writer, strict parser and storage XCTest.
   The newly discovered recognized-current/valid-legacy preservation fix must
   be bound in the actual current source before green native evidence is claimed.
2. Started host: `L08HostProbe.kt.in`, both host witnesses, original retained
   runtimes/owner/coordinator, explicit test transport, and host XCTest. Do not
   relabel the synthetic transport as P2pKit or normal lobby/rejoin execution.
3. Copy-only scope and privacy: `l08_copy.py`, `test_l08_copy.py`, closed native
   receipts, and `promotion-02.patch`. The same loaded-framework inventory must
   bind the closed union of old and new observed runs before per-gate selection.
4. Failure handling: parser rejects unknown fields before copying evidence;
   native observers do not swallow cancellation as success; the runner's owned
   cleanup paths remain intact even when the extension fails.

Koin override ordering was checked against the official **4.0.0 tag**, matching
the version-catalog pin: default override is true, module flattening preserves
insertion traversal, and later definitions replace the same index. The native
fixture still asserts the actual injected transport identity. See both research
JSONs for URLs, access dates, full-source hashes, and exact excerpts.

## Exact continuation

No new tests have run. The 34 authored Python test methods cover copy contracts,
strict success/failure parsing, invariant mutations, source/native/boot binding,
secret-field rejection, and exclusive preservation. Root should execute these
with the unchanged existing controls first, then all controls after promotion.
For a draft-only Python check, put this directory **before** the existing
controls in `PYTHONPATH`; run unittest discovery with this directory as `-s`.
Do not run competing Gradle/Xcode lanes.

Next: independent full diff/source review → fix any harness defects → execute
control tests → create a fresh full source binding → independently approve it →
root-owned simulator run → inspect all actual receipts → record exact remaining
gaps and cleanup. No existing native receipt can substitute for those steps.

The authoring checkout advanced while root continued unrelated coordinated
work. `draft-freeze-01.json` records the final observed head/tree and relevant
dirty source hashes; it is **not** a full native execution binding. Root must
freeze current inputs again after any preservation repair or control change.
