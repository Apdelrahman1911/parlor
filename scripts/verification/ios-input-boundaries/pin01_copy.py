"""Pure, exact-anchor plans for explicitly owned copies; no filesystem writes."""
from pathlib import Path
import hashlib

HERE = Path(__file__).resolve().parent
PICKER = 'composeApp/src/commonMain/kotlin/com/parlor/app/shell/playmode/PlayModePickerScreen.kt'
SHELL = 'composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/multiplayer/MultiplayerShellContent.kt'
MAIN = 'composeApp/src/iosMain/kotlin/com/parlor/app/MainViewController.kt'
CONTENT = 'iosApp/iosApp/ContentView.swift'
ADDITION = 'composeApp/src/commonMain/kotlin/com/parlor/app/inputcopy/PIN01Observation.kt'
MODIFIED = (PICKER, SHELL, MAIN, CONTENT)


def once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError('PIN01 actual-source anchor missing, ambiguous or already instrumented')
    return text.replace(old, new)


def render(sources, expected_sha256, *, native_observer):
    """Return modified/additional bytes only after every source/anchor validates.

    Caller must own/freeze the full actual-source copy and retain before/after
    manifests. This pure helper allocates no workspace, runner, device or process.
    Native mode is explicit, not environment/preference based. No default arm.
    """
    if type(native_observer) is not bool or set(sources) != set(MODIFIED) or set(expected_sha256) != set(MODIFIED):
        raise RuntimeError('Explicit native arm and exact four source bindings required')
    for path, text in sources.items():
        if not isinstance(text, str) or 'PIN01' in text:
            raise RuntimeError('Unknown or repeated PIN01 transformation')
        if hashlib.sha256(text.encode()).hexdigest() != expected_sha256[path]:
            raise RuntimeError('Frozen source digest changed')
    output = dict(sources)
    text = once(output[PICKER], 'import androidx.compose.ui.Modifier\n',
                'import androidx.compose.ui.Modifier\nimport com.parlor.app.inputcopy.pin01PublicInput\n')
    for callback, control in (('onHost', 1), ('onJoin', 2)):
        anchor = f'                        onClick = {callback},\n                        modifier = Modifier.fillMaxWidth(),'
        text = once(text, anchor, anchor.replace('Modifier.fillMaxWidth()', f'Modifier.fillMaxWidth().pin01PublicInput({control})'))
    output[PICKER] = text
    text = once(output[SHELL], '    val settings = gate::openAppSettings.takeIf { gate.canOpenNetworkSettings }',
        '    com.parlor.app.inputcopy.PIN01PublicStage(when (step) {\n'
        '        ShellStep.Setup -> 0\n        ShellStep.HostPermission -> 1\n        ShellStep.JoinPermission -> 2\n'
        '        ShellStep.HostName -> 3\n        ShellStep.JoinName -> 4\n        else -> 5\n    })\n'
        '    val settings = gate::openAppSettings.takeIf { gate.canOpenNetworkSettings }')
    for action, control, phase in (('onHost', 1, 'HostPermission'), ('onJoin', 2, 'JoinPermission')):
        old = f'{action} = {{ step = ShellStep.{phase} }}'
        new = f'{action} = {{ com.parlor.app.inputcopy.PIN01Observation.callback({control}); step = ShellStep.{phase} }}'
        text = once(text, old, new)
    output[SHELL] = text
    text = once(output[MAIN], '        startKoinOnce()',
        '        com.parlor.app.inputcopy.PIN01Observation.clock = {\n'
        '            platform.Foundation.NSProcessInfo.processInfo.systemUptime * 1000.0\n        }\n'
        '        startKoinOnce()')
    for callback, phase in (('notifyActive', 0), ('notifyInactive', 1), ('notifyBackgrounded', 2)):
        old = f'    lifecycleCoordinator().{callback}()'
        text = once(text, old, old + f'\n    com.parlor.app.inputcopy.PIN01Observation.scene({phase})')
    output[MAIN] = text + '\n' + (HERE / 'PIN01Bridge.kt.in').read_text()
    original = '        ComposeContainerViewController(contentController: MainViewControllerKt.MainViewController())'
    if native_observer:
        new = '''#if DEBUG && targetEnvironment(simulator)
        let child = MainViewControllerKt.MainViewController()
        let observer = PIN01TouchObserver(target: nil, action: nil)
        observer.isArmed = { MainViewControllerKt.PIN01InputArmed() }
        observer.record = { phase, count, pressed, x, y, eventMs in
            MainViewControllerKt.PIN01NativeInput(phase: phase, count: count, pressed: pressed,
                x: x, y: y, eventMs: eventMs)
        }
        child.view.addGestureRecognizer(observer)
        MainViewControllerKt.PIN01NativeMode(mode: 1)
        return ComposeContainerViewController(contentController: child)
#else
        return ComposeContainerViewController(contentController: MainViewControllerKt.MainViewController())
#endif'''
    else:
        new = '''#if DEBUG && targetEnvironment(simulator)
        MainViewControllerKt.PIN01NativeMode(mode: 0)
#endif
        return ComposeContainerViewController(contentController: MainViewControllerKt.MainViewController())'''
    output[CONTENT] = once(output[CONTENT], original, new) + '\n' + (HERE / 'PIN01TouchObserver.swift.in').read_text()
    output[ADDITION] = (HERE / 'PIN01Observation.kt.in').read_text()
    return output
