"""Pure catalog extension of PIN01/PIN02; never changes a shipping checkout.

The original 50ms tap, selectors, successful path and ten-second destination
wait remain unchanged. Added capture runs only after that original wait fails.
Catalog receipt enums are deliberately separate from PIN01's narrower decoder.
"""
import hashlib
import json
import math

import pin01_copy
import pin01_receipts
import pin02_capture

HOME = 'composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt'
MODIFIED = (*pin01_copy.MODIFIED, *pin02_capture.MODIFIED, HOME)
ARMS = ('original', 'compose', 'native')


def render(sources, digests, *, arm):
    if arm not in ARMS or set(sources) != set(digests) or set(sources) != set(MODIFIED):
        raise ValueError('Exact seven source bindings and explicit arm required')
    for path, value in sources.items():
        if type(value) is not str or hashlib.sha256(value.encode()).hexdigest() != digests[path]:
            raise ValueError('Frozen source changed')
    if arm == 'original':
        return dict(sources)
    output = pin01_copy.render(
        {p: sources[p] for p in pin01_copy.MODIFIED},
        {p: digests[p] for p in pin01_copy.MODIFIED}, native_observer=arm == 'native')
    output.update(pin02_capture.render(
        {p: sources[p] for p in pin02_capture.MODIFIED},
        {p: digests[p] for p in pin02_capture.MODIFIED}))
    once = pin01_copy.once
    home = once(sources[HOME], 'import androidx.compose.ui.Modifier\n',
                'import androidx.compose.ui.Modifier\nimport com.parlor.app.inputcopy.pin01PublicInput\n')
    home = once(home, '    var selectedFilter by rememberSaveable',
                '    com.parlor.app.inputcopy.PIN01PublicStage(6)\n'
                '    var selectedFilter by rememberSaveable')
    home = once(home, '.clickable(role = Role.Button, onClick = onOpen),',
                '.pin01PublicInput(3)\n'
                '                .clickable(role = Role.Button, onClick = {\n'
                '                    com.parlor.app.inputcopy.PIN01Observation.callback(3)\n'
                '                    onOpen()\n'
                '                }),')
    output[HOME] = home
    recorder = output[pin01_copy.ADDITION]
    for before, after in (
        ('kind == 4 && phase !in 0..4', 'kind == 4 && phase !in 0..4 && phase != 6'),
        ('control !in 0..2 || phase !in 0..5', 'control !in 0..3 || phase !in 0..6'),
        ('armed = value == 0', 'armed = value == 0 || value == 6'),
    ):
        recorder = once(recorder, before, after)
    output[pin01_copy.ADDITION] = recorder
    driver = output[pin02_capture.DRIVER]
    # This is the exact assertion that failed in run40794322752, not the Home
    # brand or Host->permission transition. Evaluate its original predicate once.
    driver = once(driver,
        '            XCTAssertTrue(setupButton(app, prefix: copy.host).waitForExistence(timeout: 10))',
        '            let catalogOpened = setupButton(app, prefix: copy.host).waitForExistence(timeout: 10)\n'
        '            if !catalogOpened { PIN02FailureRequest.captureAfterFailedTransition(app) }\n'
        '            XCTAssertTrue(catalogOpened)')
    output[pin02_capture.DRIVER] = driver
    return output


def decode_snapshot(raw):
    """Numeric public catalog/picker evidence, not a root-cause certification.

    Keep PIN01's existing strict decoder unchanged. Translate only the declared
    catalog control/stage for its shape/privacy checks, preserving original bytes.
    Incomplete buffers can be retained but never qualify.
    """
    require = pin01_receipts.require
    require(type(raw) is bytes and 0 < len(raw) <= pin01_receipts.MAX_BYTES, 'Bounded snapshot required')
    value = json.loads(raw, object_pairs_hook=pin01_receipts.reject_duplicates,
                       parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite JSON')))
    require(type(value) is dict and set(value) == {'schema', 'nativeMode', 'overflow', 'invalid', 'rows'},
            'Unknown/private snapshot field')
    require(all(type(value[key]) is int and 0 <= value[key] <= 512 for key in ('overflow', 'invalid')),
            'Invalid counters')
    require(type(value['rows']) is list and len(value['rows']) <= 512, 'Bounded rows required')
    translated = []
    for row in value['rows']:
        require(type(row) is list and len(row) == 10 and
                all(type(v) in (int, float) and math.isfinite(v) for v in row), 'Invalid numeric row')
        converted = list(row)
        if row[2] == 3:
            require(row[1] in (2, 3), 'Catalog control on a non-pointer/non-callback event')
            converted[2] = 1
        if row[3] == 6:
            require(row[1] == 4 and row[2] == 0, 'Catalog stage on a non-stage event')
            converted[3] = 0
        translated.append(converted)
    shape = dict(value, overflow=0, invalid=0, rows=translated or [[0, 4, 0, 0, 0, 0, 0, 0, 0, 0]])
    pin01_receipts.decode(json.dumps(shape, allow_nan=False).encode())
    return dict(snapshot=value, numeric_buffer_complete=bool(translated) and
                value['overflow'] == value['invalid'] == 0,
                source_runtime_product_cleanup_verified=False, native_input_neutrality=False,
                root_cause_proven=False)
