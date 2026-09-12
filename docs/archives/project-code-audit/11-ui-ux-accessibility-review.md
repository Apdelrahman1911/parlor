# UI / UX / accessibility / localization

## Journeys

Home → play-mode → (local setup | host/join permission+name+code) → game
phases. Bindings own sub-navigation. In-game actions hit `SessionController`.
Lobby/leave/chrome do not (correct).

Loading / empty / error / reconnect overlays exist (`ReconnectingOverlay`,
`HostDisconnectedOverlay`, `ContinueWithoutDialog`, toast host, local resume
failure). TalkBack/VoiceOver **not** executed.

## Confirmed UI issues

| ID | Issue | Sev |
|---|---|---|
| UI-001 / PF-001 | Disabled Solo card always shown | Medium |
| UI-004 | Case picker no language chip/filter (1 EN + 6 AR mixed) | Medium |
| UI-005 | `pressableSurface` Role.Button without name | Medium |
| UI-006 | Full-screen covers unlabeled | Medium |
| UI-007 | Several fullscreen cards not scrollable | Medium |
| UI-008 | iOS/Desktop platform Back is no-op | Medium |
| UI-002 | Android `app_name` not Arabic | Low |
| UI-003 | `uppercase()` default locale | Low |
| UI-009 | Unused tab bar / scrim / divider | Low |

## Localization

- Compose EN/AR key sets equal (693/693) per `LocalizationResourceContractTest`.
- iOS InfoPlist localized (Parlor / بارلور). Android launcher Latin-only.
- Case prose is **content language**, not chrome translation — picker does
  not say so.
- `supportsRtl=true` on Android. `ProvideAppLanguage` mutates process locale.

## A11y contract tests

`ProductionUiAccessibilityContractTest` is a **source grep** (Role.Button
within N characters; incomplete scroll allowlist). It cannot prove TalkBack.
Do not treat it as an accessibility certification.

## Privacy ceremony

Pass-and-play handoff/covers exist. Role text is ordinary `Text` after the
player confirms they are alone. Overlay semantics generally clear host-lost
chrome. Residual: phone handed over mid-reveal (SP-009).
