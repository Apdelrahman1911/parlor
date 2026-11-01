#!/usr/bin/env python3
"""Execute one separately validated owned-leaf failed-export control."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(owned_leaf_export=True))
