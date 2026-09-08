# MF-C1 LAN test — independent explicit-type follow-up approval

**Reviewer:** `/root/factory_review`; **author:** `/root`. **APPROVED**: the only source changes are explicit types for `LanFixture.bus` (line212) and `doctorPeer` (line240) in `game-modes/mafia/src/desktopTest/kotlin/com/parlor/games/mafia/multidevice/MafiaDoctorLanTest.kt`.

Both helpers must remain `suspend`: `peerCommand` calls the declared suspending controller/bridge APIs, and `reconnectDoctor` calls declared suspending bus methods. Their bodies, assertions, scheduler operations and cleanup remain byte-identical. Reversing only the two annotations reconstructs the full previous file hash `513a87423f1de6834d3b1664d1103f83a36d67f351a888e095b53bf1bedd5be9`; final hash is `570e8f18c12f15c480a48c38e6a09025d5aef2ab1aa38cbac246b2f2f0b5a5a5`.

The official Detekt1.23.7 rule checks resolved `isSuspend` descriptors and does not exempt unresolved calls. Its tagged compiler pin2.0.10 differs from project Kotlin2.4.10. These facts support investigation of inference/type resolution; the exact binding-context/metadata failure was **not** instrumented and must not be asserted as proven. The two-line, behavior-preserving correction demonstrably removes this source-shape-dependent false positive without weakening rules.

Raw `mafia-type-aware-green-01` evidence independently inspected: **two fresh runtime test PASS, zero skips/failures; type-aware `detektDesktopTest` executed with zero findings; command exit0**. The complete source manifest stayed unchanged during execution. `combined-production-02` remains FAIL and is not reclassified; its earlier test XML was cached, not a fresh post-correction run.

No production code, suspend modifier, assertion, scheduler, ignore/suppression, Gradle policy, dependency/version or verification metadata changed. Doctor ON/OFF, rejoin and private-projection test semantics remain intact. This is not a seventeenth application finding. Root must rerun combined gates against the resulting source.

Companion JSON records full commands, source/ranges/hashes, exact diff, raw XML hashes and authoritative research. Root cleanup receipt: Gradle stop0, no remaining owned workers/outputs, no cleanup errors. Reviewer ran no builds/tests/native processes and added only compact owned evidence.
