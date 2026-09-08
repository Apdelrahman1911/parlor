"""Fresh simulator ownership for the single remediation build lane (no Xcode).

Never selects an existing device, boots Simulator.app, erases a user profile,
or deletes paths discovered by a filesystem wildcard. The calling lane owns
signal deferral, Gradle stop, process cleanup and module-output cleanup.
"""
import datetime
import json
from pathlib import Path
import re
import subprocess
import uuid


class OwnedIosSimulator:
    def __init__(self, destination, cycle, environment, invoke):
        self.destination = Path(destination) / 'simulator'
        self.destination.mkdir()
        self.environment = environment
        self.invoke = invoke
        self.name = f'Parlor-Remediation-{cycle}-{uuid.uuid4().hex}'
        self.runtime = 'com.apple.CoreSimulator.SimRuntime.iOS-26-5'
        self.device_type = 'com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro'
        self.device = None
        self.before = set()
        self.creation_attempted = False
        self.receipt = {'name': self.name, 'runtime': self.runtime, 'device_type': self.device_type,
                        'commands': [], 'cleanup_errors': [], 'owned_device_absent': False}
        self.save()

    def save(self):
        (self.destination / 'receipt.json').write_text(json.dumps(self.receipt, indent=2) + '\n')

    def command(self, arguments, label, timeout=120):
        path = self.destination / (label + '.log')
        record = {'command': arguments, 'log': path.name,
                  'started_at': datetime.datetime.now(datetime.timezone.utc).isoformat()}
        self.receipt['commands'].append(record)
        self.save()
        try:
            record['exit_code'] = self.invoke(arguments, path, self.environment, timeout=timeout)
            if record['exit_code'] != 0:
                raise RuntimeError(f'Owned simulator command failed: {label}')
            return path.read_text()
        finally:
            record['finished_at'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
            self.save()

    def devices(self, label):
        # Keep only the owned name/UUID in the durable receipt. Inventory logs
        # can contain unrelated personal simulator names, so remove after parse.
        try:
            text = self.command(['xcrun', 'simctl', 'list', 'devices', '-j'], label)
            inventory = json.loads(text)['devices']
            return [(runtime, item) for runtime, rows in inventory.items() for item in rows]
        finally:
            # command() can fail after writing a partial inventory, or before
            # its log exists. Neither case may retain unrelated device names.
            (self.destination / (label + '.log')).unlink(missing_ok=True)

    def recover_exact_owned_device(self, label):
        devices = self.devices(label)
        matches = [item for runtime, item in devices if
                   runtime == self.runtime and item.get('name') == self.name and
                   item.get('udid') not in self.before]
        if len(matches) > 1:
            raise RuntimeError('Ambiguous owned simulator; no arbitrary device will be deleted')
        if matches:
            found = matches[0]['udid']
            if not re.fullmatch(r'[0-9A-Fa-f]{8}(?:-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}', found):
                raise RuntimeError('Owned simulator inventory returned an invalid UUID')
            if self.device is not None and self.device != found:
                raise RuntimeError('Owned simulator identity changed')
            self.device = found
            self.receipt['owned_uuid'] = found
            self.save()
        return matches[0] if matches else None

    def create_and_boot(self):
        existing = self.devices('inventory-before')
        if any(item.get('name') == self.name for _, item in existing):
            raise RuntimeError('Refusing a pre-existing simulator name')
        self.before = {item['udid'] for _, item in existing}
        self.receipt['preexisting_device_count'] = len(self.before)
        self.creation_attempted = True
        self.save()
        self.command(['xcrun', 'simctl', 'create', self.name, self.device_type, self.runtime], 'create')
        # Do not rely solely on stdout: a timed-out create may still allocate a
        # device. The same exact-name inventory recovery runs again in finally.
        if self.recover_exact_owned_device('inventory-created') is None:
            raise RuntimeError('Newly created simulator was not found by ownership identity')
        self.command(['xcrun', 'simctl', 'boot', self.device], 'boot')
        self.command(['xcrun', 'simctl', 'bootstatus', self.device, '-b'], 'bootstatus', timeout=300)
        self.environment['PARLOR_REMEDIATION_SIMULATOR_UDID'] = self.device

    def cleanup(self):
        errors = self.receipt['cleanup_errors']

        def attempt(label, operation):
            try:
                return operation()
            except BaseException as failure:
                errors.append(f'{label}: {type(failure).__name__}: {failure}')
                self.save()
                return None

        if self.creation_attempted:
            owned = attempt('Recover exact owned device', lambda: self.recover_exact_owned_device('inventory-cleanup'))
            if owned is not None:
                if owned.get('state') != 'Shutdown':
                    attempt('Shutdown', lambda: self.command(['xcrun', 'simctl', 'shutdown', self.device], 'shutdown'))
                # Delete addresses only the recovered new UUID, never a name,
                # wildcard, existing profile, device-set directory, or phone.
                attempt('Delete', lambda: self.command(['xcrun', 'simctl', 'delete', self.device], 'delete'))
            remaining = attempt('Verify device absence', lambda: self.devices('inventory-final'))
            if remaining is not None:
                present = [item for _, item in remaining if item.get('udid') == self.device or item.get('name') == self.name]
                self.receipt['owned_device_absent'] = not present
                if present:
                    errors.append('Owned simulator remains in inventory')
                final_ids = {item['udid'] for _, item in remaining}
                self.receipt['preexisting_devices_preserved'] = self.before.issubset(final_ids)
                if not self.receipt['preexisting_devices_preserved']:
                    errors.append('A pre-existing simulator disappeared; inspect external changes')
            if self.device is not None:
                scan = attempt('Verify owned device processes', lambda: subprocess.check_output(
                    ['ps', '-axo', 'pid=,command='], text=True, timeout=10))
                if scan is not None:
                    remaining_processes = [line for line in scan.splitlines() if self.device in line]
                    self.receipt['remaining_owned_uuid_processes'] = remaining_processes
                    if remaining_processes:
                        errors.append('Owned simulator UUID still appears in a process; no unrelated process was signalled')
        else:
            self.receipt['owned_device_absent'] = True
        self.save()
        if errors:
            raise RuntimeError('Owned simulator cleanup is incomplete; inspect simulator/receipt.json')
