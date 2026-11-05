"""Documented exact-PID sample alternative to the stalled spindump collector.

No eager symbol lookup, name selection, retry, system-wide capture or raw export.
The native query's ownership and deadline remain the launcher's prerequisites.
"""
import hashlib
from pathlib import Path
import re

from scripts.ci.native_process_probe import stack_frames
from private_profiler import MAX_TEXT, owned_file


def capture_command(pid, raw):
    if type(pid) is not int or pid <= 0:
        raise ValueError("Exact owned positive PID required")
    # Actual installed sample(1) documentation: -file suppresses duplicate
    # stdout/default /tmp reports. /dev/stdout is our private file-backed fd.
    # Existing native_process_probe uses this same bounded, non-eager command.
    return ["/usr/bin/sample", str(pid), "1", "10", "-file", "/dev/stdout"]


def target_frames(raw, pid, executable):
    if not 0 < len(raw) <= MAX_TEXT:
        raise ValueError("Missing or oversized native sample report")
    text = raw.decode("utf-8")
    processes = re.findall(r"^Process:\s+(.+?)\s+\[(\d+)\]\s*$", text, re.M)
    paths = re.findall(r"^Path:\s+([^\n]+)$", text, re.M)
    if processes != [(Path(executable).name, str(pid))] or paths != [executable]:
        raise ValueError("Report must match only the exact attested native target")
    frames = stack_frames(raw)
    if not frames or not any(re.search(r"\[0x[0-9a-fA-F]+\]", line) for line in frames):
        raise ValueError("Actual address-bearing native sample frames required")
    safe = ("\n".join(frames) + "\n").encode("ascii")
    return safe, dict(pid=pid, executable=executable, frames=len(frames),
                     text_bytes=len(raw), text_sha256=hashlib.sha256(raw).hexdigest(),
                     exported_sha256=hashlib.sha256(safe).hexdigest(),
                     raw_metadata_uploaded=False, host_inventory_uploaded=False,
                     original_input_causality=False, native_tool="sample")


def decode(private, out, pid, executable):
    # sample writes its text report directly; no blocking offline symbol decoder.
    safe, receipt = target_frames(owned_file(private / "record.opaque-output", MAX_TEXT), pid, executable)
    with (out / "query-native-frames.txt").open("xb") as stream:
        stream.write(safe)
    return receipt
