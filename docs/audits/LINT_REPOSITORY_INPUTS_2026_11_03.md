# Release lint repository-input correction

## Confirmed failure

- Issue: [#310](https://github.com/Apdelrahman1911/parlor/issues/310).
- Actual main: `d821bf8c68223c1b42d455a93ec286f3dbb35c11`.
- Failed Common producer: [41706055331 / 126161548129](https://github.com/Apdelrahman1911/parlor/actions/runs/41706055331/job/126161548129).
- Missing row: `DependencyUpdate|gradle/libs.versions.toml|A newer version of androidx.navigation3:navigation3-runtime than 1.0.0`.
- Actual XML: 36 warnings, SHA256 `b0921372f6dc17c97b4312d4d92e4141905b9ec2a3e1eefa601d9366004e2f2f`.
- Same-tree PR XML: 37 warnings, SHA256 `30243fa83ce7c8972266a628a0ab84ab41ecf3a159d9182c2dcc32602183ee3a`.

## Root boundary and correction

The pinned detector reads external Google Maven indexes that were not Gradle
analysis inputs. Published AGP/lint source confirms cache fallback and short
network queries; the specific failed producer's network exception is unknown.
Preparation now fetches bounded **live official** indexes, validates them,
registers their bytes as task inputs, and seeds the real task-owned lint cache
using AGP's version-aware initialization. Reports are never rewritten. The
exact warning inventory, dependency pins and all required checks are unchanged.

## Evidence and qualification limits

Before additional path/encoding hardening, the actual corrected local lint
analysis and exact 37-row verifier passed with JDK 21, the Gradle wrapper,
strict dependency verification and configuration-cache storage. Independent
readback established that both staged official indexes exactly matched the
lint cache and receipt hashes. XML SHA256:
`96d628ceb4f173259a8ceef4917a3caa81cfc4d17823d0bda880811535732e93`.

Regression controls cover failed downloads, bounds, redirects, unsafe XML,
missing/duplicate versions, unsupported pins and symlinked outputs. Final
source-specific checks and full hosted PR qualification are required before
merge, followed by distinct actual-main verification. This correction does
not resolve the still-open native typing/Host causality in #267/#276, or the
physical-device acceptance reserved for the owner.

Detailed immutable local logs, XML, indexes and hash receipts remain in
`.git/agent-custody/overhaul-2026-11-03/`. CI retains new input receipts beside
the original lint reports under the ordinary verification artifact.
