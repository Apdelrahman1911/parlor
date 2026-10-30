"""Read one immutable production failure; never build, launch, retry or qualify.

This separate reader leaves the older frozen-result investigation unchanged.
Only two exact synthetic-public Ghamza setup failures are decoded; no private gameplay.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import platform
import math
import re
import stat
import subprocess
import sys
import time
import zipfile

import public_attachments as attachments
import review_frozen_result as frozen

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.ci.native_process_probe import Commands, child_environment

REPOSITORY = 'Apdelrahman1911/parlor'
BRANCH = 'refs/heads/diagnostic/ios-pr296-public-setup-review-2026-10-30'
RUN = 41135053853
ARTIFACT = 13018521901
SOURCE = '40ae511df882cb29070a94ef3c25f95c1b4ba93e'
MERGE = 'bd5c51c8a253971bbb2c1e782e1225cc513a0ee6'
TREE = '1ef599d662d977cf862d88a9783d147ffc598579'
DIGEST = '3102267620e331fbaf0f0bde39b6a4943cde2550e0b5f2a010d87b7ae39998ad'
BYTES = 170017085
MAX_BYTES = 192 * 1024 * 1024
PREFIX = 'parlor/parlor/build/ci-evidence/'
RESULT_PREFIX = PREFIX + 'ios-ui-tests.xcresult/'
CLAIM = '_temp/parlor-verification-41135053853-1-ios-apple-ui-ownership.json'
CLAIM_HASH = '84fc47f6de8c0afff7d8b3660982213efd08ee458b3489054ba0bbd55e3f77eb'
LOG_HASH = '376af3f5075b34351c62598a4ffde41b8d08a84dbd0d21d0fbcf8af195f7252f'
DRIVER_SHA256 = 'c65fe65aee8442aa06f02007b57daaf3e9113f5f61046edb59e361c2e4a6e115'
METHOD = 'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInArabic()'
UDID = '70AAD8D2-5093-4478-A433-437AA1F98E1B'
DEVICE = 'Parlor-ci-e01d4bc252c2405f9d76f7c5ba3c3ac8'
require = frozen.require
sha = lambda raw: hashlib.sha256(raw).hexdigest()
EXPECTED = frozenset('''
ComposeContainerViewControllerTests/testChildUsesFullBoundsAcrossSizesAndOpposingSemanticDirections()
ComposeContainerViewControllerTests/testDefaultAppearanceForwardingReachesTheSameChildExactlyOnce()
ComposeContainerViewControllerTests/testOneStableChildKeepsItsExplicitSemanticsIndependentOfTheOuterView()
ComposeContainerViewControllerTests/testSystemDecorationAndOrientationPoliciesStillBelongToTheChild()
IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInArabic()
IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish()
IOSAppLaunchUITests/testColdLaunchRendersComposeHomeWithoutUnexpectedAlert()
IOSAppLaunchUITests/testKeyboardInsetsOnNameAndRoomCodeInArabic()
IOSAppLaunchUITests/testKeyboardInsetsOnNameAndRoomCodeInEnglish()
IOSAppLaunchUITests/testLastLightLocalPlayerFieldsScrollWithNextAndDismissWithDone()
KeyboardIntroductionDismissalTests/testGenericContinueWithoutIdentifiedTutorialNeverAdmitsATap()
KeyboardIntroductionDismissalTests/testLateIntroductionAfterInitialAbsenceCanBeDismissedExactlyOnce()
KeyboardIntroductionDismissalTests/testMissingInvalidOffscreenOrUpperScreenButtonCannotBeDismissed()
KeyboardIntroductionDismissalTests/testNonhittableIntroductionDoesNotConsumeTheSingleDismissal()
PublicControlFramesTests/testDisabledControlBreaksFrameAdjacency()
PublicControlFramesTests/testFirstFrameNeverSettlesButTheExactAdjacentFrameDoes()
PublicControlFramesTests/testHittabilityInterruptionRequiresTwoFreshFrames()
PublicControlFramesTests/testInvalidOrEmptyGeometryCannotSettleAndBreaksAdjacency()
PublicControlFramesTests/testMissingControlBreaksFrameAdjacency()
PublicControlFramesTests/testMotionIsNotRoundedIntoASettledFrame()
PublicTextValueTests/testMissingOrNonStringValueNeverMatchesEvenAnEmptyExpectation()
PublicTextValueTests/testOnlyTheCompleteExpectedStringMatches()
PublicTextValueTests/testWhitespaceCaseAndArabicCharactersAreNotRewritten()
'''.split())



TITLE = 'Failed public control settlement before tap'
TYPED_TITLES = []  # No new input after opening the failing Ghamza setup.
FAILURE = 'Public setup control must stop scrolling before it is tapped'


FAILURES = {
    METHOD: ('ar', 'Failed public setup transition',
             'One public setup tap must remove the source and expose its destination',
             'Tap "افتح غمزة', 'اسمك'),
    'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish()': (
        'en', TITLE, FAILURE, 'Tap "Open Ghamza', 'Your name'),
}

# Exact public Ghamza tap titles in the immutable original producer log. English
# completes Host/name/Back, then opens the catalog entry again for Join; Arabic
# fails after its first Host tap. No extra gesture or post-Ghamza typing is eligible.
PUBLIC_SETUP_TAPS = {
    METHOD: (
        'Tap "افتح غمزة, غمزة, سيب الموبايل. وركّز في العيون., 3–12 لاعبين, نفس الواي فاي · جهاز لكل لاعب" Button',
        'Tap "افتح غرفة وأظهر الرمز لأصدقائك للانضمام., استضف غرفة, أنشئ الرمز وتولَّ قيادة اللعبة., هاتف لكل لاعب" Button',
    ),
    'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish()': (
        'Tap "Open Ghamza, Ghamza, Phones down. Eyes open., 3–12 players, Same Wi-Fi · own devices" Button',
        'Tap "Open a room and show a code your friends can join., Host a Room, Create the code and lead the game., One phone per player" Button',
        'Tap "Continue and retry the real local-network operation., Continue" Button',
        'Tap "Return to the home screen." Button',
        'Tap "Open Ghamza, Ghamza, Phones down. Eyes open., 3–12 players, Same Wi-Fi · own devices" Button',
        'Tap "Enter the host\'s room code to join their table., Join a Room, Enter the host’s six-character code., One phone per player" Button',
    ),
}

def public_catalog_lines(raw):
    """Only exact source-emitted six-field settlement observations, never labels/values."""
    require(isinstance(raw, str) and len(raw.encode()) <= 1024 * 1024)
    lines = frozen.settlement_lines(dict(emittedOutput=raw))
    require(len(lines) <= 1)
    for line in lines:
        for part in line.removeprefix('Public control settlement: ').split('; '):
            values = re.findall(r'(?:at|querySeconds)=([^, ]+)', part)
            require(len(values) == 2 and all(math.isfinite(float(x)) and float(x) >= 0 for x in values))
            frame = re.search(r'frame=Optional\(\((.*?)\)\)', part)
            if frame:
                require(all(math.isfinite(float(x.strip())) for x in frame.group(1).split(',')))
    return lines

def verify_public_setup_failure(activities, method):
    require(method in FAILURES and activities.get('testIdentifier') == method)
    _, marker, failure, game_tap, field = FAILURES[method]
    titles, failures, visited = [], [], 0
    def visit(node, depth=0):
        nonlocal visited
        visited += 1
        require(visited <= 16384 and depth <= 24)
        if isinstance(node, dict):
            if 'title' in node:
                title = node['title']
                require(isinstance(title, str) and len(title.encode()) <= 4096 and len(titles) < 2048)
                titles.append(title)
                if node.get('isAssociatedWithFailure') is True:
                    failures.append(title)
            for child in node.values(): visit(child, depth + 1)
        elif isinstance(node, list):
            for child in node: visit(child, depth + 1)
    visit(activities)
    require(titles.count(marker) == 1 and len(failures) == 1 and
            failures[0].startswith('XCTAssertEqual failed:') and failure in failures[0])
    opened = [i for i, title in enumerate(titles) if title.startswith(game_tap)]
    expected_taps = PUBLIC_SETUP_TAPS[method]
    require(len(opened) == sum(title.startswith(game_tap) for title in expected_taps))
    actual_taps = tuple(title for title in titles[opened[0]:] if title.startswith('Tap '))
    require(actual_taps == expected_taps)
    typed = [title for title in titles[:opened[0]] if title.startswith('Type ')]
    # Only the fixed earlier Dominoes public fixture may have been typed. The
    # failing Ghamza iteration has no input, submitted name/code or game state.
    allowed = {f'Type \'Table Test\' into "{field}" TextView', f'Type \'\n\' into "{field}" TextView'}
    require(len(typed) <= 2 and all(title in allowed for title in typed) and
            not any(title.startswith('Type ') for title in titles[opened[0]:]) and
            titles.index(marker) > opened[0])
    return dict(nodes=visited, earlier_synthetic_typing_count=len(typed),
                failing_game='ghamza', failed_method=method,
                catalog_openings=len(opened), exact_public_setup_taps=len(actual_taps),
                scope='Exact public setup iteration, not touch delivery or query causality')


def validate_metadata(run, artifact):
    require(run['id'] == RUN and run['head_sha'] == SOURCE and run['run_attempt'] == 1 and
            run['status'] == 'completed' and run['conclusion'] == 'failure' and
            run['event'] == 'pull_request' and run['path'] == '.github/workflows/production-verification.yml' and
            run['head_branch'] == 'fix/authored-display-scalars-2026-10-30' and
            run['repository']['full_name'] == REPOSITORY and
            run['head_repository']['full_name'] == REPOSITORY)
    require(artifact['id'] == ARTIFACT and artifact['name'] == 'ios-runtime-verification' and
            artifact['expired'] is False and artifact['size_in_bytes'] == BYTES and
            artifact['digest'] == 'sha256:' + DIGEST and
            artifact['workflow_run']['id'] == RUN and artifact['workflow_run']['head_sha'] == SOURCE)

def unpack(raw, destination):
    require(len(raw) == BYTES and sha(raw) == DIGEST and
            destination.is_dir() and not destination.is_symlink() and not any(destination.iterdir()))
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        entries = archive.infolist()
        require(0 < len(entries) <= 4096 and len({e.filename for e in entries}) == len(entries) and
                sum(e.file_size for e in entries) <= MAX_BYTES)
        for entry in entries:
            name, mode = PurePosixPath(entry.filename), entry.external_attr >> 16
            require(not name.is_absolute() and '..' not in name.parts and
                    str(name) == entry.filename.rstrip('/') and
                    (not stat.S_IFMT(mode) or stat.S_ISREG(mode) or stat.S_ISDIR(mode)))
        claimraw = archive.read(CLAIM)
        require(sha(claimraw) == CLAIM_HASH)
        claim = json.loads(claimraw)
        require(claim['source']['head'] == MERGE and claim['source']['tree'] == TREE and
                claim['task'] == dict(GITHUB_RUN_ID=str(RUN), GITHUB_RUN_ATTEMPT='1', GITHUB_JOB='ios') and
                claim['cycle'] == 'apple-ui' and
                archive.read(PREFIX + 'commit.txt') == (MERGE + '\n').encode() and
                archive.read(PREFIX + 'xcode-version.txt') == b'Xcode 26.3\nBuild version 17C529\n')
        lograw = archive.read(PREFIX + 'xcode-ui-test.log')
        require(len(lograw) <= 1024 * 1024 and sha(lograw) == LOG_HASH)
        lines = public_catalog_lines(lograw.decode())
        require(len(lines) == 1)
        manifest = {}
        for entry in entries:
            if entry.is_dir() or not entry.filename.startswith(RESULT_PREFIX):
                continue
            relative = entry.filename.removeprefix(PREFIX)
            target = destination / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            content = archive.read(entry)
            with target.open('xb') as output:
                output.write(content)
            manifest[relative] = sha(content)
        require(bool(manifest))
    return manifest, lines

def verify_results(summary, tests):
    counts = dict(totalTestCount=23, passedTests=21, failedTests=2, skippedTests=0, expectedFailures=0)
    require(all(type(summary.get(k)) is int and summary[k] == v for k, v in counts.items()) and
            summary.get('result') == 'Failed' and len(summary['testFailures']) == 2 and
            {row['testIdentifierString'] for row in summary['testFailures']} == set(FAILURES))
    configs, devices = summary.get('devicesAndConfigurations', []), tests.get('devices', [])
    require(len(configs) == len(devices) == 1)
    for device in (configs[0].get('device', {}), devices[0]):
        require(device.get('deviceId') == UDID and device.get('architecture') == 'arm64' and
                device.get('platform') == 'iOS Simulator' and device.get('osVersion') == '18.5' and
                device.get('modelName') == 'iPhone 16 Pro' and device.get('deviceName') == DEVICE)
    cases, visited = [], 0

    def visit(nodes, depth=0):
        nonlocal visited
        require(isinstance(nodes, list) and depth <= 12)
        for node in nodes:
            visited += 1
            require(isinstance(node, dict) and visited <= 512)
            if node.get('nodeType') == 'Test Case':
                cases.append(node)
            visit(node.get('children', []), depth + 1)
    visit(tests.get('testNodes', []))
    require(len(cases) == 23 and {p.get('nodeIdentifier') for p in cases} == EXPECTED and
            all(p.get('result') in ('Passed', 'Failed') for p in cases) and
            {p.get('nodeIdentifier') for p in cases if p['result'] == 'Failed'} == set(FAILURES))
    return dict(counts=counts, device=devices[0], methods={p['nodeIdentifier']: p['result'] for p in cases})

def attachment_command(result, destination, method):
    require(method in FAILURES)
    return ['export', 'attachments', '--path', str(result), '--output-path', str(destination),
            '--test-id', method]

def validate_screenshots(rows, manifest):
    # Exact-method export binds the recording; an independent original-object
    # hash readback also rejects unrelated/reencoded/unbound PNG payloads.
    require(0 < len(rows) <= 8 and all(p['sha256'] in manifest.values() for p in rows))

def public_recording_metadata(directory, manifest):
    """Hash-bound exact-public-test movie metadata only; never upload raw recordings."""
    movies = [p for p in directory.rglob('*') if p.suffix.lower() in ('.mp4', '.mov')]
    require(len(movies) <= 1)
    rows = []
    for path in movies:
        raw = attachments.read_regular(path, 64 * 1024 * 1024)
        require(len(raw) >= 12 and raw[4:8] == b'ftyp' and sha(raw) in manifest.values())
        rows.append(dict(bytes=len(raw), sha256=sha(raw),
                         scope='Original movie from exact source-pinned synthetic-public failed test; metadata only'))
    return rows

def main():
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    require(platform.system() == 'Darwin' and platform.machine() == 'arm64' and
            os.environ.get('GITHUB_REPOSITORY') == REPOSITORY and
            os.environ.get('GITHUB_REF') == BRANCH and os.environ.get('GITHUB_EVENT_NAME') == 'push' and
            os.environ.get('RUNNER_ENVIRONMENT') == 'github-hosted' and os.environ.get('GITHUB_JOB') == 'review')
    require(not subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT).strip())
    require(subprocess.check_output(['/usr/bin/xcodebuild', '-version']).decode() ==
            'Xcode 26.3\nBuild version 17C529\n')
    require(subprocess.check_output(['git', 'rev-parse', SOURCE + '^{tree}'], cwd=ROOT).decode().strip() == TREE)
    driver = subprocess.check_output(['git', 'show', SOURCE + ':' + frozen.DRIVER], cwd=ROOT)
    require(sha(driver) == DRIVER_SHA256)
    out = args.output.absolute()
    require(out.parent == Path(os.environ['RUNNER_TEMP']).resolve(strict=True) and
            not out.exists() and not out.is_symlink())
    out.mkdir(mode=0o700)
    receipt = dict(run=RUN, artifact=ARTIFACT, digest=DIGEST, source=SOURCE, producer_merge=MERGE,
                   tree=TREE, driver_sha256=sha(driver), reviewer=subprocess.check_output(
                       ['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip(),
                   result='NOT_REVIEWED', production_qualified=False, app_execution=False)
    commands = None

    def checkpoint():
        (out / 'receipt.json').write_text(json.dumps(receipt | {'commands': commands.rows if commands else []}, indent=2))

    checkpoint()
    def api(path):
        row = subprocess.run(['gh', 'api', 'repos/' + REPOSITORY + '/' + path], capture_output=True, timeout=90)
        require(row.returncode == 0 and len(row.stdout) <= 128 * 1024)
        return json.loads(row.stdout)

    validate_metadata(api('actions/runs/' + str(RUN)), api('actions/artifacts/' + str(ARTIFACT)))
    archive = out / 'frozen.zip'
    with archive.open('xb') as output:
        rc = subprocess.run(['gh', 'api', f'repos/{REPOSITORY}/actions/artifacts/{ARTIFACT}/zip'],
                            stdout=output, stderr=subprocess.PIPE, timeout=90).returncode
    require(rc == 0 and archive.stat().st_size == BYTES)
    inputs = out / 'inputs'; inputs.mkdir()
    manifest, lines = unpack(archive.read_bytes(), inputs)
    (out / 'extracted-inputs.json').write_text(json.dumps(manifest, indent=2))
    receipt['archive_verified'] = True
    receipt['catalog_settlement'] = dict(lines=lines, producer_log_sha256=LOG_HASH,
                                scope='Immutable producer log; not native query causality or event delivery')
    commands = Commands(child_environment(os.environ), time.monotonic() + 360, checkpoint)
    result = inputs / 'ios-ui-tests.xcresult'

    def read(argv, label):
        row, raw, stderr = commands.capture(['/usr/bin/xcrun', 'xcresulttool', *argv], label, 60, root=ROOT)
        require(row['status'] == 'EXITED' and row.get('exit_code') == 0 and
                row.get('direct_child_reaped') is True and not stderr)
        return raw

    try:
        decoded = {}
        for kind in ('summary', 'tests'):
            raw = read(['get', 'test-results', kind, '--path', str(result)], kind)
            decoded[kind] = json.loads(raw)
            (out / (kind + '.json')).write_bytes(raw)
        receipt['actual_results'] = verify_results(decoded['summary'], decoded['tests'])
        attachments.verify_export_help(read(['help', 'export', 'attachments'], 'attachments-help'))
        receipt['public_failures'] = {}
        for method, (locale, *_rest) in FAILURES.items():
            activity = json.loads(read(['get', 'test-results', 'activities', '--path', str(result),
                                       '--test-id', method], 'activities-' + locale))
            evidence = verify_public_setup_failure(activity, method)
            exported = out / ('decoded-attachments-' + locale); exported.mkdir(mode=0o700)
            read(attachment_command(result, exported, method), 'attachments-export-' + locale)
            curated = out / ('curated-' + locale); curated.mkdir(mode=0o700)
            rows = attachments.curate_public_screenshots(exported, curated)
            validate_screenshots(rows, manifest)
            for row in rows:
                old_name = row['path']; row['path'] = 'public-' + locale + '-' + old_name
                (curated / old_name).rename(out / row['path'])
            receipt['public_failures'][method] = dict(eligibility=evidence, screenshots=rows,
                recordings=public_recording_metadata(exported, manifest))
        receipt['result'] = 'FROZEN_FAILURE_DECODED_NOT_APPLICATION_QUALIFICATION'
    except BaseException as error:
        # Never leave a partially checked public image eligible for upload.
        for image in out.glob('public-*.png'):
            image.unlink()
        receipt['result'] = 'REJECTED'
        receipt['error_type'] = type(error).__name__
        raise
    finally:
        require(all(sha((inputs / name).read_bytes()) == digest for name, digest in manifest.items()))
        receipt['result_inputs_unchanged'] = True
        checkpoint()
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
