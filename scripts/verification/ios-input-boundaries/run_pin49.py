#!/usr/bin/env python3
"""Run original PIN48 pair with bounded owned host-native stack samples only."""
import sys
import json
from pathlib import Path

import pin49_native_stacks as stacks


def main():
    if len(sys.argv) == 3 and sys.argv[1] == '--check-tool':
        stacks.tool_control(Path(sys.argv[2]).absolute())
        return 0
    import pin47_failure_runloop
    import run_pin03
    pin47_failure_runloop.require_capability_binding()
    if '--profile' not in sys.argv or sys.argv[sys.argv.index('--profile')+1] != pin47_failure_runloop.QUERY_PROFILE:
        raise ValueError('Only original AR/EN property-boundary pair may request OS samples')
    original = run_pin03.Commands
    helpers = []

    class ObservedCommands(original):
        def capture(self, arguments, label, timeout=15, **kwargs):
            if label == 'compose-build':
                destinations = [str(a).removeprefix('platform=iOS Simulator,id=') for a in arguments
                                if str(a).startswith('platform=iOS Simulator,id=')]
                stacks.require(timeout == 2700 and len(destinations) == 1
                               and stacks.UUID.fullmatch(destinations[0]) and not helpers,
                               'One original owned Debug build and one prebuild helper')
                helper = stacks.kernel.KernelChannel(destinations[0], stacks.environment())
                helpers.append(helper)
                helper.before_build = True
                helper.start()  # Before compiler load; idle on its pipe throughout build.
            options = {}
            if label == 'compose-tests':
                stacks.require(len(helpers) == 1, 'Existing actual prebuild helper required, no late startup')
                options['kernel_channel'] = helpers[0]
            return stacks.execute_observed(super().capture, arguments, label, timeout, **options, **kwargs)

    run_pin03.Commands = ObservedCommands
    try:
        result = run_pin03.main(failure_runloop=True)
        if result == 0:
            output = Path(sys.argv[sys.argv.index("--output")+1])
            state = json.loads((output/"state.json").read_text())
            products = json.loads((output/"compose/product.json").read_text())
            build = [c for c in state['commands'] if c['label'] == 'compose-build']
            stacks.require(len(build) == 1, 'One actual original build intent')
            stacks.verify_receipt(output/"compose/native-stacks", state["arms"][0]["simulator"], products["Parlor"],
                                  build_started_at=build[0]['started_at'])
        return result
    finally:
        run_pin03.Commands = original
        for helper in helpers:
            helper.close()
            output = Path(sys.argv[sys.argv.index('--output')+1])/'compose'/'kernel-prebuild.json'
            with output.open('x') as receipt:
                json.dump(helper.receipt(), receipt, indent=2)


if __name__ == '__main__':
    sys.exit(main())
