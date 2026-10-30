"""Frozen PR296 Host delivery / permission settlement observation, copy only.

Successful driver paths, queries, gestures and ten-second budgets stay exact.
The extra request runs only after the original permission settlement has failed.
"""
from pathlib import Path
import hashlib
import json

import frozen_sources
import pin01_copy
import pin02_capture
import pin03_catalog_copy as catalog

HERE = Path(__file__).resolve().parent
SOURCE = '40ae511df882cb29070a94ef3c25f95c1b4ba93e'
TREE = '1ef599d662d977cf862d88a9783d147ffc598579'
RUNTIME = 'com.apple.CoreSimulator.SimRuntime.iOS-18-5'
DEVICE = 'com.apple.CoreSimulator.SimDeviceType.iPhone-16-Pro'
MODIFIED = catalog.MODIFIED
FIXTURES = HERE / 'fixtures/public-setup-pair-2026-10-30'
MANIFEST_SHA256 = '0b7b24a1bf202b5f8edfe93f20c4b428e8ab05793600559a697f6a61c23bc24c'
INPUTS = {
    pin01_copy.PICKER: '917159ab2ed6a203600cc238deb6f8adcd4b160a323310bd12f43387edb71229',
    pin01_copy.SHELL: 'cd894bfbd49697066d2f7ce7fa22838fcd401ba3f6d5d6e4bafaef0c83ee37bb',
    pin01_copy.MAIN: 'f7da68fa3b2f8ad986d758982e72eeef5f235e6c8e8e644879c63cbac5aa0b33',
    pin01_copy.CONTENT: '4611046a14dc17732b563ca9a65fe32c8a0151df28e2a6cc8321f2b4981e5e5d',
    pin02_capture.CONTAINER: '9bdeeaf67a569f4016bc5359ae865b6df9eeaa2a9f6ee36e01041bf79000a903',
    pin02_capture.DRIVER: 'c65fe65aee8442aa06f02007b57daaf3e9113f5f61046edb59e361c2e4a6e115',
    catalog.HOME: 'af659fa12d48a5134a1752b38a1fb66f2f97d0a2462749ade99c45558b2c6a89',
}
TEMPLATES = {
    'PIN01Observation.kt.in': '193fbe932e445d7e81c41bf857d3fc44c5d0b7cf57a9e6730cd30b0bfcc39a35',
    'PIN01Bridge.kt.in': '459d1667fe5d1fe644400a41609b5e60cc44fa32816ec02f272a61747c5a02f7',
    'PIN01TouchObserver.swift.in': '367815c81695e317044da3a971b40a44e1eb1470086b3850708a234ceaa27fe7',
    'PIN02Capture.swift.in': '4a3814846d15f854f2e7ad2510fa04795a220ed55f0d65793ee185e8d5716433',
    'PIN02FailureRequest.swift.in': '07a613781ea793d323f7a54aede4dba8c94281bbc0ff94b18b437e1d71391740',
}
OWNER_CALL = '''                try tapSettledControl(button, pin06FailureOwner:
                    (prefix == "Continue and retry the real local-network operation." ||
                     prefix == "تابع وأعد تنفيذ عملية الشبكة المحلية الفعلية.") ? app : nil)'''
SIGNATURE = '''    private func tapSettledControl(
        _ control: XCUIElement,
        pin06FailureOwner: XCUIApplication? = nil
    ) throws {'''
CAPTURE = '''            if let pin06FailureOwner {
                PIN02FailureRequest.captureAfterFailedTransition(pin06FailureOwner)
            }
'''


def require(condition, message):
    if not condition:
        raise ValueError(message)


def fixture(path, *, directory=FIXTURES):
    """Hermetic software inputs; native execution still archives actual SOURCE."""
    require(type(path) is str and path in INPUTS, 'Exact public setup fixture required')
    raw = frozen_sources._read(directory / 'manifest.json', 32 * 1024)
    require(hashlib.sha256(raw).hexdigest() == MANIFEST_SHA256, 'Setup fixture manifest changed')
    manifest = json.loads(raw)
    require(manifest['source'] == SOURCE and manifest['tree'] == TREE and
            set(manifest['inputs']) == set(INPUTS), 'Setup fixture provenance changed')
    entry = manifest['inputs'][path]
    require(entry['sha256'] == INPUTS[path] and entry['file'] == Path(path).name + '.source',
            'Setup fixture binding changed')
    raw = frozen_sources._read(directory / entry['file'], frozen_sources.MAX_SOURCE_BYTES)
    require(len(raw) == entry['size'] and hashlib.sha256(raw).hexdigest() == INPUTS[path],
            'Original public setup fixture bytes changed')
    return raw.decode('utf-8')


def render(sources, digests, *, arm):
    require(arm in catalog.ARMS and set(sources) == set(digests) == set(MODIFIED),
            'Exact seven bindings and explicit setup arm required')
    for path, value in sources.items():
        require(type(value) is str and digests[path] == INPUTS[path] and
                hashlib.sha256(value.encode()).hexdigest() == INPUTS[path], 'Frozen public setup source changed')
    for name, digest in TEMPLATES.items():
        raw = frozen_sources._read(HERE / name, frozen_sources.MAX_SOURCE_BYTES)
        require(hashlib.sha256(raw).hexdigest() == digest, 'Inherited setup observation template changed')
    output = catalog.render(sources, digests, arm=arm)
    if arm == 'original':
        return output
    once = pin01_copy.once
    driver = once(output[pin02_capture.DRIVER], '                try tapSettledControl(button)', OWNER_CALL)
    driver = once(driver, '    private func tapSettledControl(_ control: XCUIElement) throws {', SIGNATURE)
    driver = once(driver, '        if result != .completed {\n            print("Public control settlement: "',
                  '        if result != .completed {\n' + CAPTURE + '            print("Public control settlement: "')
    output[pin02_capture.DRIVER] = driver
    # Preserve scene enums across public permission/name stages. Touches remain
    # armed ONLY on Setup/Home: no permission, keyboard or private touch recording.
    output[pin01_copy.ADDITION] = once(output[pin01_copy.ADDITION],
        '        if (!armed && kind != 4) return',
        '        if (!armed && kind != 4 && !(kind == 5 && stage in 0..4)) return')
    return output
