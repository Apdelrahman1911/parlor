"""Reuse the catalog-copy controls in the ordinary complete readiness discovery."""
import importlib.util
from pathlib import Path
import sys


def load_tests(loader, _tests, _pattern):
    folder = Path(__file__).resolve().parents[3] / 'scripts/verification/ios-input-boundaries'
    for name in ('pin01_copy', 'pin01_receipts', 'pin02_capture', 'pin03_catalog_copy', 'run_pin03', 'test_pin03_catalog'):
        path = folder / (name + '.py')
        key = name if name != 'test_pin03_catalog' else '_parlor_catalog_input_controls'
        module = sys.modules.get(key)
        if module is not None and Path(module.__file__).resolve() != path:
            raise RuntimeError('Unexpected catalog-input control module')
        if module is None:
            spec = importlib.util.spec_from_file_location(key, path)
            module = importlib.util.module_from_spec(spec)
            sys.modules[key] = module
            spec.loader.exec_module(module)
    return loader.loadTestsFromModule(sys.modules['_parlor_catalog_input_controls'])
