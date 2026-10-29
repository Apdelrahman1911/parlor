"""Post-build input inventory controls; no native build, device, or process signals."""
import hashlib
from pathlib import Path
import tempfile
import unittest

import copied_sources as copies


class CopiedInputBoundaryTests(unittest.TestCase):
    def fixture(self, root):
        manifest = []
        for name in ('build.gradle.kts', 'settings.gradle.kts', 'composeApp/build.gradle.kts',
                     'composeApp/src/commonMain/kotlin/Public.kt'):
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            raw = ('fixture: ' + name).encode()
            path.write_bytes(raw)
            manifest.append(dict(path=name, copied_sha256=hashlib.sha256(raw).hexdigest()))
        return manifest

    def test_hidden_and_nested_output_names_never_hide_unregistered_kotlin_inputs(self):
        for relative in (
            'composeApp/src/commonMain/kotlin/.Unregistered.kt',
            'composeApp/src/commonMain/kotlin/.hidden/Unregistered.kt',
            'composeApp/src/commonMain/kotlin/build/Unregistered.kt',
            'composeApp/src/commonMain/kotlin/.gradle/Unregistered.kt',
            'composeApp/src/commonMain/kotlin/.kotlin/Unregistered.kt',
            'composeApp/src/commonMain/kotlin/__pycache__/Unregistered.kt',
        ):
            with self.subTest(relative=relative), tempfile.TemporaryDirectory() as raw:
                root = Path(raw).resolve()
                before = self.fixture(root)
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('unregistered compilation input')
                result = copies.inspect_copied_inputs_after_build(root, before)
                self.assertFalse(result['unchanged'])
                self.assertIn(relative, result['unexpected_build_inputs'])
                self.assertEqual(result['missing_build_inputs'], [])

    def test_declared_project_outputs_are_pruned_but_generated_symlinks_fail_closed(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw).resolve()
            before = self.fixture(root)
            output = root / 'composeApp/build'
            output.mkdir()
            (output / 'Generated.kt').write_text('legitimate declared output')
            self.assertTrue(copies.inspect_copied_inputs_after_build(root, before)['unchanged'])
            (output / 'Generated.kt').unlink()
            output.rmdir()
            external = root / 'outside'
            external.mkdir()
            output.symlink_to(external, target_is_directory=True)
            result = copies.inspect_copied_inputs_after_build(root, before)
            self.assertFalse(result['unchanged'])
            self.assertIn('composeApp/build', result['unexpected_source_symlinks'])

    def test_hidden_source_directory_symlink_is_never_pruned_as_an_output(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw).resolve()
            before = self.fixture(root)
            external = root / 'outside'
            external.mkdir()
            (external / 'Unregistered.kt').write_text('redirected input')
            link = root / 'composeApp/src/commonMain/kotlin/.hidden'
            link.symlink_to(external, target_is_directory=True)
            result = copies.inspect_copied_inputs_after_build(root, before)
            self.assertFalse(result['unchanged'])
            self.assertIn(str(link.relative_to(root)), result['unexpected_source_symlinks'])


if __name__ == '__main__':
    unittest.main()
