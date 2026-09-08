# HTTPS RFC3161 synthetic witness

Campaign-only control; no application, release-policy, or JDK trust-store edits.
Root owns execution after independent review and the native owned-child probe.
Do not use any existing archive, keystore, Store credential, or real player data.

```sh
python3 run_timestamp_fixture.py --evidence-dir ../tsa-positive-run-01
```

The output directory must be new and its parent must already exist. The helper
creates one fresh disposable RSA key and a two-entry synthetic JAR, calls the
pinned Homebrew JDK21 jarsigner **once**, then deletes the private key before
verification. Only a fresh synthetic digest/nonce and normal RFC3161 fields go
to `https://timestamp.sectigo.com`; no archive or source bytes are uploaded.

## Network limits and uncertainty

Sectigo's authoritative documentation names this hostname with **HTTP**, not an
HTTPS support guarantee. A prior HTTPS GET/302 is not evidence of RFC3161 POST
support. The authorized probe has no outer retry and no HTTP fallback.

`OneTimestampInvocation.java` uses JVM-local
`HttpURLConnection.setFollowRedirects(false)` before calling the actual JDK
jarsigner entry point. It requires `sun.net.http.retryPost=false`. The separate
`http.maxRedirects=0` property is defense in depth, **not** sufficient alone.
Exact JDK21 source shows a remaining internal failed-write retry independent of
`retryPost`; therefore this is **one bounded jarsigner invocation, not proof of
exactly one outbound POST**. Any internal retry has the same synthetic query.
The root explicitly accepted this bounded limitation. Connect/read timeouts are
15 seconds; the signing command has a 45-second total bound and a 256 MiB heap.

## What a successful result proves

- The real timestamped JAR passes the exact production signature fragment and
  mandatory strict jarsigner verification with the approved disposable signer.
- Both synthetic payload entries carry the same authenticated timestamp signer.
- A wrong approved fingerprint and a tampered signed payload still fail at the
  intended production boundaries.
- Source/control and public JDK security-file hashes remain unchanged.

It does **not** prove AAB packaging, Store acceptance/signing, physical-device
readiness, or that an unreachable/unsupported HTTPS endpoint is an app defect.
No private/public-root overlay is installed. A failed probe is retained honestly;
there is no automatic alternate TSA, local authority, or security downgrade.

## Ownership and cleanup

The helper uses the independently reviewed Darwin audit-token registry, bounded
command/log counts, fresh 0700 scratch, and inode/UID/nonce attestation. Finally,
it stops only its recorded workers and removes only its owned scratch; logs and
the compact receipt remain. A cleanup ownership failure preserves scratch and
records FAIL rather than deleting an uncertain path. No Gradle is invoked and no
other task's workers/caches are stopped. No run has occurred merely because this
draft/README exists; the root execution receipt is required.
