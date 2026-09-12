"""Read-only diagnosis of the exact reviewed native09 preflight, never deletion."""
import hashlib
import importlib.util
from pathlib import Path
import json
import subprocess

path = Path(__file__).with_name("native09_secondary_fifo_recovery_01.py")
assert hashlib.sha256(path.read_bytes()).hexdigest() == "aaad39c1ff54639bd14212cd03439b5f0d285e2bac2347a18f97d344e3dc82f0"
spec = importlib.util.spec_from_file_location("native09_recovery_probe", path)
recovery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recovery)
actual_run = subprocess.run

def observed_run(arguments, **kwargs):
    if arguments not in (["/bin/ps", "-p", "41785,41768", "-o", "pid="],
            ["/usr/sbin/lsof", "-nP", "-w", "-t", "+D", str(recovery.ROOT)]):
        raise RuntimeError("Unexpected ownership query")
    result = actual_run(arguments, **kwargs)
    if len(result.stdout) > 32768 or len(result.stderr) > 4096:
        raise RuntimeError("Oversized query response; not retained")
    print(json.dumps(dict(command=arguments, exit_code=result.returncode,
                         stdout=result.stdout.decode("ascii"), stderr=result.stderr.decode("ascii"))), flush=True)
    return result

recovery.subprocess.run = observed_run
result = recovery.ExactRecovery(recovery.ROOT, recovery.NAMES, recovery.bound_plan(),
    recovery.check_pids, recovery.check_holders, lambda value: print(json.dumps(value), flush=True)).run(execute=False)
print(json.dumps(result))
