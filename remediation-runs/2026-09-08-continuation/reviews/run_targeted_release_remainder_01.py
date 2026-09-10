#!/usr/bin/env python3
"""Recheck the affected adapter file, then the unexecuted validator05 stages.

This is NOT another full release-validator run. The 477 previous passing
methods keep their original c797 source identity; validator05 remains FAIL.
Run only inside the coordinator's ordinary owned/cleaned Linux lane.
"""
import hashlib
from pathlib import Path
import subprocess

path = Path('scripts/release/validate_release_system.sh')
raw = path.read_bytes()
if hashlib.sha256(raw).hexdigest() != 'ce52c9c08edecba968337d6de42c90895badec127c7029a5afc16737d3b8864a':
    raise RuntimeError('Unreviewed canonical validator bytes')
script = raw.decode()
for completed in (
    'python3 -m py_compile scripts/generate_review_inventory.py scripts/release/*.py\n',
    "python3 -m unittest discover -s scripts/release/tests -p 'test_*.py' -v\n",
):
    if script.count(completed) != 1:
        raise RuntimeError('Canonical completed-stage anchor changed')
    script = script.replace(completed, '', 1)
print('TARGETED adapter recheck; prior full validator remains FAIL', flush=True)
subprocess.run(['/usr/bin/python3', '-B', '-m', 'unittest', '-v',
                'scripts.release.tests.test_native_continuation'], check=True)
print('UNEXECUTED canonical workflow/inventory/pinned-tool stages', flush=True)
# Preserve the canonical script's $0-based root lookup and entire cleanup body.
subprocess.run(['/bin/bash', '-c', script, str(path)], check=True)
