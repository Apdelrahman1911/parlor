"""Exact original public journeys with pre-abort failure/root/AX capture.

The PIN11 profile stays byte-exact. This separately selected profile fills its
confirmed failure-owner and public AX scope gaps, never modifies shipping input.
"""
import pin06_public_setup_pair as pair
import pin09_native_boundary as boundary
import pin10_root_boundary as root
import pin11_root_observations as observed
import pin11_public_failure_coverage as coverage

SOURCE, TREE = observed.SOURCE, observed.TREE
PROFILE = 'native-public-failure-boundaries'
ARMS = observed.ARMS
SCOPE = ('Original5029 EN/AR public journeys with once-only pre-abort export and public AX/root '
         'coverage; no private labels/values, pointer expansion, action/query/timeout changes, '
         'blind qualification retry, shipping repair or physical verification')


def renderer_for_profile(profile, source, runtime, device):
    root.native.require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
                        'Exact public failure observation source/profile/runtime/device required')
    base = observed.renderer_for_profile(observed.PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE)
    def render(sources, digests, *, arm):
        return coverage.extend(base(sources, digests, arm=arm))
    return render


test_command = observed.test_command
verify_results = observed.verify_results
failed_method_exports = observed.failed_method_exports
retain_companion = observed.retain_companion
retain_root_companion = observed.retain_root_companion


def accept_native_export(out, state):
    boundary.accept_observation_export(out, state, profile=PROFILE,
        renderer=renderer_for_profile(PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE))
    root.accept_root_companions(out, state)
    return 'PUBLIC_FAILURE_BOUNDARY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'
