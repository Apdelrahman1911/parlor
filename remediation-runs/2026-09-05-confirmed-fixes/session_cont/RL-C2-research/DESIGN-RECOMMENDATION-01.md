# RL-C2 — independent JDK 21 design verification

Reviewer: `/root/session_cont`. Date: 2026-09-05. Application checkout: main,
`3625d0663ba6eb51338cbd5f9dc45f859ec18846` plus the root's preserved working
changes. This is design research, **not an executed helper test or release
validation**. No production edits, Java/Gradle builds, signing, Store operations,
private keystore reads, or device work were performed by this reviewer.

## Recommendation

Keep the proposed bounded JarFile signer-identity verification, public-only
short-lived trust store containing **only the approved self-signed upload
certificate**, and the original successful `jarsigner -verify -strict -certs`
requirement. Add the JDK's own **code-signing algorithm checker** while explicitly
validating that leaf as a non-anchor CertPath element. Do not waive exit 4, remove
`-strict`, trust an arbitrary issuer, or infer identity from the first display
fingerprint. Non-self-signed CA paths should retain the original strict behavior
without newly trusted certificates.

An ordinary public PKIXParameters validation is **not sufficient** to preserve
code-signing certificate algorithm restrictions. A plain modern-algorithm
allowlist is not an exact substitute for the active JDK policy either.

Suggested narrow internal hook, subject to the root's implementation and tests:

```java
TrustAnchor anchor = new TrustAnchor(
    leaf.getSubjectX500Principal(), leaf.getPublicKey(), null);
PKIXParameters parameters = new PKIXParameters(Set.of(anchor));
parameters.setRevocationEnabled(false); // original jarsigner does not use -revCheck
parameters.addCertPathChecker(new AlgorithmChecker(
    anchor, /* AlgorithmConstraints: JDK certpath default */ null,
    /* validation date/timestamp, see below */ null, "code signing"));
CertPath path = CertificateFactory.getInstance("X.509")
    .generateCertPath(List.of(leaf));
CertPathValidator.getInstance("PKIX").validate(path, parameters);
```

`AlgorithmChecker` above is
`sun.security.provider.certpath.AlgorithmChecker`, **an internal implementation
API**, not a standard supported Java API. The exact constructor exists at
AlgorithmChecker.java:118–129. Its 2-argument `(TrustAnchor, String)` constructor
at 84–86 delegates to the same path. The literal `"code signing"` is the exact
JDK21 `Validator.VAR_CODE_SIGNING` value (Validator.java:108–112). A comment and
regression test should bind that usage; importing the constant instead needs a
second package export. Missing API, unexpected JDK major, compilation, linkage,
or validation errors must fail closed, never fall back to generic PKIX.

Example source-mode launch (not executed here):

```sh
java --add-exports=java.base/sun.security.provider.certpath=ALL-UNNAMED Helper.java ...
```

Keep the internal export confined to this validation-only child process, not
Gradle/the application. Enforce required JDK21 in the helper. **Do not add
`--source 21`**: Oracle21 documents that it implies `--release`, restricting the
available APIs to public APIs. A `.java` source file already selects source mode.
Oracle21 explicitly includes `--add-exports` in options forwarded to the
source-mode compiler, and executes the result in an unnamed module. No new
library/dependency or security-property override is required.

## Why both algorithm-policy domains remain enforced

These policies have different scopes; applying JAR policy indiscriminately to a
certificate's self-signature would be an additional policy, not exact parity.

| Domain | Existing JDK implementation | Proposed preservation |
| --- | --- | --- |
| JAR entry/manifest/PKCS7 digests, block signature algorithm and parameters, signing key | JarFile → JarVerifier → SignatureFileVerifier → SignerInfo.verifyAlgorithms → `jdk.jar.disabledAlgorithms`; jarsigner retains its own JAR diagnostics | Read every applicable entry to EOF with verification enabled; reject missing/unapproved signers; still require strict jarsigner success |
| Leaf certificate signature algorithm and parameters, key algorithms/sizes, `usage SignedJAR` restrictions | Main.checkWeakAlg uses **CERTPATH_DISABLED_CHECK**, and Main validates with **VAR_CODE_SIGNING** | Include the leaf in the explicit CertPath and add AlgorithmChecker with code-signing variant |
| Validity, self-signature, critical extensions, path consistency | Certificate/path checks; trusted-leaf shortcut otherwise skips important checks | Explicit leaf validation; keep original KU/EKU/Netscape strict checks; no newly trusted CA chain |
| TSA certificate/path policy, when timestamped | Original strict jarsigner verification | Do not add TSA trust or bypass its error status |

Important exact proof points:

1. JarFile.java:90–99 and Oracle JarFile API explicitly say verification **does
   not validate the signer certificate**. JarFile.java:849–878 constructs
   VerifierStream. JarVerifier.java:206–249, 264–329, 413–457 trigger verification
   while consuming entries; signature parse/algorithm failures may be treated as
   unsigned. Therefore accepting a non-throwing stream is insufficient: signer
   presence and identity must be inspected after EOF.
2. SignatureFileVerifier.java:294–324 verifies the PKCS7 block, constructs signer
   constraints, and calls SignerInfo.verifyAlgorithms. Its 380–386 path enforces
   JAR manifest digest restrictions. SignerInfo.java:729–747 passes algorithm
   parameters as well as names to JAR_DISABLED_CHECK. The certificate's own
   signature algorithm is **not** inserted into that PKCS7 algorithm collection.
3. Main.java:1680–1720 checks certificate key/signature via
   CERTPATH_DISABLED_CHECK. Main.java:2288–2298 passes VAR_CODE_SIGNING and then
   validates the chain; 2653–2661 uses the JDK internal Validator.
4. Main.java:1771–1782 trusts an imported leaf and skips certificate signature
   algorithm and validity checks that the non-trusted branch performs. Its
   1859–1878 usage check is **outside** that branch, so KU/EKU/Netscape checking
   is retained by strict jarsigner. PKIXValidator.java:235–237 immediately returns
   a leaf found among trusted certificates, explaining why import alone is not
   equivalent to validation.
5. Public PKIXParameters becomes VAR_GENERIC in PKIX.java:92, 105–111. Only the
   internal extended parameters carry a usage variant. DisabledAlgorithmConstraints
   848–865 matches `usage SignedJAR` only for VAR_CODE_SIGNING/VAR_TSA_SERVER,
   **not generic**. Thus name/key TrustAnchor avoids trusted-leaf shortcut but
   generic PKIX by itself still misses the usage-specific algorithm restriction.
6. AlgorithmChecker.java:125–128 chooses active certpath constraints when null;
   198–215 checks signature parameters as well as leaf/trusted key and variant.
   PKIXCertPathValidator.java:175–226 appends user checkers after built-in checks.
   Using the checker is smaller than maintaining a new algorithm-policy parser.
7. Installed JDK security configuration has
   `SHA1 usage SignedJAR & denyAfter 2019-01-01` in the certpath property, and
   `SHA1 denyAfter 2019-01-01` in the JAR property. These are independently scoped.
   No configuration was changed.
8. Main.java:333–349 reuses severe bit 4 for disabled algorithms, invalid paths,
   expired/not-yet-valid certificates, expired TSAs and self-signed certificates.
   Exempting code 4 generally cannot preserve these checks. Parsing localizable
   diagnostic prose instead would be more fragile than the narrow API hook.

## Date/timestamp policy

Without a trusted timestamp, validate the certificate and checker at current
time. If preserving original timestamped-JAR acceptance, use the signer's timestamp
consistently for certificate and algorithm checks **only together with mandatory
successful original strict TSA validation**; timestamp extraction alone is not
trust. Requiring current leaf validity regardless of timestamp is stricter than
original timestamp semantics and must be documented as such, not called exact
behavioral equivalence. The root proposed explicit current checkValidity; fresh
Android candidate keys can satisfy it, but that narrower choice needs clarity.
No timestamp signing service or real certificate has been inspected here.

## Required tests before approving implementation

Run through root's single build/validation lane, with synthetic certificates only:

- Modern self-signed Android-style RSA upload certificate: exact expected
  fingerprint succeeds; wrong fingerprint fails; no private signing keystore is
  available to the validator.
- Every payload entry signed; unsigned entry, tampered payload, corrupt signature,
  mixed additional valid signer and mismatched-entry signer fail. Include ordinary
  signed META-INF payload; do not blanket-exempt all META-INF contents.
- Expired/not-yet-valid leaf, unsupported critical extension, wrong KU/EKU,
  invalid self-signature, and undersized/disabled key fail.
- SHA1 self-certificate + modern JAR block signature must fail in certificate
  validation, independent of JAR digest tests.
- Modern certificate + JAR SHA1/disabled signature must fail even though the
  certificate path is acceptable.
- A temporary test-only **stricter** certpath property such as
  `SHA256 usage SignedJAR` must reject a SHA256-signed certificate while the JAR
  uses an otherwise allowed signature/digest. This distinguishes the proposed
  checker from generic PKIX/allowlist false-green. Apply test-only properties
  before JDK constraint initialization in a fresh child; never weaken production.
- PSS parameter variants (SHA1 digest or MGF1 digest) must not be accepted merely
  because the outer name is RSASSA-PSS, if PSS is in test scope.
- Original strict behavior for non-self-signed/untrusted CA chains; no accidental
  pin-as-CA import. Timestamp handling and unknown-runtime failures explicit.
- Limits before expensive ZIP/JAR processing, empty archives, duplicate paths,
  extra signature entries, failed tool subprocesses and cleanup paths.
- Actual JDK21 source-mode export invocation; no `--source`/`--release` and no
  competing build process. Preserve minimal red/green receipts, remove synthetic
  keys/stores/artifacts afterward, and record cleanup.

## Research evidence and limitations

`online-research-01.jsonl` records URLs, actual access times, HTTP status, response
hashes and comparison to installed JDK21.0.11 public src.zip. The applicable
OpenJDK tag is `jdk-21.0.11-ga`; online AlgorithmChecker, PKIX,
DisabledAlgorithmConstraints, jarsigner Main, and SignerInfo bytes **match** local
extractions. Extended parameters match in `local-source-extraction-02.json`.
`local-source-extraction.json` / `-02.json` identify preserved source excerpts.
The API evidence is Oracle Java21's JarFile API and java/jarsigner man pages.
The Android official command-line guide prescribes keytool-generated keys and
jarsigner for AAB signing; self-signed Android upload keys are not a substitute
for unrelated platforms' public signing requirements.

Not every JDK dependency file/line was read; this is a bounded API-policy review,
not an OpenJDK security audit. The application script was reread in full (1–282),
plus relevant implementation ranges noted above. Runtime behavior of the new
helper remains **NOT RUN by this reviewer**. Root must independently review the
implementation and execute all applicable tests before calling RL-C2 fixed.
