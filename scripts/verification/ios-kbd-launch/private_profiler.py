"""Reuse41423553336's independently admitted private-stdout native capture.

Capture argv, offline argv, public log filter and bounded-file reader are copied
byte-identically from c064c2bf. Raw system metadata remains private on the runner;
only exact-target native frames are exported. This is not app/input neutrality.
"""
import hashlib
import os
from pathlib import Path
import re
import subprocess

TOOL = '/usr/sbin/spindump'
MAX_RAW = 16*1024*1024
MAX_TEXT = 4*1024*1024

def capture_command(pid, raw):
    if type(pid) is not int or pid <= 0:raise ValueError('Exact owned positive PID required')
    # Actual41421565623 exit77 requires root for live sampling. GitHub standard
    # macOS runners document existing passwordless sudo; do not grant/alter it.
    # Never prompt or preserve caller credentials. Native5s limit bounds the
    # privileged tool itself inside the unchanged8s launcher deadline.
    return ['/usr/bin/sudo','-n',TOOL,str(pid),'1','10','-onlyTarget','-noText',
            '-noProcessingWhileSampling','-timelimit','5','-noFile']


def analysis_command(raw, text):
    return [TOOL,'-i',str(raw),'-timeline','-noBinary','-o',str(text)]


def public_log(raw):
    # Actual41422535117 output-write error falls back to stdout INCLUDING the
    # opaque format. Never upload raw CLI output, even on error. Keep only these
    # observed public stage/error lines; everything unknown/binary stays private.
    prefix=raw.split(b'Spindump binary format',1)[0]
    lines=[]
    for line in prefix.splitlines():
        known=line.startswith((b'Sampling ',b'Use spindump -i ',b'Spindump failed to save report to ',
                               b'Please enter a new output location ',b'spindump must be run as root '))
        if known and len(line)<=1024 and all(32<=v<=126 or v==9 for v in line):lines.append(line)
    return b'\n'.join(lines)+(b'\n' if lines else b'')


def owned_file(path, maximum):
    if path.resolve()!=path.absolute() or not path.is_file() or path.is_symlink():
        raise ValueError('Owned unredirected native report required')
    if not 0<path.stat().st_size<=maximum:raise ValueError('Bounded actual native report required')
    return path.read_bytes()


def target_frames(raw, pid, executable):
    """Export one exact native process's frames, not host inventory or raw data."""
    if not 0 < len(raw) <= MAX_TEXT:
        raise ValueError('Missing or oversized native text report')
    text = raw.decode('utf-8')
    expected = Path(executable).name
    processes = re.findall(r'^Process:\s+(.+?)\s+\[(\d+)\]\s*$', text, re.M)
    if processes != [(expected, str(pid))]:
        raise ValueError('Report must contain only the exact owned native target')
    section = text[text.index('Process:'):].split('  Binary Images:', 1)[0]
    paths = re.findall(r'^Path:\s+([^\n]+)$', section, re.M)
    if paths != [executable]:
        raise ValueError('Target report executable differs from attested process')
    rows = []
    threads = 0
    for line in section.splitlines():
        thread = re.match(r'^\s*Thread\s+(0x[0-9a-fA-F]+)\b', line)
        frame = re.match(r'^\s*\*?\d+\s+.*\[0x[0-9a-fA-F]+\]', line)
        if thread:
            rows.append('Thread ' + thread[1])
            threads += 1
        elif frame:
            if not 0 < len(line) <= 1024 or any(ord(c) < 32 and c != '\t' or ord(c) > 126 for c in line):
                raise ValueError('Unbounded or non-public native frame')
            rows.append(line)
    frames = len(rows) - threads
    if not threads or not frames:
        raise ValueError('Native thread and actual address-bearing frames required')
    safe = ('\n'.join(rows) + '\n').encode('ascii')
    return safe, dict(pid=pid, executable=executable, threads=threads, frames=frames,
                     text_bytes=len(raw), text_sha256=hashlib.sha256(raw).hexdigest(),
                     exported_sha256=hashlib.sha256(safe).hexdigest(),
                     raw_metadata_uploaded=False, host_inventory_uploaded=False,
                     original_input_causality=False)


def decode(private, out, pid, executable):
    """Decode after query retirement, without extending its original deadline."""
    raw, text = private / 'record.opaque-output', private / 'report.txt'
    owned_file(raw, MAX_RAW)
    # Command output can contain opaque metadata on failures. Never use PIPE,
    # the repository evidence directory, or stdout for its unfiltered output.
    with (private / 'analysis.stdout').open('xb') as stdout, (private / 'analysis.stderr').open('xb') as stderr:
        child = subprocess.Popen(analysis_command(raw, text), stdout=stdout, stderr=stderr)
        try:
            code = child.wait(timeout=8)
        finally:
            if child.poll() is None:
                child.kill()
                try:
                    child.wait(timeout=1)
                except subprocess.TimeoutExpired:
                    pass
        if code != 0:
            raise ValueError('Offline native decoder failed')
    safe, receipt = target_frames(owned_file(text, MAX_TEXT), pid, executable)
    with (out / 'query-native-frames.txt').open('xb') as f:
        f.write(safe)
    return receipt
