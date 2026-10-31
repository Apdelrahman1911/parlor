#!/usr/bin/env python3
"""Execute only the existing isolated motion-export control; not qualification."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(motion_control=True))
