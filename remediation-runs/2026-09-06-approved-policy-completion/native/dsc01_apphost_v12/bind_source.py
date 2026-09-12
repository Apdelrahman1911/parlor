#!/usr/bin/env python3
"""Root-only preparation, before independent review; never auto-rebind a run."""
import json
from pathlib import Path
import re
import sys

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[1]))
from run_gradle_cycle import ROOT, identity, now
from copied_sources import allowed_path


def main():
    if len(sys.argv) != 3 or any(not re.fullmatch('[a-f0-9]{64}', item) for item in sys.argv[1:]):
        raise SystemExit('Usage: bind_source.py EXPECTED_FROZEN_SOURCE_SHA256 EXPECTED_FROZEN_DIFF_SHA256')
    target = HERE / 'source-bindings.json'
    if target.exists():
        raise SystemExit('Binding already exists: retain it and create a versioned harness for another source')
    source = json.loads(json.dumps(identity()))
    if (source['source_manifest_sha256'], source['diff_sha256']) != tuple(sys.argv[1:]):
        raise SystemExit('Current dirty/untracked source differs from the explicitly frozen source')
    result = dict(schema_version=3, binding_status='REVIEW_REQUIRED_BOUND', repository=str(ROOT),
                  created_at=now(), source_identity=source,
                  copy_only=[dict(path=path, sha256=digest) for path, digest in source['source_manifest']
                             if allowed_path(path)],
                  exclusions='No Git/history, user home, signing material, local.properties, design/audit material, caches, generated outputs, or non-build documentation copied.',
                  trust='No run allowed until a separate reviewer hashes and approves all bound controls.')
    with target.open('x') as file:
        file.write(json.dumps(result, indent=2) + '\n')
    print(json.dumps(dict(source_sha256=source['source_manifest_sha256'], diff_sha256=source['diff_sha256'],
                          copied_inputs=len(result['copy_only']), status='BOUND_REQUIRES_INDEPENDENT_REVIEW')))


if __name__ == '__main__':
    main()
