#!/usr/bin/env python3
"""Correct native evidence intake; no passing-journey replay or shipping acceptance."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(native_input_export_control=True))
