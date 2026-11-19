# Ghamza: public information and final accusation

## Confirmed rules limitation, not private-slice serialization

At `8e44800b`, supplemental JVM execution of the compiled production reducer,
projection and public codec completes **1,440 final-guess scenarios**: 3–12 seats,
1/2/3 lives, 1/3/5 round settings, 16 seeds and varied elimination order.
Every final Guest identifies the Winker using the public payload alone and wins
by submitting that inferred target. With multiple lives, **960 Social scenarios**
also identify the Winker before any elimination. Host entropy and all private
roles are absent from the inference. This is not a transport authorization bug.

Reproduce by readying all seats and exhausting every Guest's reports except the
eventual final Guest. At `FinalGuess`, exclude `public.finalGuesser` from the
public zero-report seats: the remaining seat is the Winker. With multiple lives,
have all Guests report once; the sole zero-report seat is already identifiable.
The enforced ban on Winker self-reports, public per-seat counts and last-Guest
accusation trigger together make this deduction certain.

Execution log SHA256:
`5f77f8bde5084f1f473c7d6247b4a38bb2d9dce8eb4431cdd3b6dc47311fecfe`.
Reviewed supplemental fixture SHA256:
`3c5f44241dfcc98783e3df56a223c5ae1489a550554a9eeffe12455308b0b250`.
The checksum-reviewed compiler and original production bytecode remain bound to
that experiment; these results are not relabeled as tests of a later checkout.

## Implementation and disposition

`GhamzaPublicInformationTest` adds two common tests covering all 90 final and
60 multiple-life configurations, including real public codec roundtrips. The
tests document the established information model and separately verify absence
of private roles/host history. They do not silently change rules or certify
native gameplay.

Fresh strict JDK21/wrapper8.13 `:game-modes:ghamza:desktopTest` and module `detekt`
execute the entire module under Xvfb: **22 methods in seven classes, zero
failures/errors/skips**, including both new methods and existing gameplay,
recovery and UI tests. Build time3m9s includes local configuration/compilation;
it is not a hosted or mobile benchmark. Log SHA256:
`cf4fd858e3ba344b195b251ffbf602a784523a477b58c3ad365fa7ab4dd844e4`.
Fresh XML and Detekt output were independently read back and retained. An initial
receipt parser guessed the JUnit target suffix in the wrong order and rejected;
the corrected source-derived readback accepts the same original outputs without
rerunning or relabeling a failed build. This is additive local preparation, not
full-source, protected-main or Apple acceptance.

Existing [issue #260](https://github.com/Apdelrahman1911/parlor/issues/260#issuecomment-6522410442)
contains the deduplicated evidence and acceptance criteria. Classification is
**P2 game-balance/UX limitation**. The current requested mechanics explicitly
take precedence in `docs/THREE_GAMES_INTEGRATION.md`; their acknowledgment of
deductions is not a guarantee of competitive uncertainty. Keep them unchanged
unless a separately specified variant is justified.

Hiding the roster alone cannot repair uncertainty: public protocol history and
in-person eliminations still support inference. A future early-accusation/risk
variant or different last-Guest outcome needs an explicit information/win model,
EN/AR instructions, versioned protocol/persistence treatment, host-authoritative
invalid/duplicate-action tests and physical social-play review. Do not enable
Winker self-reports or weaken privacy/authorization to balance outcomes.

## Verification limits

Supplemental bytecode execution is not a new repository test count, physical LAN,
UI/accessibility or mobile energy measurement. Actual casual-group enjoyment is
unmeasured. NG-02's recipient-consistency fix remains valid and separate.
PR294's original iOS Back settlement remains failed; this audit does not qualify
that gate. No production dependency, shipping rule, private projection, release
policy or Store publication changes.
