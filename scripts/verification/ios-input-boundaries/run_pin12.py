#!/usr/bin/env python3
"""Public pre-abort root/element observations, not application qualification."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(public_boundary=True))
