# Historical material

Start with the [current documentation](../README.md) and
[project status](../PROJECT_STATUS.md). This directory preserves useful design
history, handoffs and verification evidence; it is not the current task list,
shipping UI, or a certificate for a newer source revision.

## Contents and original locations

| Archive | Original repository location | Purpose |
|---|---|---|
| [history/](history/) | Historical root documents and older `docs/` plans, listed below | Design baselines, findings, superseded plans and phase reports. |
| [handoffs/](handoffs/) | `handoffs/` plus the prose checkpoints listed below | Transfer payloads, manifests and dated continuation notes. |
| [audit-runs/](audit-runs/) | `audit-runs/` | Original audit source snapshots, scripts and results. |
| [project-code-audit/](project-code-audit/) | `project-code-audit/` | Independent findings and review evidence. |
| [design/web-ui-rework/](design/web-ui-rework/) | `design/web-ui-rework/` | A browser-based visual concept, not the Compose application. |

The prose documents were relocated as follows; filenames are unchanged:

| Original path | Archived document |
|---|---|
| `ARCHITECTURE.md` | [history/ARCHITECTURE.md](history/ARCHITECTURE.md) |
| `PROBLEMS_PARLOR.md` | [history/PROBLEMS_PARLOR.md](history/PROBLEMS_PARLOR.md) |
| `whodunit-game-design.md` | [history/whodunit-game-design.md](history/whodunit-game-design.md) |
| `docs/APP_PLAN.md` | [history/APP_PLAN.md](history/APP_PLAN.md) |
| `docs/DESIGN_TOKENS.md` | [history/DESIGN_TOKENS.md](history/DESIGN_TOKENS.md) |
| `docs/FR_REMEDIATION_FINDINGS.md` | [history/FR_REMEDIATION_FINDINGS.md](history/FR_REMEDIATION_FINDINGS.md) |
| `docs/MOCK_BACKEND.md` | [history/MOCK_BACKEND.md](history/MOCK_BACKEND.md) |
| `docs/MOTION_DOWNGRADE.md` | [history/MOTION_DOWNGRADE.md](history/MOTION_DOWNGRADE.md) |
| `docs/P2P_REMEDIATION_PLAN.md` | [history/P2P_REMEDIATION_PLAN.md](history/P2P_REMEDIATION_PLAN.md) |
| `docs/P2P_REMEDIATION_STATUS.md` | [history/P2P_REMEDIATION_STATUS.md](history/P2P_REMEDIATION_STATUS.md) |
| `docs/PARLOR_P2P_SMOKE_TEST.md` | [history/PARLOR_P2P_SMOKE_TEST.md](history/PARLOR_P2P_SMOKE_TEST.md) |
| `docs/PHASE_0_VALIDATION.md` | [history/PHASE_0_VALIDATION.md](history/PHASE_0_VALIDATION.md) |
| `docs/PHASE_8_VALIDATION.md` | [history/PHASE_8_VALIDATION.md](history/PHASE_8_VALIDATION.md) |
| `docs/PROGRESS.md` | [history/PROGRESS.md](history/PROGRESS.md) |
| `CONTINUE_HERE.md` | [handoffs/CONTINUE_HERE.md](handoffs/CONTINUE_HERE.md) |
| `docs/PARLOR_PROJECT_HANDOFF.md` | [handoffs/PARLOR_PROJECT_HANDOFF.md](handoffs/PARLOR_PROJECT_HANDOFF.md) |
| `docs/AGENT_CONTINUATION_2026-09-08.md` | [handoffs/AGENT_CONTINUATION_2026-09-08.md](handoffs/AGENT_CONTINUATION_2026-09-08.md) |
| `docs/AGENT_CONTINUATION_2026-09-08_LINUX.md` | [handoffs/AGENT_CONTINUATION_2026-09-08_LINUX.md](handoffs/AGENT_CONTINUATION_2026-09-08_LINUX.md) |
| `docs/BRANCH_CONSOLIDATION_2026-09-12.md` | [handoffs/BRANCH_CONSOLIDATION_2026-09-12.md](handoffs/BRANCH_CONSOLIDATION_2026-09-12.md) |

## Preservation and reproduction

The pre-cleanup layout is available at commit
`f8e2ec3d6aa6669b2f10650c115ae37c38eb67a1`. The three evidence packages
(`audit-runs/`, `project-code-audit/`, `handoffs/`) contain **1,966 files**;
their contents and Git file modes, and all **19 prose documents**, were retained
verbatim. The WebUI concept retains its HTML and JavaScript; only its README
and two relative CSS font URLs were adjusted for the new location.

Historical documents, manifests, hashes, embedded paths and commands retain
their original meaning. Their internal relative links may refer to the old
layout: use the mappings above or inspect the original file, for example:

```sh
git show f8e2ec3d6aa6669b2f10650c115ae37c38eb67a1:ARCHITECTURE.md
```

An old manifest attests only its recorded payload, not this new directory
layout. Archived audit scripts use original parent-relative roots: **do not run
them in place**. Reproduction requires the original recorded Git checkout,
layout, toolchain and ownership conditions, not rewritten historical controls.
Preserved failures remain failures; successful old runs do not qualify new
source or replace missing physical-device/Store evidence.

Archived `build/` directories, logs and bundles are retained reports and
evidence, **not disposable live build output**. Do not clean them with recursive
name-based deletion. `.gitattributes` preserves archive bytes across hosts.

## Deliberately kept outside the archive

- `remediation-runs/` still contains executable workflow controls, pinned
  helpers, source bindings and fixtures loaded by current verification tests.
  Some live imports come from its `actions/`, `reviews/` and `evidence/`
  subdirectories. Moving or deleting them requires a separate reviewed tooling
  migration; their old dates are not proof that they are unused.
- [`docs/review/`](../review/) contains the actively checked mechanical
  inventory together with its historical independent-review references.
- Shipping resources, fonts, licenses and the branding source master remain in
  their existing application/source locations.
