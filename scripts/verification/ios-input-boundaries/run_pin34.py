#!/usr/bin/env python3
"""Run only the existing isolated original-native-touch failure-export control."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(original_touch_export_control=True))
