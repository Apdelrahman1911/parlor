# RL-C2 — Strict JAR verification lacks upload-certificate trust input

Status: **CONFIRMED DEFECT — independent source validation**. Finder: `/root/whodunit_cont`; independent validator: `/root/session_cont`, `validations/RL-C2-session_cont.md`. Severity: Medium, latent release-validator defect, not application runtime. Baseline main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; no tracked modifications.

Independent conclusion: official Android signing guidance supports a self-signed registered upload certificate; exact JDK21.0.11 source returns strict severe-warning bit4 for the self-signed/untrusted signer without explicit expected-cert trust. Fingerprint verification is downstream of that failure. No key was generated/read and no JAR/AAB signed. This is deterministic source-and-supported-input proof, **not genuine signing or Store evidence**. Exact URLs/hashes and counter-evidence are in the validator report and `evidence/release-independent-session_cont/research.jsonl`.

## Source and applicable path

- `/Users/abdelrahman/Projects/parlor/scripts/release/validate_android_artifact.sh:35–57`, SHA-256 `1d0d423a3e149d9db85a17d82bfff9f9998239202d04a741e0779ea8218c7428`.
- `/Users/abdelrahman/Projects/parlor/.github/workflows/testing-candidate.yml:177–186,412–432`: Ubuntu candidate job supplies a pinned upload-certificate fingerprint, but supplies no truststore argument to the validator.

At54 `jarsigner -verify -strict -certs "$aab"` runs under `set -e` with no trusted upload certificate/keystore. Only if it succeeds does55–57 obtain the certificate and compare its SHA-256 with the configured upload pin. Android signing normally permits self-signed upload certificates. The suspicion is that JDK21 `-strict` treats the untrusted/self-signed chain as a severe verification warning/nonzero exit (commonly4), so the expected fingerprint check never runs even for an intact artifact signed by the correct normal upload key.

## Reproducer/research requested, not executed by finder

Consult exact JDK21 jarsigner documentation/source and official Android signing guidance. In an isolated synthetic directory, generate a throwaway self-signed upload-style key, sign a minimal JAR, and run the exact verification command; record exit/output and prove ordinary cryptographic verification/pinned certificate succeeds. Do not use private signing inputs, actual Store keys, Store APIs or release signing. This only verifies JAR/tool behavior, not correctness of a genuine AAB or Store upload.

## Counter-evidence and limits

A CA-trusted signer or prepopulated user truststore may avoid the condition; the ordinary self-signed path is the candidate. Existing configured fingerprint is meaningful authenticity protection but executes after the failing guard. RL-C1 may mask this on GNU hosts; isolate this command separately, not by altering production code. All current candidate jobs and canonical Store identity remain blocked; this is a latent release path, not an observed enabled release failure. No finder-run reproduction or real signing evidence.

## Suggested remediation/coverage

Preserve strict cryptographic and per-entry coverage checks and the explicit expected upload-certificate pin. Supply narrowly scoped trust for the already-pinned expected upload certificate (or otherwise distinguish PKI trust warnings from genuine signature/coverage failures without weakening those failures). Add JDK21 synthetic tests for accepted expected self-signed cert, wrong signer, tampered/unsigned entries, mixed signers and expired/invalid key policy. Never simply remove `-strict` to obtain green.
