"""Bounded on-disk __TEXT diagnostics only; never accept a kernel region."""
import hashlib
import os
from pathlib import Path
import re
import stat

MAX_OUTPUT_BYTES = 256 * 1024
MAX_ARTIFACT_BYTES = 512 * 1024 * 1024
REGION_FIELDS = frozenset(('requested_start requested_end_exclusive pri_address pri_size pri_offset '
    'pri_protection pri_max_protection pri_flags native_structure_bytes native_return_bytes query_errno').split())
UUID = re.compile(r'[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}')
TEXT_FIELDS = ('vmaddr', 'vmsize', 'fileoff', 'filesize', 'maxprot', 'initprot', 'nsects', 'flags')


def span_failure(row, selected):
    """Only the completed, sole oversized-region failure can trigger more work."""
    if (not isinstance(row, dict) or set(row) != {'schema_version', 'status', 'reason', 'native_error',
            'region_diagnostic'} or type(row['schema_version']) is not int or row['schema_version'] != 1 or
            row['status'] != 'FAIL' or row['reason'] != 'not-selected-executable-region' or
            type(row['native_error']) is not int or row['native_error'] != 0):
        raise RuntimeError('Not a completed selected-region boundary diagnostic')
    region = row['region_diagnostic']
    if (not isinstance(region, dict) or set(region) != REGION_FIELDS or
            any(type(value) is not int or not 0 <= value < 2**64 for value in region.values())):
        raise RuntimeError('Unknown or nonnumeric selected-region failure fields')
    start, last = selected['sample_start'], selected['sample_end_inclusive']
    if (type(start) is not int or type(last) is not int or not 0 < start <= last < 2**64 - 1 or
            region['requested_start'] != start or region['requested_end_exclusive'] != last + 1 or
            region['pri_address'] != start or not last + 1 - start < region['pri_size'] <= 2**64 - 1 - start or
            region['pri_offset'] != 0 or region['pri_protection'] != 5 or
            region['pri_max_protection'] not in {5, 7} or not 0 <= region['pri_flags'] < 2**32 or
            region['pri_flags'] & 1 or region['query_errno'] != 0 or
            not 0 < region['native_structure_bytes'] == region['native_return_bytes'] <= 65536):
        raise RuntimeError('Failure is not solely the observed selected-region span boundary')
    return dict(region)


def owned_bytes(path, maximum, *, capture=False):
    """Read one already-owned regular file through a nonfollowing bounded descriptor."""
    path = Path(path)
    if (not path.is_absolute() or path.is_symlink() or path.resolve(strict=True) != path or
            type(maximum) is not int or not 0 < maximum <= MAX_ARTIFACT_BYTES):
        raise RuntimeError('Unbounded or redirected diagnostic file')
    before = path.lstat()
    def identity(value):
        return (value.st_dev, value.st_ino, value.st_size, value.st_uid, value.st_mode,
                value.st_mtime_ns, value.st_ctime_ns)
    if (not stat.S_ISREG(before.st_mode) or before.st_uid != os.getuid() or
            not 0 < before.st_size <= maximum):
        raise RuntimeError('Diagnostic file changed type, owner or size bound')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        opened = os.fstat(fd)
        if identity(opened) != identity(before):
            raise RuntimeError('Diagnostic file replaced before read')
        remaining, chunks, digest = opened.st_size, [], hashlib.sha256()
        while remaining:
            chunk = os.read(fd, min(remaining, 1024 * 1024))
            if not chunk:
                raise RuntimeError('Diagnostic file truncated during read')
            digest.update(chunk)
            if capture:
                chunks.append(chunk)
            remaining -= len(chunk)
        if os.read(fd, 1) or identity(os.fstat(fd)) != identity(opened) or identity(path.lstat()) != identity(opened):
            raise RuntimeError('Diagnostic file changed during read')
        return (b''.join(chunks) if capture else None, digest.hexdigest(), identity(opened))
    finally:
        os.close(fd)


def artifact_fingerprint(path, selected):
    if (type(selected.get('bytes')) is not int or not 0 < selected['bytes'] <= MAX_ARTIFACT_BYTES or
            not isinstance(selected.get('sha256'), str) or
            re.fullmatch(r'[0-9a-f]{64}', selected['sha256']) is None):
        raise RuntimeError('Missing exact selected-artifact hash or byte count')
    _raw, digest, identity = owned_bytes(path, selected['bytes'])
    if identity[2] != selected['bytes'] or digest != selected['sha256']:
        raise RuntimeError('Selected artifact differs from its existing inventory')
    return identity


def number(text, bits=64):
    if not isinstance(text, str) or re.fullmatch(r'(?:0x[0-9a-fA-F]{1,16}|[0-9]{1,20})', text) is None:
        raise RuntimeError('Unknown native load-command number')
    value = int(text, 16 if text.startswith('0x') else 10)
    if not 0 <= value < 2**bits:
        raise RuntimeError('Native load-command number outside its field width')
    return value


def parse_text_layout(raw, artifact_path, selected):
    """Recognize only otool's bounded load-command framing, one __TEXT and LC_UUID."""
    if type(raw) is not bytes or not 0 < len(raw) <= MAX_OUTPUT_BYTES or b'\0' in raw:
        raise RuntimeError('Missing or unbounded native layout output')
    text = raw.decode('utf-8')
    if any(len(line) > 4096 for line in text.splitlines()) or len(text.splitlines()) > 8192:
        raise RuntimeError('Native layout line bound exceeded')
    parts = re.split(r'^Load command ([0-9]{1,4})\s*$', text, flags=re.MULTILINE)
    if (parts[0].rstrip('\n') not in {str(artifact_path) + ':', str(artifact_path) + ' (architecture arm64):'} or
            not 1 <= (len(parts) - 1) // 2 <= 4096):
        raise RuntimeError('Native layout is not the exact selected ARM64 artifact')
    result, uuid = None, None
    for ordinal in range((len(parts) - 1) // 2):
        if parts[2 * ordinal + 1] != str(ordinal):
            raise RuntimeError('Nonconsecutive native load commands')
        lines = [line.strip() for line in parts[2 * ordinal + 2].splitlines() if line.strip()]
        if len(lines) < 2 or re.fullmatch(r'cmd LC_[A-Z0-9_]+', lines[0]) is None or not lines[1].startswith('cmdsize '):
            raise RuntimeError('Unknown native load-command framing')
        command_size = number(lines[1][8:], 32)
        if command_size < 8 or command_size % 8:
            raise RuntimeError('Invalid native load-command size')
        if lines[0] == 'cmd LC_UUID':
            if (uuid is not None or command_size != 24 or len(lines) != 3 or
                    not lines[2].startswith('uuid ') or UUID.fullmatch(lines[2][5:]) is None):
                raise RuntimeError('Missing or ambiguous native image UUID')
            uuid = lines[2][5:].lower()
        elif lines[0] == 'cmd LC_SEGMENT_64':
            if len(lines) < 3 or not lines[2].startswith('segname '):
                raise RuntimeError('Missing native segment name')
            if lines[2] != 'segname __TEXT':
                continue
            if result is not None or len(lines) < 11:
                raise RuntimeError('Missing or duplicated native __TEXT command')
            result = {}
            for key, line in zip(TEXT_FIELDS, lines[3:11]):
                if not line.startswith(key + ' '):
                    raise RuntimeError('Unknown native __TEXT field order')
                result[key] = number(line[len(key) + 1:], 32 if key in {'maxprot', 'initprot', 'nsects', 'flags'} else 64)
            if command_size != 72 + 80 * result['nsects']:
                raise RuntimeError('Native __TEXT section count disagrees with command size')
    if (result is None or not isinstance(selected.get('uuid'), str) or UUID.fullmatch(selected['uuid']) is None or
            uuid != selected['uuid'].lower() or selected.get('observed_tool_uuid') != uuid):
        raise RuntimeError('Native layout UUID differs from selected sample/artifact UUID')
    return dict(uuid=uuid, text_segment=result)


def validate_host_page_size(host_page_size):
    if (type(host_page_size) is not int or not 0 < host_page_size <= 1024 * 1024 or
            host_page_size & (host_page_size - 1)):
        raise RuntimeError('Native host page size is unavailable or invalid')
    return host_page_size


def make_diagnostic(raw, artifact_path, selected, failure, host_page_size):
    region = span_failure(failure, selected)
    validate_host_page_size(host_page_size)
    layout = parse_text_layout(raw, artifact_path, selected)
    return dict(schema_version=1, kind='SELECTED_IMAGE_TEXT_LAYOUT_FAILURE_DIAGNOSTIC',
        status='OBSERVED_NOT_PROVENANCE', proves_provenance=False, original_failure_preserved=True,
        artifact={key: selected[key] for key in ('origin', 'relative_path', 'kind', 'bytes', 'sha256', 'uuid')},
        selected_sample=dict(start=selected['sample_start'], end_inclusive=selected['sample_end_inclusive'],
                             observed_tool_uuid=selected['observed_tool_uuid']),
        original_region_diagnostic=region, native_layout=layout,
        native_output=dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()),
        host_page_size=dict(value=host_page_size, api='os.sysconf(SC_PAGE_SIZE)',
                            target_page_size_proven=False),
        limitation='On-disk selected ARM64 artifact load commands from one bounded otool query, not '
                   'mapped-memory reads or kernel vnode corroboration. Host page size is measured, '
                   'not assumed to be the target VM granularity. No interval rounding, boundary '
                   'acceptance, fallback or provenance PASS is performed.')
