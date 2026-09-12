"""Strict selected-image corroboration, not whole-address-space enumeration."""
LIMITATION = (
    'Separate ninth public launch; sample-reported image UUIDs bind to current built/installed '
    'file hashes and dwarfdump UUIDs. libproc PROC_PIDREGIONPATHINFO corroborates each selected '
    'image start with executable nonwritable region bounds, exact kernel vnode path, device, '
    'inode and file size. It does not read Mach-O headers/UUIDs or hash mapped pages, enumerate '
    'all mappings, or supply vmmap evidence. sample still rejects unbound application images. '
    'Numeric-PID calls are bracketed by kernel audit-token/lifetime checks, not atomic lifetime '
    'reservations. Sample USER/literal-star paths remain explicitly lossy presentation aliases. '
    'The zero-offset readable/executable mapping contract is specific to these thin ARM64 '
    'Debug images; it is not a universal Mach-O rule. No physical-device or Store proof.')

FIELDS = frozenset(('schema_version status pid requested_start requested_end_exclusive region_start '
    'region_size region_offset protection max_protection region_flags native_structure_bytes '
    'native_return_bytes path_exact vnode_identity_matches artifact_bytes').split())


def selected_observer(arguments):
    flags = [value for value in arguments if value.startswith('--image-observer=')]
    if flags not in ([], ['--image-observer=libproc']):
        raise RuntimeError('Only one explicit libproc observer option is supported')
    return ('libproc' if flags else 'vmmap', [value for value in arguments if value not in flags])


def bind_regions(pid, selected, observations):
    if (type(pid) is not int or not 1 < pid <= 0x7fffffff or not isinstance(selected, dict) or
            not 3 <= len(selected) <= 16 or not isinstance(observations, dict) or set(selected) != set(observations)):
        raise RuntimeError('Every selected image requires exactly one bounded native region observation')
    images = []
    for path, image in sorted(selected.items()):
        row = observations[path]
        if not isinstance(row, dict) or set(row) != FIELDS or row['status'] != 'PASS':
            raise RuntimeError('Missing, failed or unknown native region fields')
        integers = FIELDS - {'status', 'path_exact', 'vnode_identity_matches'}
        if any(type(row[key]) is not int or not 0 <= row[key] < 2**64 for key in integers):
            raise RuntimeError('Native region integers must be exact unsigned values')
        start, last = image['sample_start'], image['sample_end_inclusive']
        if type(start) is not int or type(last) is not int or not 0 < start <= last < 2**64 - 1:
            raise RuntimeError('Invalid prevalidated sample interval')
        end = last + 1
        if (row['schema_version'] != 1 or row['pid'] != pid or row['requested_start'] != start or
                row['requested_end_exclusive'] != end or row['region_start'] != start or
                not 0 < row['region_size'] <= end - start or row['region_offset'] != 0 or
                row['protection'] != 5 or row['region_flags'] & 1 or row['max_protection'] not in {5, 7} or
                not 0 < row['native_structure_bytes'] == row['native_return_bytes'] <= 65536 or
                row['path_exact'] is not True or row['vnode_identity_matches'] is not True or
                row['artifact_bytes'] != image['bytes']):
            raise RuntimeError('Native region does not corroborate its exact selected artifact')
        images.append(dict(path=path, **image, kernel_region=row))
    return dict(status='PASS', method='sample image UUID/path plus libproc selected executable-region '
                'bounds/exact vnode identity, bound to built/installed file hashes and dwarfdump UUIDs',
                images=images, limitation=LIMITATION)
