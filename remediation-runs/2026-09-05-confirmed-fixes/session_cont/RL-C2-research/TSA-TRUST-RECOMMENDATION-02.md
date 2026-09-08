# RL-C2 — timestamp trust separation (design review 02)

Reviewer `/root/session_cont`; 2026-09-05. Source/API research only: **no build,
cryptographic fixture execution, private material inspection, or production edit**.
This supplements DESIGN-RECOMMENDATION-01.md. In particular, that earlier note's
reliance on final strict TSA validation must include the trust isolation below;
a strict pass with the newly trusted upload leaf alone is insufficient.

## Deterministic design hazard

An ephemeral PKCS12 containing the approved upload leaf adds it to **both** code
signing and TSA trust when jarsigner runs. Jarsigner has one trustedCerts set and
one pkixParameters value; Main.java:2288–2324 sends the code-signer path and TSA
path through the same validateCertChain helper, changing only the variant.
Main.java:2410–2439 adds imported certificate entries to that shared trust.

If a timestamp is signed directly by that upload leaf, PKIXValidator.java:235–237
returns it immediately as a one-certificate trusted chain. Validator.java:259–270
**omits EndEntityChecker for a one-certificate trusted chain**. Therefore lack of
CA status or TSA EKU does not save the design: those checks are bypassed for the
newly trusted leaf. Typical upload-certificate digitalSignature capability also
satisfies SignerInfo's PKCS7 signature key-usage checks (SignerInfo.java:414–465).
The timestamp cryptographic binding to the JAR is verified by SignerInfo.java:
628–659,668–683, but that operation is not original-root TSA path authorization.

A CA-capable upload certificate could also issue a different TSA certificate.
PKIXValidator.java:325–354 constrains non-leaf trust anchors, but does not prevent
this second case if the uploaded certificate actually has CA/keyCertSign ability.
Do not constrain upload-key type just to avoid the problem; isolate the trust.

This is a **pre-integration design hazard** in the proposed fix, not a newly
counted application defect or an assertion that a released build contains it.
The source proof has not been supplemented with an executed timestamped-JAR
fixture by this reviewer.

## Smallest structured preservation

For every distinct CodeSigner timestamp, **before using its time to validate the
upload leaf**, validate the timestamp's signer chain against the original JDK
public CA roots only. Do not add the upload certificate, any artifact certificate,
or an arbitrary issuer to this trust set. Keep final strict jarsigner mandatory.

```java
KeyStore publicCacerts = KeyStore.getInstance(
    Path.of(System.getProperty("java.home"), "lib", "security", "cacerts").toFile(),
    (char[]) null);
Validator.getInstance(Validator.TYPE_PKIX, Validator.VAR_TSA_SERVER, publicCacerts)
    .validate(timestamp.getSignerCertPath().getCertificates()
        .toArray(new X509Certificate[0]));
```

This requires one further narrow export:
`--add-exports=java.base/sun.security.validator=ALL-UNNAMED`.
`Validator` is an internal JDK implementation API, with the same pinned-version,
source-mode and fail-closed constraints as AlgorithmChecker in review 01.
Do not swallow validation errors, read `~/.keystore`, accept caller-supplied trust,
or fall back to validating with the upload PKCS12. Missing/corrupt public cacerts
must fail closed for a timestamped input. No public-root read is needed when the
artifact has no timestamp.

### Exact parity basis

- KeyStoreUtil.java:125–137 loads the original public cacerts via
  `KeyStore.getInstance(file, (char[]) null)`; FilePaths.java:33–35 gives exactly
  java.home/lib/security/cacerts. This avoids another internal export for file
  discovery and never opens the user's default private keystore.
- Validator.java:159–174 constructs the same PKIXValidator with the requested
  usage. Its KeyStore overload uses TrustStoreUtil.java:53–77 to select trusted
  certificates. The input here is exclusively JDK-distributed public trust data,
  not an artifact-supplied/secret key store.
- PKIXValidator.java:82–105 constructs PKIXBuilderParameters; 175–181 disables
  revocation for non-TLS variants. That matches original Main.java:2433–2439
  without `-revCheck`. The internal validation path preserves the TSA-specific
  algorithm variant, purpose checks, critical-extension checks and JDK distrust
  policies; a generic public PKIX check plus hand-coded EKU checks is not equal.
- Main.java:2311–2319 checks the TSA's certificate/path at **current time**, with
  null validation parameter, not at the claimed timestamp. Use `.validate(chain)`
  above with no timestamp/date override. After that succeeds, the timestamp may
  be used consistently for the upload leaf's explicit certificate/AlgorithmChecker
  checks; final strict JAR verification still binds all signature digests.
- EndEntityChecker.java:356–381 requires digitalSignature or nonRepudiation when
  KU is present, a present EKU extension, and timeStamping/anyExtendedKeyUsage as
  implemented at 211–217. Do not invent an exclusive/critical-EKU requirement in
  this fix merely because RFC prose is stronger than this exact pinned verifier.
- If a TSA leaf is **already exactly a trusted certificate in JDK cacerts**, the
  one-certificate EE shortcut remains, but that is the original public-root trust
  behavior. The upload-leaf import must not create any new such TSA authority.
  Rejecting an originally trusted root here would be a separate policy change.
- Original no-keystore jarsigner can additionally consult a user's default
  ~/.keystore (Main.java:2344–2349). This design intentionally does not read private
  user material; its explicit release trust scope is the original **JDK public CA
  roots**, not any implicit developer-personal additions. A release depending on
  personal custom TSA trust is not validated by this design and must not be
  described as an exact reproduction of that private configuration.

### Validation granularity

Do not cache authorization only by upload fingerprint: the same leaf can appear
in multiple CodeSigners with different TSA chains/timestamps, potentially on
separate entries. Validate each distinct timestamp/CodeSigner, or cache only the
complete validated timestamp identity. The JAR-derived CodeSigner timestamp has
cryptographic binding checked by JarFile, but still requires the trust check.

## Alternatives considered

1. **Validate upload certificate at current date only:** does not solve added TSA
   authority. It prevents one use of a forged timestamp to extend leaf validity,
   but still changes which timestamps the release validator accepts and discards
   original trusted-timestamp validity semantics. Not a complete fix.
2. **Original strict pre-pass checking severe bit64:** could prevent newly trusted
   TSA paths, but bit4 also includes expired-TSA errors and the interplay would
   need careful second-pass proof. Requiring an original pass plus a trusted pass
   doubles archive verification and introduces numeric-error composition. Prefer
   the structured TSA Validator call, and never broadly exempt exit4.
3. **Public generic PKIX plus manually checked EKU:** omits the code-signing/TSA
   variant, evolving JDK policy and exact EndEntityChecker behavior. More custom
   policy and less exactness than the narrow existing JDK validator.

## Regression tests required

- Synthetic timestamp chain consisting of the upload certificate: fail under
  public roots even if a separate check with the upload trust would succeed.
- Timestamp certificate issued by a synthetic CA-capable upload certificate:
  still fail unless an original public-root path actually exists.
- Unrelated self-signed TSA and wrong EKU under a synthetic test CA fail.
- Positive trusted-TSA fixture, using an explicit synthetic root in isolated test
  construction only, must preserve the timestamped leaf validity path. No test
  may alter real global cacerts or production trust configuration.
- Repeated CodeSigners sharing upload leaf with different timestamps: a bad second
  timestamp cannot inherit the first one's approval.
- Missing public trust data and unavailable internal API fail closed; successful
  un-timestamped validation does not require external TSA or private trust data.
- Root should distinguish direct chain-unit tests from a fully signed JAR/RFC3161
  token reproducer and from real external TSA/Store evidence. All temporary keys,
  certificates, stores and generated classes remain synthetic and task-owned.

## Evidence

`local-source-extraction-03-tsa.json` records installed JDK21.0.11 source hashes.
`online-research-02-tsa.jsonl` records authoritative OpenJDK exact-tag URLs, access
times and byte identity against the installed source. No live TSA, Store service,
real upload certificate, personal keystore, or device was accessed.
