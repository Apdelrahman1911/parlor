#!/usr/bin/env python3
"""Run the explicit finite catalog boundary without added pointer modifiers."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(catalog_input_boundary=True))
