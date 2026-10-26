# Original-game room-code readability

## Confirmed finding and correction

Follow-up to [#259](https://github.com/Apdelrahman1911/parlor/issues/259).
Whodunit, Mafia and Last Light's host lobbies still used `displayHero` (48 sp)
inside a card with outer and inner padding. The newer generic lobby had already
adopted a smaller monospaced style, so its passing tests did not cover these paths.

An executed geometry reproduction using the actual theme, fonts, card and padding
at 320 dp shows the valid six-character identifier `WWWWWW` wrapping into two
lines **even at 100% text size**. Both languages and both themes fail. This is a
real rendered layout defect, not a report of a failed network join or native
end-to-end host journey.

`RoomCodeText` now centralizes presentation for all four host rendering paths:
the three original games and the generic lobby serving the newer three. It uses
the existing 22-sp monospaced token, explicit LTR direction and centered placement.
It preserves full system text scaling and the original identifier. There is no
truncation, ellipsis, input normalization, font-scale override, dependency change
or protocol change.

## Executed evidence

- Baseline `6704ed9c`: four tests ran and **four failed**, each observing two
  lines at 100%. The original three call sites' typography/padding were reproduced
  directly; this was not execution of their complete host flows.
- Corrected `d9b44f5b`: strict `RoomCodeLayoutTest`,
  `:shared:design-system:detektMetadataCommonMain` and `detektDesktopTest`
  **passed**, exit 0, 57 seconds. Four methods cover eight EN/AR × light/dark ×
  100/200% configurations. Assertions check one complete line, each glyph's bounds,
  increasing LTR positions, no visual overflow and unchanged requested font scale.
- Eight corrected screenshots were captured; normal and enlarged EN/AR frames
  were inspected. This is desktop rendering, not TalkBack/VoiceOver acceptance.

Reproduce from the repository with JDK 21 and Xvfb on Linux:

```sh
PARLOR_UI_SCREENSHOT_DIR="$PWD/build/room-code-evidence" xvfb-run -a ./gradlew \
  :shared:design-system:desktopTest --tests '*RoomCodeLayoutTest*' \
  :shared:design-system:detektMetadataCommonMain \
  :shared:design-system:detektDesktopTest --dependency-verification=strict
```

The lead's local custody retains source receipts, logs, XML and before/after PNGs
under `.git/agent-custody/overhaul-2026-10-26/room-code/`. Aggregate compilation and
all affected application/game tests must qualify the integrated head separately.
Physical host/share/join journeys, assistive-technology reading and more extreme
device font/viewport configurations remain unverified; #259 is not closed here.
