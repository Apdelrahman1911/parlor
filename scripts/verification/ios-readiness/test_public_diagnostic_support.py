"""Include every diagnostic artifact/worker control in ordinary readiness checks."""
import importlib.util
from pathlib import Path
import sys


def load_tests(loader, _tests, _pattern):
    folder = Path(__file__).resolve().parents[3] / 'scripts/verification/ios-input-boundaries'
    tests = {
        'test_frozen_sources': '_parlor_frozen_source_fixture_controls',
        'test_public_attachments': '_parlor_public_attachment_controls',
        'test_frozen_result': '_parlor_frozen_input_result_controls',
        'test_xcode_worker_inventory': '_parlor_input_worker_inventory_controls',
    }
    for name in ('public_attachments', 'review_frozen_result', 'xcode_worker_inventory', 'frozen_sources', *tests):
        path = folder / (name + '.py')
        key = tests.get(name, name)
        module = sys.modules.get(key)
        if module is not None and Path(module.__file__).resolve() != path:
            raise RuntimeError('Unexpected diagnostic support control module')
        if module is None:
            spec = importlib.util.spec_from_file_location(key, path)
            module = importlib.util.module_from_spec(spec)
            sys.modules[key] = module
            spec.loader.exec_module(module)
    return loader.suiteClass(loader.loadTestsFromModule(sys.modules[key]) for key in tests.values())
