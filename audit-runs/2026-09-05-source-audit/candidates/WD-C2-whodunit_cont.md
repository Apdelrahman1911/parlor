# WD-C2 — Last Dinner gives James two incompatible true cigar timelines

> **Final independent disposition (2026-09-05): CONFIRMED DEFECT — Low.** Validator `/root/mafia_cont`; see `validations/WD-C2-mafia_cont.md`. One grouped shipped-content chronology finding with four validated manifestations only: Last Dinner, Layla Halabi, Jasmine Ring and Khan el-Khalili. Age/Zamalek timing observations remain unconfirmed. Original candidate text below is preserved; its pending language is superseded. Administrative banner added by `/root/whodunit_cont` at `/root` request, not a new source approval.

- Originator: `/root/whodunit_cont`; independent validator: pending.
- Proposed classification: CONFIRMED DEFECT in shipped authored content, pending independent validation; proposed severity **Low** (narrative integrity, not rules/authority/privacy).
- Version: main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`; affected resource SHA is in coverage receipt.
- Absolute resource: `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/composeResources/files/cases/last-dinner.json`.

## Reachable path / proof
`last-dinner` is production catalog entry (`BundledWhodunitCatalog.kt:10–17`), six seats, both modes (`last-dinner.json:9–10`). James is one of all six assigned characters and can be the seeded killer. `DossierCard.kt:157–163,243–264` renders his guilty method and each guilty timeline entry on his own dossier; no test/prototype-only path.

James guilty timeline at `last-dinner.json:154–162` tells him at **9:00 p.m.**: “Smoking room. Light a cigar. Pretend to have been here longer.” It is not labeled fake alibi (that is a separate field at line 162).

The same James-killer case supplies `kp-james-4` at line **284** (“cigar was unlit until **9:15**”), `co-james-1` at **336**, `fs-james-2` at **360**, and the final James reveal at **376** (“returned to the smoking room and lit a cigar at **9:15**”). The final narrative is rendered for every game reaching James revelation, irrespective of whether the sampled clue includes the cigar. Both describe the same smoking-room cigar as factual narrative, with contradictory lighting times.

## Expected / actual / counter-evidence
Expected: the killer’s factual private timeline and the objective evidence/final explanation agree; a *fakeAlibi* may deliberately disagree. Actual: 9:00 versus 9:15 for the same event. Source establishes distinct `GuiltyBrief.timeline` and `fakeAlibi` (`WhodunitCase.kt:43–54`) and actual UI labels “YOUR TIMELINE.”
The game allows deception, but a deliberate lie is separately authored; this conflict is in the player’s own supposed method/timeline. No claim that the reducer changes winner or that this leaks a role. All six characters are assigned, so omitted-character guards cannot make this unreachable. No device/reproducer execution; deterministic resource/display proof only.

Recommended fix: content owner should select authoritative timeline and align method/timeline/clues/final narrative, without inventing new game rules. Add a content editorial consistency fixture or structured event facts if feasible. Do not automatically infer a single intended time from frequency alone. Similar claims elsewhere (library occupancy, Henry alibi) are not part of this confirmed candidate until independently established.

## Additional manifestations found while reading every story (independently validate before confirmation)

Grouped as authored-content semantic consistency rather than separate inflated defect counts. Paths share the absolute prefix `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/composeResources/files/cases/`; all are catalogued six-seat shipping content. A structural schema validator cannot establish this editorial correctness.

| Story / exact lines | Internally incompatible authored facts | Scope/counter-evidence |
|---|---|---|
| `layla-halabi.json:18,85,219` | Public intro says electricity failed **9:45** (`التاسعة وخمس وأربعين دقيقة`); publicUniversal `ld-bedrock-3` says **9:05**; Sami true timeline at **9:35** says power had already failed. | Main proof is public intro vs universal evidence; neither is a player lie. Not just metadata discrepancy. Repeated outage not authored. |
| `iskenderia-corniche.json:20,29–30,57` | Victim is **70**, Magda his **older sister** is **65**; optional backstory says she raised him after their mother died when she was15. | Age/order is unconditionally contradictory, all roles use relationship text. No unreliability/identity-twist specified. |
| `saidi-inheritance.json:20,29–30,57` | Victim is **72**; Zahra his **older sister** is **68**, raised him after their mother died. | Same copy-family age/order root as Corniche; no rules issue. |
| `jasmine-ring.json:77–83,325` | Nadim enters/hides in cellar **10:50**, victim arrives and he attacks at **11:12**; final narrative says he hid for a **full hour** before victim descended (`ساعة كاملة`). | Both are factual guilty-method/final account, not fakeAlibi. Twelve/twenty-two minutes cannot be an hour. |
| `khan-el-khalili.json:82–84,331` | Karim poisons at **9:40**, enters records with Refaat **10:00**; final account says truthful records presence **10:15**, poisoning the **minute before**. | Guilty timeline vs final factual chronology. Innocent Refaat also puts shared records at10:15; no intentional timing-twist authored. |
| `zamalek-ramadan.json:161,320,334` | Mokhtar embezzles for **two years** in privateSecret and finalStrong audit evidence; final narrative opens with **three years** of embezzlement. | Approximate round storytelling possible, but explicit two-/three-year facts differ. Lower-impact sibling; owner should adjudicate rather than validator invent timeline. |

Other suspicious details were *not promoted*: imaginative toxicology, plausible but odd occupations/ages, alibi lies actually labelled fake, motive twists conditional on killer, NPC servants beyond six seated suspects, approximate timing or witness observations that could be mistaken. No factual realism defect inferred without requirements.
