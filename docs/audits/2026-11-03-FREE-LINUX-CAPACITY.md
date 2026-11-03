# Free hosted Linux verification capacity

## Confirmed failure

[Issue #308](https://github.com/Apdelrahman1911/parlor/issues/308) tracks
main `364214d558b256994b8f88b87e354b8579d214aa`,
[run 41580234749](https://github.com/Apdelrahman1911/parlor/actions/runs/41580234749),
Common job `125733099687` on standard `ubuntu-24.04`.
GitHub's check annotation reports `System.IO.IOException: No space left on device`.
The worker died during the full gates, before upload, cleanup and post steps;
the job-log API returns `404 BlobNotFound`. There is no Common artifact to
qualify, no measured peak, and no basis to attribute this to a failing test or
one particular build directory. Independent successful lanes do not qualify
this failed workflow.

## Bounded correction

`scripts/ci/hosted_linux_capacity.py` removes only four unused preinstalled
tool roots: .NET, GHCup, Swift and CodeQL. This Linux Kotlin/Android lane needs
none of them. It validates all roots before deletion, rejects redirection,
foreign ownership and filesystem mounts, uses bounded non-shell commands,
and never retries cleanup automatically.

The step runs only on the standard ephemeral GitHub-hosted Ubuntu24/X64
Common lane, after JDK21 setup and before dependency restoration. It never
deletes Android SDKs, Java, Gradle/Kotlin caches, checkout outputs or evidence.
Other architectures, free macOS/Windows runners, optimizations and every
verification gate remain unchanged. No paid infrastructure is introduced.

The existing upload retains an exclusive private task-bound receipt with
before/after disk metrics. **20GiB is a prospective reserve, not a measured
build peak or performance claim.** Unsafe roots, cleanup errors or insufficient
space fail the step rather than starting an underprovisioned full build.

## Acceptance and limits

Safety and closed-workflow controls use temporary directories/mocks; do not
execute real toolchain deletion locally. Fresh complete PR and independent
merged-main results, including the actual disk receipt, remain required.
Neither this fix nor a successful diagnostic establishes #267/#276 input
causality, Strict Complete Protection acceptance or physical-device readiness.

References: [GitHub-hosted runner availability](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
and [official Ubuntu24 image tools](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md).
