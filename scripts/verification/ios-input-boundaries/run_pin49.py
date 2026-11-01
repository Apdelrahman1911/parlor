#!/usr/bin/env python3
"""Run original PIN48 pair with bounded owned host-native stack samples only."""
import sys
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

    class ObservedCommands(original):
        def capture(self, arguments, label, timeout=15, **kwargs):
            return stacks.execute_observed(super().capture, arguments, label, timeout, **kwargs)

    run_pin03.Commands = ObservedCommands
    try:
        result = run_pin03.main(failure_runloop=True)
        if result == 0:
            import json
            output = Path(sys.argv[sys.argv.index("--output")+1])
            state = json.loads((output/"state.json").read_text())
            products = json.loads((output/"compose/product.json").read_text())
            stacks.verify_receipt(output/"compose/native-stacks", state["arms"][0]["simulator"], products["Parlor"])
        return result
    finally:
        run_pin03.Commands = original


if __name__ == '__main__':
    sys.exit(main())
