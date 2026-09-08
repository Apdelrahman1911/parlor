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
              'process generation atomically. An explicitly enabled USER-only path is a lossy tool '
              'presentation alias for an approved current-user device artifact, not independently '
              'kernel-attested non-launcher image-path proof. No hash of every mapped page, physical-device or Store proof.')


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


class OwnedToolPaths:
    """Explicit, bounded lookup; never a wildcard or substitution on tool input.

    The caller supplies the OS account home and exact owned device UUID only
    after kernel-attesting the new launcher. Artifact inventory/canonical paths
    must already be bound to that run. These are presentation aliases, not new
    filesystem or process ownership grants.
    """
    def __init__(self, artifacts, current_user_home, device_uuid, installed_app):
        validate_artifacts(artifacts)
        home = str(current_user_home)
        app = str(installed_app)
        if (not self.safe_path(home) or not self.safe_path(app) or re.fullmatch(UUID, str(device_uuid)) is None):
            raise RuntimeError('Invalid independent account/device path context')
        self.artifacts = {path: dict(row) for path, row in artifacts.items()}
        self.aliases = {path: path for path in artifacts}
        prefix = home + '/Library/Developer/CoreSimulator/Devices/' + str(device_uuid) + '/data/Containers/Bundle/Application/'
        suffix = app[len(prefix):].split('/') if app.startswith(prefix) else []
        if len(suffix) != 2 or re.fullmatch(UUID, suffix[0]) is None or suffix[1] != 'Parlor.app':
            raise RuntimeError('Installed app is not in the independently owned device/container')
        launcher = artifacts.get(app + '/Parlor')
        if launcher is None or any(launcher[key] != value for key, value in {
                'origin': 'installed-app', 'kind': 'launcher', 'relative_path': 'Parlor'}.items()):
            raise RuntimeError('Owned installed app lacks its exact attested launcher')
        redact_home = len(Path(home).parts) == 3 and Path(home).parts[:2] == ('/', 'Users')
        for path, row in artifacts.items():
            if not self.safe_path(path):
                raise RuntimeError('Noncanonical artifact path cannot gain an alias')
            if row['origin'] != 'installed-app':
                continue  # Copied-build/DerivedData paths remain exact.
            if not path.startswith(app + '/') or path[len(app) + 1:] != row['relative_path']:
                raise RuntimeError('Installed artifact does not match its complete app-container path')
            if redact_home:
                alias = '/Users/USER' + path[len(home):]
                if alias in self.aliases and self.aliases[alias] != path:
                    raise RuntimeError('Ambiguous exact or USER-only artifact presentation')
                self.aliases[alias] = path

    @staticmethod
    def safe_path(path):
        return (isinstance(path, str) and path.startswith('/') and not path.startswith('//') and
                0 < len(path.encode()) <= 4096 and str(Path(path)) == path and
                not any(part in {'.', '..'} for part in Path(path).parts) and
                not any(ord(char) < 32 or ord(char) == 127 for char in path))

    def resolve(self, raw):
        return self.aliases.get(raw)

    def forms(self, canonical):
        return tuple(raw for raw, path in self.aliases.items() if path == canonical)

    def require_inventory(self, artifacts):
        if artifacts != self.artifacts:
            raise RuntimeError('Tool aliases are not bound to this exact artifact inventory')


def resolve_tool_path(raw, tool_paths):
    if tool_paths is None:
        return raw
    if not isinstance(tool_paths, OwnedToolPaths):
        raise RuntimeError('Tool path policy was not built from owned artifact inputs')
    return tool_paths.resolve(raw)


def verify_header(raw, pid, executable, budget=16 * 1024 * 1024, *, tool_paths=None):
    if not isinstance(raw, str) or not 0 < len(raw.encode()) <= budget:
        raise RuntimeError('Empty or unbounded external process observation')
    process = re.findall(r'^Process:\s+[^\r\n]+ \[([0-9]+)\]\s*$', raw, flags=re.MULTILINE)
    paths = re.findall(r'^Path:\s+([^\r\n]+?)\s*$', raw, flags=re.MULTILINE)
    if (process != [str(pid)] or len(paths) != 1 or
            resolve_tool_path(paths[0], tool_paths) != str(executable)):
        raise RuntimeError('External tool did not report the exact attested app PID/path')
    return 'exact' if paths[0] == str(executable) else 'owned-USER-only-presentation'


def header_diagnostic(raw, pid, executable, budget=16 * 1024 * 1024):
    """Failure-only format evidence; never a substitute for verify_header.

    Unknown header text/path components are hashed, not retained. Indexes refer
    only to components of the already-attested executable path. Recognizing a
    public redaction marker diagnoses a format; it does not accept that format.
    """
    if not isinstance(raw, str) or not 0 < len(raw.encode()) <= budget:
        raise RuntimeError('Empty or unbounded external header diagnostic')
    processes = re.findall(r'^Process:[ \t]*([^\r\n]*)$', raw, flags=re.MULTILINE)
    paths = re.findall(r'^Path:[ \t]*([^\r\n]*)$', raw, flags=re.MULTILINE)
    if len(processes) > 8 or len(paths) > 8 or any(len(value.encode()) > 4096 for value in processes + paths):
        raise RuntimeError('External diagnostic header budget exceeded')
    expected = str(executable).split('/')
    path_rows = []
    for value in paths:
        value = value.strip()
        parts = value.split('/')
        if len(parts) > 128:
            raise RuntimeError('External diagnostic component budget exceeded')
        shape = []
        for part in parts:
            matches = [index for index, known in enumerate(expected) if part == known]
            if matches:
                shape.append(dict(expected_component_indexes=matches))
            elif part in {'USER', '*', '...', '…', '~', '<redacted>'}:
                shape.append(dict(public_marker=part))
            else:
                shape.append(dict(unknown_sha256=hashlib.sha256(part.encode()).hexdigest(),
                                  bytes=len(part.encode())))
        path_rows.append(dict(exact_attested_path=value == str(executable), bytes=len(value.encode()),
                              component_shape=shape))
    parsed_pids = re.findall(r'^Process:\s+[^\r\n]+ \[([0-9]+)\]\s*$', raw, flags=re.MULTILINE)
    parsed_paths = re.findall(r'^Path:\s+([^\r\n]+?)\s*$', raw, flags=re.MULTILINE)
    return dict(schema_version=1, kind='FAILURE_ONLY_EXTERNAL_HEADER_FORMAT', proves_provenance=False,
                original_header_predicate_matches=parsed_pids == [str(pid)] and parsed_paths == [str(executable)],
                process_headers=len(processes), parsed_pid_count=len(parsed_pids),
                parsed_pids_match_exact_target=parsed_pids == [str(pid)],
                process_header_sha256=[hashlib.sha256(value.encode()).hexdigest() for value in processes],
                path_headers=len(paths), paths=path_rows,
                decoded_text_sha256=hashlib.sha256(raw.encode()).hexdigest())


def parse_sample(raw, pid, executable, artifacts, *, tool_paths=None):
    validate_artifacts(artifacts)
    if tool_paths is not None:
        if not isinstance(tool_paths, OwnedToolPaths):
            raise RuntimeError('Unknown tool path policy')
        tool_paths.require_inventory(artifacts)
    header_kind = verify_header(raw, pid, executable, tool_paths=tool_paths)
    if raw.count('\nBinary Images:\n') != 1:
        raise RuntimeError('Actual sample result lacks one unambiguous binary-image table')
    table = raw.split('\nBinary Images:\n', 1)[1]
    selected = {}
    owned_forms = artifacts if tool_paths is None else tool_paths.aliases
    for line in table.splitlines():
        match = SAMPLE_ROW.fullmatch(line)
        if match is None:
            if any(path in line for path in owned_forms):
                raise RuntimeError('Unrecognized owned binary-image row; parser review required')
            continue
        reported_path = match[4]
        path = resolve_tool_path(reported_path, tool_paths)
        if path not in artifacts:
            # Do not retain paths to unrelated images. A second, unexpected
            # Parlor/Compose image cannot silently stand in for the owned one.
            if Path(reported_path).name in {'Parlor', 'Parlor.debug.dylib', 'ComposeApp'}:
                raise RuntimeError('Runtime contains an unbound application/framework image')
            continue
        start, end = int(match[1], 16), int(match[2], 16)
        if path in selected or not 0 < start <= end < 2**64 or match[3].lower() != artifacts[path]['uuid'].lower():
            raise RuntimeError('Duplicate, invalid or wrong-UUID owned mapped image')
        selected[path] = dict(**artifacts[path], sample_start=start, sample_end_inclusive=end,
                              observed_tool_uuid=match[3].lower(), sample_header_path_kind=header_kind,
                              sample_path_kind='exact' if path == reported_path else 'owned-USER-only-presentation')
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


def bind_vmmap(raw, pid, executable, selected, *, tool_paths=None):
    header_kind = verify_header(raw, pid, executable, tool_paths=tool_paths)
    forms = {path: (path,) if tool_paths is None else tool_paths.forms(path) for path in selected}
    if tool_paths is not None and any(
            path not in tool_paths.artifacts or any(row.get(key) != value for key, value in tool_paths.artifacts[path].items())
            for path, row in selected.items()):
        raise RuntimeError('Selected image differs from its alias-bound artifact')
    seen = {}
    for line in raw.splitlines():
        if not re.match(r'^\s*__TEXT\s+', line):
            continue
        matches = [(path, form) for path, aliases in forms.items() for form in aliases
                   if line.rstrip().endswith(' ' + form)]
        if not matches:
            if any(name in line for name in ('/Parlor.app/', '/ComposeApp.framework/ComposeApp')):
                raise RuntimeError('vmmap contains an app text mapping absent from the bound sample')
            continue
        if len(matches) != 1 or matches[0][0] in seen:
            raise RuntimeError('Ambiguous or duplicate owned executable text mapping')
        path, reported_path = matches[0]
        address = re.match(r'^\s*__TEXT\s+([0-9a-fA-F]+)-([0-9a-fA-F]+)\s+', line)
        if address is None:
            raise RuntimeError('Unknown vmmap __TEXT row format; retain no unrelated mapping data')
        start, end = (int(value, 16) for value in address.groups())
        image = selected[path]
        if start != image['sample_start'] or not start < end <= image['sample_end_inclusive'] + 1:
            raise RuntimeError('vmmap text address is not the same sampled image mapping')
        seen[path] = dict(**image, vmmap_text_start=start, vmmap_text_end_exclusive=end,
                         vmmap_path_kind='exact' if path == reported_path else 'owned-USER-only-presentation')
    if set(seen) != set(selected):
        raise RuntimeError('Every sampled owned image needs a matching actual executable mapping')
    return dict(status='PASS', vmmap_header_path_kind=header_kind,
                images=[dict(path=path, **row) for path, row in sorted(seen.items())],
                method='sample reported image path/UUID and vmmap executable-region address, '
                       'bound to built/installed current-cycle file hashes and dwarfdump UUIDs',
                limitation=LIMITATION)
