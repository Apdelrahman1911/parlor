"""Audit-only receipt writer; invoke only AFTER the displayed range was actually read."""
from pathlib import Path
import datetime
import hashlib
import json
import sys

root = Path('/Users/abdelrahman/Projects/parlor')
out = root / 'audit-runs/2026-09-05-source-audit'
start, end = int(sys.argv[1]), int(sys.argv[2])
notes = sys.argv[3]
source = root / 'gradle/verification-metadata.xml'
data = source.read_bytes()
sha = hashlib.sha256(data).hexdigest()
assert sha == '58b5a279cc5ccc58e1088fbe3dd248fcfdf31a307707204c5438259de7960f95'
assert 1 <= start <= end <= len(data.splitlines())
row = {
    'path': 'gradle/verification-metadata.xml',
    'sha256': sha,
    'reviewer': '/root/whodunit_cont',
    'reviewed_ranges': [[start, end]],
    'status': 'PARTIAL_LINE_BY_LINE_REVIEW',
    'relevance': 'build-artifact-checksum-allowlist',
    'reader_command': f"nl -ba gradle/verification-metadata.xml | sed -n '{start},{end}p'",
    'cross_file_paths': [notes],
    'evidence': [
        'reviews/metadata-whodunit_cont-notes.md',
        'coverage/metadata-human-chunks-whodunit_cont.jsonl',
        'evidence/selected-dependency-graphs-whodunit_cont.json',
    ],
    'uncertainty': 'Every displayed line in this range read. Checksum/coordinate ownership is not independently downloaded-byte or publisher trust proof. Selected graphs, not retained older allowlist entries, determine runtime use. No new builds/fetches.',
    'timestamp': datetime.datetime.now(datetime.timezone.utc).isoformat(),
}
for name in ['reviews-whodunit_cont.jsonl', 'metadata-human-chunks-whodunit_cont.jsonl']:
    with (out / 'coverage' / name).open('a') as file:
        file.write(json.dumps(row) + '\n')
print(f'Recorded completed human read {start}-{end}')
