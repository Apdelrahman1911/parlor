"""Strict validation of the executing Gradle Test's unchanged captured classpath."""
import os
from pathlib import Path


MODULE = Path('game-modes/whodunit')
TEST_TASK = ':game-modes:whodunit:desktopTest'
JAVA_OUTPUTS = {
    MODULE / 'build/classes/java/desktopMain': ':game-modes:whodunit:compileDesktopMainJava',
    MODULE / 'build/classes/java/desktopTest': ':game-modes:whodunit:compileDesktopTestJava',
}
REQUIRED_OUTPUTS = tuple(MODULE / path for path in (
    'build/classes/kotlin/desktop/main', 'build/classes/kotlin/desktop/test',
    'build/processedResources/desktop/main', 'build/processedResources/desktop/test',
))


def kind(path):
    if path.is_symlink():
        return 'symlink'
    if path.is_file():
        return 'file'
    if path.is_dir():
        return 'directory'
    return 'other' if path.exists() else 'missing'


def validate_classpath(classpath, state, repo):
    repo = Path(repo)
    if not classpath or any(character in classpath for character in '\r\n\0'):
        raise ValueError('Invalid/empty classpath')
    paths = classpath.split(os.pathsep)
    if any(not path or not Path(path).is_absolute() or os.path.normpath(path) != path for path in paths):
        raise ValueError('Classpath entries must be nonempty normalized absolute paths')
    if len(set(paths)) != len(paths):
        raise ValueError('Duplicate classpath entry')
    if (state.get('schema') != 1 or state.get('captured_for') != TEST_TASK
            or state.get('classpath') != classpath):
        raise ValueError('Executing-Test classpath capture mismatch')
    entries = state.get('entries')
    if not isinstance(entries, list) or [entry.get('path') for entry in entries] != paths:
        raise ValueError('Classpath entry capture order/content mismatch')
    java_tasks = state.get('java_compile_tasks')
    if not isinstance(java_tasks, list):
        raise ValueError('Missing Java compile task states')
    java_by_path = {task.get('path'): task for task in java_tasks}
    if len(java_by_path) != len(java_tasks) or set(java_by_path) != set(JAVA_OUTPUTS.values()):
        raise ValueError('Unexpected/duplicate Java task identities')
    required = {str(repo / path) for path in REQUIRED_OUTPUTS}
    if not required.issubset(paths):
        raise ValueError('Required Kotlin/resource outputs missing from classpath')
    optional_java = {str(repo / path): task for path, task in JAVA_OUTPUTS.items()}
    allowed_missing = []
    files = 0
    for raw, entry in zip(paths, entries):
        actual_kind = kind(Path(raw))
        captured_kind = entry.get('kind')
        expected_exists = actual_kind != 'missing'
        if (captured_kind != actual_kind or type(entry.get('exists')) is not bool
                or entry['exists'] != expected_exists):
            raise ValueError('Classpath input disappeared or changed type after Test capture: ' + raw)
        if actual_kind == 'missing':
            task_path = optional_java.get(raw)
            if task_path is None:
                raise ValueError('Missing required/unknown classpath entry: ' + raw)
            task = java_by_path[task_path]
            if (task.get('destination') != raw or task.get('executed') is not True
                    or task.get('no_source') is not True or task.get('skipped') is not True
                    or task.get('source_empty') is not True or task.get('failure') is not False
                    or task.get('skip_message') != 'NO-SOURCE'):
                raise ValueError('Missing Java output lacks exact successful NO-SOURCE evidence: ' + raw)
            allowed_missing.append(raw)
            continue
        if actual_kind not in ('file', 'directory'):
            raise ValueError('Unsafe/unsupported classpath input type: ' + raw)
        if raw in required or raw in optional_java:
            if actual_kind != 'directory':
                raise ValueError('Compiled/resource classpath output is not a directory: ' + raw)
        if actual_kind == 'file':
            if not raw.endswith('.jar'):
                raise ValueError('Unexpected non-JAR classpath file: ' + raw)
            files += 1
        elif raw.endswith('.jar'):
            raise ValueError('Required JAR input is not a regular file: ' + raw)
    if files == 0:
        raise ValueError('Expected actual JAR dependencies on Test runtime classpath')
    # The caller MUST pass its original string to Java, not a filtered version.
    return {'entries': len(paths), 'existing_jar_inputs': files,
            'required_kotlin_and_resource_directories': sorted(required),
            'allowed_missing_no_source_java_outputs': allowed_missing,
            'classpath_preserved_unchanged': True}
