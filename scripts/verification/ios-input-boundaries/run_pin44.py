#!/usr/bin/env python3
"""Original failed OS/source check; a pending precursor cannot execute."""
import sys
import run_pin03
import pin44_original_runtime

if __name__ == '__main__':
    pin44_original_runtime.require_pair_binding()
    sys.exit(run_pin03.main(original_runtime=True))
