# Current iOS catalog event loss and isolated investigation

## Confirmed failure; cause not yet established

[PR#282 run40794322752](https://github.com/Apdelrahman1911/parlor/actions/runs/40794322752)
at `841a0b0623b508eae103f79ba7b4e5773e6a8b4d` fails the English additional-game
journey's line116 Host-choice assertion **after opening Word Impostor**, not its
Home-brand assertion. The earlier Arabic attribute-cost case passes; this does
not qualify the failed run or establish a universal accessibility-latency fix.

The actual frame observations at00:49:29.625 and00:49:30.604 agree exactly:
`(24,419.6666564941406,354,267.9999694824219)`. Hittability succeeds. The original
single50ms event at00:49:30.812 targets `(201,553.6666412353516)`, inside a fully
visible card. The method's own recording and failure hierarchy retain Home after
the unchanged10s destination wait. No clipped-target explanation is supported.

Artifact12864545980's independently matched API/download ZIP SHA256 is
`3366df3a70655af4a7dbe4f8d69a88156c942fe331912184e3adedb7221abf36`.
Swift executes23methods:22pass/1fail. Independently parsed132KMP Simulator suites
contain679cases, zero failures/errors/skips. Actual UIKit runtime is iOS18.5,
iPhone16Pro, Xcode26.3/17C529—not the original iOS26.2 failure or a physical device.
The run subsequently finishes **failed** at01:35UTC: all seven other producers
and the original optimized Release join pass. The protected acceptance pipeline
correctly rejects it, without a merge or accepted-main receipt.

[#267](https://github.com/Apdelrahman1911/parlor/issues/267) retains this related
public-input investigation. A shared cause with its original lost Host event is
**unproven**; #283's unused pre-tap attributes and #276's delayed text remain distinct.

## Implemented non-shipping investigation

`pin03_catalog_copy.py` composes the existing PIN01/PIN02 plans, adding public
catalog pointer/callback/stage enums without relaxing their original decoders.
No player/game state, names, roles, credentials or keyboard coordinates are recorded.
The original arm is byte-exact. Observed arms preserve selectors, single tap,
successful path and original10s destination predicate; export begins only after
that original predicate fails. Native recognition/timing neutrality is not assumed.

`run_pin03.py` now materializes the actual frozen build inputs and implements
Kotlin Native/Swift app linkage, original/Compose/native execution and failed-only
capture retention. It owns fresh journaled devices, exact Xcode/SDK/runtime,
bounded commands, an isolated Gradle registry, build/product/input manifests and
native-worker cleanup. Unknown source additions, changed products, foreign tokens,
unsafe files and incomplete results reject. Failures remain in original xcresults.

The separate evidence-only workflow uses **one standard `macos-15` runner**,
sequential arms, read-only dependency cache and no paid infrastructure. It cannot
replace Production verification. Diagnostic completion can include faithfully
retained failed journeys; it is not a green application test or a root-cause fix.
The release contract registers its exact reviewed SHA256 image; eight mutations
to privileges, paid runners, triggers, cancellation, caching, commands or uploads
reject. Existing unknown-workflow rejection and all production checks remain.

## Executed local evidence and remaining gates

Twelve focused source/archive/result/privacy controls pass. Actual frozen841a0b06
materializes1,143original build inputs; all three copy plans and post-plan hashes
verify without changing the source checkout. These are Linux software controls,
**not** SDK compilation, native application linkage or touch execution.

The first full local check at17f0c6a9 correctly fails the closed workflow registry
because the new diagnostic workflow was not yet registered. Its1,867Desktop
(3existing LAN skips),671Android Debug+671Release and120GitHub-test cases pass;
source is unchanged and private Gradle stop succeeds. The original failed log is
preserved. Exact registration and its sensitivity controls are implemented before
any publication; a fresh committed-source complete check remains required.

Before attributing causality, inspect the original event and app-side receipt
ordering. Native build/export execution, complete60-journey neutrality controls,
the original iOS26.2 runtime and physical touch/VoiceOver remain separate gates.
No longer/replayed gesture, permission grant, Release deoptimization, check bypass,
Store publication or overall production-overhaul completion is claimed here.
