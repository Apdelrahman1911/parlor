"""Exact original #267 source plans; no filesystem writes or native execution."""
import hashlib

import pin01_copy
import pin02_capture
import pin03_catalog_copy as catalog

FROZEN_SOURCE = "9d4f369d2b1581f5c157b67cce3ff0fada49effa"
RUNTIME = "com.apple.CoreSimulator.SimRuntime.iOS-26-2"
DEVICE = "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro"
FROZEN_INPUTS = {
    "composeApp/src/commonMain/kotlin/com/parlor/app/shell/playmode/PlayModePickerScreen.kt": "917159ab2ed6a203600cc238deb6f8adcd4b160a323310bd12f43387edb71229",
    "composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/multiplayer/MultiplayerShellContent.kt": "598edb5a14cd361f1af41911f875a66a19bb4c18a035c7b3af8dd550f3a3edf3",
    "composeApp/src/iosMain/kotlin/com/parlor/app/MainViewController.kt": "f7da68fa3b2f8ad986d758982e72eeef5f235e6c8e8e644879c63cbac5aa0b33",
    "iosApp/iosApp/ContentView.swift": "4611046a14dc17732b563ca9a65fe32c8a0151df28e2a6cc8321f2b4981e5e5d",
    "iosApp/iosApp/ComposeContainerViewController.swift": "9bdeeaf67a569f4016bc5359ae865b6df9eeaa2a9f6ee36e01041bf79000a903",
    "iosApp/iosAppUITests/IOSAppLaunchUITests.swift": "d8668c84349debf296efb4ba88336d6110eb1938131060be84b59acb4003f9d8",
    "composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt": "446d3aca75d2e40928678bb3e14dedcf83afa0dadfe1b721718c700722e71580"
}
UNREACHABLE = '        XCTFail("Public setup control is not reachable: " + prefix)'


def render(sources, digests, *, arm):
    """Keep original source/actions exact; observe only explicitly frozen copies.

    No later driver is substituted. Capture is requested only after the original
    failed eight-search reachability loop, never before/around the single tap.
    """
    if arm not in catalog.ARMS or set(sources) != set(FROZEN_INPUTS) or digests != FROZEN_INPUTS:
        raise ValueError("Exact original #267 source bindings and explicit arm required")
    if any(type(value) is not str or hashlib.sha256(value.encode()).hexdigest() != digests[path]
           for path, value in sources.items()):
        raise ValueError("Original #267 source changed")
    if arm == 'original':
        return dict(sources)
    output = dict(sources)
    output.update(pin01_copy.render(
        {p: sources[p] for p in pin01_copy.MODIFIED},
        {p: digests[p] for p in pin01_copy.MODIFIED}, native_observer=arm == 'native'))
    output[pin02_capture.CONTAINER] = pin02_capture.render_container(sources[pin02_capture.CONTAINER])
    driver = sources[pin02_capture.DRIVER]
    launch = '        let app = XCUIApplication()\n'
    if driver.count(launch) != 4:
        raise ValueError("Original four launch owners changed")
    driver = driver.replace(launch, launch + '        PIN02FailureRequest.configure(app)\n')
    driver = pin01_copy.once(driver, UNREACHABLE,
        '        PIN02FailureRequest.captureAfterFailedTransition(app)\n' + UNREACHABLE)
    output[pin02_capture.DRIVER] = driver + '\n' + (pin02_capture.HERE / 'PIN02FailureRequest.swift.in').read_text()
    return output
