"""Read one immutable production failure; never build, launch, retry or qualify.

This separate reader leaves the older frozen-result investigation unchanged.
Only exact-method pre-input PNGs and bounded numeric evidence are published.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import platform
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
BRANCH = 'refs/heads/diagnostic/ios-name-settlement-review-2026-10-29'
RUN = 40940007224
ARTIFACT = 12930783901
SOURCE = 'b0706863d0084db9c293ca076a72ed30a25b8a87'
MERGE = '0bae8f1e12a9dc18f79377e097ad2b949ad5fd18'
TREE = '213bc21f999eb3c3eb31e256dd8867218209e8db'
DIGEST = '7f006ddd6e3b9888c81912e1491dc5a3e44a674cf45d85667735a64255d415cb'
BYTES = 135483785
MAX_BYTES = 160 * 1024 * 1024
PREFIX = 'parlor/parlor/build/ci-evidence/'
RESULT_PREFIX = PREFIX + 'ios-ui-tests.xcresult/'
CLAIM = '_temp/parlor-verification-40940007224-1-ios-apple-ui-ownership.json'
CLAIM_HASH = '3206d7c17bb903fcb7fa3c749d4dfe63950470468f7390eb15acc0fd33b701ed'
LOG_HASH = 'ed7d93252c74715ea912d74afbc7c33fdcb27d963a07ab102ec83523611eedbc'
METHOD = 'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish()'
UDID = 'B7E4A219-6F41-45F4-9773-4A7B2CD4FB03'
DEVICE = 'Parlor-ci-630a5a4c29b24f248bcbaa6e2d504c52'
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


def validate_metadata(run, artifact):
    require(run['id'] == RUN and run['head_sha'] == SOURCE and run['run_attempt'] == 1 and
            run['status'] == 'completed' and run['conclusion'] == 'failure' and
            run['event'] == 'pull_request' and run['path'] == '.github/workflows/production-verification.yml' and
            run['head_branch'] == 'fix/input-diagnostic-integration-2026-10-29' and
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
        lines = frozen.settlement_lines(dict(emittedOutput=lograw.decode()))
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
    counts = dict(totalTestCount=23, passedTests=22, failedTests=1, skippedTests=0, expectedFailures=0)
    require(all(type(summary.get(k)) is int and summary[k] == v for k, v in counts.items()) and
            summary.get('result') == 'Failed' and len(summary['testFailures']) == 1 and
            summary['testFailures'][0]['testIdentifierString'] == METHOD)
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
            [p.get('nodeIdentifier') for p in cases if p['result'] == 'Failed'] == [METHOD])
    return dict(counts=counts, device=devices[0], methods={p['nodeIdentifier']: p['result'] for p in cases})


def attachment_command(result, destination, method):
    require(method == METHOD)
    return ['export', 'attachments', '--path', str(result), '--output-path', str(destination),
            '--test-id', METHOD]


def validate_screenshots(rows, manifest):
    # Exact-method export binds the recording; an independent original-object
    # hash readback also rejects unrelated/reencoded/unbound PNG payloads.
    require(0 < len(rows) <= 8 and all(p['sha256'] in manifest.values() for p in rows))


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
    require(sha(driver) == frozen.DRIVER_SHA256)
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
    receipt['settlement'] = dict(lines=lines, producer_log_sha256=LOG_HASH,
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
        for kind in ('summary', 'tests', 'test-details', 'activities'):
            args = ['get', 'test-results', kind, '--path', str(result)]
            if kind in ('test-details', 'activities'):
                args += ['--test-id', METHOD]
            raw = read(args, kind)
            decoded[kind] = json.loads(raw)
            (out / (kind + '.json')).write_bytes(raw)
        receipt['actual_results'] = verify_results(decoded['summary'], decoded['tests'])
        attachments.verify_public_failure(decoded['activities'], METHOD)
        helptext = read(['help', 'export', 'attachments'], 'attachments-help')
        attachments.verify_export_help(helptext)
        exported = out / 'decoded-attachments'; exported.mkdir(mode=0o700)
        read(attachment_command(result, exported, METHOD), 'attachments-export')
        rows = attachments.curate_public_screenshots(exported, out)
        validate_screenshots(rows, manifest)
        receipt['public_screenshots'] = rows
        receipt['result'] = 'FROZEN_FAILURE_DECODED_NOT_APPLICATION_QUALIFICATION'
    except BaseException as error:
        # Never leave a partially checked public image eligible for upload.
        for image in out.glob('public-settlement-*.png'):
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
