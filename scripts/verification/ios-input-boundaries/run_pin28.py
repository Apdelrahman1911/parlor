#!/usr/bin/env python3
"""Observe original EN/AR input/lifecycle boundaries; no synthetic test replay."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(original_input_lifecycle=True))
