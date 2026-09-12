#!/usr/bin/env python3
"""Explicit read-only source binding; never approve or start a native cycle."""
import datetime
import json
import os
from pathlib import Path
import re
import sys

from copied_sources import allowed_path
from owned_lane import ROOT, identity


def checked_destination(raw, root=ROOT):
    path = Path(raw).absolute()
    if path.exists() or path.is_symlink():
        raise RuntimeError('Never overwrite an earlier binding or user file')
    if path.parent.resolve(strict=True) != path.parent or not path.parent.is_dir():
        raise RuntimeError('Binding parent must be an existing nonsymlink campaign directory')
    if (path.parent.parent != root / 'remediation-runs' or
            not re.fullmatch(r'[a-z0-9-]+', path.parent.name) or
            not re.fullmatch(r'[a-z0-9-]+\.json', path.name)):
        raise RuntimeError('Binding must be a direct JSON file in one explicit remediation campaign')
    return path


def main(arguments=None):
    arguments = sys.argv[1:] if arguments is None else arguments
    if arguments == ['--describe']:
        value = identity()
        print(json.dumps({key: value[key] for key in
              ('branch', 'commit', 'tree', 'diff_sha256', 'source_manifest_sha256')}, indent=2))
        return 0
    if len(arguments) != 3 or not all(re.fullmatch(r'[a-f0-9]{64}', value) for value in arguments[1:]):
        raise SystemExit('Usage: bind_source.py OUTPUT_JSON EXPECTED_SOURCE_MANIFEST_SHA256 EXPECTED_DIFF_SHA256')
    output = checked_destination(arguments[0])
    before = identity()
    if (before['source_manifest_sha256'], before['diff_sha256']) != tuple(arguments[1:]):
        raise RuntimeError('Current tracked/untracked bytes differ from the explicitly selected source')
    copied = [dict(path=path, sha256=digest) for path, digest in before['source_manifest'] if allowed_path(path)]
    if before != identity():
        raise RuntimeError('Source changed while binding; no implicit refresh permitted')
    value = dict(schema_version=3, binding_status='REVIEW_REQUIRED_BOUND', repository=str(ROOT),
                 created_at=datetime.datetime.now(datetime.timezone.utc).isoformat(), source_identity=before,
                 copy_only=copied,
                 policy='Independent source/control review still required. No build, simulator, signing or publication authorization is conferred by this file.')
    # O_EXCL refuses a concurrently-created destination instead of replacing it.
    descriptor = os.open(output, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(descriptor, 'w') as stream:
        stream.write(json.dumps(value, indent=2) + '\n')
        stream.flush()
        os.fsync(stream.fileno())
    print(json.dumps(dict(binding=str(output), source_manifest_sha256=before['source_manifest_sha256'],
                          copied_inputs=len(copied), status='REVIEW_REQUIRED_BOUND'), indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
