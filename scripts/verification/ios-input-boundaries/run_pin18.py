#!/usr/bin/env python3
"""Explicit synthetic AX idle-gap control; never an application qualification."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(root_idle=True))
