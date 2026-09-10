# Held Linux process-probe draft — source review only

Author: `/root/ci_workflow`. Baseline: `75c104734373703570a307de9c7a53d291bc2718`
(`4d5b660c5f508a3eb8d01cd9203f2503570dbb18`). No canonical file changed.

The existing five-job workflow gains one explicit, 10-minute, Ubuntu24.04
`linux-process-probe` selection. Other four jobs and all builds, SDK installation,
emulator/managed-device execution, package inspection and broad historical report
uploads are skipped. Full qualification keeps its existing commands and defaults.

The 271-line stdlib adapter uses existing hygiene context/source/SDK/ownership,
stop and cleanup helpers. Exact repository, branch, workflow, SHA, full history,
clean source, reviewed ten-file control digest, nonroot UID and SDK binding are
required. One actual host audit remains separate from two real-kernel controls
with explicitly synthetic self/direct-child enumeration. Expected nondumpable
`FAIL` only satisfies `CONTROL_PASS`; it cannot make a failed host audit pass.

Children have fixed isolated Python commands, verified PR_GET_DUMPABLE readiness,
12-second self-deadlines, EOF retirement and bounded direct-handle escalation.
No arbitrary PID is signaled. Exact children must be reaped and owned symlinks
retired before another control or unchanged final cleanup audit. Observations are
written before immediate Gradle stop; compact uploads gate final cleanup.

The shared `owned_dumpability_control(parent, binding, dumpable)` needs no fake
GitHub environment. Root may stage exact adapter/helper bytes in a read-only
nonroot-accessible `/tmp` packet and supply an actually nonroot-owned resource
parent plus explicitly synthetic SDK/emulator fixture. Do not chmod repository
or global SDK directories. The outer root lane owns evidence and Gradle stop.

Requires the independently reviewed diagnostic helper draft SHA256
`1fc2fb6f0b3bb7a505edc63277e0fe974a67ccf972753eec0da60386438c16f4`
from `android-proc-permission-diagnostic-01`; this proposal does not edit it.
Root must adopt or use a complete source-bound overlay before running repository
tests, because held copies retain ordinary ROOT-relative imports/contracts.

Source-only AST parsing passed for four Python files. Nine focused adapter tests
and two added workflow-contract tests are authored, **not executed**. No project
module import, process experiment, build, test, dispatch or cleanup cycle ran.
Reviewer-requested applicability correction explicitly skips this Linux-only
fixture class on non-Linux hosts; its UID/procfs/symlink controls are not portable
application tests. No production or runtime acceptance rule changed.
D03 remains FAIL. A bare-host probe cannot repair its post-emulator cleanup,
strict iOS protection, or same-source combined qualification.

Frozen proposed-file SHA256 pins:

| Path under `proposed/` | SHA256 |
|---|---|
| `.github/workflows/production-verification.yml` | `57b112d806277eeb861efc7d58cffc76878210a4c441a7229d064351ecb126f2` |
| `scripts/ci/linux_process_probe.py` | `2696b0d81f1113eeaea20b431191788925053703d2ed6e12d72489041c6f76b6` |
| `scripts/release/workflow_contract.py` | `d1b625c48af28eb0a53153f08b928fc818153d0c6eb4a9275bf7ed78700d7104` |
| `scripts/release/tests/test_workflow_contract.py` | `c0539b7b2daeef29936aa3c4763e3ee1876ff53b60fb9d68e787388eaa363372` |
| `scripts/release/tests/test_linux_process_probe.py` | `51e3086b6bc43712e689f3c06584cfc9659adbdf45564d16f60c8493f8891325` |
