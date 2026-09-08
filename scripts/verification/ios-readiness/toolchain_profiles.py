"""Closed native-observation profiles; no tool execution or implicit fallback.

The local default preserves the original Xcode 26.5 observation environment.
The explicitly selected hosted profile uses the release-qualified Xcode build,
whose actual bundled simulator SDK is 26.2, not 26.3. Neither profile authorizes
Store signing/publication or physical-device testing.
"""
from pathlib import Path

LOCAL = 'local-xcode-26.5'
QUALIFIED = 'qualified-xcode-26.3'
QUALIFIED_DEVELOPER = '/Applications/Xcode_26.3.app/Contents/Developer'


def profile(name):
    if name == LOCAL:
        return dict(name=name, xcode='26.5', build='17F42', sdk='26.5',
                    runtime='com.apple.CoreSimulator.SimRuntime.iOS-26-5',
                    developer_dir=None,
                    scope='Xcode26.5/17F42 Debug simulator; not Store-qualified26.3/17C529')
    if name == QUALIFIED:
        return dict(name=name, xcode='26.3', build='17C529', sdk='26.2',
                    runtime='com.apple.CoreSimulator.SimRuntime.iOS-26-2',
                    developer_dir=QUALIFIED_DEVELOPER,
                    scope='Xcode26.3/17C529 qualified compiler; Debug iOS26.2 simulator '
                          'observation only, not Store-signing or physical-device evidence')
    raise RuntimeError('Unknown explicit native toolchain profile')


def selected_toolchain(arguments):
    """Remove at most one exact toolchain flag; other parsers remain strict."""
    values = [argument for argument in arguments if argument.startswith('--toolchain=')]
    if len(values) > 1:
        raise RuntimeError('Duplicate native toolchain selection')
    name = LOCAL if not values else values[0].removeprefix('--toolchain=')
    profile(name)  # Reject unknown values before any allocation/native action.
    return name, [argument for argument in arguments if not argument.startswith('--toolchain=')]


def developer_environment(name, inherited):
    expected = profile(name)['developer_dir']
    if expected is None:
        # Historical default intentionally never inherited DEVELOPER_DIR.
        return {}
    if inherited.get('DEVELOPER_DIR') != expected:
        raise RuntimeError('Qualified profile requires its exact explicit DEVELOPER_DIR')
    return dict(DEVELOPER_DIR=expected)


def validate_observation(name, xcode_version, sdk_version, developer_dir, architecture):
    """Validate retained actual command output, not an inferred SDK version."""
    expected = profile(name)
    if (xcode_version != 'Xcode ' + expected['xcode'] + '\nBuild version ' + expected['build'] + '\n' or
            sdk_version.strip() != expected['sdk'] or architecture.strip() != 'arm64'):
        raise RuntimeError('Toolchain differs from the explicitly reviewed native observation profile')
    developer = developer_dir.strip()
    if (not developer or '\n' in developer or '\r' in developer or not Path(developer).is_absolute() or
            (expected['developer_dir'] is not None and developer != expected['developer_dir'])):
        raise RuntimeError('Selected Developer directory differs from the native observation profile')
    return dict(**expected, selected_developer_dir=developer, architecture='arm64',
                sdk_name='iphonesimulator' + expected['sdk'])
