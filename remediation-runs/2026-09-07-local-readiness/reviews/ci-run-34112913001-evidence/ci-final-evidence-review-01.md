# Completed CI evidence — run 34112913001

Source `f5ea8045bd903c255041b198f54aca67e893612f`; tree `b109c8ec6ec579b9197f449b36bda2c39105a652`. **Overall CI: FAIL** (Windows CRLF-sensitive contract; Linux exact lint inventory). No successful gate is promoted to proof of the subsequently changed source.

| Gate | Actual evidence | Result |
|---|---|---|
| Qualified Apple executor | Xcode26.3 /17C529, iPhoneOS+sim SDK26.2, exact source output | PASS |
| Apple Kotlin/native runtime + Apple static/linkage aggregate | 252executed tasks; archived class/index reports reconcile458tests,0failures,0ignored across13module tasks | PASS, simulator/shared-test scope |
| Swift simulator tests | 4ComposeContainer tests +1cold-launch; actual XCTest5/0fail/0skip | PASS |
| Unsigned Swift Release simulator wrapper | arm64 Mach-O; metadata guards pass; logged binary SHA1dd0790d… | PASS, not Store signing |
| Linux arm64 Desktop | 13desktopTest tasks;112actionable/112executed | PASS; individual report counts unavailable |
| macOS x64 Desktop+Native distribution | 13desktopTest tasks;113actionable/113executed | PASS; individual report counts unavailable |
| Windows x64 | composeApp95tests/1failure, exact source assertion221 | FAIL |
| Linux common/Android | exact lint inventory32→35; composeAppReleaseUnitTest report76/0fail/0ignored, Pythonrelease174/OK | OverallFAIL; downstream GMD/artifact inspectionSKIPPED |

The qualified Xcode execution gap is now closed **for this exact simulator/static/linkage/unsigned-wrapper run**, despite local Xcode26.5. Physical LAN, signed Store candidates, external identity/legal/owner gates, and unavailable manual UI scenarios remain separate.

`artifact-descriptors-and-cleanup-02.json` preserves every available actual HTML test descriptor and reconciled count. The original workflow omitted JUnitXML; this is not mislabeled as XML verification. Artifact SHA matches GitHub metadata: iOS`b3b465e656c3b85d85746fc4da9b9b1ae8345d8f55e123cbb7a37f7fc364430b`, Android`fe6bd9debf1a8466bf7425469ba519ebb342e42a46dff429f8b51a03d3d0f76d`.

Exact owned ZIPs/directory were deleted after selected sanitized-text extraction, including a caught parser-failure path. Original failed parsing receipt01 remains intact; corrected extraction02 validates XML before neutral redaction and completes the report. No local build, Gradle/Xcode worker, Store action, source modification, or unrelated cleanup was performed by this child.
