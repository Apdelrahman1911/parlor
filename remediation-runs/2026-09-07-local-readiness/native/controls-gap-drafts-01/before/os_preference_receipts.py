"""Explicit replacement of the unsupported nonempty-only OS gate, not its bypass.

Reuses every original settings/local/native/schema validator. Successful OS
proof additionally requires exact post-choice before-App baseline and unchanged
V10/V11 public-action guards, which the runner calls immediately afterwards.
"""
import probe_validation as legacy
import prerequisite_receipts


CONTRACT = dict(schemaVersion=1, shapeCases=14, ownershipCases=6, restoreCases=4, passed=True)
STAGES = ('baseline', 'english', 'system', 'restart')
KEYS = {'schemaVersion', 'stage', 'beforeChoiceSequence', 'beforeChoiceBoot', 'baselineSequence',
        'baselineBoot', 'sequence', 'boot', 'hasAppOverride', 'appLanguages'}
require = legacy.require


def exact_unowned_arabic(preference, present, languages):
    return (preference['setting'] == 'system' and preference['preferredLanguage'] == 'ar' and
            preference['ownerPresent'] is False and preference['ownerInstalled'] == 'none' and
            preference['ownerPreviousPresent'] is False and preference['ownerPrevious'] == [] and
            preference['hasAppOverride'] is present and preference['appLanguages'] == languages)


def verify_os(report, log):
    rows, starts = legacy.common(report, 'os')
    require(type(report['schemaVersion']) is int and all(type(event['ordinal']) is int for event, _ in rows),
            'OS source identity counters must be actual integers')
    contracts = prerequisite_receipts.records(log, 'DSC01_OS_PREFERENCE_DECISION_CONTRACT ', maximum=1, row_bytes=256, total_bytes=256)
    require(contracts == [CONTRACT] and all(type(contracts[0][key]) is type(value) for key, value in CONTRACT.items()),
            'Executed exact preference contract missing or invalid')
    records = prerequisite_receipts.records(log, 'DSC01_OS_PREFERENCE_BASELINE ', maximum=4, row_bytes=2048, total_bytes=8192)
    if rows[-1][0]['osDisposition'] in {'language-control-unavailable', 'selection-unavailable'}:
        require(not records, 'An unavailable public selection cannot invent an OS baseline')
        return legacy.verify_os(report)  # Original classified BLOCKED, never PASS.
    require(rows[-1][0]['osDisposition'] == 'verified-arabic' and len(records) == 4,
            'Real OS investigation and all four exact restoration stages must complete')
    baseline = records[0]
    for index, record in enumerate(records):
        require(isinstance(record, dict) and set(record) == KEYS and type(record['schemaVersion']) is int and
                record['schemaVersion'] == 1 and record['stage'] == STAGES[index], 'Wrong/reordered OS baseline receipt')
        require(all(type(record[key]) is int and 1 <= record[key] <= 256 for key in
                    ('beforeChoiceSequence', 'baselineSequence', 'sequence')), 'Invalid OS observation sequence')
        require(all(isinstance(record[key], str) and legacy.UUID.fullmatch(record[key]) for key in
                    ('beforeChoiceBoot', 'baselineBoot', 'boot')), 'Invalid OS process binding')
        require(type(record['hasAppOverride']) is bool and legacy.valid_languages(record['appLanguages']) and
                record['hasAppOverride'] == bool(record['appLanguages']), 'Malformed OS representation')
        require(all(record[key] == baseline[key] for key in KEYS - {'stage', 'sequence', 'boot'}),
                'OS baseline was silently rebased between stages')
    present, languages = baseline['hasAppOverride'], baseline['appLanguages']
    require(not present or languages[0] == 'ar' or languages[0].startswith('ar-'), 'OS baseline is not Arabic')
    by_sequence = {event['ordinal']: (event, value) for event, value in rows}
    before = by_sequence.get(baseline['beforeChoiceSequence'])
    require(before and before[0]['phase'] == 'sample' and before[0]['boot'] == baseline['beforeChoiceBoot'] and
            before[0]['preferences']['setting'] == 'system' and before[0]['preferences']['ownerPresent'] is False,
            'Pre-selection observation is not bound to actual System/no-owner state')
    require(baseline['sequence'] == baseline['baselineSequence'] and baseline['boot'] == baseline['baselineBoot'] and
            baseline['beforeChoiceSequence'] < baseline['sequence'] and baseline['beforeChoiceBoot'] != baseline['boot'],
            'OS baseline must belong to a fresh post-choice process')
    start = [event for event in starts if event['boot'] == baseline['boot']]
    require(len(start) == 1 and baseline['beforeChoiceSequence'] < start[0]['ordinal'] < baseline['sequence'] and
            exact_unowned_arabic(start[0]['preferences'], present, languages),
            'Post-choice BEFORE-App representation differs from captured baseline')
    require(any(baseline['sequence'] < event['ordinal'] < records[1]['sequence'] and
                event['phase'] == 'sample' and event['boot'] == baseline['boot'] and
                exact_unowned_arabic(event['preferences'], present, languages) and
                event['nativeDirection'] == 'force_rtl' and composition['settingsMounted'] and
                composition['settingsDirection'] == 'rtl' and
                legacy.geometry_fills_window(event['native']['geometry'], 'portrait')
                for event, composition in rows), 'Actual Arabic Settings must be observed before English selection')
    previous_sequence = baseline['beforeChoiceSequence']
    for record in records:
        actual = by_sequence.get(record['sequence'])
        require(actual and actual[0]['phase'] == 'sample' and actual[0]['boot'] == record['boot'] and
                record['sequence'] > previous_sequence, 'Missing/reordered durable OS-stage observation')
        event, composition = actual
        require(event['osDisposition'] == 'opened' and legacy.geometry_fills_window(event['native']['geometry'], 'portrait'),
                'OS stage lacks original accepted invocation or real portrait geometry')
        preference = event['preferences']
        if record['stage'] == 'english':
            require(event['boot'] == baseline['boot'] and preference['setting'] == 'en' and
                    preference['preferredLanguage'] == 'en' and preference['hasAppOverride'] is True and
                    preference['appLanguages'] == ['en'] and preference['ownerPresent'] is True and
                    preference['ownerInstalled'] == 'en' and preference['ownerPreviousPresent'] is present and
                    preference['ownerPrevious'] == languages and event['nativeDirection'] == 'force_ltr' and
                    composition['settingsMounted'] and composition['settingsDirection'] == 'ltr',
                    'Actual English owner did not preserve exact OS presence/value or Compose/native direction')
        else:
            require(exact_unowned_arabic(preference, present, languages) and event['nativeDirection'] == 'force_rtl',
                    'Exact OS representation or Arabic native direction was not preserved')
            if record['stage'] != 'baseline':
                require(composition['settingsMounted'] and composition['settingsDirection'] == 'rtl',
                        'Actual Arabic Settings composition missing after System/restart')
            if record['stage'] == 'restart':
                restarted = [event for event in starts if event['boot'] == record['boot']]
                require(record['boot'] != baseline['boot'] and len(restarted) == 1 and
                        previous_sequence < restarted[0]['ordinal'] < record['sequence'] and
                        exact_unowned_arabic(restarted[0]['preferences'], present, languages),
                        'Fresh BEFORE-App restart did not preserve exact OS representation')
            else:
                require(event['boot'] == baseline['boot'], 'Unexpected process replacement before explicit restart')
        previous_sequence = record['sequence']
    final = rows[-1][0]
    require(final['ordinal'] > records[-1]['sequence'] and final['boot'] == records[-1]['boot'] and
            exact_unowned_arabic(final['preferences'], present, languages), 'Terminal OS state changed after restart proof')
    # The log is not an action oracle on its own: V10/V11 guards are still
    # mandatory. Bind this additional sequence to the actual one-tap record.
    lines = log.splitlines()
    actions = prerequisite_receipts.records(log, 'DSC01_OS_APP_INTERACTION ', maximum=128, row_bytes=8192, total_bytes=262144)
    require(all(isinstance(action.get('fields'), dict) for action in actions), 'Malformed public-action receipt')
    action_lines = [index for index, line in enumerate(lines) if line.startswith('DSC01_OS_APP_INTERACTION ')]
    arabic = [index for index, action in zip(action_lines, actions) if action.get('stage') == 'arabic' and
               action.get('status') == 'after-activation' and action.get('fields', {}).get('control') == 'arabic']
    stages = [index for index, line in enumerate(lines) if line.startswith('DSC01_OS_PREFERENCE_BASELINE ')]
    reporting = [index for index, action in zip(action_lines, actions) if action.get('stage') == 'markerControls']
    contract_lines = [index for index, line in enumerate(lines) if line.startswith('DSC01_OS_PREFERENCE_DECISION_CONTRACT ')]
    require(len(arabic) == 1 and len(contract_lines) == 1 and reporting and
            contract_lines[0] < arabic[0] < stages[0] <= stages[-1] < reporting[0],
            'Exact baseline/stages must follow the real Arabic action and precede terminal reporting')
    if present:
        require(legacy.verify_os(report)['status'] == 'PASS', 'Original PRESENT OS preservation oracle failed')
    return dict(status='PASS', actual_settings_application_opened=True, actual_arabic_selection=True,
                actual_english_override_and_system_restoration=True, restart_preserves_os_preference=True,
                observed_os_preference_representation='PRESENT' if present else 'ABSENT',
                exact_presence_and_array_restored=True, post_choice_before_app_baseline=True,
                actual_present_os_preference_coverage=present,
                original_present_oracle_executed=present, unchanged_v10_v11_action_guards_required=True,
                applicability='Owned fresh iOS simulator; actual OS representation is reported, never inferred.',
                physical_device_claim=False)


def verify_probe(reports, legacy_validator, log):
    """Original complete matrix orchestration; only OS representation gate differs."""
    require(set(reports) == {'settings', 'whodunit', 'mafia', 'os'}, 'Every executed scenario needs its own durable receipt')
    require(len({value.get('runToken') for value in reports.values()}) == 1, 'Cross-scenario task token mismatch')
    settings = legacy.verify_settings(reports['settings'], legacy_validator)
    locals_ = {game: legacy.verify_local(reports[game], game) for game in ('whodunit', 'mafia')}
    os_gate = verify_os(reports['os'], log)
    return dict(actual_settings_and_local_sessions_gate='PASS', settings=settings, local_games=locals_, os_settings_gate=os_gate,
                os_unavailability_is_not_a_pass=True, retained_multiplayer_host_gate='NOT_RUN',
                physical_device_evidence=False, store_or_signing_evidence=False)
