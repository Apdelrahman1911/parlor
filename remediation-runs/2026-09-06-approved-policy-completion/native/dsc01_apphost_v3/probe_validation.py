"""Fail-closed bounded evidence validators; synthetic fixtures are not runtime proof."""
import json
import re

UUID = re.compile(r'[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}')
LANGUAGE = re.compile(r'(en|ar)(?:-[A-Za-z0-9]{2,8})*')
PREFERENCE_KEYS = {'setting', 'appLanguages', 'hasAppOverride', 'ownerPresent', 'ownerInstalled',
                   'ownerPreviousPresent', 'ownerPrevious', 'preferredLanguage'}
COMPOSITION_BOOLEANS = {'settingsMounted', 'checkpointCaptured', 'sameController', 'sameCanonicalFlow',
                        'sameCanonicalReference', 'sameCanonicalValue', 'samePublicPhase'}
CONTINUITY_BOOLEANS = COMPOSITION_BOOLEANS - {'settingsMounted'}
COMPOSITION_COUNTERS = {'disposalsSinceCapture', 'backgroundSinceCapture', 'foregroundSinceCapture',
                        'backgroundCallbacks', 'foregroundCallbacks', 'inactiveCallbacks', 'commandOrdinal'}
COMPOSITION_STRINGS = {'settingsDirection', 'surface', 'gameDirection', 'publicPhase', 'commandLanguage', 'commandStatus'}
COMPOSITION_KEYS = COMPOSITION_BOOLEANS | COMPOSITION_COUNTERS | COMPOSITION_STRINGS
OS_STATUSES = {'not-investigated', 'opening', 'opened', 'open-rejected', 'language-control-unavailable',
               'selection-unavailable', 'verified-english'}


def require(condition, reason):
    if not condition:
        raise RuntimeError(reason)


def valid_languages(value):
    return (isinstance(value, list) and len(value) <= 8 and all(
        isinstance(tag, str) and len(tag.encode()) <= 35 and LANGUAGE.fullmatch(tag) for tag in value))


def preference(value):
    require(isinstance(value, dict) and set(value) == PREFERENCE_KEYS, 'Unexpected preference schema')
    require(value['setting'] in {'en', 'ar', 'system'} and value['preferredLanguage'] in {'en', 'ar'} and
            value['ownerInstalled'] in {'none', 'en', 'ar'}, 'Unknown observed preference enum')
    require(valid_languages(value['appLanguages']) and valid_languages(value['ownerPrevious']),
            'Unbounded or non-allowlisted language preference')
    for key in ('hasAppOverride', 'ownerPresent', 'ownerPreviousPresent'):
        require(type(value[key]) is bool, 'Expected actual boolean preference')
    require(value['hasAppOverride'] == bool(value['appLanguages']) and
            value['ownerPreviousPresent'] == bool(value['ownerPrevious']) and
            value['ownerPresent'] == (value['ownerInstalled'] != 'none'), 'Inconsistent preference shape')


def composition(event):
    encoded = event['composition']
    require(isinstance(encoded, str) and len(encoded.encode()) <= 4096, 'Unbounded composition observation')
    value = json.loads(encoded)
    if event['phase'] == 'before_main':
        require(value == {}, 'Do not invoke Kotlin/DI before original Main/App startup')
        return value
    require(isinstance(value, dict) and set(value) == COMPOSITION_KEYS, 'Unexpected composition observation keys')
    for key in COMPOSITION_BOOLEANS:
        require(type(value[key]) is bool, 'Expected direct referential-equality boolean')
    for key in COMPOSITION_COUNTERS:
        require(type(value[key]) is int and 0 <= value[key] <= 512, 'Unbounded composition counter')
    require(value['commandOrdinal'] <= 24, 'Synthetic command ceiling exceeded')
    require(value['settingsDirection'] in {'ltr', 'rtl', 'absent'} and value['gameDirection'] in {'ltr', 'rtl', 'absent'},
            'Unknown direct Compose direction')
    require(value['surface'] in {'none', 'whodunit-local', 'mafia-local'}, 'Unknown actual session surface')
    require(value['publicPhase'] in {'none', 'setup', 'public-intro', 'role-assignment', 'other'}, 'Unknown public phase')
    require(value['commandLanguage'] in {'none', 'en', 'ar', 'system'} and
            value['commandStatus'] in {'none', 'pending', 'applied', 'cancelled', 'failed'}, 'Unknown command receipt')
    require(value['settingsMounted'] == (value['settingsDirection'] != 'absent'), 'Stale Settings-mount observation')
    require((value['surface'] == 'none') == (value['gameDirection'] == 'absent'), 'Stale game-mount observation')
    return value


def common(report, scenario):
    require(isinstance(report, dict) and set(report) == {'schemaVersion', 'runToken', 'scenario', 'completed', 'observations'},
            'Unexpected complete report schema')
    require(report['schemaVersion'] == 3 and report['scenario'] == scenario and report['completed'] is True and
            isinstance(report['runToken'], str) and UUID.fullmatch(report['runToken']), 'Wrong/incomplete scenario binding')
    events = report['observations']
    require(isinstance(events, list) and 3 <= len(events) <= 256, 'Unexpected scenario event count')
    starts, completed = [], []
    rows = []
    for index, event in enumerate(events):
        require(isinstance(event, dict) and set(event) == {'ordinal', 'phase', 'fixture', 'boot', 'preferences',
                'nativeDirection', 'controllerCreations', 'composition', 'osDisposition'}, 'Unexpected event schema')
        require(event['ordinal'] == index + 1 and event['phase'] in {'before_main', 'sample', 'complete'} and
                event['fixture'] in {'fresh', 'continue', 'previous-ar', 'existing'} and
                isinstance(event['boot'], str) and UUID.fullmatch(event['boot']), 'Invalid observation binding')
        require(event['osDisposition'] in OS_STATUSES and
                (scenario == 'os' or event['osDisposition'] == 'not-investigated'), 'Unexpected OS receipt scope')
        preference(event['preferences'])
        observed = composition(event)
        if event['phase'] == 'before_main':
            require(event['controllerCreations'] == 0 and event['nativeDirection'] == 'unavailable', 'Not a before-App witness')
            starts.append(event)
        else:
            require(starts and event['boot'] == starts[-1]['boot'] and event['controllerCreations'] == 1 and
                    event['nativeDirection'] in {'force_ltr', 'force_rtl'}, 'Unexpected root controller/boot/native direction')
            if event['phase'] == 'complete':
                completed.append(event)
        rows.append((event, observed))
    require(len({event['boot'] for event in starts}) == len(starts), 'A process UUID cannot impersonate restart')
    require(len(completed) == 1 and completed[0]['boot'] == starts[-1]['boot'], 'Scenario completion missing/duplicated')
    return rows, starts


def verify_settings(report, legacy_validator):
    rows, starts = common(report, 'settings')
    original = dict(schemaVersion=1, runToken=report['runToken'], completed=True,
                    observations=[{key: value for key, value in event.items()
                                   if key not in {'composition', 'osDisposition'}} for event, _ in rows])
    proof = legacy_validator(original)
    # Verify each actual Settings phase under both directions, not root UIKit
    # direction or a guessed language-to-direction conversion as observation.
    required = [(0, 'ar', 'rtl'), (1, 'system', None), (2, 'en', 'ltr'), (3, 'system', 'rtl')]
    for index, setting, direction in required:
        matches = [(event, value) for event, value in rows if event['boot'] == starts[index]['boot'] and
                   event['phase'] != 'before_main' and event['preferences']['setting'] == setting and
                   value['settingsMounted'] is True]
        require(any(value['settingsDirection'] == (direction or ('rtl' if event['preferences']['preferredLanguage'] == 'ar' else 'ltr'))
                    for event, value in matches), 'Required direct actual-Settings Compose direction missing')
    proof['direct_compose_direction_measurement'] = True
    return proof


def verify_local(report, game):
    rows, starts = common(report, game)
    require(len(starts) == 1 and starts[0]['fixture'] == 'existing', 'Local continuity requires one actual process, no restore inference')
    initial = starts[0]['preferences']
    require(initial['setting'] == 'system' and initial['ownerPresent'] is False,
            'Local cycle must capture actual System preferences before synthetic changes')
    previous = initial['appLanguages']
    samples = [(event, value) for event, value in rows if event['phase'] != 'before_main']
    captured = [(event, value) for event, value in samples if value['checkpointCaptured']]
    require(captured, 'Actual controller/flow/canonical checkpoint never captured')
    baseline_index = captured[0][0]['ordinal']
    complete_index = next(event['ordinal'] for event, _ in samples if event['phase'] == 'complete')
    phase = 'public-intro' if game == 'whodunit' else 'role-assignment'
    in_scope = [(event, value) for event, value in samples if baseline_index <= event['ordinal'] <= complete_index]
    for event, value in in_scope:
        require(all(value[key] is True for key in CONTINUITY_BOOLEANS) and value['disposalsSinceCapture'] == 0 and
                value['surface'] == game + '-local' and value['publicPhase'] == phase,
                'Actual controller/StateFlow/value/phase changed or was disposed; checkpoint cannot be rebased')
        require(value['commandStatus'] not in {'cancelled', 'failed'}, 'Synthetic real-store mutation did not apply')
    require(in_scope[0][1]['commandOrdinal'] == 1 and in_scope[0][1]['commandLanguage'] == 'en' and
            in_scope[0][1]['backgroundSinceCapture'] == 0 and in_scope[0][1]['foregroundSinceCapture'] == 0,
            'Canonical checkpoint was not captured after one EN setup command and before lifecycle cycles')
    for ordinal, language in ((2, 'ar'), (3, 'en'), (4, 'system')):
        relevant = [(event, value) for event, value in in_scope if value['commandOrdinal'] == ordinal and
                    value['commandLanguage'] == language and value['commandStatus'] == 'applied']
        require(relevant, 'One of the bounded real-store language invocations is missing')
        required_callbacks = ordinal - 1
        expected_language = initial['preferredLanguage'] if language == 'system' else language
        def correct_preference(value):
            if value['setting'] != language or value['preferredLanguage'] != expected_language:
                return False
            if language == 'system':
                return value['ownerPresent'] is False and value['appLanguages'] == previous
            return (value['ownerPresent'] is True and value['ownerInstalled'] == language and
                    value['appLanguages'] == [language] and value['ownerPrevious'] == previous)
        settled = [(event, value) for event, value in relevant if value['backgroundSinceCapture'] >= required_callbacks and
                   value['foregroundSinceCapture'] >= required_callbacks and
                   correct_preference(event['preferences']) and
                   value['gameDirection'] == ('rtl' if expected_language == 'ar' else 'ltr') and
                   event['nativeDirection'] == ('force_rtl' if expected_language == 'ar' else 'force_ltr')]
        require(settled, 'Missing actual background/foreground + direct Compose/session continuity after this language action')
        require(any(event['preferences']['setting'] == language for event, _ in settled), 'Live setting differs from acknowledged invocation')
    require(in_scope[-1][1]['commandOrdinal'] == 4, 'Unexpected extra local language commands')
    return dict(status='PASS', actual_process_boots=1, stable_public_phase=phase,
                actual_controller_reference=True, actual_canonical_flow_reference=True,
                actual_canonical_reference_and_value=True, direct_compose_direction=True,
                language_changes='Synthetic calls to actual production Koin SettingsStore, not an in-game Settings route',
                actual_background_foreground_cycles=3, session_disposals=0,
                complete_game_or_persistence_resume_claim=False, physical_lan_claim=False)


def verify_os(report):
    rows, starts = common(report, 'os')
    last = rows[-1][0]['osDisposition']
    require(any(event['osDisposition'] == 'opened' for event, _ in rows), 'No accepted supported OS Settings invocation')
    if last in {'language-control-unavailable', 'selection-unavailable'}:
        return dict(status='BLOCKED', actual_settings_application_opened=True, reason=last,
                    applicability='Shipped CFBundleLocalizations en/ar; actual OS language UI flow remains unverified',
                    no_global_preference_writes=True)
    require(last == 'verified-english', 'OS per-app language investigation did not reach a classified outcome')
    explicit = [(event, value) for event, value in rows if event['phase'] != 'before_main' and
                event['preferences']['setting'] == 'ar' and event['preferences']['ownerInstalled'] == 'ar' and
                event['preferences']['ownerPrevious'] and event['preferences']['ownerPrevious'][0].startswith('en') and
                value['settingsMounted'] and value['settingsDirection'] == 'rtl']
    require(explicit, 'Actual OS preference was not retained under actual Arabic Settings selection')
    previous = explicit[-1][0]['preferences']['ownerPrevious']
    require(len(starts) >= 2 and starts[-1]['preferences']['setting'] == 'system' and
            starts[-1]['preferences']['ownerPresent'] is False and starts[-1]['preferences']['appLanguages'] == previous,
            'Actual OS preference does not survive System selection and a new before-App restart witness')
    restored = [(event, value) for event, value in rows if event['ordinal'] > explicit[-1][0]['ordinal'] and
                event['phase'] != 'before_main' and event['preferences']['setting'] == 'system' and
                event['preferences']['appLanguages'] == previous and event['preferences']['ownerPresent'] is False and
                event['nativeDirection'] == 'force_ltr']
    require(any(value['settingsMounted'] and value['settingsDirection'] == 'ltr' for _, value in restored),
            'System did not return actual Settings composition to OS English')
    return dict(status='PASS', actual_settings_application_opened=True, actual_english_selection=True,
                actual_arabic_override_and_system_restoration=True, restart_preserves_os_preference=True,
                applicability='Owned fresh iOS simulator, current shipped en/ar wrapper only', physical_device_claim=False)


def verify_probe(reports, legacy_validator):
    require(set(reports) == {'settings', 'whodunit', 'mafia', 'os'}, 'Every executed scenario needs its own durable receipt')
    require(len({value.get('runToken') for value in reports.values()}) == 1, 'Cross-scenario task token mismatch')
    settings = verify_settings(reports['settings'], legacy_validator)
    locals_ = {game: verify_local(reports[game], game) for game in ('whodunit', 'mafia')}
    os_gate = verify_os(reports['os'])
    return dict(actual_settings_and_local_sessions_gate='PASS', settings=settings, local_games=locals_, os_settings_gate=os_gate,
                os_unavailability_is_not_a_pass=True, retained_multiplayer_host_gate='NOT_RUN',
                physical_device_evidence=False, store_or_signing_evidence=False)
