# Catalog game identities — October 28, 2026

## Confirmed presentation gap and scope

Actual Desktop catalog inspection showed that five games reused the same
artwork. `HomeScreen` inferred the drawing from `ParlorAccent`, which has two
values, rather than consuming a binding-supplied visual identity. This is a
confirmed presentation limitation, not a broken game rule or privacy defect.
It extends [#259](https://github.com/Apdelrahman1911/parlor/issues/259); no
duplicate Issue is needed.

The shared design system now supplies six static, cached 24dp vectors and a
64dp badge. The composition-root bindings explicitly select CaseFile
(Whodunit), Moon (Mafia), Cards (Last Light), Tiles (Egyptian Dominoes), Wink
(Ghamza), and Conversation (Word Impostor). Home consumes the supplied motif,
not a game-specific branch or an accent-to-game lookup. Existing accent,
localized copy, capabilities, layout, navigation and click handling remain.

The motifs are decorative and non-directional: no additional screen-reader
label, focus target, touch handler or animation is introduced. The enclosing
card still owns its single localized button label/action. There is no bitmap,
font, dependency, protocol, persistence or release-workflow change. The
[game-registration guide](../HOW_TO_ADD_A_GAME.md) documents this boundary.

## Executed regressions and sensitivity controls

Four new methods execute under strict dependency verification:

- The common test checks six cached, uniquely named, nonmirrored vectors with
  the reviewed viewport. Android and Apple execution must qualify separately.
- Desktop rendering paints all six motifs in English/Arabic, light/dark,
  at 200% text: **24 PNGs**. Pixel comparisons require distinct artwork and
  unchanged non-directional shapes across LTR/RTL.
- Real shipping bindings select all six expected motifs in both languages.
- The actual Home card changes pixels when only the binding's artwork changes;
  game ID, accent, copy and bounds stay fixed. All four locale/theme variants
  run at 200% text and retain one accessible button label and click action.

Three independent temporary mutations each produce exactly one failed method:
hardcoded Home consumption, hardcoded badge vector, and a duplicate shipping
binding motif. Original source bytes are restored after every control; all
four methods and shared/app plain and Desktop type-aware Detekt pass again.
Failed XML/logs, source/test hashes and restored receipts are retained under
`.git/agent-custody/overhaul-2026-10-28/setup-pointer/artwork/`.

```bash
./gradlew :shared:design-system:desktopTest --tests '*ParlorArtworkTest*' \
  --tests '*CatalogArtworkRenderingTest*' :composeApp:desktopTest \
  --tests '*HomeCatalogArtworkTest*' --dependency-verification=strict
```

## Acceptance limits

The generated Desktop rasters are visual evidence, not UIKit/Android rendering,
TalkBack/VoiceOver or physical-device testing. No mobile CPU, allocation,
battery, thermal or latency improvement is claimed. Full committed-source
`productionCheck allTests`, fresh hosted architecture/Release checks, protected
integration and automatic actual-main qualification remain mandatory.

This is an implemented, independently testable catalog improvement, **not**
completion of the whole application/game redesign. #259's native/physical
journeys, #267's original iOS26.2 input cause, strict protection and Store
acceptance remain separate outstanding gates.

The first full check exposed the unchanged real-Gradle fixture's partial PID
publication race. Its [#279 correction and sensitivity test](2026-10-28-GRADLE-MARKER-PUBLICATION.md)
are a separate non-shipping prerequisite; the failed aggregate is retained.
