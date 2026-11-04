# iOS 26.2 keyboard geometry correction

## Confirmed failure boundary

Current application run [41791343346](https://github.com/Apdelrahman1911/parlor/actions/runs/41791343346)
executes all 29 Swift methods: 26 pass, three fail, zero skips. Failures occur
**after exact input checks**, inside the keyboard-layout predicate: two measured
33pt gaps and an invalid/infinite TextView hittability query. They are not
failed initial taps or `typeText` calls.

Narrow native [41800558221](https://github.com/Apdelrahman1911/parlor/actions/runs/41800558221)
reproduces both boundaries in the unchanged EN/AR inset methods. Source
`545b43f0f47ab57accc9b941a73763c96c7f4f53`, standard free macOS-15,
Xcode26.3/17C529, fresh owned iOS26.2/iPhone17Pro. Arabic times out; English
raises the invalid hit-point error. One failure-only native snapshot contains
97 frame/type/path rows; no labels, values, prediction text or identifiers.

Actual same-generation geometry:

| Native element | Frame (points) |
| --- | --- |
| Public input | (24,245.6667,354,69.0000) |
| Public action | (24,498,354,52) |
| AX key plane | (0,583,402,233) |
| Full-width keyboard background siblings | (0,566,402,308) |

The old helper admits only short prediction rows touching the key plane. It
rejects these containing background siblings, returning583 instead of566.
The apparent gap is33; the actual surface gap is**16**. Original screenshots
independently agree. This establishes a test geometry defect, not an application
inset defect. A hit-point query also preceded all field-frame validity checks;
separate properties repeatedly resolved different snapshots.

Evidence ZIP13309516546 SHA256
`90845435b42525a90ce81d2d2eac5c8821895b2a950b0d670a494118764478ea`;
cleanup13309352154 SHA256
`533d2c80e0ddc5a9722b050b6fef555f42285d305c63fd580c1e9515948d06c0`.
Both actual XCTest failures and original bundles remain retained.

## Correction and regression contract

Resolve field/action/key-plane/background geometry from one bounded native
snapshot per observation. Require unique public controls, finite positive
frames and two exact adjacent valid samples **before** native hittability.
Missing, invalid, moving or nonhittable samples break adjacency. No typed values
or snapshot objects enter retained geometry state or logs.

Include measured full-width keyboard-sized containing surfaces as well as
adjacent prediction rows. Exclude the whole application/window and unrelated
rows. No device-specific17pt subtraction, guessed native offset or app-padding
change. Last Light uses the same surface calculation.

Eleven native regressions cover the observed background, prediction rows,
invalid frames, hit-point gating, interruptions, subpoint motion and unchanged
33pt/keyboard-sized-gap rejection. The executable workflow contract guards
single-snapshot resolution, bounds, original10s/32pt assertions and absence of
input replay. Original taps, typed strings, equality checks, Done/reopen cycles,
privacy and application implementation are unchanged.

## Verification and remaining boundaries

Fresh corrected-source [41805623717](https://github.com/Apdelrahman1911/parlor/actions/runs/41805623717)
is terminal **SUCCESS** at frozen `a5f0f0278f2be6381bd74f7304d1fd9dda0b3e0f`,
tree `53dda15f7f6c8b613ad5df56c6914aba72ebbc8a`. All **15 actual native
methods pass**, zero failures/skips: original EN/AR Name/code inset journeys,
actual Arabic display-name/ASCII-code input, Last Light's six fields, and all
eleven geometry/surface regressions. Every measured Name/code cycle reports
**16pt**, with the original input/equality/Done/reopen assertions unchanged.

Independent admission binds source/tree, exact method identities and positive
durations, Xcode26.3/17C529, fresh owned iOS26.2/iPhone17Pro, actual Debug app
Mach-O/identity/checksums, original xcresult and both owned native cleanup
cycles. Receipt SHA256
`df8b1e6be727512804e7c9a33e7e286cd7f389fd3c829ad5ce6959f32c707061`.
Evidence13311471229 SHA256
`cd0223cbc2f474f4086b9c3acbcd2362ac9db8ecd0a072517854c3fc04987e03`;
cleanup13310992362 SHA256
`16c99ea6507a5040cf7a8868f38d1a856ca47fcf56aad27e9111aef4823cda7b`.

The local strict transport suite passes388 methods (three established LAN
skips); plain and explicit Desktop type-aware Detekt pass. Documentation-only
updates retain the tested Swift blob, but invalidate declared document contracts
and require final-source affected checks. Complete protected PR checks and
distinct resulting-main qualification remain **pending**; scoped native success
is not substituted for those gates or whole Phase1 acceptance.

The first narrow run41798329166 fails earlier at a90s `simctl list devices`
timeout: zero native methods, no create intent. Its correction moves the same
owned Simulator creation before compilation, without additional queries,
retries or longer deadlines. The recovered run creates and cleans its owned
Simulator successfully; the deeper native-service timeout cause is unproven.

Original #267 HID cancellation and #276 delayed keyboard-service startup remain
separate unresolved boundaries. No larger text deadline, repaired input,
cancelled-gesture activation, speculative Compose patch, strict Complete
Protection waiver, physical-device or Store acceptance follows from this fix.
