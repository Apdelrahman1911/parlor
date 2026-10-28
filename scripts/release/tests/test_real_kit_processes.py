"""Include nonnetworking opt-in process-runner guards in ordinary release checks."""
import importlib.util
from pathlib import Path


def load_tests(loader, tests, pattern):
    source = Path(__file__).resolve().parents[2] / 'verification/real-kit-lan/test_processes.py'
    spec = importlib.util.spec_from_file_location('parlor_process_runner_tests', source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return loader.loadTestsFromModule(module)
