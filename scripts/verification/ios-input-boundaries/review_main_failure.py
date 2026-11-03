"""Read the exact failed main xcresult. No app build, execution, probe or qualification."""
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
BRANCH = 'refs/heads/diagnostic/ios-main-failure-readback-2026-11-03'
RUN = 41580234749
ARTIFACT = 13206887626
SOURCE = '364214d558b256994b8f88b87e354b8579d214aa'
TREE = '12af183e9f0429f4cd74796569186b37464a3af9'
DIGEST = '02acfaf83d136ea5195720e39adb97b7d466cc480d0328a49d5b70cfa0ae75e5'
BYTES = 137431211
MAX_BYTES = 192 * 1024 * 1024
PREFIX = 'parlor/parlor/build/ci-evidence/'
RESULT_PREFIX = PREFIX + 'ios-ui-tests.xcresult/'
CLAIM = '_temp/parlor-verification-41580234749-1-ios-apple-ui-ownership.json'
CLAIM_HASH = 'df09946952f78ebc9e071b9eab288fc82a1cb2a21734a37d0ad0f1eea63d3649'
LOG_HASH = '127c2286b944cf55e105b965db28c9a1542e2ba42f74569e3755a9b8c303c334'
DRIVER_SHA256 = '416a1ceab65c6c5bbd779df6de779fceeee1c2c4d4abc5142a58d3596ac38ab8'
METHOD = attachments.METHOD
UDID = 'DC71A32B-7942-4AF7-910C-C66C2E8ACEB6'
DEVICE = 'Parlor-ci-1907b6d4bcf34ce78bd211b22f2584b6'
require = frozen.require
sha = lambda raw: hashlib.sha256(raw).hexdigest()
EXPECTED = frozenset(['ComposeContainerViewControllerTests/testChildUsesFullBoundsAcrossSizesAndOpposingSemanticDirections()', 'ComposeContainerViewControllerTests/testDefaultAppearanceForwardingReachesTheSameChildExactlyOnce()', 'ComposeContainerViewControllerTests/testOneStableChildKeepsItsExplicitSemanticsIndependentOfTheOuterView()', 'ComposeContainerViewControllerTests/testSystemDecorationAndOrientationPoliciesStillBelongToTheChild()', 'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInArabic()', 'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish()', 'IOSAppLaunchUITests/testArabicDisplayNameAndAsciiRoomCodeThroughNativeInput()', 'IOSAppLaunchUITests/testColdLaunchRendersComposeHomeWithoutUnexpectedAlert()', 'IOSAppLaunchUITests/testKeyboardInsetsOnNameAndRoomCodeInArabic()', 'IOSAppLaunchUITests/testKeyboardInsetsOnNameAndRoomCodeInEnglish()', 'IOSAppLaunchUITests/testLastLightLocalPlayerFieldsScrollWithNextAndDismissWithDone()', 'IOSAppLaunchUITests/testPostDoneNameAndRoomCodeEditsInArabic()', 'IOSAppLaunchUITests/testPostDoneNameAndRoomCodeEditsInEnglish()', 'KeyboardIntroductionDismissalTests/testGenericContinueWithoutIdentifiedTutorialNeverAdmitsATap()', 'KeyboardIntroductionDismissalTests/testLateIntroductionAfterInitialAbsenceCanBeDismissedExactlyOnce()', 'KeyboardIntroductionDismissalTests/testMissingInvalidOffscreenOrUpperScreenButtonCannotBeDismissed()', 'KeyboardIntroductionDismissalTests/testNonhittableIntroductionDoesNotConsumeTheSingleDismissal()', 'PublicControlFramesTests/testDisabledControlBreaksFrameAdjacency()', 'PublicControlFramesTests/testFirstFrameNeverSettlesButTheExactAdjacentFrameDoes()', 'PublicControlFramesTests/testHittabilityInterruptionRequiresTwoFreshFrames()', 'PublicControlFramesTests/testInvalidOrEmptyGeometryCannotSettleAndBreaksAdjacency()', 'PublicControlFramesTests/testMissingControlBreaksFrameAdjacency()', 'PublicControlFramesTests/testMotionIsNotRoundedIntoASettledFrame()', 'PublicTextValueTests/testMissingOrNonStringValueNeverMatchesEvenAnEmptyExpectation()', 'PublicTextValueTests/testOnlyTheCompleteExpectedStringMatches()', 'PublicTextValueTests/testWhitespaceCaseAndArabicCharactersAreNotRewritten()'])

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

def public_activity_receipt(activities):
    # Exact first catalog failure on the owned clean Simulator. No input or
    # opening tap happened; never export arbitrary activity/attachment payloads.
    attachments.verify_public_failure(activities, METHOD)
    titles, timeline, visited = [], [], 0
    def visit(node, depth=0):
        nonlocal visited
        visited += 1
        require(visited <= 16384 and depth <= 24)
        if isinstance(node, dict):
            title = node.get('title')
            if title is not None:
                require(isinstance(title, str) and len(title.encode()) <= 4096)
                titles.append(title)
                # Keep operation kind/timing only, not arbitrary labels/values.
                kinds = ('Checking existence', 'Checking `Expect predicate', 'Find the',
                         'Requesting snapshot', 'Wait for', 'Swipe up', 'Get number',
                         'Checking attribute', 'XCTAssertEqual failed:', attachments.TITLE)
                operation = next((x for x in kinds if title.startswith(x)), 'other')
                timing = {k: node[k] for k in ('startTime', 'finishTime', 'start', 'finish')
                          if isinstance(node.get(k), (int, float)) and
                          not isinstance(node.get(k), bool) and math.isfinite(node[k])}
                timeline.append(dict(operation=operation, title_sha256=sha(title.encode()),
                    first_catalog_label='افتح الدومنة المصرية' in title, timing=timing))
            for child in node.values(): visit(child, depth + 1)
        elif isinstance(node, list):
            for child in node: visit(child, depth + 1)
    visit(activities)
    require(not any(t.startswith(('Type ', 'Tap ')) for t in titles) and
            sum(t.startswith('Swipe up ') for t in titles) == 1 and
            any('افتح الدومنة المصرية' in t for t in titles))
    return dict(nodes=visited, typing=0, opening_taps=0, upward_swipes=1,
                timeline=timeline, scope='Retained query activities, not native input delivery')


def validate_metadata(run, artifact):
    require(run['id'] == RUN and run['head_sha'] == SOURCE and run['run_attempt'] == 1 and
            run['status'] == 'completed' and run['conclusion'] == 'failure' and
            run['event'] == 'push' and run['path'] == '.github/workflows/production-verification.yml' and
            run['head_branch'] == 'main' and
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
        require(claim['source']['head'] == SOURCE and claim['source']['tree'] == TREE and
                claim['task'] == dict(GITHUB_RUN_ID=str(RUN), GITHUB_RUN_ATTEMPT='1', GITHUB_JOB='ios') and
                claim['cycle'] == 'apple-ui' and
                archive.read(PREFIX + 'commit.txt') == (SOURCE + '\n').encode() and
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
    counts = dict(totalTestCount=26, passedTests=25, failedTests=1, skippedTests=0, expectedFailures=0)
    require(all(type(summary.get(k)) is int and summary[k] == v for k, v in counts.items()) and
            summary.get('result') == 'Failed' and len(summary['testFailures']) == 1 and
            {row['testIdentifierString'] for row in summary['testFailures']} == {METHOD})
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
    require(len(cases) == 26 and {p.get('nodeIdentifier') for p in cases} == EXPECTED and
            all(p.get('result') in ('Passed', 'Failed') for p in cases) and
            {p.get('nodeIdentifier') for p in cases if p['result'] == 'Failed'} == {METHOD})
    return dict(counts=counts, device=devices[0], methods={p['nodeIdentifier']: p['result'] for p in cases})

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
    receipt = dict(run=RUN, artifact=ARTIFACT, digest=DIGEST, source=SOURCE,
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
        activity = json.loads(read(['get', 'test-results', 'activities', '--path', str(result),
                                   '--test-id', METHOD], 'activities-ar'))
        evidence = public_activity_receipt(activity)
        exported = out / 'decoded-attachments'; exported.mkdir(mode=0o700)
        read(attachments.attachment_command(result, exported, method=METHOD), 'attachments-export-ar')
        curated = out / 'curated'; curated.mkdir(mode=0o700)
        rows = attachments.curate_public_screenshots(exported, curated)
        require(all(p['sha256'] in manifest.values() for p in rows))
        for row in rows:
            (curated / row['path']).rename(out / row['path'])
        receipt['public_failure'] = dict(eligibility=evidence, screenshots=rows)
        receipt['result'] = 'FROZEN_FAILURE_DECODED_NOT_APPLICATION_QUALIFICATION'
    except BaseException as error:
        # Never leave a partially checked public image eligible for upload.
        for image in out.glob('public-*.png'):
            image.unlink()
        receipt['result'] = 'REJECTED'
        receipt['error_type'] = type(error).__name__
        raise
    finally:
        unchanged = False
        try:
            unchanged = all(sha((inputs / name).read_bytes()) == digest for name, digest in manifest.items())
        finally:
            receipt['result_inputs_unchanged'] = unchanged
            if not unchanged:
                for image in out.glob('public-*.png'): image.unlink()
                receipt['result'] = 'REJECTED'
            checkpoint()
        require(unchanged)
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
