import json
import os
from pathlib import Path
import struct
import tempfile
import unittest
import zlib
import public_attachments as p


def png(width=1, height=1):
    chunk = lambda kind, raw: struct.pack('>I', len(raw)) + kind + raw + struct.pack('>I', zlib.crc32(kind + raw) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(b'\x00\xff\x00\x00\xff')) + chunk(b'IEND', b'')


def activities():
    return dict(testIdentifier='FrozenPublicMethod()', children=[dict(title=p.TITLE, isAssociatedWithFailure=False), dict(title='XCTAssertEqual failed: Public setup control must stop scrolling before it is tapped', isAssociatedWithFailure=True)])


class PublicAttachmentControls(unittest.TestCase):
    def test_export_command_reads_existing_failed_attachments_without_application_execution(self):
        self.assertEqual(p.attachment_command(Path('/owned/failed.xcresult'), Path('/owned/exported'), method=p.METHOD),
                         ['export', 'attachments', '--path', '/owned/failed.xcresult',
                          '--output-path', '/owned/exported', '--test-id',
                          'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInArabic()'])

    def test_whole_result_foreign_test_and_wildcard_exports_are_not_eligible(self):
        for method in ('', None, 'IOSAppLaunchUITests', 'IOSAppLaunchUITests/*',
                       'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetup()'):
            with self.assertRaises(ValueError):
                p.attachment_command(Path('/owned/failed.xcresult'), Path('/owned/exported'), method=method)

    def test_installed_export_help_must_support_exact_test_selection(self):
        p.verify_export_help(b'--path --output-path --test-id --only-failures')
        for helptext in (b'--path --output-path --only-failures', b'--test-id', b''):
            with self.assertRaises(ValueError):
                p.verify_export_help(helptext)

    def test_only_the_exact_pre_input_settlement_failure_is_eligible(self):
        p.verify_public_failure(activities(), 'FrozenPublicMethod()')
        for changed in (dict(testIdentifier='foreign'),
                        activities() | {'children': []},
                        activities() | {'children': activities()['children'] + [dict(title='Type private text')]},
                        activities() | {'children': activities()['children'] + [dict(title='Another failure', isAssociatedWithFailure=True)]}):
            with self.assertRaises(ValueError):
                p.verify_public_failure(changed, 'FrozenPublicMethod()')

    def test_only_numeric_png_metadata_and_fixed_public_paths_leave_the_export_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp);src = root / 'exported';src.mkdir();out = root / 'curated';out.mkdir()
            (src / 'private-name.png').write_bytes(png())
            (src / 'manifest.json').write_text('{"private": "never copied"}')
            (src / 'recording.mp4').write_bytes(b'not uploaded')
            result = p.curate_public_screenshots(src, out)
            self.assertEqual(result[0]['path'], 'public-settlement-01.png')
            self.assertEqual(result[0]['width'], 1)
            self.assertNotIn('private', json.dumps(result))
            self.assertEqual(list(out.iterdir()), [out / 'public-settlement-01.png'])
            self.assertEqual((out / result[0]['path']).read_bytes(), png())
            with self.assertRaises(ValueError):
                p.curate_public_screenshots(src, out)

    def test_corrupt_oversized_geometry_and_missing_png_reject_before_writing(self):
        for raw in (b'not a PNG', png(width=4097), png(height=0), png()[:-1] + bytes([png()[-1] ^ 1]), None):
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp);src = root / 'exported';src.mkdir();out = root / 'curated';out.mkdir()
                if raw is not None:
                    (src / 'a.png').write_bytes(raw)
                else:
                    (src / 'manifest.json').write_text('{}')
                with self.assertRaises(ValueError):
                    p.curate_public_screenshots(src, out)
                self.assertEqual(list(out.iterdir()), [])

    def test_redirected_and_multiple_link_inputs_reject_before_writing(self):
        for kind in ('symlink', 'hardlink'):
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp);src = root / 'exported';src.mkdir();out = root / 'curated';out.mkdir();outside = root / 'outside.png';outside.write_bytes(png())
                if kind == 'symlink':
                    (src / 'a.png').symlink_to(outside)
                else:
                    os.link(outside, src / 'a.png')
                with self.assertRaises(ValueError):
                    p.curate_public_screenshots(src, out)
                self.assertEqual(list(out.iterdir()), [])

    def test_png_count_is_bounded_and_partial_images_are_not_published(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp);src = root / 'exported';src.mkdir();out = root / 'curated';out.mkdir()
            for i in range(9):
                (src / ('%02d.png' % i)).write_bytes(png())
            with self.assertRaises(ValueError):
                p.curate_public_screenshots(src, out)
            self.assertEqual(list(out.iterdir()), [])
