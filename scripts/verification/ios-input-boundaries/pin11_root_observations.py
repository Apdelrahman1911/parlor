"""Original root/element evidence; no added touch, query, timer or synthetic fault."""
import pin06_public_setup_pair as pair
import pin09_native_boundary as boundary
import pin10_root_boundary as root

SOURCE, TREE = root.SOURCE, root.TREE
PROFILE = 'native-root-public-boundaries'
ARMS = boundary.ARMS
SCOPE = ('Distinct missing root-lifetime measurement beside original element returns in actual5029 EN/AR '
         'methods; no extra touch/query/timeout, synthetic fault, blind qualification retry or physical evidence')


def renderer_for_profile(profile, source, runtime, device):
    root.native.require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
                        'Exact root observation source/profile/runtime/device required')
    base = boundary.renderer_for_profile(boundary.PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE)
    def render(sources, digests, *, arm):
        return root.add_root_observation(base(sources, digests, arm=arm))
    return render


test_command = boundary.test_command
verify_results = boundary.verify_results
failed_method_exports = boundary.failed_method_exports
retain_companion = root.retain_companion
retain_root_companion = root.retain_root_companion


def accept_native_export(out, state):
    boundary.accept_observation_export(out, state, profile=PROFILE,
        renderer=renderer_for_profile(PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE))
    root.accept_root_companions(out, state)
    return 'ROOT_BOUNDARY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'
