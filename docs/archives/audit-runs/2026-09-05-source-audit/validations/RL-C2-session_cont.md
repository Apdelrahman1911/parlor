# RL-C2 — Independent validation: ordinary Android upload certificate fails untrusted jarsigner strict check

- **Classification: CONFIRMED DEFECT (latent release-validator code), Medium.** A normal Android upload-key certificate rejects an otherwise correctly signed candidate before the configured fingerprint comparison. This is a code-level release gate incompatibility, not proof about any owner's current private certificate or an actual Store submission.
- Finder: `/root/whodunit_cont`. Independent validator: `/root/session_cont` (2026-09-05).
- Baseline: `main`, HEAD `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`.
- Exact affected source: `/Users/abdelrahman/Projects/parlor/scripts/release/validate_android_artifact.sh:2,53–57`. Caller: `/Users/abdelrahman/Projects/parlor/.github/workflows/testing-candidate.yml:177–185,315–321,351–387,412–432`.

## Expected and actual; complete source proof

Android's official command-line signing guide explicitly supports an AAB signed by `jarsigner` using a locally generated RSA upload keystore (`keytool -genkey ...`, without a CA step). The Play App Signing guide requires registering that upload certificate, not obtaining a public CA code-signing chain. JDK21 keytool documentation specifies a generated signing key without `-signer` is wrapped in one self-signed X.509 certificate.

1. Given a valid, non-expired, algorithm-compliant AAB signed with this registered self-signed upload certificate, the validator reaches54 (on BSD/macOS, or after separately correcting RL-C1 on GNU/Linux).
2. It invokes `jarsigner -verify -strict -certs "$aab"` without an explicit keystore/truststore. `set -e` is active. The expected upload certificate SHA256 argument is checked only **after** this command,56–57.
3. Exact JDK21.0.11 source (`Main.java:875–885`) calls `signerInfo` for signed entries even without verbose output; `2241–2256,2288–2303` validates the signer chain and records untrusted-chain failure. `2327–2329` additionally sets `signerSelfSigned` for a one-element self-signed chain.
4. The explicit trusted-keystore exception is `992–997`: it clears `signerSelfSigned` only if the signer is in the supplied keystore and `keystore != null`. The script supplies none. Default cacerts cannot be assumed to trust an owner's newly generated upload certificate; no such private/default store was inspected.
5. `333–352` returns strict severe-warning bit4 for `signerSelfSigned` or `chainNotValidated`. The JDK21 manual independently documents both errors as code4. Thus the shell terminates before reaching the configured fingerprint equality, even though the certificate is a supported Android upload certificate and the jar signature is intact.

The candidate caller deletes the private upload keystore before validation384–387 and correctly does not pass signing secrets to that step. It never creates/passes a public-cert-only truststore. The remediation must preserve that secret separation.

## Counter-evidence and limitations

- Self-signed **debug** certificates are not an acceptable release substitute; this finding concerns a dedicated registered upload key, not the insecure Android debug key. Android-specific official docs, rather than a generic public code-signing assumption, establish legitimacy.
- An artifact signed with a trusted public CA chain, or an explicitly supplied approved-cert truststore, can behave differently. The script API neither requires the former nor supplies the latter. The existence/shape of real owner's certificates remains uninspected.
- Current Android workflow is explicitly disabled at178; Store identity ownership is blocked. This is **latent validator behavior** under the documented upload-key setup, not current app failure or authorization to enable publishing.
- On the declared Ubuntu runner RL-C1 currently fails earlier. That is a prerequisite/ordering relationship, not a reason the strict-trust defect is harmless on macOS or after fixing byte counting.
- No private key was created/read; no JAR/AAB was signed and no real signature or Store validation was run. Confirmation is deterministic exact-version source + authoritative supported-input proof. Workflow pins Java21/Temurin, not a patch; local available JDK is21.0.11, whose inspected behavior agrees with the Java21 public command contract.

## Recommendation and regression tests

Retain strong signature verification, unsigned-entry/tamper/algorithm/validity checks and exact registered fingerprint binding. Supply an explicit ephemeral **public-certificate-only** truststore whose certificate is first verified against the approved fingerprint (or an equivalent rigorously tested Android-aware verification approach). Do not blindly accept exit4, remove `-strict`, import arbitrary unverified certificate material as trust, or retain the private signing keystore merely to bypass this check.

Later authorized isolated tests should cover valid registered self-signed upload cert, wrong fingerprint, tampered/unsigned entries, expired/not-yet-valid/disabled algorithms and unexpected additional signers. Synthetic signing tests are local-verifier evidence only, never real Store evidence. No remediation is implemented here.

## Research, evidence, hygiene

Authoritative URLs, accessed2026-09-05:
- <https://developer.android.com/build/building-cmdline#sign_manually> (retained text733–750).
- <https://developer.android.com/studio/publish/app-signing> (540–569,602–615,645–685,780–840,1031–1071).
- <https://support.google.com/googleplay/android-developer/answer/9842756?hl=en> (53–67,94–100).
- <https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html> (retained text335–344).
- <https://docs.oracle.com/en/java/javase/21/docs/specs/man/jarsigner.html> (strict exit/warning contract).
- <https://raw.githubusercontent.com/openjdk/jdk21u/jdk-21.0.11-ga/src/jdk.jartool/share/classes/sun/security/tools/jarsigner/Main.java> SHA256 `179db252f16ca8dfc7ec2cf843afec458654c99a56318c3b5c9b86d74738e61e`.

Local evidence: `evidence/release-independent-session_cont/` with URL/access/hash receipts in `research.jsonl`; source hashes `validations/RL-source-hashes-session_cont.json`; coverage `coverage/reviews-session_cont.jsonl`.

Only audit text/research was created. No Gradle/Xcode/app/server/signing/Store process or artifact was generated; no unrelated processes or files were touched. Root retains exclusive build/cleanup lane ownership.
