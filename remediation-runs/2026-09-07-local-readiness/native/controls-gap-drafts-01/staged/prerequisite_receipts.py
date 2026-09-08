"""V8 bounded receipt checks. Synthetic inputs test parsers, never prove runtime.

The caller must ALSO require the unchanged exact-five-XCTest and original probe
validators. A printed geometry-contract receipt alone is not a passing XCTest.
"""
import json
import math


def records(log, prefix, *, maximum, row_bytes, total_bytes):
    rows = []
    size = 0
    for line in log.splitlines():
        if not line.startswith(prefix):
            continue
        text = line[len(prefix):]
        length = len(text.encode())
        size += length
        if length > row_bytes or size > total_bytes or len(rows) >= maximum:
            raise RuntimeError('V8 public diagnostic bounds exceeded: ' + prefix)
        value = json.loads(text)
        if not isinstance(value, dict):
            raise RuntimeError('V8 public diagnostic must be an object: ' + prefix)
        rows.append(value)
    return rows


def rectangle(value):
    if (not isinstance(value, dict) or value.get('present') is not True or
            value.get('validFiniteRectangle') is not True):
        raise RuntimeError('Start requires a measured valid finite rectangle')
    coordinates = value.get('rectangle')
    if (not isinstance(coordinates, list) or len(coordinates) != 4 or
            any(type(v) not in (int, float) or not math.isfinite(v) or abs(v) > 16384 for v in coordinates) or
            coordinates[2] <= 0 or coordinates[3] <= 0):
        raise RuntimeError('Start rectangle shape or numeric bounds invalid')
    return coordinates


def near(first, second):
    return all(abs(a - b) <= 0.5 for a, b in zip(first, second))


def verify_start(log):
    contract = records(log, 'DSC01_MAFIA_START_GEOMETRY_CONTRACT ', maximum=1, row_bytes=256, total_bytes=256)
    if contract != [dict(schemaVersion=1, cases=16, passed=True)]:
        raise RuntimeError('Actual Swift Mafia Start geometry contract missing')
    rows = records(log, 'DSC01_MAFIA_START_METADATA ', maximum=96, row_bytes=4096, total_bytes=196608)
    if not 10 <= len(rows) <= 96:
        raise RuntimeError('Incomplete Mafia Start receipt sequence')
    previous_time = -1
    for ordinal, row in enumerate(rows, 1):
        if (set(row) != {'schemaVersion', 'ordinal', 'kind', 'elapsedMilliseconds', 'fields'} or
                row['schemaVersion'] != 1 or type(row['ordinal']) is not int or row['ordinal'] != ordinal or
                type(row['elapsedMilliseconds']) is not int or row['elapsedMilliseconds'] < previous_time or
                not isinstance(row['fields'], dict)):
            raise RuntimeError('Malformed or reordered Mafia Start envelope')
        previous_time = row['elapsedMilliseconds']
    if (rows[0]['kind'] != 'setup' or rows[0]['fields'] !=
            dict(scenario='mafia', surface='mafia-local', publicPhase='setup')):
        raise RuntimeError('Actual canonical public Setup prerequisite missing')
    search = rows[1:-3]
    if (not 6 <= len(search) <= 84 or len(search) % 6 or any(row['kind'] != 'search' for row in search) or
            [row['kind'] for row in rows[-3:]] != ['before-activation', 'after-activation', 'successor']):
        raise RuntimeError('Start activation must occur once after complete readiness windows')
    for index, row in enumerate(search):
        if (row['fields'].get('window'), row['fields'].get('index')) != (index // 6 + 1, index % 6 + 1):
            raise RuntimeError('Start readiness window is partial/reordered')
    before = rows[-3]['fields']
    required = dict(targetCountCapped16=1, overlayCountCapped16=1, hittable=True, enabled=True,
                    foreground=True, alertPresent=False, keyboardPresent=False)
    for sample in [row['fields'] for row in search[-6:]] + [before]:
        if any(type(sample.get(k)) is not type(v) or sample.get(k) != v for k, v in required.items()):
            raise RuntimeError('Start activation lacks stable enabled/hittable foreground prerequisite')
        for key in ('target', 'viewport', 'overlay'):
            if not near(rectangle(sample.get(key)), rectangle(before.get(key))):
                raise RuntimeError('Start target/app/overlay moved within final observation window')
    target = rectangle(before.get('target'))
    viewport = rectangle(before.get('viewport'))
    overlay = rectangle(before.get('overlay'))
    if not near(target, rectangle(before.get('activationTargetFrame'))):
        raise RuntimeError('Start activation frame drift')
    for key in ('normalizedPoint', 'plannedPoint', 'sampledCoordinatePoint'):
        point = before.get(key)
        if (not isinstance(point, list) or len(point) != 2 or
                any(type(v) not in (int, float) or not math.isfinite(v) for v in point)):
            raise RuntimeError('Start coordinate observation absent/nonfinite')
    nx, ny = before['normalizedPoint']
    x, y = before['plannedPoint']
    if (not 0 < nx < 1 or not 0 < ny < 1 or
            not near([target[0] + nx * target[2], target[1] + ny * target[3]], [x, y]) or
            not near(before['sampledCoordinatePoint'], [x, y]) or
            not viewport[0] + 8 < x < viewport[0] + viewport[2] - 8 or
            not max(viewport[1] + 48, overlay[1] + overlay[3] + 8) < y < viewport[1] + viewport[3] - 48):
        raise RuntimeError('Start coordinate does not match safe measured target-relative point')
    interior = rectangle(before.get('visibleInterior'))
    if not interior[0] <= x <= interior[0] + interior[2] or not interior[1] <= y <= interior[1] + interior[3]:
        raise RuntimeError('Start planned point is outside the retained visible interior')
    final = rows[-1]['fields']
    if (final.get('targetCountCapped16') != 0 or final.get('foreground') is not True or
            final.get('alertPresent') is not False or final.get('keyboardPresent') is not False or
            final.get('gameDirection') != 'ltr' or final.get('publicContext') !=
            dict(scenario='mafia', surface='mafia-local', publicPhase='role-assignment')):
        raise RuntimeError('Canonical RoleAssignment and public Start disappearance not proven')
    return dict(swift_geometry_contract_cases=16, search_windows=len(search) // 6,
                physical_start_activations=1, canonical_role_assignment_observed=True,
                limitation='Coordinate receipt is a sample, not attested OS event location/callback delivery.')


def verify_os_prerequisite(log):
    rows = records(log, 'DSC01_OS_PREREQUISITE ', maximum=96, row_bytes=8192, total_bytes=196608)
    stages = {'ownership', 'settingsRoot', 'general', 'languageRegion', 'initialEnglishPrimary',
              'addLanguage', 'searchArabic', 'selectArabic', 'retainEnglishPrimary', 'finalPreferredLanguages'}
    if not rows:
        raise RuntimeError('Actual OS multilingual prerequisite not executed')
    for ordinal, row in enumerate(rows, 1):
        if (set(row) != {'schemaVersion', 'ordinal', 'stage', 'status', 'fields'} or row['schemaVersion'] != 1 or
                type(row['ordinal']) is not int or row['ordinal'] != ordinal or row['stage'] not in stages or
                row['status'] not in {'observe', 'before-activation', 'after-activation', 'PASS'} or
                not isinstance(row['fields'], dict)):
            raise RuntimeError('OS prerequisite blocked/malformed/reordered; no inferred OS success')
    ownership = rows[0]
    if (ownership['stage'], ownership['status'], ownership['fields']) != ('ownership', 'PASS',
            dict(sameExpectedSimulator=True, ownedSyntheticDeviceName=True, debugSimulatorBuild=True)):
        raise RuntimeError('Missing fresh simulator ownership precondition')
    for stage in ('general', 'languageRegion', 'addLanguage', 'searchArabic', 'selectArabic', 'retainEnglishPrimary'):
        actions = [row['status'] for row in rows if row['stage'] == stage and row['status'].endswith('activation')]
        if actions != ['before-activation', 'after-activation']:
            raise RuntimeError('OS semantic step missing or activated repeatedly: ' + stage)
    initial = [row for row in rows if row['stage'] == 'initialEnglishPrimary' and row['status'] == 'PASS']
    if (len(initial) != 1 or initial[0]['fields'].get('englishPrimaryObserved') is not True or
            initial[0]['fields'].get('arabicObserved') is not False):
        raise RuntimeError('Fresh English-primary/no-Arabic baseline not observed')
    final = rows[-1]
    if (final['stage'] != 'finalPreferredLanguages' or final['status'] != 'PASS' or
            any(final['fields'].get(k) is not True for k in
                ('englishPrimaryObserved', 'arabicObserved', 'languageRegionTitleObserved', 'foreground',
                 'languageRegionTitleHittable', 'englishPrimaryHittable', 'arabicPreferredRowObserved',
                 'addLanguageSuccessorHittableEnabled')) or final['fields'].get('keyboardPresent') is not False or
            final['fields'].get('searchFieldCountCapped16') != 0 or final['fields'].get('alertCountCapped16') != 0):
        raise RuntimeError('Actual English-primary + Arabic preferred list not proven')
    english = rectangle(final['fields'].get('englishPrimaryFrame'))
    arabic = rectangle(final['fields'].get('arabicPreferredRowFrame'))
    add = rectangle(final['fields'].get('addLanguageSuccessorFrame'))
    if english[1] + english[3] > arabic[1] + 1 or arabic[1] + arabic[3] > add[1] + 1:
        raise RuntimeError('Arabic must be in actual ordered preferred list, not an overlaid picker result')
    return dict(status='PASS', actual_settings_actions=True, english_remains_primary=True,
                arabic_added=True, rows=len(rows),
                limitation='Prerequisite only. Original per-app Settings/AR-System/restart probe remains mandatory.')


def verify_prerequisite_receipts(log):
    return dict(mafia_start=verify_start(log), os_multilingual_prerequisite=verify_os_prerequisite(log))
