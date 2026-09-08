# Native15 provenance: independent reconciliation and narrow alternative

Reviewer: `/root/release_fix_review`. Date: 2026-09-08. This is a source/evidence review, not a native execution or approval of a future implementation. Root exclusively owns the build/native lane.

## Native15 remains FAIL

`evidence/ios-readiness-15/receipt.json` SHA-256 `7b4235bc8d869db573ffbdf65a246be7d25ac1ebefb81b7affe01ffd97818c50` reports runtime PASS, provenance FAIL, notices PASS, cleanup PASS. Independently reopened raw XCResult summary and test details contain **one XCTest method repeated eight times**, all passing, zero failures/skips. The retained periodic observation data contains 48 sampled foreground/Home/no-alert observations, eight intervals of at least ten seconds. These are neither continuous liveness nor per-repetition image provenance.

The ninth, separate provenance launch used owned simulator `4BC6C7C8-914E-4BB3-98BB-CF346C5A0DFA`, app PID 38058 and bracketed kernel process identity. `/usr/bin/sample 38058 1 10 -file <owned>` exited 0; `/usr/bin/vmmap -w 38058` exited 255. Reopened runner lines 510–580 (SHA `32a2eac1d972d7345ba89bcbc0af51b48f7ca1ce25a8d6df047ef163194936a3`) establish that the **strict sample parser returned** before the primary vmmap command. However, selected image rows, the post-vmmap lifetime check and final artifact rehash/structured provenance receipt were not completed. Raw sample/maps were removed. Do not reconstruct missing selected paths/UUIDs/addresses or retrospectively mark this run PASS.

The receipt identifies dirty source at commit `8b9e3b0cdfdab82e7cff3ad8135d0eae9a0b8ca3`, tree `c1e92b5e00ab64a293c4d7f9c9b5e2b85887be88`, branch `fix/local-readiness-2026-09-07`, five explicitly modified tracked normal-control files, diff SHA `8e5e873e93e89aa6e077d78e0263018d567117b3e14e59148a9447062a336b9d`, 800-file manifest SHA `c4d3387a95ff1a4462071147afe92411f5a08ee8b10f78852a59494c8d3e4420`. Before/after identities are equal. Native15's approved and final control hashes are both `5e0cedc613aca90bf3b0baeabc6ec178d9364c3afda6588c4219ba3c3fc46e5c`. This does not identify subsequent root-authored changes.

## What the vmmap diagnostic actually says

Read the installed public binary, without executing it. `/usr/bin/vmmap` SHA `cd3a4ab5bbe594ca4e25110f93338cb6d126f55105cf3d15689e251e6a7fbb97` contains this exact fatal C format string at offsets 74304 and 191785:

> `%s[%d]: [fatal] Failed to get DYLD info for %s with error %s (%d). Assuming it's a minimal corpse which can't be analyzed, which we sometimes see for processes which use a lot of memory.`

Its literal-token hashes match the retained diagnostic. The installed header's `KERN_FAILURE = 5` rendered with the literal format `(5).` matches token 18 exactly; that header describes a **catch-all** failure. Three dynamic prefix/task tokens remain unidentified. The words “Assuming … minimal corpse” are the tool's hypothesis. They do not prove app crash, permission denial, OOM or excessive app memory. The exact token reconciliation is retained separately, without recovering private data or inventing the deleted full line.

The installed vmmap manpage documents `-w` as full mapped-file paths, not a solution to DYLD-info acquisition. Sample's `-fullPaths` concerns debug **source-code** paths, not binary-image redaction. Sample suspends/resumes its target. No blind retry, undocumented entitlement, sudo, changed app launch or guessed tool flag is recommended.

## Public API semantics

Authoritative source: Apple OSS XNU tag `xnu-12377.61.12`, resolved to commit `4d495c6e23c53686cf65f45067f79024cf5dcee8`; fixed-commit URLs, hashes, access times and exact excerpts are in adjacent research JSON. Installed evidence reports macOS 26.2/25C56, Darwin 25.2.0, Xcode 26.5/17F42. Installed MacOSX26.5 SDK headers independently expose the declarations. Available OSS source is not asserted byte-equivalent to the installed kernel/libproc. A failed MasterVersion lookup is recorded as 404, not version proof.

- `libproc.h:96,100`: `proc_pidinfo(...)` and **`proc_regionfilename(...)`**, not `proc_pidregionfilename`.
- `libproc.c:100–110,234–250`: `proc_pidinfo` translates syscall -1 to 0; success is the returned byte count. The path-only wrapper calls **PROC_PIDREGIONPATH**, requires MAXPATHLEN-sized storage, and returns `strlcpy`'s source-string length. It does not expose region bounds.
- `proc_info.c:1230–1267` → `bsd_vm.c:1238–1327`: the path-only flavor calls `task_find_region_details` with options NONE, so it may **skip a hole/non-vnode mapping and return a later file-backed region**. It cannot safely corroborate a sampled address by success alone.
- `sys/proc_info.h:166–191,282–340,735–739`: public **PROC_PIDREGIONPATHINFO = 8** returns `proc_regionwithpathinfo`: region address/size/protection/flags/offset, vnode stat and MAXPATHLEN path.
- `proc_info.c:1155–1188` → `bsd_vm.c:975–1150`: the full flavor also returns the **next region on a hole**. It returns actual region start/size. It may return full size with zero/missing vnode/path/stat; individual vnode/stat/path failure is not reliably reflected in its final byte count. Bounds/path/identity must therefore be validated independently.
- The chain inspects VM map metadata and backing vnode, not DYLD state or app memory contents. It may collect region accounting under map locks; do not call it a passive timing measurement.
- `proc_info.c:2182–2237,3184–3227`: minimum buffer size, live-proc lookup, MAC policy, and same-user/global-process-info privilege checks apply. Public API presence is **not** proof current execution is permitted.

## Recommended minimal explicit observer

Root's proposed optional `--image-observer=libproc`, preserving default vmmap and no automatic fallback, is a suitable bounded design **subject to independent implementation review and actual runtime evidence**:

1. Reuse the existing strict sample parser, finite artifact inventory, exact source/file hashes, own simulator/PID checks and before/after audit-token/start-time/executable brackets. Do not retry rejected evidence.
2. Compile one small task-owned C helper against the installed macOS SDK and libproc. Use real `sizeof`/header layout, zero-initialized storage and exact returned size, not guessed ctypes offsets. Query only the attested PID and selected image's sampled start.
3. Require nonzero nonoverflowing bounds, returned start equal to sampled start, region end within the sample's inclusive range; exact nonempty bounded NUL-terminated canonical expected path; mapped vnode device/inode matching the attested ordinary file. Reject a missing/partial stat/path as failure. Capture errno immediately on API failure.
4. Require a file-backed executable, nonwritable region. Offset-zero is `VME_OFFSET(entry)`, not a universal Mach-O format rule: it is a fail-closed guard for this particular thin ARM64 normal Debug image expectation, not proof all valid fat/header mappings use zero. Unexpected valid layout remains explicitly blocked pending source/artifact review, never silently relaxed.
5. Corroborate **every selected row**, including optional `__preview.dylib`, not only the three required image kinds. Rehash selected artifacts and recheck target identity afterward. Use bounded numeric/boolean failure diagnostics, no raw unbound path or memory output; root's existing ownership finalizer owns helper/build cleanup.
6. Name the evidence precisely: sample-reported image UUID/path plus **kernel executable-region/backing-file corroboration at the sampled starts**. Flavor8 supplies neither Mach-O segment name nor in-memory UUID/page hash. Per-address queries do not enumerate all mappings or replace vmmap's whole-output extra-app-mapping check. Keep sample unbound-image rejection, disclose this scope difference, and do not reuse vmmap's method label.

Negative controls should cover hole/next-region, end/overflow, truncated or empty path, partial/zero return, wrong inode/device/path, unexpected protection/offset, target-generation change, artifact replacement, missing optional selected row, malformed helper JSON and exact observer selection. Fixtures do not prove host API availability. One newly authorized native execution must establish that separately; failure remains failure without escalation/retry.

## Cleanup and remaining uncertainty

Native15 records immediate/final Gradle stop exit 0, no remaining owned workers/unknown holders, no cleanup errors and all raw stack/map files removed. Fresh read-only `lstat` checks in this review confirm absence of its exact temp root, owned simulator directory and four attested secondary FIFO paths. No broader process probe or cleanup was performed; root may have unrelated current-lane work.

This review created only compact additive evidence under this directory, using short-lived stdlib reads/analysis and bounded public-source GETs. No builds/tests, native probes, repository imports, Git/CI/Store operations, app changes or long-running workers were started. No global caches or pre-existing evidence were changed. Actual libproc availability, C-helper ABI execution, future observer controls and future native provenance remain **unexecuted**, not PASS. Physical-device/Store readiness and historical crash causes are outside this evidence.
