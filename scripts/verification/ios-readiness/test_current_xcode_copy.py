"""Run the actual-project copy guards in routine Linux/macOS readiness CI.

Reuse the normal lane's cases and real PBX input, not another simplified fixture.
Importing these controls does not build, launch, instrument or allocate a Simulator.
"""
import importlib.util
from pathlib import Path
import sys


def load_tests(loader, _tests, _pattern):
    repository = Path(__file__).resolve().parents[3]
    folder = repository / 'remediation-runs/2026-09-07-local-readiness/native/normal-ios-launch-proposal-01'
    path = folder / 'test_normal_launch_controls.py'
    name = '_parlor_current_xcode_copy_controls'
    module = sys.modules.get(name)
    if module is not None and Path(module.__file__).resolve() != path:
        raise RuntimeError('Unexpected current-Xcode copy control module')
    if module is None:
        spec = importlib.util.spec_from_file_location(name, path)
        module = importlib.util.module_from_spec(spec)
        sys.modules[name] = module
        previous = sys.path[:]
        try:
            sys.path.insert(0, str(folder))
            spec.loader.exec_module(module)
        finally:
            sys.path[:] = previous
    return loader.loadTestsFromTestCase(module.CopiedSourceTests)
