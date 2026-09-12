"""Strict selected-image corroboration, not whole-address-space enumeration."""
import os

import image_text_extent as text_extent

LIMITATION = (
    'Separate ninth public launch; sample-reported image UUIDs bind to current built/installed '
    'file hashes and dwarfdump UUIDs. libproc PROC_PIDREGIONPATHINFO corroborates each selected '
    'image start with executable nonwritable region bounds, exact kernel vnode path, device, '
    'inode, owner and file size. Each exact artifact is independently SHA-bound before/after; '
    'bounded C/Python file decoders agree on its unique thin LE ARM64 __TEXT/UUID. '
    'The unchanged sample interval must be contained in the file __TEXT; the kernel mapping '
    'must either retain the original sample-contained prefix bound or equal that exact file '
    '__TEXT extent, with no intermediate tolerance or rounding. Claimed native intervals cannot overlap. '
    'This does not read mapped-memory Mach-O headers/UUIDs or hash mapped pages, enumerate '
    'all mappings, or supply vmmap evidence. sample still rejects unbound application images. '
    'Numeric-PID calls are bracketed by kernel audit-token/lifetime checks, not atomic lifetime '
    'reservations. Sample USER/literal-star paths remain explicitly lossy presentation aliases. '
    'The zero-offset readable/executable, flags-zero, equal __TEXT file/VM-size contract is specific to these thin ARM64 '
    'Debug images; it is not a universal Mach-O rule. No physical-device or Store proof.')

FIELDS = frozenset(('schema_version status pid requested_start requested_end_exclusive region_start '
    'region_size region_offset protection max_protection region_flags native_structure_bytes '
    'native_return_bytes path_exact vnode_identity_matches artifact_bytes artifact_device artifact_inode '
    'artifact_uid artifact_text extent_kind').split())


def selected_observer(arguments):
    flags = [value for value in arguments if value.startswith('--image-observer=')]
    if flags not in ([], ['--image-observer=libproc']):
        raise RuntimeError('Only one explicit libproc observer option is supported')
    return ('libproc' if flags else 'vmmap', [value for value in arguments if value not in flags])


def bind_regions(pid, selected, observations, text_bindings=None):
    if (type(pid) is not int or not 1 < pid <= 0x7fffffff or not isinstance(selected, dict) or
            not 3 <= len(selected) <= 16 or not isinstance(observations, dict) or set(selected) != set(observations) or
            not isinstance(text_bindings, dict) or set(selected) != set(text_bindings)):
        raise RuntimeError('Every selected image requires exactly one bounded native region observation')
    images, intervals = [], []
    for path, image in sorted(selected.items()):
        row = observations[path]
        if not isinstance(row, dict) or set(row) != FIELDS or row['status'] != 'PASS':
            raise RuntimeError('Missing, failed or unknown native region fields')
        integers = FIELDS - {'status', 'path_exact', 'vnode_identity_matches', 'artifact_text', 'extent_kind'}
        if any(type(row[key]) is not int or not 0 <= row[key] < 2**64 for key in integers):
            raise RuntimeError('Native region integers must be exact unsigned values')
        binding = text_bindings[path]
        text_end = text_extent.validate_binding(binding, image)
        if row['artifact_text'] != binding['layout']:
            raise RuntimeError('Native file geometry/UUID differs from the independently hashed artifact')
        expected_kind = text_extent.bound_kind(row['region_size'], image, row['artifact_text'])
        start, last = image['sample_start'], image['sample_end_inclusive']
        if type(start) is not int or type(last) is not int or not 0 < start <= last < 2**64 - 1:
            raise RuntimeError('Invalid prevalidated sample interval')
        end = last + 1
        if (row['schema_version'] != 2 or row['pid'] != pid or row['requested_start'] != start or
                row['requested_end_exclusive'] != end or row['region_start'] != start or
                row['extent_kind'] != expected_kind or row['region_offset'] != 0 or
                row['protection'] != 5 or row['region_flags'] & 1 or row['max_protection'] not in {5, 7} or
                not 0 < row['native_structure_bytes'] == row['native_return_bytes'] <= 65536 or
                row['path_exact'] is not True or row['vnode_identity_matches'] is not True or
                row['artifact_bytes'] != image['bytes'] or row['artifact_device'] != binding['identity'][0] or
                row['artifact_inode'] != binding['identity'][1] or row['artifact_uid'] != binding['identity'][3] or
                row['artifact_uid'] != os.getuid()):
            raise RuntimeError('Native region does not corroborate its exact selected artifact')
        intervals.append((start, start + row['region_size']))
        images.append(dict(path=path, **image, kernel_region=row, artifact_text_runtime_end_exclusive=text_end))
    ordered = sorted(intervals)
    if any(left[1] > right[0] for left, right in zip(ordered, ordered[1:])):
        raise RuntimeError('Selected native executable regions overlap')
    return dict(status='PASS', method='sample image UUID/path plus independently file-bound exact __TEXT geometry '
                'and libproc selected executable-region bounds/exact vnode identity, bound to built/installed '
                'file hashes and dwarfdump UUIDs',
                images=images, limitation=LIMITATION)
