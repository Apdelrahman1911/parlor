"""V9 idempotent public prerequisite receipt checks; no runtime is inferred.

V8 Start and actual-language-addition validators remain independently required.
The exact-five-XCTest, original Settings/local-game/OS probes remain mandatory
in the caller. Synthetic receipt fixtures do not prove device or app behavior.
"""
import prerequisite_receipts


def rectangle(fields, key):
    return prerequisite_receipts.rectangle(fields.get(key))


def contained(inner, outer):
    x, y, width, height = inner
    ox, oy, ow, oh = outer
    return ox <= x and oy <= y and x + width <= ox + ow and y + height <= oy + oh


def english_primary_list(fields):
    required_true = ('foreground', 'languageRegionTitleObserved', 'languageRegionTitleHittable',
                     'englishPrimaryObserved', 'englishPrimaryHittable', 'addLanguageSuccessorHittableEnabled')
    if (any(fields.get(key) is not True for key in required_true) or
            fields.get('keyboardPresent') is not False or
            any(type(fields.get(key)) is not int or fields[key] != 0
                for key in ('searchFieldCountCapped16', 'alertCountCapped16'))):
        return False
    counts = [fields.get(key) for key in ('englishPrimaryCellCountCapped16', 'mergedEnglishPrimaryCountCapped16')]
    if any(type(value) is not int or value not in (0, 1) for value in counts) or sum(counts) < 1:
        return False
    try:
        english = rectangle(fields, 'englishPrimaryFrame')
        add = rectangle(fields, 'addLanguageSuccessorFrame')
        viewport = rectangle(fields, 'viewport')
    except RuntimeError:
        return False
    return contained(english, viewport) and contained(add, viewport) and english[1] + english[3] <= add[1] + 1


def preferred_list(fields):
    if (not english_primary_list(fields) or fields.get('arabicObserved') is not True or
            fields.get('arabicPreferredRowObserved') is not True or
            type(fields.get('arabicCellCountCapped16')) is not int or fields['arabicCellCountCapped16'] != 1):
        return False
    try:
        english = rectangle(fields, 'englishPrimaryFrame')
        arabic = rectangle(fields, 'arabicPreferredRowFrame')
        add = rectangle(fields, 'addLanguageSuccessorFrame')
        viewport = rectangle(fields, 'viewport')
    except RuntimeError:
        return False
    return (contained(arabic, viewport) and english[1] + english[3] <= arabic[1] + 1 and
            arabic[1] + arabic[3] <= add[1] + 1)


def initial_disposition(fields):
    if preferred_list(fields):
        return 'already-satisfied'
    if (english_primary_list(fields) and fields.get('arabicObserved') is False and
            all(type(fields.get(key)) is int and fields[key] == 0
                for key in ('arabicCellCountCapped16', 'arabicTextCountCapped16'))):
        return 'requires-arabic'
    return 'blocked'


def verify_os_prerequisite(log):
    contracts = prerequisite_receipts.records(log, 'DSC01_OS_PREREQUISITE_DECISION_CONTRACT ',
        maximum=1, row_bytes=256, total_bytes=256)
    if contracts != [dict(schemaVersion=1, cases=24, passed=True)]:
        raise RuntimeError('Actual Swift idempotent prerequisite decision contract missing')
    rows = prerequisite_receipts.records(log, 'DSC01_OS_PREREQUISITE ', maximum=96, row_bytes=8192, total_bytes=196608)
    ordered_stages = ['ownership', 'settingsRoot', 'general', 'languageRegion', 'initialEnglishPrimary',
                      'addLanguage', 'searchArabic', 'selectArabic', 'retainEnglishPrimary', 'finalPreferredLanguages']
    if not rows:
        raise RuntimeError('Actual public OS prerequisite not executed')
    sequence = []
    for ordinal, row in enumerate(rows, 1):
        if (set(row) != {'schemaVersion', 'ordinal', 'stage', 'status', 'fields'} or row['schemaVersion'] != 1 or
                type(row['ordinal']) is not int or row['ordinal'] != ordinal or row['stage'] not in ordered_stages or
                row['status'] not in {'observe', 'before-activation', 'after-activation', 'PASS'} or
                not isinstance(row['fields'], dict)):
            raise RuntimeError('OS prerequisite blocked/malformed; no inferred OS success')
        if not sequence or sequence[-1] != row['stage']:
            sequence.append(row['stage'])
    ownership = rows[0]
    if ((ownership['stage'], ownership['status']) != ('ownership', 'PASS') or
            set(ownership['fields']) != {'sameExpectedSimulator', 'ownedSyntheticDeviceName', 'debugSimulatorBuild'} or
            any(value is not True for value in ownership['fields'].values())):
        raise RuntimeError('Missing exact owned simulator prerequisite')
    for stage in ('ownership', 'settingsRoot', 'general', 'languageRegion', 'initialEnglishPrimary', 'finalPreferredLanguages'):
        if sum(row['stage'] == stage and row['status'] == 'PASS' for row in rows) != 1:
            raise RuntimeError('Missing/repeated public prerequisite stage: ' + stage)
    initial = next(row['fields'] for row in rows if row['stage'] == 'initialEnglishPrimary' and row['status'] == 'PASS')
    disposition = initial_disposition(initial)
    if disposition == 'blocked' or initial.get('prerequisiteDisposition') != disposition:
        raise RuntimeError('Initial actual public preferred list is incomplete/covered/ambiguous')
    final = rows[-1]
    if final['stage'] != 'finalPreferredLanguages' or final['status'] != 'PASS' or not preferred_list(final['fields']):
        raise RuntimeError('Final actual exposed ordered English-primary/Arabic-preferred list not proven')
    added = disposition == 'requires-arabic'
    expected_sequence = ordered_stages if added else ordered_stages[:5] + [ordered_stages[-1]]
    if sequence != expected_sequence:
        raise RuntimeError('Prerequisite actions absent/reordered or languages mutated on already-satisfied path')
    activation_stages = ('general', 'languageRegion') + (('addLanguage', 'searchArabic', 'selectArabic', 'retainEnglishPrimary') if added else ())
    if any(row['status'].endswith('activation') and row['stage'] not in activation_stages for row in rows):
        raise RuntimeError('Unexpected activation on an observation-only prerequisite stage')
    for stage in activation_stages:
        actions = [row['status'] for row in rows if row['stage'] == stage and row['status'].endswith('activation')]
        if actions != ['before-activation', 'after-activation']:
            raise RuntimeError('OS semantic action absent/repeated: ' + stage)
    if (final['fields'].get('prerequisiteDisposition') != ('arabic-added' if added else 'already-satisfied') or
            final['fields'].get('arabicAddedByThisPrerequisite') is not added):
        raise RuntimeError('Receipt falsely attributes preexisting language to this prerequisite')
    if added:
        # Retain every executed V8 addition-step oracle, not a synthesized proxy.
        prerequisite_receipts.verify_os_prerequisite(log)
    return dict(status='PASS', disposition='arabic-added' if added else 'already-satisfied',
                actual_settings_actions=True, english_remains_primary=True, arabic_added=added,
                preexisting_configuration_verified=not added, swift_decision_contract_cases=24, rows=len(rows),
                limitation='Prerequisite only. Original per-app Settings/AR-System/restart probe remains mandatory; language origin not inferred.')


def verify_public_interaction_receipts(log):
    return dict(mafia_start=prerequisite_receipts.verify_start(log), os_multilingual_prerequisite=verify_os_prerequisite(log))
