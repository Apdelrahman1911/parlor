#!/usr/bin/env python3
"""Run original journeys with failure-only equivalent query observation."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(equivalent_query=True))
