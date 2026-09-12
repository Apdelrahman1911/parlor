"""Synthetic fixed-width Mach-O bytes and real owned files, never native queries."""
import copy
import hashlib
import os
from pathlib import Path
import stat
import struct
import tempfile
import unittest
import uuid

import image_text_extent as extent
from image_text_layout_diagnostic import owned_bytes

IMAGE_UUID = '778d39b8-c432-3c5a-b597-8e95cca2d559'


def macho_prefix(text_size=32768, *, image_uuid=IMAGE_UUID, vmaddr=0, nsects=0, kind=6,
                 fileoff=0, filesize=None, maxprot=5, initprot=5, flags=0, extra_commands=()):
    filesize = text_size if filesize is None else filesize
    segment = struct.pack('<II16s4Q4I', 0x19, 72 + nsects * 80, b'__TEXT', vmaddr, text_size,
                          fileoff, filesize, maxprot, initprot, nsects, flags) + b'\0' * (80 * nsects)
    image_id = struct.pack('<II16s', 0x1b, 24, uuid.UUID(image_uuid).bytes)
    commands = segment + image_id + b''.join(extra_commands)
    return struct.pack('<8I', 0xfeedfacf, 0x0100000c, 0, kind, 2 + len(extra_commands), len(commands), 0, 0) + commands


def macho_bytes(text_size=32768, *, file_bytes=65536, **options):
    prefix = macho_prefix(text_size, **options)
    if len(prefix) > file_bytes:
        raise ValueError('Synthetic fixture prefix exceeds its file')
    return prefix + b'\0' * (file_bytes - len(prefix))


def selected_image(data, *, start=65536, span=24577, image_uuid=IMAGE_UUID):
    return dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest(), uuid=image_uuid,
                observed_tool_uuid=image_uuid, sample_start=start, sample_end_inclusive=start + span - 1)


def changed_word(raw, offset, value, width=4):
    changed = bytearray(raw)
    struct.pack_into('<I' if width == 4 else '<Q', changed, offset, value)
    return bytes(changed)


class ImageTextExtentTests(unittest.TestCase):
    def setUp(self):
        allocation = tempfile.TemporaryDirectory(prefix='parlor-text-extent-control-')
        self.addCleanup(allocation.cleanup)
        self.root = Path(allocation.name).resolve()

    def test_observed_b28_numbers_mean_containment_not_sample_filesize_equality(self):
        # Synthetic encoding of the retained diagnostic numbers, NOT B28 image bytes.
        raw = macho_prefix(42729472, nsects=24)
        image = dict(bytes=82428752, uuid=IMAGE_UUID, observed_tool_uuid=IMAGE_UUID,
                     sample_start=4461772800, sample_end_inclusive=4504490783)
        layout = extent.parse_text(raw, image['bytes'])
        self.assertEqual(4504502272, extent.validate_geometry(layout, image))
        self.assertEqual(42717984, image['sample_end_inclusive'] + 1 - image['sample_start'])
        self.assertEqual(42729472, layout['text_segment']['filesize'])
        self.assertEqual(extent.EXACT, extent.bound_kind(42729472, image, layout))
        self.assertEqual(4504490783, image['sample_end_inclusive'])

    def test_nonzero_preferred_vmaddr_and_bounded_unrelated_load_commands(self):
        for vmaddr, kind in ((0, 6), (0x100000000, 2)):
            raw = macho_prefix(vmaddr=vmaddr, kind=kind, nsects=2,
                               extra_commands=(struct.pack('<IIQ', 0x2a, 16, 1),))
            image = selected_image(macho_bytes(), start=0x104000000)
            layout = extent.parse_text(raw, image['bytes'])
            self.assertEqual(vmaddr, layout['text_segment']['vmaddr'])
            self.assertEqual(image['sample_start'] + 32768, extent.validate_geometry(layout, image))

    def test_header_architecture_width_count_and_total_bytes_fail_closed(self):
        raw = macho_prefix()
        malformed = [b'', raw[:31], raw[:-1], changed_word(raw, 0, 0xcafebabe),
            changed_word(raw, 0, 0xcffaedfe), changed_word(raw, 4, 0x01000007), changed_word(raw, 8, 2),
            changed_word(raw, 12, 1), changed_word(raw, 16, 0), changed_word(raw, 16, 4097),
            changed_word(raw, 16, 3), changed_word(raw, 20, 0), changed_word(raw, 20, 95),
            changed_word(raw, 20, extent.MAX_COMMAND_BYTES + 8), changed_word(raw, 28, 1),
            changed_word(raw, 32, 1)]
        for value in malformed:
            with self.subTest(sha256=hashlib.sha256(value).hexdigest()), self.assertRaises(RuntimeError):
                extent.parse_text(value, 65536)
        for size in (True, 31, 127, 512 * 1024 * 1024 + 1):
            with self.subTest(bytes=size), self.assertRaises(RuntimeError): extent.parse_text(raw, size)

    def test_unique_uuid_text_and_exact_command_section_table_framing(self):
        raw = macho_prefix()
        malformed_name = raw[:40] + b'__TEXT\0' + b'X' * 9 + raw[56:]
        self.assertEqual(len(raw), len(malformed_name))
        malformed = [macho_prefix(extra_commands=(raw[32:104],)),
            macho_prefix(extra_commands=(raw[104:128],)), changed_word(raw, 104, 0x2a),
            changed_word(raw, 36, 64), changed_word(raw, 36, 73), changed_word(raw, 108, 16),
            changed_word(raw, 96, 1), changed_word(raw, 96, 2**32 - 1),
            malformed_name, raw.replace(b'__TEXT', b'__DATA'),
            changed_word(raw, 56, 2**64 - 1, 8), macho_prefix(text_size=512, nsects=6)]
        for value in malformed:
            with self.subTest(sha256=hashlib.sha256(value).hexdigest()), self.assertRaises(RuntimeError):
                extent.parse_text(value, 65536)

    def test_zero_fill_flags_protections_file_bounds_and_address_overflow_rejected(self):
        image = selected_image(macho_bytes())
        for options in (dict(fileoff=1), dict(filesize=32767), dict(filesize=32769), dict(flags=1),
                        dict(initprot=7), dict(maxprot=0), dict(vmaddr=2**64 - 32767)):
            with self.subTest(options=options), self.assertRaises(RuntimeError):
                extent.validate_geometry(extent.parse_text(macho_prefix(**options), image['bytes']), image)
        layout = extent.parse_text(macho_prefix(), image['bytes'])
        for changes in (dict(sample_start=True), dict(sample_end_inclusive=65536 + 32768),
                        dict(sample_start=2**64 - 32767, sample_end_inclusive=2**64 - 2), dict(bytes=32767),
                        dict(uuid='11111111-2222-3333-4444-555555555555'), dict(observed_tool_uuid='wrong')):
            with self.subTest(changes=changes), self.assertRaises(RuntimeError):
                extent.validate_geometry(layout, dict(image, **changes))
        for key in extent.TEXT_FIELDS:
            changed = copy.deepcopy(layout); changed['text_segment'][key] = True
            with self.subTest(boolean=key), self.assertRaises(RuntimeError): extent.validate_geometry(changed, image)

    def test_old_prefix_or_exact_text_equality_never_intermediate_or_rounded_tolerance(self):
        image = selected_image(macho_bytes(40000))
        layout = extent.parse_text(macho_prefix(40000), image['bytes'])
        for size in (1, 8192, 24577): self.assertEqual(extent.PREFIX, extent.bound_kind(size, image, layout))
        self.assertEqual(extent.EXACT, extent.bound_kind(40000, image, layout))
        for size in (0, True, 24578, 28672, 32768, 39999, 40001, 2**64):
            with self.subTest(size=size), self.assertRaises(RuntimeError): extent.bound_kind(size, image, layout)

    def test_streaming_hash_covers_all_bytes_while_retained_prefix_is_bounded(self):
        data = macho_bytes(file_bytes=2 * 1024 * 1024 + 64)
        path = self.root / 'image'; path.write_bytes(data)
        image = selected_image(data)
        binding = extent.inspect_artifact(path, image)
        self.assertEqual(hashlib.sha256(data).hexdigest(), binding['sha256'])
        self.assertEqual(path.stat().st_ino, binding['identity'][1])
        self.assertEqual(98304, extent.validate_binding(binding, image))
        prefix, fingerprint, identity = owned_bytes(path, len(data), capture_prefix=extent.MAX_COMMAND_BYTES + 32)
        self.assertEqual(extent.MAX_COMMAND_BYTES + 32, len(prefix))
        self.assertEqual(binding['sha256'], fingerprint)
        self.assertEqual(binding['identity'], identity)
        path.write_bytes(data[:-1] + b'x')
        with self.assertRaises(RuntimeError): extent.inspect_artifact(path, image)

    def test_redirected_wrong_fingerprint_or_forged_descriptor_binding_cannot_qualify(self):
        data = macho_bytes(); path = self.root / 'image'; path.write_bytes(data)
        image = selected_image(data)
        link = self.root / 'link'; link.symlink_to(path)
        for candidate, selected in ((link, image), (path, dict(image, bytes=len(data) - 1)),
                                    (path, dict(image, sha256='0' * 64)), (self.root, image)):
            with self.subTest(path=str(candidate)), self.assertRaises(RuntimeError):
                extent.inspect_artifact(candidate, selected)
        binding = extent.inspect_artifact(path, image)
        for index, value in ((0, -1), (1, 0), (2, 1), (3, -1), (4, stat.S_IFDIR | 0o700), (5, True)):
            forged = dict(binding); identity = list(binding['identity']); identity[index] = value
            forged['identity'] = tuple(identity)
            with self.subTest(index=index), self.assertRaises(RuntimeError): extent.validate_binding(forged, image)
        self.assertEqual(os.getuid(), binding['identity'][3])


if __name__ == '__main__':
    unittest.main()
