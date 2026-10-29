"""Run post-failure capture controls in the existing strict readiness gate."""
import importlib.util
from pathlib import Path
import sys


def load_tests(loader, _tests, _pattern):
    folder = Path(__file__).resolve().parents[3] / 'scripts/verification/ios-input-boundaries'
    modules = {}
    for name in ('pin01_copy', 'pin01_receipts', 'pin02_capture', 'test_pin02_capture'):
        path = folder / (name + '.py')
        key = name if name != 'test_pin02_capture' else '_parlor_public_capture_controls'
        module = sys.modules.get(key)
        if module is not None and Path(module.__file__).resolve() != path:
            raise RuntimeError('Unexpected public-capture control module')
        if module is None:
            spec = importlib.util.spec_from_file_location(key, path)
            module = importlib.util.module_from_spec(spec)
            sys.modules[key] = module
            spec.loader.exec_module(module)
        modules[name] = module
    return loader.loadTestsFromModule(modules['test_pin02_capture'])
