#!/usr/bin/env python3
"""Observe the original full XCTest context with the existing touch adapter."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(original_touch_cohort=True))
