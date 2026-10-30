"""Reuse portable name-input copy/fixture/parser controls in ordinary readiness."""
import importlib.util
from pathlib import Path
import sys


def load_tests(loader, _tests, _pattern):
    folder = Path(__file__).resolve().parents[3] / 'scripts/verification/ios-input-boundaries'
    test_key = '_parlor_name_input_controls'
    for name in ('pin01_copy', 'pin01_receipts', 'pin02_capture', 'frozen_sources',
                 'pin05_name_input', 'pin03_catalog_copy', 'pin04_original_host',
                 'public_attachments', 'run_pin03', 'test_pin05_name_input'):
        path = folder / (name + '.py')
        key = test_key if name == 'test_pin05_name_input' else name
        module = sys.modules.get(key)
        if module is not None and Path(module.__file__).resolve() != path:
            raise RuntimeError('Unexpected name-input control module')
        if module is None:
            spec = importlib.util.spec_from_file_location(key, path)
            module = importlib.util.module_from_spec(spec)
            sys.modules[key] = module
            spec.loader.exec_module(module)
    return loader.loadTestsFromModule(sys.modules[test_key])
