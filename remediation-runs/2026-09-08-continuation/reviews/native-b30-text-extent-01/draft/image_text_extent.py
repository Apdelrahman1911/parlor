"""Exact owned thin-ARM64 file geometry; never infer a size from page rounding."""
import re
import stat
import struct
import uuid

from image_text_layout_diagnostic import MAX_ARTIFACT_BYTES, owned_bytes

MAX_COMMAND_BYTES = 1024 * 1024
TEXT_FIELDS = frozenset('vmaddr vmsize fileoff filesize maxprot initprot nsects flags'.split())
UUID = re.compile(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}')
PREFIX = 'sample-contained-prefix'
EXACT = 'exact-owned-text-extent'


def parse_text(raw, artifact_bytes):
    """Decode only fixed-width LE mach_header_64/segment_command_64/uuid_command.

    Every load command and declared section table is bounded before extracting
    the unique __TEXT and UUID. Fat, swapped, non-ARM64 and 32-bit segments fail.
    Unrelated 64-bit load commands remain bounded but do not supply geometry.
    """
    if (type(raw) is not bytes or not 32 <= len(raw) <= MAX_COMMAND_BYTES + 32 or
            type(artifact_bytes) is not int or not 32 <= artifact_bytes <= MAX_ARTIFACT_BYTES):
        raise RuntimeError('Missing or unbounded owned Mach-O header')
    magic, cpu, subtype, kind, count, size, _flags, reserved = struct.unpack_from('<8I', raw)
    if (magic != 0xfeedfacf or cpu != 0x0100000c or subtype != 0 or kind not in {2, 6} or reserved != 0 or
            not 1 <= count <= 4096 or not count * 8 <= size <= MAX_COMMAND_BYTES or size % 8 or
            size > artifact_bytes - 32 or size > len(raw) - 32):
        raise RuntimeError('Not a bounded thin little-endian ARM64 executable/dylib')
    cursor, end, text, image_uuid = 32, 32 + size, None, None
    for _ordinal in range(count):
        if end - cursor < 8:
            raise RuntimeError('Truncated Mach-O load command')
        command, command_size = struct.unpack_from('<II', raw, cursor)
        if command_size < 8 or command_size % 8 or command_size > end - cursor or command == 1:
            raise RuntimeError('Invalid-width or unbounded Mach-O load command')
        if command == 0x19:  # LC_SEGMENT_64
            if command_size < 72:
                raise RuntimeError('Truncated Mach-O segment')
            name = raw[cursor + 8:cursor + 24]
            vmaddr, vmsize, fileoff, filesize, maxprot, initprot, nsects, flags = struct.unpack_from(
                '<4Q4I', raw, cursor + 24)
            if (nsects > (command_size - 72) // 80 or command_size != 72 + 80 * nsects or
                    vmaddr > 2**64 - 1 - vmsize or fileoff > artifact_bytes or filesize > artifact_bytes - fileoff):
                raise RuntimeError('Mach-O segment/section table exceeds its file or address bound')
            if name.split(b'\0', 1)[0] == b'__TEXT':
                if text is not None or name != b'__TEXT' + b'\0' * 10:
                    raise RuntimeError('Duplicate or malformed __TEXT segment name')
                text = dict(vmaddr=vmaddr, vmsize=vmsize, fileoff=fileoff, filesize=filesize,
                            maxprot=maxprot, initprot=initprot, nsects=nsects, flags=flags)
        elif command == 0x1b:  # LC_UUID
            if image_uuid is not None or command_size != 24:
                raise RuntimeError('Duplicate or malformed Mach-O UUID')
            image_uuid = str(uuid.UUID(bytes=raw[cursor + 8:cursor + 24]))
        cursor += command_size
    if cursor != end or text is None or image_uuid is None or text['filesize'] < end:
        raise RuntimeError('Missing or incompletely framed owned Mach-O text/UUID')
    return dict(uuid=image_uuid, text_segment=text)


def validate_geometry(layout, image):
    """The narrow observed contract: no zero-fill, HIGHVM or page-size inference."""
    if (not isinstance(layout, dict) or set(layout) != {'uuid', 'text_segment'} or
            not isinstance(layout['uuid'], str) or UUID.fullmatch(layout['uuid']) is None or
            not isinstance(image.get('uuid'), str) or layout['uuid'] != image['uuid'].lower() or
            layout['uuid'] != image.get('observed_tool_uuid')):
        raise RuntimeError('Owned Mach-O UUID differs from sample and artifact inventory')
    segment = layout['text_segment']
    if (not isinstance(segment, dict) or set(segment) != TEXT_FIELDS or
            any(type(value) is not int or not 0 <= value < 2**64 for value in segment.values()) or
            any(segment[key] >= 2**32 for key in ('maxprot', 'initprot', 'nsects', 'flags'))):
        raise RuntimeError('Unknown or nonnumeric exact text geometry')
    start, last, file_bytes = image.get('sample_start'), image.get('sample_end_inclusive'), image.get('bytes')
    if (type(start) is not int or type(last) is not int or not 0 < start <= last < 2**64 - 1 or
            type(file_bytes) is not int or not 32 <= file_bytes <= MAX_ARTIFACT_BYTES or
            segment['fileoff'] != 0 or not 0 < last + 1 - start <= segment['filesize'] == segment['vmsize'] <= file_bytes or
            segment['vmaddr'] > 2**64 - 1 - segment['vmsize'] or start > 2**64 - 1 - segment['vmsize'] or
            segment['initprot'] != 5 or segment['maxprot'] not in {5, 7} or segment['flags'] != 0 or
            segment['nsects'] > (MAX_COMMAND_BYTES - 72) // 80):
        raise RuntimeError('Sample is not contained by the exact supported owned __TEXT geometry')
    return start + segment['vmsize']


def inspect_artifact(path, image):
    """Hash all exact inventoried bytes while retaining only the bounded prefix."""
    if (type(image.get('bytes')) is not int or not 32 <= image['bytes'] <= MAX_ARTIFACT_BYTES or
            not isinstance(image.get('sha256'), str) or re.fullmatch(r'[a-f0-9]{64}', image['sha256']) is None):
        raise RuntimeError('Missing exact artifact fingerprint for text geometry')
    prefix, fingerprint, identity = owned_bytes(path, image['bytes'], capture_prefix=MAX_COMMAND_BYTES + 32)
    if identity[2] != image['bytes'] or fingerprint != image['sha256']:
        raise RuntimeError('Text geometry file differs from selected artifact bytes')
    layout = parse_text(prefix, image['bytes'])
    validate_geometry(layout, image)
    return dict(layout=layout, identity=identity, sha256=fingerprint)


def validate_binding(binding, image):
    if (not isinstance(binding, dict) or set(binding) != {'layout', 'identity', 'sha256'} or
            binding['sha256'] != image.get('sha256') or not isinstance(binding['identity'], tuple) or
            len(binding['identity']) != 7 or any(type(value) is not int for value in binding['identity'])):
        raise RuntimeError('Missing descriptor/hash-bound text observation')
    dev, ino, size, uid, mode, _mtime, _ctime = binding['identity']
    if not 0 <= dev < 2**64 or not 0 < ino < 2**64 or not 0 <= uid < 2**32 or size != image['bytes'] or not stat.S_ISREG(mode):
        raise RuntimeError('Invalid owned artifact descriptor identity')
    return validate_geometry(binding['layout'], image)


def bound_kind(region_size, image, layout):
    validate_geometry(layout, image)
    if type(region_size) is not int or region_size <= 0:
        raise RuntimeError('Invalid native region extent')
    if region_size <= image['sample_end_inclusive'] + 1 - image['sample_start']:
        return PREFIX  # Preserve the original bounded prefix branch.
    if region_size == layout['text_segment']['vmsize']:
        return EXACT  # No tolerance between the sample end and exact file extent.
    raise RuntimeError('Native region is neither sample-contained nor the exact owned text extent')
