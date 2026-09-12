#!/usr/bin/env python3
"""Render the audit's final index from separately validated dossiers, not from old audits."""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
register = json.loads((HERE / "canonical-register.json").read_text())

# Root consolidation of the independently reopened evidence; not new approvals.
details = {
    "ST-C1": {
        "scope": "iOS local saves retained from the legacy Documents layout; malformed legacy prefix, or damaged protected header plus an old copy. Fresh installs without legacy saves are not the witness.",
        "impact": "Medium: host-private application-plaintext remains eligible for system backup despite the current excluded/encrypted-storage policy. Actual transfer or disclosure is not established.",
        "proof": "Home recovery -> common store list/metadata -> IosSnapshotFileSystem.list/read -> failed migration. Legacy-only framing rejection occurs before encryption/deletion; malformed current magic bypasses the recognized-magic cleanup finally. Only the new Application Support destination is excluded. A corrupt first byte can leave the rest of the private payload recoverable. Expected: safe retained quarantine, not forced deletion of the last copy.",
        "repro": "Actual production Kotlin/Native filesystem on a fresh iOS 26.5 simulator: two witnesses preserve synthetic bytes and read false Foundation backup-exclusion flags; the desired-safety assertion fails. No Keychain access is needed on these rejecting branches. Three tests executed, two witness passes, one expected failure.",
        "counter": "Healthy migration and recognized-magic missing-key cleanup already work. Sandbox/Data Protection are not the backup-exclusion flag. No real backup, private user data, Android leak or cryptographic bypass was tested or inferred.",
        "fix": "Exclude/verify the old directory before migration, or quarantine retained opaque records in protected excluded storage. Preserve Retry/Discard, current-record precedence and corrupt-state rejection. Test both failures, exclusion failure, mixed inventory, oversized/read failures, interrupted replacement/deletion and explicit discard; separately validate real backup behavior.",
    },
    "SN-C1": {
        "scope": "Whodunit and Mafia LAN peers; a valid session-start commit arrives close to the receive deadline and acknowledgement sending suspends beyond the remaining budget. Not pass-and-play.",
        "impact": "Medium: peer shows failed start after the host has irreversibly entered play, forcing recovery rather than installing the committed session.",
        "proof": "Both peer flows call awaitAuthoritativeSessionStart. Its outer timeout contains commit reception AND best-effort ACK sending. The valid commit passes all binding checks, but a deadline during ACK turns the whole operation into Failure before Success is returned. The host has already committed Started; peer duplicate recovery is not installed after this failed return. Expected: an accepted commit is not revoked merely by its best-effort delivery receipt.",
        "repro": "Production handshake with deterministic coroutine transport: commit at 99 ms of a 100-ms wait, ACK takes 10 ms under a separate 20-ms ACK bound. The Success assertion fails. Exact P2pKit rc3 source corroborates genuinely suspending send/mutex semantics; no device latency was measured.",
        "counter": "Immediate failed ACK already succeeds correctly; missing/invalid commits and explicit caller cancellation must remain failures. No peer reducer, secret leak or incidence rate is established.",
        "fix": "Separate accepted-commit authority from bounded best-effort acknowledgement without swallowing caller cancellation. Test near-deadline delayed/failed ACK, invalid/no commit, duplicate commit, cancellation and revision-zero snapshot recovery for both games; keep exact protocol 4.2 validation.",
    },
    "SN-C2": {
        "scope": "Both LAN host-opening flows; cancellation while registering an already-created room with background lifecycle state.",
        "impact": "Medium: room/kit ownership can escape cleanup, leaving an inaccessible collector/native-kit owner after the user cancels opening.",
        "proof": "P2pKitRoomTransport.host marks initialization complete and exits its cleanup finally before suspending registerHost. The opening owner cleans only returned Success objects. Cancellation during registerHost therefore reaches neither owner nor kit teardown. The kit belongs to a process scope, not the cancelled opening coroutine. Background expiry can help, but an intervening foreground may re-advertise and cancel that expiry.",
        "repro": "Production transport with fake kit and current background registration: cancellation leaves stopCalls=0 instead of the required 1. The test preserves the library's noncancellable cleanup behavior and tears its fixtures down. It is not a real native-radio leak observation.",
        "counter": "Earlier initialization failures and explicit leave have cleanup. SDK scope ownership was checked at exact rc3; implicit parent-job cancellation does not rescue this gap. Earlier audit init-script failure is not reproduction evidence.",
        "fix": "Keep creation and lifecycle registration in one ownership-transfer try/finally; close unreturned resources noncancellably and detach registration. Cover every cancellation boundary, foreground-after-cancel, duplicate close and production-owner Leave. Do not change game authority or protocol.",
    },
    "WD-C1": {
        "scope": "Whodunit LAN peer PublicIntro/RulesBriefing, host remains in that phase, and a frame observes command progress. Use six seats with shipped content; four seats require explicitly synthetic Classic content.",
        "impact": "Medium: automatic readiness can repeatedly send fresh commands, generate invalid-action feedback and churn waiting UI without new user input.",
        "proof": "PeerPhaseRouter sends ACK in an entry LaunchedEffect without checking authoritative readiness. Sending sets Awaiting; parent replaces the router with a command overlay. Applied result/snapshot -> Idle remounts the router in the same phase -> another fresh command. Host set-membership guard returns InvalidAction for that duplicate semantic action; outcome acknowledgement returns Idle and repeats. Deduplication correctly distinguishes new command IDs. Expected: one already-recorded readiness action is not automatically reissued on presentation remount.",
        "repro": "Complete reachable source-level schedule, independently checked against the exact Compose effect implementation. Delay result delivery long enough for Awaiting to render, then alternate results/Idle while host does not advance. No runtime/LAN/UI test was executed for this candidate.",
        "counter": "Fast state conflation can avoid the remount; host progression ends the phase. Ordinary recomposition keys alone do not survive removal. Pass-and-play and Mafia do not share this auto-entry peer ACK path. No host-authority or privacy violation is shown.",
        "fix": "Bind one pending/readied action to authoritative phase/seat/generation, and/or preserve effect ownership under noninteractive command chrome. Test delayed transport, pause return, recreation, replay, stale rejection and rejoin; never weaken duplicate/revision validation or blindly retry non-idempotent commands.",
    },
    "IOS-B1": {
        "scope": "Actual Xcode app target's Compile Kotlin Framework phase, incremental stale framework present, Gradle fails but normalizer can succeed. Local native witness used Xcode 26.5, not qualified 26.3.",
        "impact": "Medium: a failed Kotlin build can be masked, allowing Xcode to continue with stale bytes and invalidating source-to-app verification.",
        "proof": "The /bin/sh phase executes Gradle and normalizer as independent commands, with no checked failure propagation. Child normalizer's shell options do not affect its caller; it validates shape rather than source freshness. A regular stale framework lets the final command succeed. Expected: failed embedding aborts the app build.",
        "repro": "Copied unmodified phase and normalizer in an isolated native PBXAggregateTarget; synthetic wrapper exits 42, stale non-executable placeholder satisfies normalizer, actual xcodebuild returns 0. This verifies shell failure masking, not that a stale app was launched or signed.",
        "counter": "Fresh/missing framework outputs normally fail later; fresh candidate directories reduce the prerequisite. Explicit IDE skip is intentional and disabled in the witness. No explanation of historical repeated iPhone crashes follows from this test.",
        "fix": "Abort on failed cd/Gradle and normalize only successful embedding, preserving strict verification and intentional IDE skip. Test failure with stale output, fresh/missing output, normalizer case handling and successful build; rerun on qualified Xcode separately.",
    },
    "ROOT-T3": {
        "scope": "Desktop Jupiter tests in the transport module: eight enabled lifecycle methods and two already-ignored physical-loopback methods. Test registration, not application failure.",
        "impact": "Medium: ordinary green suites silently omit eight intended regression bodies; two physical tests do not even appear as skipped.",
        "proof": "Expression-bodied fun test() = runBlocking { ... } infers AssertK assertion or Throwable return values. Actual bytecode has non-void @Test methods. Resolved Jupiter 5.10.1 filters them before execution. Compiled inspection finds 222 annotations/10 non-void; normal task reports 212 cases, 211 passing and one skipped. Expected: all enabled annotated regression tests are discoverable.",
        "repro": "Bytecode/descriptor reconciliation at the reviewed tree plus isolated Unit wrappers: all eight previously missing enabled bodies execute and pass. Original annotations remain unchanged, so ordinary registration is still defective. The two physical methods were neither enabled nor run.",
        "counter": "Not every runBlocking test is affected; Unit-returning endings are discovered. Returning an exception is not the same as throwing it. Physical ignores are intentional; passing fake-kit wrappers does not prove LAN.",
        "fix": "Make test methods explicitly Unit/block-bodied without removing assertions/teardown/ignores. Add compiled Jupiter signature/discovery checks (not a ban on value-returning TestFactory). Verify ten new descriptors with the two existing physical methods still skipped and eight enabled cases passing.",
    },
    "M-C01": {
        "scope": "Mafia local resume of a malformed but authenticated current snapshot; living Doctor after a resolved night, consecutive protection disabled. No ordinary producer or disk-authentication bypass found.",
        "impact": "Low, defense-in-depth: accepted recovery can permit consecutive protection or wrongly forbid another target, contradicting retained night history.",
        "proof": "Normal ResolveNight writes previousDoctorProtect and nightLog from the same effective target. Recovery validates the private field and log independently but never binds their final values. Change only the Doctor private previous target to null or another legal seat; codec/resume accepts it. UI/reducer then use that private field to decide legality. Expected: current authenticated recovery preserves reducer-reachable history invariants.",
        "repro": "Five-seat deterministic session with Doctor protecting X on night 1 and Mafia/civilians skipping; reach night 2, mutate only the synthetic recovered private history. One witness passes, rejection assertion fails. It does not demonstrate corruption through normal UI actions.",
        "counter": "Normal producer is consistent; terminal cleanup clears private fields intentionally. Peer projections lack host logs by design. Dead/absent Doctor and skipped nights need precise exceptions, not secret transmission.",
        "fix": "Bind last protection to the latest retained resolved-night record for applicable nonterminal canonical snapshots. Test wrong/null value, before-first-night, skips, dead Doctor, bounded history and PostGame. Do not send host history to peers to repair local validation.",
    },
    "WD-C3": {
        "scope": "Whodunit Elimination current authenticated snapshot or invalid host projection with an active final-two state. Not normal reducer output, Classic Vote, Mafia, or a cryptographic bypass.",
        "impact": "Low, defense-in-depth: accepted impossible recovery can reopen a decided game, change the winner and produce a state that can no longer be saved.",
        "proof": "Normal reducer ends at two survivors. Active-phase canonical/peer validators omit that reachability constraint. A canonical-looking Round4 two-survivor ballot is accepted by codec, local load/content checks and own-peer validators. A subsequent ordinary ballot adds a fifth elimination, changes PlayersWin/KillerWins and violates completed-round history at the next encode. Expected: reject impossible current data rather than repair/reopen it.",
        "repro": "Actual six-seat bundled Last Dinner, seed 73, real reducer through four innocent eliminations; valid final-two terminal round-trip passes. Synthetic mutation to active Round4 is accepted; remaining innocent accuses killer and killer abstains, creating an unsaveable fifth elimination. Full witness passes; required-rejection assertion fails.",
        "counter": "An existing defensive reducer test intentionally constructs impossible active data; that does not make it an admissible current save. No normal producer or storage bypass is claimed. Legacy migration is a separate policy.",
        "fix": "Reject active Elimination Round/TiedRevote with two or fewer survivors at canonical and peer validation boundaries. Test actual bundled six-seat codecs/load/content/peer paths, valid terminal state, early end and replay. Keep defensive fallback tests separate from allowed recovery states.",
    },
    "M-C03": {
        "scope": "Mafia final-role screen in local and LAN modes; legal 32-character wide name and compact layout. Confirmed manifestation is final-role width only.",
        "impact": "Low: intentionally public final role can disappear visually, impairing post-game explanation.",
        "proof": "PostGameScreen places two unweighted Text children in a SpaceBetween Row. Row measures the long first child using available width and gives zero remaining width to the role. SpaceBetween does not reserve trailing width. Both routers pass legal names unchanged. Expected: supported names do not erase required result information.",
        "repro": "Unchanged production composable/theme rendered on Desktop at 320x640 dp, density/fontScale 1, five roles, policy-valid 32 W name. Role bounds left=right=256 dp; required nonzero width assertion fails. Prior audit configuration/compile failures are not layout evidence.",
        "counter": "Vertical scrolling does not solve horizontal starvation. Accessibility semantics may retain the text; no screen-reader failure or other proposed setup/tally clipping is confirmed. Mobile geometry remains unexecuted.",
        "fix": "Reserve role space or weight/truncate/reflow the name. Test compact/landscape/large-text/RTL layouts for both modes without reducing allowed names or accessibility scaling. No game-rule or projection change is needed.",
    },
    "DS-C01": {
        "scope": "iOS app language preference after explicit language, process termination without disposal and restart, then Follow System. Both games' chrome; no reducer effect.",
        "impact": "Low: System can retain the previous explicit language and direction instead of following the platform preference.",
        "proof": "Locale effect captures resolved defaults, writes an app-domain override, and later restores the captured value without tracking domain ownership. After restart the capture can already be the old app override; selecting null/System restores that same value and the null effect does not clear it. Expected: selecting System releases only the app-owned override.",
        "repro": "Two separate native Swift/Foundation processes using an isolated UUID defaults suite and synthetic key demonstrate persisted English recaptured/restored over an Arabic fallback. This is equivalent preference-ownership evidence, not actual iOS UI/AppleLanguages/device execution.",
        "counter": "Same-process switching can work when original ownership is correct. Apple's resolved defaults search and official Compose use of AppleLanguages do not make the API itself a defect. External OS per-app overrides must not be indiscriminately deleted.",
        "fix": "Track/reconcile app-owned override separately from platform fallback across restart/System. Test English/Arabic/System, restart, interrupted writes and OS per-app changes; separately verify UIKit/resource direction without recreating active sessions.",
    },
    "DS-C02": {
        "scope": "Shared toast host on Android/iOS and Desktop development; a distinct notification arrives immediately after automatic dismissal before an empty UI frame.",
        "impact": "Low: a queued error/feedback toast can remain invisible and miss automatic expiry.",
        "proof": "IDs derive from the live queue. A expires, host remembers visible=false, queue empties; B receives A's old ID. Conflation can hide the empty frame, preserving the same keyed composition and completed expiry effect. Expected: distinct notification lifetimes receive distinct effect identity.",
        "repro": "Production Desktop state/host/theme; a synthetic producer uses only public show and follows automatic dismissal. Dismissal and queued replacement assertions pass; missing replacement semantics assertion fails. Later expiry assertion is not reached; nonrestart follows from source, not a second runtime assertion.",
        "counter": "Bounded queue, pure CAS and uniqueness within the live queue are correct but insufficient across lifetimes. Coalescing same text is not the fixture, and separated empty frames work. No session/network privacy defect is established.",
        "fix": "Use atomic lifetime-unique identity independent of queue contents, with defined overflow behavior. Test same-frame expiry/replacement, concurrent producers, coalescing, eviction and stale timers while keeping bounds.",
    },
    "DS-C03": {
        "scope": "Light/System-light theme in ReconnectingOverlay and HostDisconnectedOverlay, both games' LAN recovery surfaces.",
        "impact": "Low, legibility/accessibility: active Leave labels have 2.70034:1 contrast, below both normal and large-text minimums.",
        "proof": "Recovery paints opaque black coverScreen; enabled Ghost button explicitly uses Light textSecondary #55524D rather than a cover token. Actual theme/accent scopes preserve this pair. Independent W3C sRGB arithmetic proves the contrast. Expected: active recovery actions meet the project's existing text-contrast contract.",
        "repro": "Deterministic source/color proof. Select Light and reach startup reconnecting or required-seat disconnect; the resolved pair is as above. No actual component screenshot or physical display measurement was executed for this candidate.",
        "counter": "Dark palette contrast is adequate. Other overlay text uses proper tokens; ContinueWithoutDialog has an elevated surface and is not another manifestation. Clickability/semantics remain, so do not claim an invisible or inaccessible-by-screen-reader action.",
        "fix": "Use a cover-aware button style/token without changing ordinary light-surface Ghost colors. Add real foreground/background pairs for both themes/accents and component rendering/semantics tests; retain the privacy cover and Leave callback.",
    },
    "WD-C2": {
        "scope": "Four bundled Whodunit stories at their supported six-seat count, both modes and shipping platforms. Character-specific contradictions require the relevant seeded killer assignment.",
        "impact": "Low, authored-content consistency: players receive incompatible purported factual timelines and final explanations. Not rule nondeterminism or privacy leakage.",
        "proof": "Catalog -> offline payload validator -> seeded assignment -> authorized dossier/objective clues -> final narrative presents unchanged authored facts. Last Dinner: factual cigar at 9:00 vs unlit until/lit at 9:15; Layla Halabi: public outage 9:45 vs universal 9:05/already out at 9:35; Jasmine Ring: cellar entry 10:50/attack 11:10–12 vs full hour hiding; Khan el-Khalili: poison 9:40 vs minute before 10:15 records alibi. Fake alibis are separately labelled and do not explain these contradictions.",
        "repro": "Complete data-to-display source proof; compare the relevant authorized dossier/objective clue and final narrative during a supported game. No seed search or physical playthrough was executed by the validator. Four manifestations are one grouped content issue.",
        "counter": "Corniche/Saidi age-order wording and Zamalek two/three-year embezzlement duration are editorial ambiguities, not approved manifestations. Schema validation proves shape/references, not semantic chronology. Intentional lies must remain intentional.",
        "fix": "Content owner chooses canonical times; align factual method/timeline/clues/reveal without altering intended lies, reducers or sampling. Review content version/digest and saved-session compatibility. Test structural/catalog/clue invariants and targeted editorial facts across killer variants.",
    },
    "RL-C1": {
        "scope": "Latent Android artifact validator on GNU/Linux (declared Ubuntu runner); CLI code exists but candidate workflows and identity are currently blocked.",
        "impact": "Medium release-tooling defect: otherwise in-bound artifacts fail size preflight before substantive validation.",
        "proof": "BSD-first stat -f %z treats %z as a filename under GNU stat. Failure on that operand does not suppress filesystem stdout for the real artifact; numeric fallback appends its output into the same arithmetic operand. Both AAB and dependency-report guards are affected. Expected: a regular bounded file produces one numeric byte count.",
        "repro": "Complete shell plus upstream coreutils source proof for GNU option/error/stdout behavior. No genuine GNU/Linux execution or real AAB validation took place on this Darwin host.",
        "counter": "BSD form works on macOS; stderr redirection does not remove stdout. Disabled workflows/identity guards remain effective. No current enabled upload or Store incident is claimed.",
        "fix": "Choose the platform form explicitly or use required Python stat, preserving regular-file/no-symlink and bounds. Test Linux/macOS preflight with spaces, missing, exact-limit and oversized synthetic files. Fix before RL-C2's later Linux signature step; do not activate publishing.",
    },
    "RL-C2": {
        "scope": "Latent Android signed-artifact validator with a normal registered self-signed upload certificate, after size preflight. Actual owner certificate and credentials remain uninspected.",
        "impact": "Medium release-tooling incompatibility: strict jarsigner can reject an otherwise legitimate registered upload signature before its approved fingerprint is checked.",
        "proof": "Script invokes jarsigner -verify -strict -certs without a pinned public-cert truststore, under set -e. JDK 21 strict self-signed/untrusted-chain result returns bit 4; later fingerprint comparison is unreachable. Android's official upload-key flow legitimately uses a generated self-signed certificate. Expected: verify Android upload integrity and exact approved certificate without treating missing public-CA trust as a universal invalid signature.",
        "repro": "Exact JDK 21.0.11 implementation and official Android/JDK contract source proof. This finding was not validated by generating a key, signing an artifact, inspecting an owner's certificate or performing a Store operation. Disposable installation signing for a separate Android runtime gate is not evidence for this finding.",
        "counter": "A publicly trusted CA chain or explicitly approved truststore differs. Private keystore removal before validation is sound; do not retain secrets to work around this. Current identity/workflow gates block operation; RL-C1 fails earlier on Linux.",
        "fix": "Bind an ephemeral public-certificate-only truststore to the approved fingerprint, or use equivalently rigorous Android-aware verification. Do not blindly ignore exit 4 or remove strict checks. After separate authorization test good/wrong signer, tamper/unsigned entries, validity, algorithms and extra signers; real Store signing remains external.",
    },
    "RL-C3": {
        "scope": "Latent Google Play promotion helper with an operator change between a deleted read edit and later mutation edit. Present CLI identity guard and workflow disablement prevent authorized production execution here.",
        "impact": "Medium release-policy defect: an intervening 5% rollout of the same legitimate candidate can be silently completed, bypassing explicit refusal to replace staged destinations.",
        "proof": "read_inventory creates/reads/deletes edit A. Source/destination/digest policies inspect A. Helper then inserts edit B and PUTs completed without checking B's copied state. Operator stages the same candidate between A deletion and B creation. B sees staged state but no Parlor guard; staged->completed is valid Google behavior. Expected: policy checks and mutation apply to the same edit snapshot.",
        "repro": "Real helper/codec logic with only HTTP request replaced by a bounded edit-snapshot model: between-edits guard assertion fails; change after mutation-edit insertion correctly invalidates and its counter-test passes. No actual Google API request, promotion or Store receipt was obtained.",
        "counter": "Google invalidates existing edits, not future ones. Workflow concurrency serializes these jobs, not Console operators. Immutable digest/candidate checks work and the witness uses the same valid candidate. Sibling upload races are not asserted without proof.",
        "fix": "Insert mutation edit first, read/validate all source/destination/bundle state inside it, then mutate/validate/commit the same edit. Clean rejected/idempotent exits, retain no-blind-retry and digest binding. Test both race intervals, absent source, validation failure and cleanup. Keep workflows disabled pending owner authorization.",
    },
}

def links(refs):
    return ", ".join(f"[{x}]({x})" for x in refs)

def locations(entry):
    out = []
    for loc in entry["primary_source_locations"]:
        ranges = ", ".join(str(a) if a == b else f"{a}–{b}" for a, b in loc["one_based_line_ranges"])
        out += [f"- `{loc['absolute_path']}` — **{ranges}** (one-based).",
                f"  SHA-256: `{loc['sha256']}`."]
    return out

confirmed = [x for x in register["findings"] if x["classification"] == "CONFIRMED DEFECT"]
assert set(details) == {x["id"] for x in confirmed}
out = ["# Independently confirmed findings", "",
       f"**{len(confirmed)} confirmed defects; no fixes implemented.** Audit-only source: `{register['branch']}` at",
       f"`{register['commit']}`, tree `{register['tree']}`.", "",
       "The scope is the frozen checkout plus the fingerprinted baseline inventory, not a commit alone.",
       "Each section below incorporates its linked complete independent validation: callers/callees,",
       "guards, expectation, counter-evidence, exact reproduction qualifications and recommendations.",
       "These are current-source audit findings, not GitHub issue statuses. No introduction/regression",
       "commit or first-ever discovery is asserted unless the underlying dossier specifically establishes it.",
       "Severity concerns the stated prerequisite and impact; absence of High/Critical findings does not imply readiness.", ""]
order = ["ST-C1", "SN-C1", "SN-C2", "WD-C1", "IOS-B1", "ROOT-T3", "M-C01", "WD-C3", "M-C03", "DS-C01", "DS-C02", "DS-C03", "WD-C2", "RL-C1", "RL-C2", "RL-C3"]
byid = {x["id"]: x for x in confirmed}
for id in order:
    e = byid[id]; d = details[id]
    out += [f"## {id} — {e['title']}", "",
            f"**{e['classification']} / {e['severity']}**. Finder `{e['finder']}`; separate validator `{e['independent_validator']}`.", "",
            f"**Scope/prerequisites:** {d['scope']}", "",
            f"**Impact/severity:** {d['impact']}", "", "### Exact locations", ""]
    out += locations(e)
    out += ["", f"**Expected versus actual / reachable root cause:** {d['proof']}", "",
            f"**Reproduction/proof:** {d['repro']}", "",
            f"**Counter-evidence/limits:** {d['counter']}", "",
            f"**Recommended remediation and regression coverage (not implemented):** {d['fix']}", "",
            f"**Independent conclusion and full source trace:** {links(e['validation_refs'])}."]
    for execution in e.get("execution_records", []):
        out += [f"- Execution `{execution['cycle']}`: {execution['qualification']} Evidence: {links(execution['evidence_refs'])}."]
    out += [""]
(HERE / "FINDINGS.md").write_text("\n".join(out) + "\n")

out = ["# Documentation, evidence gaps, rejected and blocked candidates", "",
       f"Source: `{register['commit']}` / tree `{register['tree']}`. No production changes.",
       "Only the confirmed-defect classification is counted in FINDINGS.md. A documentation mismatch",
       "or weak test is not automatically an application defect. Original pending candidate text is",
       "historical; the linked final independent adjudication plus execution qualifications governs.", ""]
for classification, label in [("DOCUMENTATION MISMATCH", "Documentation mismatches"), ("TEST/EVIDENCE GAP", "Test and evidence gaps"), ("FALSE POSITIVE", "Rejected numbered candidates")]:
    entries = [x for x in register["findings"] if x["classification"] == classification]
    out += [f"## {label} ({len(entries)})", ""]
    for e in entries:
        out += [f"### {e['id']} — {e['title']}", "",
                f"**{classification}**" + (f" / {e['severity']}." if e.get("severity") else ".") +
                f" Finder `{e['finder']}`; independent validator `{e['independent_validator']}`.", "",
                e["qualified_status"], ""]
        out += locations(e)
        out += ["", f"Complete reasoning, counter-evidence and recommended action: {links(e['validation_refs'])}.", ""]
out += [f"## Independently reviewed but blocked candidates ({len(register['unconfirmed_candidates'])})", ""]
for e in register["unconfirmed_candidates"]:
    out += [f"### {e['id']} — {e['title']}", "",
            f"**{e['classification']}**, no approved defect severity. Finder `{e['finder']}`; validator `{e['independent_validator']}`.", "",
            e["qualified_status"], ""]
    out += locations(e)
    out += ["", f"Actual path, evidence and exact remaining verification: {links(e['validation_refs'])}.", ""]
out += ["## Other explicitly retained leads — not extra numbered findings", "",
        "- **Ordered GameEvent cancellation:** the generic three-batch bypass is real, but no shipping subscriber or consequent UI/state/protocol failure was established. Original finder `/root/session_cont`, independent rejection `/root`: [root disposition](reviews/root-residual-event-lead.md); [source/library follow-up](reviews/ordered-event-residual-session_cont.md). Reject a current-app defect claim; retain extension-contract uncertainty before any future consumer is added.",
        "- **Android legacy retention:** success-only deletion can retain an old copy, but production manifest plus both backup-rule formats exclude that directory/domain. The iOS backup-leak inference is rejected, not copied to Android. A stricter local purge policy remains a product/security question. [Independent root closeout](validations/storage-residual-root.md).",
        "- **iOS post-open NSFileHandle exception:** legacy throwing APIs warrant investigation, but no deterministic reachable read/close failure was established. The macOS directory opener returning nil rejects only the earlier directory-crash premise; it does not prove all iOS post-open I/O safe. UNCONFIRMED — BLOCKED. [Root closeout](validations/storage-residual-root.md).",
        "- **iOS localized Debug display name:** FALSE POSITIVE for localization defeating an already-configured distinct development label: no such label is configured. The actual isolation contract is the `.debug` bundle identifier; the shared localized brand name is intentional. Finder `/root`, separate validator `/root/session_cont`: [configuration, caller and native-precedence proof](reviews/ios-localized-display-name-session_cont.md). A future request for distinct launcher branding is not a current defect.",
        "- **Whodunit editorial ambiguities:** Corniche/Saidi age wording and Zamalek embezzlement duration remain owner-clarification questions, excluded from WD-C2's four confirmed chronologies. [Independent content verdict](validations/WD-C2-mafia_cont.md).",
        "- **Other layout leads:** setup/tally or timer/loading geometry without production measurements is not a confirmed clipping defect. See [Whodunit follow-up](reviews/whodunit-ui-followup-whodunit_cont.md) and M-C03's narrowed validation. Duplicate-name rejection already exists; case variants are intended. Top-level tab navigation cannot strand an active game because the navigator rejects the switch and hides the bar.",
        "", "No candidate above is marked fixed. None licenses changes to rules, privacy, platform settings or Store workflows."]
(HERE / "OTHER_CANDIDATES.md").write_text("\n".join(out) + "\n")
print(f"Rendered {len(confirmed)} confirmed defects and remaining classifications from canonical-register.json")
