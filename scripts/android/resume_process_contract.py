"""Strict public protocol for two actual Android process-acceptance invocations."""
from __future__ import annotations

from dataclasses import dataclass
import re
from uuid import UUID

CLASS = "com.parlor.app.storage.AndroidResumeProcessPhases"
GAMES = ("LastLight", "Mafia", "Whodunit")
LANGUAGES = ("en", "ar")
ENTRY_KINDS = ("Local", "Multiplayer")
MAX_PHASE_OUTPUT = 256 * 1024


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


@dataclass(frozen=True)
class Fixture:
    id: str
    game: str
    language: str
    entry_kind: str = "Local"

    def __post_init__(self) -> None:
        require(str(UUID(self.id)) == self.id, "Noncanonical process-fixture UUID")
        require(self.game in GAMES and self.language in LANGUAGES, "Unsupported process-fixture case")
        require(self.entry_kind in ENTRY_KINDS, "Unsupported saved-entry kind")

    def arguments(self, method: str, component: str) -> list[str]:
        require(method in ("prepare", "verify"), "Unknown process phase")
        require(re.fullmatch(r"[a-z][a-z0-9_.]+\.debug\.test/android\.test\.InstrumentationTestRunner",
                             component) is not None, "Not the dedicated Debug test runner")
        return ["shell", "am", "instrument", "-w", "-r", "-e", "class", f"{CLASS}#{method}",
                "-e", "parlorFixtureId", self.id, "-e", "parlorGame", self.game,
                "-e", "parlorLanguage", self.language, "-e", "parlorEntryKind", self.entry_kind, component]


def phase_result(raw: bytes, fixture: Fixture, method: str) -> dict:
    """Exit0 or a final instrumentation code alone is NOT a JUnit success."""
    require(0 < len(raw) <= MAX_PHASE_OUTPUT, "Phase output exceeded its bound or is absent")
    require(method in ("prepare", "verify"), "Unknown phase result")
    events, pending, terminals = [], {}, []
    text = raw.decode("utf-8", errors="strict").replace("\r\n", "\n")
    for line in text.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            require(not terminals, "Status emitted after terminal instrumentation result")
            key, separator, value = line.removeprefix("INSTRUMENTATION_STATUS: ").partition("=")
            require(bool(separator) and key not in pending, "Malformed or duplicate status field")
            pending[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            value = line.removeprefix("INSTRUMENTATION_STATUS_CODE: ")
            require(value in ("1", "2", "0") and not terminals, "Failed/ignored/unknown native phase status")
            events.append((int(value), pending))
            pending = {}
        elif line.startswith("INSTRUMENTATION_CODE: "):
            terminals.append(line.removeprefix("INSTRUMENTATION_CODE: "))
        elif line.startswith(("INSTRUMENTATION_FAILED", "INSTRUMENTATION_ABORTED")):
            raise RuntimeError("Instrumentation did not complete its selected phase")
    require(not pending and terminals == ["-1"], "Missing/ambiguous terminal instrumentation result")
    require([code for code, _ in events] == [1, 2, 0], "Need one started, attested and passed JUnit method")
    require(re.findall(r"(?m)^OK \(1 test\)$", text) == ["OK (1 test)"], "Missing exact one-method JUnit success")
    require(not re.search(r"(?m)^(FAILURES!!!|FAILURES|ERRORS|OK \(0 tests\))", text), "Failed or empty phase")
    for _, event in (events[0], events[2]):
        require(set(event) == {"id", "numtests", "class", "test", "current", "stream"},
                "Unexpected JUnit event shape")
        require(event["id"] == "InstrumentationTestRunner" and event["numtests"] == event["current"] == "1"
                and event["class"] == CLASS and event["test"] == method, "Different/multiple JUnit methods ran")
    marker = events[1][1]
    require(set(marker) == {"parlorPhase", "parlorFixtureId", "parlorLanguage", "parlorGame",
                            "parlorEntryKind", "parlorProcessNonce", "parlorPid"}, "Missing/extra native phase fields")
    require(marker["parlorFixtureId"] == fixture.id and marker["parlorLanguage"] == fixture.language
            and marker["parlorGame"] == fixture.game, "Native phase belongs to another fixture")
    require(marker["parlorEntryKind"] == fixture.entry_kind, "Native phase belongs to a different saved-entry kind")
    require(marker["parlorPhase"] == ("prepared" if method == "prepare" else "verified"), "Wrong native phase")
    nonce = marker["parlorProcessNonce"]
    require(str(UUID(nonce)) == nonce, "Invalid process correlation value")
    return {"result": "PASS", "method": f"{CLASS}#{method}", "fixture": fixture.id,
            "game": fixture.game, "language": fixture.language, "entry_kind": fixture.entry_kind,
            "process_nonce": nonce, "pid": live_pid(marker["parlorPid"]), "junit_methods": 1}


def live_pid(text: str) -> int:
    value = text.strip()
    require(re.fullmatch(r"[1-9][0-9]{0,9}", value) is not None and 1 < int(value) < 2**31,
            "Expected exactly one live app PID, not an absent/ambiguous/failed query")
    return int(value)


def require_absent(text: str) -> None:
    # The device shell prints this code itself. An adb transport error must not
    # be mistaken for pidof's exit1; the host command must also exit0.
    require(text.strip() == "PARLOR_PIDOF_EXIT=1", "Force-stopped app is not independently absent")


def complete_case(fixture: Fixture, prepared: dict, verified: dict, stopped_pid: int, absent: str) -> dict:
    require_absent(absent)
    live_pid(str(stopped_pid))
    for method, result in (("prepare", prepared), ("verify", verified)):
        require(result["result"] == "PASS" and result["method"] == f"{CLASS}#{method}"
                and result["fixture"] == fixture.id and result["game"] == fixture.game
                and result["language"] == fixture.language and result["junit_methods"] == 1,
                "Mismatched/nonpassing phase in process-boundary case")
        require(result["entry_kind"] == fixture.entry_kind, "Mixed entry kinds are not a process-boundary case")
    require(prepared["process_nonce"] != verified["process_nonce"], "Same-process phases are not acceptance")
    return {"result": "PASS", "fixture": fixture.id, "game": fixture.game, "language": fixture.language,
            "entry_kind": fixture.entry_kind,
            "prepared": prepared, "verified": verified, "launched_app_pid": stopped_pid,
            "explicit_force_stop_and_independent_absence": True, "physical_device_verified": False}
