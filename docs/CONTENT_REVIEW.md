# Bundled Whodunit content review

Document status: current release checklist for creative/manual review. Kotlin
models, validators, reducers, and tests are the executable source of truth for
structural and gameplay compatibility; this checklist does not override them.

Apply this checklist to every JSON file in:

`game-modes/whodunit/src/commonMain/composeResources/files/cases/`

The current bundle contains seven cases: `last-dinner`, `iskenderia-corniche`,
`jasmine-ring`, `khan-el-khalili`, `layla-halabi`, `saidi-inheritance`, and
`zamalek-ramadan`. The resource registry and automated content tests must fail
if this set and the declared identifiers drift.

## Automated structural gate

Before creative sign-off, run:

```text
./gradlew :game-modes:whodunit:desktopTest \
  --tests '*BundledCaseLoadingTest*' \
  --tests '*WhodunitContentIdentityTest*' \
  --tests '*WhodunitPayloadHardeningTest*' \
  --tests '*ArabicCaseValidationTest*' \
  --dependency-verification=strict --rerun-tasks --no-daemon
```

The release-wide content gate must also pass through `productionCheck`. The
automated checks cover strict JSON decoding, schema/app/game compatibility,
safe and unique IDs, exact resource identity, supported modes/player counts,
bounded text and collection sizes, character/brief completeness, reveal-map
identity, clue-pool uniqueness and reachability, mode-specific evidence,
round-config support, and removal of placeholder author metadata.

An automated PASS does not prove prose quality or game balance.

## Manual review per case and language

- [ ] Title, subtitle, public intro, and all player-visible prose are final—not
  draft, test, placeholder, machine-instruction, or internal notes.
- [ ] The declared language matches the prose and renders correctly in its
  locale and direction.
- [ ] Every character has a distinct motive, secret, relationship, innocent
  brief, guilty brief, timeline, and playable goal.
- [ ] No dossier makes a character trivially guilty or impossible to accuse
  before discussion.
- [ ] Every possible killer has a coherent method, timeline, fake alibi,
  deflection target, panic move, evidence trail, contradiction, final clue,
  and reveal narrative.
- [ ] Clue text is understandable aloud, attributable to the intended person
  or object, and does not use ambiguous references.
- [ ] Red herrings distribute suspicion fairly and do not contradict bedrock
  facts or accidentally prove another killer.
- [ ] Classic and Elimination modes both have sufficient evidence through
  their maximum legal round trace.
- [ ] Reveal narratives explain method, motive, and evidence without leaking
  unrelated private secrets.
- [ ] No content contains unverifiable author attribution, personal data,
  unsafe external URL, store promise, or unsupported feature claim.
- [ ] English and Arabic text was reviewed by a fluent human for tone,
  grammar, cultural context, and RTL presentation.

## Playtest and release evidence

For each case, record the app SHA/build, case ID/version/digest, language, mode,
player count, assigned killer, result, duration, rule dispute, confusing clue,
stuck transition, and reviewer notes. Exercise every possible killer across
both modes over the release playtest campaign; a single successful game is not
balance evidence.

Release sign-off requires named product/content owners and a dated receipt.
Those names belong in the release record, not as invented JSON metadata.
