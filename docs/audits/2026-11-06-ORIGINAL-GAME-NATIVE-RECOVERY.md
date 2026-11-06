# Original-game native recovery acceptance

## Scope and status

**Prepared, not compiled/runtime-qualified or merged.** Included in still-draft
PR328, this focused #253 work extends its real Android Home journey to the other
two originally reported games and to actual native deletion failure. It introduces
no additional shipping implementation, dependency, workflow, wait or optimized
Release change. Consolidation avoids a separate duplicate qualification cycle.

## Real fixtures and journeys

Six explicit English/Arabic methods cover Last Light, Mafia and Whodunit using
the shipping Activity, Koin graph, encrypted files, preferences and native
accessibility actions. Each verifies cancellation, selected removal, list refresh,
Activity relaunch, surviving ciphertext-backed state and an unrelated credential.

- Last Light retains the existing definition/recovery checks.
- Mafia starts through `ConfigureAndStart` and uses its strict production codec;
  no role map or recovery phase is manufactured.
- Whodunit loads the actual bundled `last-dinner` case through the shipping
  repository/validator, assigns roles through its reducer and persists that
  validated case's exact version/digest. Unmarked legacy metadata is not treated
  as a current compatible investigation.

Two further EN/AR methods inject a real nonempty-directory deletion failure at
one exclusively UUID-owned synthetic snapshot path. They require the public
error text and Android **polite live-region** property, preserve the unrelated
save, restore the original authenticated ciphertext, cancel the failed dialog,
then begin a new explicit deletion. This is deliberate fault injection followed
by fault repair, not an automatic retry hiding an unexpected action failure.

Observation remains bounded; actions are sent once and native trees are never
logged. The fixture does not clear app data, shared keys or other saves. Expected
API34 roster: navigation3 + native storage7 + Home8 + label/action-owner5 =
**23 methods**. The five controls cover the native observation adapter, not
additional game journeys. API35
optimized Release remains3. These counts are requirements, not execution evidence.

## Qualification and remaining limits

Require fresh compilation/static gates, all18 actual native method identities,
the unchanged3 Release methods and full PR328 qualification. Source review,
normal protected merge and distinct main verification remain mandatory. Failed
earlier PR328 revisions are retained as failures, not acceptance evidence.

Native accessibility export is not TalkBack speech/gesture testing. Activity
relaunch and adapter readback are not OS process-death acceptance. These are
native **Home deletion** journeys with legal fixtures, not complete in-game
resume/multiplayer/device campaigns. iOS positive protected persistence remains
gated by #258; no physical Phase4 work is started or claimed. #253 stays open
until its remaining platform, process and accessibility criteria are satisfied.
