# Project status — October 31, 2026

## Current independently qualified main

Latest independently accepted application checkpoint is main
`16d4ecabc74d29690edd22ba278d6bb86d8c70dd`, tree
`079b8cd36021df541b340ff405ed204bf2f3f02f`. Normally protected
[PR303](https://github.com/Apdelrahman1911/parlor/pull/303) fixes queued-event
cancellation ordering. Its separate automatic
[actual-main41158996451](https://github.com/Apdelrahman1911/parlor/actions/runs/41158996451)
qualifies all eight producers and the optimized Apple Release join with exact
checkout/tree/artifact/native/cleanup custody, **23 Swift /759 KMP Simulator
/450 macOS readiness methods**, zero native/readiness skips. Qualification SHA256:
`5a21fa0ffc207e84527b6ec5c0fc94864f8eaf83922e1326100e14df47dd6a32`.
Scoped #302 is closed; the wider overhaul is not complete. This receipt does not
certify changes after that checkpoint, physical devices or Store readiness.

The hosted main run completed in **43m43s** on standard free public runners.
That is this run's observed elapsed time, not a matched-source speedup or proof
of the fastest possible configuration. Checks, architectures and Release
optimizations were preserved. Prior accepted main35288/41118248151 and normally
merged PR294/PR298 retain their source-bound evidence; #297 is already closed.
PR256 was already merged. Do not replay completed integrations or substitute a
PR pass for newer main verification.

## Current unmerged work and failed runs

- [#304](https://github.com/Apdelrahman1911/parlor/issues/304): actual current-main
  coordinator regressions reproduce caller-owned snapshot-buffer corruption:
  **four Desktop methods /two assertion failures /two passing size controls**.
  Reusing a private encoding buffer can overwrite a different recipient's queued
  bytes; later producer mutation can change an in-flight revision's body.
  The isolated correction copies validated public/private bytes before asynchronous
  outbox handoff. This is not an observed shipping-device disclosure: current game
  encoders allocate fresh buffers. Corrected affected suites pass **173 Desktop
  /170 Android per Debug/Release**, zero failures/errors/skips, all earlier
  methods plus four common regressions; plain/type-aware Detekt pass. Final-source
  and fresh hosted/native, protected and actual-main qualification remain required.
  [Source-bound audit](audits/2026-10-31-SNAPSHOT-PAYLOAD-OWNERSHIP.md).
- [Draft PR296](https://github.com/Apdelrahman1911/parlor/pull/296) remains failed
  at `40ae511df882cb29070a94ef3c25f95c1b4ba93e`.
  [41135053853](https://github.com/Apdelrahman1911/parlor/actions/runs/41135053853):
  **21 Swift PASS /2 FAIL**,764 KMP Simulator methods passing, zero skips.
  Arabic Ghamza Host's single settled tap does not transition; English Ghamza
  Join permission Continue fails settlement **before any Continue tap**.
  Seven passing producers and the passing optimized join cannot qualify the run.
- [Draft PR300](https://github.com/Apdelrahman1911/parlor/pull/300) stays failed
  at published `22c67a6c`;41126885894 was not retried. The unpublished local
  #299/#301 ownership union `52bc7a82` / tree `f4c22663` passes fresh strict
  full local gates: **1,950 Desktop /733 Android per Debug/Release /120
  GitHub-test /777 release /450 readiness methods**, established local skips
  only. Exact local receipt SHA256:
  `67d7459296f658b6f2eff3f1c8919d3423244029dd1e08c1f3f29012a1161810`.
  This is not fresh hosted/native, a blind retry, publication or merge.
- [Completed frozen PIN06 experiment41145628399](https://github.com/Apdelrahman1911/parlor/actions/runs/41145628399)
  runs unchanged failed application40ae511d on one standard free `macos-15`
  runner: original, Compose copy and native+Compose copy each **2 PASS**.
  No journey failed, so no failed-only capture executed. Independent readback
  matches1,211 original source hashes, all arm manifests/48 exited-reaped
  commands,239 hosted preflight controls and owned cleanup receipts;63
  in-memory actual-artifact mutations reject. Receipt SHA256:
  `d8800fe4326e568e5aafe84f27dbbb2d9a92d3a78168f05ec38b98d462ebf34d`.
  [Published report](https://github.com/Apdelrahman1911/parlor/blob/59e60c323e0bf9a46cdf97ff07a8f80ce5a6200c/docs/audits/2026-10-30-IOS-PUBLIC-SETUP-PAIR-READBACK.md).
  This scoped pass is **not** original causality, full60-journey neutrality,
  live process re-attestation, a mobile/CI benchmark or PR296/300 clearance.
- #267/#276 and #293/#299/#301 remain open. Draft295's Ghamza evidence,
  draft282's input observers and draft291's hybrid benchmark are unqualified;
  no production dependency, verification threshold or Release optimization is
  weakened. No new Store release or real-device qualification is claimed.

## Earlier accepted checkpoints and investigation history

The dated observations below retain their original source/run identities;
they do not certify the current unmerged corrections.

## Prior accepted checkpoint (PR290)

[PR #290](https://github.com/Apdelrahman1911/parlor/pull/290) normally merged as
`26eb15b630d9348fa5964b012c27a703e6c4fe06`, qualified tree
`c936c0157f571e949bb3ac3fce03c0a576b3e59b`.
[PR40972918577](https://github.com/Apdelrahman1911/parlor/actions/runs/40972918577)
independently qualifies all eight producers/the original optimized Apple Release
join, **23 Swift /716 source-derived KMP Simulator /450 ordinary readiness methods**,
zero macOS skips and exact source/artifact/native/output custody. Qualification
SHA256 `36c1b4e70f39119702afd82013968564c47da982a95c293901fd09373c0c8f27`.

The distinct automatic
[actual-main40979550743](https://github.com/Apdelrahman1911/parlor/actions/runs/40979550743)
independently qualifies that exact merge with the same complete counts/custody;
qualification SHA256
`adba10d36861ac678aeb7d722c8455fef6da022c227d21471571731e6e0efe79`.
This is not PR-pass substitution or certification of newer work. PR289's copied
diagnostic integration and scoped #284 closure remain accepted; original input
causality stays separate. Protections remain active.
PR #256 already merged as `81ee9b7b`; do not replay earlier integrations.

## Application and verification repairs

- [Cold-peer recovery](audits/2026-10-29-COLD-RECOVERY-INTEGRATION.md): selected
  Resume/Leave membership, one foreground startup attempt, explicit Retry.
- [Host-opening intent](audits/2026-10-29-HOST-OPENING-INTENT.md): all six games
  prevent implicit failed Host replay after root recreation.
- [Continuous hard-recovery budgets](audits/2026-10-29-RECOVERY-ELAPSED-BUDGET.md):
  wall rollback and delayed timers cannot renew the120-second recovery window.
- [Readiness copied-input boundaries](audits/2026-10-29-READINESS-COPIED-INPUTS.md)
  and [copy-only diagnostic integration](audits/2026-10-29-INPUT-DIAGNOSTIC-INTEGRATION.md):
  hidden/nested inputs, output symlinks and unregistered suffixes fail closed;
  hash-bound original-file software fixtures work in shallow hosted checkouts.

The [short-grace correction](audits/2026-10-29-CONNECTION-GRACE-ELAPSED.md) is
**merged and independently main-qualified through PR290**; follow
[#252](https://github.com/Apdelrahman1911/parlor/issues/252) for physical-device
acceptance still required. Actual host/peer fault tests reproduce remote
command eligibility and retained validation exceeding15 elapsed seconds when
wall clocks/timers are stale. Immutable elapsed stamps fix those paths and carry
spent time into hard recovery. Combined local `f3784375` / tree `1c165500` passes strict
full gates: 1,901 Desktop (three existing LAN skips),689 Android per Debug/Release,
120 GitHub-test,768 release controls (one platform skip),Detekt/lint/R8/AAB/inventory;
all 450 readiness methods (six Apple-only Linux skips). Its 388.2 seconds uses caches,
not a cold/hosted benchmark. A tree-identical normal local merge `102b38ae` aligns
with `c81f75b4`; old receipts remain bound to `f378`, not relabeled as final-source tests.
That local preparation receipt remains historical; final-source checks, protected
PR290 and distinct actual-main verification above supply accepted-source evidence.

Three fresh real pinned-P2pKit Linux host/peer Resume pairs at `f378` pass, six
natural zero exits, selected seat/game/version, credential rotation/revocation,
terminal delivery and cleanup. Receipt SHA256
`13a16a1d25798ee453e3e8735e2207b14ac26a6fc804888522a01260d5965a5f`.
Preparation explicitly lowers private Gradle workers 2 to 1/adds no-parallel; actual
pair/helper/assertion/deadline source stays unchanged. These surviving same-machine
JVMs/in-memory stores are not phones, short-grace faults, OS sleep, process death,
game reducers or mobile energy measurements.

## Native-input investigation

[Draft PR291 ARM baseline40987141694](https://github.com/Apdelrahman1911/parlor/actions/runs/40987141694)
finishes **failed**,22 Swift PASS/1FAIL: Arabic Egyptian Dominoes Join/name after
the single full typing synthesis. Seven other producers/original optimized Apple
join pass, but the whole run cannot qualify a hybrid benchmark. Separate read-only
[decoder40994699843](https://github.com/Apdelrahman1911/parlor/actions/runs/40994699843)
passes; independent readback matches1,257 original objects/all23 identifiers.
Actually viewed original image/movie samples show empty Arabic name and keyboard,
not the earlier visible QuickPath overlay. These are not native causal proof.
The [shared-UI return regression report](audits/2026-10-30-NAME-INPUT-RETURN.md)
records nine local methods/15 added journeys and an executed input-loss negative
control. Tests are locally prepared, not a shipping keyboard fix or merged
qualification. Keep PR291 draft and #276/#267 open; no blind rerun, longer deadline,
replayed input or hybrid dispatch from a failed baseline.

PR294's additional copy-only input experiments and original failures remain
[source-bound in its prior status](https://github.com/Apdelrahman1911/parlor/blob/b758f25d4f83c0c05e99526f4f2239ce0bd60336/docs/PROJECT_STATUS.md#native-input-investigation).
Their source-set/compiler and bounded-build diagnostics do not qualify shipping
input, erase original failures or certify this integration. Existing #267/#276
remain open; no copied diagnostic code is imported into production here.

[Current-harness40953628450](https://github.com/Apdelrahman1911/parlor/actions/runs/40953628450)
independently matches all three frozen original/Compose/native plans and EN/AR
results:2 PASS per arm, zero failures/skips, owned cleanup receipts and exact
archive custody. ZIP SHA256
`f8ac28ea9a96a6e50d9a72b63b8adef0a32067dc1ac614aca6b8d9a969951537`.
Actual runtime is **iOS 18.5 / 22F77 / iPhone 16 Pro**, Xcode 26.3 / 17C529. Consistent producer
cleanup receipts are not independent live process re-attestation; passing scoped
journeys do not prove failed-only capture, full 60-journey neutrality or causality.

The prior [40940007224](https://github.com/Apdelrahman1911/parlor/actions/runs/40940007224)
remains **22 Swift PASS / 1 FAIL**: English Egyptian Dominoes Join/name fails settlement
before field tap, keyboard, typing or networking; identical frame, enabled true
then false. Shipping name field has no dynamic disabled condition. Read-only
[original xcresult decoder40955026246](https://github.com/Apdelrahman1911/parlor/actions/runs/40955026246)
independently matches1,247objects/all23 Swift identifiers and exports one actually
viewed empty Join screenshot: no keyboard/tutorial/alert/overlay. This rules out
only a visible overlay in that frame, **not** stale-lookup or runtime causality.
No deadline increase, replayed gestures or removed settlement checks. Original
failures remain failed; #267/#276 stay open; PR #282 stays draft/unmerged.

## Outstanding acceptance gates

- **Release:** #230 Store identity/ownership; #258 Strict Complete Protection
  **0 PASS / 6 FAIL**, cause unresolved; signing, legal/security and Store approvals.
  Candidate/promotion workflows remain disabled; no Store publication.
- **Physical recovery:** #251/#252/#253 Android/iOS admission, selected-seat
  restoration/deletion, locked storage, calls/lock/Wi-Fi/process death,
  simultaneous recovery and CPU/RAM/battery/thermal. Host death ends the room;
  no host migration or indefinite iOS LAN hosting.
- **Six games/UX:** #259/#260 physical LAN/gameplay/accessibility, hidden-information
  isolation, large text/RTL and full resource matrices. No real-device claim.
- **Native/tooling:** #267/#276 input causality/failed-only pipeline/full 60-journey
  neutrality; #284 copied-input integration is closed after actual-main acceptance, as are
  scoped #283/#285. Closed #6 covers separate-process automation, not physical phones.
- **RPC:** reviewed P2pKit 0.7.0-rc3 Maven Central single-connection messaging remains.
  October30 publication/source readback confirms upstream
  [0.8.0-rc8](https://github.com/p2pKit/P2pKit/tree/6c85fdc2977386e572830c243f9aa8bb17d84ec3)
  includes #524; publication is no longer the reason to defer. No comparative
  mobile benefit or qualified replacement graph is established. A separate
  detailed documentation reassessment is locally prepared; the
  [older decision](audits/RPC_AND_LIFECYCLE_2026-10-25.md) retains dated rc7 evidence.

Only standard free public runners (documented 20 total / five macOS ceilings, no allocation
guarantee), no paid infrastructure/additional agents, weakened verification or
Release deoptimization. Local Linux lacks Xcode and physical mobile devices.
Historical checkpoints remain [preserved](archives/README.md), never certificates
for newer source. Source/tests/build wiring take precedence over this checkpoint.
