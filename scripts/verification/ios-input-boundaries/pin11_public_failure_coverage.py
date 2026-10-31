"""Prepared copy-only public failure coverage; not wired into an executed profile.

PIN11 initially observes Home/Setup only. Permission/name root observations need
their own public AX gate, without arming keyboard or permission pointer recording.
Capture must precede XCTest's failure abort, not rely on Swift defer, and cannot
duplicate an earlier explicit request. No original test assertion is replaced.
"""
import pin01_copy
import pin02_capture

PROPERTY = '''    // AX getters record only enums/ordinals, never labels or values.
    // Keep this separate from pointer arming: no keyboard touches are recorded.
    val publicAXArmed: Boolean get() = stage in 0..4 || stage == 6
'''
BRIDGE = '''
// COPY ONLY: public AX scope is broader than the public pointer scope.
fun PIN11PublicAXArmed(): Boolean = com.parlor.app.inputcopy.PIN01Observation.publicAXArmed
'''
HELPER = '''    @MainActor private var pin11RequestedTokens = Set<String>()
    @MainActor private var pin11PublicApp: XCUIApplication?

    override func record(_ issue: XCTIssue) {
        // record is called only after XCTest decides to report a failure.
        // continueAfterFailure=false can bypass Swift defer. Capture BEFORE
        // forwarding that unchanged failure, without reading issue content.
        if Thread.isMainThread {
            MainActor.assumeIsolated {
                if let app = pin11PublicApp {
                    pin11PublicApp = nil
                    pin11CapturePublicFailureOnce(app)
                }
            }
        }
        super.record(issue)
    }

    @MainActor
    private func pin11CapturePublicFailureOnce(_ app: XCUIApplication) {
        guard let token = app.launchEnvironment["PARLOR_PIN02_TOKEN"],
              UUID(uuidString: token)?.uuidString == token,
              !pin11RequestedTokens.contains(token), pin11RequestedTokens.count < 8 else { return }
        // Attempt before delivery. An absent ACK must not cause a second request.
        pin11RequestedTokens.insert(token)
        if pin11PublicApp === app { pin11PublicApp = nil }
        PIN02FailureRequest.captureAfterFailedTransition(app)
    }

'''
OLD_DEFER = '            defer { app.terminate() }'
NEW_DEFER = '''            defer {
                // Success cleanup only. Failure capture is in record(_:),
                // because XCTest can abort without unwinding Swift defer.
                pin11PublicApp = nil
                app.terminate()
            }'''
LAUNCH = '            app.launch()\n'
OWNED_LAUNCH = '            pin11PublicApp = app\n' + LAUNCH
METHOD = '    @MainActor\n    private func verifyAdditionalGamesSetup('
NEXT_METHOD = '    @MainActor\n    func testLastLightLocalPlayerFieldsScrollWithNextAndDismissWithDone()'
ARMED = 'armed: { MainViewControllerKt.PIN01InputArmed() }'
AX_ARMED = 'armed: { MainViewControllerKt.PIN11PublicAXArmed() }'


def extend(plan):
    """Exact PIN11 plan only; pure transformation, no workspace/device authority."""
    result = dict(plan)
    result[pin01_copy.ADDITION] = pin01_copy.once(result[pin01_copy.ADDITION],
        '    var armed = false\n', PROPERTY + '    var armed = false\n')
    result[pin01_copy.MAIN] += BRIDGE
    container = result[pin02_capture.CONTAINER]
    if container.count(ARMED) != 2:
        raise ValueError('Exactly the existing element and root AX observers required')
    result[pin02_capture.CONTAINER] = container.replace(ARMED, AX_ARMED)
    driver = result[pin02_capture.DRIVER]
    if driver.count('PIN02FailureRequest.captureAfterFailedTransition(app)') != 2:
        raise ValueError('Exact catalog and Host/Join transition failure requests required')
    driver = driver.replace('PIN02FailureRequest.captureAfterFailedTransition(app)',
                            'pin11CapturePublicFailureOnce(app)')
    driver = pin01_copy.once(driver,
        'PIN02FailureRequest.captureAfterFailedTransition(pin06FailureOwner)',
        'pin11CapturePublicFailureOnce(pin06FailureOwner)')
    start, end = driver.index(METHOD), driver.index(NEXT_METHOD)
    public_method = pin01_copy.once(driver[start:end], OLD_DEFER, NEW_DEFER)
    public_method = pin01_copy.once(public_method, LAUNCH, OWNED_LAUNCH)
    driver = driver[:start] + HELPER + public_method + driver[end:]
    result[pin02_capture.DRIVER] = driver
    return result


def restore(plan):
    """Control-only inverse; prove original successful driver/actions stay intact."""
    result = dict(plan)
    result[pin01_copy.ADDITION] = pin01_copy.once(result[pin01_copy.ADDITION], PROPERTY, '')
    if not result[pin01_copy.MAIN].endswith(BRIDGE):
        raise ValueError('Exact appended public AX bridge required')
    result[pin01_copy.MAIN] = result[pin01_copy.MAIN][:-len(BRIDGE)]
    container = result[pin02_capture.CONTAINER]
    if container.count(AX_ARMED) != 2:
        raise ValueError('Exact two public AX gates required')
    result[pin02_capture.CONTAINER] = container.replace(AX_ARMED, ARMED)
    driver = pin01_copy.once(result[pin02_capture.DRIVER], HELPER, '')
    driver = pin01_copy.once(driver, NEW_DEFER, OLD_DEFER)
    driver = pin01_copy.once(driver, OWNED_LAUNCH, LAUNCH)
    if driver.count('pin11CapturePublicFailureOnce(app)') != 2:
        raise ValueError('Exact original failure owners required')
    driver = driver.replace('pin11CapturePublicFailureOnce(app)',
                            'PIN02FailureRequest.captureAfterFailedTransition(app)')
    result[pin02_capture.DRIVER] = pin01_copy.once(driver,
        'pin11CapturePublicFailureOnce(pin06FailureOwner)',
        'PIN02FailureRequest.captureAfterFailedTransition(pin06FailureOwner)')
    return result
