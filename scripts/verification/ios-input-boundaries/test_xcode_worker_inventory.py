"""Filesystem/compiler-command controls, not actual Xcode or process acceptance."""
import hashlib
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

import xcode_worker_inventory as inventory


class InventoryControls(unittest.TestCase):
    def test_discovery_records_actual_paths_without_guessing_or_following_aliases(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve(); path = root / 'unexpected/component/DTServiceHub'
            path.parent.mkdir(parents=True); path.write_bytes(b'public fixture'); path.chmod(0o700)
            (root / 'alias').symlink_to(path.parent, target_is_directory=True)
            self.assertEqual(inventory.discover(root), [path])

    def test_missing_redirected_nonexecutable_and_excessive_candidates_reject(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve()
            with self.assertRaises(ValueError): inventory.discover(root)
            path = root / 'DTServiceHub'; path.write_bytes(b'fixture'); path.chmod(0o600)
            with self.assertRaises(ValueError): inventory.discover(root)
            path.chmod(0o700)
            with patch.object(inventory, 'MAX_COMPONENTS', 0), self.assertRaises(ValueError): inventory.discover(root)
            with patch.object(inventory, 'MAX_ENTRIES', 0), self.assertRaises(ValueError): inventory.discover(root)
            alias = root / 'redirected'; alias.symlink_to(root, target_is_directory=True)
            with self.assertRaises(ValueError): inventory.discover(alias)

    def test_component_symlink_and_oversize_input_reject(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve(); target = root / 'file'; target.write_bytes(b'public'); target.chmod(0o700)
            path = root / 'DTServiceHub'; path.symlink_to(target)
            with self.assertRaises(ValueError): inventory.discover(root)
            path.unlink(); path.write_bytes(b'public'); path.chmod(0o700)
            with patch.object(inventory, 'MAX_BYTES', 1), self.assertRaises(ValueError): inventory.discover(root)

    def test_signature_commands_and_hash_are_actual_and_grant_no_cleanup_permission(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve(); path = root / 'DTServiceHub'; path.write_bytes(b'fixture'); path.chmod(0o700)
            run = Mock(side_effect=[subprocess.CompletedProcess([], 0, b'', b''),
                                   subprocess.CompletedProcess([], 0, b'', b'Identifier=com.apple.fixture\nAuthority=Apple fixture\nOther=omit\n')])
            row = inventory.inspect_component(root, path, run)
            self.assertEqual(row['sha256'], hashlib.sha256(b'fixture').hexdigest())
            self.assertFalse(row['cleanup_authorized'])
            self.assertEqual(row['signature_fields'], [('Identifier', 'com.apple.fixture'), ('Authority', 'Apple fixture')])
            self.assertEqual(run.call_args_list[0].args[0], ['/usr/bin/codesign', '--verify', '--strict', str(path)])
            self.assertEqual(run.call_args_list[1].args[0], ['/usr/bin/codesign', '-d', '--verbose=4', str(path)])
            self.assertTrue(all(call.kwargs['timeout'] == 45 for call in run.call_args_list))

    def test_signature_failure_is_retained_not_reclassified_as_success(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve(); path = root / 'DTServiceHub'; path.write_bytes(b'fixture')
            run = Mock(return_value=subprocess.CompletedProcess([], 1, b'', b''))
            row = inventory.inspect_component(root, path, run)
            self.assertEqual(row['signature_verify_exit'], 1)
            self.assertFalse(row['cleanup_authorized'])

    def test_component_mutation_and_oversized_signature_output_reject(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve(); path = root / 'DTServiceHub'; path.write_bytes(b'fixture')
            def mutate(*args, **kwargs):
                path.write_bytes(b'changed'); return subprocess.CompletedProcess([], 0, b'', b'')
            with self.assertRaises(ValueError): inventory.inspect_component(root, path, mutate)
            run = Mock(return_value=subprocess.CompletedProcess([], 0, b'', b'x' * 65537))
            with self.assertRaises(ValueError): inventory.inspect_component(root, path, run)

    def test_foreign_and_redirected_components_reject_before_hash_or_signature(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve(); app = root / 'Xcode.app'; app.mkdir()
            path = root / 'DTServiceHub'; path.write_bytes(b'fixture')
            run = Mock()
            with self.assertRaises(ValueError): inventory.inspect_component(app, path, run)
            alias = app / 'DTServiceHub'; alias.symlink_to(path)
            with self.assertRaises(ValueError): inventory.inspect_component(app, alias, run)
            run.assert_not_called()


if __name__ == '__main__':
    unittest.main()
