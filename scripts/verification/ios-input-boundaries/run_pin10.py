#!/usr/bin/env python3
"""Explicit distinct root capability; never shipping qualification."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(root_capability=True))
