"""Reuse the catalog-copy controls in the ordinary complete readiness discovery."""
import importlib.util
from pathlib import Path
import sys


def load_tests(loader, _tests, _pattern):
    folder = Path(__file__).resolve().parents[3] / 'scripts/verification/ios-input-boundaries'
    tests = {'test_pin03_catalog': '_parlor_catalog_input_controls',
             'test_pin04_original_host': '_parlor_original_host_controls',
             'test_pin06_public_setup_pair': '_parlor_public_setup_pair_controls',
             'test_pin07_failure_control': '_parlor_synthetic_failed_export_controls',
             'test_pin07_native_adapter': '_parlor_native_failure_export_controls',
             'test_current_boundary_adapter': '_parlor_current_boundary_controls',
             'test_pin08_accessibility': '_parlor_native_accessibility_controls'}
    for name in ('pin01_copy', 'pin01_receipts', 'pin02_capture', 'pin03_catalog_copy',
                 'pin04_original_host', 'public_attachments', 'frozen_sources',
                 'pin06_public_setup_pair', 'pin07_failure_control', 'pin07_native_adapter', 'run_pin03', 'current_boundary_adapter', 'pin08_accessibility', *tests):
        path = folder / (name + '.py')
        key = tests.get(name, name)
        module = sys.modules.get(key)
        if module is not None and Path(module.__file__).resolve() != path:
            raise RuntimeError('Unexpected catalog-input control module')
        if module is None:
            spec = importlib.util.spec_from_file_location(key, path)
            module = importlib.util.module_from_spec(spec)
            sys.modules[key] = module
            spec.loader.exec_module(module)
    return loader.suiteClass(loader.loadTestsFromModule(sys.modules[key]) for key in tests.values())
