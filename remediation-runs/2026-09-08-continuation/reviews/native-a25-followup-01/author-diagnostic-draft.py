"""Author held source files using stdlib only. Does not import or execute tests.

Root alone owns integration/execution. This generator is not a canonical control.
"""
from pathlib import Path
import ast
import difflib
import hashlib
import json


ROOT = Path(__file__).resolve().parents[4]
BASE = ROOT / 'remediation-runs/2026-09-07-local-readiness/l08-storage-functional-companion-02'
OUT = Path(__file__).resolve().parent

BOUNDARIES = (
    ('prepare-initial-state', '                check(owned == null && !captured && mounts == 0)'),
    ('prepare-presentation-slot', '                check(L08UiSeam.hostContent == null)'),
    ('prepare-koin', '                val koin = KoinPlatform.getKoin()'),
    ('prepare-owner-binding', '                owner = koin.get()'),
    ('prepare-transport-binding', '                transport = koin.get<L08ControlledTransport>()'),
    ('prepare-scope-binding', '                processScope = koin.get(qualifier = named("multiplayerSession"))'),
    ('prepare-transport-identity', '                check(koin.get<RoomTransport>() === transport)'),
    ('prepare-system-language', '                check(koin.get<SettingsStore>().languageOverride.first() == null)'),
    ('prepare-owner-idle', '                check(checkNotNull(owner).state.value == ProcessMultiplayerState.Idle)'),
    ('prepare-fixture-roster', '                val players = (1..6).map { Player(PlayerId("l08-host-seat-$it"), "Audit Player $it", it - 1) }'),
    ('prepare-content-load', '                    val result = koin.get<CaseRepository>().loadCase(CaseId("last-dinner"), WhodunitPayloadValidator(koin.get()))'),
    ('prepare-content-result', '                    check(result is Result.Success)'),
    ('prepare-route', '                val route = if (loadedCase != null) MultiplayerSessionRoute.host(WhodunitIds.GameId,'),
    ('prepare-acquire', '                val result = checkNotNull(owner).acquire(route, hostSeed = 91L) { mode ->'),
    ('prepare-acquire-result', '                check(result is Result.Success)'),
    ('prepare-runtime-empty', '                check(session.runtime.value == null)'),
    ('prepare-admissions-freeze', '                val frozen = session.freezeAdmissions()'),
    ('prepare-room-identity', '                val room = checkNotNull(transport).room()'),
    ('prepare-frozen-roster', '                check(frozen is Result.Success && frozen.data == room.members.value)'),
    ('prepare-witness', '                val exits: () -> Unit = { workerFailed = true }'),
    ('prepare-presentation', '                witness = value'),
    ('prepare-await-offers', '                await { value.waiting() && room.offeredPeerCount == 5 }'),
    ('prepare-ready-empty', '                check(room.readyPeerCount == 0 && room.committedPeerCount == 0)'),
    ('prepare-release-ready', '                room.releaseReady(value.protocol(), value.accepts)'),
    ('prepare-await-started', '                await { value.started() && room.snapshottedPeerCount == 5 && direction != null }'),
    ('prepare-committed-roster', '                check(room.committedPeerCount == 5 && room.admissionClosures == 1)'),
    ('prepare-no-drops', '                room.requireNoDrops()'),
    ('prepare-return', '                return checks("real_owner_acquisition", "admissions_frozen_once", "strict_start_barrier",'),
)
OLD_STAGE_TEST = "value['stage'] in (STORAGE_FAILURE_STAGES if scenario == 'l08-storage' else {'context', *HOST_PLAN}),"
NEW_STAGE_TEST = "value['stage'] in (STORAGE_FAILURE_STAGES if scenario == 'l08-storage' else {'context', *HOST_PLAN, *HOST_PREPARE_FAILURE_STAGES}),"
ACTION_RESTRICTION = (
    "        require(scenario != 'l08-host' or value['stage'] not in HOST_PREPARE_FAILURE_STAGES or action == 'prepare',\n"
    "                'Host prepare diagnostics belong only to the prepare action')\n"
)
DECLARATION = ('HOST_PREPARE_FAILURE_STAGES = frozenset({\n' +
    ''.join('    ' + repr(stage) + ',\n' for stage, _ in BOUNDARIES) + '})\n')


def once(source, before, after):
    if source.count(before) != 1:
        raise RuntimeError('Missing or ambiguous mechanical source anchor: ' + before)
    return source.replace(before, after, 1)


originals = {name: (BASE / name).read_text() for name in
             ('L08HostProbe.kt.in', 'l08_receipts.py', 'test_l08_functional_copy.py')}
probe = originals['L08HostProbe.kt.in']
first = probe.index('            "prepare" -> {\n')
last = probe.index('            "capture" -> {\n', first)
prepare = probe[first:last]
for stage, anchor in BOUNDARIES:
    indent = anchor[:len(anchor) - len(anchor.lstrip())]
    prepare = once(prepare, '\n' + anchor + '\n',
                   '\n' + indent + 'stage = "' + stage + '"\n' + anchor + '\n')
probe = probe[:first] + prepare + probe[last:]
restored = probe
for stage, anchor in BOUNDARIES:
    indent = anchor[:len(anchor) - len(anchor.lstrip())]
    restored = once(restored, indent + 'stage = "' + stage + '"\n', '')
if restored != originals['L08HostProbe.kt.in']:
    raise RuntimeError('Host changed beyond 28 exact literal assignments')

receipts = once(originals['l08_receipts.py'], 'HOST_CHECKS = {\n', DECLARATION + 'HOST_CHECKS = {\n')
receipts = once(receipts, OLD_STAGE_TEST, NEW_STAGE_TEST)
receipts = once(receipts, "                'Unknown failure metadata must not enter retained evidence')\n",
                "                'Unknown failure metadata must not enter retained evidence')\n" + ACTION_RESTRICTION)
restored_parser = once(once(once(receipts, DECLARATION, ''), NEW_STAGE_TEST, OLD_STAGE_TEST), ACTION_RESTRICTION, '')
if restored_parser != originals['l08_receipts.py']:
    raise RuntimeError('Parser changed beyond exact closed failure-only extension')

helpers = '''
# Literal, failure-only labels. Each is bound to its exact original prepare line.
# Removing these insertions must restore the complete original host source.
HOST_PREPARE_BOUNDARIES = (
'''
helpers += ''.join('    (' + repr(stage) + ', ' + repr(anchor) + '),\n' for stage, anchor in BOUNDARIES)
helpers += ''')
HOST_PREPARE_DECLARATION = ('HOST_PREPARE_FAILURE_STAGES = frozenset({\\n' +
    ''.join('    ' + repr(stage) + ',\\n' for stage, _ in HOST_PREPARE_BOUNDARIES) + '})\\n')
HOST_PREPARE_OLD_STAGE_TEST = ''' + repr(OLD_STAGE_TEST) + '''
HOST_PREPARE_NEW_STAGE_TEST = ''' + repr(NEW_STAGE_TEST) + '''
HOST_PREPARE_ACTION_RESTRICTION = ''' + repr(ACTION_RESTRICTION) + '''


def strip_host_prepare_stages(candidate):
    first, last = '            "prepare" -> {\\n', '            "capture" -> {\\n'
    prepare = section(candidate, first, last)
    for stage, anchor in HOST_PREPARE_BOUNDARIES:
        indent = anchor[:len(anchor) - len(anchor.lstrip())]
        inserted = indent + 'stage = "' + stage + '"\\n'
        require(candidate.count(inserted) == 1, 'Missing/duplicated closed host prepare stage')
        prepare = strict.once(prepare, inserted + anchor + '\\n', anchor + '\\n')
    return strict.once(candidate, section(candidate, first, last), prepare)


def assert_host_prepare_contract(candidate, original):
    require(strip_host_prepare_stages(candidate) == original,
            'Host prepare diagnostics must preserve complete original execution and failure payload')


def strip_host_prepare_parser_extension(candidate):
    for before, after in (
        (HOST_PREPARE_DECLARATION, ''),
        (HOST_PREPARE_NEW_STAGE_TEST, HOST_PREPARE_OLD_STAGE_TEST),
        (HOST_PREPARE_ACTION_RESTRICTION, ''),
    ):
        candidate = strict.once(candidate, before, after)
    return candidate


def assert_host_prepare_parser_contract(candidate, original):
    require(strip_host_prepare_parser_extension(candidate) == original,
            'Host diagnostics must preserve every original sanitizer and complete-matrix verifier')

'''

tests = '''

class L08HostPrepareDiagnosticTests(unittest.TestCase):
    """Synthetic source/sanitizer controls only, not Kotlin or Simulator runtime."""

    def source_pair(self, name):
        return (HERE / name).read_text(), (CANONICAL / name).read_text()

    def failure(self, stage, reason='fixture_or_boundary_failure'):
        # Deliberately synthetic. No actual A25 failure is rewritten by these tests.
        record = host_matrix()[0][0]
        record['observation'] = dict(schema_version=1, status='FAIL', run_token=TOKEN,
            boot_ordinal=record['boot_ordinal'], action=record['action'], stage=stage, reason=reason)
        return record

    def test_exact_closed_stages_preserve_complete_original_host_source(self):
        candidate, original = self.source_pair('L08HostProbe.kt.in')
        assert_host_prepare_contract(candidate, original)
        stages = {stage for stage, _ in HOST_PREPARE_BOUNDARIES}
        self.assertEqual(len(stages), 28)
        self.assertIsInstance(host_receipts.HOST_PREPARE_FAILURE_STAGES, frozenset)
        self.assertEqual(host_receipts.HOST_PREPARE_FAILURE_STAGES, stages)
        self.assertEqual(set(re.findall(r'stage = "(prepare-[a-z-]+)"', candidate)), stages)
        # Exact restoration includes every other action, timeout, catch, cleanup,
        # original room/owner/seed/authority binding, and closed FAIL payload.
        self.assertEqual(strip_host_prepare_stages(candidate), original)

    def test_parser_extension_does_not_touch_storage_or_strict_host_verification(self):
        candidate, original = self.source_pair('l08_receipts.py')
        assert_host_prepare_parser_contract(candidate, original)
        self.assertEqual(section(candidate, 'def verify_host(', 'def preserve_l08_evidence('),
                         section(original, 'def verify_host(', 'def preserve_l08_evidence('))

    def test_every_stage_is_failure_only_preservable_without_record_mutation(self):
        for stage in sorted(host_receipts.HOST_PREPARE_FAILURE_STAGES):
            for reason in ('cancelled', 'fixture_or_boundary_failure'):
                with self.subTest(stage=stage, reason=reason):
                    record = self.failure(stage, reason)
                    before = copy.deepcopy(record)
                    host_receipts.validate_preservable_operation(record, 'l08-host')
                    self.assertEqual(record, before)
                    self.assertEqual(record['observation']['status'], 'FAIL')

    def test_prepare_diagnostics_cannot_appear_on_another_action_or_storage(self):
        for stage in sorted(host_receipts.HOST_PREPARE_FAILURE_STAGES):
            for action in host_receipts.HOST_PLAN[1:]:
                record = self.failure(stage)
                record['action'] = record['observation']['action'] = action
                with self.subTest(stage=stage, action=action), self.assertRaisesRegex(
                        RuntimeError, '^Host prepare diagnostics belong only to the prepare action$'):
                    host_receipts.validate_preservable_operation(record, 'l08-host')
            record = storage_failure_record(stage=stage)
            with self.subTest(stage=stage, scenario='l08-storage'), self.assertRaises(RuntimeError):
                host_receipts.validate_preservable_operation(record, 'l08-storage')

    @mock.patch.object(host_receipts, 'bind_loaded_images')
    def test_no_preservable_diagnostic_failure_can_satisfy_the_complete_host_matrix(self, _binding):
        # Verify the positive synthetic baseline first, avoiding an always-failing oracle.
        records, scenario, log = host_matrix()
        self.assertEqual(host_receipts.verify_host(records, scenario, TOKEN, log, 'disabled', {}, {},
            [framework_inventory()])['status'], 'PASS')
        for stage in sorted(host_receipts.HOST_PREPARE_FAILURE_STAGES):
            for changed_status in ('FAIL', 'PASS'):
                rows, scenario, log = host_matrix()
                rows[0] = self.failure(stage)
                rows[0]['observation']['status'] = changed_status
                with self.subTest(stage=stage, changed_status=changed_status), self.assertRaises(RuntimeError):
                    host_receipts.verify_host(rows, scenario, TOKEN, log, 'disabled', {}, {}, [framework_inventory()])
            passed = host_matrix()[0][0]
            passed['observation']['stage'] = stage
            with self.subTest(stage=stage, operation='PASS'), self.assertRaises(RuntimeError):
                host_receipts.validate_preservable_operation(passed, 'l08-host')

    def test_failure_payload_rejects_dynamic_unknown_or_sensitive_metadata(self):
        valid = self.failure('prepare-await-offers')
        host_receipts.validate_preservable_operation(valid, 'l08-host')
        changes = [('stage', value) for value in ('', 'prepare-unknown', '/private/synthetic',
                   'prepare-' + 'x' * 8192, True, 1, None, [], {})]
        changes += [('reason', value) for value in ('raw exception text', True, 1, None, [], {})]
        changes += [(key, 'synthetic-rejection-only') for key in
                    ('exception', 'message', 'path', 'payload', 'private_state', 'role_map', 'seed',
                     'action_ordinal', 'checks', 'protection_metadata')]
        for field, value in changes:
            changed = copy.deepcopy(valid)
            changed['observation'][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(RuntimeError):
                host_receipts.validate_preservable_operation(changed, 'l08-host')

    def test_every_stage_must_remain_unique_literal_and_at_the_exact_original_boundary(self):
        source, original = self.source_pair('L08HostProbe.kt.in')
        assert_host_prepare_contract(source, original)
        for stage, anchor in HOST_PREPARE_BOUNDARIES:
            indent = anchor[:len(anchor) - len(anchor.lstrip())]
            inserted = indent + 'stage = "' + stage + '"\\n'
            self.assertEqual(source.count(inserted), 1)
            for replacement in ('', inserted + inserted, indent + 'stage = token\\n'):
                altered = source.replace(inserted, replacement, 1)
                self.assertNotEqual(altered, source)
                with self.subTest(stage=stage, replacement=replacement), self.assertRaises(RuntimeError):
                    assert_host_prepare_contract(altered, original)
            altered = strict.once(source, inserted + anchor + '\\n', anchor + '\\n' + inserted)
            with self.subTest(stage=stage, moved_after_guard=True), self.assertRaises(RuntimeError):
                assert_host_prepare_contract(altered, original)

    def test_diagnostics_cannot_hide_execution_guard_reset_timeout_cleanup_or_privacy_changes(self):
        source, original = self.source_pair('L08HostProbe.kt.in')
        assert_host_prepare_contract(source, original)
        changes = (
            ('check(owned == null && !captured && mounts == 0)', 'check(true)'),
            ('check(koin.get<RoomTransport>() === transport)', 'check(true)'),
            ('check(koin.get<SettingsStore>().languageOverride.first() == null)',
             'koin.get<SettingsStore>().setLanguageOverride(null)'),
            ('hostSeed = 91L', 'hostSeed = 92L'),
            ('val frozen = session.freezeAdmissions()', 'val frozen = Result.Success(emptyList())'),
            ('check(frozen is Result.Success && frozen.data == room.members.value)', 'check(true)'),
            ('await { value.waiting() && room.offeredPeerCount == 5 }', 'await { true }'),
            ('check(room.readyPeerCount == 0 && room.committedPeerCount == 0)', 'check(true)'),
            ('room.releaseReady(value.protocol(), value.accepts)', 'Unit'),
            ('await { value.started() && room.snapshottedPeerCount == 5 && direction != null }', 'await { true }'),
            ('check(room.committedPeerCount == 5 && room.admissionClosures == 1)', 'check(true)'),
            ('withTimeout(20_000)', 'withTimeout(200_000)'),
            ('withTimeout(60_000)', 'withTimeout(600_000)'),
            ('throw cancelled', 'return@launch'),
            ('if (finished) observerScope.cancel()', 'if (finished) Unit'),
            ('put("stage", stage)', 'put("stage", token)'),
            ('put("stage", stage)', 'put("stage", stage); put("exception", "synthetic-private")'),
            ('checkNotNull(witness).activeAck()', 'Unit'),
        )
        for before, after in changes:
            self.assertEqual(source.count(before), 1)
            altered = source.replace(before, after, 1)
            self.assertNotEqual(altered, source)
            with self.subTest(mutation=before, replacement=after), self.assertRaises(RuntimeError):
                assert_host_prepare_contract(altered, original)

    def test_parser_contract_rejects_relaxed_failure_scope_unknown_stage_and_success_laundering(self):
        source, original = self.source_pair('l08_receipts.py')
        assert_host_prepare_parser_contract(source, original)
        changes = (
            (HOST_PREPARE_ACTION_RESTRICTION, ''),
            ("    'prepare-initial-state',", "    'prepare-unknown',"),
            ("value.get('status') == 'FAIL'", "value.get('status') in {'FAIL', 'PASS'}"),
            ("value['schema_version'] == 1 and value['status'] == 'PASS' and",
             "value['schema_version'] == 1 and value['status'] in {'PASS', 'FAIL'} and"),
        )
        for before, after in changes:
            self.assertEqual(source.count(before), 1)
            altered = source.replace(before, after, 1)
            self.assertNotEqual(altered, source)
            with self.subTest(mutation=before), self.assertRaises(RuntimeError):
                assert_host_prepare_parser_contract(altered, original)
'''

control = once(originals['test_l08_functional_copy.py'], 'import ast\n', 'import ast\nimport copy\n')
control = once(control, 'import unittest\n', 'import unittest\nfrom unittest import mock\n')
control = once(control, 'import l08_functional_receipts as functional_receipts\n',
    'import l08_functional_receipts as functional_receipts\nimport l08_receipts as host_receipts\n'
    'from test_l08_host_receipts import host_matrix\n'
    'from test_l08_diagnostics import failure_record as storage_failure_record\n'
    'from test_native_readiness import TOKEN, framework_inventory\n')
control = once(control, '\n\ndef sha(path):\n', helpers + '\n\ndef sha(path):\n')
clone_anchor = "                elif row['path'] not in changed:\n"
clone_extension = (
    "                elif row['path'] == 'L08HostProbe.kt.in':\n"
    "                    assert_host_prepare_contract((HERE / row['path']).read_text(),\n"
    "                                                 (CANONICAL / row['path']).read_text())\n"
    "                elif row['path'] == 'l08_receipts.py':\n"
    "                    assert_host_prepare_parser_contract((HERE / row['path']).read_text(),\n"
    "                                                        (CANONICAL / row['path']).read_text())\n"
)
control = once(control, clone_anchor, clone_extension + clone_anchor)
control = once(control, "\n\nif __name__ == '__main__': unittest.main()\n",
               tests + "\n\nif __name__ == '__main__': unittest.main()\n")
for name, source in (('l08_receipts.py', receipts), ('test_l08_functional_copy.py', control)):
    ast.parse(source, filename=name)  # Syntax inspection only, never import/execute controls.

contents = {'L08HostProbe.kt.in': probe, 'l08_receipts.py': receipts, 'test_l08_functional_copy.py': control}
for name in [*contents, 'held-diagnostic.patch', 'author-input-identities.json']:
    if (OUT / name).exists():
        raise RuntimeError('Refuse to overwrite held source: ' + name)
for name, source in contents.items():
    (OUT / name).write_text(source)
patch = ''.join(''.join(difflib.unified_diff(originals[name].splitlines(True), contents[name].splitlines(True),
    fromfile='a/' + str((BASE / name).relative_to(ROOT)),
    tofile='b/' + str((BASE / name).relative_to(ROOT)))) for name in originals)
(OUT / 'held-diagnostic.patch').write_text(patch)
identities = {'status': 'HELD_UNEXECUTED', 'stages': len(BOUNDARIES),
    'canonical_inputs': [{'path': str((BASE / name).relative_to(ROOT)),
        'sha256': hashlib.sha256(source.encode()).hexdigest(), 'bytes': len(source.encode())}
        for name, source in originals.items()],
    'held_outputs': [{'path': str((OUT / name).relative_to(ROOT)),
        'sha256': hashlib.sha256(source.encode()).hexdigest(), 'bytes': len(source.encode())}
        for name, source in contents.items()],
    'source_only_reversal_equal': True, 'python_ast_only': True, 'tests_run': False,
    'native_execution': False, 'canonical_paths_written': []}
(OUT / 'author-input-identities.json').write_text(json.dumps(identities, indent=2) + '\n')
print(json.dumps(identities, indent=2))
