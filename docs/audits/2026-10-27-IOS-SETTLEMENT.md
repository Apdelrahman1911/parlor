# Public iOS setup settlement — failure investigation

## Executed failure and scope

[Run40510904388, job122212244236](https://github.com/Apdelrahman1911/parlor/actions/runs/40510904388/job/122212244236)
executes frozen `b9d6f780ad6ac4bbe505aa446245d1ec23d996c0`, tree
`8b4ebfcaa27bacfe6c4b4474333a1baa58a3b6b3`. The Arabic additional-game journey
fails at line276 while settling **Egyptian Dominoes Host, before any Host tap**.
This differs from #267's earlier synthesized Ghamza tap that did not advance.
Both observations remain linked to [#267](https://github.com/Apdelrahman1911/parlor/issues/267);
this does not establish a common application defect.

The catalog tap finishes13:43:39.294 UTC. Host/Join controls then exist.
The waiter begins after13:43:42.255 with the original10-second deadline:

| Native polling sequence | Start–finish UTC | Elapsed |
|---|---|---:|
| First `exists`/hittability/frame sample |13:43:43.435–46.207|2.772s|
| Second sample, including internal lookup retries |13:43:46.393–52.643|6.250s|

The referenced public recording shows a stationary setup screen in this interval.
No Host event, permission screen or overlay is observed. Returned sample frames
were not logged. The helper consumes its deadline with separate native queries;
**why the native queries become expensive/unresolved remains unverified**. Do not
infer motion from the assertion's wording, label this a permissions defect, or
claim an application touch fix.

The actual destination is iPhone16Pro/**iOS18.5**, M1/three cores/7GB RAM;
Xcode26.3(17C529) builds with Simulator SDK26.2. The existing owned-device selector
inherits the first available iPhone template. SDK version is not runtime version.

## Independently retained evidence

- Runtime artifact12739161890 ZIP SHA256:
  `1c8512751840639c867dcc43525b70c37701e8ea88fc9a2e764f42f8d03614fb`.
- Cleanup artifact12739352654 ZIP SHA256:
  `2bf59aa254f938d97492c99c691ec88e35d0b04c4165cf0af88996cd6a97260e`.
- Job log SHA256:
  `36bf70713dcd776dae1fa5499cbb4f461720b9685fffaaccfb40fa3fb17232f5`.
- Bounded read-only decoding follows the failed `ActionTestSummary` attachment
  IDs to its original public recording, synthesized catalog event and native
  lookup descriptions. These are not screenshots from a different test.
- Exact producer/source/tree, uploaded ownership, both native retirement cycles
  and output-cleanup bindings independently match. Cleanup passes while retaining
  **`apple-ui.run_outcome=failure`**, never converting the test/run to PASS.
- Current-module KMP Simulator672 cases pass without skips/errors. Swift/native
  has9passes/1failure: English additional-games, four other application cases
  and four native container cases pass. This is not full CI qualification.

Original ZIPs, bounded decoded activity, referenced attachments and verification
receipt stay under the task's untracked `.git/agent-custody/` directory. Original
failed attempts are not rewritten or deleted.

## Test-only correction and safeguards

`tapSettledControl` now obtains one coherent `XCUIElement.snapshot()` per frame
sample, rather than independently resolving existence, hittability and frame.
A snapshot provides frame/enabled attributes; native hittability is queried only
when two adjacent valid frames match **exactly**. Missing/disabled/invalid or
nonhittable observations reset adjacency. No geometry tolerance is introduced.

The10-second bound and ordinary single `tap()` remain unchanged. Failed settlement
throws before a tap even if failure continuation is later enabled. At most12
geometry/status/timing samples and a public screenshot are attached **only on
failure**. Never retain snapshot dictionaries, labels, typed values, arbitrary
hierarchies or private gameplay. The helper is public-setup-only, not a generic
private-state diagnostic. The immediate Host/Join destination/source-disappearance
observer is unchanged; it still forbids replay or searching another screen.

Six Swift regression cases cover first/adjacent frames, subpixel motion, missing
snapshots, disabled controls, invalid geometry and interrupted hittability. A
Desktop source-contract regression protects the single-snapshot/one-tap/bounded
privacy policy; it is not an execution of Swift or native input.

Official API references: [snapshot providing](https://developer.apple.com/documentation/xcuiautomation/xcuielementsnapshotproviding),
[snapshot](https://developer.apple.com/documentation/xcuiautomation/xcuielementsnapshot),
[frame/enabled attributes](https://developer.apple.com/documentation/xcuiautomation/xcuielementattributes).

## Verification and outstanding work

Local strict `:shared:transport-p2p:desktopTest staticAnalysis` passes in39s:
334 test cases, zero failures/errors, three existing real-LAN skips. These results
precede this report and must be rerun after documentation/integration changes.
No Swift compiler, macOS or physical iOS device is available locally: the six new
Swift cases and complete English/Arabic public journeys **await hosted execution**.
No native speedup, shipping touch fix, closed issue or merged qualification is
claimed. Retain the failed CI sample; compare free runner profiles at the same
new frozen source only after all affected gates pass (#270).
