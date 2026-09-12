"""Bounded failure-only image-format metadata, never provenance acceptance.

This module performs no filesystem, account, process or native-tool operations.
It cannot create a path policy, select trusted images or relax a parser. Unknown
paths/symbols/UUIDs are never retained. Artifact indexes refer to the sorted exact
inventory keys, not to a guessed match or a newly authorized image origin.
"""
import hashlib
import io
import json
from pathlib import Path
import re

from external_image_provenance import OwnedToolPaths, SAMPLE_ROW, UUID, validate_artifacts

MAX_INPUT_BYTES = 16 * 1024 * 1024
MAX_OUTPUT_BYTES = 256 * 1024
MAX_LINES = 65536
MAX_LINE_BYTES = 8192
MAX_CANDIDATES = 32
MAX_PATH_BYTES = 4096
MAX_COMPONENTS = 128
MAX_RELATIONS_PER_COMPONENT = 64
MAX_RELATIONS_PER_PATH = 2048
IMAGE_NAMES = frozenset({'Parlor', 'Parlor.debug.dylib', 'ComposeApp'})
PUBLIC_MARKERS = frozenset({'USER', '*', '...', '…', '~', '<redacted>'})
ABSOLUTE_TAIL = re.compile(r'(?:^|[ \t])(/[^\r\n]*?)\s*$')
SAMPLE_ADDRESS = re.compile(r'^\s*(0x[0-9a-fA-F]+)\s*-\s*(0x[0-9a-fA-F]+)\s+')
VMMAP_ADDRESS = re.compile(r'^\s*__TEXT\s+([0-9a-fA-F]+)-([0-9a-fA-F]+)\s+')


def _context(artifacts, tool_paths, selected):
    validate_artifacts(artifacts)
    if tool_paths is not None:
        if not isinstance(tool_paths, OwnedToolPaths):
            raise RuntimeError('Unknown diagnostic path policy')
        tool_paths.require_inventory(artifacts)
    paths = sorted(artifacts)
    components = [path.split('/') for path in paths]
    if any(len(parts) > MAX_COMPONENTS for parts in components):
        raise RuntimeError('Diagnostic inventory component budget exceeded')
    if any(not isinstance(row['relative_path'], str) or len(row['relative_path'].encode()) > MAX_PATH_BYTES
           for row in artifacts.values()):
        raise RuntimeError('Invalid diagnostic inventory relative path')
    if selected is not None:
        if not isinstance(selected, dict) or not 1 <= len(selected) <= len(artifacts):
            raise RuntimeError('Invalid diagnostic sampled-image reference')
        for path, row in selected.items():
            if (path not in artifacts or not isinstance(row, dict) or
                    any(row.get(key) != value for key, value in artifacts[path].items()) or
                    type(row.get('sample_start')) is not int or type(row.get('sample_end_inclusive')) is not int or
                    not 0 < row['sample_start'] <= row['sample_end_inclusive'] < 2**64):
                raise RuntimeError('Diagnostic sample reference differs from its inventory')
    return paths, components


def _path_shape(value, paths, components, tool_paths):
    if not isinstance(value, str) or not 0 < len(value.encode()) <= MAX_PATH_BYTES:
        raise RuntimeError('Diagnostic candidate path budget exceeded')
    parts = value.split('/')
    if len(parts) > MAX_COMPONENTS:
        raise RuntimeError('Diagnostic candidate component budget exceeded')
    exact = [index for index, path in enumerate(paths) if value == path]
    canonical = value if tool_paths is None else tool_paths.resolve(value)
    aliases = [index for index, path in enumerate(paths) if canonical == path and value != path]
    shape, relationships = [], 0
    for part in parts:
        matches = [dict(artifact_index=index, component_indexes=[offset for offset, known in enumerate(known_parts)
                                                                if known == part])
                   for index, known_parts in enumerate(components) if part in known_parts]
        count = sum(len(match['component_indexes']) for match in matches)
        relationships += count
        if count > MAX_RELATIONS_PER_COMPONENT or relationships > MAX_RELATIONS_PER_PATH:
            raise RuntimeError('Diagnostic component relationship budget exceeded')
        if matches:
            shape.append(dict(known_components=matches))
        elif part in PUBLIC_MARKERS:
            shape.append(dict(public_marker=part))
        else:
            shape.append(dict(unknown_sha256=hashlib.sha256(part.encode()).hexdigest(), bytes=len(part.encode())))
    return dict(bytes=len(value.encode()), component_shape=shape,
                exact_artifact_indexes=exact, owned_presentation_artifact_indexes=aliases,
                classification='EXACT_BOUND' if exact else 'OWNED_USER_PRESENTATION' if aliases else 'UNBOUND')


def _interval(line, tool):
    match = (SAMPLE_ADDRESS if tool == 'sample' else VMMAP_ADDRESS).match(line)
    value = dict(syntax_matches=match is not None, start=None, end=None,
                 start_hex_digits=None, end_hex_digits=None, within_u64=False,
                 ordered_nonzero=False, end_inclusive=tool == 'sample')
    if match is not None:
        start, end = int(match[1], 16), int(match[2], 16)
        # An exclusive vmmap end may be the sentinel just beyond the last u64
        # address. Do not misdescribe a range the unchanged binder permits.
        end_fits = 0 <= end < 2**64 if tool == 'sample' else 0 <= end <= 2**64
        value.update(start_hex_digits=len(match[1].removeprefix('0x')),
                     end_hex_digits=len(match[2].removeprefix('0x')),
                     within_u64=0 <= start < 2**64 and end_fits)
        if value['within_u64']:
            value.update(start=start, end=end,
                         ordered_nonzero=0 < start <= end if tool == 'sample' else 0 < start < end)
    return value


def _uuid_relations(line, artifacts, paths, path_indexes, matched_uuid=None):
    tokens = [matched_uuid] if matched_uuid is not None else re.findall(r'<([^<>\r\n]*)>', line)
    valid = len(tokens) == 1 and re.fullmatch(UUID, tokens[0]) is not None
    matches = [index for index, path in enumerate(paths)
               if valid and tokens[0].lower() == artifacts[path]['uuid'].lower()]
    return dict(token_count=len(tokens), syntax_matches=valid, matching_artifact_indexes=matches,
                matches_bound_path_uuid=bool(path_indexes) and all(index in matches for index in path_indexes))


def _sample_interval_relations(interval, path_indexes, paths, selected):
    if selected is None:
        return []
    result = []
    for index in path_indexes:
        row = selected.get(paths[index])
        if row is not None:
            bounded = interval['within_u64'] and interval['ordered_nonzero']
            result.append(dict(artifact_index=index,
                same_start=bounded and interval['start'] == row['sample_start'],
                end_within_sample=bounded and interval['end'] <= row['sample_end_inclusive'] + 1))
    return result


def image_diagnostic(raw, tool, artifacts, *, tool_paths=None, selected=None,
                     budget=MAX_INPUT_BYTES, output_budget=MAX_OUTPUT_BYTES):
    """Describe candidate formats after a rejection without accepting any image.

    ``selected`` is only the successful parse_sample result at the caller. A
    failed sample has no such reference, even when some rows looked plausible.
    Invalid/oversized diagnostics raise; the caller must preserve its original
    failure. Missing/ambiguous tables are reported as unscanned, never repaired.
    """
    if tool not in {'sample', 'vmmap'} or tool == 'sample' and selected is not None:
        raise RuntimeError('Invalid failure-only image diagnostic context')
    if (type(budget) is not int or not 0 < budget <= MAX_INPUT_BYTES or
            type(output_budget) is not int or not 0 < output_budget <= MAX_OUTPUT_BYTES or
            not isinstance(raw, str) or not 0 < len(raw.encode()) <= budget):
        raise RuntimeError('Empty or unbounded failure-only image diagnostic')
    paths, components = _context(artifacts, tool_paths, selected)
    forms = {path: path for path in paths} if tool_paths is None else tool_paths.aliases
    value = dict(schema_version=1, kind='FAILURE_ONLY_EXTERNAL_IMAGE_FORMAT', proves_provenance=False,
        tool=tool, decoded_input_bytes=len(raw.encode()),
        inventory_value_sha256=hashlib.sha256(json.dumps(artifacts, sort_keys=True, separators=(',', ':')).encode()).hexdigest(),
        artifact_index_order='SORTED_EXACT_INVENTORY_PATHS',
        inventory=[dict(index=index, origin=artifacts[path]['origin'], kind=artifacts[path]['kind'])
                   for index, path in enumerate(paths)],
        sample_selection_available=selected is not None, binary_image_table_markers=None,
        scan_status='COMPLETE_DIAGNOSTIC_SCAN_NOT_PROVENANCE', lines_scanned=0, candidates=[])
    table = raw
    if tool == 'sample':
        value['binary_image_table_markers'] = raw.count('\nBinary Images:\n')
        if value['binary_image_table_markers'] != 1:
            value['scan_status'] = 'NOT_SCANNED_MISSING_OR_AMBIGUOUS_TABLE'
            table = ''
        else:
            table = raw.split('\nBinary Images:\n', 1)[1]
    for ordinal, line in enumerate(io.StringIO(table), start=1):
        if ordinal > MAX_LINES or len(line.encode()) > MAX_LINE_BYTES:
            raise RuntimeError('Failure-only image diagnostic line budget exceeded')
        value['lines_scanned'] = ordinal
        if tool == 'vmmap' and not line.lstrip().startswith('__TEXT'):
            continue
        strict = SAMPLE_ROW.fullmatch(line.rstrip('\r\n')) if tool == 'sample' else None
        tail = ABSOLUTE_TAIL.search(line)
        reported = strict[4] if strict is not None else tail[1] if tail is not None else None
        contains = sorted({paths.index(path) for form, path in forms.items() if form in line})
        resolved = (reported if tool_paths is None else tool_paths.resolve(reported)) if reported is not None else None
        name = Path(reported).name if reported is not None else None
        if name not in IMAGE_NAMES and resolved not in artifacts and not contains:
            continue  # No unrelated system row or arbitrary symbol is retained.
        if len(value['candidates']) >= MAX_CANDIDATES:
            raise RuntimeError('Failure-only image candidate budget exceeded')
        shape = _path_shape(reported, paths, components, tool_paths) if reported is not None else None
        indexes = [] if shape is None else shape['exact_artifact_indexes'] + shape['owned_presentation_artifact_indexes']
        interval = _interval(line, tool)
        row = dict(table_line=ordinal, bytes=len(line.encode()),
                   image_name=name if name in IMAGE_NAMES else None,
                   path_locator='STRICT_SAMPLE_ROW' if strict is not None else 'ABSOLUTE_TAIL' if tail is not None else 'NONE',
                   contains_bound_form_artifact_indexes=contains, path=shape, interval=interval)
        if tool == 'sample':
            prefix = line[:tail.start(1)] if tail is not None else line
            row.update(strict_row_syntax_matches=strict is not None,
                       uuid=_uuid_relations(prefix, artifacts, paths, indexes,
                                            matched_uuid=strict[3] if strict is not None else None))
        else:
            row['mapping_kind'] = ('EXPECTED_TEXT' if re.match(r'^\s*__TEXT\s+', line) is not None else
                                   'ALTERNATE_TEXT_EXEC' if re.match(r'^\s*__TEXT_EXEC\s+', line) is not None else
                                   'UNKNOWN_TEXT_PREFIX')
            row['successful_sample_interval_relations'] = _sample_interval_relations(interval, indexes, paths, selected)
        value['candidates'].append(row)
        # Check each bounded append, not only an arbitrarily amplified complete
        # result. Even adversarial repeated components cannot accumulate a huge
        # retained diagnostic before its serialization budget is evaluated.
        if len((json.dumps(value, indent=2) + '\n').encode()) > output_budget:
            raise RuntimeError('Failure-only image diagnostic serialization budget exceeded')
    if len((json.dumps(value, indent=2) + '\n').encode()) > output_budget:
        raise RuntimeError('Failure-only image diagnostic serialization budget exceeded')
    return value
