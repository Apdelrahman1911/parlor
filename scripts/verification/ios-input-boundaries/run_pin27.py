#!/usr/bin/env python3
"""Observe the actual input attachment; never manufacture/retry original input."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(attached_input_delivery=True))
