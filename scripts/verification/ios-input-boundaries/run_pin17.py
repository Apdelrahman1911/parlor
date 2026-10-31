#!/usr/bin/env python3
"""One paired original/observed experiment, never a production retry."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(observer_comparison=True))
