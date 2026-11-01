#!/usr/bin/env python3
"""Run fixed original samples observing only existing native recognizer calls."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(original_touch_delivery=True))
