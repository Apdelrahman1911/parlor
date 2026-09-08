"""Synthetic classpath/type/task-state fixtures; no Gradle/JDK/application runs."""
import copy
import os
from pathlib import Path
import tempfile
import unittest

from classpath_validation import JAVA_OUTPUTS, REQUIRED_OUTPUTS, TEST_TASK, kind, validate_classpath


class ClasspathValidationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='parlor-cp-fixture-')
        self.addCleanup(self.temporary.cleanup)
        self.repo = Path(self.temporary.name)
        self.paths = [self.repo / path for path in REQUIRED_OUTPUTS]
        for path in self.paths:
            path.mkdir(parents=True)
        self.paths += [self.repo / path for path in JAVA_OUTPUTS]
        self.jar = self.repo / 'dependency.jar'
        self.jar.write_bytes(b'Existence/type fixture, not an executed Java archive')
        self.paths.append(self.jar)
        self.cp = os.pathsep.join(str(path) for path in self.paths)
        self.state = self.capture()

    def capture(self):
        return {
            'schema': 1, 'captured_for': TEST_TASK, 'classpath': self.cp,
            'entries': [{'path': str(path), 'kind': kind(path), 'exists': path.exists()}
                        for path in self.paths],
            'java_compile_tasks': [
                {'path': task, 'destination': str(self.repo / path),
                 'executed': True, 'no_source': True, 'skipped': True,
                 'source_empty': True, 'failure': False, 'skip_message': 'NO-SOURCE'}
                for path, task in JAVA_OUTPUTS.items()
            ],
        }

    def check(self, state=None):
        return validate_classpath(self.cp, self.state if state is None else state, self.repo)

    def test_only_two_known_no_source_java_directories_may_be_absent(self):
        result = self.check()
        self.assertEqual([str(self.repo / path) for path in JAVA_OUTPUTS],
                         result['allowed_missing_no_source_java_outputs'])
        self.assertEqual(self.cp, self.state['classpath'])
        self.assertTrue(result['classpath_preserved_unchanged'])
        self.assertEqual(1, result['existing_jar_inputs'])

    def test_missing_dependency_jar_cannot_pass_even_if_absent_at_capture(self):
        self.jar.unlink()
        with self.assertRaisesRegex(ValueError, 'Missing required/unknown'):
            self.check(self.capture())

    def test_dependency_jar_disappearing_after_capture_is_rejected(self):
        self.jar.unlink()
        with self.assertRaisesRegex(ValueError, 'disappeared or changed type'):
            self.check()

    def test_each_kotlin_and_resource_directory_is_required(self):
        for relative in REQUIRED_OUTPUTS:
            path = self.repo / relative
            path.rmdir()
            with self.subTest(path=str(relative)), self.assertRaisesRegex(ValueError, 'Missing required/unknown'):
                self.check(self.capture())
            path.mkdir()

    def test_unknown_missing_output_is_not_treated_as_optional_java(self):
        self.paths.append(self.repo / 'another-module/build/classes/java/desktopMain')
        self.cp = os.pathsep.join(map(str, self.paths))
        with self.assertRaisesRegex(ValueError, 'Missing required/unknown'):
            self.check(self.capture())

    def test_no_source_task_evidence_requires_all_independent_guards(self):
        changes = {
            'executed': False, 'no_source': False, 'skipped': False,
            'source_empty': False, 'failure': True, 'skip_message': 'SKIPPED',
            'destination': str(self.repo / 'wrong-destination'),
        }
        for field, value in changes.items():
            state = copy.deepcopy(self.state)
            state['java_compile_tasks'][0][field] = value
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, 'NO-SOURCE evidence'):
                self.check(state)

    def test_omitted_java_compile_state_cannot_authorize_missing_output(self):
        state = copy.deepcopy(self.state)
        state['java_compile_tasks'].pop()
        with self.assertRaisesRegex(ValueError, 'task identities'):
            self.check(state)

    def test_duplicate_java_task_state_is_rejected(self):
        state = copy.deepcopy(self.state)
        state['java_compile_tasks'].append(state['java_compile_tasks'][0])
        with self.assertRaisesRegex(ValueError, 'task identities'):
            self.check(state)

    def test_jar_named_directory_is_not_a_valid_jar_input(self):
        self.jar.unlink()
        self.jar.mkdir()
        with self.assertRaisesRegex(ValueError, 'JAR input is not a regular file'):
            self.check(self.capture())

    def test_symlink_jar_is_rejected_even_when_target_exists(self):
        self.jar.unlink()
        target = self.repo / 'other.jar'
        target.write_bytes(b'fixture')
        self.jar.symlink_to(target)
        with self.assertRaisesRegex(ValueError, 'Unsafe/unsupported'):
            self.check(self.capture())

    def test_required_classes_cannot_be_removed_from_classpath(self):
        self.paths.pop(0)
        self.cp = os.pathsep.join(map(str, self.paths))
        with self.assertRaisesRegex(ValueError, 'missing from classpath'):
            self.check(self.capture())

    def test_order_and_path_capture_must_match_exact_unchanged_classpath(self):
        state = copy.deepcopy(self.state)
        state['entries'].reverse()
        with self.assertRaisesRegex(ValueError, 'order/content mismatch'):
            self.check(state)
        state = copy.deepcopy(self.state)
        state['classpath'] += os.pathsep + '/unrecorded'
        with self.assertRaisesRegex(ValueError, 'capture mismatch'):
            self.check(state)

    def test_empty_relative_duplicate_and_control_character_paths_fail(self):
        for cp in ('', 'relative.jar', self.cp + os.pathsep,
                   self.cp + os.pathsep + str(self.jar), self.cp + '\n'):
            with self.subTest(cp=cp[-40:]), self.assertRaises(ValueError):
                validate_classpath(cp, self.state, self.repo)

    def test_previously_absent_java_directory_appearing_after_capture_fails(self):
        (self.repo / next(iter(JAVA_OUTPUTS))).mkdir(parents=True)
        with self.assertRaisesRegex(ValueError, 'disappeared or changed type'):
            self.check()

    def test_present_java_output_is_preserved_not_required_to_be_no_source(self):
        for relative in JAVA_OUTPUTS:
            (self.repo / relative).mkdir(parents=True)
        state = self.capture()
        for task in state['java_compile_tasks']:
            task.update(no_source=False, skipped=False, source_empty=False, skip_message=None)
        result = self.check(state)
        self.assertEqual([], result['allowed_missing_no_source_java_outputs'])
        self.assertEqual(self.cp, state['classpath'])

    def test_wrong_executing_task_cannot_supply_the_classpath_capture(self):
        state = copy.deepcopy(self.state)
        state['captured_for'] = ':other:desktopTest'
        with self.assertRaisesRegex(ValueError, 'capture mismatch'):
            self.check(state)


if __name__ == '__main__':
    unittest.main()
