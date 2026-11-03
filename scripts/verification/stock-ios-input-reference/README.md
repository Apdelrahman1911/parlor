# Stock UIKit input reference
This independent baseline is not Parlor application qualification or a shipping correction.
Execution status and evidence belong to its source-specific workflow; preparation
alone does not establish an Apple compilation or test result.
Lead decides whether this is still needed after run41557761766 terminal capture.

Four serial UITests: first AR, first EN, blank Done/reopen AR, blank Done/reopen EN.
Each launches a new app; each injects exactly one full `Table Test` string.
Post-Done uses the SAME initially blank view, avoiding direct clearing, deletion,
forced caret placement or ambiguous insertion into an existing draft. Every case
also verifies the full draft survives a later Done/reopen without more full-string input.

Exact settlement, exact-value predicate and tutorial/frame guards were copied from
`iosApp/iosAppUITests/IOSAppLaunchUITests.swift`, SHA256 `e64ab3f11638b13accf8934cc43fa0adb7758ee730308abb29a66bd77ec9deb7`.
Text helper changes only stock assertion wording and the failure-only native read.
All waits remain10s. No input replay, extra successful-path native read, timer,
hook, background thread, transport, KMP, SwiftUI, dependency or keyboard forcing.
Keyboard screenshots are retained AFTER the exact input predicate, not before synthesis.

Match actual Xcode26.3/17C529 + SDK/runtime26.2 + ARM64 iPhone17Pro and serial
`-parallel-testing-enabled NO -maximum-concurrent-test-simulator-destinations 1`.
The standalone `run.sh` executes only on the dedicated free `macos-15` workflow.
It reads version values from `config/parlor-version.xcconfig`. Retain actual device/keyboard history and actual
method order; do not assume launch language establishes active keyboard mode.
Do not change hardware-keyboard, prediction, autocorrection or keyboard preferences.
The native app declares en/ar localizations without overriding system keyboard mode.
Traits match pinned1.10.3 Name: default keyboard/appearance, sentences, correction
YES, Done, nonsecure, nil content type, return-always-enabled, native input/accessory.
Spellchecking/smart-input/writing-tools remain stock defaults, not forced off.

Only after text waiter failure, one public fixture button reads native `.text`
and publishes bounded categories: value0 empty/1 exact/2 other; focus/frame/editable/
enabled bits. No text/range export, mutation, focus repair, IPC or instrumentation.
Its focus/geometry are POST-FAILURE/POST-TAP, not injection-time evidence. Button
unavailability reports unavailable; no fallback/retry. Failure screenshots follow
that action; native readback is explicitly NOT timing-neutral/original qualification.
Stock success cannot exclude XCTest/custom-Compose-editor behavior; stock failure
with native value0/2 proves loss without Parlor/KMP, not XCTest-versus-UIKit cause.
This project is not a release artifact. Do not infer Parlor qualification from it.
