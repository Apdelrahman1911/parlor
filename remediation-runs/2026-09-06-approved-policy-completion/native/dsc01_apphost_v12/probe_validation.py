"""Fail-closed bounded evidence validators; synthetic fixtures are not runtime proof."""
import json
import math
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
SAMPLING_COORDINATES = {'samplingWindow', 'samplingIndex', 'samplingElapsedMilliseconds'}
SAMPLING_KEYS = SAMPLING_COORDINATES | {'samplingContext'}
NATIVE_BOOLEANS = {'sameOuterController', 'sameComposeController', 'sameOuterView', 'sameComposeView',
                  'childParentIsOuter', 'singleComposeChild', 'directSubview', 'sameWindow'}
NATIVE_KEYS = NATIVE_BOOLEANS | {'attachments', 'disposals', 'outerDirection'}
RECTANGLES = {'windowBounds', 'outerBounds', 'composeBounds', 'outerInWindow', 'composeInWindow', 'composeFrameInOuter'}
SAFE_AREAS = {'windowSafeArea', 'outerSafeArea', 'composeSafeArea'}
WINDOW_PLAN = ((0, 'foreground'), (0, 'foreground'), (0, 'landscape'), (0, 'portrait'),
               (1, 'foreground'), (1, 'foreground'), (1, 'landscape'), (1, 'portrait'),
               (2, 'foreground'), (2, 'foreground'), (3, 'foreground'), (3, 'foreground'))


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


def geometry(value):
    require(isinstance(value, dict) and set(value) == RECTANGLES | SAFE_AREAS | {'orientation'},
            'Unexpected raw UIKit geometry schema')
    for key in RECTANGLES | SAFE_AREAS:
        row = value[key]
        require(isinstance(row, list) and len(row) == 4 and all(
            type(number) in (int, float) and math.isfinite(number) and abs(number) <= 100000 for number in row),
            'Non-finite, unbounded, or non-numeric raw UIKit geometry')
        if key in RECTANGLES:
            require(row[2] > 0 and row[3] > 0, 'Raw view/window rectangle is empty')
        else:
            require(all(number >= 0 for number in row), 'Negative UIKit safe-area inset')
    require(value['orientation'] in {'portrait', 'landscape', 'other'}, 'Unknown actual UIWindowScene orientation')


def geometry_fills_window(value, orientation=None):
    """Independent raw-number oracle, not acceptance of Swift's compact `g`."""
    geometry(value)
    equal = lambda left, right: all(abs(a - b) <= 0.5 for a, b in zip(left, right))
    bounds = value['windowBounds']
    local = [0, 0, bounds[2], bounds[3]]
    insets = value['windowSafeArea']
    actual = value['orientation']
    return (equal(value['outerBounds'], local) and equal(value['composeBounds'], local) and
            equal(value['outerInWindow'], bounds) and equal(value['composeInWindow'], bounds) and
            equal(value['composeFrameInOuter'], value['outerBounds']) and
            equal(value['outerSafeArea'], insets) and equal(value['composeSafeArea'], insets) and
            any(inset > 0.5 for inset in insets) and insets[0] + insets[2] < bounds[3] and
            insets[1] + insets[3] < bounds[2] and
            ((actual == 'portrait' and bounds[3] > bounds[2]) or
             (actual == 'landscape' and bounds[2] > bounds[3])) and
            (orientation is None or actual == orientation))


def native(event):
    value = event['native']
    before = event['phase'] == 'before_main'
    require(isinstance(value, dict) and set(value) == NATIVE_KEYS | (set() if before else {'geometry'}),
            'Unexpected actual LocalUIViewController/outer identity schema')
    for key in NATIVE_BOOLEANS:
        require(type(value[key]) is bool, 'Native controller/view/window identity is not a boolean')
    require(all(type(value[key]) is int and 0 <= value[key] <= 16 for key in ('attachments', 'disposals')),
            'Unbounded actual Compose controller attach/dispose counter')
    require(value['outerDirection'] in {'unavailable', 'force_ltr', 'force_rtl', 'unspecified', 'other'},
            'Unknown outer UIKit semantic direction')
    if before:
        require(not any(value[key] for key in NATIVE_BOOLEANS) and value['attachments'] == 0 and
                value['disposals'] == 0 and value['outerDirection'] == 'unavailable',
                'Native observer must not run or load a view before original App startup')
    else:
        geometry(value['geometry'])
        require(all(value[key] for key in NATIVE_BOOLEANS) and value['attachments'] == 1 and
                value['disposals'] == 0 and value['outerDirection'] != 'unavailable',
                'Actual outer/Compose controller, views, containment or UIWindow changed/disposed')
    return value


def common(report, scenario):
    require(isinstance(report, dict) and set(report) == {'schemaVersion', 'runToken', 'scenario', 'completed', 'observations'},
            'Unexpected complete report schema')
    require(report['schemaVersion'] == 6 and report['scenario'] == scenario and report['completed'] is True and
            isinstance(report['runToken'], str) and UUID.fullmatch(report['runToken']), 'Wrong/incomplete scenario binding')
    events = report['observations']
    require(isinstance(events, list) and 3 <= len(events) <= 256, 'Unexpected scenario event count')
    starts, completed = [], []
    rows = []
    for index, event in enumerate(events):
        require(isinstance(event, dict) and set(event) == {'ordinal', 'phase', 'fixture', 'boot', 'preferences',
                'nativeDirection', 'native', 'controllerCreations', 'composition', 'osDisposition'} | SAMPLING_KEYS,
                'Unexpected event schema')
        require(event['ordinal'] == index + 1 and event['phase'] in {'before_main', 'sample', 'window_sample', 'complete'} and
                event['fixture'] in {'fresh', 'continue', 'previous-ar', 'existing'} and
                isinstance(event['boot'], str) and UUID.fullmatch(event['boot']), 'Invalid observation binding')
        require(all(type(event[key]) is int for key in SAMPLING_COORDINATES), 'Sampling coordinates must be actual integers')
        if event['phase'] == 'window_sample':
            require(scenario in {'whodunit', 'mafia'} and 1 <= event['samplingWindow'] <= 12 and
                    1 <= event['samplingIndex'] <= 6 and 200 <= event['samplingElapsedMilliseconds'] <= 10000,
                    'Unbounded or wrongly scoped passive observation')
            require(event['samplingContext'] in {'foreground', 'landscape', 'portrait'},
                    'Unknown passive observation context')
        else:
            require(all(event[key] == 0 for key in SAMPLING_COORDINATES) and event['samplingContext'] == 'none',
                    'Non-window event impersonates a passive sample')
        require(event['osDisposition'] in OS_STATUSES and
                (scenario == 'os' or event['osDisposition'] == 'not-investigated'), 'Unexpected OS receipt scope')
        preference(event['preferences'])
        observed = composition(event)
        native(event)
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
                                   if key not in {'composition', 'osDisposition', 'native'} | SAMPLING_KEYS} for event, _ in rows])
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
    require(all(geometry_fills_window(event['native']['geometry'], 'portrait') for event, _ in rows
                if event['phase'] != 'before_main'), 'Actual Settings container/child/window bounds or safe areas diverged')
    proof['native_direction_from_actual_local_uiviewcontroller'] = True
    proof['stable_outer_child_and_window'] = True
    return proof


def verify_local(report, game):
    rows, starts = common(report, game)
    require(len(starts) == 1 and starts[0]['fixture'] == 'existing', 'Local continuity requires one actual process, no restore inference')
    initial = starts[0]['preferences']
    require(initial['setting'] == 'system' and initial['ownerPresent'] is False,
            'Local cycle must capture actual System preferences before real-UI or synthetic changes')
    previous = initial['appLanguages']
    samples = [(event, value) for event, value in rows if event['phase'] != 'before_main']
    captured = [(event, value) for event, value in samples if value['checkpointCaptured']]
    require(captured, 'Actual controller/flow/canonical checkpoint never captured')
    baseline_index = captured[0][0]['ordinal']
    complete_index = next(event['ordinal'] for event, _ in samples if event['phase'] == 'complete')
    phase = 'public-intro' if game == 'whodunit' else 'role-assignment'
    in_scope = [(event, value) for event, value in samples if baseline_index <= event['ordinal'] <= complete_index]
    before_capture = [(event, value) for event, value in samples if event['ordinal'] < baseline_index]
    require(all(value['commandOrdinal'] == 0 and value['commandLanguage'] == 'none' and
                value['commandStatus'] == 'none' for _, value in before_capture),
            'A synthetic language invocation preceded the real-Settings local checkpoint')
    require(any(value['settingsMounted'] and value['settingsDirection'] == 'ltr' and
                event['preferences']['setting'] == 'en' and event['preferences']['ownerInstalled'] == 'en' and
                event['preferences']['ownerPrevious'] == previous and event['nativeDirection'] == 'force_ltr'
                for event, value in before_capture), 'Missing actual Settings EN selection before game entry')
    for event, value in in_scope:
        require(geometry_fills_window(event['native']['geometry']),
                'Actual local full-bounds/window safe-area geometry diverged')
        require(all(value[key] is True for key in CONTINUITY_BOOLEANS) and value['disposalsSinceCapture'] == 0 and
                value['surface'] == game + '-local' and value['publicPhase'] == phase,
                'Actual controller/StateFlow/value/phase changed or was disposed; checkpoint cannot be rebased')
        require(value['commandStatus'] not in {'cancelled', 'failed'}, 'Synthetic real-store mutation did not apply')
        require(value['commandOrdinal'] in range(4) and
                value['commandLanguage'] == ('none', 'ar', 'en', 'system')[value['commandOrdinal']],
                'Unexpected local command order or setup mutation')
    command_ordinals = [value['commandOrdinal'] for _, value in in_scope]
    require(command_ordinals == sorted(command_ordinals), 'Synthetic command counter moved backwards')
    require(in_scope[0][1]['commandOrdinal'] == 0 and in_scope[0][1]['commandLanguage'] == 'none' and
            in_scope[0][1]['commandStatus'] == 'none' and
            in_scope[0][1]['backgroundSinceCapture'] == 0 and in_scope[0][1]['foregroundSinceCapture'] == 0,
            'Canonical checkpoint must precede every synthetic command and lifecycle cycle')
    windows = [(event, value) for event, value in samples if event['phase'] == 'window_sample']
    require(len(windows) == 72 and {event['samplingWindow'] for event, _ in windows} == set(range(1, 13)) and
            all(baseline_index < event['ordinal'] < complete_index for event, _ in windows),
            'Eight lifecycle plus four orientation windows require exactly 72 passive samples')
    require([(event['samplingWindow'], event['samplingIndex']) for event, _ in windows] ==
            [(window, index) for window in range(1, 13) for index in range(1, 7)],
            'Passive windows must remain in their actual observation/publication order')
    for ordinal, language in ((0, 'en'), (1, 'ar'), (2, 'en'), (3, 'system')):
        command_language, status = ('none', 'none') if ordinal == 0 else (language, 'applied')
        relevant = [(event, value) for event, value in in_scope if value['commandOrdinal'] == ordinal and
                    value['commandLanguage'] == command_language and value['commandStatus'] == status]
        require(relevant, 'The real-Settings baseline or a bounded real-store invocation is missing')
        required_callbacks = ordinal + 1
        expected_language = initial['preferredLanguage'] if language == 'system' else language
        def correct_preference(value):
            if value['setting'] != language or value['preferredLanguage'] != expected_language:
                return False
            if language == 'system':
                return value['ownerPresent'] is False and value['appLanguages'] == previous
            return (value['ownerPresent'] is True and value['ownerInstalled'] == language and
                    value['appLanguages'] == [language] and value['ownerPrevious'] == previous)
        settled = [(event, value) for event, value in relevant if value['backgroundSinceCapture'] >= required_callbacks and
                   value['foregroundSinceCapture'] >= required_callbacks]
        require(settled, 'Missing actual background/foreground observation for this language action')
        # Include the FIRST observed callback completion and every later sample,
        # not just samples whose directions happen to match. Divergence cannot
        # be hidden by waiting for one lucky correct final value.
        require(all(correct_preference(event['preferences']) and
                    value['gameDirection'] == ('rtl' if expected_language == 'ar' else 'ltr') and
                    event['nativeDirection'] == ('force_rtl' if expected_language == 'ar' else 'force_ltr')
                    for event, value in settled), 'Post-foreground preference/native/Compose direction diverged')
        for window_id, (command_id, context) in enumerate(WINDOW_PLAN, 1):
            if command_id != ordinal:
                continue
            train = [(event, value) for event, value in windows if event['samplingWindow'] == window_id]
            require(len(train) == 6 and [event['samplingIndex'] for event, _ in train] == list(range(1, 7)),
                    'A passive window was missing, duplicated, reordered, or truncated')
            require(all((event, value) in settled for event, value in train),
                    'Passive window lacks its own completed lifecycle/requested-language witness')
            require(all(event['samplingContext'] == context and
                        geometry_fills_window(event['native']['geometry'], 'landscape' if context == 'landscape' else 'portrait')
                        for event, _ in train), 'Raw UIKit geometry does not match its planned portrait/landscape window')
            elapsed = [0] + [event['samplingElapsedMilliseconds'] for event, _ in train]
            require(all(right - left >= 200 for left, right in zip(elapsed, elapsed[1:])),
                    'Passive samples were not separated by the bounded nonblocking interval')
        geometry_rows = [(event, value) for event, value in windows if
                         value['commandOrdinal'] == ordinal and event['samplingContext'] in {'landscape', 'portrait'}]
        if geometry_rows:
            baseline = [(event, value) for event, value in windows if value['commandOrdinal'] == ordinal and
                        event['samplingContext'] == 'foreground'][-1][1]
            require(all(value['backgroundSinceCapture'] == baseline['backgroundSinceCapture'] and
                        value['foregroundSinceCapture'] == baseline['foregroundSinceCapture'] for _, value in geometry_rows),
                    'Orientation unexpectedly manufactured a background/foreground lifecycle cycle')
    require(in_scope[-1][1]['commandOrdinal'] == 3, 'Unexpected extra local language commands')
    return dict(status='PASS', actual_process_boots=1, stable_public_phase=phase,
                actual_controller_reference=True, actual_canonical_flow_reference=True,
                actual_canonical_reference_and_value=True, direct_compose_direction=True,
                initial_english_selection='Actual App Settings UI, zero synthetic commands before checkpoint',
                language_changes='AR/EN/System synthetic calls to actual production Koin SettingsStore, not an in-game Settings route',
                actual_background_foreground_cycles=4, passive_windows=12, passive_samples=72,
                foreground_passive_windows=8, portrait_landscape_passive_windows=4,
                native_direction_from_actual_local_uiviewcontroller=True,
                stable_outer_child_views_and_window=True, raw_full_bounds_and_window_safe_areas=True,
                geometry_directions=['en-ltr', 'ar-rtl'], geometry_orientations=['portrait', 'landscape'],
                all_post_foreground_samples_must_agree=True, session_disposals=0,
                observer_limitation='Overlay and initial/final publications may perturb SwiftUI; no display publication within each six-sample window',
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
                value['settingsMounted'] and value['settingsDirection'] == 'rtl' and
                geometry_fills_window(event['native']['geometry'], 'portrait')]
    require(explicit, 'Actual OS preference was not retained under actual Arabic Settings selection')
    previous = explicit[-1][0]['preferences']['ownerPrevious']
    require(len(starts) >= 2 and starts[-1]['preferences']['setting'] == 'system' and
            starts[-1]['preferences']['ownerPresent'] is False and starts[-1]['preferences']['appLanguages'] == previous,
            'Actual OS preference does not survive System selection and a new before-App restart witness')
    restored = [(event, value) for event, value in rows if event['ordinal'] > explicit[-1][0]['ordinal'] and
                event['phase'] != 'before_main' and event['preferences']['setting'] == 'system' and
                event['preferences']['appLanguages'] == previous and event['preferences']['ownerPresent'] is False and
                event['nativeDirection'] == 'force_ltr' and geometry_fills_window(event['native']['geometry'], 'portrait')]
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
