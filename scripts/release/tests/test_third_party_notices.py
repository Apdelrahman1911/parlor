from __future__ import annotations

import copy
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import stat
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
import warnings
import zipfile
import zlib


ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / "scripts/verification/third_party_notices.py"
SPEC = importlib.util.spec_from_file_location("third_party_notices", SCRIPT)
notices = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(notices)


class WriteOnly(io.BytesIO):
    """Forces real ZIP data descriptors rather than seek-back local headers."""
    def seek(self, *args):
        raise io.UnsupportedOperation("synthetic nonseekable output")


class ThirdPartyNoticesTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="parlor-notice-test-")
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name).resolve()
        self.root = self.directory / "source"
        self.root.mkdir()
        for relative in (notices.MANIFEST_PATH, notices.CATALOG_PATH, notices.BUILD_PATH):
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((ROOT / relative).read_bytes())
        self.legal = self.root / notices.RESOURCE_DIRECTORY
        shutil.copytree(ROOT / notices.RESOURCE_DIRECTORY, self.legal)
        self.manifest_path = self.root / notices.MANIFEST_PATH
        self.manifest = json.loads(self.manifest_path.read_bytes())
        self.source_files = {name: (self.legal / name).read_bytes() for name in notices.NOTICE_NAMES}

    def save_manifest(self, value=None):
        self.manifest_path.write_text(json.dumps(self.manifest if value is None else value), encoding="utf-8")

    def aab(self, changes=None, extra=None, prefix=None, compression=zipfile.ZIP_DEFLATED,
            write_only=False):
        path = self.directory / "synthetic.aab"
        output = WriteOnly() if write_only else path
        prefix = notices.AAB_PREFIX if prefix is None else prefix
        resources = dict(self.source_files)
        resources.update(changes or {})
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)  # Deliberate duplicate ZIP fixtures.
            with zipfile.ZipFile(output, "w", compression=compression) as archive:
                archive.writestr("BundleConfig.pb", b"synthetic; not Android runtime evidence")
                for name, raw in sorted(resources.items()):
                    if raw is not None:
                        archive.writestr(prefix + "/" + name, raw)
                for name, raw in extra or []:
                    archive.writestr(name, raw)
        if write_only:
            path.write_bytes(output.getvalue())
        return path

    def app(self, prefix=None):
        path = self.directory / "Synthetic.app"
        target = path / (notices.APP_PREFIX if prefix is None else prefix)
        target.mkdir(parents=True)
        for name, raw in self.source_files.items():
            (target / name).write_bytes(raw)
        (path / "Info.plist").write_bytes(b"synthetic non-notice metadata; not an iOS runtime fixture")
        return path

    def mutate_zip_headers(self, path, member, *, flags=None, logical_bytes=None):
        with zipfile.ZipFile(path) as archive:
            item = archive.getinfo(member)
            central = archive.start_dir
        raw = bytearray(path.read_bytes())
        cursor = central
        while raw[cursor:cursor + 4] == b"PK\x01\x02":
            name_size, extra_size, comment_size = struct.unpack_from("<3H", raw, cursor + 28)
            name = bytes(raw[cursor + 46:cursor + 46 + name_size]).decode()
            if name == member:
                if flags is not None:
                    struct.pack_into("<H", raw, cursor + 8, flags)
                    struct.pack_into("<H", raw, item.header_offset + 6, flags)
                if logical_bytes is not None:
                    crc = zlib.crc32(logical_bytes) & 0xffffffff
                    struct.pack_into("<L", raw, cursor + 16, crc)
                    struct.pack_into("<L", raw, cursor + 24, len(logical_bytes))
                    struct.pack_into("<L", raw, item.header_offset + 14, crc)
                    struct.pack_into("<L", raw, item.header_offset + 22, len(logical_bytes))
                path.write_bytes(raw)
                return
            cursor += 46 + name_size + extra_size + comment_size
        self.fail("Synthetic central entry not found")

    def test_actual_source_set_is_26_complete_hash_bound_resources_not_a_package_pass(self):
        receipt = notices.verify(ROOT)
        self.assertEqual(receipt["status"], "PASS")
        self.assertIsNone(receipt["package"])
        self.assertEqual(receipt["source"]["resource_count"], 26)
        self.assertEqual({row["name"] for row in receipt["source"]["files"]}, notices.NOTICE_NAMES)
        self.assertEqual(sum(len(raw) for name, raw in self.source_files.items() if name != "INDEX.txt"), 94484)

    def test_verbatim_crlf_and_notice_provenance_are_preserved(self):
        raw = self.source_files["SLF4J-2.0.16-LICENSE.txt"]
        self.assertEqual(hashlib.sha256(raw).hexdigest(), "4e7f90c86ab51278228bce153122f1d8df30149d13ce9ef524c8444a84c32dcc")
        self.assertIn(b"\r\n", raw)
        self.assertNotEqual(raw, raw.replace(b"\r\n", b"\n"))
        self.assertEqual(len(self.manifest["files"]), 26)
        self.assertTrue(all("/Users/" not in json.dumps(row) for row in self.manifest["files"]))
        entries = {row["name"]: row for row in self.manifest["files"]}
        self.assertEqual(entries["Kotlin-libbacktrace-LICENSE.txt"]["classification"], "NATIVE_INPUT_LICENSE")
        self.assertEqual(entries["Kotlin-Unicode-LICENSE.txt"]["classification"], "STDLIB_INPUT_LICENSE")
        self.assertEqual(entries["libpng-LICENSE.txt"]["classification"], "OPTIONAL_BINARY_ACKNOWLEDGEMENT")
        self.assertEqual(entries["zlib-LICENSE.txt"]["classification"], "OPTIONAL_BINARY_ACKNOWLEDGEMENT")

    def test_valid_stored_and_deflated_aab_matches_every_exact_resource(self):
        for method in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
            with self.subTest(method=method):
                receipt = notices.verify(self.root, self.aab(compression=method))
                packaged = receipt["package"]
                self.assertEqual(packaged["status"], "PASS")
                self.assertEqual(packaged["format"], "android-aab")
                self.assertEqual(packaged["resource_count"], 26)
                self.assertEqual(packaged["exact_byte_policy"], notices.EXACT_BYTE_POLICY)
                for row in packaged["files"]:
                    self.assertEqual(row["path"], notices.AAB_PREFIX + "/" + row["name"])
                    self.assertEqual(row["sha256"], hashlib.sha256(self.source_files[row["name"]]).hexdigest())

    def test_real_zip_data_descriptors_are_accepted(self):
        path = self.aab(write_only=True)
        with zipfile.ZipFile(path) as archive:
            self.assertTrue(all(item.flag_bits & 8 for item in archive.infolist()))
        self.assertEqual(notices.verify(self.root, path)["status"], "PASS")

    def test_deflate_compression_level_flags_are_not_mistaken_for_encryption(self):
        path = self.aab()
        member = notices.AAB_PREFIX + "/INDEX.txt"
        self.mutate_zip_headers(path, member, flags=2)
        self.assertEqual(notices.verify(self.root, path)["status"], "PASS")

    def test_valid_app_checks_complete_namespace_and_does_not_read_non_notice_contents(self):
        path = self.app()
        with mock.patch.object(notices, "read_file", wraps=notices.read_file) as read:
            packaged = notices.verify_app(path, self.source_files)
        self.assertEqual(packaged["status"], "PASS")
        self.assertEqual(packaged["format"], "ios-app")
        self.assertEqual({call.args[1] for call in read.call_args_list}, notices.NOTICE_NAMES)
        self.assertTrue(all(row["path"].startswith(notices.APP_PREFIX + "/") for row in packaged["files"]))

    def test_manifest_rejects_closed_schema_identity_count_and_duplicate_mutations(self):
        mutations = [
            lambda m: m.update(schema_version=True), lambda m: m.update(schema_version=2),
            lambda m: m.update(scope="ALL_LEGAL_WORK_APPROVED"), lambda m: m.update(extra="unexpected"),
            lambda m: m.update(resource_directory="../../private"), lambda m: m.update(resource_namespace="other"),
            lambda m: m.update(resource_count=25), lambda m: m.update(resource_count=True),
            lambda m: m["files"].pop(), lambda m: m["files"].append(copy.deepcopy(m["files"][0])),
            lambda m: m["files"].__setitem__(1, copy.deepcopy(m["files"][0])),
            lambda m: m["files"][0].update(name="../APACHE-2.0.txt"),
            lambda m: m["files"][0].update(name="/APACHE-2.0.txt"),
            lambda m: m["files"][0].update(name="a\\APACHE-2.0.txt"),
            lambda m: m["files"][0].update(name="APACHE-2.0.txt\n"),
            lambda m: m["files"][0].update(name="apache-2.0.txt"),
            lambda m: m["files"][0].update(bytes=True), lambda m: m["files"][0].update(bytes=0),
            lambda m: m["files"][0].update(bytes=notices.MAX_NOTICE_BYTES + 1),
            lambda m: m["files"][0].update(sha256="not a digest"),
            lambda m: m["files"][0].update(kind="index"),
            lambda m: m["files"][0].update(classification="ALL_OBJECTS_LINKED"),
            lambda m: m["files"][0].update(classification={}),
            lambda m: m["files"][0].update(components=[]),
            lambda m: m["files"][0].update(platforms=["ios", "ios"]),
            lambda m: m["files"][0].update(platforms=[[]]),
            lambda m: m["files"][0].update(applicability="private\ntext"),
            lambda m: m["upstream_versions"].update(kotlin_native="0.1"),
            lambda m: m["upstream_versions"].update(kotlin_commit="main"),
            lambda m: m["catalog"].update(path="other.toml"),
        ]
        for number, mutation in enumerate(mutations):
            with self.subTest(mutation=number):
                value = copy.deepcopy(self.manifest)
                mutation(value)
                with self.assertRaises(ValueError):
                    notices.read_manifest(json.dumps(value).encode())

    def test_manifest_rejects_malformed_duplicate_keys_and_nonfinite_json(self):
        raw = self.manifest_path.read_bytes()
        for value in (b"[]", b"null", b"{", b"\xff", raw.replace(b'"schema_version": 1', b'"schema_version": 1, "schema_version": 1'),
                      raw.replace(b'"schema_version": 1', b'"schema_version": NaN')):
            with self.subTest(value=value[:35]), self.assertRaises(ValueError):
                notices.read_manifest(value)

    def test_provenance_rejects_private_urls_unsafe_members_and_forged_hashes(self):
        entry = next(row for row in self.manifest["files"] if row["name"] == "BouncyCastle-1.85-LICENSE.md")
        mutations = [lambda p: p.update(url="file:///Users/private/key"),
                     lambda p: p.update(url="https://user:secret@repo.maven.apache.org/license"),
                     lambda p: p.update(url="https://unreviewed.invalid/license"),
                     lambda p: p.update(url="https://repo.maven.apache.org:444/license"),
                     lambda p: p.update(source_archive="/Users/private/.gradle/cache"),
                     lambda p: p.update(source_sha256="a" * 64), lambda p: p.update(source_bytes=1),
                     lambda p: p.update(archive_sha256="main"), lambda p: p.update(archive_bytes=True),
                     lambda p: p.update(member="../LICENSE"), lambda p: p.update(method="not reviewed")]
        for number, mutation in enumerate(mutations):
            with self.subTest(mutation=number):
                value = copy.deepcopy(entry)
                mutation(value["provenance"])
                with self.assertRaises(ValueError):
                    notices.validate_provenance(value)

    def test_source_lines_provenance_requires_real_ordered_integer_range(self):
        original = next(row for row in self.manifest["files"] if row["name"] == "HarfBuzz-hb-ucd-ISC-LICENSE.txt")
        for value in ([15, 1], [True, 15], [0, 15], [1], "1-15"):
            with self.subTest(value=value):
                entry = copy.deepcopy(original)
                entry["provenance"]["line_range"] = value
                with self.assertRaises(ValueError):
                    notices.validate_provenance(entry)

    def test_missing_and_extra_source_files_fail(self):
        missing = self.legal / "INDEX.txt"
        missing.unlink()
        with self.assertRaisesRegex(ValueError, "notice set"):
            notices.verify(self.root)
        missing.write_bytes(self.source_files["INDEX.txt"])
        (self.legal / "unreviewed.txt").write_bytes(b"unexpected")
        with self.assertRaisesRegex(ValueError, "notice set"):
            notices.verify(self.root)

    def test_source_tampering_and_line_ending_normalization_fail(self):
        path = self.legal / "SLF4J-2.0.16-LICENSE.txt"
        for raw in (self.source_files[path.name].replace(b"\r\n", b"\n"), b"x" * len(self.source_files[path.name])):
            path.write_bytes(raw)
            with self.assertRaisesRegex(ValueError, "bytes differ"):
                notices.verify(self.root)

    def test_index_required_sentences_and_complete_file_list_cannot_be_removed_by_rehashing(self):
        original = self.source_files["INDEX.txt"]
        for token in (*notices.REQUIRED_ACKNOWLEDGEMENTS, "Expat-COPYING.txt"):
            with self.subTest(token=token):
                raw = original.replace(token.encode(), b"omitted")
                (self.legal / "INDEX.txt").write_bytes(raw)
                value = copy.deepcopy(self.manifest)
                row = next(row for row in value["files"] if row["name"] == "INDEX.txt")
                row.update(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
                self.save_manifest(value)
                with self.assertRaisesRegex(ValueError, "index acknowledgement|Index omits"):
                    notices.verify(self.root)

    def test_catalog_bytes_pins_and_namespace_declaration_are_bound(self):
        path = self.root / notices.CATALOG_PATH
        original = path.read_bytes()
        path.write_bytes(original + b"\n# unreviewed graph change\n")
        with self.assertRaisesRegex(ValueError, "Catalog changed"):
            notices.verify(self.root)
        path.write_bytes(original)
        self.manifest["catalog"]["pins"]["ktor"] = "0.0.1"
        self.save_manifest()
        with self.assertRaisesRegex(ValueError, "Catalog changed"):
            notices.verify(self.root)
        self.manifest_path.write_bytes((ROOT / notices.MANIFEST_PATH).read_bytes())
        build = self.root / notices.BUILD_PATH
        build.write_bytes(build.read_bytes().replace(b'packageOfResClass = "com.parlor.app.resources"', b'packageOfResClass = "other.namespace"'))
        with self.assertRaisesRegex(ValueError, "namespace declaration"):
            notices.verify(self.root)

    def test_duplicate_catalog_version_declarations_are_not_silently_last_wins(self):
        with self.assertRaises(ValueError):
            notices.catalog_versions(b'[versions]\nkotlin="1"\nkotlin="2"\n')
        with self.assertRaises(ValueError):
            notices.catalog_versions(b'[versions]\nkotlin="1"\n[versions]\nktor="1"\n')

    def test_only_catalog_line_endings_are_canonicalized_and_raw_identity_remains_visible(self):
        path = self.root / notices.CATALOG_PATH
        original = path.read_bytes()
        self.assertNotIn(b"\r", original)
        path.write_bytes(original.replace(b"\n", b"\r\n"))
        receipt = notices.verify(self.root)["source"]
        self.assertEqual(receipt["catalog_hash_policy"], "CRLF_TO_LF")
        self.assertEqual(receipt["catalog_sha256"], hashlib.sha256(original).hexdigest())
        self.assertEqual(receipt["catalog_raw_sha256"], hashlib.sha256(path.read_bytes()).hexdigest())
        self.assertNotEqual(receipt["catalog_sha256"], receipt["catalog_raw_sha256"])

    def test_symlink_source_root_ancestor_and_file_fail_without_following(self):
        link = self.directory / "source-link"
        link.symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(OSError):
            notices.verify(link)
        path = self.legal / "INDEX.txt"
        path.unlink()
        path.symlink_to(ROOT / notices.RESOURCE_DIRECTORY / "INDEX.txt")
        with self.assertRaises(ValueError):
            notices.verify(self.root)
        path.unlink()
        path.write_bytes(self.source_files["INDEX.txt"])
        real = self.legal.parent / "real-legal"
        self.legal.rename(real)
        self.legal.symlink_to(real, target_is_directory=True)
        with self.assertRaises(OSError):
            notices.verify(self.root)

    def test_nonregular_source_and_manifest_do_not_block_on_fifo_open(self):
        path = self.legal / "INDEX.txt"
        path.unlink()
        os.mkfifo(path)
        with self.assertRaises(ValueError):
            notices.verify(self.root)
        self.manifest_path.unlink()
        os.mkfifo(self.manifest_path)
        with self.assertRaises(ValueError):
            notices.verify(self.root)

    def test_source_input_size_and_aggregate_limits_are_enforced(self):
        with mock.patch.object(notices, "MAX_MANIFEST_BYTES", 10), self.assertRaises(ValueError):
            notices.verify(self.root)
        with mock.patch.object(notices, "MAX_TOTAL_NOTICE_BYTES", 10), self.assertRaises(ValueError):
            notices.verify(self.root)
        (self.legal / "INDEX.txt").write_bytes(b"x" * (notices.MAX_NOTICE_BYTES + 1))
        with self.assertRaises(ValueError):
            notices.verify(self.root)

    def test_aab_missing_extra_wrong_namespace_and_wrong_root_fail(self):
        scenarios = [dict(changes={"INDEX.txt": None}),
                     dict(extra=[(notices.AAB_PREFIX + "/unexpected.txt", b"extra")]),
                     dict(prefix=notices.AAB_PREFIX.replace(notices.NAMESPACE, "other.namespace")),
                     dict(prefix="not-shipped/" + notices.RESOURCE_SUFFIX),
                     dict(extra=[("other/" + notices.RESOURCE_SUFFIX + "/INDEX.txt", self.source_files["INDEX.txt"])])]
        for scenario in scenarios:
            with self.subTest(scenario=str(scenario)[:150]), self.assertRaises(ValueError):
                notices.verify(self.root, self.aab(**scenario))

    def test_aab_duplicate_case_alias_traversal_and_file_parent_collisions_fail(self):
        extras = [[(notices.AAB_PREFIX + "/INDEX.txt", self.source_files["INDEX.txt"])],
                  [("Other/a", b"1"), ("other/b", b"2")], [("../outside", b"1")],
                  [("/absolute", b"1")], [("a\\b", b"1")], [("a/./b", b"1")],
                  [("a//b", b"1")], [("a", b"1"), ("a/b", b"2")],
                  [("a/\ncontrol", b"1")], [("C:/drive", b"1")]]
        for extra in extras:
            with self.subTest(extra=extra), self.assertRaises(ValueError):
                notices.verify(self.root, self.aab(extra=extra))

    def test_zip_symlink_and_zip64_extra_fail(self):
        symbolic = zipfile.ZipInfo("symbolic")
        symbolic.create_system = 3
        symbolic.external_attr = (stat.S_IFLNK | 0o777) << 16
        zip64 = zipfile.ZipInfo("zip64")
        zip64.extra = struct.pack("<HH", 1, 0)
        for entry in (symbolic, zip64):
            with self.subTest(entry=entry.filename), self.assertRaises(ValueError):
                notices.verify(self.root, self.aab(extra=[(entry, b"target")]))

    def test_zip_encryption_and_nul_filename_fail(self):
        path = self.aab()
        self.mutate_zip_headers(path, notices.AAB_PREFIX + "/INDEX.txt", flags=1)
        with self.assertRaisesRegex(ValueError, "Encrypted"):
            notices.verify(self.root, path)
        path = self.aab(extra=[("badXY.txt", b"fixture")])
        path.write_bytes(path.read_bytes().replace(b"badXY.txt", b"bad\x00Y.txt"))
        with self.assertRaisesRegex(ValueError, "Truncated ZIP member name"):
            notices.verify(self.root, path)

    def test_package_tamper_fails_even_if_a_digest_function_is_made_to_collide(self):
        path = self.aab(changes={"INDEX.txt": b"x" * len(self.source_files["INDEX.txt"])})
        # Direct byte equality is deliberately stronger than trusting only a
        # test-double digest or the manifest's claimed hash.
        with mock.patch.object(notices.hashlib, "sha256") as digest:
            digest.return_value.hexdigest.return_value = "0" * 64
            with self.assertRaisesRegex(ValueError, "bytes differ"):
                notices.verify_aab(path, self.source_files)

    def test_crc_corruption_and_truncated_aab_fail(self):
        path = self.aab(compression=zipfile.ZIP_STORED)
        with zipfile.ZipFile(path) as archive:
            item = archive.getinfo(notices.AAB_PREFIX + "/INDEX.txt")
        raw = bytearray(path.read_bytes())
        name_size, extra_size = struct.unpack_from("<2H", raw, item.header_offset + 26)
        raw[item.header_offset + 30 + name_size + extra_size] ^= 1
        path.write_bytes(raw)
        with self.assertRaisesRegex(ValueError, "CRC mismatch"):
            notices.verify(self.root, path)
        path = self.aab()
        path.write_bytes(path.read_bytes()[:-1])
        with self.assertRaises(ValueError):
            notices.verify(self.root, path)

    def test_forged_short_uncompressed_length_cannot_hide_extra_decompressed_bytes(self):
        expected = self.source_files["INDEX.txt"]
        path = self.aab(changes={"INDEX.txt": expected + b"unexpected trailing text"})
        member = notices.AAB_PREFIX + "/INDEX.txt"
        self.mutate_zip_headers(path, member, logical_bytes=expected)
        # Python's normal reader trusts the shortened central file_size. Our
        # bounded raw DEFLATE check must not mistake that truncation for proof.
        with zipfile.ZipFile(path) as archive:
            self.assertEqual(archive.read(member), expected)
        with self.assertRaisesRegex(ValueError, "length or CRC mismatch"):
            notices.verify(self.root, path)

    def test_compressed_notice_bomb_and_manifest_directory_bounds_fail(self):
        path = self.aab(changes={"INDEX.txt": b"A" * (notices.MAX_NOTICE_BYTES + 1)})
        with self.assertRaisesRegex(ValueError, "ZIP notice size"):
            notices.verify(self.root, path)
        path = self.aab()
        with mock.patch.object(notices, "MAX_DIRECTORY_BYTES", 16), self.assertRaises(ValueError):
            notices.verify(self.root, path)
        with mock.patch.object(notices, "MAX_ARCHIVE_BYTES", 32), self.assertRaises(ValueError):
            notices.verify(self.root, path)
        with mock.patch.object(notices, "MAX_PACKAGE_ENTRIES", 10), self.assertRaises(ValueError):
            notices.verify(self.root, path)

    def test_zip_eocd_count_lie_is_rejected_before_zipinfo_allocation(self):
        path = self.aab()
        raw = bytearray(path.read_bytes())
        offset = raw.rfind(b"PK\x05\x06")
        struct.pack_into("<HH", raw, offset + 8, 1, 1)
        path.write_bytes(raw)
        with mock.patch.object(notices.zipfile, "ZipFile", side_effect=AssertionError("Must not allocate")):
            with self.assertRaisesRegex(ValueError, "count mismatch"):
                notices.verify(self.root, path)

    def test_app_missing_extra_wrong_namespace_symlink_and_fifo_fail(self):
        path = self.app()
        target = path / notices.APP_PREFIX / "INDEX.txt"
        target.unlink()
        with self.assertRaisesRegex(ValueError, "incomplete"):
            notices.verify(self.root, path)
        target.write_bytes(self.source_files["INDEX.txt"])
        extra = target.parent / "unreviewed.txt"
        extra.write_bytes(b"extra")
        with self.assertRaisesRegex(ValueError, "Unexpected packaged"):
            notices.verify(self.root, path)
        extra.unlink()
        target.unlink()
        target.symlink_to(self.legal / "INDEX.txt")
        with self.assertRaisesRegex(ValueError, "Symlink or nonregular"):
            notices.verify(self.root, path)
        target.unlink()
        os.mkfifo(target)
        with self.assertRaisesRegex(ValueError, "Symlink or nonregular"):
            notices.verify(self.root, path)
        target.unlink()
        target.write_bytes(self.source_files["INDEX.txt"])
        target.parent.rename(target.parent.with_name("not-legal"))
        with self.assertRaisesRegex(ValueError, "incomplete"):
            notices.verify(self.root, path)

    def test_app_root_symlink_wrong_prefix_and_depth_or_entry_ceiling_fail(self):
        path = self.app(prefix="wrong/" + notices.RESOURCE_SUFFIX)
        with self.assertRaisesRegex(ValueError, "unreviewed package root"):
            notices.verify(self.root, path)
        link = self.directory / "Link.app"
        link.symlink_to(path, target_is_directory=True)
        with self.assertRaises(OSError):
            notices.verify(self.root, link)
        with mock.patch.object(notices, "MAX_PACKAGE_DEPTH", 2), self.assertRaises(ValueError):
            notices.verify_app(path, self.source_files)
        with mock.patch.object(notices, "MAX_PACKAGE_ENTRIES", 1), self.assertRaises(ValueError):
            notices.verify_app(path, self.source_files)

    def test_input_replacement_during_read_fails(self):
        name = "index-fixture"
        path = self.directory / name
        path.write_bytes(b"before")
        with notices.directory(self.directory) as parent:
            with self.assertRaisesRegex(ValueError, "Input changed"):
                with notices.regular_file(parent, name, 100) as (stream, _):
                    self.assertEqual(stream.read(), b"before")
                    replacement = path.with_name("replacement")
                    replacement.write_bytes(b"before")
                    replacement.replace(path)

    def test_cli_json_success_failure_and_unexecuted_package_are_unambiguous(self):
        for extra, expected in (([], 0), (["--package", str(self.aab())], 0),
                                (["--package", str(self.directory / "missing.aab")], 1),
                                (["--package", str(self.directory / "wrong.zip")], 1),
                                (["--not-a-real-option"], 1)):
            with self.subTest(extra=extra):
                result = subprocess.run([sys.executable, "-B", str(SCRIPT), "--root", str(self.root), "--json", *extra],
                                        stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20, check=False)
                self.assertEqual(result.returncode, expected, result.stderr.decode())
                self.assertLess(len(result.stdout), 64 * 1024)
                self.assertEqual(result.stdout.count(b"\n"), 1)
                receipt = json.loads(result.stdout)
                self.assertEqual(receipt["status"], "PASS" if expected == 0 else "FAIL")
                if expected or not extra:
                    self.assertIsNone(receipt["package"])
                else:
                    self.assertEqual(receipt["package"]["status"], "PASS")
                self.assertNotIn(str(self.directory), result.stdout.decode())


if __name__ == "__main__":
    unittest.main()
