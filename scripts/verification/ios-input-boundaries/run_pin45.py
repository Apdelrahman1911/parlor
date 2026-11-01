#!/usr/bin/env python3
"""Original full context on the failed runtime; not a qualification replay."""
import sys
import run_pin03
import pin45_original_runtime_context

if __name__ == '__main__':
    pin45_original_runtime_context.require_context_binding()
    sys.exit(run_pin03.main(original_runtime_context=True))
