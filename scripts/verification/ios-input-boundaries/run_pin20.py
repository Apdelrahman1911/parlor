#!/usr/bin/env python3
"""Actual public catalog motion and isolated export control; not qualification."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(public_motion=True))
