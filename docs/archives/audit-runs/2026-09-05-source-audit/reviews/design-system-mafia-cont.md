# Design-system review

Reviewer `/root/mafia_cont`; main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, no production edits or execution. Task-specific inventory `assignments/design_system_mafia_cont.txt`.

## Tokens, motion, locale and safe-area foundations

Complete source reads now receipted: every token/theme/motion source, locale sources for all platforms, backdrop, safe-area and focus helpers, activity indicator/CandleFlame, locale-selection and Desktop locale tests, module build file. Previously read typography/theme/card and two layout test harnesses have earlier receipts. Cross-boundary PersistentSettingsStore fully read; App45–106 is only a partial entry trace.

Reduced-motion observation scopes are disposed on Android/iOS; Desktop does not detect native reduced-motion but app preference remains applicable to this development-only target. Potential reduced-motion default-size indicator lead rejected as shipping defect: every actual production caller gives explicit size (CandleFlame, OfflineBanner, ReconnectingOverlay, ParlorButton); remaining callers still need complete review. Palette isLight is a classification helper, not WCAG contrast computation. All theme state wraps stable composition; locale boundary does not key/recreate app content.

Native locale loading contracts use string-presence tests rather than runtime verification; Desktop locale test checks retained state/restoration, not iOS UserDefaults behavior. Unconfirmed investigation DS-C01: iOS AppleLanguages effect reads resolved preferences and writes persistent app-domain override, restoring only on effect disposal. Need authoritative domain/process-death semantics and independent review before classification. No real preferences/player data inspected.

No runtime/Gradle operations by this reviewer; root owns one lane and cleanup. Most components, remaining tests, resources/font metadata remain pending.

## All remaining components, tests and resources read

Every remaining component/icons source, all design-system tests, and both complete19-line EN/AR resource XMLs read and receipted. Modules/resources are commonMain production; commonTest/desktopTest are fixtures only. Vector icons have copied Apache header; directional Back/Forward set autoMirror; icon tests assert flags only, not physical RTL rendering. Brand mark is decorative and clears semantics. Headers require localized Back descriptions; normal controls retain minimum heights, fill patterns and explicit roles. ContinueWithoutDialog and SessionExitConfirmation replace sensitive content by caller contract; confirm/cancel labels come from localized resources. Black recovery surfaces expose candidate DS-C03 only for Ghost labels, not filled dialog controls.

Toast updates use pure StateFlow CAS and bounded four-entry queue, but live-queue-derived ID reuse can collide with remembered visibility/completed timer across conflated empty states (DS-C02 prepared). No manual dismiss is assumed: root reproducer schedules public show after actual auto-dismiss. Fonts: both shipped TTFs inspected with file(1)+read-only SFNT metadata parse; name/license/table ranges/embedding flags recorded in evidence/design-font-metadata.json. Inter4.001, JetBrainsMono2.211; both embedding_flags0 and within-file table ranges. This is not glyph/fallback/native typography or provenance authenticity proof. Module-root .DS_Store excluded as non-shipping OS metadata, hash/size only.

Test qualifications: primitive safe-area tests compute static LTR inset sums; they do not render system bars/keyboard or prove every nested-scroll consumption path. Overlay layout tests use320x640dp+2x font; not all landscape/tablet/RTL devices. Reconnecting focus test exercises Desktop Tab only, not VoiceOver/TalkBack/iOS focus restoration. Locale loading tests scan source strings except Desktop composition retention test. Palette tests enumerate standard surfaces/active pairs but omit Ghost-on-cover; registration list is manual, not reflection. Toast tests originally cover only queue bounds and adjacent coalescing. No ignored test markers encountered in this module. No tests executed by this reviewer.

## Candidates and limits

DS-C01 iOS persisted AppleLanguages/Follow System: source/research and safe synthetic suite witness ready, root independent review pending. DS-C02 toast lifecycle identity: source/research + production-host Compose timing witness ready, root independent review/execution pending. DS-C03 light-theme recovery Leave contrast: independently confirmed Low by /root/whodunit_cont; source-level exact color proof and official W3C reference, no runtime rendering claim. All temporary material stays isolated under this audit; no production/Git/release operation.

## DS-C02 independent adjudication update

Root independently confirmed DS-C02 Low; full `validations/DS-C02-root.md` reopened/read. The production-host repro passed automatic-dismissal and replacement-enqueued assertions44–45 and failed missing-replacement semantics assertion47. Subsequent expiry assertion did not run; nonrestart remains source proof. No mobile timing/device proof is claimed. DS-C01 still awaits root native-source adjudication.
