"""Separate public-tool observation of the task's normal-source simulator app.

Never attach by name, enumerate arbitrary processes, read app data or inject app
code. sample/vmmap take a numeric PID: surrounding kernel-lifetime attestation is
not an atomic reservation. Raw temporary stacks/mappings must not be retained.
Only the allowed current-cycle image metadata leaves the owned temporary root.
"""
import hashlib
import json
import os
from pathlib import Path
import re

APP_ID = 'com.parlor.app.debug'
UUID = r'[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}'
SAMPLE_ROW = re.compile(r'^\s*(0x[0-9a-fA-F]+)\s*-\s*(0x[0-9a-fA-F]+)\s+.+?\s+<(' +
                        UUID + r')>\s+(/[^\r\n]+?)\s*$')
LIMITATION = ('Separate ninth launch via public simctl, after the eight XCTest observations; '
              'not per-repetition image proof. sample suspends the app while sampling, and vmmap '
              'inspects it, so this is not passive startup-timing evidence. UUIDs are reported by '
              'sample and matched to dwarf/file inventories, not independently read from mapped '
              'Mach-O headers. Pre/post kernel audit-token/lifetime checks, matching tool PID/path '
              'headers and mapped addresses fail closed, but numeric-PID tools do not reserve a '
              'process generation atomically. No hash of every mapped page, physical-device or Store proof.')


def parse_launch_pid(output):
    if not isinstance(output, str) or len(output.encode()) > 256:
        raise RuntimeError('Unbounded simulator launch identity response')
    match = re.fullmatch(re.escape(APP_ID) + r': ([1-9][0-9]{0,8})\n?', output)
    if match is None or not 1 < int(match[1]) <= 0x7fffffff:
        raise RuntimeError('Public simulator launch did not return the exact app PID')
    return int(match[1])


def attest_target(record, pid, installed_executable, launched_between, baseline_pids):
    if (record is None or record.pid != pid or pid in baseline_pids or pid == os.getpid() or
            record.uid != os.getuid() or record.command != str(installed_executable) or
            not isinstance(record.started, tuple) or len(record.started) != 2 or
            len(record.token) != 8 or record.token[5] != pid):
        raise RuntimeError('The public-launch PID is not the exact newly owned installed app')
    started = record.started[0] + record.started[1] / 1000000
    if not launched_between[0] <= started <= launched_between[1]:
        raise RuntimeError('App lifetime does not lie inside this exact public launch interval')
    return record


def unchanged_target(before, after):
    if (after is None or before.lifetime != after.lifetime or before.command != after.command or
            before.token != after.token):
        raise RuntimeError('Owned app exited, execed or changed generation during external observation')
    return dict(pid=before.pid, uid=before.uid, started=list(before.started),
                executable=before.command, generation=before.token[7],
                audit_token_sha256=hashlib.sha256(json.dumps(before.token).encode()).hexdigest(),
                bracketed_lifetime_unchanged=True)


def validate_artifacts(artifacts):
    if not isinstance(artifacts, dict) or not 3 <= len(artifacts) <= 16:
        raise RuntimeError('Missing or unbounded current-cycle executable inventory')
    for path, row in artifacts.items():
        if (not isinstance(path, str) or not path.startswith('/') or len(path.encode()) > 4096 or
                any(ord(c) < 32 or ord(c) == 127 for c in path) or not isinstance(row, dict) or
                set(row) != {'origin', 'relative_path', 'sha256', 'bytes', 'uuid', 'kind'} or
                row['origin'] not in {'installed-app', 'owned-copy-build', 'owned-derived-app'} or
                row['kind'] not in {'launcher', 'debug-dylib', 'compose-framework', 'other-app-image'} or
                not isinstance(row['uuid'], str) or re.fullmatch(UUID, row['uuid']) is None or
                not isinstance(row['sha256'], str) or re.fullmatch(r'[a-f0-9]{64}', row['sha256']) is None or
                type(row['bytes']) is not int or not 1 <= row['bytes'] <= 512 * 1024 * 1024):
            raise RuntimeError('Unsafe external-image inventory binding')
    return artifacts


def verify_header(raw, pid, executable, budget=16 * 1024 * 1024):
    if not isinstance(raw, str) or not 0 < len(raw.encode()) <= budget:
        raise RuntimeError('Empty or unbounded external process observation')
    process = re.findall(r'^Process:\s+[^\r\n]+ \[([0-9]+)\]\s*$', raw, flags=re.MULTILINE)
    paths = re.findall(r'^Path:\s+([^\r\n]+?)\s*$', raw, flags=re.MULTILINE)
    if process != [str(pid)] or paths != [str(executable)]:
        raise RuntimeError('External tool did not report the exact attested app PID/path')


def parse_sample(raw, pid, executable, artifacts):
    validate_artifacts(artifacts)
    verify_header(raw, pid, executable)
    if raw.count('\nBinary Images:\n') != 1:
        raise RuntimeError('Actual sample result lacks one unambiguous binary-image table')
    table = raw.split('\nBinary Images:\n', 1)[1]
    selected = {}
    for line in table.splitlines():
        match = SAMPLE_ROW.fullmatch(line)
        if match is None:
            if any(path in line for path in artifacts):
                raise RuntimeError('Unrecognized owned binary-image row; parser review required')
            continue
        path = match[4]
        if path not in artifacts:
            # Do not retain paths to unrelated images. A second, unexpected
            # Parlor/Compose image cannot silently stand in for the owned one.
            if Path(path).name in {'Parlor', 'Parlor.debug.dylib', 'ComposeApp'}:
                raise RuntimeError('Runtime contains an unbound application/framework image')
            continue
        start, end = int(match[1], 16), int(match[2], 16)
        if path in selected or not 0 < start <= end < 2**64 or match[3].lower() != artifacts[path]['uuid'].lower():
            raise RuntimeError('Duplicate, invalid or wrong-UUID owned mapped image')
        selected[path] = dict(**artifacts[path], sample_start=start, sample_end_inclusive=end,
                              observed_tool_uuid=match[3].lower())
    kinds = [row['kind'] for row in selected.values()]
    if any(kinds.count(kind) != 1 for kind in ('launcher', 'debug-dylib', 'compose-framework')):
        raise RuntimeError('One launcher, one debug dylib and one actual Compose framework must be observed')
    if any(row['origin'] != 'installed-app' for row in selected.values() if row['kind'] == 'launcher'):
        raise RuntimeError('Public simulator launch must execute its owned installed launcher')
    embedded = [row for row in artifacts.values() if row['kind'] == 'compose-framework' and row['origin'] == 'installed-app']
    framework = next(row for row in selected.values() if row['kind'] == 'compose-framework')
    if len(embedded) != 1 or embedded[0]['uuid'].lower() != framework['observed_tool_uuid']:
        raise RuntimeError('Observed framework UUID differs from the exact built/installed embedded framework')
    framework['embedded_sha256'] = embedded[0]['sha256']
    framework['same_file_bytes_as_embedded'] = framework['sha256'] == embedded[0]['sha256'] and framework['bytes'] == embedded[0]['bytes']
    return selected


def bind_vmmap(raw, pid, executable, selected):
    verify_header(raw, pid, executable)
    seen = {}
    for line in raw.splitlines():
        if not re.match(r'^\s*__TEXT\s+', line):
            continue
        matches = [path for path in selected if line.rstrip().endswith(' ' + path)]
        if not matches:
            if any(name in line for name in ('/Parlor.app/', '/ComposeApp.framework/ComposeApp')):
                raise RuntimeError('vmmap contains an app text mapping absent from the bound sample')
            continue
        if len(matches) != 1 or matches[0] in seen:
            raise RuntimeError('Ambiguous or duplicate owned executable text mapping')
        path = matches[0]
        address = re.match(r'^\s*__TEXT\s+([0-9a-fA-F]+)-([0-9a-fA-F]+)\s+', line)
        if address is None:
            raise RuntimeError('Unknown vmmap __TEXT row format; retain no unrelated mapping data')
        start, end = (int(value, 16) for value in address.groups())
        image = selected[path]
        if start != image['sample_start'] or not start < end <= image['sample_end_inclusive'] + 1:
            raise RuntimeError('vmmap text address is not the same sampled image mapping')
        seen[path] = dict(**image, vmmap_text_start=start, vmmap_text_end_exclusive=end)
    if set(seen) != set(selected):
        raise RuntimeError('Every sampled owned image needs a matching actual executable mapping')
    return dict(status='PASS', images=[dict(path=path, **row) for path, row in sorted(seen.items())],
                method='sample reported image path/UUID and vmmap executable-region address, '
                       'bound to built/installed current-cycle file hashes and dwarfdump UUIDs',
                limitation=LIMITATION)
