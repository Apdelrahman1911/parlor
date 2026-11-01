#!/usr/bin/env python3
"""Run one predeclared original or observed public-session stress arm."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(public_session_stress=True))
